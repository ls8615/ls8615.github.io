package cvmes.hzzk;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;
import cvmes.common.AbstractSubServiceThread;
import cvmes.common.Log;
import cvmes.common.LogLevel;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class HzzkOutputDataEdit extends AbstractSubServiceThread {
    private String msg = "";

    @Override
    public void initServiceCode() {
        this.strServiceCode = "HzzkOutputDataEdit";
    }

    @Override
    public String runBll(Record rec_service) throws Exception {
        List<Record> list = Db.find("SELECT LINE_CODE, PRODUCTION_DATE, STATION_CODE, COUNT(*) AS OUTPUT_NUM, PLAN_OEE" +
                " FROM (" +
                "SELECT t1.ACTUAL_POINT_CODE, to_char(t1.WORK_DATE, 'yyyy-mm-dd') AS PRODUCTION_DATE, t2.STATION_CODE," +
                "  (SELECT LINE_CODE FROM T_MODEL_STATION WHERE STATION_CODE = t2.STATION_CODE) AS LINE_CODE," +
                "  (SELECT PARA_VALUE FROM T_SYS_PARA WHERE PARA_CODE = 'weld_plan_oee') AS PLAN_OEE" +
                " FROM T_ACTUAL_PASSED_RECORD t1" +
                " LEFT JOIN T_MODEL_ACTUAL_POINT t2 ON t1.ACTUAL_POINT_CODE = t2.ACTUAL_POINT_CODE" +
                " WHERE to_char(WORK_DATE, 'yyyy-mm-dd') = func_get_workday()" +
                " AND t2.STATION_CODE IN ('BK060', 'L_SB050', 'R_SB050', 'MB010', 'FB220', 'UB080', 'UB010')" +
                ") GROUP BY LINE_CODE, PRODUCTION_DATE, STATION_CODE, PLAN_OEE");
        if (list == null) {
            Db.update("update T_SYS_SERVICE set LAST_OPERATE_TIME=sysdate, LAST_OPERATE_INFO='获取产量统计数据失败' where SERVICE_CODE=?", strServiceCode);
            msg = "获取产量统计数据失败";
            Log.Write(strServiceCode, LogLevel.Error, msg);
            return msg;
        }

        float factOee = getFactOee(rec_service);

        for (Record sub : list) {
            // 根据产线、工位、生产日期查询产量接口表是否已经存在这个工位数据
            Record rec = Db.findFirst("select count(*) as CNT from T_INF_TO_STAMPC_OUTPUT where LINE_CODE = ? and to_char(PRODUCTION_DATE, 'yyyy-mm-dd') = ? and STATION_CODE = ?",
                    sub.getStr("LINE_CODE"),
                    sub.getStr("PRODUCTION_DATE"),
                    sub.getStr("STATION_CODE"));

            if (rec.getInt("CNT") > 0) {
                // 数据已存在，更新
                String sql = String.format("update T_INF_TO_STAMPC_OUTPUT set OUTPUT_NUM = '%d', DEAL_TIME = sysdate, FACT_OEE = '%s', PLAN_OEE = '%s' where LINE_CODE = '%s' and to_char(PRODUCTION_DATE, 'yyyy-mm-dd') = '%s' and STATION_CODE = '%s'",
                        sub.getInt("OUTPUT_NUM"),
                        String.valueOf(factOee),
                        sub.getStr("PLAN_OEE"),
                        sub.getStr("LINE_CODE"),
                        sub.getStr("PRODUCTION_DATE"),
                        sub.getStr("STATION_CODE"));
                Db.update(sql);
            } else {
                // 数据不存在，插入
                String sql = String.format("insert into T_INF_TO_STAMPC_OUTPUT(ID, LINE_CODE, PRODUCTION_DATE, STATION_CODE, OUTPUT_NUM, DEAL_STATUS, DEAL_TIME, PLAN_OEE, FACT_OEE) values(sys_guid(), '%s', to_date('%s', 'yyyy-mm-dd'), '%s', '%d', '0', sysdate, '%s', '%s')",
                        sub.getStr("LINE_CODE"),
                        sub.getStr("PRODUCTION_DATE"),
                        sub.getStr("STATION_CODE"),
                        sub.getInt("OUTPUT_NUM"),
                        sub.getStr("PLAN_OEE"),
                        String.valueOf(factOee));
                Db.update(sql);
            }
        }

        // 更新OEE结果
        if (list.size() == 0) {
            msg = "无需更新OEE结果";
            Log.Write(strServiceCode, LogLevel.Warning, msg);
            return msg;
        }

        List<Record> listoee = Db.find("select * from T_ACTUAL_OEERESULT where LINE_CODE='HzPhaseLine' and to_char(WORK_DATE,'yyyy-mm-dd')=?", list.get(0).getStr("PRODUCTION_DATE"));
        if (listoee == null) {
            msg = "更新OEE结果失败";
            Log.Write(strServiceCode, LogLevel.Error, msg);
            return msg;
        }
        if (listoee.size() == 0) {
            // 不存在，插入
            String sql = String.format("insert into T_ACTUAL_OEERESULT(ID, LINE_CODE, WORK_DATE, PLAN_OEE, FACT_OEE, UPD_DATE) values(sys_guid(), 'HzPhaseLine', to_date('%s', 'yyyy-mm-dd'), '%s', '%s', sysdate)",
                    list.get(0).getStr("PRODUCTION_DATE"),
                    list.get(0).getStr("PLAN_OEE"),
                    String.valueOf(factOee));
            Db.update(sql);
        } else {
            // 已存在更新
            String sql = String.format("update T_ACTUAL_OEERESULT set PLAN_OEE='%s', FACT_OEE='%s', UPD_DATE=sysdate where LINE_CODE='HzPhaseLine' and to_char(WORK_DATE,'yyyy-mm-dd')='%s'",
                    list.get(0).getStr("PLAN_OEE"),
                    String.valueOf(factOee),
                    list.get(0).getStr("PRODUCTION_DATE"));
            Db.update(sql);
        }

        msg = "焊装产量统计成功";
        return msg;
    }

    /**
     * 计算实际OEE
     *
     * @param rec_service
     * @return 实际OEE的值
     */
    private float getFactOee(Record rec_service) {
        try {
            // 获取班次时间方案编号和系统时间
            Record rec = Db.findFirst("SELECT sysdate as curtime, func_get_workday() as workday, t.* FROM T_WORK_CALENDAR t WHERE LINE_CODE=? AND TO_CHAR(WORK_DATE, 'yyyy-mm-dd')=func_get_workday() AND IS_SCHEDULING=1", rec_service.getStr("SERVICE_PARA2_VALUE"));
            if (rec == null) {
                Log.Write(strServiceCode, LogLevel.Error, "获取当前班次时间方案编码失败");
                return 0.0f;
            }
            String shift_solut_code = rec.getStr("WORK_SHIFT_SOLUT_CODE");
            Date dat_sys = rec.getDate("curtime");
            String workday = rec.getStr("workday");

            // 获取班次时间方案
            List<Record> list = Db.find("SELECT * FROM T_WORK_SHIFT_TIME_SOLUT WHERE WORK_SHIFT_SOLUT_CODE=? ORDER BY WORK_SHIFT_SOLUT_CODE,WORK_SHIFT,BEGIN_TIME_DAY, BEGIN_TIME", shift_solut_code);
            if (list == null || list.size() == 0) {
                Log.Write(strServiceCode, LogLevel.Error, "获取班次时间方案失败");
                return 0.0f;
            }

            // 计算上班到当前的总时间（分钟）
            long totalRest = 0;
            long totalMinute = 0;

            // 计算上班到当前的计划休息时间（分钟）、计划工作时间（分钟）、总时间
            for (Record sub : list) {
                // 当前记录的开始和结束时间转换
                Date dat_begin = getDateByTime(sub.getStr("BEGIN_TIME"), dat_sys, true);
                Date dat_end = getDateByTime(sub.getStr("END_TIME"), dat_sys, false);

                // 系统时间是否在当前记录时间范围内
                if (diffTime(dat_begin, dat_sys) && diffTime(dat_sys, dat_end)) {
                    if ("1".equals(sub.getStr("TIME_QUANTUM_TYPE"))) {
                        totalRest += calcTimeSub(dat_begin, dat_sys);
                        totalMinute += calcTimeSub(dat_begin, dat_sys);
                    } else {
                        totalMinute += calcTimeSub(dat_begin, dat_sys);
                    }
                    break;
                } else {
                    if ("1".equals(sub.getStr("TIME_QUANTUM_TYPE"))) {
                        totalRest += calcTimeSub(dat_begin, dat_end);
                        totalMinute += calcTimeSub(dat_begin, dat_end);
                    } else {
                        totalMinute += calcTimeSub(dat_begin, dat_end);
                    }
                }
            }

            // 计算实际产量
            Record recOutput = Db.findFirst("SELECT count(*) as OUTPUT FROM T_ACTUAL_PASSED_RECORD WHERE ACTUAL_POINT_CODE=? AND to_char(WORK_DATE,'yyyy-mm-dd')=?", rec_service.getStr("SERVICE_PARA1_VALUE"), workday);
            if (recOutput == null) {
                Log.Write(strServiceCode, LogLevel.Error, "获取产量失败");
                return 0.0f;
            }
            int output = recOutput.getInt("OUTPUT");

            // 计算临时计划停线时间
            List<Record> listTempPlanStopLine = Db.find("select * from T_WORK_SCHEDULED_STOPTIME where LINE_CODE=? and to_char(WORKING_DAY,'yyyy-mm-dd')=?", rec_service.getStr("SERVICE_PARA2_VALUE"), workday);
            long totalTempPlanStopLine = 0;
            if (listTempPlanStopLine != null) {
                for (Record subTempPlanStopLine : listTempPlanStopLine) {
                    // 开始时间转换
                    Date dat_temp_begin = getDateByTime(subTempPlanStopLine.getStr("TIME_BEGIN"), dat_sys, true);

                    // 计算开始时间和当前时间差
                    long subbegin = calcTimeSub(dat_temp_begin, dat_sys);

                    // 当前时间大于开始时间
                    if (diffTime(dat_temp_begin, dat_sys)) {
                        // 开始时间和当前时间差小于预设停线分钟数，说明还没有到计划结束时间
                        if (subbegin < subTempPlanStopLine.getInt("STOP_TIME_NUM")) {
                            totalTempPlanStopLine += subbegin;
                        } else {
                            totalTempPlanStopLine += subTempPlanStopLine.getInt("STOP_TIME_NUM");
                        }
                    }
                }
            }

            // 计划JPH
            Record recjph = Db.findFirst("SELECT * FROM T_SYS_PARA WHERE PARA_CODE='weld_plan_jph'");
            if (recjph == null) {
                Log.Write(strServiceCode, LogLevel.Error, "获取计划JPH失败");
                return 0.0f;
            }
            float planjph = Float.parseFloat(recjph.getStr("PARA_VALUE"));

            // 实际JPH（台/小时）
            if (totalMinute - totalRest - totalTempPlanStopLine <= 0) return 0.0f;
            float jph = output / ((float) (totalMinute - totalRest - totalTempPlanStopLine) / 60);

            // 实际OEE=实际JPH/计划JPH
            float fact_oee = jph / planjph;
            if (fact_oee > 0.99f) {
                fact_oee = 0.99f;
            }
            return fact_oee;
        } catch (Exception e) {
            e.printStackTrace();
            Log.Write(strServiceCode, LogLevel.Error, String.format("计算OEE失败，原因【%s】", e.getMessage()));
            return 0.0f;
        }
    }

    /**
     * 根据时间字符串获取完整时间
     *
     * @param str     时间字符串，格式HH:mm
     * @param dat_sys 系统日
     * @param flag    true=开始时间；false=结束时间
     * @return
     */
    private Date getDateByTime(String str, Date dat_sys, boolean flag) {
        String strday;
        String begin_time = String.format("%s 00:00:00", new SimpleDateFormat("yyyy-MM-dd").format(dat_sys));
        String end_time = String.format("%s 07:29:59", new SimpleDateFormat("yyyy-MM-dd").format(dat_sys));

        if (flag) {
            strday = String.format("%s %s:00", new SimpleDateFormat("yyyy-MM-dd").format(dat_sys), str);
        } else {
            strday = String.format("%s %s:59", new SimpleDateFormat("yyyy-MM-dd").format(dat_sys), str);
        }

        try {
            Date day = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(strday);
            Date begin = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(begin_time);
            Date end = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(end_time);

            if (diffTime(begin, day) && diffTime(day, end)) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(day);
                calendar.add(Calendar.DATE, -1);
                day = calendar.getTime();
            }

            return day;
        } catch (ParseException e) {
            return null;
        }
    }

    /**
     * 比较两个时间的大小
     *
     * @param d1
     * @param d2
     * @return true=参数2时间大；false=参数1时间大
     */
    private boolean diffTime(Date d1, Date d2) {
        if (d1 == null || d2 == null) return false;

        long diff = d2.getTime() - d1.getTime();
        if (diff >= 0) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * 计算时间差（分钟）
     *
     * @param begin 开始时间
     * @param end   结束时间
     * @return 两者之间所差的分钟数
     */
    private long calcTimeSub(Date begin, Date end) {
        if (begin == null || end == null) return 0;

        long diff = end.getTime() - begin.getTime();
        return (diff / (1000 * 60)) + 1;
    }
}

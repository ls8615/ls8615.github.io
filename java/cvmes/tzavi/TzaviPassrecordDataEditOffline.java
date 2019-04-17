package cvmes.tzavi;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.IAtom;
import com.jfinal.plugin.activerecord.ICallback;
import com.jfinal.plugin.activerecord.Record;
import cvmes.CvmesService;
import cvmes.common.Log;
import cvmes.common.LogLevel;
import oracle.jdbc.OracleTypes;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class TzaviPassrecordDataEditOffline {
    public void run() {
        Thread thread = new Thread(new TzaviPassrecordDataEditOfflineThread());
        thread.start();
    }

    class TzaviPassrecordDataEditOfflineThread extends Thread {
        // 服务编码
        private final String strServiceCode = "TzaviPassrecordDataEditOffline";

        // 业务操作
        private void runBll(Record rec_service) {
            // 获取待处理的涂装AVI过点记录数据
            List<Record> list = Db.find("select * from T_INF_FROM_PAINTAVI_PASSRECORD where DEAL_STATUS = 0 and POSITION_NAME=? ORDER BY MOVE_TIME", rec_service.getStr("SERVICE_PARA1_VALUE"));

            // 逐条处理涂装AVI过点记录
            int ok = 0;
            for (Record sub : list) {
                boolean flag = dealTzaviPassRecord(sub);
                if (flag) {
                    Db.update("update T_INF_FROM_PAINTAVI_PASSRECORD set DEAL_STATUS=1, DEAL_TIME=sysdate where ID=?", sub.getStr("ID"));
                    ok++;
                } else {
                    Db.update("update T_SYS_SERVICE set LAST_OPERATE_TIME=sysdate, LAST_OPERATE_INFO=? where SERVICE_CODE=?", String.format("涂装下线记录处理失败导致卡车，ID【%s】，工位【%s】，钢码号【%s】", sub.getStr("ID"), sub.getStr("POSITION_NAME"), sub.getStr("FIN_NO")), strServiceCode);
                    return;
                }
            }

            // 更新服务信息
            if (list.size() > 0) {
                Db.update("update T_SYS_SERVICE set LAST_OPERATE_TIME=sysdate, LAST_OPERATE_INFO=? where SERVICE_CODE=?", String.format("成功处理涂装下线记录【%d】条", ok), strServiceCode);
            }
        }

        private boolean dealTzaviPassRecord(Record rec) {
            try {
                // 获取生产线
                List<Record> list_line = Db.find("select * from T_MODEL_STATION where STATION_CODE = ?", rec.getStr("POSITION_NAME"));
                if (list_line.size() != 1) {
                    Log.Write(strServiceCode, LogLevel.Error, String.format("ID【%s】，钢码号【%s】，工位【%s】的过点记录无法找到正确的生产线信息，请检查配置", rec.getStr("ID"), rec.getStr("FIN_NO"), rec.getStr("POSITION_NAME")));
                    return false;
                }
                String strLineCode = list_line.get(0).getStr("LINE_CODE");

                // 获取过点信息
                List<Record> list_pass = Db.find("SELECT t2.ACTUAL_POINT_CODE, t3.PRODUCTION_CODE," +
                        " ? AS LINE_CODE, to_date(func_getnosys_workday(t1.MOVE_TIME),'yyyy-mm-dd') AS WORK_DATE," +
                        " t3.DEMAND_PRODUCT_CODE AS PRODUCT_CODE, '1' AS PASSED_RECORD_TYPE, t1.MOVE_TIME AS PASSED_TIME," +
                        " (SELECT WORKSHOP_CODE FROM T_MODEL_Line WHERE LINE_CODE=?) AS WORKSHOP_CODE" +
                        " FROM T_INF_FROM_PAINTAVI_PASSRECORD t1" +
                        " LEFT JOIN T_MODEL_ACTUAL_POINT t2 ON t1.POSITION_NAME = t2.STATION_CODE" +
                        " LEFT JOIN T_PLAN_DEMAND_PRODUCT t3 ON t1.FIN_NO = t3.K_STAMP_ID" +
                        " WHERE t1.ID = ?", strLineCode, strLineCode, rec.getStr("ID"));
                if (list_pass.size() != 1) {
                    Log.Write(strServiceCode, LogLevel.Error, String.format("ID【%s】，钢码号【%s】，工位【%s】的过点记录匹配业务表数据失败，请检查数据", rec.getStr("ID"), rec.getStr("FIN_NO"), rec.getStr("POSITION_NAME")));
                    return false;
                }
                Record rec_pass = list_pass.get(0);
                if (rec_pass.getStr("ACTUAL_POINT_CODE") == null) {
                    Log.Write(strServiceCode, LogLevel.Error, String.format("ID【%s】，钢码号【%s】，工位【%s】的过点记录匹配业务表数据失败，实绩点编码不存在，请检查数据", rec.getStr("ID"), rec.getStr("FIN_NO"), rec.getStr("POSITION_NAME")));
                    return false;
                }
                if (rec_pass.getStr("PRODUCTION_CODE") == null) {
                    Log.Write(strServiceCode, LogLevel.Error, String.format("ID【%s】，钢码号【%s】，工位【%s】的过点记录匹配业务表数据失败，生产编码不存在，请检查数据", rec.getStr("ID"), rec.getStr("FIN_NO"), rec.getStr("POSITION_NAME")));
                    return false;
                }

                // 重复数据判断（实绩点+生产编码+过点时间）
                List<Record> list_repeat = Db.find("select * from T_ACTUAL_PASSED_RECORD where ACTUAL_POINT_CODE = ? and PRODUCTION_CODE = ? and to_char(PASSED_TIME, 'yyyy-mm-dd hh24:mi:ss') = ?", rec_pass.getStr("ACTUAL_POINT_CODE"), rec_pass.getStr("PRODUCTION_CODE"), new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(rec_pass.getDate("PASSED_TIME")));
                if (list_repeat.size() > 0) {
                    Log.Write(strServiceCode, LogLevel.Warning, String.format("过点记录数据重复，记录ID【%s】，钢码号【%s】，工位【%s】（实绩点【%s】），过点时间【%s】", rec.getStr("ID"), rec.getStr("FIN_NO"), rec.getStr("POSITION_NAME"), rec_pass.getStr("ACTUAL_POINT_CODE"), new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(rec_pass.getDate("PASSED_TIME"))));
                    return true;
                }

                boolean ret = Db.tx(new IAtom() {
                    public boolean run() throws SQLException {
                        // 插入过点记录业务表
                        String uuid = java.util.UUID.randomUUID().toString();
                        Db.update("insert into T_ACTUAL_PASSED_RECORD(ID,ACTUAL_POINT_CODE,PRODUCTION_CODE,LINE_CODE,WORK_DATE,PRODUCT_CODE,PASSED_RECORD_TYPE,PASSED_TIME) values(?,?,?,?,?,?,?,?)",
                                uuid,
                                rec_pass.getStr("ACTUAL_POINT_CODE"),
                                rec_pass.getStr("PRODUCTION_CODE"),
                                rec_pass.getStr("LINE_CODE"),
                                rec_pass.getDate("WORK_DATE"),
                                rec_pass.getStr("PRODUCT_CODE"),
                                rec_pass.getStr("PASSED_RECORD_TYPE"),
                                rec_pass.getDate("PASSED_TIME"));

                        // 移车
                        Record rec_actual_point = Db.findById("T_MODEL_ACTUAL_POINT", "ACTUAL_POINT_CODE", rec_pass.getStr("ACTUAL_POINT_CODE"));
                        if (rec_actual_point.getStr("TARGET_ZONE_CODE") == null) {
                        } else {
                            boolean flag = (boolean) Db.execute(new ICallback() {
                                @Override
                                public Object call(Connection conn) throws SQLException {
                                    CallableStatement proc = conn.prepareCall("{CALL PROC_MOVE_CAR(?,?,?,?)}");
                                    proc.setString("PARA_PRODUCTION_CODE", rec_pass.getStr("PRODUCTION_CODE"));
                                    proc.setString("PARA_DEST_ZONE", rec_actual_point.getStr("TARGET_ZONE_CODE"));
                                    proc.setInt("PARA_IS_CHECK_POS", 1);
                                    proc.registerOutParameter("PARA_MSG", OracleTypes.VARCHAR);
                                    proc.execute();
                                    //代码来到这里就说明你的存储过程已经调用成功，如果有输出参数，接下来就是取输出参数的一个过程
                                    String strRet = proc.getString("PARA_MSG");
                                    if (strRet == null) {
                                        return true;
                                    } else {
                                        if (strRet.length() > 0) {
                                            Log.Write(strServiceCode, LogLevel.Error, String.format("移车失败，原因【%s】", strRet));
                                            return false;
                                        } else {
                                            return true;
                                        }
                                    }
                                }
                            });

                            // 移车失败，回滚事务
                            if (!flag) return false;
                        }

                        // 插入生产指令
                        List<Record> list_indicate = Db.find("select * from T_MODEL_INDICATION_POINT where INDICATION_POINT_TRIGGER='1' and ACTUAL_POINT_CODE=?", rec_actual_point.getStr("ACTUAL_POINT_CODE"));
                        for (Record rec_indicate : list_indicate) {
                            Db.update("insert into T_CMD_PRODUCTION_ORDER(ID,INDICATION_POINT_CODE,WORK_DAY,PRODUCTION_CODE,CREATE_TIME,PRODUCTION_ORDER_STATUS,WORKSHOP_CODE,PASSED_ID) values(?,?,?,?,?,?,?,?)",
                                    java.util.UUID.randomUUID().toString(),
                                    rec_indicate.getStr("INDICATION_POINT_CODE"),
                                    rec_pass.getDate("WORK_DATE"),
                                    rec_pass.getStr("PRODUCTION_CODE"),
                                    new Date(),
                                    "0",
                                    rec_pass.getStr("WORKSHOP_CODE"),
                                    uuid);
                        }

                        // 提交事务
                        return true;
                    }
                });

                return ret;
            } catch (Exception e) {
                e.printStackTrace();
                Log.Write(strServiceCode, LogLevel.Error, String.format("处理涂装AVI过点记录失败，记录ID【%s】，钢码号【%s】，异常原因【%s】", rec.getStr("ID"), rec.getStr("FIN_NO"), e.getMessage()));
                return false;
            }
        }

        // 线程控制
        @Override
        public void run() {
            Log.Write(strServiceCode, LogLevel.Information, "服务启动");

            while (true) {
                // 获取服务信息
                Record rec = Db.findById("T_SYS_SERVICE", "SERVICE_CODE", strServiceCode);

                // 更新生存时间
                Db.update("update T_SYS_SERVICE set LAST_LIVE_TIME=sysdate where SERVICE_CODE=?", strServiceCode);

                // 退出指令
                if (rec.getInt("SERVICE_STATUS") == 0) {
                    Log.Write(strServiceCode, LogLevel.Warning, "服务停止");
                    CvmesService.setServiceStop(strServiceCode);
                    break;
                }

                // 业务操作
                try {
                    runBll(rec);
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.Write(strServiceCode, LogLevel.Error, String.format("业务操作发生异常，原因【%s】", e.getMessage()));
                }

                // 休眠
                try {
                    Thread.sleep(1000 * rec.getInt("SLEEP_TIME"));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}

package cvmes.cvpm;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.IAtom;
import com.jfinal.plugin.activerecord.Record;
import cvmes.common.AbstractSubServiceThread;
import cvmes.common.Log;
import cvmes.common.LogLevel;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.List;

public class CvpmPaintInf extends AbstractSubServiceThread {
    private String msg = "";

    @Override
    public void initServiceCode() {
        this.strServiceCode = "CvpmPaintInf";
    }

    @Override
    public String runBll(Record rec_service) throws Exception {
        msg = "";

        // 获取已锁定未获取的涂装计划
        List<Record> list1 = Db.use("cvpm").find("select * from v_scgl_cs0 WHERE SHF='Y' AND TO_CHAR(RQ, 'yyyy-mm-dd') > ? order by rq", rec_service.getStr("SERVICE_PARA1_VALUE"));
        if (list1 == null || list1.size() == 0) {
        } else {
            for (Record sub1 : list1) {
                msg = dealPaintPlan(1, sub1);
            }
        }

        // 获取修改计划
        List<Record> list2 = Db.use("cvpm").find("select * from v_scgl_cs0 WHERE SHF='Y' AND TO_CHAR(XGSJ, 'yyyy-mm-dd hh24:mi:ss') > ? order by xgsj", rec_service.getStr("SERVICE_PARA2_VALUE"));
        if (list2 == null || list2.size() == 0) {
        } else {
            for (Record sub2 : list2) {
                msg = dealPaintPlan(2, sub2);
            }
        }

        return msg;
    }

    /**
     * 处理涂装计划
     *
     * @param getType
     * @param rec
     * @return
     */
    private String dealPaintPlan(int getType, Record rec) {
        String ret = "";

        String plan_date = new SimpleDateFormat("yyyy-MM-dd").format(rec.getDate("RQ"));
        String plan_code = String.format("TZ%s", new SimpleDateFormat("yyyyMMdd").format(rec.getDate("RQ")));

        // 获取指定日期的涂装计划
        List<Record> list = Db.use("cvpm").find("SELECT * FROM V_SCGL_TZJH WHERE QX is null and TO_CHAR(RQ, 'yyyy-mm-dd') = ? ORDER BY ZPSX4", plan_date);
        if (list == null || list.size() == 0) {
            ret = String.format("获取日期【%s】的涂装计划失败或者无数据", plan_date);
            Log.Write(strServiceCode, LogLevel.Error, ret);
            return ret;
        }

        // 更新需求产品表字段（涂装计划备注）
        for (Record sub : list) {
            Record rec_car = Db.findFirst("select * from T_PLAN_DEMAND_PRODUCT where PRODUCTION_CODE = ?", sub.getStr("SCBM"));
            if (rec_car == null) {
                // 判断车辆是否在需求产品表存在，如果不存在，则插入
                StringBuffer sql_car = new StringBuffer();
                sql_car.append("insert into T_PLAN_DEMAND_PRODUCT(ID, DEMAND_DATE, PRODUCTION_CODE, DEMAND_SOURCE, DEMAND_PRODUCT_CODE, K_CARTYPE,");
                sql_car.append(" K_CARBODY_CODE, K_STAMP_ID, K_PAINT_REMARK, DEMAND_NUM, K_COLOR_NAME, AVAILABLE_STATUS, SCHEDULING_STATUS)");
                sql_car.append(" values(sys_guid(), ?, ?, '0', ?, ?,");
                sql_car.append(" ?, ?, ?, '1', ?, '1', '0')");

                Db.update(sql_car.toString(),
                        sub.getDate("RQ2"), //需求日期
                        sub.getStr("SCBM"), //生产编码
                        sub.getStr("CPDM"), //需求产品编码
                        sub.getStr("CS"), //车身图号
                        sub.getStr("CS1"), //白车身图号
                        sub.getStr("ZBBH"), //钢码号
                        sub.getStr("BZ"), //涂装计划备注
                        sub.getStr("CSYS") //颜色名称
                );
            } else {
                // 更新需求产品表字段（涂装计划备注）
                 /*if (sub.getStr("BZ") != null) {
                Db.update("update T_PLAN_DEMAND_PRODUCT set K_PAINT_REMARK = ?,K_STAMP_ID = ?,K_COLOR_NAME = ?,K_CARBODY_CODE = ?  where PRODUCTION_CODE = ?",
                        sub.getStr("BZ"),sub.getStr("ZBBH"),sub.getStr("CSYS"),sub.getStr("CS1"), sub.getStr("SCBM"));
            }*/
        	
                Db.update("update T_PLAN_DEMAND_PRODUCT set K_PAINT_REMARK = ?,K_STAMP_ID = ?,K_COLOR_NAME = ?,K_CARBODY_CODE = ?  where PRODUCTION_CODE = ?",
                        sub.getStr("BZ"),sub.getStr("ZBBH"),sub.getStr("CSYS"),sub.getStr("CS1"), sub.getStr("SCBM"));
            }
        }

        // 判断主表计划是否存在，不存在则插入
        Record rec_plan_m = Db.findFirst("select count(1) as cnt from T_PLAN_SCHEDULING where SCHEDULING_PLAN_CODE = ?", plan_code);
        if (rec_plan_m == null) {
            ret = String.format("获取计划主表【%s】是否已存在失败", plan_code);
            Log.Write(strServiceCode, LogLevel.Error, ret);
            return ret;
        } else {
            if (rec_plan_m.getInt("cnt") == 0) {
                StringBuffer sql_pm = new StringBuffer();
                sql_pm.append("insert into T_PLAN_SCHEDULING(ID, SCHEDULING_PLAN_CODE, WORKSHOP_CODE,");
                sql_pm.append(" SCHEDULING_PLAN_DATE, SCHEDULING_PLAN_STATUS, AVAILABLE_STATUS, SCHEDULING_PLAN_TYPE)");
                sql_pm.append(" values(sys_guid(), ?, 'tz01', to_date(?, 'yyyy-mm-dd'), '3', '1', '13')");
                Db.update(sql_pm.toString(), plan_code, plan_date);
            }
        }

        // 处理涂装日计划
        boolean flag = Db.tx(new IAtom() {
            @Override
            public boolean run() throws SQLException {
                // 获取明细表营销车计划到列表
                StringBuffer sql_sale = new StringBuffer();
                sql_sale.append("SELECT t1.PRODUCTION_CODE, t2.DEMAND_PRODUCT_CODE");
                sql_sale.append(" FROM T_PLAN_SCHEDULING_D t1");
                sql_sale.append(" LEFT JOIN T_PLAN_DEMAND_PRODUCT t2 ON t1.PRODUCTION_CODE = t2.PRODUCTION_CODE");
                sql_sale.append(" WHERE t1.SCHEDULING_PLAN_CODE = ?");
                sql_sale.append(" AND t2.DEMAND_PRODUCT_TYPE = '5'");
                sql_sale.append(" ORDER BY t1.SEQ_NO");

                List<Record> list_sale = Db.find(sql_sale.toString(), plan_code);

                // 删除明细计划
                Db.update("delete from T_PLAN_SCHEDULING_D where SCHEDULING_PLAN_CODE = ?", plan_code);

                // 插入明细计划
                int seq_no = 1;
                for (Record sub : list) {
                    // 如果有旧明细计划，先删除
                    Db.update("delete from T_PLAN_SCHEDULING_D where PRODUCTION_CODE = ? and LINE_CODE='tz0101'", sub.getStr("SCBM"));

                    // 插入新明细计划
                    StringBuffer sql = new StringBuffer();
                    sql.append("insert into T_PLAN_SCHEDULING_D(ID, LINE_CODE, SEQ_NO, PRODUCTION_CODE, PRODUCT_CODE, PRODUCTION_NUM,");
                    sql.append(" PLAN_ONLINE_TIME, PLAN_OFFLINE_TIME, SCHEDULING_PLAN_CODE, AVAILABLE_STATUS)");
                    sql.append(" values(sys_guid(), 'tz0101', ?, ?, ?, '1',");
                    sql.append(" ?, ?, ?, '1')");

                    Db.update(sql.toString(),
                            sub.getInt("ZPSX4"),
                            sub.getStr("SCBM"),
                            sub.getStr("CPDM"),
                            sub.getDate("CSSJ4"),
                            sub.getDate("CSSJ9"),
                            plan_code);

                    Log.Write(strServiceCode, LogLevel.Information, String.format("收到计划数据，日期【%s】，生产编码【%s】，钢码号【%s】，颜色【%s】，顺序【%d】",
                            plan_date,
                            sub.getStr("SCBM"),
                            sub.getStr("ZBBH"),
                            sub.getStr("CSYS"),
                            sub.getInt("ZPSX4")));

                    seq_no = sub.getInt("ZPSX4");
                }

                // 插入营销车计划
                if (list_sale == null || list.size() == 0) {
                } else {
                    for (Record rec_sale : list_sale) {
                        seq_no++;

                        StringBuffer sql = new StringBuffer();
                        sql.append("insert into T_PLAN_SCHEDULING_D(ID, LINE_CODE, SEQ_NO, PRODUCTION_CODE, PRODUCT_CODE, PRODUCTION_NUM,");
                        sql.append(" SCHEDULING_PLAN_CODE, AVAILABLE_STATUS)");
                        sql.append(" values(sys_guid(), 'tz0101', ?, ?, ?, '1',");
                        sql.append(" ?, '1')");

                        Db.update(sql.toString(),
                                seq_no,
                                rec_sale.getStr("PRODUCTION_CODE"),
                                rec_sale.getStr("DEMAND_PRODUCT_CODE"),
                                plan_code);
                    }
                }

                // 插入生产指令
                List<Record> list_indication_point = Db.find("select indication_point_code from T_MODEL_INDICATION_POINT where INDICATION_POINT_TRIGGER='0' and trigger_plan_type='1'");
                if (list_indication_point == null || list_indication_point.size() == 0) {
                } else {
                    for (Record sub_indication_point : list_indication_point) {
                        // 查询是否存在未处理指令
                        StringBuffer sql_cmd = new StringBuffer();
                        sql_cmd.append("select count(1) as cnt from T_CMD_PRODUCTION_ORDER");
                        sql_cmd.append(" where INDICATION_POINT_CODE = ?");
                        sql_cmd.append(" and PRODUCTION_ORDER_STATUS = '0'");
                        sql_cmd.append(" and PLAN_NO = ?");

                        Record rec_cmd = Db.findFirst(sql_cmd.toString(),
                                sub_indication_point.getStr("INDICATION_POINT_CODE"),
                                plan_code);

                        if (rec_cmd == null || rec_cmd.getInt("cnt") == 0) {
                            // 不存在未处理指令，则插入
                            StringBuffer sql = new StringBuffer();
                            sql.append("insert into T_CMD_PRODUCTION_ORDER(ID, INDICATION_POINT_CODE, WORK_DAY, CREATE_TIME,");
                            sql.append(" PRODUCTION_ORDER_STATUS, WORKSHOP_CODE, PLAN_DATE, PLAN_LINE_CODE, PLAN_NO)");
                            sql.append(" values(");
                            sql.append("sys_guid(), ?, to_date(func_get_workday(), 'yyyy-mm-dd'), sysdate,");
                            sql.append(" '0', 'tz01', to_date(?, 'yyyy-mm-dd'), 'tz0101', ?)");

                            Db.update(sql.toString(),
                                    sub_indication_point.getStr("indication_point_code"),
                                    plan_date,
                                    plan_code);
                        }
                    }
                }

                return true;
            }
        });

        if (flag) {
            if (getType == 1) {
                Db.update("update t_sys_service set SERVICE_PARA1_VALUE = ? where service_code = ?",
                        plan_date, strServiceCode);
            } else if (getType == 2) {
                Db.update("update t_sys_service set SERVICE_PARA2_VALUE = to_char(?, 'yyyy-mm-dd hh24:mi:ss') where service_code = ?",
                        rec.getDate("XGSJ"), strServiceCode);
            }

            ret = String.format("同步涂装计划【%s】成功", plan_date);
            Log.Write(strServiceCode, LogLevel.Information, ret);
        } else {
            ret = String.format("同步涂装计划【%s】失败", plan_date);
            Log.Write(strServiceCode, LogLevel.Error, ret);
        }

        return ret;
    }
}

package cvmes.cvpm;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.IAtom;
import com.jfinal.plugin.activerecord.Record;
import cvmes.common.AbstractSubServiceThread;
import cvmes.common.Log;
import cvmes.common.LogLevel;

import java.sql.SQLException;
import java.util.List;

public class CvpmTrimPlanD7ModifyInf extends AbstractSubServiceThread {
    private String msg = "";

    @Override
    public void initServiceCode() {
        this.strServiceCode = "CvpmTrimPlanD7ModifyInf";
    }

    @Override
    public String runBll(Record rec_service) throws Exception {
        // 重置最后操作信息
        msg = "";

        // 获取生产管理系统计划修改表数据（状态含义：0=未处理,1=已处理,2=计划未锁定,3=历史数据不处理标识）
        List<Record> list = Db.use("cvpm").find("select * from LQGA.T_MES_JHXG where DEAL_STATUS = '0' order by M_TIME");
        if (list == null || list.size() == 0) {
            return msg;
        }

        // 逐条处理计划修改数据
        for (Record sub : list) {
            // 如果需求产品表车辆不存在，更新状态为2
            Record rec_demand_product = Db.findFirst("select count(1) as cnt from t_plan_demand_product where PRODUCTION_CODE = ? and DEMAND_PRODUCT_TYPE = '0'", sub.getStr("PRODUCTION_CODE"));
            if (rec_demand_product == null) {
                // 需求产品表查询失败
                msg = String.format("需求产品表查询失败，生产编码【%s】", sub.getStr("PRODUCTION_CODE"));
                return msg;
            } else {
                // 需求产品表查询成功
                if (rec_demand_product.getInt("cnt") == 0) {
                    // 需求产品表不存在对应的车辆数据
                    Db.use("cvpm").update("update LQGA.T_MES_JHXG set DEAL_STATUS = '2', DEAL_TIME = sysdate where PASSREC_ID = ?", sub.getInt("PASSREC_ID"));
                    msg = String.format("需求产品表不存在生产编码【%s】", sub.getStr("PRODUCTION_CODE"));
                    return msg;
                }

                // 排产计划明细是否有对应的总装计划
                boolean flag_scheduling_d;
                Record rec_plan = Db.findFirst("SELECT t1.PRODUCTION_CODE, t1.SCHEDULING_PLAN_CODE, t1.LINE_CODE, t2.SCHEDULING_PLAN_DATE FROM T_PLAN_SCHEDULING_D t1 LEFT JOIN T_PLAN_SCHEDULING t2 ON t1.SCHEDULING_PLAN_CODE = t2.SCHEDULING_PLAN_CODE WHERE t1.LINE_CODE IN ('zz0101', 'zz0102') and t1.PRODUCTION_CODE = ?", sub.getStr("PRODUCTION_CODE"));
                if (rec_plan == null) {
                    // 不存在总装计划
                    flag_scheduling_d = false;
                } else {
                    // 存在总装计划
                    flag_scheduling_d = true;
                }

                // 排产计划明细是否有对应的涂装计划
                boolean flag_paint;
                Record rec_paint = Db.findFirst("SELECT t1.PRODUCTION_CODE, t1.SCHEDULING_PLAN_CODE, t1.LINE_CODE, t2.SCHEDULING_PLAN_DATE FROM T_PLAN_SCHEDULING_D t1 LEFT JOIN T_PLAN_SCHEDULING t2 ON t1.SCHEDULING_PLAN_CODE = t2.SCHEDULING_PLAN_CODE WHERE t1.LINE_CODE IN ('tz0101') and t1.PRODUCTION_CODE = ?", sub.getStr("PRODUCTION_CODE"));
                if (rec_paint == null) {
                    // 不存在涂装计划
                    flag_paint = false;
                } else {
                    // 存在涂装计划
                    flag_paint = true;
                }

                boolean ret = Db.tx(new IAtom() {
                    @Override
                    public boolean run() throws SQLException {
                        boolean flag_paint_upd = false;

                        // 需求产品表存在对应的车辆信息
                        switch (Integer.parseInt(sub.getStr("ACTUAL_POINT"))) {
                            case 0: //取消计划
                                // 删除排产计划明细
                                Db.update("delete from t_plan_scheduling_d where production_code = ? and line_code in ('zz0101', 'zz0102')", sub.getStr("PRODUCTION_CODE"));
                                // 更新需求产品表取消标记
                                Db.update("update t_plan_demand_product set k_is_plan_cancel = '1' where production_code = ? and DEMAND_PRODUCT_TYPE = '0'", sub.getStr("PRODUCTION_CODE"));
                                break;
                            case 1: //发动机
                                Db.update("update t_plan_demand_product set K_ENGINE=? where production_code = ? and DEMAND_PRODUCT_TYPE = '0'", sub.getStr("LJBH_NEW"), sub.getStr("PRODUCTION_CODE"));
                                break;
                            case 2: //变速箱
                                Db.update("update t_plan_demand_product set K_GEARBOX=? where production_code = ? and DEMAND_PRODUCT_TYPE = '0'", sub.getStr("LJBH_NEW"), sub.getStr("PRODUCTION_CODE"));
                                break;
                            case 3: //车架
                                Db.update("update t_plan_demand_product set K_CARRIAGE=? where production_code = ? and DEMAND_PRODUCT_TYPE = '0'", sub.getStr("LJBH_NEW"), sub.getStr("PRODUCTION_CODE"));
                                break;
                            case 4: //车身总成
                                Db.update("update t_plan_demand_product set K_CARTYPE=? where production_code = ? and DEMAND_PRODUCT_TYPE = '0'", sub.getStr("LJBH_NEW"), sub.getStr("PRODUCTION_CODE"));
                                break;
                            case 5: //颜色
                                Db.update("update t_plan_demand_product set K_COLOR_NAME=? where production_code = ? and DEMAND_PRODUCT_TYPE = '0'", sub.getStr("LJBH_NEW"), sub.getStr("PRODUCTION_CODE"));
                                flag_paint_upd = true;
                                break;
                            case 6: //车身白件
                                Db.update("update t_plan_demand_product set K_CARBODY_CODE=? where production_code = ? and DEMAND_PRODUCT_TYPE = '0'", sub.getStr("LJBH_NEW"), sub.getStr("PRODUCTION_CODE"));
                                flag_paint_upd = true;
                                break;
                            case 7: //车身白件钢码
                                Db.update("update t_plan_demand_product set K_STAMP_ID=? where production_code = ? and DEMAND_PRODUCT_TYPE = '0'", sub.getStr("LJBH_NEW"), sub.getStr("PRODUCTION_CODE"));
                                flag_paint_upd = true;
                                break;
                            case 8: //前桥一轴
                                Db.update("update t_plan_demand_product set K_FRONT_AXLE_ONE=? where production_code = ? and DEMAND_PRODUCT_TYPE = '0'", sub.getStr("LJBH_NEW"), sub.getStr("PRODUCTION_CODE"));
                                break;
                            case 9: //前桥二轴
                                Db.update("update t_plan_demand_product set K_FRONT_AXLE_TWO=? where production_code = ? and DEMAND_PRODUCT_TYPE = '0'", sub.getStr("LJBH_NEW"), sub.getStr("PRODUCTION_CODE"));
                                break;
                            case 10: //后桥
                                Db.update("update t_plan_demand_product set K_REARAXLE=? where production_code = ? and DEMAND_PRODUCT_TYPE = '0'", sub.getStr("LJBH_NEW"), sub.getStr("PRODUCTION_CODE"));
                                break;
                            case 11: //中桥
                                Db.update("update t_plan_demand_product set K_MID_AXLE=? where production_code = ? and DEMAND_PRODUCT_TYPE = '0'", sub.getStr("LJBH_NEW"), sub.getStr("PRODUCTION_CODE"));
                                break;
                            case 12: //浮桥
                                Db.update("update t_plan_demand_product set K_FLOAT_AXLE=? where production_code = ? and DEMAND_PRODUCT_TYPE = '0'", sub.getStr("LJBH_NEW"), sub.getStr("PRODUCTION_CODE"));
                                break;
                            case 13: //悬挂
                                Db.update("update t_plan_demand_product set K_BALANCE_SUSPENSION=? where production_code = ? and DEMAND_PRODUCT_TYPE = '0'", sub.getStr("LJBH_NEW"), sub.getStr("PRODUCTION_CODE"));
                                break;
                            case 14: //悬置
                                Db.update("update t_plan_demand_product set K_SUSPENSION=? where production_code = ? and DEMAND_PRODUCT_TYPE = '0'", sub.getStr("LJBH_NEW"), sub.getStr("PRODUCTION_CODE"));
                                break;
                            case 15: //轮胎
                                Db.update("update t_plan_demand_product set K_TYRE=? where production_code = ? and DEMAND_PRODUCT_TYPE = '0'", sub.getStr("LJBH_NEW"), sub.getStr("PRODUCTION_CODE"));
                                break;
                            case 16: //电动机
                                Db.update("update t_plan_demand_product set K_ELECTROMOTOR=? where production_code = ? and DEMAND_PRODUCT_TYPE = '0'", sub.getStr("LJBH_NEW"), sub.getStr("PRODUCTION_CODE"));
                                break;
                            case 17: //电池包图号
                                Db.update("update t_plan_demand_product set k_batter_part=? where production_code = ? and DEMAND_PRODUCT_TYPE = '0'", sub.getStr("LJBH_NEW"), sub.getStr("PRODUCTION_CODE"));
                                break;
                            case 18: //整车控制器图号
                                Db.update("update t_plan_demand_product set k_controller_part=? where production_code = ? and DEMAND_PRODUCT_TYPE = '0'", sub.getStr("LJBH_NEW"), sub.getStr("PRODUCTION_CODE"));
                                break;
                            case 19: //切换项目
                                Db.update("update t_plan_demand_product set k_switching_project=? where production_code = ? and DEMAND_PRODUCT_TYPE = '0'", sub.getStr("LJBH_NEW"), sub.getStr("PRODUCTION_CODE"));
                                break;
                            case 20: //试装项目
                                Db.update("update t_plan_demand_product set k_trial_assembly=? where production_code = ? and DEMAND_PRODUCT_TYPE = '0'", sub.getStr("LJBH_NEW"), sub.getStr("PRODUCTION_CODE"));
                                break;
                        }

                        // 有对应的总装排产计划，则插入生产指令
                        if (flag_scheduling_d) {
                            //存在总装计划 更新计划明细表 是否生成目视卡为未生成
                            Db.update("update t_plan_scheduling_d set INSRTC_STATUS = '0'  where PRODUCTION_CODE = ?  and LINE_CODE in ('zz0101','zz0102') ", sub.getStr("PRODUCTION_CODE"));


                            List<Record> list_indication_point = Db.find("select indication_point_code from T_MODEL_INDICATION_POINT where INDICATION_POINT_TRIGGER='0' and trigger_plan_type='0'");
                            if (list_indication_point == null || list_indication_point.size() == 0) {
                            } else {
                                for (Record sub_indication_point : list_indication_point) {
                                    // 查询是否存在未处理指令
                                    StringBuffer sql_cmd = new StringBuffer();
                                    sql_cmd.append("select count(1) as cnt from T_CMD_PRODUCTION_ORDER");
                                    sql_cmd.append(" where INDICATION_POINT_CODE = ?");
                                    sql_cmd.append(" and PRODUCTION_ORDER_STATUS = '0'");
                                    sql_cmd.append(" and to_char(PLAN_DATE, 'yyyy-mm-dd') = to_char(?, 'yyyy-mm-dd')");
                                    sql_cmd.append(" and PLAN_LINE_CODE = ?");

                                    Record rec_cmd = Db.findFirst(sql_cmd.toString(),
                                            sub_indication_point.getStr("INDICATION_POINT_CODE"),
                                            rec_plan.getDate("SCHEDULING_PLAN_DATE"),
                                            rec_plan.getStr("LINE_CODE"));

                                    if (rec_cmd == null || rec_cmd.getInt("cnt") == 0) {
                                        // 不存在未处理指令，则插入
                                        StringBuffer sql = new StringBuffer();
                                        sql.append("insert into T_CMD_PRODUCTION_ORDER(ID, INDICATION_POINT_CODE, WORK_DAY, CREATE_TIME,");
                                        sql.append(" PRODUCTION_ORDER_STATUS, WORKSHOP_CODE, PLAN_DATE, PLAN_LINE_CODE, PLAN_NO)");
                                        sql.append(" values(");
                                        sql.append("sys_guid(), ?, to_date(func_get_workday(), 'yyyy-mm-dd'), sysdate,");
                                        sql.append(" '0', 'zz01', ?, ?, ?)");

                                        Db.update(sql.toString(),
                                                sub_indication_point.getStr("indication_point_code"),
                                                rec_plan.getDate("SCHEDULING_PLAN_DATE"),
                                                rec_plan.getStr("LINE_CODE"),
                                                rec_plan.getStr("SCHEDULING_PLAN_CODE"));
                                    }
                                }
                            }
                        }

                        // 有对应的涂装计划,且需要更新
                        if (flag_paint && flag_paint_upd) {
                            List<Record> list_indication_point = Db.find("select indication_point_code from T_MODEL_INDICATION_POINT where INDICATION_POINT_TRIGGER='0' and trigger_plan_type='1'");
                            if (list_indication_point == null || list_indication_point.size() == 0) {
                            } else {
                                for (Record sub_indication_point : list_indication_point) {
                                    // 查询是否存在未处理指令
                                    StringBuffer sql_cmd = new StringBuffer();
                                    sql_cmd.append("select count(1) as cnt from T_CMD_PRODUCTION_ORDER");
                                    sql_cmd.append(" where INDICATION_POINT_CODE = ?");
                                    sql_cmd.append(" and PRODUCTION_ORDER_STATUS = '0'");
                                    sql_cmd.append(" and to_char(PLAN_DATE, 'yyyy-mm-dd') = to_char(?, 'yyyy-mm-dd')");
                                    sql_cmd.append(" and PLAN_LINE_CODE = ?");

                                    Record rec_cmd = Db.findFirst(sql_cmd.toString(),
                                            sub_indication_point.getStr("INDICATION_POINT_CODE"),
                                            rec_paint.getDate("SCHEDULING_PLAN_DATE"),
                                            rec_paint.getStr("LINE_CODE"));

                                    if (rec_cmd == null || rec_cmd.getInt("cnt") == 0) {
                                        // 不存在未处理指令，则插入
                                        StringBuffer sql = new StringBuffer();
                                        sql.append("insert into T_CMD_PRODUCTION_ORDER(ID, INDICATION_POINT_CODE, WORK_DAY, CREATE_TIME,");
                                        sql.append(" PRODUCTION_ORDER_STATUS, WORKSHOP_CODE, PLAN_DATE, PLAN_LINE_CODE, PLAN_NO)");
                                        sql.append(" values(");
                                        sql.append("sys_guid(), ?, to_date(func_get_workday(), 'yyyy-mm-dd'), sysdate,");
                                        sql.append(" '0', 'tz01', ?, ?, ?)");

                                        Db.update(sql.toString(),
                                                sub_indication_point.getStr("indication_point_code"),
                                                rec_paint.getDate("SCHEDULING_PLAN_DATE"),
                                                rec_paint.getStr("LINE_CODE"),
                                                rec_paint.getStr("SCHEDULING_PLAN_CODE"));
                                    }
                                }
                            }
                        }

                        // 提交事务
                        return true;
                    }
                });

                if (ret) {
                    Db.use("cvpm").update("update LQGA.T_MES_JHXG set DEAL_STATUS = '1', DEAL_TIME = sysdate where PASSREC_ID = ?", sub.getInt("PASSREC_ID"));
                    msg = String.format("修改计划成功，修改ID【%d】，生产编码【%s】", sub.getInt("PASSREC_ID"), sub.getStr("PRODUCTION_CODE"));
                    Log.Write(strServiceCode, LogLevel.Information, msg);
                }
            }
        }

        return msg;
    }
}

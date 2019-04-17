package cvmes.cvpm;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.IAtom;
import com.jfinal.plugin.activerecord.Record;
import cvmes.common.AbstractSubServiceThread;
import cvmes.common.Log;
import cvmes.common.LogLevel;

import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;

public class CvpmTrimPlanD3Inf extends AbstractSubServiceThread {
    private String msg = "";
    private String dmsg = "";

    @Override
    public void initServiceCode() {
        this.strServiceCode = "CvpmTrimPlanD3Inf";
    }

    @Override
    public String runBll(Record rec_service) throws Exception {
        // 重置最后操作信息
        msg = "";

        // 获取变更的锁定D+3计划清单
        StringBuffer sql = new StringBuffer();
        sql.append("select TO_CHAR(RQ, 'yyyy-mm-dd') AS RQ, ZPX, SHSJ,");
        sql.append(" ('ZZ'||TO_CHAR(RQ, 'yyyymmdd')) AS SCHEDULING_PLAN_CODE, ('zz010'||SUBSTR(zpx,0,1)) AS LINE_CODE");
        sql.append(" from v_scgl_d3_rq");
        sql.append(" where shf = 'Y'");
        sql.append(" and to_char(shsj, 'yyyy-mm-dd hh24:mi:ss') > ?");
        sql.append(" and ZPX in ('1线', '2线')");
        sql.append(" order by SHSJ");
        List<Record> list = Db.use("cvpm").find(sql.toString(), rec_service.getStr("SERVICE_PARA1_VALUE"));
        if (list == null || list.size() == 0) {
            return msg;
        }

        for (Record sub : list) {
            // 检查计划主表数据是否已存在，不存在则插入
            Record rec_d3 = Db.findFirst("select count(1) as cnt from T_PLAN_SCHEDULING where SCHEDULING_PLAN_CODE = ?", sub.getStr("SCHEDULING_PLAN_CODE"));
            if (rec_d3 == null) {
                msg = String.format("获取计划主表【%s】是否已存在失败", sub.getStr("SCHEDULING_PLAN_CODE"));
                Log.Write(strServiceCode, LogLevel.Error, msg);
                return msg;
            } else {
                if (rec_d3.getInt("cnt") == 0) {
                    StringBuffer sql_d3m = new StringBuffer();
                    sql_d3m.append("insert into T_PLAN_SCHEDULING(ID, SCHEDULING_PLAN_CODE, WORKSHOP_CODE,");
                    sql_d3m.append(" SCHEDULING_PLAN_DATE, SCHEDULING_PLAN_STATUS, AVAILABLE_STATUS, SCHEDULING_PLAN_TYPE)");
                    sql_d3m.append(" values(sys_guid(), ?, 'zz01', to_date(?, 'yyyy-mm-dd'), '3', '1', '0')");
                    Db.update(sql_d3m.toString(), sub.getStr("SCHEDULING_PLAN_CODE"), sub.getStr("RQ"));
                }
            }

            // 重新同步d+7计划
            DateFormat d = new SimpleDateFormat("yyyy-MM-dd");
            Calendar c = Calendar.getInstance();
            c.setTime(d.parse(sub.getStr("RQ")));
            c.set(Calendar.DATE, c.get(Calendar.DATE) - 1);
            String redate = d.format(c.getTime());
            Db.update("update T_SYS_SERVICE set SERVICE_PARA1_VALUE = ? where SERVICE_CODE = 'CvpmTrimPlanD7Inf'", redate);
            // 休眠30秒以等待d+7重新同步完成
            Thread.sleep(30000);

            // 获取指定日期、指定生产线的D+3计划
            StringBuffer sql_d3 = new StringBuffer();
            sql_d3.append("SELECT t3.SCBM, t2.XH, t2.CPDM, t2.SL, t2.BZ, t2.ZPSX2, t2.CSYS, t2.JHLB, t3.ZPSX, t3.SXSJ, t1.ZBBH,");
            sql_d3.append(" (CASE WHEN T2.ZPX = '1线' THEN 1 WHEN T2.ZPX = '2线' THEN 2 WHEN T2.ZPX = '3线' THEN 3 END) AS K_ASSEMBLY_LINE, T2.ZPX AS K_SOURCE_ASSEMBLY_LINE ");
            sql_d3.append(" FROM LQGA.LQSCDJPCLYJSX t3");
            sql_d3.append(" LEFT JOIN V_SCGL_YSJH t1 ON t1.SCBM = t3.SCBM");
            sql_d3.append(" LEFT JOIN V_SCGL_YSJH_D3 t2 ON t2.XH = t3.XH");
            sql_d3.append(" WHERE TO_CHAR(t2.RQ, 'yyyy-mm-dd') = ?");
            sql_d3.append(" AND t2.ZPX = ?");
            sql_d3.append(" ORDER BY t3.ZPSX");
            List<Record> list_d3 = Db.use("cvpm").find(sql_d3.toString(), sub.getStr("RQ"), sub.getStr("ZPX"));
            if (list_d3 == null || list_d3.size() == 0) {
                msg = String.format("获取日期【%s】，生产线【%s】D+3计划失败或者无数据", sub.getStr("RQ"), sub.getStr("ZPX"));
                Log.Write(strServiceCode, LogLevel.Error, msg);
                return msg;
            }

            // 更新需求产品表信息
            for (Record sub_d7 : list_d3) {
                // 检查钢码号是否存在多个
                Record rec_d7 = Db.findFirst("select count(1) as cnt from t_plan_demand_product where K_STAMP_ID = ?", sub_d7.getStr("ZBBH"));
                if (rec_d7.getInt("cnt") > 1) {
                    Db.update("update t_plan_demand_product set K_STAMP_ID = 'QX' || K_STAMP_ID where K_STAMP_ID = ? and PRODUCTION_CODE != ?",
                            sub_d7.getStr("ZBBH"),
                            sub_d7.getStr("SCBM"));
                }

                StringBuffer sql_d7 = new StringBuffer();
                sql_d7.append("update t_plan_demand_product set");
                sql_d7.append(" BOM_CODE = ?,"); //BOM编码
                sql_d7.append(" DEMAND_PRODUCT_CODE = ?,"); //需求产品编码（车型代码）
                sql_d7.append(" K_D3_BATCH_NUM = ?,"); //D+3批次数量
                sql_d7.append(" DEMAND_PRODUCT_REMARK = ?,"); //备注
                sql_d7.append(" D3X = ?,"); //D+3批次序
                sql_d7.append(" K_COLOR_NAME = ?,"); //车身颜色
                sql_d7.append(" K_PLAN_TYPE = ?,"); //计划类别
                sql_d7.append(" K_ASSEMBLY_LINE = ?,"); //装配线
                sql_d7.append(" K_SOURCE_ASSEMBLY_LINE = ?"); //原始装配线
                sql_d7.append(" where PRODUCTION_CODE = ? and DEMAND_PRODUCT_TYPE = '0'"); //根据生产编码进行更新

                Db.update(sql_d7.toString(),
                        sub_d7.getStr("XH"), //BOM编码
                        sub_d7.getStr("CPDM"), //需求产品编码（车型代码）
                        sub_d7.getStr("SL"), //D+3批次数量
                        sub_d7.getStr("BZ"), //备注
                        sub_d7.getStr("ZPSX2"), //D+3批次序
                        sub_d7.getStr("CSYS"), //车身颜色
                        sub_d7.getStr("JHLB"), //计划类别
                        sub_d7.getStr("K_ASSEMBLY_LINE"), //装配线
                        sub_d7.getStr("K_SOURCE_ASSEMBLY_LINE"), //原始装配线
                        sub_d7.getStr("SCBM") //生产编码
                );
            }

            // 更新d+3计划
            boolean ret = Db.tx(new IAtom() {
                @Override
                public boolean run() throws SQLException {
                    // 删除计划明细表数据
                    Db.update("delete from T_PLAN_SCHEDULING_D where SCHEDULING_PLAN_CODE = ? and LINE_CODE = ?",
                            sub.getStr("SCHEDULING_PLAN_CODE"),
                            sub.getStr("LINE_CODE"));

                    // 插入明细表数据
                    for (Record sub_d3 : list_d3) {
                        StringBuffer sql_sub_d3 = new StringBuffer();
                        sql_sub_d3.append("insert into T_PLAN_SCHEDULING_D(ID, LINE_CODE, SEQ_NO, PRODUCTION_CODE, PRODUCT_CODE,");
                        sql_sub_d3.append(" PRODUCTION_NUM, PLAN_ONLINE_TIME, SCHEDULING_PLAN_CODE, AVAILABLE_STATUS, INSRTC_STATUS)");
                        sql_sub_d3.append(" values(sys_guid(), ?, ?, ?, ?, '1', ?, ?, '1', '0')");
                        Db.update(sql_sub_d3.toString(),
                                sub.getStr("LINE_CODE"),
                                sub_d3.getInt("ZPSX"),
                                sub_d3.getStr("SCBM"),
                                sub_d3.getStr("CPDM"),
                                sub_d3.getDate("SXSJ"),
                                sub.getStr("SCHEDULING_PLAN_CODE"));
                    }

                    // 发送生产指令
                    List<Record> list_indication_point = Db.find("select indication_point_code from T_MODEL_INDICATION_POINT where INDICATION_POINT_TRIGGER='0' and trigger_plan_type='0'");
                    if (list_indication_point == null || list.size() == 0) {
                    } else {
                        for (Record sub_indication_point : list_indication_point) {
                            // 查询是否存在未处理指令
                            Record rec_cmd = Db.findFirst("select count(1) as cnt from T_CMD_PRODUCTION_ORDER where INDICATION_POINT_CODE = ? " +
                                            " and PRODUCTION_ORDER_STATUS = '0' and to_char(WORK_DAY,'yyyy-mm-dd') = func_get_workday " +
                                            "and WORKSHOP_CODE = 'zz01' and to_char(PLAN_DATE ,'yyyy-mm-dd') = ? and PLAN_LINE_CODE= ? ",
                                    sub_indication_point.getStr("INDICATION_POINT_CODE"),
                                    sub.getStr("RQ"),
                                    sub.getStr("LINE_CODE"));
                            if (rec_cmd == null || rec_cmd.getInt("cnt") == 0) {
                                // 不存在未处理指令，则插入
                                StringBuffer sql_cmd = new StringBuffer();
                                sql_cmd.append("insert into T_CMD_PRODUCTION_ORDER(ID, INDICATION_POINT_CODE, WORK_DAY, CREATE_TIME,");
                                sql_cmd.append(" PRODUCTION_ORDER_STATUS, WORKSHOP_CODE, PLAN_DATE, PLAN_LINE_CODE, PLAN_NO)");
                                sql_cmd.append(" values(");
                                sql_cmd.append("sys_guid(), ?, to_date(func_get_workday(), 'yyyy-mm-dd'), sysdate,");
                                sql_cmd.append(" '0', 'zz01', to_date(?, 'yyyy-mm-dd'), ?, ?)");

                                Db.update(sql_cmd.toString(),
                                        sub_indication_point.getStr("indication_point_code"),
                                        sub.getStr("RQ"),
                                        sub.getStr("LINE_CODE"),
                                        sub.getStr("SCHEDULING_PLAN_CODE"));
                            }
                        }
                    }

                    // 计划同步成功
                    return true;
                }
            });

            // 计划同步成功
            if (ret) {
                Db.update("update T_SYS_SERVICE set SERVICE_PARA1_VALUE = to_char(?, 'yyyy-mm-dd hh24:mi:ss') where SERVICE_CODE = ?",
                        sub.getDate("SHSJ"),
                        strServiceCode);

                msg = String.format("总装D+3计划同步成功，日期【%s】，生产线【%s】", sub.getStr("RQ"), sub.getStr("ZPX"));

                for (Record sub_d7 : list_d3) {
                    dmsg = String.format("总装D+3计划同步成功，日期【%s】，生产线【%s】，生产编码【%s】，BOM编码【%s】，需求产品编码（车型代码）【%s】，D+3批次数量【%s】，备注【%s】" +
                                    "，D+3批次序【%s】，车身颜色【%s】，计划类别【%s】，装配线【%s】，原始装配线【%s】", sub.getStr("RQ"), sub.getStr("ZPX"), sub_d7.getStr("SCBM"),
                            sub_d7.getStr("XH"), sub_d7.getStr("CPDM"), sub_d7.getStr("SL"), sub_d7.getStr("BZ"), sub_d7.getStr("ZPSX2"),
                            sub_d7.getStr("CSYS"), sub_d7.getStr("JHLB"), sub_d7.getStr("K_ASSEMBLY_LINE"), sub_d7.getStr("K_SOURCE_ASSEMBLY_LINE"));
                    Log.Write(strServiceCode, LogLevel.Information, dmsg);
                }
            } else {
                msg = String.format("总装D+3计划同步失败，日期【%s】，生产线【%s】", sub.getStr("RQ"), sub.getStr("ZPX"));
                Log.Write(strServiceCode, LogLevel.Error, msg);
            }
        }

        return msg;
    }
}

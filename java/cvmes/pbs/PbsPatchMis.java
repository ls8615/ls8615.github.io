package cvmes.pbs;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.IAtom;
import com.jfinal.plugin.activerecord.ICallback;
import com.jfinal.plugin.activerecord.Record;
import cvmes.common.AbstractSubServiceThread;
import cvmes.common.Log;
import cvmes.common.LogLevel;
import oracle.jdbc.OracleTypes;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class PbsPatchMis extends AbstractSubServiceThread {
    private String msg = "";

    @Override
    public void initServiceCode() {
        this.strServiceCode = "PbsPatchMis";
    }

    @Override
    public String runBll(Record rec_service) throws Exception {
        msg = "";

        // 获取是否有待补指令
        StringBuffer sql = new StringBuffer();
        sql.append("SELECT t1.ID, t1.PRODUCTION_CODE, t1.MIS_TIME, t2.K_ASSEMBLY_LINE,");
        sql.append(" FUNC_GETNOSYS_WORKDAY(t1.MIS_TIME) AS WORK_DATE, ('zz_mis' || t2.K_ASSEMBLY_LINE) AS ACTUAL_POINT_CODE,");
        sql.append(" ('zz010' || t2.K_ASSEMBLY_LINE) AS LINE_CODE, t2.DEMAND_PRODUCT_CODE AS PRODUCT_CODE");
        sql.append(" FROM T_CMD_PATCH_MIS t1");
        sql.append(" LEFT JOIN T_PLAN_DEMAND_PRODUCT t2 ON t1.PRODUCTION_CODE = t2.PRODUCTION_CODE");
        sql.append(" WHERE t1.DEAL_STATUS = 0");
        sql.append(" ORDER BY t1.MIS_TIME");
        List<Record> list = Db.find(sql.toString());
        if (list == null || list.size() == 0) {
            return msg;
        }

        for (Record sub : list) {
            Db.tx(new IAtom() {
                @Override
                public boolean run() throws SQLException {
                    String uuid = java.util.UUID.randomUUID().toString();

                    // 插入MIS过点记录
                    StringBuffer sql_pass = new StringBuffer();
                    sql_pass.append("insert into T_ACTUAL_PASSED_RECORD(ID, ACTUAL_POINT_CODE, PRODUCTION_CODE,");
                    sql_pass.append(" LINE_CODE, WORK_DATE, WORK_SHIFT, PRODUCT_CODE, PASSED_RECORD_TYPE, PASSED_TIME)");
                    sql_pass.append(" values(");
                    sql_pass.append("?, ?, ?, ?, to_date(?, 'yyyy-mm-dd'), '1', ?, '2', ?)");
                    Db.update(sql_pass.toString(),
                            uuid,
                            sub.getStr("ACTUAL_POINT_CODE"),
                            sub.getStr("PRODUCTION_CODE"),
                            sub.getStr("LINE_CODE"),
                            sub.getStr("WORK_DATE"),
                            sub.getStr("PRODUCT_CODE"),
                            sub.getDate("MIS_TIME"));

                    // 移车
                    Record rec_actual_point = Db.findById("T_MODEL_ACTUAL_POINT", "ACTUAL_POINT_CODE", sub.getStr("ACTUAL_POINT_CODE"));
                    if (rec_actual_point.getStr("TARGET_ZONE_CODE") == null) {
                    } else {
                        boolean flag = (boolean) Db.execute(new ICallback() {
                            @Override
                            public Object call(Connection conn) throws SQLException {
                                CallableStatement proc = conn.prepareCall("{CALL PROC_MOVE_CAR(?,?,?,?)}");
                                proc.setString("PARA_PRODUCTION_CODE", sub.getStr("PRODUCTION_CODE"));
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
                        Db.update("insert into T_CMD_PRODUCTION_ORDER(ID,INDICATION_POINT_CODE,WORK_DAY,PRODUCTION_CODE,CREATE_TIME,PRODUCTION_ORDER_STATUS,WORKSHOP_CODE,PASSED_ID) values(?,?,to_date(?,'yyyy-mm-dd'),?,sysdate,'0','zz01',?)",
                                java.util.UUID.randomUUID().toString(),
                                rec_indicate.getStr("INDICATION_POINT_CODE"),
                                sub.getStr("WORK_DATE"),
                                sub.getStr("PRODUCTION_CODE"),
                                uuid);
                    }

                    // 更新处理状态
                    Db.update("update t_cmd_patch_mis set deal_time = sysdate, deal_status = 1 where id = ?", sub.getStr("ID"));

                    // 处理完毕
                    msg = String.format("补MIS点指令处理完毕，ID【%s】，生产编码【%s】，生产线【%d】，MIS过点时间【%s】",
                            sub.getStr("ID"),
                            sub.getStr("PRODUCTION_CODE"),
                            sub.getInt("K_ASSEMBLY_LINE"),
                            sub.getDate("MIS_TIME"));
                    Log.Write(strServiceCode, LogLevel.Information, msg);
                    return true;
                }
            });
        }

        return msg;
    }
}

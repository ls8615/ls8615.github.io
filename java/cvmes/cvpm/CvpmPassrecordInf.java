package cvmes.cvpm;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;
import cvmes.common.AbstractSubServiceThread;
import cvmes.common.Log;
import cvmes.common.LogLevel;

import java.util.List;

public class CvpmPassrecordInf extends AbstractSubServiceThread {
    private String msg = "";

    @Override
    public void initServiceCode() {
        this.strServiceCode = "CvpmPassrecordInf";
    }

    @Override
    public String runBll(Record rec_service) throws Exception {
        // 重置最后操作信息
        msg = "";

        // 获取待发送数据（一次处理500条以内）
        List<Record> list = Db.find("SELECT t1.ID, SEQ_CVPM_PASSREC_ID.NEXTVAL AS PASSREC_ID, t1.PRODUCTION_CODE, t2.CVPM_ALIAS_NAME AS ACTUAL_POINT," +
                " t3.K_CARRIAGE_NO AS CARRIAGE_NO, t3.K_CHASSIS_NO AS CHASSIS_NO, t3.K_ENGINE_NO AS ENGINE_NO," +
                " t1.PASSED_TIME AS PASS_TIME, t3.K_ELECTROMOTOR_NO AS MOTOR_NO, t1.DOWNLINE_TYPE AS OFFSTATUS" +
                " FROM T_ACTUAL_PASSED_RECORD t1" +
                " LEFT JOIN T_MODEL_ACTUAL_POINT t2 ON t1.ACTUAL_POINT_CODE=t2.ACTUAL_POINT_CODE" +
                " LEFT JOIN T_PLAN_DEMAND_PRODUCT t3 ON t1.PRODUCTION_CODE=t3.PRODUCTION_CODE" +
                " WHERE t1.IS_SEND_CVPM=0" +
                " AND t2.IS_SEND_CVPM=1" +
                " AND t3.DEMAND_PRODUCT_TYPE=0" +
                " AND ROWNUM<=500");

        for (Record sub : list) {
            // 写入生产管理系统接口表
            int ret = Db.use("cvpm").update("insert into lqga.T_MES_1(PASSREC_ID, PRODUCTION_CODE, ACTUAL_POINT, CARRIAGE_NO, CHASSIS_NO, ENGINE_NO, PASS_TIME, DEAL_STATUS, DEAL_TIME, MOTOR_NO, OFFSTATUS) values(?, ?, ?, ?, ?, ?, ?, 0, SYSDATE, ?, ?)",
                    sub.getLong("PASSREC_ID"),
                    sub.getStr("PRODUCTION_CODE"),
                    sub.getInt("ACTUAL_POINT"),
                    sub.getStr("CARRIAGE_NO"),
                    sub.getStr("CHASSIS_NO"),
                    sub.getStr("ENGINE_NO"),
                    sub.getDate("PASS_TIME"),
                    sub.getStr("MOTOR_NO"),
                    sub.getStr("OFFSTATUS"));

            // 更新发送状态
            if (ret >= 1) {
                Db.update("update T_ACTUAL_PASSED_RECORD set IS_SEND_CVPM=1 where ID=?", sub.getStr("ID"));
                msg = String.format("发送数据成功，过点记录ID【%s】，生产编码【%s】", sub.getStr("ID"), sub.getStr("PRODUCTION_CODE"));
            } else {
                msg = String.format("发送数据失败，过点记录ID【%s】，生产编码【%s】", sub.getStr("ID"), sub.getStr("PRODUCTION_CODE"));
                Log.Write(strServiceCode, LogLevel.Error, msg);
            }
        }

        return msg;
    }
}

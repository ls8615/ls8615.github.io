package cvmes.zzsps;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;
import cvmes.common.AbstractSubServiceThread;
import cvmes.common.Log;
import cvmes.common.LogLevel;

public class ZzspsPlanDataEdit extends AbstractSubServiceThread {
    @Override
    public void initServiceCode() {
        this.strServiceCode = "ZzspsPlanDataEdit";
    }

    @Override
    public String runBll(Record rec_service) throws Exception {
        String msg = "";

        // 获取待处理的生产指令
        Record rec = Db.findFirst("select * from T_CMD_PRODUCTION_ORDER where PRODUCTION_ORDER_STATUS = '0' and INDICATION_POINT_CODE = ?",
                rec_service.getStr("SERVICE_PARA1_VALUE"));
        if (rec == null) {
            return msg;
        }

        // 插入数据到接口表
        StringBuffer sql = new StringBuffer();
        sql.append("INSERT INTO T_INF_TO_SPS_CARPLAN(ID, PRODUCTION_CODE, ASSEMBLY_LINE, STEEL_CODE,");
        sql.append(" CAR_TYPE, CAR_TYPE_CODE,");
        sql.append(" ASSEMBLY_SEQUENCE, COLOR_NAME, REMARK,");
        sql.append(" PLAN_DATE, DEAL_STATUS, DEAL_TIME, BOM_CODE,");
        sql.append(" K_CARRIAGE, DAY3_ORDER, K_CARTYPE, PLAN_TYPE)");
        sql.append(" SELECT SYS_GUID() AS ID, t1.PRODUCTION_CODE AS PRODUCTION_CODE, t2.K_ASSEMBLY_LINE AS ASSEMBLY_LINE, t2.K_STAMP_ID AS STEEL_CODE,");
        sql.append(" t2.K_NOTICE_CARTYPE AS CAR_TYPE, t2.DEMAND_PRODUCT_CODE AS CAR_TYPE_CODE,");
        sql.append(" t1.SEQ_NO AS ASSEMBLY_SEQUENCE, t2.K_COLOR_NAME AS COLOR_NAME, t2.DEMAND_PRODUCT_REMARK AS REMARK,");
        sql.append(" t3.SCHEDULING_PLAN_DATE AS PLAN_DATE, '0' AS DEAL_STATUS, SYSDATE AS DEAL_TIME, t2.BOM_CODE AS BOM_CODE,");
        sql.append(" t2.K_CARRIAGE AS K_CARRIAGE, t2.D3X AS DAY3_ORDER, t2.K_CARTYPE AS K_CARTYPE, t2.K_PLAN_TYPE AS PLAN_TYPE");
        sql.append(" FROM T_PLAN_SCHEDULING_D t1");
        sql.append(" LEFT JOIN T_PLAN_DEMAND_PRODUCT t2 ON t1.PRODUCTION_CODE = t2.PRODUCTION_CODE");
        sql.append(" LEFT JOIN T_PLAN_SCHEDULING t3 ON t1.SCHEDULING_PLAN_CODE = t3.SCHEDULING_PLAN_CODE");
        sql.append(" WHERE t1.SCHEDULING_PLAN_CODE = ?");
        sql.append(" AND t1.LINE_CODE = ?");

        Db.update(sql.toString(), rec.getStr("PLAN_NO"), rec.getStr("PLAN_LINE_CODE"));

        // 更新指令状态
        Db.update("update T_CMD_PRODUCTION_ORDER set PRODUCTION_ORDER_STATUS = '1' where ID = ?", rec.getStr("ID"));

        // 记录日志并返回最后处理消息
        msg = String.format("处理生产指令成功，ID【%s】，计划编码【%s】", rec.getStr("ID"), rec.getStr("PLAN_NO"));
        Log.Write(strServiceCode, LogLevel.Information, msg);

        return msg;
    }
}

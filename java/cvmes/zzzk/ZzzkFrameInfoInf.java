package cvmes.zzzk;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;
import cvmes.common.AbstractSubServiceThread;
import cvmes.common.Log;
import cvmes.common.LogLevel;

import java.util.List;

public class ZzzkFrameInfoInf extends AbstractSubServiceThread {
    private String msg = "";

    @Override
    public void initServiceCode() {
        this.strServiceCode = "ZzzkFrameInfoInf";
    }

    @Override
    public String runBll(Record rec_service) throws Exception {
        // 重置最后操作信息
        msg = "";

        // 获取待处理指令（一次处理500条以内）
        String sql1 = String.format("select * from T_CMD_PRODUCTION_ORDER where PRODUCTION_ORDER_STATUS='0' and INDICATION_POINT_CODE in (%s) and rownum<=500",
                rec_service.getStr("SERVICE_PARA1_VALUE"));
        List<Record> list = Db.find(sql1);
        if (list == null) return msg;

        // 逐条处理指令
        for (Record sub : list) {
            // 根据过点记录ID获取车辆信息
            String sql2 = String.format("SELECT t1.PRODUCTION_CODE, t2.K_ASSEMBLY_LINE, t2.D3X, t3.SEQ_NO" +
                    " FROM T_ACTUAL_PASSED_RECORD t1" +
                    " LEFT JOIN T_PLAN_DEMAND_PRODUCT t2 ON t1.PRODUCTION_CODE=t2.PRODUCTION_CODE" +
                    " LEFT JOIN T_PLAN_SCHEDULING_D t3 ON t1.PRODUCTION_CODE=t3.PRODUCTION_CODE" +
                    " WHERE t3.LINE_CODE IN (%s)" +
                    " AND t1.ID='%s'", rec_service.getStr("SERVICE_PARA2_VALUE"), sub.getStr("PASSED_ID"));
            Record rec = Db.findFirst(sql2);

            if (rec != null) {
                // 更新总装中控对应记录
                int ret = Db.use("zzzk").update("update TABLE_ORDER set SEQ=?, UP_NUM=?, UPDATE_TIME=getdate() where LINE_CODE=?",
                        rec.getInt("D3X").toString(),
                        rec.getInt("SEQ_NO").toString(),
                        rec.getInt("K_ASSEMBLY_LINE").toString());

                // 设置指令状态为已处理
                if (ret >= 1) {
                    Db.update("update T_CMD_PRODUCTION_ORDER set PRODUCTION_ORDER_STATUS='1' where ID=?", sub.getStr("ID"));
                    msg = String.format("发送数据成功，指令ID【%s】，生产编码【%s】", sub.getStr("ID"), sub.getStr("PRODUCTION_CODE"));
                } else {
                    Db.update("update T_CMD_PRODUCTION_ORDER set PRODUCTION_ORDER_STATUS='2' where ID=?", sub.getStr("ID"));
                    msg = String.format("发送数据失败，指令ID【%s】，生产编码【%s】", sub.getStr("ID"), sub.getStr("PRODUCTION_CODE"));
                    Log.Write(strServiceCode, LogLevel.Error, msg);
                }
            } else {
                Db.update("update T_CMD_PRODUCTION_ORDER set PRODUCTION_ORDER_STATUS='2' where ID=?", sub.getStr("ID"));
                msg = String.format("发送数据失败，指令ID【%s】，生产编码【%s】", sub.getStr("ID"), sub.getStr("PRODUCTION_CODE"));
                Log.Write(strServiceCode, LogLevel.Error, msg);
            }
        }

        return msg;
    }
}

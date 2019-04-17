package cvmes.u9;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;
import cvmes.common.AbstractSubServiceThread;
import cvmes.common.Log;
import cvmes.common.LogLevel;

import java.util.List;

public class U9PassrecordSendDataEdit extends AbstractSubServiceThread {
    private String msg = "";

    @Override
    public void initServiceCode() {
        this.strServiceCode = "U9PassrecordSendDataEdit";
    }

    @Override
    public String runBll(Record rec_service) throws Exception {
        msg = "";

        // 获取待处理指令数据
        List<Record> list = Db.find("select * from T_CMD_PRODUCTION_ORDER where PRODUCTION_ORDER_STATUS=0 and (INDICATION_POINT_CODE=? or INDICATION_POINT_CODE=?)", rec_service.getStr("SERVICE_PARA1_VALUE"), rec_service.getStr("SERVICE_PARA2_VALUE"));

        // 逐条处理指令
        for (Record sub : list) {
            // 获取生产指令对应的车辆数据
            Record rec_car = Db.findFirst("select t2.K_STAMP_ID, t1.PASSED_TIME, t2.DEMAND_PRODUCT_TYPE, t2.IS_PAINT, t2.K_NOTICE_CARTYPE, t2.K_CARTYPE, t2.K_CARBODY_CODE FROM T_ACTUAL_PASSED_RECORD t1 LEFT JOIN T_PLAN_DEMAND_PRODUCT t2 ON t1.PRODUCTION_CODE=t2.PRODUCTION_CODE WHERE t1.ID=?", sub.getStr("PASSED_ID"));
            if (rec_car != null) {
                // 创建准备写入接口表的数据
                // 备注
                String remark = "";
                if (rec_car.getStr("IS_PAINT") != null && rec_car.getStr("IS_PAINT").equals("1")
                        && rec_car.getStr("DEMAND_PRODUCT_TYPE").equals("5")) {
                    remark = "柳新车";
                }
                // 过点类型
                int passtype = 0;
                // 扫描地点
                int passpos = 0;
                if (sub.getStr("INDICATION_POINT_CODE").equals(rec_service.getStr("SERVICE_PARA1_VALUE"))) {
                    passtype = 2;
                    passpos = 31;
                } else {
                    passtype = 5;
                    passpos = 41;
                }
                // 保存数据
                int ret = Db.update("insert into T_INF_TO_U9_PASSREC(ID, STEEL_NO, PASS_TIME, REMARK, PASS_TYPE, NOTICE_CARTYPE, CARTYPE_CODE, CARBODY_CODE, PASS_POS, DEAL_STATUS, DEAL_TIME) values(sys_guid(), ?, ?, ?, ?, ?, ?, ?, ?, 0, sysdate)", rec_car.getStr("K_STAMP_ID"), rec_car.getDate("PASSED_TIME"), remark, passtype, rec_car.getStr("K_NOTICE_CARTYPE"), rec_car.getStr("K_CARTYPE"), rec_car.getStr("K_CARBODY_CODE"), passpos);
                if (ret == 1) {
                    Db.update("update T_CMD_PRODUCTION_ORDER set PRODUCTION_ORDER_STATUS=1 where ID=?", sub.getStr("ID"));
                    Db.update("update T_SYS_SERVICE set LAST_OPERATE_TIME=sysdate, LAST_OPERATE_INFO=? where SERVICE_CODE=?", String.format("生产指令处理成功，ID【%s】，指示点编码【%s】，生产编码【%s】，过点记录ID【%s】", sub.getStr("ID"), sub.getStr("INDICATION_POINT_CODE"), sub.getStr("PRODUCTION_CODE"), sub.getStr("PASSED_ID")), strServiceCode);
                } else {
                    Db.update("update T_CMD_PRODUCTION_ORDER set PRODUCTION_ORDER_STATUS=2 where ID=?", sub.getStr("ID"));
                    Db.update("update T_SYS_SERVICE set LAST_OPERATE_TIME=sysdate, LAST_OPERATE_INFO=? where SERVICE_CODE=?", String.format("生产指令处理失败，ID【%s】，指示点编码【%s】，生产编码【%s】，过点记录ID【%s】", sub.getStr("ID"), sub.getStr("INDICATION_POINT_CODE"), sub.getStr("PRODUCTION_CODE"), sub.getStr("PASSED_ID")), strServiceCode);
                    Log.Write(strServiceCode, LogLevel.Error, String.format("生产指令处理失败，ID【%s】，指示点编码【%s】，生产编码【%s】，过点记录ID【%s】", sub.getStr("ID"), sub.getStr("INDICATION_POINT_CODE"), sub.getStr("PRODUCTION_CODE"), sub.getStr("PASSED_ID")));
                }
            } else {
                // 指令错误
                Db.update("update T_CMD_PRODUCTION_ORDER set PRODUCTION_ORDER_STATUS=2 where ID=?", sub.getStr("ID"));
                Db.update("update T_SYS_SERVICE set LAST_OPERATE_TIME=sysdate, LAST_OPERATE_INFO=? where SERVICE_CODE=?", String.format("生产指令处理失败，找不到车辆信息，ID【%s】，指示点编码【%s】，生产编码【%s】，过点记录ID【%s】", sub.getStr("ID"), sub.getStr("INDICATION_POINT_CODE"), sub.getStr("PRODUCTION_CODE"), sub.getStr("PASSED_ID")), strServiceCode);
                Log.Write(strServiceCode, LogLevel.Error, String.format("生产指令处理失败，找不到车辆信息，ID【%s】，指示点编码【%s】，生产编码【%s】，过点记录ID【%s】", sub.getStr("ID"), sub.getStr("INDICATION_POINT_CODE"), sub.getStr("PRODUCTION_CODE"), sub.getStr("PASSED_ID")));
            }
        }

        return msg;
    }
}

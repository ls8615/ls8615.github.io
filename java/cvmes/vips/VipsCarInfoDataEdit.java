package cvmes.vips;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.IAtom;
import com.jfinal.plugin.activerecord.Record;
import cvmes.CvmesService;
import cvmes.common.Log;
import cvmes.common.LogLevel;

import java.sql.SQLException;
import java.util.List;

public class VipsCarInfoDataEdit {
    public void run() {
        Thread thread = new Thread(new VipsCarInfoDataEditThread());
        thread.start();
    }

    class VipsCarInfoDataEditThread extends Thread {
        // 服务编码
        private final String strServiceCode = "VipsCarInfoDataEdit";

        // 业务操作
        private void runBll(Record rec_service) {
            // 获取待处理指令（一次处理500条以内）
            String sql1 = String.format("select * from T_CMD_PRODUCTION_ORDER where PRODUCTION_ORDER_STATUS='0' and INDICATION_POINT_CODE in (%s) and rownum<=500", rec_service.getStr("SERVICE_PARA1_VALUE"));
            List<Record> list = Db.find(sql1);
            if (list == null) return;

            // 逐条处理指令
            for (Record sub : list) {
                try {
                    boolean flag = Db.tx(new IAtom() {
                        public boolean run() throws SQLException {
                            // 插入重保件车辆信息接口表
                            String sql2 = String.format("INSERT INTO T_INF_TO_PARTS_CARDINFO(ID, PRODUCTION_CODE, CHASSIS_NO, LINE_NO, ENGINE_NO" +
                                    "  , ENGINE_TYPE, CAR_TYPE_CODE, CAR_TYPE" +
                                    "  , ASSEMBLY_OFF_TIME, GEAR_BOX, PALN_DATE, MOTOR_NO" +
                                    "  , MOTOR_PART, BATTER_PART, CAR_CONTROLLER_PART, DEAL_STATUS" +
                                    "  , DEAL_TIME)" +
                                    " SELECT SYS_GUID() AS ID, t1.PRODUCTION_CODE, t2.K_CHASSIS_NO AS CHASSIS_NO, t2.K_ASSEMBLY_LINE AS LINE_NO, t2.K_ENGINE_NO AS ENGINE_NO" +
                                    "  , t2.K_ENGINE AS ENGINE_TYPE, t2.DEMAND_PRODUCT_CODE AS CAR_TYPE_CODE, t2.K_NOTICE_CARTYPE AS CAR_TYPE" +
                                    "  , t4.PASSED_TIME AS ASSEMBLY_OFF_TIME, t2.K_GEARBOX AS GEAR_BOX, t3.SCHEDULING_PLAN_DATE AS PALN_DATE, t2.K_ELECTROMOTOR_NO AS MOTOR_NO" +
                                    "  , t2.K_ELECTROMOTOR AS MOTOR_PART, t2.K_BATTER_PART AS BATTER_PART, t2.K_CONTROLLER_PART AS CAR_CONTROLLER_PART, '0' AS DEAL_STATUS" +
                                    "  , sysdate AS DEAL_TIME" +
                                    " FROM T_PLAN_SCHEDULING_D t1" +
                                    " LEFT JOIN T_PLAN_DEMAND_PRODUCT t2 ON t1.PRODUCTION_CODE=t2.PRODUCTION_CODE" +
                                    " LEFT JOIN T_PLAN_SCHEDULING t3 ON t1.SCHEDULING_PLAN_CODE=t3.SCHEDULING_PLAN_CODE" +
                                    " LEFT JOIN T_ACTUAL_PASSED_RECORD t4 ON t1.PRODUCTION_CODE=t4.PRODUCTION_CODE" +
                                    " WHERE t1.LINE_CODE IN (%s)" +
                                    " AND t4.ID='%s'", rec_service.getStr("SERVICE_PARA2_VALUE"), sub.getStr("PASSED_ID"));
                            int cnt = Db.update(sql2);
                            if (cnt == 0) return false;

                            // 更新生产指令状态
                            sub.set("PRODUCTION_ORDER_STATUS", "1");
                            Db.update("T_CMD_PRODUCTION_ORDER", sub);
                            return true;
                        }
                    });

                    // 更新最后操作信息
                    if (flag) {
                        Db.update("update T_SYS_SERVICE set LAST_OPERATE_TIME=sysdate, LAST_OPERATE_INFO=? where SERVICE_CODE=?", String.format("处理生产指令成功，指令ID【%s】，生产编码【%s】", sub.getStr("ID"), sub.getStr("PRODUCTION_CODE")), strServiceCode);
                    } else {
                        Db.update("update T_SYS_SERVICE set LAST_OPERATE_TIME=sysdate, LAST_OPERATE_INFO=? where SERVICE_CODE=?", String.format("处理生产指令失败，指令ID【%s】，生产编码【%s】", sub.getStr("ID"), sub.getStr("PRODUCTION_CODE")), strServiceCode);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Db.update("update T_SYS_SERVICE set LAST_OPERATE_TIME=sysdate, LAST_OPERATE_INFO=? where SERVICE_CODE=?", String.format("处理生产指令异常，指令ID【%s】，生产编码【%s】，原因【%s】", sub.getStr("ID"), sub.getStr("PRODUCTION_CODE"), e.getMessage()), strServiceCode);
                    Log.Write(strServiceCode, LogLevel.Error, String.format("处理生产指令异常，指令ID【%s】，生产编码【%s】，原因【%s】", sub.getStr("ID"), sub.getStr("PRODUCTION_CODE"), e.getMessage()));
                }
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

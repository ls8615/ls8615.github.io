package cvmes.hzplc;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.IAtom;
import com.jfinal.plugin.activerecord.Record;
import cvmes.CvmesService;
import cvmes.common.Log;
import cvmes.common.LogLevel;

import java.sql.SQLException;

public class HzplcMb170DataEdit {

    public void run() {
        Thread thread = new HzplcMb170DataEditThread();
        thread.start();
    }

    class HzplcMb170DataEditThread extends Thread {
        // 服务编码
        private final String strServiceCode = "HzplcMb170DataEdit";

        // 业务操作
        private void runBll(Record rec_service) {
            Db.tx(new IAtom(){
                @Override
                public boolean run() throws SQLException {
                    //指示点
                    String indicationPointCode = rec_service.getStr("SERVICE_PARA1_VALUE");
                    int ctn = 0;
                    ctn = Db.queryInt("SELECT COUNT(1) AS CTN FROM T_CMD_PRODUCTION_ORDER WHERE INDICATION_POINT_CODE = ? AND PRODUCTION_ORDER_STATUS = '0'", indicationPointCode);
                    if(ctn == 0){ //如果没有指令不做操作
                        return true;
                    }
                    //删除已导入未处理的接口数据
                    ctn = Db.delete("DELETE FROM T_INF_TO_WELD_MB170 A WHERE EXISTS ( " +
                            "SELECT DISTINCT T1.SCHEDULING_PLAN_DATE, T2.LINE_CODE " +
                            "   FROM T_CMD_PRODUCTION_ORDER T " +
                            "JOIN T_PLAN_SCHEDULING T1 on(T.PLAN_NO = T1.SCHEDULING_PLAN_CODE) " +
                            "JOIN T_PLAN_SCHEDULING_D T2 on(T2.SCHEDULING_PLAN_CODE = T1.SCHEDULING_PLAN_CODE) " +
                            "WHERE T1.SCHEDULING_PLAN_DATE = A.PLAN_TIME AND T2.LINE_CODE = A.LINE_CODE AND T.INDICATION_POINT_CODE = ? AND PRODUCTION_ORDER_STATUS = '0' " +
                            ") AND A.DEAL_STATUS = '0'", indicationPointCode);
                    Log.Write(strServiceCode, LogLevel.Information, "已删除指示点:"+indicationPointCode+",未处理的接口数据["+ctn+"条]。");
                    //检测生产指令表是否有自己需要处理的待处理指令（指示点编码=设定值，状态=0）
                    ctn = Db.update("INSERT INTO T_INF_TO_WELD_MB170(ID,CARBODY_CODE, ROBOT_CODE, SEQ_NO, PLAN_TIME, DEAL_STATUS, LINE_CODE, CREATE_TIME, DEAL_TIME) " +
                            "SELECT SYS_GUID(), E.K_STAMP_ID, F.PRODUCT_CODE, C.SEQ_NO, A.PLAN_DATE,'0' AS DEAL_STATUS, C.LINE_CODE, SYSDATE, SYSDATE " +
                            "FROM T_CMD_PRODUCTION_ORDER A " +
                            "LEFT JOIN T_PLAN_SCHEDULING B ON(A.PLAN_NO = B.SCHEDULING_PLAN_CODE) " +
                            "LEFT JOIN T_PLAN_SCHEDULING_D C ON(C.SCHEDULING_PLAN_CODE = B.SCHEDULING_PLAN_CODE) " +
                            "LEFT JOIN T_PLAN_DEMAND_PRODUCT E on(E.PRODUCTION_CODE = C.PRODUCTION_CODE) " +
                            "LEFT JOIN ( " +
                            "SELECT M.* FROM ( " +
                            "SELECT " +
                            "T.PRODUCT_CODE, " +
                            "REPLACE(T.CAR_BODY_FIGURE_NUM, '-LD', '') AS CAR_BODY_FIGURE_NUM, " +
                            "T.CREATE_TIME, " +
                            "ROW_NUMBER() OVER(PARTITION BY T.CAR_BODY_FIGURE_NUM ORDER BY T.CREATE_TIME DESC) AS RN " +
                            "FROM " +
                            "T_BASE_CH_DIFF_FIGURE_NUM T) M WHERE M.RN = 1 " +
                            ") F ON(F.CAR_BODY_FIGURE_NUM = E.K_CARBODY_CODE) " +
                            "WHERE A.INDICATION_POINT_CODE = ? AND A.PRODUCTION_ORDER_STATUS = '0' ", indicationPointCode);
                    Log.Write(strServiceCode, LogLevel.Information, "已插入指示点:"+indicationPointCode+",接口数据["+ctn+"条]。");
                    //更新指令状态为1
                    ctn = Db.update("UPDATE T_CMD_PRODUCTION_ORDER SET PRODUCTION_ORDER_STATUS = '1'" +
                            " WHERE INDICATION_POINT_CODE = ? AND PRODUCTION_ORDER_STATUS = '0'", indicationPointCode);
                    Log.Write(strServiceCode, LogLevel.Information, "已处理指示点:"+indicationPointCode+",指令数据["+ctn+"条]。");
                    return true;
                }
            });
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

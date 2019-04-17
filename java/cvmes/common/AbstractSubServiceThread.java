package cvmes.common;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;
import cvmes.CvmesService;

public abstract class AbstractSubServiceThread extends Thread {
    // 服务编码
    public String strServiceCode;

    //最后操作信息
    String msg = "";

    /**
     * 抽象初始化服务编码方法
     */
    public abstract void initServiceCode();

    /**
     * 抽象业务逻辑方法
     *
     * @param rec_service
     */
    public abstract String runBll(Record rec_service) throws Exception;

    /**
     * 线程控制
     */
    @Override
    public void run() {
        //初始化服务编码
        initServiceCode();

        //记录自服务变更记录
        CvmesService.serviceStatusChange(strServiceCode, 1);
        Log.Write(strServiceCode, LogLevel.Information, "服务启动");

        //检查服务编码
        if (strServiceCode == null) {
            Log.Write("main", LogLevel.Error, String.format("检查服务编码失败，服务无法启动。服务编码[%s]", strServiceCode));
            CvmesService.setServiceStop(strServiceCode);
            return;
        }

        while (true) {
            // 获取服务信息
            Record rec = Db.findById("T_SYS_SERVICE", "SERVICE_CODE", strServiceCode);

            // 更新生存时间
            Db.update("update T_SYS_SERVICE set LAST_LIVE_TIME=sysdate where SERVICE_CODE=?", strServiceCode);

            // 退出指令
            if (rec.getInt("SERVICE_STATUS") == 0) {
                Log.Write(strServiceCode, LogLevel.Warning, "服务停止");
                CvmesService.setServiceStop(strServiceCode);

                Db.update("update T_SYS_SERVICE set LAST_OPERATE_TIME=sysdate,LAST_OPERATE_INFO=? where SERVICE_CODE=?", "服务停止", strServiceCode);

                //记录
                CvmesService.serviceStatusChange(strServiceCode, 0);
                break;
            }

            // 业务操作
            try {
                msg = runBll(rec);

                // 更新服务信息
                if (msg.length() != 0) {
                    Db.update("update T_SYS_SERVICE set LAST_OPERATE_TIME=sysdate,LAST_OPERATE_INFO=? where SERVICE_CODE=?", msg, strServiceCode);
                }
            } catch (Exception e) {
                msg = String.format("业务处理异常，原因[%s]", e.getMessage());
                Log.Write(strServiceCode, LogLevel.Error, msg);
            } catch (Error error) {
                CvmesService.setServiceStop(strServiceCode);
                msg = String.format("发生严重错误，线程已退出，原因[%s]", error.getMessage());
                Log.Write(strServiceCode, LogLevel.Error, msg);
                break;
            }

            // 休眠
            try {
                Thread.sleep(((Double) (rec.getDouble("SLEEP_TIME") * 1000)).intValue());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

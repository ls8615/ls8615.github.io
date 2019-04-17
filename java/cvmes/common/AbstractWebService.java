package cvmes.common;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;
import cvmes.CvmesService;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.xml.ws.Endpoint;

@WebService
public abstract class AbstractWebService extends Thread {
    // 服务编码
    public String strServiceCode;
    // 发布地址
    public String strUrl;

    // 服务信息
    private Record rec_service;

    @WebMethod(exclude = true)
    public Record getRecService() {
        return rec_service;
    }

    // 发布状态
    private boolean blnPublish = false;
    // WebService操作对象
    private Endpoint endpoint;
    //最后操作信息
    private String msg = "";

    /**
     * 抽象初始化服务编码方法
     */
    public abstract void initServiceCode();

    /**
     * 抽象初始化WebService方法
     */
    public abstract void initWebService();

    /**
     * 线程控制
     */
    @Override
    @WebMethod(exclude = true)
    public void run() {
        // 初始化服务编码
        initServiceCode();

        // 记录自服务变更记录
        CvmesService.serviceStatusChange(strServiceCode, 1);
        Log.Write(strServiceCode, LogLevel.Information, "服务启动");

        // 检查服务编码
        if (strServiceCode == null) {
            Log.Write("main", LogLevel.Error, String.format("检查服务编码失败，服务无法启动。服务编码【%s】", strServiceCode));
            CvmesService.setServiceStop(strServiceCode);
            return;
        }

        while (true) {
            // 获取服务信息
            rec_service = Db.findById("T_SYS_SERVICE", "SERVICE_CODE", strServiceCode);
            // 初始化WebService方法
            initWebService();

            // 更新生存时间
            Db.update("update T_SYS_SERVICE set LAST_LIVE_TIME = sysdate where SERVICE_CODE = ?", strServiceCode);

            // 退出指令
            if (rec_service.getInt("SERVICE_STATUS") == 0) {
                endpoint.stop();

                Log.Write(strServiceCode, LogLevel.Warning, "服务停止");
                CvmesService.setServiceStop(strServiceCode);

                Db.update("update T_SYS_SERVICE set LAST_OPERATE_TIME = sysdate, LAST_OPERATE_INFO = ? where SERVICE_CODE = ?", "服务停止", strServiceCode);

                // 记录
                CvmesService.serviceStatusChange(strServiceCode, 0);
                break;
            }

            // 业务操作
            try {
                if (!blnPublish) {
                    endpoint = Endpoint.publish(strUrl, this);
                    blnPublish = true;

                    msg = "WebService服务发布成功";
                    Log.Write(strServiceCode, LogLevel.Information, msg);
                    Db.update("update T_SYS_SERVICE set LAST_OPERATE_TIME = sysdate, LAST_OPERATE_INFO = ? where SERVICE_CODE = ?", msg, strServiceCode);
                }
            } catch (Exception e) {
                msg = String.format("业务处理异常，原因【%s】", e.getMessage());
                Log.Write(strServiceCode, LogLevel.Error, msg);
            } catch (Error error) {
                CvmesService.setServiceStop(strServiceCode);
                msg = String.format("发生严重错误，线程已退出，原因[%s]", error.getMessage());
                Log.Write(strServiceCode, LogLevel.Error, msg);
                if (endpoint.isPublished()) {
                    endpoint.stop();
                }
                break;
            }

            // 休眠
            try {
                Thread.sleep(((Double) (rec_service.getDouble("SLEEP_TIME") * 1000)).intValue());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

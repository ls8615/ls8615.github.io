package cvmes;

import com.jfinal.kit.PropKit;
import com.jfinal.plugin.activerecord.ActiveRecordPlugin;
import com.jfinal.plugin.activerecord.CaseInsensitiveContainerFactory;
import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;
import com.jfinal.plugin.activerecord.dialect.OracleDialect;
import com.jfinal.plugin.druid.DruidPlugin;
import cvmes.common.*;

import java.util.ArrayList;
import java.util.List;

public class CvmesService {
    // 进程服务标记
    private static final String strServiceFlag = "Main";
    // 进程停止标记
    private static boolean blnStopFlag = false;
    // 服务对象列表
    private static List<String> listService;

    // 启动服务事件响应
    public static void start(String[] args) {
        // 启动进程
        init();
    }

    // 停止服务事件响应
    public static void stop(String[] args) {
        // 进程停止
        Log.Write(strServiceFlag, LogLevel.Warning, "进程停止");

        // 通知各服务线程退出
        Db.update("update T_SYS_SERVICE set SERVICE_STATUS = 0 where SERVER_FLAG = ?", PropKit.getInt("serverFlag"));
        // 通知进程退出
        blnStopFlag = true;

        //记录服务变更时间
        serviceStatusChange(String.format("%s-flag%s", strServiceFlag, PropKit.getInt("serverFlag")), 0);
    }

    // 服务线程停止
    public static void setServiceStop(String setServiceCode) {
        for (String sub : listService) {
            if (setServiceCode.equals(sub)) {
                listService.remove(sub);
                break;
            }
        }
    }

    // 程序入口
    public static void main(String[] args) {
        init();
    }

    // 主进程初始化
    public static void init() {
        // 加载配置文件
        PropKit.use("CvmesService.properties");

        // 进程启动开始
        Log.Write(strServiceFlag, LogLevel.Information, "进程启动开始");

        // 创建连接池
        DruidPlugin dp = new DruidPlugin(PropKit.get("dbUrl"), PropKit.get("dbUid"), PropKit.get("dbPwd"));
        dp.setMaxActive(PropKit.getInt("maxActive"));
        // 保证连接有效
        dp.setValidationQuery("select 1 from dual");
        dp.setTimeBetweenEvictionRunsMillis(PropKit.getInt("connectCheckTime") * 1000);
        dp.setTestWhileIdle(true);
        dp.setTestOnBorrow(false);
        dp.setTestOnReturn(false);
        // 启动连接池
        dp.start();

        // 创建ActiveRecordPlugin操作对象
        ActiveRecordPlugin arp = new ActiveRecordPlugin("cvmes", dp);
        // 设置数据库方言
        arp.setDialect(new OracleDialect());
        // 配置属性名(字段名)大小写不敏感容器工厂
        arp.setContainerFactory(new CaseInsensitiveContainerFactory());
        // 启动ActiveRecord
        try {
            arp.start();
        } catch (Exception e) {
            Log.Write(strServiceFlag, LogLevel.Error, "进程启动失败，无法连接到cvmes数据库");
            return;
        }

        // 启动服务
        Db.update("update T_SYS_SERVICE set SERVICE_STATUS = 1 where SERVER_FLAG = ?", PropKit.getInt("serverFlag"));
        listService = new ArrayList<>();

        // 进程启动完成
        Log.Write(strServiceFlag, LogLevel.Information, "进程启动完成");
        serviceStatusChange(String.format("%s-flag%s", strServiceFlag, PropKit.getInt("serverFlag")), 1);

        while (true) {
            try {
                if (blnStopFlag) {
                    break;
                }

                // 获取服务清单
                List<Record> list = Db.find("select * from T_SYS_SERVICE where SERVER_FLAG = ?", PropKit.getInt("serverFlag"));
                // 服务启动标记
                boolean flag;

                for (Record sub : list) {
                    // 重置服务启动标记
                    flag = false;

                    // 要求服务启动
                    if (sub.getInt("SERVICE_STATUS") == 1) {
                        for (String strService : listService) {
                            if (strService.equals(sub.getStr("SERVICE_CODE"))) {
                                flag = true;
                                break;
                            }
                        }

                        if (!flag) {
                            try {

                                HotLoaderServicePackge loader = new HotLoaderServicePackge(sub.getStr("SERVICE_PACKAGE_NAME"), sub.getStr("SERVICE_CLASS_NAME"), sub.getInt("SERVICE_HOT_TYPE"));
                                switch (sub.getInt("SERVICE_VERSION")) {
                                    //旧版本实现方式
                                    case 0:
                                        // 实例化类并启动
                                        Class cls = loader.getStartClass();
                                        Object obj = cls.getConstructor().newInstance();
                                        cls.getMethod("run").invoke(obj);
                                        break;
                                    //新版本启动方式
                                    case 1:
                                        Thread thread = loader.getStartThread();
                                        thread.start();
                                        break;
                                }

                                // 记录已启动的服务
                                listService.add(sub.getStr("SERVICE_CODE"));

                            } catch (Exception e) {
                                Log.Write(strServiceFlag, LogLevel.Error, String.format("服务【%s】启动失败，原因【%s】", sub.getStr("SERVICE_CODE"), e));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.Write(strServiceFlag, LogLevel.Error, String.format("子服务控制发生异常，原因【%s】", e.getMessage()));
            }

            // 休眠
            try {
                Thread.sleep(1000 * PropKit.getInt("sleepTime"));
            } catch (Exception e) {
                e.printStackTrace();
                Log.Write(strServiceFlag, LogLevel.Error, String.format("休眠异常，原因【%s】", e.getMessage()));
            }
        }
    }

    /**
     * 服务变更记录
     *
     * @param strServiceCode 服务编码/服务标记
     * @param service_status 服务状态
     */
    public static void serviceStatusChange(String strServiceCode, int service_status) {
        Db.update("INSERT INTO t_sys_service_change_rec(ID,service_code,service_status,status_change_time) VALUES(sys_guid(),?,?,SYSDATE)", strServiceCode, service_status);
    }
}

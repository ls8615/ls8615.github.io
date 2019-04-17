package cvmes.opc.device.status.common;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.URLDecoder;
import java.util.Properties;

public class OpcConfig {

    private static Properties prop = new Properties();

    // OPC服务器地址
    private static String ServerHost;
    // OPC服务器域
    private static String ServerDomain = "";
    // 数据库用户名
    private static String LoginUser;
    // 数据库密码
    private static String LoginPassword;

    // 应用程序进程ClsId
    private static String ClsId;

    //过滤通道编码
    private static String ChannelCode;
    //过滤设备编码
    private static String DeviceCode;
    //过滤标签组编码
    private static String GroupCode;

    //重连时间间隔
    private static Integer ReConnection;


    // 读取配置文件
    public static boolean readConfig() {

        try {
            prop.load(OpcConfig.class.getClassLoader().getResourceAsStream("cvmes/opc/device/status/res/opc-server-conf.properties"));

            ServerHost = prop.getProperty("ServerHost");
            ServerDomain = prop.getProperty("ServerDomain");
            LoginUser = prop.getProperty("LoginUser");
            LoginPassword = prop.getProperty("LoginPassword");
            ClsId = prop.getProperty("ClsId");
            ChannelCode = prop.getProperty("ChannelCode");
            DeviceCode = prop.getProperty("DeviceCode");
            GroupCode = prop.getProperty("GroupCode");
            ReConnection = Integer.parseInt(prop.getProperty("ReConnection"));

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static String getServerHost() {
        return ServerHost;
    }

    public static String getServerDomain() {
        return ServerDomain;
    }

    public static String getLoginUser() {
        return LoginUser;
    }

    public static String getLoginPassword() {
        return LoginPassword;
    }

    public static String getClsId() {
        return ClsId;
    }

    public static String getChannelCode() {
        return ChannelCode;
    }

    public static String getDeviceCode() {
        return DeviceCode;
    }

    public static String getGroupCode() {
        return GroupCode;
    }

    public static Integer getReConnection() {
        return ReConnection;
    }
}

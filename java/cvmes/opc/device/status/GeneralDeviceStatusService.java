package cvmes.opc.device.status;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;
import cvmes.CvmesService;
import cvmes.common.Log;
import cvmes.common.LogLevel;
import cvmes.opc.device.status.common.OpcConfig;
import org.jinterop.dcom.common.JIException;
import org.jinterop.dcom.core.JIVariant;
import org.openscada.opc.lib.common.ConnectionInformation;
import org.openscada.opc.lib.da.*;

import java.rmi.server.ExportException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * 通用设备状态OPC Client(实时性要求小于等于1s的设备数据不适应)
 */
public class GeneralDeviceStatusService {
    // 服务编码
    private final String strServiceCode = "GeneralDeviceStatusService";


    public void run() {

        Log.Write(strServiceCode, LogLevel.Information, "服务启动");

        //初始化配置参数
        // 读取配置文件
        boolean ret = OpcConfig.readConfig();
        if (!ret) {
            Log.Write(strServiceCode, LogLevel.Error, "读取配置文件失败，请检查配置文件");
            Log.Write(strServiceCode, LogLevel.Error, "停止服务");
            // 更新生存时间
            Db.update("update T_SYS_SERVICE set LAST_LIVE_TIME=sysdate where SERVICE_CODE=?", strServiceCode);
            return;
        }

        //opc连接定义
        //申请OPC Server连接对象
        ConnectionInformation conn = new ConnectionInformation();
        //设置OPC Server Host
        conn.setHost(OpcConfig.getServerHost());

        //设置OPC Server Domain
        if (OpcConfig.getServerDomain() != null && OpcConfig.getServerDomain().length() != 0) {
            conn.setDomain(OpcConfig.getServerDomain());
        }

        //设置DCOM登录用户名
        conn.setUser(OpcConfig.getLoginUser());

        //设置DCOM登录密码
        conn.setPassword(OpcConfig.getLoginPassword());

        //设置应用程序进程ClsId
        conn.setClsid(OpcConfig.getClsId());

        //申请服务器对象
        Server server = new Server(conn, Executors.newSingleThreadScheduledExecutor());

        //启用调度
        server.getScheduler().execute(new Runnable() {
            @Override
            public void run() {
                //opc通讯连接守护，通讯异常时断开重连
                outer:
                while (true) {
                    try {
                        //1.检查是否退出指令
                        //1.1.检查是否退出指令——获取服务信息
                        Record rec = Db.findById("T_SYS_SERVICE", "SERVICE_CODE", strServiceCode);

                        //1.2.检查是否退出指令——更新生存时间
                        Db.update("update T_SYS_SERVICE set LAST_LIVE_TIME=sysdate where SERVICE_CODE=?", strServiceCode);

                        //1.3.检查是否退出指令——退出指令
                        if (rec.getInt("SERVICE_STATUS") == 0) {
                            Log.Write(strServiceCode, LogLevel.Warning, "服务停止");
                            CvmesService.setServiceStop(strServiceCode);
                            break;
                        }

                        //2.获取所有监听标签配置
                        //
                        List<Record> list_tag = Db.find("select * from  T_DEVICE_STATUS_VALUES where IS_ENABLES=0");

                        //2.1.获取所有监听标签配置——无监听标签,等待下一次循环
                        if (list_tag.size() == 0) {
                            String msg = String.format("没有找到配置监听标签，断开OPC连接，等待重连",
                                    OpcConfig.getChannelCode(), OpcConfig.getDeviceCode(), OpcConfig.getGroupCode());
                            Log.Write(strServiceCode, LogLevel.Warning, msg);
                            //更新最后操作信息
                            Db.update("update T_SYS_SERVICE set LAST_OPERATE_TIME=sysdate,LAST_OPERATE_INFO=? where SERVICE_CODE=?", msg, strServiceCode);


                            Thread.sleep(OpcConfig.getReConnection() * 1000);
                            continue outer;
                        }

                        //3.打开连接
                        try {
                            server.connect();
                            Log.Write(strServiceCode, LogLevel.Information, String.format("打开OPC连接成功"));
                        } catch (Exception e) {
                            server.disconnect();

                            String msg = String.format("打开OPC连接失败，原因[%s]，等待重连", e.toString());
                            Log.Write(strServiceCode, LogLevel.Error, msg);

                            //更新最后操作信息
                            Db.update("update T_SYS_SERVICE set LAST_OPERATE_TIME=sysdate,LAST_OPERATE_INFO=? where SERVICE_CODE=?", msg, strServiceCode);

                            Thread.sleep(OpcConfig.getReConnection() * 1000);

                            continue outer;
                        }

                        //4.获取所有符合过滤条件的服务器标签
                        List<Record> list_server_tag = getOpcServerAllTag(server);
                        if (list_server_tag.size() == 0) {
                            server.disconnect();

                            String msg = String.format("没有找到符合的服务器标签，通道编码[%s].设备编码[%s].标签组[%s]，断开OPC连接，等待重连",
                                    OpcConfig.getChannelCode(), OpcConfig.getDeviceCode(), OpcConfig.getGroupCode());
                            Log.Write(strServiceCode, LogLevel.Warning, msg);
                            //更新最后操作信息
                            Db.update("update T_SYS_SERVICE set LAST_OPERATE_TIME=sysdate,LAST_OPERATE_INFO=? where SERVICE_CODE=?", msg, strServiceCode);

                            Thread.sleep(OpcConfig.getReConnection() * 1000);
                            continue;
                        }

                        //5.定义客户端标签组（一般只需要定义一个组，也可以根据标签类型定义多个组）
                        Group group = server.addGroup("client");
                        group.setActive(true);

                        //6.获取监听标签配置存在于服务器标签,并添加到监听中
                        List<Item> list_items = new ArrayList<>();
                        for (Record sub_tag : list_tag) {
                            boolean isFind = false;
                            for (Record sub_server_tag : list_server_tag) {
                                if (sub_server_tag.getStr("CHANNEL_CODE").equals(sub_tag.getStr("CHANNEL_CODE")) &&
                                        sub_server_tag.getStr("DEVICE_CODE").equals(sub_tag.getStr("DEVICE_CODE")) &&
                                        sub_server_tag.getStr("GROUP_CODE").equals(sub_tag.getStr("GROUP_CODE")) &&
                                        sub_server_tag.getStr("TAG_CODE").equals(sub_tag.getStr("TAG_CODE"))
                                ) {
                                    Item item = group.addItem(sub_server_tag.getStr("OPC_TAG"));
                                    item.setActive(true);
                                    list_items.add(item);

                                    isFind = true;
                                    continue;
                                }
                            }
                            if (!isFind) {
                                Log.Write(strServiceCode, LogLevel.Error, String.format("监听标签【%s.%s.%s.%s】，在服务器中没有配置监听",
                                        sub_tag.getStr("CHANNEL_CODE"),
                                        sub_tag.getStr("DEVICE_CODE"),
                                        sub_tag.getStr("GROUP_CODE"),
                                        sub_tag.getStr("TAG_CODE")));
                            }
                        }

                        //7.读取业务处理——循环单个标签读取
                        runPoolSingleBll(rec, list_items);
                    } catch (Exception ex) {
                        String msg = String.format("OPC通讯连接异常，原因[%s],断开OPC连接，等待重连", ex.getCause());
                        Log.Write(strServiceCode, LogLevel.Error, msg);
                        //更新最后操作信息
                        Db.update("update T_SYS_SERVICE set LAST_OPERATE_TIME=sysdate,LAST_OPERATE_INFO=? where SERVICE_CODE=?", msg, strServiceCode);

                        try {
                            Thread.sleep(OpcConfig.getReConnection() * 1000);
                        } catch (Exception es) {
                            es.printStackTrace();
                        }

                        continue;
                    }
                }
            }
        });
    }


    private List<Record> getOpcServerAllTag(Server server) {

        List<Record> list = new ArrayList<>();
        try {
            for (String sub : server.getFlatBrowser().browse()) {
                String s[] = sub.split("\\.");
                //标签定义不符合规范（通道编码.设备编码.标签组编码.标签编码）
                if (s.length != 4)
                    continue;

                //是否在过滤的通道编码
                if (OpcConfig.getChannelCode().indexOf(s[0]) == -1)
                    continue;

                //是否在过滤的设备编码
                if (OpcConfig.getDeviceCode().indexOf(s[1]) == -1)
                    continue;

                //是否在过滤的标签组编码
                if (OpcConfig.getGroupCode().indexOf(s[2]) == -1)
                    continue;

                Record record = new Record()
                        .set("OPC_TAG", sub)
                        .set("CHANNEL_CODE", s[0])
                        .set("DEVICE_CODE", s[1])
                        .set("GROUP_CODE", s[2])
                        .set("TAG_CODE", s[3]);
                list.add(record);
            }
        } catch (Exception ex) {
            Log.Write(strServiceCode, LogLevel.Error, String.format("获取OPC服务器标签失败，原因[%s]", ex));
        }

        return list;
    }


    /**
     * 业务处理方法——单个循环读取
     *
     * @param rec_service
     * @param list_items
     * @throws JIException
     */
    private void runPoolSingleBll(Record rec_service, List<Item> list_items) throws JIException {

        int OK = 0;
        int NG = 0;
        String msg = "";

        while (true) {
            OK = 0;
            NG = 0;

            //1.检查是否退出指令
            try {
                //1.1.检查是否退出指令——获取服务信息
                Record rec = Db.findById("T_SYS_SERVICE", "SERVICE_CODE", strServiceCode);

                //1.2.检查是否退出指令——更新生存时间
                Db.update("update T_SYS_SERVICE set LAST_LIVE_TIME=sysdate where SERVICE_CODE=?", strServiceCode);

                //1.3.检查是否退出指令——退出指令
                if (rec.getInt("SERVICE_STATUS") == 0) {
                    Log.Write(strServiceCode, LogLevel.Warning, "服务停止");
                    CvmesService.setServiceStop(strServiceCode);
                    break;
                }
            } catch (Exception ex) {
                Log.Write(strServiceCode, LogLevel.Error, String.format("执行退出指令异常，原因[%s]", ex.getCause()));
                break;
            }

            for (Item sub : list_items) {
                //同步读取
                ItemState itemState = sub.read(false);
                if (itemState.getQuality() != 0) {
                    short value = itemState.getValue().getObjectAsUnsigned().getValue().shortValue();
                    try {
                        int ret = Db.update("UPDATE T_DEVICE_STATUS_VALUES t SET t.tag_value=?,t.last_listen_time=sysdate WHERE t.channel_code=? AND t.device_code=? AND t.group_code=? AND t.tag_code=?",
                                value,
                                sub.getId().split("\\.")[0],
                                sub.getId().split("\\.")[1],
                                sub.getId().split("\\.")[2],
                                sub.getId().split("\\.")[3]);
                        if (ret == -1) {
                            NG++;
                            Log.Write(strServiceCode, LogLevel.Error, String.format("更新标签[%s]数据失败", sub.getId()));
                        }

                        OK++;
                        Log.Write(strServiceCode, LogLevel.Information, String.format("获取到标签[%s]值[%s]", sub.getId(), value));
                    } catch (Exception ex) {
                        Log.Write(strServiceCode, LogLevel.Error, String.format("获取更新标签值到DB异常，原因[%s]", ex.getCause()));
                    }
                } else {
                    NG++;
                    Log.Write(strServiceCode, LogLevel.Error, String.format("获取标签[%s]数据失败，错误代码[%s]", sub.getId(), itemState.getErrorCode()));
                }
            }
            msg = String.format("获取标签总数[%s]个,成功[%s]个，失败[%s]个", list_items.size(), OK, NG);
            Log.Write(strServiceCode, LogLevel.Information, msg);

            //更新最后操作信息
            Db.update("update T_SYS_SERVICE set LAST_OPERATE_TIME=sysdate,LAST_OPERATE_INFO=? where SERVICE_CODE=?", msg, strServiceCode);


            // 休眠
            try {
                Thread.sleep(1000 * rec_service.getInt("SLEEP_TIME"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

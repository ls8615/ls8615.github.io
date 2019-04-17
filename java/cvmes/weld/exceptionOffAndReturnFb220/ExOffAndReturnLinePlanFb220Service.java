package cvmes.weld.exceptionOffAndReturnFb220;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.IAtom;
import com.jfinal.plugin.activerecord.Record;
import cvmes.CvmesService;
import cvmes.common.AbstractSubServiceThread;
import cvmes.common.Log;
import cvmes.common.LogLevel;

import java.sql.SQLException;
import java.util.List;

/**
 * 焊装异常离线回线服务
 */

public class ExOffAndReturnLinePlanFb220Service extends AbstractSubServiceThread {


    // Fb220异常离线回线内存地址组编码
    private final String groupMemoryCode = "fb220.exception.offline.returnline.plan";

    //工位编码
    private final String stationCode = "FB220";

    //生产线编码
    private final String lineCode = "ch01";

    @Override
    public void initServiceCode() {
        // 服务编码
        this.strServiceCode = "ExOffAndReturnFb220Service";
    }

    // 业务操作
    public String runBll(Record rec_service) {
        return DealHeader();
    }

    /**
     * 逻辑处理入口
     */
    private String DealHeader() {
        //计算结果信息
        String msg = "";

        //1.获取写表是否存在未处理指令
        List<Record> list_memory_write = Db.find("SELECT 1 FROM T_DEVICE_WELD_WRITE_RAM WHERE  DEAL_STATUS = '0'  AND  group_memory_code  = ?", groupMemoryCode);

        //1.1.写表存在未处理指令，计算结束
        if (list_memory_write.size() != 0) {
            msg = String.format("Fb220异常离线回线写表内存地址组【%s】存在未处理指令，请检查OPC通讯是否正常", groupMemoryCode);
            Log.Write(strServiceCode, LogLevel.Error, msg);
            return msg;
        }

        //2.获取读表指令
        List<Record> list_memory_read = Db.find("SELECT T.* FROM T_DEVICE_WELD_READ_RAM T WHERE T.GROUP_MEMORY_CODE =?", groupMemoryCode);
        if (list_memory_read.size() != 1) {
            msg = String.format("读取Fb220异常离线回线内存地址组【%s】配置错误，请检查配置", groupMemoryCode);
            Log.Write(strServiceCode, LogLevel.Error, msg);
            return msg;
        }

        //2.1.获取PLC离线状态
        String automatism = list_memory_read.get(0).getStr("AUTOMATISM");
        if (automatism == null) {
            msg = String.format("Fb220异常离线回线读表内存地址组【%s】自动标记状态为空，请检查自动标记状态", groupMemoryCode);
            Log.Write(strServiceCode, LogLevel.Information, msg);
            return msg;
        }

        //2.1.1.PLC处于在线状态，不做处理，结束运行
        if ("2".equals(automatism)) {
            msg = String.format("Fb220异常离线回线读表内存地址组【%s】PLC处于在线状态，不下发计划", groupMemoryCode);
            Log.Write(strServiceCode, LogLevel.Information, msg);
            return msg;
        }

        //3.获取控制命令字
        String memoryValue = list_memory_read.get(0).getStr("LOGIC_VALUE");

        switch (memoryValue) {
            case "0":
                Log.Write(strServiceCode, LogLevel.Information, String.format("地址组[%s],控制命令字[%s]下位操作。", groupMemoryCode, memoryValue));
                return "";
            case "1":
                return recordExceptDataAndUpdateLogic(strServiceCode, list_memory_read.get(0), "2").getStr("msg");
            case "2":
                return recordExceptDataAndUpdateLogic(strServiceCode, list_memory_read.get(0), "3").getStr("msg");
            case "3":
                Log.Write(strServiceCode, LogLevel.Information, String.format("地址组[%s],控制命令字[%s]下位操作。", groupMemoryCode, memoryValue));
                return "";
            default:
                msg = String.format("控制命令字未定义，内存地址组[%s],命令字[%s]", groupMemoryCode, msg);
                Log.Write(strServiceCode, LogLevel.Error, msg);
                return msg;
        }
    }

    private Record recordExceptDataAndUpdateLogic(String strServiceCode, Record memoryRead, String offAndReturnType) {
        String msg = "";
        Record recordMsg = new Record().set("msg", "").set("error", true);
        Log.Write(strServiceCode, LogLevel.Information, String.format("控制命令字[%s]开始计算", memoryRead.getStr("LOGIC_VALUE")));

        //1.获取车身钢码号
        String bodySteelCode = ComFun.getVpartFromMemoryValues(memoryRead);
        if (bodySteelCode.length() == 0 || bodySteelCode.equals("null")) {
            msg = "获取读表车身钢码号的值错误";
            Log.Write(strServiceCode, LogLevel.Warning, msg);
            recordMsg.set("msg", msg);
            return recordMsg;
        }

        //2.获取前置车身钢码号
        String preBodySteelCode = ComFun.getLoopForwardFromMemoryValues(memoryRead);
        if (preBodySteelCode.length() == 0 || preBodySteelCode.equals("null")) {
            msg = "获取读表前置车身钢码号的值错误";
            Log.Write(strServiceCode, LogLevel.Warning, msg);
            recordMsg.set("msg", msg);
            return recordMsg;
        }

        //启用事务
        boolean isSucess = Db.tx(new IAtom() {
            @Override
            public boolean run() throws SQLException {
                String msg = "";
                String planType = "";

                //计划类型
                if (("2").equals(offAndReturnType)) {
                    planType = "离线计划";
                } else if (("3").equals(offAndReturnType)) {
                    planType = "回线计划";
                }

                //2.1 插入离线回线车辆
                int update = Db.update(ComFun.insertExcReturnOffLinePlan(),
                        offAndReturnType,
                        bodySteelCode,
                        preBodySteelCode,
                        lineCode,
                        stationCode);
                if (update <= 0) {
                    msg = String.format("焊装Fb220异常离线回线计划记录失败:内存读表的钢码号:[%s],计划类型为:[%s]", bodySteelCode, planType);
                    Log.Write(strServiceCode, LogLevel.Error, msg);
                    recordMsg.set("msg", msg).set("error", false);
                    return false;
                }

                //2.2 插入成功
                msg = String.format("焊装Fb220异常离线回线计划记录成功:内存读表的钢码号:[%s],前置车身钢码:[%s],计划类型为:[%s]", bodySteelCode, preBodySteelCode, planType);
                Log.Write(strServiceCode, LogLevel.Information, msg);

                //3 下达控制命令字 3
                Db.update(ComFun.insertWriteMemoryStatus(),
                        "3",
                        groupMemoryCode
                );

                msg = String.format("焊装Fb220异常离线回线,下达控制命令字[%s]", "3");
                Log.Write(strServiceCode, LogLevel.Information, msg);
                recordMsg.set("msg", msg).set("error", true);

                return true;
            }
        });
        return recordMsg;
    }


}

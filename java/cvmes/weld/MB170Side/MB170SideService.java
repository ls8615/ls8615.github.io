package cvmes.weld.MB170Side;

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
 * 焊装主线MB170计划发送服务
 */

public class MB170SideService extends AbstractSubServiceThread {

    // MB170内存地址组编码
    private final String groupMemoryCode = "MB170.around";

    @Override
    public void initServiceCode() {
        // 服务编码
        this.strServiceCode = "MB170SideService";
    }

    // 业务操作
    @Override
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
            msg = String.format("MB170写表内存地址组【%s】存在未处理指令，请检查OPC通讯是否正常", groupMemoryCode);
            Log.Write(strServiceCode, LogLevel.Error, msg);
            return msg;
        }

        //2.获取读表指令
        List<Record> list_memory_read = Db.find("SELECT T.* FROM T_DEVICE_WELD_READ_RAM T WHERE T.GROUP_MEMORY_CODE =?", groupMemoryCode);
        if (list_memory_read.size() != 1) {
            msg = String.format("读取MB170内存地址组【%s】配置错误，请检查配置", groupMemoryCode);
            Log.Write(strServiceCode, LogLevel.Error, msg);
            return msg;
        }

        //2.1.获取PLC离线状态
        String automatism = list_memory_read.get(0).getStr("AUTOMATISM");
        if (automatism == null) {
            msg = String.format("MB170读表内存地址组【%s】自动标记状态为空，请检查自动标记状态", groupMemoryCode);
            Log.Write(strServiceCode, LogLevel.Information, msg);
            return msg;
        }

        //2.1.1.PLC离线状态，不做处理，结束运行
        if ("1".equals(automatism)) {
            msg = String.format("MB170读表内存地址组【%s】PLC处于离线状态，不下发计划", groupMemoryCode);
            Log.Write(strServiceCode, LogLevel.Information, msg);
            return msg;
        }

        //3.获取控制命令字
        String memoryValue = list_memory_read.get(0).getStr("LOGIC_VALUE");

        switch (memoryValue) {
            case "0":
                return executeOperation(strServiceCode, list_memory_read.get(0)).getStr("msg");
            case "1":
                Log.Write(strServiceCode, LogLevel.Information, String.format("地址组[%s],控制命令字[%s]下位操作。", groupMemoryCode, memoryValue));
                return "";
            case "2":
                return reset(strServiceCode, list_memory_read.get(0)).getStr("msg");
            default:
                msg = String.format("控制命令字未定义，内存地址组[%s],命令字[%s]", groupMemoryCode, msg);
                Log.Write(strServiceCode, LogLevel.Error, msg);
                return msg;
        }
    }

    /**
     * 收到命令字符0 mes操作
     * 发送焊装MB170plc计划
     *
     * @param strServiceCode 服务名
     * @param memoryRead     内存地址组读表值
     */
    private Record executeOperation(String strServiceCode, Record memoryRead) {
        String msg = "";
        Record recordMsg = new Record().set("msg", "").set("error", true);
        Log.Write(strServiceCode, LogLevel.Information, String.format("控制命令字[%s]开始计算", memoryRead.getStr("LOGIC_VALUE")));

        //1.获取焊装MB170plc计划队列
        List<Record> list_manual_plan = Db.find(ComFun.getMB170SidePlan());
        if (list_manual_plan == null || list_manual_plan.size() == 0) {
            msg = "没有获取到焊装MB170计划任务";
            Log.Write(strServiceCode, LogLevel.Warning, msg);
            recordMsg.set("msg", msg);
            return recordMsg;
        }

        //2.获取车身钢码号
        String bodySteelCode = list_manual_plan.get(0).get("CARBODY_CODE") + "";
        if (bodySteelCode.length() == 0 || bodySteelCode.equals("null")) {
            msg = "获取焊装MB170队列车身钢码号的值错误";
            Log.Write(strServiceCode, LogLevel.Warning, msg);
            recordMsg.set("msg", msg);
            return recordMsg;
        }

        //3.获取焊接代码
        String robotlCode = list_manual_plan.get(0).get("ROBOT_CODE") + "";
        if (robotlCode.length() == 0 || robotlCode.equals("null")) {
            msg = "获取焊装MB170队列机器人代码的值错误";
            Log.Write(strServiceCode, LogLevel.Warning, msg);
            recordMsg.set("msg", msg);
            return recordMsg;
        }

        //4.写入到写表
        //4.1转换车身钢码号
        Record recordShorts = ComFun.getMemoryValuesFromVpart(bodySteelCode);

        //4.2转换焊接代码
        Record robotShorts = ComFun.getRobotMemoryValuesFromRobot(robotlCode);

        //4.3写入到写表 将控制命令改成 1
        Db.update(ComFun.getWriteMemorySql(),
                "1",
                bodySteelCode,
                robotlCode,
                recordShorts.getInt("VPART_MEMORY_VALUE1"),
                recordShorts.getInt("VPART_MEMORY_VALUE2"),
                recordShorts.getInt("VPART_MEMORY_VALUE3"),
                recordShorts.getInt("VPART_MEMORY_VALUE4"),
                recordShorts.getInt("VPART_MEMORY_VALUE5"),
                recordShorts.getInt("VPART_MEMORY_VALUE6"),
                recordShorts.getInt("VPART_MEMORY_VALUE7"),
                recordShorts.getInt("VPART_MEMORY_VALUE8"),
                recordShorts.getInt("VPART_MEMORY_VALUE9"),
                recordShorts.getInt("VPART_MEMORY_VALUE10"),
                recordShorts.getInt("VPART_MEMORY_VALUE11"),
                recordShorts.getInt("VPART_MEMORY_VALUE12"),
                recordShorts.getInt("VPART_MEMORY_VALUE13"),
                robotShorts.getInt("ROBOT_MEMORY_VALUE1"),
                robotShorts.getInt("ROBOT_MEMORY_VALUE2"),
                robotShorts.getInt("ROBOT_MEMORY_VALUE3"),
                robotShorts.getInt("ROBOT_MEMORY_VALUE4"),
                robotShorts.getInt("ROBOT_MEMORY_VALUE5"),
                robotShorts.getInt("ROBOT_MEMORY_VALUE6"),
                robotShorts.getInt("ROBOT_MEMORY_VALUE7"),
                robotShorts.getInt("ROBOT_MEMORY_VALUE8"),
                robotShorts.getInt("ROBOT_MEMORY_VALUE9"),
                robotShorts.getInt("ROBOT_MEMORY_VALUE10"),
                robotShorts.getInt("ROBOT_MEMORY_VALUE11"),
                robotShorts.getInt("ROBOT_MEMORY_VALUE12"),
                robotShorts.getInt("ROBOT_MEMORY_VALUE13"),
                robotShorts.getInt("ROBOT_MEMORY_VALUE14"),
                robotShorts.getInt("ROBOT_MEMORY_VALUE15"),
                robotShorts.getInt("ROBOT_MEMORY_VALUE16"),
                robotShorts.getInt("ROBOT_MEMORY_VALUE17"),
                robotShorts.getInt("ROBOT_MEMORY_VALUE18"),
                robotShorts.getInt("ROBOT_MEMORY_VALUE19"),
                robotShorts.getInt("ROBOT_MEMORY_VALUE20"),
                robotShorts.getInt("ROBOT_MEMORY_VALUE21"),
                robotShorts.getInt("ROBOT_MEMORY_VALUE22"),
                robotShorts.getInt("ROBOT_MEMORY_VALUE23"),
                robotShorts.getInt("ROBOT_MEMORY_VALUE24"),
                robotShorts.getInt("ROBOT_MEMORY_VALUE25"),
                robotShorts.getInt("ROBOT_MEMORY_VALUE26"),
                robotShorts.getInt("ROBOT_MEMORY_VALUE27"),
                robotShorts.getInt("ROBOT_MEMORY_VALUE28"),
                robotShorts.getInt("ROBOT_MEMORY_VALUE29"),
                robotShorts.getInt("ROBOT_MEMORY_VALUE30"),
                groupMemoryCode
        );
        msg = String.format("焊装MB170,发送计划,下达控制命令字[%s],钢码号为[%s]",
                "1",
                bodySteelCode);
        Log.Write(strServiceCode, LogLevel.Information, msg);
        recordMsg.set("msg", msg);

        return recordMsg;
    }


    /**
     * 收到命令字符2 mes操作
     * 修改已完成车辆状态
     * 把命令字复位为0，清空钢码号和焊接代码等信息
     *
     * @param strServiceCode 服务名
     * @param memoryRead     内存地址组读表值
     */
    private Record reset(String strServiceCode, Record memoryRead) {
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

        //2.获取焊装MB170plc计划队列
        List<Record> list_manual_plan = Db.find(ComFun.getMB170SidePlan());
        if (list_manual_plan == null || list_manual_plan.size() == 0) {
            msg = "没有获取到焊装MB170计划任务";
            Log.Write(strServiceCode, LogLevel.Warning, msg);
            recordMsg.set("msg", msg);
            return recordMsg;
        }

        //2.1 获取车身钢码号
        String steelCode = list_manual_plan.get(0).get("CARBODY_CODE") + "";
        if (steelCode.length() == 0 || steelCode.equals("null")) {
            msg = "获取焊装MB170队列计划车身钢码号的值错误";
            Log.Write(strServiceCode, LogLevel.Warning, msg);
            recordMsg.set("msg", msg);
            return recordMsg;
        }

        //2.2 校验读表中获取的钢码号和计划队列中钢码号是否一致
        if (!bodySteelCode.equals(steelCode)) {
            msg = String.format("焊装MB170检验钢码号失败：计划队列的刚码号[%s],内存读表的钢码号[%s]", steelCode, bodySteelCode);
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("msg", msg);
            return recordMsg;
        }

        //启用事务
        boolean isSucess = Db.tx(new IAtom() {
            @Override
            public boolean run() throws SQLException {
                String msg = "";
                //2.1 修改完成状态
                int update = Db.update("UPDATE  T_INF_TO_WELD_MB170 SET DEAL_STATUS='1',DEAL_TIME=sysdate WHERE CARBODY_CODE=?", bodySteelCode);
                if (update <= 0) {
                    msg = String.format("焊装MB170计划处理失败:内存读表的钢码号:[%s]", bodySteelCode);
                    Log.Write(strServiceCode, LogLevel.Error, msg);
                    recordMsg.set("msg", msg).set("error", false);
                    return false;
                }
                //2.2
                msg = String.format("焊装MB170计划处理成功:内存读表的钢码号:[%s]", bodySteelCode);
                Log.Write(strServiceCode, LogLevel.Information, msg);

                //3.清空写表数据
                //3.1清空车身钢码号
                Record recordShorts = ComFun.getMemoryValuesFromVpart("");

                //3.2清空焊接代码
                Record robotShorts = ComFun.getRobotMemoryValuesFromRobot("");

                Db.update(ComFun.getWriteMemorySql(),
                        "0",
                        "",
                        "",
                        recordShorts.getInt("VPART_MEMORY_VALUE1"),
                        recordShorts.getInt("VPART_MEMORY_VALUE2"),
                        recordShorts.getInt("VPART_MEMORY_VALUE3"),
                        recordShorts.getInt("VPART_MEMORY_VALUE4"),
                        recordShorts.getInt("VPART_MEMORY_VALUE5"),
                        recordShorts.getInt("VPART_MEMORY_VALUE6"),
                        recordShorts.getInt("VPART_MEMORY_VALUE7"),
                        recordShorts.getInt("VPART_MEMORY_VALUE8"),
                        recordShorts.getInt("VPART_MEMORY_VALUE9"),
                        recordShorts.getInt("VPART_MEMORY_VALUE10"),
                        recordShorts.getInt("VPART_MEMORY_VALUE11"),
                        recordShorts.getInt("VPART_MEMORY_VALUE12"),
                        recordShorts.getInt("VPART_MEMORY_VALUE13"),
                        robotShorts.getInt("ROBOT_MEMORY_VALUE1"),
                        robotShorts.getInt("ROBOT_MEMORY_VALUE2"),
                        robotShorts.getInt("ROBOT_MEMORY_VALUE3"),
                        robotShorts.getInt("ROBOT_MEMORY_VALUE4"),
                        robotShorts.getInt("ROBOT_MEMORY_VALUE5"),
                        robotShorts.getInt("ROBOT_MEMORY_VALUE6"),
                        robotShorts.getInt("ROBOT_MEMORY_VALUE7"),
                        robotShorts.getInt("ROBOT_MEMORY_VALUE8"),
                        robotShorts.getInt("ROBOT_MEMORY_VALUE9"),
                        robotShorts.getInt("ROBOT_MEMORY_VALUE10"),
                        robotShorts.getInt("ROBOT_MEMORY_VALUE11"),
                        robotShorts.getInt("ROBOT_MEMORY_VALUE12"),
                        robotShorts.getInt("ROBOT_MEMORY_VALUE13"),
                        robotShorts.getInt("ROBOT_MEMORY_VALUE14"),
                        robotShorts.getInt("ROBOT_MEMORY_VALUE15"),
                        robotShorts.getInt("ROBOT_MEMORY_VALUE16"),
                        robotShorts.getInt("ROBOT_MEMORY_VALUE17"),
                        robotShorts.getInt("ROBOT_MEMORY_VALUE18"),
                        robotShorts.getInt("ROBOT_MEMORY_VALUE19"),
                        robotShorts.getInt("ROBOT_MEMORY_VALUE20"),
                        robotShorts.getInt("ROBOT_MEMORY_VALUE21"),
                        robotShorts.getInt("ROBOT_MEMORY_VALUE22"),
                        robotShorts.getInt("ROBOT_MEMORY_VALUE23"),
                        robotShorts.getInt("ROBOT_MEMORY_VALUE24"),
                        robotShorts.getInt("ROBOT_MEMORY_VALUE25"),
                        robotShorts.getInt("ROBOT_MEMORY_VALUE26"),
                        robotShorts.getInt("ROBOT_MEMORY_VALUE27"),
                        robotShorts.getInt("ROBOT_MEMORY_VALUE28"),
                        robotShorts.getInt("ROBOT_MEMORY_VALUE29"),
                        robotShorts.getInt("ROBOT_MEMORY_VALUE30"),
                        groupMemoryCode
                );

                msg = String.format("焊装MB170清空下位数据,下达控制命令字[%s]", "0");
                Log.Write(strServiceCode, LogLevel.Information, msg);
                recordMsg.set("msg", msg).set("error", true);

                return true;
            }
        });

        return recordMsg;
    }


}

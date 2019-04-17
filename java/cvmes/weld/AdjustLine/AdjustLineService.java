package cvmes.weld.AdjustLine;

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
 * 焊装调整线打刻机计划发送服务
 */
public class AdjustLineService extends AbstractSubServiceThread {

    // 打码机内存地址组编码
    private final String groupMemoryCode = "adjustLine.mark";

    @Override
    public void initServiceCode() {
        // 服务编码
        this.strServiceCode = "AdjustLineService";
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
            msg = String.format("打码机写表内存地址组【%s】存在未处理指令，请检查OPC通讯是否正常", groupMemoryCode);
            Log.Write(strServiceCode, LogLevel.Error, msg);
            return msg;
        }

        //2.获取读表指令
        List<Record> list_memory_read = Db.find("SELECT T.* FROM T_DEVICE_WELD_READ_RAM T WHERE T.GROUP_MEMORY_CODE =?", groupMemoryCode);
        if (list_memory_read.size() != 1) {
            msg = String.format("读取打码机内存地址组【%s】配置错误，请检查配置", groupMemoryCode);
            Log.Write(strServiceCode, LogLevel.Error, msg);
            return msg;
        }

        //2.1.获取PLC离线状态
        String automatism = list_memory_read.get(0).getStr("AUTOMATISM");
        if (automatism == null) {
            msg = String.format("打码机读表内存地址组【%s】自动标记状态为空，请检查自动标记状态", groupMemoryCode);
            Log.Write(strServiceCode, LogLevel.Information, msg);
            return msg;
        }

        //2.1.1.PLC离线状态，不做处理，结束运行
        if ("1".equals(automatism)) {
            msg = String.format("打码机读表内存地址组【%s】PLC处于离线状态，不下发计划", groupMemoryCode);
            Log.Write(strServiceCode, LogLevel.Information, msg);
            return msg;
        }

        //3.获取控制命令字
        String memoryValue = list_memory_read.get(0).getStr("LOGIC_VALUE");
        switch (memoryValue) {
            //下位操作
            case "0":
                Log.Write(strServiceCode, LogLevel.Information, String.format("地址组[%s],控制命令字[%s]下位操作。", groupMemoryCode, memoryValue));
                return "";
            case "2":
                Log.Write(strServiceCode, LogLevel.Information, String.format("地址组[%s],控制命令字[%s]下位操作。", groupMemoryCode, memoryValue));
                return "";
            case "3":
                Log.Write(strServiceCode, LogLevel.Information, String.format("地址组[%s],控制命令字[%s]下位操作。", groupMemoryCode, memoryValue));
                return "";
            case "4":
                Log.Write(strServiceCode, LogLevel.Information, String.format("地址组[%s],控制命令字[%s]下位操作。", groupMemoryCode, memoryValue));
                return "";

            //发送白车身图号
            case "1":
                return executeOperation(strServiceCode, list_memory_read.get(0)).getStr("msg");

            //清空下位数据
            case "5":
                return reset(strServiceCode, list_memory_read.get(0)).getStr("msg");
            default:
                msg = String.format("控制命令字未定义，内存地址组[%s],命令字[%s]", groupMemoryCode, msg);
                Log.Write(strServiceCode, LogLevel.Error, msg);
                return msg;
        }
    }


    /**
     * 收到命令字符1 mes操作
     * A:验证车身钢码是否打刻过，若打刻过，把命令字改为3；若未打刻过，进行下步验证。
     * B:与计划队列进行验证。若验证成功，则写入对应车身白件图号，并把命令字改为2；
     * C:若验证失败，把MES验证的待打刻车身钢码写入，且命令字改为4
     *
     * @param strServiceCode 服务名
     * @param memoryRead     内存地址组读表值
     */
    private Record executeOperation(String strServiceCode, Record memoryRead) {
        String msg = "";
        Record recordMsg = new Record().set("msg", "").set("error", true);
        Log.Write(strServiceCode, LogLevel.Information, String.format("控制命令字[%s]开始计算", memoryRead.getStr("LOGIC_VALUE")));

        //1. 获取读表钢码号
        String bodySteelCode = ComFun.getVpartFromMemoryValues(memoryRead);
        if (("null").equals(bodySteelCode) || bodySteelCode.length() == 0) {
            msg = "获取读表车身钢码号的值错误";
            Log.Write(strServiceCode, LogLevel.Warning, msg);
            recordMsg.set("msg", msg);
            return recordMsg;
        }

        //2.验证车身钢码是否打刻
        List<Record> isEngrave = Db.find("SELECT T.IS_ENGRAVE FROM T_INF_TO_WELD_MSIDE T WHERE T.STEEL_CODE = ?", bodySteelCode);
        if (isEngrave == null || isEngrave.size() == 0) {
            msg = String.format("未获取到该辆车打刻状态，钢码号为[%s]", bodySteelCode);
            Log.Write(strServiceCode, LogLevel.Warning, msg);
            recordMsg.set("msg", msg);
            return recordMsg;
        }

        //2.1 获取该车身钢码打刻状态
        String isEngraving = isEngrave.get(0).getStr("IS_ENGRAVE");
        if (isEngraving == null || isEngraving.length() == 0) {
            msg = String.format("打刻状态为空，钢码号为[%s]", bodySteelCode);
            Log.Write(strServiceCode, LogLevel.Warning, msg);
            recordMsg.set("msg", msg);
            return recordMsg;
        }

        //2.2 如果打刻过发送命令字 3
        if (("1").equals(isEngraving)) {
            //2.2.1  转换车身钢码号
            Record recordShorts = ComFun.getMemoryValuesFromVpart(bodySteelCode);

            //2.2.2转换待打刻车身钢码
            Record engravedShorts = ComFun.getMemoryValuesFromEngraved("");

            //2.2.3 转换车身白件图号
            Record whitedrawingShorts = ComFun.getMemoryValuesFroWhitedrawing("");

            //2.2.3 写入写表
            Db.update(ComFun.getWriteMemorySql(),
                    "3",
                    bodySteelCode,
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
                    engravedShorts.getInt("ENGRAVED_MEMORY_VALUE1"),
                    engravedShorts.getInt("ENGRAVED_MEMORY_VALUE2"),
                    engravedShorts.getInt("ENGRAVED_MEMORY_VALUE3"),
                    engravedShorts.getInt("ENGRAVED_MEMORY_VALUE4"),
                    engravedShorts.getInt("ENGRAVED_MEMORY_VALUE5"),
                    engravedShorts.getInt("ENGRAVED_MEMORY_VALUE6"),
                    engravedShorts.getInt("ENGRAVED_MEMORY_VALUE7"),
                    engravedShorts.getInt("ENGRAVED_MEMORY_VALUE8"),
                    engravedShorts.getInt("ENGRAVED_MEMORY_VALUE9"),
                    engravedShorts.getInt("ENGRAVED_MEMORY_VALUE10"),
                    engravedShorts.getInt("ENGRAVED_MEMORY_VALUE11"),
                    engravedShorts.getInt("ENGRAVED_MEMORY_VALUE12"),
                    engravedShorts.getInt("ENGRAVED_MEMORY_VALUE13"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE1"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE2"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE3"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE4"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE5"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE6"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE7"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE8"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE9"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE10"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE11"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE12"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE13"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE14"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE15"),
                    groupMemoryCode
            );

            msg = String.format("车身钢码已打刻过，钢码号为[%s]，发送命令字符[%s]", bodySteelCode, "3");
            Log.Write(strServiceCode, LogLevel.Warning, msg);
            recordMsg.set("msg", msg);
            return recordMsg;
        }

        //3 获取队列中待打刻车身钢码与车身图号
        List<Record> records = Db.find(ComFun.getToBeEngravedSql());
        if (records == null || records.size() == 0) {
            msg = "获取队列中待打刻车身钢码与车身图号失败";
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("msg", msg);
            return recordMsg;
        }

        //3.1 获取队列中待打刻车身钢码
        String steelCode = records.get(0).getStr("STEEL_CODE");
        if (steelCode == null || steelCode.length() == 0) {
            msg = "获取队列中待打刻车身钢码的值错误";
            Log.Write(strServiceCode, LogLevel.Warning, msg);
            recordMsg.set("msg", msg);
            return recordMsg;
        }

        //3.2 获取队列中待打刻白车身图号
        String carbodyFigureCode = records.get(0).getStr("CARBODY_FIGURE_CODE");
        if (carbodyFigureCode == null || carbodyFigureCode.length() == 0) {
            msg = "获取队列中待打刻白车身图号的值错误";
            Log.Write(strServiceCode, LogLevel.Warning, msg);
            recordMsg.set("msg", msg);
            return recordMsg;
        }

        //4 读表钢码和队列钢码验证
        if (steelCode.equals(bodySteelCode)) {
            //4.1如果一致，写入车身白件图号，发送命令 2
            //4.1.1  转换车身钢码号
            Record recordShorts = ComFun.getMemoryValuesFromVpart(bodySteelCode);

            //4.1.2转换待打刻车身钢码
            Record engravedShorts = ComFun.getMemoryValuesFromEngraved("");

            //4.1.3 转换车身白件图号
            Record whitedrawingShorts = ComFun.getMemoryValuesFroWhitedrawing(carbodyFigureCode);

            //4.1.3 写入写表
            Db.update(ComFun.getWriteMemorySql(),
                    "2",
                    bodySteelCode,
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
                    engravedShorts.getInt("ENGRAVED_MEMORY_VALUE1"),
                    engravedShorts.getInt("ENGRAVED_MEMORY_VALUE2"),
                    engravedShorts.getInt("ENGRAVED_MEMORY_VALUE3"),
                    engravedShorts.getInt("ENGRAVED_MEMORY_VALUE4"),
                    engravedShorts.getInt("ENGRAVED_MEMORY_VALUE5"),
                    engravedShorts.getInt("ENGRAVED_MEMORY_VALUE6"),
                    engravedShorts.getInt("ENGRAVED_MEMORY_VALUE7"),
                    engravedShorts.getInt("ENGRAVED_MEMORY_VALUE8"),
                    engravedShorts.getInt("ENGRAVED_MEMORY_VALUE9"),
                    engravedShorts.getInt("ENGRAVED_MEMORY_VALUE10"),
                    engravedShorts.getInt("ENGRAVED_MEMORY_VALUE11"),
                    engravedShorts.getInt("ENGRAVED_MEMORY_VALUE12"),
                    engravedShorts.getInt("ENGRAVED_MEMORY_VALUE13"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE1"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE2"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE3"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE4"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE5"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE6"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE7"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE8"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE9"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE10"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE11"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE12"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE13"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE14"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE15"),
                    groupMemoryCode
            );

            msg = String.format("发送计划，写入白车身图号[%s]，钢码号为[%s]，发送命令字符[%s]", carbodyFigureCode, bodySteelCode, "2");
            Log.Write(strServiceCode, LogLevel.Information, msg);
            recordMsg.set("msg", msg);
        } else {
            //4.2 如果不一致，写入待打刻车身钢码，发送命令 4
            //4.2.1  转换车身钢码号
            Record recordShorts = ComFun.getMemoryValuesFromVpart(bodySteelCode);

            //4.2.2转换待打刻车身钢码
            Record engravedShorts = ComFun.getMemoryValuesFromEngraved(steelCode);

            //4.2.3 转换车身白件图号
            Record whitedrawingShorts = ComFun.getMemoryValuesFroWhitedrawing("");

            //4.2.3 写入写表
            Db.update(ComFun.getWriteMemorySql(),
                    "4",
                    bodySteelCode,
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
                    engravedShorts.getInt("ENGRAVED_MEMORY_VALUE1"),
                    engravedShorts.getInt("ENGRAVED_MEMORY_VALUE2"),
                    engravedShorts.getInt("ENGRAVED_MEMORY_VALUE3"),
                    engravedShorts.getInt("ENGRAVED_MEMORY_VALUE4"),
                    engravedShorts.getInt("ENGRAVED_MEMORY_VALUE5"),
                    engravedShorts.getInt("ENGRAVED_MEMORY_VALUE6"),
                    engravedShorts.getInt("ENGRAVED_MEMORY_VALUE7"),
                    engravedShorts.getInt("ENGRAVED_MEMORY_VALUE8"),
                    engravedShorts.getInt("ENGRAVED_MEMORY_VALUE9"),
                    engravedShorts.getInt("ENGRAVED_MEMORY_VALUE10"),
                    engravedShorts.getInt("ENGRAVED_MEMORY_VALUE11"),
                    engravedShorts.getInt("ENGRAVED_MEMORY_VALUE12"),
                    engravedShorts.getInt("ENGRAVED_MEMORY_VALUE13"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE1"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE2"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE3"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE4"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE5"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE6"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE7"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE8"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE9"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE10"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE11"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE12"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE13"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE14"),
                    whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE15"),
                    groupMemoryCode
            );

            msg = String.format("钢码与计划队列验证失败,plc发送刚码[%s]，写入的待打刻钢码号[%s]，发送命令字符[%s]", bodySteelCode, steelCode, "4");
            Log.Write(strServiceCode, LogLevel.Warning, msg);
            recordMsg.set("msg", msg);
        }

        return recordMsg;
    }


    /**
     * 收到命令5 打码成功
     * 把命令字改为0，清空车身钢码、白件图号、MES待打刻钢码。
     *
     * @param strServiceCode 服务名
     * @param memoryRead     内存地址组读表值
     */
    private Record reset(String strServiceCode, Record memoryRead) {
        String msg = "";
        Record recordMsg = new Record().set("msg", "").set("error", true);
        Log.Write(strServiceCode, LogLevel.Information, String.format("控制命令字[%s]开始计算", memoryRead.getStr("LOGIC_VALUE")));

        //1. 获取读表钢码号
        String bodySteelCode = ComFun.getVpartFromMemoryValues(memoryRead);
        if (("null").equals(bodySteelCode) || bodySteelCode.length() == 0) {
            msg = "获取读表车身钢码号的值错误";
            Log.Write(strServiceCode, LogLevel.Warning, msg);
            recordMsg.set("msg", msg);
            return recordMsg;
        }

        //启用事务
        boolean isSucess = Db.tx(new IAtom() {
            @Override
            public boolean run() throws SQLException {
                String msg = "";
                //2 修改打刻状态
                int update = Db.update("UPDATE T_INF_TO_WELD_MSIDE SET is_ENGRAVE = 1 WHERE STEEL_CODE=?",
                        bodySteelCode);
                if (update <= 0) {
                    msg = String.format("焊装调整线打码机计划处理失败:内存读表的钢码号:[%s]", bodySteelCode);
                    Log.Write(strServiceCode, LogLevel.Error, msg);
                    recordMsg.set("msg", msg).set("error", false);
                    return false;
                }

                //2.1 修改处理状态成功
                msg = String.format("焊装调整线打码机计划处理成功:内存读表的钢码号:[%s]", bodySteelCode);
                Log.Write(strServiceCode, LogLevel.Information, msg);

                //3 下达控制命令字 0
                //3.1.1  转换车身钢码号
                Record recordShorts = ComFun.getMemoryValuesFromVpart("");

                //3.1.2转换待打刻车身钢码
                Record engravedShorts = ComFun.getMemoryValuesFromEngraved("");

                //3.1.3 转换车身白件图号
                Record whitedrawingShorts = ComFun.getMemoryValuesFroWhitedrawing("");

                //3.1.3 写入写表
                Db.update(ComFun.getWriteMemorySql(),
                        "0",
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
                        engravedShorts.getInt("ENGRAVED_MEMORY_VALUE1"),
                        engravedShorts.getInt("ENGRAVED_MEMORY_VALUE2"),
                        engravedShorts.getInt("ENGRAVED_MEMORY_VALUE3"),
                        engravedShorts.getInt("ENGRAVED_MEMORY_VALUE4"),
                        engravedShorts.getInt("ENGRAVED_MEMORY_VALUE5"),
                        engravedShorts.getInt("ENGRAVED_MEMORY_VALUE6"),
                        engravedShorts.getInt("ENGRAVED_MEMORY_VALUE7"),
                        engravedShorts.getInt("ENGRAVED_MEMORY_VALUE8"),
                        engravedShorts.getInt("ENGRAVED_MEMORY_VALUE9"),
                        engravedShorts.getInt("ENGRAVED_MEMORY_VALUE10"),
                        engravedShorts.getInt("ENGRAVED_MEMORY_VALUE11"),
                        engravedShorts.getInt("ENGRAVED_MEMORY_VALUE12"),
                        engravedShorts.getInt("ENGRAVED_MEMORY_VALUE13"),
                        whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE1"),
                        whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE2"),
                        whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE3"),
                        whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE4"),
                        whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE5"),
                        whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE6"),
                        whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE7"),
                        whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE8"),
                        whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE9"),
                        whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE10"),
                        whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE11"),
                        whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE12"),
                        whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE13"),
                        whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE14"),
                        whitedrawingShorts.getInt("WHITEDRAWING_MEMORY_VALUE15"),
                        groupMemoryCode
                );


                msg = String.format("焊装调整线打码机清空下位数据,下达控制命令字[%s]", "0");
                Log.Write(strServiceCode, LogLevel.Information, msg);
                recordMsg.set("msg", msg).set("error", true);

                return true;
            }
        });
        return recordMsg;
    }

}

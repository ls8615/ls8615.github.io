package cvmes.wbs.rfidTransfer;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.IAtom;
import com.jfinal.plugin.activerecord.ICallback;
import com.jfinal.plugin.activerecord.Record;
import cvmes.common.AbstractSubServiceThread;
import cvmes.common.Log;
import cvmes.common.LogLevel;
import oracle.jdbc.OracleTypes;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class RfidTransferService extends AbstractSubServiceThread {

    final String groupMemoryCode = "rfid.transfer";

    @Override
    public void initServiceCode() {
        this.strServiceCode = "RfidTransferService";
    }

    @Override
    public String runBll(Record rec_service) throws Exception {

        String msg = "";

        //0.获取WBS内存地址写表指令
        List<Record> list_memory_write = Db.find("SELECT 1 FROM T_WBS_WRITE_MEMORY wm where wm.GROUP_MEMORY_CODE=? AND wm.DEAL_STATUS=0", groupMemoryCode);
        if (list_memory_write.size() != 0) {
            msg = String.format("wbs写表内存地址组【%s】存在未处理指令，请检查OPC通讯是否正常", groupMemoryCode);
            Log.Write(strServiceCode, LogLevel.Error, msg);
            return msg;
        }

        //1.获取WBS内存地址读表值
        List<Record> list_memory_read = Db.find("SELECT rm.* FROM T_WBS_READ_MEMORY rm where rm.GROUP_MEMORY_CODE=?", groupMemoryCode);
        if (list_memory_read.size() != 1) {
            msg = String.format("读取wbs内存地址组【%s】配置错误，请检查配置", groupMemoryCode);
            Log.Write(strServiceCode, LogLevel.Error, msg);
            return msg;
        }

        //1.1.获取WBS内存地址读表值——控制命令字值
        String memoryValue = list_memory_read.get(0).getStr("LOGIC_MEMORY_VALUE");
        switch (memoryValue) {
            case "0":
            case "2":
                Log.Write(strServiceCode, LogLevel.Information, String.format("地址组[%s],控制命令字[%s]下位操作。", groupMemoryCode, memoryValue));
                return "";
            case "1":
                return dealVerification(rec_service, list_memory_read.get(0)).getStr("msg");
            case "3":
                return dealPassed(rec_service, list_memory_read.get(0)).getStr("msg");
            case "99":
                msg = String.format("支撑号校验失败");
                Log.Write(strServiceCode, LogLevel.Error, msg);
                return msg;
            default:
                msg = String.format("控制命令字[%s]错误，命令字没有定义", memoryValue);
                Log.Write(strServiceCode, LogLevel.Error, msg);
                return msg;


        }
    }

    /**
     * 支腿号校验
     *
     * @param rec_service
     * @param memoryRead
     * @return
     */
    private Record dealVerification(Record rec_service, Record memoryRead) {
        //计算结果信息
        String msg, cmdMsg = "";
        Record recordMsg = new Record().set("msg", "").set("error", true);
        Log.Write(strServiceCode, LogLevel.Information, String.format("控制命令字[%s]开始计算", memoryRead.getStr("LOGIC_MEMORY_VALUE")));

        //1.根据内存地址组的值，获取白车身钢码号
        String vpartCode = RfidTransferComFun.getVpartFromMemoryValues(memoryRead);
        if (vpartCode == null || vpartCode.length() == 0 || vpartCode.equals("null")) {
            msg = String.format("根据内存地址组的值[%s],获取白车身钢码号错误", memoryRead.toJson());
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("msg", msg).set("error", false);
            return recordMsg;
        }

        //2.获取需求产品表信息
        List<Record> list_demand_prodect = Db.find(RfidTransferComFun.getDemandProductInfoFromVpartCodeCodeSql(), vpartCode);
        if (list_demand_prodect.size() != 1) {
            msg = String.format("根据白车身钢码号[%s],获取需求产品信息异常", vpartCode);
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("msg", msg).set("error", false);
            return recordMsg;
        }

        //2.1.获取生产编码
        String productionCode = list_demand_prodect.get(0).getStr("PRODUCTION_CODE");

        //3.根据内存地址值，获取支撑号Short
        String memoryValueSupportNo = RfidTransferComFun.getSupperNoFromReadMemoryValue(memoryRead);

        //4.判断系统参数运行状态
        if (!RfidTransferComFun.judgeRunStatus(strServiceCode, "wbs_rfid_status")) {
            //服务暂停
            return recordMsg.set("msg", "服务暂停计算").set("error", false);
        }

        //5.判断是否跳过支撑号校验
        String carbodyCode = list_demand_prodect.get(0).getStr("K_CARBODY_CODE");
        String supperNo = "";
        if (rec_service.getStr("SERVICE_PARA1_VALUE").equals("1")) {

            //5.1.获取白车身图号
            if (carbodyCode == null || carbodyCode.length() == 0) {
                msg = String.format("根据白车身钢码号[%s],获取白车身图号[%s]信息异常", vpartCode, carbodyCode);
                Log.Write(strServiceCode, LogLevel.Error, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
            }

            //5.2.获取机器人代码支撑号
            List<Record> listSupperNo = Db.find(RfidTransferComFun.getSupperNoFromCarBoyCode(), carbodyCode);
            if (listSupperNo.size() == 0) {
                msg = String.format("根据白车身图号[%s],无法获取机器人代码中支撑号信息", carbodyCode);
                Log.Write(strServiceCode, LogLevel.Error, msg);
                recordMsg.set("msg", msg).set("error", false);
                return recordMsg;
            }

            //5.3.获取机器人代码支撑号
            supperNo = listSupperNo.get(0).getStr("SUPPORT_NO");
            if (supperNo == null || supperNo.length() == 0) {
                msg = String.format("根据白车身图号[%s]，获取机器人代码支撑号[%s]异常", carbodyCode, supperNo);
                Log.Write(strServiceCode, LogLevel.Error, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
            }

            //5.4.支撑号校验不一致
            if (!supperNo.equals(memoryValueSupportNo)) {
                msg = String.format("支撑号信息校验不一致，车身钢码号[%s],支撑号[%],机器人代码支撑号[%s]", vpartCode, memoryValueSupportNo, supperNo);
                Log.Write(strServiceCode, LogLevel.Error, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
            }
        }

        //6.校验车辆是否已经在wbs区域内
        //6.1.获取wbs区域信息
        List<Record> listZone = Db.find("SELECT 1 ICNT FROM t_model_zone t WHERE t.production_code=? AND t.workshop_code='wbs01' AND t.zone_code!='wbs02'", productionCode);

        //6.1.1.获取区域信息异常
        if (listZone.size() != 0) {
            msg = String.format("转接车辆信息异常，车辆已经在WBS区域中，钢码号[%s],生产编码[%s]", vpartCode, productionCode);
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("msg", msg);
            return recordMsg;
        }

        //7.下达指令
        int cmd = 2;
        Record recordShorts = RfidTransferComFun.getMemoryValuesFromVpart(vpartCode, memoryValueSupportNo);
        Db.update(RfidTransferComFun.getWriteMemorySql(),
                cmd,
                vpartCode,
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
                recordShorts.getInt("SUPPORT_NO"),
                groupMemoryCode
        );

        msg = String.format("车身钢码号[%s],生产编码[%s],白车身图号[%s],支撑号[%s]，机器人代码支撑号[%s]",
                vpartCode, productionCode, carbodyCode, memoryValueSupportNo, supperNo);
        msg = String.format("%s,%s,下达命令字[%s]", msg, supperNo == "" ? "跳过支撑号校验" : "支撑号校验一致", cmd);
        Log.Write(strServiceCode, LogLevel.Information, msg);
        recordMsg.set("msg", msg);
        return recordMsg;
    }

    /**
     * REID转接，车辆离开
     *
     * @param rec_service
     * @param memoryRead
     * @return
     */
    private Record dealPassed(Record rec_service, Record memoryRead) {
        //计算结果信息
        String msg, cmdMsg = "";
        Record recordMsg = new Record().set("msg", "").set("error", true);
        Log.Write(strServiceCode, LogLevel.Information, String.format("控制命令字[%s]开始计算", memoryRead.getStr("LOGIC_MEMORY_VALUE")));

        //1.根据内存地址组的值，获取白车身钢码号
        String vpartCode = RfidTransferComFun.getVpartFromMemoryValues(memoryRead);
        if (vpartCode == null || vpartCode.length() == 0 || vpartCode.equals("null")) {
            msg = String.format("根据内存地址组的值[%s],获取白车身钢码号错误", memoryRead.toJson());
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("msg", msg).set("error", false);
            return recordMsg;
        }

        //2.获取需求产品表信息
        List<Record> list_demand_prodect = Db.find(RfidTransferComFun.getDemandProductInfoFromVpartCodeCodeSql(), vpartCode);
        if (list_demand_prodect.size() != 1) {
            msg = String.format("根据白车身钢码号[%s],获取需求产品信息异常", vpartCode);
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("msg", msg).set("error", false);
            return recordMsg;
        }

        //2.1.获取生产编码
        String productionCode = list_demand_prodect.get(0).getStr("PRODUCTION_CODE");

        //3.移动车辆道入库缓存道（wbs14）
        String dest_zone_code = "wbs14";
        msg = (String) Db.execute(new ICallback() {
            @Override
            public Object call(Connection conn) throws SQLException {
                //执行搬出程序结果信息
                String msg = "";

                CallableStatement proc = conn.prepareCall("{CALL PROC_MOVE_CAR(?,?,?,?)}");
                proc.setString("PARA_PRODUCTION_CODE", productionCode);
                proc.setString("PARA_DEST_ZONE", dest_zone_code);
                proc.setInt("PARA_IS_CHECK_POS", 1);
                proc.registerOutParameter("PARA_MSG", OracleTypes.VARCHAR);
                proc.execute();

                String ret = proc.getString("PARA_MSG");
                if (ret == null || ret.length() == 0 || ret.equals("null")) {
                    msg = "";
                } else {
                    if (ret.length() > 0) {
                        msg = ret;
                    }
                }

                return msg;
            }
        });
        if (msg.length() == 0) {
            msg = String.format("般车成功，把车[%s],般道区域[%s]", productionCode, dest_zone_code);
            Log.Write(strServiceCode, LogLevel.Information, msg);
        } else {
            msg = String.format("把车[%s],般道区域[%s]失败，原因[%s]", productionCode, dest_zone_code, msg);
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("msg", msg).set("error", false);
            return recordMsg;
        }

        Db.tx(new IAtom() {
            @Override
            public boolean run() throws SQLException {
                String msg = "";
                //4.下达指令（写0，清空钢码号和支撑号）
                int cmd = 0;
                Record recordShorts = RfidTransferComFun.getMemoryValuesFromVpart("", "");
                Db.update(RfidTransferComFun.getWriteMemorySql(),
                        cmd,
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
                        recordShorts.getInt("SUPPORT_NO"),
                        groupMemoryCode
                );

                msg = String.format("RFID转接，车辆[%s]离开处理成功，下达命令字[%s]", vpartCode, cmd);
                Log.Write(strServiceCode, LogLevel.Information, msg);
                recordMsg.set("msg", msg);

                //5.写过点记录
                Record recordPassedRec = new Record();
                //5.1.设置生产编码
                recordPassedRec.set("PRODUCTION_CODE", productionCode);
                //5.2.设置生产线编码
                recordPassedRec.set("LINE_CODE", "tz0101");
                //5.3.设置需求产品比那吗
                recordPassedRec.set("DEMAND_PRODUCT_CODE", list_demand_prodect.get(0).getStr("DEMAND_PRODUCT_CODE"));
                //5.4.设置实绩点编码
                recordPassedRec.set("ACTUAL_POINT_CODE", "wbs01_in");

                Db.update(RfidTransferComFun.getInsertPassedRecord(),
                        recordPassedRec.getStr("PRODUCTION_CODE"),
                        recordPassedRec.getStr("LINE_CODE"),
                        recordPassedRec.getStr("LINE_CODE"),
                        recordPassedRec.getStr("DEMAND_PRODUCT_CODE"),
                        recordPassedRec.getStr("ACTUAL_POINT_CODE")
                );

                return true;
            }
        });


        return recordMsg;
    }
}

package cvmes.wbs.exportTurntable;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.ICallback;
import com.jfinal.plugin.activerecord.Record;
import cvmes.common.AbstractSubServiceThread;
import cvmes.common.Log;
import cvmes.common.LogLevel;
import cvmes.wbs.shiftingmMachineOutOne.ShiftingmMachineOutOneComFun;
import oracle.jdbc.OracleTypes;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * 子服务务实现类：
 * wbs出口转盘
 */
public class ExportTurntableService extends AbstractSubServiceThread {

    // 内存地址组
    private final String groupMemoryCode = "export.turntable";

    @Override
    public void initServiceCode() {
        // 服务编码
        strServiceCode = "ExportTurntableService";
    }

    // 业务操作
    @Override
    public String runBll(Record rec_service) {
        return DealHeader(rec_service);
    }

    /**
     * 逻辑处理入口
     */
    private String DealHeader(Record rec_service) {
        //接车计算结果信息
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

        //2.根据控制命令字处理
        switch (memoryValue) {
            //2.0.下位操作，MES不做处理
            case "1":
            case "2":
                Log.Write(strServiceCode, LogLevel.Information, String.format("地址组[%s],控制命令字[%s]下位操作。", groupMemoryCode, memoryValue));
                return "";
            //2.1.接车计算
            case "0":
                if (!ShiftingmMachineOutOneComFun.judgeRunStatus(strServiceCode, "wbs_out_turntable")) {
                    //服务暂停
                    return "服务暂停计算";
                }

                return pickCar(strServiceCode, rec_service, list_memory_read.get(0)).getStr("msg");

            case "3":
                return passedDeal(strServiceCode, rec_service, list_memory_read.get(0)).getStr("msg");

            default:
                msg = String.format("计算错误，命令字未定义，内存地址组[%s],命令字值[%s]", groupMemoryCode, memoryValue);
                Log.Write(strServiceCode, LogLevel.Error, msg);
                return msg;
        }
    }

    /**
     * 出口转盘接车计算：
     * A:判断参数1的值
     * 0：双向接车，即优先判断出口1号移行机命令字是否为21，如果是，则到出口1号移行机接车，
     * 否则判断入库缓存道（wbs14）是否有车，如果有车，则接入口缓存道；
     * 1：直接出口1号移行机，即出口1号移行机命令字为21时，到出口1号移行机接车，其他情况不接车。
     *
     * @param memoryRead 内存地址组读表值
     */
    private Record pickCar(String strServiceCode, Record rec_service, Record memoryRead) {
        //计算结果信息
        String msg, cmdMsg = "";
        Record recordMsg = new Record().set("msg", "").set("error", true);
        Log.Write(strServiceCode, LogLevel.Information, String.format("控制命令字[%s]开始计算", memoryRead.getStr("LOGIC_MEMORY_VALUE")));

        //1.获取出口1号移行机内存地址组的值
        List<Record> list_memory_read = Db.find("SELECT rm.* FROM T_WBS_READ_MEMORY rm where rm.GROUP_MEMORY_CODE=?", "no1.out.shifting.machine");
        if (list_memory_read.size() != 1) {
            msg = String.format("读取wbs内存地址组【%s】配置错误，请检查配置", groupMemoryCode);
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg = new Record().set("msg", msg).set("error", false);
            return recordMsg;
        }

        //1.1.判断出口1号移行机是否允许接车
        if (list_memory_read.get(0).getStr("LOGIC_MEMORY_VALUE").equals("21")) {
            //1.根据内存地址组的值，获取白车身钢码号
            String vpartCode = ShiftingmMachineOutOneComFun.getVpartFromMemoryValues(list_memory_read.get(0));
            if (vpartCode == null || vpartCode.length() == 0 || vpartCode.equals("null")) {
                msg = String.format("根据内存地址组的值[%s],获取白车身钢码号错误", list_memory_read.get(0).toJson());
                Log.Write(strServiceCode, LogLevel.Error, msg);
                recordMsg.set("msg", msg).set("error", false);
                return recordMsg;
            }

            //3.下达指令
            Record recordShorts = ShiftingmMachineOutOneComFun.getMemoryValuesFromVpart(vpartCode);
            Db.update(ShiftingmMachineOutOneComFun.getWriteMemorySql(),
                    1,
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
                    groupMemoryCode
            );

            msg = String.format("接出口1号移行机,钢码号[%s],下达指令[%s]", vpartCode, 1);
            Log.Write(strServiceCode, LogLevel.Information, msg);
            recordMsg.set("msg", msg).set("error", true);
            return recordMsg;

        }

        //1.2.判断出口1号移行机是等待出口转盘接车
        if (list_memory_read.get(0).getStr("LOGIC_MEMORY_VALUE").equals("16")) {
            //1.2.1.根据内存地址组的值，获取白车身钢码号
            String vpartCode = ShiftingmMachineOutOneComFun.getVpartFromMemoryValues(list_memory_read.get(0));
            if (vpartCode == null || vpartCode.length() == 0 || vpartCode.equals("null")) {
                msg = String.format("根据内存地址组的值[%s],获取白车身钢码号错误", list_memory_read.get(0).toJson());
                Log.Write(strServiceCode, LogLevel.Error, msg);
                recordMsg.set("msg", msg).set("error", false);
                return recordMsg;
            }

            //1.2.2.获取需求产品信息
            List<Record> list_product = Db.find(ShiftingmMachineOutOneComFun.getDemandProductInfoFromVpartCodeCodeSql(), vpartCode);
            if (list_product.size() != 1) {
                msg = String.format("根据生产编码[%s],获取需求产品信息异常", vpartCode);
                Log.Write(strServiceCode, LogLevel.Error, msg);
                recordMsg.set("msg", msg).set("error", false);
                return recordMsg;
            }

            //1.2.3.判断出口1号移行机是否有返回车标记
            if (list_product.get(0).getStr("K_IS_WBS_BACK_CAR").equals(1)) {
                msg = String.format("等待出口1号移行机下指令。");
                Log.Write(strServiceCode, LogLevel.Warning, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
            }
        }

        //2.接入库缓存道计算
        //2.1.判断是否运行接入库缓存道
        if (!rec_service.getStr("SERVICE_PARA1_VALUE").equals("0")) {
            msg = String.format("出口转盘接车参数不允许接wbs入库缓存道车，参数值1：直接出口一号移行机车；参数值0：双向接车；当前参数值[%s]", rec_service.getStr("SERVICE_PARA1_VALUE"));
            Log.Write(strServiceCode, LogLevel.Warning, msg);
            recordMsg.set("msg", msg);
            return recordMsg;
        }

        //2.1.1.获取入库缓存道车是否有车
        List<Record> list_wbs14 = Db.find("SELECT * FROM t_model_zone t WHERE t.zone_code='wbs14' and t.PRODUCTION_CODE is not null ORDER BY t.zone_code,t.product_pos");
        if (list_wbs14.size() == 0) {
            msg = String.format("入库缓存道无车，不下达接车指令。");
            Log.Write(strServiceCode, LogLevel.Information, msg);
            recordMsg.set("msg", msg).set("error", true);

            return recordMsg;
        }

        //2.1.1.1.获取入库缓存道头车生成编码
        String productionCode = list_wbs14.get(0).getStr("PRODUCTION_CODE");

        /*//2.1.2.获取入库缓存道是否有占位信号
        Record recordStatus = Db.findFirst("SELECT t.*,ROWID FROM t_wbs_device_signal_status t WHERE t.device_signal_code='SR_018'");
        if (recordStatus == null) {
            msg = String.format("获取入库缓存道车辆占位信号失败。");
            Log.Write(strServiceCode, LogLevel.Information, msg);
            recordMsg = new Record().set("msg", msg).set("error", true);

            return recordMsg;
        }

        //2.1.2.1.判断入库缓存道是否有车占位
        if (recordStatus.getInt("DEVICE_STATUS") != 1) {
            msg = String.format("入库缓存道无车占位，等待车辆到位，暂时不下达接车指令。");
            Log.Write(strServiceCode, LogLevel.Information, msg);
            recordMsg = new Record().set("msg", msg).set("error", true);

            return recordMsg;
        }*/

        //2.1.1.2.获取需求产品信息
        //2.获取需求产品表信息
        List<Record> list_demand_prodect = Db.find(ShiftingmMachineOutOneComFun.getDemandProductInfoFromProductionCodeSql(), productionCode);
        if (list_demand_prodect.size() != 1) {
            msg = String.format("根据生产编码[%s],获取需求产品信息异常", productionCode);
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("msg", msg).set("error", false);
            return recordMsg;
        }

        //2.1.2.车辆移动
        msg = (String) Db.execute(new ICallback() {
            @Override
            public Object call(Connection conn) throws SQLException {
                //执行搬出程序结果信息
                String msg = "";

                CallableStatement proc = conn.prepareCall("{CALL PROC_MOVE_CAR(?,?,?,?)}");
                proc.setString("PARA_PRODUCTION_CODE", productionCode);
                proc.setString("PARA_DEST_ZONE", "wbs06");
                proc.setInt("PARA_IS_CHECK_POS", 1);
                proc.registerOutParameter("PARA_MSG", OracleTypes.VARCHAR);
                proc.execute();

                String ret = proc.getString("PARA_MSG");
                if (ret == null || ret.length() == 0 || ret.equals("null")) {
                    msg = "";
                } else {
                    if (ret.length() > 0) {
                        msg = String.format("移车失败，原因【%s】", ret);
                        Log.Write(strServiceCode, LogLevel.Error, msg);
                    }
                }

                return msg;
            }
        });
        if (msg.length() == 0) {
            msg = String.format("般车成功，把车[%s],般道区域[%s]", productionCode, "wbs06");
            Log.Write(strServiceCode, LogLevel.Information, msg);
        } else {
            msg = String.format("把车[%s],般道区域[%s]失败，原因[%s]", productionCode, "wbs06", msg);
            recordMsg.set("msg", msg).set("error", false);
            return recordMsg;
        }

        //3.下达指令
        Record recordShorts = ShiftingmMachineOutOneComFun.getMemoryValuesFromVpart(list_demand_prodect.get(0).getStr("K_STAMP_ID"));
        Db.update(ShiftingmMachineOutOneComFun.getWriteMemorySql(),
                2,
                list_demand_prodect.get(0).getStr("K_STAMP_ID"),
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
                groupMemoryCode
        );

        msg = String.format("接缓入库缓存道车,生产编码[%s],下达指令[%s]", productionCode, 2);
        Log.Write(strServiceCode, LogLevel.Information, msg);
        recordMsg.set("msg", msg).set("error", true);
        return recordMsg;

    }


    /**
     * 出口转盘，车辆离开信号处理
     *
     * @param strServiceCode
     * @param rec_service
     * @param memoryRead
     * @return
     */
    private Record passedDeal(String strServiceCode, Record rec_service, Record memoryRead) {
        //计算结果信息
        String msg, cmdMsg = "";
        Record recordMsg = new Record().set("msg", "").set("error", true);
        Log.Write(strServiceCode, LogLevel.Information, String.format("控制命令字[%s]开始计算", memoryRead.getStr("LOGIC_MEMORY_VALUE")));

        //1.根据内存地址组的值，获取白车身钢码号
        String vpartCode = ExportTurntableComFun.getVpartFromMemoryValues(memoryRead);
        if (vpartCode == null || vpartCode.length() == 0 || vpartCode.equals("null")) {
            msg = String.format("根据内存地址组的值[%s],获取白车身钢码号错误", memoryRead.toJson());
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("msg", msg).set("error", false);
            return recordMsg;
        }

        //3.获取需求产品表信息
        List<Record> list_demand_prodect = Db.find(ExportTurntableComFun.getDemandProductInfoFromVpartCodeCodeSql(), vpartCode);
        if (list_demand_prodect.size() != 1) {
            msg = String.format("根据白车身钢码号[%s],获取需求产品信息异常", vpartCode);
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("msg", msg).set("error", false);
            return recordMsg;
        }

        //3.1.获取生产编码
        String productionCode = list_demand_prodect.get(0).getStr("PRODUCTION_CODE");

        //4.车辆移动
        String dest_zone_code = "wbs12";
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

        //5.清空指令
        Record recordShorts = ExportTurntableComFun.getMemoryValuesFromVpart("");
        Db.update(ExportTurntableComFun.getWriteMemorySql(),
                0,
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
                groupMemoryCode
        );
        msg = String.format("处理命令字[%s]成功，清空内存地址组[%s]指令。",
                memoryRead.getStr("LOGIC_MEMORY_VALUE"),
                groupMemoryCode);
        Log.Write(strServiceCode, LogLevel.Information, msg);
        recordMsg.set("msg", msg).set("error", true);
        return recordMsg;
    }
}

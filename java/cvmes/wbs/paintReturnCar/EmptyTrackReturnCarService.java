package cvmes.wbs.paintReturnCar;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.ICallback;
import com.jfinal.plugin.activerecord.Record;
import cvmes.CvmesService;
import cvmes.common.AbstractSubServiceThread;
import cvmes.common.Log;
import cvmes.common.LogLevel;
import oracle.jdbc.OracleTypes;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * wbs空撬道返回实车转盘服务
 */


public class EmptyTrackReturnCarService extends AbstractSubServiceThread {

    // 空撬返回实车转盘机地址组
    private final String groupMemoryCode = "empty.sled.return.real.car";

    @Override
    public void initServiceCode() {
        // 服务编码
        this.strServiceCode = "EmptyTrackReturnCarService";
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

        switch (memoryValue) {
            case "0":
            case "2":
            case "3":
                Log.Write(strServiceCode, LogLevel.Information, String.format("地址组[%s],控制命令字[%s]下位操作。", groupMemoryCode, memoryValue));
                return "";
            //在位计算
            case "1":
                return reignCalculatingDirection(rec_service, list_memory_read.get(0)).getStr("msg");
            //离开处理
            case "4":
            case "5":
                return passedCalculating(rec_service, list_memory_read.get(0)).getStr("msg");
            default:
                msg = String.format("控制命令字[]错误，命令字没有定义", memoryValue);
                Log.Write(strServiceCode, LogLevel.Error, msg);
                return msg;
        }
    }


    /**
     * 实车在位计算
     *
     * @param rec_service
     * @param memoryRead
     * @return
     */
    private Record reignCalculatingDirection(Record rec_service, Record memoryRead) {
        //计算结果信息
        String msg = "";
        Record recordMsg = new Record().set("msg", "").set("error", true);
        Log.Write(strServiceCode, LogLevel.Information, String.format("控制命令字[%s]开始计算", memoryRead.getStr("LOGIC_MEMORY_VALUE")));

        //1.根据内存地址组的值，获取白车身钢码号
        String vpartCode = EmptyTrackReturnComFun.getVpartFromMemoryValues(memoryRead);
        if (vpartCode == null || vpartCode.length() == 0 || vpartCode.equals("null")) {
            msg = String.format("根据内存地址组的值[%s],获取白车身钢码号错误", groupMemoryCode);
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("msg", msg).set("error", false);
            return recordMsg;
        }

        //2.获取需求产品表信息
        List<Record> list_demand_prodect = Db.find(EmptyTrackReturnComFun.getDemandProductInfoFromVpartCodeCodeSql(), vpartCode);
        if (list_demand_prodect.size() != 1) {
            msg = String.format("根据白车身钢码号[%s],获取需求产品信息异常", vpartCode);
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("msg", msg).set("error", false);
            return recordMsg;
        }

        //2.1.获取生产编码
        String productionCode = list_demand_prodect.get(0).getStr("PRODUCTION_CODE");

        //2.2.获取临时上线弹出标记
        String wbs_pop_flag = list_demand_prodect.get(0).getStr("K_IS_WBS_TEMP_POP");

        //3.获取涂装上线记录
        List<Record> list_paint_online = Db.find("SELECT * FROM t_actual_passed_record t WHERE t.actual_point_code='TZ01' AND t.production_code=?", productionCode);
        if (list_paint_online.size() != 0) {
            //3.1.跳过实车处理
            if (rec_service.getStr("service_para1_value").equals("0")) {

                //3.1.1.检查服务允许状态
                if (!EmptyTrackReturnComFun.judgeRunStatus(strServiceCode, "wbs_paint_return_car_status")) {
                    //服务暂停
                    return recordMsg.set("msg", "服务暂停计算").set("error", false);
                }

                //3.1.2.下达指令3
                int cmd = 3;
                Record recordShorts = EmptyTrackReturnComFun.getMemoryValuesFromVpart(vpartCode);
                Db.update(EmptyTrackReturnComFun.getWriteMemorySql(),
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
                        groupMemoryCode
                );
                msg = String.format("执行车辆存在涂装上线记录，跳过实车处理，生产编码[%s],下达控制命令字[%s]", productionCode, cmd);
                Log.Write(strServiceCode, LogLevel.Information, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
            }


            msg = String.format("实车检测异常，该车已经进入涂装，请现场确认是否吴识别实车，通过手工强制信号处理");
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("msg", msg);
            return recordMsg;
        }

        //4.车辆移动
        msg = (String) Db.execute(new ICallback() {
            @Override
            public Object call(Connection conn) throws SQLException {
                //执行搬出程序结果信息
                String msg = "";

                CallableStatement proc = conn.prepareCall("{CALL PROC_MOVE_CAR(?,?,?,?)}");
                proc.setString("PARA_PRODUCTION_CODE", productionCode);
                proc.setString("PARA_DEST_ZONE", "wbs13");
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
            msg = String.format("般车成功，把车[%s],般道区域[%s]", productionCode, "wbs13");
            Log.Write(strServiceCode, LogLevel.Information, msg);
        } else {
            msg = String.format("把车[%s],般道区域[%s]失败，原因[%s]", productionCode, "wbs13", msg);
            recordMsg.set("msg", msg).set("error", false);
            return recordMsg;
        }

        //5.判断系统参数运行状态
        if (!EmptyTrackReturnComFun.judgeRunStatus(strServiceCode, "wbs_paint_return_car_status")) {
            //服务暂停
            return recordMsg.set("msg", "服务暂停计算").set("error", false);
        }

        //6.去向返修工工位，处理
        if (wbs_pop_flag.equals("0")) {
            //6.1.检查返修工位是否允许进车，如果不允许进车则不下指令，等待人工干预
            int icnt = Db.findFirst(EmptyTrackReturnComFun.getepairCarOrReviewCarOrBackCarStatusSql()).getInt("ICNT");
            //6.1.1.返修工位不允许进车,等待人工干预
            if (icnt != 0) {
                msg = String.format("返修工位不允许进车，等待人工干预，如果放行至临时上件工位，请标记临时上件弹出标记，或通过出口手工计划或强制信号把返修工位车辆接走。");
                Log.Write(strServiceCode, LogLevel.Warning, msg);
                recordMsg.set("msg", msg);

                return recordMsg;
            }

            //6.1.2.去向临时上件工位，下达指令2
            int cmd = 2;
            Record recordShorts = EmptyTrackReturnComFun.getMemoryValuesFromVpart(vpartCode);
            Db.update(EmptyTrackReturnComFun.getWriteMemorySql(),
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
                    groupMemoryCode
            );
            msg = String.format("执行车辆去向返修工位，生产编码[%s],下达控制命令字[%s]", productionCode, cmd);
            Log.Write(strServiceCode, LogLevel.Information, msg);
            recordMsg.set("msg", msg);
            return recordMsg;

        }

        //7.去向临时上件工位，下达指令3
        int cmd = 3;
        Record recordShorts = EmptyTrackReturnComFun.getMemoryValuesFromVpart(vpartCode);
        Db.update(EmptyTrackReturnComFun.getWriteMemorySql(),
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
                groupMemoryCode
        );
        msg = String.format("执行车辆去向临时上件工位，生产编码[%s],下达控制命令字[%s]", productionCode, cmd);
        Log.Write(strServiceCode, LogLevel.Information, msg);
        recordMsg.set("msg", msg);
        return recordMsg;
    }

    /**
     * 车辆离开信号处理
     *
     * @param rec_service
     * @param memoryRead
     * @return
     */
    private Record passedCalculating(Record rec_service, Record memoryRead) {
        //计算结果信息
        String msg, cmdMsg = "";
        Record recordMsg = new Record().set("msg", "").set("error", true);
        Log.Write(strServiceCode, LogLevel.Information, String.format("控制命令字[%s]开始计算", memoryRead.getStr("LOGIC_MEMORY_VALUE")));

        //1.根据内存地址组的值，获取白车身钢码号
        String vpartCode = EmptyTrackReturnComFun.getVpartFromMemoryValues(memoryRead);
        if (vpartCode == null || vpartCode.length() == 0 || vpartCode.equals("null")) {
            msg = String.format("根据内存地址组的值[%s],获取白车身钢码号错误", memoryRead.toJson());
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("msg", msg).set("error", false);
            return recordMsg;
        }

        //2.获取需求产品表信息
        List<Record> list_demand_prodect = Db.find(EmptyTrackReturnComFun.getDemandProductInfoFromVpartCodeCodeSql(), vpartCode);
        if (list_demand_prodect.size() != 1) {
            msg = String.format("根据白车身钢码号[%s],获取需求产品信息异常", vpartCode);
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("msg", msg).set("error", false);
            return recordMsg;
        }

        //2.1.获取生产编码
        String productionCode = list_demand_prodect.get(0).getStr("PRODUCTION_CODE");

        String memoryValue = memoryRead.getStr("LOGIC_MEMORY_VALUE");

        //移车目标区域
        String descCode;

        switch (memoryValue) {
            case "4":
                descCode = "wbs07";
                break;
            case "5":
                descCode = "wbs17";
                break;
            default:
                msg = String.format("控制命令字[]错误，命令字没有定义", memoryValue);
                Log.Write(strServiceCode, LogLevel.Error, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
        }

        //3.获取涂装返回实车转盘区域信息
        List<Record> list_wbs13 = Db.find("SELECT * FROM t_model_zone t WHERE t.zone_code='wbs13' AND t.production_code IS NOT NULL");

        //3.1.涂装返回实车转盘区域信息异常
        if (list_wbs13.size() > 1) {
            msg = String.format("涂装返回实车转盘区域信息异常,区域存在多个定义");
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("msg", msg);
            return recordMsg;
        }

        //3.2.涂装返回实车转盘区域信息存在实车
        if (list_wbs13.size() == 1) {
            //3.2.1移动车辆到目标区域
            msg = (String) Db.execute(new ICallback() {
                @Override
                public Object call(Connection conn) throws SQLException {
                    //执行搬出程序结果信息
                    String msg = "";

                    CallableStatement proc = conn.prepareCall("{CALL PROC_MOVE_CAR(?,?,?,?)}");
                    proc.setString("PARA_PRODUCTION_CODE", productionCode);
                    proc.setString("PARA_DEST_ZONE", descCode);
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
                msg = String.format("般车成功，把车[%s],般道区域[%s]", productionCode, descCode);
                Log.Write(strServiceCode, LogLevel.Information, msg);
            } else {
                msg = String.format("把车[%s],般道区域[%s]失败，原因[%s]", productionCode, descCode, msg);
                recordMsg.set("msg", msg).set("error", false);
                return recordMsg;
            }
        }

        //4.直接清空指令，下达指令0
        int cmd = 0;
        Record recordShorts = EmptyTrackReturnComFun.getMemoryValuesFromVpart("");
        Db.update(EmptyTrackReturnComFun.getWriteMemorySql(),
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
                groupMemoryCode
        );
        msg = String.format("执行车辆去向临时上件工位，生产编码[%s],下达控制命令字[%s]", productionCode, cmd);
        Log.Write(strServiceCode, LogLevel.Information, msg);
        recordMsg.set("msg", msg);
        return recordMsg;
    }
}

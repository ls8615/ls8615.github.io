package cvmes.wbs.shiftingmMachineInOne;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.IAtom;
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
 * 子服务务实现类：
 * wbs入口1号移行机服务
 */
public class ShiftingmMachineInOneService extends AbstractSubServiceThread {

    // 一号出口移行机地址组
    private final String groupMemoryCode = "no1.in.shifting.machine";

    @Override
    public void initServiceCode() {
        this.strServiceCode = "ShiftingmMachineInOneService";
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
            case "0":
            case "6":
            case "7":
            case "8":
            case "9":
            case "10":
            case "11":
            case "12":
            case "13":
                Log.Write(strServiceCode, LogLevel.Information, String.format("地址组[%s],控制命令字[%s]下位操作。", groupMemoryCode, memoryValue));
                return "";

            //2.1.在位计算去向（进道）
            case "1":
                return reignCalculatingDirection(rec_service, strServiceCode, list_memory_read.get(0)).getStr("msg");

            //2.2.车辆进道指令处理
            case "18":
            case "19":
            case "20":
            case "21":
            case "22":
            case "23":
            case "24":
            case "25":
                return passedClearCmd(strServiceCode, list_memory_read.get(0)).getStr("msg");
            default:
                msg = String.format("控制命令字[%s]错误，命令字没有定义", memoryValue);
                Log.Write(strServiceCode, LogLevel.Error, msg);
                return msg;
        }
    }

    /**
     * 进道计算：
     * A：判断是否存在手工计划，如果存在，则执行手工计划；
     * B：在位车辆为空撬，优先进入快速道，如果快速道已满，则选择进入空车道；如果不存在空车道，则空撬不下达指令；
     * C：在位车辆为直通车，则指示进入快速道，如果快速道已满，则不下指示。
     * D：进入可搬入车道尾车比当前车顺序号小，并且最接近的车道；
     * E：进入可搬入车道尾车比当前车顺序号大，并且最接近的车道；（判断参数，是否运行进入尾车计划大与当前车的车道）;
     * PS:尾车是空撬时，尾车往前推一个位置，直到是实车为止
     *
     * @param strServiceCode
     * @param memoryRead
     * @return
     */
    private Record reignCalculatingDirection(Record rec_service, String strServiceCode, Record memoryRead) {

        //计算结果信息
        String msg, cmdMsg = "";
        Record recordMsg = new Record().set("msg", "").set("error", true);
        Log.Write(strServiceCode, LogLevel.Information, String.format("控制命令字[%s]开始计算", memoryRead.getStr("LOGIC_MEMORY_VALUE")));

        //1.根据内存地址组的值，获取白车身钢码号
        String vpartCode = ShiftingmMachineInOneComFun.getVpartFromMemoryValues(memoryRead);
        if (vpartCode == null || vpartCode.length() == 0 || vpartCode.equals("null")) {
            msg = String.format("根据内存地址组的值[%s],获取白车身钢码号错误", memoryRead.toJson());
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("msg", msg).set("error", false);
            return recordMsg;
        }

        //2.获取需求产品表信息
        List<Record> list_demand_prodect = Db.find(ShiftingmMachineInOneComFun.getDemandProductInfoFromVpartCodeCodeSql(), vpartCode);
        if (list_demand_prodect.size() != 1) {
            msg = String.format("根据白车身钢码号[%s],获取需求产品信息异常", vpartCode);
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("msg", msg).set("error", false);
            return recordMsg;
        }

        //2.1.获取生产编码
        String productionCode = list_demand_prodect.get(0).getStr("PRODUCTION_CODE");

        //2.1.2.车辆移动
        msg = (String) Db.execute(new ICallback() {
            @Override
            public Object call(Connection conn) throws SQLException {
                //执行搬出程序结果信息
                String msg = "";

                CallableStatement proc = conn.prepareCall("{CALL PROC_MOVE_CAR(?,?,?,?)}");
                proc.setString("PARA_PRODUCTION_CODE", productionCode);
                proc.setString("PARA_DEST_ZONE", "wbs04");
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
            msg = String.format("般车成功，把车[%s],般道区域[%s]", productionCode, "wbs04");
            Log.Write(strServiceCode, LogLevel.Information, msg);
        } else {
            msg = String.format("把车[%s],般道区域[%s]失败，原因[%s]", productionCode, "wbs04", msg);
            recordMsg.set("msg", msg).set("error", false);
            return recordMsg;
        }

        //3.判断系统参数运行状态
        if (!ShiftingmMachineInOneComFun.judgeRunStatus(strServiceCode, "wbs_in_status")) {
            //服务暂停
            return recordMsg.set("msg", "服务暂停计算").set("error", false);
        }

        /**
         * 4.计算去向
         */
        //4.1.获取当前车是否有手工计划
        List<Record> list_manual_plan = Db.find(ShiftingmMachineInOneComFun.getMoveInManualPlanByProductionCodeSql(), productionCode);
        if (list_manual_plan.size() > 1) {
            msg = String.format("获取车辆[%s]手工搬入计划数据异常，存在多个未处理手工计划，请检查手工搬出计划数据", productionCode);
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("msg", msg).set("error", false);
            return recordMsg;
        }

        //4.1.1.存在手工计划
        if (list_manual_plan.size() == 1) {
            //打开事务
            Db.tx(new IAtom() {
                @Override
                public boolean run() throws SQLException {
                    String msg = "";

                    //进道指令
                    int cmdInTrack = -1;

                    //4.1.1.1.获取手工计划进道指令
                    int in_tarck = list_manual_plan.get(0).getInt("IN_TRACK");
                    switch (in_tarck) {
                        case 5:
                        case 6:
                        case 7:
                        case 8:
                        case 9:
                        case 10:
                        case 11:
                        case 12:
                            cmdInTrack = in_tarck + 1;
                            break;
                        default:
                            msg = String.format("获取车辆[%s]手工搬入计划数据搬入车道[%s]未定义，请检查手工搬出计划数据", productionCode, in_tarck);
                            Log.Write(strServiceCode, LogLevel.Error, msg);
                            recordMsg.set("msg", msg).set("error", false);
                            return false;
                    }

                    //4.1.1.2.更新手工计划状态
                    Db.update("UPDATE t_wbs_move_in_manual_plan t SET t.plan_status=1,t.execute_time=SYSDATE WHERE t.production_code=? AND t.plan_status=0", productionCode);

                    //4.1.1.3.写入指令
                    Record recordShorts = ShiftingmMachineInOneComFun.getMemoryValuesFromVpart(vpartCode);
                    Db.update(ShiftingmMachineInOneComFun.getWriteMemorySql(),
                            cmdInTrack,
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
                    msg = String.format("执行手工计划[%s],指示车辆进道[%s],生产编码[%s],下达控制命令字[%s]",
                            list_manual_plan.get(0).getStr("ID"),
                            in_tarck,
                            list_demand_prodect.get(0).getStr("PRODUCTION_CODE"),
                            cmdInTrack);
                    Log.Write(strServiceCode, LogLevel.Information, msg);
                    recordMsg.set("msg", msg);
                    return true;
                }
            });

            return recordMsg;
        }

        /**
         * 4.2.空撬处理：
         * A：快速道没满，进入快速道；
         * B：如果快速道已满，进入普通车道的空车道（按车道优先级进入）
         * C：如果没有空车道，则不进车
         */
        if (vpartCode.substring(0, 5).equals("WBSBB")) {
            //4.2.1.获取快速道剩余空位
            Record recordCount = Db.findFirst(ShiftingmMachineInOneComFun.getCountOfWbs05_05Empty());

            //4.2.1.1.快速通道存在空位置
            if (recordCount.getInt("ICNT") > 0) {
                Record recordShorts = ShiftingmMachineInOneComFun.getMemoryValuesFromVpart(vpartCode);
                int cmdInTrack = 6;
                Db.update(ShiftingmMachineInOneComFun.getWriteMemorySql(),
                        cmdInTrack,
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
                msg = String.format("执行空撬搬入,指示车辆进道[%s],空撬码[%s],下达控制命令字[%s]",
                        cmdInTrack - 1,
                        vpartCode,
                        cmdInTrack);
                Log.Write(strServiceCode, LogLevel.Information, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
            }

            //4.2.2.获取未满空撬道
            List<Record> list_not_full_empty_track = Db.find(ShiftingmMachineInOneComFun.getNotFullEmptyTrackBySpecialCarTypeCodeSql(), "WBS_NORMAL_CAR");

            //4.2.2.1.空撬搬入未满空撬道
            if (list_not_full_empty_track.size() != 0) {
                //4.2.2.1.1.下达指令
                Record recordShorts = ShiftingmMachineInOneComFun.getMemoryValuesFromVpart(vpartCode);
                int cmdInTrack = ShiftingmMachineInOneComFun.getRecordInStatusOfZoneCodesRela().getInt(list_not_full_empty_track.get(0).getStr("ZONE_CODE"));
                Db.update(ShiftingmMachineInOneComFun.getWriteMemorySql(),
                        cmdInTrack,
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
                msg = String.format("执行空撬搬入未满空橇道,指示车辆进道[%s],空撬码[%s],下达控制命令字[%s]",
                        cmdInTrack - 1,
                        vpartCode,
                        cmdInTrack);
                Log.Write(strServiceCode, LogLevel.Information, msg);
                recordMsg.set("msg", msg);
                return recordMsg;

            }

            //4.2.3.获取普通车道的空车道
            List<Record> list_empty_track = Db.find(ShiftingmMachineInOneComFun.getEmptyTrackBySpecialCarTypeCodeSql(), "WBS_NORMAL_CAR");

            //4.2.3.1.没有空车道，空撬不下搬入指令
            if (list_empty_track.size() == 0) {
                msg = String.format("空撬搬入失败，没有可搬入空车道，空撬号[%s]", vpartCode);
                Log.Write(strServiceCode, LogLevel.Warning, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
            }

            //4.2.3.2.下达空撬搬入指令
            Record recordShorts = ShiftingmMachineInOneComFun.getMemoryValuesFromVpart(vpartCode);
            int cmdInTrack = ShiftingmMachineInOneComFun.getRecordInStatusOfZoneCodesRela().getInt(list_empty_track.get(0).getStr("ZONE_CODE"));
            Db.update(ShiftingmMachineInOneComFun.getWriteMemorySql(),
                    cmdInTrack,
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
            msg = String.format("执行空撬搬入空道,指示车辆进道[%s],空撬码[%s],下达控制命令字[%s]",
                    cmdInTrack - 1,
                    vpartCode,
                    cmdInTrack);
            Log.Write(strServiceCode, LogLevel.Information, msg);
            recordMsg.set("msg", msg);
            return recordMsg;

        }

        /**
         * 4.3.直通车搬入
         */
        if (list_demand_prodect.get(0).getStr("K_IS_WBS_DIRECT_CAR").equals("1")) {
            //4.3.1.获取快速道剩余空位
            Record recordCount = Db.findFirst("SELECT COUNT(1) ICNT FROM t_model_zone t WHERE t.zone_code='wbs05_05' AND t.production_code IS NULL");
            if (recordCount.getInt("ICNT") == 0) {
                msg = String.format("直通车搬入失败，快速通道无空位，生产编码[%s],钢码号[%s]", productionCode, vpartCode);
                Log.Write(strServiceCode, LogLevel.Warning, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
            }

            //4.3.2.下达直通车进入快速道
            Record recordShorts = ShiftingmMachineInOneComFun.getMemoryValuesFromVpart(vpartCode);
            int cmdInTrack = 6;
            Db.update(ShiftingmMachineInOneComFun.getWriteMemorySql(),
                    cmdInTrack,
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
            msg = String.format("执行直通车搬入,指示车辆进道[%s],空撬码[%s],下达控制命令字[%s]",
                    cmdInTrack - 1,
                    vpartCode,
                    cmdInTrack);
            Log.Write(strServiceCode, LogLevel.Information, msg);
            recordMsg.set("msg", msg);
            return recordMsg;
        }

        /**
         * 4.4.普通车，进道计算：
         * A：判断是否存在可搬入车道
         * A：获取车道尾车计划顺序号比当前车小，并且最接近的车道进入
         * B：获取空车道进入
         * C：获取车道尾车计划比当前前车大，并且最接近的车道进入
         */
        //4.4.0.获取可搬入车道（非禁止车道，并且车道未满）
        List<Record> list_allow_enter = Db.find(ShiftingmMachineInOneComFun.getAllowEnterTrackSql());
        if (list_allow_enter.size() == 0) {
            msg = String.format("没有可搬入车道，当前车生产编码[%s]", productionCode);
            Log.Write(strServiceCode, LogLevel.Warning, msg);
            recordMsg.set("msg", msg);
            return recordMsg;
        }

        //4.4.1.获取当前车是否存在涂装计划
        List<Record> list_plan_cru = Db.find("SELECT COUNT(1) ICNT FROM T_PLAN_SCHEDULING M LEFT JOIN T_PLAN_SCHEDULING_D D ON D.SCHEDULING_PLAN_CODE=M.SCHEDULING_PLAN_CODE WHERE D.LINE_CODE='tz0101' AND d.production_code=?", productionCode);

        //4.4.1.1.计划异常
        if (list_plan_cru.get(0).getInt("ICNT") != 1) {
            msg = String.format("获取涂装计划异常，不存在涂装计划或存在多个涂装计划，生产编码[%s],涂装计划个数[%s]"
                    , productionCode, list_plan_cru.get(0).getInt("ICNT"));
            Log.Write(strServiceCode, LogLevel.Error, msg);

            recordMsg.set("msg", msg);
            return recordMsg;
        }

        //4.4.2.获取可搬入尾车比当前车小的车道
        List<Record> list_min = Db.find(ShiftingmMachineInOneComFun.getCruCarOfLastTrackCarDivSql(1), productionCode);
        if (list_min.size() == 0) {
            msg = String.format("没有获取到可搬入车道尾车计划好比当车小的车道，当前车生产编码[%s]", productionCode);
            Log.Write(strServiceCode, LogLevel.Information, msg);
        } else {
            //4.2.2.1.下达进道指令
            Record recordShorts = ShiftingmMachineInOneComFun.getMemoryValuesFromVpart(vpartCode);
            int cmdInTrack = ShiftingmMachineInOneComFun.getShiftingMachineInDirectionCarZoneCodeOfInstructionsRela().getInt(list_min.get(0).getStr("ZONE_CODE"));
            Db.update(ShiftingmMachineInOneComFun.getWriteMemorySql(),
                    cmdInTrack,
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
            msg = String.format("执行搬入尾车计划顺序号比当前车小的车道,指示车辆进道[%s],钢码号[%s],生产编码[%s],下达控制命令字[%s]",
                    cmdInTrack - 1,
                    vpartCode,
                    productionCode,
                    cmdInTrack);
            Log.Write(strServiceCode, LogLevel.Information, msg);
            recordMsg.set("msg", msg);
            return recordMsg;
        }

        //4.4.3.可搬入空车道
        List<Record> list_empty_track = Db.find(ShiftingmMachineInOneComFun.getEmptyTrackBySpecialCarTypeCodeSql(), "WBS_NORMAL_CAR");

        //4.4.3.1.没有可搬入空车道
        if (list_empty_track.size() == 0) {
            msg = String.format("普通车搬入，没有可搬入空车道，钢码号[%s]", vpartCode);
            Log.Write(strServiceCode, LogLevel.Warning, msg);
        }

        //4.4.3.2.有可搬入空车道
        if (list_empty_track.size() > 0) {
            //4.4.4.2.1.下达进道指令
            Record recordShorts = ShiftingmMachineInOneComFun.getMemoryValuesFromVpart(vpartCode);
            int cmdInTrack = ShiftingmMachineInOneComFun.getShiftingMachineInDirectionCarZoneCodeOfInstructionsRela().getInt(list_empty_track.get(0).getStr("ZONE_CODE"));
            Db.update(ShiftingmMachineInOneComFun.getWriteMemorySql(),
                    cmdInTrack,
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
            msg = String.format("执行搬入空车道,指示车辆进道[%s],钢码号[%s],生产编码[%s],下达控制命令字[%s]",
                    cmdInTrack - 1,
                    vpartCode,
                    productionCode,
                    cmdInTrack);
            Log.Write(strServiceCode, LogLevel.Information, msg);
            recordMsg.set("msg", msg);
            return recordMsg;
        }


        //4.4.4.获取可搬入尾车比当前车大的车道
        List<Record> list_max = Db.find(ShiftingmMachineInOneComFun.getCruCarOfLastTrackCarDivSql(2), productionCode);
        if (list_max.size() == 0) {
            msg = String.format("没有获取到可搬入车道尾车计划号比当车大的车道，当前车生产编码[%s]", productionCode);
            Log.Write(strServiceCode, LogLevel.Information, msg);
            recordMsg.set("msg", msg);

            return recordMsg;
        } else {
            //判断是否允许进入尾车计划比当前车大的车道（参数）

        }

        //4.4.4.1.是否允许进入尾车计划比当前车大的车道（参数）
        if (rec_service.getStr("SERVICE_PARA1_VALUE") == null || !rec_service.getStr("SERVICE_PARA1_VALUE").equals("1")) {
            msg = String.format("参数禁止进入尾车计划比当前车计划大车道，或参数值错误，当前车生产编码[%s]，参数值[%s]", productionCode, rec_service.getStr("SERVICE_PARA1_VALUE"));
            Log.Write(strServiceCode, LogLevel.Information, msg);
            recordMsg.set("msg", msg);

            return recordMsg;
        }

        //4.4.4.2.下达指令
        Record recordShorts = ShiftingmMachineInOneComFun.getMemoryValuesFromVpart(vpartCode);
        int cmdInTrack = ShiftingmMachineInOneComFun.getShiftingMachineInDirectionCarZoneCodeOfInstructionsRela().getInt(list_max.get(0).getStr("ZONE_CODE"));
        Db.update(ShiftingmMachineInOneComFun.getWriteMemorySql(),
                cmdInTrack,
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
        msg = String.format("执行搬入尾车计划顺序号比当前车大的车道,指示车辆进道[%s],钢码号[%s],生产编码[%s],下达控制命令字[%s]",
                cmdInTrack - 1,
                vpartCode,
                productionCode,
                cmdInTrack);
        Log.Write(strServiceCode, LogLevel.Information, msg);
        recordMsg.set("msg", msg);
        return recordMsg;
    }

    /**
     * 车辆进道指令处理：
     * 下达清空指令，然后移车进对应车道，如果清空指令失败，则不移车，反之，移车失败，回滚清空指令
     *
     * @param strServiceCode
     * @param menoryRead
     * @return
     */
    private Record passedClearCmd(String strServiceCode, Record menoryRead) {
        //计算结果信息
        String msg = "";
        //计算返回结果
        Record recordMsg = new Record().set("msg", "");

        //控制命令字
        String memoryValue = menoryRead.getStr("LOGIC_MEMORY_VALUE");

        Log.Write(strServiceCode, LogLevel.Information, String.format("控制命令字[%s]开始计算", memoryValue));

        //1.获取目标区域编码
        final String dest_zone_code;
        switch (memoryValue) {
            case "18":
                dest_zone_code = "wbs05_05";
                break;
            case "19":
                dest_zone_code = "wbs05_06";
                break;
            case "20":
                dest_zone_code = "wbs05_07";
                break;
            case "21":
                dest_zone_code = "wbs05_08";
                break;
            case "22":
                dest_zone_code = "wbs05_09";
                break;
            case "23":
                dest_zone_code = "wbs05_10";
                break;
            case "24":
                dest_zone_code = "wbs05_11";
                break;
            case "25":
                dest_zone_code = "wbs05_12";
                break;
            default:
                msg = String.format("控制命令字[]错误，命令字没有定义", memoryValue);
                Log.Write(strServiceCode, LogLevel.Error, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
        }

        //2.根据内存地址组的值，获取白车身钢码号
        String vpartCode = ShiftingmMachineInOneComFun.getVpartFromMemoryValues(menoryRead);
        if (vpartCode == null || vpartCode.length() == 0 || vpartCode.equals("null")) {
            msg = String.format("根据内存地址组的值[%s],获取白车身钢码号错误", menoryRead.toJson());
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("msg", msg).set("error", false);
            return recordMsg;
        }

        //3.获取需求产品表信息
        List<Record> list_demand_prodect = Db.find(ShiftingmMachineInOneComFun.getDemandProductInfoFromVpartCodeCodeSql(), vpartCode);
        if (list_demand_prodect.size() != 1) {
            msg = String.format("根据白车身钢码号[%s],获取需求产品信息异常", vpartCode);
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("msg", msg).set("error", false);
            return recordMsg;
        }

        //3.1.获取生产编码
        String productionCode = list_demand_prodect.get(0).getStr("PRODUCTION_CODE");

        //4.车辆移动
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
        Record recordShorts = ShiftingmMachineInOneComFun.getMemoryValuesFromVpart("");
        Db.update(ShiftingmMachineInOneComFun.getWriteMemorySql(),
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
                menoryRead.getStr("LOGIC_MEMORY_VALUE"),
                groupMemoryCode);
        Log.Write(strServiceCode, LogLevel.Information, msg);
        recordMsg.set("msg", msg).set("error", true);
        return recordMsg;
    }
}


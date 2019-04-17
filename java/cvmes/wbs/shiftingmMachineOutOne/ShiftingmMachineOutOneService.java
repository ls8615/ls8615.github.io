package cvmes.wbs.shiftingmMachineOutOne;

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
 * wbs出口1号移行机服务
 */
public class ShiftingmMachineOutOneService extends AbstractSubServiceThread {


    // 一号出口移行机地址组
    private final String groupMemoryCode = "no1.out.shifting.machine";


    @Override
    public void initServiceCode() {
        // 服务编码
        strServiceCode = "ShiftingmMachineOutOneService";
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
            case "5":
            case "6":
            case "7":
            case "8":
            case "9":
            case "10":
            case "11":
            case "12":
            case "13":
            case "14":
            case "15":
            case "17":
            case "18":
            case "19":
            case "20":
            case "21":
                Log.Write(strServiceCode, LogLevel.Information, String.format("地址组[%s],控制命令字[%s]下位操作。", groupMemoryCode, memoryValue));
                return "";

            //2.1.接车计算
            case "0":
                if (!ShiftingmMachineOutOneComFun.judgeRunStatus(strServiceCode, "wbs_out_status")) {
                    //服务暂停
                    return "服务暂停计算";
                }

                return pickCar(strServiceCode, list_memory_read.get(0)).getStr("msg");
            //在位计算去向
            case "16":
                return reignCalculatingDirection(strServiceCode, list_memory_read.get(0)).getStr("msg");
            case "22":
            case "23":
            case "24":
            case "25":
            case "26":
                return passDeal(strServiceCode, list_memory_read.get(0)).getStr("msg");
        }


        return msg;
    }

    /**
     * 出口接车计算
     * <p>
     * 执行手工计划： 1.判断是否存在手工计划？ 是：执行手工计划，计算结束 否：跳转至A逻辑
     * <p>
     * A：接返修工位车辆 1.判断返修工位命令字是否=2 否：跳转至B逻辑 是：写入13，计算结束
     * <p>
     * B：接评审工位1车辆 1.判断评审工位1命令字是否=2 否：跳转至C逻辑 是：写入14，计算结束
     * <p>
     * C：接评审工位2车辆 1.判断评审工位1命令字是否=2 否：跳转至D逻辑 是：写入15，计算结束
     * <p>
     * D:接库区5-12道车辆 1.判断快速道是否有车？ 是：接快速道车，写入5，结束计算 否：跳转至D2 2.判断是否存在可搬出车道？ 否：结束计算 是：
     * 获取可搬出车道头车计划顺序号最小的车辆所在车道
     *
     * @param memoryRead 内存地址组读表值
     */
    private Record pickCar(String strServiceCode, Record memoryRead) {
        String msg = "";
        Record recordMsg = new Record().set("msg", "").set("error", true);
        Log.Write(strServiceCode, LogLevel.Information, String.format("控制命令字[%s]开始计算", memoryRead.getStr("LOGIC_MEMORY_VALUE")));


        //1.获取手工计划
        List<Record> list_manual_plan = Db.find(ShiftingmMachineOutOneComFun.getShiftingMachineOutOneManualPlan());

        //1.1.存在可处理手工计划处理
        if (list_manual_plan.size() == 1 && list_manual_plan.get(0).getStr("PRODUCT_POS").equals("1")) {
            //1.1.1.获取手工计划车在区域位置
            String zoneCode = list_manual_plan.get(0).getStr("ZONE_CODE");

            //1.1.2.获取手工计划车所在车道接车指令
            int OutTrackCmd = ShiftingmMachineOutOneComFun.getShiftingMachineOutOnePickCarZoneCodeOfInstructionsRela().getInt(zoneCode);


            //启用事务
            boolean isSucess = Db.tx(new IAtom() {
                @Override
                public boolean run() throws SQLException {
                    String msg = "";

                    /**
                     * 1.1.3.车辆去向处理：
                     * A：去向返修工位，更新返修车标记为1
                     * B：去向评审工位1或评审工位2，更新评审车标记为1
                     * C：去向出口转盘，更新返回车标记为1
                     */
                    switch (list_manual_plan.get(0).getStr("MOVE_DIRECTION")) {
                           /* //返修工位
                            case "1":
                                Db.update("UPDATE T_PLAN_DEMAND_PRODUCT SET K_IS_WBS_REPAIR_CAR=1 where DEMAND_PRODUCT_TYPE IN (0,5,6) and  PRODUCTION_CODE=?", list_manual_plan.get(0).getStr("PRODUCTION_CODE"));
                                break;
                            //评审工位
                            case "2":
                            case "3":
                                Db.update("UPDATE T_PLAN_DEMAND_PRODUCT SET K_IS_WBS_REVIEW_CAR=1 where DEMAND_PRODUCT_TYPE IN (0,5,6) and  PRODUCTION_CODE=?", list_manual_plan.get(0).getStr("PRODUCTION_CODE"));
                                break;*/
                            //出口转盘
                            /*case "4":
                                Db.update("UPDATE T_PLAN_DEMAND_PRODUCT SET K_IS_WBS_BACK_CAR=1 where DEMAND_PRODUCT_TYPE IN (0,5,6) and  PRODUCTION_CODE=?", list_manual_plan.get(0).getStr("PRODUCTION_CODE"));
                                break;*/
                        //涂装缓存
                        case "0":
                            //1.1.3.1.判断涂装缓存是否有空为，如果没有空位则不接车
                            Record recordDeviceStatus = Db.findFirst("SELECT t.DEVICE_STATUS FROM t_wbs_device_signal_status t WHERE t.device_signal_code = 'SR_017'");
                            if (recordDeviceStatus == null) {
                                msg = String.format("获取涂装出口占位信息失败");
                                Log.Write(strServiceCode, LogLevel.Information, msg);
                                recordMsg.set("msg", msg);
                                return false;
                            }
                            if ("1".equals(recordDeviceStatus.getStr("DEVICE_STATUS"))) {
                                msg = "涂装缓存出口有车占位，不接去向为涂装缓存的车，移行机不留车";
                                Log.Write(strServiceCode, LogLevel.Information, msg);
                                recordMsg.set("msg", msg);
                                return false;
                            }
                            break;
                    }

                        /*//1.1.4.更新手工计划状态
                        Db.update("UPDATE t_wbs_move_out_manual_plan SET plan_status=1, execute_time=SYSDATE WHERE production_code=? AND plan_status=0",
                                list_manual_plan.get(0).getStr("PRODUCTION_CODE"));*/

                    //1.1.5.下达接车指令
                    String vpartCode = "";
                    Record recordShorts = ShiftingmMachineOutOneComFun.getMemoryValuesFromVpart(vpartCode);
                    Db.update(ShiftingmMachineOutOneComFun.getWriteMemorySql(),
                            OutTrackCmd,
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
                    msg = String.format("执行手工搬出计划[%s],生产编码[%s],下达控制命令字[%s]",
                            list_manual_plan.get(0).getStr("ID"),
                            list_manual_plan.get(0).getStr("PRODUCTION_CODE"),
                            OutTrackCmd);
                    Log.Write(strServiceCode, LogLevel.Information, msg);
                    recordMsg.set("msg", msg).set("error", true);

                    return true;
                }
            });

            return recordMsg;
        }

        //1.2.空撬处理逻辑——待处理
        /**
         * 1.2.空撬处理逻辑
         * A:空撬和实车同时放，连续放空撬数不能大于设定的参数，并且要间隔台数大于设定的参数才能继续放空撬
         *   当空撬当到5道待搬出去向涂装的车时，不考虑间隔台数和连续投放台数参数
         * B:连续排放空撬
         */
        Record recMsg = EmptyCarDeal(strServiceCode, memoryRead);

        if (!recMsg.getStr("msg").equals("")) {
            return recMsg;
        }

        /**
         * 1.3.判断5道是否可搬出：
         * A.车道禁止为不可搬出
         * B.车道头车无直通车标记，按正常车处理，按头车顺序号最小搬出
         *
         */
        //1.3.1.获取快速道，车道头车区域信息
        List<Record> list_T5 = Db.find(ShiftingmMachineOutOneComFun.getZoneOfT5Ssl());

        //1.3.1.1.获取快速道（5道）区域头车信息有误
        if (list_T5.size() > 1) {
            msg = String.format("获取快速道（5道）区域头车信息有误");
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("msg", msg);
            return recordMsg;
        }

        //1.3.1.2.快速道（5道）存在可搬出车辆
        if (list_T5.size() == 1) {
            //1.3.1.2.1.获取快速道（5道）头车需求产品信息
            List<Record> list_T5_Product = Db.find(ShiftingmMachineOutOneComFun.getDemandProductInfoFromProductionCodeSql(), list_T5.get(0).getStr("PRODUCTION_CODE"));

            //1.2.1.2.1.1.获取需求产品信息错误
            if (list_T5_Product.size() != 1) {
                msg = String.format("获取生产编码[%s]的需求产品信息存在多条记录或者不存在");
                Log.Write(strServiceCode, LogLevel.Error, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
            }

            //1.2.1.2.1.2.判断是否有直通车标记，如果没有直通车标记，按普通车道处理
            if (list_T5_Product.get(0).getStr("K_IS_WBS_DIRECT_CAR").equals("1")) {
                //1.2.1.2.1.1.判断涂装缓存是否有空为，如果没有空位则不接车
                Record recordDeviceStatus = Db.findFirst("SELECT t.DEVICE_STATUS FROM t_wbs_device_signal_status t WHERE t.device_signal_code = 'SR_017'");
                if (recordDeviceStatus == null) {
                    msg = String.format("获取涂装出口占位信息失败");
                    Log.Write(strServiceCode, LogLevel.Information, msg);
                    recordMsg.set("msg", msg);
                    return recordMsg;
                }
                if ("1".equals(recordDeviceStatus.getStr("DEVICE_STATUS"))) {
                    msg = "涂装缓存出口有车占位，不接去向为涂装缓存的车，移行机不留车";
                    Log.Write(strServiceCode, LogLevel.Information, msg);
                    recordMsg.set("msg", msg);
                    return recordMsg;
                }

                //1.2.1.2.1.2.2.下达接车指令
                int OutTrackCmd = ShiftingmMachineOutOneComFun.getShiftingMachineOutOnePickCarZoneCodeOfInstructionsRela().getInt(list_T5.get(0).getStr("ZONE_CODE"));
                String vpartCode = "";// list_T5_Product.get(0).getStr("K_STAMP_ID");
                Record recordShorts = ShiftingmMachineOutOneComFun.getMemoryValuesFromVpart(vpartCode);
                Db.update(ShiftingmMachineOutOneComFun.getWriteMemorySql(),
                        OutTrackCmd,
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
                msg = String.format("接快速道车,生产编码[%s],下达控制命令字[%s]",
                        list_T5_Product.get(0).getStr("PRODUCTION_CODE"),
                        OutTrackCmd);
                Log.Write(strServiceCode, LogLevel.Information, msg);
                recordMsg.set("msg", msg);

                return recordMsg;
            }
        }

        /**
         * 1.4.获取5-12道可搬出车道头车是否有返修车和评审车和返回车
         */
        //1.4.1.获取返修车
        int type = 0;
        int status = 0;
        List<Record> list_repairCar = Db.find(ShiftingmMachineOutOneComFun.getRepairCarOrReviewCarOrBackCar(type));
        if (list_repairCar.size() != 0) {
            //1.4.1.1.返修工位是否可进车
            int icnt = Db.findFirst(ShiftingmMachineOutOneComFun.getepairCarOrReviewCarOrBackCarStatus(status)).getInt("ICNT");
            if (icnt == 0) {
                int OutTrackCmd = ShiftingmMachineOutOneComFun.getShiftingMachineOutOnePickCarZoneCodeOfInstructionsRela().getInt(list_repairCar.get(0).getStr("ZONE_CODE"));
                String vpartCode = "";
                Record recordShorts = ShiftingmMachineOutOneComFun.getMemoryValuesFromVpart(vpartCode);
                Db.update(ShiftingmMachineOutOneComFun.getWriteMemorySql(),
                        OutTrackCmd,
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
                msg = String.format("接车道返修车,生产编码[%s],下达控制命令字[%s]",
                        list_repairCar.get(0).getStr("PRODUCTION_CODE"),
                        OutTrackCmd);
                Log.Write(strServiceCode, LogLevel.Information, msg);
                recordMsg.set("msg", msg);

                return recordMsg;
            }
            Log.Write(strServiceCode, LogLevel.Information, "返修工位有车占位或车道禁止接车，无法接返修车。");
        }

        //1.4.2.获取评审
        type = 1;
        List<Record> list_reviewCar = Db.find(ShiftingmMachineOutOneComFun.getRepairCarOrReviewCarOrBackCar(type));
        if (list_reviewCar.size() != 0) {
            //1.4.2.1.评审工位1工位是否可进车
            status = 1;
            int icnt = Db.findFirst(ShiftingmMachineOutOneComFun.getepairCarOrReviewCarOrBackCarStatus(status)).getInt("ICNT");
            if (icnt == 0) {
                int OutTrackCmd = ShiftingmMachineOutOneComFun.getShiftingMachineOutOnePickCarZoneCodeOfInstructionsRela().getInt(list_reviewCar.get(0).getStr("ZONE_CODE"));
                String vpartCode = "";
                Record recordShorts = ShiftingmMachineOutOneComFun.getMemoryValuesFromVpart(vpartCode);
                Db.update(ShiftingmMachineOutOneComFun.getWriteMemorySql(),
                        OutTrackCmd,
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
                msg = String.format("接车道评审车,生产编码[%s],下达控制命令字[%s]",
                        list_reviewCar.get(0).getStr("PRODUCTION_CODE"),
                        OutTrackCmd);
                Log.Write(strServiceCode, LogLevel.Information, msg);
                recordMsg.set("msg", msg);

                return recordMsg;
            }

            //1.4.2.2.评审工位2工位是否可进车
            status = 2;
            icnt = Db.findFirst(ShiftingmMachineOutOneComFun.getepairCarOrReviewCarOrBackCarStatus(status)).getInt("ICNT");
            if (icnt == 0) {
                int OutTrackCmd = ShiftingmMachineOutOneComFun.getShiftingMachineOutOnePickCarZoneCodeOfInstructionsRela().getInt(list_reviewCar.get(0).getStr("ZONE_CODE"));
                String vpartCode = "";
                Record recordShorts = ShiftingmMachineOutOneComFun.getMemoryValuesFromVpart(vpartCode);
                Db.update(ShiftingmMachineOutOneComFun.getWriteMemorySql(),
                        OutTrackCmd,
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
                msg = String.format("接车道评审车,生产编码[%s],下达控制命令字[%s]",
                        list_reviewCar.get(0).getStr("PRODUCTION_CODE"),
                        OutTrackCmd);
                Log.Write(strServiceCode, LogLevel.Information, msg);
                recordMsg.set("msg", msg);

                return recordMsg;
            }

            Log.Write(strServiceCode, LogLevel.Information, "评审工位有车占位或车道禁止接车，无法接评审车。");
        }

        //1.4.3.获取返回车
        type = 2;
        List<Record> list_backCar = Db.find(ShiftingmMachineOutOneComFun.getRepairCarOrReviewCarOrBackCar(type));
        if (list_backCar.size() != 0) {
            int OutTrackCmd = ShiftingmMachineOutOneComFun.getShiftingMachineOutOnePickCarZoneCodeOfInstructionsRela().getInt(list_backCar.get(0).getStr("ZONE_CODE"));
            String vpartCode = "";
            Record recordShorts = ShiftingmMachineOutOneComFun.getMemoryValuesFromVpart(vpartCode);
            Db.update(ShiftingmMachineOutOneComFun.getWriteMemorySql(),
                    OutTrackCmd,
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
            msg = String.format("接车道返回车,生产编码[%s],下达控制命令字[%s]",
                    list_backCar.get(0).getStr("PRODUCTION_CODE"),
                    OutTrackCmd);
            Log.Write(strServiceCode, LogLevel.Information, msg);
            recordMsg.set("msg", msg);

            return recordMsg;
        }

        /**
         * 1.5.获取5-12道可搬出车道头车、可搬出返修工位、可搬出评审工位1和可搬出评审工位2 获取可搬出车道头车计划顺序号最小的车辆所在车道
         * 即：获取可搬出区域头车
         */
        List<Record> list_normal = Db.find(ShiftingmMachineOutOneComFun.getZoneCodeSmallCar());
        if (list_normal.size() == 0) {
            msg = "没有可搬出车辆";
            Log.Write(strServiceCode, LogLevel.Information, msg);
            recordMsg.set("msg", msg);
            return recordMsg;
        }

        //1.5.1.获取搬出车辆需求产品信息
        List<Record> list_normal_demand_product = Db.find(ShiftingmMachineOutOneComFun.getDemandProductInfoFromProductionCodeSql(), list_normal.get(0).getStr("PRODUCTION_CODE"));
        if (list_normal_demand_product.size() != 1) {
            msg = String.format("获取生产编码[%s]的需求产品信息存在多条记录或者不存在");
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("msg", msg);
            return recordMsg;
        }
        //1.5.1.1.如果车辆去向为涂装缓存，并且涂装缓存已满，则不接车
        if ((list_normal_demand_product.get(0).getStr("K_IS_WBS_BACK_CAR").equals("0") &&
                list_normal_demand_product.get(0).getStr("K_IS_WBS_REVIEW_CAR").equals("0") &&
                list_normal_demand_product.get(0).getStr("K_IS_WBS_REPAIR_CAR").equals("0"))) {
            Record recordDeviceStatus = Db.findFirst("SELECT to_number(t.DEVICE_STATUS) DEVICE_STATUS FROM t_wbs_device_signal_status t WHERE t.device_signal_code = 'SR_017'");
            if (recordDeviceStatus == null) {
                msg = String.format("获取涂装出口占位信息失败");
                Log.Write(strServiceCode, LogLevel.Information, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
            }
            if ("1".equals(recordDeviceStatus.getStr("DEVICE_STATUS"))) {
                msg = "涂装缓存出口有车占位，不接去向为涂装缓存的车，移行机不留车";
                Log.Write(strServiceCode, LogLevel.Information, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
            }
        }

        //1.5.2.下达接车指令
        int OutTrackCmd = ShiftingmMachineOutOneComFun.getShiftingMachineOutOnePickCarZoneCodeOfInstructionsRela().getInt(list_normal.get(0).getStr("ZONE_CODE"));
        String vpartCode = "";
        Record recordShorts = ShiftingmMachineOutOneComFun.getMemoryValuesFromVpart(vpartCode);
        Db.update(ShiftingmMachineOutOneComFun.getWriteMemorySql(),
                OutTrackCmd,
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
        msg = String.format("正常接车,生产编码[%s],下达控制命令字[%s]",
                list_normal.get(0).getStr("PRODUCTION_CODE"),
                OutTrackCmd);
        Log.Write(strServiceCode, LogLevel.Information, msg);
        recordMsg.set("msg", msg);

        return recordMsg;
    }

    /**
     * 车辆在位，计算车辆去向：
     * A：空撬，指示涂装缓存；
     * B：返修标记车辆，指示返修工位
     * C：返修标记，指示返修工位1或返修工位2
     *
     * @param strServiceCode
     * @param memoryRead
     * @return
     */
    private Record reignCalculatingDirection(String strServiceCode, Record memoryRead) {
        //计算结果信息
        String msg, cmdMsg = "";
        Record recordMsg = new Record().set("msg", "").set("error", true);
        Log.Write(strServiceCode, LogLevel.Information, String.format("控制命令字[%s]开始计算", memoryRead.getStr("LOGIC_MEMORY_VALUE")));

        //1.根据内存地址组的值，获取白车身钢码号
        String vpartCode = ShiftingmMachineOutOneComFun.getVpartFromMemoryValues(memoryRead);
        if (vpartCode == null || vpartCode.length() == 0 || vpartCode.equals("null")) {
            msg = String.format("根据内存地址组的值[%s],获取白车身钢码号错误", memoryRead.toJson());
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("msg", msg).set("error", false);
            return recordMsg;
        }

        //2.获取需求产品表信息
        List<Record> list_demand_prodect = Db.find(ShiftingmMachineOutOneComFun.getDemandProductInfoFromVpartCodeCodeSql(), vpartCode);
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

        //3.判断系统参数运行状态
        if (!ShiftingmMachineOutOneComFun.judgeRunStatus(strServiceCode, "wbs_out_status")) {
            //服务暂停
            return recordMsg.set("msg", "服务暂停计算").set("error", false);
        }

        /**
         * 5.计算车辆去向:
         * A:如果在位车辆是从返修工位接的车，需要对返修工位控制命令字和钢码号的内存地址清0
         * B:如果在位车辆是从评审工位1接的车，需要对评审工位1控制命令字和钢码号的内存地址清0
         * C:如果在位车辆是从评审工位2接的车，需要对评审工位2控制命令字和钢码号的内存地址清0
         *
         */

        //开启事务
        Db.tx(new IAtom() {
            @Override
            public boolean run() throws SQLException {
                //计算结果信息
                String msg = "";

                /**
                 * 5.1.计算车辆来向
                 *  A:如果在位车辆是从返修工位接的车，需要对返修工位控制命令字和钢码号的内存地址清0
                 * B:如果在位车辆是从评审工位1接的车，需要对评审工位1控制命令字和钢码号的内存地址清0
                 * C:如果在位车辆是从评审工位2接的车，需要对评审工位2控制命令字和钢码号的内存地址清0
                 */
                //5.1.1.计算车辆来向是否为返修工位
                //5.1.1.1.获取返修工位内存地址组值
                List<Record> list_memory_read = Db.find("SELECT rm.* FROM T_WBS_READ_MEMORY rm where rm.GROUP_MEMORY_CODE=?", "rework.station");
                if (list_memory_read.size() != 1) {
                    msg = String.format("读取wbs内存地址组【%s】配置错误，请检查配置", "rework.stations");
                    Log.Write(strServiceCode, LogLevel.Error, msg);
                    recordMsg.set("msg", msg).set("error", false);
                    return false;
                }

                //5.1.1.2.判断车辆来向是否为返修工位
                if (list_memory_read.get(0).getInt("LOGIC_MEMORY_VALUE") == 2) {
                    String fromVpartCode = ShiftingmMachineOutOneComFun.getVpartFromMemoryValues(list_memory_read.get(0));
                    if (fromVpartCode.equals(vpartCode)) {
                        Record recordShorts = ShiftingmMachineOutOneComFun.getMemoryValuesFromVpart(vpartCode);
                        Db.update(ShiftingmMachineOutOneComFun.getWriteMemorySql(),
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
                                "rework.station"
                        );
                        msg = String.format("生成清空返修工位指令");
                        Log.Write(strServiceCode, LogLevel.Information, msg);
                    }
                }

                //5.1.2.计算车辆来向是否为评审工位1
                //5.1.2.1.获取评审工位1内存地址组值
                list_memory_read = Db.find("SELECT rm.* FROM T_WBS_READ_MEMORY rm where rm.GROUP_MEMORY_CODE=?", "no1.review.station");
                if (list_memory_read.size() != 1) {
                    msg = String.format("读取wbs内存地址组【%s】配置错误，请检查配置", "no1.review.station");
                    recordMsg.set("msg", msg).set("error", false);
                    Log.Write(strServiceCode, LogLevel.Error, msg);
                    return false;
                }

                //5.1.2.2.判断车辆来向是否为评审工位1
                if (list_memory_read.get(0).getInt("LOGIC_MEMORY_VALUE") == 2) {
                    String fromVpartCode = ShiftingmMachineOutOneComFun.getVpartFromMemoryValues(list_memory_read.get(0));
                    if (fromVpartCode.equals(vpartCode)) {
                        Record recordShorts = ShiftingmMachineOutOneComFun.getMemoryValuesFromVpart(vpartCode);
                        Db.update(ShiftingmMachineOutOneComFun.getWriteMemorySql(),
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
                                "no1.review.station"
                        );
                        msg = String.format("生成清空评审工位1指令");
                        Log.Write(strServiceCode, LogLevel.Information, msg);
                    }
                }

                //5.1.2.计算车辆来向是否为返修工位2
                //5.1.2.1.获取评审工位2内存地址组值
                list_memory_read = Db.find("SELECT rm.* FROM T_WBS_READ_MEMORY rm where rm.GROUP_MEMORY_CODE=?", "no2.review.station");
                if (list_memory_read.size() != 1) {
                    msg = String.format("读取wbs内存地址组【%s】配置错误，请检查配置", "no2.review.station");
                    Log.Write(strServiceCode, LogLevel.Error, msg);
                    recordMsg.set("msg", msg).set("error", false);
                    return false;
                }

                //5.1.2.2.判断车辆来向是否为评审工位2
                if (list_memory_read.get(0).getInt("LOGIC_MEMORY_VALUE") == 2) {
                    String fromVpartCode = ShiftingmMachineOutOneComFun.getVpartFromMemoryValues(list_memory_read.get(0));
                    if (fromVpartCode.equals(vpartCode)) {
                        Record recordShorts = ShiftingmMachineOutOneComFun.getMemoryValuesFromVpart(vpartCode);
                        Db.update(ShiftingmMachineOutOneComFun.getWriteMemorySql(),
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
                                "no2.review.station"
                        );
                        msg = String.format("生成清空评审工位2指令");
                        Log.Write(strServiceCode, LogLevel.Information, msg);
                    }
                }

                /**
                 * 5.2.计算车辆去向，有以下两种情况
                 * 一、情况1：手工计划
                 * A:根据手工计划去向，下达指示，并把手工计划处理为以处理，并结束。
                 * 二、情况2：
                 * A:如果在位车辆为空撬，或直通车，则指示涂装缓存；
                 * B:如果在位车辆为返修车，则指示返修工位；
                 * C:如果在位车辆为评审车，则指示评审工位1或评审工位2；
                 * D:如果位置车辆为返回车，则指示出口转盘；
                 * E:以上条件不满足，则指示涂装缓存;
                 *
                 */
                //5.2.1.手工计划处理
                //5.2.1.1.获取当前车手工计划
                List<Record> list_manual_plan = Db.find("SELECT t.*,ROWID FROM t_wbs_move_out_manual_plan t WHERE t.plan_status=0 AND t.production_code=?", productionCode);

                //5.2.1.1.2.判断手工计划数据是否正确
                if (list_manual_plan.size() > 1) {
                    msg = String.format("获取车辆[%s]手工搬出计划数据异常，存在多个未处理手工计划，请检查手工搬出计划数据", productionCode);
                    Log.Write(strServiceCode, LogLevel.Error, msg);
                    recordMsg.set("msg", msg).set("error", false);
                    return false;
                }

                //5.2.1.1.3.存在手工计划
                if (list_manual_plan.size() == 1) {
                    int OutTrackCmd = -1;
                    switch (list_manual_plan.get(0).getStr("MOVE_DIRECTION")) {
                        //返修工位
                        case "1":
                            OutTrackCmd = 18;
                            break;
                        //评审工位1
                        case "2":
                            OutTrackCmd = 19;
                            break;
                        //评审工位2
                        case "3":
                            OutTrackCmd = 20;
                            break;
                        //出口转盘
                        case "4":
                            OutTrackCmd = 21;
                            break;
                        //涂装缓存
                        case "0":
                            OutTrackCmd = 17;
                            break;
                        default:
                            msg = String.format("执行手工计划失败，车辆[%s]手工搬出计划去向[%s]未定义",
                                    productionCode, list_manual_plan.get(0).getStr("MOVE_DIRECTION"));
                            Log.Write(strServiceCode, LogLevel.Error, msg);
                            recordMsg.set("msg", msg).set("error", false);
                            return false;
                    }

                    //5.2.1.1.3.1.更新手工计划执行状态
                    Db.update("UPDATE t_wbs_move_out_manual_plan SET plan_status=1, execute_time=SYSDATE WHERE production_code=? AND plan_status=0",
                            list_manual_plan.get(0).getStr("PRODUCTION_CODE"));

                    //下达指令
                    Record recordShorts = ShiftingmMachineOutOneComFun.getMemoryValuesFromVpart(vpartCode);
                    Db.update(ShiftingmMachineOutOneComFun.getWriteMemorySql(),
                            OutTrackCmd,
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

                    msg = String.format("执行手工计划[%s],生产编码[%s],下达指令[%s]",
                            list_manual_plan.get(0).getStr("ID"), productionCode, OutTrackCmd);
                    Log.Write(strServiceCode, LogLevel.Information, msg);
                    recordMsg.set("msg", msg).set("error", true);
                    return true;
                }

                //5.2.2.空撬或直通车处理逻辑
                if (vpartCode.substring(0, 5).equals("WBSBB")
                        || list_demand_prodect.get(0).getStr("K_IS_WBS_DIRECT_CAR").equals("1")) {
                    //4.1.下达指令
                    int OutTrackCmd = 17;
                    Record recordShorts = ShiftingmMachineOutOneComFun.getMemoryValuesFromVpart(vpartCode);
                    Db.update(ShiftingmMachineOutOneComFun.getWriteMemorySql(),
                            OutTrackCmd,
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

                    msg = String.format("车辆，生产编码[%s],钢码号[%s],识别为空撬或直通车，把空撬或直通车指示涂装缓存,下达指令[%s]", productionCode, vpartCode, 17);
                    Log.Write(strServiceCode, LogLevel.Information, msg);
                    recordMsg.set("msg", msg).set("error", true);
                    return true;
                }

                //5.2.3.返修车处理逻辑
                if (list_demand_prodect.get(0).getStr("K_IS_WBS_REPAIR_CAR").equals("1")) {
                    int OutTrackCmd = 18;

                    //下达指令
                    Record recordShorts = ShiftingmMachineOutOneComFun.getMemoryValuesFromVpart(vpartCode);
                    Db.update(ShiftingmMachineOutOneComFun.getWriteMemorySql(),
                            OutTrackCmd,
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

                    msg = String.format("执行返修车处理，生产编码[%s],下达指令[%s]", productionCode, OutTrackCmd);
                    Log.Write(strServiceCode, LogLevel.Information, msg);
                    recordMsg.set("msg", msg).set("error", true);
                    return true;
                }

                //5.2.4.评审车处理逻辑
                if (list_demand_prodect.get(0).getStr("K_IS_WBS_REVIEW_CAR").equals("1")) {
                    //5.2.4.1.获取评审工位1内存地址组值
                    List<Record> list_review = Db.find("SELECT rm.* FROM T_WBS_READ_MEMORY rm where rm.GROUP_MEMORY_CODE=?", "no1.review.station");
                    if (list_review.size() != 1) {
                        msg = String.format("判断评审工位1是否运行进车，读取wbs内存地址组【%s】配置错误，请检查配置", groupMemoryCode);
                        Log.Write(strServiceCode, LogLevel.Error, msg);
                        recordMsg.set("msg", msg).set("error", false);
                        return false;
                    }

                    //5.2.4.1.1.判断评审工位1是否运行进车
                    if (list_review.get(0).getInt("LOGIC_MEMORY_VALUE") == 0) {
                        int OutTrackCmd = 19;

                        //下达指令
                        Record recordShorts = ShiftingmMachineOutOneComFun.getMemoryValuesFromVpart(vpartCode);
                        Db.update(ShiftingmMachineOutOneComFun.getWriteMemorySql(),
                                OutTrackCmd,
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

                        msg = String.format("执行评审车处理，生产编码[%s],下达指令[%s]", productionCode, OutTrackCmd);
                        Log.Write(strServiceCode, LogLevel.Information, msg);
                        recordMsg.set("msg", msg).set("error", true);
                        return true;
                    }

                    //5.2.4.2.获取评审工位2内存地址组值
                    list_review = Db.find("SELECT rm.* FROM T_WBS_READ_MEMORY rm where rm.GROUP_MEMORY_CODE=?", "no2.review.station");
                    if (list_review.size() != 1) {
                        msg = String.format("判断评审工位2是否运行进车，读取wbs内存地址组【%s】配置错误，请检查配置", groupMemoryCode);
                        Log.Write(strServiceCode, LogLevel.Error, msg);
                        recordMsg.set("msg", msg).set("error", false);
                        return false;
                    }
                    if (list_review.get(0).getInt("LOGIC_MEMORY_VALUE") == 0) {
                        int OutTrackCmd = 20;

                        //下达指令
                        Record recordShorts = ShiftingmMachineOutOneComFun.getMemoryValuesFromVpart(vpartCode);
                        Db.update(ShiftingmMachineOutOneComFun.getWriteMemorySql(),
                                OutTrackCmd,
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

                        msg = String.format("执行评审车处理，生产编码[%s],下达指令[%s]", productionCode, OutTrackCmd);
                        Log.Write(strServiceCode, LogLevel.Information, msg);
                        recordMsg.set("msg", msg).set("error", true);
                        return true;
                    }
                }

                //5.2.5.返回车处理逻辑
                if (list_demand_prodect.get(0).getStr("K_IS_WBS_BACK_CAR").equals("1")) {
                    int OutTrackCmd = 21;

                    //下达指令
                    Record recordShorts = ShiftingmMachineOutOneComFun.getMemoryValuesFromVpart(vpartCode);
                    Db.update(ShiftingmMachineOutOneComFun.getWriteMemorySql(),
                            OutTrackCmd,
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

                    msg = String.format("执行返回车处理，生产编码[%s],下达指令[%s]", productionCode, OutTrackCmd);
                    Log.Write(strServiceCode, LogLevel.Information, msg);
                    recordMsg.set("msg", msg).set("error", true);
                    return true;
                }

                //5.2.6.执行正常车逻辑
                int OutTrackCmd = 17;

                //下达指令
                Record recordShorts = ShiftingmMachineOutOneComFun.getMemoryValuesFromVpart(vpartCode);
                Db.update(ShiftingmMachineOutOneComFun.getWriteMemorySql(),
                        OutTrackCmd,
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

                msg = String.format("执行正常车处理，生产编码[%s],下达指令[%s]", productionCode, OutTrackCmd);
                Log.Write(strServiceCode, LogLevel.Information, msg);
                recordMsg.set("msg", msg).set("error", true);
                return true;
            }
        });
        return recordMsg;
    }

    /**
     * 车辆离开计算：
     * A:如果车辆离开方向为涂装缓存，并且非空撬，则需要写入实绩点为wbs01_out的过点记录;
     * B:如果车辆离开方向为返修工位，则需要把返修车标记取消
     * C:如果车辆离开方向为评审工位1或评审工位2，则需要把评审车标记取消
     * D:如果车辆离开方向为出口转盘，则需要把返回车标记取消
     *
     * @param strServiceCode
     * @param memoryRead
     * @return
     */
    private Record passDeal(String strServiceCode, Record memoryRead) {
        String msg = "";
        Record recordMsg = new Record().set("msg", "").set("error", true);
        Log.Write(strServiceCode, LogLevel.Information, String.format("控制命令字[%s]开始计算", memoryRead.getStr("LOGIC_MEMORY_VALUE")));

        //1.根据内存地址组的值，获取白车身钢码号
        String vpartCode = ShiftingmMachineOutOneComFun.getVpartFromMemoryValues(memoryRead);
        if (vpartCode == null || vpartCode.length() == 0 || vpartCode.equals("null")) {
            msg = String.format("根据内存地址组的值[%s],获取白车身钢码号错误", memoryRead.toJson());
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("msg", msg).set("error", false);
            return recordMsg;
        }

        //2.获取需求产品表信息
        List<Record> list_demand_prodect = Db.find(ShiftingmMachineOutOneComFun.getDemandProductInfoFromVpartCodeCodeSql(), vpartCode);
        if (list_demand_prodect.size() != 1) {
            msg = String.format("根据白车身钢码号[%s],获取需求产品信息异常", vpartCode);
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("msg", msg).set("error", false);
            return recordMsg;
        }

        //2.1.获取生产编码
        String productionCode = list_demand_prodect.get(0).getStr("PRODUCTION_CODE");

        //开启事务
        Db.tx(new IAtom() {
            @Override
            public boolean run() throws SQLException {
                String msg = "";
                final String dest_code;

                //3.处理车辆离开到达目标区域
                switch (memoryRead.getStr("LOGIC_MEMORY_VALUE")) {
                    //移行机把车放至涂装缓存
                    case "22":
                        dest_code = "wbs10";

                        //非空撬处理，写入过点记录
                        if (!vpartCode.substring(0, 5).equals("WBSBB")) {
                            Db.update(ShiftingmMachineOutOneComFun.addActualPassedRecord(),
                                    "wbs01_out",
                                    productionCode,
                                    "tz0101",
                                    "tz0101",
                                    list_demand_prodect.get(0).getStr("DEMAND_PRODUCT_CODE"));
                            msg = String.format("新增过点实绩信息，生成编码[%s],实绩点[%s]", productionCode, "wbs01_out");
                            Log.Write(strServiceCode, LogLevel.Information, msg);
                        }
                        break;
                    //移行机把车放至返修工位
                    case "23":
                        dest_code = "wbs07";

                        //取消返修车标记
                        Db.update("UPDATE T_PLAN_DEMAND_PRODUCT SET K_IS_WBS_REPAIR_CAR=0,K_IS_WBS_REVIEW_CAR=0,K_IS_WBS_BACK_CAR=0 where DEMAND_PRODUCT_TYPE IN (0,5,6) and  PRODUCTION_CODE=?", productionCode);
                        break;
                    //移行机把车放至评审工位1
                    case "24":
                        dest_code = "wbs08";

                        //取消评审车标记
                        Db.update("UPDATE T_PLAN_DEMAND_PRODUCT SET K_IS_WBS_REPAIR_CAR=0,K_IS_WBS_REVIEW_CAR=0,K_IS_WBS_BACK_CAR=0 where DEMAND_PRODUCT_TYPE IN (0,5,6) and  PRODUCTION_CODE=?", productionCode);
                        break;
                    //移行机把车放至评审工位2
                    case "25":
                        dest_code = "wbs09";

                        //取消评审车标记
                        Db.update("UPDATE T_PLAN_DEMAND_PRODUCT SET K_IS_WBS_REPAIR_CAR=0,K_IS_WBS_REVIEW_CAR=0,K_IS_WBS_BACK_CAR=0 where DEMAND_PRODUCT_TYPE IN (0,5,6) and  PRODUCTION_CODE=?", productionCode);
                        break;
                    //移行机把车放至出口转盘
                    case "26":
                        dest_code = "wbs16";

                        //取消返回车标记
                        Db.update("UPDATE T_PLAN_DEMAND_PRODUCT SET K_IS_WBS_REPAIR_CAR=0,K_IS_WBS_REVIEW_CAR=0,K_IS_WBS_BACK_CAR=0 where DEMAND_PRODUCT_TYPE IN (0,5,6) and  PRODUCTION_CODE=?", productionCode);
                        break;
                    default:
                        msg = String.format("");
                        Log.Write(strServiceCode, LogLevel.Error, msg);
                        recordMsg.set("msg", msg).set("error", false);
                        return false;
                }

                //4.车辆移动
                msg = (String) Db.execute(new ICallback() {
                    @Override
                    public Object call(Connection conn) throws SQLException {
                        //执行搬出程序结果信息
                        String msg = "";

                        CallableStatement proc = conn.prepareCall("{CALL PROC_MOVE_CAR(?,?,?,?)}");
                        proc.setString("PARA_PRODUCTION_CODE", productionCode);
                        proc.setString("PARA_DEST_ZONE", dest_code);
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
                    msg = String.format("般车成功，把车[%s],般道区域[%s]", productionCode, dest_code);
                    Log.Write(strServiceCode, LogLevel.Information, msg);
                } else {
                    msg = String.format("把车[%s],般道区域[%s]失败，原因[%s]", productionCode, dest_code, msg);
                    Log.Write(strServiceCode, LogLevel.Error, msg);
                    recordMsg.set("msg", msg).set("error", false);
                    return false;
                }

                //5.清空指令
                Record recordShorts = ShiftingmMachineOutOneComFun.getMemoryValuesFromVpart("");
                Db.update(ShiftingmMachineOutOneComFun.getWriteMemorySql(),
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
                return true;
            }
        });

        return recordMsg;
    }

    /**
     * 接空撬处理逻辑：
     * 模式1：均衡，即：实车和空撬均衡，排空撬必须满足设定的连续投入台数和间隔台数
     * 模式2：连续排空撬，即：优先把可搬出5道空撬排排完，然后按车道优先级把其他车道空撬排出，等空撬排完再排出实车
     *
     * @param strServiceCode
     * @param menoryRead
     * @return
     */
    private Record EmptyCarDeal(String strServiceCode, Record menoryRead) {

        String msg = "";
        Record recordMsg = new Record().set("msg", "").set("error", true);
        Log.Write(strServiceCode, LogLevel.Information, String.format("控制命令字[%s]空撬搬出逻辑处理开始", menoryRead.getStr("LOGIC_MEMORY_VALUE")));

        //1.获取wbs涂装缓存可搬入空位
        Record recordDeviceStatus = Db.findFirst("SELECT t.DEVICE_STATUS FROM t_wbs_device_signal_status t WHERE t.device_signal_code = 'SR_017'");
        if (recordDeviceStatus == null) {
            msg = String.format("获取涂装出口占位信息失败");
            Log.Write(strServiceCode, LogLevel.Information, msg);
            recordMsg.set("msg", msg);
            return recordMsg;
        }


        //2.获取排空撬参数设定值:空撬连续投入台数、空撬间隔台数和空撬投入模式
        Record rec_service_para = Db.findFirst("SELECT to_number(t.service_para1_value) PARA_ICNT,to_number(t.service_para2_value) PARA_INTERVAL, to_number(t.service_para3_value) PARA_TYPE FROM t_sys_service t WHERE t.service_code=?", strServiceCode);
        if (rec_service_para == null) {
            msg = String.format("获取空撬连续投入台数和间隔台数的参数值失败");
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("msg", msg);
            return recordMsg;
        }

        //2.1.获取排空撬参数——空撬连续投入台数
        int para_icnt = rec_service_para.getInt("PARA_ICNT");

        //2.2.获取排空撬参数——空撬投入间隔台数
        int para_interval = rec_service_para.getInt("PARA_INTERVAL");

        //2.3.获取排空撬参数——空撬投入模式
        int para_type = rec_service_para.getInt("PARA_TYPE");

        //3.空撬投入模式处理
        switch (para_type) {
            case 0:
                /**
                 * 3.0.1获取快速道直通车所在车道头车是否为空撬(空撬当车，必须把空撬排走，不考虑投入台数和间隔台数)：
                 * 是：接头车，结束
                 * 否：继续其他情况
                 */
                List<Record> list_wbs05_05_top = Db.find(ShiftingmMachineOutOneComFun.getEmptyCarOfDirectCar());
                if (list_wbs05_05_top.size() != 0) {

                    if (recordDeviceStatus.getStr("DEVICE_STATUS").equals("1")) {
                        msg = "涂装缓存出口有车占位，不接去向为涂装缓存的车，移行机不留车";
                        Log.Write(strServiceCode, LogLevel.Information, msg);
                        recordMsg.set("msg", msg);
                        return recordMsg;
                    }

                    //3.0.1.1.快速道直通车头车是为空撬
                    String vpartCode = "";
                    int OutTrackCmd = ShiftingmMachineOutOneComFun.getShiftingMachineOutOnePickCarZoneCodeOfInstructionsRela().getInt(list_wbs05_05_top.get(0).getStr("ZONE_CODE"));
                    Record recordShorts = ShiftingmMachineOutOneComFun.getMemoryValuesFromVpart(vpartCode);
                    Db.update(ShiftingmMachineOutOneComFun.getWriteMemorySql(),
                            OutTrackCmd,
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

                    msg = String.format("执行空撬间隔投入模式处理逻辑，接快速道直通车所在车道头车空撬，生产编码[%s],下达指令[%s]",
                            list_wbs05_05_top.get(0).getStr("PRODUCTION_CODE"), OutTrackCmd);
                    Log.Write(strServiceCode, LogLevel.Information, msg);
                    recordMsg.set("msg", msg).set("error", true);
                    return recordMsg;
                }


                /**
                 * 3.0.2.获取可搬出区域计划顺序号最小的车所在车道头车是否为空撬(空撬当车，必须把空撬排走，不考虑投入台数和间隔台数)：
                 * 是：接头车，结束
                 * 否：继续其他情况
                 */
                List<Record> list_min_top = Db.find(ShiftingmMachineOutOneComFun.getMinCarTopIsEmptyCar());
                if (list_min_top.size() != 0) {

                    if (recordDeviceStatus.getStr("DEVICE_STATUS").equals("1")) {
                        msg = "涂装缓存出口有车占位，不接去向为涂装缓存的车，移行机不留车";
                        Log.Write(strServiceCode, LogLevel.Information, msg);
                        recordMsg.set("msg", msg);
                        return recordMsg;
                    }

                    //3.0.2.1.执行接顺序号最小的车所在头车空撬
                    String vpartCode = "";
                    int OutTrackCmd = ShiftingmMachineOutOneComFun.getShiftingMachineOutOnePickCarZoneCodeOfInstructionsRela().getInt(list_min_top.get(0).getStr("ZONE_CODE"));
                    Record recordShorts = ShiftingmMachineOutOneComFun.getMemoryValuesFromVpart(vpartCode);
                    Db.update(ShiftingmMachineOutOneComFun.getWriteMemorySql(),
                            OutTrackCmd,
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

                    msg = String.format("执行空撬间隔投入模式处理逻辑，接可搬出区域计划顺序号最小的车所在车道头车空撬，生产编码[%s],下达指令[%s]",
                            list_min_top.get(0).getStr("PRODUCTION_CODE"), OutTrackCmd);
                    Log.Write(strServiceCode, LogLevel.Information, msg);
                    recordMsg.set("msg", msg).set("error", true);
                    return recordMsg;
                }


                /**
                 * 3.0.3.获取可搬出区域头车为空撬区域
                 * A:是否达到最大连续投入台数
                 * B:是否达到投入间隔台数
                 *
                 */
                List<Record> list_empty_all_zone_type0 = Db.find(ShiftingmMachineOutOneComFun.getEmptyCarOfAllZone());
                if (list_empty_all_zone_type0.size() != 0) {

                    //3.0.3.1.获取空撬连续投入台数和间隔台数
                    List<Record> list_empty_count = Db.find(ShiftingmMachineOutOneComFun.getEmptyCarCountAndInterval());
                    if (list_empty_count.size() != 1) {
                        msg = String.format("获取空撬连续投入台数和间隔台数失败");
                        Log.Write(strServiceCode, LogLevel.Error, msg);
                        recordMsg.set("msg", msg).set("error", true);
                        return recordMsg;
                    }

                    //3.0.3.1.1获取空撬连续投入台数
                    int icnt = list_empty_count.get(0).getInt("ICNT");

                    //3.0.3.1.2获取空撬投入间隔台数
                    int icnt_interval = list_empty_count.get(0).getInt("ICNT_INTERVAL");

                    //3.0.3.2.空撬投入达到最大连续数
                    if (icnt >= para_icnt) {
                        msg = String.format("空撬投入达到最大连续数，最大连续投入台数设定值[%s],当前连续投入台数[%s]", para_icnt, icnt);
                        Log.Write(strServiceCode, LogLevel.Warning, msg);
                        recordMsg.set("msg", "").set("error", true);
                        return recordMsg;
                    }

                    //3.0.3.3.空撬投入间隔台数未达到参数设定台数
                    if (icnt_interval < para_interval && icnt_interval > 0) {
                        msg = String.format("空撬投入间隔台数未达到参数设定台数，投入间隔台数设定值[%s],当前间隔台数[%s]", para_interval, icnt_interval);
                        Log.Write(strServiceCode, LogLevel.Warning, msg);
                        recordMsg.set("msg", "").set("error", true);
                        return recordMsg;

                    }

                    if (recordDeviceStatus.getStr("DEVICE_STATUS").equals("1")) {
                        msg = "涂装缓存出口有车占位，不接去向为涂装缓存的车，移行机不留车";
                        Log.Write(strServiceCode, LogLevel.Information, msg);
                        recordMsg.set("msg", msg);
                        return recordMsg;
                    }

                    //3.0.3.4下达接车指令
                    String vpartCode = "";
                    int OutTrackCmd = ShiftingmMachineOutOneComFun.getShiftingMachineOutOnePickCarZoneCodeOfInstructionsRela().getInt(list_empty_all_zone_type0.get(0).getStr("ZONE_CODE"));
                    Record recordShorts = ShiftingmMachineOutOneComFun.getMemoryValuesFromVpart(vpartCode);
                    Db.update(ShiftingmMachineOutOneComFun.getWriteMemorySql(),
                            OutTrackCmd,
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

                    msg = String.format("执行空撬间隔投入模式处理逻辑，接可搬出区域车道头车空撬，生产编码[%s],下达指令[%s]",
                            list_empty_all_zone_type0.get(0).getStr("PRODUCTION_CODE"), OutTrackCmd);
                    Log.Write(strServiceCode, LogLevel.Information, msg);
                    recordMsg.set("msg", msg).set("error", true);
                    return recordMsg;
                }

                return recordMsg.set("msg", "").set("error", true);
            case 1:
                /**
                 * 3.1.1.获取可搬出区域头车为空撬区域，按车道优先级顺序排序
                 */
                List<Record> list_empty_all_zone_type1 = Db.find(ShiftingmMachineOutOneComFun.getEmptyCarOfAllZone());
                if (list_empty_all_zone_type1.size() != 0) {

                    if (recordDeviceStatus.getStr("DEVICE_STATUS").equals("1")) {
                        msg = "涂装缓存出口有车占位，不接去向为涂装缓存的车，移行机不留车";
                        Log.Write(strServiceCode, LogLevel.Information, msg);
                        recordMsg.set("msg", msg);
                        return recordMsg;
                    }

                    //下达接车指令
                    String vpartCode = "";
                    int OutTrackCmd = ShiftingmMachineOutOneComFun.getShiftingMachineOutOnePickCarZoneCodeOfInstructionsRela().getInt(list_empty_all_zone_type1.get(0).getStr("ZONE_CODE"));
                    Record recordShorts = ShiftingmMachineOutOneComFun.getMemoryValuesFromVpart(vpartCode);
                    Db.update(ShiftingmMachineOutOneComFun.getWriteMemorySql(),
                            OutTrackCmd,
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

                    msg = String.format("执行空撬连续投入模式处理逻辑，接可搬出区域车道头车空撬，生产编码[%s],下达指令[%s]",
                            list_empty_all_zone_type1.get(0).getStr("PRODUCTION_CODE"), OutTrackCmd);
                    Log.Write(strServiceCode, LogLevel.Information, msg);
                    recordMsg.set("msg", msg).set("error", true);
                    return recordMsg;
                }

                return recordMsg.set("msg", msg).set("error", true);
            default:
                msg = String.format("空撬投入模式设定错误，0:间隔;1:连续投入,当前设定值[%s]", para_type);
                Log.Write(strServiceCode, LogLevel.Error, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
        }
    }
}


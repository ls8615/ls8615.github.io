package cvmes.pbs.pbsOutService;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.IAtom;
import com.jfinal.plugin.activerecord.ICallback;
import com.jfinal.plugin.activerecord.Record;
import cvmes.common.Log;
import cvmes.common.LogLevel;
import oracle.jdbc.OracleTypes;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class SmallAreaService {

    //内存地址组
    final String groupMemoryCode = "small.area.out.shifting.machine";
    //日志标记
    String logFlag = "SmallArea";

    String strServiceCode;

    public SmallAreaService(String strServiceCode) {
        this.strServiceCode = strServiceCode;
    }

    public String runBll(Record rec_service) throws Exception {
        return DealHeader();
    }

    /**
     * 逻辑处理入口
     */
    private String DealHeader() {
        //接车计算结果信息
        String msg = "";

        //1.获取PBS内存地址写表指令
        List<Record> list_memory_write = Db.find("SELECT 1 FROM T_PBS_WRITE_MEMORY wm where wm.GROUP_MEMORY_CODE=? AND wm.DEAL_STATUS=0", groupMemoryCode);
        if (list_memory_write.size() != 0) {
            msg = String.format("PBS写表内存地址组【%s】存在未处理指令，请检查设备通讯是否正常", groupMemoryCode);
            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
            return msg;
        }

        //2.获取PBS内存地址读表值
        List<Record> list_memory_read = Db.find("SELECT rm.* FROM T_PBS_READ_MEMORY rm where rm.GROUP_MEMORY_CODE=?", groupMemoryCode);
        if (list_memory_read.size() != 1) {
            msg = String.format("读取PBS内存地址组【%s】配置错误，请检查配置", groupMemoryCode);
            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
            return msg;
        }

        //2.1.获取PBS内存地址读表值——控制命令字值
        String memoryValue = list_memory_read.get(0).getStr("LOGIC_MEMORY_VALUE");

        //3.根据控制命令字处理
        switch (memoryValue) {
            //下位操作
            case "1":
            case "2":
            case "3":
            case "4":
            case "5":
            case "7":
            case "8":
            case "11":
            case "12":
                ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, String.format("地址组[%s],控制命令字[%s]下位操作。", groupMemoryCode, memoryValue));
                return "";

            //接车计算
            case "0":
                return pickCar(list_memory_read.get(0)).getStr("msg");

            //在位计算
            case "6":
                return reignCalculatingDirection(list_memory_read.get(0)).getStr("msg");

            //车辆离开移行机处理
            case "9":
            case "10":
                return passDeal(list_memory_read.get(0)).getStr("msg");

            default:
                msg = String.format("控制命令字[%s]错误，命令字没有定义,内存地址组[%s]", memoryValue, groupMemoryCode);
                ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
                return msg;

        }
    }

    /**
     * 小库区出口接车计算
     *
     * @param memoryRead
     * @return
     */
    private Record pickCar(Record memoryRead) {
        String msg = "";
        Record recordMsg = new Record().set("msg", "").set("error", true);
        ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, String.format("控制命令字[%s]开始计算", memoryRead.getStr("LOGIC_MEMORY_VALUE")));

        //0.判断服务状态
        if (!ExportDirectBlendServiceFunc.judgeRunStatus(strServiceCode, logFlag, "pbs_small_out_status")) {
            return recordMsg.set("msg", "服务暂停计算");
        }

        //1.接出口转盘
        //1.1.判断是否有车正在从大库存往小库区放车
        if (ExportDirectBlendServiceFunc.isRunningCarBitAreaToSmallArea()) {
            //1.1.1.获取小库区出口转盘内存地址值
            Record recordMemory = Db.findFirst("SELECT rm.* FROM T_PBS_READ_MEMORY rm where rm.GROUP_MEMORY_CODE=?", "small.area.out.turntable");

            //1.1.1.1.获取小库区出口转盘内存地址值错误
            if (recordMemory == null) {
                msg = String.format("获取小库区出口转盘内存地址值错误，内存地址组[%s]", "small.area.out.turntable");
                ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
            }

            //1.1.1.2.获取小库区出口转盘控制命令字值
            String memoryCmdValue = recordMemory.getStr("LOGIC_MEMORY_VALUE");

            //1.1.1.2.1.等待接车
            if (!memoryCmdValue.equals("3")) {
                msg = String.format("小库区出口移行机接大库区往小库区的车逻辑，等待车辆到位，内存地址组[%s]，命令字[%s]", "small.area.out.turntable", memoryCmdValue);
                ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
            }

            //1.1.2.下达指令
            String vpartCode = "";
            int OutTrackCmd = 5;
            Record recordShorts = ExportDirectBlendServiceFunc.getMemoryValuesFromVpart(vpartCode);
            Db.update(ExportDirectBlendServiceFunc.getWriteMemoryOfOtherSql(),
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

            msg = String.format("小库区出口移行机接大库区往小库区的车逻辑，接小库区出口转盘车，下达指令[%s]", OutTrackCmd);
            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, msg);
            recordMsg.set("msg", msg).set("error", true);

            return recordMsg;
        }

        //2.总装1线跟随车计算
        Record recMsg = execManualPlan(memoryRead, "zz0101");
        if (!recMsg.getStr("msg").equals("")) {
            return recMsg;
        }

        //3.总装2线跟随车计算
        recMsg = execManualPlan(memoryRead, "zz0102");
        if (!recMsg.getStr("msg").equals("")) {
            return recMsg;
        }

        /**
         * 3.无手工计划执行：
         * 详细逻辑看方法注解
         */
        return execOutNormal(memoryRead);
    }

    /**
     * 小库区出口移行机接车逻辑——手工计划执行
     *
     * @param memoryRead 小库区出口移行机内存地址组
     * @param line_code  生产线编码
     * @return
     */
    private Record execManualPlan(Record memoryRead, String line_code) {
        String msg = "";
        //计算结果对象，msg值为空字符串，表示无计算结果，其他值表示计算结果或错误信息
        Record recordMsg = new Record().set("msg", "");
        ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, String.format("小库区出口移行机手工计划处理逻辑开始计算，生产线编码[%s]", line_code));

        //1.获取总装生产线最后车生产编码
        String last_passed_trim_production_code;
        switch (line_code) {
            case "zz0101":
                last_passed_trim_production_code = ExportDirectBlendServiceFunc.getLineOneFollowCarProdutionCode(strServiceCode, logFlag);
                break;
            case "zz0102":
                last_passed_trim_production_code = ExportDirectBlendServiceFunc.getLineTowFollowCarProdutionCode(strServiceCode, logFlag);
                break;
            default:
                msg = String.format("生产线编码未定义，生产线编码[%s]", line_code);
                recordMsg.set("msg", msg);
                ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
                return recordMsg;
        }

        //1.1.无法获取到总装生产线最后出车生产编码（PS：首次允许会无该值，所以只记录log，不返回计算结果）
        if (last_passed_trim_production_code == null) {
            msg = String.format("获取总装生产线[%s]最后出车生产编码错误", line_code);
            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Warning, msg);
            recordMsg.set("msg", "");

            return recordMsg;
        }

        //2.获取小库区出口手工计划
        List<Record> list_manual_plan = Db.find("SELECT * FROM t_pbs_out_mplan_small t WHERE t.follow_production_code=? AND t.plan_status=0", last_passed_trim_production_code);

        //2.1.小库区出口手工计划异常
        if (list_manual_plan.size() > 1) {
            msg = String.format("获取总装生产线小库区出口手工计划异常,跟随车生产编码[%s]，生产线编码[%s]存在多个未执行手工计划，未执行计划个数[%s]", last_passed_trim_production_code, line_code, list_manual_plan.size());
            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
            recordMsg.set("msg", msg);

            return recordMsg;
        }

        //2.2.总装生产线存在最后出车跟随手工计划
        if (list_manual_plan.size() == 1) {
            //2.2.1.获取最后出车小库区出口跟随计划车所在区域信息
            List<Record> list_zone_all_car = Db.find(SmallAreaFun.getZoneAllCarByProductionSql(), list_manual_plan.get(0).getStr("PRODUCTION_CODE"));
            if (list_zone_all_car.size() == 0) {
                msg = String.format("获取获取指定生产编码所在小库区的区域所有车信息失败，车辆不在小库区或生产编码有误，生产编码[%s],生产线编码[%s]",
                        list_manual_plan.get(0).getStr("PRODUCTION_CODE"),
                        line_code);
                ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
                recordMsg.set("msg", msg);

                return recordMsg;
            }

            //2.2.1.1.手工计划跟随车是否在返回到或小库区入口移行机
            if (list_zone_all_car.get(0).getStr("ZONE_CODE").equals("pbs08_06") ||
                    list_zone_all_car.get(0).getStr("ZONE_CODE").equals("pbs07")) {
                msg = String.format("小库区出口移行机手工计划处理逻辑,手工计划车[%s]在小库区返回道或小库区入口移行机，等待车辆进库。手工计划车所在区域编码[%s]",
                        list_manual_plan.get(0).getStr("PRODUCTION_CODE"),
                        list_zone_all_car.get(0).getStr("ZONE_CODE")
                );
                ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, msg);
                recordMsg.set("msg", msg);

                return recordMsg;
            }

            //2.2.1.2.手工计划车不在小库区，等待车辆进库，或人工干预取消手工计划
            if (!(list_zone_all_car.get(0).getStr("ZONE_CODE").equals("pbs08_01") ||
                    list_zone_all_car.get(0).getStr("ZONE_CODE").equals("pbs08_02") ||
                    list_zone_all_car.get(0).getStr("ZONE_CODE").equals("pbs08_03") ||
                    list_zone_all_car.get(0).getStr("ZONE_CODE").equals("pbs08_04") ||
                    list_zone_all_car.get(0).getStr("ZONE_CODE").equals("pbs08_05")
            )) {
                msg = String.format("小库区出口移行机手工计划处理逻辑,手工计划车[%s],不在小库区车道，等待车辆进库或人工取消手工计划。手工计划车所在区域编码[%s]",
                        list_manual_plan.get(0).getStr("PRODUCTION_CODE"),
                        list_zone_all_car.get(0).getStr("ZONE_CODE")
                );
                ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, msg);
                recordMsg.set("msg", msg);

                return recordMsg;
            }

            //2.2.2.最后出车小库区出口跟随计划车在区域头车
            if (list_zone_all_car.get(0).getStr("PRODUCTION_CODE").equals(list_manual_plan.get(0).getStr("PRODUCTION_CODE"))) {
                //开始事务
                Db.tx(new IAtom() {
                    @Override
                    public boolean run() throws SQLException {
                        String msg = "";
                        try {
                            //2.2.2.1.判断是否有车正在从小库区往大库区出口移行机放车
                            if (!ExportDirectBlendServiceFunc.isAllowPickCarSmallAreaToBitArea()) {
                                msg = String.format("存在车辆从小库区往大库区出口移行机放车或存在大库区放小库区放车，小库区出口转盘和小库区出口等待工位不留车规则，小库区不接放行大库区出口移行机方向车");
                                ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, msg);
                                recordMsg.set("msg", "");
                                return false;
                            }

                            //2.2.2.1.1.判断升降机是否有车占位
                            if (ExportDirectBlendServiceFunc.isReginCarOfOutStation(strServiceCode, logFlag, line_code)) {
                                msg = String.format("执行小库区出口手工计划跟随车为车道头车逻辑，出口升降机有车占位，生产线编码[%s]", line_code);
                                ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, msg);
                                recordMsg.set("msg", "");
                                return false;
                            }

                            //2.2.2.2.取消手工计划车返回车标记
                            int statusvalue = 0;
                            int recIcnt = Db.update(ExportDirectBlendServiceFunc.getUpdatePbsBackCar(), statusvalue, list_zone_all_car.get(0).getStr("PRODUCTION_CODE"));
                            if (recIcnt < 0) {
                                msg = String.format("取消车辆返回车标记失败，生产编码[%s],生产线编码[%s],标记值[%s]", list_zone_all_car.get(0).getStr("PRODUCTION_CODE"), line_code, statusvalue);
                                ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
                                recordMsg.set("msg", msg);
                                return false;
                            }

                            //2.2.2.3.更新手工计划状态为已处理
                            recIcnt = Db.update(SmallAreaFun.updateManualPlanStatus(), list_manual_plan.get(0).getStr("ID"));
                            if (recIcnt < 0) {
                                msg = String.format("标记手工计划为已处理失败，计划ID[%s]，生产编码[%s],生产线编码[%s]",
                                        list_manual_plan.get(0).getStr("ID"),
                                        list_zone_all_car.get(0).getStr("PRODUCTION_CODE"),
                                        line_code);
                                ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
                                recordMsg.set("msg", msg);
                                return false;
                            }

                            //2.2.2.4.下达接车指令
                            String vpartCode = "";
                            int OutTrackCmd = SmallAreaFun.getShiftingMachineOutOnePickCarZoneCodeOfInstructionsRela().getInt(list_zone_all_car.get(0).getStr("ZONE_CODE"));
                            Record recordShorts = ExportDirectBlendServiceFunc.getMemoryValuesFromVpart(vpartCode);
                            Db.update(ExportDirectBlendServiceFunc.getWriteMemoryOfOtherSql(),
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

                            msg = String.format("执行小库区出口手工计划跟随车为车道头车逻辑，计划ID[%s],生产编码[%s],跟随车生产编码[%s]，生产线编码[%s],下达指令[%s]",
                                    list_manual_plan.get(0).getStr("ID"),
                                    list_manual_plan.get(0).getStr("PRODUCTION_CODE"),
                                    last_passed_trim_production_code,
                                    line_code,
                                    OutTrackCmd);
                            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, msg);
                            recordMsg.set("msg", msg).set("error", true);

                            return true;
                        } catch (Exception ex) {
                            msg = String.format("执行小库区出口手工计划异常[%s]", ex.getMessage());
                            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
                            recordMsg.set("msg", msg);
                            return false;
                        }
                    }
                });

                return recordMsg;
            }

            /**
             * 2.2.3.最后出车小库区出口跟随计划车不在区域头车:
             * A：标记标记区域头车为返回车（修改为判断头车是否也存在手工计划并也是另外一总装生产线跟随计划）
             * B：下达接头车指令
             */
            //开启事务
            Db.tx(new IAtom() {
                @Override
                public boolean run() throws SQLException {
                    String msg = "";
                    //2.2.3.1.标记手工计划头车为返回车标记
                    int statusvalue = 1;
                    int recIcnt = Db.update(ExportDirectBlendServiceFunc.getUpdatePbsBackCar(), statusvalue, list_zone_all_car.get(0).getStr("PRODUCTION_CODE"));
                    if (recIcnt < 0) {
                        msg = String.format("标记小库区跟随车手工搬出计划所在区域头车返回车标记失败，区域头车生产编码[%s],跟随车生产编码[%s],生产线编码[%s],标记值[%s]",
                                list_zone_all_car.get(0).getStr("PRODUCTION_CODE"),
                                last_passed_trim_production_code,
                                line_code,
                                statusvalue);
                        ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
                        recordMsg.set("msg", msg);
                        return false;
                    }
                    ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information,
                            String.format("执行小库区出口移行机接车逻辑——最后出车小库区出口跟随计划车不在区域头车，标记头车返回车标记。最后出车生产编码[%s],手工计划车生产编码[%s],手工计划所在区域头车生产编码[%s]和区域编码[%s],",
                                    last_passed_trim_production_code,
                                    list_manual_plan.get(0).getStr("PRODUCTION_CODE"),
                                    list_zone_all_car.get(0).getStr("PRODUCTION_CODE"),
                                    list_zone_all_car.get(0).getStr("ZONE_CODE")));

                    //2.2.3.2.下达接头车指令
                    String vpartCode = "";
                    int OutTrackCmd = SmallAreaFun.getShiftingMachineOutOnePickCarZoneCodeOfInstructionsRela().getInt(list_zone_all_car.get(0).getStr("ZONE_CODE"));
                    Record recordShorts = ExportDirectBlendServiceFunc.getMemoryValuesFromVpart(vpartCode);
                    Db.update(ExportDirectBlendServiceFunc.getWriteMemoryOfOtherSql(),
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

                    msg = String.format("执行小库区出口手工计划跟随车不在车道头车逻辑，计划ID[%s],生产编码[%s],跟随车生产编码[%s]，生产线编码[%s],下达指令[%s]",
                            list_manual_plan.get(0).getStr("ID"),
                            list_manual_plan.get(0).getStr("PRODUCTION_CODE"),
                            last_passed_trim_production_code,
                            line_code,
                            OutTrackCmd);
                    ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, msg);
                    recordMsg.set("msg", msg).set("error", true);

                    return true;
                }
            });

            return recordMsg;
        }

        //2.3.小库区出口最后出车无手工计划
        return recordMsg.set("msg", "");
    }

    private Record execOutNormal(Record memoryRead) {
        //计算结果信息
        String msg, cmdMsg = "";
        Record recordMsg = new Record().set("msg", "");
        ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, String.format("控制命令字[%s]正常接车开始计算", memoryRead.getStr("LOGIC_MEMORY_VALUE")));

        //1.获取总装1线出口是否允许出车
        //1.1.总装1线出口允许出车
        //1.1.1.获取小库区（包括小库区出口转盘和小库区出口等待工位）总装1线非禁止车和大库区总装1线可搬出区域非禁止车所在车道头车计划顺序号最小的车所在区域信息
        //1.1.1.1.最小车所在区域为大库区，小库区不做接1线车指令，继续计算小库区总装2线非禁止车接车逻辑
        //1.1.1.2.最小车所在区域为小库区，把该车接到大库区出口移行机（ps：大库区出口移行机检查该逻辑，判断等待）

        //2.获取总装2线出口是否允许出车
        //2.1.总装2线出口允许出车
        //2.1.1.获取小库区（包括小库区出口转盘和小库区出口等待工位）总装2线非禁止车和大库区总装2线可搬出区域非禁止车所在车道头车计划顺序号最小的车所在区域信息
        //2.1.1.1.最小车所在区域为大库区，小库区不做接2线车指令，计算结束
        //2.1.1.2.最小车所在区域为小库区，把该车接到大库区出口移行机（ps:大库区出口移行机检查该逻辑，判断等待）


        //1.

        //3.导车逻辑-根据车道头车存在返回车标记，优先导总装1线车，再导总装2线车
        //3.1.获取小库区可搬出区域总装1线头车有返回车标记的车（如果存在多台车，按计划顺序号最小的优先导车）
        Record recMsg = execOutNormalBackCar("zz0101");
        if (!recMsg.getStr("msg").equals("")) {
            return recMsg;
        }
        //3.2.获取小库区可搬出区域总装2线头车有返回车标记的车（如果存在多台车，按计划顺序号最小的优先导车）
        recMsg = execOutNormalBackCar("zz0102");
        if (!recMsg.getStr("msg").equals("")) {
            return recMsg;
        }

        //4.正常车计算，优先总装1线车，再总装2线车
        //4.1.获取小库区可搬出区域总装1线车，按计划顺序号最小
        recMsg = execOutNormalMinPlan("zz0101");
        if (!recMsg.getStr("msg").equals("")) {
            return recMsg;
        }
        //3.2.获取小库区可搬出区域总装2线车，按计划顺序号最小
        recMsg = execOutNormalMinPlan("zz0102");
        if (!recMsg.getStr("msg").equals("")) {
            return recMsg;
        }

        return recordMsg;
    }

    /**
     * 小库区出口移行机接车逻辑——正常车导车处理逻辑
     *
     * @param line_code
     * @return
     */
    private Record execOutNormalBackCar(String line_code) {
        String msg = "";
        //计算结果对象，msg值为空字符串，表示无计算结果，其他值表示计算结果或错误信息
        Record recordMsg = new Record().set("msg", "");
        ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, String.format("小库区出口移行机导车处理逻辑开始计算，生产线编码[%s]", line_code));

        //1.获取小库区可搬出区域总装生产线头车为返回车信息，按计划顺序从小到大排序
        List<Record> list = Db.find(SmallAreaFun.getSmallAreaMinPlanSeqZoneInfoOfBackCarSql(), line_code);

        //1.1.小库区可搬出头车不存在总装生产线返回车
        if (list.size() == 0) {
            return recordMsg.set("msg", "");
        }

        //1.2.下达指令
        String vpartCode = "";
        int OutTrackCmd = SmallAreaFun.getShiftingMachineOutOnePickCarZoneCodeOfInstructionsRela().getInt(list.get(0).getStr("ZONE_CODE"));
        Record recordShorts = ExportDirectBlendServiceFunc.getMemoryValuesFromVpart(vpartCode);
        Db.update(ExportDirectBlendServiceFunc.getWriteMemoryOfOtherSql(),
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

        msg = String.format("执行小库区出口可搬出区域头车为返回车逻辑,生产线编码[%s],接车区域[%s],区域头车生产编码[%s],下达指令[%s]",
                line_code,
                list.get(0).getStr("ZONE_CODE"),
                list.get(0).getStr("PRODUCTION_CODE"),
                OutTrackCmd);
        ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, msg);
        recordMsg.set("msg", msg).set("error", true);
        return recordMsg;
    }

    /**
     * 小库区出口移行机接车逻辑——正常车计划顺序号最小车处理逻辑
     *
     * @param line_code
     * @return
     */
    private Record execOutNormalMinPlan(String line_code) {
        String msg = "";
        //计算结果对象，msg值为空字符串，表示无计算结果，其他值表示计算结果或错误信息
        Record recordMsg = new Record().set("msg", "");
        ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, String.format("正常车计划顺序号最小车处理逻辑开始计算，生产线编码[%s]", line_code));

        //1.获取总装生产线小库区可搬出区域正常车（非禁止车，非返回车）计划顺序号最小车所在区域信息
        List<Record> list = Db.find(SmallAreaFun.getSmallAreaMinPlanSeqZoneInfoOfNormalCarSql(), line_code);

        //1.1.不存在可搬出车辆
        if (list.size() == 0) {
            msg = String.format("小库区出口移行机接车——正常车计划顺序号最小车处理逻辑，无可搬出正常车，生产线编码[%s]", line_code);
            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Warning, msg);
            return recordMsg.set("msg", "");
        }

        //2.存在可搬出车辆
        //2.1.计划顺序号最小车在返回道或小库区入口移行机
        if (list.get(0).getStr("zone_code").equals("pbs08_06")||list.get(0).getStr("zone_code").equals("pbs08_07")) {
            msg = String.format("小库区出口移行机接车——正常车计划顺序号最小车处理逻辑，最小车在返回道或小库区入口移行机，等待车辆入库，生产线编码[%s]", line_code);
            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Warning, msg);
            return recordMsg.set("msg", "");
        }


        //2.2.可搬出车辆在区域头车
        if (list.get(0).getInt("PRODUCT_POS") == 1) {
            //2.2.1.获取总装生产线大库区可搬出区域头车和小库区正常车，按计划顺序号由小到大排序
            List<Record> list_small_bit_plan = Db.find(BitAreaFun.getBitAreaAndSmalAreaMinPlanSeqZoneInfoSql(), line_code);

            //2.2.1.1.获取总装生产线大库区可搬出区域头车和小库区正常车区域信息异常
            if (list_small_bit_plan.size() == 0) {
                msg = String.format("获取总装生产线大库区可搬出区域头车和小库区正常车,生产线编码[%s]", line_code);
                ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
            }

            //2.2.1.2.获取总装生产线大库区可搬出区域头车和小库区正常车,计划顺序号最小车不在小库区，等待大库区出车
            if (!list.get(0).getStr("PRODUCTION_CODE").equals(list_small_bit_plan.get(0).getStr("PRODUCTION_CODE"))) {
                msg = String.format("总装生产线大库区可搬出区域头车和小库区正常车,计划顺序号最小车不在小库区，等待大库区出车,生产线编码[%s]", line_code);
                ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, msg);
                recordMsg.set("msg", "");
                return recordMsg;
            }

            //2.2.2.计算可搬出车辆最小车所在区域——小库区
            //2.2.2.1.是否允许接车到大库区出口移行机
            if (!ExportDirectBlendServiceFunc.isAllowPickCarSmallAreaToBitArea()) {
                msg = String.format("计算可搬出车辆最小车所在区域小库区，存在车辆从大库区往小库区放或存在小库区往大库区出口移行机放车，等待计算。总装生产线[%s]", line_code);
                ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Warning, msg);
                return recordMsg.set("msg", "");
            }

            //2.2.2.1.1.总装生产线出口升降机是否有车占位
            if (ExportDirectBlendServiceFunc.isReginCarOfOutStation(strServiceCode, logFlag, line_code)) {
                msg = String.format("计算可搬出车辆最小车所在区域小库区，总装生产线[%s]出口升降机有车占位", line_code);
                ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Warning, msg);
                recordMsg.set("msg", "");
                return recordMsg;
            }

            //2.2.2.2.下达指令
            String vpartCode = "";
            int OutTrackCmd = SmallAreaFun.getShiftingMachineOutOnePickCarZoneCodeOfInstructionsRela().getInt(list.get(0).getStr("ZONE_CODE"));
            Record recordShorts = ExportDirectBlendServiceFunc.getMemoryValuesFromVpart(vpartCode);
            Db.update(ExportDirectBlendServiceFunc.getWriteMemoryOfOtherSql(),
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

            msg = String.format("小库区出口移行机接车逻辑——正常车计划顺序号最小车处理逻辑,生产线编码[%s],接车区域[%s],区域头车生产编码[%s],下达指令[%s]",
                    line_code,
                    list.get(0).getStr("ZONE_CODE"),
                    list.get(0).getStr("PRODUCTION_CODE"),
                    OutTrackCmd);
            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, msg);
            recordMsg.set("msg", msg).set("error", true);
            return recordMsg;
        }

        //2.2.可搬出车辆不在区域头车
        //2.2.1.获取区域头车信息
        List<Record> list_zone = Db.find(ExportDirectBlendServiceFunc.getZoneAllCarByProductionSql(), list.get(0).getStr("PRODUCTION_CODE"));

        //2.2.1.1.区域头车为禁止车
        if (list_zone.get(0).getStr("K_IS_PBS_DISABLE_CAR").equals("1")) {
            //2.2.1.1.1.标记头车为返回车
            Db.update("update T_PLAN_DEMAND_PRODUCT set K_IS_PBS_BACK_CAR=1 where production_code=? ", list_zone.get(0).getStr("PRODUCTION_CODE"));
            msg = String.format("库区出口移行机接车逻辑——正常车计划顺序号最小车处理逻辑,可搬出计划顺序号最小车不在车道头车，并且头车为禁止车，标记头车为返回车。计划顺序号最小车生产编码[%s]和生产线编码[%s]，区域头车生产编码[%s]和生产线编码[%s]",
                    list.get(0).getStr("production_code"),
                    list.get(0).getStr("line_code"),
                    list_zone.get(0).getStr("production_code"),
                    list_zone.get(0).getStr("line_code"));
            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, msg);

            //2.2.1.1.2.执行导车逻辑
            return execOutNormalBackCar(list_zone.get(0).getStr("LINE_CODE"));
        }

        //2.2.1.2.区域头车为正常车——并且为同一生产线
        if (list_zone.get(0).getStr("K_IS_PBS_DISABLE_CAR").equals("0") &&
                list_zone.get(0).getStr("K_IS_PBS_BACK_CAR").equals("0") &&
                list_zone.get(0).getStr("LINE_CODE").equals(list.get(0).getStr("line_code"))) {
            //2.2.1.2.1.标记头车为返回车
            Db.update("update T_PLAN_DEMAND_PRODUCT set K_IS_PBS_BACK_CAR=1 where production_code=? ", list_zone.get(0).getStr("PRODUCTION_CODE"));

            //2.2.1.2.2.执行导车逻辑
            return execOutNormalBackCar(line_code);
        }

        //2.2.1.3.区域头车为正常车——并且为不同一生产线
        if (list_zone.get(0).getStr("K_IS_PBS_DISABLE_CAR").equals("0") &&
                list_zone.get(0).getStr("K_IS_PBS_BACK_CAR").equals("0") &&
                (!list_zone.get(0).getStr("line_code").equals(list.get(0).getStr("line_code")))) {
            //2.2.1.3.1.获取头车总装生产线计划顺序是否比大库区可搬出区域头车计划顺序号小
            List<Record> list_min = Db.find(SmallAreaFun.getSmallAreaZoneCodeAanBitAreaMinPlanSeqZnotinfoSql(),
                    list_zone.get(0).getStr("ZONE_CODE"),
                    list_zone.get(0).getStr("LINE_CODE"),
                    list_zone.get(0).getStr("LINE_CODE"));
            if (list_min.size() == 0) {
                msg = String.format("获取大库区区域头车计划顺序号与小库区区域[%s]头车顺序号最小车信息错误，生产线编码[%s]",
                        list_zone.get(0).getStr("ZONE_CODE"),
                        list_zone.get(0).getStr("LINE_CODE"));
                ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
            }

            //2.2.1.3.1.1.头车是总装生产线可搬出车辆计划顺序号最小车
            if (list_min.get(0).getStr("zone_code").startsWith("pbs08_")) {
                //2.2.1.3.1.1.1.判断总装生产线出口升降机是否有车占位
                if (ExportDirectBlendServiceFunc.isReginCarOfOutStation(strServiceCode, logFlag, list_zone.get(0).getStr("LINE_CODE"))) {
                    msg = String.format("小库区出口移行机接车逻辑——正常车计划顺序号最小车处理逻辑，总装生产线[%s]出口升降机是有车占位", list_zone.get(0).getStr("LINE_CODE"));
                    ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Warning, msg);
                    recordMsg.set("msg", msg);
                    return recordMsg;
                }

                //2.2.1.3.1.1.2.获取总装生产线出口是否允许出车，如果允许出车，接头车，如果不允许出车，把头车标记返回车，并执行导车逻辑
                if (!ExportDirectBlendServiceFunc.isAllowPickCarSmallAreaToBitArea()) {
                    msg = String.format("存在车辆从小库区往大库区出口移行机放车或存在大库区放小库区放车，小库区出口转盘和小库区出口等待工位不留车规则，小库区不接放行大库区出口移行机方向车");
                    recordMsg.set("msg", msg);
                    return recordMsg;
                }

                //2.2.1.3.1.1.3.下达指令
                String vpartCode = "";
                int OutTrackCmd = SmallAreaFun.getShiftingMachineOutOnePickCarZoneCodeOfInstructionsRela().getInt(list_zone.get(0).getStr("ZONE_CODE"));
                Record recordShorts = ExportDirectBlendServiceFunc.getMemoryValuesFromVpart(vpartCode);
                Db.update(ExportDirectBlendServiceFunc.getWriteMemoryOfOtherSql(),
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

                msg = String.format("小库区出口移行机接车逻辑——正常车计划顺序号最小车处理逻辑,生产线编码[%s],接车区域[%s],区域头车生产编码[%s],下达指令[%s]",
                        list_zone.get(0).getStr("line_code"),
                        list.get(0).getStr("ZONE_CODE"),
                        list.get(0).getStr("PRODUCTION_CODE"),
                        OutTrackCmd);
                ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, msg);
                recordMsg.set("msg", msg).set("error", true);
                return recordMsg;
            }

            //提前导车，导致无限导车情况
           /* //2.2.1.3.1.2.头车不是总装生产线可搬出车辆计划顺序号最小车
            //2.2.1.3.1.2.1.标记头车为返回车
            Db.update("update t_plan_demand_product set K_IS_PBS_BACK_CAR=1 where production_code=? ", list_zone.get(0).getStr("PRODUCTION_CODE"));

            //2.2.1.3.1.2.2.执行导车逻辑
            return execOutNormalBackCar(list_zone.get(0).getStr("LINE_CODE"));*/
        }

        return recordMsg.set("msg", msg);
    }

    /**
     * 小库区出口移行机在位计算逻辑
     *
     * @param memoryRead
     * @return
     */
    private Record reignCalculatingDirection(Record memoryRead) {
        //计算结果信息
        String msg, cmdMsg = "";
        Record recordMsg = new Record().set("msg", "").set("error", true);
        ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, String.format("小库区出口移行机在位计算逻辑,控制命令字[%s]开始计算", memoryRead.getStr("LOGIC_MEMORY_VALUE")));

        //1.根据内存地址组的值，获取白车身钢码号
        String vpartCode = ExportDirectBlendServiceFunc.getVpartFromMemoryValues(memoryRead);
        if (vpartCode == null || vpartCode.length() == 0 || vpartCode.equals("null")) {
            msg = String.format("根据内存地址组的值[%s],获取白车身钢码号错误", memoryRead.toJson());
            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
            recordMsg.set("msg", msg).set("error", false);
            return recordMsg;
        }

        //2.获取需求产品表信息
        List<Record> list_demand_prodect = Db.find(ExportDirectBlendServiceFunc.getDemandProductByVpartSql(), vpartCode);
        if (list_demand_prodect.size() != 1) {
            msg = String.format("根据白车身钢码号[%s],获取需求产品信息异常", vpartCode);
            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
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
                proc.setString("PARA_DEST_ZONE", "pbs09");
                proc.setInt("PARA_IS_CHECK_POS", 1);
                proc.registerOutParameter("PARA_MSG", OracleTypes.VARCHAR);
                proc.execute();

                String ret = proc.getString("PARA_MSG");
                if (ret == null || ret.length() == 0 || ret.equals("null")) {
                    msg = "";
                } else {
                    if (ret.length() > 0) {
                        msg = String.format("移车失败，原因【%s】", ret);
                        ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
                    }
                }

                return msg;
            }
        });
        if (msg.length() == 0) {
            msg = String.format("般车成功，把车[%s],般道区域[%s]", productionCode, "pbs09");
            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, msg);
        } else {
            msg = String.format("把车[%s],般道区域[%s]失败，原因[%s]", productionCode, "pbs09", msg);
            recordMsg.set("msg", msg).set("error", false);
            return recordMsg;
        }

        //3.获取小库区出口等待工位内存地址值
        List<Record> list_memory_read = Db.find("SELECT rm.* FROM T_PBS_READ_MEMORY rm where rm.GROUP_MEMORY_CODE=?", "small.area.out.turntable");
        if (list_memory_read.size() != 1) {
            msg = String.format("读取PBS内存地址组【%s】配置错误，请检查配置", "small.area.out.wait.station");
            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
            recordMsg.set("msg", msg);
            return recordMsg;
        }

        //3.1.获取PBS内存地址读表值——控制命令字值
        String memoryValue = list_memory_read.get(0).getStr("LOGIC_MEMORY_VALUE");

        //3.2.判断在位车辆是否来自小库区出口转盘
        if (memoryValue.equals("3")) {
            //3.2.1.获取小库区出口转盘内存地址组钢码号
            String vpartCodeSmallTurntable = ExportDirectBlendServiceFunc.getVpartFromMemoryValues(list_memory_read.get(0));
            if (vpartCodeSmallTurntable == null || vpartCodeSmallTurntable.length() == 0 || vpartCodeSmallTurntable.equals("null")) {
                msg = String.format("根据内存地址组的值[%s],获取白车身钢码号错误", list_memory_read.get(0).toJson());
                ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
                recordMsg.set("msg", msg).set("error", false);
                return recordMsg;
            }

            //3.2.2.在位车辆来自小库区出口转盘，复位小库区出口转盘
            if (vpartCodeSmallTurntable.equals(vpartCode)) {
                int OutTrackCmd = 0;
                Record recordShorts = ExportDirectBlendServiceFunc.getMemoryValuesFromVpart("");
                Db.update(ExportDirectBlendServiceFunc.getWriteMemoryOfOtherSql(),
                        OutTrackCmd,
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
                        "small.area.out.turntable"
                );

                msg = String.format("小库区出口移行机在位计算——复位小库区出口转盘,生产线编码[%s],钢码号[%s],下达指令[%s]",
                        productionCode,
                        vpartCode,
                        OutTrackCmd);
                ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, msg);
            }
        }

        //4.判断服务状态
        if (!ExportDirectBlendServiceFunc.judgeRunStatus(strServiceCode, logFlag, "pbs_small_out_status")) {
            return recordMsg.set("msg", "服务暂停计算");
        }

        //5.去向返回道
        if (list_demand_prodect.get(0).getStr("k_is_pbs_back_car").equals("1")) {
            //5.1.下达指令
            int OutTrackCmd = 8;
            Record recordShorts = ExportDirectBlendServiceFunc.getMemoryValuesFromVpart(vpartCode);
            Db.update(ExportDirectBlendServiceFunc.getWriteMemoryOfOtherSql(),
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

            msg = String.format("小库区出口移行机在位计算——去向返回道逻辑,生产线编码[%s],钢码号[%s],下达指令[%s]",
                    productionCode,
                    vpartCode,
                    OutTrackCmd);
            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, msg);
            recordMsg.set("msg", msg).set("error", true);
            return recordMsg;
        }

        //6.去向小库区出口转盘
        int OutTrackCmd = 7;
        Record recordShorts = ExportDirectBlendServiceFunc.getMemoryValuesFromVpart(vpartCode);
        Db.update(ExportDirectBlendServiceFunc.getWriteMemoryOfOtherSql(),
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

        msg = String.format("小库区出口移行机在位计算——去向返回道逻辑,生产线编码[%s],钢码号[%s],下达指令[%s]",
                productionCode,
                vpartCode,
                OutTrackCmd);
        ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, msg);
        recordMsg.set("msg", msg).set("error", true);
        return recordMsg;
    }

    /**
     * 小库区出口移行机车辆离开信号处理
     *
     * @param memoryRead
     * @return
     */
    private Record passDeal(Record memoryRead) {
        String msg = "";
        Record recordMsg = new Record().set("msg", "").set("error", true);
        ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, String.format("控制命令字[%s]开始计算", memoryRead.getStr("LOGIC_MEMORY_VALUE")));

        //1.根据内存地址组的值，获取白车身钢码号
        String vpartCode = ExportDirectBlendServiceFunc.getVpartFromMemoryValues(memoryRead);
        if (vpartCode == null || vpartCode.length() == 0 || vpartCode.equals("null")) {
            msg = String.format("根据内存地址组的值[%s],获取白车身钢码号错误", memoryRead.toJson());
            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
            recordMsg.set("msg", msg).set("error", false);
            return recordMsg;
        }

        //2.获取需求产品表信息
        List<Record> list_demand_prodect = Db.find(ExportDirectBlendServiceFunc.getDemandProductByVpartSql(), vpartCode);
        if (list_demand_prodect.size() != 1) {
            msg = String.format("根据白车身钢码号[%s],获取需求产品信息异常", vpartCode);
            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
            recordMsg.set("msg", msg).set("error", false);
            return recordMsg;
        }

        //2.1.获取生产编码
        String productionCode = list_demand_prodect.get(0).getStr("PRODUCTION_CODE");

        Db.tx(new IAtom() {
            @Override
            public boolean run() throws SQLException {
                String msg = "";
                final String dest_code;

                //3.处理车辆离开到达目标区域
                switch (memoryRead.getStr("LOGIC_MEMORY_VALUE")) {
                    //移行机把车放至返回道
                    case "10":
                        dest_code = "pbs08_06";
                        //取消返回车标记
                        Db.update(ExportDirectBlendServiceFunc.getUpdatePbsBackCar(), 0, productionCode);
                        break;
                    //移行机把车放至小库区出口等待工位
                    case "9":
                        dest_code = "pbs10";
                        break;
                    default:
                        msg = String.format("控制命令字[%s]错误，命令字没有定义,内存地址组[%s]", memoryRead.getStr("LOGIC_MEMORY_VALUE"), groupMemoryCode);
                        ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
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
                    ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, msg);
                } else {
                    msg = String.format("把车[%s],般道区域[%s]失败，原因[%s]", productionCode, dest_code, msg);
                    ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
                    recordMsg.set("msg", msg).set("error", false);
                    return false;
                }

                //5.清空指令
                Record recordShorts = ExportDirectBlendServiceFunc.getMemoryValuesFromVpart("");
                Db.update(ExportDirectBlendServiceFunc.getWriteMemoryOfOtherSql(),
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
                ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, msg);
                recordMsg.set("msg", msg).set("error", true);
                return true;
            }
        });

        return recordMsg;

    }
}

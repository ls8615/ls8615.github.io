package cvmes.pbs.pbsOutService;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.IAtom;
import com.jfinal.plugin.activerecord.ICallback;
import com.jfinal.plugin.activerecord.Record;
import cvmes.common.LogLevel;
import oracle.jdbc.OracleTypes;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class BitAreaService {
    //内存地址组
    final String groupMemoryCode = "big.area.out.shifting.machine";
    //日志标记
    String logFlag = "BitArea";

    String strServiceCode;

    public BitAreaService(String strServiceCode) {
        this.strServiceCode = strServiceCode;
    }

    public String runBll(Record rec_service) throws Exception {
        return DealHeader(rec_service);
    }

    /**
     * 大库区出口移行机业务入口
     *
     * @return
     */
    private String DealHeader(Record rec_service) {

        String msg = "";

        //1.获取写表指令
        List<Record> list_memory_write = Db.find("SELECT wm.* FROM T_PBS_WRITE_MEMORY wm where wm.GROUP_MEMORY_CODE=? AND wm.DEAL_STATUS=0", groupMemoryCode);

        //1.1.存在未处理指令
        if (list_memory_write.size() != 0) {
            msg = String.format("存在未处理指令，请检查设备通讯是否正常。内存地址组[%S]", groupMemoryCode);
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

        //1.2.获取PBS内存地址读表值——控制命令字值
        String memoryValue = list_memory_read.get(0).getStr("LOGIC_MEMORY_VALUE");

        //3.根据控制命令字处理
        switch (memoryValue) {
            case "1":
            case "2":
            case "3":
            case "4":
            case "5":
            case "6":
            case "7":
            case "8":
            case "9":
            case "11":
            case "12":
            case "13":
                ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, String.format("地址组[%s],控制命令字[%s]下位操作。", groupMemoryCode, memoryValue));
                return "";

            case "0":
                return pickCar(rec_service, list_memory_read.get(0)).getStr("msg");
            case "10":
                return reignCalculatingDirection(list_memory_read.get(0)).getStr("msg");

            case "14":
            case "15":
            case "16":
                return passDeal(list_memory_read.get(0)).getStr("msg");
            default:
                msg = String.format("控制命令字[%s]错误，命令字没有定义,内存地址组[%s]", memoryValue, groupMemoryCode);
                ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
                return msg;

        }
    }

    /**
     * 大库区出口移行机接车逻辑入口
     *
     * @param memoryRead
     * @return
     */
    private Record pickCar(Record rec_service, Record memoryRead) {
        String msg = "";
        Record recordMsg = new Record().set("msg", "").set("error", true);
        ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, String.format("控制命令字[%s]开始计算", memoryRead.getStr("LOGIC_MEMORY_VALUE")));

        //0.判断服务状态
        if (!ExportDirectBlendServiceFunc.judgeRunStatus(strServiceCode, logFlag, "pbs_gareaout_service_status")) {
            return recordMsg.set("msg", "服务暂停计算");
        }

        //1.获取大库区出口移行机出口运行模式参数
        List<Record> list_para = Db.find("SELECT t.para_code,t.para_name,t.para_value FROM t_sys_para t WHERE t.para_code='pbs_bigarea_out_type'");

        //1.1.获取大库区出口移行机出口运行模式参数异常
        if (list_para.size() == 0) {
            msg = String.format("获取大库区出口运行参数异常，参数编码[%s]");
            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
            recordMsg.set("msg", msg);
            return recordMsg;
        }

        //1.2.获取PBS大库区出口移行机搬车参数（0：均衡出车；1：只接总装1线车；2：直接总装2线车）
        String pickType = list_para.get(0).getStr("PARA_VALUE");
        switch (pickType) {
            case "0":
                return pickCarOfEqu(rec_service);
            case "1":
                return pickCarOfLineCode("zz0101");
            case "2":
                return pickCarOfLineCode("zz0102");
            default:
                msg = String.format("PBS大库区出口移行机搬车参数未定义（0：均衡出车；1：只接总装1线车；2：直接总装2线车），当前值[%s]", pickType);
                ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
        }
    }

    /**
     * 大库区出口移行机接车逻辑入口——均衡接车逻辑
     *
     * @return
     */
    private Record pickCarOfEqu(Record rec_service) {
        String msg = "";
        Record recordMsg = new Record().set("msg", "").set("error", true);
        ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, String.format("大库区出口移行机均衡接车逻辑开始计算"));
        Record recMsg;
        //1.获取参数，判断上次搬出生产线
        String pre_move_out_line_code = rec_service.getStr("SERVICE_PARA1_VALUE");
        switch (pre_move_out_line_code) {
            case "zz0101":
                recMsg = pickCarOfLineCode("zz0102");
                if (!recMsg.getStr("msg").equals("")) {
                    return recMsg;
                }

                return pickCarOfLineCode("zz0101");
            case "zz0102":
                recMsg = pickCarOfLineCode("zz0101");
                if (!recMsg.getStr("msg").equals("")) {
                    return recMsg;
                }

                return pickCarOfLineCode("zz0102");
            default:
                msg = String.format("大库区出口移行机均衡接车逻辑开始计算——获取最后搬出车辆总装生产线编码错误(总装1线[zz0101],总装2线[zz0102]),当前值[%s]。",
                        pre_move_out_line_code);
                ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
        }
    }

    /**
     * 大库区出口移行机接车逻辑——车接车处理（根据总装生产线）
     *
     * @param line_code 总装生产线编码
     * @return 对象msg为空表示无计算结果，msg非空表示计算结果或计算错误信息， error未true表示计算无错误，error未false表示计算有错误
     */
    private Record pickCarOfLineCode(String line_code) {
        String msg = "";
        Record recordMsg = new Record().set("msg", "").set("error", true);
        ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, String.format("大库区出口移行机接车逻辑开始计算，总装生产线编码[%s]", line_code));

        /*
         * 0.判断是否有总装生产线车正从小库区往大库区出口移行机放车
         *
         *//*
        if (ExportDirectBlendServiceFunc.isRunningCarSmallAreaToBitArea(line_code)) {
            msg = String.format("大库区出口移行机接车逻辑，总装生产线[%s]存在从小库区往大库区出车，大库区出口移行机不接该生产线车。", line_code);
            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Warning, msg);
            recordMsg.set("msg", "");
            return recordMsg;
        }*/


        //1.获取总装生产线出口升降机是否有车占位（总装1线：SR_033，总装2线SR_034）
        if (ExportDirectBlendServiceFunc.isReginCarOfOutStation(strServiceCode, logFlag, line_code)) {
            msg = String.format("大库区出口移行机接车逻辑，总装生产线[%s]出口升降机有车占位", line_code);
            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Warning, msg);
            recordMsg.set("msg", "");
            return recordMsg;
        }

        /**
         * 2.手工计划执行，先执行判断是否存在小库区出口跟随计划，再执行大库区出口手工计划
         *
         */
        Record retMsg = execManualPlanOfLineCode(line_code);
        if (!retMsg.getStr("msg").equals("") || retMsg.getBoolean("IsBlack")) {
            return retMsg;
        }

        /**
         * 3.检查PBS大库区车道配置是否有吴
         * 配置位总装1线车道存在总装2线车，表示车道配置有吴，反之亦然。
         * 该情况不做自动接车计算，需要人工干预手工计划放车
         */
        //3.1.

        /**
         * 4.接小库区出口等待工位车逻辑：
         * A：判断小库区出口等待工位控制命令字是否为3
         * B：判断小库区出口等待工位在位等待车辆总装生产线与接车生产线一致
         * C：接小库区出口等待工位车辆
         */
        //4.1.获取小库区出口等待工位内存地址值
        List<Record> list_memory_read = Db.find("SELECT rm.* FROM T_PBS_READ_MEMORY rm where rm.GROUP_MEMORY_CODE=?", "small.area.out.wait.station");
        if (list_memory_read.size() != 1) {
            msg = String.format("读取PBS内存地址组【%s】配置错误，请检查配置", "small.area.out.wait.station");
            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
            recordMsg.set("msg", msg);
            return recordMsg;
        }

        //4.1.1.获取内存地址值控制命令字
        String memoryValue = list_memory_read.get(0).getStr("LOGIC_MEMORY_VALUE");

        //4.2.小库区出口等待工位等待接车
        if (memoryValue.equals("3")) {
            //4.2.1.获取内存地址组钢码号
            String vpart = ExportDirectBlendServiceFunc.getVpartFromMemoryValues(list_memory_read.get(0));
            if (vpart == null || vpart.length() == 0) {
                msg = String.format("接小库区出口等待工位车逻辑，获取小库区出口等待工位内存地址组钢码号失败，总装生产线编码[%s],内存地址组数据[%s]", line_code, list_memory_read.get(0).toJson());
                ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
            }

            //4.2.获取需求产品信息
            List<Record> list_demand_product = Db.find(ExportDirectBlendServiceFunc.getDemandProductByVpartSql(), vpart);

            //4.2.1.获取需求产品信息异常
            if (list_demand_product.size() != 1) {
                msg = String.format("获取需求产品信息异常，白车身钢码号[%s]不存在需求产品信息或存在多个需求产品信息，获取到需求产品个数[%s]", vpart, list_demand_product.size());
                ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
            }

            //4.3.接小库区出口等待工位
            if (list_demand_product.get(0).getStr("LINE_CODE").equals(line_code)) {
                //4.3.1.下达接车指令
                String vpartCode = "";
                int OutTrackCmd = BitAreaFun.getBitAreaPickCarZoneCodeOfInstructionsRela().getInt("pbs11");
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

                msg = String.format("执行接小库区出口等待工位车逻辑，生产编码[%s],生产线编码[%s],下达指令[%s]",
                        list_demand_product.get(0).getStr("PRODUCTION_CODE"),
                        line_code,
                        OutTrackCmd);
                ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, msg);
                recordMsg.set("msg", msg).set("error", true);

                return recordMsg;
            }

        }

        /*
         * 0.判断是否有总装生产线车正从小库区往大库区出口移行机放车
         *
         */
        if (ExportDirectBlendServiceFunc.isRunningCarSmallAreaToBitArea(line_code)) {
            msg = String.format("大库区出口移行机接车逻辑，总装生产线[%s]存在从小库区往大库区出车，大库区出口移行机不接该生产线车。", line_code);
            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Warning, msg);
            recordMsg.set("msg", "");
            return recordMsg;
        }


        /**
         * 5.接计划顺序号最小车所在区域逻辑（大库区可搬出区域头车、小库区可搬出区域和小库区返回道）
         * A：获取可搬出去计划顺序最小车
         * B：计划顺序最小车区域不是大库区，等待小库区处理指令
         * C：可搬出区域最小车在大库区，接大库区车（如果是禁止车，则需要标记返回车）
         */
        //5.1.获取大库区总装生产线可搬出区域头车和小库区可搬出区域计划顺序号最小车所在区域信息，按计划顺序从小到大排序
        List<Record> list_plan_mis = Db.find(BitAreaFun.getBitAreaAndSmalAreaMinPlanSeqZoneInfoSql(), line_code);
        if (list_plan_mis.size() == 0) {
            msg = String.format("总装生产线无可搬出车辆,生产线编码");
            recordMsg.set("msg", msg);
            return recordMsg;
        }

        //5.1.1.可搬出计划顺序号最小车在小库区，等待
        if (!list_plan_mis.get(0).getStr("ZONE_CODE").startsWith("pbs06_")) {
            msg = String.format("可搬出计划顺序号最小车不在大库区可搬出区域或不在小库区等待工位区域，等待小库区出口移行机接车。总装生产线编码[%s]", line_code);
            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Warning, msg);
            recordMsg.set("msg", "");
            return recordMsg;
        }

        //5.2.接大库区车
        for (Record sub : list_plan_mis) {
            //5.2.1.如果接车区域头车为禁止车，标记接车区域头车返回车标记
            if (sub.getStr("K_IS_PBS_DISABLE_CAR").equals("1")) {
                //5.2.1.1.获取小库区是否有空位
                List<Record> list_small_is_empty = Db.find(BitAreaFun.getSmallIsEmpty());
                if (list_small_is_empty.size() == 1) {
                    msg = String.format("小库区无空位，无法将车计划顺序好最小车的禁止车往小库区放车，总装生产线[%s],生产编码[%s]", line_code, sub.getStr("PRODUCTION_CODE"));
                    ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Warning, msg);
                    recordMsg.set("msg", "");
                    return recordMsg;
                }

                Db.tx(new IAtom() {
                    @Override
                    public boolean run() throws SQLException {
                        String msg = "";

                        //5.2.1.1.1.标记接车区域头车返回车标记
                        Db.update(ExportDirectBlendServiceFunc.getUpdatePbsBackCar(), 1, sub.getStr("PRODUCTION_CODE"));
                        msg = String.format("标记大库区可搬出区域头车为总装禁止车，标记头车返回车标记。生产编码[%s],总装生产线编码[%s]",
                                sub.getStr("PRODUCTION_CODE"), line_code);
                        ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, msg);

                        //5.2.2.下达指令
                        String vpartCode = "";
                        int OutTrackCmd = BitAreaFun.getBitAreaPickCarZoneCodeOfInstructionsRela().getInt(sub.getStr("ZONE_CODE"));
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

                        msg = String.format("执行接大库区可搬出区域头车逻辑，生产编码[%s],生产线编码[%s],下达指令[%s]",
                                sub.getStr("PRODUCTION_CODE"),
                                line_code,
                                OutTrackCmd);
                        ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, msg);
                        recordMsg.set("msg", msg).set("error", true);
                        return true;


                    }
                });


                return recordMsg;
            }

            //5.2.2.下达指令
            String vpartCode = "";
            int OutTrackCmd = BitAreaFun.getBitAreaPickCarZoneCodeOfInstructionsRela().getInt(sub.getStr("ZONE_CODE"));
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

            msg = String.format("执行接大库区可搬出区域头车逻辑，生产编码[%s],生产线编码[%s],下达指令[%s]",
                    sub.getStr("PRODUCTION_CODE"),
                    line_code,
                    OutTrackCmd);
            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, msg);
            recordMsg.set("msg", msg).set("error", true);
            return recordMsg;

        }

        return recordMsg;
    }

    /**
     * 大库区出口移行机接车逻辑——手工计划处理逻辑（根据总装生产线）
     * A：先判断是否存在小库区出口跟随计划，再判断是否存在大库区搬出手工计划
     *
     * @param line_code
     * @return
     */
    private Record execManualPlanOfLineCode(String line_code) {
        String msg = "";
        Record recordMsg = new Record().set("msg", "").set("IsBlack", false);
        ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, String.format("大库区出口移行机接车逻辑——手工计划处理逻辑开始计算，总装生产线编码[%s]", line_code));

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

        //1.1.无法获取到总装生产线最后出车生产编码（PS：不返回计算结果）
        if (last_passed_trim_production_code == null) {
            msg = String.format("获取总装生产线[%s]最后出车生产编码错误", line_code);
            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Warning, msg);
            recordMsg.set("msg", "");

            return recordMsg;
        }

        //2.获取小库区出口手工计划
        List<Record> list_manual_plan_small = Db.find("SELECT * FROM t_pbs_out_mplan_small t WHERE t.follow_production_code=? AND t.plan_status=0", last_passed_trim_production_code);

        //2.1.存在小库区出口手工跟随计划
        if (list_manual_plan_small.size() != 0) {
            msg = String.format("大库区出口移行机接车逻辑——手工计划处理逻辑，小库区存在最后车车跟随计划,等待小库区执行手工计划。最后出车生产编码[%s],生产线编码[%s],小库区手工计划生产编码[%s]",
                    last_passed_trim_production_code, line_code, list_manual_plan_small.get(0).getStr("PRODUCTION_CODE"));
            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Warning, msg);
            return recordMsg.set("msg", "").set("IsBlack", true);
        }

        //3.获取大库区出口手工计划
        List<Record> list_mamual_plan_bit = Db.find(BitAreaFun.getManualPlanOfBit(), line_code);
        if (list_mamual_plan_bit.size() == 0) {
            msg = String.format("大库区出口移行机接车逻辑——手工计划处理逻辑，大库区出口不存在未执行手工计划。最后出车生产编码[%s],生产线编码[%s]",
                    last_passed_trim_production_code, line_code);
            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Warning, msg);
            return recordMsg.set("msg", "");
        }

        /**
         * 4.执行手工计划（事务操作）：
         * A：如果手工计划车未禁止车，需要计算警告（未考虑清楚如何实现）
         * B：手工计划车为去小库区，则把手工计划车标记位pbs返回车，并标记位pbs禁止车
         * C：下达接车指令
         * D：标记手工计划处理完成
         */
        Db.tx(new IAtom() {
            @Override
            public boolean run() throws SQLException {
                String msg = "";
                //4.1.判断手工计划车辆去向，如果去向位总装，取消返回车标记；如果去向小库区，标记pbs返回车
                switch (list_mamual_plan_bit.get(0).getStr("MOVE_DIRECTION")) {
                    case "0":
                        break;
                    case "1":
                        break;
                    default:
                        msg = String.format("大库区出口手工计划去向未定义（0：总装，1：小库区）,当前手工计划ID[%s]，手工计划去向[%s]，总装生产线编码[%s]。",
                                list_mamual_plan_bit.get(0).getStr("ID"), list_mamual_plan_bit.get(0).getStr("MOVE_DIRECTION"), line_code);
                        ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
                        recordMsg.set("msg", msg);
                        return false;
                }

                //4.2.下达接车指令
                String vpartCode = "";
                int OutTrackCmd = BitAreaFun.getBitAreaPickCarZoneCodeOfInstructionsRela().getInt(list_mamual_plan_bit.get(0).getStr("ZONE_CODE"));
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

                msg = String.format("执行大库区出口手工计划逻辑，计划ID[%s],生产编码[%s],生产线编码[%s],下达指令[%s]",
                        list_mamual_plan_bit.get(0).getStr("ID"),
                        list_mamual_plan_bit.get(0).getStr("PRODUCTION_CODE"),
                        line_code,
                        OutTrackCmd);
                ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, msg);
                recordMsg.set("msg", msg).set("IsBlack", true);

                return true;
            }
        });

        return recordMsg;
    }

    /**
     * 大库区出口移行机在位计算逻辑
     *
     * @param memoryRead
     * @return
     */
    private Record reignCalculatingDirection(Record memoryRead) {
        //计算结果信息
        String msg, cmdMsg = "";
        Record recordMsg = new Record().set("msg", "").set("error", true);
        ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, String.format("大库区出口移行机在位计算逻辑,控制命令字[%s]开始计算", memoryRead.getStr("LOGIC_MEMORY_VALUE")));

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
                proc.setString("PARA_DEST_ZONE", "pbs12");
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
            msg = String.format("般车成功，把车[%s],般道区域[%s]", productionCode, "pbs12");
            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, msg);
        } else {
            msg = String.format("把车[%s],般道区域[%s]失败，原因[%s]", productionCode, "pbs12", msg);
            recordMsg.set("msg", msg).set("error", false);
            return recordMsg;
        }

        //3.复位小库区出口等待工位
        //3.1.获取小库区出口等待工位内存地址组值
        List<Record> list_memory_read = Db.find("SELECT rm.* FROM T_PBS_READ_MEMORY rm where rm.GROUP_MEMORY_CODE=?", "small.area.out.wait.station");
        if (list_memory_read.size() != 1) {
            msg = String.format("读取PBS内存地址组【%s】配置错误，请检查配置", "small.area.out.wait.station");
            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
            recordMsg.set("msg", msg);
            return recordMsg;
        }

        //3.1.获取PBS内存地址读表值——控制命令字值
        String memoryValue = list_memory_read.get(0).getStr("LOGIC_MEMORY_VALUE");

        //3.2.判断在位车辆是否来自小库区出口等待工位
        if (memoryValue.equals("3")) {
            //3.2.1.获取小库区出口等待工位内存地址组钢码号
            String vpartCodeSmallWaitStation = ExportDirectBlendServiceFunc.getVpartFromMemoryValues(list_memory_read.get(0));
            if (vpartCodeSmallWaitStation == null || vpartCodeSmallWaitStation.length() == 0 || vpartCodeSmallWaitStation.equals("null")) {
                msg = String.format("根据内存地址组的值[%s],获取白车身钢码号错误", list_memory_read.get(0).toJson());
                ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
                recordMsg.set("msg", msg).set("error", false);
                return recordMsg;
            }

            //3.2.2.在位车辆来自小库区出口等待工位，复位小库区出口等待工位
            if (vpartCodeSmallWaitStation.equals(vpartCode)) {
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
                        "small.area.out.wait.station"
                );

                msg = String.format("小库区出口移行机在位计算——复位小库区出口转盘,生产线编码[%s],钢码号[%s],下达指令[%s]",
                        productionCode,
                        vpartCode,
                        OutTrackCmd);
                ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, msg);
            }
        }

        //4.判断服务状态
        if (!ExportDirectBlendServiceFunc.judgeRunStatus(strServiceCode, logFlag, "pbs_gareaout_service_status")) {
            return recordMsg.set("msg", "服务暂停计算");
        }

        //5.去向小库区
        if (list_demand_prodect.get(0).getStr("k_is_pbs_back_car").equals("1")) {
            //5.1.下达指令
            int OutTrackCmd = 13;
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

            msg = String.format("大库区出口移行机在位计算——去向小库区出口等待工位逻辑,生产线编码[%s],钢码号[%s],下达指令[%s]",
                    productionCode,
                    vpartCode,
                    OutTrackCmd);
            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, msg);
            recordMsg.set("msg", msg).set("error", true);
            return recordMsg;
        }

        //5.去向总装生产线
        //5.1.计算去向指令
        int OutTrackCmd;
        switch (list_demand_prodect.get(0).getStr("line_code")) {
            case "zz0101":
                OutTrackCmd = 11;
                break;
            case "zz0102":
                OutTrackCmd = 12;
                break;
            default:
                msg = String.format("无法计算车辆去向，生产编码[%s],钢码号[%s]",
                        list_demand_prodect.get(0).getStr("PRODUCTION_CODE"),
                        list_demand_prodect.get(0).getStr("K_STAMP_ID"));
                ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
        }

        //5.3.下达指令
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

        msg = String.format("大库区出口移行机在位计算——去向返回道逻辑,生产线编码[%s],钢码号[%s],下达指令[%s]",
                productionCode,
                vpartCode,
                OutTrackCmd);
        ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, msg);
        recordMsg.set("msg", msg).set("error", true);
        return recordMsg;
    }

    /**
     * 大库区出口移行机车辆离开计算
     *
     * @param memoryRead
     * @return
     */
    private Record passDeal(Record memoryRead) {
        //计算结果信息
        String msg, cmdMsg = "";
        Record recordMsg = new Record().set("msg", "").set("error", true);
        ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, String.format("大库区出口移行机车辆离开计算逻辑,控制命令字[%s]开始计算", memoryRead.getStr("LOGIC_MEMORY_VALUE")));

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


        //3.去向目标区域处理
        String zone_code, lasd_line_code;
        switch (memoryRead.getStr("LOGIC_MEMORY_VALUE")) {
            case "14":
                zone_code = "pbs13";
                lasd_line_code = "zz0101";
                break;
            case "15":
                zone_code = "pbs14";
                lasd_line_code = "zz0102";
                break;
            case "16":
                zone_code = "pbs11";
                lasd_line_code = "";
                break;
            default:
                msg = String.format("控制命令字未定义，内存地址组[%s],控制命令字[%s]", groupMemoryCode, memoryRead.getStr("LOGIC_MEMORY_VALUE"));
                ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
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
                proc.setString("PARA_DEST_ZONE", zone_code);
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
            msg = String.format("般车成功，把车[%s],般道区域[%s]", productionCode, zone_code);
            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, msg);
        } else {
            msg = String.format("把车[%s],般道区域[%s]失败，原因[%s]", productionCode, zone_code, msg);
            recordMsg.set("msg", msg).set("error", false);
            return recordMsg;
        }

        //5.清空指令
        Db.tx(new IAtom() {
            @Override
            public boolean run() throws SQLException {
                String msg = "";
                //5.1.更新最后出车生产线
                if (!(lasd_line_code == null || lasd_line_code.equals(""))) {
                    Db.update("update t_sys_service set service_para1_value=? where service_code=?", lasd_line_code, strServiceCode);
                }

                //5.2.下达指令
                int OutTrackCmd = 0;
                Record recordShorts = ExportDirectBlendServiceFunc.getMemoryValuesFromVpart("");
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

                msg = String.format("大库区出口移行机车辆离开处理逻辑,生产线编码[%s],钢码号[%s],下达指令[%s]",
                        productionCode,
                        vpartCode,
                        OutTrackCmd);
                ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Information, msg);
                recordMsg.set("msg", msg).set("error", true);
                return true;
            }
        });

        return recordMsg;
    }


}

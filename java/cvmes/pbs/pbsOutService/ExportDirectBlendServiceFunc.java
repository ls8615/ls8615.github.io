package cvmes.pbs.pbsOutService;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;
import cvmes.common.LogLevel;

import java.util.List;

/**
 * PBS大小库区出口混合服务（PBS出口分线服务）公共方法
 */
public class ExportDirectBlendServiceFunc {

    /**
     * 混合服务写log日志
     *
     * @param stServiceCode
     * @param logFlag
     * @param logLevel
     * @param msg
     */
    public static void Write(String stServiceCode, String logFlag, LogLevel logLevel, String msg) {
        cvmes.common.Log.Write(stServiceCode, logLevel, String.format("[%s]-%s", logFlag, msg));
    }

    /**
     * 根据生产线编码，获取出口升降机是否有车占位（总装1线：SR_033，总装2线SR_034）
     *
     * @param strServiceCode 服务编码
     * @param line_code      总装生产线
     * @return true：有车占位；false：无车占位
     */
    public static boolean isReginCarOfOutStation(String strServiceCode, String logFlag, String line_code) {

        String device_code;
        switch (line_code) {
            case "zz0101":
                device_code = "SR_033";
                break;
            case "zz0102":
                device_code = "SR_034";
                break;
            default:
                ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, String.format("生产线编码[%s],未定义", line_code));
                return false;
        }
        List<Record> list = Db.find("SELECT 1 FROM t_pbs_device_signal_status t WHERE t.device_signal_code =? AND t.device_status=0", device_code);
        if (list.size() == 1) {
            return false;
        }

        return true;
    }

    /**
     * 获取总装1线出口最后出车生产编码
     *
     * @return 返回值null表示找不道最后出车生产编码
     */
    public static String getLineOneFollowCarProdutionCode(String strServiceCode, String logFlag) {
        String msg = "";

        String productionCode = "";
        //1.总装1线出口升降机位置最后车辆
        String zoneCode = "pbs13";
        List<Record> list = Db.find(getCarOfMaxZoneProductPos(), zoneCode);
        if (list == null) {
            msg = String.format("获取1线出口升级机区域尾车失败，区域编码[%s]", zoneCode);
            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
            return null;
        }

        if (list.size() != 0) {
            return list.get(0).getStr("PRODUCTION_CODE");
        }

        //2.总装1线mis点区域位置最后出车
        zoneCode = "pbs15";
        list = Db.find(getCarOfMaxZoneProductPos(), zoneCode);
        if (list == null) {
            msg = String.format("获取1线MIS点区域尾车失败，区域编码[%s]", zoneCode);
            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
            return null;
        }

        if (list.size() != 0) {
            return list.get(0).getStr("PRODUCTION_CODE");
        }

        //3.总装1线缓存区域位置最后出车
        zoneCode = "pbs17";
        list = Db.find(getCarOfMaxZoneProductPos(), zoneCode);
        if (list == null) {
            msg = String.format("获取1线总装缓存区域尾车失败，区域编码[%s]", zoneCode);
            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
            return null;
        }

        if (list.size() != 0) {
            return list.get(0).getStr("PRODUCTION_CODE");
        }

        //4.总装1线MIS点实绩最后过点实绩过车
        list = Db.find("SELECT production_code FROM (SELECT * FROM T_ACTUAL_PASSED_RECORD T WHERE T.ACTUAL_POINT_CODE = 'zz_mis1' ORDER BY T.PASSED_TIME DESC) WHERE ROWNUM=1");
        if (list == null) {
            msg = String.format("获取1线MIS点实绩最后过车，区域编码[%s]", zoneCode);
            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
            return null;
        }

        if (list.size() != 0) {
            return list.get(0).getStr("PRODUCTION_CODE");
        }

        return null;
    }

    /**
     * 获取总装2线出口最后出车生产编码
     *
     * @return 返回值null表示找不道最后出车生产编码
     */
    public static String getLineTowFollowCarProdutionCode(String strServiceCode, String logFlag) {
        String msg = "";

        String productionCode = "";
        //1.总装2线出口升降机位置最后车辆
        String zoneCode = "pbs14";
        List<Record> list = Db.find(getCarOfMaxZoneProductPos(), zoneCode);
        if (list == null) {
            msg = String.format("获取1线出口升级机区域尾车失败，区域编码[%s]", zoneCode);
            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
            return null;
        }

        if (list.size() != 0) {
            return list.get(0).getStr("PRODUCTION_CODE");
        }

        //2.总装2线mis点区域位置最后出车
        zoneCode = "pbs16";
        list = Db.find(getCarOfMaxZoneProductPos(), zoneCode);
        if (list == null) {
            msg = String.format("获取1线出口升级机区域尾车失败，区域编码[%s]", zoneCode);
            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
            return null;
        }

        if (list.size() != 0) {
            return list.get(0).getStr("PRODUCTION_CODE");
        }

        //3.总装2线缓存区域位置最后出车
        zoneCode = "pbs18";
        list = Db.find(getCarOfMaxZoneProductPos(), zoneCode);
        if (list == null) {
            msg = String.format("获取1线出口升级机区域尾车失败，区域编码[%s]", zoneCode);
            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
            return null;
        }

        if (list.size() != 0) {
            return list.get(0).getStr("PRODUCTION_CODE");
        }

        //4.总装1线MIS点实绩最后过点实绩过车
        list = Db.find("SELECT production_code FROM (SELECT * FROM T_ACTUAL_PASSED_RECORD T WHERE T.ACTUAL_POINT_CODE = 'zz_mis2' ORDER BY T.PASSED_TIME DESC) WHERE ROWNUM=1");
        if (list == null) {
            msg = String.format("获取1线MIS点实绩最后过车，区域编码[%s]", zoneCode);
            ExportDirectBlendServiceFunc.Write(strServiceCode, logFlag, LogLevel.Error, msg);
            return null;
        }

        if (list.size() != 0) {
            return list.get(0).getStr("PRODUCTION_CODE");
        }

        return null;
    }

    /**
     * 获取区域最后实车，参数：区域编码
     *
     * @return
     */
    private static String getCarOfMaxZoneProductPos() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("SELECT * FROM t_model_zone t WHERE t.production_code IS NOT NULL AND t.zone_code=? ORDER BY t.product_pos DESC");

        return sql.toString();
    }

    /**
     * 获取插入写表指令语句——MIS点以外的其他点
     *
     * @return
     */
    public static String getWriteMemoryOfOtherSql() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("insert into t_pbs_write_memory(id, logic_memory_address, logic_memory_value, vpart_code, vpart_memory_address1, vpart_memory_address2, vpart_memory_address3, vpart_memory_address4, vpart_memory_address5, vpart_memory_address6, vpart_memory_address7, vpart_memory_address8, vpart_memory_address9, vpart_memory_address10, vpart_memory_address11, vpart_memory_address12, vpart_memory_address13, vpart_memory_value1, vpart_memory_value2, vpart_memory_value3, vpart_memory_value4, vpart_memory_value5, vpart_memory_value6, vpart_memory_value7, vpart_memory_value8, vpart_memory_value9, vpart_memory_value10, vpart_memory_value11, vpart_memory_value12, vpart_memory_value13, deal_user, deal_time, deal_status, fail_reason, device_code, group_memory_code, group_memory_name)");
        sql.append("select sys_guid(),logic_memory_address, ?, ?, vpart_memory_address1, vpart_memory_address2, vpart_memory_address3, vpart_memory_address4, vpart_memory_address5, vpart_memory_address6, vpart_memory_address7, vpart_memory_address8, vpart_memory_address9, vpart_memory_address10, vpart_memory_address11, vpart_memory_address12, vpart_memory_address13, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'system', sysdate, 0, '', device_code, group_memory_code, group_memory_name ");
        sql.append("from t_pbs_read_memory t where t.group_memory_code=?");

        return sql.toString();
    }

    /**
     * 根据重保号字符串，获取内存地址值（short值）
     *
     * @param vparCode
     * @return
     */
    public static Record getMemoryValuesFromVpart(String vparCode) {
        Record record = new Record();
        if (vparCode.length() == 0) {
            record.set("VPART_MEMORY_VALUE1", 0);
            record.set("VPART_MEMORY_VALUE2", 0);
            record.set("VPART_MEMORY_VALUE3", 0);
            record.set("VPART_MEMORY_VALUE4", 0);
            record.set("VPART_MEMORY_VALUE5", 0);
            record.set("VPART_MEMORY_VALUE6", 0);
            record.set("VPART_MEMORY_VALUE7", 0);
            record.set("VPART_MEMORY_VALUE8", 0);
            record.set("VPART_MEMORY_VALUE9", 0);
            record.set("VPART_MEMORY_VALUE10", 0);
            record.set("VPART_MEMORY_VALUE11", 0);
            record.set("VPART_MEMORY_VALUE12", 0);
            record.set("VPART_MEMORY_VALUE13", 0);
        }

        if (vparCode.length() != 0) {
            byte[] bytes = vparCode.getBytes();

            record.set("VPART_MEMORY_VALUE1", ToShort(bytes, 0));
            record.set("VPART_MEMORY_VALUE2", ToShort(bytes, 2));
            record.set("VPART_MEMORY_VALUE3", ToShort(bytes, 4));
            record.set("VPART_MEMORY_VALUE4", ToShort(bytes, 6));
            record.set("VPART_MEMORY_VALUE5", ToShort(bytes, 8));
            record.set("VPART_MEMORY_VALUE6", ToShort(bytes, 10));
            record.set("VPART_MEMORY_VALUE7", ToShort(bytes, 12));
            record.set("VPART_MEMORY_VALUE8", ToShort(bytes, 14));
            record.set("VPART_MEMORY_VALUE9", ToShort(bytes, 16));
            record.set("VPART_MEMORY_VALUE10", ToShort(bytes, 18));
            record.set("VPART_MEMORY_VALUE11", ToShort(bytes, 20));
            record.set("VPART_MEMORY_VALUE12", ToShort(bytes, 22));
            record.set("VPART_MEMORY_VALUE13", ToShort(bytes, 24));
        }

        return record;
    }

    /**
     * 将两个byte转成short
     *
     * @param bytes
     * @param idx
     * @return
     */
    private static final short ToShort(byte[] bytes, int idx) {
        if (idx + 1 > bytes.length - 1) {
            return (short) ((bytes[idx] << 8) | ((byte) 0 & 0xff));
        }
        return (short) ((bytes[idx] << 8) | (bytes[idx + 1] & 0xff));
    }

    /**
     * 根据内存地址值（short值），获取重保号字符串
     *
     * @param memoryRead 重保号内存地址Map
     * @return 重保号
     */
    public static String getVpartFromMemoryValues(Record memoryRead) {
        StringBuffer vparCode = new StringBuffer();
        vparCode.append(short2Byte(Short.parseShort(memoryRead.getStr("VPART_MEMORY_VALUE1"))));
        vparCode.append(short2Byte(Short.parseShort(memoryRead.getStr("VPART_MEMORY_VALUE2"))));
        vparCode.append(short2Byte(Short.parseShort(memoryRead.getStr("VPART_MEMORY_VALUE3"))));
        vparCode.append(short2Byte(Short.parseShort(memoryRead.getStr("VPART_MEMORY_VALUE4"))));
        vparCode.append(short2Byte(Short.parseShort(memoryRead.getStr("VPART_MEMORY_VALUE5"))));
        vparCode.append(short2Byte(Short.parseShort(memoryRead.getStr("VPART_MEMORY_VALUE6"))));
        vparCode.append(short2Byte(Short.parseShort(memoryRead.getStr("VPART_MEMORY_VALUE7"))));
        vparCode.append(short2Byte(Short.parseShort(memoryRead.getStr("VPART_MEMORY_VALUE8"))));
        vparCode.append(short2Byte(Short.parseShort(memoryRead.getStr("VPART_MEMORY_VALUE9"))));
        vparCode.append(short2Byte(Short.parseShort(memoryRead.getStr("VPART_MEMORY_VALUE10"))));
        vparCode.append(short2Byte(Short.parseShort(memoryRead.getStr("VPART_MEMORY_VALUE11"))));
        vparCode.append(short2Byte(Short.parseShort(memoryRead.getStr("VPART_MEMORY_VALUE12"))));
        vparCode.append(short2Byte(Short.parseShort(memoryRead.getStr("VPART_MEMORY_VALUE13"))));

        return vparCode.toString().trim();
    }

    /**
     * 将一个short值转换2个成As码表字符
     *
     * @param s short值
     * @return 字符串
     */
    public static String short2Byte(short s) {
        byte[] byteAsci = new byte[2];
        byteAsci[0] = (byte) (s >> 8);
        byteAsci[1] = (byte) (s & 0x00FF);


        return String.valueOf(new char[]{(char) byteAsci[0], (char) byteAsci[1]});
    }

    /**
     * 判断是否存在车辆正在从大库区往小库区放车：（不用命令字判断）
     * 获取大库区车口接车指令所在车道头车是否有返回车标记
     * 获取大库区出口移行机所在区域的车是否有返回车标记
     * 获取小库区出口等待工位所在区域的车是否有返回车标记
     * 获取小库区出口转盘所在区域的车是否有返回车标记
     *
     * @return true:存在；false：不存在
     */
    public static boolean isRunningCarBitAreaToSmallArea() {
        //1.获取大库区出口移行机接车指令所在车道头车是否有返回车标记
        //2.获取大库区出口移行机所在区域车辆是否有返回车标记
        //3.获取小库区出口等待工位所在区域车辆是否有返回车标记
        //4.获取小库区出口转盘所在区域车辆是否有返回车标记
        List<Record> list = Db.find(getBitAreaShiftingMachinPickCmd());
        if (list.size() != 0) {
            return true;
        }

        return false;
    }

    /**
     * 判断是否存总装生产线车辆正在从小库区往大库区出口移行机放车
     *
     * @param line_code
     * @return true:存在；false：不存在
     */
    public static boolean isRunningCarSmallAreaToBitArea(String line_code) {
        List<Record> list = Db.find(getSmalAreaShiftingMachinPickCmd(), line_code);
        if (list.size() != 0) {
            return true;
        }

        return false;
    }

    /**
     * 获取大库区车口接车指令所在车道头车是否为返回车（去向小库区出口移行机）
     * 获取大库区出口移行机所在区域的车是否为返回车（去向小库区出口移行机）
     * 获取小库区出口等待工位所在区域的车是否为返回车（去向小库区出口移行机）
     * 获取小库区出口转盘所在区域的车是否为返回车（去向小库区出口移行机）
     *
     * @return
     */
    private static String getBitAreaShiftingMachinPickCmd() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("SELECT T.PRODUCTION_CODE");
        sql.append("  FROM T_MODEL_ZONE T");
        sql.append("  LEFT JOIN T_PLAN_DEMAND_PRODUCT P");
        sql.append("    ON P.PRODUCTION_CODE = T.PRODUCTION_CODE");
        sql.append(" WHERE T.PRODUCT_POS = 1");
        sql.append("   AND P.K_IS_PBS_BACK_CAR = 1 AND (T.ZONE_CODE = (");
        sql.append("  SELECT CASE");
        sql.append("     WHEN T.LOGIC_MEMORY_VALUE = 1 THEN");
        sql.append("      'pbs06_01'");
        sql.append("     WHEN T.LOGIC_MEMORY_VALUE = 2 THEN");
        sql.append("      'pbs06_02'");
        sql.append("     WHEN T.LOGIC_MEMORY_VALUE = 3 THEN");
        sql.append("      'pbs06_03'");
        sql.append("     WHEN T.LOGIC_MEMORY_VALUE = 4 THEN");
        sql.append("      'pbs06_04'");
        sql.append("     WHEN T.LOGIC_MEMORY_VALUE = 5 THEN");
        sql.append("      'pbs06_05'");
        sql.append("     WHEN T.LOGIC_MEMORY_VALUE = 6 THEN");
        sql.append("      'pbs06_06'");
        sql.append("     WHEN T.LOGIC_MEMORY_VALUE = 7 THEN");
        sql.append("      'pbs06_07'");
        sql.append("     WHEN T.LOGIC_MEMORY_VALUE = 8 THEN");
        sql.append("      'pbs06_08'");
        sql.append("   END ZONE_CODE");
        sql.append("    FROM T_PBS_READ_MEMORY T");
        sql.append("   WHERE T.GROUP_MEMORY_CODE = 'big.area.out.shifting.machine'");
        sql.append("     AND T.LOGIC_MEMORY_VALUE IN (1, 2, 3, 4, 5, 6, 7, 8)) OR");
        sql.append("   T.ZONE_CODE IN ('pbs10', 'pbs11','pbs12'))");

        return sql.toString();
    }

    /**
     * 获取小库区出口移行机接车指令所在头车是否为非返回车（去向大库区出口移行机）
     * 获取小库区出口移行机所在区域的车是否为非返回车（去向大库区出口移行机）
     * 获取小库区出口等待工位所在区域的车是否为非返回车（去向大库区出口移行机）
     * 获取小库区出口转盘所在区域的车是否为非返回车（去向大库区出口移行机）
     *
     * @return
     */
    private static String getSmalAreaShiftingMachinPickCmd() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("SELECT T.PRODUCTION_CODE, VP.LINE_CODE");
        sql.append("  FROM T_MODEL_ZONE T");
        sql.append("  LEFT JOIN T_PLAN_DEMAND_PRODUCT P");
        sql.append("    ON P.PRODUCTION_CODE = T.PRODUCTION_CODE");
        sql.append("  LEFT JOIN (SELECT D.PRODUCTION_CODE, D.LINE_CODE");
        sql.append("               FROM T_PLAN_SCHEDULING M");
        sql.append("               LEFT JOIN T_PLAN_SCHEDULING_D D");
        sql.append("                 ON D.SCHEDULING_PLAN_CODE = M.SCHEDULING_PLAN_CODE");
        sql.append("              WHERE D.LINE_CODE IN ('zz0101', 'zz0102')) VP");
        sql.append("    ON T.PRODUCTION_CODE = VP.PRODUCTION_CODE");
        sql.append(" WHERE T.PRODUCT_POS = 1");
        sql.append("   AND P.K_IS_PBS_BACK_CAR = 0");
        sql.append("   AND VP.LINE_CODE = ?");
        sql.append("   AND (T.ZONE_CODE = (SELECT CASE");
        sql.append("                                 WHEN T.LOGIC_MEMORY_VALUE = 1 THEN");
        sql.append("                                  'pbs08_01'");
        sql.append("                                 WHEN T.LOGIC_MEMORY_VALUE = 2 THEN");
        sql.append("                                  'pbs08_02'");
        sql.append("                                 WHEN T.LOGIC_MEMORY_VALUE = 3 THEN");
        sql.append("                                  'pbs08_03'");
        sql.append("                                 WHEN T.LOGIC_MEMORY_VALUE = 4 THEN");
        sql.append("                                  'pbs08_04'");
        sql.append("                                 WHEN T.LOGIC_MEMORY_VALUE = 11 THEN");
        sql.append("                                  'pbs08_05'");
        sql.append("                               END ZONE_CODE");
        sql.append("                         FROM T_PBS_READ_MEMORY T");
        sql.append("                        WHERE T.GROUP_MEMORY_CODE = 'small.area.out.shifting.machine'");
        sql.append("                          AND T.LOGIC_MEMORY_VALUE IN (1, 2, 3, 4, 11)) OR T.ZONE_CODE IN ('pbs10', 'pbs11', 'pbs09'))");


        return sql.toString();
    }

    /**
     * 判断是否允许小库区出口移行机接车放行大库区出口移行机方向：
     * A:判断是否存在车辆正在从小库区往大库区出口移行机放车：
     * A1:获取小库区出口移行机所在区域车辆是否有返回车标记
     * A2:获取小库区出口等待工位所在区域车辆是否有返回车标记
     * A3:获取小库区出口转盘所在区域车辆是否有返回车标记
     * <p>
     * B:判断是否有车正在从大库区往小库区放车
     *
     * @return true：允许；false：不允许
     */
    public static boolean isAllowPickCarSmallAreaToBitArea() {
        //A:
        List<Record> list = Db.find(getSmallAreaToBitAreaCarSql());
        if (list.size() != 0) {
            return false;
        }

        //B:
        if (isRunningCarBitAreaToSmallArea()) {
            return false;
        }

        return true;
    }

    /**
     * 获取小库区出口移行机、小库区出口转盘和出口等待工位所在区域车辆非返回车清单
     *
     * @return
     */
    private static String getSmallAreaToBitAreaCarSql() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("SELECT T.ZONE_CODE, T.PRODUCT_POS, T.PRODUCTION_CODE, P.K_IS_PBS_BACK_CAR");
        sql.append("  FROM T_MODEL_ZONE T");
        sql.append("  LEFT JOIN T_PLAN_DEMAND_PRODUCT P");
        sql.append("    ON P.PRODUCTION_CODE = T.PRODUCTION_CODE");
        sql.append(" WHERE T.ZONE_CODE IN ('pbs10', 'pbs11', 'pbs09')");
        sql.append("   AND T.PRODUCTION_CODE IS NOT NULL");
        sql.append("   AND P.K_IS_PBS_BACK_CAR = 0");

        return sql.toString();
    }


    /**
     * 标记pbs小库区返回车标记，参数：标记值|生产编码
     *
     * @return
     */
    public static String getUpdatePbsBackCar() {
        StringBuffer sql = new StringBuffer(128);
        sql.append("UPDATE t_plan_demand_product t SET t.k_is_pbs_back_car=? WHERE t.production_code=?");

        return sql.toString();
    }

    /**
     * 根据生产编码获取所在区域内所有实车，参数：生产编码
     *
     * @return
     */
    public static String getZoneAllCarByProductionSql() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("SELECT Z.PRODUCTION_CODE,");
        sql.append("       Z.ZONE_CODE,");
        sql.append("       Z.PRODUCT_POS,");
        sql.append("       P.K_IS_PBS_DISABLE_CAR,");
        sql.append("       P.K_IS_PBS_BACK_CAR,");
        sql.append("       P.K_IS_PBS_RETURN_CAR,");
        sql.append("       VP.LINE_CODE,");
        sql.append("       VP.SEQ_NO");
        sql.append("  FROM T_MODEL_ZONE Z");
        sql.append("  LEFT JOIN T_PLAN_DEMAND_PRODUCT P");
        sql.append("    ON P.PRODUCTION_CODE = Z.PRODUCTION_CODE");
        sql.append("  LEFT JOIN (SELECT D.PRODUCTION_CODE,");
        sql.append("                    D.LINE_CODE,");
        sql.append("                    TO_CHAR(M.SCHEDULING_PLAN_DATE, 'yyyymmdd') ||");
        sql.append("                    LPAD(D.SEQ_NO, 3, 0) SEQ_NO");
        sql.append("               FROM T_PLAN_SCHEDULING M");
        sql.append("               LEFT JOIN T_PLAN_SCHEDULING_D D");
        sql.append("                 ON D.SCHEDULING_PLAN_CODE = M.SCHEDULING_PLAN_CODE");
        sql.append("              WHERE D.LINE_CODE IN ('zz0101', 'zz0102')) VP");
        sql.append("    ON VP.PRODUCTION_CODE = Z.PRODUCTION_CODE");
        sql.append(" WHERE Z.ZONE_CODE =");
        sql.append("       (SELECT T.ZONE_CODE");
        sql.append("          FROM T_MODEL_ZONE T");
        sql.append("         WHERE T.PRODUCTION_CODE = ?)");
        sql.append("   AND Z.ZONE_CODE IN");
        sql.append("       ('pbs08_01', 'pbs08_02', 'pbs08_03', 'pbs08_04', 'pbs08_05')");
        sql.append("   AND Z.PRODUCTION_CODE IS NOT NULL");
        sql.append(" ORDER BY Z.PRODUCT_POS");

        return sql.toString();
    }


    /**
     * 获取需求产品信息，参数：白车身钢码号
     *
     * @return
     */
    public static String getDemandProductByVpartSql() {
        StringBuffer sql = new StringBuffer(128);
        sql.append("SELECT T.PRODUCTION_CODE,");
        sql.append("       T.K_STAMP_ID,");
        sql.append("       T.K_IS_PBS_DISABLE_CAR,");
        sql.append("       T.K_IS_PBS_RETURN_CAR,");
        sql.append("       T.K_IS_PBS_BACK_CAR,");
        sql.append("       VP.LINE_CODE,");
        sql.append("       VP.SEQ_NO,");
        sql.append("       T.DEMAND_PRODUCT_TYPE");
        sql.append("  FROM T_PLAN_DEMAND_PRODUCT T");
        sql.append("  LEFT JOIN (SELECT D.PRODUCTION_CODE,");
        sql.append("                    D.LINE_CODE,");
        sql.append("                    TO_CHAR(M.SCHEDULING_PLAN_DATE, 'yyyymmdd') ||");
        sql.append("                    LPAD(D.SEQ_NO, 3, 0) SEQ_NO");
        sql.append("               FROM T_PLAN_SCHEDULING M");
        sql.append("               LEFT JOIN T_PLAN_SCHEDULING_D D");
        sql.append("                 ON D.SCHEDULING_PLAN_CODE = M.SCHEDULING_PLAN_CODE");
        sql.append("              WHERE D.LINE_CODE IN ('zz0101', 'zz0102')) VP");
        sql.append("    ON VP.PRODUCTION_CODE = T.PRODUCTION_CODE");
        sql.append(" WHERE T.DEMAND_PRODUCT_TYPE = 0");
        sql.append("   AND T.K_STAMP_ID = ?");

        return sql.toString();
    }

    /**
     * 获取运行参数
     *
     * @param paraCode 系统参数名称
     * @return false：停止运算，true：启动运算
     */
    public static boolean judgeRunStatus(String strServiceCode, String logFlag, String paraCode) {

        List<Record> list = Db.find("SELECT T.PARA_VALUE,T.PARA_NAME FROM T_SYS_PARA T WHERE T.PARA_CODE=?", paraCode);

        if (list.size() != 1) {
            Write(strServiceCode, logFlag, LogLevel.Error, String.format("读取参数【%s】配置错误，请检查配置", paraCode));
            return false;
        }

        String runStatus = list.get(0).getStr("PARA_VALUE");
        if ("0".equals(runStatus)) {
            Write(strServiceCode, logFlag, LogLevel.Information, String.format("[%s]参数运行手动，停止计算", paraCode));
            return false;
        }
        if (!"1".equals(runStatus)) {
            Write(strServiceCode, logFlag, LogLevel.Information, String.format("[%s]参数配置错误,请检查配置,当前配置值[%s]", paraCode, runStatus));
            return false;
        }

        return true;
    }

}

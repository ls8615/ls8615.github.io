package cvmes.wbs.shiftingmMachineInOne;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;
import cvmes.common.Log;
import cvmes.common.LogLevel;

import javax.swing.plaf.synth.SynthToggleButtonUI;
import java.util.List;

/**
 * 公共处理方法
 */
public class ShiftingmMachineInOneComFun {

    /**
     * 出口一号移行机接车所在区域与接车控制命令字关系
     */
    static Record recordInCmd;

    //出口接车区域与手工禁止信号对照关系
    static Record recordInStatusOfZoneCode;

    /**
     * 获取一号入口移行机运行参数
     *
     * @param paraCode 系统参数名称
     * @return false：停止运算，true：启动运算
     */
    public static boolean judgeRunStatus(String strServiceCode, String paraCode) {

        List<Record> list = Db.find("SELECT T.PARA_VALUE,T.PARA_NAME FROM T_SYS_PARA T WHERE T.PARA_CODE=?", paraCode);

        if (list.size() != 1) {
            Log.Write(strServiceCode, LogLevel.Error, String.format("读取参数【%s】配置错误，请检查配置", paraCode));
            return false;
        }

        String runStatus = list.get(0).getStr("PARA_VALUE");
        if ("0".equals(runStatus)) {
            Log.Write(strServiceCode, LogLevel.Information, String.format("[%s]参数运行手动，停止计算", paraCode));
            return false;
        }
        if (!"1".equals(runStatus)) {
            Log.Write(strServiceCode, LogLevel.Information, String.format("[%s]参数配置错误,请检查配置", paraCode));
            return false;
        }

        return true;
    }


    /**
     * 入口一号移行机进道所在区域与进道指令关系对照关系
     *
     * @return
     */
    public static Record getShiftingMachineInDirectionCarZoneCodeOfInstructionsRela() {
        if (recordInCmd == null) {
            recordInCmd = new Record();
            recordInCmd.set("wbs05_05", 6);
            recordInCmd.set("wbs05_06", 7);
            recordInCmd.set("wbs05_07", 8);
            recordInCmd.set("wbs05_08", 9);
            recordInCmd.set("wbs05_09", 10);
            recordInCmd.set("wbs05_10", 11);
            recordInCmd.set("wbs05_11", 12);
            recordInCmd.set("wbs05_12", 13);
        }

        return recordInCmd;
    }

    /**
     * 出口一号移行机接车所在区域与接车指令关系对照关系
     *
     * @return
     */
    public static Record getRecordInStatusOfZoneCodesRela() {
        if (recordInStatusOfZoneCode == null) {
            recordInStatusOfZoneCode = new Record();
            recordInStatusOfZoneCode.set("wbs05_05", "SH_001");
            recordInStatusOfZoneCode.set("wbs05_06", "SH_002");
            recordInStatusOfZoneCode.set("wbs05_07", "SH_003");
            recordInStatusOfZoneCode.set("wbs05_08", "SH_004");
            recordInStatusOfZoneCode.set("wbs05_09", "SH_005");
            recordInStatusOfZoneCode.set("wbs05_10", "SH_006");
            recordInStatusOfZoneCode.set("wbs05_11", "SH_007");
            recordInStatusOfZoneCode.set("wbs05_12", "SH_008");
        }

        return recordInStatusOfZoneCode;
    }

    /**
     * 获取插入写表指令语句
     *
     * @return
     */
    public static String getWriteMemorySql() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("insert into t_wbs_write_memory(id, logic_memory_address, logic_memory_value, vpart_code, vpart_memory_address1, vpart_memory_address2, vpart_memory_address3, vpart_memory_address4, vpart_memory_address5, vpart_memory_address6, vpart_memory_address7, vpart_memory_address8, vpart_memory_address9, vpart_memory_address10, vpart_memory_address11, vpart_memory_address12, vpart_memory_address13, vpart_memory_value1, vpart_memory_value2, vpart_memory_value3, vpart_memory_value4, vpart_memory_value5, vpart_memory_value6, vpart_memory_value7, vpart_memory_value8, vpart_memory_value9, vpart_memory_value10, vpart_memory_value11, vpart_memory_value12, vpart_memory_value13, deal_user, deal_time, deal_status, fail_reason, device_code, group_memory_code, group_memory_name)");
        sql.append("select sys_guid(),logic_memory_address, ?, ?, vpart_memory_address1, vpart_memory_address2, vpart_memory_address3, vpart_memory_address4, vpart_memory_address5, vpart_memory_address6, vpart_memory_address7, vpart_memory_address8, vpart_memory_address9, vpart_memory_address10, vpart_memory_address11, vpart_memory_address12, vpart_memory_address13, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'system', sysdate, 0, '', device_code, group_memory_code, group_memory_name ");
        sql.append("from t_wbs_read_memory t where t.group_memory_code=?");

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
     * 获取需求产品表信息Sql
     *
     * @return
     */
    public static String getDemandProductInfoFromProductionCodeSql() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("SELECT P.PRODUCTION_CODE, P.K_STAMP_ID");
        sql.append(" ,K_IS_WBS_REPAIR_CAR");
        sql.append(" ,K_IS_WBS_REVIEW_CAR");
        sql.append(" ,K_IS_WBS_BACK_CAR");
        sql.append(" ,K_IS_WBS_DIRECT_CAR");
        sql.append(" ,DEMAND_PRODUCT_CODE");
        sql.append("  FROM T_PLAN_DEMAND_PRODUCT P");
        sql.append(" WHERE P.PRODUCTION_CODE = ?");
        sql.append(" AND p.DEMAND_PRODUCT_TYPE IN (0,5,6)");

        return sql.toString();
    }

    /**
     * 获取需求产品表信息Sql
     *
     * @return
     */
    public static String getDemandProductInfoFromVpartCodeCodeSql() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("SELECT P.PRODUCTION_CODE, P.K_STAMP_ID");
        sql.append(" ,K_IS_WBS_REPAIR_CAR");
        sql.append(" ,K_IS_WBS_REVIEW_CAR");
        sql.append(" ,K_IS_WBS_BACK_CAR");
        sql.append(" ,K_IS_WBS_DIRECT_CAR");
        sql.append(" ,DEMAND_PRODUCT_CODE");
        sql.append("  FROM T_PLAN_DEMAND_PRODUCT P");
        sql.append(" WHERE P.K_STAMP_ID = ?");
        sql.append(" AND p.DEMAND_PRODUCT_TYPE IN (0,5,6)");

        return sql.toString();
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
     * 根据生产编码获取入口一号移行机手工搬入计划，参数：生产编码
     *
     * @return
     */
    public static String getMoveInManualPlanByProductionCodeSql() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("SELECT * FROM t_wbs_move_in_manual_plan t WHERE t.production_code=? AND t.plan_status=0");

        return sql.toString();
    }

    /**
     * 根据特别车类型，获取普通车道可搬入未满空撬道（按特别车优先级排序）,参数：特别车编码
     *
     * @return
     */
    public static String getNotFullEmptyTrackBySpecialCarTypeCodeSql() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("SELECT VT.*, VL.LEVEL_NUM");
        sql.append("  FROM (SELECT T.ZONE_CODE");
        sql.append("          FROM T_MODEL_ZONE T");
        sql.append("         GROUP BY T.ZONE_CODE");
        sql.append("        HAVING COUNT(CASE WHEN t.production_code IS NOT NULL THEN 1 END) =COUNT( CASE WHEN SUBSTR(T.PRODUCTION_CODE, 0, 5) ='WBSBB' THEN 1 END)");
        sql.append("         ORDER BY T.ZONE_CODE) VT");
        sql.append("  LEFT JOIN (SELECT CASE");
        sql.append("                      WHEN ZONE_CODE = 'CAR_LANE5' THEN");
        sql.append("                       'wbs05_05'");
        sql.append("                      WHEN ZONE_CODE = 'CAR_LANE6' THEN");
        sql.append("                       'wbs05_06'");
        sql.append("                      WHEN ZONE_CODE = 'CAR_LANE7' THEN");
        sql.append("                       'wbs05_07'");
        sql.append("                      WHEN ZONE_CODE = 'CAR_LANE8' THEN");
        sql.append("                       'wbs05_08'");
        sql.append("                      WHEN ZONE_CODE = 'CAR_LANE9' THEN");
        sql.append("                       'wbs05_09'");
        sql.append("                      WHEN ZONE_CODE = 'CAR_LANE10' THEN");
        sql.append("                       'wbs05_10'");
        sql.append("                      WHEN ZONE_CODE = 'CAR_LANE11' THEN");
        sql.append("                       'wbs05_11'");
        sql.append("                      WHEN ZONE_CODE = 'CAR_LANE12' THEN");
        sql.append("                       'wbs05_12'");
        sql.append("                    END ZONE_CODE,");
        sql.append("                    LEVEL_NUM");
        sql.append("               FROM T_WBS_SCAR_LINE UNPIVOT(LEVEL_NUM FOR ZONE_CODE IN(CAR_LANE5,");
        sql.append("                                                                       CAR_LANE6,");
        sql.append("                                                                       CAR_LANE7,");
        sql.append("                                                                       CAR_LANE8,");
        sql.append("                                                                       CAR_LANE9,");
        sql.append("                                                                       CAR_LANE10,");
        sql.append("                                                                       CAR_LANE11,");
        sql.append("                                                                       CAR_LANE12))");
        sql.append("              WHERE SPECIAL_CAR_TYPE_CODE = ?) VL");
        sql.append("    ON VL.ZONE_CODE = VT.ZONE_CODE");
        sql.append(" WHERE VT.ZONE_CODE IN");
        sql.append("       (SELECT CASE");
        sql.append("                 WHEN D.DEVICE_SIGNAL_CODE = 'SH_001' THEN");
        sql.append("                  'wbs05_05'");
        sql.append("                 WHEN D.DEVICE_SIGNAL_CODE = 'SH_002' THEN");
        sql.append("                  'wbs05_06'");
        sql.append("                 WHEN D.DEVICE_SIGNAL_CODE = 'SH_003' THEN");
        sql.append("                  'wbs05_07'");
        sql.append("                 WHEN D.DEVICE_SIGNAL_CODE = 'SH_004' THEN");
        sql.append("                  'wbs05_08'");
        sql.append("                 WHEN D.DEVICE_SIGNAL_CODE = 'SH_005' THEN");
        sql.append("                  'wbs05_09'");
        sql.append("                 WHEN D.DEVICE_SIGNAL_CODE = 'SH_006' THEN");
        sql.append("                  'wbs05_10'");
        sql.append("                 WHEN D.DEVICE_SIGNAL_CODE = 'SH_007' THEN");
        sql.append("                  'wbs05_11'");
        sql.append("                 WHEN D.DEVICE_SIGNAL_CODE = 'SH_008' THEN");
        sql.append("                  'wbs05_12'");
        sql.append("               END ZONE_CODE");
        sql.append("          FROM T_WBS_DEVICE_SIGNAL_STATUS D");
        sql.append("         WHERE D.DEVICE_STATUS = 0");
        sql.append("           AND D.DEVICE_SIGNAL_CODE IN ('SH_002',");
        sql.append("                                        'SH_003',");
        sql.append("                                        'SH_004',");
        sql.append("                                        'SH_005',");
        sql.append("                                        'SH_006',");
        sql.append("                                        'SH_007',");
        sql.append("                                        'SH_008'))");
        sql.append(" ORDER BY VL.LEVEL_NUM");

        return sql.toString();
    }

    /**
     * 根据特别车类型，获取普通车道可搬入空车道（按特别车优先级排序）,参数：特别车编码
     *
     * @return
     */
    public static String getEmptyTrackBySpecialCarTypeCodeSql() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("SELECT vt.*,vl.level_num FROM (");
        sql.append("SELECT T.ZONE_CODE");
        sql.append("  FROM T_MODEL_ZONE T");
        sql.append(" WHERE ");
        sql.append("    T.PRODUCTION_CODE IS NULL");
        sql.append(" GROUP BY T.ZONE_CODE ");
        sql.append("HAVING COUNT(1) = MAX(T.PRODUCT_POS)");
        sql.append(" ORDER BY T.ZONE_CODE) vt ");
        sql.append(" LEFT JOIN (SELECT CASE");
        sql.append("         WHEN ZONE_CODE = 'CAR_LANE5' THEN");
        sql.append("          'wbs05_05'");
        sql.append("         WHEN ZONE_CODE = 'CAR_LANE6' THEN");
        sql.append("          'wbs05_06'");
        sql.append("         WHEN ZONE_CODE = 'CAR_LANE7' THEN");
        sql.append("          'wbs05_07'");
        sql.append("         WHEN ZONE_CODE = 'CAR_LANE8' THEN");
        sql.append("          'wbs05_08'");
        sql.append("         WHEN ZONE_CODE = 'CAR_LANE9' THEN");
        sql.append("          'wbs05_09'");
        sql.append("         WHEN ZONE_CODE = 'CAR_LANE10' THEN");
        sql.append("          'wbs05_10'");
        sql.append("         WHEN ZONE_CODE = 'CAR_LANE11' THEN");
        sql.append("          'wbs05_11'");
        sql.append("         WHEN ZONE_CODE = 'CAR_LANE12' THEN");
        sql.append("          'wbs05_12'");
        sql.append("       END ZONE_CODE,");
        sql.append("       LEVEL_NUM");
        sql.append("  FROM T_WBS_SCAR_LINE UNPIVOT(LEVEL_NUM FOR ZONE_CODE IN(CAR_LANE5,");
        sql.append("                                                          CAR_LANE6,");
        sql.append("                                                          CAR_LANE7,");
        sql.append("                                                          CAR_LANE8,");
        sql.append("                                                          CAR_LANE9,");
        sql.append("                                                          CAR_LANE10,");
        sql.append("                                                          CAR_LANE11,");
        sql.append("                                                          CAR_LANE12))");
        sql.append(" WHERE SPECIAL_CAR_TYPE_CODE = ?) vl ON vl.zone_code=vt.zone_code");
        sql.append(" WHERE vt.ZONE_CODE IN");
        sql.append("       (SELECT CASE");
        sql.append("                 WHEN D.DEVICE_SIGNAL_CODE = 'SH_001' THEN");
        sql.append("                  'wbs05_05'");
        sql.append("                 WHEN D.DEVICE_SIGNAL_CODE = 'SH_002' THEN");
        sql.append("                  'wbs05_06'");
        sql.append("                 WHEN D.DEVICE_SIGNAL_CODE = 'SH_003' THEN");
        sql.append("                  'wbs05_07'");
        sql.append("                 WHEN D.DEVICE_SIGNAL_CODE = 'SH_004' THEN");
        sql.append("                  'wbs05_08'");
        sql.append("                 WHEN D.DEVICE_SIGNAL_CODE = 'SH_005' THEN");
        sql.append("                  'wbs05_09'");
        sql.append("                 WHEN D.DEVICE_SIGNAL_CODE = 'SH_006' THEN");
        sql.append("                  'wbs05_10'");
        sql.append("                 WHEN D.DEVICE_SIGNAL_CODE = 'SH_007' THEN");
        sql.append("                  'wbs05_11'");
        sql.append("                 WHEN D.DEVICE_SIGNAL_CODE = 'SH_008' THEN");
        sql.append("                  'wbs05_12'");
        sql.append("               END ZONE_CODE");
        sql.append("          FROM T_WBS_DEVICE_SIGNAL_STATUS D");
        sql.append("         WHERE D.DEVICE_STATUS = 0");
        sql.append("           AND D.DEVICE_SIGNAL_CODE IN (");
        sql.append("                                        'SH_002',");
        sql.append("                                        'SH_003',");
        sql.append("                                        'SH_004',");
        sql.append("                                        'SH_005',");
        sql.append("                                        'SH_006',");
        sql.append("                                        'SH_007',");
        sql.append("                                        'SH_008'))");
        sql.append(" ORDER BY vl.level_num");

        return sql.toString();
    }

    /**
     * 获取当前车与可搬入车道（6-8道）尾车顺序的差异，参数：生产编码
     *
     * @param type 1:尾车比当前车小；2：尾车比当前车大
     * @return
     */
    public static String getCruCarOfLastTrackCarDivSql(int type) {
        StringBuffer sql = new StringBuffer(256);
        sql.append("SELECT VD.PRODUCTION_CODE, VD.ZONE_CODE, SEQ_NO_CRU - SEQ_NO AS DIV");
        sql.append("  FROM (SELECT VT.*,");
        sql.append("               Z.PRODUCTION_CODE,");
        sql.append("               TO_CHAR(VP.SCHEDULING_PLAN_DATE, 'yyyymmdd') ||");
        sql.append("               LPAD(VP.SEQ_NO, 3, 0) SEQ_NO,");
        sql.append("               (SELECT TO_CHAR(M.SCHEDULING_PLAN_DATE, 'yyyymmdd') ||");
        sql.append("                       LPAD(D.SEQ_NO, 3, 0) SEQ_NO");
        sql.append("                  FROM T_PLAN_SCHEDULING M");
        sql.append("                  LEFT JOIN T_PLAN_SCHEDULING_D D");
        sql.append("                    ON D.SCHEDULING_PLAN_CODE = M.SCHEDULING_PLAN_CODE");
        sql.append("                 WHERE D.LINE_CODE = 'tz0101'");
        sql.append("                   AND D.PRODUCTION_CODE = ?) SEQ_NO_CRU");
        sql.append("          FROM (SELECT T.ZONE_CODE,");
        sql.append("                       MAX(CASE");
        sql.append("                             WHEN T.PRODUCTION_CODE IS NOT NULL THEN");
        sql.append("                              T.PRODUCT_POS");
        sql.append("                           END) AS PRODUCT_POS");
        sql.append("                  FROM T_MODEL_ZONE T");
        sql.append("                 WHERE T.ZONE_CODE LIKE 'wbs05_%'");
        sql.append("                 GROUP BY T.ZONE_CODE");
        sql.append("                HAVING MAX(CASE");
        sql.append("                  WHEN T.PRODUCTION_CODE IS NOT NULL THEN");
        sql.append("                   T.PRODUCT_POS");
        sql.append("                END) < MAX(T.PRODUCT_POS)");
        sql.append("                ");
        sql.append("                 ORDER BY T.ZONE_CODE) VT");
        sql.append("          LEFT JOIN T_MODEL_ZONE Z");
        sql.append("            ON Z.ZONE_CODE = VT.ZONE_CODE");
        sql.append("           AND Z.PRODUCT_POS = VT.PRODUCT_POS");
        sql.append("        ");
        sql.append("          LEFT JOIN (SELECT M.SCHEDULING_PLAN_DATE,");
        sql.append("                           D.SEQ_NO,");
        sql.append("                           D.PRODUCTION_CODE");
        sql.append("                      FROM T_PLAN_SCHEDULING M");
        sql.append("                      LEFT JOIN T_PLAN_SCHEDULING_D D");
        sql.append("                        ON D.SCHEDULING_PLAN_CODE = M.SCHEDULING_PLAN_CODE");
        sql.append("                     WHERE D.LINE_CODE = 'tz0101') VP");
        sql.append("            ON VP.PRODUCTION_CODE = Z.PRODUCTION_CODE) VD");
        sql.append("");
        sql.append(" WHERE ZONE_CODE IN");
        sql.append("       (SELECT CASE");
        sql.append("                 WHEN D.DEVICE_SIGNAL_CODE = 'SH_001' THEN");
        sql.append("                  'wbs05_05'");
        sql.append("                 WHEN D.DEVICE_SIGNAL_CODE = 'SH_002' THEN");
        sql.append("                  'wbs05_06'");
        sql.append("                 WHEN D.DEVICE_SIGNAL_CODE = 'SH_003' THEN");
        sql.append("                  'wbs05_07'");
        sql.append("                 WHEN D.DEVICE_SIGNAL_CODE = 'SH_004' THEN");
        sql.append("                  'wbs05_08'");
        sql.append("                 WHEN D.DEVICE_SIGNAL_CODE = 'SH_005' THEN");
        sql.append("                  'wbs05_09'");
        sql.append("                 WHEN D.DEVICE_SIGNAL_CODE = 'SH_006' THEN");
        sql.append("                  'wbs05_10'");
        sql.append("                 WHEN D.DEVICE_SIGNAL_CODE = 'SH_007' THEN");
        sql.append("                  'wbs05_11'");
        sql.append("                 WHEN D.DEVICE_SIGNAL_CODE = 'SH_008' THEN");
        sql.append("                  'wbs05_12'");
        sql.append("               END ZONE_CODE");
        sql.append("          FROM T_WBS_DEVICE_SIGNAL_STATUS D");
        sql.append("         WHERE D.DEVICE_STATUS = 0");
        sql.append("           AND D.DEVICE_SIGNAL_CODE IN (");
        sql.append("                                        'SH_002',");
        sql.append("                                        'SH_003',");
        sql.append("                                        'SH_004',");
        sql.append("                                        'SH_005',");
        sql.append("                                        'SH_006',");
        sql.append("                                        'SH_007',");
        sql.append("                                        'SH_008'))");
        if (type == 1) {
            sql.append("   AND SEQ_NO_CRU - SEQ_NO > 0");
            sql.append(" ORDER BY SEQ_NO_CRU - SEQ_NO");
        }

        if (type == 2) {
            sql.append("  AND SEQ_NO_CRU - SEQ_NO < 0");
            sql.append(" ORDER BY SEQ_NO_CRU - SEQ_NO DESC");
        }

        return sql.toString();
    }

    /**
     * 获取可搬入车道
     *
     * @return
     */
    public static String getAllowEnterTrackSql() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("SELECT Z.ZONE_CODE,");
        sql.append("       COUNT(CASE WHEN Z.PRODUCTION_CODE IS NULL THEN 1 END) EMPTY_NUM ");
        sql.append("  FROM T_MODEL_ZONE Z");
        sql.append(" WHERE Z.ZONE_CODE IN");
        sql.append("       (SELECT CASE");
        sql.append("                 WHEN D.DEVICE_SIGNAL_CODE = 'SH_001' THEN");
        sql.append("                  'wbs05_05'");
        sql.append("                 WHEN D.DEVICE_SIGNAL_CODE = 'SH_002' THEN");
        sql.append("                  'wbs05_06'");
        sql.append("                 WHEN D.DEVICE_SIGNAL_CODE = 'SH_003' THEN");
        sql.append("                  'wbs05_07'");
        sql.append("                 WHEN D.DEVICE_SIGNAL_CODE = 'SH_004' THEN");
        sql.append("                  'wbs05_08'");
        sql.append("                 WHEN D.DEVICE_SIGNAL_CODE = 'SH_005' THEN");
        sql.append("                  'wbs05_09'");
        sql.append("                 WHEN D.DEVICE_SIGNAL_CODE = 'SH_006' THEN");
        sql.append("                  'wbs05_10'");
        sql.append("                 WHEN D.DEVICE_SIGNAL_CODE = 'SH_007' THEN");
        sql.append("                  'wbs05_11'");
        sql.append("                 WHEN D.DEVICE_SIGNAL_CODE = 'SH_008' THEN");
        sql.append("                  'wbs05_12'");
        sql.append("               END ZONE_CODE");
        sql.append("          FROM T_WBS_DEVICE_SIGNAL_STATUS D");
        sql.append("         WHERE D.DEVICE_STATUS = 0");
        sql.append("           AND D.DEVICE_SIGNAL_CODE IN (");
        sql.append("                                        'SH_002',");
        sql.append("                                        'SH_003',");
        sql.append("                                        'SH_004',");
        sql.append("                                        'SH_005',");
        sql.append("                                        'SH_006',");
        sql.append("                                        'SH_007',");
        sql.append("                                        'SH_008'))");
        sql.append(" GROUP BY Z.ZONE_CODE ");
        sql.append("HAVING COUNT(CASE WHEN Z.PRODUCTION_CODE IS NULL THEN 1 END) > 0");

        return sql.toString();
    }

    /**
     * 获取快速道可搬入空位
     * A:车道禁止搬入时，可搬入空位为0，表示不可搬入
     * B:车道非禁止且未满时，表示可搬入
     * C：车道非禁止，且车道已满，可搬入空位为0，表示不可搬入
     *
     * @return
     */
    public static String getCountOfWbs05_05Empty() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("SELECT COUNT(1) ICNT");
        sql.append("  FROM T_MODEL_ZONE T");
        sql.append(" WHERE T.PRODUCTION_CODE IS NULL");
        sql.append("   AND T.ZONE_CODE IN");
        sql.append("       (SELECT CASE");
        sql.append("                 WHEN D.DEVICE_SIGNAL_CODE = 'SH_001' THEN");
        sql.append("                  'wbs05_05'");
        sql.append("               END ZONE_CODE");
        sql.append("          FROM T_WBS_DEVICE_SIGNAL_STATUS D");
        sql.append("         WHERE D.DEVICE_STATUS = 0");
        sql.append("           AND D.DEVICE_SIGNAL_CODE = 'SH_001')");

        return sql.toString();
    }

}

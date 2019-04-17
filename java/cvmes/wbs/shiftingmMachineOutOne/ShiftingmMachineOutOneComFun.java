package cvmes.wbs.shiftingmMachineOutOne;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;
import cvmes.common.Log;
import cvmes.common.LogLevel;

import javax.print.DocFlavor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 公共处理方法
 */
public class ShiftingmMachineOutOneComFun {

    /**
     * 出口一号移行机接车所在区域与接车控制命令字关系
     */
    static Record recordOutCmd;

    //出口接车区域与手工禁止信号对照关系
    static Record recordOutStatusOfZoneCode;

    /**
     * 获取一号出口移行机运行参数
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
     * 获取出口一号移行机手工搬出计划:
     * 只获取手工计划车在车道头车的数据，如果同时有多个手工计划车在车道头车，则按搬出计划编制时间执行
     *
     * @return
     */
    public static String getShiftingMachineOutOneManualPlan() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("SELECT VT.*");
        sql.append("  FROM (SELECT Z.*, T.MAKE_TIME, T.MOVE_DIRECTION");
        sql.append("          FROM T_MODEL_ZONE Z");
        sql.append("          LEFT JOIN T_WBS_MOVE_OUT_MANUAL_PLAN T");
        sql.append("            ON Z.PRODUCTION_CODE = T.PRODUCTION_CODE");
        sql.append("         WHERE Z.PRODUCT_POS = 1");
        sql.append("           AND Z.PRODUCTION_CODE IS NOT NULL");
        sql.append("           AND T.PLAN_STATUS = 0");
        sql.append("           AND Z.ZONE_CODE IN (SELECT CASE");
        sql.append("                                        WHEN T.DEVICE_SIGNAL_CODE = 'SH_009' THEN");
        sql.append("                                         'wbs05_05'");
        sql.append("                                        WHEN T.DEVICE_SIGNAL_CODE = 'SH_010' THEN");
        sql.append("                                         'wbs05_06'");
        sql.append("                                        WHEN T.DEVICE_SIGNAL_CODE = 'SH_011' THEN");
        sql.append("                                         'wbs05_07'");
        sql.append("                                        WHEN T.DEVICE_SIGNAL_CODE = 'SH_012' THEN");
        sql.append("                                         'wbs05_08'");
        sql.append("                                        WHEN T.DEVICE_SIGNAL_CODE = 'SH_013' THEN");
        sql.append("                                         'wbs05_09'");
        sql.append("                                        WHEN T.DEVICE_SIGNAL_CODE = 'SH_014' THEN");
        sql.append("                                         'wbs05_10'");
        sql.append("                                        WHEN T.DEVICE_SIGNAL_CODE = 'SH_015' THEN");
        sql.append("                                         'wbs05_11'");
        sql.append("                                        WHEN T.DEVICE_SIGNAL_CODE = 'SH_016' THEN");
        sql.append("                                         'wbs05_12'");
        sql.append("                                        WHEN T.DEVICE_SIGNAL_CODE = 'SH_017' THEN");
        sql.append("                                         'wbs05_12'");
        sql.append("                                        WHEN T.DEVICE_SIGNAL_CODE = 'SH_018' THEN");
        sql.append("                                         'wbs05_12'");
        sql.append("                                        WHEN T.DEVICE_SIGNAL_CODE = 'SH_019' THEN");
        sql.append("                                         'wbs05_12'");
        sql.append("                                      ");
        sql.append("                                      END AS ZONE_CODE");
        sql.append("                                 FROM T_WBS_DEVICE_SIGNAL_STATUS T");
        sql.append("                                WHERE 1 = 1");
        sql.append("                                  AND T.DEVICE_SIGNAL_CODE IN");
        sql.append("                                      ('SH_009',");
        sql.append("                                       'SH_010',");
        sql.append("                                       'SH_011',");
        sql.append("                                       'SH_012',");
        sql.append("                                       'SH_013',");
        sql.append("                                       'SH_014',");
        sql.append("                                       'SH_015',");
        sql.append("                                       'SH_016',");
        sql.append("                                       'SH_017',");
        sql.append("                                       'SH_018',");
        sql.append("                                       'SH_019')");
        sql.append("                                  AND T.DEVICE_STATUS = 0)");
        sql.append("         ORDER BY T.MAKE_TIME) VT");
        sql.append(" WHERE ROWNUM = 1");

        return sql.toString();
    }

    /**
     * 出口一号移行机接车所在区域与接车指令关系对照关系
     *
     * @return
     */
    public static Record getShiftingMachineOutOnePickCarZoneCodeOfInstructionsRela() {
        if (recordOutCmd == null) {
            recordOutCmd = new Record();
            recordOutCmd.set("wbs05_05", 5);
            recordOutCmd.set("wbs05_06", 6);
            recordOutCmd.set("wbs05_07", 7);
            recordOutCmd.set("wbs05_08", 8);
            recordOutCmd.set("wbs05_09", 9);
            recordOutCmd.set("wbs05_10", 10);
            recordOutCmd.set("wbs05_11", 11);
            recordOutCmd.set("wbs05_12", 12);
            recordOutCmd.set("wbs07", 13);
            recordOutCmd.set("wbs08", 14);
            recordOutCmd.set("wbs09", 15);
        }

        return recordOutCmd;
    }

    /**
     * 出口一号移行机接车所在区域与接车指令关系对照关系
     *
     * @return
     */
    public static Record getrecordOutStatusOfZoneCodesRela() {
        if (recordOutStatusOfZoneCode == null) {
            recordOutStatusOfZoneCode = new Record();
            recordOutStatusOfZoneCode.set("wbs05_05", "SH_009");
            recordOutStatusOfZoneCode.set("wbs05_06", "SH_010");
            recordOutStatusOfZoneCode.set("wbs05_07", "SH_011");
            recordOutStatusOfZoneCode.set("wbs05_08", "SH_012");
            recordOutStatusOfZoneCode.set("wbs05_09", "SH_013");
            recordOutStatusOfZoneCode.set("wbs05_10", "SH_014");
            recordOutStatusOfZoneCode.set("wbs05_11", "SH_015");
            recordOutStatusOfZoneCode.set("wbs05_12", "SH_016");
            recordOutStatusOfZoneCode.set("wbs07", "SH_017");
            recordOutStatusOfZoneCode.set("wbs08", "SH_018");
            recordOutStatusOfZoneCode.set("wbs09", "SH_019");
        }

        return recordOutStatusOfZoneCode;
    }

    /**
     * 获取插入写表指令语句,
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
     * 获取快速道（5道）可搬出道头车区域信息SQL
     *
     * @return
     */
    public static String getZoneOfT5Ssl() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("SELECT *");
        sql.append("  FROM T_MODEL_ZONE");
        sql.append(" WHERE PRODUCT_POS = 1");
        sql.append("   AND PRODUCTION_CODE IS NOT NULL");
        sql.append("   AND ZONE_CODE IN (SELECT CASE");
        sql.append("                              WHEN T.DEVICE_SIGNAL_CODE = 'SH_009' THEN");
        sql.append("                               'wbs05_05'");
        sql.append("                            END AS ZONE_CODE");
        sql.append("                       FROM T_WBS_DEVICE_SIGNAL_STATUS T");
        sql.append("                      WHERE T.DEVICE_SIGNAL_CODE = 'SH_009'");
        sql.append("                        AND T.DEVICE_STATUS = 0)");

        return sql.toString();
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
     * 获取5-12道头车、返修工位、评审工位1和评审工位2是否有返修车、评审车或返回车
     *
     * @param type 0：返修车；1：评审车；2：返回车
     * @return
     */
    public static String getRepairCarOrReviewCarOrBackCar(int type) {
        String str = "";
        switch (type) {
            //返修车
            case 0:
                str = " AND PD.K_IS_WBS_REPAIR_CAR = 1 ";
                break;
            //评审车
            case 1:
                str = " AND PD.K_IS_WBS_REVIEW_CAR = 1 ";
                break;
            //返回车
            case 2:
                str = " AND PD.K_IS_WBS_BACK_CAR = 1 ";
                break;
            default:
                return "";
        }

        StringBuffer sql = new StringBuffer();
        sql.append("SELECT Z.*, ");
        sql.append("       VT.SEQ_NO, ");
        sql.append("       PD.K_IS_WBS_REVIEW_CAR, ");
        sql.append("       PD.K_IS_WBS_REPAIR_CAR, ");
        sql.append("       PD.K_IS_WBS_BACK_CAR ");
        sql.append("  FROM T_MODEL_ZONE Z ");
        sql.append("  LEFT JOIN T_PLAN_DEMAND_PRODUCT PD ");
        sql.append("    ON PD.PRODUCTION_CODE = Z.PRODUCTION_CODE ");
        sql.append("  LEFT JOIN (SELECT TO_CHAR(M.SCHEDULING_PLAN_DATE, 'yyyymmdd') || ");
        sql.append("                    LPAD(D.SEQ_NO, 3, 0) SEQ_NO, ");
        sql.append("                    D.PRODUCTION_CODE ");
        sql.append("               FROM T_PLAN_SCHEDULING M ");
        sql.append("               LEFT JOIN T_PLAN_SCHEDULING_D D ");
        sql.append("                 ON M.SCHEDULING_PLAN_CODE = D.SCHEDULING_PLAN_CODE ");
        sql.append("              WHERE D.LINE_CODE = 'tz0101') VT ");
        sql.append("    ON VT.PRODUCTION_CODE = Z.PRODUCTION_CODE ");
        sql.append(" WHERE PRODUCT_POS = 1 ");
        sql.append("   AND Z.PRODUCTION_CODE IS NOT NULL ");
        sql.append(str);
        sql.append("   AND Z.ZONE_CODE IN ");
        sql.append("       (SELECT CASE ");
        sql.append("                 WHEN T.DEVICE_SIGNAL_CODE = 'SH_009' THEN ");
        sql.append("                  'wbs05_05' ");
        sql.append("                 WHEN T.DEVICE_SIGNAL_CODE = 'SH_010' THEN ");
        sql.append("                  'wbs05_06' ");
        sql.append("                 WHEN T.DEVICE_SIGNAL_CODE = 'SH_011' THEN ");
        sql.append("                  'wbs05_07' ");
        sql.append("                 WHEN T.DEVICE_SIGNAL_CODE = 'SH_012' THEN ");
        sql.append("                  'wbs05_08' ");
        sql.append("                 WHEN T.DEVICE_SIGNAL_CODE = 'SH_013' THEN ");
        sql.append("                  'wbs05_09' ");
        sql.append("                 WHEN T.DEVICE_SIGNAL_CODE = 'SH_014' THEN ");
        sql.append("                  'wbs05_10' ");
        sql.append("                 WHEN T.DEVICE_SIGNAL_CODE = 'SH_015' THEN ");
        sql.append("                  'wbs05_11' ");
        sql.append("                 WHEN T.DEVICE_SIGNAL_CODE = 'SH_016' THEN ");
        sql.append("                  'wbs05_12' ");
        sql.append("                 WHEN T.DEVICE_SIGNAL_CODE = 'SH_017' THEN ");
        sql.append("                  'wbs07' ");
        sql.append("                 WHEN T.DEVICE_SIGNAL_CODE = 'SH_018' THEN ");
        sql.append("                  'wbs08' ");
        sql.append("                 WHEN T.DEVICE_SIGNAL_CODE = 'SH_019' THEN ");
        sql.append("                  'wbs09' ");
        sql.append("               END AS ZONE_CODE ");
        sql.append("          FROM T_WBS_DEVICE_SIGNAL_STATUS T ");
        sql.append("         WHERE 1 = 1 ");
        sql.append("           AND T.DEVICE_SIGNAL_CODE IN ('SH_009', ");
        sql.append("                                        'SH_010', ");
        sql.append("                                        'SH_011', ");
        sql.append("                                        'SH_012', ");
        sql.append("                                        'SH_013', ");
        sql.append("                                        'SH_014', ");
        sql.append("                                        'SH_015', ");
        sql.append("                                        'SH_016', ");
        sql.append("                                        'SH_017', ");
        sql.append("                                        'SH_018', ");
        sql.append("                                        'SH_019') ");
        sql.append("           AND T.DEVICE_STATUS = 0) ");
        sql.append(" ORDER BY VT.SEQ_NO");

        return sql.toString();
    }

    /**
     * 判断返修工位、评审工位1或者评审工位2是否可进车，语句返回值为0：表示运行进车，其他值：表示不允许进车
     *
     * @param status
     * @return
     */
    public static String getepairCarOrReviewCarOrBackCarStatus(int status) {
        String groupMemoryCode = "";
        String zoneCode = "";
        String deviceStatus = "";

        switch (status) {
            //返修工位
            case 0:
                groupMemoryCode = "rework.station";
                zoneCode = "wbs07";
                deviceStatus = "SH_017";
                break;
            //评审工位1
            case 1:
                groupMemoryCode = "no1.review.station";
                zoneCode = "wbs08";
                deviceStatus = "SH_018";
                break;
            //评审工位2
            case 2:
                groupMemoryCode = "no2.review.station";
                zoneCode = "wbs09";
                deviceStatus = "SH_019";
                break;
            default:
                return "";
        }

        StringBuffer sql = new StringBuffer();
        sql.append("SELECT ");
        sql.append("   (SELECT COUNT(1) FROM t_model_zone t WHERE t.zone_code='" + zoneCode + "' AND t.production_code IS NOT NULL)+");
        sql.append("   (SELECT COUNT(1) FROM T_WBS_READ_MEMORY t WHERE t.group_memory_code='" + groupMemoryCode + "' AND t.logic_memory_value!=0)+");
        sql.append("   (SELECT COUNT(1) FROM t_WBS_DEVICE_SIGNAL_STATUS t WHERE t.DEVICE_SIGNAL_CODE='" + deviceStatus + "' AND t.DEVICE_STATUS!='0') icnt");
        sql.append(" FROM dual");

        return sql.toString();
    }

    /**
     * 1.5.获取5-12道可搬出车道头车、可搬出返修工位、可搬出评审工位1和可搬出评审工位2 获取可搬出车道头车计划顺序号最小的车辆所在车道
     * 即：获取可搬出区域头车，计划顺序号最小的车
     *
     * @return
     */
    public static String getZoneCodeSmallCar() {
        StringBuffer sql = new StringBuffer(512);
        sql.append("SELECT z.*, vt.seq_no ");
        sql.append("  FROM T_MODEL_ZONE z ");
        sql.append("  left join (select to_char(m.scheduling_plan_date, 'yyyymmdd') || ");
        sql.append("                    lpad(d.seq_no, 3, 0) SEQ_NO, ");
        sql.append("                    d.PRODUCTION_CODE ");
        sql.append("               from t_plan_scheduling m ");
        sql.append("               left join t_plan_scheduling_d d ");
        sql.append("                 on m.scheduling_plan_code = d.scheduling_plan_code ");
        sql.append("              where d.line_code = 'tz0101') vt ");
        sql.append("    on vt.production_code = z.production_code ");
        sql.append(" WHERE PRODUCT_POS = 1 AND Z.production_code IS NOT NULL ");
        sql.append("   AND z.ZONE_CODE IN ");
        sql.append("       (select case ");
        sql.append("                 when t.device_signal_code = 'SH_009' then ");
        sql.append("                  'wbs05_05' ");
        sql.append("                 when t.device_signal_code = 'SH_010' then ");
        sql.append("                  'wbs05_06' ");
        sql.append("                 when t.device_signal_code = 'SH_011' then ");
        sql.append("                  'wbs05_07' ");
        sql.append("                 when t.device_signal_code = 'SH_012' then ");
        sql.append("                  'wbs05_08' ");
        sql.append("                 when t.device_signal_code = 'SH_013' then ");
        sql.append("                  'wbs05_09' ");
        sql.append("                 when t.device_signal_code = 'SH_014' then ");
        sql.append("                  'wbs05_10' ");
        sql.append("                 when t.device_signal_code = 'SH_015' then ");
        sql.append("                  'wbs05_11' ");
        sql.append("                 when t.device_signal_code = 'SH_016' then ");
        sql.append("                  'wbs05_12' ");
        sql.append("                 when t.device_signal_code = 'SH_017' then ");
        sql.append("                  'wbs07' ");
        sql.append("                 when t.device_signal_code = 'SH_018' then ");
        sql.append("                  'wbs08' ");
        sql.append("                 when t.device_signal_code = 'SH_019' then ");
        sql.append("                  'wbs09' ");
        sql.append("               end as ZONE_CODE ");
        sql.append("          from t_wbs_device_signal_status t ");
        sql.append("         where 1 = 1 ");
        sql.append("           and t.device_signal_code in ('SH_009', ");
        sql.append("                                        'SH_010', ");
        sql.append("                                        'SH_011', ");
        sql.append("                                        'SH_012', ");
        sql.append("                                        'SH_013', ");
        sql.append("                                        'SH_014', ");
        sql.append("                                        'SH_015', ");
        sql.append("                                        'SH_016', ");
        sql.append("                                        'SH_017', ");
        sql.append("                                        'SH_018', ");
        sql.append("                                        'SH_019') ");
        sql.append("           and t.device_status = 0) ");
        sql.append(" order by seq_no ");

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
     * 根据生产编码，获取出口一号移行机手工计划
     *
     * @return
     */
    public static String getMoveOutManualPlanByProductionCode() {
        return "SELECT dp.* FROM T_WBS_MOVE_OUT_MANUAL_PLAN dp where dp.plan_status='0' and dp.PRODUCTION_CODE=?";
    }

    /**
     * 写入过点实绩信息
     *
     * @return
     */
    public static String addActualPassedRecord() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("INSERT INTO T_ACTUAL_PASSED_RECORD(ID,ACTUAL_POINT_CODE,PRODUCTION_CODE,LINE_CODE,WORK_DATE,WORK_SHIFT,PRODUCT_CODE,PASSED_RECORD_TYPE,PASSED_TIME,USER_CODE,DEVICE_CODE)");
        sql.append("SELECT SYS_GUID(),?,?,?,TO_DATE((SELECT FUNC_GET_WORKDAY() FROM DUAL), 'yyyy-mm-dd'),(SELECT FUNC_GET_WORK_SHIFT(?) FROM DUAL),?,T.COLLECT_TYPE,SYSDATE,'system',T.DEVICE_CODE FROM T_MODEL_ACTUAL_POINT T WHERE T.ACTUAL_POINT_CODE = 'wbs01_out'");

        return sql.toString();
    }


    /**
     * 获取可搬出区域头车是否空撬
     *
     * @return
     */
    public static String getMinCarTopIsEmptyCar() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("SELECT *");
        sql.append("  FROM T_MODEL_ZONE T");
        sql.append(" WHERE T.PRODUCT_POS = 1");
        sql.append("   AND SUBSTR(T.PRODUCTION_CODE, 0, 5) = 'WBSBB'");
        sql.append("   AND T.ZONE_CODE =");
        sql.append("       (SELECT ZONE_CODE");
        sql.append("          FROM (SELECT Z.ZONE_CODE, VP.SEQ_NO");
        sql.append("                  FROM T_MODEL_ZONE Z");
        sql.append("                  LEFT JOIN (SELECT D.PRODUCTION_CODE,");
        sql.append("                                   TO_CHAR(M.SCHEDULING_PLAN_DATE, 'yyyymmdd') ||");
        sql.append("                                   LPAD(D.SEQ_NO, 3, 0) SEQ_NO");
        sql.append("                              FROM T_PLAN_SCHEDULING M");
        sql.append("                              LEFT JOIN T_PLAN_SCHEDULING_D D");
        sql.append("                                ON D.SCHEDULING_PLAN_CODE =");
        sql.append("                                   M.SCHEDULING_PLAN_CODE");
        sql.append("                               AND D.LINE_CODE = 'tz0101') VP");
        sql.append("                    ON Z.PRODUCTION_CODE = VP.PRODUCTION_CODE");
        sql.append("                 WHERE Z.PRODUCTION_CODE IS NOT NULL");
        sql.append("                   AND Z.ZONE_CODE IN");
        sql.append("                       (SELECT CASE");
        sql.append("                                 WHEN T.DEVICE_SIGNAL_CODE = 'SH_009' THEN");
        sql.append("                                  'wbs05_05'");
        sql.append("                                 WHEN T.DEVICE_SIGNAL_CODE = 'SH_010' THEN");
        sql.append("                                  'wbs05_06'");
        sql.append("                                 WHEN T.DEVICE_SIGNAL_CODE = 'SH_011' THEN");
        sql.append("                                  'wbs05_07'");
        sql.append("                                 WHEN T.DEVICE_SIGNAL_CODE = 'SH_012' THEN");
        sql.append("                                  'wbs05_08'");
        sql.append("                                 WHEN T.DEVICE_SIGNAL_CODE = 'SH_013' THEN");
        sql.append("                                  'wbs05_09'");
        sql.append("                                 WHEN T.DEVICE_SIGNAL_CODE = 'SH_014' THEN");
        sql.append("                                  'wbs05_10'");
        sql.append("                                 WHEN T.DEVICE_SIGNAL_CODE = 'SH_015' THEN");
        sql.append("                                  'wbs05_11'");
        sql.append("                                 WHEN T.DEVICE_SIGNAL_CODE = 'SH_016' THEN");
        sql.append("                                  'wbs05_12'");
        sql.append("                                 WHEN T.DEVICE_SIGNAL_CODE = 'SH_017' THEN");
        sql.append("                                  'wbs07'");
        sql.append("                                 WHEN T.DEVICE_SIGNAL_CODE = 'SH_018' THEN");
        sql.append("                                  'wbs08'");
        sql.append("                                 WHEN T.DEVICE_SIGNAL_CODE = 'SH_019' THEN");
        sql.append("                                  'wbs09'");
        sql.append("                               ");
        sql.append("                               END AS ZONE_CODE");
        sql.append("                          FROM T_WBS_DEVICE_SIGNAL_STATUS T");
        sql.append("                         WHERE 1 = 1");
        sql.append("                           AND T.DEVICE_SIGNAL_CODE IN");
        sql.append("                               ('SH_009',");
        sql.append("                                'SH_010',");
        sql.append("                                'SH_011',");
        sql.append("                                'SH_012',");
        sql.append("                                'SH_013',");
        sql.append("                                'SH_014',");
        sql.append("                                'SH_015',");
        sql.append("                                'SH_016',");
        sql.append("                                'SH_017',");
        sql.append("                                'SH_018',");
        sql.append("                                'SH_019')");
        sql.append("                           AND T.DEVICE_STATUS = 0)");
        sql.append("                 ORDER BY SEQ_NO)");
        sql.append("         WHERE ROWNUM = 1)");

        return sql.toString();
    }

    /**
     * 获取空撬连续投入台数和间隔台数
     *
     * @return
     */
    public static String getEmptyCarCountAndInterval() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("SELECT MAX_CAR_POS - MAX_EMPTY_POS ICNT_INTERVAL,");
        sql.append("       CASE WHEN MAX_CAR_POS - MAX_EMPTY_POS > 0 THEN 0 ELSE MAX_EMPTY_POS - MIN_CAR_POS END ICNT");
        sql.append("  FROM (SELECT VT.MAX_CAR_POS,");
        sql.append("               VT.MAX_EMPTY_POS,");
        sql.append("               NVL(MAX(CASE");
        sql.append("                         WHEN SUBSTR(Z.PRODUCTION_CODE, 0, 5) != 'WBSBB' AND");
        sql.append("                              Z.PRODUCT_POS < VT.MAX_EMPTY_POS THEN");
        sql.append("                          Z.PRODUCT_POS");
        sql.append("                       END),");
        sql.append("                   0) MIN_CAR_POS");
        sql.append("          FROM T_MODEL_ZONE Z");
        sql.append("          LEFT JOIN (SELECT NVL(MAX(CASE");
        sql.append("                                     WHEN SUBSTR(T.PRODUCTION_CODE, 0, 5) = 'WBSBB' THEN");
        sql.append("                                      T.PRODUCT_POS");
        sql.append("                                   END),");
        sql.append("                               0) MAX_EMPTY_POS,");
        sql.append("                           NVL(MAX(CASE");
        sql.append("                                     WHEN SUBSTR(T.PRODUCTION_CODE, 0, 5) != 'WBSBB' THEN");
        sql.append("                                      T.PRODUCT_POS");
        sql.append("                                   END),");
        sql.append("                               0) MAX_CAR_POS,");
        sql.append("                           T.ZONE_CODE");
        sql.append("                    ");
        sql.append("                      FROM T_MODEL_ZONE T");
        sql.append("                     WHERE T.ZONE_CODE = 'wbs10'");
        sql.append("                     GROUP BY T.ZONE_CODE) VT");
        sql.append("            ON Z.ZONE_CODE = VT.ZONE_CODE");
        sql.append("         WHERE Z.ZONE_CODE = 'wbs10'");
        sql.append("         GROUP BY MAX_EMPTY_POS, MAX_CAR_POS) VD");

        return sql.toString();
    }

    /**
     * 获取快速道直通车头车是否为空撬
     *
     * @return
     */
    public static String getEmptyCarOfDirectCar() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("SELECT *");
        sql.append("  FROM T_MODEL_ZONE T");
        sql.append(" WHERE T.PRODUCT_POS = 1");
        sql.append("   AND T.PRODUCTION_CODE IS NOT NULL");
        sql.append("   AND SUBSTR(T.PRODUCTION_CODE, 0, 5) = 'WBSBB'");
        sql.append("   AND T.ZONE_CODE IN");
        sql.append("        (SELECT Z.ZONE_CODE");
        sql.append("          FROM T_MODEL_ZONE Z");
        sql.append("          LEFT JOIN T_PLAN_DEMAND_PRODUCT P");
        sql.append("            ON Z.PRODUCTION_CODE = P.PRODUCTION_CODE");
        sql.append("         WHERE Z.PRODUCTION_CODE IS NOT NULL");
        sql.append("           AND P.K_IS_WBS_DIRECT_CAR = 1");
        sql.append("           AND Z.ZONE_CODE IN (SELECT CASE");
        sql.append("                                        WHEN T.DEVICE_SIGNAL_CODE = 'SH_009' THEN");
        sql.append("                                         'wbs05_05'");
        sql.append("                                      END AS ZONE_CODE");
        sql.append("                                 FROM T_WBS_DEVICE_SIGNAL_STATUS T");
        sql.append("                                WHERE 1 = 1");
        sql.append("                                  AND T.DEVICE_SIGNAL_CODE IN ('SH_009')");
        sql.append("                                  AND T.DEVICE_STATUS = 0))");

        return sql.toString();
    }

    /**
     * 获取所有可搬出区域头车空撬信息
     *
     * @return
     */
    public static String getEmptyCarOfAllZone() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("SELECT *");
        sql.append("  FROM T_MODEL_ZONE T");
        sql.append(" WHERE T.PRODUCT_POS = 1");
        sql.append("   AND T.PRODUCTION_CODE IS NOT NULL");
        sql.append("   AND SUBSTR(T.PRODUCTION_CODE, 0, 5) = 'WBSBB'");
        sql.append("   AND t.zone_code IN ");
        sql.append("          (select case ");
        sql.append("                         when t.device_signal_code = 'SH_009' then ");
        sql.append("                          'wbs05_05' ");
        sql.append("                         when t.device_signal_code = 'SH_010' then ");
        sql.append("                          'wbs05_06' ");
        sql.append("                         when t.device_signal_code = 'SH_011' then ");
        sql.append("                          'wbs05_07' ");
        sql.append("                         when t.device_signal_code = 'SH_012' then ");
        sql.append("                          'wbs05_08' ");
        sql.append("                         when t.device_signal_code = 'SH_013' then ");
        sql.append("                          'wbs05_09' ");
        sql.append("                         when t.device_signal_code = 'SH_014' then ");
        sql.append("                          'wbs05_10' ");
        sql.append("                         when t.device_signal_code = 'SH_015' then ");
        sql.append("                          'wbs05_11' ");
        sql.append("                         when t.device_signal_code = 'SH_016' then ");
        sql.append("                          'wbs05_12' ");
        sql.append("                         when t.device_signal_code = 'SH_017' then ");
        sql.append("                          'wbs07' ");
        sql.append("                         when t.device_signal_code = 'SH_018' then ");
        sql.append("                          'wbs08' ");
        sql.append("                         when t.device_signal_code = 'SH_019' then ");
        sql.append("                          'wbs09' ");
        sql.append("                       end as ZONE_CODE ");
        sql.append("                  from t_wbs_device_signal_status t ");
        sql.append("                 where 1 = 1 ");
        sql.append("                   and t.device_signal_code in ('SH_009', ");
        sql.append("                                                'SH_010', ");
        sql.append("                                                'SH_011', ");
        sql.append("                                                'SH_012', ");
        sql.append("                                                'SH_013', ");
        sql.append("                                                'SH_014', ");
        sql.append("                                                'SH_015', ");
        sql.append("                                                'SH_016', ");
        sql.append("                                                'SH_017', ");
        sql.append("                                                'SH_018', ");
        sql.append("                                                'SH_019') ");
        sql.append("                   and t.device_status = 0) ");
        sql.append(" ORDER BY T.ZONE_CODE");

        return sql.toString();
    }

}

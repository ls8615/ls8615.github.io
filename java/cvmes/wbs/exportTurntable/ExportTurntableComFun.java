package cvmes.wbs.exportTurntable;

import com.jfinal.plugin.activerecord.Record;

public class ExportTurntableComFun {

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
}

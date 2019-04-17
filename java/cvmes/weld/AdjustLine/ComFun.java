package cvmes.weld.AdjustLine;

import com.jfinal.plugin.activerecord.Record;

public class ComFun {


    /**
     * 获取待打刻车身钢码和白车身图号语句
     */
    public static String getToBeEngravedSql() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("SELECT T1.* ");
        sql.append("  FROM (SELECT T.STEEL_CODE, T.CARBODY_FIGURE_CODE");
        sql.append("          FROM T_INF_TO_WELD_MSIDE T");
        sql.append("         WHERE T.IS_ENGRAVE = 0");
        sql.append("         ORDER BY T.SEQ_NO ASC) T1");
        sql.append(" WHERE ROWNUM = 1");

        return sql.toString();
    }


    /**
     * 获取插入写表指令语句
     */
    public static String getWriteMemorySql() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("INSERT INTO T_DEVICE_WELD_WRITE_RAM(ID,LOGIC_VALUE,VPART_CODE, ROBOT_CODE,AUTOMATISM,");
        sql.append("GROUP_MEMORY_CODE,GROUP_MEMORY_NAME,DEAL_STATUS, DEVICE_CODE,");
        sql.append("VPART_MEMORY_VALUE1, VPART_MEMORY_VALUE2,VPART_MEMORY_VALUE3,VPART_MEMORY_VALUE4,VPART_MEMORY_VALUE5,");
        sql.append(" VPART_MEMORY_VALUE6,VPART_MEMORY_VALUE7,VPART_MEMORY_VALUE8,VPART_MEMORY_VALUE9,VPART_MEMORY_VALUE10,");
        sql.append(" VPART_MEMORY_VALUE11,VPART_MEMORY_VALUE12,VPART_MEMORY_VALUE13,ENGRAVED_MEMORY_VALUE1,ENGRAVED_MEMORY_VALUE2,");
        sql.append(" ENGRAVED_MEMORY_VALUE3,ENGRAVED_MEMORY_VALUE4,ENGRAVED_MEMORY_VALUE5,ENGRAVED_MEMORY_VALUE6,ENGRAVED_MEMORY_VALUE7, ");
        sql.append(" ENGRAVED_MEMORY_VALUE8,ENGRAVED_MEMORY_VALUE9,ENGRAVED_MEMORY_VALUE10,ENGRAVED_MEMORY_VALUE11, ");
        sql.append(" ENGRAVED_MEMORY_VALUE12,ENGRAVED_MEMORY_VALUE13,WHITEDRAWING_MEMORY_VALUE1,WHITEDRAWING_MEMORY_VALUE2,WHITEDRAWING_MEMORY_VALUE3, ");
        sql.append(" WHITEDRAWING_MEMORY_VALUE4,WHITEDRAWING_MEMORY_VALUE5,WHITEDRAWING_MEMORY_VALUE6,WHITEDRAWING_MEMORY_VALUE7,WHITEDRAWING_MEMORY_VALUE8, ");
        sql.append(" WHITEDRAWING_MEMORY_VALUE9,WHITEDRAWING_MEMORY_VALUE10,WHITEDRAWING_MEMORY_VALUE11,WHITEDRAWING_MEMORY_VALUE12,WHITEDRAWING_MEMORY_VALUE13, ");
        sql.append(" WHITEDRAWING_MEMORY_VALUE14,WHITEDRAWING_MEMORY_VALUE15)");
        sql.append("  SELECT SYS_GUID(),?,?,ROBOT_CODE,AUTOMATISM,GROUP_MEMORY_CODE,GROUP_MEMORY_NAME,'0',DEVICE_CODE,?,?,?,?,?,?,?,?,?,?,?,?,?, ");
        sql.append("?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?");
        sql.append(" FROM T_DEVICE_WELD_READ_RAM WHERE GROUP_MEMORY_CODE=?");

        return sql.toString();
    }


    /**
     * 根据待打刻车身钢码字符串，获取内存地址值（short值）
     *
     * @param whitedrawing
     * @return
     */
    public static Record getMemoryValuesFroWhitedrawing(String whitedrawing) {
        Record record = new Record();
        if (whitedrawing.length() == 0) {
            record.set("WHITEDRAWING_MEMORY_VALUE1", 0);
            record.set("WHITEDRAWING_MEMORY_VALUE2", 0);
            record.set("WHITEDRAWING_MEMORY_VALUE3", 0);
            record.set("WHITEDRAWING_MEMORY_VALUE4", 0);
            record.set("WHITEDRAWING_MEMORY_VALUE5", 0);
            record.set("WHITEDRAWING_MEMORY_VALUE6", 0);
            record.set("WHITEDRAWING_MEMORY_VALUE7", 0);
            record.set("WHITEDRAWING_MEMORY_VALUE8", 0);
            record.set("WHITEDRAWING_MEMORY_VALUE9", 0);
            record.set("WHITEDRAWING_MEMORY_VALUE10", 0);
            record.set("WHITEDRAWING_MEMORY_VALUE11", 0);
            record.set("WHITEDRAWING_MEMORY_VALUE12", 0);
            record.set("WHITEDRAWING_MEMORY_VALUE13", 0);
            record.set("WHITEDRAWING_MEMORY_VALUE14", 0);
            record.set("WHITEDRAWING_MEMORY_VALUE15", 0);
        }

        if (whitedrawing.length() != 0) {
            whitedrawing = String.format("%s%s", whitedrawing, "\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0").substring(0, 30);

            byte[] bytes = whitedrawing.getBytes();

            record.set("WHITEDRAWING_MEMORY_VALUE1", ToShort(bytes, 0));
            record.set("WHITEDRAWING_MEMORY_VALUE2", ToShort(bytes, 2));
            record.set("WHITEDRAWING_MEMORY_VALUE3", ToShort(bytes, 4));
            record.set("WHITEDRAWING_MEMORY_VALUE4", ToShort(bytes, 6));
            record.set("WHITEDRAWING_MEMORY_VALUE5", ToShort(bytes, 8));
            record.set("WHITEDRAWING_MEMORY_VALUE6", ToShort(bytes, 10));
            record.set("WHITEDRAWING_MEMORY_VALUE7", ToShort(bytes, 12));
            record.set("WHITEDRAWING_MEMORY_VALUE8", ToShort(bytes, 14));
            record.set("WHITEDRAWING_MEMORY_VALUE9", ToShort(bytes, 16));
            record.set("WHITEDRAWING_MEMORY_VALUE10", ToShort(bytes, 18));
            record.set("WHITEDRAWING_MEMORY_VALUE11", ToShort(bytes, 20));
            record.set("WHITEDRAWING_MEMORY_VALUE12", ToShort(bytes, 22));
            record.set("WHITEDRAWING_MEMORY_VALUE13", ToShort(bytes, 24));
            record.set("WHITEDRAWING_MEMORY_VALUE14", ToShort(bytes, 26));
            record.set("WHITEDRAWING_MEMORY_VALUE15", ToShort(bytes, 28));
        }

        return record;
    }


    /**
     * 根据待打刻车身钢码字符串，获取内存地址值（short值）
     *
     * @param engravedCode
     * @return
     */
    public static Record getMemoryValuesFromEngraved(String engravedCode) {
        Record record = new Record();
        if (engravedCode.length() == 0) {
            record.set("ENGRAVED_MEMORY_VALUE1", 0);
            record.set("ENGRAVED_MEMORY_VALUE2", 0);
            record.set("ENGRAVED_MEMORY_VALUE3", 0);
            record.set("ENGRAVED_MEMORY_VALUE4", 0);
            record.set("ENGRAVED_MEMORY_VALUE5", 0);
            record.set("ENGRAVED_MEMORY_VALUE6", 0);
            record.set("ENGRAVED_MEMORY_VALUE7", 0);
            record.set("ENGRAVED_MEMORY_VALUE8", 0);
            record.set("ENGRAVED_MEMORY_VALUE9", 0);
            record.set("ENGRAVED_MEMORY_VALUE10", 0);
            record.set("ENGRAVED_MEMORY_VALUE11", 0);
            record.set("ENGRAVED_MEMORY_VALUE12", 0);
            record.set("ENGRAVED_MEMORY_VALUE13", 0);
        }

        if (engravedCode.length() != 0) {
            byte[] bytes = engravedCode.getBytes();

            record.set("ENGRAVED_MEMORY_VALUE1", ToShort(bytes, 0));
            record.set("ENGRAVED_MEMORY_VALUE2", ToShort(bytes, 2));
            record.set("ENGRAVED_MEMORY_VALUE3", ToShort(bytes, 4));
            record.set("ENGRAVED_MEMORY_VALUE4", ToShort(bytes, 6));
            record.set("ENGRAVED_MEMORY_VALUE5", ToShort(bytes, 8));
            record.set("ENGRAVED_MEMORY_VALUE6", ToShort(bytes, 10));
            record.set("ENGRAVED_MEMORY_VALUE7", ToShort(bytes, 12));
            record.set("ENGRAVED_MEMORY_VALUE8", ToShort(bytes, 14));
            record.set("ENGRAVED_MEMORY_VALUE9", ToShort(bytes, 16));
            record.set("ENGRAVED_MEMORY_VALUE10", ToShort(bytes, 18));
            record.set("ENGRAVED_MEMORY_VALUE11", ToShort(bytes, 20));
            record.set("ENGRAVED_MEMORY_VALUE12", ToShort(bytes, 22));
            record.set("ENGRAVED_MEMORY_VALUE13", ToShort(bytes, 24));
        }

        return record;
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


}

package cvmes.weld.backSide;

import com.jfinal.plugin.activerecord.Record;

public class ComFun {

    /**
     * 获取焊装后围计划任务
     */
    public static String getBackSidePlan() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("SELECT B.* ");
        sql.append(" FROM (SELECT T.CARBODY_CODE,");
        sql.append("               RPAD(T.ROBOT_CODE, 60, '0') ROBOT_CODE,");
        sql.append("               T.LINE_CODE");
        sql.append("          FROM T_INF_TO_WELD_BSIDE T");
        sql.append("         WHERE T.DEAL_STATUS = 0");
        sql.append("         ORDER BY T.SEQ_NO ASC) B");
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
        sql.append(" VPART_MEMORY_VALUE11,VPART_MEMORY_VALUE12,VPART_MEMORY_VALUE13,ROBOT_MEMORY_VALUE1,ROBOT_MEMORY_VALUE2,");
        sql.append(" ROBOT_MEMORY_VALUE3,ROBOT_MEMORY_VALUE4,ROBOT_MEMORY_VALUE5,ROBOT_MEMORY_VALUE6,ROBOT_MEMORY_VALUE7, ");
        sql.append(" ROBOT_MEMORY_VALUE8,ROBOT_MEMORY_VALUE9,ROBOT_MEMORY_VALUE10,ROBOT_MEMORY_VALUE11, ");
        sql.append(" ROBOT_MEMORY_VALUE12,ROBOT_MEMORY_VALUE13,ROBOT_MEMORY_VALUE14,ROBOT_MEMORY_VALUE15,ROBOT_MEMORY_VALUE16, ");
        sql.append(" ROBOT_MEMORY_VALUE17,ROBOT_MEMORY_VALUE18,ROBOT_MEMORY_VALUE19,ROBOT_MEMORY_VALUE20,ROBOT_MEMORY_VALUE21, ");
        sql.append(" ROBOT_MEMORY_VALUE22,ROBOT_MEMORY_VALUE23,ROBOT_MEMORY_VALUE24,ROBOT_MEMORY_VALUE25,ROBOT_MEMORY_VALUE26, ");
        sql.append(" ROBOT_MEMORY_VALUE27,ROBOT_MEMORY_VALUE28,ROBOT_MEMORY_VALUE29,ROBOT_MEMORY_VALUE30)");
        sql.append("  SELECT SYS_GUID(),?,?,?,AUTOMATISM,GROUP_MEMORY_CODE,GROUP_MEMORY_NAME,'0',DEVICE_CODE,?,?,?,?,?,?,?,?,?,?,?,?, ");
        sql.append("?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?");
        sql.append(" FROM T_DEVICE_WELD_READ_RAM WHERE GROUP_MEMORY_CODE=?");

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
     * 根据焊接代码字符串，获取内存地址值（short值）
     *
     * @param robotlCode 焊接代码
     * @return
     */
    public static Record getRobotMemoryValuesFromRobot(String robotlCode) {
        Record record = new Record();
        if (robotlCode.length() == 0) {
            record.set("ROBOT_MEMORY_VALUE1", 0);
            record.set("ROBOT_MEMORY_VALUE2", 0);
            record.set("ROBOT_MEMORY_VALUE3", 0);
            record.set("ROBOT_MEMORY_VALUE4", 0);
            record.set("ROBOT_MEMORY_VALUE5", 0);
            record.set("ROBOT_MEMORY_VALUE6", 0);
            record.set("ROBOT_MEMORY_VALUE7", 0);
            record.set("ROBOT_MEMORY_VALUE8", 0);
            record.set("ROBOT_MEMORY_VALUE9", 0);
            record.set("ROBOT_MEMORY_VALUE10", 0);
            record.set("ROBOT_MEMORY_VALUE11", 0);
            record.set("ROBOT_MEMORY_VALUE12", 0);
            record.set("ROBOT_MEMORY_VALUE13", 0);
            record.set("ROBOT_MEMORY_VALUE14", 0);
            record.set("ROBOT_MEMORY_VALUE15", 0);
            record.set("ROBOT_MEMORY_VALUE16", 0);
            record.set("ROBOT_MEMORY_VALUE17", 0);
            record.set("ROBOT_MEMORY_VALUE18", 0);
            record.set("ROBOT_MEMORY_VALUE19", 0);
            record.set("ROBOT_MEMORY_VALUE20", 0);
            record.set("ROBOT_MEMORY_VALUE21", 0);
            record.set("ROBOT_MEMORY_VALUE22", 0);
            record.set("ROBOT_MEMORY_VALUE23", 0);
            record.set("ROBOT_MEMORY_VALUE24", 0);
            record.set("ROBOT_MEMORY_VALUE25", 0);
            record.set("ROBOT_MEMORY_VALUE26", 0);
            record.set("ROBOT_MEMORY_VALUE27", 0);
            record.set("ROBOT_MEMORY_VALUE28", 0);
            record.set("ROBOT_MEMORY_VALUE29", 0);
            record.set("ROBOT_MEMORY_VALUE30", 0);
        }

        if (robotlCode.length() != 0) {
            byte[] bytes = robotlCode.getBytes();

            record.set("ROBOT_MEMORY_VALUE1", ToShort(bytes, 0));
            record.set("ROBOT_MEMORY_VALUE2", ToShort(bytes, 2));
            record.set("ROBOT_MEMORY_VALUE3", ToShort(bytes, 4));
            record.set("ROBOT_MEMORY_VALUE4", ToShort(bytes, 6));
            record.set("ROBOT_MEMORY_VALUE5", ToShort(bytes, 8));
            record.set("ROBOT_MEMORY_VALUE6", ToShort(bytes, 10));
            record.set("ROBOT_MEMORY_VALUE7", ToShort(bytes, 12));
            record.set("ROBOT_MEMORY_VALUE8", ToShort(bytes, 14));
            record.set("ROBOT_MEMORY_VALUE9", ToShort(bytes, 16));
            record.set("ROBOT_MEMORY_VALUE10", ToShort(bytes, 18));
            record.set("ROBOT_MEMORY_VALUE11", ToShort(bytes, 20));
            record.set("ROBOT_MEMORY_VALUE12", ToShort(bytes, 22));
            record.set("ROBOT_MEMORY_VALUE13", ToShort(bytes, 24));
            record.set("ROBOT_MEMORY_VALUE14", ToShort(bytes, 26));
            record.set("ROBOT_MEMORY_VALUE15", ToShort(bytes, 28));
            record.set("ROBOT_MEMORY_VALUE16", ToShort(bytes, 30));
            record.set("ROBOT_MEMORY_VALUE17", ToShort(bytes, 32));
            record.set("ROBOT_MEMORY_VALUE18", ToShort(bytes, 34));
            record.set("ROBOT_MEMORY_VALUE19", ToShort(bytes, 36));
            record.set("ROBOT_MEMORY_VALUE20", ToShort(bytes, 38));
            record.set("ROBOT_MEMORY_VALUE21", ToShort(bytes, 40));
            record.set("ROBOT_MEMORY_VALUE22", ToShort(bytes, 42));
            record.set("ROBOT_MEMORY_VALUE23", ToShort(bytes, 44));
            record.set("ROBOT_MEMORY_VALUE24", ToShort(bytes, 46));
            record.set("ROBOT_MEMORY_VALUE25", ToShort(bytes, 48));
            record.set("ROBOT_MEMORY_VALUE26", ToShort(bytes, 50));
            record.set("ROBOT_MEMORY_VALUE27", ToShort(bytes, 52));
            record.set("ROBOT_MEMORY_VALUE28", ToShort(bytes, 54));
            record.set("ROBOT_MEMORY_VALUE29", ToShort(bytes, 56));
            record.set("ROBOT_MEMORY_VALUE30", ToShort(bytes, 58));
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

package cvmes.weld.exceptionOffAndReturnFb220;

import com.jfinal.plugin.activerecord.Record;

public class ComFun {

    /**
     * 插入异常离线回线sql
     */
    public static String insertExcReturnOffLinePlan(){
        StringBuffer sql = new StringBuffer();
        sql.append("insert into T_plan_WELD_OFF_RETURN r (id,plan_type,steel_code,pre_steel_code,");
        sql.append("seq_no,plan_time,line_code,station_code,deal_status,deal_date)");
        sql.append("values (sys_guid(),?,?,?,0,sysdate,?,?,1,sysdate)");

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
     * 根据前置车身钢码号字符串，获取内存地址值（short值）
     *
     * @param loopForwardCode
     * @return
     */
    public static Record getMemoryValuesFromLoopForward(String loopForwardCode) {
        Record record = new Record();
        if (loopForwardCode.length() == 0) {
            record.set("FRONTLINE_MEMORY_VALUE1", 0);
            record.set("FRONTLINE_MEMORY_VALUE2", 0);
            record.set("FRONTLINE_MEMORY_VALUE3", 0);
            record.set("FRONTLINE_MEMORY_VALUE4", 0);
            record.set("FRONTLINE_MEMORY_VALUE5", 0);
            record.set("FRONTLINE_MEMORY_VALUE6", 0);
            record.set("FRONTLINE_MEMORY_VALUE7", 0);
            record.set("FRONTLINE_MEMORY_VALUE8", 0);
            record.set("FRONTLINE_MEMORY_VALUE9", 0);
            record.set("FRONTLINE_MEMORY_VALUE10", 0);
            record.set("FRONTLINE_MEMORY_VALUE11", 0);
            record.set("FRONTLINE_MEMORY_VALUE12", 0);
            record.set("FRONTLINE_MEMORY_VALUE13", 0);
        }

        if (loopForwardCode.length() != 0) {
            byte[] bytes = loopForwardCode.getBytes();

            record.set("FRONTLINE_MEMORY_VALUE1", ToShort(bytes, 0));
            record.set("FRONTLINE_MEMORY_VALUE2", ToShort(bytes, 2));
            record.set("FRONTLINE_MEMORY_VALUE3", ToShort(bytes, 4));
            record.set("FRONTLINE_MEMORY_VALUE4", ToShort(bytes, 6));
            record.set("FRONTLINE_MEMORY_VALUE5", ToShort(bytes, 8));
            record.set("FRONTLINE_MEMORY_VALUE6", ToShort(bytes, 10));
            record.set("FRONTLINE_MEMORY_VALUE7", ToShort(bytes, 12));
            record.set("FRONTLINE_MEMORY_VALUE8", ToShort(bytes, 14));
            record.set("FRONTLINE_MEMORY_VALUE9", ToShort(bytes, 16));
            record.set("FRONTLINE_MEMORY_VALUE10", ToShort(bytes, 18));
            record.set("FRONTLINE_MEMORY_VALUE11", ToShort(bytes, 20));
            record.set("FRONTLINE_MEMORY_VALUE12", ToShort(bytes, 22));
            record.set("FRONTLINE_MEMORY_VALUE13", ToShort(bytes, 24));
        }

        return record;
    }

    /**
     * 根据内存地址值（short值），获取前置车身钢码号
     *
     * @param memoryRead 重保号内存地址Map
     * @return 前置车身钢码
     */
    public static String getLoopForwardFromMemoryValues(Record memoryRead) {
        StringBuffer vparCode = new StringBuffer();
        vparCode.append(short2Byte(Short.parseShort(memoryRead.getStr("FRONTLINE_MEMORY_VALUE1"))));
        vparCode.append(short2Byte(Short.parseShort(memoryRead.getStr("FRONTLINE_MEMORY_VALUE2"))));
        vparCode.append(short2Byte(Short.parseShort(memoryRead.getStr("FRONTLINE_MEMORY_VALUE3"))));
        vparCode.append(short2Byte(Short.parseShort(memoryRead.getStr("FRONTLINE_MEMORY_VALUE4"))));
        vparCode.append(short2Byte(Short.parseShort(memoryRead.getStr("FRONTLINE_MEMORY_VALUE5"))));
        vparCode.append(short2Byte(Short.parseShort(memoryRead.getStr("FRONTLINE_MEMORY_VALUE6"))));
        vparCode.append(short2Byte(Short.parseShort(memoryRead.getStr("FRONTLINE_MEMORY_VALUE7"))));
        vparCode.append(short2Byte(Short.parseShort(memoryRead.getStr("FRONTLINE_MEMORY_VALUE8"))));
        vparCode.append(short2Byte(Short.parseShort(memoryRead.getStr("FRONTLINE_MEMORY_VALUE9"))));
        vparCode.append(short2Byte(Short.parseShort(memoryRead.getStr("FRONTLINE_MEMORY_VALUE10"))));
        vparCode.append(short2Byte(Short.parseShort(memoryRead.getStr("FRONTLINE_MEMORY_VALUE11"))));
        vparCode.append(short2Byte(Short.parseShort(memoryRead.getStr("FRONTLINE_MEMORY_VALUE12"))));
        vparCode.append(short2Byte(Short.parseShort(memoryRead.getStr("FRONTLINE_MEMORY_VALUE13"))));

        return vparCode.toString().trim();
    }


    /**
     * 写入数据
     */
    public static String insertWriteMemoryStatus() {
        StringBuffer sql = new StringBuffer();
        sql.append("INSERT INTO T_DEVICE_WELD_WRITE_RAM (ID,LOGIC_VALUE,AUTOMATISM,GROUP_MEMORY_CODE,GROUP_MEMORY_NAME,DEAL_STATUS,");
        sql.append(" DEAL_TIME,DEVICE_CODE,COMPARE_OK,PLAN_TYPE,vpart_memory_value1,vpart_memory_value2,vpart_memory_value3,");
        sql.append(" vpart_memory_value4,vpart_memory_value5,vpart_memory_value6,vpart_memory_value7,vpart_memory_value8,");
        sql.append("vpart_memory_value9,vpart_memory_value10,vpart_memory_value11,vpart_memory_value12,vpart_memory_value13,");
        sql.append("robot_memory_value1,robot_memory_value2,robot_memory_value3,robot_memory_value4,robot_memory_value5,");
        sql.append(" robot_memory_value6,robot_memory_value7,robot_memory_value8,robot_memory_value9,robot_memory_value10,");
        sql.append("robot_memory_value11,robot_memory_value12,robot_memory_value13,robot_memory_value14,robot_memory_value15,");
        sql.append("robot_memory_value16,robot_memory_value17,robot_memory_value18,robot_memory_value19,robot_memory_value20,");
        sql.append(" robot_memory_value21,robot_memory_value22,robot_memory_value23,robot_memory_value24,robot_memory_value25,");
        sql.append("robot_memory_value26,robot_memory_value27,robot_memory_value28,robot_memory_value29,robot_memory_value30,");
        sql.append("engraved_memory_value1,engraved_memory_value2,engraved_memory_value3,engraved_memory_value4,engraved_memory_value5,");
        sql.append("engraved_memory_value6,engraved_memory_value7,engraved_memory_value8,engraved_memory_value9,engraved_memory_value10,");
        sql.append("engraved_memory_value11,engraved_memory_value12,engraved_memory_value13,frontline_memory_value1,");
        sql.append("frontline_memory_value2,frontline_memory_value3,frontline_memory_value4,frontline_memory_value5,");
        sql.append("frontline_memory_value6,frontline_memory_value7,frontline_memory_value8,frontline_memory_value9,");
        sql.append(" frontline_memory_value10,frontline_memory_value11,frontline_memory_value12,frontline_memory_value13,");
        sql.append(" whitedrawing_memory_value1,whitedrawing_memory_value2,whitedrawing_memory_value3,whitedrawing_memory_value4,");
        sql.append("whitedrawing_memory_value5,whitedrawing_memory_value6,whitedrawing_memory_value7,whitedrawing_memory_value8,");
        sql.append(" whitedrawing_memory_value9,whitedrawing_memory_value10,whitedrawing_memory_value11,whitedrawing_memory_value12,");
        sql.append("  whitedrawing_memory_value13,whitedrawing_memory_value14,whitedrawing_memory_value15)");
        sql.append("SELECT sys_guid(),?,AUTOMATISM,GROUP_MEMORY_CODE,GROUP_MEMORY_NAME,0,SYSDATE,DEVICE_CODE,COMPARE_OK,PLAN_TYPE,");
        sql.append("vpart_memory_value1,vpart_memory_value2,vpart_memory_value3,");
        sql.append(" vpart_memory_value4,vpart_memory_value5,vpart_memory_value6,vpart_memory_value7,vpart_memory_value8,");
        sql.append(" vpart_memory_value9,vpart_memory_value10,vpart_memory_value11,vpart_memory_value12,vpart_memory_value13,");
        sql.append("robot_memory_value1,robot_memory_value2,robot_memory_value3,robot_memory_value4,robot_memory_value5,");
        sql.append("robot_memory_value6,robot_memory_value7,robot_memory_value8,robot_memory_value9,robot_memory_value10,");
        sql.append(" robot_memory_value11,robot_memory_value12,robot_memory_value13,robot_memory_value14,robot_memory_value15,");
        sql.append(" robot_memory_value16,robot_memory_value17,robot_memory_value18,robot_memory_value19,robot_memory_value20,");
        sql.append("robot_memory_value21,robot_memory_value22,robot_memory_value23,robot_memory_value24,robot_memory_value25,");
        sql.append("robot_memory_value26,robot_memory_value27,robot_memory_value28,robot_memory_value29,robot_memory_value30,");
        sql.append("engraved_memory_value1,engraved_memory_value2,engraved_memory_value3,engraved_memory_value4,engraved_memory_value5,");
        sql.append("engraved_memory_value6,engraved_memory_value7,engraved_memory_value8,engraved_memory_value9,engraved_memory_value10,");
        sql.append("engraved_memory_value11,engraved_memory_value12,engraved_memory_value13,frontline_memory_value1,");
        sql.append("frontline_memory_value2,frontline_memory_value3,frontline_memory_value4,frontline_memory_value5,");
        sql.append("frontline_memory_value6,frontline_memory_value7,frontline_memory_value8,frontline_memory_value9,");
        sql.append("frontline_memory_value10,frontline_memory_value11,frontline_memory_value12,frontline_memory_value13,");
        sql.append("whitedrawing_memory_value1,whitedrawing_memory_value2,whitedrawing_memory_value3,whitedrawing_memory_value4,");
        sql.append(" whitedrawing_memory_value5,whitedrawing_memory_value6,whitedrawing_memory_value7,whitedrawing_memory_value8,");
        sql.append("whitedrawing_memory_value9,whitedrawing_memory_value10,whitedrawing_memory_value11,whitedrawing_memory_value12,");
        sql.append(" whitedrawing_memory_value13,whitedrawing_memory_value14,whitedrawing_memory_value15 FROM t_device_weld_read_ram t");
        sql.append("    WHERE t.group_memory_code = ?");
        return sql.toString();
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

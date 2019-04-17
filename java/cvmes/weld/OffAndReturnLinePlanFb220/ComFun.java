package cvmes.weld.OffAndReturnLinePlanFb220;

import com.jfinal.plugin.activerecord.Record;

public class ComFun {

    /**
     * 获取FB220离线回线计划sql
     */
    public static String getOffAndReturnLinePlanFB220Plan() {

        StringBuffer sql = new StringBuffer(256);
        sql.append("SELECT T2.* FROM  (SELECT T.*, P.K_STAMP_ID ");
        sql.append("   FROM T_PLAN_OFFLINE_RETURNLINE T");
        sql.append("   left join T_PLAN_DEMAND_PRODUCT P");
        sql.append("     ON P.PRODUCTION_CODE = T.PRODUCTION_CODE");
        sql.append("   LEFT JOIN T_PLAN_SCHEDULING_D D");
        sql.append("     ON T.PRODUCTION_CODE = D.PRODUCTION_CODE");
        sql.append("  WHERE T.DEAL_STATUS = '0' AND T.WORKSHOP_CODE='ch01'");
        sql.append("           AND (T.STATION_CODE = 'FB220' OR");
        sql.append("               T.RETURNLINE_STATION_CODE = 'FB220')");
        sql.append("  ORDER BY D.SEQ_NO ASC) T2 WHERE ROWNUM =1");

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
        sql.append("  FROM T_PLAN_DEMAND_PRODUCT P");
        sql.append(" WHERE P.PRODUCTION_CODE = ?");
        sql.append(" AND p.DEMAND_PRODUCT_TYPE = 0");

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
        sql.append("  FROM T_PLAN_DEMAND_PRODUCT P");
        sql.append(" WHERE P.K_STAMP_ID = ?");
        sql.append(" AND p.DEMAND_PRODUCT_TYPE =0");

        return sql.toString();
    }

    /**
     * 获取处理离线车辆sql
     */
    public static String getOffDealSql(){

        StringBuffer sql=new StringBuffer(256);
        sql.append("UPDATE T_PLAN_OFFLINE_RETURNLINE O");
        sql.append("   SET O.DEAL_STATUS             = 1,");
        sql.append("       O.DEAL_TIME               = SYSDATE,");
        sql.append("       O.OFFLINE_TIME            = SYSDATE");
        sql.append(" WHERE O.PRODUCTION_CODE =");
        sql.append("       (SELECT D.PRODUCTION_CODE");
        sql.append("          FROM T_PLAN_DEMAND_PRODUCT D");
        sql.append("         WHERE D.K_STAMP_ID = ?)");
        sql.append("   AND O.WORKSHOP_CODE = 'ch01'");
        sql.append("   AND O.PLAN_TYPE = 0");
        sql.append("   AND O.STATION_CODE = 'FB220'");

        return sql.toString();
    }

    /**
     * 获取处理回线车辆sql
     */
    public static String getRetrunDealSql(){

        StringBuffer sql=new StringBuffer(256);
        sql.append("UPDATE T_PLAN_OFFLINE_RETURNLINE O");
        sql.append("   SET O.DEAL_STATUS             = 1,");
        sql.append("       O.DEAL_TIME               = SYSDATE,");
        sql.append("       O.RETURNLINE_TIME            = SYSDATE,");
        sql.append("       O.RETURNLINE_STATION_CODE = 'FB220'");
        sql.append(" WHERE O.PRODUCTION_CODE =");
        sql.append("       (SELECT D.PRODUCTION_CODE");
        sql.append("          FROM T_PLAN_DEMAND_PRODUCT D");
        sql.append("         WHERE D.K_STAMP_ID = ?)");
        sql.append("   AND O.WORKSHOP_CODE = 'ch01'");
        sql.append("   AND O.PLAN_TYPE = 1");
        sql.append("   AND O.PRE_PRODUCTION_CODE=?");

        return sql.toString();
    }


    /**
     * 获取插入写表指令语句
     */
    public static String getWriteMemorySql() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("INSERT INTO T_DEVICE_WELD_WRITE_RAM (ID, LOGIC_VALUE,VPART_CODE,");
        sql.append("AUTOMATISM,LOOP_FORWARD_STEEL_CODE,GROUP_MEMORY_CODE,GROUP_MEMORY_NAME,");
        sql.append("DEAL_STATUS,DEVICE_CODE,PLAN_TYPE,VPART_MEMORY_VALUE1,");
        sql.append(" VPART_MEMORY_VALUE2,VPART_MEMORY_VALUE3,VPART_MEMORY_VALUE4, VPART_MEMORY_VALUE5,");
        sql.append(" VPART_MEMORY_VALUE6,VPART_MEMORY_VALUE7,VPART_MEMORY_VALUE8,VPART_MEMORY_VALUE9,");
        sql.append(" VPART_MEMORY_VALUE10,VPART_MEMORY_VALUE11,VPART_MEMORY_VALUE12,VPART_MEMORY_VALUE13,");
        sql.append(" FRONTLINE_MEMORY_VALUE1,FRONTLINE_MEMORY_VALUE2,FRONTLINE_MEMORY_VALUE3, ");
        sql.append(" FRONTLINE_MEMORY_VALUE4,FRONTLINE_MEMORY_VALUE5,FRONTLINE_MEMORY_VALUE6, ");
        sql.append(" FRONTLINE_MEMORY_VALUE7,FRONTLINE_MEMORY_VALUE8,FRONTLINE_MEMORY_VALUE9, ");
        sql.append(" FRONTLINE_MEMORY_VALUE10,FRONTLINE_MEMORY_VALUE11,FRONTLINE_MEMORY_VALUE12,FRONTLINE_MEMORY_VALUE13) ");
        sql.append("  SELECT SYS_GUID(),?,?,AUTOMATISM,?,GROUP_MEMORY_CODE,GROUP_MEMORY_NAME,'0',DEVICE_CODE,?,?,?,?,?,?,?,?,?,?,?,?,?, ");
        sql.append("?,?,?,?,?,?,?,?,?,?,?,?,?,?");
        sql.append(" FROM T_DEVICE_WELD_READ_RAM WHERE GROUP_MEMORY_CODE=?");

        return sql.toString();
    }



    /**
     * 发送命令 4
     * 获取插入写表指令语句
     * 数据从写表获取
     */
    public static String getSuccessWriteMemorySql() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("INSERT INTO T_DEVICE_WELD_WRITE_RAM (ID, LOGIC_VALUE,VPART_CODE,");
        sql.append("AUTOMATISM,LOOP_FORWARD_STEEL_CODE,GROUP_MEMORY_CODE,GROUP_MEMORY_NAME,");
        sql.append("DEAL_STATUS,DEVICE_CODE,PLAN_TYPE,VPART_MEMORY_VALUE1,");
        sql.append(" VPART_MEMORY_VALUE2,VPART_MEMORY_VALUE3,VPART_MEMORY_VALUE4, VPART_MEMORY_VALUE5,");
        sql.append(" VPART_MEMORY_VALUE6,VPART_MEMORY_VALUE7,VPART_MEMORY_VALUE8,VPART_MEMORY_VALUE9,");
        sql.append(" VPART_MEMORY_VALUE10,VPART_MEMORY_VALUE11,VPART_MEMORY_VALUE12,VPART_MEMORY_VALUE13,");
        sql.append(" FRONTLINE_MEMORY_VALUE1,FRONTLINE_MEMORY_VALUE2,FRONTLINE_MEMORY_VALUE3, ");
        sql.append(" FRONTLINE_MEMORY_VALUE4,FRONTLINE_MEMORY_VALUE5,FRONTLINE_MEMORY_VALUE6, ");
        sql.append(" FRONTLINE_MEMORY_VALUE7,FRONTLINE_MEMORY_VALUE8,FRONTLINE_MEMORY_VALUE9, ");
        sql.append(" FRONTLINE_MEMORY_VALUE10,FRONTLINE_MEMORY_VALUE11,FRONTLINE_MEMORY_VALUE12,FRONTLINE_MEMORY_VALUE13) ");
        sql.append("  SELECT SYS_GUID(),?,VPART_CODE,AUTOMATISM,LOOP_FORWARD_STEEL_CODE,GROUP_MEMORY_CODE,GROUP_MEMORY_NAME,");
        sql.append(" '0',DEVICE_CODE,PLAN_TYPE,VPART_MEMORY_VALUE1,");
        sql.append(" VPART_MEMORY_VALUE2,VPART_MEMORY_VALUE3,VPART_MEMORY_VALUE4, VPART_MEMORY_VALUE5,");
        sql.append(" VPART_MEMORY_VALUE6,VPART_MEMORY_VALUE7,VPART_MEMORY_VALUE8,VPART_MEMORY_VALUE9,");
        sql.append(" VPART_MEMORY_VALUE10,VPART_MEMORY_VALUE11,VPART_MEMORY_VALUE12,VPART_MEMORY_VALUE13,");
        sql.append(" FRONTLINE_MEMORY_VALUE1,FRONTLINE_MEMORY_VALUE2,FRONTLINE_MEMORY_VALUE3, ");
        sql.append(" FRONTLINE_MEMORY_VALUE4,FRONTLINE_MEMORY_VALUE5,FRONTLINE_MEMORY_VALUE6, ");
        sql.append(" FRONTLINE_MEMORY_VALUE7,FRONTLINE_MEMORY_VALUE8,FRONTLINE_MEMORY_VALUE9, ");
        sql.append(" FRONTLINE_MEMORY_VALUE10,FRONTLINE_MEMORY_VALUE11,FRONTLINE_MEMORY_VALUE12,FRONTLINE_MEMORY_VALUE13 ");
        sql.append(" FROM T_DEVICE_WELD_READ_RAM T WHERE T.GROUP_MEMORY_CODE=? ");

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

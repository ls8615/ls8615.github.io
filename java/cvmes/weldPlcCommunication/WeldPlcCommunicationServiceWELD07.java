package cvmes.weldPlcCommunication;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;
import cvmes.common.Log;
import cvmes.common.LogLevel;
import cvmes.common.PlcSiemens;
import cvmes.common.PlcType;

import java.nio.ByteBuffer;
import java.util.Date;
import java.util.List;

/**
 * 焊装空中输送链——PLC通讯服务
 */
public class WeldPlcCommunicationServiceWELD07 extends PlcSiemens {
    int db_code_MB010EX = 2102;//2102
    int db_code_MB010PLAN = 2101;//2101

    @Override
    public void initServiceCode() {
        this.strServiceCode = "WeldPlc07";
    }

    @Override
    public void initPlc(Record rec_service) {
        this.plctype = PlcType.SiemensS300;
        this.ip = rec_service.getStr("SERVICE_PARA1_VALUE");
        this.port = Integer.parseInt(rec_service.getStr("SERVICE_PARA2_VALUE"));
    }

    @Override
    public String runBll(Record rec_service) throws Exception {

        Date start = new Date();
        Log.Write(strServiceCode, LogLevel.Information, String.format("开始同步[%s]", start));

        int ngReadCount = 0;
        int okReadCount = 0;
        int ngWriteCount = 0;
        int okWriteCount = 0;
        int readCount = 2;
        int writeCount = 0;

        Record ret;

        //1.获取同步——MB010异常离线
        ret = syncMemoryMB010EX(db_code_MB010EX);
        if (ret.getBoolean("isWrite")) {
            writeCount++;
            if (ret.getBoolean("isWriteSucess")) {
                okWriteCount++;
            } else {
                okWriteCount++;
            }
        }
        if (ret.getBoolean("isReadSucess")) {
            okReadCount++;
        } else {
            ngReadCount++;
        }

        //2.获取同步——MB010计划离线
        ret = syncMemoryMB010PLAN(db_code_MB010PLAN);
        if (ret.getBoolean("isWrite")) {
            writeCount++;
            if (ret.getBoolean("isWriteSucess")) {
                okWriteCount++;
            } else {
                okWriteCount++;
            }
        }
        if (ret.getBoolean("isReadSucess")) {
            okReadCount++;
        } else {
            ngReadCount++;
        }

        String msg = String.format("读取内存地址组个数[%s]，读取成功个数[%s]，读取失败个数[%s]，写内存地址组指令个数[%s]，写成功个数[%s]，写失败个数[%s]",
                readCount, okReadCount, ngReadCount, writeCount, okWriteCount, ngWriteCount);
        Log.Write(strServiceCode, LogLevel.Information, msg);

        Date end = new Date();
        Log.Write(strServiceCode, LogLevel.Information, String.format("同步结束[%s],耗时[%s]毫米", end, end.getTime() - start.getTime()));

        return msg;
    }

    /**
     * 根据内存地址值（bytes），获取重保号字符串
     *
     * @param bytes 重保号内存地址bytes
     * @return 重保号
     */
    private static String getVpartFromMemoryValues(byte[] bytes) {
        String vpartCode = new String(bytes).trim();
        return vpartCode.trim().length() > 25 ? vpartCode.trim().substring(0, 25) : vpartCode.trim();
    }

    /**
     * FB220与MB010异常离线回线，db2102
     * group1:dbw0
     * byte[0-1]:控制命令字
     * <p>
     * group2:dbw4-dbw54
     * byte[0-29]:车身钢码号
     * byte[30-55]:前置车身钢码号
     * <p>
     * group3:dbw160
     * byte[0-1]:PLC在线离线状态
     *
     * @param dbcode
     * @return
     */
    private Record syncMemoryMB010EX(int dbcode) {
        String msg = "";
        Record recordMsg = new Record().set("isWrite", false).set("isWriteSucess", false).set("isReadSucess", false).set("msg", "");

        //内存地址组
        String groupMemoryCode = "mb010.exception.offline.returnline.plan";

        int len = 56;

        //0.获取读表值
        List<Record> list_read = Db.find(getMemoryReadMB010EXSql(), groupMemoryCode);
        if (list_read.size() != 1) {
            msg = String.format("获取读表信息异常，写表存在多个配置或者配置不存在，内存地址组[%s],配置个数[%s]", groupMemoryCode, list_read.size());
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("isWrite", false);
            return recordMsg;
        }

        //1.获取写表未处理指令
        List<Record> list_write = Db.find(getMemoryWriteMB010EXSql(), groupMemoryCode);

        //1.1.写表未处理指令异常
        if (list_write.size() > 1) {
            msg = String.format("获取待处理指令异常，写表存在多个指令，内存地址组[%s],待处理指令个数[%s]", groupMemoryCode, list_write.size());
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("isWrite", true);
        }

        //1.2.存在待处理指令
        if (list_write.size() == 1) {

            //1.2.1.获取写第一组数据，在线离线状态(只读，不写)

            //1.2.2.获取第二组数据，车身钢码号和前置车身钢码号
            byte[] writeBytes = getWriteBytesFromRecordMB010EX(list_write.get(0));

            //1.2.2.1.写第二组数据
            boolean ret = Write(dbcode, 4, writeBytes);
            if (!ret) {
                msg = String.format("写入车身钢码号和前置车身钢码号到PLC失败，指令数据[%s]", list_write.get(0).toJson());
                Log.Write(strServiceCode, LogLevel.Error, msg);
                recordMsg.set("isWrite", true).set("isWriteSucess", false).set("msg", msg);
            }

            //1.2.3.1.获取写第三组数据，控制命令字
            writeBytes = shortToBytes(Short.parseShort(list_write.get(0).getStr("LOGIC_VALUE")));

            //1.2.3.2.写第三组数据
            ret = Write(dbcode, 0, writeBytes);
            if (!ret) {
                msg = String.format("写入控制命令字到PLC失败，指令数据[%s]", list_write.get(0).toJson());
                Log.Write(strServiceCode, LogLevel.Error, msg);
                recordMsg.set("isWrite", true).set("isWriteSucess", false).set("msg", msg);
            } else {
                //1.3.更新指令状态
                Db.update("update T_DEVICE_WELD_WRITE_RAM t set t.deal_status=1,t.deal_time=sysdate where  T.ID=?", list_write.get(0).getStr("ID"));

                msg = String.format("写入指令到PLC成功，内存地址组[%s],指令数据[%s]", groupMemoryCode, list_write.get(0).toJson());
                Log.Write(strServiceCode, LogLevel.Information, msg);
                recordMsg.set("isWrite", true).set("isWriteSucess", true);
            }
        }

        //1.3.读取数据
        //1.3.1.读取第一组数据，在线离线状态
        byte[] readBytes = Read(dbcode, 160, 2);
        if (readBytes == null) {
            msg = String.format("读取PLC状态（离线状态）内存值失败，内存地址组[%s]", groupMemoryCode);
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("isReadSucess", false).set("msg", msg);

            return recordMsg;
        }

        //1.3.1.1.解析数据
        Short automatism = ToShort(readBytes, 0);

        //1.3.2.读取第二组数据，车身钢码号和前置车身钢码号
        readBytes = Read(dbcode, 4, 52);
        if (readBytes == null) {
            msg = String.format("读取车身钢码号和前置车身刚码号内存值失败，内存地址组[%s]", groupMemoryCode);
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("isReadSucess", false).set("msg", msg);

            return recordMsg;
        }

        //1.3.2.1.解析数据
        Record recordRead = getReadRecordFromBytesMB010EX(readBytes);

        //1.3.3.读取第二组数据，控制命令字
        readBytes = Read(dbcode, 0, 2);
        Short logicValue = ToShort(readBytes, 0);

        //1.4.整合读取数据对象
        recordRead.set("AUTOMATISM", automatism);
        recordRead.set("LOGIC_VALUE", logicValue);


        //1.5.更新内存读表
        int ret = Db.update(getUpdateMemoryReadMB010EXSql(),
                recordRead.getInt("LOGIC_VALUE"),
                recordRead.getInt("AUTOMATISM"),
                recordRead.getInt("VPART_MEMORY_VALUE1"),
                recordRead.getInt("VPART_MEMORY_VALUE2"),
                recordRead.getInt("VPART_MEMORY_VALUE3"),
                recordRead.getInt("VPART_MEMORY_VALUE4"),
                recordRead.getInt("VPART_MEMORY_VALUE5"),
                recordRead.getInt("VPART_MEMORY_VALUE6"),
                recordRead.getInt("VPART_MEMORY_VALUE7"),
                recordRead.getInt("VPART_MEMORY_VALUE8"),
                recordRead.getInt("VPART_MEMORY_VALUE9"),
                recordRead.getInt("VPART_MEMORY_VALUE10"),
                recordRead.getInt("VPART_MEMORY_VALUE11"),
                recordRead.getInt("VPART_MEMORY_VALUE12"),
                recordRead.getInt("VPART_MEMORY_VALUE13"),
                recordRead.getInt("FRONTLINE_MEMORY_VALUE1"),
                recordRead.getInt("FRONTLINE_MEMORY_VALUE2"),
                recordRead.getInt("FRONTLINE_MEMORY_VALUE3"),
                recordRead.getInt("FRONTLINE_MEMORY_VALUE4"),
                recordRead.getInt("FRONTLINE_MEMORY_VALUE5"),
                recordRead.getInt("FRONTLINE_MEMORY_VALUE6"),
                recordRead.getInt("FRONTLINE_MEMORY_VALUE7"),
                recordRead.getInt("FRONTLINE_MEMORY_VALUE8"),
                recordRead.getInt("FRONTLINE_MEMORY_VALUE9"),
                recordRead.getInt("FRONTLINE_MEMORY_VALUE10"),
                recordRead.getInt("FRONTLINE_MEMORY_VALUE11"),
                recordRead.getInt("FRONTLINE_MEMORY_VALUE12"),
                recordRead.getInt("FRONTLINE_MEMORY_VALUE13"),

                recordRead.getStr("VPART_CODE"),
                groupMemoryCode
        );

        if (!isEqualsMB010ExOrFB220Ex(list_read.get(0), recordRead)) {
            msg = String.format("获取内存地址值成功，内存地址组[%s],钢码号[%s],前置车身钢码号[%s],控制命令字[%s],指令数据[%s]",
                    groupMemoryCode,
                    recordRead.getStr("VPART_CODE"),
                    recordRead.getStr("FRONTLINE_VPART_CODE"),
                    recordRead.getInt("LOGIC_VALUE"),
                    recordRead.toJson());
            Log.Write(strServiceCode, LogLevel.Information, msg);
        }

        recordMsg.set("isReadSucess", true);
        return recordMsg;
    }

    /**
     * 获取焊装主线写表指令，参数：内存地址组
     *
     * @return
     */
    private static String getMemoryWriteMB010EXSql() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("SELECT T.ID,");
        sql.append("       T.LOGIC_VALUE,");
        sql.append("       T.AUTOMATISM,");
        sql.append("       T.VPART_MEMORY_VALUE1,");
        sql.append("       T.VPART_MEMORY_VALUE2,");
        sql.append("       T.VPART_MEMORY_VALUE3,");
        sql.append("       T.VPART_MEMORY_VALUE4,");
        sql.append("       T.VPART_MEMORY_VALUE5,");
        sql.append("       T.VPART_MEMORY_VALUE6,");
        sql.append("       T.VPART_MEMORY_VALUE7,");
        sql.append("       T.VPART_MEMORY_VALUE8,");
        sql.append("       T.VPART_MEMORY_VALUE9,");
        sql.append("       T.VPART_MEMORY_VALUE10,");
        sql.append("       T.VPART_MEMORY_VALUE11,");
        sql.append("       T.VPART_MEMORY_VALUE12,");
        sql.append("       T.VPART_MEMORY_VALUE13,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE1,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE2,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE3,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE4,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE5,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE6,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE7,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE8,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE9,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE10,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE11,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE12,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE13,");
        sql.append("       T.VPART_CODE");
        sql.append("  FROM T_DEVICE_WELD_WRITE_RAM T");
        sql.append(" WHERE T.GROUP_MEMORY_CODE = ?  AND T.DEAL_STATUS = 0");

        return sql.toString();
    }

    /**
     * 获取焊装主线写表指令，参数：内存地址组
     *
     * @return
     */
    private static String getMemoryReadMB010EXSql() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("SELECT T.ID,");
        sql.append("       T.LOGIC_VALUE,");
        sql.append("       T.AUTOMATISM,");
        sql.append("       T.VPART_MEMORY_VALUE1,");
        sql.append("       T.VPART_MEMORY_VALUE2,");
        sql.append("       T.VPART_MEMORY_VALUE3,");
        sql.append("       T.VPART_MEMORY_VALUE4,");
        sql.append("       T.VPART_MEMORY_VALUE5,");
        sql.append("       T.VPART_MEMORY_VALUE6,");
        sql.append("       T.VPART_MEMORY_VALUE7,");
        sql.append("       T.VPART_MEMORY_VALUE8,");
        sql.append("       T.VPART_MEMORY_VALUE9,");
        sql.append("       T.VPART_MEMORY_VALUE10,");
        sql.append("       T.VPART_MEMORY_VALUE11,");
        sql.append("       T.VPART_MEMORY_VALUE12,");
        sql.append("       T.VPART_MEMORY_VALUE13,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE1,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE2,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE3,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE4,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE5,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE6,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE7,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE8,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE9,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE10,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE11,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE12,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE13,");
        sql.append("       T.VPART_CODE");
        sql.append("  FROM T_DEVICE_WELD_READ_RAM T");
        sql.append(" WHERE T.GROUP_MEMORY_CODE = ?");

        return sql.toString();
    }

    /**
     * 获取更新读表数据sql，参数：控制命令字|PLC状态|车身钢码号|前置车身钢码号
     *
     * @return
     */
    private static String getUpdateMemoryReadMB010EXSql() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("UPDATE T_DEVICE_WELD_READ_RAM T");
        sql.append("   SET T.LOGIC_VALUE              = ?,");
        sql.append("       T.AUTOMATISM               = ?,");
        sql.append("       T.VPART_MEMORY_VALUE1      = ?,");
        sql.append("       T.VPART_MEMORY_VALUE2      = ?,");
        sql.append("       T.VPART_MEMORY_VALUE3      = ?,");
        sql.append("       T.VPART_MEMORY_VALUE4      = ?,");
        sql.append("       T.VPART_MEMORY_VALUE5      = ?,");
        sql.append("       T.VPART_MEMORY_VALUE6      = ?,");
        sql.append("       T.VPART_MEMORY_VALUE7      = ?,");
        sql.append("       T.VPART_MEMORY_VALUE8      = ?,");
        sql.append("       T.VPART_MEMORY_VALUE9      = ?,");
        sql.append("       T.VPART_MEMORY_VALUE10     = ?,");
        sql.append("       T.VPART_MEMORY_VALUE11     = ?,");
        sql.append("       T.VPART_MEMORY_VALUE12     = ?,");
        sql.append("       T.VPART_MEMORY_VALUE13     = ?,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE1  = ?,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE2  = ?,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE3  = ?,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE4  = ?,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE5  = ?,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE6  = ?,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE7  = ?,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE8  = ?,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE9  = ?,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE10 = ?,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE11 = ?,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE12 = ?,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE13 = ?,");
        sql.append("       T.VPART_CODE               = ?");
        sql.append(" WHERE T.GROUP_MEMORY_CODE = ?");

        return sql.toString();
    }


    /**
     * 根据指令对象，获取指令bytes(车身钢码号和前置车身钢码号)
     *
     * @param memoryWrite
     * @return
     */
    private static byte[] getWriteBytesFromRecordMB010EX(Record memoryWrite) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(56);

        //1.1.钢码号1
        Short s = memoryWrite.getShort("VPART_MEMORY_VALUE1");
        byte[] bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //1.2.钢码号2
        s = memoryWrite.getShort("VPART_MEMORY_VALUE2");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //1.3.钢码号3
        s = memoryWrite.getShort("VPART_MEMORY_VALUE3");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //1.4.钢码号4
        s = memoryWrite.getShort("VPART_MEMORY_VALUE4");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //1.5.钢码号5
        s = memoryWrite.getShort("VPART_MEMORY_VALUE5");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //1.6.钢码号6
        s = memoryWrite.getShort("VPART_MEMORY_VALUE6");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //1.7.钢码号7
        s = memoryWrite.getShort("VPART_MEMORY_VALUE7");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //1.8.钢码号8
        s = memoryWrite.getShort("VPART_MEMORY_VALUE8");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //1.9.钢码号9
        s = memoryWrite.getShort("VPART_MEMORY_VALUE9");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //1.10.钢码号10
        s = memoryWrite.getShort("VPART_MEMORY_VALUE10");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //1.11.钢码号11
        s = memoryWrite.getShort("VPART_MEMORY_VALUE11");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //1.12.钢码号12
        s = memoryWrite.getShort("VPART_MEMORY_VALUE12");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //1.13.钢码号13
        s = memoryWrite.getShort("VPART_MEMORY_VALUE13");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.1.前置车身钢码号1
        s = memoryWrite.getShort("FRONTLINE_MEMORY_VALUE1");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.2.前置车身钢码号2
        s = memoryWrite.getShort("FRONTLINE_MEMORY_VALUE2");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.3.前置车身钢码号3
        s = memoryWrite.getShort("FRONTLINE_MEMORY_VALUE3");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.4.前置车身钢码号4
        s = memoryWrite.getShort("FRONTLINE_MEMORY_VALUE4");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.5.前置车身钢码号5
        s = memoryWrite.getShort("FRONTLINE_MEMORY_VALUE5");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.6.前置车身钢码号6
        s = memoryWrite.getShort("FRONTLINE_MEMORY_VALUE6");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.7.前置车身钢码号7
        s = memoryWrite.getShort("FRONTLINE_MEMORY_VALUE7");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.8.前置车身钢码号8
        s = memoryWrite.getShort("FRONTLINE_MEMORY_VALUE8");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.9.前置车身钢码号9
        s = memoryWrite.getShort("FRONTLINE_MEMORY_VALUE9");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.10.前置车身钢码号10
        s = memoryWrite.getShort("FRONTLINE_MEMORY_VALUE10");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.11.前置车身钢码号11
        s = memoryWrite.getShort("FRONTLINE_MEMORY_VALUE11");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.12.前置车身钢码号12
        s = memoryWrite.getShort("FRONTLINE_MEMORY_VALUE12");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.13.前置车身钢码号13
        s = memoryWrite.getShort("FRONTLINE_MEMORY_VALUE13");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        return byteBuffer.array();
    }

    /**
     * 获取读取内存地址组Record对象
     *
     * @param readBytes
     * @return
     */
    private static Record getReadRecordFromBytesMB010EX(byte[] readBytes) {
        Record record = new Record();

        if (readBytes.length != 52) {
            return null;
        }

        //2.钢码号
        record.set("VPART_MEMORY_VALUE1", ToShort(readBytes, 0));
        record.set("VPART_MEMORY_VALUE2", ToShort(readBytes, 2));
        record.set("VPART_MEMORY_VALUE3", ToShort(readBytes, 4));
        record.set("VPART_MEMORY_VALUE4", ToShort(readBytes, 6));
        record.set("VPART_MEMORY_VALUE5", ToShort(readBytes, 8));
        record.set("VPART_MEMORY_VALUE6", ToShort(readBytes, 10));
        record.set("VPART_MEMORY_VALUE7", ToShort(readBytes, 12));
        record.set("VPART_MEMORY_VALUE8", ToShort(readBytes, 14));
        record.set("VPART_MEMORY_VALUE9", ToShort(readBytes, 16));
        record.set("VPART_MEMORY_VALUE10", ToShort(readBytes, 18));
        record.set("VPART_MEMORY_VALUE11", ToShort(readBytes, 20));
        record.set("VPART_MEMORY_VALUE12", ToShort(readBytes, 22));
        record.set("VPART_MEMORY_VALUE13", ToShort(readBytes, 24));

        //4.前置车身钢码号
        record.set("FRONTLINE_MEMORY_VALUE1", ToShort(readBytes, 26));
        record.set("FRONTLINE_MEMORY_VALUE2", ToShort(readBytes, 28));
        record.set("FRONTLINE_MEMORY_VALUE3", ToShort(readBytes, 30));
        record.set("FRONTLINE_MEMORY_VALUE4", ToShort(readBytes, 32));
        record.set("FRONTLINE_MEMORY_VALUE5", ToShort(readBytes, 34));
        record.set("FRONTLINE_MEMORY_VALUE6", ToShort(readBytes, 36));
        record.set("FRONTLINE_MEMORY_VALUE7", ToShort(readBytes, 38));
        record.set("FRONTLINE_MEMORY_VALUE8", ToShort(readBytes, 40));
        record.set("FRONTLINE_MEMORY_VALUE9", ToShort(readBytes, 42));
        record.set("FRONTLINE_MEMORY_VALUE10", ToShort(readBytes, 44));
        record.set("FRONTLINE_MEMORY_VALUE11", ToShort(readBytes, 46));
        record.set("FRONTLINE_MEMORY_VALUE12", ToShort(readBytes, 48));
        record.set("FRONTLINE_MEMORY_VALUE13", ToShort(readBytes, 50));

        //6.获取钢码号字符串
        int len = 26;
        byte[] tmpbytes = new byte[len];
        System.arraycopy(readBytes, 0, tmpbytes, 0, len);
        record.set("VPART_CODE", getVpartFromMemoryValues(tmpbytes));

        //7.获取机器人代码字符串
        len = 26;
        tmpbytes = new byte[len];
        System.arraycopy(readBytes, 26, tmpbytes, 0, len);
        record.set("FRONTLINE_VPART_CODE", getVpartFromMemoryValues(tmpbytes));

        return record;
    }

    /**
     * FB220计划离线回线
     *
     * @param dbCode
     * @return
     */
    private Record syncMemoryMB010PLAN(int dbCode) {
        String msg = "";
        Record recordMsg = new Record().set("isWrite", false).set("isWriteSucess", false).set("isReadSucess", false).set("msg", "");

        //内存地址组
        String groupMemoryCode = "plan.offline.returnline.mb010";

        int len = 56;

        //0.获取读表值
        List<Record> list_read = Db.find(getMemoryReadMB010PLANSql(), groupMemoryCode);
        if (list_read.size() != 1) {
            msg = String.format("获取读表信息异常，写表存在多个配置或者配置不存在，内存地址组[%s],配置个数[%s]", groupMemoryCode, list_read.size());
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("isWrite", true);
        }

        //1.获取写表未处理指令
        List<Record> list_write = Db.find(getMemoryWriteMB010PLANSql(), groupMemoryCode);

        //1.1.写表未处理指令异常
        if (list_write.size() > 1) {
            msg = String.format("获取待处理指令异常，写表存在多个指令，内存地址组[%s],待处理指令个数[%s]", groupMemoryCode, list_write.size());
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("isWrite", true);
        }

        //1.2.存在待处理指令
        if (list_write.size() == 1) {

            //1.2.1.获取写第一组数据，第二组数据不写（只读），不需要获取
            byte[] bytes = getWriteBytesFromRecordMB010PLAN(list_write.get(0));

            //1.2.1.2.
            boolean ret = Write(dbCode, 0, bytes);
            if (!ret) {
                msg = String.format("写入指令到PLC失败，指令数据:[%s]", list_write.get(0).toJson());
                Log.Write(strServiceCode, LogLevel.Error, msg);
                recordMsg.set("isWrite", true).set("isWriteSucess", false).set("msg", msg);
            } else {
                //1.3.更新指令状态
                Db.update("update T_DEVICE_WELD_WRITE_RAM t set t.deal_status=1,t.deal_time=sysdate where  T.ID=?", list_write.get(0).getStr("ID"));

                msg = String.format("写入指令到PLC成功，指令数据:[%s]", list_write.get(0).toJson());
                Log.Write(strServiceCode, LogLevel.Information, msg);
                recordMsg.set("isWrite", true).set("isWriteSucess", true);
            }
        }

        //1.3.读取内存地址值
        byte[] readBytes = Read(dbCode, 0, len);
        if (readBytes == null) {
            msg = String.format("读取内存值失败，内存地址组[%s]", groupMemoryCode);
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("isReadSucess", false).set("msg", msg);

            return recordMsg;
        }

        //1.3.1.解析内存地址值
        Record recordRead = getReadRecordFromBytesMB010PLAN(readBytes);

        //1.3.1.读取第一组数据，在线离线状态
        readBytes = Read(dbCode, 160, 2);
        if (readBytes == null) {
            msg = String.format("读取PLC状态（离线状态）内存值失败，内存地址组[%s]", groupMemoryCode);
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("isReadSucess", false).set("msg", msg);

            return recordMsg;
        }

        //1.3.1.1.解析数据
        Short automatism = ToShort(readBytes, 0);
        recordRead.set("AUTOMATISM", automatism);

        //1.3.2.更新内存读表
        int ret = Db.update(getUpdateMemoryReadMB010PLANSql(),
                recordRead.getInt("LOGIC_VALUE"),
                recordRead.getInt("AUTOMATISM"),
                recordRead.getInt("PLAN_TYPE"),
                recordRead.getInt("VPART_MEMORY_VALUE1"),
                recordRead.getInt("VPART_MEMORY_VALUE2"),
                recordRead.getInt("VPART_MEMORY_VALUE3"),
                recordRead.getInt("VPART_MEMORY_VALUE4"),
                recordRead.getInt("VPART_MEMORY_VALUE5"),
                recordRead.getInt("VPART_MEMORY_VALUE6"),
                recordRead.getInt("VPART_MEMORY_VALUE7"),
                recordRead.getInt("VPART_MEMORY_VALUE8"),
                recordRead.getInt("VPART_MEMORY_VALUE9"),
                recordRead.getInt("VPART_MEMORY_VALUE10"),
                recordRead.getInt("VPART_MEMORY_VALUE11"),
                recordRead.getInt("VPART_MEMORY_VALUE12"),
                recordRead.getInt("VPART_MEMORY_VALUE13"),
                recordRead.getInt("FRONTLINE_MEMORY_VALUE1"),
                recordRead.getInt("FRONTLINE_MEMORY_VALUE2"),
                recordRead.getInt("FRONTLINE_MEMORY_VALUE3"),
                recordRead.getInt("FRONTLINE_MEMORY_VALUE4"),
                recordRead.getInt("FRONTLINE_MEMORY_VALUE5"),
                recordRead.getInt("FRONTLINE_MEMORY_VALUE6"),
                recordRead.getInt("FRONTLINE_MEMORY_VALUE7"),
                recordRead.getInt("FRONTLINE_MEMORY_VALUE8"),
                recordRead.getInt("FRONTLINE_MEMORY_VALUE9"),
                recordRead.getInt("FRONTLINE_MEMORY_VALUE10"),
                recordRead.getInt("FRONTLINE_MEMORY_VALUE11"),
                recordRead.getInt("FRONTLINE_MEMORY_VALUE12"),
                recordRead.getInt("FRONTLINE_MEMORY_VALUE13"),

                recordRead.getStr("VPART_CODE"),
                groupMemoryCode
        );

        if (!isEqualsMB010PLANOrFB220PLAN(list_read.get(0), recordRead)) {
            msg = String.format("获取内存地址值成功，内存地址组[%s],钢码号[%s],控制命令字[%s],指令数据[%s]",
                    groupMemoryCode,
                    recordRead.getStr("VPART_CODE"),
                    recordRead.getInt("LOGIC_VALUE"),
                    recordRead.toJson());
            Log.Write(strServiceCode, LogLevel.Information, msg);
        }

        recordMsg.set("isReadSucess", true);
        return recordMsg;
    }

    /**
     * 获取MB010 计划离线回线内存读表
     *
     * @return
     */
    private static String getMemoryWriteMB010PLANSql() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("SELECT T.ID,");
        sql.append("       T.LOGIC_VALUE,");
        sql.append("       T.AUTOMATISM,");
        sql.append("       T.PLAN_TYPE,");
        sql.append("       T.VPART_MEMORY_VALUE1,");
        sql.append("       T.VPART_MEMORY_VALUE2,");
        sql.append("       T.VPART_MEMORY_VALUE3,");
        sql.append("       T.VPART_MEMORY_VALUE4,");
        sql.append("       T.VPART_MEMORY_VALUE5,");
        sql.append("       T.VPART_MEMORY_VALUE6,");
        sql.append("       T.VPART_MEMORY_VALUE7,");
        sql.append("       T.VPART_MEMORY_VALUE8,");
        sql.append("       T.VPART_MEMORY_VALUE9,");
        sql.append("       T.VPART_MEMORY_VALUE10,");
        sql.append("       T.VPART_MEMORY_VALUE11,");
        sql.append("       T.VPART_MEMORY_VALUE12,");
        sql.append("       T.VPART_MEMORY_VALUE13,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE1,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE2,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE3,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE4,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE5,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE6,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE7,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE8,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE9,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE10,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE11,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE12,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE13,");
        sql.append("       T.VPART_CODE");
        sql.append("  FROM T_DEVICE_WELD_WRITE_RAM T");
        sql.append(" WHERE T.GROUP_MEMORY_CODE = ?  AND T.DEAL_STATUS = 0");
        return sql.toString();
    }

    /**
     * 获取MB010 计划离线回线内存读表
     *
     * @return
     */
    private static String getMemoryReadMB010PLANSql() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("SELECT T.ID,");
        sql.append("       T.LOGIC_VALUE,");
        sql.append("       T.AUTOMATISM,");
        sql.append("       T.PLAN_TYPE,");
        sql.append("       T.VPART_MEMORY_VALUE1,");
        sql.append("       T.VPART_MEMORY_VALUE2,");
        sql.append("       T.VPART_MEMORY_VALUE3,");
        sql.append("       T.VPART_MEMORY_VALUE4,");
        sql.append("       T.VPART_MEMORY_VALUE5,");
        sql.append("       T.VPART_MEMORY_VALUE6,");
        sql.append("       T.VPART_MEMORY_VALUE7,");
        sql.append("       T.VPART_MEMORY_VALUE8,");
        sql.append("       T.VPART_MEMORY_VALUE9,");
        sql.append("       T.VPART_MEMORY_VALUE10,");
        sql.append("       T.VPART_MEMORY_VALUE11,");
        sql.append("       T.VPART_MEMORY_VALUE12,");
        sql.append("       T.VPART_MEMORY_VALUE13,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE1,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE2,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE3,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE4,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE5,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE6,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE7,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE8,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE9,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE10,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE11,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE12,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE13,");
        sql.append("       T.VPART_CODE");
        sql.append("  FROM T_DEVICE_WELD_READ_RAM T");
        sql.append(" WHERE T.GROUP_MEMORY_CODE = ?");
        return sql.toString();
    }

    /**
     * 更新MB010 计划离线回线内存读表
     *
     * @return
     */
    private static String getUpdateMemoryReadMB010PLANSql() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("UPDATE T_DEVICE_WELD_READ_RAM T");
        sql.append("   SET T.LOGIC_VALUE              = ?,");
        sql.append("       T.AUTOMATISM               = ?,");
        sql.append("       T.PLAN_TYPE                = ?,");
        sql.append("       T.VPART_MEMORY_VALUE1      = ?,");
        sql.append("       T.VPART_MEMORY_VALUE2      = ?,");
        sql.append("       T.VPART_MEMORY_VALUE3      = ?,");
        sql.append("       T.VPART_MEMORY_VALUE4      = ?,");
        sql.append("       T.VPART_MEMORY_VALUE5      = ?,");
        sql.append("       T.VPART_MEMORY_VALUE6      = ?,");
        sql.append("       T.VPART_MEMORY_VALUE7      = ?,");
        sql.append("       T.VPART_MEMORY_VALUE8      = ?,");
        sql.append("       T.VPART_MEMORY_VALUE9      = ?,");
        sql.append("       T.VPART_MEMORY_VALUE10     = ?,");
        sql.append("       T.VPART_MEMORY_VALUE11     = ?,");
        sql.append("       T.VPART_MEMORY_VALUE12     = ?,");
        sql.append("       T.VPART_MEMORY_VALUE13     = ?,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE1  = ?,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE2  = ?,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE3  = ?,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE4  = ?,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE5  = ?,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE6  = ?,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE7  = ?,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE8  = ?,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE9  = ?,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE10 = ?,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE11 = ?,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE12 = ?,");
        sql.append("       T.FRONTLINE_MEMORY_VALUE13 = ?,");
        sql.append("       T.VPART_CODE               = ?");
        sql.append(" WHERE T.GROUP_MEMORY_CODE = ?");
        return sql.toString();
    }

    /**
     * 根据指令对象，获取指令bytes
     *
     * @param memoryWrite
     * @return
     */
    private static byte[] getWriteBytesFromRecordMB010PLAN(Record memoryWrite) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(56);

        //1.控制命令字
        Short s = Short.parseShort(memoryWrite.getStr("LOGIC_VALUE"));
        byte[] bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.计划类型
        s = Short.parseShort(memoryWrite.getStr("PLAN_TYPE"));
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //3.1.钢码号1
        s = memoryWrite.getShort("VPART_MEMORY_VALUE1");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //3.2.钢码号2
        s = memoryWrite.getShort("VPART_MEMORY_VALUE2");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //3.3.钢码号3
        s = memoryWrite.getShort("VPART_MEMORY_VALUE3");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //3.4.钢码号4
        s = memoryWrite.getShort("VPART_MEMORY_VALUE4");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //3.5.钢码号5
        s = memoryWrite.getShort("VPART_MEMORY_VALUE5");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //3.6.钢码号6
        s = memoryWrite.getShort("VPART_MEMORY_VALUE6");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //3.7.钢码号7
        s = memoryWrite.getShort("VPART_MEMORY_VALUE7");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //3.8.钢码号8
        s = memoryWrite.getShort("VPART_MEMORY_VALUE8");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //3.9.钢码号9
        s = memoryWrite.getShort("VPART_MEMORY_VALUE9");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //3.10.钢码号10
        s = memoryWrite.getShort("VPART_MEMORY_VALUE10");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //3.11.钢码号11
        s = memoryWrite.getShort("VPART_MEMORY_VALUE11");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //3.12.钢码号12
        s = memoryWrite.getShort("VPART_MEMORY_VALUE12");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //3.13.钢码号13
        s = memoryWrite.getShort("VPART_MEMORY_VALUE13");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.1.前置车身钢码号1
        s = memoryWrite.getShort("FRONTLINE_MEMORY_VALUE1");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.2.前置车身钢码号2
        s = memoryWrite.getShort("FRONTLINE_MEMORY_VALUE2");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.3.前置车身钢码号3
        s = memoryWrite.getShort("FRONTLINE_MEMORY_VALUE3");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.4.前置车身钢码号4
        s = memoryWrite.getShort("FRONTLINE_MEMORY_VALUE4");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.5.前置车身钢码号5
        s = memoryWrite.getShort("FRONTLINE_MEMORY_VALUE5");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.6.前置车身钢码号6
        s = memoryWrite.getShort("FRONTLINE_MEMORY_VALUE6");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.7.前置车身钢码号7
        s = memoryWrite.getShort("FRONTLINE_MEMORY_VALUE7");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.8.前置车身钢码号8
        s = memoryWrite.getShort("FRONTLINE_MEMORY_VALUE8");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.9.前置车身钢码号9
        s = memoryWrite.getShort("FRONTLINE_MEMORY_VALUE9");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.10.前置车身钢码号10
        s = memoryWrite.getShort("FRONTLINE_MEMORY_VALUE10");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.11.前置车身钢码号11
        s = memoryWrite.getShort("FRONTLINE_MEMORY_VALUE11");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.12.前置车身钢码号12
        s = memoryWrite.getShort("FRONTLINE_MEMORY_VALUE12");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.13.前置车身钢码号13
        s = memoryWrite.getShort("FRONTLINE_MEMORY_VALUE13");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        return byteBuffer.array();
    }

    /**
     * 获取读取内存地址组Record对象
     *
     * @param readBytes
     * @return
     */
    private static Record getReadRecordFromBytesMB010PLAN(byte[] readBytes) {
        Record record = new Record();

        if (readBytes.length != 56) {
            return null;
        }


        record.set("LOGIC_VALUE", ToShort(readBytes, 0));
        record.set("PLAN_TYPE", ToShort(readBytes, 2));

        //2.钢码号
        record.set("VPART_MEMORY_VALUE1", ToShort(readBytes, 4));
        record.set("VPART_MEMORY_VALUE2", ToShort(readBytes, 6));
        record.set("VPART_MEMORY_VALUE3", ToShort(readBytes, 8));
        record.set("VPART_MEMORY_VALUE4", ToShort(readBytes, 10));
        record.set("VPART_MEMORY_VALUE5", ToShort(readBytes, 12));
        record.set("VPART_MEMORY_VALUE6", ToShort(readBytes, 14));
        record.set("VPART_MEMORY_VALUE7", ToShort(readBytes, 16));
        record.set("VPART_MEMORY_VALUE8", ToShort(readBytes, 18));
        record.set("VPART_MEMORY_VALUE9", ToShort(readBytes, 20));
        record.set("VPART_MEMORY_VALUE10", ToShort(readBytes, 22));
        record.set("VPART_MEMORY_VALUE11", ToShort(readBytes, 24));
        record.set("VPART_MEMORY_VALUE12", ToShort(readBytes, 26));
        record.set("VPART_MEMORY_VALUE13", ToShort(readBytes, 28));

        //4.前置车身钢码号
        record.set("FRONTLINE_MEMORY_VALUE1", ToShort(readBytes, 30));
        record.set("FRONTLINE_MEMORY_VALUE2", ToShort(readBytes, 32));
        record.set("FRONTLINE_MEMORY_VALUE3", ToShort(readBytes, 34));
        record.set("FRONTLINE_MEMORY_VALUE4", ToShort(readBytes, 36));
        record.set("FRONTLINE_MEMORY_VALUE5", ToShort(readBytes, 38));
        record.set("FRONTLINE_MEMORY_VALUE6", ToShort(readBytes, 40));
        record.set("FRONTLINE_MEMORY_VALUE7", ToShort(readBytes, 42));
        record.set("FRONTLINE_MEMORY_VALUE8", ToShort(readBytes, 44));
        record.set("FRONTLINE_MEMORY_VALUE9", ToShort(readBytes, 46));
        record.set("FRONTLINE_MEMORY_VALUE10", ToShort(readBytes, 48));
        record.set("FRONTLINE_MEMORY_VALUE11", ToShort(readBytes, 50));
        record.set("FRONTLINE_MEMORY_VALUE12", ToShort(readBytes, 52));
        record.set("FRONTLINE_MEMORY_VALUE13", ToShort(readBytes, 54));

        //6.获取钢码号字符串
        int len = 26;
        byte[] tmpbytes = new byte[len];
        System.arraycopy(readBytes, 4, tmpbytes, 0, len);
        record.set("VPART_CODE", getVpartFromMemoryValues(tmpbytes));

        //7.获取前置车身钢码号字符串
        len = 26;
        tmpbytes = new byte[len];
        System.arraycopy(readBytes, 30, tmpbytes, 0, len);
        record.set("FRONTLINE_VPART_CODE", getVpartFromMemoryValues(tmpbytes));

        return record;
    }

    /**
     * 将Short转成bytes.len=2
     *
     * @param s
     * @return
     */
    private static byte[] shortToBytes(short s) {
        byte[] byteAsci = new byte[2];
        byteAsci[0] = (byte) (s >> 8);
        byteAsci[1] = (byte) (s & 0x00FF);

        return byteAsci;
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
     * MB010EX与FB220EX内存读表值与设备内存值比较
     *
     * @param recordRead       内存读表值
     * @param recordMemoryRead 设备内存值
     * @return
     */
    private static boolean isEqualsMB010ExOrFB220Ex(Record recordRead, Record recordMemoryRead) {
        if (!recordRead.getStr("LOGIC_VALUE").equals(recordMemoryRead.getStr("LOGIC_VALUE"))) {
            return false;
        }
        if (!recordRead.getStr("AUTOMATISM").equals(recordMemoryRead.getStr("AUTOMATISM"))) {
            return false;
        }
        if (!recordRead.getStr("VPART_MEMORY_VALUE1").equals(recordMemoryRead.getStr("VPART_MEMORY_VALUE1"))) {
            return false;
        }
        if (!recordRead.getStr("VPART_MEMORY_VALUE2").equals(recordMemoryRead.getStr("VPART_MEMORY_VALUE2"))) {
            return false;
        }
        if (!recordRead.getStr("VPART_MEMORY_VALUE3").equals(recordMemoryRead.getStr("VPART_MEMORY_VALUE3"))) {
            return false;
        }
        if (!recordRead.getStr("VPART_MEMORY_VALUE4").equals(recordMemoryRead.getStr("VPART_MEMORY_VALUE4"))) {
            return false;
        }
        if (!recordRead.getStr("VPART_MEMORY_VALUE5").equals(recordMemoryRead.getStr("VPART_MEMORY_VALUE5"))) {
            return false;
        }
        if (!recordRead.getStr("VPART_MEMORY_VALUE6").equals(recordMemoryRead.getStr("VPART_MEMORY_VALUE6"))) {
            return false;
        }
        if (!recordRead.getStr("VPART_MEMORY_VALUE7").equals(recordMemoryRead.getStr("VPART_MEMORY_VALUE7"))) {
            return false;
        }
        if (!recordRead.getStr("VPART_MEMORY_VALUE8").equals(recordMemoryRead.getStr("VPART_MEMORY_VALUE8"))) {
            return false;
        }
        if (!recordRead.getStr("VPART_MEMORY_VALUE9").equals(recordMemoryRead.getStr("VPART_MEMORY_VALUE9"))) {
            return false;
        }
        if (!recordRead.getStr("VPART_MEMORY_VALUE10").equals(recordMemoryRead.getStr("VPART_MEMORY_VALUE10"))) {
            return false;
        }
        if (!recordRead.getStr("VPART_MEMORY_VALUE11").equals(recordMemoryRead.getStr("VPART_MEMORY_VALUE11"))) {
            return false;
        }
        if (!recordRead.getStr("VPART_MEMORY_VALUE12").equals(recordMemoryRead.getStr("VPART_MEMORY_VALUE12"))) {
            return false;
        }
        if (!recordRead.getStr("VPART_MEMORY_VALUE13").equals(recordMemoryRead.getStr("VPART_MEMORY_VALUE13"))) {
            return false;
        }
        if (!recordRead.getStr("FRONTLINE_MEMORY_VALUE1").equals(recordMemoryRead.getStr("FRONTLINE_MEMORY_VALUE1"))) {
            return false;
        }
        if (!recordRead.getStr("FRONTLINE_MEMORY_VALUE2").equals(recordMemoryRead.getStr("FRONTLINE_MEMORY_VALUE2"))) {
            return false;
        }
        if (!recordRead.getStr("FRONTLINE_MEMORY_VALUE3").equals(recordMemoryRead.getStr("FRONTLINE_MEMORY_VALUE3"))) {
            return false;
        }
        if (!recordRead.getStr("FRONTLINE_MEMORY_VALUE4").equals(recordMemoryRead.getStr("FRONTLINE_MEMORY_VALUE4"))) {
            return false;
        }
        if (!recordRead.getStr("FRONTLINE_MEMORY_VALUE5").equals(recordMemoryRead.getStr("FRONTLINE_MEMORY_VALUE5"))) {
            return false;
        }
        if (!recordRead.getStr("FRONTLINE_MEMORY_VALUE6").equals(recordMemoryRead.getStr("FRONTLINE_MEMORY_VALUE6"))) {
            return false;
        }
        if (!recordRead.getStr("FRONTLINE_MEMORY_VALUE7").equals(recordMemoryRead.getStr("FRONTLINE_MEMORY_VALUE7"))) {
            return false;
        }
        if (!recordRead.getStr("FRONTLINE_MEMORY_VALUE8").equals(recordMemoryRead.getStr("FRONTLINE_MEMORY_VALUE8"))) {
            return false;
        }
        if (!recordRead.getStr("FRONTLINE_MEMORY_VALUE9").equals(recordMemoryRead.getStr("FRONTLINE_MEMORY_VALUE9"))) {
            return false;
        }
        if (!recordRead.getStr("FRONTLINE_MEMORY_VALUE10").equals(recordMemoryRead.getStr("FRONTLINE_MEMORY_VALUE10"))) {
            return false;
        }
        if (!recordRead.getStr("FRONTLINE_MEMORY_VALUE11").equals(recordMemoryRead.getStr("FRONTLINE_MEMORY_VALUE11"))) {
            return false;
        }
        if (!recordRead.getStr("FRONTLINE_MEMORY_VALUE12").equals(recordMemoryRead.getStr("FRONTLINE_MEMORY_VALUE12"))) {
            return false;
        }
        if (!recordRead.getStr("FRONTLINE_MEMORY_VALUE13").equals(recordMemoryRead.getStr("FRONTLINE_MEMORY_VALUE13"))) {
            return false;
        }

        return true;
    }

    /**
     * MB010PLAN与FB220PLAN内存读表值与设备内存值比较
     *
     * @param recordRead       内存读表值
     * @param recordMemoryRead 设备内存值
     * @return
     */
    private static boolean isEqualsMB010PLANOrFB220PLAN(Record recordRead, Record recordMemoryRead) {
        if (!recordRead.getStr("LOGIC_VALUE").equals(recordMemoryRead.getStr("LOGIC_VALUE"))) {
            return false;
        }
        if (!recordRead.getStr("AUTOMATISM").equals(recordMemoryRead.getStr("AUTOMATISM"))) {
            return false;
        }
        if (!recordRead.getStr("PLAN_TYPE").equals(recordMemoryRead.getStr("PLAN_TYPE"))) {
            return false;
        }
        if (!recordRead.getStr("VPART_MEMORY_VALUE1").equals(recordMemoryRead.getStr("VPART_MEMORY_VALUE1"))) {
            return false;
        }
        if (!recordRead.getStr("VPART_MEMORY_VALUE2").equals(recordMemoryRead.getStr("VPART_MEMORY_VALUE2"))) {
            return false;
        }
        if (!recordRead.getStr("VPART_MEMORY_VALUE3").equals(recordMemoryRead.getStr("VPART_MEMORY_VALUE3"))) {
            return false;
        }
        if (!recordRead.getStr("VPART_MEMORY_VALUE4").equals(recordMemoryRead.getStr("VPART_MEMORY_VALUE4"))) {
            return false;
        }
        if (!recordRead.getStr("VPART_MEMORY_VALUE5").equals(recordMemoryRead.getStr("VPART_MEMORY_VALUE5"))) {
            return false;
        }
        if (!recordRead.getStr("VPART_MEMORY_VALUE6").equals(recordMemoryRead.getStr("VPART_MEMORY_VALUE6"))) {
            return false;
        }
        if (!recordRead.getStr("VPART_MEMORY_VALUE7").equals(recordMemoryRead.getStr("VPART_MEMORY_VALUE7"))) {
            return false;
        }
        if (!recordRead.getStr("VPART_MEMORY_VALUE8").equals(recordMemoryRead.getStr("VPART_MEMORY_VALUE8"))) {
            return false;
        }
        if (!recordRead.getStr("VPART_MEMORY_VALUE9").equals(recordMemoryRead.getStr("VPART_MEMORY_VALUE9"))) {
            return false;
        }
        if (!recordRead.getStr("VPART_MEMORY_VALUE10").equals(recordMemoryRead.getStr("VPART_MEMORY_VALUE10"))) {
            return false;
        }
        if (!recordRead.getStr("VPART_MEMORY_VALUE11").equals(recordMemoryRead.getStr("VPART_MEMORY_VALUE11"))) {
            return false;
        }
        if (!recordRead.getStr("VPART_MEMORY_VALUE12").equals(recordMemoryRead.getStr("VPART_MEMORY_VALUE12"))) {
            return false;
        }
        if (!recordRead.getStr("VPART_MEMORY_VALUE13").equals(recordMemoryRead.getStr("VPART_MEMORY_VALUE13"))) {
            return false;
        }
        if (!recordRead.getStr("FRONTLINE_MEMORY_VALUE1").equals(recordMemoryRead.getStr("FRONTLINE_MEMORY_VALUE1"))) {
            return false;
        }
        if (!recordRead.getStr("FRONTLINE_MEMORY_VALUE2").equals(recordMemoryRead.getStr("FRONTLINE_MEMORY_VALUE2"))) {
            return false;
        }
        if (!recordRead.getStr("FRONTLINE_MEMORY_VALUE3").equals(recordMemoryRead.getStr("FRONTLINE_MEMORY_VALUE3"))) {
            return false;
        }
        if (!recordRead.getStr("FRONTLINE_MEMORY_VALUE4").equals(recordMemoryRead.getStr("FRONTLINE_MEMORY_VALUE4"))) {
            return false;
        }
        if (!recordRead.getStr("FRONTLINE_MEMORY_VALUE5").equals(recordMemoryRead.getStr("FRONTLINE_MEMORY_VALUE5"))) {
            return false;
        }
        if (!recordRead.getStr("FRONTLINE_MEMORY_VALUE6").equals(recordMemoryRead.getStr("FRONTLINE_MEMORY_VALUE6"))) {
            return false;
        }
        if (!recordRead.getStr("FRONTLINE_MEMORY_VALUE7").equals(recordMemoryRead.getStr("FRONTLINE_MEMORY_VALUE7"))) {
            return false;
        }
        if (!recordRead.getStr("FRONTLINE_MEMORY_VALUE8").equals(recordMemoryRead.getStr("FRONTLINE_MEMORY_VALUE8"))) {
            return false;
        }
        if (!recordRead.getStr("FRONTLINE_MEMORY_VALUE9").equals(recordMemoryRead.getStr("FRONTLINE_MEMORY_VALUE9"))) {
            return false;
        }
        if (!recordRead.getStr("FRONTLINE_MEMORY_VALUE10").equals(recordMemoryRead.getStr("FRONTLINE_MEMORY_VALUE10"))) {
            return false;
        }
        if (!recordRead.getStr("FRONTLINE_MEMORY_VALUE11").equals(recordMemoryRead.getStr("FRONTLINE_MEMORY_VALUE11"))) {
            return false;
        }
        if (!recordRead.getStr("FRONTLINE_MEMORY_VALUE12").equals(recordMemoryRead.getStr("FRONTLINE_MEMORY_VALUE12"))) {
            return false;
        }
        if (!recordRead.getStr("FRONTLINE_MEMORY_VALUE13").equals(recordMemoryRead.getStr("FRONTLINE_MEMORY_VALUE13"))) {
            return false;
        }

        return true;
    }
}

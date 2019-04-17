package cvmes.wbsPlcCommunication;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;
import cvmes.common.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * WBS一区PLC通讯服务
 */
public class WbsPlcCommunicationServiceTwo extends PlcSiemens {
    int dbCode1 = 3;

    @Override
    public void initServiceCode() {
        this.strServiceCode = "WbsPlcCommunicationServiceTwo";
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

        //1.获取同步——RFID转接
        ret = syncMemoryOfSupperNo("rfid.transfer", dbCode1, 2);
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

        //2.获取同步——空撬返回道返回实车
        ret = syncMemory("no1.in.shifting.machine", dbCode1, 86);
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
     * WBS-PLC通讯通讯——带支撑号
     *
     * @return
     */
    private Record syncMemoryOfSupperNo(String groupMemoryCode, int dbCode, int dbPos) {
        String msg = "";
        Record recordMsg = new Record().set("isWrite", false).set("isWriteSucess", false).set("isReadSucess", false).set("msg", "");

        //读取字节长度
        int len = 30;

        //0.获取读表值
        List<Record> list_read = Db.find(getMemoryReadSql(), groupMemoryCode);
        if (list_read.size() != 1) {
            msg = String.format("获取读表信息异常，写表存在多个配置或者配置不存在，内存地址组[%s],配置个数[%s]", groupMemoryCode, list_read.size());
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("isWrite", true);
        }

        //1.获取写表未处理指令
        List<Record> list_write = Db.find(getMemoryWriteSql(), groupMemoryCode);

        //1.1.写表未处理指令异常
        if (list_write.size() > 1) {
            msg = String.format("获取待处理指令异常，写表存在多个指令，内存地址组[%s],待处理指令个数[%s]", groupMemoryCode, list_write.size());
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("isWrite", true);
        }

        //1.2.存在待处理指令
        if (list_write.size() == 1) {

            //1.2.1.获取写数据
            byte[] bytes = getWriteBytesFromRecordOfSupperNo(list_write.get(0));

            //1.2.1.2.写数据
            boolean ret = Write(dbCode, dbPos, bytes);
            if (!ret) {
                msg = String.format("写入命令字到PLC失败，指令ID[%s],内存地址组[%s],钢码号[%s],支持号[%s]，命令字[%s]",
                        list_write.get(0).getStr("ID"),
                        groupMemoryCode,
                        list_write.get(0).getStr("VPART_CODE"),
                        list_write.get(0).getInt("SUPPORT_NO"),
                        list_write.get(0).getInt("LOGIC_MEMORY_VALUE"));
                Log.Write(strServiceCode, LogLevel.Error, msg);
                recordMsg.set("isWrite", true).set("isWriteSucess", false).set("msg", msg);
            } else {
                //1.3.更新指令状态
                Db.update("update T_WBS_WRITE_MEMORY t set t.deal_status=1,t.deal_time=sysdate where t.GROUP_MEMORY_CODE=?", groupMemoryCode);

                msg = String.format("写入命令字到PLC成功，指令ID[%s],内存地址组[%s],钢码号[%s],支持号[%s]，命令字[%s]",
                        list_write.get(0).getStr("ID"),
                        groupMemoryCode,
                        list_write.get(0).getStr("VPART_CODE"),
                        list_write.get(0).getInt("SUPPORT_NO"),
                        list_write.get(0).getInt("LOGIC_MEMORY_VALUE"));
                Log.Write(strServiceCode, LogLevel.Information, msg);
                recordMsg.set("isWrite", true).set("isWriteSucess", true);
            }
        }

        //1.3.读取内存地址值
        byte[] readBytes = Read(dbCode, dbPos, len);
        if (readBytes == null) {
            msg = String.format("读取内存值失败，内存地址组[%s]", groupMemoryCode);
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("isReadSucess", false).set("msg", msg);

            return recordMsg;
        }

        //1.3.1.解析内存地址值
        Record recordRead = getReadRecordFromBytesOfSupperNo(readBytes);

        boolean isEquals = isEqualsOfSupperNo(list_read.get(0), recordRead, true);
        if (!isEquals) {
            //1.3.2.更新内存写表
            int ret = Db.update(getUpdateMemoryReadOfSupperNoSql(),
                    recordRead.getInt("LOGIC_MEMORY_VALUE"),
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
                    recordRead.getInt("SUPPORT_NO"),
                    groupMemoryCode
            );

            msg = String.format("获取内存地址值成功，内存地址组[%s],钢码号[%s],控制命令字[%s],指令数据[%s]",
                    groupMemoryCode,
                    recordRead.getStr("VPART_CODE"),
                    recordRead.getInt("LOGIC_MEMORY_VALUE"),
                    recordRead.toJson());
            Log.Write(strServiceCode, LogLevel.Information, msg);
            recordMsg.set("isReadSucess", true);
        } else {
            recordMsg.set("isReadSucess", true);
        }

        return recordMsg;
    }

    /**
     * WBS-PLC通讯通讯——不带支撑号
     *
     * @return
     */
    private Record syncMemory(String groupMemoryCode, int dbCode, int dbPos) {
        String msg = "";
        Record recordMsg = new Record().set("isWrite", false).set("isWriteSucess", false).set("isReadSucess", false).set("msg", "");

        //读取字节长度
        int len = 28;

        //0.获取读表值
        List<Record> list_read = Db.find(getMemoryReadSql(), groupMemoryCode);
        if (list_read.size() != 1) {
            msg = String.format("获取读表信息异常，写表存在多个配置或者配置不存在，内存地址组[%s],配置个数[%s]", groupMemoryCode, list_read.size());
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("isWrite", true);
        }

        //1.获取写表未处理指令
        List<Record> list_write = Db.find(getMemoryWriteSql(), groupMemoryCode);

        //1.1.写表未处理指令异常
        if (list_write.size() > 1) {
            msg = String.format("获取待处理指令异常，写表存在多个指令，内存地址组[%s],待处理指令个数[%s]", groupMemoryCode, list_write.size());
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("isWrite", true);
        }

        //1.2.存在待处理指令
        if (list_write.size() == 1) {

            //1.2.1.获取写数据
            byte[] bytes = getWriteBytesFromRecord(list_write.get(0));

            //1.2.1.2.写数据
            boolean ret = Write(dbCode, dbPos, bytes);
            if (!ret) {
                msg = String.format("写入命令字到PLC失败，指令ID[%s],内存地址组[%s],钢码号[%s],支持号[%s]，命令字[%s]",
                        list_write.get(0).getStr("ID"),
                        groupMemoryCode,
                        list_write.get(0).getStr("VPART_CODE"),
                        list_write.get(0).getInt("SUPPORT_NO"),
                        list_write.get(0).getInt("LOGIC_MEMORY_VALUE"));
                Log.Write(strServiceCode, LogLevel.Error, msg);
                recordMsg.set("isWrite", true).set("isWriteSucess", false).set("msg", msg);
            } else {
                //1.3.更新指令状态
                Db.update("update T_WBS_WRITE_MEMORY t set t.deal_status=1,t.deal_time=sysdate where t.GROUP_MEMORY_CODE=?", groupMemoryCode);

                msg = String.format("写入命令字到PLC成功，指令ID[%s],内存地址组[%s],钢码号[%s],支持号[%s]，命令字[%s]",
                        list_write.get(0).getStr("ID"),
                        groupMemoryCode,
                        list_write.get(0).getStr("VPART_CODE"),
                        list_write.get(0).getInt("SUPPORT_NO"),
                        list_write.get(0).getInt("LOGIC_MEMORY_VALUE"));
                Log.Write(strServiceCode, LogLevel.Information, msg);
                recordMsg.set("isWrite", true).set("isWriteSucess", true);
            }
        }

        //1.3.读取内存地址值
        byte[] readBytes = Read(dbCode, dbPos, len);
        if (readBytes == null) {
            msg = String.format("读取内存值失败，内存地址组[%s]", groupMemoryCode);
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("isReadSucess", false).set("msg", msg);

            return recordMsg;
        }

        //1.3.1.解析内存地址值
        Record recordRead = getReadRecordFromBytes(readBytes);

        boolean isEquals = isEqualsOfSupperNo(list_read.get(0), recordRead, false);
        if (!isEquals) {
            //1.3.2.更新内存写表
            int ret = Db.update(getUpdateMemoryReadSql(),
                    recordRead.getInt("LOGIC_MEMORY_VALUE"),
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
                    groupMemoryCode
            );

            msg = String.format("获取内存地址值成功，内存地址组[%s],钢码号[%s],控制命令字[%s],指令数据[%s]",
                    groupMemoryCode,
                    recordRead.getStr("VPART_CODE"),
                    recordRead.getInt("LOGIC_MEMORY_VALUE"),
                    recordRead.toJson());
            Log.Write(strServiceCode, LogLevel.Information, msg);
            recordMsg.set("isReadSucess", true);
        } else {
            recordMsg.set("isReadSucess", true);
        }

        return recordMsg;
    }

    /**
     * 获取WBS写表指令，参数：内存地址组
     *
     * @return
     */
    private static String getMemoryWriteSql() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("SELECT ID,");
        sql.append("       LOGIC_MEMORY_VALUE,");
        sql.append("       VPART_CODE,");
        sql.append("       VPART_MEMORY_VALUE1,");
        sql.append("       VPART_MEMORY_VALUE2,");
        sql.append("       VPART_MEMORY_VALUE3,");
        sql.append("       VPART_MEMORY_VALUE4,");
        sql.append("       VPART_MEMORY_VALUE5,");
        sql.append("       VPART_MEMORY_VALUE6,");
        sql.append("       VPART_MEMORY_VALUE7,");
        sql.append("       VPART_MEMORY_VALUE8,");
        sql.append("       VPART_MEMORY_VALUE9,");
        sql.append("       VPART_MEMORY_VALUE10,");
        sql.append("       VPART_MEMORY_VALUE11,");
        sql.append("       VPART_MEMORY_VALUE12,");
        sql.append("       VPART_MEMORY_VALUE13,");
        sql.append("       NVL(T.SUPPORT_NO, 0) SUPPORT_NO");
        sql.append("  FROM T_WBS_WRITE_MEMORY T");
        sql.append(" WHERE T.GROUP_MEMORY_CODE = ? and t.deal_status=0");

        return sql.toString();
    }

    /**
     * 获取WBS读表指令，参数：内存地址组
     *
     * @return
     */
    private static String getMemoryReadSql() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("SELECT ID,");
        sql.append("       LOGIC_MEMORY_VALUE,");
        sql.append("       VPART_MEMORY_VALUE1,");
        sql.append("       VPART_MEMORY_VALUE2,");
        sql.append("       VPART_MEMORY_VALUE3,");
        sql.append("       VPART_MEMORY_VALUE4,");
        sql.append("       VPART_MEMORY_VALUE5,");
        sql.append("       VPART_MEMORY_VALUE6,");
        sql.append("       VPART_MEMORY_VALUE7,");
        sql.append("       VPART_MEMORY_VALUE8,");
        sql.append("       VPART_MEMORY_VALUE9,");
        sql.append("       VPART_MEMORY_VALUE10,");
        sql.append("       VPART_MEMORY_VALUE11,");
        sql.append("       VPART_MEMORY_VALUE12,");
        sql.append("       VPART_MEMORY_VALUE13,");
        sql.append("       NVL(T.SUPPORT_NO, 0) SUPPORT_NO");
        sql.append("  FROM T_WBS_READ_MEMORY T");
        sql.append(" WHERE T.GROUP_MEMORY_CODE = ?");

        return sql.toString();
    }

    /**
     * 更新写表内存地址值，参数：控制命令字|钢码号1-13|支持号|内存地址组编码
     *
     * @return
     */
    private static String getUpdateMemoryReadOfSupperNoSql() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("UPDATE T_WBS_READ_MEMORY");
        sql.append("   SET");
        sql.append("       LOGIC_MEMORY_VALUE     = ?,");
        sql.append("       VPART_MEMORY_VALUE1    = ?,");
        sql.append("       VPART_MEMORY_VALUE2    = ?,");
        sql.append("       VPART_MEMORY_VALUE3    = ?,");
        sql.append("       VPART_MEMORY_VALUE4    = ?,");
        sql.append("       VPART_MEMORY_VALUE5    = ?,");
        sql.append("       VPART_MEMORY_VALUE6    = ?,");
        sql.append("       VPART_MEMORY_VALUE7    = ?,");
        sql.append("       VPART_MEMORY_VALUE8    = ?,");
        sql.append("       VPART_MEMORY_VALUE9    = ?,");
        sql.append("       VPART_MEMORY_VALUE10   = ?,");
        sql.append("       VPART_MEMORY_VALUE11   = ?,");
        sql.append("       VPART_MEMORY_VALUE12   = ?,");
        sql.append("       VPART_MEMORY_VALUE13   = ?,");
        sql.append("       SUPPORT_NO             = ?");
        sql.append(" WHERE GROUP_MEMORY_CODE = ?");

        return sql.toString();
    }

    /**
     * 更新写表内存地址值，参数：控制命令字|钢码号1-13|内存地址组编码
     *
     * @return
     */
    private static String getUpdateMemoryReadSql() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("UPDATE T_WBS_READ_MEMORY");
        sql.append("   SET");
        sql.append("       LOGIC_MEMORY_VALUE     = ?,");
        sql.append("       VPART_MEMORY_VALUE1    = ?,");
        sql.append("       VPART_MEMORY_VALUE2    = ?,");
        sql.append("       VPART_MEMORY_VALUE3    = ?,");
        sql.append("       VPART_MEMORY_VALUE4    = ?,");
        sql.append("       VPART_MEMORY_VALUE5    = ?,");
        sql.append("       VPART_MEMORY_VALUE6    = ?,");
        sql.append("       VPART_MEMORY_VALUE7    = ?,");
        sql.append("       VPART_MEMORY_VALUE8    = ?,");
        sql.append("       VPART_MEMORY_VALUE9    = ?,");
        sql.append("       VPART_MEMORY_VALUE10   = ?,");
        sql.append("       VPART_MEMORY_VALUE11   = ?,");
        sql.append("       VPART_MEMORY_VALUE12   = ?,");
        sql.append("       VPART_MEMORY_VALUE13   = ?");
        sql.append(" WHERE GROUP_MEMORY_CODE = ?");

        return sql.toString();
    }

    /**
     * 获取读取内存地址组Record对象——带支撑号
     *
     * @param readBytes
     * @return
     */
    private static Record getReadRecordFromBytesOfSupperNo(byte[] readBytes) {
        Record record = new Record();

        if (readBytes.length != 30) {
            return null;
        }

        //1.控制命令字
        record.set("LOGIC_MEMORY_VALUE", ToShort(readBytes, 0));

        //2.钢码号
        record.set("VPART_MEMORY_VALUE1", ToShort(readBytes, 2));
        record.set("VPART_MEMORY_VALUE2", ToShort(readBytes, 4));
        record.set("VPART_MEMORY_VALUE3", ToShort(readBytes, 6));
        record.set("VPART_MEMORY_VALUE4", ToShort(readBytes, 8));
        record.set("VPART_MEMORY_VALUE5", ToShort(readBytes, 10));
        record.set("VPART_MEMORY_VALUE6", ToShort(readBytes, 12));
        record.set("VPART_MEMORY_VALUE7", ToShort(readBytes, 14));
        record.set("VPART_MEMORY_VALUE8", ToShort(readBytes, 16));
        record.set("VPART_MEMORY_VALUE9", ToShort(readBytes, 18));
        record.set("VPART_MEMORY_VALUE10", ToShort(readBytes, 20));
        record.set("VPART_MEMORY_VALUE11", ToShort(readBytes, 22));
        record.set("VPART_MEMORY_VALUE12", ToShort(readBytes, 24));
        record.set("VPART_MEMORY_VALUE13", ToShort(readBytes, 26));

        //3.支撑号
        record.set("SUPPORT_NO", ToShort(readBytes, 28));

        //4.获取钢码号字符串
        record.set("VPART_CODE", getVpartFromMemoryValues(record));

        return record;
    }

    /**
     * 获取读取内存地址组Record对象——不带支撑号
     *
     * @param readBytes
     * @return
     */
    private static Record getReadRecordFromBytes(byte[] readBytes) {
        Record record = new Record();

        if (readBytes.length != 28) {
            return null;
        }

        //1.控制命令字
        record.set("LOGIC_MEMORY_VALUE", ToShort(readBytes, 0));

        //2.钢码号
        record.set("VPART_MEMORY_VALUE1", ToShort(readBytes, 2));
        record.set("VPART_MEMORY_VALUE2", ToShort(readBytes, 4));
        record.set("VPART_MEMORY_VALUE3", ToShort(readBytes, 6));
        record.set("VPART_MEMORY_VALUE4", ToShort(readBytes, 8));
        record.set("VPART_MEMORY_VALUE5", ToShort(readBytes, 10));
        record.set("VPART_MEMORY_VALUE6", ToShort(readBytes, 12));
        record.set("VPART_MEMORY_VALUE7", ToShort(readBytes, 14));
        record.set("VPART_MEMORY_VALUE8", ToShort(readBytes, 16));
        record.set("VPART_MEMORY_VALUE9", ToShort(readBytes, 18));
        record.set("VPART_MEMORY_VALUE10", ToShort(readBytes, 20));
        record.set("VPART_MEMORY_VALUE11", ToShort(readBytes, 22));
        record.set("VPART_MEMORY_VALUE12", ToShort(readBytes, 24));
        record.set("VPART_MEMORY_VALUE13", ToShort(readBytes, 26));

        //4.获取钢码号字符串
        record.set("VPART_CODE", getVpartFromMemoryValues(record));

        return record;
    }

    /**
     * 获取连续写入地址byte数组——带支撑号
     *
     * @param record
     * @return
     */
    private static byte[] getWriteBytesFromRecordOfSupperNo(Record record) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(56);

        //1.控制命令字
        Short s = record.getShort("LOGIC_MEMORY_VALUE");
        byte[] bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.1.钢码号1
        s = record.getShort("VPART_MEMORY_VALUE1");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.2.钢码号2
        s = record.getShort("VPART_MEMORY_VALUE2");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.3.钢码号3
        s = record.getShort("VPART_MEMORY_VALUE3");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.4.钢码号4
        s = record.getShort("VPART_MEMORY_VALUE4");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.5.钢码号5
        s = record.getShort("VPART_MEMORY_VALUE5");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.6.钢码号6
        s = record.getShort("VPART_MEMORY_VALUE6");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.7.钢码号7
        s = record.getShort("VPART_MEMORY_VALUE7");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.8.钢码号8
        s = record.getShort("VPART_MEMORY_VALUE8");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.9.钢码号9
        s = record.getShort("VPART_MEMORY_VALUE9");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.10.钢码号10
        s = record.getShort("VPART_MEMORY_VALUE10");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.11.钢码号11
        s = record.getShort("VPART_MEMORY_VALUE11");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.12.钢码号12
        s = record.getShort("VPART_MEMORY_VALUE12");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.13.钢码号13
        s = record.getShort("VPART_MEMORY_VALUE13");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //3.支撑号
        s = record.getShort("SUPPORT_NO");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        return byteBuffer.array();
    }

    /**
     * 获取连续写入地址byte数组——不带支撑号
     *
     * @param record
     * @return
     */
    private static byte[] getWriteBytesFromRecord(Record record) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(54);

        //1.控制命令字
        Short s = record.getShort("LOGIC_MEMORY_VALUE");
        byte[] bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.1.钢码号1
        s = record.getShort("VPART_MEMORY_VALUE1");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.2.钢码号2
        s = record.getShort("VPART_MEMORY_VALUE2");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.3.钢码号3
        s = record.getShort("VPART_MEMORY_VALUE3");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.4.钢码号4
        s = record.getShort("VPART_MEMORY_VALUE4");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.5.钢码号5
        s = record.getShort("VPART_MEMORY_VALUE5");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.6.钢码号6
        s = record.getShort("VPART_MEMORY_VALUE6");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.7.钢码号7
        s = record.getShort("VPART_MEMORY_VALUE7");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.8.钢码号8
        s = record.getShort("VPART_MEMORY_VALUE8");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.9.钢码号9
        s = record.getShort("VPART_MEMORY_VALUE9");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.10.钢码号10
        s = record.getShort("VPART_MEMORY_VALUE10");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.11.钢码号11
        s = record.getShort("VPART_MEMORY_VALUE11");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.12.钢码号12
        s = record.getShort("VPART_MEMORY_VALUE12");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //2.13.钢码号13
        s = record.getShort("VPART_MEMORY_VALUE13");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        return byteBuffer.array();
    }

    /**
     * 根据内存地址值（short值），获取重保号字符串
     *
     * @param memoryRead 重保号内存地址Map
     * @return 重保号
     */
    private static String getVpartFromMemoryValues(Record memoryRead) {
        StringBuffer vparCode = new StringBuffer();
        vparCode.append(shortToStringOfByte(Short.parseShort(memoryRead.getStr("VPART_MEMORY_VALUE1"))));
        vparCode.append(shortToStringOfByte(Short.parseShort(memoryRead.getStr("VPART_MEMORY_VALUE2"))));
        vparCode.append(shortToStringOfByte(Short.parseShort(memoryRead.getStr("VPART_MEMORY_VALUE3"))));
        vparCode.append(shortToStringOfByte(Short.parseShort(memoryRead.getStr("VPART_MEMORY_VALUE4"))));
        vparCode.append(shortToStringOfByte(Short.parseShort(memoryRead.getStr("VPART_MEMORY_VALUE5"))));
        vparCode.append(shortToStringOfByte(Short.parseShort(memoryRead.getStr("VPART_MEMORY_VALUE6"))));
        vparCode.append(shortToStringOfByte(Short.parseShort(memoryRead.getStr("VPART_MEMORY_VALUE7"))));
        vparCode.append(shortToStringOfByte(Short.parseShort(memoryRead.getStr("VPART_MEMORY_VALUE8"))));
        vparCode.append(shortToStringOfByte(Short.parseShort(memoryRead.getStr("VPART_MEMORY_VALUE9"))));
        vparCode.append(shortToStringOfByte(Short.parseShort(memoryRead.getStr("VPART_MEMORY_VALUE10"))));
        vparCode.append(shortToStringOfByte(Short.parseShort(memoryRead.getStr("VPART_MEMORY_VALUE11"))));
        vparCode.append(shortToStringOfByte(Short.parseShort(memoryRead.getStr("VPART_MEMORY_VALUE12"))));
        vparCode.append(shortToStringOfByte(Short.parseShort(memoryRead.getStr("VPART_MEMORY_VALUE13"))));

        return vparCode.toString().trim();
    }

    /**
     * 根据short值，转换成Asci字符
     *
     * @param s
     * @return
     */
    private static String shortToStringOfByte(short s) {
        byte[] byteAsci = new byte[2];
        byteAsci[0] = (byte) (s >> 8);
        byteAsci[1] = (byte) (s & 0x00FF);

        return String.valueOf(new char[]{(char) byteAsci[0], (char) byteAsci[1]});
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
     * 比较
     *
     * @param recordBase 数据库值
     * @param recordRead PLC读取到值
     * @return
     */
    private static boolean isEqualsOfSupperNo(Record recordBase, Record recordRead, boolean isSupperNo) {
        if (recordBase.getShort("LOGIC_MEMORY_VALUE") != recordRead.getShort("LOGIC_MEMORY_VALUE")) {
            return false;
        }

        if (recordBase.getShort("VPART_MEMORY_VALUE1").shortValue() != recordRead.getShort("VPART_MEMORY_VALUE1").shortValue()) {
            return false;
        }

        if (recordBase.getShort("VPART_MEMORY_VALUE2").shortValue() != recordRead.getShort("VPART_MEMORY_VALUE2").shortValue()) {
            return false;
        }

        if (recordBase.getShort("VPART_MEMORY_VALUE3").shortValue() != recordRead.getShort("VPART_MEMORY_VALUE3").shortValue()) {
            return false;
        }

        if (recordBase.getShort("VPART_MEMORY_VALUE4").shortValue() != recordRead.getShort("VPART_MEMORY_VALUE4").shortValue()) {
            return false;
        }

        if (recordBase.getShort("VPART_MEMORY_VALUE5").shortValue() != recordRead.getShort("VPART_MEMORY_VALUE5").shortValue()) {
            return false;
        }

        if (recordBase.getShort("VPART_MEMORY_VALUE6").shortValue() != recordRead.getShort("VPART_MEMORY_VALUE6").shortValue()) {
            return false;
        }

        if (recordBase.getShort("VPART_MEMORY_VALUE7").shortValue() != recordRead.getShort("VPART_MEMORY_VALUE7").shortValue()) {
            return false;
        }

        if (recordBase.getShort("VPART_MEMORY_VALUE8").shortValue() != recordRead.getShort("VPART_MEMORY_VALUE8").shortValue()) {
            return false;
        }

        if (recordBase.getShort("VPART_MEMORY_VALUE9").shortValue() != recordRead.getShort("VPART_MEMORY_VALUE9").shortValue()) {
            return false;
        }

        if (recordBase.getShort("VPART_MEMORY_VALUE10").shortValue() != recordRead.getShort("VPART_MEMORY_VALUE10").shortValue()) {
            return false;
        }

        if (recordBase.getShort("VPART_MEMORY_VALUE11").shortValue() != recordRead.getShort("VPART_MEMORY_VALUE11").shortValue()) {
            return false;
        }

        if (recordBase.getShort("VPART_MEMORY_VALUE12").shortValue() != recordRead.getShort("VPART_MEMORY_VALUE12").shortValue()) {
            return false;
        }

        if (recordBase.getShort("VPART_MEMORY_VALUE13").shortValue() != recordRead.getShort("VPART_MEMORY_VALUE13").shortValue()) {
            return false;
        }

        if (isSupperNo) {
            if (recordBase.getShort("SUPPORT_NO").shortValue() != recordRead.getShort("SUPPORT_NO").shortValue()) {
                return false;
            }
        }

        return true;
    }
}

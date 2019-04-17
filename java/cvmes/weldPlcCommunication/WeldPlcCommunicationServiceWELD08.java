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
 * 焊装主线MB170——PLC通讯服务
 */
public class WeldPlcCommunicationServiceWELD08 extends PlcSiemens {

    int dbCode;//20

    @Override
    public void initServiceCode() {
        this.strServiceCode = "WeldPlc08";
    }

    @Override
    public void initPlc(Record rec_service) {
        this.plctype = PlcType.SiemensS300;
        this.ip = rec_service.getStr("SERVICE_PARA1_VALUE");
        this.port = Integer.parseInt(rec_service.getStr("SERVICE_PARA2_VALUE"));
        this.dbCode = Integer.parseInt(rec_service.getStr("SERVICE_PARA3_VALUE"));
    }

    @Override
    public String runBll(Record rec_service) throws Exception {
        Date start = new Date();

        if (rec_service.getStr("SERVICE_PARA4_VALUE").equals("1")) {
            Log.Write(strServiceCode, LogLevel.Information, String.format("开始同步[%s]", start));
        }

        int ngReadCount = 0;
        int okReadCount = 0;
        int ngWriteCount = 0;
        int okWriteCount = 0;
        int readCount = 1;
        int writeCount = 0;

        Record ret;

        //1.获取同步——MB170计划
        ret = syncMemoryMB170(dbCode);
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

    private Record syncMemoryMB170(int dbCode) {

        String msg = "";
        Record recordMsg = new Record().set("isWrite", false).set("isWriteSucess", false).set("isReadSucess", false).set("msg", "");

        //内存地址组
        String groupMemoryCode = "MB170.around";

        int len = 64;

        //0.获取读表值
        List<Record> list_read = Db.find(getMemoryReadMB170Sql(), groupMemoryCode);
        if (list_read.size() != 1) {
            msg = String.format("获取读表信息异常，写表存在多个配置或者配置不存在，内存地址组[%s],配置个数[%s]", groupMemoryCode, list_read.size());
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("isWrite", false);
            return recordMsg;
        }

        //1.获取写表未处理指令
        List<Record> list_write = Db.find(getMemoryWriteMB170Sql(), groupMemoryCode);

        //1.1.写表未处理指令异常
        if (list_write.size() > 1) {
            msg = String.format("获取待处理指令异常，写表存在多个指令，内存地址组[%s],待处理指令个数[%s]", groupMemoryCode, list_write.size());
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("isWrite", true);
        }

        //1.2.存在待处理指令
        if (list_write.size() == 1) {
            //1.2.1.获取写第一组数据，在线离线状态(只读，不写)

            //1.2.2.获取第二组数据，机器人焊接代码
            byte[] writeBytes = getRobitWriteBytesFromRecordMB170(list_write.get(0));

            //1.2.2.1.写第二组数据
            boolean ret = Write(dbCode, 4, writeBytes);

            if (!ret) {
                msg = String.format("写入机器人焊接代码到PLC失败，指令数据[%s]", list_write.get(0).toJson());
                Log.Write(strServiceCode, LogLevel.Error, msg);
                recordMsg.set("isWrite", true).set("isWriteSucess", false).set("msg", msg);
            }

            //1.2.3 获取第三组数据，车身钢码
            writeBytes = getVpartWriteBytesFromRecordMB170(list_write.get(0));

            //1.2.3.1 写第三组数据
            ret = Write(dbCode, 70, writeBytes);

            if (!ret) {
                msg = String.format("写入车身钢码到PLC失败，指令数据[%s]", list_write.get(0).toJson());
                Log.Write(strServiceCode, LogLevel.Error, msg);
                recordMsg.set("isWrite", true).set("isWriteSucess", false).set("msg", msg);
            }

            //1.2.4.1.获取写第四组数据，控制命令字
            writeBytes = shortToBytes(Short.parseShort(list_write.get(0).getStr("LOGIC_VALUE")));

            //1.2.3.2.写第三组数据
            ret = Write(dbCode, 0, writeBytes);
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
        //1.3.1.读取第一组数据，（控制命令字，plc自动状态标记，机器人焊接代码）
        byte[] readBytes = Read(dbCode, 0, len);
        if (readBytes == null) {
            msg = String.format("读取内存值失败，内存地址组[%s]", groupMemoryCode);
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("isReadSucess", false).set("msg", msg);

            return recordMsg;
        }

        //1.3.1.解析内存地址值
        Record recordRead = getReadRecordFromBytesMB170(readBytes);

        //1.3.2.读取第二组数据，车身钢码号
        readBytes = Read(dbCode, 70, 26);
        if (readBytes == null) {
            msg = String.format("读取车身钢码号和前置车身刚码号内存值失败，内存地址组[%s]", groupMemoryCode);
            Log.Write(strServiceCode, LogLevel.Error, msg);
            recordMsg.set("isReadSucess", false).set("msg", msg);

            return recordMsg;
        }

        //1.3.2.1.解析刚码号
        recordRead = getVpartReadRecordFromBytesMB170(readBytes, recordRead);

        //1.3.3.更新内存读表
        int ret = Db.update(getUpdateMemoryReadMB170Sql(),
                recordRead.getStr("LOGIC_VALUE"),
                recordRead.getStr("AUTOMATISM"),
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
                recordRead.getInt("ROBOT_MEMORY_VALUE1"),
                recordRead.getInt("ROBOT_MEMORY_VALUE2"),
                recordRead.getInt("ROBOT_MEMORY_VALUE3"),
                recordRead.getInt("ROBOT_MEMORY_VALUE4"),
                recordRead.getInt("ROBOT_MEMORY_VALUE5"),
                recordRead.getInt("ROBOT_MEMORY_VALUE6"),
                recordRead.getInt("ROBOT_MEMORY_VALUE7"),
                recordRead.getInt("ROBOT_MEMORY_VALUE8"),
                recordRead.getInt("ROBOT_MEMORY_VALUE9"),
                recordRead.getInt("ROBOT_MEMORY_VALUE10"),
                recordRead.getInt("ROBOT_MEMORY_VALUE11"),
                recordRead.getInt("ROBOT_MEMORY_VALUE12"),
                recordRead.getInt("ROBOT_MEMORY_VALUE13"),
                recordRead.getInt("ROBOT_MEMORY_VALUE14"),
                recordRead.getInt("ROBOT_MEMORY_VALUE15"),
                recordRead.getInt("ROBOT_MEMORY_VALUE16"),
                recordRead.getInt("ROBOT_MEMORY_VALUE17"),
                recordRead.getInt("ROBOT_MEMORY_VALUE18"),
                recordRead.getInt("ROBOT_MEMORY_VALUE19"),
                recordRead.getInt("ROBOT_MEMORY_VALUE20"),
                recordRead.getInt("ROBOT_MEMORY_VALUE21"),
                recordRead.getInt("ROBOT_MEMORY_VALUE22"),
                recordRead.getInt("ROBOT_MEMORY_VALUE23"),
                recordRead.getInt("ROBOT_MEMORY_VALUE24"),
                recordRead.getInt("ROBOT_MEMORY_VALUE25"),
                recordRead.getInt("ROBOT_MEMORY_VALUE26"),
                recordRead.getInt("ROBOT_MEMORY_VALUE27"),
                recordRead.getInt("ROBOT_MEMORY_VALUE28"),
                recordRead.getInt("ROBOT_MEMORY_VALUE29"),
                recordRead.getInt("ROBOT_MEMORY_VALUE30"),
                recordRead.getStr("VPART_CODE"),
                recordRead.getStr("ROBOT_CODE"),
                groupMemoryCode
        );

        if (!isEqualsBack(list_read.get(0), recordRead)) {
            msg = String.format("获取内存地址值成功，内存地址组[%s],钢码号[%s],控制命令字[%s],指令数据[%s]",
                    groupMemoryCode,
                    recordRead.getStr("VPART_CODE"),
                    recordRead.getStr("LOGIC_VALUE"),
                    recordRead.toJson());
            Log.Write(strServiceCode, LogLevel.Information, msg);
        }

        recordMsg.set("isReadSucess", true);
        return recordMsg;
    }

    /**
     *  解析内存地址组 车身钢码
     * @param readBytes
     * @param record
     * @return
     */
    private Record getVpartReadRecordFromBytesMB170(byte[] readBytes, Record record) {

        if (readBytes.length != 26) {
            return null;
        }

        //1.钢码号
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

        //2.获取钢码号字符串
        int len = 26;
        byte[] tmpbytes = new byte[len];
        System.arraycopy(readBytes, 0, tmpbytes, 0, len);
        record.set("VPART_CODE", getVpartFromMemoryValues(tmpbytes));

        return record;
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
     * 获取读取内存地址组Record对象
     *
     * @param readBytes
     * @return
     */
    private Record getReadRecordFromBytesMB170(byte[] readBytes) {
        Record record = new Record();

        if (readBytes.length != 64) {
            return null;
        }

        //1.控制命令字
        record.set("LOGIC_VALUE", ToShort(readBytes, 0));

        //2.在线离线标记
        record.set("AUTOMATISM", ToShort(readBytes, 2));

        //3.机器人代码
        record.set("ROBOT_MEMORY_VALUE1", ToShort(readBytes, 4));
        record.set("ROBOT_MEMORY_VALUE2", ToShort(readBytes, 6));
        record.set("ROBOT_MEMORY_VALUE3", ToShort(readBytes, 8));
        record.set("ROBOT_MEMORY_VALUE4", ToShort(readBytes, 10));
        record.set("ROBOT_MEMORY_VALUE5", ToShort(readBytes, 12));
        record.set("ROBOT_MEMORY_VALUE6", ToShort(readBytes, 14));
        record.set("ROBOT_MEMORY_VALUE7", ToShort(readBytes, 16));
        record.set("ROBOT_MEMORY_VALUE8", ToShort(readBytes, 18));
        record.set("ROBOT_MEMORY_VALUE9", ToShort(readBytes, 20));
        record.set("ROBOT_MEMORY_VALUE10", ToShort(readBytes, 22));
        record.set("ROBOT_MEMORY_VALUE11", ToShort(readBytes, 24));
        record.set("ROBOT_MEMORY_VALUE12", ToShort(readBytes, 26));
        record.set("ROBOT_MEMORY_VALUE13", ToShort(readBytes, 28));
        record.set("ROBOT_MEMORY_VALUE14", ToShort(readBytes, 30));
        record.set("ROBOT_MEMORY_VALUE15", ToShort(readBytes, 32));
        record.set("ROBOT_MEMORY_VALUE16", ToShort(readBytes, 34));
        record.set("ROBOT_MEMORY_VALUE17", ToShort(readBytes, 36));
        record.set("ROBOT_MEMORY_VALUE18", ToShort(readBytes, 38));
        record.set("ROBOT_MEMORY_VALUE19", ToShort(readBytes, 40));
        record.set("ROBOT_MEMORY_VALUE20", ToShort(readBytes, 42));
        record.set("ROBOT_MEMORY_VALUE21", ToShort(readBytes, 44));
        record.set("ROBOT_MEMORY_VALUE22", ToShort(readBytes, 46));
        record.set("ROBOT_MEMORY_VALUE23", ToShort(readBytes, 48));
        record.set("ROBOT_MEMORY_VALUE24", ToShort(readBytes, 50));
        record.set("ROBOT_MEMORY_VALUE25", ToShort(readBytes, 52));
        record.set("ROBOT_MEMORY_VALUE26", ToShort(readBytes, 54));
        record.set("ROBOT_MEMORY_VALUE27", ToShort(readBytes, 56));
        record.set("ROBOT_MEMORY_VALUE28", ToShort(readBytes, 58));
        record.set("ROBOT_MEMORY_VALUE29", ToShort(readBytes, 60));
        record.set("ROBOT_MEMORY_VALUE30", ToShort(readBytes, 62));

        //4.获取机器人代码字符串
        int len = 60;
        byte[] tmpbytes = new byte[len];
        System.arraycopy(readBytes, 4, tmpbytes, 0, len);
        record.set("ROBOT_CODE", getRobotCodeFromMemoryValues(tmpbytes));

        return record;
    }


    /**
     * 根据内存地址值(bytes),获取机器人代码字符串
     *
     * @param bytes 重机器人代码内存地址bytes
     * @return
     */
    private static String getRobotCodeFromMemoryValues(byte[] bytes) {
        return new String(bytes).trim();
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
     * 根据指令对象，获取指令bytes(车身钢码)
     *
     * @param memoryWrite
     * @return
     */
    private byte[] getVpartWriteBytesFromRecordMB170(Record memoryWrite) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(26);

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

        return byteBuffer.array();
    }

    /**
     * 根据指令对象，获取指令bytes(机器人焊接代码)
     *
     * @param memoryWrite
     * @return
     */
    private byte[] getRobitWriteBytesFromRecordMB170(Record memoryWrite) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(60);

        //1.1.机器人焊接代码1
        Short s = memoryWrite.getShort("ROBOT_MEMORY_VALUE1");
        byte[] bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //4.2.机器人代码2
        s = memoryWrite.getShort("ROBOT_MEMORY_VALUE2");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //4.3.机器人代码3
        s = memoryWrite.getShort("ROBOT_MEMORY_VALUE3");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //4.4.机器人代码4
        s = memoryWrite.getShort("ROBOT_MEMORY_VALUE4");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //4.5.机器人代码5
        s = memoryWrite.getShort("ROBOT_MEMORY_VALUE5");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //4.6.机器人代码6
        s = memoryWrite.getShort("ROBOT_MEMORY_VALUE6");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //4.7.机器人代码7
        s = memoryWrite.getShort("ROBOT_MEMORY_VALUE7");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //4.8.机器人代码8
        s = memoryWrite.getShort("ROBOT_MEMORY_VALUE8");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //4.9.机器人代码9
        s = memoryWrite.getShort("ROBOT_MEMORY_VALUE9");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //4.10.机器人代码10
        s = memoryWrite.getShort("ROBOT_MEMORY_VALUE10");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //4.11.机器人代码11
        s = memoryWrite.getShort("ROBOT_MEMORY_VALUE11");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //4.12.机器人代码12
        s = memoryWrite.getShort("ROBOT_MEMORY_VALUE12");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //4.13.机器人代码13
        s = memoryWrite.getShort("ROBOT_MEMORY_VALUE13");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //4.14.机器人代码14
        s = memoryWrite.getShort("ROBOT_MEMORY_VALUE14");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //4.15.机器人代码15
        s = memoryWrite.getShort("ROBOT_MEMORY_VALUE15");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //4.16.机器人代码16
        s = memoryWrite.getShort("ROBOT_MEMORY_VALUE16");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //4.17.机器人代码17
        s = memoryWrite.getShort("ROBOT_MEMORY_VALUE17");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //4.18.机器人代码18
        s = memoryWrite.getShort("ROBOT_MEMORY_VALUE18");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //4.19.机器人代码19
        s = memoryWrite.getShort("ROBOT_MEMORY_VALUE19");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //4.20.机器人代码20
        s = memoryWrite.getShort("ROBOT_MEMORY_VALUE20");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //4.21.机器人代码21
        s = memoryWrite.getShort("ROBOT_MEMORY_VALUE21");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //4.22.机器人代码22
        s = memoryWrite.getShort("ROBOT_MEMORY_VALUE22");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //4.23.机器人代码23
        s = memoryWrite.getShort("ROBOT_MEMORY_VALUE23");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //4.24.机器人代码24
        s = memoryWrite.getShort("ROBOT_MEMORY_VALUE24");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //4.25.机器人代码25
        s = memoryWrite.getShort("ROBOT_MEMORY_VALUE25");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //4.26.机器人代码26
        s = memoryWrite.getShort("ROBOT_MEMORY_VALUE26");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //4.27.机器人代码27
        s = memoryWrite.getShort("ROBOT_MEMORY_VALUE27");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //4.28.机器人代码28
        s = memoryWrite.getShort("ROBOT_MEMORY_VALUE28");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //4.29.机器人代码29
        s = memoryWrite.getShort("ROBOT_MEMORY_VALUE29");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        //4.30.机器人代码30
        s = memoryWrite.getShort("ROBOT_MEMORY_VALUE30");
        bytes = shortToBytes(s);
        byteBuffer.put(bytes);

        return byteBuffer.array();
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
     * 获取焊装主线MB170写表指令，参数：内存地址组
     *
     * @return
     */
    public String getMemoryReadMB170Sql() {
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
        sql.append("       T.ROBOT_MEMORY_VALUE1,");
        sql.append("       T.ROBOT_MEMORY_VALUE2,");
        sql.append("       T.ROBOT_MEMORY_VALUE3,");
        sql.append("       T.ROBOT_MEMORY_VALUE4,");
        sql.append("       T.ROBOT_MEMORY_VALUE5,");
        sql.append("       T.ROBOT_MEMORY_VALUE6,");
        sql.append("       T.ROBOT_MEMORY_VALUE7,");
        sql.append("       T.ROBOT_MEMORY_VALUE8,");
        sql.append("       T.ROBOT_MEMORY_VALUE9,");
        sql.append("       T.ROBOT_MEMORY_VALUE10,");
        sql.append("       T.ROBOT_MEMORY_VALUE11,");
        sql.append("       T.ROBOT_MEMORY_VALUE12,");
        sql.append("       T.ROBOT_MEMORY_VALUE13,");
        sql.append("       T.ROBOT_MEMORY_VALUE14,");
        sql.append("       T.ROBOT_MEMORY_VALUE15,");
        sql.append("       T.ROBOT_MEMORY_VALUE16,");
        sql.append("       T.ROBOT_MEMORY_VALUE17,");
        sql.append("       T.ROBOT_MEMORY_VALUE18,");
        sql.append("       T.ROBOT_MEMORY_VALUE19,");
        sql.append("       T.ROBOT_MEMORY_VALUE20,");
        sql.append("       T.ROBOT_MEMORY_VALUE21,");
        sql.append("       T.ROBOT_MEMORY_VALUE22,");
        sql.append("       T.ROBOT_MEMORY_VALUE23,");
        sql.append("       T.ROBOT_MEMORY_VALUE24,");
        sql.append("       T.ROBOT_MEMORY_VALUE25,");
        sql.append("       T.ROBOT_MEMORY_VALUE26,");
        sql.append("       T.ROBOT_MEMORY_VALUE27,");
        sql.append("       T.ROBOT_MEMORY_VALUE28,");
        sql.append("       T.ROBOT_MEMORY_VALUE29,");
        sql.append("       T.ROBOT_MEMORY_VALUE30,");
        sql.append("       T.VPART_CODE,");
        sql.append("       T.ROBOT_CODE");
        sql.append("  FROM T_DEVICE_WELD_READ_RAM T");
        sql.append(" WHERE T.GROUP_MEMORY_CODE = ?");

        return sql.toString();
    }

    /**
     * 获取焊装主线MB170写表指令，参数：内存地址组
     *
     * @return
     */
    public String getMemoryWriteMB170Sql() {
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
        sql.append("       T.ROBOT_MEMORY_VALUE1,");
        sql.append("       T.ROBOT_MEMORY_VALUE2,");
        sql.append("       T.ROBOT_MEMORY_VALUE3,");
        sql.append("       T.ROBOT_MEMORY_VALUE4,");
        sql.append("       T.ROBOT_MEMORY_VALUE5,");
        sql.append("       T.ROBOT_MEMORY_VALUE6,");
        sql.append("       T.ROBOT_MEMORY_VALUE7,");
        sql.append("       T.ROBOT_MEMORY_VALUE8,");
        sql.append("       T.ROBOT_MEMORY_VALUE9,");
        sql.append("       T.ROBOT_MEMORY_VALUE10,");
        sql.append("       T.ROBOT_MEMORY_VALUE11,");
        sql.append("       T.ROBOT_MEMORY_VALUE12,");
        sql.append("       T.ROBOT_MEMORY_VALUE13,");
        sql.append("       T.ROBOT_MEMORY_VALUE14,");
        sql.append("       T.ROBOT_MEMORY_VALUE15,");
        sql.append("       T.ROBOT_MEMORY_VALUE16,");
        sql.append("       T.ROBOT_MEMORY_VALUE17,");
        sql.append("       T.ROBOT_MEMORY_VALUE18,");
        sql.append("       T.ROBOT_MEMORY_VALUE19,");
        sql.append("       T.ROBOT_MEMORY_VALUE20,");
        sql.append("       T.ROBOT_MEMORY_VALUE21,");
        sql.append("       T.ROBOT_MEMORY_VALUE22,");
        sql.append("       T.ROBOT_MEMORY_VALUE23,");
        sql.append("       T.ROBOT_MEMORY_VALUE24,");
        sql.append("       T.ROBOT_MEMORY_VALUE25,");
        sql.append("       T.ROBOT_MEMORY_VALUE26,");
        sql.append("       T.ROBOT_MEMORY_VALUE27,");
        sql.append("       T.ROBOT_MEMORY_VALUE28,");
        sql.append("       T.ROBOT_MEMORY_VALUE29,");
        sql.append("       T.ROBOT_MEMORY_VALUE30,");
        sql.append("       T.VPART_CODE,");
        sql.append("       T.ROBOT_CODE");
        sql.append("  FROM T_DEVICE_WELD_WRITE_RAM T");
        sql.append(" WHERE T.GROUP_MEMORY_CODE = ?  AND T.DEAL_STATUS = 0");

        return sql.toString();
    }

    /**
     * 更新写表内存地址值，参数：控制命令字|钢码号1-13|支持号|内存地址组编码
     *
     * @return
     */
    public String getUpdateMemoryReadMB170Sql() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("UPDATE T_DEVICE_WELD_READ_RAM T");
        sql.append("   SET T.LOGIC_VALUE          = ?,");
        sql.append("       T.AUTOMATISM           = ?,");
        sql.append("       T.VPART_MEMORY_VALUE1  = ?,");
        sql.append("       T.VPART_MEMORY_VALUE2  = ?,");
        sql.append("       T.VPART_MEMORY_VALUE3  = ?,");
        sql.append("       T.VPART_MEMORY_VALUE4  = ?,");
        sql.append("       T.VPART_MEMORY_VALUE5  = ?,");
        sql.append("       T.VPART_MEMORY_VALUE6  = ?,");
        sql.append("       T.VPART_MEMORY_VALUE7  = ?,");
        sql.append("       T.VPART_MEMORY_VALUE8  = ?,");
        sql.append("       T.VPART_MEMORY_VALUE9  = ?,");
        sql.append("       T.VPART_MEMORY_VALUE10 = ?,");
        sql.append("       T.VPART_MEMORY_VALUE11 = ?,");
        sql.append("       T.VPART_MEMORY_VALUE12 = ?,");
        sql.append("       T.VPART_MEMORY_VALUE13 = ?,");
        sql.append("       T.ROBOT_MEMORY_VALUE1  = ?,");
        sql.append("       T.ROBOT_MEMORY_VALUE2  = ?,");
        sql.append("       T.ROBOT_MEMORY_VALUE3  = ?,");
        sql.append("       T.ROBOT_MEMORY_VALUE4  = ?,");
        sql.append("       T.ROBOT_MEMORY_VALUE5  = ?,");
        sql.append("       T.ROBOT_MEMORY_VALUE6  = ?,");
        sql.append("       T.ROBOT_MEMORY_VALUE7  = ?,");
        sql.append("       T.ROBOT_MEMORY_VALUE8  = ?,");
        sql.append("       T.ROBOT_MEMORY_VALUE9  = ?,");
        sql.append("       T.ROBOT_MEMORY_VALUE10 = ?,");
        sql.append("       T.ROBOT_MEMORY_VALUE11 = ?,");
        sql.append("       T.ROBOT_MEMORY_VALUE12 = ?,");
        sql.append("       T.ROBOT_MEMORY_VALUE13 = ?,");
        sql.append("       T.ROBOT_MEMORY_VALUE14 = ?,");
        sql.append("       T.ROBOT_MEMORY_VALUE15 = ?,");
        sql.append("       T.ROBOT_MEMORY_VALUE16 = ?,");
        sql.append("       T.ROBOT_MEMORY_VALUE17 = ?,");
        sql.append("       T.ROBOT_MEMORY_VALUE18 = ?,");
        sql.append("       T.ROBOT_MEMORY_VALUE19 = ?,");
        sql.append("       T.ROBOT_MEMORY_VALUE20 = ?,");
        sql.append("       T.ROBOT_MEMORY_VALUE21 = ?,");
        sql.append("       T.ROBOT_MEMORY_VALUE22 = ?,");
        sql.append("       T.ROBOT_MEMORY_VALUE23 = ?,");
        sql.append("       T.ROBOT_MEMORY_VALUE24 = ?,");
        sql.append("       T.ROBOT_MEMORY_VALUE25 = ?,");
        sql.append("       T.ROBOT_MEMORY_VALUE26 = ?,");
        sql.append("       T.ROBOT_MEMORY_VALUE27 = ?,");
        sql.append("       T.ROBOT_MEMORY_VALUE28 = ?,");
        sql.append("       T.ROBOT_MEMORY_VALUE29 = ?,");
        sql.append("       T.ROBOT_MEMORY_VALUE30 = ?,");
        sql.append("       T.VPART_CODE           = ?,");
        sql.append("       T.ROBOT_CODE           = ?");
        sql.append(" WHERE T.GROUP_MEMORY_CODE = ?");

        return sql.toString();
    }


    /**
     * 右围内存读表值与设备内存值比较
     *
     * @param recordRead       内存读表值
     * @param recordMemoryRead 设备内存值
     * @return
     */
    private static boolean isEqualsBack(Record recordMemoryRead, Record recordRead) {

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
        if (!recordRead.getStr("ROBOT_MEMORY_VALUE1").equals(recordMemoryRead.getStr("ROBOT_MEMORY_VALUE1"))) {
            return false;
        }
        if (!recordRead.getStr("ROBOT_MEMORY_VALUE2").equals(recordMemoryRead.getStr("ROBOT_MEMORY_VALUE2"))) {
            return false;
        }
        if (!recordRead.getStr("ROBOT_MEMORY_VALUE3").equals(recordMemoryRead.getStr("ROBOT_MEMORY_VALUE3"))) {
            return false;
        }
        if (!recordRead.getStr("ROBOT_MEMORY_VALUE4").equals(recordMemoryRead.getStr("ROBOT_MEMORY_VALUE4"))) {
            return false;
        }
        if (!recordRead.getStr("ROBOT_MEMORY_VALUE5").equals(recordMemoryRead.getStr("ROBOT_MEMORY_VALUE5"))) {
            return false;
        }
        if (!recordRead.getStr("ROBOT_MEMORY_VALUE6").equals(recordMemoryRead.getStr("ROBOT_MEMORY_VALUE6"))) {
            return false;
        }
        if (!recordRead.getStr("ROBOT_MEMORY_VALUE7").equals(recordMemoryRead.getStr("ROBOT_MEMORY_VALUE7"))) {
            return false;
        }
        if (!recordRead.getStr("ROBOT_MEMORY_VALUE8").equals(recordMemoryRead.getStr("ROBOT_MEMORY_VALUE8"))) {
            return false;
        }
        if (!recordRead.getStr("ROBOT_MEMORY_VALUE9").equals(recordMemoryRead.getStr("ROBOT_MEMORY_VALUE9"))) {
            return false;
        }
        if (!recordRead.getStr("ROBOT_MEMORY_VALUE10").equals(recordMemoryRead.getStr("ROBOT_MEMORY_VALUE10"))) {
            return false;
        }
        if (!recordRead.getStr("ROBOT_MEMORY_VALUE11").equals(recordMemoryRead.getStr("ROBOT_MEMORY_VALUE11"))) {
            return false;
        }
        if (!recordRead.getStr("ROBOT_MEMORY_VALUE12").equals(recordMemoryRead.getStr("ROBOT_MEMORY_VALUE12"))) {
            return false;
        }
        if (!recordRead.getStr("ROBOT_MEMORY_VALUE13").equals(recordMemoryRead.getStr("ROBOT_MEMORY_VALUE13"))) {
            return false;
        }
        if (!recordRead.getStr("ROBOT_MEMORY_VALUE14").equals(recordMemoryRead.getStr("ROBOT_MEMORY_VALUE14"))) {
            return false;
        }
        if (!recordRead.getStr("ROBOT_MEMORY_VALUE15").equals(recordMemoryRead.getStr("ROBOT_MEMORY_VALUE15"))) {
            return false;
        }
        if (!recordRead.getStr("ROBOT_MEMORY_VALUE16").equals(recordMemoryRead.getStr("ROBOT_MEMORY_VALUE16"))) {
            return false;
        }
        if (!recordRead.getStr("ROBOT_MEMORY_VALUE17").equals(recordMemoryRead.getStr("ROBOT_MEMORY_VALUE17"))) {
            return false;
        }
        if (!recordRead.getStr("ROBOT_MEMORY_VALUE18").equals(recordMemoryRead.getStr("ROBOT_MEMORY_VALUE18"))) {
            return false;
        }
        if (!recordRead.getStr("ROBOT_MEMORY_VALUE19").equals(recordMemoryRead.getStr("ROBOT_MEMORY_VALUE19"))) {
            return false;
        }
        if (!recordRead.getStr("ROBOT_MEMORY_VALUE20").equals(recordMemoryRead.getStr("ROBOT_MEMORY_VALUE20"))) {
            return false;
        }
        if (!recordRead.getStr("ROBOT_MEMORY_VALUE21").equals(recordMemoryRead.getStr("ROBOT_MEMORY_VALUE21"))) {
            return false;
        }
        if (!recordRead.getStr("ROBOT_MEMORY_VALUE22").equals(recordMemoryRead.getStr("ROBOT_MEMORY_VALUE22"))) {
            return false;
        }
        if (!recordRead.getStr("ROBOT_MEMORY_VALUE23").equals(recordMemoryRead.getStr("ROBOT_MEMORY_VALUE23"))) {
            return false;
        }
        if (!recordRead.getStr("ROBOT_MEMORY_VALUE24").equals(recordMemoryRead.getStr("ROBOT_MEMORY_VALUE24"))) {
            return false;
        }
        if (!recordRead.getStr("ROBOT_MEMORY_VALUE25").equals(recordMemoryRead.getStr("ROBOT_MEMORY_VALUE25"))) {
            return false;
        }
        if (!recordRead.getStr("ROBOT_MEMORY_VALUE26").equals(recordMemoryRead.getStr("ROBOT_MEMORY_VALUE26"))) {
            return false;
        }
        if (!recordRead.getStr("ROBOT_MEMORY_VALUE27").equals(recordMemoryRead.getStr("ROBOT_MEMORY_VALUE27"))) {
            return false;
        }
        if (!recordRead.getStr("ROBOT_MEMORY_VALUE28").equals(recordMemoryRead.getStr("ROBOT_MEMORY_VALUE28"))) {
            return false;
        }
        if (!recordRead.getStr("ROBOT_MEMORY_VALUE29").equals(recordMemoryRead.getStr("ROBOT_MEMORY_VALUE29"))) {
            return false;
        }
        if (!recordRead.getStr("ROBOT_MEMORY_VALUE30").equals(recordMemoryRead.getStr("ROBOT_MEMORY_VALUE30"))) {
            return false;
        }

        return true;
    }




}

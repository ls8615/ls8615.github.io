package cvmes.common;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;
import cvmes.CvmesService;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

public abstract class PlcSiemens extends Thread {
    // 服务编码
    public String strServiceCode;
    // PLC类型
    public PlcType plctype;
    // IP地址
    public String ip;
    // 端口
    public int port;

    //最后操作信息
    private String msg = "";

    // 连接状态标记
    private boolean flag = false;

    // Socket对象
    private Socket socket = null;

    /**
     * 抽象初始化服务编码方法
     */
    public abstract void initServiceCode();

    /**
     * 抽象初始化PLC方法
     */
    public abstract void initPlc(Record rec_service);

    /**
     * 抽象业务逻辑方法
     *
     * @param rec_service
     */
    public abstract String runBll(Record rec_service) throws Exception;

    /**
     * 从buf中获取字符串
     *
     * @param buf
     * @param cutpos 偏移量
     * @param cutlen 长度
     * @return
     */
    public static String BufGetString(byte[] buf, int cutpos, int cutlen) {
        byte[] v = new byte[cutlen];
        System.arraycopy(buf, cutpos, v, 0, cutlen);
        return new String(v).trim();
    }

    /**
     * 从buf中获取short
     *
     * @param buf
     * @param cutpos 偏移量
     * @return
     */
    public static short BufGetShort(byte[] buf, int cutpos) {
        byte[] v = new byte[2];
        System.arraycopy(buf, cutpos, v, 0, 2);
        return (short) (((v[0] & 0xff) << 8) | ((v[1] & 0xff) << 0));
    }

    /**
     * 从buf中获取位
     *
     * @param buf
     * @param cutpos 偏移量
     * @param cutbit 位
     * @return
     */
    public static int BufGetBit(byte[] buf, int cutpos, int cutbit) {
        byte[] v = new byte[1];
        System.arraycopy(buf, cutpos, v, 0, 1);
        return (v[0] >> cutbit) & 0x01;
    }

    /**
     * 从buf中获取int
     *
     * @param buf
     * @param cutpos 偏移量
     * @return
     */
    public static int BufGetInt(byte[] buf, int cutpos) {
        byte[] v = new byte[4];
        System.arraycopy(buf, cutpos, v, 0, 4);
        return (int) (((v[0] & 0xff) << 24) | ((v[1] & 0xff) << 16) | ((v[2] & 0xff) << 8) | ((v[3] & 0xff) << 0));
    }

    /**
     * 从buf中获取float
     *
     * @param buf
     * @param cutpos 偏移量
     * @return
     */
    public static float BufGetFloat(byte[] buf, int cutpos) {
        Integer ret = BufGetInt(buf, cutpos);
        return Float.intBitsToFloat(ret);
    }

    /**
     * 写入Short
     *
     * @param dbcode DB块编号
     * @param dbpos  偏移量（起始地址）
     * @param value  要写入的Short值
     * @return
     */
    public boolean WriteShort(int dbcode, int dbpos, short value) {
        byte[] buf = new byte[2];
        buf[0] = (byte) ((value >> 8) & 0xff);
        buf[1] = (byte) (value & 0xff);

        return Write(dbcode, dbpos, buf);
    }

    /**
     * 读取字符串
     *
     * @param dbcode DB块编号
     * @param dbpos  偏移量（起始地址）
     * @param len    字符串长度
     * @return 读取到的字符串
     */
    public String ReadString(int dbcode, int dbpos, int len) {
        byte[] buf = Read(dbcode, dbpos, len);
        if (buf == null) return null;

        return new String(buf).trim();
    }

    /**
     * 读取Float
     *
     * @param dbcode DB块编号
     * @param dbpos  偏移量（起始地址）
     * @return 读取到的Float值
     */
    public Float ReadFloat(int dbcode, int dbpos) {
        Integer ret = ReadInt(dbcode, dbpos);
        if (ret == null) return null;

        return Float.intBitsToFloat(ret);
    }

    /**
     * 读取Int
     *
     * @param dbcode DB块编号
     * @param dbpos  偏移量（起始地址）
     * @return 读取到的Int值
     */
    public Integer ReadInt(int dbcode, int dbpos) {
        byte[] buf = Read(dbcode, dbpos, 4);
        if (buf == null) return null;

        return (int) (((buf[0] & 0xff) << 24) | ((buf[1] & 0xff) << 16) | ((buf[2] & 0xff) << 8) | ((buf[3] & 0xff) << 0));
    }

    /**
     * 读取Short
     *
     * @param dbcode DB块编号
     * @param dbpos  偏移量（起始地址）
     * @return 读取到的Short值
     */
    public Short ReadShort(int dbcode, int dbpos) {
        byte[] buf = Read(dbcode, dbpos, 2);
        if (buf == null) return null;

        return (short) (((buf[0] & 0xff) << 8) | ((buf[1] & 0xff) << 0));
    }

    /**
     * 读取Bit
     *
     * @param dbcode DB块编号
     * @param dbpos  偏移量（起始地址）
     * @param bitpos 位编码
     * @return 位值
     */
    public Integer ReadBit(int dbcode, int dbpos, int bitpos) {
        byte[] buf = Read(dbcode, dbpos, 1);
        if (buf == null) return null;

        return (buf[0] >> bitpos) & 0x01;
    }

    /**
     * 写入PLC数据
     *
     * @param dbcode DB块编号
     * @param dbpos  偏移量（起始地址）
     * @param buf    要写入的数据BUF
     * @return true=成功；false=失败
     */
    public boolean Write(int dbcode, int dbpos, byte[] buf) {
        if (!flag) {
            return false;
        }

        try {
            // 写入请求报文
            byte[] byte1 = new byte[35 + buf.length];
            // 报文头
            byte1[0] = 0x03;
            byte1[1] = 0x00;
            // 报文长度
            byte1[2] = (byte) ((35 + buf.length) / 256 % 256);
            byte1[3] = (byte) ((35 + buf.length) % 256);
            // 固定
            byte1[4] = 0x02;
            byte1[5] = (byte) 0xF0;
            byte1[6] = (byte) 0x80;
            // 协议标识
            byte1[7] = 0x32;
            // 固定
            byte1[8] = 0x01;
            byte1[9] = 0x00;
            byte1[10] = 0x00;
            // 标识序列号
            byte1[11] = 0x00;
            byte1[12] = 0x01;
            // 固定
            byte1[13] = 0x00;
            byte1[14] = 0x0E;
            // 写入长度+4
            byte1[15] = (byte) ((4 + buf.length) / 256 % 256);
            byte1[16] = (byte) ((4 + buf.length) % 256);
            // 固定
            byte1[17] = 0x05;
            // CPU SLOT
            byte1[18] = 0x01;
            // 固定
            byte1[19] = 0x12;
            byte1[20] = 0x0A;
            byte1[21] = 0x10;
            // 写入方式：1按bit；2按字节
            byte1[22] = 0x02;
            // 写入数据的个数
            byte1[23] = (byte) (buf.length / 256 % 256);
            byte1[24] = (byte) (buf.length % 256);
            // DB块的编号
            byte1[25] = (byte) (dbcode / 256 % 256);
            byte1[26] = (byte) (dbcode % 256);
            // 写入数据块的类型：0x81-input，0x82-output，0x83-flag，0x84-DB
            byte1[27] = (byte) 0x84;
            // 写入DB块的偏移量（按位，一个字节为8位）
            byte1[28] = (byte) (dbpos * 8 / 256 / 256 % 256);
            byte1[29] = (byte) (dbpos * 8 / 256 % 256);
            byte1[30] = (byte) (dbpos * 8 % 256);
            // 写入方式：03按bit；04按字节
            byte1[31] = 0x00;
            byte1[32] = 0x04;
            // 写入bit的个数
            byte1[33] = (byte) (buf.length * 8 / 256 % 256);
            byte1[34] = (byte) (buf.length * 8 % 256);
            // 要写入的数据
            for (int i = 0; i < buf.length; i++) {
                byte1[35 + i] = buf[i];
            }

            // 写入数据
            socket.getOutputStream().write(byte1);

            // 读取反馈
            byte[] byte2 = new byte[22];
            socket.getInputStream().read(byte2);

            if (byte2[21] == (byte) 0xFF)
                return true;
            else
                return false;
        } catch (Exception e) {
            flag = false;
            Log.Write(strServiceCode, LogLevel.Error, String.format("写数据异常，原因【%s】，主动关闭连接", e.getMessage()));
            return false;
        }
    }

    /**
     * 读取PLC数据
     *
     * @param dbcode DB块编号
     * @param dbpos  偏移量（起始地址）
     * @param len    读取的字节数
     * @return 读取到的字节数组
     */
    public byte[] Read(int dbcode, int dbpos, int len) {
        if (!flag) {
            return null;
        }

        try {
            // 读取请求报文
            byte[] byte1 = new byte[31];
            // 报文头
            byte1[0] = 0x03;
            byte1[1] = 0x00;
            // 读取请求的长度
            byte1[2] = (byte) (byte1.length / 256 % 256);
            byte1[3] = (byte) (byte1.length % 256);
            // 固定
            byte1[4] = 0x02;
            byte1[5] = (byte) 0xF0;
            byte1[6] = (byte) 0x80;
            // 协议标识
            byte1[7] = 0x32;
            // 固定
            byte1[8] = 0x01;
            byte1[9] = 0x00;
            byte1[10] = 0x00;
            // 标识序列号
            byte1[11] = 0x00;
            byte1[12] = 0x01;
            // 固定
            byte1[13] = 0x00;
            byte1[14] = 0x0E;
            byte1[15] = 0x00;
            byte1[16] = 0x00;
            byte1[17] = 0x04;
            // CPU SLOT
            byte1[18] = 0x01;
            // 固定
            byte1[19] = 0x12;
            byte1[20] = 0x0A;
            byte1[21] = 0x10;
            byte1[22] = 0x02;
            // 访问的数据个数，以字节为单位
            byte1[23] = (byte) (len / 256 % 256);
            byte1[24] = (byte) (len % 256);
            // DB块的编号
            byte1[25] = (byte) (dbcode / 256 % 256);
            byte1[26] = (byte) (dbcode % 256);
            // 访问数据块的类型：0x81-input，0x82-output，0x83-flag，0x84-DB
            byte1[27] = (byte) 0x84;
            // 访问DB块的偏移量（按位，一个字节为8位）
            byte1[28] = (byte) (dbpos * 8 / 256 / 256 % 256);
            byte1[29] = (byte) (dbpos * 8 / 256 % 256);
            byte1[30] = (byte) (dbpos * 8 % 256);

            // 发送读取请求
            socket.getOutputStream().write(byte1);

            // 读取响应
            byte[] byte2 = new byte[25 + len];
            socket.getInputStream().read(byte2);

            // PLC内存数据字节数组
            byte[] ret = new byte[len];
            System.arraycopy(byte2, 25, ret, 0, len);

            // 返回读取到的数据
            if (byte2[21] == (byte) 0xFF)
                return ret;
            else
                return null;
        } catch (Exception e) {
            flag = false;
            Log.Write(strServiceCode, LogLevel.Error, String.format("读数据异常，原因【%s】，主动关闭连接", e.getMessage()));
            return null;
        }
    }

    /**
     * 线程控制
     */
    @Override
    public void run() {
        initServiceCode();

        //记录自服务变更记录
        CvmesService.serviceStatusChange(strServiceCode, 1);
        Log.Write(strServiceCode, LogLevel.Information, "服务启动");

        //检查服务编码
        if (strServiceCode == null) {
            Log.Write("main", LogLevel.Error, String.format("检查服务编码失败，服务无法启动。服务编码[%s]", strServiceCode));
            CvmesService.setServiceStop(strServiceCode);
            return;
        }

        while (true) {
            // 获取服务信息
            Record rec = Db.findById("T_SYS_SERVICE", "SERVICE_CODE", strServiceCode);

            // 更新生存时间
            Db.update("update T_SYS_SERVICE set LAST_LIVE_TIME=sysdate where SERVICE_CODE=?", strServiceCode);

            //初始化PLC
            initPlc(rec);

            // 退出指令
            if (rec.getInt("SERVICE_STATUS") == 0) {
                Log.Write(strServiceCode, LogLevel.Warning, "关闭PLC连接");
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    socket = null;
                }

                Log.Write(strServiceCode, LogLevel.Warning, "服务停止");
                CvmesService.setServiceStop(strServiceCode);

                Db.update("update T_SYS_SERVICE set LAST_OPERATE_TIME=sysdate,LAST_OPERATE_INFO=? where SERVICE_CODE=?", "服务停止", strServiceCode);

                //记录
                CvmesService.serviceStatusChange(strServiceCode, 0);
                break;
            }

            // 检查PLC连接
            try {
                if (!flag) {
                    socket = new Socket(InetAddress.getByName(ip), port);
                    Log.Write(strServiceCode, LogLevel.Information, String.format("成功建立连接，远端IP【%s】，端口【%d】", ip, port));

                    // 第一次握手
                    byte[] byte1 = new byte[]{0x03, 0x00, 0x00, 0x16, 0x11, (byte) 0xE0, 0x00, 0x00, 0x00, 0x01, 0x00, (byte) 0xC1, 0x02, 0x01, 0x00, (byte) 0xC2, 0x02, 0x01, 0x02, (byte) 0xC0, 0x01, 0x09};
                    socket.getOutputStream().write(byte1);
                    byte[] byte2 = new byte[22];
                    socket.getInputStream().read(byte2);
                    Log.Write(strServiceCode, LogLevel.Information, "第一次握手成功");

                    // 第二次握手
                    byte[] byte3 = new byte[]{0x03, 0x00, 0x00, 0x19, 0x02, (byte) 0xF0, (byte) 0x80, 0x32, 0x01, 0x00, 0x00, (byte) 0xFF, (byte) 0xFF, 0x00, 0x08, 0x00, 0x00, (byte) 0xF0, 0x00, 0x00, 0x01, 0x00, 0x01, 0x07, (byte) 0x80};
                    socket.getOutputStream().write(byte3);
                    byte[] byte4 = new byte[27];
                    socket.getInputStream().read(byte4);
                    Log.Write(strServiceCode, LogLevel.Information, "第二次握手成功");

                    // 设置连接状态标记为可读写
                    flag = true;
                }
            } catch (Exception e) {
                flag = false;
                Log.Write(strServiceCode, LogLevel.Error, String.format("建立连接失败，远端IP【%s】，端口【%d】，原因【%s】", ip, port, e.getMessage()));
            }

            // 业务操作
            try {
                msg = runBll(rec);

                // 更新服务信息
                if (msg.length() != 0) {
                    Db.update("update T_SYS_SERVICE set LAST_OPERATE_TIME=sysdate,LAST_OPERATE_INFO=? where SERVICE_CODE=?", msg, strServiceCode);
                }
            } catch (Exception e) {
                msg = String.format("业务处理异常，原因[%s]", e.getMessage());
                Log.Write(strServiceCode, LogLevel.Error, msg);
            } catch (Error error) {
                CvmesService.setServiceStop(strServiceCode);
                msg = String.format("发生严重错误，线程已退出，原因[%s]", error.getMessage());
                Log.Write(strServiceCode, LogLevel.Error, msg);
                break;
            }

            // 休眠
            try {
                Thread.sleep(((Double) (rec.getDouble("SLEEP_TIME") * 1000)).intValue());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

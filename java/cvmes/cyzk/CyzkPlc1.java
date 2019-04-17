package cvmes.cyzk;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;
import cvmes.common.Log;
import cvmes.common.LogLevel;
import cvmes.common.PlcSiemens;
import cvmes.common.PlcType;

import java.util.List;

public class CyzkPlc1 extends PlcSiemens {
    @Override
    public void initServiceCode() {
        this.strServiceCode = "CyzkPlc1";
    }

    @Override
    public void initPlc(Record rec_service) {
        this.plctype = PlcType.SiemensS300;
        this.ip = rec_service.getStr("SERVICE_PARA1_VALUE");
        this.port = Integer.parseInt(rec_service.getStr("SERVICE_PARA2_VALUE"));
    }

    @Override
    public String runBll(Record rec_service) throws Exception {
        // 设备编码
        String device_code = rec_service.getStr("SERVICE_PARA3_VALUE");
        // 最后操作信息
        String msg = "";

        // 写入

        // 读取
        // 获取读取配置
        List<Record> list_read = Db.find("select * from t_device_plc_read where device_code = ?", device_code);
        if (list_read == null || list_read.size() == 0) {
            msg = "没有有效的读取配置";
            Log.Write(strServiceCode, LogLevel.Warning, msg);
        } else {
            // 循环读取
            for (Record sub_read : list_read) {
                byte[] buf = Read(sub_read.getInt("dbcode"), sub_read.getInt("dbpos"), sub_read.getInt("dblen"));
                if (buf == null) {
                    msg = String.format("读取失败，dbcode【%d】，dbpos【%d】，dblen【%d】", sub_read.getInt("dbcode"), sub_read.getInt("dbpos"), sub_read.getInt("dblen"));
                    break;
                } else {
                    // 按需截取
                    List<Record> list_read_d = Db.find("select * from t_device_plc_read_d where device_code = ? and read_code = ?", device_code, sub_read.getStr("read_code"));
                    if (list_read_d == null || list_read_d.size() == 0) {
                        msg = String.format("没有有效的截取设置，读取编码【%s】", sub_read.getStr("read_code"));
                        Log.Write(strServiceCode, LogLevel.Warning, msg);
                    } else {
                        // 循环截取
                        for (Record sub_read_d : list_read_d) {
                            switch (sub_read_d.getInt("cuttype")) {
                                case 0:
                                    // 字符串
                                    String v1 = BufGetString(buf, sub_read_d.getInt("cutpos"), sub_read_d.getInt("cutlen"));
                                    Db.update("update t_device_plc_read_d set cutvalue=? where id=?", v1, sub_read_d.getStr("id"));
                                    break;
                                case 1:
                                    // short
                                    short v2 = BufGetShort(buf, sub_read_d.getInt("cutpos"));
                                    Db.update("update t_device_plc_read_d set cutvalue=? where id=?", Short.toString(v2), sub_read_d.getStr("id"));
                                    break;
                                case 2:
                                    // bit
                                    int v3 = BufGetBit(buf, sub_read_d.getInt("cutpos"), sub_read_d.getInt("cutbit"));
                                    Db.update("update t_device_plc_read_d set cutvalue=? where id=?", Integer.toString(v3), sub_read_d.getStr("id"));
                                    break;
                                case 3:
                                    // int
                                    int v4 = BufGetInt(buf, sub_read_d.getInt("cutpos"));
                                    Db.update("update t_device_plc_read_d set cutvalue=? where id=?", Integer.toString(v4), sub_read_d.getStr("id"));
                                    break;
                                case 4:
                                    // float
                                    float v5 = BufGetFloat(buf, sub_read_d.getInt("cutpos"));
                                    Db.update("update t_device_plc_read_d set cutvalue=? where id=?", Float.toString(v5), sub_read_d.getStr("id"));
                                    break;
                            }
                        }
                    }
                }
            }
            msg = "读取成功";
        }

        return msg;
    }
}

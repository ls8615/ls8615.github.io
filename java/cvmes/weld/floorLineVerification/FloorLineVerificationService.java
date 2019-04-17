package cvmes.weld.floorLineVerification;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.IAtom;
import com.jfinal.plugin.activerecord.Record;
import cvmes.CvmesService;
import cvmes.common.Log;
import cvmes.common.LogLevel;

import java.sql.SQLException;
import java.util.List;

/**
 * 地板线校验服务
 */
public class FloorLineVerificationService {

    //子服务入口
    public void run() {
        Thread thread = new Thread(new FloorLineVerificationThread());
        thread.start();
    }

    /**
     * 子服务线程
     */
    class FloorLineVerificationThread extends Thread {
        // 服务编码
        private final String strServiceCode = "FloorVerificationService";

        // 地板线头扫码验证内存地址组编码
        private final String groupMemoryCode = "floor.verification";

        // 业务操作
        private void runBll(Record rec_service) {
            String msg = "";
            try {
                msg = DealHeader();
            } catch (Exception ex) {
                msg = String.format("业务处理异常：[%s]", ex.getMessage());
                Log.Write(strServiceCode, LogLevel.Error, msg);
            }
            // 更新服务信息
            if (msg.length() != 0) {
                Db.update("update T_SYS_SERVICE set LAST_OPERATE_TIME=sysdate,LAST_OPERATE_INFO=? where SERVICE_CODE=?", msg, strServiceCode);
            }
        }

        /**
         * 逻辑处理入口
         */
        private String DealHeader() {
            //计算结果信息
            String msg = "";

            //2.获取写表是否存在未处理指令
            List<Record> list_memory_write = Db.find("SELECT 1 FROM T_DEVICE_WELD_WRITE_RAM WHERE  DEAL_STATUS = '0'  AND  group_memory_code  = ?", groupMemoryCode);

            //2.1.写表存在未处理指令，计算结束
            if (list_memory_write.size() != 0) {
                msg = String.format("地板线头扫码验证写表内存地址组【%s】存在未处理指令，请检查OPC通讯是否正常", groupMemoryCode);
                Log.Write(strServiceCode, LogLevel.Error, msg);
                return msg;
            }

            //3.获取读表指令
            List<Record> list_memory_read = Db.find("SELECT T.* FROM T_DEVICE_WELD_READ_RAM T WHERE T.GROUP_MEMORY_CODE =?", groupMemoryCode);
            if (list_memory_read.size() != 1) {
                msg = String.format("读取地板线头扫码验证内存地址组【%s】配置错误，请检查配置", groupMemoryCode);
                Log.Write(strServiceCode, LogLevel.Error, msg);
                return msg;
            }

            //4.获取控制命令字
            String memoryValue = list_memory_read.get(0).getStr("LOGIC_VALUE");

            switch (memoryValue) {
                case "0":
                    Log.Write(strServiceCode, LogLevel.Information, String.format("地址组[%s],控制命令字[%s]下位操作。", groupMemoryCode, memoryValue));
                    return "";
                case "1":
                    return executeOperation(strServiceCode, list_memory_read.get(0)).getStr("msg");
                case "2":
                    //MES验证车身钢码失败，只读
                    Log.Write(strServiceCode, LogLevel.Information, String.format("地址组[%s],控制命令字[%s]下位操作。", groupMemoryCode, memoryValue));
                    return "";
                default:
                    msg = String.format("控制命令字未定义，内存地址组[%s],命令字[%s]", groupMemoryCode, msg);
                    Log.Write(strServiceCode, LogLevel.Error, msg);
                    return msg;
            }
        }

        /**
         * 读取PLC验证成功的车身钢码，MES与待上线的第一台车进行验证。若验证通过，复位车身钢码、标志位；若验证失败，改标志位为2
         *
         * @param strServiceCode 服务编码
         * @param memoryRead     内存地址组读表值
         */
        private Record executeOperation(String strServiceCode, Record memoryRead) {
            String msg = "";
            Record recordMsg = new Record().set("msg", "").set("error", true);
            Log.Write(strServiceCode, LogLevel.Information, String.format("控制命令字[%s]开始计算", memoryRead.getStr("LOGIC_VALUE")));

            //1.获取焊装地板线头扫码验证plc计划队列
            List<Record> list_manual_plan = Db.find("SELECT T.CARBODY_CODE,T.ROBOT_CODE,T.SEQ_NO FROM T_INF_TO_WELD_FSIDE T WHERE T.IS_CHECK =0 AND T.DEAL_STATUS=1 ORDER BY SEQ_NO ASC ");
            if (list_manual_plan == null || list_manual_plan.size() == 0) {
                msg = "没有获取到焊装地板线头扫码验证计划任务";
                Log.Write(strServiceCode, LogLevel.Warning, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
            }

            //2.获取车身钢码号
            String bodySteelCode = list_manual_plan.get(0).get("CARBODY_CODE") + "";
            if (bodySteelCode.length() == 0 || bodySteelCode.equals("null")) {
                msg = "获取焊装地板线头扫码验证队列车身钢码号的值错误";
                Log.Write(strServiceCode, LogLevel.Warning, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
            }

            //3. 获取读表钢码号
            String vPart = ComFun.getVpartFromMemoryValues(memoryRead);
            if (vPart.length() == 0 || vPart.equals("null")) {
                msg = "获取读表车身钢码号的值错误";
                Log.Write(strServiceCode, LogLevel.Warning, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
            }

            //3.1 读表钢码号如果超过25位 则截取23位
            boolean ref = false;
            if (vPart.length() > 25) {
                vPart = vPart.substring(0, 25);
            }

            //4 校验读表钢码号与队列钢码号是否一致
            if (bodySteelCode.equals(vPart.trim())) {
                ref = true;
            }

            if (!ref) {
                msg = String.format("验证钢码与队列中不一致：计划队列的刚码号[%s],内存读表的钢码号[%s],下发控制命[%s]", bodySteelCode, vPart,"2");
                Log.Write(strServiceCode, LogLevel.Error, msg);
                recordMsg.set("msg", msg);
                //3 验证不通过,下达控制命令字 2
                Db.update(ComFun.insertWriteMemoryStatus(),
                        "2",
                        groupMemoryCode
                );
                return recordMsg;
            }
            //启用事务
            boolean isSucess = Db.tx(new IAtom() {
                @Override
                public boolean run() throws SQLException {
                    String msg = "";
                    //2.1 验证通过 修改验证状态
                    int update = Db.update("UPDATE T_INF_TO_WELD_FSIDE SET IS_CHECK=1 WHERE CARBODY_CODE = ?", bodySteelCode);
                    if (update <= 0) {
                        msg = String.format("焊装地板线头扫码验证计划处理失败:内存读表的钢码号:[%s]", bodySteelCode);
                        Log.Write(strServiceCode, LogLevel.Error, msg);
                        recordMsg.set("msg", msg).set("error", false);
                        return false;
                    }
                    //2.2
                    msg = String.format("焊装地板线头扫码验证计划处理成功:内存读表的钢码号:[%s]", bodySteelCode);
                    Log.Write(strServiceCode, LogLevel.Information, msg);

                    //3.清空写表数据
                    //3.1清空车身钢码号
                    Record recordShorts = ComFun.getMemoryValuesFromVpart("");

                    Db.update(ComFun.getWriteMemorySql(),
                            "0",
                            "",
                            "",
                            recordShorts.getInt("VPART_MEMORY_VALUE1"),
                            recordShorts.getInt("VPART_MEMORY_VALUE2"),
                            recordShorts.getInt("VPART_MEMORY_VALUE3"),
                            recordShorts.getInt("VPART_MEMORY_VALUE4"),
                            recordShorts.getInt("VPART_MEMORY_VALUE5"),
                            recordShorts.getInt("VPART_MEMORY_VALUE6"),
                            recordShorts.getInt("VPART_MEMORY_VALUE7"),
                            recordShorts.getInt("VPART_MEMORY_VALUE8"),
                            recordShorts.getInt("VPART_MEMORY_VALUE9"),
                            recordShorts.getInt("VPART_MEMORY_VALUE10"),
                            recordShorts.getInt("VPART_MEMORY_VALUE11"),
                            recordShorts.getInt("VPART_MEMORY_VALUE12"),
                            recordShorts.getInt("VPART_MEMORY_VALUE13"),
                            groupMemoryCode
                    );
                    msg = String.format("焊装地板线头扫码验证清空下位数据,下达控制命令字[%s]", "0");
                    Log.Write(strServiceCode, LogLevel.Information, msg);
                    recordMsg.set("msg", msg).set("error", true);

                    return true;
                }
            });


            return recordMsg;
        }


        // 线程控制
        @Override
        public void run() {
            Log.Write(strServiceCode, LogLevel.Information, "服务启动");

            while (true) {
                // 获取服务信息
                Record rec = Db.findById("T_SYS_SERVICE", "SERVICE_CODE", strServiceCode);

                // 更新生存时间
                Db.update("update T_SYS_SERVICE set LAST_LIVE_TIME=sysdate where SERVICE_CODE=?", strServiceCode);

                // 退出指令
                if (rec.getInt("SERVICE_STATUS") == 0) {
                    Log.Write(strServiceCode, LogLevel.Warning, "服务停止");
                    CvmesService.setServiceStop(strServiceCode);
                    break;
                }

                // 业务操作
                try {
                    runBll(rec);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                // 休眠
                try {
                    Thread.sleep(1000 * rec.getInt("SLEEP_TIME"));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

    }
}

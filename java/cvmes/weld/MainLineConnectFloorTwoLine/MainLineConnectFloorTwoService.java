package cvmes.weld.MainLineConnectFloorTwoLine;

import com.alibaba.druid.util.StringUtils;
import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.IAtom;
import com.jfinal.plugin.activerecord.Record;
import cvmes.CvmesService;
import cvmes.common.Log;
import cvmes.common.LogLevel;

import java.sql.SQLException;
import java.util.List;

/**
 * 地板二线与主线交接点
 */
public class MainLineConnectFloorTwoService {

    //子服务入口
    public void run() {
        Thread thread = new Thread(new MainLineConnectThread());
        thread.start();
    }

    /**
     * 子服务线程
     */
    class MainLineConnectThread extends Thread {
        // 服务编码
        private final String strServiceCode = "MainLineConnectService";

        // 地板二线与主线交接点内存地址组编码
        private final String groupMemoryCode = "mainLine.connect";

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

            //1.获取读表指令
            List<Record> list_memory_read = Db.find("SELECT T.* FROM T_DEVICE_WELD_READ_RAM T WHERE T.GROUP_MEMORY_CODE =?", groupMemoryCode);
            if (list_memory_read.size() != 1) {
                msg = String.format("读取地板二线与主线交接点内存地址组【%s】配置错误，请检查配置", groupMemoryCode);
                Log.Write(strServiceCode, LogLevel.Error, msg);
                return msg;
            }

            //2.1.获取PLC离线状态
            String automatism = list_memory_read.get(0).getStr("AUTOMATISM");
            if (automatism == null) {
                msg = String.format("地板二线与主线交接点读表内存地址组【%s】自动标记状态为空，请检查自动标记状态", groupMemoryCode);
                Log.Write(strServiceCode, LogLevel.Information, msg);
                return msg;
            }

            //2.1.1.PLC离线状态，不做处理，结束运行
            if ("1".equals(automatism)) {
                msg = String.format("地板二线与主线交接点读表内存地址组【%s】PLC处于离线状态，不下发计划", groupMemoryCode);
                Log.Write(strServiceCode, LogLevel.Information, msg);
                return msg;
            }

            //2.获取写表是否存在未处理指令
            List<Record> list_memory_write = Db.find("SELECT 1 FROM T_DEVICE_WELD_WRITE_RAM WHERE  DEAL_STATUS = '0'  AND  group_memory_code  = ?", groupMemoryCode);

            //2.1.写表存在未处理指令，计算结束
            if (list_memory_write.size() != 0) {
                msg = String.format("地板二线与主线交接点写表内存地址组【%s】存在未处理指令，请检查OPC通讯是否正常", groupMemoryCode);
                Log.Write(strServiceCode, LogLevel.Warning, msg);
                return msg;
            }


            //3.获取控制命令字
            String memoryValue = list_memory_read.get(0).getStr("LOGIC_VALUE");

            switch (memoryValue) {
                case "0":
                    return executeOperation(strServiceCode, list_memory_read.get(0)).getStr("msg");
                case "1":
                    return checkStatus(strServiceCode, list_memory_read.get(0)).getStr("msg");
                case "2":
                    return reset(strServiceCode, list_memory_read.get(0)).getStr("msg");
                case "3":
                    Log.Write(strServiceCode, LogLevel.Information, String.format("地址组[%s],控制命令字[%s]下位操作。", groupMemoryCode, memoryValue));
                    return "";
                default:
                    msg = String.format("控制命令字未定义，内存地址组[%s],命令字[%s]", groupMemoryCode, msg);
                    Log.Write(strServiceCode, LogLevel.Error, msg);
                    return msg;
            }
        }

        /**
         * 判断一致信号
         */
        private Record checkStatus(String strServiceCode, Record memoryRead) {
            String msg = "";
            Record recordMsg = new Record().set("msg", "").set("error", true);
            Log.Write(strServiceCode, LogLevel.Information, String.format("控制命令字[%s]开始计算", memoryRead.getStr("LOGIC_VALUE")));

            //1.获取焊装地板二线与主线交接点plc计划队列
            List<Record> list_manual_plan = Db.find(ComFun.getMainLinePlanQueue());
            if (list_manual_plan == null || list_manual_plan.size() == 0) {
                msg = "没有获取到焊装地板二线与主线交接点计划任务";
                Log.Write(strServiceCode, LogLevel.Warning, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
            }



            //1.2获取车身钢码号
            String steelCode = list_manual_plan.get(0).get("STEEL_CODE") + "";
            if (steelCode.length() == 0 || steelCode.equals("null")) {
                msg = "获取焊装地板二线与主线交接点队列车身钢码号的值错误";
                Log.Write(strServiceCode, LogLevel.Warning, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
            }

            //2.1.获取对比一致信号
            String compareOk = memoryRead.getStr("COMPARE_OK");
            if (StringUtils.isEmpty(compareOk)) {
                msg = String.format("焊装主线计划比较一致信号不存在!");
                Log.Write(strServiceCode, LogLevel.Error, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
            }

            //2.2 获取读表钢码号
            String bodySteelCode = ComFun.getVpartFromMemoryValues(memoryRead);
            if (bodySteelCode.length() == 0 || bodySteelCode.equals("null")) {
                msg = "获取读表车身钢码号的值错误";
                Log.Write(strServiceCode, LogLevel.Warning, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
            }



            switch (compareOk) {
                case "0":
                    //只读
                    Log.Write(strServiceCode, LogLevel.Information, String.format("地址组[%s],一致信号为[%s]下位操作。", groupMemoryCode, compareOk));
                    return recordMsg;
                case "1":
                    Log.Write(strServiceCode, LogLevel.Information, String.format("一致信号[%s]开始计算", compareOk));

                    //启用事务
                    boolean isSucess = Db.tx(new IAtom() {
                        @Override
                        public boolean run() throws SQLException {
                            String msg = "";

                            //3.2 校验读表中获取的钢码号和计划队列中钢码号是否一致
                            if (!bodySteelCode.equals(steelCode)) {
                                msg = String.format("焊装主线计划检验钢码号失败：计划队列的刚码号[%s],内存读表的钢码号[%s]", steelCode, bodySteelCode);
                                Log.Write(strServiceCode, LogLevel.Error, msg);
                                recordMsg.set("msg", msg);
                                return false;
                            }

                            //2.2 逻辑值改为3，其他从读表获取
                            int ref = Db.update(ComFun.insertWriteMemoryStatus(), 3, groupMemoryCode);
                            if (ref == -1) {
                                msg = String.format("下达控制命令字[%s]失败", "3");
                                Log.Write(strServiceCode, LogLevel.Error, msg);
                                recordMsg.set("msg", msg).set("error", false);
                                return false;
                            }
                            //2.3标记为已发送 修改处理状态
                            int ref1 = Db.update(ComFun.updateMainLinePlanStatus(), bodySteelCode);
                            if (ref1 == -1) {
                                msg = String.format("地板二线与主线交接点计划处理失败:内存读表的钢码号:[%s]", bodySteelCode);
                                Log.Write(strServiceCode, LogLevel.Error, msg);
                                recordMsg.set("msg", msg).set("error", false);
                                return false;
                            }
                            msg = String.format("地板二线与主线交接点计划处理成功:内存读表的钢码号:[%s]，下达控制命令字[%s]成功", bodySteelCode, "3");
                            Log.Write(strServiceCode, LogLevel.Information, msg);
                            recordMsg.set("msg", msg).set("error", false);
                            return true;
                        }
                    });
                    break;
                case "2":
                    Log.Write(strServiceCode, LogLevel.Information, String.format("一致信号[%s]开始计算", compareOk));

                    //2 一致信号为2 记录log 不做其他操作
                    msg = String.format("地板二线与主线交接点plc车辆不一致:一致信号:[%s],内存读表的钢码号:[%s],计划队列钢码号:[%s]",
                            compareOk, bodySteelCode,steelCode);
                    Log.Write(strServiceCode, LogLevel.Information, msg);
                    break;
                default:
                    msg = String.format("一致信号未定义，内存地址组[%s],一致信号[%s]", groupMemoryCode, compareOk);
                    Log.Write(strServiceCode, LogLevel.Error, msg);
                    break;
            }
            return recordMsg;
        }


        /**
         * 收到命令字符2 mes操作
         * 把命令字复位为0，车辆一致信号改为0，
         * 清空钢码号和焊接代码等信息
         *
         * @param strServiceCode 服务名
         * @param memoryRead     内存地址组读表值
         */
        private Record reset(String strServiceCode, Record memoryRead) {
            String msg = "";
            Record recordMsg = new Record().set("msg", "").set("error", true);
            Log.Write(strServiceCode, LogLevel.Information, String.format("控制命令字[%s]开始计算", memoryRead.getStr("LOGIC_VALUE")));

            //3.清空写表数据
            //3.1清空车身钢码号
            Record recordShorts = ComFun.getMemoryValuesFromVpart("");

            //3.2清空焊接代码
            Record robotShorts = ComFun.getRobotMemoryValuesFromRobot("");
            //3.3 清空写表数据
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
                    robotShorts.getInt("ROBOT_MEMORY_VALUE1"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE2"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE3"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE4"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE5"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE6"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE7"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE8"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE9"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE10"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE11"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE12"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE13"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE14"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE15"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE16"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE17"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE18"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE19"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE20"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE21"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE22"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE23"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE24"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE25"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE26"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE27"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE28"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE29"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE30"),
                    groupMemoryCode
            );

            msg = String.format("焊装地板二线与主线交接点清空下位数据,下达控制命令字[%s]", "0");
            Log.Write(strServiceCode, LogLevel.Information, msg);
            recordMsg.set("msg", msg).set("error", true);


            return recordMsg;
        }


        private Record executeOperation(String strServiceCode, Record memoryRead) {
            String msg = "";
            Record recordMsg = new Record().set("msg", "").set("error", true);
            Log.Write(strServiceCode, LogLevel.Information, String.format("控制命令字[%s]开始计算", memoryRead.getStr("LOGIC_VALUE")));

            //1.获取焊装地板二线与主线交接点plc计划队列
            List<Record> list_manual_plan = Db.find(ComFun.getMainLinePlanQueue());
            if (list_manual_plan == null || list_manual_plan.size() == 0) {
                msg = "没有获取到焊装地板二线与主线交接点计划任务";
                Log.Write(strServiceCode, LogLevel.Warning, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
            }

            //2.获取车身钢码号
            String bodySteelCode = list_manual_plan.get(0).get("STEEL_CODE") + "";
            if (bodySteelCode.length() == 0 || bodySteelCode.equals("null")) {
                msg = "获取焊装地板二线与主线交接点队列车身钢码号的值错误";
                Log.Write(strServiceCode, LogLevel.Warning, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
            }

            //3.获取焊接代码
            String robotlCode = list_manual_plan.get(0).get("ROBOT_CODE") + "";
            if (robotlCode.length() == 0 || robotlCode.equals("null")) {
                msg = "获取焊装地板二线与主线交接点计划机器人代码的值错误";
                Log.Write(strServiceCode, LogLevel.Warning, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
            }

            //4.写入到写表
            //4.1转换车身钢码号
            Record recordShorts = ComFun.getMemoryValuesFromVpart(bodySteelCode);

            //4.2转换焊接代码
            Record robotShorts = ComFun.getRobotMemoryValuesFromRobot(robotlCode);

            //4.3写入到写表 将控制命令改成 1
            Db.update(ComFun.getWriteMemorySql(),
                    "1",
                    bodySteelCode,
                    robotlCode,
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
                    robotShorts.getInt("ROBOT_MEMORY_VALUE1"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE2"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE3"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE4"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE5"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE6"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE7"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE8"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE9"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE10"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE11"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE12"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE13"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE14"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE15"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE16"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE17"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE18"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE19"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE20"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE21"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE22"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE23"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE24"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE25"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE26"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE27"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE28"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE29"),
                    robotShorts.getInt("ROBOT_MEMORY_VALUE30"),
                    groupMemoryCode
            );
            msg = String.format("焊装地板二线与主线交接点,发送计划,下达控制命令字[%s],钢码号为[%s]",
                    "1",
                    bodySteelCode);
            Log.Write(strServiceCode, LogLevel.Information, msg);
            recordMsg.set("msg", msg);

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

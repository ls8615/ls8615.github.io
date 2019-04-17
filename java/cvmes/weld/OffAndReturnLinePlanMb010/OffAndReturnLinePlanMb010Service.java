package cvmes.weld.OffAndReturnLinePlanMb010;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.IAtom;
import com.jfinal.plugin.activerecord.Record;
import cvmes.CvmesService;
import cvmes.common.Log;
import cvmes.common.LogLevel;

import java.sql.SQLException;
import java.util.List;

/**
 * 焊装MB010计划离线回线服务
 */
public class OffAndReturnLinePlanMb010Service {

    //子服务入口
    public void run() {
        Thread thread = new Thread(new OffAndReturnLinePlanMb010Thread());
        thread.start();
    }

    /**
     * 子服务线程
     */
    class OffAndReturnLinePlanMb010Thread extends Thread {

        // 服务编码
        private final String strServiceCode = "OffAndReturnMb010Service";

        // MB010离线回线内存地址组编码
        private final String groupMemoryCode = "plan.offline.returnline.mb010";

        // 业务操作
        private void runBll(Record rec_service) {
            String msg = "";
            try {
                msg = DealHeader();
            } catch (Exception ex) {
                msg = ex.getMessage();
            }

            // 更新服务信息
            if (msg.length() != 0) {
                Db.update("update T_SYS_SERVICE set LAST_OPERATE_TIME=sysdate,LAST_OPERATE_INFO=? where SERVICE_CODE=?", msg, strServiceCode);
            }
        }

        /**
         * 处理逻辑入口
         */
        private String DealHeader() {
            //计算结果信息
            String msg = "";

            //1.获取写表是否存在未处理指令
            List<Record> list_memory_write = Db.find("SELECT 1 FROM T_DEVICE_WELD_WRITE_RAM WHERE  DEAL_STATUS = '0'  AND  group_memory_code  = ?", groupMemoryCode);

            //1.1.写表存在未处理指令，计算结束
            if (list_memory_write.size() != 0) {
                msg = String.format("MB010计划离线回线写表内存地址组【%s】存在未处理指令，请检查OPC通讯是否正常", groupMemoryCode);
                Log.Write(strServiceCode, LogLevel.Error, msg);
                return msg;
            }

            //2.获取读表指令
            List<Record> list_memory_read = Db.find("SELECT T.* FROM T_DEVICE_WELD_READ_RAM T WHERE T.GROUP_MEMORY_CODE =?", groupMemoryCode);
            if (list_memory_read.size() != 1) {
                msg = String.format("读取MB010计划离线回线内存地址组【%s】配置错误，请检查配置", groupMemoryCode);
                Log.Write(strServiceCode, LogLevel.Error, msg);
                return msg;
            }

            //2.1.获取PLC离线状态
            String automatism = list_memory_read.get(0).getStr("AUTOMATISM");
            if (automatism==null) {
                msg = String.format("MB010计划离线回线读表内存地址组【%s】自动标记状态为空，请检查自动标记状态", groupMemoryCode);
                Log.Write(strServiceCode, LogLevel.Information, msg);
                return msg;
            }

            //2.1.  1.PLC离线状态，不做处理，结束运行  2.  1代表离线 2 代表在线
            if ("1".equals(automatism)) {
                msg = String.format("MB010计划离线回线读表内存地址组【%s】PLC处于离线状态，不下发计划", groupMemoryCode);
                Log.Write(strServiceCode, LogLevel.Information, msg);
                return msg;
            }

            //3.获取控制命令字
            String memoryValue = list_memory_read.get(0).getStr("LOGIC_VALUE");

            //0：mes发送计划 （1.发送离线计划  2.发送回线计划） 1:只读 2:只读　  3：计划发送成功直接改4
            // 4：只读  5:离线计划执行完毕 清空读表   6：回线计划执行完毕 清空读表  7： 回线钢码和RFID不一致，mes只读
            switch (memoryValue) {
                case "0":
                    return executeOperation(strServiceCode, list_memory_read.get(0)).getStr("msg");
                case "1":
                    Log.Write(strServiceCode, LogLevel.Information, String.format("地址组[%s],控制命令字[%s]下位操作。", groupMemoryCode, memoryValue));
                    return "";
                case "2":
                    Log.Write(strServiceCode, LogLevel.Information, String.format("地址组[%s],控制命令字[%s]下位操作。", groupMemoryCode, memoryValue));
                    return "";
                case "3":
                    return successPlan(strServiceCode, list_memory_read.get(0)).getStr("msg");
                case "4":
                    Log.Write(strServiceCode, LogLevel.Information, String.format("地址组[%s],控制命令字[%s]下位操作。", groupMemoryCode, memoryValue));
                    return "";
                case "5":
                    return reset(strServiceCode, list_memory_read.get(0)).getStr("msg");
                case "6":
                    return reset(strServiceCode, list_memory_read.get(0)).getStr("msg");
                case "7":
                    Log.Write(strServiceCode, LogLevel.Information, String.format("地址组[%s],控制命令字[%s]下位操作。", groupMemoryCode, memoryValue));
                    return "";
                default:
                    msg = String.format("控制命令字未定义，内存地址组[%s],命令字[%s]", groupMemoryCode, msg);
                    Log.Write(strServiceCode, LogLevel.Error, msg);
                    return msg;

            }
        }

        /**
         * 收到命令3 说明计划发送成功   mes操作 将命令改成4
         *
         * @param strServiceCode 服务名
         * @param memoryRead     内存地址组读表值
         */
        private Record successPlan(String strServiceCode, Record memoryRead) {
            String msg = "";
            Record recordMsg = new Record().set("msg", "").set("error", true);
            Log.Write(strServiceCode, LogLevel.Information, String.format("控制命令字[%s]开始计算", memoryRead.getStr("LOGIC_VALUE")));

            //1.读表写入4
            Db.update(ComFun.getSuccessWriteMemorySql(), "4", groupMemoryCode);

            msg = String.format("焊装计划离线回线计划发送成功,下达控制命令字[%s]", "4");
            Log.Write(strServiceCode, LogLevel.Information, msg);
            recordMsg.set("msg", msg);

            return recordMsg;
        }

        /**
         * 收到命令字符0 mes操作
         * 发送焊装MB010离线回线计划plc计划
         *
         * @param strServiceCode 服务名
         * @param memoryRead     内存地址组读表值
         */
        private Record executeOperation(String strServiceCode, Record memoryRead) {
            String msg = "";
            Record recordMsg = new Record().set("msg", "").set("error", true);
            Log.Write(strServiceCode, LogLevel.Information, String.format("控制命令字[%s]开始计算", memoryRead.getStr("LOGIC_VALUE")));

            //1.获取MB010离线回线计划队列
            List<Record> list_manual_plan = Db.find(ComFun.getOffAndReturnLinePlanMb010Plan());
            if (list_manual_plan == null || list_manual_plan.size() == 0) {
                msg = "没有获取到焊装MB010离线回线计划任务";
                Log.Write(strServiceCode, LogLevel.Warning, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
            }
            //2.判断计划类型  0.离线计划，  1. 回线计划
            //2.1  获取计划类型
            String planType = list_manual_plan.get(0).getStr("PLAN_TYPE") + "";
            if (("null").equals(planType) || planType.length() == 0) {
                msg = "获取焊装MB010离线回线计划类型错误";
                Log.Write(strServiceCode, LogLevel.Warning, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
            }

            //2.1.2 获取车身钢码
            String bodySteelCode = list_manual_plan.get(0).get("K_STAMP_ID") + "";
            if (bodySteelCode.length() == 0 || bodySteelCode.equals("null")) {
                msg = "获取焊装计划离线回线队列车身钢码号的值错误";
                Log.Write(strServiceCode, LogLevel.Warning, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
            }

            //前置车身钢码号生产编码   离线车为空
            String preProductionCode = "";
            //2.1.3转换车身钢码号
            Record recordShorts = ComFun.getMemoryValuesFromVpart(bodySteelCode);
            //2.1.4 前置车身钢码号
            String k_stamp_id = "";
            //2.1.5转换前置车身钢码号， 离线车没有前置 为空
            Record loopForwardShorts;


            //2.2发MB010工位的离线计划时，标记改为1，写入计划类型、车身钢码
            String logicValue;

            //写表计划类型1为离线  2为回线  计划表 0 离线  1 回线
            String writePlanType="0";
            if (("0").equals(planType)) {
                //2.2.1  0 代表离线  写入到写表1
                logicValue = "1";
                writePlanType="1";
                loopForwardShorts = ComFun.getMemoryValuesFromLoopForward("");
            } else if (("1").equals(planType)) {
                //2.2.2  1 代表回线  写入到写表2
                logicValue = "2";
                writePlanType="2";
                //2.2.3 获取回线车有前置车身钢码号生产编码
                preProductionCode = list_manual_plan.get(0).get("PRE_PRODUCTION_CODE") + "";
                if (preProductionCode.length() == 0 || preProductionCode.equals("null")) {
                    msg = "获取焊装计划离线回线队列前置车身钢码号生产编码的值错误";
                    Log.Write(strServiceCode, LogLevel.Warning, msg);
                    recordMsg.set("msg", msg);
                    return recordMsg;
                }

                //2.2.4  通过前置车身钢码号生产编码在需求产品找前置车身钢码号
                List<Record> loopForwardRecord = Db.find(ComFun.getDemandProductInfoFromProductionCodeSql(), preProductionCode);
                if (loopForwardRecord.size() != 1) {
                    msg = String.format("获取生产编码[%s]的需求产品信息存在多条记录或者不存在", preProductionCode);
                    Log.Write(strServiceCode, LogLevel.Error, msg);
                    recordMsg.set("msg", msg);
                    return recordMsg;
                }
                k_stamp_id = loopForwardRecord.get(0).getStr("K_STAMP_ID");
                if (k_stamp_id.length() == 0 || k_stamp_id.equals("null")) {
                    msg = "获取需求产品表焊装计划离线回线队列前置车身钢码号的值错误";
                    Log.Write(strServiceCode, LogLevel.Warning, msg);
                    recordMsg.set("msg", msg);
                    return recordMsg;
                }

                //2.2.5 转换前置车身钢码号
                loopForwardShorts = ComFun.getMemoryValuesFromLoopForward(k_stamp_id);
            } else {
                msg = "获取焊装MB010离线回线计划类型错误";
                Log.Write(strServiceCode, LogLevel.Warning, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
            }
            //2.3 发回线计划时，标记改为2，写入计划类型、车身钢码、回线前置车身钢码
            Db.update(ComFun.getWriteMemorySql(),
                    logicValue,
                    bodySteelCode,
                    k_stamp_id,
                    writePlanType,
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
                    loopForwardShorts.getInt("FRONTLINE_MEMORY_VALUE1"),
                    loopForwardShorts.getInt("FRONTLINE_MEMORY_VALUE2"),
                    loopForwardShorts.getInt("FRONTLINE_MEMORY_VALUE3"),
                    loopForwardShorts.getInt("FRONTLINE_MEMORY_VALUE4"),
                    loopForwardShorts.getInt("FRONTLINE_MEMORY_VALUE5"),
                    loopForwardShorts.getInt("FRONTLINE_MEMORY_VALUE6"),
                    loopForwardShorts.getInt("FRONTLINE_MEMORY_VALUE7"),
                    loopForwardShorts.getInt("FRONTLINE_MEMORY_VALUE8"),
                    loopForwardShorts.getInt("FRONTLINE_MEMORY_VALUE9"),
                    loopForwardShorts.getInt("FRONTLINE_MEMORY_VALUE10"),
                    loopForwardShorts.getInt("FRONTLINE_MEMORY_VALUE11"),
                    loopForwardShorts.getInt("FRONTLINE_MEMORY_VALUE12"),
                    loopForwardShorts.getInt("FRONTLINE_MEMORY_VALUE13"),
                    groupMemoryCode
            );
            msg = String.format("焊装计划离线回线,发送计划,下达控制命令字[%s],计划类型为[%s],钢码号为{%s},前置车身钢码号为[%s]",
                    logicValue,
                    planType,
                    bodySteelCode,
                    k_stamp_id);
            Log.Write(strServiceCode, LogLevel.Information, msg);
            recordMsg.set("msg", msg);

            return recordMsg;
        }


        /**
         * 收到命令字符5、6 mes操作
         * 修改已完成车辆状态
         * 把命令字复位为0，清空钢码号和前置车身钢码等信息
         *
         * @param strServiceCode 服务名
         * @param memoryRead     内存地址组读表值
         */
        private Record reset(String strServiceCode, Record memoryRead) {
            String msg = "";
            Record recordMsg = new Record().set("msg", "").set("error", true);
            Log.Write(strServiceCode, LogLevel.Information, String.format("控制命令字[%s]开始计算", memoryRead.getStr("LOGIC_VALUE")));

            //1.获取车身钢码号 
            String bodySteelCode = ComFun.getVpartFromMemoryValues(memoryRead);
            if (bodySteelCode.length() == 0 || bodySteelCode.equals("null")) {
                msg = "获取读表车身钢码号的值错误";
                Log.Write(strServiceCode, LogLevel.Warning, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
            }

            //2.获取焊装MB010离线回线plc计划队列
            List<Record> list_manual_plan = Db.find(ComFun.getOffAndReturnLinePlanMb010Plan());
            if (list_manual_plan == null || list_manual_plan.size() == 0) {
                msg = "没有获取到焊装MB010离线回线计划任务";
                Log.Write(strServiceCode, LogLevel.Warning, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
            }

            //2.1 获取车身钢码号
            String steelCode = list_manual_plan.get(0).get("K_STAMP_ID") + "";
            if (steelCode.length() == 0 || steelCode.equals("null")) {
                msg = "获取焊装MB010离线回线队列计划车身钢码号的值错误";
                Log.Write(strServiceCode, LogLevel.Warning, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
            }

            //2.2 校验读表中获取的钢码号和计划队列中钢码号是否一致
            if (!bodySteelCode.equals(steelCode)) {
                msg = String.format("焊装MB010离线回线检验钢码号失败：计划队列的刚码号[%s],内存读表的钢码号[%s]", steelCode, bodySteelCode);
                Log.Write(strServiceCode, LogLevel.Error, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
            }



            //2.4 钢码号转生产编码
            List<Record> bodySteelProdect =Db.find(ComFun.getDemandProductInfoFromVpartCodeCodeSql(),bodySteelCode);
            if (bodySteelProdect.size() != 1) {
                msg = String.format("根据车身钢码号[%s],获取需求产品信息异常", bodySteelCode);
                Log.Write(strServiceCode, LogLevel.Error, msg);
                recordMsg.set("msg", msg).set("error", false);
                return recordMsg;
            }

            //2.1.获取车身钢码的生产编码
            String bodySteelProdectCode = bodySteelProdect.get(0).getStr("PRODUCTION_CODE");


            //判断是离线还是回线
            String planType = memoryRead.getStr("PLAN_TYPE");
            if (("null").equals(planType) || planType.length() == 0) {
                msg = "获取焊装MB010离线回线计划类型为空";
                Log.Write(strServiceCode, LogLevel.Warning, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
            }
            switch (planType) {
                case "1":
                    //启用事务
                    boolean isSucess = Db.tx(new IAtom() {
                        @Override
                        public boolean run() throws SQLException {
                            String msg = "";
                            //1 修改完成状态
                            int update = Db.update(ComFun.getOffDealSql(), bodySteelCode);
                            if (update <= 0) {
                                msg = String.format("焊装MB010离线计划处理失败:内存读表的钢码号:[%s],生产编码:[%s]", bodySteelCode,bodySteelProdectCode);
                                Log.Write(strServiceCode, LogLevel.Error, msg);
                                recordMsg.set("msg", msg).set("error", false);
                                return false;
                            }
                            msg = String.format("焊装MB010离线回线计划处理成功:内存读表的钢码号:[%s],生产编码:[%s]", bodySteelCode,bodySteelProdectCode);
                            Log.Write(strServiceCode, LogLevel.Information, msg);
                            //2 清空写表
                            clearWrite();
                            msg = String.format("焊装MB010离线回线清空下位数据,下达控制命令字[%s]", "0");
                            Log.Write(strServiceCode, LogLevel.Information, msg);
                            recordMsg.set("msg", msg).set("error", true);

                            return true;
                        }
                    });
                    return recordMsg;
                case "2":
                    //1.获取读表前置车身钢码号
                    String loopForward = ComFun.getLoopForwardFromMemoryValues(memoryRead);
                    if (loopForward.length() == 0 || loopForward.equals("null")) {
                        msg = "获取回线读表前置车身钢码号的值错误";
                        Log.Write(strServiceCode, LogLevel.Warning, msg);
                        recordMsg.set("msg", msg);
                        return recordMsg;
                    }
                    //2.1 获取队列中前置车身钢码号生产编码
                    String preProductionCode = list_manual_plan.get(0).get("PRE_PRODUCTION_CODE") + "";
                    if (preProductionCode.length() == 0 || preProductionCode.equals("null")) {
                        msg = "获取焊装MB010离线回线队列计划前置车身生产编码的值错误";
                        Log.Write(strServiceCode, LogLevel.Warning, msg);
                        recordMsg.set("msg", msg);
                        return recordMsg;
                    }

                    //2.2 通过前置车身钢码号找到生产编码
                    List<Record> list_demand_prodect =Db.find(ComFun.getDemandProductInfoFromVpartCodeCodeSql(),loopForward);
                    if (list_demand_prodect.size() != 1) {
                        msg = String.format("根据前置车身钢码号[%s],获取需求产品信息异常", loopForward);
                        Log.Write(strServiceCode, LogLevel.Error, msg);
                        recordMsg.set("msg", msg).set("error", false);
                        return recordMsg;
                    }

                    //2.3.获取生产编码
                    String productionCode = list_demand_prodect.get(0).getStr("PRODUCTION_CODE");

                    //3.前置车身生产编码和对列中前置车身编码对比
                    if (!productionCode.equals(preProductionCode)){
                        msg = String.format("焊装MB010回线车检验前置钢码号生产编码失败：" +
                                "计划队列的前置刚码号生产编码[%s],内存读表的前置钢码号生产编码[%s]",preProductionCode,productionCode);
                        Log.Write(strServiceCode, LogLevel.Error, msg);
                        recordMsg.set("msg", msg);
                        return recordMsg;
                    }

                    //启用事务
                    Db.tx(new IAtom() {
                        @Override
                        public boolean run() throws SQLException {
                            String msg = "";
                            //2.1 修改完成状态
                            int update = Db.update(ComFun.getRetrunDealSql(), bodySteelCode,preProductionCode);
                            if (update <= 0) {
                                msg = String.format("焊装MB010回线计划处理失败:内存读表的钢码号:[%s],前置车身钢码号为[%s]:", bodySteelCode,loopForward);
                                Log.Write(strServiceCode, LogLevel.Error, msg);
                                recordMsg.set("msg", msg).set("error", false);
                                return false;
                            }
                            msg = String.format("焊装MB010离线回线计划处理成功:内存读表的钢码号:[%s],前置车身钢码号为[%s]:", bodySteelCode,loopForward);
                            Log.Write(strServiceCode, LogLevel.Information, msg);
                            //2.2发送成功，清空写表
                            clearWrite();
                            msg = String.format("焊装MB010离线回线清空下位数据,下达控制命令字[%s]", "0");
                            Log.Write(strServiceCode, LogLevel.Information, msg);
                            recordMsg.set("msg", msg).set("error", true);

                            return true;
                        }
                    });
                    return recordMsg;
                default:
                    msg = "获取焊装MB010离线回线计划类型错误";
                    Log.Write(strServiceCode, LogLevel.Warning, msg);
                    recordMsg.set("msg", msg);
                    return recordMsg;
            }

        }

        private void clearWrite(){
            //3.清空写表数据
            //3.1清空车身钢码号
            Record recordShorts = ComFun.getMemoryValuesFromVpart("");

            //3.2清空前置车身钢码
            Record loopForwardShorts = ComFun.getMemoryValuesFromLoopForward("");
            //3.3 清空写表数据
            Db.update(ComFun.getWriteMemorySql(),
                    "0",
                    "",
                    "",
                    "0",
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
                    loopForwardShorts.getInt("FRONTLINE_MEMORY_VALUE1"),
                    loopForwardShorts.getInt("FRONTLINE_MEMORY_VALUE2"),
                    loopForwardShorts.getInt("FRONTLINE_MEMORY_VALUE3"),
                    loopForwardShorts.getInt("FRONTLINE_MEMORY_VALUE4"),
                    loopForwardShorts.getInt("FRONTLINE_MEMORY_VALUE5"),
                    loopForwardShorts.getInt("FRONTLINE_MEMORY_VALUE6"),
                    loopForwardShorts.getInt("FRONTLINE_MEMORY_VALUE7"),
                    loopForwardShorts.getInt("FRONTLINE_MEMORY_VALUE8"),
                    loopForwardShorts.getInt("FRONTLINE_MEMORY_VALUE9"),
                    loopForwardShorts.getInt("FRONTLINE_MEMORY_VALUE10"),
                    loopForwardShorts.getInt("FRONTLINE_MEMORY_VALUE11"),
                    loopForwardShorts.getInt("FRONTLINE_MEMORY_VALUE12"),
                    loopForwardShorts.getInt("FRONTLINE_MEMORY_VALUE13"),
                    groupMemoryCode
            );

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

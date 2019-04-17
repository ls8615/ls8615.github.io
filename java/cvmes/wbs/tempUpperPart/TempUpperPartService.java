package cvmes.wbs.tempUpperPart;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.IAtom;
import com.jfinal.plugin.activerecord.ICallback;
import com.jfinal.plugin.activerecord.Record;
import cvmes.CvmesService;
import cvmes.common.Log;
import cvmes.common.LogLevel;
import oracle.jdbc.OracleTypes;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class TempUpperPartService {
    //子服务入口
    public void run() {
        Thread thread = new Thread(new TempUpperPartServiceThread());
        thread.start();
    }

    class TempUpperPartServiceThread extends Thread {
        // 服务编码
        private final String strServiceCode = "TempUpperPartService";

        // 一号出口移行机地址组
        private final String groupMemoryCode = "temporary.upper.part";

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

        private String DealHeader() {
            //接车计算结果信息
            String msg = "";

            //0.获取WBS内存地址写表指令
            List<Record> list_memory_write = Db.find("SELECT 1 FROM T_WBS_WRITE_MEMORY wm where wm.GROUP_MEMORY_CODE=? AND wm.DEAL_STATUS=0", groupMemoryCode);
            if (list_memory_write.size() != 0) {
                msg = String.format("wbs写表内存地址组【%s】存在未处理指令，请检查OPC通讯是否正常", groupMemoryCode);
                Log.Write(strServiceCode, LogLevel.Error, msg);
                return msg;
            }

            //1.获取WBS内存地址读表值
            List<Record> list_memory_read = Db.find("SELECT rm.* FROM T_WBS_READ_MEMORY rm where rm.GROUP_MEMORY_CODE=?", groupMemoryCode);
            if (list_memory_read.size() != 1) {
                msg = String.format("读取wbs内存地址组【%s】配置错误，请检查配置", groupMemoryCode);
                Log.Write(strServiceCode, LogLevel.Error, msg);
                return msg;
            }

            //1.1.获取WBS内存地址读表值——控制命令字值
            String menoryValue = list_memory_read.get(0).getStr("LOGIC_MEMORY_VALUE");

            //2.根据控制命令字处理
            switch (menoryValue) {
                //2.0.下位操作，MES不做处理
                case "0":
                case "1":
                case "3":
                case "5":
                case "7":
                case "9":
                case "11":
                    Log.Write(strServiceCode, LogLevel.Information, String.format("地址组[%s],控制命令字[%s]下位操作。", groupMemoryCode, menoryValue));
                    return "";
                case "2":
                    /*msg = String.format("等待扫描，地址组[%s],控制命令字[%s]", groupMemoryCode, menoryValue);
                    Log.Write(strServiceCode, LogLevel.Information, msg);
                    return msg;*/
                    return tempCarInScan(strServiceCode, list_memory_read.get(0)).getStr("msg");
                //2.1.涂装返回车弹出处理
                case "4":
                    return paintRetrunCarReign(strServiceCode, list_memory_read.get(0)).getStr("msg");

                //2.2.移动空撬道临时上件工位
                case "6":
                    return moveEmptyCarToWbs02(strServiceCode, list_memory_read.get(0)).getStr("msg");

                //2.3.进库道实车在位
                case "8":
                    return weldBreakCarReing(strServiceCode, list_memory_read.get(0)).getStr("msg");
                //2.4.进库道实车弹出,并写入空撬码
                case "10":
                    return weldBreakCarOut(strServiceCode, list_memory_read.get(0)).getStr("msg");
                default:
                    return String.format("未定义控制命令字,内存地址组[%s]，控制命令字[%s]", groupMemoryCode, menoryValue);
            }
        }

        /**
         * 当界面扫描成功后，并且把车移动临时上件工位（wbs02)时，写入命令字3
         *
         * @param strServiceCode
         * @param menoryRead
         * @return
         */
        private Record tempCarInScan(String strServiceCode, Record menoryRead) {
            //计算结果
            String msg = "";
            Record recordMsg = new Record().set("msg", "");
            Log.Write(strServiceCode, LogLevel.Information, String.format("控制命令字[%s]开始计算", menoryRead.getStr("LOGIC_MEMORY_VALUE")));

            //0.获取运行参数
            if (!TempUpperPartComFun.judgeRunStatus(strServiceCode, "wbs_temp_status")) {
                //服务暂停
                return recordMsg.set("msg", "服务暂停计算").set("error", false);
            }

            //1.获取临时上件工位的车生产编码
            List<Record> list_temp_car = Db.find("SELECT * FROM t_model_zone t WHERE t.zone_code='wbs02' AND t.product_pos=1");
            if (list_temp_car.size() != 1) {
                msg = String.format("临时上件工位无车，等待界面扫描");
                Log.Write(strServiceCode, LogLevel.Warning, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
            }

            //1.1.获取临时上件工位生产编码
            String tempProductionCode = list_temp_car.get(0).getStr("PRODUCTION_CODE");

            //2.根据内存地址组的值，获取白车身钢码号
            String vpartCode = TempUpperPartComFun.getVpartFromMemoryValues(menoryRead);
            if (vpartCode == null || vpartCode.length() == 0 || vpartCode.equals("null")) {
                msg = String.format("根据内存地址组的值[%s],获取白车身钢码号错误", menoryRead.toJson());
                Log.Write(strServiceCode, LogLevel.Error, msg);
                recordMsg.set("msg", msg).set("error", false);
                return recordMsg;
            }

            //3.获取需求产品表信息
            List<Record> list_demand_prodect = Db.find(TempUpperPartComFun.getDemandProductInfoFromVpartCodeCodeSql(), vpartCode);
            if (list_demand_prodect.size() != 1) {
                msg = String.format("根据白车身钢码号[%s],获取需求产品信息异常", vpartCode);
                Log.Write(strServiceCode, LogLevel.Error, msg);
                recordMsg.set("msg", msg).set("error", false);
                return recordMsg;
            }

            //3.1.获取生产编码
            String productionCode = list_demand_prodect.get(0).getStr("PRODUCTION_CODE");

            //4.下达指令3
            if (tempProductionCode.equals(productionCode)) {
                Record recordShorts = TempUpperPartComFun.getMemoryValuesFromVpart(vpartCode);
                int cmd = 3;
                int ret = Db.update(TempUpperPartComFun.getWriteMemorySql(),
                        cmd,
                        vpartCode,
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

                msg = String.format("处理命令字[%s]成功，内存地址组[%s],下达指令[%s]。",
                        menoryRead.getStr("LOGIC_MEMORY_VALUE"),
                        groupMemoryCode,
                        cmd);
                Log.Write(strServiceCode, LogLevel.Information, msg);
                recordMsg.set("msg", msg).set("error", true);

                return recordMsg;
            }

            return recordMsg;
        }

        /**
         * 空撬返回道返回涂装实车到临时上件工位弹出
         * 移动车辆到wbs15（离开区域），并写入5
         *
         * @param strServiceCode
         * @param menoryRead
         * @return
         */
        private Record paintRetrunCarReign(String strServiceCode, Record menoryRead) {
            //计算结果
            String msg = "";
            Record recordMsg = new Record().set("msg", "");
            Log.Write(strServiceCode, LogLevel.Information, String.format("控制命令字[%s]开始计算", menoryRead.getStr("LOGIC_MEMORY_VALUE")));

            //0.获取运行参数
            if (!TempUpperPartComFun.judgeRunStatus(strServiceCode, "wbs_temp_status")) {
                //服务暂停
                return recordMsg.set("msg", "服务暂停计算").set("error", false);
            }

            //1.根据内存地址组的值，获取白车身钢码号
            String vpartCode = TempUpperPartComFun.getVpartFromMemoryValues(menoryRead);
            if (vpartCode == null || vpartCode.length() == 0 || vpartCode.equals("null")) {
                msg = String.format("根据内存地址组的值[%s],获取白车身钢码号错误", menoryRead.toJson());
                Log.Write(strServiceCode, LogLevel.Error, msg);
                recordMsg.set("msg", msg).set("error", false);
                return recordMsg;
            }

            //2.获取需求产品表信息
            List<Record> list_demand_prodect = Db.find(TempUpperPartComFun.getDemandProductInfoFromVpartCodeCodeSql(), vpartCode);
            if (list_demand_prodect.size() != 1) {
                msg = String.format("根据白车身钢码号[%s],获取需求产品信息异常", vpartCode);
                Log.Write(strServiceCode, LogLevel.Error, msg);
                recordMsg.set("msg", msg).set("error", false);
                return recordMsg;
            }

            //2.1.获取生产编码
            String productionCode = list_demand_prodect.get(0).getStr("PRODUCTION_CODE");

            //开启事务
            Db.tx(new IAtom() {
                @Override
                public boolean run() throws SQLException {
                    String msg = "";

                    //3.下达指令
                    Record recordShorts = TempUpperPartComFun.getMemoryValuesFromVpart(vpartCode);
                    int cmd = 5;
                    int ret = Db.update(TempUpperPartComFun.getWriteMemorySql(),
                            cmd,
                            vpartCode,
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

                    //3.1.写入指令到写表失败
                    if (ret == -1) {
                        msg = String.format("下达指令到写表失败，内存地址组[%s],生产编码[%s],钢码号[%s],下达指令[%s]",
                                groupMemoryCode, productionCode, vpartCode, cmd);
                        Log.Write(strServiceCode, LogLevel.Error, msg);
                        recordMsg.set("msg", msg);
                        return false;
                    }

                    msg = String.format("处理命令字[%s]成功，内存地址组[%s],下达指令[%s]。",
                            menoryRead.getStr("LOGIC_MEMORY_VALUE"),
                            groupMemoryCode,
                            cmd);
                    Log.Write(strServiceCode, LogLevel.Information, msg);
                    recordMsg.set("msg", msg).set("error", true);

                    //3.2.移动车辆
                    String dest_code = "wbs15";
                    msg = (String) Db.execute(new ICallback() {
                        @Override
                        public Object call(Connection conn) throws SQLException {
                            //执行搬出程序结果信息
                            String msg = "";

                            CallableStatement proc = conn.prepareCall("{CALL PROC_MOVE_CAR(?,?,?,?)}");
                            proc.setString("PARA_PRODUCTION_CODE", productionCode);
                            proc.setString("PARA_DEST_ZONE", dest_code);
                            proc.setInt("PARA_IS_CHECK_POS", 1);
                            proc.registerOutParameter("PARA_MSG", OracleTypes.VARCHAR);
                            proc.execute();

                            String ret = proc.getString("PARA_MSG");
                            if (ret == null || ret.length() == 0 || ret.equals("null")) {
                                msg = "";
                            } else {
                                if (ret.length() > 0) {
                                    msg = ret;
                                }
                            }

                            return msg;
                        }
                    });
                    if (msg.length() == 0) {
                        msg = String.format("般车成功，把车[%s],般道区域[%s]", productionCode, dest_code);
                        Log.Write(strServiceCode, LogLevel.Information, msg);
                    } else {
                        msg = String.format("把车[%s],般道区域[%s]失败，原因[%s]", productionCode, dest_code, msg);
                        Log.Write(strServiceCode, LogLevel.Error, msg);
                        recordMsg.set("msg", msg).set("error", false);
                        return false;
                    }

                    return true;
                }
            });

            return recordMsg;
        }

        /**
         * 临时上件工位写入空撬码，处理空撬进入区域
         * 移动车辆到wbs02，并写入7
         *
         * @param strServiceCode
         * @param menoryRead
         * @return
         */
        private Record moveEmptyCarToWbs02(String strServiceCode, Record menoryRead) {
            //计算结果
            String msg = "";
            Record recordMsg = new Record().set("msg", "");
            Log.Write(strServiceCode, LogLevel.Information, String.format("控制命令字[%s]开始计算", menoryRead.getStr("LOGIC_MEMORY_VALUE")));

            //0.获取运行参数
            if (!TempUpperPartComFun.judgeRunStatus(strServiceCode, "wbs_temp_status")) {
                //服务暂停
                return recordMsg.set("msg", "服务暂停计算").set("error", false);
            }

            //1.根据内存地址组的值，获取白车身钢码号
            String vpartCode = TempUpperPartComFun.getVpartFromMemoryValues(menoryRead);
            if (vpartCode == null || vpartCode.length() == 0 || vpartCode.equals("null")) {
                msg = String.format("根据内存地址组的值[%s],获取白车身钢码号错误", menoryRead.toJson());
                Log.Write(strServiceCode, LogLevel.Error, msg);
                recordMsg.set("msg", msg).set("error", false);
                return recordMsg;
            }

            //1.1.空撬码写入完成，判断空撬码是否已经上传
            if (!vpartCode.substring(0, 5).equals("WBSBB")) {
                msg = String.format("空撬码不符合，命令字[%s],空撬码[%s]", menoryRead.getStr(""), vpartCode);
                Log.Write(strServiceCode, LogLevel.Error, msg);
                recordMsg.set("msg", msg);

                return recordMsg;
            }

            //2.获取需求产品表信息
            List<Record> list_demand_prodect = Db.find(TempUpperPartComFun.getDemandProductInfoFromVpartCodeCodeSql(), vpartCode);
            if (list_demand_prodect.size() != 1) {
                msg = String.format("根据白车身钢码号[%s],获取需求产品信息异常", vpartCode);
                Log.Write(strServiceCode, LogLevel.Error, msg);
                recordMsg.set("msg", msg).set("error", false);
                return recordMsg;
            }

            //2.1.获取生产编码
            String productionCode = list_demand_prodect.get(0).getStr("PRODUCTION_CODE");

            //开启事务
            Db.tx(new IAtom() {
                @Override
                public boolean run() throws SQLException {
                    String msg = "";

                    //3.下达指令
                    Record recordShorts = TempUpperPartComFun.getMemoryValuesFromVpart(vpartCode);
                    int cmd = 7;
                    int ret = Db.update(TempUpperPartComFun.getWriteMemorySql(),
                            cmd,
                            vpartCode,
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

                    //3.1.写入指令到写表失败
                    if (ret == -1) {
                        msg = String.format("下达指令到写表失败，内存地址组[%s],生产编码[%s],钢码号[%s],下达指令[%s]",
                                groupMemoryCode, productionCode, vpartCode, cmd);
                        Log.Write(strServiceCode, LogLevel.Error, msg);
                        recordMsg.set("msg", msg);
                        return false;
                    }

                    msg = String.format("处理命令字[%s]成功，内存地址组[%s],下达指令[%s]。",
                            menoryRead.getStr("LOGIC_MEMORY_VALUE"),
                            groupMemoryCode,
                            cmd);
                    Log.Write(strServiceCode, LogLevel.Information, msg);
                    recordMsg.set("msg", msg).set("error", true);


                    //3.2.移动车辆
                    String dest_code = "wbs02";
                    msg = (String) Db.execute(new ICallback() {
                        @Override
                        public Object call(Connection conn) throws SQLException {
                            //执行搬出程序结果信息
                            String msg = "";

                            CallableStatement proc = conn.prepareCall("{CALL PROC_MOVE_CAR(?,?,?,?)}");
                            proc.setString("PARA_PRODUCTION_CODE", productionCode);
                            proc.setString("PARA_DEST_ZONE", dest_code);
                            proc.setInt("PARA_IS_CHECK_POS", 1);
                            proc.registerOutParameter("PARA_MSG", OracleTypes.VARCHAR);
                            proc.execute();

                            String ret = proc.getString("PARA_MSG");
                            if (ret == null || ret.length() == 0 || ret.equals("null")) {
                                msg = "";
                            } else {
                                if (ret.length() > 0) {
                                    msg = ret;
                                }
                            }

                            return msg;
                        }
                    });
                    if (msg.length() == 0) {
                        msg = String.format("般车成功，把车[%s],般道区域[%s]", productionCode, dest_code);
                        Log.Write(strServiceCode, LogLevel.Information, msg);
                    } else {
                        msg = String.format("把车[%s],般道区域[%s]失败，原因[%s]", productionCode, dest_code, msg);
                        Log.Write(strServiceCode, LogLevel.Error, msg);
                        recordMsg.set("msg", msg).set("error", false);
                        return false;
                    }

                    return true;
                }
            });

            return recordMsg;
        }


        /**
         * 临时上件工位，入库道实车在位处理
         * A：把车移出区域（移到wbs15），然后再把空撬移入区域(移到wbs02)
         *
         * @param strServiceCode
         * @param menoryRead
         * @return
         */
        private Record weldBreakCarReing(String strServiceCode, Record menoryRead) {
            //计算结果
            String msg = "";
            Record recordMsg = new Record().set("msg", "");
            Log.Write(strServiceCode, LogLevel.Information, String.format("控制命令字[%s]开始计算", menoryRead.getStr("LOGIC_MEMORY_VALUE")));

            //0.获取运行参数
            if (!TempUpperPartComFun.judgeRunStatus(strServiceCode, "wbs_temp_status")) {
                //服务暂停
                return recordMsg.set("msg", "服务暂停计算").set("error", false);
            }

            //1.根据内存地址组的值，获取白车身钢码号
            String vpartCode = TempUpperPartComFun.getVpartFromMemoryValues(menoryRead);
            if (vpartCode == null || vpartCode.length() == 0 || vpartCode.equals("null")) {
                msg = String.format("根据内存地址组的值[%s],获取白车身钢码号错误", menoryRead.toJson());
                Log.Write(strServiceCode, LogLevel.Error, msg);
                recordMsg.set("msg", msg).set("error", false);
                return recordMsg;
            }

            //2.获取需求产品表信息
            List<Record> list_demand_prodect = Db.find(TempUpperPartComFun.getDemandProductInfoFromVpartCodeCodeSql(), vpartCode);
            if (list_demand_prodect.size() != 1) {
                msg = String.format("根据白车身钢码号[%s],获取需求产品信息异常", vpartCode);
                Log.Write(strServiceCode, LogLevel.Error, msg);
                recordMsg.set("msg", msg).set("error", false);
                return recordMsg;
            }

            //2.1.获取生产编码
            String productionCode = list_demand_prodect.get(0).getStr("PRODUCTION_CODE");

            //3.获取在位车是否在区域中，如果不在，跳过处理
            Record icnt = Db.findFirst("SELECT COUNT(1) ICNT FROM t_model_zone t WHERE t.production_code=?", productionCode);
            //3.1.获取车区域数据失败
            if (icnt == null) {
                msg = String.format("获取车区域数据失败，生产编码[%s]", productionCode);
                Log.Write(strServiceCode, LogLevel.Error, msg);
                recordMsg.set("msg", msg);
                return recordMsg;
            }

            //4.车辆移动
            //4.1.先把车移到wbs15（离开）
            if (icnt.getInt("ICNT") == 1) {
                String dest_code = "wbs15";
                msg = (String) Db.execute(new ICallback() {
                    @Override
                    public Object call(Connection conn) throws SQLException {
                        //执行搬出程序结果信息
                        String msg = "";

                        CallableStatement proc = conn.prepareCall("{CALL PROC_MOVE_CAR(?,?,?,?)}");
                        proc.setString("PARA_PRODUCTION_CODE", productionCode);
                        proc.setString("PARA_DEST_ZONE", dest_code);
                        proc.setInt("PARA_IS_CHECK_POS", 1);
                        proc.registerOutParameter("PARA_MSG", OracleTypes.VARCHAR);
                        proc.execute();

                        String ret = proc.getString("PARA_MSG");
                        if (ret == null || ret.length() == 0 || ret.equals("null")) {
                            msg = "";
                        } else {
                            if (ret.length() > 0) {
                                msg = ret;
                            }
                        }

                        return msg;
                    }
                });
                if (msg.length() == 0) {
                    msg = String.format("般车成功，把车[%s],般道区域[%s]", productionCode, dest_code);
                    Log.Write(strServiceCode, LogLevel.Information, msg);
                } else {
                    msg = String.format("把车[%s],般道区域[%s]失败，原因[%s]", productionCode, dest_code, msg);
                    Log.Write(strServiceCode, LogLevel.Error, msg);
                    recordMsg.set("msg", msg).set("error", false);
                    return recordMsg;
                }
            }

            //4.2.获取下达指令值
            Record rec_service = Db.findFirst("SELECT to_number(t.service_para1_value) CMD FROM t_sys_service t WHERE t.service_code=?", strServiceCode);
            if (rec_service == null) {
                msg = String.format("获取空撬连续投入台数和间隔台数的参数值失败");
                Log.Write(strServiceCode, LogLevel.Error, msg);
                recordMsg.set("msg", msg).set("error", false);
            }

            //4.2.1.下达指令值
            int cmd = rec_service.getInt("CMD");

            //4.3.把车移到wbs02（临时上件工位）
            if(cmd==9){
                String dest_code = "wbs02";
                msg = (String) Db.execute(new ICallback() {
                    @Override
                    public Object call(Connection conn) throws SQLException {
                        //执行搬出程序结果信息
                        String msg = "";

                        CallableStatement proc = conn.prepareCall("{CALL PROC_MOVE_CAR(?,?,?,?)}");
                        proc.setString("PARA_PRODUCTION_CODE", productionCode);
                        proc.setString("PARA_DEST_ZONE", dest_code);
                        proc.setInt("PARA_IS_CHECK_POS", 1);
                        proc.registerOutParameter("PARA_MSG", OracleTypes.VARCHAR);
                        proc.execute();

                        String ret = proc.getString("PARA_MSG");
                        if (ret == null || ret.length() == 0 || ret.equals("null")) {
                            msg = "";
                        } else {
                            if (ret.length() > 0) {
                                msg = ret;
                            }
                        }

                        return msg;
                    }
                });
                if (msg.length() == 0) {
                    msg = String.format("般车成功，把车[%s],般道区域[%s]", productionCode, dest_code);
                    Log.Write(strServiceCode, LogLevel.Information, msg);
                } else {
                    msg = String.format("把车[%s],般道区域[%s]失败，原因[%s]", productionCode, dest_code, msg);
                    Log.Write(strServiceCode, LogLevel.Error, msg);
                    recordMsg.set("msg", msg).set("error", false);
                    return recordMsg;
                }
            }

            //5.下达指令
            Record recordShorts = TempUpperPartComFun.getMemoryValuesFromVpart(vpartCode);
            int ret = Db.update(TempUpperPartComFun.getWriteMemorySql(),
                    cmd,
                    vpartCode,
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

            msg = String.format("处理命令字[%s]成功，内存地址组[%s],下达指令[%s]。",
                    menoryRead.getStr("LOGIC_MEMORY_VALUE"),
                    groupMemoryCode,
                    cmd);
            Log.Write(strServiceCode, LogLevel.Information, msg);
            recordMsg.set("msg", msg).set("error", true);

            return recordMsg;

        }

        /**
         * 临时上件工位，入库道实车在位弹出，并写入空撬码处理
         * A：把临时上件工位实车移出区域（移到wbs15），把空撬移入区域（移到wbs02）
         *
         * @param strServiceCode
         * @param menoryRead
         * @return
         */
        private Record weldBreakCarOut(String strServiceCode, Record menoryRead) {
            //计算结果
            String msg = "";
            Record recordMsg = new Record().set("msg", "");
            Log.Write(strServiceCode, LogLevel.Information, String.format("控制命令字[%s]开始计算", menoryRead.getStr("LOGIC_MEMORY_VALUE")));

            //0.获取运行参数
            if (!TempUpperPartComFun.judgeRunStatus(strServiceCode, "wbs_temp_status")) {
                //服务暂停
                return recordMsg.set("msg", "服务暂停计算").set("error", false);
            }

            //1.根据内存地址组的值，获取白车身钢码号
            String vpartCode = TempUpperPartComFun.getVpartFromMemoryValues(menoryRead);
            if (vpartCode == null || vpartCode.length() == 0 || vpartCode.equals("null")) {
                msg = String.format("根据内存地址组的值[%s],获取白车身钢码号错误", menoryRead.toJson());
                Log.Write(strServiceCode, LogLevel.Error, msg);
                recordMsg.set("msg", msg).set("error", false);
                return recordMsg;
            }

            //1.1.空撬码写入完成，判断空撬码是否已经上传
            if (!vpartCode.substring(0, 5).equals("WBSBB")) {
                msg = String.format("空撬码不符合，命令字[%s],空撬码[%s]", menoryRead.getStr(""), vpartCode);
                Log.Write(strServiceCode, LogLevel.Error, msg);
                recordMsg.set("msg", msg);

                return recordMsg;
            }

            //2.获取需求产品表信息
            List<Record> list_demand_prodect = Db.find(TempUpperPartComFun.getDemandProductInfoFromVpartCodeCodeSql(), vpartCode);
            if (list_demand_prodect.size() != 1) {
                msg = String.format("根据白车身钢码号[%s],获取需求产品信息异常", vpartCode);
                Log.Write(strServiceCode, LogLevel.Error, msg);
                recordMsg.set("msg", msg).set("error", false);
                return recordMsg;
            }

            //2.1.获取生产编码
            String productionCode = list_demand_prodect.get(0).getStr("PRODUCTION_CODE");

            //3.获取临时上件工位生产编码
            List<Record> list_zone_wbs02 = Db.find("select PRODUCTION_CODE from t_model_zone t where  t.zone_code='wbs02' AND t.production_code IS NOT NULL AND t.product_pos=1");
            if (list_zone_wbs02.size() != 1) {
                msg = String.format("区域中临时上件工位没有车");
                Log.Write(strServiceCode, LogLevel.Warning, msg);
            } else if (productionCode.equals(list_zone_wbs02.get(0).getStr("PRODUCTION_CODE"))) {

            } else {
                //3.1.把临时上件工位实车移出区域
                String dest_code = "wbs15";
                msg = (String) Db.execute(new ICallback() {
                    @Override
                    public Object call(Connection conn) throws SQLException {
                        //执行搬出程序结果信息
                        String msg = "";

                        CallableStatement proc = conn.prepareCall("{CALL PROC_MOVE_CAR(?,?,?,?)}");
                        proc.setString("PARA_PRODUCTION_CODE", list_zone_wbs02.get(0).getStr("PRODUCTION_CODE"));
                        proc.setString("PARA_DEST_ZONE", dest_code);
                        proc.setInt("PARA_IS_CHECK_POS", 1);
                        proc.registerOutParameter("PARA_MSG", OracleTypes.VARCHAR);
                        proc.execute();

                        String ret = proc.getString("PARA_MSG");
                        if (ret == null || ret.length() == 0 || ret.equals("null")) {
                            msg = "";
                        } else {
                            if (ret.length() > 0) {
                                msg = ret;
                            }
                        }

                        return msg;
                    }
                });
                if (msg.length() == 0) {
                    msg = String.format("般车成功，把车[%s],般道区域[%s]", productionCode, dest_code);
                    Log.Write(strServiceCode, LogLevel.Information, msg);
                } else {
                    msg = String.format("把车[%s],般道区域[%s]失败，原因[%s]", productionCode, dest_code, msg);
                    Log.Write(strServiceCode, LogLevel.Error, msg);
                    recordMsg.set("msg", msg).set("error", false);
                    return recordMsg;
                }
            }

            //4.把空撬移入临时上件工位（wbs02）
            String dest_code = "wbs02";
            msg = (String) Db.execute(new ICallback() {
                @Override
                public Object call(Connection conn) throws SQLException {
                    //执行搬出程序结果信息
                    String msg = "";

                    CallableStatement proc = conn.prepareCall("{CALL PROC_MOVE_CAR(?,?,?,?)}");
                    proc.setString("PARA_PRODUCTION_CODE", productionCode);
                    proc.setString("PARA_DEST_ZONE", dest_code);
                    proc.setInt("PARA_IS_CHECK_POS", 1);
                    proc.registerOutParameter("PARA_MSG", OracleTypes.VARCHAR);
                    proc.execute();

                    String ret = proc.getString("PARA_MSG");
                    if (ret == null || ret.length() == 0 || ret.equals("null")) {
                        msg = "";
                    } else {
                        if (ret.length() > 0) {
                            msg = ret;
                        }
                    }

                    return msg;
                }
            });
            if (msg.length() == 0) {
                msg = String.format("般车成功，把车[%s],般道区域[%s]", productionCode, dest_code);
                Log.Write(strServiceCode, LogLevel.Information, msg);
            } else {
                msg = String.format("把车[%s],般道区域[%s]失败，原因[%s]", productionCode, dest_code, msg);
                Log.Write(strServiceCode, LogLevel.Error, msg);
                recordMsg.set("msg", msg).set("error", false);
                return recordMsg;
            }

            //5.下达指令
            Record recordShorts = TempUpperPartComFun.getMemoryValuesFromVpart(vpartCode);
            int cmd = 11;
            int ret = Db.update(TempUpperPartComFun.getWriteMemorySql(),
                    cmd,
                    vpartCode,
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

            msg = String.format("处理命令字[%s]成功，内存地址组[%s],下达指令[%s]。",
                    menoryRead.getStr("LOGIC_MEMORY_VALUE"),
                    groupMemoryCode,
                    cmd);
            Log.Write(strServiceCode, LogLevel.Information, msg);
            recordMsg.set("msg", msg).set("error", true);
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

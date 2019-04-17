package cvmes.vips;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.IAtom;
import com.jfinal.plugin.activerecord.Record;
import cvmes.CvmesService;
import cvmes.common.Log;
import cvmes.common.LogLevel;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;

public class VipsSupplierInfoDataEdit {
    public void run() {
        Thread thread = new Thread(new VipsSupplierInfoDataEditThread());
        thread.start();
    }

    class VipsSupplierInfoDataEditThread extends Thread {
        // 服务编码
        private final String strServiceCode = "VipsSupplierInfoDataEdit";

        // 业务操作
        private void runBll(Record rec_service) {
            // 获取待处理数据（一次500条以内）
            List<Record> list = Db.find("select * from T_INF_FROM_PARTS_SUPPLIER where DEAL_STATUS=0 and rownum<=500");
            if (list == null || list.size() == 0) return;

            // 逐条处理数据
            for (Record sub : list) {
                try {
                    Record rec = Db.findFirst("select count(1) as CNT from T_BASE_SUPPLIER where SUPPLIER_CODE=?", sub.getStr("ID_CODE"));
                    int cnt = rec.getInt("CNT");
                    boolean flag = Db.tx(new IAtom() {
                        public boolean run() throws SQLException {
                            int ret = 0;
                            if (cnt > 0) {
                                // 已存在，更新
                                ret = Db.update("update T_BASE_SUPPLIER set SUPPLIER_NAME =?, SUPPLIER_TYPE=? where SUPPLIER_CODE=?", sub.getStr("SUPPILER_NAME"), sub.getStr("SUPPILER_TYPE"), sub.getStr("ID_CODE"));
                            } else {
                                // 不存在，插入
                                ret = Db.update("insert into T_BASE_SUPPLIER(ID, SUPPLIER_CODE, SUPPLIER_NAME, WORKSHOP_CODE, SUPPLIER_TYPE) values(sys_guid(), ?, ?, 'zz01', ?)", sub.getStr("ID_CODE"), sub.getStr("SUPPILER_NAME"), sub.getStr("SUPPILER_TYPE"));
                            }

                            if (ret == 0) return false;

                            sub.set("DEAL_STATUS", 1).set("DEAL_TIME", new Date());
                            Db.update("T_INF_FROM_PARTS_SUPPLIER", sub);
                            return true;
                        }
                    });

                    // 更新最后操作信息
                    if (flag) {
                        Db.update("update T_SYS_SERVICE set LAST_OPERATE_TIME=sysdate, LAST_OPERATE_INFO=? where SERVICE_CODE=?", String.format("处理供应商识别信息成功，ID【%s】，识别码（供应商编码）【%s】", sub.getStr("ID"), sub.getStr("ID_CODE")), strServiceCode);
                    } else {
                        Db.update("update T_SYS_SERVICE set LAST_OPERATE_TIME=sysdate, LAST_OPERATE_INFO=? where SERVICE_CODE=?", String.format("处理供应商识别信息失败，ID【%s】，识别码（供应商编码）【%s】", sub.getStr("ID"), sub.getStr("ID_CODE")), strServiceCode);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Db.update("update T_SYS_SERVICE set LAST_OPERATE_TIME=sysdate, LAST_OPERATE_INFO=? where SERVICE_CODE=?", String.format("处理供应商识别信息异常，ID【%s】，识别码（供应商编码）【%s】", sub.getStr("ID"), sub.getStr("ID_CODE")), strServiceCode);
                    Log.Write(strServiceCode, LogLevel.Error, String.format("处理供应商识别信息异常，ID【%s】，识别码（供应商编码）【%s】，原因【%s】", sub.getStr("ID"), sub.getStr("ID_CODE"), e.getMessage()));
                }
            }
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
                    Log.Write(strServiceCode, LogLevel.Error, String.format("业务操作发生异常，原因【%s】", e.getMessage()));
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

package cvmes.tzavi;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.IAtom;
import com.jfinal.plugin.activerecord.Record;
import cvmes.common.AbstractSubServiceThread;
import cvmes.common.Log;
import cvmes.common.LogLevel;

import java.sql.SQLException;
import java.util.List;

public class TzaviPassingDataEdit extends AbstractSubServiceThread {
    private String msg = "";
    private String production_code = "";

    @Override
    public void initServiceCode() {
        this.strServiceCode = "TzaviPassingDataEdit";
    }

    @Override
    public String runBll(Record rec_service) throws Exception {
        msg = "";

        // 删除已处理并且时间超过1个月的数据
        Db.update("delete from T_INF_FROM_PAINTAVI_POSITON where DEAL_STATUS=1 AND ceil(sysdate-DEAL_TIME)>30");

        // 获取待处理的涂装AVI在制信息数据（一次处理500条以内）
        List<Record> list = Db.find("select * from T_INF_FROM_PAINTAVI_POSITON where DEAL_STATUS=0 and rownum<=500 order by MOVE_TIME");
        if (list == null || list.size() == 0) {
            return msg;
        }

        // 逐条处理涂装AVI在制信息
        int ok = 0;
        int ng = 0;

        for (Record sub : list) {
            Record recPass = Db.findFirst("SELECT PRODUCTION_CODE FROM T_PLAN_DEMAND_PRODUCT WHERE K_STAMP_ID=?", sub.getStr("FIN_NO"));
            if (recPass != null) {
                production_code = recPass.getStr("PRODUCTION_CODE");
            } else {
                production_code = sub.getStr("FIN_NO").substring(sub.getStr("FIN_NO").length() - 11);
            }

            boolean ret = Db.tx(new IAtom() {
                @Override
                public boolean run() throws SQLException {
                    Db.update("UPDATE T_MODEL_PAINT_ZONE SET PRODUCTION_CODE='' WHERE PRODUCTION_CODE=?", production_code);
                    Db.update("UPDATE T_MODEL_PAINT_ZONE SET PRODUCTION_CODE=? WHERE STATION_CODE=?", production_code, sub.getStr("POSITION_NAME"));

                    Db.update("update T_INF_FROM_PAINTAVI_POSITON set DEAL_STATUS='1', DEAL_TIME=sysdate where ID=?", sub.getStr("ID"));
                    return true;
                }
            });

            if (ret) {
                ok++;
            } else {
                ng++;
            }
        }

        msg = String.format("共处理数据【%d】条，其中成功【%d】条，失败【%d】条", ok + ng, ok, ng);
        return msg;
    }
}

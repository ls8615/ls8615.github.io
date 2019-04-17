package cvmes.pbs.pbsOutService;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;
import cvmes.common.AbstractSubServiceThread;
import cvmes.common.Log;
import cvmes.common.LogLevel;

public class PbsOutService extends AbstractSubServiceThread {
    //小库区出口移行机
    SmallAreaService smallAreaService;

    BitAreaService bitAreaService;

    @Override
    public void initServiceCode() {
        this.strServiceCode = "PbsOutService";
        smallAreaService = new SmallAreaService(this.strServiceCode);
        bitAreaService = new BitAreaService(this.strServiceCode);
    }

    @Override
    public String runBll(Record rec_service) throws Exception {
        String msg = "";
        Record retMsg = new Record();


        //1.执行小库区逻辑
        retMsg.set("msgSmall", smallAreaService.runBll(rec_service));


        //2.大库区出口移行机逻辑
        retMsg.set("msgBit", bitAreaService.runBll(rec_service));

        msg = String.format("小库区计算结果{%s}，大库区计算结果{%s}", retMsg.getStr("msgSmall"), retMsg.getStr("msgBit"));
        Log.Write(strServiceCode, LogLevel.Information, msg);

        if(retMsg.getStr("msgSmall").length()!=0){
            Db.update("update T_SYS_SERVICE set LAST_OPERATE_TIME=sysdate,SERVICE_PARA2_VALUE=? where SERVICE_CODE=?", retMsg.getStr("msgSmall"), strServiceCode);
        }

        if(retMsg.getStr("msgBit").length()!=0){
            Db.update("update T_SYS_SERVICE set LAST_OPERATE_TIME=sysdate,SERVICE_PARA3_VALUE=? where SERVICE_CODE=?", retMsg.getStr("msgBit"), strServiceCode);
        }

        return "";
    }
}

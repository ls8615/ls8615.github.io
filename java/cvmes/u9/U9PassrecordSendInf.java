package cvmes.u9;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;
import cvmes.common.AbstractSubServiceThread;
import cvmes.common.Log;
import cvmes.common.LogLevel;

import java.util.List;

public class U9PassrecordSendInf extends AbstractSubServiceThread {
    private String msg = "";

    @Override
    public void initServiceCode() {
        this.strServiceCode = "U9PassrecordSendInf";
    }

    @Override
    public String runBll(Record rec_service) throws Exception {
        msg = "";

        List<Record> list = Db.find("select * from T_INF_TO_U9_PASSREC where DEAL_STATUS = '0'");
        if (list == null || list.size() == 0) {
            return msg;
        }

        StringBuffer sql = new StringBuffer();
        sql.append("insert into U9_SM_tracking(CreatedOn, Businessdate, CreatedBy, Vin, Memo, ProductStatusEnum,");
        sql.append(" SysVersion, ProductScanPlace, CreatedBySys, State, CarModelNO, ProductNO, ItemCode, ScanLinePlace)");
        sql.append(" values(getdate(), ?, 'CVMES', ?, ?, ?, '0', '4', 'CVMES', '0', ?, ?, ?, ?)");

        for (Record sub : list) {
            Db.use("u9").update(sql.toString(),
                    sub.getDate("PASS_TIME"),
                    sub.getStr("STEEL_NO"),
                    sub.getStr("REMARK"),
                    sub.getInt("PASS_TYPE"),
                    sub.getStr("CARTYPE_CODE"),
                    sub.getStr("NOTICE_CARTYPE"),
                    sub.getStr("CARBODY_CODE"),
                    sub.getInt("PASS_POS").toString());

            Db.update("update T_INF_TO_U9_PASSREC set DEAL_STATUS = '1', DEAL_TIME = sysdate where ID = ?", sub.getStr("ID"));

            msg = String.format("发送数据成功，钢码号【%s】，过点类型【%d】（2=焊装下线；5=涂装上线）， 过点时间【%s】",
                    sub.getStr("STEEL_NO"),
                    sub.getInt("PASS_TYPE"),
                    sub.getDate("PASS_TIME").toString());
            Log.Write(strServiceCode, LogLevel.Information, msg);
        }

        return msg;
    }
}

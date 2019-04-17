package cvmes.partl;

import java.util.List;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;

import cvmes.common.AbstractSubServiceThread;
import cvmes.common.Log;
import cvmes.common.LogLevel;

public class PartlFifthwheelDataEdit extends AbstractSubServiceThread {
    @Override
    public void initServiceCode() {
        this.strServiceCode = "PartlFifthwheelDataEdit";
    }

    @Override
    public String runBll(Record rec_service) throws Exception {
        String msg = "";

        List<Record> list1 = Db.find("select id, BOM_CODE,MODULE_CODE, MODULE_NAME, PARTS_NUMS,SUPPIER_CODE from T_INF_FROM_PARTL_TRACSEAT where deal_status = '0' and ROWNUM <=1");

        int ok1 = 0;
        int ng1 = 0;
        if (list1.size() > 0) {
            for (Record sub : list1) {
                boolean flag = dealLbjQyzBjService(sub);
                if (flag) {
                    Db.update(
                            "UPDATE T_INF_FROM_PARTL_TRACSEAT SET deal_status = '1' WHERE id =?",
                            sub.getStr("ID"));
                    ok1++;
                } else {
                    Db.update(
                            "UPDATE T_INF_FROM_PARTL_TRACSEAT SET deal_status = '2' WHERE id =?",
                            sub.getStr("ID"));
                    ng1++;
                }
            }

            msg = String.format("共处理数据【%d】条，其中成功【%d】条，失败【%d】条", ok1 + ng1, ok1, ng1);
        }

        return msg;
    }

    private boolean dealLbjQyzBjService(Record rec) {
        try {
            List<Record> list_qyz = Db.find("select t.id,t.BOM_CODE from T_TRACTION_SEAT t where t.BOM_CODE = ? ", rec.getStr("BOM_CODE"));
            if (list_qyz.size() == 0) {
                String uuid = java.util.UUID.randomUUID().toString();
                int num1 = Db.update(
                        "insert into T_TRACTION_SEAT(ID,BOM_CODE,MODULE_CODE,MODULE_NAME,PARTS_NUMS,SUPPIER_CODE) values(?,?,?,?,?,?)",
                        uuid, rec.getStr("BOM_CODE"), rec.getStr("MODULE_CODE"), rec.getStr("MODULE_NAME"), rec.getInt("PARTS_NUMS"),
                        rec.getStr("SUPPIER_CODE")
                );
                if (num1 > 0) {
                    return true;
                } else {
                    return false;
                }
            } else {
                int num1 = Db.update(
                        " update T_TRACTION_SEAT set  MODULE_CODE=?,MODULE_NAME=?,PARTS_NUMS=?,SUPPIER_CODE=?" +
                                " where BOM_CODE=? ",

                        rec.getStr("MODULE_CODE"), rec.getStr("MODULE_NAME"), rec.getInt("PARTS_NUMS"),
                        rec.getStr("SUPPIER_CODE"), rec.getStr("BOM_CODE"));

                if (num1 > 0) {
                    return true;
                } else {
                    return false;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.Write(strServiceCode, LogLevel.Error, String.format(
                    "处理牵引座接口表失败，接口表ID【%s】，模块编码【%s】，异常原因【%s】",
                    rec.getStr("ID"), rec.getStr("BOM_CODE"),
                    e.getMessage()));
            return false;
        }
    }
}

package cvmes.partl;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.IAtom;
import com.jfinal.plugin.activerecord.Record;
import cvmes.common.AbstractSubServiceThread;
import cvmes.common.Log;
import cvmes.common.LogLevel;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class PartlSingleCarBomDataEdit extends AbstractSubServiceThread {
    private String msg = "";

    @Override
    public void initServiceCode() {
        this.strServiceCode = "PartlSingleCarBomDataEdit";
    }

    @Override
    public String runBll(Record rec_service) throws Exception {
        msg = "";

        // 获取一个完整BOM
        List<Record> list = Db.find("SELECT * FROM T_INF_FROM_PART_SINGLECAR WHERE DEAL_STATUS = 0 AND BOM_ID = (SELECT BOM_ID FROM T_INF_FROM_PART_SINGLECAR WHERE DEAL_STATUS = 0 AND rownum = 1)");
        if (list == null || list.size() == 0) {
            return msg;
        }

        // 去重
        List<Record> listBom = new ArrayList<>();
        for (Record sub : list) {
            boolean flag = false;
            for (Record subBom : listBom) {
                if (sub.getStr("BOM_ID").equals(subBom.getStr("BOM_ID"))
                        && sub.getStr("PART_NO").equals(subBom.getStr("PART_NO"))
                        && sub.getInt("SINGLE_NUMBER").equals(subBom.getInt("SINGLE_NUMBER"))) {
                    flag = true;
                    break;
                }
            }

            if (!flag) {
                listBom.add(sub);
            }
        }

        // 数量为负冲减
        List<Record> listRemove = new ArrayList<>();
        for (Record sub1 : listBom) {
            for (Record sub2 : listBom) {
                if (sub1.getStr("PART_NO").equals(sub2.getStr("PART_NO"))
                        && (sub1.getInt("SINGLE_NUMBER") + sub2.getInt("SINGLE_NUMBER")) == 0) {
                    listRemove.add(sub1);
                    break;
                }
            }
        }

        Iterator<Record> it = listBom.iterator();
        while (it.hasNext()) {
            Record rec = it.next();
            for (Record subRemove : listRemove) {
                if (rec.getStr("ID").equals(subRemove.getStr("ID"))) {
                    it.remove();
                    break;
                }
            }
        }

        // 写业务表
        boolean ret = Db.tx(new IAtom() {
            public boolean run() throws SQLException {
                // 删除BOM表数据
                Db.update("delete from t_base_bom where bom_code = ?", listBom.get(0).getStr("BOM_ID"));

                // 写入BOM表
                for (Record rec : listBom) {
                    Db.update("insert into t_base_bom(ID, BOM_CODE, MATERIAL_CODE, MATERIAL_NUM, STATION_CODE, BOM_TYPE, K_ASSEMBLY_MARK) values(?, ?, ?, ?, ?, ?, ?)",
                            java.util.UUID.randomUUID().toString(),
                            rec.getStr("BOM_ID"),
                            rec.getStr("PART_NO"),
                            rec.getInt("SINGLE_NUMBER"),
                            rec.getStr("ASSEMBLY_STATION"),
                            "0",
                            rec.getStr("ASSEMBLY_MARK")
                    );
                }

                // 更新处理状态
                for (Record sub : list) {
                    Db.update("update T_INF_FROM_PART_SINGLECAR set DEAL_STATUS=1, DEAL_TIME=sysdate where id=?", sub.getStr("ID"));
                }
                return true;
            }
        });

        if (ret) {
            msg = String.format("处理BOM【%s】成功，记录数量【%d】", listBom.get(0).getStr("BOM_ID"), listBom.size());
            Log.Write(strServiceCode, LogLevel.Information, msg);
        } else {
            msg = String.format("处理BOM【%s】失败，记录数量【%d】", listBom.get(0).getStr("BOM_ID"), listBom.size());
            Log.Write(strServiceCode, LogLevel.Error, msg);

            Db.tx(new IAtom() {
                public boolean run() throws SQLException {
                    for (Record sub : list) {
                        Db.update("update T_INF_FROM_PART_SINGLECAR set DEAL_STATUS=2, DEAL_TIME=sysdate where id=?", sub.getStr("ID"));
                    }
                    return true;
                }
            });
        }

        return msg;
    }
}

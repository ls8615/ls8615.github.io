package cvmes.kaimu;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.IAtom;
import com.jfinal.plugin.activerecord.Record;
import cvmes.common.AbstractSubServiceThread;
import cvmes.common.Log;
import cvmes.common.LogLevel;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public class KaimuBomInf extends AbstractSubServiceThread {
    private String msg = "";
    private String item_version = "";
    private String pcode = "";
    private String dcode = "";

    @Override
    public void initServiceCode() {
        this.strServiceCode = "KaimuBomInf";
    }

    @Override
    public String runBll(Record rec_service) throws Exception {
        msg = "";

        // 获取待同步的bom批次号数据
        List<Record> list = Db.use("kaimu").find("SELECT BATCH FROM PDM_MES_BOM WHERE FLAG = 'D' group by BATCH ORDER BY BATCH asc");
        if (list == null || list.size() == 0) {
            return msg;
        }

        for (Record sub : list) {
            // 获取一个完整的bom数据
            List<Record> list_bom = Db.use("kaimu").find("select * from PDM_MES_BOM where FLAG = 'D' and BATCH = ?",
                    sub.getStr("BATCH"));
            if (list_bom == null || list_bom.size() == 0) {
                msg = String.format("批次号【%s】数据获取失败", sub.getStr("BATCH"));
                Log.Write(strServiceCode, LogLevel.Error, msg);
                continue;
            }

            // 获取系统时间
            Record rec_time = Db.findFirst("select to_char(sysdate, 'yyyymmdd') as curtime from dual");
            if (rec_time == null) {
                msg = String.format("获取系统时间失败，批次号【%s】", sub.getStr("BATCH"));
                Log.Write(strServiceCode, LogLevel.Error, msg);
                continue;
            }

            // 计算要使用的版本号
            Record rec = Db.findFirst("SELECT CURRENTVERSION FROM (SELECT CURRENTVERSION FROM T_BASE_BOM_VERSION GROUP BY CURRENTVERSION ORDER BY CURRENTVERSION DESC) t WHERE rownum = 1");
            if (rec == null) {
                msg = String.format("获取当前最大版本号失败，批次号【%s】", sub.getStr("BATCH"));
                Log.Write(strServiceCode, LogLevel.Warning, msg);

                item_version = getVersionByDate(rec_time.getStr("curtime"));
            } else {
                if (rec_time.getStr("curtime").equals(rec.getStr("CURRENTVERSION").substring(0, 8))) {
                    item_version = getVersionByVersion(rec.getStr("CURRENTVERSION"));
                } else {
                    item_version = getVersionByDate(rec_time.getStr("curtime"));
                }
            }

            // 写入bom
            boolean ret1 = Db.tx(new IAtom() {
                public boolean run() throws SQLException {
                    for (Record sub_bom : list_bom) {
                        pcode = sub_bom.getStr("PCODE");
                        if (pcode.contains("-LD")) {
                            pcode = pcode.substring(0, pcode.length() - 3).trim();
                        }

                        dcode = sub_bom.getStr("DCODE");
                        if (dcode.contains("-LD")) {
                            dcode = dcode.substring(0, dcode.length() - 3).trim();
                        }

                        Db.update("insert into t_base_bom(ID, BOM_CODE, MATERIAL_CODE, MATERIAL_NUM, STATION_CODE, BOM_TYPE, ITEMVERSION, BATCH_NUM, ERROR_NUM, ERROR_REMARK) values(sys_guid(), ?, ?, ?, ?, '2', ?, ?, ?, ?)",
                                pcode,
                                dcode,
                                sub_bom.getNumber("NR"),
                                sub_bom.getStr("GWH"),
                                item_version,
                                sub_bom.getStr("BATCH"),
                                sub_bom.getStr("PROPERTY02"),
                                sub_bom.getStr("PROPERTY03"));
                    }

                    Db.update("insert into T_BASE_BOM_VERSION(UUIDVERSION, CURRENTDATE, CURRENTVERSION) values(?, ?, ?)",
                            UUID.randomUUID().toString(),
                            rec_time.getStr("curtime"),
                            item_version);

                    // 提交事务
                    return true;
                }
            });

            // 更新同步状态
            boolean ret2 = Db.use("kaimu").tx(new IAtom() {
                public boolean run() throws SQLException {
                    for (Record sub_bom_commit : list_bom) {
                        Db.use("kaimu").update("update PDM_MES_BOM set FLAG = 'E', SYNCHROTIME = getdate() where ID = ?", sub_bom_commit.getStr("ID"));
                    }

                    // 提交事务
                    return true;
                }
            });

            if (ret1) {
                msg = String.format("数据同步成功，批次号【%s】，记录数量【%d】", sub.getStr("BATCH"), list_bom.size());
                Log.Write(strServiceCode, LogLevel.Information, msg);
            } else {
                msg = String.format("数据同步失败，批次号【%s】，记录数量【%d】", sub.getStr("BATCH"), list_bom.size());
                Log.Write(strServiceCode, LogLevel.Error, msg);
            }
        }

        return msg;
    }

    private String getVersionByVersion(String strVersion) {
        Long lng = Long.parseLong(strVersion);
        lng++;
        return lng.toString();
    }

    private String getVersionByDate(String strDate) {
        return strDate + "001";
    }
}

package cvmes.kaimu;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.IAtom;
import com.jfinal.plugin.activerecord.Record;
import cvmes.common.AbstractSubServiceThread;
import cvmes.common.Log;
import cvmes.common.LogLevel;

import java.sql.SQLException;
import java.util.List;

public class KaimuTechnologyBTInf extends AbstractSubServiceThread {
    private String msg = "";

    @Override
    public void initServiceCode() {
        this.strServiceCode = "KaimuTechnologyBTInf";
    }

    @Override
    public String runBll(Record rec_service) throws Exception {
        msg = "";

        // 获取待处理数据
        StringBuffer sql_kaimu = new StringBuffer();
        sql_kaimu.append("select CDOCID, 车身系列 as CAR_BODY_SERIES, 图号 as FIGURE_NUMBER, 件号 as PIECE_NUMBER,");
        sql_kaimu.append(" 零件名称 as PART_NAME, 工艺号 as TECHNOLOGY_NUMBER, 消耗定额 as CONSUMPTION_FIGURES,");
        sql_kaimu.append(" 材质 as MATERIAL_QUALITY, 开卷规格 as UNCOILING_SPECIFICATION,");
        sql_kaimu.append(" 开卷尺寸 as UNCOILING_SIZE, 坯料尺寸 as STOCK_SIZE, 净重 as SUTTLE,");
        sql_kaimu.append(" 名称 as NAME, 工位器具 as CONTAINER, 容量 as CAPACITY,");
        sql_kaimu.append(" 工艺类型 as TECHNOLOGY_TYPE, BATCH as BATCHGY");
        sql_kaimu.append(" from PDM_MES_BT");
        sql_kaimu.append(" where FLAG = 'D' AND MES_STATUS = '0'");
        List<Record> list = Db.use("kaimu").find(sql_kaimu.toString());
        if (list == null || list.size() == 0) {
            return msg;
        }

        StringBuffer sql = new StringBuffer();
        sql.append("insert into T_BASE_CH_TECHNOLOGY_BT(ID, CDOCID, CAR_BODY_SERIES, FIGURE_NUMBER, PIECE_NUMBER,");
        sql.append(" PART_NAME, TECHNOLOGY_NUMBER, CONSUMPTION_FIGURES, MATERIAL_QUALITY, UNCOILING_SPECIFICATION,");
        sql.append(" UNCOILING_SIZE, STOCK_SIZE, SUTTLE, NAME, CONTAINER, CAPACITY, TECHNOLOGY_TYPE, BATCHGY)");
        sql.append(" values(sys_guid(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

        boolean ret = Db.tx(new IAtom() {
            @Override
            public boolean run() throws SQLException {
                for (Record sub : list) {
                    Db.update(sql.toString(),
                            sub.getStr("CDOCID"),
                            sub.getStr("CAR_BODY_SERIES"),
                            sub.getStr("FIGURE_NUMBER"),
                            sub.getStr("PIECE_NUMBER"),
                            sub.getStr("PART_NAME"),
                            sub.getStr("TECHNOLOGY_NUMBER"),
                            sub.getStr("CONSUMPTION_FIGURES"),
                            sub.getStr("MATERIAL_QUALITY"),
                            sub.getStr("UNCOILING_SPECIFICATION"),
                            sub.getStr("UNCOILING_SIZE"),
                            sub.getStr("STOCK_SIZE"),
                            sub.getStr("SUTTLE"),
                            sub.getStr("NAME"),
                            sub.getStr("CONTAINER"),
                            sub.getStr("CAPACITY"),
                            sub.getStr("TECHNOLOGY_TYPE"),
                            sub.getStr("BATCHGY"));

                    msg = String.format("获取下料冲压工艺表头信息，车身系列【%s】，图号【%s】，件号【%s】",
                            sub.getStr("CAR_BODY_SERIES"),
                            sub.getStr("FIGURE_NUMBER"),
                            sub.getStr("PIECE_NUMBER"));
                    Log.Write(strServiceCode, LogLevel.Information, msg);

                    // 下料写入物料信息
                    if ("下料工艺规程".equals(sub.getStr("TECHNOLOGY_TYPE"))) {
                        String blank_size = sub.getStr("STOCK_SIZE");
                        if (blank_size != null && blank_size.contains("/")) {
                            blank_size = blank_size.substring(0, blank_size.indexOf("/")).trim();
                        }

                        String coil_size = sub.getStr("UNCOILING_SIZE");
                        if (coil_size != null && coil_size.contains("/")) {
                            coil_size = coil_size.substring(0, coil_size.indexOf("/")).trim();
                        }

                        String coil_spec = sub.getStr("UNCOILING_SPECIFICATION");
                        if (coil_spec != null && coil_spec.contains("/")) {
                            coil_spec = coil_spec.substring(0, coil_spec.indexOf("/")).trim();
                        }

                        // 零件坯料尺寸与开卷尺寸不一致时，既生成零件坯料零件信息，又生成开卷板料零件信息
                        // 零件坯料尺寸与开卷尺寸一致时，只生成零件坯料零件信息，不生成开卷板料零件信息
                        String blank_part_code = null;
                        String coil_part_code = null;
                        String blank_spec_part_code = null;

                        // 零件坯料信息
                        blank_part_code = sub.getStr("TECHNOLOGY_NUMBER") + "-零件坯料";

                        if (!blank_size.equals(coil_size)) {
                            // 开卷板料信息
                            coil_part_code = sub.getStr("TECHNOLOGY_NUMBER") + "-开卷板料";
                        }

                        // 零件开卷尺寸与工艺板规不一致时，生成工艺板规信息
                        if (!coil_size.equals(coil_spec)) {
                            blank_spec_part_code = sub.getStr("MATERIAL_QUALITY") + "-工艺板规";
                        }

                        // 更新物料信息
                        if (blank_part_code != null) {
                            AddMaterial(1, blank_part_code, sub);
                        }

                        if (coil_part_code != null) {
                            AddMaterial(2, coil_part_code, sub);
                        }

                        if (blank_spec_part_code != null) {
                            AddMaterial(3, blank_spec_part_code, sub);
                        }
                    }
                }

                return true;
            }
        });

        if (ret) {
            for (Record sub_commit : list) {
                Db.use("kaimu").update("update PDM_MES_BT set MES_STATUS = '1', FLAG = 'E' where MES_STATUS = '0' and CDOCID = ?",
                        sub_commit.getStr("CDOCID"));
            }
            msg = String.format("同步数据成功，记录数量【%d】", list.size());
        }

        return msg;
    }

    /**
     * @param add_type      添加物料类型：1、零件坯料；2、开卷板料；3、工艺板规
     * @param material_code 物料编码
     * @param rec           从开目获取的物料信息
     */
    private void AddMaterial(int add_type, String material_code, Record rec) {
        // 物料属性
        String material_type = null;
        // 单位
        String unit_code = null;
        // 物料规格
        String material_specification = null;
        // 坯料尺寸
        String blank_size = rec.getStr("STOCK_SIZE");
        if (blank_size != null && blank_size.contains("/")) {
            blank_size = blank_size.substring(0, blank_size.indexOf("/")).trim();
        }
        // 开卷尺寸
        String coil_size = rec.getStr("UNCOILING_SIZE");
        if (coil_size != null && coil_size.contains("/")) {
            coil_size = coil_size.substring(0, coil_size.indexOf("/")).trim();
        }

        switch (add_type) {
            case 1:
                material_type = "零件坯料";
                unit_code = "件";
                material_specification = rec.getStr("STOCK_SIZE");
                break;
            case 2:
                material_type = "开卷板料";
                unit_code = "块";
                material_specification = rec.getStr("UNCOILING_SIZE");

                break;
            case 3:
                material_type = "卷料";
                unit_code = "捆";
                material_specification = rec.getStr("UNCOILING_SPECIFICATION");
                break;
            default:
                return;
        }

        if (material_specification != null && material_specification.contains("/")) {
            material_specification = material_specification.substring(0, material_specification.indexOf("/")).trim();
        }

        Record is_have_rec = Db.findFirst("select * from T_BASE_MATERIAL where WORKSHOP_CODE = 'ch01' and MATERIAL_CODE = ?", material_code);
        if (is_have_rec == null) {
            // 不存在，插入
            StringBuffer sql_add = new StringBuffer();
            sql_add.append("insert into T_BASE_MATERIAL(ID, WORKSHOP_CODE, MATERIAL_CODE, MATERIAL_NAME, K_TEXTURE,");
            sql_add.append(" UNIT_CODE, K_STAMP_MATERIAL_TYPE, MATERIAL_SPECIFICATION, K_BLANK_SIZE, K_COIL_SIZE, K_QUOTA)");
            sql_add.append(" values(sys_guid(), 'ch01', ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            Db.update(sql_add.toString(),
                    material_code,
                    material_code,
                    rec.getStr("MATERIAL_QUALITY"),
                    unit_code,
                    material_type,
                    material_specification,
                    blank_size,
                    coil_size,
                    rec.getStr("CONSUMPTION_FIGURES"));
        } else {
            // 已存在，更新
            StringBuffer sql_upd = new StringBuffer();
            sql_upd.append("update T_BASE_MATERIAL set MATERIAL_NAME = ?,");
            sql_upd.append(" K_TEXTURE = ?,");
            sql_upd.append(" UNIT_CODE = ?,");
            sql_upd.append(" K_STAMP_MATERIAL_TYPE = ?,");
            sql_upd.append(" MATERIAL_SPECIFICATION = ?,");
            sql_upd.append(" K_BLANK_SIZE = ?,");
            sql_upd.append(" K_COIL_SIZE = ?,");
            sql_upd.append(" K_QUOTA = ?");
            sql_upd.append(" where WORKSHOP_CODE = 'ch01'");
            sql_upd.append(" and MATERIAL_CODE = ?");
            Db.update(sql_upd.toString(),
                    material_code,
                    rec.getStr("MATERIAL_QUALITY"),
                    unit_code,
                    material_type,
                    material_specification,
                    blank_size,
                    coil_size,
                    rec.getStr("CONSUMPTION_FIGURES"),
                    material_code);
        }
    }
}

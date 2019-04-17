package cvmes.kaimu;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.IAtom;
import com.jfinal.plugin.activerecord.Record;
import cvmes.common.AbstractSubServiceThread;
import cvmes.common.Log;
import cvmes.common.LogLevel;

import java.sql.SQLException;
import java.util.List;

public class KaimuMaterialInf extends AbstractSubServiceThread {
    private String msg = "";
    private String material_code = "";
    private String k_drawing_no = "";

    @Override
    public void initServiceCode() {
        this.strServiceCode = "KaimuMaterialInf";
    }

    @Override
    public String runBll(Record rec_service) throws Exception {
        msg = "";

        // 获取待同步数据
        StringBuffer sql = new StringBuffer();
        sql.append("select 零件号 as MATERIAL_CODE, 零件名称 as MATERIAL_NAME, 物料属性 as K_STAMP_MATERIAL_TYPE,");
        sql.append(" 零件属性 as K_STAMP_PART_TYPE, 是否营销 as K_IS_SALE, 供应商 as K_SUPPLIER,");
        sql.append(" 柳汽图号 as K_DRAWING_NO, 是否涂装小件 as IS_COATING_SMALL, 钢材供应商 as STEEL_SUPPLIERS,");
        sql.append(" 生产线 as LINE_CODES, 车型种类 as K_CAR_TYPE, CONVERT(varchar(19), 发布时间, 20) as UPD_TIME");
        sql.append(" from VIEW_MES_ITEMS");
        sql.append(" where CONVERT(varchar(19), 发布时间, 20) > ?");
        sql.append(" order by 发布时间");

        List<Record> list = Db.use("kaimu").find(sql.toString(), rec_service.getStr("SERVICE_PARA1_VALUE"));
        if (list == null || list.size() == 0) {
            return msg;
        }

        for (Record sub : list) {
            boolean ret = Db.tx(new IAtom() {
                @Override
                public boolean run() throws SQLException {
                    material_code = sub.getStr("MATERIAL_CODE");
                    if (material_code.contains("-LD")) {
                        material_code = material_code.substring(0, material_code.length() - 3).trim();
                    }

                    k_drawing_no = sub.getStr("K_DRAWING_NO");
                    if (k_drawing_no.contains("-LD")) {
                        k_drawing_no = k_drawing_no.substring(0, k_drawing_no.length() - 3).trim();
                    }

                    // 更新自制零件和生产线关系
                    if ("自制件".equals(sub.getStr("K_STAMP_PART_TYPE")) && "零件".equals(sub.getStr("K_STAMP_MATERIAL_TYPE"))
                            && ("CV1线".equals(sub.getStr("LINE_CODES")) || "CV2线".equals(sub.getStr("LINE_CODES")))) {
                        Record rec_product_line = Db.findFirst("SELECT * FROM T_MODEL_PRODUCT_WITH_LINE WHERE PRODUCT_CODE = ? AND line_code in ('cy01', 'cy02')",
                                material_code);
                        if (rec_product_line == null) {
                            Db.update("insert into T_MODEL_PRODUCT_WITH_LINE(ID, PRODUCT_CODE, LINE_CODE) values(sys_guid(), ?, ?)",
                                    material_code, String.format("cy0%s", sub.getStr("LINE_CODES").substring(2, 3)));
                        } else {
                            Db.update("update T_MODEL_PRODUCT_WITH_LINE set LINE_CODE = ? where PRODUCT_CODE = ? and line_code in ('cy01', 'cy02')",
                                    String.format("cy0%s", sub.getStr("LINE_CODES").substring(2, 3)), material_code);
                        }
                    }

                    // 更新物料表
                    Record rec = Db.findFirst("select * from T_BASE_MATERIAL where WORKSHOP_CODE = 'ch01' and MATERIAL_CODE = ?", material_code);
                    if (rec == null) {
                        // 不存在，插入
                        StringBuffer sql_add = new StringBuffer();
                        sql_add.append("insert into T_BASE_MATERIAL(ID, WORKSHOP_CODE, MATERIAL_CODE, MATERIAL_NAME,");
                        sql_add.append(" K_STAMP_MATERIAL_TYPE, K_STAMP_PART_TYPE, K_IS_SALE, K_SUPPLIER,");
                        sql_add.append(" K_DRAWING_NO, IS_COATING_SMALL, STEEL_SUPPLIERS, LINE_CODES, K_CAR_TYPE)");
                        sql_add.append(" values(sys_guid(), 'ch01', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                        Db.update(sql_add.toString(),
                                material_code,
                                sub.getStr("MATERIAL_NAME"),
                                sub.getStr("K_STAMP_MATERIAL_TYPE"),
                                sub.getStr("K_STAMP_PART_TYPE"),
                                sub.getStr("K_IS_SALE"),
                                sub.getStr("K_SUPPLIER"),
                                k_drawing_no,
                                sub.getStr("IS_COATING_SMALL"),
                                sub.getStr("STEEL_SUPPLIERS"),
                                sub.getStr("LINE_CODES"),
                                sub.getStr("K_CAR_TYPE"));

                        msg = String.format("新增物料【%s】，开目变更时间【%s】", material_code, sub.getStr("UPD_TIME"));
                    } else {
                        // 已存在，更新
                        StringBuffer sql_upd = new StringBuffer();
                        sql_upd.append("update T_BASE_MATERIAL set MATERIAL_NAME = ?,");
                        sql_upd.append(" K_STAMP_MATERIAL_TYPE = ?,");
                        sql_upd.append(" K_STAMP_PART_TYPE = ?,");
                        sql_upd.append(" K_IS_SALE = ?,");
                        sql_upd.append(" K_SUPPLIER = ?,");
                        sql_upd.append(" K_DRAWING_NO = ?,");
                        sql_upd.append(" IS_COATING_SMALL = ?,");
                        sql_upd.append(" STEEL_SUPPLIERS = ?,");
                        sql_upd.append(" LINE_CODES = ?,");
                        sql_upd.append(" K_CAR_TYPE = ?");
                        sql_upd.append(" where WORKSHOP_CODE = 'ch01'");
                        sql_upd.append(" and MATERIAL_CODE = ?");
                        Db.update(sql_upd.toString(),
                                sub.getStr("MATERIAL_NAME"),
                                sub.getStr("K_STAMP_MATERIAL_TYPE"),
                                sub.getStr("K_STAMP_PART_TYPE"),
                                sub.getStr("K_IS_SALE"),
                                sub.getStr("K_SUPPLIER"),
                                k_drawing_no,
                                sub.getStr("IS_COATING_SMALL"),
                                sub.getStr("STEEL_SUPPLIERS"),
                                sub.getStr("LINE_CODES"),
                                sub.getStr("K_CAR_TYPE"),
                                material_code);

                        msg = String.format("修改物料【%s】，开目变更时间【%s】", material_code, sub.getStr("UPD_TIME"));
                    }

                    return true;
                }
            });

            if (ret) {
            } else {
                msg = String.format("同步物料【%s】失败，开目变更时间【%s】", material_code, sub.getStr("UPD_TIME"));
            }

            Log.Write(strServiceCode, LogLevel.Information, msg);
        }

        Db.update("update t_sys_service set SERVICE_PARA1_VALUE = ? where service_code = ?", list.get(list.size() - 1).getStr("UPD_TIME"), strServiceCode);
        return msg;
    }
}

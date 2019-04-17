package cvmes.kaimu;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;
import cvmes.common.AbstractSubServiceThread;
import cvmes.common.Log;
import cvmes.common.LogLevel;

import java.util.List;

public class KaimuDiffViewInf extends AbstractSubServiceThread {
    private String msg = "";
    private String carbodycode = "";

    @Override
    public void initServiceCode() {
        this.strServiceCode = "KaimuDiffViewInf";
    }

    @Override
    public String runBll(Record rec_service) throws Exception {
        msg = "";

        // 获取待同步数据
        StringBuffer sql = new StringBuffer();
        sql.append("select 车身图号 as CAR_BODY_FIGURE_NUM, 差异化图号 as DIFFERENTIA_CODE, 工位号 as STATION_CODE, 产品型号 as PRODUCT_CODE,");
        sql.append(" 总成名称 as ASSEMBLY_NAME, 差异化说明 as DIFF_INSTRUCTION, 车型说明 as CAR_TYPE_INSTRUCTION, CONVERT(varchar(19), 差异化代码发布时间, 20) as OPEN_EYE_UPDATE_TIME");
        sql.append(" from VIEW_MES_差异化图号");
        sql.append(" where CONVERT(varchar(19), 差异化代码发布时间, 20) > ?");
        sql.append(" order by 差异化代码发布时间");

        List<Record> list = Db.use("kaimu").find(sql.toString(), rec_service.getStr("SERVICE_PARA1_VALUE"));
        if (list == null || list.size() == 0) {
            return msg;
        }

        for (Record sub : list) {
            Record rec = Db.findFirst("select * from T_BASE_CH_DIFF_FIGURE_NUM where CAR_BODY_FIGURE_NUM = ? and DIFFERENTIA_CODE = ?",
                    sub.getStr("CAR_BODY_FIGURE_NUM"),
                    sub.getStr("DIFFERENTIA_CODE"));
            carbodycode = sub.getStr("CAR_BODY_FIGURE_NUM");
            if (carbodycode.contains("-LD")) {
                carbodycode = carbodycode.substring(0, carbodycode.length() - 3).trim();
            }

            if (rec != null) {
                // 已存在，更新
                Db.update("update T_BASE_CH_DIFF_FIGURE_NUM set STATION_CODE = ?, PRODUCT_CODE = ?, ASSEMBLY_NAME = ?, DIFF_INSTRUCTION = ?, CAR_TYPE_INSTRUCTION = ?, OPEN_EYE_UPDATE_TIME = to_date(?, 'yyyy-mm-dd hh24:mi:ss') where CAR_BODY_FIGURE_NUM = ? and DIFFERENTIA_CODE = ?",
                        sub.getStr("STATION_CODE"),
                        sub.getStr("PRODUCT_CODE"),
                        sub.getStr("ASSEMBLY_NAME"),
                        sub.getStr("DIFF_INSTRUCTION"),
                        sub.getStr("CAR_TYPE_INSTRUCTION"),
                        sub.getStr("OPEN_EYE_UPDATE_TIME"),
                        carbodycode,
                        sub.getStr("DIFFERENTIA_CODE"));

                msg = String.format("更新差异化视图，车身图号【%s】，差异化图号【%s】，产品型号【%s】，开目变更时间【%s】",
                        sub.getStr("CAR_BODY_FIGURE_NUM"),
                        sub.getStr("DIFFERENTIA_CODE"),
                        sub.getStr("PRODUCT_CODE"),
                        sub.getStr("OPEN_EYE_UPDATE_TIME"));
            } else {
                // 不存在，插入
                Db.update("insert into T_BASE_CH_DIFF_FIGURE_NUM(ID, CAR_BODY_FIGURE_NUM, DIFFERENTIA_CODE, STATION_CODE, PRODUCT_CODE, ASSEMBLY_NAME, DIFF_INSTRUCTION, CAR_TYPE_INSTRUCTION, CREATE_TIME, OPEN_EYE_UPDATE_TIME) values(sys_guid(), ?, ?, ?, ?, ?, ?, ?, sysdate, to_date(?, 'yyyy-mm-dd hh24:mi:ss'))",
                        carbodycode,
                        sub.getStr("DIFFERENTIA_CODE"),
                        sub.getStr("STATION_CODE"),
                        sub.getStr("PRODUCT_CODE"),
                        sub.getStr("ASSEMBLY_NAME"),
                        sub.getStr("DIFF_INSTRUCTION"),
                        sub.getStr("CAR_TYPE_INSTRUCTION"),
                        sub.getStr("OPEN_EYE_UPDATE_TIME"));

                msg = String.format("新增差异化视图，车身图号【%s】，差异化图号【%s】，产品型号【%s】，开目变更时间【%s】",
                        sub.getStr("CAR_BODY_FIGURE_NUM"),
                        sub.getStr("DIFFERENTIA_CODE"),
                        sub.getStr("PRODUCT_CODE"),
                        sub.getStr("OPEN_EYE_UPDATE_TIME"));
            }

            Log.Write(strServiceCode, LogLevel.Information, msg);
        }

        Db.update("update t_sys_service set SERVICE_PARA1_VALUE = ? where service_code = ?", list.get(list.size() - 1).getStr("OPEN_EYE_UPDATE_TIME"), strServiceCode);
        return msg;
    }
}

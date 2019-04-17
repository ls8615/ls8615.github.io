package cvmes.kaimu;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;
import cvmes.common.AbstractSubServiceThread;
import cvmes.common.Log;
import cvmes.common.LogLevel;

import java.util.List;

public class KaimuTechnologyGXInf extends AbstractSubServiceThread {
    private String msg = "";

    @Override
    public void initServiceCode() {
        this.strServiceCode = "KaimuTechnologyGXInf";
    }

    @Override
    public String runBll(Record rec_service) throws Exception {
        msg = "";

        // 获取待同步数据
        StringBuffer sql_kaimu = new StringBuffer();
        sql_kaimu.append("select CDOCID, 工序号 as PROCESS_NO, 工序内容 as PROCESS_CONTENT, [外形尺寸.厚] as SHAPE_SIZE_THICK,");
        sql_kaimu.append(" [外形尺寸.长] as SHAPE_SIZE_LENGTH, [外形尺寸.宽] as SHAPE_SIZE_WIDTH, [外形尺寸.工差] as SHAPE_SIZE_TOLERANCE,");
        sql_kaimu.append(" 一张切几块 as SHEET_CUT_BLOCK, 一块切几件 as SHEET_CUT_PIECE, 设备编号 as DEVICE_CODE,");
        sql_kaimu.append(" 设备名称 as DEVICE_NAME, 模具号 as MOULD_CODE, 模具尺寸 as MOULD_SIZE, 同时加工 as TOGETHER_PROCESS,");
        sql_kaimu.append(" 工艺类型 as TECHNOLOGY_TYPE, BATCH as BATCHGY");
        sql_kaimu.append(" from PDM_MES_GX");
        sql_kaimu.append(" where FLAG = 'D' AND MES_STATUS = '0'");
        List<Record> list = Db.use("kaimu").find(sql_kaimu.toString());
        if (list == null || list.size() == 0) {
            return msg;
        }

        StringBuffer sql = new StringBuffer();
        sql.append("insert into T_BASE_CH_TECHNOLOGY_GX(ID, CDOCID, PROCESS_NO, PROCESS_CONTENT, SHAPE_SIZE_THICK,");
        sql.append(" SHAPE_SIZE_LENGTH, SHAPE_SIZE_WIDTH, SHAPE_SIZE_TOLERANCE, SHEET_CUT_BLOCK, SHEET_CUT_PIECE,");
        sql.append(" DEVICE_CODE, DEVICE_NAME, MOULD_CODE, MOULD_SIZE, TOGETHER_PROCESS, TECHNOLOGY_TYPE, BATCHGY)");
        sql.append(" values(sys_guid(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

        for (Record sub : list) {
            Db.update(sql.toString(),
                    sub.getStr("CDOCID"),
                    sub.getStr("PROCESS_NO"),
                    sub.getStr("PROCESS_CONTENT"),
                    sub.getStr("SHAPE_SIZE_THICK"),
                    sub.getStr("SHAPE_SIZE_LENGTH"),
                    sub.getStr("SHAPE_SIZE_WIDTH"),
                    sub.getStr("SHAPE_SIZE_TOLERANCE"),
                    sub.getStr("SHEET_CUT_BLOCK"),
                    sub.getStr("SHEET_CUT_PIECE"),
                    sub.getStr("DEVICE_CODE"),
                    sub.getStr("DEVICE_NAME"),
                    sub.getStr("MOULD_CODE"),
                    sub.getStr("MOULD_SIZE"),
                    sub.getStr("TOGETHER_PROCESS"),
                    sub.getStr("TECHNOLOGY_TYPE"),
                    sub.getStr("BATCHGY"));

            msg = String.format("获取下料冲压工艺工序信息, CDOCID【%s】，工序号【%s】，工序内容【%s】",
                    sub.getStr("CDOCID"),
                    sub.getStr("PROCESS_NO"),
                    sub.getStr("PROCESS_CONTENT"));
            Log.Write(strServiceCode, LogLevel.Information, msg);

            Db.use("kaimu").update("update PDM_MES_BT set MES_STATUS = '1', FLAG = 'E' where MES_STATUS = '0' and CDOCID = ?",
                    sub.getStr("CDOCID"));
        }

        msg = String.format("同步数据成功，记录数量【%d】", list.size());
        return msg;
    }
}

package cvmes.zznjj;

import java.util.List;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;

import cvmes.common.AbstractSubServiceThread;
import cvmes.common.Log;
import cvmes.common.LogLevel;

/**
 * 总装拧紧机前桥扭矩指示数据编辑
 * @author CIKE
 *
 */
public class ZznjjQlTqDataEdit extends AbstractSubServiceThread {
    private String msg;

    @Override
    public void initServiceCode() {
        this.strServiceCode = "ZznjjQlTqDataEdit";
    }

    @Override
    public String runBll(Record rec_service) throws Exception {
        msg = "";

        // 获取待处理指令
        List<Record> recList = Db.find("select * from T_CMD_PRODUCTION_ORDER where PRODUCTION_ORDER_STATUS='0' and INDICATION_POINT_CODE in (?,?)",
                rec_service.getStr("SERVICE_PARA1_VALUE"),rec_service.getStr("SERVICE_PARA2_VALUE"));
        if (recList == null) {
            return msg;
        }
        if(recList.size()>0){
        for (Record rec : recList) {
        	
        	 // 插入数据到接口表
            StringBuffer sql = new StringBuffer();
            sql.append("INSERT INTO T_INF_TO_POMMEL_TORQUE(ID,");
            sql.append(" PRODUCTION_CODE, LINE_NO, MIS_TIME,");
            sql.append(" FRONT_AXLE_TORQUE, FRONT_AXLE_TWO_TORQUE,");
            sql.append(" DEAL_STATUS, DEAL_TIME)");
            sql.append(" SELECT SYS_GUID() AS ID,t1.PRODUCTION_CODE,t1.K_ASSEMBLY_LINE AS line_no,");
            sql.append(" (SELECT tt2.TORSION FROM T_PLAN_DEMAND_PRODUCT tt1 LEFT JOIN T_BASE_AXLESTEEL tt2 on tt1.K_FRONT_AXLE_ONE=tt2.AXLE_CODE");
            sql.append(" WHERE tt1.PRODUCTION_CODE = ?");
            sql.append(" ) as FRONT_AXLE_TORQUE,");
            sql.append(" (SELECT vt2.TORSION FROM T_PLAN_DEMAND_PRODUCT vt1 LEFT JOIN T_BASE_AXLESTEEL vt2 ON vt1.K_FRONT_AXLE_TWO=vt2.AXLE_CODE");
            sql.append(" WHERE vt1.PRODUCTION_CODE = ?");
            sql.append(" ) as FRONT_AXLE_TORQUE,");
            sql.append(" '0' AS DEAL_STATUS, SYSDATE AS DEAL_TIME");
            sql.append(" FROM T_PLAN_DEMAND_PRODUCT T1");
            sql.append(" LEFT JOIN T_BASE_AXLESTEEL t3 ON  t1.K_FRONT_AXLE_ONE=t3.AXLE_CODE OR t1.K_FRONT_AXLE_TWO=t3.AXLE_CODE");
            sql.append(" LEFT JOIN T_ACTUAL_PASSED_RECORD T2 ON T1.PRODUCTION_CODE = T2.PRODUCTION_CODE");
            sql.append(" WHERE t2.ACTUAL_POINT_CODE IN ('zz_mis1','zz_mis2') AND t1.DEMAND_PRODUCT_TYPE='0'");
            sql.append(" AND t1.PRODUCTION_CODE = ?");
            Db.update(sql.toString(), rec.getStr("PRODUCTION_CODE"),rec.getStr("PRODUCTION_CODE"),rec.getStr("PRODUCTION_CODE"));
            // 更新指令状态
            Db.update("update T_CMD_PRODUCTION_ORDER set PRODUCTION_ORDER_STATUS = '1' where ID = ?", rec.getStr("ID"));
            // 记录日志并返回最后处理消息
            msg = String.format("处理生产指令成功，ID【%s】，生产编码【%s】", rec.getStr("ID"), rec.getStr("PRODUCTION_CODE"));
            Log.Write(strServiceCode, LogLevel.Information, msg);
        }
        }
        return msg;
    }
}

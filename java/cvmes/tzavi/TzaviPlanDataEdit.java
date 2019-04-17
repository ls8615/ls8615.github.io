package cvmes.tzavi;

import java.util.List;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;
import cvmes.common.AbstractSubServiceThread;
import cvmes.common.Log;
import cvmes.common.LogLevel;

public class TzaviPlanDataEdit extends AbstractSubServiceThread {
    private String msg;

    @Override
    public void initServiceCode() {
        this.strServiceCode = "TzaviPlanDataEdit";
    }

    @Override
    public String runBll(Record rec_service) throws Exception {
        msg = "";

        // 获取待处理指令
        List<Record> list_rec = Db.find("select * from T_CMD_PRODUCTION_ORDER where PRODUCTION_ORDER_STATUS='0' and INDICATION_POINT_CODE=?",
                rec_service.getStr("SERVICE_PARA1_VALUE"));
        if (list_rec == null || list_rec.size()==0) {
            return msg;
        }
        int msg_rec = 0;
        for(Record rec : list_rec){
        	try {
        		 // 插入数据到接口表
                StringBuffer sql = new StringBuffer();
                sql.append("INSERT INTO T_INF_TO_PAINTAVI_PLAN(ID, BODY_CAR_NO, CARTYPE_CODE, COLOR_NAME,");
                sql.append(" ASSEMBLY_SEQUENCE, OPTIONAL_PACKAGE, SIDE_WINDOW,");
                sql.append(" SKY_LIGHT, BACK_LIGHT, PANEL, ANGLE_PLATE,");
                sql.append(" FLAT_PLATE, CARTYPE_MASK, PLAN_SOURCE,");
                sql.append(" DEAL_STATUS, DEAL_TIME, K_DRAWING_NO)");
                sql.append(" SELECT SYS_GUID() AS ID, t2.K_STAMP_ID AS BODY_CAR_NO, t2.K_CARBODY_CODE AS CARTYPE_CODE, t2.K_COLOR_NAME AS COLOR_NAME,");
                sql.append(" TO_NUMBER(SUBSTR(t1.SCHEDULING_PLAN_CODE, 5, 6) || LPAD(t1.SEQ_NO, 3, 0)) AS ASSEMBLY_SEQUENCE,");
                sql.append(" t2.K_PAINT_REMARK AS OPTIONAL_PACKAGE, t3.SIDE_WINDOWS AS SIDE_WINDOW,");
                sql.append(" t3.SCUTTLE AS SKY_LIGHT, t3.BACKLIGHT AS BACK_LIGHT, t3.COLOURED_PANEL AS PANEL, t3.COLOURED_FRICTION_ANGLE AS ANGLE_PLATE,");
                sql.append(" t3.FLAT_FLOOR AS FLAT_PLATE, DECODE(DEMAND_PRODUCT_TYPE, 0, 0, 5, 1) AS CARTYPE_MASK, '0' AS PLAN_SOURCE,");
                sql.append(" '0' AS DEAL_STATUS, SYSDATE AS DEAL_TIME, t2.K_CARBODY_CODE AS K_DRAWING_NO");
                sql.append(" FROM T_PLAN_SCHEDULING_D t1");
                sql.append(" LEFT JOIN T_PLAN_DEMAND_PRODUCT t2 ON t1.PRODUCTION_CODE = t2.PRODUCTION_CODE");
                sql.append(" LEFT JOIN T_BASE_PAINT_CARTYPE_LIST t3 ON t2.K_CARBODY_CODE = t3.CAR_BODY_CODE");
                sql.append(" WHERE t1.SCHEDULING_PLAN_CODE = ?");
                sql.append(" ORDER BY t1.SEQ_NO");

                Db.update(sql.toString(), rec.getStr("PLAN_NO"));

                // 更新指令状态
                Db.update("update T_CMD_PRODUCTION_ORDER set PRODUCTION_ORDER_STATUS = '1' where ID = ?", rec.getStr("ID"));
                // 记录日志并返回最后处理消息
                msg = String.format("处理生产指令成功，ID【%s】，计划编码【%s】", rec.getStr("ID"), rec.getStr("PLAN_NO"));
                Log.Write(strServiceCode, LogLevel.Information, msg);
				
			} catch (Exception e) {
				msg_rec++;
				e.printStackTrace();
			}
        	

          
        }

       if(msg_rec>0){
    	   // 记录日志并返回最后处理消息
           msg = String.format("处理生产指令成功，成功数量【%s】，失败数量【%s】", list_rec.size()-msg_rec,msg_rec);
           Log.Write(strServiceCode, LogLevel.Information, msg);
       }else{
    	   msg = String.format("处理生产指令成功，成功数量【%s】", list_rec.size());
           Log.Write(strServiceCode, LogLevel.Information, msg);
       }
       
        return msg;
    }
}

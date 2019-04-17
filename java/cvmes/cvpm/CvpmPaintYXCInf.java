package cvmes.cvpm;


import java.sql.SQLException;
import java.util.List;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.IAtom;
import com.jfinal.plugin.activerecord.Record;

import cvmes.common.AbstractSubServiceThread;
import cvmes.common.Log;
import cvmes.common.LogLevel;

/**
 * 涂装计划排入营销车服务
 * */
public class CvpmPaintYXCInf extends AbstractSubServiceThread {
	private String msg = "";
	@Override
	public void initServiceCode() {
		// TODO Auto-generated method stub
		strServiceCode = "CvpmPaintYXCInf";
	}
	
	@Override
	public String runBll(Record rec_service) throws Exception {
		// TODO Auto-generated method stub
		//SERVICE_PARA1_VALUE
		//1.获取大于等于今天的涂装计划
        String sql = String.format("select ps.scheduling_plan_code, "
                + "       to_char(ps.scheduling_plan_date, 'yyyy-mm-dd') scheduling_plan_date,id "
                + "  from t_plan_scheduling ps "
                + " where ps.workshop_code = 'tz01' "
                + "   and to_char(ps.scheduling_plan_date, 'yyyy-mm-dd') >= "
                + "       to_char(sysdate, 'yyyy-mm-dd') "
                + "   and ps.scheduling_plan_type = '13' "
                + "   and ps.available_status ='1' order by ps.scheduling_plan_date" );
        List<Record> tzlist = Db.find(sql);
		if(tzlist !=null && tzlist.size()>0){
			for(Record rec : tzlist){
				//2.计划日期是否存在未排产的营销车
                String scheduling_plan_date = rec.getStr("scheduling_plan_date");
                String scheduling_plan_code = rec.getStr("scheduling_plan_code");
				 sql = String.format(" select id, demand_product_code, k_drawing_no, k_color_name, k_stamp_id,demand_num,"
                         + " demand_plan_remark,bom_code,k_car_body,demand_work_shift  "
                         + "     from t_plan_demand pd "
                         + "    where to_char(pd.demand_plan_date, 'yyyy-mm-dd') = '%s' "
                         + "      and pd.is_top_paint = '1' "
                         + "      and pd.k_demand_type = '0' "
                         + "      and pd.is_paint = '0' order by id " , scheduling_plan_date);
				List<Record> yxclist = Db.find(sql);
				if(yxclist !=null && yxclist.size()>0) {
                    Db.tx(new IAtom() {
                        public boolean run() throws SQLException {
                            String Sql ="";
                            for(Record rec : yxclist){
                                //获取生产编码
                                Sql = " SELECT decode(count(1),0,'XQTZ_'||TO_CHAR(SYSDATE,'yyMMdd')||'_01','XQTZ_'||TO_CHAR(SYSDATE,'yyMMdd')||'_'||substr('00'||((substr(max(PRODUCTION_CODE),-2,2))+1),-2,2)) PRODUCTION_CODE  "
                                        + "                       FROM t_plan_demand_product pdp "
                                        + "                       WHERE pdp.DEMAND_PRODUCT_TYPE in ('0' ,'5') "
                                        + "                      and pdp.PRODUCTION_CODE like 'XQTZ_'||TO_CHAR(SYSDATE,'yyMMdd')||'%' ";
                                String production_code = Db.findFirst(Sql).getStr("production_code");
                                //写入需求产品信息
                                Sql = String.format("insert into t_plan_demand_product "
                                                +   "   (id, "
                                                +   "   DEMAND_DATE, "
                                                +   "   K_CARBODY_CODE, "
                                                +   "   K_CARTYPE,"
                                                +   "   K_COLOR_NAME, "
                                                +   "   K_STAMP_ID, "
                                                +   "   DEMAND_PRODUCT_CODE, "
                                                +   "   K_PAINT_REMARK, "
                                                +   "   PRODUCTION_CODE, "
                                                +   "   DEMAND_NUM, "
                                                +   "   DEMAND_SOURCE, "
                                                +   "   AVAILABLE_STATUS, "
                                                +   "   DEMAND_PRODUCT_TYPE, "
                                                +   "   SCHEDULING_STATUS) "
                                                +   "   values (sys_guid(), "
                                                +   "   to_date('%s','yyyy-mm-dd'), "
                                                +   "   '%s', "
                                                +   "   '%s', "
                                                +   "   '%s', "
                                                +   "   '%s', "
                                                +   "   NVL('%s','yxccpbm'), "
                                                +   "   '%s', "
                                                +   "   '%s', "
                                                +   "   '1', "
                                                +   "   '0', "
                                                +   "   '1', "
                                                +   "   '5', "
                                                +   "   '1')",scheduling_plan_date,rec.getStr("k_drawing_no"),rec.getStr("k_car_body"),
                                        rec.getStr("k_color_name"),rec.getStr("k_stamp_id"),rec.getStr("demand_product_code"),rec.getStr("demand_plan_remark"),production_code);
                                msg = "写入需求产品信息：计划日期【"+scheduling_plan_date+"】，车身白件图号【"+rec.getStr("k_drawing_no")+"】，车身图号【"+rec.getStr("k_car_body")+"】，" +
                                        "颜色【"+rec.getStr("k_color_name")+"】，钢码号【"+rec.getStr("k_stamp_id")+"】，产品编码【"+rec.getStr("demand_product_code")+"】，备注【"+rec.getStr("demand_plan_remark")+"】，生产编码【"+production_code+"】；";
                                Log.Write(strServiceCode, LogLevel.Information, msg);
                                Db.update(Sql);

                                //写入排产计划明细信息
                                Sql = String.format("insert into T_PLAN_SCHEDULING_D "
                                        +"      (ID, "
                                        +"      LINE_CODE, "
                                        +"      SEQ_NO, "
                                        +"      WORK_SHIFT, "
                                        +"      PRODUCTION_CODE, "
                                        +"      PRODUCTION_NUM, "
                                        +"      SCHEDULING_PLAN_CODE, "
                                        +"      PRODUCT_CODE ) "
                                        +"  SELECT sys_guid(), "
                                        +"          'tz0101', "
                                        +"          NVL((SELECT MAX(SEQ_NO) +1 FROM T_PLAN_SCHEDULING_D "
                                        +"                    WHERE  SCHEDULING_PLAN_CODE ='%s'  AND LINE_CODE='tz0101' ),1), "
                                        +"          '%s', "
                                        +"          '%s', "
                                        +"          '1', "
                                        +"          '%s', "
                                        +"          NVL('%s','yxccpbm') "
                                        +"      FROM DUAL" ,scheduling_plan_code,rec.getStr("demand_work_shift"),production_code,scheduling_plan_code,rec.getStr("demand_product_code"));
                                msg = "写入排产计划明细表：计划编码【"+scheduling_plan_code+"】，班次【"+rec.getStr("demand_work_shift")+"】，生产编码【"+rec.getStr("production_code")+"】，" +
                                        "产品编码【"+rec.getStr("demand_product_code")+"】；";
                                Log.Write(strServiceCode, LogLevel.Information, msg);
                                Db.update(Sql);
                                //标识营销车信息为已涂装排产
                                Sql = String.format("update t_plan_demand set is_paint = '1' where id='%s' ",rec.getStr("id"));
                                msg = "标识营销车信息为已涂装排产 t_plan_demand：id【"+rec.getStr("id")+"】";
                                Log.Write(strServiceCode, LogLevel.Information, msg);
                                Db.update(Sql);

                            }
                            //更新当天计划状态为预排产
                            Sql = String.format("update t_plan_scheduling set SCHEDULING_PLAN_STATUS = '0' where id='%s' ",rec.getStr("id"));
                            msg = "更新当天计划状态为预排产 t_plan_scheduling：计划编码【"+scheduling_plan_code+"】";
                            Log.Write(strServiceCode, LogLevel.Information, msg);
                            Db.update(Sql);
                            msg = "计划日期【"+scheduling_plan_date+"】营销车排产成功！";
                            return true;
                        }
                    });
				}else{
                    msg = "计划日期【"+scheduling_plan_date+"】不存在未排产的营销车！";
                    Log.Write(strServiceCode, LogLevel.Information, msg);
                }
            }
        }else{
            msg = "不存在大于等于今天的涂装计划！";
            Log.Write(strServiceCode, LogLevel.Information,msg);
        }

		return msg;
	}
}

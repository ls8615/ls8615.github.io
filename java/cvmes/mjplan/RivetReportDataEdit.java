package cvmes.mjplan;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;

import cvmes.common.AbstractSubServiceThread;
import cvmes.common.Log;
import cvmes.common.LogLevel;

/**
 * 总装车架上线扫描扣减库存
 * */
public class RivetReportDataEdit extends AbstractSubServiceThread {
	
	private String msg = "";
	@Override
	public void initServiceCode() {
		// TODO Auto-generated method stub
		strServiceCode="RivetReportDataEdit";
	}

	@Override
	public String runBll(Record rec_service) throws Exception {
		// TODO Auto-generated method stub
		//获取实绩点
		String points = rec_service.getStr("SERVICE_PARA1_VALUE");
		if(points !=null && !"".equals(points)){
			String [] pointList = points.split("\\|");
			StringBuffer pointBuffer = new StringBuffer();
			for(String point :pointList){
				pointBuffer.append("'"+point+"',");
			}
			Record rd = Db.findFirst(String.format(PASSED_RECORD_SQL, pointBuffer.toString().substring(0, pointBuffer.length()-1)));
			String passedId = rd.getStr("id");
			String kCarriage = rd.getStr("k_carriage");
			if(kCarriage !=null && !"".equals(kCarriage)){
				String [] split = kCarriage.split("-N");
				if(split.length>1){
					String match = "\\d+";
					if(split[1].matches(match)){
						String kCarriagePn = "N"+split[1];
						int i =Db.update(String.format(UPDATE_STOCK_PN,kCarriagePn,split[0]));
						msg = "扣减成功！过点ID:【"+passedId+"】扣减返回【"+i+"】";
					}else{
						//
						int i =Db.update(String.format(UPDATE_STOCK_NOT_PN,kCarriage));
						msg = "扣减成功！过点ID:【"+passedId+"】扣减返回【"+i+"】";
					}
				}else{
					//
					int i =Db.update(String.format(UPDATE_STOCK_NOT_PN,kCarriage));
					msg = "扣减成功！过点ID:【"+passedId+"】扣减返回【"+i+"】";
				}
			}else{
				msg = "需求产品表车架字段为空！【"+passedId+"】";
				Log.Write(strServiceCode, LogLevel.Error,msg);
			}
			Log.Write(strServiceCode, LogLevel.Information,msg);
			Db.update(String.format(UPDATE_PASSED_RECORD_SQL,passedId));
		}else{
			msg = "实绩点未配置";
			Log.Write(strServiceCode, LogLevel.Error,msg);
		}
		return msg;
	}
	
	private static final String UPDATE_STOCK_NOT_PN="update t_report_frame_balance t set t.jc0=t.jc0-1 where t.k_carriage_pn is null and t.ljbh='%s'";
	
	private static final String UPDATE_STOCK_PN="update t_report_frame_balance t set t.jc0=t.jc0-1 where t.k_carriage_pn='%s' and t.ljbh='%s'";
	
	private static final String UPDATE_PASSED_RECORD_SQL="update T_ACTUAL_PASSED_RECORD t set t.DEDUCT_STOCK='1' where t.id='%s'";

	private static final String PASSED_RECORD_SQL="select t.id,t1.k_carriage from T_ACTUAL_PASSED_RECORD t "
			+ "left join T_PLAN_DEMAND_PRODUCT t1 on t.production_code=t1.production_code "
			+ "where t.deduct_stock='0' and t.actual_point_code in(%s) and rownum =1 ";
}

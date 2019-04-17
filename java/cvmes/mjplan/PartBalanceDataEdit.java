package cvmes.mjplan;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;

import cvmes.common.AbstractSubServiceThread;

/**
 * 零部件平衡表数据清算服务
 * */
public class PartBalanceDataEdit extends AbstractSubServiceThread {

	private String msg = "";
	
	@Override
	public void initServiceCode() {
		// TODO Auto-generated method stub
		strServiceCode = "PartBalanceDataEdit";
	}

	@Override
	public String runBll(Record rec_service) throws Exception {
		// TODO Auto-generated method stub
		//分解铆接计划车架，获得零部件
		splitRivetPlan();
		//获取铆接需求
		loadRivetDemand();
		//日需求数
		loadDayDemand();
		msg="零部件计划平衡表数据初始化完成";
		return msg;
	}
	
	private void loadDayDemand(){
		List<Record> codeList = Db.find("select ID,PART_CODE from T_PLAN_PARTS_BALANCE");
		for(Record rd :codeList){
			String id = rd.getStr("ID");
			String partCode = rd.getStr("PART_CODE");
			List<Record> dayList = Db.find(String.format(DAY_DEMAND_SQL, partCode));
			BigDecimal dayMjStock = new BigDecimal(0);
			BigDecimal dayZzStock = new BigDecimal(0);
			if(dayList!=null && dayList.size()>0){
				for(Record dayCord : dayList){
					if("0".equals(dayCord.get("SCHEDULING_STATE"))){
						//日需求
						dayZzStock = dayCord.getBigDecimal("planStock");
					}else if("1".equals(dayCord.get("SCHEDULING_STATE"))){
						//日铆接
						dayMjStock = dayCord.getBigDecimal("planStock");
					}
				}
			}
			//更新平衡表数据
			Db.update(String.format(UPDATE_BALANCE_DAY_SQL, 
									dayMjStock.intValue(),
									dayZzStock.intValue(),id));
		}
	}
	
	private static final String DAY_DEMAND_SQL="select sum(c.material_num) planStock,b.SCHEDULING_STATE from t_plan_scheduling a "
											+ "left join t_plan_scheduling_d b on a.scheduling_plan_code = b.scheduling_plan_code "
											+ "left join t_base_bom c on b.product_code = c.bom_code "
											+ "where (b.line_code = 'cj0101' or b.line_code = 'cj0102') "
											+ "and a.scheduling_plan_date = to_date(to_char(sysdate,'yyyy/mm/dd'), 'yyyy/mm/dd') "
											+ "and c.material_code = '%s' group by b.SCHEDULING_STATE ";
	
	private static final String UPDATE_BALANCE_DAY_SQL="update T_PLAN_PARTS_BALANCE set "
													+ "DAY_RIVET=%s ,"
													+ "DAY_DEMAND=%s "
													+ "where id='%s'";
	
	private void  loadRivetDemand(){
		//获取平衡表里面所有的零件
		List<Record> codeList = Db.find("select ID,PART_CODE from T_PLAN_PARTS_BALANCE");
		for(Record rd :codeList){
			String id = rd.getStr("ID");
			String partCode = rd.getStr("PART_CODE");
			//铆接2-4计划数
			BigDecimal mj2Stock = new BigDecimal(0);
			BigDecimal mj3Stock = new BigDecimal(0);
			BigDecimal mj4Stock = new BigDecimal(0);
			List<Record> mjList = Db.find(String.format(MJ_PLAN_SQL, partCode));
			if(mjList !=null && mjList.size()>0){
				for(int i=0;i<mjList.size();i++){
					if(i==0)mj2Stock = mjList.get(i).getBigDecimal("planStock");
					if(i==1)mj3Stock = mjList.get(i).getBigDecimal("planStock");
					if(i==2)mj4Stock = mjList.get(i).getBigDecimal("planStock");
				}
			}
			BigDecimal tl1Stock = new BigDecimal(0);
			BigDecimal tl2Stock = new BigDecimal(0);
			BigDecimal tl3Stock = new BigDecimal(0);
			//投料1-3
			List<Record> tlList = Db.find(String.format(TL_PLAN_SQL, partCode));
			if(tlList !=null && tlList.size()>0){
				for(int i=0;i<tlList.size();i++){
					if(i==0)tl1Stock = tlList.get(i).getBigDecimal("planStock");
					if(i==1)tl2Stock = tlList.get(i).getBigDecimal("planStock");
					if(i==2)tl3Stock = tlList.get(i).getBigDecimal("planStock");
				}
			}
			BigDecimal wg1Stock = new BigDecimal(0);
			BigDecimal wg2Stock = new BigDecimal(0);
			BigDecimal wg3Stock = new BigDecimal(0);
			//完工1-3
			List<Record> wgList = Db.find(String.format(WG_PLAN_SQL, partCode));
			if(wgList !=null && wgList.size()>0){
				for(int i=0;i<wgList.size();i++){
					if(i==0)wg1Stock = wgList.get(i).getBigDecimal("planStock");
					if(i==1)wg2Stock = wgList.get(i).getBigDecimal("planStock");
					if(i==2)wg3Stock = wgList.get(i).getBigDecimal("planStock");
				}
			}
			//投料3天累计
			Record tlThree = Db.findFirst(String.format(TL_THREE_PLAN_SQL, partCode));
			BigDecimal tlThreeStock = tlThree!=null&&tlThree.getBigDecimal("planStock")!=null?tlThree.getBigDecimal("planStock"):new BigDecimal(0);
			//完工3天累计
			Record wgThree = Db.findFirst(String.format(WG_THREE_PLAN_SQL, partCode));
			BigDecimal wgThreeStock = wgThree!=null&&wgThree.getBigDecimal("planStock")!=null?wgThree.getBigDecimal("planStock"):new BigDecimal(0);
			//更新平衡表数据
			Db.update(String.format(UPDATE_BALANCE_SQL, 
									mj2Stock.intValue(),
									mj3Stock.intValue(),
									mj4Stock.intValue(),
									tl1Stock.intValue(),
									tl2Stock.intValue(),
									tl3Stock.intValue(),
									wg1Stock.intValue(),
									wg2Stock.intValue(),
									wg3Stock.intValue(),
									tlThreeStock.intValue(),
									wgThreeStock.intValue(),id));
		}
	}
	
	private static final String UPDATE_BALANCE_SQL="update T_PLAN_PARTS_BALANCE set "
													+ "RIVET_DEMAND_TWO=%s ,"
													+ "RIVET_DEMAND_THREE=%s ,"
													+ "RIVET_DEMAND_FOUR=%s ,"
													+ "RELEASE_PLAN_ONE=%s ,"
													+ "RELEASE_PLAN_TWO=%s ,"
													+ "RELEASE_PLAN_THREE=%s ,"
													+ "COMPLETE_PLAN_ONE=%s ,"
													+ "COMPLETE_PLAN_TWO=%s ,"
													+ "COMPLETE_PLAN_THREE=%s ,"
													+ "THREE_DAY_RELEASE=%s ,"
													+ "THREE_DAY_COMPLETE=%s "
													+ "where id='%s'";
	
	private static final String TL_THREE_PLAN_SQL="select sum(a.production_num) - sum(c.ok_num) planStock from t_Plan_Scheduling_d a "
												+ "left join t_Plan_Scheduling b on a.scheduling_plan_code = b.scheduling_plan_code "
												+ "left join T_ACTUAL_CARD c on a.production_code = c.production_code "
												+ "where b.scheduling_plan_type = '4' and b.scheduling_plan_status = '3' "
												+ "and a.product_code ='%s' ";
	
	private static final String WG_THREE_PLAN_SQL="select sum(a.production_num) - sum(c.ok_num) planStock from t_Plan_Scheduling_d a "
												+ "left join t_Plan_Scheduling b on a.scheduling_plan_code = b.scheduling_plan_code "
												+ "left join T_ACTUAL_CARD c on a.production_code = c.production_code "
												+ "where b.scheduling_plan_type = '3' and b.scheduling_plan_status = '3' "
												+ "and a.product_code ='%s' ";
	
	private static final String MJ_PLAN_SQL="select sum(c.material_num) planStock,a.scheduling_plan_date plandate from t_plan_scheduling a "
											+ "left join t_plan_scheduling_d b on a.scheduling_plan_code =b.scheduling_plan_code "
											+ "left join t_base_bom c on b.product_code = c.bom_code "
											+ "where (b.line_code = 'cj0101' or b.line_code = 'cj0102') and b.SCHEDULING_STATE = '0' "
											+ "and a.scheduling_plan_type = '1' and c.material_code = '%s' "
											+ "and a.scheduling_plan_date > to_date(to_char(sysdate,'yyyy/mm/dd'), 'yyyy/mm/dd') "
											+ "group by a.scheduling_plan_date order by a.scheduling_plan_date asc ";
	
	private static final String TL_PLAN_SQL="select sum(a.production_num) planStock from t_Plan_Scheduling_d a "
											+ "left join t_Plan_Scheduling b on a.scheduling_plan_code = b.scheduling_plan_code "
											+ "where b.scheduling_plan_type = '4' and b.scheduling_plan_status= '3' "
											+ "and a.product_code = '%s' and b.scheduling_plan_date >= to_date(to_char(sysdate,'yyyy/mm/dd'), 'yyyy/mm/dd') "
											+ "group by b.scheduling_plan_date order by b.scheduling_plan_date asc ";
	
	private static final String WG_PLAN_SQL="select sum(a.production_num) planStock from t_Plan_Scheduling_d a "
											+ "left join t_Plan_Scheduling b on a.scheduling_plan_code = b.scheduling_plan_code "
											+ "where b.scheduling_plan_type = '3' and b.scheduling_plan_status = '3' and a.product_code = '%s' "
											+ "and b.scheduling_plan_date >= to_date(to_char(sysdate,'yyyy/mm/dd'),'yyyy/mm/dd') "
											+ "group by b.scheduling_plan_date order by b.scheduling_plan_date asc ";
	
	private static final String GET_PARTS_BY_PLAN="select t4.material_code PART_CODE "
												+ "from T_PLAN_SCHEDULING t1 "
												+ "left join T_PLAN_SCHEDULING_D t2 on t1.scheduling_plan_code=t2.scheduling_plan_code "
												+ "left join t_base_bom t3 on t2.product_code = t3.bom_code "
												+ "left join T_BASE_MATERIAL t4 on t4.material_code=t3.material_code "
												+ "where t1.scheduling_plan_status='3' and t1.scheduling_plan_type='1' "
												+ "and t3.bom_type='1' and t4.K_CATEGORY in ('2','3','10') "
												+ "and t1.scheduling_plan_date >= to_date(to_char(sysdate,'yyyy/mm/dd'),'yyyy/mm/dd') "
												+ "group by t4.material_code ";

	private void splitRivetPlan(){
		List<Record> codeList = Db.find("select PART_CODE from T_PLAN_PARTS_BALANCE");
		Map<String,String> codeMap = new HashMap<String,String>();
		if(codeList!=null && codeList.size()>0){
			for(Record rd :codeList){
				String partCode = rd.getStr("PART_CODE");
				if(!codeMap.containsKey(partCode)){
					codeMap.put(partCode, partCode);
				}
			}
		}
		Map<String,String> insertMap = new HashMap<String,String>();
		//分解大于等于sysdate的铆接计划，得到零件图号
		List<Record> rivetCodeList = Db.find(GET_PARTS_BY_PLAN);
		//判断零件图号是否存在
		for(Record rd : rivetCodeList){
			String partCode = rd.getStr("PART_CODE");
			if(!codeMap.containsKey(partCode) && !insertMap.containsKey(partCode)){
				insertMap.put(partCode, partCode);
			}
		}
		//新增平衡表不存在的零件图号
		if(!insertMap.isEmpty()){
			StringBuffer insertSql = new StringBuffer();
			insertSql.append("insert into T_PLAN_PARTS_BALANCE (ID,PART_CODE)");
			boolean first = true;
			for(String key : insertMap.keySet()){
				if(!first){
					insertSql.append(" UNION ALL ");
					insertSql.append(" select SYS_GUID(),");
					insertSql.append("'"+key+"'");
					insertSql.append(" from dual ");
				}else{
					insertSql.append(" select SYS_GUID(),");
					insertSql.append("'"+key+"'");
					insertSql.append(" from dual ");
					first = false;
				}
			}
			if(!first){
				//Log.Write(strServiceCode, LogLevel.Information, insertSql.toString());
				Db.update(insertSql.toString());
			}
		}
	}
}

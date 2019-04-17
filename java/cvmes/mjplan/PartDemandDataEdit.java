package cvmes.mjplan;

import java.math.BigDecimal;
import java.util.List;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;

import cvmes.common.AbstractSubServiceThread;

/**
 * 同步装车需求6，7值
 * */
public class PartDemandDataEdit extends AbstractSubServiceThread {

	private String msg = "";
	
	@Override
	public void initServiceCode() {
		// TODO Auto-generated method stub
		strServiceCode = "PartDemandDataEdit";
	}

	@Override
	public String runBll(Record rec_service) throws Exception {
		// TODO Auto-generated method stub
		//初始化装车需求6,7数据
		Db.update("update T_PLAN_PARTS_BALANCE set ASSEMBLY_PLAN_SIX=0,ASSEMBLY_PLAN=0");
		//获取总装计划
		List<Record> codeList = Db.find("select t.rivet_code from t_plan_rivet_scheduling t where t.plan_date >= sysdate order by t.plan_date asc ");
		if(codeList !=null && codeList.size()>=5){
			if(codeList.size() >= 5){
				loadDay6Demand(codeList);
			}
			if(codeList.size() >= 6){
				loadDay7Demand(codeList);
			}
			msg="零部件计划装车需求数据初始化完成";
		}else{
			msg="暂无需处理数据";
		}
		return msg;
	}

	private void loadDay7Demand(List<Record> codeList){
		StringBuffer codeBuffer = new StringBuffer();
		String lastCode = "";
		for(int i=0;i<6;i++){
			if(i==5){
				lastCode = codeList.get(i).getStr("RIVET_CODE");
				codeBuffer.append("'"+lastCode+"'");
			}else{
				codeBuffer.append("'"+codeList.get(i).getStr("RIVET_CODE")+"',");
			}
		}
		splitCarFrame(codeBuffer, lastCode,false);
	}
	
	private void loadDay6Demand(List<Record> codeList){
		StringBuffer codeBuffer = new StringBuffer();
		String lastCode = "";
		for(int i=0;i<5;i++){
			if(i==4){
				lastCode = codeList.get(i).getStr("RIVET_CODE");
				codeBuffer.append("'"+lastCode+"'");
			}else{
				codeBuffer.append("'"+codeList.get(i).getStr("RIVET_CODE")+"',");
			}
		}
		splitCarFrame(codeBuffer, lastCode,true);
	}

	private void splitCarFrame(StringBuffer codeBuffer, String lastCode,Boolean isSix) {
		List<Record> demandList = Db.find(String.format(DAY_DEMAND_SQL, codeBuffer.toString()));
		StringBuffer splitSb = new StringBuffer();
		if(demandList !=null && demandList.size()>0){
			BigDecimal tempNum = new BigDecimal(0);
			for(Record rd :demandList){
				String kCarriage = rd.getStr("k_carriage");
				BigDecimal zzDemand = rd.getBigDecimal("DEMAND_NUM");
				BigDecimal dayStock = rd.getBigDecimal("jc0");
				BigDecimal zzDayDemand = rd.getBigDecimal("zz_day_demand");
				BigDecimal mjDayDemand = rd.getBigDecimal("mj_day_demand");
				BigDecimal mjDemand = rd.getBigDecimal("before_demand");
				//装车-结存-日装车+日铆接-铆接>0 开始分解
				BigDecimal splitNum = zzDemand.subtract(dayStock).subtract(zzDayDemand).add(mjDayDemand).subtract(mjDemand);
				if(splitNum.compareTo(tempNum)>0){
					//大于0，开启分解
					splitSb.append("'"+kCarriage+"',");
				}
			}
		}
		if(splitSb.length()>0){
			List<Record> materialList = Db.find(String.format(SPLIT_SQL,lastCode, splitSb.toString().substring(0, splitSb.length()-1)));
			for(Record mater : materialList){
				if(isSix){
					Db.update(String.format(UPDATE_SIX_SQL,mater.getBigDecimal("sumCount").longValue(),mater.getStr("material_code")));
				}else{
					Db.update(String.format(UPDATE_SEVEN_SQL,mater.getBigDecimal("sumCount").longValue(),mater.getStr("material_code")));
				}
			}
		}
	}
	private static final String UPDATE_SEVEN_SQL="update T_PLAN_PARTS_BALANCE set "
			+ "ASSEMBLY_PLAN=%s "
			+ "where PART_CODE='%s'";
	
	private static final String UPDATE_SIX_SQL="update T_PLAN_PARTS_BALANCE set "
			+ "ASSEMBLY_PLAN_SIX=%s "
			+ "where PART_CODE='%s'";
	
	private static final String SPLIT_SQL="select b.material_code, sum(t1.material_num) sumCount "
			+ "from t_plan_rivet_scheduling_d t left join t_base_bom t1 on t.k_carriage=t1.bom_code "
			+ "left join T_BASE_MATERIAL b on t1.material_code=b.material_code where t1.bom_type='1' and b.K_CATEGORY in ('2','3','10') and b.k_process_code is not null "
			+ "and t.rivet_code='%s' and t.k_carriage in(%s) group by b.material_code ";
	
	private static final String DAY_DEMAND_SQL="select s.k_carriage,s.demand_num,NVL(s1.jc0,0)jc0,NVL(s2.production_num,0) zz_day_demand,NVL(s3.production_num,0) mj_day_demand,NVL(s4.production_num,0) before_demand "
			+ "from (select t.k_carriage,sum(t.demand_num)demand_num "
			+ "from t_plan_rivet_scheduling_d t where t.rivet_code in(%s) group by t.k_carriage)s "
			+ "left join (select decode(t.k_carriage_pn,null,t.LJBH,t.LJBH||'-'||t.k_carriage_pn) k_carriage,t.jc0 "
			+ "from T_REPORT_FRAME_BALANCE t)s1 on s.k_carriage =s1.k_carriage "
			+ "left join (SELECT p.K_CARRIAGE,sum(d.production_num) production_num "
			+ "FROM T_PLAN_SCHEDULING m LEFT JOIN T_PLAN_SCHEDULING_D d ON d.SCHEDULING_PLAN_CODE=m.SCHEDULING_PLAN_CODE "
			+ "LEFT JOIN T_PLAN_DEMAND_PRODUCT p ON p.PRODUCTION_CODE=d.PRODUCTION_CODE "
			+ "WHERE m.WORKSHOP_CODE='zz01'AND TO_CHAR(m.SCHEDULING_PLAN_DATE,'yyyy-mm-dd')=to_char(sysdate,'yyyy-mm-dd') "
			+ "group by p.K_CARRIAGE)s2 on s.k_carriage =s2.k_carriage "
			+ "left join (select t.k_carriage,sum(t.production_num)production_num from "
			+ "(select decode(c.k_carriage_pn,null,b.product_code,b.product_code||'-'||c.k_carriage_pn) k_carriage,b.production_num "
			+ "from t_plan_scheduling a left join t_plan_scheduling_d b on a.scheduling_plan_code = b.scheduling_plan_code "
			+ "left join t_plan_demand_product c on b.production_code = c.production_code "
			+ "where a.scheduling_plan_date =to_date(to_char(sysdate, 'yyyy/mm/dd'), 'yyyy/mm/dd') "
			+ "and a.scheduling_plan_status='3' and a.scheduling_plan_type='1')t group by t.k_carriage)s3 on s.k_carriage =s3.k_carriage "
			+ "left join (select t.k_carriage,sum(t.production_num)production_num from "
			+ "(select decode(c.k_carriage_pn,null,b.product_code,b.product_code||'-'||c.k_carriage_pn) k_carriage,b.production_num "
			+ "from t_plan_scheduling a left join t_plan_scheduling_d b on a.scheduling_plan_code = b.scheduling_plan_code "
			+ "left join t_plan_demand_product c on b.production_code = c.production_code "
			+ "where a.scheduling_plan_date >to_date(to_char(sysdate, 'yyyy/mm/dd'), 'yyyy/mm/dd') "
			+ "and a.scheduling_plan_status='3' and a.scheduling_plan_type='1')t "
			+ "group by t.k_carriage)s4  on s.k_carriage =s4.k_carriage ";
}

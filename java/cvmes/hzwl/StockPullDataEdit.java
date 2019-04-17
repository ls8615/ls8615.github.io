package cvmes.hzwl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;

import cvmes.common.AbstractSubServiceThread;
import cvmes.common.Log;
import cvmes.common.LogLevel;

/**
 * 库存拉动指示编辑
 * */
public class StockPullDataEdit extends AbstractSubServiceThread {

	private String msg = "";
	
	@Override
	public void initServiceCode() {
		// TODO Auto-generated method stub
		strServiceCode = "StockPullDataEdit";
	}
	
	@Override
	public String runBll(Record rec_service) throws Exception {
		// TODO Auto-generated method stub
		//SERVICE_PARA1_VALUE
		String points = rec_service.getStr("SERVICE_PARA1_VALUE");
		if(points !=null && !"".equals(points)){
			String [] pointList = points.split("|");
			for(String point : pointList){
				//查询对应指示点的记录是否有
				String checkPointSql = String.format("select t.id,t1.station_code from t_actual_passed_record t"
						+ " left join T_MODEL_ACTUAL_POINT t1 on t.actual_point_code=t1.actual_point_code"
						+ " where t.actual_point_code='%s' and t.pull_status='0'", point);
				List<Record> list = Db.find(checkPointSql);
				if(list !=null && list.size()>0){
					//有对应实绩点的过点记录，开启库存判断
					String stationCode = list.get(0).getStr("station_code");
					String partsSql = String.format(PART_SQL, stationCode);
					List<Record> partList = Db.find(partsSql);
					StringBuffer insertSql = new StringBuffer();
					insertSql.append("insert into T_LWM_MATERIALPULL_DEMAND (ID,WAREHOUSE_CODE,WAREHOUSE_POS_CODE,PARTS_CODE,DEMAND_PIECE_NUM,DEMAND_BOX_NUM,CREATE_TIME,DEMAND_STATUS)");
					boolean first = true;
					for(Record re : partList){
						//最小库存数
						BigDecimal minStock = re.getBigDecimal("min_stock")==null?new BigDecimal(0):re.getBigDecimal("min_stock");
						//最大库存数
						//BigDecimal maxStock = re.getBigDecimal("max_stock")==null?new BigDecimal(0):re.getBigDecimal("max_stock");
						//线边库编码
						String warehouseCode = re.getStr("warehouse_code");
						//线边库库位编码
						String warehousePosCode = re.getStr("warehouse_pos_code");
						//零件编码
						String partCode = re.getStr("parts_code");
						//当前库存数
						BigDecimal stockNum = re.getBigDecimal("stock_num")==null?new BigDecimal(0):re.getBigDecimal("stock_num");
						//需求表库存数
						BigDecimal demandNum = re.getBigDecimal("demandNum")==null?new BigDecimal(0):re.getBigDecimal("demandNum");
						//需求单库存数
						BigDecimal demandMainNum = re.getBigDecimal("demandMainNum")==null?new BigDecimal(0):re.getBigDecimal("demandMainNum");
						//一箱的零件个数
						BigDecimal snp = re.getBigDecimal("snp")==null?new BigDecimal(0):re.getBigDecimal("snp");
						if(minStock.compareTo(new BigDecimal(0))>0){
							//当前实绩库存=当前库存+需求表库存+需求单库存
							BigDecimal trueDemand = stockNum.add(demandNum).add(demandMainNum);
							if(trueDemand.compareTo(minStock)<0){
								//当前实际库存小于最小库存，触发新增需求单
								if(!first){
									insertSql.append(" UNION ALL ");
								}
								insertSql.append(" select SYS_GUID(),");
								insertSql.append("'"+warehouseCode+"',");
								insertSql.append("'"+warehousePosCode+"',");
								insertSql.append("'"+partCode+"',");
								insertSql.append(snp.intValue()+",");
								insertSql.append("1,sysdate,'0' from dual ");
								first = false;
							}
						}
					}
					if(!first){
						//Log.Write(strServiceCode, LogLevel.Information, insertSql.toString());
						Db.update(insertSql.toString());
					}
					List<String> ids = new ArrayList<String>();
					for(Record r : list){
						ids.add("'"+r.getStr("id")+"'");
					}
					String updateSql = String.format("update t_actual_passed_record set pull_status='1' where id in (%s)", ids.toString());
					//Log.Write(strServiceCode, LogLevel.Information, updateSql);
					Db.update(updateSql);
				}
			}
			msg="库存拉动成功！";
		}else{
			msg = "实绩点未配置";
			Log.Write(strServiceCode, LogLevel.Error,msg);
		}
		return msg;
	}

	private static final String PART_SQL="select t.min_stock, t.max_stock, t.warehouse_code, t.warehouse_pos_code,"
			+ " t.parts_code, t1.stock_num,t5.snp,"
			+ " (select sum(t2.demand_piece_num) from T_LWM_MATERIALPULL_DEMAND t2"
			+ " where t.parts_code= t2.parts_code and t.warehouse_code=t2.warehouse_code "
			+ "       and t.warehouse_pos_code=t2.warehouse_pos_code and t2.demand_status='0') demandNum,"
			+ " (select sum(s2.demand_parts_num) from T_LWM_MP_DEMAND_MAIN s1 left join"
			+ " T_LWM_MP_DEMAND_DETAIL s2 on s1.demand_code = s2.demand_code "
			+ " where s1.demand_type='0' and s1.document_status in ('0','1')"
			+ " and s1.warehouse_code=t.warehouse_code "
			+ " and s1.warehouse_pos_code=t.warehouse_pos_code"
			+ " and s2.parts_code = t.parts_code)demandMainNum"
			+ " from T_LWM_PART_PARA t"
			+ " left join "
			+ " T_LWM_WAREHOUSE_NUM t1 on t.parts_code = t1.product_code"
			+ " left join t_base_material t5 on t.parts_code = t5.material_code"
			+ " where t.bom_station_code='%s'"
			+ " and t.pull_approach_type='K'";
}

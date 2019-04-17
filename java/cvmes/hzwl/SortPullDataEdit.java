package cvmes.hzwl;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.IAtom;
import com.jfinal.plugin.activerecord.Record;


import cvmes.common.AbstractSubServiceThread;
import cvmes.common.Log;
import cvmes.common.LogLevel;

/**
 * 排序拉动服务
 * */
public class SortPullDataEdit extends AbstractSubServiceThread {
	private String msg = "";
	@Override
	public void initServiceCode() {
		// TODO Auto-generated method stub
		strServiceCode = "SortPullDataEdit";
	}
	
	@Override
	public String runBll(Record rec_service) throws Exception {
		// TODO Auto-generated method stub
		//SERVICE_PARA1_VALUE
		//1.获取实绩点的信息
		String points = rec_service.getStr("SERVICE_PARA1_VALUE");
		if(points !=null && !"".equals(points)){
			String [] pointList = points.split("\\|");
			for(String point : pointList){
				//2.查询对应指示点的记录是否存在未进行排序拉动处理的
				String checkPointSql = String.format("select * from ( select t.id,t1.station_code, " +
						"       nvl((select snp " +
						"          from T_LWM_PART_PARA para, T_CRAFT_EQUIPMENT ce " +
						"         where para.pull_approach_type = 'P' " +
						"           and para.bom_station_code = t1.station_code " +
						"           and para.sorting_station_tools = CE.CRAFT_EQUIPMENT_CODE " +
						"           and ce.snp is not null " +
						"           and rownum = 1),0) snp " +
						"  from t_actual_passed_record t " +
						"  left join T_MODEL_ACTUAL_POINT t1 " +
						"    on t.actual_point_code = t1.actual_point_code " +
						"where t.actual_point_code='%s' and  t.sort_pull_status='0'  order by t.passed_time  ) t  where  rownum <=100 " , point);
				List<Record> list = Db.find(checkPointSql);
				if(list !=null && list.size()>0) {
                    Db.tx(new IAtom() {
                        public boolean run() throws SQLException {
                            //有对应实绩点的过点记录，开启库存判断
                            String stationCode = list.get(0).getStr("station_code");
                            int snp = list.get(0).getInt("snp");
                            //标准装箱数大于0
                            if (snp > 0) {
                                //查询当前库存数量和未收货需求单数量
                                String partsSql = String.format("select * from (select min(t.min_stock) min_stock, min(t.max_stock) max_stock, t.warehouse_code, t.warehouse_pos_code,"
                                        + "  sum(t1.stock_num) stock_num,"
                                        + " (select sum(s2.demand_parts_num) from T_LWM_MP_DEMAND_MAIN s1 left join"
                                        + " T_LWM_MP_DEMAND_DETAIL s2 on s1.demand_code = s2.demand_code "
                                        + " where s1.demand_type='1' and s1.document_status in ('0','1')"
                                        + " and s1.warehouse_code=t.warehouse_code "
                                        + " and s1.warehouse_pos_code=t.warehouse_pos_code"
                                        + " )demandMainNum "
                                        + " from T_LWM_PART_PARA t"
                                        + " left join  T_LWM_WAREHOUSE_NUM t1 on t.parts_code = t1.product_code"
                                        + " where t.bom_station_code='%s'"
                                        + " and t.pull_approach_type='P' and t.min_stock > 0 "
                                        + " GROUP BY t.warehouse_code, t.warehouse_pos_code) t where rownum =1 ", stationCode);

                                Record partRec = Db.findFirst(partsSql);
                                //最小安全库存数
                                BigDecimal minStock = partRec.getBigDecimal("min_stock") == null ? new BigDecimal(0) : partRec.getBigDecimal("min_stock");
                                //当前库存数
                                BigDecimal stockNum = partRec.getBigDecimal("stock_num") == null ? new BigDecimal(0) : partRec.getBigDecimal("stock_num");
                                //需求单数量
                                BigDecimal demandMainNum = partRec.getBigDecimal("demandMainNum") == null ? new BigDecimal(0) : partRec.getBigDecimal("demandMainNum");
                                //最小安全库存大于0
                                if (minStock.compareTo(new BigDecimal(0)) > 0) {
                                    //实际库存
                                    BigDecimal trueDemand = stockNum.add(demandMainNum);
                                    if (trueDemand.compareTo(minStock) < 0) {
                                        //若实际库存小于最小安全库存
                                        //查询获取排序拉动该工位的最后一台车的下一台车，若没有则取当天计划的第一台车
                                        String carInfoSql = String.format("select production_code from t_lwm_sort_pull_station_car pc where pc.station_code ='%s' and rownum ='1' ", stationCode);
                                        Record carInfoRec = Db.findFirst(carInfoSql);

                                        String nextCarSql = "";
                                        if (carInfoRec == null) {//若不存在则取当天计划的第一台车存在排序件的车辆
                                            nextCarSql = "";
                                            nextCarSql = String.format("select ps.scheduling_plan_date, "
                                                    + "       psd.seq_no, "
                                                    + "       pdp.production_code, "
                                                    + "       sum(material_num) material_nums "
                                                    + "  from t_plan_scheduling ps "
                                                    + "  left join t_plan_scheduling_d psd "
                                                    + "    on ps.scheduling_plan_code = psd.scheduling_plan_code "
                                                    + "  left join t_plan_demand_product pdp "
                                                    + "    on psd.production_code = pdp.production_code "
                                                    + "  left join t_plan_punch_bom_material pbm "
                                                    + "    on pbm.bom_code = pdp.k_carbody_code "
                                                    + "  left join T_BASE_MATERIAL bm on pbm.material_code = bm.k_drawing_no  "
                                                    + "  left join t_lwm_part_para lpp "
                                                    + "    on lpp.parts_code = bm.material_code "
                                                    + " where ps.scheduling_plan_type = '7' "
                                                    + "   and to_char(ps.scheduling_plan_date, 'yyyy-mm-dd') >= "
                                                    + "       to_char(sysdate, 'yyyy-mm-dd') "
                                                    + "   and pdp.demand_product_type in ('0', '5') "
                                                    + "   and ps.available_status = '1' "
                                                    + "   and psd.available_status = '1' "
                                                    + "   AND nvl(PBM.MATERIAL_NUM,0) != 0 "
                                                    + "   and lpp.pull_approach_type = 'P' "
                                                    + "   and lpp.bom_station_code = '%s' "
                                                    + " group by ps.scheduling_plan_date, psd.seq_no, pdp.production_code "
                                                    + " order by ps.scheduling_plan_date, PSD.SEQ_NO ", stationCode);
                                        } else { //若存在则取下一台存在排序件的车辆
                                            String last_production_code = carInfoRec.getStr("production_code");
                                            nextCarSql = "";
                                            nextCarSql = String.format("select ps.scheduling_plan_date, "
                                                    + "       psd.seq_no, "
                                                    + "       pdp.production_code, "
                                                    + "       sum(material_num) material_nums "
                                                    + "  from t_plan_scheduling ps "
                                                    + "  left join t_plan_scheduling_d psd "
                                                    + "    on ps.scheduling_plan_code = psd.scheduling_plan_code "
                                                    + "  left join t_plan_demand_product pdp "
                                                    + "    on psd.production_code = pdp.production_code "
                                                    + "  left join t_plan_punch_bom_material pbm "
                                                    + "    on pbm.bom_code = pdp.k_carbody_code "
                                                    + "  left join T_BASE_MATERIAL bm on pbm.material_code = bm.k_drawing_no  "
                                                    + "  left join t_lwm_part_para lpp "
                                                    + "    on lpp.parts_code = bm.material_code "
                                                    + " where ps.scheduling_plan_type = '7' "
                                                    + "   and pdp.demand_product_type in ('0', '5') "
                                                    + "   and ps.available_status = '1' "
                                                    + "   and psd.available_status = '1' "
                                                    + "   AND nvl(PBM.MATERIAL_NUM,0) != 0 "
                                                    + "   and lpp.pull_approach_type = 'P' "
                                                    + "   and lpp.bom_station_code = '%s' "
                                                    + "   and (ps.scheduling_plan_date > (select ps1.scheduling_plan_date from t_plan_scheduling ps1 left join t_plan_scheduling_d psd1 on   ps1.scheduling_plan_code = psd1.scheduling_plan_code "
                                                    + "                        where psd1.production_code = '%s' and ps1.scheduling_plan_type = '7'   AND ps1.AVAILABLE_STATUS = '1') "
                                                    + "           or  ( ps.scheduling_plan_date =  (select ps1.scheduling_plan_date  from t_plan_scheduling ps1 left join t_plan_scheduling_d psd1 on   ps1.scheduling_plan_code = psd1.scheduling_plan_code "
                                                    + "                        where psd1.production_code = '%s' and ps1.scheduling_plan_type = '7'   AND ps1.AVAILABLE_STATUS = '1' "
                                                    + "                        ) "
                                                    + "                        and psd.seq_no > (select psd1.seq_no  from t_plan_scheduling ps1 left join t_plan_scheduling_d psd1 on   ps1.scheduling_plan_code = psd1.scheduling_plan_code "
                                                    + "                        where psd1.production_code = '%s' and ps1.scheduling_plan_type = '7'   AND ps1.AVAILABLE_STATUS = '1' )  )) "
                                                    + " group by ps.scheduling_plan_date, psd.seq_no, pdp.production_code "
                                                    + " order by ps.scheduling_plan_date, PSD.SEQ_NO ", stationCode, last_production_code, last_production_code, last_production_code);
                                        }
                                        Record firstCar = Db.findFirst(nextCarSql);
                                        if (firstCar != null) {//若存在下一台车
                                            int material_nums = firstCar.getInt("material_nums");
                                            List<String> production_codes = new ArrayList<>();
                                            String last_production_code = firstCar.getStr("production_code");
                                            production_codes.add("'" + last_production_code + "'");
                                            if (material_nums <= snp) { //若第一台车零件数小于等于标准装箱数
                                                //循环获取下一台车的零件数，满足snp为止
                                                for (int i = 1; snp - material_nums > 0; i++) {
                                                    nextCarSql = "";
                                                    nextCarSql = String.format("select ps.scheduling_plan_date, "
                                                            + "       psd.seq_no, "
                                                            + "       pdp.production_code, "
                                                            + "       sum(material_num) material_nums "
                                                            + "  from t_plan_scheduling ps "
                                                            + "  left join t_plan_scheduling_d psd "
                                                            + "    on ps.scheduling_plan_code = psd.scheduling_plan_code "
                                                            + "  left join t_plan_demand_product pdp "
                                                            + "    on psd.production_code = pdp.production_code "
                                                            + "  left join t_plan_punch_bom_material pbm "
                                                            + "    on pbm.bom_code = pdp.k_carbody_code "
                                                            + "  left join T_BASE_MATERIAL bm on pbm.material_code = bm.k_drawing_no  "
                                                            + "  left join t_lwm_part_para lpp "
                                                            + "    on lpp.parts_code = bm.material_code "
                                                            + " where ps.scheduling_plan_type = '7' "
                                                            + "   and pdp.demand_product_type in ('0', '5') "
                                                            + "   and ps.available_status = '1' "
                                                            + "   and psd.available_status = '1' "
                                                            + "   AND nvl(PBM.MATERIAL_NUM,0) != 0 "
                                                            + "   and lpp.pull_approach_type = 'P' "
                                                            + "   and lpp.bom_station_code = '%s' "
                                                            + "   and (ps.scheduling_plan_date > (select ps1.scheduling_plan_date from t_plan_scheduling ps1 left join t_plan_scheduling_d psd1 on   ps1.scheduling_plan_code = psd1.scheduling_plan_code "
                                                            + "                        where psd1.production_code = '%s' and ps1.scheduling_plan_type = '7'   AND ps1.AVAILABLE_STATUS = '1') "
                                                            + "           or  ( ps.scheduling_plan_date =  (select ps1.scheduling_plan_date  from t_plan_scheduling ps1 left join t_plan_scheduling_d psd1 on   ps1.scheduling_plan_code = psd1.scheduling_plan_code "
                                                            + "                        where psd1.production_code = '%s' and ps1.scheduling_plan_type = '7'   AND ps1.AVAILABLE_STATUS = '1' "
                                                            + "                        ) "
                                                            + "                        and psd.seq_no > (select psd1.seq_no  from t_plan_scheduling ps1 left join t_plan_scheduling_d psd1 on   ps1.scheduling_plan_code = psd1.scheduling_plan_code "
                                                            + "                        where psd1.production_code = '%s' and ps1.scheduling_plan_type = '7'   AND ps1.AVAILABLE_STATUS = '1' )  )) "
                                                            + " group by ps.scheduling_plan_date, psd.seq_no, pdp.production_code "
                                                            + " order by ps.scheduling_plan_date, PSD.SEQ_NO ", stationCode, last_production_code, last_production_code, last_production_code);
                                                    Record nextCar = Db.findFirst(nextCarSql);
                                                    if (nextCar == null) {//若不存在当前车 则跳出循环
                                                        break;
                                                    }
                                                    //获取当前车的零件数是否是否满足snp
                                                    material_nums = material_nums + nextCar.getInt("material_nums");
                                                    if (material_nums > snp) {//若大于snp则跳出循环
                                                        break;
                                                    }
                                                    //将当前车存入最后拉动车中
                                                    last_production_code = nextCar.getStr("production_code");
                                                    //记录当前车的生产编码
                                                    production_codes.add("'" + last_production_code + "'");
                                                }
                                            } else {//若第一台车零件数大于标准装箱数

                                            }

                                            //将最后一台车和工位信息写入 排序拉动工位与最后拉动车辆关系表 中
                                            if (carInfoRec == null) {//若不存在关系数据则创建关系数据
                                                String updateSql = String.format("insert into t_lwm_sort_pull_station_car (id,station_code,PRODUCTION_CODE,modify_date) values (sys_guid(),'%s','%s',sysdate) ", stationCode, last_production_code);
                                                Log.Write(strServiceCode, LogLevel.Information, "新增排序拉动工位与最后拉动车辆关系表 t_lwm_sort_pull_station_car：工位:"+stationCode+",生产编码："+last_production_code+"。");
                                                Db.update(updateSql);
                                            } else {//若存在关系数据则更新生产编码为最新编码
                                                String updateSql = String.format(" update t_lwm_sort_pull_station_car set PRODUCTION_CODE ='%s', modify_date=sysdate where station_code = '%s' ", last_production_code, stationCode);
                                                Log.Write(strServiceCode, LogLevel.Information, "更新排序拉动工位与最后拉动车辆关系表 t_lwm_sort_pull_station_car：工位:"+stationCode+",生产编码："+last_production_code+"。");
                                                Db.update(updateSql);
                                            }
                                            //获取需求单单号 PX201902280001
                                            String sql = String.format("SELECT 'PX'||TO_CHAR(SYSDATE,'yyyymmdd')||substr('0000'||(NVL(MAX(FLOW_NUMBER) ,0)+1),-4)  as demand_code, "
                                                    + " substr('0000'||(NVL(MAX(FLOW_NUMBER) ,0)+1),-4)  flow_number "
                                                    + "              FROM T_BASE_BUSINESS_NUM "
                                                    + "             WHERE BUSINESS_CODE = 'ldxq02' "
                                                    + "               AND WORKING_DAY = to_date(func_get_workday, 'yyyy-MM-dd')");
                                            Record rec = Db.findFirst(sql);
                                            String demand_code = rec.getStr("demand_code");
                                            String flow_number = rec.getStr("flow_number");
                                            // 写入序号
                                            String updateSql = String.format(" insert into T_BASE_BUSINESS_NUM values(sys_guid(),'ldxq02','%s',to_date(func_get_workday,'yyyy-MM-dd')) ", flow_number);
                                            Db.update(updateSql);

                                            //获取拉动的生产编码
                                            String production_codess = "";
                                            for (String production_code: production_codes) {
                                                production_codess =production_codess + production_code +",";
                                            }
                                            production_codess = production_codess.substring(0,production_codess.length()-1);
                                            //写入需求单主表
                                            updateSql = String.format(" insert into T_LWM_MP_DEMAND_MAIN (ID,DEMAND_CODE,DEMAND_TYPE,warehouse_code,warehouse_pos_code,material_rack,distribution_user,create_time,expected_stock_time, expected_time,document_status  ) "
                                                    + " select sys_guid(), "
                                                    + " '%s', "
                                                    + " '1', "
                                                    + " LPP.WAREHOUSE_CODE, "
                                                    + " LPP.WAREHOUSE_POS_CODE, "
                                                    + " LPP.SORTING_STATION_TOOLS, "
                                                    + " LPP.DISTRIBUTION_USER, "
                                                    + " SYSDATE, "
                                                    + " sysdate+nvl(lpp.stocking_time/60/24,0) , "
                                                    + " sysdate+nvl(lpp.delivery_time/60/24,0) , "
                                                    + " '0' "
                                                    + " from t_plan_scheduling ps  "
                                                    + " left join t_plan_scheduling_d psd  "
                                                    + "   on ps.scheduling_plan_code = psd.scheduling_plan_code  "
                                                    + " left join t_plan_demand_product pdp  "
                                                    + "   on psd.production_code = pdp.production_code  "
                                                    + " left join t_plan_punch_bom_material pbm  "
                                                    + "   on pbm.bom_code = pdp.k_carbody_code  "
                                                    + " left join T_BASE_MATERIAL bm on pbm.material_code = bm.k_drawing_no  "
                                                    + " left join t_lwm_part_para lpp  "
                                                    + "   on lpp.parts_code = bm.material_code  "
                                                    + " where ps.scheduling_plan_type = '7'  "
                                                    + "  and pdp.demand_product_type in ('0', '5')  "
                                                    + "  and ps.available_status = '1'  "
                                                    + "  and psd.available_status = '1'  "
                                                    + "  AND nvl(PBM.MATERIAL_NUM,0) != 0  "
                                                    + "  and lpp.pull_approach_type = 'P'  "
                                                    + "  and lpp.bom_station_code = '%s'  "
                                                    + "  and psd.production_code in (%s) "
                                                    + "  and rownum = 1 ", demand_code, stationCode, production_codess);
                                            Log.Write(strServiceCode, LogLevel.Information, "新增需求单主表T_LWM_MP_DEMAND_MAIN：单号："+demand_code+",工位:"+stationCode+",生产编码："+production_codess+"。");
                                            Db.update(updateSql);

                                            //写入需求单明细表
                                            updateSql = String.format(" INSERT INTO T_LWM_MP_DEMAND_DETAIL  (ID,DEMAND_CODE,STEEL_CODE,CARBODY_FIGURE_CODE,PARTS_CODE,DEMAND_PARTS_NUM,DEMAND_BOX_NUM,BAR_CODE) "
                                                    + "  select sys_guid(), "
                                                    + "  '%s', "
                                                    + "  pdp.k_stamp_id, "
                                                    + "  pdp.K_CARBODY_CODE, "
                                                    + "  bm.material_code, "
                                                    + "  sum(pbm.material_num), "
                                                    + "  '1', "
                                                    + "  nvl((select K_PACKAGE_NO from t_lwm_stockcard ls where ls.MATERIAL_CODE = bm.material_code and K_PACKAGE_NO is not null AND ROWNUM= 1 ),'该零件的库存卡中的捆包号不存在数据') "
                                                    + "  from t_plan_scheduling ps  "
                                                    + "  left join t_plan_scheduling_d psd  "
                                                    + "    on ps.scheduling_plan_code = psd.scheduling_plan_code  "
                                                    + "  left join t_plan_demand_product pdp  "
                                                    + "    on psd.production_code = pdp.production_code  "
                                                    + "  left join t_plan_punch_bom_material pbm  "
                                                    + "    on pbm.bom_code = pdp.k_carbody_code  "
                                                    + "  left join T_BASE_MATERIAL bm on pbm.material_code = bm.k_drawing_no "
                                                    + "  left join t_lwm_part_para lpp  "
                                                    + "    on lpp.parts_code = bm.material_code  "
                                                    + "  where ps.scheduling_plan_type = '7'  "
                                                    + "   and pdp.demand_product_type in ('0', '5')  "
                                                    + "   and ps.available_status = '1'  "
                                                    + "   and psd.available_status = '1'  "
                                                    + "   AND nvl(PBM.MATERIAL_NUM,0) != 0  "
                                                    + "   and lpp.pull_approach_type = 'P'  "
                                                    + "   and lpp.bom_station_code = '%s'  "
                                                    + "   and psd.production_code in (%s) "
                                                    + "   group by pdp.k_stamp_id, "
                                                    + "  pdp.K_CARBODY_CODE, "
                                                    + "  bm.material_code ", demand_code, stationCode, production_codess);
                                            Log.Write(strServiceCode, LogLevel.Information, "新增需求单明细表T_LWM_MP_DEMAND_DETAIL：单号："+demand_code+",工位:"+stationCode+",生产编码："+production_codess+"。");
                                            Db.update(updateSql);

                                            //满足安全库存，更新过点记录的标识为已排序拉动
                                            updateSql = String.format("update t_actual_passed_record set sort_pull_status='1' where id ='%s' ", list.get(0).getStr("id"));
                                            Log.Write(strServiceCode, LogLevel.Information, "更新过点记录标识为已排序拉动。");
                                            Db.update(updateSql);

                                            Log.Write(strServiceCode, LogLevel.Information, "排序拉动成功！T_LWM_MP_DEMAND_MAIN：单号："+demand_code+",工位:"+stationCode+",生产编码："+production_codess+"。");
                                            msg="排序拉动成功！";
                                            return true;

                                        } else {
                                            msg = "拉动车辆不存在下一台存在工位【" + stationCode + "】的排序件的车辆！";
                                            Log.Write(strServiceCode, LogLevel.Error, msg);
                                        }
                                    } else {
                                        msg = "工位【" + stationCode + "】的实际库存+未收货的需求单的数量满足最小安全库存！更新过点记录标识为已排序拉动。";
                                        //满足安全库存，更新过点记录的标识为已排序拉动
                                        String updateSql = String.format("update t_actual_passed_record set sort_pull_status='1' where id ='%s' ", list.get(0).getStr("id"));
                                        Log.Write(strServiceCode, LogLevel.Information, msg);
                                        Db.update(updateSql);
                                        return true;
                                    }
                                } else {
                                    msg = "工位【" + stationCode + "】的最小库存为0！";
                                    Log.Write(strServiceCode, LogLevel.Error, msg);
                                }
                            } else {
                                //若snp等于0
                                msg = "工位【" + stationCode + "】的SNP未配置！";
                                Log.Write(strServiceCode, LogLevel.Error, msg);
                            }
                            return true;
                        }
                    });
				}else{
                    msg = "实绩点【"+point+"】不存在未拉动的过点记录！";
                    Log.Write(strServiceCode, LogLevel.Information, msg);
                }

			}
		}else{
			msg = "实绩点未配置";
			Log.Write(strServiceCode, LogLevel.Error,msg);
		}
		return msg;
	}
}

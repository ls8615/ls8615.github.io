package cvmes.wlkj;

/*import java.sql.CallableStatement;
 import java.sql.Connection;
 import java.sql.SQLException;*/
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/*import oracle.jdbc.OracleTypes;*/

import com.jfinal.plugin.activerecord.Db;
/*import com.jfinal.plugin.activerecord.IAtom;
 import com.jfinal.plugin.activerecord.ICallback;*/
import com.jfinal.plugin.activerecord.Record;

import cvmes.CvmesService;
import cvmes.common.Log;
import cvmes.common.LogLevel;

public class MaterialDeductionEditService {

	public void run() {
		Thread thread = new Thread(new MaterialDeductionEditServiceThread());
		thread.start();
	}

	class MaterialDeductionEditServiceThread extends Thread {
		// 服务编码
		private final String strServiceCode = "MaterialDeductionEditService";

		/*
		 * 定义指示点
		 */
		/*
		 * private final String wlkjCode1 = "MaterialDeductionEditService";
		 * private final String wlkjCode2 = "MaterialDeductionEditService";
		 * private final String wlkjCode3 = "MaterialDeductionEditService";
		 * private final String wlkjCode4 = "MaterialDeductionEditService";
		 * private final String wlkjCode5 = "MaterialDeductionEditService";
		 * private final String wlkjCode6 = "MaterialDeductionEditService";
		 * private final String wlkjCode7 = "MaterialDeductionEditService";
		 */

		// 业务操作
		private void runBll(Record rec_service) {
			// 获取待扣减的车架物料BOM_CODE
			List<Record> list1 = Db
					.find("select tpdp.BOM_CODE,tcpo.ID,tpdp.PRODUCTION_CODE,tmip.ACTUAL_POINT_CODE,tms.LINE_CODE from T_PLAN_DEMAND_PRODUCT tpdp "
							+ " left join  T_ACTUAL_PASSED_RECORD tapr on tapr.frame_num = tpdp.k_Carriage_No "
							+ " left join  T_CMD_PRODUCTION_ORDER tcpo on tcpo.passed_id = tapr.id "
							+ " left join T_MODEL_INDICATION_POINT tmip on  tmip.indication_point_code =  tcpo.indication_point_code  "
							+ " left join T_MODEL_STATION tms on  tms.STATION_CODE =  tmip.STATION_CODE  "
							+ " where tcpo.INDICATION_POINT_CODE in ('CJXXZSD1','CJXXZSD12') and TO_CHAR(tcpo.work_day,'yyyy-mm-dd') = FUNC_GET_WORKDAY()  and tpdp.demand_product_type = '1' and tcpo.PRODUCTION_ORDER_STATUS = '0' ");
			// 获取待扣减的焊装物料BOM_CODE
			List<Record> list2 = Db
					.find("select tpdp.K_CARBODY_CODE AS BOM_CODE,tpdp.PRODUCTION_CODE,tcpo.ID,tmip.ACTUAL_POINT_CODE from T_PLAN_DEMAND_PRODUCT tpdp  "
							+ " left join  T_ACTUAL_PASSED_RECORD tapr on tapr.production_code = tpdp.production_code  "
							+ " left join  T_CMD_PRODUCTION_ORDER tcpo on tcpo.passed_id = tapr.id "
							+ " left join T_MODEL_INDICATION_POINT tmip on  tmip.indication_point_code =  tcpo.indication_point_code  "
							+ " where tcpo.INDICATION_POINT_CODE in('UB010_wlkj','待定') and TO_CHAR(tcpo.work_day,'yyyy-mm-dd') = FUNC_GET_WORKDAY() and tcpo.PRODUCTION_ORDER_STATUS = '0' ");

			// 获取待扣减的冲压物料BOM_CODE
			List<Record> list3 = Db
					.find("select tmip.ACTUAL_POINT_CODE,tcpo.ID from T_MODEL_INDICATION_POINT tmip  "
							+ " left join  T_CMD_PRODUCTION_ORDER tcpo on tmip.indication_point_code =  tcpo.indication_point_code "
							+ " where tcpo.INDICATION_POINT_CODE in('cy01_01','cy02_01','cy_fjxl_01') and TO_CHAR(tcpo.work_day,'yyyy-mm-dd') = FUNC_GET_WORKDAY() ");

			// 逐条处理车架待扣减过点记录
			int ok1 = 0;
			int ng1 = 0;
			// 更新服务信息
			if (list1.size() > 0) {
				for (Record sub : list1) {
					sub.set("BOM_TYPE", "1");
					sub.set("WLKJ_NAME", "车架");
					boolean flag = dealMaterialDeductionEditService(sub);
					if (flag) {

						Db.update(
								"UPDATE T_CMD_PRODUCTION_ORDER SET PRODUCTION_ORDER_STATUS = '1' WHERE id =?",
								sub.getStr("ID"));
						ok1++;
					} else {

						Db.update(
								"UPDATE T_CMD_PRODUCTION_ORDER SET PRODUCTION_ORDER_STATUS = '2' WHERE id =?",
								sub.getStr("ID"));
						ng1++;
					}
					// Db.update("T_INF_TO_STAMPC_PASSREC", sub);
				}

				Db.update(
						"update T_SYS_SERVICE set LAST_OPERATE_TIME=sysdate, LAST_OPERATE_INFO=? where SERVICE_CODE=?",
						String.format("共处理数据【%d】条，其中成功【%d】条，失败【%d】条",
								ok1 + ng1, ok1, ng1), strServiceCode);
			}
			// 逐条处理焊装待扣减过点记录
			int ok2 = 0;
			int ng2 = 0;
			// 更新服务信息

			if (list2.size() > 0) {
				for (Record sub : list2) {
					sub.set("BOM_TYPE", "2");
					sub.set("WLKJ_NAME", "焊装");
					boolean flag = dealMaterialDeductionEditService(sub);
					if (flag) {

						Db.update(
								"UPDATE T_CMD_PRODUCTION_ORDER SET PRODUCTION_ORDER_STATUS = '1' WHERE id =?",
								sub.getStr("ID"));
						ok2++;
					} else {

						Db.update(
								"UPDATE T_CMD_PRODUCTION_ORDER SET PRODUCTION_ORDER_STATUS = '2' WHERE id =?",
								sub.getStr("ID"));
						ng2++;
					}
					// Db.update("T_INF_TO_STAMPC_PASSREC", sub);
				}

				Db.update(
						"update T_SYS_SERVICE set LAST_OPERATE_TIME=sysdate, LAST_OPERATE_INFO=? where SERVICE_CODE=?",
						String.format("焊装扣减共处理数据【%d】条，其中成功【%d】条，失败【%d】条", ok2
								+ ng2, ok2, ng2), strServiceCode);
			}
			// 逐条处理冲压待扣减过点记录
			int ok3 = 0;
			int ng3 = 0;
			// 更新服务信息

			if (list3.size() > 0) {
				for (Record sub : list2) {
					sub.set("BOM_TYPE", "3");
					sub.set("WLKJ_NAME", "冲压");
					boolean flag = dealMaterialDeductionEditService(sub);
					if (flag) {

						Db.update(
								"UPDATE T_CMD_PRODUCTION_ORDER SET PRODUCTION_ORDER_STATUS = '1' WHERE id =?",
								sub.getStr("ID"));
						ok3++;
					} else {

						Db.update(
								"UPDATE T_CMD_PRODUCTION_ORDER SET PRODUCTION_ORDER_STATUS = '2' WHERE id =?",
								sub.getStr("ID"));
						ng3++;
					}
					// Db.update("T_INF_TO_STAMPC_PASSREC", sub);
				}

				Db.update(
						"update T_SYS_SERVICE set LAST_OPERATE_TIME=sysdate, LAST_OPERATE_INFO=? where SERVICE_CODE=?",
						String.format("冲压扣减共处理数据【%d】条，其中成功【%d】条，失败【%d】条", ok3
								+ ng3, ok3, ng3), strServiceCode);
			}

		}

		private boolean dealMaterialDeductionEditService(Record rec) {
			try {
				String materialCode = "";// 获取物料编码
				int materialNum = 0;// 获取工位扣减值
				// String materialName = "";//获取BOM名称
				// String bomCode = "";//获取BOM编码
				String stationCode = "";// 获取工位编码
				String workShopCode = "";// 冲焊车间编码
				String productionCode = "";// 生产编码
				if ("2".equalsIgnoreCase(rec.getStr("BOM_TYPE"))) {// 焊装扣减
					// 获取工位、工位物料用量等信息
					List<Record> list_wlkj = Db
							.find("select a.* from (SELECT temp.MATERIAL_CODE, sum(temp.MATERIAL_NUM),tm.MATERIAL_NAME,temp.BOM_CODE,STATION_CODE,max(BATCH_NUMt) "
									+ " FROM "
									+ " ("
									+ " SELECT BOM_CODE, MATERIAL_CODE, MATERIAL_NUM, STATION_CODE, BATCH_NUMt "
									+ " FROM ( "
									+ " SELECT T.BOM_CODE, "
									+ " T.MATERIAL_CODE,"
									+ " T.MATERIAL_NUM,"
									+ " T.STATION_CODE,"
									+ " MAX(T.BATCH_NUM) BATCH_NUMt "
									+ " FROM T_BASE_BOM T "
									+ " WHERE "
									+ " T.BOM_TYPE = ? "
									+ " AND t.bom_code!=t.material_code AND t.BATCH_NUM=(SELECT MAX(BATCH_NUM) FROM t_base_bom WHERE bom_code =? AND BOM_TYPE = ?) "
									+ " GROUP BY T.BOM_CODE, T.MATERIAL_CODE, T.MATERIAL_NUM, T.STATION_CODE "
									+ ") VT "
									+ " START WITH vt.bom_code=? "
									+ " CONNECT BY   PRIOR VT.MATERIAL_CODE = VT.BOM_CODE) temp "
									+ " INNER JOIN T_BASE_MATERIAL tm ON tm.K_DRAWING_NO=temp.MATERIAL_CODE "
									+ " WHERE  tm.K_STAMP_PART_TYPE='自制件' AND TM.K_STAMP_MATERIAL_TYPE='总成' "
									+ " GROUP BY  temp.MATERIAL_CODE,tm.MATERIAL_NAME,temp.bom_code,STATION_CODE)a "
									+ " left join T_LWM_WAREHOUSE_STATION tlws on a.STATION_CODE = tlws.station_code "
									+ " where tlws.actual_point_code = ?  ",
									rec.getStr("BOM_TYPE"),
									rec.getStr("BOM_CODE"),
									rec.getStr("BOM_TYPE"),
									rec.getStr("BOM_CODE"),
									rec.getStr("ACTUAL_POINT_CODE"));
					if (list_wlkj.size() < 1) {
						Log.Write(
								strServiceCode,
								LogLevel.Warning,
								String.format(
										"生产指令主键ID【%s】，生产编码【%s】，原因【%s】，触发的过点记录无法找到正确的BOM拆分值，请检查配置",
										rec.getStr("ID"),
										rec.getStr("PRODUCTION_CODE"),
										"BOM拆分结果为0条"));
						return false;
					} else {
						for (Record sub : list_wlkj) {
							materialCode = sub.getStr("MATERIAL_CODE");// 获取物料编码
							materialNum = sub.getInt("MATERIAL_NUM");// 获取工位扣减值
							// materialName =
							// sub.getStr("MATERIAL_NAME");//获取BOM名称
							// bomCode = sub.getStr("BOM_CODE");//获取BOM编码
							stationCode = sub.getStr("STATION_CODE");// 获取工位编码
							workShopCode = "ch01";// 冲焊车间编码

							// 插入出入库记录
							String uuid = java.util.UUID.randomUUID()
									.toString();
							// Date date = new Date();
							SimpleDateFormat sdf = new SimpleDateFormat(
									"yyyy-MM-dd HH:mm:ss");
							Date date = sdf.parse(sdf.format(new Date()));
							Db.update(
									"insert into T_LWM_WAREHOUSE_REC(ID,WORKSHOP_CODE,IN_OUT_STOCK_TYPE,IN_OUT_STOCK_DIRECT,PRODUCTION_CODE,PRODUCT_CODE,WAREHOUSE_POS_CODE,OPER_USER,IN_OUT_STOCK_NUM,STOCK_TIME,WAREHOUSE_CODE,OPERATION_TYPE) values(?,?,?,?,?,?,?,?,?,?,?,?)",
									uuid, workShopCode, "2", "1",
									rec.getStr("PRODUCTION_CODE"), stationCode, materialCode,
									"amdin", materialNum, date, 
									"SHXB01", "2");

							// 物料扣减

							Db.update(
									" update T_LWM_WAREHOUSE_NUM set STOCK_NUM = STOCK_NUM-? where WAREHOUSE_POS_CODE=? and PRODUCT_CODE =? and WAREHOUSE_CODE = ?",

									materialNum, stationCode, materialCode,
									"SHXB01");
						}
						
						return true;

					}

				} else if ("1".equalsIgnoreCase(rec.getStr("BOM_TYPE"))) {// 车架扣减
					
					// 车架扣减
					// 获取工位、工位物料用量等信息
					List<Record> list_wlkj = Db
							.find("select * from (select a.material_code,a.material_num,b.k_process_code from T_BASE_BOM a "
                                  +" left join t_base_material b on a.material_code = b.material_code "
                                  +" where  a.bom_type = '1' and  a.bom_code = ? and b.k_process_code is not null ) m "
                                  +", "
                                  +" (select tlws.warehouse_pos_code,tlws.station_code,tlws.warehouse_code,substr(tms.station_name,5,length(tms.station_name)-6) station_name from T_LWM_WAREHOUSE_STATION tlws left join t_model_station tms on tlws.station_code = tms.station_code "
                                  +" where tlws.workshop_code = 'cj01' and tms.line_code = ? and tms.station_name is not null and tlws.warehouse_pos_code is not null and tlws.warehouse_code is not null ) n "
                                  +" where m.k_process_code = n.station_name  ",
									rec.getStr("BOM_CODE"),
									rec.getStr("LINE_CODE"));
					if (list_wlkj.size() < 1) {
						Log.Write(
								strServiceCode,
								LogLevel.Warning,
								String.format(
										"生产指令主键ID【%s】，生产编码【%s】，原因【%s】，触发的过点记录无法找到正确的BOM拆分值，请检查配置",
										rec.getStr("ID"),
										rec.getStr("PRODUCTION_CODE"),
										"BOM拆分结果为0条"));
						return false;
					} else {
						for (Record sub : list_wlkj) {
							materialCode = sub.getStr("MATERIAL_CODE");// 获取物料编码
							materialNum = sub.getInt("MATERIAL_NUM");// 获取工位扣减值
							// materialName =
							// sub.getStr("MATERIAL_NAME");//获取BOM名称
							// bomCode = sub.getStr("BOM_CODE");//获取BOM编码
							stationCode = sub.getStr("STATION_CODE");// 获取工位编码
							sub.getStr("WAREHOUSE_POS_CODE");// 获取线边库库位
							workShopCode = "cj01";// 

							// 插入出入库记录
							String uuid = java.util.UUID.randomUUID()
									.toString();
							// Date date = new Date();
							SimpleDateFormat sdf = new SimpleDateFormat(
									"yyyy-MM-dd HH:mm:ss");
							Date date = sdf.parse(sdf.format(new Date()));
							

							// 物料扣减

							int ret = Db.update(
									" update T_LWM_WAREHOUSE_NUM set STOCK_NUM = STOCK_NUM-? where WAREHOUSE_POS_CODE=? and PRODUCT_CODE =? and WAREHOUSE_CODE = ? and STOCK_NUM>?",

									materialNum, sub.getStr("WAREHOUSE_POS_CODE"), materialCode,
									sub.getStr("WAREHOUSE_CODE"),materialNum);
							if(ret>0){//扣减成功
								Db.update(
										"insert into T_LWM_WAREHOUSE_REC(ID,WORKSHOP_CODE,IN_OUT_STOCK_TYPE,IN_OUT_STOCK_DIRECT,PRODUCTION_CODE,PRODUCT_CODE,WAREHOUSE_POS_CODE,OPER_USER,IN_OUT_STOCK_NUM,STOCK_TIME,WAREHOUSE_CODE,OPERATION_TYPE,REMARK) values(?,?,?,?,?,?,?,?,?,?,?,?,?)",
										uuid, workShopCode, "2", "1",
										rec.getStr("PRODUCTION_CODE"), materialCode,sub.getStr("WAREHOUSE_POS_CODE"), 
										"amdin", materialNum, date, 
										sub.getStr("WAREHOUSE_CODE"), "2","扣减成功");
							}else{
								Db.update(
										"insert into T_LWM_WAREHOUSE_REC(ID,WORKSHOP_CODE,IN_OUT_STOCK_TYPE,IN_OUT_STOCK_DIRECT,PRODUCTION_CODE,PRODUCT_CODE,WAREHOUSE_POS_CODE,OPER_USER,IN_OUT_STOCK_NUM,STOCK_TIME,WAREHOUSE_CODE,OPERATION_TYPE,REMARK) values(?,?,?,?,?,?,?,?,?,?,?,?,?)",
										uuid, workShopCode, "2", "1",
										rec.getStr("PRODUCTION_CODE"), materialCode,sub.getStr("WAREHOUSE_POS_CODE"), 
										"amdin", materialNum, date, 
										sub.getStr("WAREHOUSE_CODE"), "2","扣减失败，待扣减数量大于库存量");
							}
						}
						
						return true;

					}

				


				} else {// 冲压扣减
						// 提交事务
					List<Record> list_wlkj = Db
							.find("select t.STATION_CODE,t.WAREHOUSE_POS_CODE,t.WORKSHOP_CODE,t.MATERIAL_CODE,t.WAREHOUSE_CODE from T_LWM_WAREHOUSE_STATION t where t.ACTUAL_POINT_CODE = ? ",rec.getStr("ACTUAL_POINT_CODE"));
					if (list_wlkj.size() < 1) {
						Log.Write(
								strServiceCode,
								LogLevel.Warning,
								String.format(
										"生产指令主键ID【%s】，实绩点编码【%s】，原因【%s】，触发的生产指令无法找到实绩卡，请检查配置",
										rec.getStr("ID"),
										rec.getStr("ACTUAL_POINT_CODE"),
										"BOM拆分结果为0条"));
						return false;
					} else {
						for (Record sub : list_wlkj) {
							materialCode = sub.getStr("MATERIAL_CODE");// 获取物料编码
							materialNum = 1;// 获取工位扣减值（每张实绩卡扣减一次）
							// materialName =
							// sub.getStr("MATERIAL_NAME");//获取BOM名称
							// bomCode = sub.getStr("BOM_CODE");//获取BOM编码
							stationCode = sub.getStr("STATION_CODE");// 获取工位编码
							workShopCode = sub.getStr("WORKSHOP_CODE");// 冲焊车间编码

							// 插入出入库记录
							String uuid = java.util.UUID.randomUUID()
									.toString();
							// Date date = new Date();
							SimpleDateFormat sdf = new SimpleDateFormat(
									"yyyy-MM-dd HH:mm:ss");
							Date date = sdf.parse(sdf.format(new Date()));
							Db.update(
									"insert into T_LWM_WAREHOUSE_REC(ID,WORKSHOP_CODE,IN_OUT_STOCK_TYPE,IN_OUT_STOCK_DIRECT,PRODUCTION_CODE,PRODUCT_CODE,WAREHOUSE_POS_CODE,OPER_USER,IN_OUT_STOCK_NUM,STOCK_TIME,WAREHOUSE_POS_CODE,WAREHOUSE_CODE,OPERATION_TYPE) values(?,?,?,?,?,?,?,?,?,?,?,?,?)",
									uuid, workShopCode, "2", "1",
									productionCode, stationCode, materialCode,
									"amdin", materialNum, date, sub.getStr("WAREHOUSE_POS_CODE"),
									sub.getStr("WAREHOUSE_CODE"), "2");

							// 物料扣减

							Db.update(
									" update T_LWM_WAREHOUSE_NUM set STOCK_NUM = STOCK_NUM-? where WAREHOUSE_POS_CODE=? and ONLY_MATERIAL_CODE =? and WAREHOUSE_CODE = ?",

									materialNum, stationCode, materialCode,
									sub.getStr("WAREHOUSE_CODE"));
						}
						
						return true;

					}
				}

			} catch (Exception e) {
				e.printStackTrace();
				Log.Write(strServiceCode, LogLevel.Error, String.format(
						"处理物料扣减失败，生产指令ID【%s】，失败车间【%s】，异常原因【%s】",
						rec.getStr("ID"), rec.getStr("WLKJ_NAME"),
						e.getMessage()));
				return false;
			}
		}

		// 线程控制
		@Override
		public void run() {
			Log.Write(strServiceCode, LogLevel.Information, "服务启动");

			while (true) {
				// 获取服务信息
				Record rec = Db.findById("T_SYS_SERVICE", "SERVICE_CODE",
						strServiceCode);

				// 更新生存时间
				Db.update(
						"update T_SYS_SERVICE set LAST_LIVE_TIME=sysdate where SERVICE_CODE=?",
						strServiceCode);

				// 退出指令
				if (rec.getInt("SERVICE_STATUS") == 0) {
					Log.Write(strServiceCode, LogLevel.Warning, "服务停止");
					CvmesService.setServiceStop(strServiceCode);
					break;
				}

				// 业务操作
				try {
					runBll(rec);
				} catch (Exception e) {
					e.printStackTrace();
					Log.Write(strServiceCode, LogLevel.Error,
							String.format("业务操作发生异常，原因【%s】", e.getMessage()));
				}

				// 休眠
				try {
					Thread.sleep(1000 * rec.getInt("SLEEP_TIME"));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

}

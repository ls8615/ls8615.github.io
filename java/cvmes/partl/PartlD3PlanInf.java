package cvmes.partl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.druid.util.StringUtils;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;

import cvmes.common.AbstractSubServiceThread;
import cvmes.common.Log;
import cvmes.common.LogLevel;
import cvmes.common.WebServiceClient;
import cvmes.entity.LbjEntityWebServicePara;

/**
 * 总装零部件D+3计划接口
 * 
 * @author CIKE
 *
 */
public class PartlD3PlanInf extends AbstractSubServiceThread {
	private String msg;

	@Override
	public void initServiceCode() {
		this.strServiceCode = "PartlD3PlanInf";
	}

	@Override
	public String runBll(Record rec_service) throws Exception {

		msg = "";

		// 获取服务器时间
		Record record = Db.findFirst(" select to_char(sysdate,'yyyy-mm-dd hh24:mi:ss') db_date from dual ");
		String plandate = record.getStr("db_date");
		Date sysTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(plandate);
		

		// 标记D+1计划更新服务参数时间
		boolean falge = false;

		// ------------D+3计划
		// 根据条件查询出来的数据
		List<Record> list = new ArrayList<Record>();

		StringBuffer sql = new StringBuffer();
		sql.append(" SELECT T1.PRODUCTION_CODE,");
		sql.append(" T2.SCHEDULING_PLAN_DATE AS SCHEDULING_PLAN_DATE,t1.K_ASSEMBLY_LINE AS LINE_CODE,");
		sql.append(" t1.DEMAND_PRODUCT_CODE,t1.K_COLOR_NAME,");
		sql.append(" t1.D3X,t3.SEQ_NO,t1.K_D3_BATCH_NUM,t1.BOM_CODE,");
		sql.append(" '' AS SCHEDULED_DATE,t3.PLAN_ONLINE_TIME,t1.DEMAND_PRODUCT_REMARK,");
		sql.append(" '0' AS DEAL_STATUS, SYSDATE AS DEAL_TIME");
		sql.append(" FROM T_PLAN_SCHEDULING T2");
		sql.append(" LEFT JOIN T_PLAN_SCHEDULING_D T3");
		sql.append(" ON T2.SCHEDULING_PLAN_CODE = T3.SCHEDULING_PLAN_CODE");
		sql.append(" LEFT JOIN T_PLAN_DEMAND_PRODUCT T1");
		sql.append(" ON T3.PRODUCTION_CODE = T1.PRODUCTION_CODE");
		sql.append(" WHERE to_char(t2.SCHEDULING_PLAN_DATE,'yyyy-MM-DD')> ?");
		sql.append(" AND t2.workshop_code='zz01' AND t2.SCHEDULING_PLAN_TYPE='0' AND  rownum <= 1500");
		sql.append(" AND t2.SCHEDULING_PLAN_DATE = (SELECT min(ps.SCHEDULING_PLAN_DATE) FROM  T_PLAN_SCHEDULING ps");
		sql.append(
				" WHERE to_char(ps.SCHEDULING_PLAN_DATE,'yyyy-MM-DD') > ? AND ps.workshop_code='zz01' AND ps.SCHEDULING_PLAN_TYPE='0')");
		sql.append(" ORDER BY t2.SCHEDULING_PLAN_DATE");

		// 根据服务参数D+3计划日期,查询大于D+3计划日期的数据发送,并且发送后更新服务参数D+3计划日期
		String planD3Date = rec_service.getStr("SERVICE_PARA4_VALUE");
		list = Db.find(sql.toString(), planD3Date, planD3Date);

		// ----------------D+1计划

		List<Record> list1 = new ArrayList<Record>();

		// 根据系统日期

		// 判断当天是否有计划来判断是否是工作日
		StringBuffer sql1 = new StringBuffer();
		sql1.append(" SELECT T1.PRODUCTION_CODE,");
		sql1.append(" T2.SCHEDULING_PLAN_DATE AS SCHEDULING_PLAN_DATE,t1.K_ASSEMBLY_LINE AS LINE_CODE,");
		sql1.append(" t1.DEMAND_PRODUCT_CODE,t1.K_COLOR_NAME,");
		sql1.append(" t1.D3X,t3.SEQ_NO,t1.K_D3_BATCH_NUM,t1.BOM_CODE,");
		sql1.append(" '' AS SCHEDULED_DATE,t3.PLAN_ONLINE_TIME,t1.DEMAND_PRODUCT_REMARK,");
		sql1.append(" '0' AS DEAL_STATUS, SYSDATE AS DEAL_TIME");
		sql1.append(" FROM T_PLAN_SCHEDULING T2");
		sql1.append(" LEFT JOIN T_PLAN_SCHEDULING_D T3");
		sql1.append(" ON T2.SCHEDULING_PLAN_CODE = T3.SCHEDULING_PLAN_CODE");
		sql1.append(" LEFT JOIN T_PLAN_DEMAND_PRODUCT T1");
		sql1.append(" ON T3.PRODUCTION_CODE = T1.PRODUCTION_CODE");
		sql1.append("  WHERE to_char(t2.SCHEDULING_PLAN_DATE,'yyyy-MM-DD') = ?");
		sql1.append(" AND t2.workshop_code='zz01' AND t2.SCHEDULING_PLAN_TYPE='0' AND rownum <= 1500 ");

		list1 = Db.find(sql1.toString(), plandate.subSequence(0, 10));

		// 获取服务参数时间
		String planD1Time = rec_service.getStr("SERVICE_PARA5_VALUE");
		Date nplanD1Time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(planD1Time);

		// 定时执行，每天当系统时间大于或等于服务参数时间，并且根据List值判断是否有计划来判断是工作日,发送一次数据,
		if (sysTime.getTime() >= nplanD1Time.getTime()
				&& (list1 != null && list1.size() > 0)) {

			falge = true;
			list = Db.find(sql.toString(), plandate, plandate);

		}

		if (list == null || list.size() == 0) {
			return msg;
		}

		// 重新存储发送数据到List中
		List<Map<String, Object>> list_qltq = new ArrayList<Map<String, Object>>();

		// 循环查询出来的数据
		for (Record sub : list) {

			Map<String, Object> map = new HashMap<String, Object>();

			String ProductionCode = sub.getStr("PRODUCTION_CODE");
			if (StringUtils.isEmpty(ProductionCode)) {
				ProductionCode = "0";
			}
			map.put("ProductionCode", ProductionCode);

			String tplanDate = sub.getStr("SCHEDULING_PLAN_DATE");
			if (!StringUtils.isEmpty(tplanDate)) {

				// 区分D+1和D+3计划日期
				if (falge == true) {
					planD1Time = tplanDate.substring(0, 10);
				} else {
					planD3Date = tplanDate.substring(0, 10);
				}

			}

			String LineCode = sub.getStr("LINE_CODE");
			if (StringUtils.isEmpty(LineCode)) {
				LineCode = "0";
			}
			map.put("LineCode", LineCode);

			String CarTypeCode = sub.getStr("DEMAND_PRODUCT_CODE");
			if (StringUtils.isEmpty(CarTypeCode)) {
				CarTypeCode = "0";
			}
			map.put("CarTypeCode", CarTypeCode);

			String ColourName = sub.getStr("K_COLOR_NAME");
			if (StringUtils.isEmpty(ColourName)) {
				ColourName = "0";
			}
			map.put("ColourName", ColourName);

			String D3X = sub.getStr("D3X");
			if (StringUtils.isEmpty(D3X)) {
				D3X = "0";
			}
			map.put("D3x", D3X);

			String SeqNo = sub.getStr("SEQ_NO");
			if (StringUtils.isEmpty(SeqNo)) {
				SeqNo = "0";
			}
			map.put("SeqNo", SeqNo);

			String Quantity = sub.getStr("K_D3_BATCH_NUM");
			if (StringUtils.isEmpty(Quantity)) {
				Quantity = "0";
			}
			map.put("Quantity", Quantity);

			String BomCode = sub.getStr("BOM_CODE");
			if (StringUtils.isEmpty(BomCode)) {
				BomCode = "0";
			}
			map.put("BomCode", BomCode);

			// 已排产时间暂时没有数据来源,定位空值
			String schDate = sub.getStr("SCHEDULED_DATE");
			if (!StringUtils.isEmpty(schDate)) {
				schDate = schDate.substring(0, 10);
			}
			map.put("ScheduledDate", schDate);

			String planUptime = sub.getStr("PLAN_ONLINE_TIME");
			if (!StringUtils.isEmpty(planUptime)) {
				planUptime = planUptime.substring(0, planUptime.length() - 2);
			}
			map.put("PlanUpTime", planUptime);

			String Remark = sub.getStr("DEMAND_PRODUCT_REMARK");
			if (StringUtils.isEmpty(Remark)) {
				Remark = "0";
			}
			map.put("Remark", Remark);

			list_qltq.add(map);
		}

		LbjEntityWebServicePara entity = new LbjEntityWebServicePara();
		entity.setKey(rec_service.getStr("SERVICE_PARA2_VALUE"));
		entity.setData(list_qltq);

		if (falge == true) {

			entity.setPlanDate(planD1Time);
			entity.setPlanType("1");

			// D+1计划更新服务参数时间
			planD1Time = planD1Time + " 01:00:00";

		} else {
			entity.setPlanDate(planD3Date);
			entity.setPlanType("3");
		}

		String json = JSONObject.toJSONString(entity, SerializerFeature.WriteMapNullValue);
		WebServiceClient wsc = new WebServiceClient(rec_service.getStr("SERVICE_PARA1_VALUE"),
				rec_service.getStr("SERVICE_PARA3_VALUE"));

		try {

			Log.Write(strServiceCode, LogLevel.Information, String.format("发送数据【%s】", json));
			String str = (String) wsc.invoke("Getcar_plan", new String[] { "_para" }, new Object[] { json });

			Log.Write(strServiceCode, LogLevel.Information, String.format("接收响应【%s】", str));
			JSONObject object = JSONObject.parseObject(str);

			int errcode = Integer.parseInt(object.getString("errcode"));
			if (errcode == 0) {

				// 成功

				// 更新服务参数表D+3计划日期
				Db.update(
						"update T_SYS_SERVICE set SERVICE_PARA4_VALUE = ?,SERVICE_PARA5_VALUE = ? where SERVICE_CODE = ?",
						planD3Date, planD1Time, rec_service.getStr("SERVICE_CODE"));

				msg = String.format("成功发送数据【%d】条", list.size());

			} else {

				// 失败
				msg = String.format("发送失败，原因【%s】", object.getString("errmsg"));
			}

		} catch (Exception e) {
			msg = String.format("发生异常，原因【%s】", e.getMessage());
		}
		return msg;
	}

}

package cvmes.warn;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;

import cvmes.common.AbstractSubServiceThread;
import cvmes.common.Log;
import cvmes.common.LogLevel;

/**
 * 预警首页临时数据生成
 * */
public class WarnDailyEdit extends AbstractSubServiceThread {

	private String msg = "";
	
	@Override
	public void initServiceCode() {
		// TODO Auto-generated method stub
		strServiceCode = "warnDailyReflash";
	}
	
	@Override
	public String runBll(Record rec_service) throws Exception {
		
		  // 当天数据是否存在
        boolean isFlag = false;
        int ctn = Db.queryInt("SELECT COUNT(1) AS CTN FROM T_WARN_TEMP WHERE TO_CHAR(QUERY_DATE,'yyyy-MM-dd')=TO_CHAR(SYSDATE,'yyyy-MM-dd')");
        if(ctn == 0){ //如果没有指令不做操作
        	isFlag = true;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String curDate = sdf.format(new Date());
        // 不存在 新增
        if (isFlag){
        	 Db.update(INSERT_SQL);
        	 msg = "新增["+curDate+"]数据成功！";
        	 Log.Write(strServiceCode, LogLevel.Information, "已新增["+curDate+"]预警首页数据。");
        } else {
        	// 存在修改
        	Db.update(UPDATE_SQL);
        	msg = "修改["+curDate+"]数据成功！";
        	Log.Write(strServiceCode, LogLevel.Information, "已修改["+curDate+"]预警首页数据。");
        }
        
		Log.Write(strServiceCode, LogLevel.Information,msg);
		return msg;
	}

	private static final String INSERT_SQL="insert into T_WARN_TEMP (QUERY_DATE, ZZ_ZAIZHI, ZZ_PLAN, ZZ_DAY, ZZ_MONTH, ZZ_YEAR, PBS_ZAIZHI, PBS_DAY, PBS_MONTH, TZ_ZAIZHI, TZ_PLAN, TZ_DAY, TZ_MONTH, TZ_YEAR, CH_ZAIZHI, CH_PLAN, CH_DAY, CH_MONTH, CH_YEAR, WBS_ZAIZHI, WBS_DAY, WBS_MONTH, CY_PLAN, CY_DAY, CY_MONTH, CY_YEAR, CJ_PLAN, CJ_DAY, CJ_MONTH, CJ_YEAR) values ( "
			 +"sysdate,"
			 +"(SELECT COUNT(1) FROM T_PLAN_DEMAND_PRODUCT T WHERE 1=1 AND ( (T.CAR_STATUS IN (400,405,410,415)) OR (T.CAR_STATUS IN (420,425) AND T.K_PLAN_TYPE=1)) AND T.K_IS_PLAN_CANCEL IS NULL)," // 总装在制
			 +"(SELECT NVL(SUM(B.PRODUCTION_NUM),0) FROM T_PLAN_SCHEDULING A, T_PLAN_SCHEDULING_D B WHERE A.SCHEDULING_PLAN_CODE = B.SCHEDULING_PLAN_CODE AND TO_CHAR(A.SCHEDULING_PLAN_DATE, 'yyyy-MM-dd') = TO_CHAR(SYSDATE,'yyyy-MM-dd') AND A.WORKSHOP_CODE = 'zz01')," // 总装日计划
			 +"(SELECT NVL(count(1),0) FROM T_ACTUAL_PASSED_RECORD r WHERE r.ACTUAL_POINT_CODE in ('zz_offline1','zz_offline2') AND to_char(r.passed_time, 'yyyy-MM-dd hh24:mi:ss') >=  TO_CHAR(SYSDATE ,'yyyy-MM-dd') || ' 08:00:00' AND to_char(r.passed_time, 'yyyy-MM-dd hh24:mi:ss') <=  TO_CHAR(SYSDATE+1,'yyyy-MM-dd') || ' 07:59:59' AND r.LINE_CODE in (select line_code from t_model_line where workshop_code ='zz01'))," // 总装日完成
			 +"(SELECT NVL(count(1),0) FROM T_ACTUAL_PASSED_RECORD r WHERE r.ACTUAL_POINT_CODE in ('zz_offline1','zz_offline2') AND to_char(r.passed_time, 'yyyy-MM') = TO_CHAR(SYSDATE,'yyyy-MM') AND r.LINE_CODE in (select line_code from t_model_line where workshop_code ='zz01'))," // 总装月完成
			 +"(SELECT NVL(count(1),0) FROM T_ACTUAL_PASSED_RECORD r WHERE r.ACTUAL_POINT_CODE in ('zz_offline1','zz_offline2') AND to_char(r.passed_time, 'yyyy') = TO_CHAR(SYSDATE,'yyyy') AND r.LINE_CODE in (select line_code from t_model_line where workshop_code ='zz01'))," // 总装年完成
			 +"(SELECT NVL(count(1),0) from t_plan_demand_product t where t.car_status>=265 and t.car_status<395 and t.demand_product_code not in ('yxccpbm') AND T.K_IS_PLAN_CANCEL IS NULL)," // PBS在制
			 +"(SELECT NVL(count(1),0) FROM T_ACTUAL_PASSED_RECORD r WHERE r.ACTUAL_POINT_CODE in ('zz_mis1','zz_mis2') AND to_char(r.passed_time, 'yyyy-MM-dd hh24:mi:ss') >=TO_CHAR(SYSDATE ,'yyyy-MM-dd') || ' 08:00:00' AND to_char(r.passed_time, 'yyyy-MM-dd hh24:mi:ss') <= TO_CHAR(SYSDATE+1,'yyyy-MM-dd') || ' 07:59:59' AND r.LINE_CODE in (select line_code from t_model_line where workshop_code ='zz01'))," // PBS日完成
			 +"(SELECT NVL(count(1),0) FROM T_ACTUAL_PASSED_RECORD r WHERE r.ACTUAL_POINT_CODE in ('zz_mis1','zz_mis2') AND to_char(r.passed_time, 'yyyy-MM') = TO_CHAR(SYSDATE,'yyyy-MM') AND r.LINE_CODE in (select line_code from t_model_line where workshop_code ='zz01'))," // PBS月完成
			 +"(SELECT NVL(count(1),0) from t_Plan_Demand_Product t where t.car_status>=175 and t.car_status<265 and t.demand_product_code not in ('WBSBB','yxccpbm') AND T.K_IS_PLAN_CANCEL IS NULL)," // 涂装在制
			 +"(SELECT NVL(SUM(B.PRODUCTION_NUM),0) FROM T_PLAN_SCHEDULING A, T_PLAN_SCHEDULING_D B where A.SCHEDULING_PLAN_CODE = B.SCHEDULING_PLAN_CODE and to_char(A.SCHEDULING_PLAN_DATE, 'yyyy-MM-dd') = TO_CHAR(SYSDATE,'yyyy-MM-dd') and A.scheduling_plan_type = '13' and A.workshop_code = 'tz01')," // 涂装日计划
			 +"(SELECT NVL(count(1),0) FROM T_ACTUAL_PASSED_RECORD r WHERE r.ACTUAL_POINT_CODE = 'TZ28' AND to_char(r.passed_time, 'yyyy-MM-dd hh24:mi:ss') >=TO_CHAR(SYSDATE ,'yyyy-MM-dd') || ' 08:00:00' and to_char(r.passed_time, 'yyyy-MM-dd hh24:mi:ss') <= TO_CHAR(SYSDATE+1,'yyyy-MM-dd') || ' 07:59:59' AND r.LINE_CODE in (select line_code from t_model_line where workshop_code ='tz01'))," // 涂装日完成
			 +"(SELECT NVL(count(1),0) FROM T_ACTUAL_PASSED_RECORD r WHERE r.ACTUAL_POINT_CODE = 'TZ28' AND to_char(r.passed_time, 'yyyy-MM') = TO_CHAR(SYSDATE,'yyyy-MM') AND r.LINE_CODE in (select line_code from t_model_line where workshop_code ='tz01'))," // 涂装月完成
			 +"(SELECT NVL(count(1),0) FROM T_ACTUAL_PASSED_RECORD r WHERE r.ACTUAL_POINT_CODE = 'TZ28' AND to_char(r.passed_time, 'yyyy') = TO_CHAR(SYSDATE,'yyyy') AND r.LINE_CODE in (select line_code from t_model_line where workshop_code ='tz01'))," // 涂装年完成
			 +"(SELECT NVL(count(1),0) from t_plan_demand_product t where t.car_status in (5,10,15,20,25,25,30,35) AND T.K_IS_PLAN_CANCEL IS NULL)," // 焊装在制
			 +"(SELECT NVL(SUM(B.PRODUCTION_NUM),0) FROM T_PLAN_SCHEDULING A, T_PLAN_SCHEDULING_D B where A.SCHEDULING_PLAN_CODE = B.SCHEDULING_PLAN_CODE and to_char(A.SCHEDULING_PLAN_DATE, 'yyyy-MM-dd') = TO_CHAR(SYSDATE,'yyyy-MM-dd') and A.scheduling_plan_type = '7' and A.workshop_code = 'ch01')," // 焊装日计划
			 +"(SELECT NVL(count(1),0) FROM T_ACTUAL_PASSED_RECORD r WHERE r.ACTUAL_POINT_CODE = 'HzPhaseLineOffLine' AND to_char(r.passed_time, 'yyyy-MM-dd hh24:mi:ss') >=TO_CHAR(SYSDATE ,'yyyy-MM-dd') || ' 08:00:00' and to_char(r.passed_time, 'yyyy-MM-dd hh24:mi:ss') <=  TO_CHAR(SYSDATE+1,'yyyy-MM-dd') || ' 07:59:59' AND r.LINE_CODE in (select line_code from t_model_line where workshop_code ='ch01'))," // 焊装日完成
			 +"(SELECT NVL(count(1),0) FROM T_ACTUAL_PASSED_RECORD r WHERE r.ACTUAL_POINT_CODE = 'HzPhaseLineOffLine' AND to_char(r.passed_time, 'yyyy-MM') = TO_CHAR(SYSDATE,'yyyy-MM')  AND r.LINE_CODE in (select line_code from t_model_line where workshop_code ='ch01'))," // 焊装月完成
			 +"(SELECT NVL(count(1),0) FROM T_ACTUAL_PASSED_RECORD r WHERE r.ACTUAL_POINT_CODE = 'HzPhaseLineOffLine' AND to_char(r.passed_time, 'yyyy') = TO_CHAR(SYSDATE,'yyyy') AND r.LINE_CODE in (select line_code from t_model_line where workshop_code ='ch01'))," // 焊装年完成
			 +"(SELECT NVL(count(1),0) from t_plan_demand_product t where t.car_status>=40 and t.car_status<=170 and t.demand_product_code not in ('WBSBB','yxccpbm') AND T.K_IS_PLAN_CANCEL IS NULL)," // WBS在制
			 +"(SELECT NVL(count(1),0) FROM T_ACTUAL_PASSED_RECORD r WHERE r.ACTUAL_POINT_CODE = 'wbs01_out' AND to_char(r.passed_time, 'yyyy-MM-dd hh24:mi:ss') >=TO_CHAR(SYSDATE ,'yyyy-MM-dd') || ' 08:00:00' and to_char(r.passed_time, 'yyyy-MM-dd hh24:mi:ss') <=  TO_CHAR(SYSDATE+1,'yyyy-MM-dd') || ' 07:59:59')," // WBS日完成
			 +"(SELECT NVL(count(1),0) FROM T_ACTUAL_PASSED_RECORD r WHERE r.ACTUAL_POINT_CODE = 'wbs01_out' AND to_char(r.passed_time, 'yyyy-MM') = TO_CHAR(SYSDATE,'yyyy-MM'))," // WBS月完成
			 +"(SELECT NVL(SUM(B.PRODUCTION_NUM),0) FROM T_PLAN_SCHEDULING A, T_PLAN_SCHEDULING_D B where A.SCHEDULING_PLAN_CODE = B.SCHEDULING_PLAN_CODE and to_char(A.SCHEDULING_PLAN_DATE, 'yyyy-MM-dd') = TO_CHAR(SYSDATE,'yyyy-MM-dd') and b.line_code in ('cy01','cy02'))," // 冲压日计划
			 +"(SELECT NVL(sum(OK_NUM),0) FROM T_ACTUAL_CARD WHERE CATEGORY = '1' and to_char(k_generate_date, 'yyyy-MM-dd') = TO_CHAR(SYSDATE,'yyyy-MM-dd') AND LINE_CODE in ('cy01','cy02'))," // 冲压日完成
			 +"(SELECT NVL(sum(OK_NUM),0) FROM T_ACTUAL_CARD WHERE CATEGORY = '1' and to_char(k_generate_date, 'yyyy-MM') = TO_CHAR(SYSDATE,'yyyy-MM') AND LINE_CODE in ('cy01','cy02'))," // 冲压月完成
			 +"(SELECT NVL(sum(OK_NUM),0) FROM T_ACTUAL_CARD WHERE CATEGORY = '1' and to_char(k_generate_date, 'yyyy') = TO_CHAR(SYSDATE,'yyyy') AND LINE_CODE in ('cy01','cy02'))," // 冲压年完成
			 +"(SELECT NVL(SUM(B.PRODUCTION_NUM),0) FROM T_PLAN_SCHEDULING A, T_PLAN_SCHEDULING_D B where A.SCHEDULING_PLAN_CODE = B.SCHEDULING_PLAN_CODE and to_char(A.SCHEDULING_PLAN_DATE, 'yyyy-MM-dd') = TO_CHAR(SYSDATE,'yyyy-MM-dd') and A.scheduling_plan_type = '1' and A.workshop_code = 'cj01')," // 车架日计划
			 +"(SELECT NVL(count(1),0) FROM T_ACTUAL_PASSED_RECORD r WHERE r.ACTUAL_POINT_CODE in ('mjyx0101','mjexxxsj') AND to_char(r.passed_time, 'yyyy-MM-dd hh24:mi:ss') >= TO_CHAR(SYSDATE ,'yyyy-MM-dd') || ' 08:00:00' and to_char(r.passed_time, 'yyyy-MM-dd hh24:mi:ss') <=  TO_CHAR(SYSDATE+1,'yyyy-MM-dd') || ' 07:59:59' AND r.LINE_CODE in (select line_code from t_model_line where workshop_code ='cj01'))," // 车架日完成
			 +"(SELECT NVL(count(1),0) FROM T_ACTUAL_PASSED_RECORD r WHERE r.ACTUAL_POINT_CODE in ('mjyx0101','mjexxxsj') AND to_char(r.passed_time, 'yyyy-MM') = TO_CHAR(SYSDATE,'yyyy-MM') AND r.LINE_CODE in (select line_code from t_model_line where workshop_code ='cj01'))," // 车架月完成
			 +"(SELECT NVL(count(1),0) FROM T_ACTUAL_PASSED_RECORD r WHERE r.ACTUAL_POINT_CODE in ('mjyx0101','mjexxxsj') AND to_char(r.passed_time, 'yyyy') = TO_CHAR(SYSDATE,'yyyy') AND r.LINE_CODE in (select line_code from t_model_line where workshop_code ='cj01'))" // 车架年完成
			 +") ";
	private static final String UPDATE_SQL="UPDATE T_WARN_TEMP SET "
			+"QUERY_DATE = sysdate,"
			 +"ZZ_ZAIZHI = (SELECT COUNT(1) FROM T_PLAN_DEMAND_PRODUCT T WHERE 1=1 AND ( (T.CAR_STATUS IN (400,405,410,415)) OR (T.CAR_STATUS IN (420,425) AND T.K_PLAN_TYPE=1)) AND T.K_IS_PLAN_CANCEL IS NULL)," // 总装在制
			 +"ZZ_PLAN = (SELECT NVL(SUM(B.PRODUCTION_NUM),0) FROM T_PLAN_SCHEDULING A, T_PLAN_SCHEDULING_D B WHERE A.SCHEDULING_PLAN_CODE = B.SCHEDULING_PLAN_CODE AND TO_CHAR(A.SCHEDULING_PLAN_DATE, 'yyyy-MM-dd') = TO_CHAR(SYSDATE,'yyyy-MM-dd') AND A.WORKSHOP_CODE = 'zz01')," // 总装日计划
			 +"ZZ_DAY = (SELECT NVL(count(1),0) FROM T_ACTUAL_PASSED_RECORD r WHERE r.ACTUAL_POINT_CODE in ('zz_offline1','zz_offline2') AND to_char(r.passed_time, 'yyyy-MM-dd hh24:mi:ss') >=  TO_CHAR(SYSDATE ,'yyyy-MM-dd') || ' 08:00:00' AND to_char(r.passed_time, 'yyyy-MM-dd hh24:mi:ss') <=  TO_CHAR(SYSDATE+1,'yyyy-MM-dd') || ' 07:59:59' AND r.LINE_CODE in (select line_code from t_model_line where workshop_code ='zz01'))," // 总装日完成
			 +"ZZ_MONTH = (SELECT NVL(count(1),0) FROM T_ACTUAL_PASSED_RECORD r WHERE r.ACTUAL_POINT_CODE in ('zz_offline1','zz_offline2') AND to_char(r.passed_time, 'yyyy-MM') = TO_CHAR(SYSDATE,'yyyy-MM') AND r.LINE_CODE in (select line_code from t_model_line where workshop_code ='zz01'))," // 总装月完成
			 +"ZZ_YEAR = (SELECT NVL(count(1),0) FROM T_ACTUAL_PASSED_RECORD r WHERE r.ACTUAL_POINT_CODE in ('zz_offline1','zz_offline2') AND to_char(r.passed_time, 'yyyy') = TO_CHAR(SYSDATE,'yyyy') AND r.LINE_CODE in (select line_code from t_model_line where workshop_code ='zz01'))," // 总装年完成
			 +"PBS_ZAIZHI = (SELECT NVL(count(1),0) from t_plan_demand_product t where t.car_status>=265 and t.car_status<395 and t.demand_product_code not in ('yxccpbm') AND T.K_IS_PLAN_CANCEL IS NULL)," // PBS在制
			 +"PBS_DAY = (SELECT NVL(count(1),0) FROM T_ACTUAL_PASSED_RECORD r WHERE r.ACTUAL_POINT_CODE in ('zz_mis1','zz_mis2') AND to_char(r.passed_time, 'yyyy-MM-dd hh24:mi:ss') >=TO_CHAR(SYSDATE ,'yyyy-MM-dd') || ' 08:00:00' AND to_char(r.passed_time, 'yyyy-MM-dd hh24:mi:ss') <= TO_CHAR(SYSDATE+1,'yyyy-MM-dd') || ' 07:59:59' AND r.LINE_CODE in (select line_code from t_model_line where workshop_code ='zz01'))," // PBS日完成
			 +"PBS_MONTH = (SELECT NVL(count(1),0) FROM T_ACTUAL_PASSED_RECORD r WHERE r.ACTUAL_POINT_CODE in ('zz_mis1','zz_mis2') AND to_char(r.passed_time, 'yyyy-MM') = TO_CHAR(SYSDATE,'yyyy-MM') AND r.LINE_CODE in (select line_code from t_model_line where workshop_code ='zz01'))," // PBS月完成
			 +"TZ_ZAIZHI = (SELECT NVL(count(1),0) from t_Plan_Demand_Product t where t.car_status>=175 and t.car_status<265 and t.demand_product_code not in ('WBSBB','yxccpbm') AND T.K_IS_PLAN_CANCEL IS NULL)," // 涂装在制
			 +"TZ_PLAN = (SELECT NVL(SUM(B.PRODUCTION_NUM),0) FROM T_PLAN_SCHEDULING A, T_PLAN_SCHEDULING_D B where A.SCHEDULING_PLAN_CODE = B.SCHEDULING_PLAN_CODE and to_char(A.SCHEDULING_PLAN_DATE, 'yyyy-MM-dd') = TO_CHAR(SYSDATE,'yyyy-MM-dd') and A.scheduling_plan_type = '13' and A.workshop_code = 'tz01')," // 涂装日计划
			 +"TZ_DAY = (SELECT NVL(count(1),0) FROM T_ACTUAL_PASSED_RECORD r WHERE r.ACTUAL_POINT_CODE = 'TZ28' AND to_char(r.passed_time, 'yyyy-MM-dd hh24:mi:ss') >=TO_CHAR(SYSDATE ,'yyyy-MM-dd') || ' 08:00:00' and to_char(r.passed_time, 'yyyy-MM-dd hh24:mi:ss') <= TO_CHAR(SYSDATE+1,'yyyy-MM-dd') || ' 07:59:59' AND r.LINE_CODE in (select line_code from t_model_line where workshop_code ='tz01'))," // 涂装日完成
			 +"TZ_MONTH = (SELECT NVL(count(1),0) FROM T_ACTUAL_PASSED_RECORD r WHERE r.ACTUAL_POINT_CODE = 'TZ28' AND to_char(r.passed_time, 'yyyy-MM') = TO_CHAR(SYSDATE,'yyyy-MM') AND r.LINE_CODE in (select line_code from t_model_line where workshop_code ='tz01'))," // 涂装月完成
			 +"TZ_YEAR = (SELECT NVL(count(1),0) FROM T_ACTUAL_PASSED_RECORD r WHERE r.ACTUAL_POINT_CODE = 'TZ28' AND to_char(r.passed_time, 'yyyy') = TO_CHAR(SYSDATE,'yyyy') AND r.LINE_CODE in (select line_code from t_model_line where workshop_code ='tz01'))," // 涂装年完成
			 +"CH_ZAIZHI = (SELECT NVL(count(1),0) from t_plan_demand_product t where t.car_status in (5,10,15,20,25,25,30,35) AND T.K_IS_PLAN_CANCEL IS NULL)," // 焊装在制
			 +"CH_PLAN = (SELECT NVL(SUM(B.PRODUCTION_NUM),0) FROM T_PLAN_SCHEDULING A, T_PLAN_SCHEDULING_D B where A.SCHEDULING_PLAN_CODE = B.SCHEDULING_PLAN_CODE and to_char(A.SCHEDULING_PLAN_DATE, 'yyyy-MM-dd') = TO_CHAR(SYSDATE,'yyyy-MM-dd') and A.scheduling_plan_type = '7' and A.workshop_code = 'ch01')," // 焊装日计划
			 +"CH_DAY = (SELECT NVL(count(1),0) FROM T_ACTUAL_PASSED_RECORD r WHERE r.ACTUAL_POINT_CODE = 'HzPhaseLineOffLine' AND to_char(r.passed_time, 'yyyy-MM-dd hh24:mi:ss') >=TO_CHAR(SYSDATE ,'yyyy-MM-dd') || ' 08:00:00' and to_char(r.passed_time, 'yyyy-MM-dd hh24:mi:ss') <=  TO_CHAR(SYSDATE+1,'yyyy-MM-dd') || ' 07:59:59' AND r.LINE_CODE in (select line_code from t_model_line where workshop_code ='ch01'))," // 焊装日完成
			 +"CH_MONTH = (SELECT NVL(count(1),0) FROM T_ACTUAL_PASSED_RECORD r WHERE r.ACTUAL_POINT_CODE = 'HzPhaseLineOffLine' AND to_char(r.passed_time, 'yyyy-MM') = TO_CHAR(SYSDATE,'yyyy-MM')  AND r.LINE_CODE in (select line_code from t_model_line where workshop_code ='ch01'))," // 焊装月完成
			 +"CH_YEAR = (SELECT NVL(count(1),0) FROM T_ACTUAL_PASSED_RECORD r WHERE r.ACTUAL_POINT_CODE = 'HzPhaseLineOffLine' AND to_char(r.passed_time, 'yyyy') = TO_CHAR(SYSDATE,'yyyy') AND r.LINE_CODE in (select line_code from t_model_line where workshop_code ='ch01'))," // 焊装年完成
			 +"WBS_ZAIZHI = (SELECT NVL(count(1),0) from t_plan_demand_product t where t.car_status>=40 and t.car_status<=170 and t.demand_product_code not in ('WBSBB','yxccpbm') AND T.K_IS_PLAN_CANCEL IS NULL)," // WBS在制
			 +"WBS_DAY = (SELECT NVL(count(1),0) FROM T_ACTUAL_PASSED_RECORD r WHERE r.ACTUAL_POINT_CODE = 'wbs01_out' AND to_char(r.passed_time, 'yyyy-MM-dd hh24:mi:ss') >=TO_CHAR(SYSDATE ,'yyyy-MM-dd') || ' 08:00:00' and to_char(r.passed_time, 'yyyy-MM-dd hh24:mi:ss') <=  TO_CHAR(SYSDATE+1,'yyyy-MM-dd') || ' 07:59:59')," // WBS日完成
			 +"WBS_MONTH = (SELECT NVL(count(1),0) FROM T_ACTUAL_PASSED_RECORD r WHERE r.ACTUAL_POINT_CODE = 'wbs01_out' AND to_char(r.passed_time, 'yyyy-MM') = TO_CHAR(SYSDATE,'yyyy-MM'))," // WBS月完成
			 +"CY_PLAN = (SELECT NVL(SUM(B.PRODUCTION_NUM),0) FROM T_PLAN_SCHEDULING A, T_PLAN_SCHEDULING_D B where A.SCHEDULING_PLAN_CODE = B.SCHEDULING_PLAN_CODE and to_char(A.SCHEDULING_PLAN_DATE, 'yyyy-MM-dd') = TO_CHAR(SYSDATE,'yyyy-MM-dd') and b.line_code in ('cy01','cy02'))," // 冲压日计划
			 +"CY_DAY = (SELECT NVL(sum(OK_NUM),0) FROM T_ACTUAL_CARD WHERE CATEGORY = '1' and to_char(k_generate_date, 'yyyy-MM-dd') = TO_CHAR(SYSDATE,'yyyy-MM-dd') AND LINE_CODE in ('cy01','cy02'))," // 冲压日完成
			 +"CY_MONTH = (SELECT NVL(sum(OK_NUM),0) FROM T_ACTUAL_CARD WHERE CATEGORY = '1' and to_char(k_generate_date, 'yyyy-MM') = TO_CHAR(SYSDATE,'yyyy-MM') AND LINE_CODE in ('cy01','cy02'))," // 冲压月完成
			 +"CY_YEAR = (SELECT NVL(sum(OK_NUM),0) FROM T_ACTUAL_CARD WHERE CATEGORY = '1' and to_char(k_generate_date, 'yyyy') = TO_CHAR(SYSDATE,'yyyy') AND LINE_CODE in ('cy01','cy02'))," // 冲压年完成
			 +"CJ_PLAN = (SELECT NVL(SUM(B.PRODUCTION_NUM),0) FROM T_PLAN_SCHEDULING A, T_PLAN_SCHEDULING_D B where A.SCHEDULING_PLAN_CODE = B.SCHEDULING_PLAN_CODE and to_char(A.SCHEDULING_PLAN_DATE, 'yyyy-MM-dd') = TO_CHAR(SYSDATE,'yyyy-MM-dd') and A.scheduling_plan_type = '1' and A.workshop_code = 'cj01')," // 车架日计划
			 +"CJ_DAY = (SELECT NVL(count(1),0) FROM T_ACTUAL_PASSED_RECORD r WHERE r.ACTUAL_POINT_CODE in ('mjyx0101','mjexxxsj') AND to_char(r.passed_time, 'yyyy-MM-dd hh24:mi:ss') >= TO_CHAR(SYSDATE ,'yyyy-MM-dd') || ' 08:00:00' and to_char(r.passed_time, 'yyyy-MM-dd hh24:mi:ss') <=  TO_CHAR(SYSDATE+1,'yyyy-MM-dd') || ' 07:59:59' AND r.LINE_CODE in (select line_code from t_model_line where workshop_code ='cj01'))," // 车架日完成
			 +"CJ_MONTH = (SELECT NVL(count(1),0) FROM T_ACTUAL_PASSED_RECORD r WHERE r.ACTUAL_POINT_CODE in ('mjyx0101','mjexxxsj') AND to_char(r.passed_time, 'yyyy-MM') = TO_CHAR(SYSDATE,'yyyy-MM') AND r.LINE_CODE in (select line_code from t_model_line where workshop_code ='cj01'))," // 车架月完成
			 +"CJ_YEAR = (SELECT NVL(count(1),0) FROM T_ACTUAL_PASSED_RECORD r WHERE r.ACTUAL_POINT_CODE in ('mjyx0101','mjexxxsj') AND to_char(r.passed_time, 'yyyy') = TO_CHAR(SYSDATE,'yyyy') AND r.LINE_CODE in (select line_code from t_model_line where workshop_code ='cj01')" // 车架年完成
			 +") WHERE TO_CHAR(QUERY_DATE,'yyyy-MM-dd')=TO_CHAR(SYSDATE,'yyyy-MM-dd')";
}

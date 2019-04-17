package cvmes.zzhh;

import java.util.ArrayList;
import java.util.List;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;

import cvmes.common.AbstractWebService;
import cvmes.common.Log;
import cvmes.common.LogLevel;

@WebService(serviceName = "ZzhhWebService", targetNamespace = "http://service.zzhhService.inf.comm/")
public class ZzhhWebService extends AbstractWebService {

    @Override
    @WebMethod(exclude = true)
	public void initServiceCode() {
		// TODO Auto-generated method stub
		strServiceCode = "ZzhhWebService";
	}

    @Override
    @WebMethod(exclude = true)
	public void initWebService() {
		// TODO Auto-generated method stub
    	strUrl = getRecService().getStr("SERVICE_PARA1_VALUE");
	}
    
    /**
     * 处理接口方法返回结果
     *
     * @param infName
     * @param errcode
     * @param errmsg
     * @return
     */
    private String getResponseString(String infName, String errcode, String errmsg,List<Record> list) {
    	JSONObject result = new JSONObject();
    	JSONArray resultArray = new JSONArray();
    	JSONObject resultObj = new JSONObject();
    	resultObj.put("errcode", errcode);
    	resultObj.put("errmsg", errmsg);
        if(list !=null && list.size()>0){
        	JSONArray array = new JSONArray();
        	for(Record rec : list){
        		array.add(rec.toJson());
        	}
        	resultObj.put("data", array);
        }
        resultArray.add(resultObj);
        result.put("data", resultArray);
        String json = JSON.toJSONString(result, SerializerFeature.WriteMapNullValue);
        Log.Write(strServiceCode, LogLevel.Information, String.format("【%s】响应请求【%s】", infName, json));
        return json;
    }

    /**
     * 宏华水箱零件计划
     * @param _para
     * @return
     */
    @WebMethod
    @WebResult
    public String water_box_part(@WebParam(name = "_para", targetNamespace = "http://service.zzhhService.inf.comm/") String _para) {
        String infName = "宏华水箱零件计划";

        try {
            Log.Write(strServiceCode, LogLevel.Information, String.format("【%s】接收到请求【%s】", infName, _para));

            //参数转JSON
            JSONObject dataJson = JSONObject.parseObject(_para);

            //获取验证密钥
            String key = dataJson.getString("key");
            //获取接口数据
            JSONArray datas = dataJson.getJSONArray("data");

            //判断校验密钥是否正确
            if (!key.equals(getRecService().getStr("SERVICE_PARA2_VALUE"))) {
                return getResponseString(infName, "1", "校验密钥不正确",null);
            }

            //Json数组data为空或无数据
            if (datas == null || datas.size() == 0) {
                return getResponseString(infName, "1", "参数Json中data数组为空或无数据",null);
            }
            List<Record> resultList = new ArrayList<Record>();
            for (int i = 0; i < datas.size(); i++) {
	            JSONObject item = datas.getJSONObject(i);
	            String planDate = item.getString("plan_Date");
	            if(planDate ==null || "".equals(planDate)){
	            	return getResponseString(infName, "1", "参数data数组中数据异常",null);
	            }
	            String sql1 = String.format("select to_char(t1.scheduling_plan_date,'yyyy-mm-dd')plan_date,"
	            		+ "t2.production_code production_code,t2.seq_no,t3.d3x,t3.demand_product_code car_type_code,"
	            		+ "t3.bom_code bomid,t4.ljbh part_no,t4.ljmc part_name,t5.material_num part_num"
	            		+ " from T_PLAN_SCHEDULING t1"
	            		+ " left join T_PLAN_SCHEDULING_D t2 on t1.scheduling_plan_code=t2.scheduling_plan_code"
	            		+ " left join T_PLAN_DEMAND_PRODUCT t3 on t2.production_code=t3.production_code"
	            		+ " left join T_BASE_BOM t5 on t5.bom_code=t3.bom_code"
	            		+ " right join (select distinct m.cxdm,m.ljbh,m.LJMC,m.fah from MV_PBOM_PARTS_SUB_UNIT m where m.line4_fzdw_code='F05') t4"
	            		+ " on t3.demand_product_code=t4.cxdm and t3.K_SCHEME_CODE=t4.fah and t5.material_code=t4.ljbh"
	            		+ " where t1.scheduling_plan_date=to_date('%s','yyyy-mm-dd')"
	            		+ " and t1.SCHEDULING_PLAN_TYPE='0' and t1.Scheduling_Plan_Status='3' and t1.available_status='1'",
	            		planDate);
	            Log.Write(strServiceCode, LogLevel.Information, sql1);
	            List<Record> list = Db.find(sql1);
	            if(list !=null && list.size()>0)resultList.addAll(list);
            }
            if (resultList.size()>0) {
                return getResponseString(infName, "0", "ok",resultList);
            } else {
                return getResponseString(infName, "1", "无满足条件的对应数据！",null);
            }
        } catch (Exception e) {
            return getResponseString(infName, "1", String.format("发生异常，原因【%s】", e.getMessage()),null);
        }
    }
}

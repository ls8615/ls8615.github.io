package cvmes.zzelec;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.IAtom;
import com.jfinal.plugin.activerecord.Record;

import cvmes.common.AbstractWebService;
import cvmes.common.Log;
import cvmes.common.LogLevel;

/**
 * 总装电动车诊断接口 -服务端  (客户端给CVMES系统数据)
 * @author CIKE
 *
 */
@SOAPBinding(style = SOAPBinding.Style.RPC)
@WebService(serviceName = "ZzElecWebService", targetNamespace = "http://service.zzelec.inf.comm/")
public class ZzElecWebService extends AbstractWebService {
    @Override
    @WebMethod(exclude = true)
    public void initServiceCode() {
        this.strServiceCode = "ZzElecWebService";
    }

    @Override
    @WebMethod(exclude = true)
    public void initWebService() {
        this.strUrl = getRecService().getStr("SERVICE_PARA1_VALUE");

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
        	//JSONArray array = new JSONArray();
        	for(Record rec : list){
        		//array.add(rec.toJson());
        		resultObj.put("PRODUCTION_CODE", rec.getStr("PRODUCTION_CODE"));
        		resultObj.put("CAR_TYPE", rec.getStr("DEMAND_PRODUCT_CODE"));
        		resultObj.put("K_ELECTROMOTOR", rec.getStr("K_ELECTROMOTOR"));
        		resultObj.put("K_BATTER_PART", rec.getStr("K_BATTER_PART"));
        		resultObj.put("K_CONTROLLER_PART", rec.getStr("K_CONTROLLER_PART"));
        		resultObj.put("BMS_PART", rec.getStr("BMS_PART"));
        		resultObj.put("EPS_PART", rec.getStr("EPS_PART"));
        		resultObj.put("EAC_PART", rec.getStr("EAC_PART"));
        		resultObj.put("DCDC_PART", rec.getStr("DCDC_PART"));
        		resultObj.put("FAH", rec.getStr("K_SCHEME_CODE"));
        	}
        	//resultObj.put("data", array);
        }
        resultArray.add(resultObj);
        result.put("data", resultArray);
        String json = JSON.toJSONString(result, SerializerFeature.WriteMapNullValue);
        Log.Write(strServiceCode, LogLevel.Information, String.format("【%s】响应请求【%s】", infName, json));
        return json;
    }

    /**
     * ELEC01车辆信息
     * @param _para
     * @return
     */
    @WebMethod
    @WebResult
    public String GetCarMsgInfos(@WebParam(name = "_para", targetNamespace = "http://service.zzelec.inf.comm/") String _para) {
        String infName = "ELEC01车辆信息";

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
                return getResponseString(infName, "1", "客户端传入参数Json中data数组为空或无数据",null);
            }
            List<Record> resultList = new ArrayList<Record>();
            
            for (int i = 0; i < datas.size(); i++) {
            	
	            JSONObject item = datas.getJSONObject(i);
	            
	            
	            String pcode = item.getString("ProductionCode");
	            if(pcode ==null || "".equals(pcode)){
	            	return getResponseString(infName, "1", "参数data数组中数据异常",null);
	            }
	            
	            String sql = String.format("SELECT dp.PRODUCTION_CODE,dp.DEMAND_PRODUCT_CODE,dp.K_ELECTROMOTOR,dp.K_BATTER_PART,"
                             +" dp.K_CONTROLLER_PART,dp.BMS_PART,dp.EPS_PART,dp.EAC_PART,dp.DCDC_PART,dp.K_SCHEME_CODE"
                             +" FROM T_PLAN_DEMAND_PRODUCT dp WHERE dp.PRODUCTION_CODE= '%s'",pcode);
	            Log.Write(strServiceCode, LogLevel.Information, sql);
	            List<Record> list = Db.find(sql);
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
    
    
    /**
     * ELEC02车辆检测报告
     *
     * @param _para
     * @return
     */
    @WebMethod
    @WebResult
    public String WriteGetCarReportInfos(@WebParam(name = "_para", targetNamespace = "http://service.zzelec.inf.comm/") String _para) {
        String infName = "ELEC02车辆检测报告信息";

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

            // 写入数据库接口表
            boolean ret = Db.tx(new IAtom() {
                @Override
                public boolean run() throws SQLException {
                    for (int i = 0; i < datas.size(); i++) {
                        JSONObject item = datas.getJSONObject(i);
                        StringBuffer sql = new StringBuffer();
                        
                        //1组11个
                        sql.append("insert into T_INF_FROM_ELEC_GETCARREPORT(id, production_code, vin, detection_time,vehicle_controller_version,");
                        sql.append(" bms_version, mcu_version, eac_version, eps_version, dcdc_version,bms_soh_standard,");
                       
                        //2组10个
                        sql.append(" bms_soh_value, bms_soh_evaluate, bms_soc_standard, bms_soc_value,bms_soc_evaluate,");
                        sql.append(" bms_pbpc_standard, bms_pbpc_value, bms_pbpc_evaluate, bms_pbv_standard,bms_pbv_value,");
                        
                        //3组10个
                        sql.append(" bms_pbv_evaluate, bms_hvncr_standard, bms_hvncr_value, bms_hvncr_evaluate,bms_hvpp_standard,");
                        sql.append(" bms_hvpp_value, bms_hvpp_evaluate, bms_aefp_standard, bms_aefp_value,bms_aefp_evaluate,");
                        
                        //4组10个
                        sql.append(" bms_mnrs_standard, bms_mnrs_value, bms_mnrs_evaluate, dcdc_mt_standard,dcdc_mt_value,");
                        sql.append(" dcdc_mt_evaluate, dcdc_outc_standard, dcdc_outc_value, dcdc_outc_evaluate,dcdc_outv_standard,");
                        
                        //5组10个
                        sql.append(" dcdc_outv_value, dcdc_outv_evaluate, dcdc_inv_standard, dcdc_inv_value,dcdc_inv_evaluate,");
                        sql.append(" vcu_mpr_standard, vcu_mpr_value, vcu_mpr_evaluate, vcu_apsv_standard,vcu_apsv_value,");
                        
                        //6组10个
                        sql.append(" vcu_apsv_evaluate, eac_inv_standard, eac_inv_value, eac_inv_evaluate,eac_wa_standard,");
                        sql.append(" eac_wa_value, eac_wa_evaluate, eac_rpm_standard, eac_rpm_value,eac_rpm_evaluate,");
                        
                        //7组10个
                        sql.append(" eac_ct_standard, eac_ct_value, eac_ct_evaluate, eps_inv_standard,eps_inv_value,");
                        sql.append(" eps_inv_evaluate, eps_wa_standard, eps_wa_value, eps_wa_evaluate,eps_rpm_standard,");
                        
                        //8组10个
                        sql.append(" eps_rpm_value, eps_rpm_evaluate, eps_ct_standard, eps_ct_value,eps_ct_evaluate,");
                        sql.append(" mcu_mv_standard, mcu_mv_value, mcu_mv_evaluate, mcu_mt_standard,mcu_mt_value,");
                        
                        //9组10个
                        sql.append(" mcu_mt_evaluate, mcu_mct_standard, mcu_mct_value, mcu_mct_evaluate,vcu_mnr_standard,");
                        sql.append(" vcu_mnr_value, vcu_mnr_evaluate, vcu_bps_standard, vcu_bps_value,vcu_bps_evaluate,");
                        
                        //10组3个
                        sql.append(" deal_status, deal_time, create_time)");
                        
                        sql.append(" values(sys_guid(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,");
                        
                        sql.append(" ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,");
                        
                        sql.append(" ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,");
                        
                        sql.append(" ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,");
                        
                        sql.append(" ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,");
                        
                        sql.append(" ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,");
                        
                        sql.append(" ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,");
                        
                        sql.append(" ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,");
                        
                        sql.append(" ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,");
                        
                        sql.append("  '0', sysdate, sysdate)");
                        
                        Db.update(sql.toString(),
                                item.getString("ProductionCode"),item.getString("Vin"),item.getDate("DetectionTime"),
                                item.getString("VehicleControllerVersion"), item.getString("BmsVersion"),
                                item.getString("McuVersion"),item.getString("EacVersion"),item.getString("EpsVersion"),
                                item.getString("DcdcVersion"),item.getString("BmsSohStandard"),
                                
                                item.getString("BmsSohValue"),
                                item.getString("BmsSohEvaluate"),item.getString("BmsSocStandard"),item.getString("BmsSocValue"),
                                item.getString("BmsSocEvaluate"),item.getString("BmsPbpcStandard"),item.getString("BmsPbpcValue"),
                                item.getString("BmsPbpcEvaluate"),item.getString("BmsPbvStandard"),item.getString("BmsPbvValue"),
                                
                                item.getString("BmsPbvEvaluate"),item.getString("BmsHvncrStandard"),item.getString("BmsHvncrValue"),
                                item.getString("BmsHvncrEvaluate"),item.getString("BmsHvppStandard"),item.getString("BmsHvppValue"),
                                item.getString("BmsHvppEvaluate"),item.getString("BmsAefpStandard"),item.getString("BmsAefpValue"),
                                item.getString("BmsAefpEvaluate"),
                                
                                item.getString("BmsMnrsStandard"),item.getString("BmsMnrsValue"),
                                item.getString("BmsMnrsEvaluate"),item.getString("DcdcMtStandard"),item.getString("DcdcMtValue"),
                                item.getString("DcdcMtEvaluate"),item.getString("DcdcOutcStandard"),item.getString("DcdcOutcValue"),
                                item.getString("DcdcOutcEvaluate"),item.getString("DcdcOutvStandard"),
                                
                                item.getString("DcdcOutvValue"),
                                item.getString("DcdcOutvEvaluate"),item.getString("DcdcInvStandard"),item.getString("DcdcInvValue"),
                                item.getString("DcdcInvEvaluate"),item.getString("VcuMprStandard"),item.getString("VcuMprValue"),
                                item.getString("VcuMprEvaluate"),item.getString("VcuApsvStandard"),item.getString("VcuApsvValue"),
                                
                                item.getString("VcuApsvEvaluate"),item.getString("EacInvStandard"),item.getString("EacInvValue"),
                                item.getString("EacInvEvaluate"),item.getString("EacWaStandard"),item.getString("EacWaValue"),
                                item.getString("EacWaEvaluate"),item.getString("EacRpmStandard"),item.getString("EacRpmValue"),
                                item.getString("EacRpmEvaluate"),
                                
                                item.getString("EacCtStandard"),item.getString("EacCtValue"),
                                item.getString("EacCtEvaluate"),item.getString("EpsInvStandard"),item.getString("EpsInvValue"),
                                item.getString("EpsInvEvaluate"),item.getString("EpsWaStandard"),item.getString("EpsWaValue"),
                                item.getString("EpsWaEvaluate"),item.getString("EpsRpmStandard"),
                                
                                item.getString("EpsRpmValue"),
                                item.getString("EpsRpmEvaluate"),item.getString("EpsCtStandard"),item.getString("EpsCtValue"),
                                item.getString("EpsCtEvaluate"),item.getString("McuMvStandard"),item.getString("McuMvValue"),
                                item.getString("McuMvEvaluate"),item.getString("McuMtStandard"),item.getString("McuMtValue"),
                                
                                item.getString("McuMtEvaluate"),item.getString("McuMctStandard"),item.getString("McuMctValue"),
                                item.getString("McuMctEvaluate"),item.getString("VcuMnrStandard"),item.getString("VcuMnrValue"),
                                item.getString("VcuMnrEvaluate"),item.getString("VcuBpsStandard"),item.getString("VcuBpsValue"),
                                item.getString("VcuBpsEvaluate")
                        		);

                    }

                    return true;
                }
            });

            if (ret) {
                return getResponseString(infName, "0", "ok",null);
            } else {
                return getResponseString(infName, "1", "数据写入接口表失败",null);
            }
        } catch (Exception e) {
            return getResponseString(infName, "1", String.format("发生异常，原因【%s】", e.getMessage()),null);
        }
    }

    /**
     * ELEC03车辆故障
     *
     * @param _para
     * @return
     */
    @WebMethod
    @WebResult
    public String WriteGetCarFaultInfos(@WebParam(name = "_para", targetNamespace = "http://service.zzelec.inf.comm/") String _para) {
        String infName = "ELEC03车辆故障信息";

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

            // 写入数据库接口表
            boolean ret = Db.tx(new IAtom() {
                @Override
                public boolean run() throws SQLException {
                    for (int i = 0; i < datas.size(); i++) {
                        JSONObject item = datas.getJSONObject(i);
                        StringBuffer sql = new StringBuffer();
                        sql.append("insert into T_INF_FROM_ELEC_GETCARFAULT(id, producttion_code, control_module, fault_code,");
                        sql.append(" fault_description, fault_condition, deal_status, deal_time, create_time)");
                        sql.append(" values(sys_guid(), ?, ?, ?,");
                        sql.append(" ?, ?, '0', sysdate, sysdate)");

                        Db.update(sql.toString(),
                                item.getString("ProducttionCode"),
                                item.getString("ControlModule"),
                                item.getString("FaultCode"),
                                item.getString("FaultDescription"),
                                item.getString("FaultCondition"));
                    }

                    return true;
                }
            });

            if (ret) {
                return getResponseString(infName, "0", "ok",null);
            } else {
                return getResponseString(infName, "1", "数据写入接口表失败",null);
            }
        } catch (Exception e) {
            return getResponseString(infName, "1", String.format("发生异常，原因【%s】", e.getMessage()),null);
        }
    }
}

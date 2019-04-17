package cvmes.vips;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

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

import cvmes.common.AbstractWebService;
import cvmes.common.Log;
import cvmes.common.LogLevel;

/**
 * 总装重保件接口 -服务端  (客户端给CVMES系统数据)
 * @author CIKE
 *
 */
@SOAPBinding(style = SOAPBinding.Style.RPC)
@WebService(serviceName = "VipsElevInfobjWebService", targetNamespace = "http://service.zzzbj.inf.comm/")
public class VipsElevInfobjWebService extends AbstractWebService {
    @Override
    @WebMethod(exclude = true)
    public void initServiceCode() {
        this.strServiceCode = "VipsElevInfobjWebService";
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
    private String getResponseString(String infName, String errcode, String errmsg) {
        Map<String, Object> dataMap = new HashMap<String, Object>();
        dataMap.put("errcode", errcode);
        dataMap.put("errmsg", errmsg);

        String json = JSON.toJSONString(dataMap, SerializerFeature.WriteMapNullValue);

        Log.Write(strServiceCode, LogLevel.Information, String.format("【%s】响应请求【%s】", infName, json));
        return json;
    }

    /**
     * ZBJ04电动车信息
     *
     * @param _para
     * @return
     */
    @WebMethod
    @WebResult
    public String GetMotor(@WebParam(name = "_para", targetNamespace = "http://service.zzzbj.inf.comm/") String _para) {
        String infName = "ZBJ04电动车信息";

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
                return getResponseString(infName, "1", "校验密钥不正确");
            }

            //Json数组data为空或无数据
            if (datas == null || datas.size() == 0) {
                return getResponseString(infName, "1", "参数Json中data数组为空或无数据");
            }

            // 写入数据库接口表
            boolean ret = Db.tx(new IAtom() {
                @Override
                public boolean run() throws SQLException {
                    for (int i = 0; i < datas.size(); i++) {
                        JSONObject item = datas.getJSONObject(i);
                        StringBuffer sql = new StringBuffer();
                        sql.append("insert into T_INF_FROM_ZBJ_GETELEINFO(id, production_code, battery_no, controller_no,");
                        sql.append(" bms_part, eps_part,eac_part,dcdc_part,pass_time, deal_status, deal_time, create_time)");
                        sql.append(" values(sys_guid(), ?, ?, ?,");
                        sql.append(" ?, ?, ?, ?, ?, '0', sysdate, sysdate)");

                        Db.update(sql.toString(),
                                item.getString("ProductionCode"),
                                item.getString("BatteryNo"),
                                item.getString("ControllerNo"),
                                item.getString("BmsPart"),
                                item.getString("EpsPart"),
                        		item.getString("EacPart"),
                        		item.getString("DcdcPart"),
                        		item.getDate("PassTime"));
                    }

                    return true;
                }
            });

            if (ret) {
                return getResponseString(infName, "0", "ok");
            } else {
                return getResponseString(infName, "1", "数据写入接口表失败");
            }
        } catch (Exception e) {
            return getResponseString(infName, "1", String.format("发生异常，原因【%s】", e.getMessage()));
        }
    }
}

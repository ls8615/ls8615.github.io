package cvmes.zzsps;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.IAtom;
import cvmes.common.AbstractWebService;
import cvmes.common.Log;
import cvmes.common.LogLevel;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

@WebService(serviceName = "ZzspsWebService", targetNamespace = "http://service.zzSps.inf.comm/")
public class ZzspsWebService extends AbstractWebService {
    @Override
    @WebMethod(exclude = true)
    public void initServiceCode() {
        this.strServiceCode = "ZzspsWebService";
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
     * SPS03集配零件报警
     *
     * @param _para
     * @return
     */
    @WebMethod
    @WebResult
    public String WriteSpsAblyPartCallInfos(@WebParam(name = "_para", targetNamespace = "http://service.zzSps.inf.comm/") String _para) {
        String infName = "SPS03集配零件报警信息";

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
                        sql.append("insert into T_INF_FROM_GETPARTWARN(id, part_code, part_name, setwith_name,");
                        sql.append(" stack_code, stock_num, hubline_no, miss_part_time, is_lack_part, warning_type,");
                        sql.append(" assembly_line, deal_status, deal_time, create_time)");
                        sql.append(" values(sys_guid(), ?, ?, ?, ?, ?, ?,");
                        sql.append(" ?, ?, ?, ?, '0', sysdate, sysdate)");

                        Db.update(sql.toString(),
                                item.getString("PartCode"),
                                item.getString("PositionName"),
                                item.getString("SetwithName"),
                                item.getString("StackCode"),
                                item.getInteger("StockNum"),
                                item.getString("HublineNo"),
                                item.getDate("MissPartTime"),
                                item.getInteger("IsLackPart"),
                                item.getString("WarningType"),
                                item.getInteger("AssemblyLine"));

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

    /**
     * SPS04集配零件救援
     *
     * @param _para
     * @return
     */
    @WebMethod
    @WebResult
    public String WriteSpsAblyPartRescueInfos(@WebParam(name = "_para", targetNamespace = "http://service.zzSps.inf.comm/") String _para) {
        String infName = "SPS03集配零件救援信息";

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
                        sql.append("insert into T_INF_FROM_SPS_GETMOTORTIME(id, hubline_no, area_code, area_name,");
                        sql.append(" help_time, valid, deal_status, deal_time, create_time)");
                        sql.append(" values(sys_guid(), ?, ?, ?,");
                        sql.append(" ?, ?, '0', sysdate, sysdate)");

                        Db.update(sql.toString(),
                                item.getString("HublineNo"),
                                item.getString("AreaCode"),
                                item.getString("AreaName"),
                                item.getDate("HelpTime"),
                                item.getInteger("Valid"));
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

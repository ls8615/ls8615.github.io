package cvmes.tzavi;

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
import javax.jws.soap.SOAPBinding;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

@SOAPBinding(style = SOAPBinding.Style.RPC)
@WebService(serviceName = "MesToPaintAviService", targetNamespace = "http://service.paintAvi.inf.comm/")
public class TzaviWebService extends AbstractWebService {
    @Override
    @WebMethod(exclude = true)
    public void initServiceCode() {
        this.strServiceCode = "TzaviWebService";
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
     * AVI02车辆出入工位信息
     *
     * @param _para
     * @return
     */
    @WebMethod
    @WebResult
    public String WritePositionMoveInfos(@WebParam(name = "_para", targetNamespace = "http://service.paintAvi.inf.comm/") String _para) {
        String infName = "AVI02车辆出入工位信息";

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
                        sql.append("insert into T_INF_FROM_PAINTAVI_PASSRECORD(id, fin_no, position_name, move_time,");
                        sql.append(" move_direction, car_status_code, cartype_mark, plan_source, deal_status, deal_time)");
                        sql.append(" values(sys_guid(), ?, ?, ?,");
                        sql.append(" ?, ?, ?, ?, '0', sysdate)");

                        Db.update(sql.toString(),
                                item.getString("FINNo"),
                                item.getString("PositionName"),
                                item.getDate("MoveTime"),
                                item.getInteger("MoveDirection"),
                                item.getInteger("CarStatusCode"),
                                item.getInteger("CarTypeMark"),
                                item.getInteger("PlanSource"));
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
     * AVI03车辆质量信息
     *
     * @param _para
     * @return
     */
    @WebMethod
    @WebResult
    public String WriteQualityMoveInfos(@WebParam(name = "_para", targetNamespace = "http://service.paintAvi.inf.comm/") String _para) {
        String infName = "AVI03车辆质量信息";

        try {
            //参数转JSON
            JSONObject dataJson = JSONObject.parseObject(_para);

            //获取验证密钥
            String key = dataJson.getString("key");
            //获取接口数据
            JSONArray datas = dataJson.getJSONArray("data");
            Log.Write(strServiceCode, LogLevel.Information, String.format("【%s】接收到数据【%d】条", infName, datas.size()));

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
                        sql.append("insert into T_INF_FROM_PAINTAVI_CARQUALITY(id, fin_no, position_name, move_time,");
                        sql.append(" move_direction, CARSTATUS_CODE, cartype_mark, plan_source, deal_status, deal_time)");
                        sql.append(" values(sys_guid(), ?, ?, ?,");
                        sql.append(" ?, ?, ?, ?, '0', sysdate)");

                        Db.update(sql.toString(),
                                item.getString("FINNo"),
                                item.getString("PositionName"),
                                item.getDate("MoveTime"),
                                item.getInteger("MoveDirection"),
                                item.getInteger("CarStatusCode"),
                                item.getInteger("CarTypeMark"),
                                item.getInteger("PlanSource"));
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
     * AVI04涂装空撬统计信息
     *
     * @param _para
     * @return
     */
    @WebMethod
    @WebResult
    public String WriteStatisticsInfos(@WebParam(name = "_para", targetNamespace = "http://service.paintAvi.inf.comm/") String _para) {
        String infName = "AVI04涂装空撬统计信息";

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
                        sql.append("insert into T_INF_FROM_PAINTAVI_EMPTYSLIDE (ID, PRIMER_EMPTY_PRY_NUM,");
                        sql.append(" FINISH_EMPTY_PRY_NUM, AVI_READ_TIME, DEAL_STATUS, DEAL_TIME)");
                        sql.append(" values(sys_guid(), ?,");
                        sql.append(" ?, ?, '0', sysdate)");

                        Db.update(sql.toString(),
                                item.getInteger("PrimerEmptyPryNum"),
                                item.getInteger("FinishEmptyPryNum"),
                                item.getDate("AVIReadTime"));
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
     * AVI05非计划车辆信息
     *
     * @param _para
     * @return
     */
    @WebMethod
    @WebResult
    public String WriteNoPlanCarInfos(@WebParam(name = "_para", targetNamespace = "http://service.paintAvi.inf.comm/") String _para) {
        String infName = "AVI05非计划车辆信息";

        try {
            //参数转JSON
            JSONObject dataJson = JSONObject.parseObject(_para);

            //获取验证密钥
            String key = dataJson.getString("key");
            //获取接口数据
            JSONArray datas = dataJson.getJSONArray("data");
            Log.Write(strServiceCode, LogLevel.Information, String.format("【%s】接收到数据【%d】条", infName, datas.size()));

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
                        sql.append("INSERT INTO T_INF_FROM_PAINTAVI_AVIPLAN(ID, BODY_CAR_NO, CARTYPE_CODE, K_DRAWING_NO,");
                        sql.append(" COLOR_NAME, SIDE_WINDOW, SKY_LIGHT, BACK_LIGHT, PANEL, ANGLE_PLATE, FLAT_PLATE,");
                        sql.append(" CARTYPE_MASK, PLAN_SOURCE, DEAL_STATUS, DEAL_TIME, ASSEMBLY_SEQUENCE, OPTIONAL_PACKAGE)");
                        sql.append(" values(sys_guid(), ?, ?, ?,");
                        sql.append(" ?, ?, ?, ?, ?, ?, ?,");
                        sql.append(" ?, ?, '0', sysdate, ?, ?)");

                        Db.update(sql.toString(),
                                item.getString("BodyCarNo"),
                                item.getString("CarTypeCode"),
                                item.getString("CarBodyFiguerNo"),
                                item.getString("ColorName"),
                                item.getInteger("SideWindow"),
                                item.getInteger("SkyLight"),
                                item.getInteger("BackLight"),
                                item.getInteger("Panel"),
                                item.getInteger("AnglePlate"),
                                item.getInteger("FlatPlate"),
                                item.getInteger("CarTypeMark"),
                                item.getInteger("PlanSource"),
                                item.getInteger("AssemblySequence"),
                                item.getString("OptionalPackage"));
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
     * AVI06工位实时信息
     *
     * @param _para
     * @return
     */
    @WebMethod
    @WebResult
    public String WriteWorkshopMoveInfos(@WebParam(name = "_para", targetNamespace = "http://service.paintAvi.inf.comm/") String _para) {
        String infName = "AVI06工位实时信息";

        try {
            //参数转JSON
            JSONObject dataJson = JSONObject.parseObject(_para);

            //获取验证密钥
            String key = dataJson.getString("key");
            //获取接口数据
            JSONArray datas = dataJson.getJSONArray("data");
            Log.Write(strServiceCode, LogLevel.Information, String.format("【%s】接收到数据【%d】条", infName, datas.size()));

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
                        sql.append("INSERT INTO T_INF_FROM_PAINTAVI_POSITON(id, position_name, move_time, fin_no,");
                        sql.append(" car_status_code, cartype_mark, plan_source, deal_status, deal_time)");
                        sql.append(" values(sys_guid(), ?, ?, ?,");
                        sql.append(" ?, ?, ?, '0', sysdate)");

                        Db.update(sql.toString(),
                                item.getString("PositionName"),
                                item.getDate("MoveTime"),
                                item.getString("FINNo"),
                                item.getIntValue("CarStatusCode"),
                                item.getIntValue("CarTypeMark"),
                                item.getIntValue("PlanSource"));
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

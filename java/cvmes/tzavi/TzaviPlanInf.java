package cvmes.tzavi;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;
import cvmes.common.AbstractSubServiceThread;
import cvmes.common.Log;
import cvmes.common.LogLevel;
import cvmes.common.WebServiceClient;
import cvmes.entity.EntityWebServicePara;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TzaviPlanInf extends AbstractSubServiceThread {
    private String msg;

    @Override
    public void initServiceCode() {
        this.strServiceCode = "TzaviPlanInf";
    }

    @Override
    public String runBll(Record rec_service) throws Exception {
        msg = "";

        List<Record> list = Db.find("select * from t_inf_to_paintavi_plan where DEAL_STATUS = '0' and rownum <= 1500");
        if (list == null || list.size() == 0) {
            return msg;
        }

        List list_plan = new ArrayList();
        for (Record sub : list) {
            Map<String, Object> map = new HashMap<String, Object>();
            map.put("BodyCarNo", sub.getStr("BODY_CAR_NO"));
            map.put("CarTypeCode", sub.getStr("CARTYPE_CODE"));
            map.put("CarBodyFiguerNo", sub.getStr("K_DRAWING_NO"));
            map.put("ColorName", sub.getStr("COLOR_NAME"));
            map.put("AssemblySequence", sub.getInt("ASSEMBLY_SEQUENCE"));
            map.put("OptionalPackage", sub.getStr("OPTIONAL_PACKAGE"));
            map.put("SideWindow", sub.getInt("SIDE_WINDOW"));
            map.put("SkyLight", sub.getInt("SKY_LIGHT"));
            map.put("BackLight", sub.getInt("BACK_LIGHT"));
            map.put("Panel", sub.getInt("PANEL"));
            map.put("AnglePlate", sub.getInt("ANGLE_PLATE"));
            map.put("FlatPlate", sub.getInt("FLAT_PLATE"));
            map.put("CarTypeMark", sub.getInt("CARTYPE_MASK"));
            map.put("PlanSource", sub.getInt("PLAN_SOURCE"));
            list_plan.add(map);
        }

        EntityWebServicePara entity = new EntityWebServicePara();
        entity.setKey(rec_service.getStr("SERVICE_PARA2_VALUE"));
        entity.setData(list_plan);

        String json = JSONObject.toJSONString(entity, SerializerFeature.WriteMapNullValue);
        WebServiceClient wsc = new WebServiceClient(rec_service.getStr("SERVICE_PARA1_VALUE"), rec_service.getStr("SERVICE_PARA3_VALUE"));

        try {
            Log.Write(strServiceCode, LogLevel.Information, String.format("发送数据【%s】", json));
            String str = (String) wsc.invoke("WritePaintPlan", new String[]{"_para"}, new Object[]{json});

            Log.Write(strServiceCode, LogLevel.Information, String.format("接收响应【%s】", str));
            JSONObject object = JSONObject.parseObject(str);

            int errcode = Integer.parseInt(object.getString("errcode"));
            if (errcode == 0) {
                // 成功
                for (Record sub : list) {
                    Db.update("update t_inf_to_paintavi_plan set DEAL_STATUS = '1', DEAL_TIME = sysdate where ID = ?", sub.getStr("ID"));
                }

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

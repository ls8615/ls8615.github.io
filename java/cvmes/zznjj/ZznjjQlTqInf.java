package cvmes.zznjj;

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

/**
 * 总装拧紧机前桥扭矩接口
 * @author CIKE
 *
 */
public class ZznjjQlTqInf extends AbstractSubServiceThread {
    private String msg;

    @Override
    public void initServiceCode() {
        this.strServiceCode = "ZznjjQlTqInf";
    }

    @Override
    public String runBll(Record rec_service) throws Exception {
        msg = "";

        List<Record> list = Db.find("select * from T_INF_TO_POMMEL_TORQUE where DEAL_STATUS = '0' and rownum <= 1500");
        if (list == null || list.size() == 0) {
            return msg;
        }

        List<Map<String, Object>> list_qltq = new ArrayList<Map<String, Object>>();
        for (Record sub : list) {
            Map<String, Object> map = new HashMap<String, Object>();
            map.put("ProductionCode", sub.getStr("PRODUCTION_CODE"));
            map.put("LineNo", sub.getStr("LINE_NO"));
            map.put("Mistime", sub.getStr("MIS_TIME"));
            map.put("FrontAxleTorque", sub.getStr("FRONT_AXLE_TORQUE"));
            map.put("FrontAxleTwoTorque", sub.getInt("FRONT_AXLE_TWO_TORQUE"));
            list_qltq.add(map);
        }

        EntityWebServicePara entity = new EntityWebServicePara();
        entity.setKey(rec_service.getStr("SERVICE_PARA2_VALUE"));
        entity.setData(list_qltq);

        String json = JSONObject.toJSONString(entity, SerializerFeature.WriteMapNullValue);
        WebServiceClient wsc = new WebServiceClient(rec_service.getStr("SERVICE_PARA1_VALUE"), rec_service.getStr("SERVICE_PARA3_VALUE"));

        try {
            Log.Write(strServiceCode, LogLevel.Information, String.format("发送数据【%s】", json));
            String str = (String) wsc.invoke("Getforontaxletorque", new String[]{"_para"}, new Object[]{json});

            Log.Write(strServiceCode, LogLevel.Information, String.format("接收响应【%s】", str));
            JSONObject object = JSONObject.parseObject(str);

            int errcode = Integer.parseInt(object.getString("errcode"));
            if (errcode == 0) {
                // 成功
                for (Record sub : list) {
                    Db.update("update T_INF_TO_POMMEL_TORQUE set DEAL_STATUS = '1', DEAL_TIME = sysdate where ID = ?", sub.getStr("ID"));
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

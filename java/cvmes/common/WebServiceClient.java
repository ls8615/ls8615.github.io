package cvmes.common;

import org.apache.axis.client.Call;
import org.apache.axis.client.Service;
import org.apache.axis.encoding.XMLType;

import javax.xml.namespace.QName;
import javax.xml.rpc.ParameterMode;
import java.net.URL;

public class WebServiceClient {
    private String wsurl;
    private String namespace;
    private Service service;

    public WebServiceClient(String wsurl, String namespace) {
        this.wsurl = wsurl;
        this.namespace = namespace;
        this.service = new Service();
    }

    public Object invoke(String method, String[] params, Object[] values) throws Exception {
        // 建立服务调用实例
        Call call = (Call) service.createCall();

        // 设定调用路径
        call.setTargetEndpointAddress(new URL(wsurl));
        call.setUseSOAPAction(true);

        // 设定调用方法
        call.setOperationName(new QName(namespace, method));
        call.setSOAPActionURI(namespace + method);

        // 设置被调用方法的返回值类型
        call.setReturnType(XMLType.XSD_STRING);

        // 设置参数
        for (String param : params) {
            call.addParameter(new QName(namespace, param), XMLType.XSD_STRING, ParameterMode.IN);
        }

        return call.invoke(values);
    }
}

package cvmes.entity;

import java.util.ArrayList;
import java.util.List;

public class EntityWebServicePara {
    private String key;
    private List data = new ArrayList();

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public List getData() {
        return data;
    }

    public void setData(List data) {
        this.data = data;
    }
}

package cvmes.u9;

import com.jfinal.plugin.activerecord.*;
import com.jfinal.plugin.activerecord.dialect.SqlServerDialect;
import com.jfinal.plugin.druid.DruidPlugin;
import cvmes.common.AbstractSubServiceThread;
import cvmes.common.Log;
import cvmes.common.LogLevel;

public class U9Common extends AbstractSubServiceThread {
    private String msg = "";

    @Override
    public void initServiceCode() {
        this.strServiceCode = "U9Common";
    }

    @Override
    public String runBll(Record rec_service) throws Exception {
        msg = "";

        try {
            DbPro db = Db.use("u9");
        } catch (Exception e) {
            // 创建连接池
            DruidPlugin dp = new DruidPlugin(rec_service.getStr("SERVICE_PARA1_VALUE"), rec_service.getStr("SERVICE_PARA2_VALUE"), rec_service.getStr("SERVICE_PARA3_VALUE"));
            dp.setMaxActive(Integer.parseInt(rec_service.getStr("SERVICE_PARA4_VALUE")));
            // 保证连接有效
            dp.setValidationQuery("select 1");
            dp.setTimeBetweenEvictionRunsMillis(Integer.parseInt(rec_service.getStr("SERVICE_PARA5_VALUE")) * 1000);
            dp.setTestWhileIdle(true);
            dp.setTestOnBorrow(false);
            dp.setTestOnReturn(false);
            // 启动连接池
            dp.start();

            // 创建ActiveRecordPlugin操作对象
            ActiveRecordPlugin arp = new ActiveRecordPlugin("u9", dp);
            // 设置数据库方言
            arp.setDialect(new SqlServerDialect());
            // 配置属性名(字段名)大小写不敏感容器工厂
            arp.setContainerFactory(new CaseInsensitiveContainerFactory());
            // 启动ActiveRecord
            try {
                arp.start();
                msg = "创建u9连接池成功";
                Log.Write(strServiceCode, LogLevel.Information, msg);
            } catch (Exception ex) {
                arp.stop();
                dp.stop();
                msg = String.format("创建u9连接池失败，原因【%s】", ex.getMessage());
                Log.Write(strServiceCode, LogLevel.Error, msg);
            }
        }

        return msg;
    }
}

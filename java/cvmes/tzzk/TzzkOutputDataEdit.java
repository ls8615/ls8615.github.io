package cvmes.tzzk;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;
import cvmes.common.AbstractSubServiceThread;

import java.util.List;

public class TzzkOutputDataEdit extends AbstractSubServiceThread {
    @Override
    public void initServiceCode() {
        this.strServiceCode = "TzzkOutputDataEdit";
    }

    @Override
    public String runBll(Record rec_service) throws Exception {
        String msg = "";

        StringBuffer sql = new StringBuffer();
        sql.append("SELECT func_getnosys_workday(MOVE_TIME) AS day_time,");
        sql.append(" DECODE(POSITION_NAME, 'DDG5-9', 1, 'DDG5-16', 2, 'DDG5-72', 3, 'DDG5-78', 6,");
        sql.append(" 'DDG5-130', 7, 'DDG5-132', 8, 'HSJ1-5', 9, 'DDG5-229', 10, 'YH-18', 11, 'XZG-6', 12) AS job_name,");
        sql.append(" COUNT(1) AS actual_num");
        sql.append(" FROM (select distinct MOVE_TIME,POSITION_NAME,FIN_NO from T_INF_FROM_PAINTAVI_PASSRECORD)");
        sql.append(" WHERE POSITION_NAME IN ('DDG5-9', 'DDG5-16', 'DDG5-72', 'DDG5-78', 'DDG5-130',");
        sql.append(" 'DDG5-132', 'HSJ1-5', 'DDG5-229', 'YH-18', 'XZG-6')");
        sql.append(" AND to_char(MOVE_TIME,'yyyy-mm-dd')=func_get_workday()");
        sql.append(" AND FIN_NO NOT LIKE '%DFLZM%'");
        sql.append(" GROUP BY func_getnosys_workday(MOVE_TIME), POSITION_NAME");

        sql.append(" UNION ALL");

        sql.append(" SELECT func_getnosys_workday(MOVE_TIME) AS day_time,");
        sql.append(" DECODE(POSITION_NAME, 'HSJ1-8', 4, 'YH-24', 5) AS job_name,");
        sql.append(" COUNT(1) AS actual_num");
        sql.append(" FROM (select distinct MOVE_TIME,POSITION_NAME,FIN_NO from T_INF_FROM_PAINTAVI_PASSRECORD)");
        sql.append(" WHERE POSITION_NAME IN ('HSJ1-8', 'YH-24')");
        sql.append(" AND to_char(MOVE_TIME,'yyyy-mm-dd')=func_get_workday()");
        sql.append(" AND FIN_NO LIKE '%DFLZM%'");
        sql.append(" GROUP BY func_getnosys_workday(MOVE_TIME), POSITION_NAME");

        List<Record> list = Db.find(sql.toString());
        if (list == null) return msg;

        for (Record sub : list) {
            Record rec = Db.findFirst("select * from T_INF_TO_PAINTMC_CARACTUAL where to_char(DAY_TIME,'yyyy-mm-dd')=func_get_workday() and JOB_NAME=?", sub.getInt("JOB_NAME"));
            if (rec == null) {
                // 不存在，插入
                Db.update("insert into T_INF_TO_PAINTMC_CARACTUAL(ID, DAY_TIME, JOB_NAME, ACTUAL_NUM, DEAL_STATUS, DEAL_TIME) values(sys_guid(), to_date(func_get_workday(),'yyyy-mm-dd hh24:mi:ss'), ?, ?, '0',sysdate)", sub.getInt("JOB_NAME"), sub.getInt("ACTUAL_NUM"));
            } else {
                // 已存在，更新
                Db.update("update T_INF_TO_PAINTMC_CARACTUAL set ACTUAL_NUM=?, DEAL_STATUS='0', DEAL_TIME=sysdate where ID = ?", sub.getInt("ACTUAL_NUM"), rec.getStr("ID"));
            }
        }

        msg = "涂装产量统计成功";
        return msg;
    }
}

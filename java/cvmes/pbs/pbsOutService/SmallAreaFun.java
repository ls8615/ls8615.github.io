package cvmes.pbs.pbsOutService;


import com.jfinal.plugin.activerecord.Record;

public class SmallAreaFun {

    static Record recordSmallOutCmd;

    /**
     * 根据生产编码获取所在区域内所有实车，参数：生产编码
     *
     * @return
     */
    public static String getZoneAllCarByProductionSql() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("SELECT Z.PRODUCTION_CODE,");
        sql.append("       Z.ZONE_CODE,");
        sql.append("       Z.PRODUCT_POS,");
        sql.append("       P.K_IS_PBS_DISABLE_CAR,");
        sql.append("       P.K_IS_PBS_BACK_CAR,");
        sql.append("       P.K_IS_PBS_RETURN_CAR,");
        sql.append("       VP.LINE_CODE,");
        sql.append("       VP.SEQ_NO");
        sql.append("  FROM T_MODEL_ZONE Z");
        sql.append("  LEFT JOIN T_PLAN_DEMAND_PRODUCT P");
        sql.append("    ON P.PRODUCTION_CODE = Z.PRODUCTION_CODE");
        sql.append("  LEFT JOIN (SELECT D.PRODUCTION_CODE,");
        sql.append("                    D.LINE_CODE,");
        sql.append("                    TO_CHAR(M.SCHEDULING_PLAN_DATE, 'yyyymmdd') ||");
        sql.append("                    LPAD(D.SEQ_NO, 3, 0) SEQ_NO");
        sql.append("               FROM T_PLAN_SCHEDULING M");
        sql.append("               LEFT JOIN T_PLAN_SCHEDULING_D D");
        sql.append("                 ON D.SCHEDULING_PLAN_CODE = M.SCHEDULING_PLAN_CODE");
        sql.append("              WHERE D.LINE_CODE IN ('zz0101', 'zz0102')) VP");
        sql.append("    ON VP.PRODUCTION_CODE = Z.PRODUCTION_CODE");
        sql.append(" WHERE Z.ZONE_CODE =");
        sql.append("       (SELECT T.ZONE_CODE");
        sql.append("          FROM T_MODEL_ZONE T");
        sql.append("         WHERE T.PRODUCTION_CODE = ?)");
        //sql.append("   AND Z.ZONE_CODE IN");
        //sql.append("       ('pbs08_01', 'pbs08_02', 'pbs08_03', 'pbs08_04', 'pbs08_05')");
        sql.append("   AND Z.PRODUCTION_CODE IS NOT NULL");
        sql.append(" ORDER BY Z.PRODUCT_POS");

        return sql.toString();
    }

    /**
     * 更新小库区出口手工计划状态，参数：生产编码
     *
     * @return
     */
    public static String updateManualPlanStatus() {
        StringBuffer sql = new StringBuffer(128);
        sql.append("UPDATE t_pbs_out_mplan_small t SET t.plan_status=1 ,t.execute_time=SYSDATE WHERE t.ID=?");

        return sql.toString();
    }

    /**
     * 出口一号移行机接车所在区域与接车指令关系对照关系
     *
     * @return
     */
    public static Record getShiftingMachineOutOnePickCarZoneCodeOfInstructionsRela() {
        if (recordSmallOutCmd == null) {
            recordSmallOutCmd = new Record();
            recordSmallOutCmd.set("pbs08_01", 1);
            recordSmallOutCmd.set("pbs08_02", 2);
            recordSmallOutCmd.set("pbs08_03", 3);
            recordSmallOutCmd.set("pbs08_04", 4);
            recordSmallOutCmd.set("pbs08_05", 11);
        }

        return recordSmallOutCmd;
    }

    /**
     * 根据生产线编码获取小库区1-5道可搬出区域头车，返回车，计划顺序号最小的车所在车道区域信息。参数：生产线编码
     *
     * @return
     */
    public static String getSmallAreaMinPlanSeqZoneInfoOfBackCarSql() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("SELECT Z.ZONE_CODE,");
        sql.append("       Z.PRODUCT_POS,");
        sql.append("       Z.PRODUCTION_CODE,");
        sql.append("       VP.LINE_CODE,");
        sql.append("       VP.SEQ_NO");
        sql.append("  FROM T_MODEL_ZONE Z");
        sql.append("  LEFT JOIN (SELECT D.PRODUCTION_CODE,");
        sql.append("                    D.LINE_CODE,");
        sql.append("                    TO_CHAR(M.SCHEDULING_PLAN_DATE, 'yyyymmdd') ||");
        sql.append("                    LPAD(D.SEQ_NO, 3, 0) SEQ_NO");
        sql.append("               FROM T_PLAN_SCHEDULING M");
        sql.append("               LEFT JOIN T_PLAN_SCHEDULING_D D");
        sql.append("                 ON D.SCHEDULING_PLAN_CODE = M.SCHEDULING_PLAN_CODE");
        sql.append("              WHERE D.LINE_CODE IN ('zz0101', 'zz0102')) VP");
        sql.append("    ON Z.PRODUCTION_CODE = VP.PRODUCTION_CODE");
        sql.append("  LEFT JOIN T_PLAN_DEMAND_PRODUCT P");
        sql.append("    ON P.PRODUCTION_CODE = Z.PRODUCTION_CODE");
        sql.append(" WHERE 1 = 1");
        sql.append("   AND Z.PRODUCT_POS = 1");
        sql.append("   AND Z.PRODUCTION_CODE IS NOT NULL");
        sql.append("   AND P.K_IS_PBS_BACK_CAR = 1");
        sql.append("   AND VP.LINE_CODE = ?");
        sql.append("   AND Z.ZONE_CODE IN");
        sql.append("       (SELECT CASE");
        sql.append("                 WHEN DEVICE_SIGNAL_CODE = 'SH_006' THEN");
        sql.append("                  'pbs08_01'");
        sql.append("                 WHEN DEVICE_SIGNAL_CODE = 'SH_007' THEN");
        sql.append("                  'pbs08_02'");
        sql.append("                 WHEN DEVICE_SIGNAL_CODE = 'SH_008' THEN");
        sql.append("                  'pbs08_03'");
        sql.append("                 WHEN DEVICE_SIGNAL_CODE = 'SH_009' THEN");
        sql.append("                  'pbs08_04'");
        sql.append("                 WHEN DEVICE_SIGNAL_CODE = 'SH_010' THEN");
        sql.append("                  'pbs08_05'");
        sql.append("               END ZONE_CODE");
        sql.append("          FROM T_PBS_DEVICE_SIGNAL_STATUS T");
        sql.append("         WHERE DEVICE_SIGNAL_CODE IN");
        sql.append("               ('SH_006', 'SH_007', 'SH_008', 'SH_009', 'SH_010')");
        sql.append("           AND DEVICE_STATUS = 0)");
        sql.append(" ORDER BY VP.SEQ_NO");

        return sql.toString();
    }

    /**
     * 根据生产线编码获取小库区1-5道可搬出区域和返回道正常车（非禁止车，非返回车）计划顺序号最小的车所在车道区域信息。参数：生产线编码
     *
     * @return
     */
    public static String getSmallAreaMinPlanSeqZoneInfoOfNormalCarSql() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("SELECT Z.ZONE_CODE,");
        sql.append("       Z.PRODUCT_POS,");
        sql.append("       Z.PRODUCTION_CODE,");
        sql.append("       VP.LINE_CODE,");
        sql.append("       VP.SEQ_NO");
        sql.append("  FROM T_MODEL_ZONE Z");
        sql.append("  LEFT JOIN (SELECT D.PRODUCTION_CODE,");
        sql.append("                    D.LINE_CODE,");
        sql.append("                    TO_CHAR(M.SCHEDULING_PLAN_DATE, 'yyyymmdd') ||");
        sql.append("                    LPAD(D.SEQ_NO, 3, 0) SEQ_NO");
        sql.append("               FROM T_PLAN_SCHEDULING M");
        sql.append("               LEFT JOIN T_PLAN_SCHEDULING_D D");
        sql.append("                 ON D.SCHEDULING_PLAN_CODE = M.SCHEDULING_PLAN_CODE");
        sql.append("              WHERE D.LINE_CODE IN ('zz0101', 'zz0102')) VP");
        sql.append("    ON Z.PRODUCTION_CODE = VP.PRODUCTION_CODE");
        sql.append("  LEFT JOIN T_PLAN_DEMAND_PRODUCT P");
        sql.append("    ON P.PRODUCTION_CODE = Z.PRODUCTION_CODE");
        sql.append(" WHERE 1 = 1");
        sql.append("   AND Z.PRODUCTION_CODE IS NOT NULL");
        sql.append("   AND P.K_IS_PBS_DISABLE_CAR = 0");
        sql.append("   AND P.K_IS_PBS_BACK_CAR = 0");
        sql.append("   AND VP.LINE_CODE = ?");
        sql.append("   AND Z.ZONE_CODE IN");
        sql.append("       (SELECT CASE");
        sql.append("                 WHEN DEVICE_SIGNAL_CODE = 'SH_006' THEN");
        sql.append("                  'pbs08_01'");
        sql.append("                 WHEN DEVICE_SIGNAL_CODE = 'SH_007' THEN");
        sql.append("                  'pbs08_02'");
        sql.append("                 WHEN DEVICE_SIGNAL_CODE = 'SH_008' THEN");
        sql.append("                  'pbs08_03'");
        sql.append("                 WHEN DEVICE_SIGNAL_CODE = 'SH_009' THEN");
        sql.append("                  'pbs08_04'");
        sql.append("                 WHEN DEVICE_SIGNAL_CODE = 'SH_010' THEN");
        sql.append("                  'pbs08_05'");
        sql.append("               END ZONE_CODE");
        sql.append("          FROM T_PBS_DEVICE_SIGNAL_STATUS T");
        sql.append("         WHERE DEVICE_SIGNAL_CODE IN");
        sql.append("               ('SH_006', 'SH_007', 'SH_008', 'SH_009', 'SH_010')");
        sql.append("           AND DEVICE_STATUS = 0");
        sql.append("        UNION SELECT 'pbs08_06' FROM DUAL");
        sql.append("        UNION SELECT 'pbs07' FROM DUAL)");
        sql.append(" ORDER BY VP.SEQ_NO");

        return sql.toString();
    }

    /**
     * 根据小库区区域头车信息，与大库区可搬出区域头车区域信息，按计划顺序从小到大排序。参数：小库区区域编码|小库区区域头车生产线编码（zz0101，zz0102）|小库区区域头车生产线编码（zz0101，zz0102）
     *
     * @return
     */
    public static String getSmallAreaZoneCodeAanBitAreaMinPlanSeqZnotinfoSql() {
        StringBuffer sql = new StringBuffer(256);
        sql.append("SELECT Z.PRODUCTION_CODE,");
        sql.append("       Z.ZONE_CODE,");
        sql.append("       Z.PRODUCT_POS,");
        sql.append("       Z.ZONE_NAME,");
        sql.append("       VP.LINE_CODE,");
        sql.append("       VP.SEQ_NO");
        sql.append("  FROM T_MODEL_ZONE Z");
        sql.append("  LEFT JOIN (SELECT D.PRODUCTION_CODE,");
        sql.append("                    D.LINE_CODE,");
        sql.append("                    TO_CHAR(M.SCHEDULING_PLAN_DATE, 'yyyymmdd') ||");
        sql.append("                    LPAD(D.SEQ_NO, 3, 0) SEQ_NO");
        sql.append("               FROM T_PLAN_SCHEDULING M");
        sql.append("               LEFT JOIN T_PLAN_SCHEDULING_D D");
        sql.append("                 ON M.SCHEDULING_PLAN_CODE = D.SCHEDULING_PLAN_CODE");
        sql.append("              WHERE D.LINE_CODE IN ('zz0101', 'zz0102')) VP");
        sql.append("    ON VP.PRODUCTION_CODE = Z.PRODUCTION_CODE");
        sql.append(" INNER JOIN (SELECT CASE");
        sql.append("                      WHEN T.DEVICE_SIGNAL_CODE = 'SH_019' THEN");
        sql.append("                       'pbs06_01'");
        sql.append("                      WHEN T.DEVICE_SIGNAL_CODE = 'SH_020' THEN");
        sql.append("                       'pbs06_02'");
        sql.append("                      WHEN T.DEVICE_SIGNAL_CODE = 'SH_021' THEN");
        sql.append("                       'pbs06_03'");
        sql.append("                      WHEN T.DEVICE_SIGNAL_CODE = 'SH_022' THEN");
        sql.append("                       'pbs06_04'");
        sql.append("                      WHEN T.DEVICE_SIGNAL_CODE = 'SH_023' THEN");
        sql.append("                       'pbs06_05'");
        sql.append("                      WHEN T.DEVICE_SIGNAL_CODE = 'SH_024' THEN");
        sql.append("                       'pbs06_06'");
        sql.append("                      WHEN T.DEVICE_SIGNAL_CODE = 'SH_025' THEN");
        sql.append("                       'pbs06_07'");
        sql.append("                      WHEN T.DEVICE_SIGNAL_CODE = 'SH_026' THEN");
        sql.append("                       'pbs06_08'");
        sql.append("                    END ZONE_CODE,");
        sql.append("                    LINE_NUM");
        sql.append("               FROM T_PBS_DEVICE_SIGNAL_STATUS T");
        sql.append("               LEFT JOIN (SELECT CASE");
        sql.append("                                  WHEN LINE_CODE = 'CARLANE1' THEN");
        sql.append("                                   'SH_019'");
        sql.append("                                  WHEN LINE_CODE = 'CARLANE2' THEN");
        sql.append("                                   'SH_020'");
        sql.append("                                  WHEN LINE_CODE = 'CARLANE3' THEN");
        sql.append("                                   'SH_021'");
        sql.append("                                  WHEN LINE_CODE = 'CARLANE4' THEN");
        sql.append("                                   'SH_022'");
        sql.append("                                  WHEN LINE_CODE = 'CARLANE5' THEN");
        sql.append("                                   'SH_023'");
        sql.append("                                  WHEN LINE_CODE = 'CARLANE6' THEN");
        sql.append("                                   'SH_024'");
        sql.append("                                  WHEN LINE_CODE = 'CARLANE7' THEN");
        sql.append("                                   'SH_025'");
        sql.append("                                  WHEN LINE_CODE = 'CARLANE8' THEN");
        sql.append("                                   'SH_026'");
        sql.append("                                END SIGNAL_CODE,");
        sql.append("                                DECODE(LINE_NUM, 1, 'zz0101', 2, 'zz0102') LINE_NUM");
        sql.append("                           FROM T_PBS_LINE_CARLANE UNPIVOT(LINE_NUM FOR LINE_CODE IN(CARLANE1,");
        sql.append("                                                                                     CARLANE2,");
        sql.append("                                                                                     CARLANE3,");
        sql.append("                                                                                     CARLANE4,");
        sql.append("                                                                                     CARLANE5,");
        sql.append("                                                                                     CARLANE6,");
        sql.append("                                                                                     CARLANE7,");
        sql.append("                                                                                     CARLANE8))) VT");
        sql.append("                 ON VT.SIGNAL_CODE = T.DEVICE_SIGNAL_CODE");
        sql.append("              WHERE T.DEVICE_STATUS = 0");
        sql.append("                AND VT.SIGNAL_CODE IS NOT NULL");
        sql.append("             UNION ALL SELECT ?, ? FROM DUAL) VZ");
        sql.append("    ON Z.ZONE_CODE = VZ.ZONE_CODE");
        sql.append("   AND VP.LINE_CODE = VZ.LINE_NUM");
        sql.append(" WHERE VP.LINE_CODE = ?");
        sql.append("   AND Z.PRODUCT_POS = 1");
        sql.append(" ORDER BY VP.SEQ_NO");

        return sql.toString();
    }
}

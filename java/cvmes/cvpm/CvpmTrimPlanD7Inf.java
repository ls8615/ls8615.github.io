package cvmes.cvpm;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;
import cvmes.common.AbstractSubServiceThread;
import cvmes.common.Log;
import cvmes.common.LogLevel;

import java.util.List;

public class CvpmTrimPlanD7Inf extends AbstractSubServiceThread {
    private String msg = "";
    private String dmsg = "";

    @Override
    public void initServiceCode() {
        this.strServiceCode = "CvpmTrimPlanD7Inf";
    }

    @Override
    public String runBll(Record rec_service) throws Exception {
        // 重置最后操作信息
        msg = "";

        // 获取已同步计划日期
        String alreadySyncPlanDate = rec_service.getStr("SERVICE_PARA1_VALUE");

        // 获取第1条记录的计划审核状态
        StringBuffer sql_audit = new StringBuffer();
        sql_audit.append("SELECT TO_CHAR(ZPRQ, 'yyyy-mm-dd') AS ZPRQ, SHF");
        sql_audit.append(" FROM V_SCGL_D7_RQ");
        sql_audit.append(" WHERE TO_CHAR(ZPRQ, 'yyyy-mm-dd')>?");
        sql_audit.append(" ORDER BY ZPRQ");

        Record rec_audit = Db.use("cvpm").findFirst(sql_audit.toString(), alreadySyncPlanDate);
        if (rec_audit == null) {
            return msg;
        }

        if ("Y".equals(rec_audit.getStr("SHF"))) {
            // 先同步颜色基础数据
            List<Record> list_color = Db.use("cvpm").find("SELECT DISTINCT CSYS FROM V_SCGL_YSJH WHERE TO_CHAR(RQ, 'yyyy-mm-dd')=?", rec_audit.getStr("ZPRQ"));
            if (list_color != null && list_color.size() != 0) {
                for (Record sub_color : list_color) {
                    if (sub_color.getStr("CSYS") != null) {
                        Record rec_color = Db.findFirst("select count(*) as cnt from t_base_color where color_code=?", sub_color.getStr("CSYS"));
                        if (rec_color != null && rec_color.getInt("cnt") == 0) {
                            Db.update("insert into t_base_color(id, color_code, color_name, color_status) values(sys_guid(), ?, ?, '0')", sub_color.getStr("csys"), sub_color.getStr("csys"));
                        }
                    }
                }
            }

            // 再同步车型代码基础数据
            List<Record> list_cartype = Db.use("cvpm").find("SELECT DISTINCT CPDM FROM V_SCGL_YSJH WHERE TO_CHAR(RQ, 'yyyy-mm-dd')=?", rec_audit.getStr("ZPRQ"));
            if (list_cartype != null && list_cartype.size() != 0) {
                for (Record sub_cartype : list_cartype) {
                    if (sub_cartype.getStr("CPDM") != null) {
                        Record rec_cartype = Db.findFirst("select count(*) as cnt from T_BASE_PRODUCT where PRODUCT_CODE=?", sub_cartype.getStr("CPDM"));
                        if (rec_cartype != null && rec_cartype.getInt("cnt") == 0) {
                            Db.update("insert into T_BASE_PRODUCT(ID, WORKSHOP_CODE, PRODUCT_CODE) values(sys_guid(), 'zz01', ?)", sub_cartype.getStr("CPDM"));
                        }
                    }
                }
            }

            // 获取指定日期的D+7计划数据
            StringBuffer sql_d7 = new StringBuffer();
            sql_d7.append("SELECT DISTINCT");
            sql_d7.append(" T1.SCBM AS PRODUCTION_CODE,"); //生产编码
            sql_d7.append(" NVL(T2.CPBH, T1.CPBH) AS K_NOTICE_CARTYPE,"); //公告车型
            sql_d7.append(" T1.CPDM AS DEMAND_PRODUCT_CODE,"); //需求产品编码
            sql_d7.append(" T1.XH AS BOM_CODE,"); //BOM编码
            sql_d7.append(" TO_CHAR(NVL(T2.RKYQ, T1.RKYQ), 'yyyy-mm-dd') AS DEMAND_DATE,"); //要货日期
            sql_d7.append(" NVL(NVL(T2.BZ, T3.BZ), T1.BZ) AS DEMAND_PRODUCT_REMARK,"); //需求产品备注
            sql_d7.append(" NVL(T2.FAH, T1.FAH) AS K_SCHEME_CODE,"); //方案号
            sql_d7.append(" NVL(T2.FDJ, T1.FDJ) AS K_ENGINE,"); //发动机
            sql_d7.append(" NVL(T2.DDJ, T1.DDJ) AS K_ELECTROMOTOR,"); //电动机
            sql_d7.append(" NVL(T2.BSX, T1.BSX) AS K_GEARBOX,"); //变速箱
            sql_d7.append(" NVL(T2.QQ, T1.QQ) AS K_FRONT_AXLE_ONE,"); //前桥一轴
            sql_d7.append(" NVL(T2.QQ1, T1.QQ1) AS K_FRONT_AXLE_TWO,"); //前桥二轴
            sql_d7.append(" NVL(T2.HQ, T1.HQ) AS K_REARAXLE,"); //后桥
            sql_d7.append(" NVL(T2.ZQ, T1.ZQ) AS K_MID_AXLE,"); //中桥
            sql_d7.append(" NVL(T2.FQ, T1.FQ) AS K_FLOAT_AXLE,"); //浮桥
            sql_d7.append(" NVL(T2.CJ, T1.CJ) AS K_CARRIAGE,"); //车架
            sql_d7.append(" CASE WHEN t1.PF = '喷粉'  THEN '1' ELSE '0' END K_CARRIAGE_IS_JET_POWDER,"); //车架是否喷粉
            sql_d7.append(" NVL(NVL(T2.CS, T3.CS), T1.CS) AS K_CARTYPE,"); //车身图号
            sql_d7.append(" NVL(NVL(T2.CS1, T3.CS1), T1.CS1) AS K_CARBODY_CODE,"); //车身白件图号
            sql_d7.append(" NVL(NVL(T2.CSYS, T3.CSYS), T1.CSYS) AS K_COLOR_NAME,"); //颜色名称
            sql_d7.append(" NVL(T2.LT, T1.LT) AS K_TYRE,"); //轮胎
            sql_d7.append(" NVL(T2.LT02, T1.LT02) AS K_TYRE2,"); //轮胎2
            sql_d7.append(" NVL(T2.A1, T1.A1) AS K_BALANCE_SUSPENSION,"); //平衡悬挂
            sql_d7.append(" NVL(T2.XUZ, T1.XUZ) AS K_SUSPENSION,"); //悬置
            sql_d7.append(" T1.RQ,"); //装配线计划日期
            sql_d7.append(" (CASE WHEN T1.ZPX = '1线' THEN 1 WHEN T1.ZPX = '2线' THEN 2 WHEN T1.ZPX = '3线' THEN 3 END) AS K_ASSEMBLY_LINE,"); //装配线
            sql_d7.append(" T1.ZPX AS K_SOURCE_ASSEMBLY_LINE,"); //原始装配线
            sql_d7.append(" T1.ZPSX,"); //装配顺序
            sql_d7.append(" NVL(T2.DCB, T1.DCB) AS K_BATTER_PART,"); //电池包图号
            sql_d7.append(" NVL(T2.ZCKZQ, T1.ZCKZQ) AS K_CONTROLLER_PART,"); //整车控制器图号
            sql_d7.append(" DECODE(T2.EL, null, '整车', '二类') AS TWO_TYPE,"); //二类
            sql_d7.append(" T1.CLPZXX AS K_SHOTCRETE_MSG,"); //喷字信息
            sql_d7.append(" NVL(TO_NUMBER(SUBSTR(T2.XHID, -3)), T1.ZPSX) AS D7X,"); //D+7计划序
            sql_d7.append(" TO_CHAR(T1.RQ, 'yyyy-mm-dd') D7_DATE,"); //D+7计划日期
            sql_d7.append(" CASE WHEN T1.DC = '是'  THEN '1' ELSE '0' END K_IS_TRAM,"); //是否电车
            sql_d7.append(" NVL(T2.DHDW, T1.DHDW) AS K_BUYER,"); //订货单位
            sql_d7.append(" NVL(T2.SZBZ, T1.SZBZ) AS K_TRIAL_ASSEMBLY,"); //试装项目
            sql_d7.append(" NVL(T2.JSCZ, T1.JSCZ) AS K_SWITCHING_PROJECT,"); //切换项目
            sql_d7.append(" NVL(T2.DDLX, T1.DDLX) AS K_CATEGORY,"); //类别
            sql_d7.append(" T1.ZBBH AS K_STAMP_ID,"); //焊装钢码号
            sql_d7.append(" T2.DDBH AS K_CARID,"); //销售CARID
            sql_d7.append(" NVL(T2.XZH1, T1.XZH1) AS K_OPTIONAL_PACKAGE_1,"); //选装号1
            sql_d7.append(" NVL(T2.XZH2, T1.XZH2) AS K_OPTIONAL_PACKAGE_2,"); //选装号2
            sql_d7.append(" NVL(T2.XZH3, T1.XZH3) AS K_OPTIONAL_PACKAGE_3,"); //选装号3
            sql_d7.append(" NVL(T2.XZH4, T1.XZH4) AS K_OPTIONAL_PACKAGE_4,"); //选装号4
            sql_d7.append(" NVL(T2.XZH5, T1.XZH5) AS K_OPTIONAL_PACKAGE_5,"); //选装号5
            sql_d7.append(" NVL(T2.XZH6, T1.XZH6) AS K_OPTIONAL_PACKAGE_6,"); //选装号6
            sql_d7.append(" NVL(T2.XZH7, T1.XZH7) AS K_OPTIONAL_PACKAGE_7,"); //选装号7
            sql_d7.append(" NVL(T2.XZH8, T1.XZH8) AS K_OPTIONAL_PACKAGE_8,"); //选装号8
            sql_d7.append(" NVL(T2.XZH9, T1.XZH9) AS K_OPTIONAL_PACKAGE_9,"); //选装号9
            sql_d7.append(" NVL(T2.XZH10, T1.XZH10) AS K_OPTIONAL_PACKAGE_10,"); //选装号10
            sql_d7.append(" NVL(T2.XZH11, T1.XZH11) AS K_OPTIONAL_PACKAGE_11,"); //选装号11
            sql_d7.append(" NVL(T2.XZH12, T1.XZH12) AS K_OPTIONAL_PACKAGE_12,"); //选装号12
            sql_d7.append(" NVL(T2.XZH13, T1.XZH13) AS K_OPTIONAL_PACKAGE_13,"); //选装号13
            sql_d7.append(" NVL(T2.XZH14, T1.XZH14) AS K_OPTIONAL_PACKAGE_14,"); //选装号14
            sql_d7.append(" NVL(T2.XZH15, T1.XZH15) AS K_OPTIONAL_PACKAGE_15,"); //选装号15
            sql_d7.append(" NVL(T2.BZH1, T1.BZH1) AS K_MUST_PACKAGE_1,"); //必装号1
            sql_d7.append(" NVL(T2.BZH2, T1.BZH2) AS K_MUST_PACKAGE_2,"); //必装号2
            sql_d7.append(" NVL(T2.BZH3, T1.BZH3) AS K_MUST_PACKAGE_3,"); //必装号3
            sql_d7.append(" NVL(T2.BZH4, T1.BZH4) AS K_MUST_PACKAGE_4,"); //必装号4
            sql_d7.append(" NVL(T2.BZH5, T1.BZH5) AS K_MUST_PACKAGE_5,"); //必装号5
            sql_d7.append(" NVL(T2.BZH6, T1.BZH6) AS K_MUST_PACKAGE_6,"); //必装号6
            sql_d7.append(" NVL(T2.BZH7, T1.BZH7) AS K_MUST_PACKAGE_7,"); //必装号7
            sql_d7.append(" NVL(T2.BZH8, T1.BZH8) AS K_MUST_PACKAGE_8,"); //必装号8
            sql_d7.append(" NVL(T2.BZH9, T1.BZH9) AS K_MUST_PACKAGE_9,"); //必装号9
            sql_d7.append(" NVL(T2.BZH10, T1.BZH10) AS K_MUST_PACKAGE_10,"); //必装号10
            sql_d7.append(" T2.HGZBZ CERTIFICATE_REMARK,"); //合格证备注
            sql_d7.append(" T4.CPLB PRODUCT_TYPE,"); //产品类型
            sql_d7.append(" D3.ZPSX2 D3X,"); //D+3计划顺序
            sql_d7.append(" T1.JHLB K_PLAN_TYPE,"); //计划类型
            sql_d7.append(" T1.SCBM_CSMSK K_VISUAL_PRODUCTION_CODE,"); //三包车目视卡生产编码
            sql_d7.append(" DECODE(SUBSTR(T1.ZBBH, 8, 1), '*', '1', '0') AS K_IS_FOREIGN_CARBODY,"); //外来车身标记
            sql_d7.append(" T3.BXGYS AS K_BUMPER_COLOR"); //保险杠颜色
            sql_d7.append(" FROM V_SCGL_YSJH T1");
            sql_d7.append(" LEFT JOIN LQGA.LQSCDJHJK T2 ON T1.SCBM = T2.SCBM");
            sql_d7.append(" LEFT JOIN LQGA.LQSCDJHJKCS T3 ON T1.SCBM = T3.SCBM");
            sql_d7.append(" LEFT JOIN LQGA.LQGAD20003A_TMP T4 ON T1.SCBM = T4.PRODUCT_ID");
            sql_d7.append(" LEFT JOIN V_SCGL_YSJH_D3 D3 ON T1.XH = D3.XH");
            sql_d7.append(" WHERE TO_CHAR(T1.RQ, 'yyyy-mm-dd') = ?");

            List<Record> list = Db.use("cvpm").find(sql_d7.toString(), rec_audit.getStr("ZPRQ"));
            if (list == null || list.size() == 0) {
                msg = String.format("获取日期【%s】D+7车辆信息失败", rec_audit.getStr("ZPRQ"));
                Log.Write(strServiceCode, LogLevel.Error, msg);
            } else {
                // 逐条处理并写入需求产品表
                for (Record sub : list) {
                    // 车辆是否已存在
                    StringBuffer sql_have = new StringBuffer();
                    sql_have.append("SELECT COUNT(*) AS CNT FROM T_PLAN_DEMAND_PRODUCT WHERE PRODUCTION_CODE=? and DEMAND_PRODUCT_TYPE='0'");

                    Record rec_have = Db.findFirst(sql_have.toString(), sub.getStr("PRODUCTION_CODE"));
                    if (rec_have == null) {
                        Log.Write(strServiceCode, LogLevel.Error, String.format("判断车辆【%s】是否已存在失败", sub.getStr("PRODUCTION_CODE")));
                        continue;
                    }
                    if (rec_have.getInt("CNT") >= 1) {
                        // 车辆已存在，更新
                        StringBuffer sql_upd = new StringBuffer();
                        sql_upd.append("update t_plan_demand_product set ");
                        sql_upd.append("DEMAND_PRODUCT_CODE = ?, "); //需求产品编码
                        sql_upd.append("DEMAND_DATE = to_date(?,'yyyy-mm-dd'), "); //要货日期
                        sql_upd.append("BOM_CODE = ?, "); //BOM编码
                        sql_upd.append("DEMAND_PRODUCT_REMARK = ?, "); //需求产品备注
                        sql_upd.append("K_ENGINE = ?, "); //发动机
                        sql_upd.append("K_GEARBOX = ?, "); //变速箱
                        sql_upd.append("K_CARTYPE = ?, "); //车身图号
                        sql_upd.append("K_CARBODY_CODE = ?, "); //车身白件图号
                        sql_upd.append("K_CARRIAGE = ?, "); //车架
                        sql_upd.append("K_FRONT_AXLE_TWO = ?, "); //前桥二轴
                        sql_upd.append("K_REARAXLE = ?, "); //后桥
                        sql_upd.append("K_TYRE = ?, "); //轮胎
                        sql_upd.append("K_TYRE2 = ?, "); //轮胎2
                        sql_upd.append("K_SUSPENSION = ?, "); //悬置
                        sql_upd.append("K_BALANCE_SUSPENSION = ?, "); //平衡悬挂
                        sql_upd.append("K_ELECTROMOTOR = ?, "); //电动机
                        sql_upd.append("K_CONTROLLER_PART = ?, "); //整车控制器图号
                        sql_upd.append("K_SWITCHING_PROJECT = ?, "); //切换项目
                        sql_upd.append("K_STAMP_ID = ?, "); //焊装钢码号
                        sql_upd.append("K_ASSEMBLY_LINE = ?, "); //装配线
                        sql_upd.append("K_SOURCE_ASSEMBLY_LINE = ?, "); //原始装配线
                        sql_upd.append("K_FRONT_AXLE_ONE = ?, "); //前桥一轴
                        sql_upd.append("K_SCHEME_CODE = ?, "); //方案号
                        sql_upd.append("K_MID_AXLE = ?, "); //中桥
                        sql_upd.append("K_FLOAT_AXLE = ?, "); //浮桥
                        sql_upd.append("K_CARRIAGE_IS_JET_POWDER = ?, "); //车架是否喷粉（0=否；1=是）
                        sql_upd.append("K_COLOR_NAME = ?, "); //颜色名称
                        sql_upd.append("K_BATTER_PART = ?, "); //电池包图号
                        sql_upd.append("K_IS_TRAM = ?, "); //是否电车（0=否；1=是）
                        sql_upd.append("K_BUYER = ?, "); //订货单位
                        sql_upd.append("K_TRIAL_ASSEMBLY = ?, "); //试装项目
                        sql_upd.append("K_CATEGORY = ?, "); //类别
                        sql_upd.append("K_NOTICE_CARTYPE = ?, "); //公告车型
                        sql_upd.append("K_OPTIONAL_PACKAGE_1 = ?, "); //选装号1
                        sql_upd.append("K_OPTIONAL_PACKAGE_2 = ?, "); //选装号2
                        sql_upd.append("K_OPTIONAL_PACKAGE_3 = ?, "); //选装号3
                        sql_upd.append("K_OPTIONAL_PACKAGE_4 = ?, "); //选装号4
                        sql_upd.append("K_OPTIONAL_PACKAGE_5 = ?, "); //选装号5
                        sql_upd.append("K_OPTIONAL_PACKAGE_6 = ?, "); //选装号6
                        sql_upd.append("K_OPTIONAL_PACKAGE_7 = ?, "); //选装号7
                        sql_upd.append("K_OPTIONAL_PACKAGE_8 = ?, "); //选装号8
                        sql_upd.append("K_OPTIONAL_PACKAGE_9 = ?, "); //选装号9
                        sql_upd.append("K_OPTIONAL_PACKAGE_10 = ?, "); //选装号10
                        sql_upd.append("K_OPTIONAL_PACKAGE_11 = ?, "); //选装号11
                        sql_upd.append("K_OPTIONAL_PACKAGE_12 = ?, "); //选装号12
                        sql_upd.append("K_OPTIONAL_PACKAGE_13 = ?, "); //选装号13
                        sql_upd.append("K_OPTIONAL_PACKAGE_14 = ?, "); //选装号14
                        sql_upd.append("K_OPTIONAL_PACKAGE_15 = ?, "); //选装号15
                        sql_upd.append("K_MUST_PACKAGE_1 = ?, "); //必装号1
                        sql_upd.append("K_MUST_PACKAGE_2 = ?, "); //必装号2
                        sql_upd.append("K_MUST_PACKAGE_3 = ?, "); //必装号3
                        sql_upd.append("K_MUST_PACKAGE_4 = ?, "); //必装号4
                        sql_upd.append("K_MUST_PACKAGE_5 = ?, "); //必装号5
                        sql_upd.append("K_MUST_PACKAGE_6 = ?, "); //必装号6
                        sql_upd.append("K_MUST_PACKAGE_7 = ?, "); //必装号7
                        sql_upd.append("K_MUST_PACKAGE_8 = ?, "); //必装号8
                        sql_upd.append("K_MUST_PACKAGE_9 = ?, "); //必装号9
                        sql_upd.append("K_MUST_PACKAGE_10 = ?, "); //必装号10
                        sql_upd.append("K_CARID = ?, "); //CARID
                        sql_upd.append("K_SHOTCRETE_MSG = ?, "); //喷字信息
                        sql_upd.append("TWO_TYPE = ?, "); //二类/整车
                        sql_upd.append("D7X = ?, "); //D+7计划顺序
                        sql_upd.append("D3X = ?, "); //D+3计划顺序
                        sql_upd.append("D7_DATE = to_date(?,'yyyy-mm-dd'), "); //D+7计划日期
                        sql_upd.append("CERTIFICATE_REMARK = ?, "); //合格证备注
                        sql_upd.append("PRODUCT_TYPE = ?, "); //产品类型
                        sql_upd.append("K_PLAN_TYPE = ?, "); //计划类型 (1=正常车，2=单独车身,3=试制车身)
                        sql_upd.append("K_VISUAL_PRODUCTION_CODE = ?, "); //三包车目视卡参考的生产编码
                        sql_upd.append("K_IS_FOREIGN_CARBODY = ?, "); //外来车身标记
                        sql_upd.append("K_BUMPER_COLOR = ? "); //保险杠颜色
                        sql_upd.append("where PRODUCTION_CODE = ? and DEMAND_PRODUCT_TYPE = '0'"); //根据生产编码进行更新

                        Db.update(sql_upd.toString(),
                                sub.getStr("DEMAND_PRODUCT_CODE"), //需求产品编码
                                sub.getStr("DEMAND_DATE"), //要货日期
                                sub.getStr("BOM_CODE"), //BOM编码
                                sub.getStr("DEMAND_PRODUCT_REMARK"), //需求产品备注
                                sub.getStr("K_ENGINE"), //发动机
                                sub.getStr("K_GEARBOX"), //变速箱
                                sub.getStr("K_CARTYPE"), //车身图号
                                sub.getStr("K_CARBODY_CODE"), //车身白件图号
                                sub.getStr("K_CARRIAGE"), //车架
                                sub.getStr("K_FRONT_AXLE_TWO"), //前桥二轴
                                sub.getStr("K_REARAXLE"), //后桥
                                sub.getStr("K_TYRE"), //轮胎
                                sub.getStr("K_TYRE2"), //轮胎2
                                sub.getStr("K_SUSPENSION"), //悬置
                                sub.getStr("K_BALANCE_SUSPENSION"), //平衡悬挂
                                sub.getStr("K_ELECTROMOTOR"), //电动机
                                sub.getStr("K_CONTROLLER_PART"), //整车控制器图号
                                sub.getStr("K_SWITCHING_PROJECT"), //切换项目
                                sub.getStr("K_STAMP_ID"), //焊装钢码号
                                sub.getStr("K_ASSEMBLY_LINE"), //装配线
                                sub.getStr("K_SOURCE_ASSEMBLY_LINE"), //原始装配线
                                sub.getStr("K_FRONT_AXLE_ONE"), //前桥一轴
                                sub.getStr("K_SCHEME_CODE"), //方案号
                                sub.getStr("K_MID_AXLE"), //中桥
                                sub.getStr("K_FLOAT_AXLE"), //浮桥
                                sub.getStr("K_CARRIAGE_IS_JET_POWDER"), //车架是否喷粉（0=否；1=是）
                                sub.getStr("K_COLOR_NAME"), //颜色名称
                                sub.getStr("K_BATTER_PART"), //电池包图号
                                sub.getStr("K_IS_TRAM"), //是否电车（0=否；1=是）
                                sub.getStr("K_BUYER"), //订货单位
                                sub.getStr("K_TRIAL_ASSEMBLY"), //试装项目
                                sub.getStr("K_CATEGORY"), //类别
                                sub.getStr("K_NOTICE_CARTYPE"), //公告车型
                                sub.getStr("K_OPTIONAL_PACKAGE_1"), //选装号1
                                sub.getStr("K_OPTIONAL_PACKAGE_2"), //选装号2
                                sub.getStr("K_OPTIONAL_PACKAGE_3"), //选装号3
                                sub.getStr("K_OPTIONAL_PACKAGE_4"), //选装号4
                                sub.getStr("K_OPTIONAL_PACKAGE_5"), //选装号5
                                sub.getStr("K_OPTIONAL_PACKAGE_6"), //选装号6
                                sub.getStr("K_OPTIONAL_PACKAGE_7"), //选装号7
                                sub.getStr("K_OPTIONAL_PACKAGE_8"), //选装号8
                                sub.getStr("K_OPTIONAL_PACKAGE_9"), //选装号9
                                sub.getStr("K_OPTIONAL_PACKAGE_10"), //选装号10
                                sub.getStr("K_OPTIONAL_PACKAGE_11"), //选装号11
                                sub.getStr("K_OPTIONAL_PACKAGE_12"), //选装号12
                                sub.getStr("K_OPTIONAL_PACKAGE_13"), //选装号13
                                sub.getStr("K_OPTIONAL_PACKAGE_14"), //选装号14
                                sub.getStr("K_OPTIONAL_PACKAGE_15"), //选装号15
                                sub.getStr("K_MUST_PACKAGE_1"), //必装号1
                                sub.getStr("K_MUST_PACKAGE_2"), //必装号2
                                sub.getStr("K_MUST_PACKAGE_3"), //必装号3
                                sub.getStr("K_MUST_PACKAGE_4"), //必装号4
                                sub.getStr("K_MUST_PACKAGE_5"), //必装号5
                                sub.getStr("K_MUST_PACKAGE_6"), //必装号6
                                sub.getStr("K_MUST_PACKAGE_7"), //必装号7
                                sub.getStr("K_MUST_PACKAGE_8"), //必装号8
                                sub.getStr("K_MUST_PACKAGE_9"), //必装号9
                                sub.getStr("K_MUST_PACKAGE_10"), //必装号10
                                sub.getStr("K_CARID"), //CARID
                                sub.getStr("K_SHOTCRETE_MSG"), //喷字信息
                                sub.getStr("TWO_TYPE"), //二类/整车
                                sub.getStr("D7X"), //D+7计划顺序
                                sub.getStr("D3X"), //D+3计划顺序
                                sub.getStr("D7_DATE"), //D+7计划日期
                                sub.getStr("CERTIFICATE_REMARK"), //合格证备注
                                sub.getStr("PRODUCT_TYPE"), //产品类型
                                sub.getStr("K_PLAN_TYPE"), //计划类型 (1=正常车，2=单独车身,3=试制车身)
                                sub.getStr("K_VISUAL_PRODUCTION_CODE"), //三包车目视卡参考的生产编码
                                sub.getStr("K_IS_FOREIGN_CARBODY"), //外来车身标记
                                sub.getStr("K_BUMPER_COLOR"), //保险杠颜色
                                sub.getStr("PRODUCTION_CODE") //生产编码
                        );
                    } else {
                        // 车辆不存在，插入
                        StringBuffer sql_add = new StringBuffer();
                        sql_add.append("insert into t_plan_demand_product(");
                        sql_add.append("ID, "); //ID
                        sql_add.append("PRODUCTION_CODE, "); //生产编码
                        sql_add.append("DEMAND_SOURCE, "); //需求来源（0：直接需求；1：分解需求）
                        sql_add.append("SCHEDULING_STATUS, "); //排产状态（0=初始；1=已处理）
                        sql_add.append("DEMAND_PRODUCT_CODE, "); //需求产品编码
                        sql_add.append("DEMAND_DATE, "); //要货日期
                        sql_add.append("DEMAND_NUM, "); //需求数量
                        sql_add.append("BOM_CODE, "); //BOM编码
                        sql_add.append("IS_DISASSEMBLY, "); //是否解体
                        sql_add.append("DEMAND_PRODUCT_REMARK, "); //需求产品备注
                        sql_add.append("K_ENGINE, "); //发动机
                        sql_add.append("K_GEARBOX, "); //变速箱
                        sql_add.append("K_CARTYPE, "); //车身图号
                        sql_add.append("K_CARBODY_CODE, "); //车身白件图号
                        sql_add.append("K_CARRIAGE, "); //车架
                        sql_add.append("K_FRONT_AXLE_TWO, "); //前桥二轴
                        sql_add.append("K_REARAXLE, "); //后桥
                        sql_add.append("K_TYRE, "); //轮胎
                        sql_add.append("K_TYRE2, "); //轮胎2
                        sql_add.append("K_SUSPENSION, "); //悬置
                        sql_add.append("K_BALANCE_SUSPENSION, "); //平衡悬挂
                        sql_add.append("K_ELECTROMOTOR, "); //电动机
                        sql_add.append("K_CONTROLLER_PART, "); //整车控制器图号
                        sql_add.append("K_SWITCHING_PROJECT, "); //切换项目
                        sql_add.append("K_IS_PBS_DISABLE_CAR, "); //是否PBS禁止车（0：否；1：是）
                        sql_add.append("K_IS_PBS_RETURN_CAR, "); //是否PBS回线车（0：否；1：是）
                        sql_add.append("K_IS_WBS_DISABLE_CAR, "); //是否WBS禁止车（0：否；1：是）
                        sql_add.append("K_IS_WBS_RETURN_CAR, "); //是否WBS回线车（0：否；1：是）
                        sql_add.append("K_STAMP_ID, "); //焊装白车身重保号
                        sql_add.append("K_ASSEMBLY_LINE, "); //装配线
                        sql_add.append("K_SOURCE_ASSEMBLY_LINE, "); //原始装配线
                        sql_add.append("K_IS_WBS_REVIEW_CAR, "); //是否WBS评审车
                        sql_add.append("K_IS_WBS_REPAIR_CAR, "); //是否WBS返修车
                        sql_add.append("K_IS_WBS_DIRECT_CAR, "); //是否WBS直通车
                        sql_add.append("K_IS_PBS_BACK_CAR, "); //是否PBS返回车
                        sql_add.append("K_PAINT_CARTYPE_MARK, "); //涂装车辆类型标记（0：量产车；1：营销车；2：试制车；3：底漆小件；4：面漆小件；5：吸尘车；6：其它车；7：底漆滑撬；8：面漆滑撬；）
                        sql_add.append("K_IS_WBS_BACK_CAR, "); //是否WBS返回车（0=否；1=是）
                        sql_add.append("K_IS_WBS_TEMP_POP, "); //是否WBS临时上件弹出（0=否；1=是）
                        sql_add.append("K_FRONT_AXLE_ONE, "); //前桥一轴
                        sql_add.append("DEMAND_PRODUCT_TYPE, "); //需求产品类型（0=整车；1=车架；2=焊装左车门；3=焊装右车门；4=焊装前面板；5=车身；6=营销小件；7=异常库存）
                        sql_add.append("K_SCHEME_CODE, "); //方案号
                        sql_add.append("K_MID_AXLE, "); //中桥
                        sql_add.append("K_FLOAT_AXLE, "); //浮桥
                        sql_add.append("K_CARRIAGE_IS_JET_POWDER, "); //车架是否喷粉（0=否；1=是）
                        sql_add.append("K_IS_CARRIAGE_OUTSTOCK, "); //是否车架已出库（0=否；1=是）
                        sql_add.append("K_COLOR_NAME, "); //颜色名称
                        sql_add.append("K_PAINT_LINE, "); //涂装线
                        sql_add.append("K_BATTER_PART, "); //电池包图号
                        sql_add.append("K_IS_TRAM, "); //是否电车（0=否；1=是）
                        sql_add.append("K_BUYER, "); //订货单位
                        sql_add.append("K_TRIAL_ASSEMBLY, "); //试装项目
                        sql_add.append("K_CATEGORY, "); //类型
                        sql_add.append("K_NOTICE_CARTYPE, "); //公告车型
                        sql_add.append("K_OPTIONAL_PACKAGE_1, "); //选装号1
                        sql_add.append("K_OPTIONAL_PACKAGE_2, "); //选装号2
                        sql_add.append("K_OPTIONAL_PACKAGE_3, "); //选装号3
                        sql_add.append("K_OPTIONAL_PACKAGE_4, "); //选装号4
                        sql_add.append("K_OPTIONAL_PACKAGE_5, "); //选装号5
                        sql_add.append("K_OPTIONAL_PACKAGE_6, "); //选装号6
                        sql_add.append("K_OPTIONAL_PACKAGE_7, "); //选装号7
                        sql_add.append("K_OPTIONAL_PACKAGE_8, "); //选装号8
                        sql_add.append("K_OPTIONAL_PACKAGE_9, "); //选装号9
                        sql_add.append("K_OPTIONAL_PACKAGE_10, "); //选装号10
                        sql_add.append("K_OPTIONAL_PACKAGE_11, "); //选装号11
                        sql_add.append("K_OPTIONAL_PACKAGE_12, "); //选装号12
                        sql_add.append("K_OPTIONAL_PACKAGE_13, "); //选装号13
                        sql_add.append("K_OPTIONAL_PACKAGE_14, "); //选装号14
                        sql_add.append("K_OPTIONAL_PACKAGE_15, "); //选装号15
                        sql_add.append("K_MUST_PACKAGE_1, "); //必装号1
                        sql_add.append("K_MUST_PACKAGE_2, "); //必装号2
                        sql_add.append("K_MUST_PACKAGE_3, "); //必装号3
                        sql_add.append("K_MUST_PACKAGE_4, "); //必装号4
                        sql_add.append("K_MUST_PACKAGE_5, "); //必装号5
                        sql_add.append("K_MUST_PACKAGE_6, "); //必装号6
                        sql_add.append("K_MUST_PACKAGE_7, "); //必装号7
                        sql_add.append("K_MUST_PACKAGE_8, "); //必装号8
                        sql_add.append("K_MUST_PACKAGE_9, "); //必装号9
                        sql_add.append("K_MUST_PACKAGE_10, "); //必装号10
                        sql_add.append("K_CARID, "); //CARID
                        sql_add.append("K_SHOTCRETE_MSG, "); //喷字信息
                        sql_add.append("TWO_TYPE, "); //二类/整车
                        sql_add.append("D7X, "); //D+7计划顺序
                        sql_add.append("D3X, "); //D+3计划顺序
                        sql_add.append("D7_DATE, "); //D+7计划日期
                        sql_add.append("CERTIFICATE_REMARK, "); //合格证备注
                        sql_add.append("PRODUCT_TYPE, "); //产品类型
                        sql_add.append("K_PLAN_TYPE, "); //计划类型 (1=正常车，2=单独车身,3=试制车身)
                        sql_add.append("K_VISUAL_PRODUCTION_CODE, "); //三包车目视卡参考的生产编码
                        sql_add.append("K_IS_FOREIGN_CARBODY, "); //外来车身标记
                        sql_add.append("K_BUMPER_COLOR"); //保险杠颜色
                        sql_add.append(") values(");
                        sql_add.append("SYS_GUID(), "); //ID
                        sql_add.append("?, "); //生产编码
                        sql_add.append("'0', "); //需求来源（0：直接需求；1：分解需求）
                        sql_add.append("'0', "); //排产状态（0=初始；1=已处理）
                        sql_add.append("?, "); //需求产品编码
                        sql_add.append("to_date(?,'yyyy-mm-dd'), "); //要货日期
                        sql_add.append("1, "); //需求数量
                        sql_add.append("?, "); //BOM编码
                        sql_add.append("'0', "); //是否解体
                        sql_add.append("?, "); //需求产品备注
                        sql_add.append("?, "); //发动机
                        sql_add.append("?, "); //变速箱
                        sql_add.append("?, "); //车身图号
                        sql_add.append("?, "); //车身白件图号
                        sql_add.append("?, "); //车架
                        sql_add.append("?, "); //前桥二轴
                        sql_add.append("?, "); //后桥
                        sql_add.append("?, "); //轮胎
                        sql_add.append("?, "); //轮胎2
                        sql_add.append("?, "); //悬置
                        sql_add.append("?, "); //平衡悬挂
                        sql_add.append("?, "); //电动机
                        sql_add.append("?, "); //整车控制器图号
                        sql_add.append("?, "); //切换项目
                        sql_add.append("'0', "); //是否PBS禁止车（0：否；1：是）
                        sql_add.append("'0', "); //是否PBS回线车（0：否；1：是）
                        sql_add.append("'0', "); //是否WBS禁止车（0：否；1：是）
                        sql_add.append("'0', "); //是否WBS回线车（0：否；1：是）
                        sql_add.append("?, "); //焊装白车身重保号
                        sql_add.append("?, "); //装配线
                        sql_add.append("?, "); //原始装配线
                        sql_add.append("'0', "); //是否WBS评审车
                        sql_add.append("'0', "); //是否WBS返修车
                        sql_add.append("'0', "); //是否WBS直通车
                        sql_add.append("'0', "); //是否PBS返回车
                        sql_add.append("'0', "); //涂装车辆类型标记（0：量产车；1：营销车；2：试制车；3：底漆小件；4：面漆小件；5：吸尘车；6：其它车；7：底漆滑撬；8：面漆滑撬；）
                        sql_add.append("'0', "); //是否WBS返回车（0=否；1=是）
                        sql_add.append("'0', "); //是否WBS临时上件弹出（0=否；1=是）
                        sql_add.append("?, "); //前桥一轴
                        sql_add.append("'0', "); //需求产品类型（0=整车；1=车架；2=焊装左车门；3=焊装右车门；4=焊装前面板；5=车身；6=营销小件；7=异常库存）
                        sql_add.append("?, "); //方案号
                        sql_add.append("?, "); //中桥
                        sql_add.append("?, "); //浮桥
                        sql_add.append("?, "); //车架是否喷粉（0=否；1=是）
                        sql_add.append("'0', "); //是否车架已出库（0=否；1=是）
                        sql_add.append("?, "); //颜色名称
                        sql_add.append("'1', "); //涂装线
                        sql_add.append("?, "); //电池包图号
                        sql_add.append("?, "); //是否电车（0=否；1=是）
                        sql_add.append("?, "); //订货单位
                        sql_add.append("?, "); //试装项目
                        sql_add.append("?, "); //类别
                        sql_add.append("?, "); //公告车型
                        sql_add.append("?, "); //选装号1
                        sql_add.append("?, "); //选装号2
                        sql_add.append("?, "); //选装号3
                        sql_add.append("?, "); //选装号4
                        sql_add.append("?, "); //选装号5
                        sql_add.append("?, "); //选装号6
                        sql_add.append("?, "); //选装号7
                        sql_add.append("?, "); //选装号8
                        sql_add.append("?, "); //选装号9
                        sql_add.append("?, "); //选装号10
                        sql_add.append("?, "); //选装号11
                        sql_add.append("?, "); //选装号12
                        sql_add.append("?, "); //选装号13
                        sql_add.append("?, "); //选装号14
                        sql_add.append("?, "); //选装号15
                        sql_add.append("?, "); //必装号1
                        sql_add.append("?, "); //必装号2
                        sql_add.append("?, "); //必装号3
                        sql_add.append("?, "); //必装号4
                        sql_add.append("?, "); //必装号5
                        sql_add.append("?, "); //必装号6
                        sql_add.append("?, "); //必装号7
                        sql_add.append("?, "); //必装号8
                        sql_add.append("?, "); //必装号9
                        sql_add.append("?, "); //必装号10
                        sql_add.append("?, "); //CARID
                        sql_add.append("?, "); //喷字信息
                        sql_add.append("?, "); //二类/整车
                        sql_add.append("?, "); //D+7计划顺序
                        sql_add.append("?, "); //D+3计划顺序
                        sql_add.append("to_date(?,'yyyy-mm-dd'), "); //D+7计划日期
                        sql_add.append("?, "); //合格证备注
                        sql_add.append("?, "); //产品类型
                        sql_add.append("?, "); //计划类型 (1=正常车，2=单独车身,3=试制车身)
                        sql_add.append("?, "); //三包车目视卡参考的生产编码
                        sql_add.append("?, "); //外来车身标记
                        sql_add.append("?"); //保险杠颜色
                        sql_add.append(")");

                        Db.update(sql_add.toString(),
                                sub.getStr("PRODUCTION_CODE"), //生产编码
                                sub.getStr("DEMAND_PRODUCT_CODE"), //需求产品编码
                                sub.getStr("DEMAND_DATE"), //要货日期
                                sub.getStr("BOM_CODE"), //BOM编码
                                sub.getStr("DEMAND_PRODUCT_REMARK"), //需求产品备注
                                sub.getStr("K_ENGINE"), //发动机
                                sub.getStr("K_GEARBOX"), //变速箱
                                sub.getStr("K_CARTYPE"), //车身图号
                                sub.getStr("K_CARBODY_CODE"), //车身白件图号
                                sub.getStr("K_CARRIAGE"), //车架
                                sub.getStr("K_FRONT_AXLE_TWO"), //前桥二轴
                                sub.getStr("K_REARAXLE"), //后桥
                                sub.getStr("K_TYRE"), //轮胎
                                sub.getStr("K_TYRE2"), //轮胎2
                                sub.getStr("K_SUSPENSION"), //悬置
                                sub.getStr("K_BALANCE_SUSPENSION"), //平衡悬挂
                                sub.getStr("K_ELECTROMOTOR"), //电动机
                                sub.getStr("K_CONTROLLER_PART"), //整车控制器图号
                                sub.getStr("K_SWITCHING_PROJECT"), //切换项目
                                sub.getStr("K_STAMP_ID"), //焊装钢码号
                                sub.getStr("K_ASSEMBLY_LINE"), //装配线
                                sub.getStr("K_SOURCE_ASSEMBLY_LINE"), //原始装配线
                                sub.getStr("K_FRONT_AXLE_ONE"), //前桥一轴
                                sub.getStr("K_SCHEME_CODE"), //方案号
                                sub.getStr("K_MID_AXLE"), //中桥
                                sub.getStr("K_FLOAT_AXLE"), //浮桥
                                sub.getStr("K_CARRIAGE_IS_JET_POWDER"), //车架是否喷粉（0=否；1=是）
                                sub.getStr("K_COLOR_NAME"), //颜色名称
                                sub.getStr("K_BATTER_PART"), //电池包图号
                                sub.getStr("K_IS_TRAM"), //是否电车（0=否；1=是）
                                sub.getStr("K_BUYER"), //订货单位
                                sub.getStr("K_TRIAL_ASSEMBLY"), //试装项目
                                sub.getStr("K_CATEGORY"), //类别
                                sub.getStr("K_NOTICE_CARTYPE"), //公告车型
                                sub.getStr("K_OPTIONAL_PACKAGE_1"), //选装号1
                                sub.getStr("K_OPTIONAL_PACKAGE_2"), //选装号2
                                sub.getStr("K_OPTIONAL_PACKAGE_3"), //选装号3
                                sub.getStr("K_OPTIONAL_PACKAGE_4"), //选装号4
                                sub.getStr("K_OPTIONAL_PACKAGE_5"), //选装号5
                                sub.getStr("K_OPTIONAL_PACKAGE_6"), //选装号6
                                sub.getStr("K_OPTIONAL_PACKAGE_7"), //选装号7
                                sub.getStr("K_OPTIONAL_PACKAGE_8"), //选装号8
                                sub.getStr("K_OPTIONAL_PACKAGE_9"), //选装号9
                                sub.getStr("K_OPTIONAL_PACKAGE_10"), //选装号10
                                sub.getStr("K_OPTIONAL_PACKAGE_11"), //选装号11
                                sub.getStr("K_OPTIONAL_PACKAGE_12"), //选装号12
                                sub.getStr("K_OPTIONAL_PACKAGE_13"), //选装号13
                                sub.getStr("K_OPTIONAL_PACKAGE_14"), //选装号14
                                sub.getStr("K_OPTIONAL_PACKAGE_15"), //选装号15
                                sub.getStr("K_MUST_PACKAGE_1"), //必装号1
                                sub.getStr("K_MUST_PACKAGE_2"), //必装号2
                                sub.getStr("K_MUST_PACKAGE_3"), //必装号3
                                sub.getStr("K_MUST_PACKAGE_4"), //必装号4
                                sub.getStr("K_MUST_PACKAGE_5"), //必装号5
                                sub.getStr("K_MUST_PACKAGE_6"), //必装号6
                                sub.getStr("K_MUST_PACKAGE_7"), //必装号7
                                sub.getStr("K_MUST_PACKAGE_8"), //必装号8
                                sub.getStr("K_MUST_PACKAGE_9"), //必装号9
                                sub.getStr("K_MUST_PACKAGE_10"), //必装号10
                                sub.getStr("K_CARID"), //CARID
                                sub.getStr("K_SHOTCRETE_MSG"), //喷字信息
                                sub.getStr("TWO_TYPE"), //二类/整车
                                sub.getStr("D7X"), //D+7计划顺序
                                sub.getStr("D3X"), //D+3计划顺序
                                sub.getStr("D7_DATE"), //D+7计划日期
                                sub.getStr("CERTIFICATE_REMARK"), //合格证备注
                                sub.getStr("PRODUCT_TYPE"), //产品类型
                                sub.getStr("K_PLAN_TYPE"), //计划类型 (1=正常车，2=单独车身,3=试制车身)
                                sub.getStr("K_VISUAL_PRODUCTION_CODE"), //三包车目视卡参考的生产编码
                                sub.getStr("K_IS_FOREIGN_CARBODY"), //外来车身标记
                                sub.getStr("K_BUMPER_COLOR") //保险杠颜色
                        );
                    }
                }

                // 处理成功
                Db.update("update T_SYS_SERVICE set SERVICE_PARA1_VALUE=? where SERVICE_CODE=?", rec_audit.getStr("ZPRQ"), strServiceCode);
                msg = String.format("获取日期【%s】D+7车辆信息成功", rec_audit.getStr("ZPRQ"));

                for (Record sub : list) {
                    dmsg = String.format("D+7车辆信息成功，获取日期【%s】，生产编码【%s】，需求产品编码【%s】，要货日期【%s】，BOM编码【%s】，需求产品备注【%s】，发动机【%s】" +
                                    "，变速箱【%s】，车身图号【%s】，车身白件图号【%s】，车架【%s】，前桥二轴【%s】，后桥【%s】，轮胎【%s】，轮胎2【%s】，悬置【%s】，平衡悬挂【%s】" +
                                    "，电动机【%s】，整车控制器图号【%s】，切换项目【%s】，焊装钢码号【%s】，装配线【%s】，原始装配线【%s】，前桥一轴【%s】，方案号【%s】" +
                                    "，中桥【%s】，浮桥【%s】，车架是否喷粉【%s】，颜色名称【%s】，电池包图号【%s】，是否电车【%s】，订货单位【%s】，试装项目【%s】" +
                                    "，类别【%s】，公告车型【%s】，选装号1【%s】，选装号2【%s】，选装号3【%s】，选装号4【%s】，选装号5【%s】，选装号6【%s】" +
                                    "，选装号7【%s】，选装号8【%s】，选装号9【%s】，选装号10【%s】，选装号11【%s】，选装号12【%s】，选装号13【%s】，选装号14【%s】" +
                                    "，选装号15【%s】，必装号1【%s】，必装号2【%s】，必装号3【%s】，必装号4【%s】，必装号5【%s】，必装号6【%s】，必装号7【%s】" +
                                    "，必装号8【%s】，必装号9【%s】，必装号10【%s】，CARID【%s】，喷字信息【%s】，二类/整车【%s】，D+7计划顺序【%s】，D+3计划顺序【%s】" +
                                    "，D+7计划日期【%s】，合格证备注【%s】，产品类型【%s】，计划类型【%s】，三包车目视卡参考的生产编码【%s】，外来车身标记【%s】，保险杠颜色【%s】",
                            rec_audit.getStr("ZPRQ"), sub.getStr("PRODUCTION_CODE"), sub.getStr("DEMAND_PRODUCT_CODE"), sub.getStr("DEMAND_DATE"),
                            sub.getStr("BOM_CODE"), sub.getStr("DEMAND_PRODUCT_REMARK"), sub.getStr("K_ENGINE"), sub.getStr("K_GEARBOX"), sub.getStr("K_CARTYPE"),
                            sub.getStr("K_CARBODY_CODE"), sub.getStr("K_CARRIAGE"), sub.getStr("K_FRONT_AXLE_TWO"), sub.getStr("K_REARAXLE"),
                            sub.getStr("K_TYRE"), sub.getStr("K_TYRE2"), sub.getStr("K_SUSPENSION"), sub.getStr("K_BALANCE_SUSPENSION"),
                            sub.getStr("K_ELECTROMOTOR"), sub.getStr("K_CONTROLLER_PART"), sub.getStr("K_SWITCHING_PROJECT"), sub.getStr("K_STAMP_ID"),
                            sub.getStr("K_ASSEMBLY_LINE"), sub.getStr("K_SOURCE_ASSEMBLY_LINE"), sub.getStr("K_FRONT_AXLE_ONE"), sub.getStr("K_SCHEME_CODE"),
                            sub.getStr("K_MID_AXLE"), sub.getStr("K_FLOAT_AXLE"), sub.getStr("K_CARRIAGE_IS_JET_POWDER"), sub.getStr("K_COLOR_NAME"),
                            sub.getStr("K_BATTER_PART"), sub.getStr("K_IS_TRAM"), sub.getStr("K_BUYER"), sub.getStr("K_TRIAL_ASSEMBLY"),
                            sub.getStr("K_CATEGORY"), sub.getStr("K_NOTICE_CARTYPE"), sub.getStr("K_OPTIONAL_PACKAGE_1"), sub.getStr("K_OPTIONAL_PACKAGE_2"),
                            sub.getStr("K_OPTIONAL_PACKAGE_3"), sub.getStr("K_OPTIONAL_PACKAGE_4"), sub.getStr("K_OPTIONAL_PACKAGE_5"), sub.getStr("K_OPTIONAL_PACKAGE_6"),
                            sub.getStr("K_OPTIONAL_PACKAGE_7"), sub.getStr("K_OPTIONAL_PACKAGE_8"), sub.getStr("K_OPTIONAL_PACKAGE_9"), sub.getStr("K_OPTIONAL_PACKAGE_10"),
                            sub.getStr("K_OPTIONAL_PACKAGE_11"), sub.getStr("K_OPTIONAL_PACKAGE_12"), sub.getStr("K_OPTIONAL_PACKAGE_13"), sub.getStr("K_OPTIONAL_PACKAGE_14"),
                            sub.getStr("K_OPTIONAL_PACKAGE_15"), sub.getStr("K_MUST_PACKAGE_1"), sub.getStr("K_MUST_PACKAGE_2"), sub.getStr("K_MUST_PACKAGE_3"),
                            sub.getStr("K_MUST_PACKAGE_4"), sub.getStr("K_MUST_PACKAGE_5"), sub.getStr("K_MUST_PACKAGE_6"), sub.getStr("K_MUST_PACKAGE_7"),
                            sub.getStr("K_MUST_PACKAGE_8"), sub.getStr("K_MUST_PACKAGE_9"), sub.getStr("K_MUST_PACKAGE_10"), sub.getStr("K_CARID"),
                            sub.getStr("K_SHOTCRETE_MSG"), sub.getStr("TWO_TYPE"), sub.getStr("D7X"), sub.getStr("D3X"), sub.getStr("D7_DATE"),
                            sub.getStr("CERTIFICATE_REMARK"), sub.getStr("PRODUCT_TYPE"), sub.getStr("K_PLAN_TYPE"), sub.getStr("K_VISUAL_PRODUCTION_CODE"),
                            sub.getStr("K_IS_FOREIGN_CARBODY"), sub.getStr("K_BUMPER_COLOR"));

                    Log.Write(strServiceCode, LogLevel.Information, dmsg);
                }
            }
        }

        return msg;
    }
}

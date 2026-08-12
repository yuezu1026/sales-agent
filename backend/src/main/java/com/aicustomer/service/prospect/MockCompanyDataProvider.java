package com.aicustomer.service.prospect;

import com.aicustomer.dto.ProspectCompany;
import com.aicustomer.dto.ProspectQuery;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 内置演示数据源（data_source.type = mock）
 * 无外部 API Key 也能验证"挖掘 → 筛选 → 入库"全链路（开发/演示/E2E）。
 * 企业数据为虚构示例，仅作演示。
 */
@Component
public class MockCompanyDataProvider implements CompanyDataProvider {

    @Override
    public String type() {
        return "mock";
    }

    @Override
    public List<ProspectCompany> search(ProspectQuery query, int limit) {
        List<ProspectCompany> result = new ArrayList<>();
        for (ProspectCompany c : DEMO_COMPANIES) {
            if (matches(c, query)) {
                result.add(c);
                if (result.size() >= limit) {
                    break;
                }
            }
        }
        return result;
    }

    private boolean matches(ProspectCompany c, ProspectQuery query) {
        if (query == null || !query.hasCondition()) {
            return true;
        }
        if (isNotBlank(query.industry()) && !containsIgnoreCase(c.industry(), query.industry())) {
            return false;
        }
        if (isNotBlank(query.region()) && !containsIgnoreCase(c.region(), query.region())) {
            return false;
        }
        if (isNotBlank(query.scale()) && !containsIgnoreCase(c.scale(), query.scale())) {
            return false;
        }
        if (isNotBlank(query.keyword())
                && !containsIgnoreCase(c.companyName(), query.keyword())
                && !containsIgnoreCase(c.industry(), query.keyword())) {
            return false;
        }
        return true;
    }

    private static boolean containsIgnoreCase(String text, String keyword) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(keyword.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }

    // ==================== 演示数据（虚构） ====================
    private static final List<ProspectCompany> DEMO_COMPANIES = List.of(
            company("云启软件", "陈启明", "chen.qm@yunqi-soft.cn", "13910001001",
                    "SaaS", "深圳", "50-200人", "https://yunqi-soft.cn", "深圳市南山区科技园南区", "mock:1001"),
            company("数澜科技", "林晓岚", "lin.xl@shulan-tech.cn", "13910001002",
                    "SaaS", "深圳", "200-500人", "https://shulan-tech.cn", "深圳市福田区车公庙", "mock:1002"),
            company("智云互联", "赵子昂", "zhao.za@zhiyun-iot.cn", "13910001003",
                    "SaaS", "杭州", "50-200人", "https://zhiyun-iot.cn", "杭州市余杭区未来科技城", "mock:1003"),
            company("极数科技", "孙悦", "sun.yue@jishu-ai.cn", "13910001004",
                    "SaaS", "上海", "500-1000人", "https://jishu-ai.cn", "上海市浦东新区张江高科", "mock:1004"),
            company("云帆数据", "周明远", "zhou.my@yunfan-data.cn", "13910001005",
                    "SaaS", "北京", "200-500人", "https://yunfan-data.cn", "北京市海淀区中关村", "mock:1005"),
            company("华信金服", "吴丽华", "wu.lh@huaxin-fin.cn", "13910001006",
                    "金融科技", "上海", "1000人以上", "https://huaxin-fin.cn", "上海市黄浦区外滩金融中心", "mock:1006"),
            company("银桥科技", "郑海涛", "zheng.ht@yinqiao-tech.cn", "13910001007",
                    "金融科技", "深圳", "200-500人", "https://yinqiao-tech.cn", "深圳市前海深港合作区", "mock:1007"),
            company("恒锐精密", "黄志强", "huang.zq@hengrui-precision.cn", "13910001008",
                    "智能制造", "东莞", "500-1000人", "https://hengrui-precision.cn", "东莞市松山湖高新区", "mock:1008"),
            company("精工智造", "徐文博", "xu.wb@jinggong-mfg.cn", "13910001009",
                    "智能制造", "苏州", "200-500人", "https://jinggong-mfg.cn", "苏州市工业园区", "mock:1009"),
            company("新锐电商", "马晓峰", "ma.xf@xinrui-ec.cn", "13910001010",
                    "电子商务", "杭州", "50-200人", "https://xinrui-ec.cn", "杭州市滨江区网商路", "mock:1010"),
            company("粤商优选", "罗家豪", "luo.jh@yueshang-opt.cn", "13910001011",
                    "电子商务", "广州", "200-500人", "https://yueshang-opt.cn", "广州市天河区体育西路", "mock:1011"),
            company("博雅教育", "何雅婷", "he.yt@boya-edu.cn", "13910001012",
                    "教育培训", "北京", "200-500人", "https://boya-edu.cn", "北京市朝阳区望京", "mock:1012"),
            company("蓉城云教", "唐一鸣", "tang.ym@rongcheng-edu.cn", "13910001013",
                    "教育培训", "成都", "50-200人", "https://rongcheng-edu.cn", "成都市高新区天府软件园", "mock:1013"),
            company("康源医疗", "石磊", "shi.lei@kangyuan-med.cn", "13910001014",
                    "医疗健康", "上海", "500-1000人", "https://kangyuan-med.cn", "上海市闵行区漕河泾开发区", "mock:1014"),
            company("华康生物", "高慧", "gao.hui@huakang-bio.cn", "13910001015",
                    "医疗健康", "广州", "200-500人", "https://huakang-bio.cn", "广州市黄埔区科学城", "mock:1015")
    );

    private static ProspectCompany company(String name, String contact, String email, String phone,
                                           String industry, String region, String scale,
                                           String website, String address, String sourceId) {
        return new ProspectCompany(name, contact, email, phone, industry, region, scale,
                website, address, "mock", sourceId, false);
    }
}

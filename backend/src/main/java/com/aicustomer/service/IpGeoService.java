package com.aicustomer.service;

import jakarta.annotation.PostConstruct;
import org.lionsoul.ip2region.xdb.LongByteArray;
import org.lionsoul.ip2region.xdb.Searcher;
import org.lionsoul.ip2region.xdb.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * M7.13：离线 IP 地理定位（ip2region.xdb 全内存查询，零外部依赖）。
 * <p>
 * 启动时把 classpath:ip2region.xdb（约 11MB）整体载入内存，单次查询微秒级。
 * 返回格式：国家|省|市|ISP|国家码（如 中国|江苏省|南京市|0|CN）。
 */
@Service
public class IpGeoService {

    private static final Logger log = LoggerFactory.getLogger(IpGeoService.class);

    private Searcher searcher;

    @PostConstruct
    void init() {
        try (InputStream in = new ClassPathResource("ip2region.xdb").getInputStream()) {
            LongByteArray cbuf = Searcher.loadContentFromInputStream(in);
            searcher = Searcher.newWithBuffer(Version.IPv4, cbuf);
            log.info("ip2region.xdb 已载入内存（离线 IP 定位就绪）");
        } catch (Exception e) {
            // 加载失败不阻断启动，登录时 geo 记 null
            log.error("ip2region.xdb 加载失败，地理定位功能不可用", e);
        }
    }

    /**
     * 解析 IP 归属。内网 / 回环 / 非法 IP 返回 null。
     *
     * @return 原始串 国家|省|市|ISP|国家码，失败返回 null
     */
    public String resolve(String ip) {
        if (searcher == null || ip == null || ip.isBlank()) {
            return null;
        }
        if (isPrivate(ip)) {
            return null;
        }
        try {
            return searcher.search(ip.trim());
        } catch (Exception e) {
            log.debug("IP 定位失败 ip={}: {}", ip, e.getMessage());
            return null;
        }
    }

    /** 内网 / 回环 / 链路本地地址不做定位 */
    private boolean isPrivate(String ip) {
        if (ip.startsWith("127.") || ip.startsWith("10.") || ip.startsWith("192.168.")
                || ip.startsWith("169.254.") || ip.equals("::1") || ip.equals("localhost")) {
            return true;
        }
        // 172.16.0.0 - 172.31.255.255
        if (ip.startsWith("172.")) {
            try {
                int second = Integer.parseInt(ip.split("\\.")[1]);
                if (second >= 16 && second <= 31) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    /** 从 国家|省|市|ISP|国家码 中提取省（第 2 段），无则 null */
    public static String provinceOf(String geo) {
        return segmentOf(geo, 1);
    }

    /** 从 国家|省|市|ISP|国家码 中提取市（第 3 段），无则 null */
    public static String cityOf(String geo) {
        return segmentOf(geo, 2);
    }

    private static String segmentOf(String geo, int idx) {
        if (geo == null) {
            return null;
        }
        String[] parts = geo.split("\\|");
        if (parts.length <= idx) {
            return null;
        }
        String v = parts[idx].trim();
        return (v.isEmpty() || "0".equals(v)) ? null : v;
    }
}

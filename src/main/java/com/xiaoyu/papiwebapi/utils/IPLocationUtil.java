package com.xiaoyu.papiwebapi.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * IP地理位置解析工具
 * 使用缓存减少API调用
 */
public class IPLocationUtil {

    // 缓存IP查询结果，避免重复查询
    private static final Map<String, CachedLocation> locationCache = new HashMap<>();
    // 缓存过期时间（小时）
    private static final long CACHE_EXPIRY_HOURS = 24;

    /**
     * 获取IP地址对应的地理位置
     *
     * @param ip IP地址
     * @return 地理位置信息
     */
    public static String getIPLocation(String ip) {
        // 检查是否为本地网络IP
        if (isLocalIP(ip)) {
            return "本地网络";
        }

        // 检查缓存
        CachedLocation cached = locationCache.get(ip);
        if (cached != null && !cached.isExpired()) {
            return cached.getLocation();
        }

        // 调用API获取位置信息
        try {
            String location = fetchLocationFromAPI(ip);
            // 缓存结果
            locationCache.put(ip, new CachedLocation(location));
            return location;
        } catch (Exception e) {
            return "未知地区";
        }
    }

    /**
     * 判断是否为本地网络IP
     */
    private static boolean isLocalIP(String ip) {
        return ip.equals("127.0.0.1")
                || ip.equals("0:0:0:0:0:0:0:1")
                || ip.startsWith("192.168.")
                || ip.startsWith("10.")
                || (ip.startsWith("172.") && isIn172Range(ip));
    }

    /**
     * 判断IP是否在172.16.0.0-172.31.255.255范围内
     */
    private static boolean isIn172Range(String ip) {
        try {
            if (ip.startsWith("172.")) {
                String[] parts = ip.split("\\.");
                if (parts.length > 1) {
                    int second = Integer.parseInt(parts[1]);
                    return second >= 16 && second <= 31;
                }
            }
        } catch (NumberFormatException ignored) {
        }
        return false;
    }

    /**
     * 从API获取地理位置信息
     * 这里使用的是ip-api.com的免费服务，实际使用中可以替换为其他服务
     */
    private static String fetchLocationFromAPI(String ip) throws Exception {
        URL url = new URL("http://ip-api.com/line/" + ip + "?fields=country,regionName,city&lang=zh-CN");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!response.isEmpty()) {
                    response.append(" ");
                }
                response.append(line);
            }

            return response.toString();
        }
    }

    /**
     * 缓存的地理位置信息类
     */
    private static class CachedLocation {
        private final String location;
        private final long timestamp;

        public CachedLocation(String location) {
            this.location = location;
            this.timestamp = System.currentTimeMillis();
        }

        public String getLocation() {
            return location;
        }

        public boolean isExpired() {
            long currentTime = System.currentTimeMillis();
            long expiry = TimeUnit.HOURS.toMillis(CACHE_EXPIRY_HOURS);
            return (currentTime - timestamp) > expiry;
        }
    }
}

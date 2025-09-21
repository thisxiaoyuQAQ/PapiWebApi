package com.xiaoyu.papiwebapi.utils;

import com.xiaoyu.papiwebapi.PapiWebAPI;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * 管理API访问日志
 */
public class LogManager {

    private final PapiWebAPI plugin;
    private final File logFile;
    private YamlConfiguration logs;
    private final SimpleDateFormat dateFormat;
    private final ExecutorService logExecutor;

    // 日志计数，用于生成唯一的键
    private int logCounter = 0;

    public LogManager(PapiWebAPI plugin) {
        this.plugin = plugin;
        this.logFile = new File(plugin.getDataFolder(), "logs.yml");
        this.dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

        // 创建单线程执行器来处理日志，避免主线程IO操作
        this.logExecutor = Executors.newSingleThreadExecutor();

        // 初始化日志文件
        initLogFile();
    }

    /**
     * 初始化日志文件
     */
    private void initLogFile() {
        try {
            if (!logFile.exists()) {
                logFile.getParentFile().mkdirs();
                logFile.createNewFile();
            }

            logs = YamlConfiguration.loadConfiguration(logFile);

            // 如果日志中已有条目，找出最大的计数器值
            if (logs.getKeys(false).size() > 0) {
                for (String key : logs.getKeys(false)) {
                    try {
                        int id = Integer.parseInt(key);
                        if (id > logCounter) {
                            logCounter = id;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error initializing log file", e);
        }
    }

    /**
     * 记录API访问日志
     *
     * @param ip 访问IP地址
     * @param region 访问地区
     * @param path 访问路径
     * @param authSuccess 鉴权是否成功
     * @param responseMessage 返回信息
     */
    public void logAccess(String ip, String region, String path, boolean authSuccess, String responseMessage) {
        // 异步处理日志写入
        logExecutor.submit(() -> {
            try {
                // 生成日志条目编号
                logCounter++;
                String logId = String.valueOf(logCounter);

                // 构造日志条目
                String timestamp = dateFormat.format(new Date());
                String logEntry = String.format("[%s] %s %s 访问了%s %s 返回%s",
                        timestamp, ip, region, path,
                        authSuccess ? "鉴权正确" : "鉴权错误",
                        responseMessage);

                // 写入日志
                logs.set(logId + ".timestamp", timestamp);
                logs.set(logId + ".ip", ip);
                logs.set(logId + ".region", region);
                logs.set(logId + ".path", path);
                logs.set(logId + ".auth_success", authSuccess);
                logs.set(logId + ".response", responseMessage);
                logs.set(logId + ".full_entry", logEntry);

                // 保存日志文件
                logs.save(logFile);

                // 打印到控制台
                plugin.getLogger().info(logEntry);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Error writing to log file", e);
            }
        });
    }

    /**
     * 获取指定IP的地区信息
     *
     * @param ip IP地址
     * @return 地区信息，如果无法获取则返回"未知地区"
     */
    public String getRegion(String ip) {
        // 如果是本地IP，直接返回
        if (ip.equals("127.0.0.1") || ip.equals("0:0:0:0:0:0:0:1") || ip.startsWith("192.168.")
                || ip.startsWith("10.") || ip.startsWith("172.16.")) {
            return "本地网络";
        }

        // 这里可以接入IP地理位置API，如果需要的话
        // 例如使用GeoIP数据库或第三方API服务

        // 简单实现，实际项目中可以使用更准确的服务
        return "未知地区";
    }

    /**
     * 关闭日志管理器
     */
    public void shutdown() {
        // 关闭执行器
        logExecutor.shutdown();
    }
}

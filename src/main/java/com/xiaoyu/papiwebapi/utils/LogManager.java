package com.xiaoyu.papiwebapi.utils;

import com.xiaoyu.papiwebapi.PapiWebAPI;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * 管理API访问日志
 */
public class LogManager {

    private final PapiWebAPI plugin;
    private final File logsDir;
    private final SimpleDateFormat dateFormat;
    private final SimpleDateFormat fileFormat;
    private final ExecutorService logExecutor;

    // 当前日志文件和配置
    private File currentLogFile;
    private YamlConfiguration currentLogs;
    private String currentDateStr;

    // 日志计数，用于生成唯一的键
    private int logCounter = 0;

    public LogManager(PapiWebAPI plugin) {
        this.plugin = plugin;
        this.logsDir = new File(plugin.getDataFolder(), "logs");
        this.dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        this.fileFormat = new SimpleDateFormat("yyyy-MM-dd");

        // 创建单线程执行器来处理日志，避免主线程IO操作
        this.logExecutor = Executors.newSingleThreadExecutor();

        // 初始化日志目录
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }

        // 初始化当天的日志文件
        initCurrentLogFile();
    }

    /**
     * 初始化当天的日志文件
     */
    private void initCurrentLogFile() {
        try {
            // 获取当前日期字符串
            currentDateStr = fileFormat.format(new Date());

            // 创建对应的日志文件
            currentLogFile = new File(logsDir, currentDateStr + "-log.yml");
            if (!currentLogFile.exists()) {
                currentLogFile.createNewFile();
            }

            // 加载日志配置
            currentLogs = YamlConfiguration.loadConfiguration(currentLogFile);

            // 如果日志中已有条目，找出最大的计数器值
            if (currentLogs.getKeys(false).size() > 0) {
                for (String key : currentLogs.getKeys(false)) {
                    try {
                        int id = Integer.parseInt(key);
                        if (id > logCounter) {
                            logCounter = id;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }

            plugin.getLogger().info("Initialized log file: " + currentLogFile.getName());

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error initializing log file", e);
        }
    }

    /**
     * 检查是否需要切换到新的日志文件
     */
    private void checkLogFileRotation() {
        String dateStr = fileFormat.format(new Date());

        // 如果日期改变，切换到新的日志文件
        if (!dateStr.equals(currentDateStr)) {
            plugin.getLogger().info("Rotating log file to new date: " + dateStr);
            initCurrentLogFile();
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
                // 检查是否需要轮转日志文件
                checkLogFileRotation();

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
                currentLogs.set(logId + ".timestamp", timestamp);
                currentLogs.set(logId + ".ip", ip);
                currentLogs.set(logId + ".region", region);
                currentLogs.set(logId + ".path", path);
                currentLogs.set(logId + ".auth_success", authSuccess);
                currentLogs.set(logId + ".response", responseMessage);
                currentLogs.set(logId + ".full_entry", logEntry);

                // 保存日志文件
                currentLogs.save(currentLogFile);

                // 打印到控制台
                plugin.getLogger().info(logEntry);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Error writing to log file", e);
            }
        });
    }

    /**
     * 获取所有日志文件
     * @return 日志文件列表，按日期排序（最新的在前）
     */
    public List<File> getLogFiles() {
        List<File> files = new ArrayList<>();
        File[] logFiles = logsDir.listFiles((dir, name) -> name.endsWith("-log.yml"));

        if (logFiles != null) {
            // 按文件名（日期）排序，最新的在前
            java.util.Arrays.sort(logFiles, (f1, f2) -> f2.getName().compareTo(f1.getName()));
            java.util.Collections.addAll(files, logFiles);
        }

        return files;
    }

    /**
     * 获取指定日期的日志文件
     * @param date 日期字符串（格式：yyyy-MM-dd）
     * @return 日志文件，如果不存在则返回null
     */
    public File getLogFile(String date) {
        File file = new File(logsDir, date + "-log.yml");
        return file.exists() ? file : null;
    }

    /**
     * 获取最近的日志记录
     * @param count 要获取的记录数
     * @return 日志记录列表
     */
    public List<String> getRecentLogs(int count) {
        List<String> result = new ArrayList<>();
        List<File> logFiles = getLogFiles();

        int remaining = count;

        for (File logFile : logFiles) {
            if (remaining <= 0) break;

            YamlConfiguration config = YamlConfiguration.loadConfiguration(logFile);
            List<String> entries = new ArrayList<>();

            for (String key : config.getKeys(false)) {
                String entry = config.getString(key + ".full_entry");
                if (entry != null) {
                    entries.add(entry);
                }
            }

            // 按ID逆序排列，最新的记录在前
            entries.sort((e1, e2) -> e2.compareTo(e1));

            // 添加到结果列表
            for (String entry : entries) {
                if (remaining <= 0) break;
                result.add(entry);
                remaining--;
            }
        }

        return result;
    }

    /**
     * 关闭日志管理器
     */
    public void shutdown() {
        // 关闭执行器
        logExecutor.shutdown();
    }
    /**
     * 清理过期的日志文件
     */
    public void cleanupOldLogs() {
        int retentionDays = plugin.getConfig().getInt("logging.retention_days", 30);
        if (retentionDays <= 0) {
            return; // 不清理
        }

        logExecutor.submit(() -> {
            try {
                List<File> logFiles = getLogFiles();

                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DAY_OF_MONTH, -retentionDays);
                Date cutoffDate = cal.getTime();
                String cutoffDateStr = fileFormat.format(cutoffDate);

                for (File file : logFiles) {
                    String fileName = file.getName();
                    if (fileName.endsWith("-log.yml")) {
                        String dateStr = fileName.substring(0, fileName.lastIndexOf("-log.yml"));

                        // 如果日志文件日期早于截止日期，则删除
                        if (dateStr.compareTo(cutoffDateStr) < 0) {
                            if (file.delete()) {
                                plugin.getLogger().info("Deleted old log file: " + fileName);
                            } else {
                                plugin.getLogger().warning("Failed to delete old log file: " + fileName);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error cleaning up old logs", e);
            }
        });
    }

}


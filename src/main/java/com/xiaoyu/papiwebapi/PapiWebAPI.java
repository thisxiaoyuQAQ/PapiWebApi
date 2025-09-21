package com.xiaoyu.papiwebapi;

import com.xiaoyu.papiwebapi.api.ApiManager;
import com.xiaoyu.papiwebapi.commands.PapiWebAPICommand;
import com.xiaoyu.papiwebapi.http.HttpServer;
import com.xiaoyu.papiwebapi.utils.LogManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class PapiWebAPI extends JavaPlugin {

    private HttpServer httpServer;
    private ApiManager apiManager;
    private LogManager logManager;

    @Override
    public void onEnable() {
        // 检查PlaceholderAPI是否已安装
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().severe("Could not find PlaceholderAPI! This plugin requires PlaceholderAPI to work.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 加载配置
        loadConfig();

        // 初始化日志管理器
        logManager = new LogManager(this);

        // 初始化API管理器
        apiManager = new ApiManager(this);
        apiManager.loadApis();

        // 初始化并启动HTTP服务器
        startHttpServer();

        // 注册命令
        getCommand("papiwebapi").setExecutor(new PapiWebAPICommand(this));

        getLogger().info("PapiWebAPI has been enabled!");
    }

    @Override
    public void onDisable() {
        // 保存API配置
        if (apiManager != null) {
            apiManager.saveApis();
        }

        // 关闭日志管理器
        if (logManager != null) {
            logManager.shutdown();
        }

        // 关闭HTTP服务器
        if (httpServer != null) {
            httpServer.stop();
            getLogger().info("Web API server stopped");
        }

        getLogger().info("PapiWebAPI has been disabled!");
    }

    /**
     * 加载配置文件
     */
    private void loadConfig() {
        try {
            // 确保配置目录存在
            if (!getDataFolder().exists()) {
                getDataFolder().mkdirs();
            }

            // 保存默认配置（如果不存在）
            File configFile = new File(getDataFolder(), "config.yml");
            if (!configFile.exists()) {
                saveResource("config.yml", false);
            }

            // 加载配置
            reloadConfig();
            getLogger().info("Configuration loaded successfully");
        } catch (Exception e) {
            getLogger().warning("Failed to load config.yml, using default settings: " + e.getMessage());
        }
    }

    /**
     * 启动HTTP服务器
     */
    private void startHttpServer() {
        // 如果已存在服务器实例，先停止
        if (httpServer != null) {
            httpServer.stop();
        }

        // 从配置中获取端口和主机
        int port = getConfig().getInt("server.port", 8080);
        String host = getConfig().getString("server.host", "0.0.0.0");

        // 创建并启动新的服务器实例
        httpServer = new HttpServer(this, host, port);

        try {
            httpServer.start();
            getLogger().info("Web API server started on " + host + ":" + port);
        } catch (Exception e) {
            getLogger().severe("Failed to start Web API server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 重新加载插件配置
     * @return 重载是否成功
     */
    public boolean reload() {
        try {
            // 重载配置文件
            reloadConfig();

            // 重载API配置
            apiManager.loadApis();

            // 重载HTTP服务器认证配置
            if (httpServer != null) {
                httpServer.reloadAuthConfig();
            }

            // 重启HTTP服务器以应用新配置
            startHttpServer();

            getLogger().info("PapiWebAPI has been reloaded successfully!");
            return true;
        } catch (Exception e) {
            getLogger().severe("Failed to reload PapiWebAPI: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }


    public LogManager getLogManager() {
        return logManager;
    }

    public ApiManager getApiManager() {
        return apiManager;
    }

    public HttpServer getHttpServer() {
        return httpServer;
    }
}


package com.xiaoyu.papiwebapi.api;

import com.xiaoyu.papiwebapi.PapiWebAPI;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ApiManager {

    private final PapiWebAPI plugin;
    private final Map<String, String> registeredApis = new HashMap<>();  // 别名 -> 占位符
    private final File apisFile;

    public ApiManager(PapiWebAPI plugin) {
        this.plugin = plugin;
        this.apisFile = new File(plugin.getDataFolder(), "apis.yml");
    }

    public void registerApi(String endpoint, String placeholder) {
        // 确保占位符格式正确
        if (placeholder.startsWith("%") && placeholder.endsWith("%")) {
            // 已经有百分号，保持原样
        } else if (!placeholder.contains("%")) {
            // 没有百分号，添加百分号
            placeholder = "%" + placeholder + "%";
        } else {
            // 有一些百分号，但不是正确格式，清理并添加
            placeholder = placeholder.replace("%", "");
            placeholder = "%" + placeholder + "%";
        }

        registeredApis.put(endpoint, placeholder);
        saveApis();
    }


    public void unregisterApi(String endpoint) {
        registeredApis.remove(endpoint);
        saveApis();
    }

    public boolean isApiRegistered(String endpoint) {
        return registeredApis.containsKey(endpoint);
    }

    public String getPlaceholder(String endpoint) {
        return registeredApis.get(endpoint);
    }

    public Set<String> getRegisteredEndpoints() {
        return registeredApis.keySet();
    }

    public Map<String, String> getRegisteredApis() {
        return new HashMap<>(registeredApis);
    }

    public void loadApis() {
        try {
            // 清空当前注册的APIs
            registeredApis.clear();

            if (!apisFile.exists()) {
                plugin.getLogger().info("APIs file not found, creating new one");
                return;
            }

            FileConfiguration config = YamlConfiguration.loadConfiguration(apisFile);
            for (String key : config.getKeys(false)) {
                registeredApis.put(key, config.getString(key));
            }

            plugin.getLogger().info("Loaded " + registeredApis.size() + " API endpoints");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load APIs: " + e.getMessage());
            e.printStackTrace();
        }
    }


    public void saveApis() {
        try {
            if (!apisFile.exists()) {
                apisFile.getParentFile().mkdirs();
                apisFile.createNewFile();
            }

            FileConfiguration config = new YamlConfiguration();
            for (Map.Entry<String, String> entry : registeredApis.entrySet()) {
                config.set(entry.getKey(), entry.getValue());
            }

            config.save(apisFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save APIs configuration: " + e.getMessage());
            e.printStackTrace();
        }
    }
}


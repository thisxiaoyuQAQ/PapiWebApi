package com.xiaoyu.papiwebapi.commands;

import com.xiaoyu.papiwebapi.PapiWebAPI;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class PapiWebAPICommand implements CommandExecutor, TabCompleter {

    private final PapiWebAPI plugin;

    public PapiWebAPICommand(PapiWebAPI plugin) {
        this.plugin = plugin;
    }
    @Deprecated
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("papiwebapi.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length < 1) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " create <placeholder> [alias]");
                    return true;
                }

                String placeholder = args[1];
                // 确保占位符格式正确
                if (!placeholder.startsWith("%") && !placeholder.endsWith("%")) {
                    placeholder = "%" + placeholder + "%";
                }

                // 提取别名 - 如果提供了别名，使用它，否则使用占位符
                String endpoint;
                if (args.length > 2) {
                    // 使用自定义别名
                    endpoint = args[2];
                } else {
                    // 使用占位符作为别名
                    endpoint = extractEndpoint(placeholder);
                }

                if (plugin.getApiManager().isApiRegistered(endpoint)) {
                    sender.sendMessage(ChatColor.RED + "API endpoint already exists: " + endpoint);
                    return true;
                }

                plugin.getApiManager().registerApi(endpoint, placeholder);
                sender.sendMessage(ChatColor.GREEN + "Created API endpoint: /" + endpoint);
                sender.sendMessage(ChatColor.GREEN + "For placeholder: " + placeholder);
                sender.sendMessage(ChatColor.GREEN + "Access it at: http://" + plugin.getHttpServer().getHost() + ":"
                        + plugin.getHttpServer().getPort() + "/" + endpoint);
                break;

            case "delete":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " delete <endpoint_or_placeholder>");
                    return true;
                }

                String toDelete = args[1];

                // 尝试直接删除指定端点
                if (plugin.getApiManager().isApiRegistered(toDelete)) {
                    plugin.getApiManager().unregisterApi(toDelete);
                    sender.sendMessage(ChatColor.GREEN + "Deleted API endpoint: " + toDelete);
                    return true;
                }

                // 如果不是端点，可能是尝试删除占位符
                String placeholderEndpoint = extractEndpoint(toDelete);
                if (plugin.getApiManager().isApiRegistered(placeholderEndpoint)) {
                    plugin.getApiManager().unregisterApi(placeholderEndpoint);
                    sender.sendMessage(ChatColor.GREEN + "Deleted API endpoint: " + placeholderEndpoint);
                    return true;
                }

                // 如果都找不到
                sender.sendMessage(ChatColor.RED + "API endpoint not found: " + toDelete);
                break;

            case "list":
                sender.sendMessage(ChatColor.YELLOW + "=== Registered API Endpoints ===");
                for (String ep : plugin.getApiManager().getRegisteredEndpoints()) {
                    String ph = plugin.getApiManager().getPlaceholder(ep);
                    sender.sendMessage(ChatColor.GOLD + "/" + ep + ChatColor.WHITE + " - " +
                            ChatColor.AQUA + ph);
                }
                break;
            case "reload":
                // 执行重载操作
                sender.sendMessage(ChatColor.YELLOW + "Reloading PapiWebAPI...");
                boolean success = plugin.reload();

                if (success) {
                    sender.sendMessage(ChatColor.GREEN + "PapiWebAPI has been reloaded successfully!");
                } else {
                    sender.sendMessage(ChatColor.RED + "Failed to reload PapiWebAPI. Check console for errors.");
                }
                break;
            case "logs":
                // 检查是否有足够的权限
                if (!sender.hasPermission("papiwebapi.logs")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to view logs.");
                    return true;
                }

                // 默认参数
                int page = 1;
                int entriesPerPage = 5;
                String date = null;

                // 解析参数
                if (args.length > 1) {
                    // 检查是否是日期格式参数 (yyyy-MM-dd)
                    if (args[1].matches("\\d{4}-\\d{2}-\\d{2}")) {
                        date = args[1];

                        // 如果提供了页码
                        if (args.length > 2) {
                            try {
                                page = Integer.parseInt(args[2]);
                                if (page < 1) page = 1;
                            } catch (NumberFormatException e) {
                                sender.sendMessage(ChatColor.RED + "Invalid page number. Using page 1.");
                                page = 1;
                            }
                        }
                    } else {
                        // 参数是页码
                        try {
                            page = Integer.parseInt(args[1]);
                            if (page < 1) page = 1;
                        } catch (NumberFormatException e) {
                            sender.sendMessage(ChatColor.RED + "Invalid parameter. Use a date (yyyy-MM-dd) or page number.");
                            return true;
                        }
                    }
                }

                // 获取日志条目
                List<String> logEntries = new ArrayList<>();

                if (date != null) {
                    // 获取特定日期的日志
                    File logFile = plugin.getLogManager().getLogFile(date);
                    if (logFile == null || !logFile.exists()) {
                        sender.sendMessage(ChatColor.RED + "No logs found for date: " + date);
                        return true;
                    }

                    YamlConfiguration logs = YamlConfiguration.loadConfiguration(logFile);
                    for (String key : logs.getKeys(false)) {
                        String entry = logs.getString(key + ".full_entry");
                        if (entry != null) {
                            logEntries.add(entry);
                        }
                    }

                    // 逆序排序，最新的日志在前
                    logEntries.sort((e1, e2) -> e2.compareTo(e1));

                    sender.sendMessage(ChatColor.YELLOW + "Showing logs for date: " + date);
                } else {
                    // 获取最近的日志（所有日期）
                    logEntries = plugin.getLogManager().getRecentLogs(1000); // 限制为最近1000条
                }

                // 分页显示
                int totalLogs = logEntries.size();
                int totalPages = (int) Math.ceil((double) totalLogs / entriesPerPage);
                if (totalPages == 0) totalPages = 1;
                if (page > totalPages) page = totalPages;

                sender.sendMessage(ChatColor.YELLOW + "=== API Access Logs (Page " + page + "/" + totalPages + ") ===");

                if (totalLogs == 0) {
                    sender.sendMessage(ChatColor.GRAY + "No logs found.");
                    return true;
                }

                int startIndex = (page - 1) * entriesPerPage;
                int endIndex = Math.min(startIndex + entriesPerPage, totalLogs);

                for (int i = startIndex; i < endIndex; i++) {
                    sender.sendMessage(ChatColor.WHITE + logEntries.get(i));
                }

                // 显示导航提示
                if (page < totalPages) {
                    sender.sendMessage(ChatColor.GOLD + "Use '/papiwebapi logs " + (date != null ? date + " " : "") + (page + 1) + "' for the next page.");
                }

                if (date == null && totalPages > 1) {
                    sender.sendMessage(ChatColor.GOLD + "Use '/papiwebapi logs <date> [page]' to view logs for a specific date.");

                    // 显示可用的日志日期
                    List<File> logFiles = plugin.getLogManager().getLogFiles();
                    if (!logFiles.isEmpty()) {
                        StringBuilder availableDates = new StringBuilder(ChatColor.GOLD + "Available dates: ");
                        int maxDatesToShow = 5;
                        for (int i = 0; i < Math.min(logFiles.size(), maxDatesToShow); i++) {
                            String fileName = logFiles.get(i).getName();
                            String dateStr = fileName.substring(0, fileName.lastIndexOf("-log.yml"));
                            availableDates.append(dateStr).append(i < Math.min(logFiles.size(), maxDatesToShow) - 1 ? ", " : "");
                        }
                        if (logFiles.size() > maxDatesToShow) {
                            availableDates.append(", ...");
                        }
                        sender.sendMessage(availableDates.toString());
                    }
                }

                break;



            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private String extractEndpoint(String placeholder) {
        // 移除百分号
        if (placeholder.startsWith("%") && placeholder.endsWith("%")) {
            placeholder = placeholder.substring(1, placeholder.length() - 1);
        }

        return placeholder;
    }
    @Deprecated
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "=== PAPI Web API Help ===");
        sender.sendMessage(ChatColor.GOLD + "/papiwebapi create <placeholder> [alias]" + ChatColor.WHITE + " - Create a new API endpoint with optional alias");
        sender.sendMessage(ChatColor.GOLD + "/papiwebapi delete <endpoint_or_placeholder>" + ChatColor.WHITE + " - Delete an API endpoint");
        sender.sendMessage(ChatColor.GOLD + "/papiwebapi list" + ChatColor.WHITE + " - List all registered API endpoints");
        sender.sendMessage(ChatColor.GOLD + "/papiwebapi reload" + ChatColor.WHITE + " - Reload configuration and APIs");

        // 如果有查看日志的权限，显示日志命令
        if (sender.hasPermission("papiwebapi.logs")) {
            sender.sendMessage(ChatColor.GOLD + "/papiwebapi logs [page]" + ChatColor.WHITE + " - View recent API access logs");
            sender.sendMessage(ChatColor.GOLD + "/papiwebapi logs <date> [page]" + ChatColor.WHITE + " - View logs for a specific date (format: yyyy-MM-dd)");
        }

        // 显示认证信息，如果启用
        if (plugin.getConfig().getBoolean("security.authentication.enabled", false)) {
            String tokenParam = plugin.getConfig().getString("security.authentication.parameter", "token");
            sender.sendMessage(ChatColor.YELLOW + "Authentication is enabled. API URLs format:");
            sender.sendMessage(ChatColor.AQUA + "http://server:port/" + tokenParam + "=YOUR_TOKEN/endpoint");
        }

        sender.sendMessage(ChatColor.YELLOW + "Examples:");
        sender.sendMessage(ChatColor.GOLD + "/papiwebapi create %luckperms_prefix% 前缀" + ChatColor.WHITE + " - Creates endpoint at /前缀");
        sender.sendMessage(ChatColor.GOLD + "/papiwebapi create player_exp exp" + ChatColor.WHITE + " - Creates endpoint at /exp");
    }



    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("papiwebapi.admin")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return Arrays.asList("create", "delete", "list", "reload", "logs").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("delete")) {
                return plugin.getApiManager().getRegisteredEndpoints().stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("logs")) {
                // 提供可用日志日期作为补全
                List<String> dates = new ArrayList<>();
                for (File file : plugin.getLogManager().getLogFiles()) {
                    String fileName = file.getName();
                    if (fileName.endsWith("-log.yml")) {
                        String dateStr = fileName.substring(0, fileName.lastIndexOf("-log.yml"));
                        if (dateStr.startsWith(args[1].toLowerCase())) {
                            dates.add(dateStr);
                        }
                    }
                }
                return dates;
            }
        }

        return new ArrayList<>();
    }

}

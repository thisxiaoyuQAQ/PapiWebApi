package com.xiaoyu.papiwebapi.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.xiaoyu.papiwebapi.PapiWebAPI;
import com.xiaoyu.papiwebapi.utils.IPLocationUtil;
import com.xiaoyu.papiwebapi.utils.PlaceholderUtils;
import org.bukkit.Bukkit;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpServer {

    private final PapiWebAPI plugin;
    private final String host;
    private final int port;
    private com.sun.net.httpserver.HttpServer server;

    // 用于匹配令牌参数的正则表达式
    private Pattern tokenPattern;
    private boolean authEnabled;
    private String configuredToken;
    private String tokenParameter;

    public HttpServer(PapiWebAPI plugin, String host, int port) {
        this.plugin = plugin;
        this.host = host;
        this.port = port;

        // 从配置加载认证设置
        loadAuthConfig();
    }

    /**
     * 加载认证配置
     */
    private void loadAuthConfig() {
        authEnabled = plugin.getConfig().getBoolean("security.authentication.enabled", false);
        configuredToken = plugin.getConfig().getString("security.authentication.token", "");
        tokenParameter = plugin.getConfig().getString("security.authentication.parameter", "token");

        // 编译用于匹配令牌的正则表达式
        tokenPattern = Pattern.compile("/" + tokenParameter + "=([^/]+)/(.*)");

        plugin.getLogger().info("API authentication " + (authEnabled ? "enabled" : "disabled"));
    }

    /**
     * 重新加载认证配置
     */
    public void reloadAuthConfig() {
        loadAuthConfig();
    }

    public void start() throws IOException {
        server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(host, port), 0);
        server.createContext("/", new RootHandler());
        server.setExecutor(null); // 使用默认执行器
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    private class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String clientAddress = exchange.getRemoteAddress().getAddress().getHostAddress();
            String clientRegion = IPLocationUtil.getIPLocation(clientAddress);

            String fullUrl = "http://" + host + ":" + port + path;

            // 检查是否需要认证
            if (authEnabled) {
                // 检查路径中是否包含令牌
                Matcher matcher = tokenPattern.matcher(path);
                if (matcher.find()) {
                    String providedToken = matcher.group(1);
                    String actualPath = "/" + matcher.group(2);

                    // 验证令牌
                    if (!configuredToken.equals(providedToken)) {
                        String errorMessage = "Invalid token";
                        // 记录失败日志
                        plugin.getLogManager().logAccess(
                                clientAddress, clientRegion, fullUrl, false, errorMessage
                        );

                        sendResponse(exchange, 403, "Forbidden: " + errorMessage);
                        return;
                    }

                    // 令牌有效，继续处理实际路径
                    handleRequest(exchange, actualPath, clientAddress, clientRegion, fullUrl, true);
                } else {
                    // 未提供令牌
                    if (path.equals("/") || path.isEmpty()) {
                        // 主页可以访问，显示简化信息
                        String message = "PapiWebAPI is running. Authentication is required.";
                        // 记录日志（未鉴权但允许访问主页）
                        plugin.getLogManager().logAccess(
                                clientAddress, clientRegion, fullUrl, false, message
                        );

                        sendResponse(exchange, 200, message +
                                "\nFormat: http://server:port/" + tokenParameter + "=YOUR_TOKEN/endpoint");
                    } else {
                        String errorMessage = "Token required";
                        // 记录失败日志
                        plugin.getLogManager().logAccess(
                                clientAddress, clientRegion, fullUrl, false, errorMessage
                        );

                        sendResponse(exchange, 401, "Unauthorized: " + errorMessage);
                    }
                }
            } else {
                // 不需要认证，直接处理请求
                handleRequest(exchange, path, clientAddress, clientRegion, fullUrl, true);
            }
        }

        /**
         * 处理API请求
         */
        private void handleRequest(HttpExchange exchange, String path, String clientAddress,
                                   String clientRegion, String fullUrl, boolean authSuccess) throws IOException {
            if (path.equals("/") || path.isEmpty()) {
                String response = getApiListHtml();
                // 记录首页访问日志
                plugin.getLogManager().logAccess(
                        clientAddress, clientRegion, fullUrl, authSuccess, "API首页"
                );

                sendResponse(exchange, 200, response);
                return;
            }

            // 移除开头的斜杠
            String endpoint = path.substring(1);
            String[] parts = endpoint.split("/");

            if (parts.length == 0) {
                String errorMessage = "Not Found";
                // 记录404日志
                plugin.getLogManager().logAccess(
                        clientAddress, clientRegion, fullUrl, authSuccess, errorMessage
                );

                sendResponse(exchange, 404, errorMessage);
                return;
            }

            String placeholderEndpoint = parts[0];

            if (!plugin.getApiManager().isApiRegistered(placeholderEndpoint)) {
                String errorMessage = "API endpoint not found: " + placeholderEndpoint;
                // 记录404日志
                plugin.getLogManager().logAccess(
                        clientAddress, clientRegion, fullUrl, authSuccess, errorMessage
                );

                sendResponse(exchange, 404, errorMessage);
                return;
            }

            String placeholder = plugin.getApiManager().getPlaceholder(placeholderEndpoint);

            try {
                final String response;

                // 处理排序请求
                if (parts.length >= 3 && parts[1].equalsIgnoreCase("sort")) {
                    String sortOrder = parts[2]; // htl, lth, atz, zta
                    int limit = -1;

                    if (parts.length >= 4) {
                        try {
                            limit = Integer.parseInt(parts[3]);
                        } catch (NumberFormatException ignored) {
                            // 使用默认限制
                        }
                    }

                    List<String> sorted = PlaceholderUtils.getPlayersSorted(placeholder, sortOrder, limit);
                    response = String.join("\n", sorted);
                }
                // 处理特定玩家请求
                else if (parts.length >= 2) {
                    String playerName = parts[1];
                    response = PlaceholderUtils.getPlaceholderValue(placeholder, playerName);
                }
                // 处理默认请求 - 返回所有在线玩家的值
                else {
                    List<String> values = new ArrayList<>();
                    Bukkit.getOnlinePlayers().forEach(player -> {
                        String value = PlaceholderUtils.getPlaceholderValue(placeholder, player.getName());
                        values.add(player.getName() + ": " + value);
                    });
                    response = String.join("\n", values);
                }

                // 记录成功日志 - 截断响应内容以避免日志过长
                String logResponse = response.length() > 100 ?
                        response.substring(0, 97) + "..." : response;
                plugin.getLogManager().logAccess(
                        clientAddress, clientRegion, fullUrl, authSuccess, logResponse
                );

                sendResponse(exchange, 200, response);
            } catch (Exception e) {
                String errorMessage = "Error: " + e.getMessage();
                // 记录错误日志
                plugin.getLogManager().logAccess(
                        clientAddress, clientRegion, fullUrl, authSuccess, errorMessage
                );

                plugin.getLogger().severe("Error processing API request: " + e.getMessage());
                e.printStackTrace();
                sendResponse(exchange, 500, "Internal Server Error: " + e.getMessage());
            }
        }

        private String getApiListHtml() {
            StringBuilder html = new StringBuilder();
            html.append("<html><head><title>PAPI Web API</title>");
            html.append("<style>body{font-family:Arial,sans-serif;margin:20px;} h1{color:#333;} ul{list-style-type:none;padding:0;} li{margin:10px 0;padding:10px;background:#f5f5f5;border-radius:5px;} code{background:#e0e0e0;padding:2px 5px;border-radius:3px;}</style>");
            html.append("</head><body>");
            html.append("<h1>PAPI Web API</h1>");

            // 如果启用了认证，添加认证信息
            if (authEnabled) {
                html.append("<div style='padding:10px;background:#fff8e1;border-left:4px solid #ffa000;margin-bottom:20px;'>");
                html.append("<p><strong>Authentication Required</strong></p>");
                html.append("<p>All API requests must include a valid token in the URL:</p>");
                html.append("<code>http://").append(host).append(":").append(port).append("/").append(tokenParameter).append("=YOUR_TOKEN/endpoint</code>");
                html.append("</div>");
            }

            html.append("<p>Available API Endpoints:</p>");
            html.append("<ul>");

            for (String endpoint : plugin.getApiManager().getRegisteredEndpoints()) {
                String placeholder = plugin.getApiManager().getPlaceholder(endpoint);
                html.append("<li><strong>").append(endpoint).append("</strong> - Placeholder: <code>").append(placeholder).append("</code>");
                html.append("<div>Usage examples:");
                html.append("<ul>");

                // 创建带有令牌的示例URL前缀
                String urlPrefix = authEnabled ?
                        tokenParameter + "=YOUR_TOKEN/" : "";

                html.append("<li><code>/").append(urlPrefix).append(endpoint).append("</code> - Get values for all online players</li>");
                html.append("<li><code>/").append(urlPrefix).append(endpoint).append("/playername</code> - Get value for specific player</li>");
                html.append("<li><code>/").append(urlPrefix).append(endpoint).append("/sort/htl/10</code> - Sort high to low (top 10)</li>");
                html.append("<li><code>/").append(urlPrefix).append(endpoint).append("/sort/lth</code> - Sort low to high (all)</li>");
                html.append("<li><code>/").append(urlPrefix).append(endpoint).append("/sort/atz</code> - Sort A to Z</li>");
                html.append("<li><code>/").append(urlPrefix).append(endpoint).append("/sort/zta</code> - Sort Z to A</li>");
                html.append("</ul></div>");
                html.append("</li>");
            }

            html.append("</ul>");
            html.append("</body></html>");
            return html.toString();
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            byte[] responseBytes = response.getBytes();
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
        }
    }
}
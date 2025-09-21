package com.xiaoyu.papiwebapi.utils;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class PlaceholderUtils {

    public static String getPlaceholderValue(String placeholder, String playerName) {
        OfflinePlayer player = getPlayerByName(playerName);
        if (player == null) {
            return "Player not found";
        }

        // 确保占位符格式正确
        String formattedPlaceholder = formatPlaceholder(placeholder);

        return PlaceholderAPI.setPlaceholders(player, formattedPlaceholder);
    }

    public static List<String> getPlayersSorted(String placeholder, String sortOrder, int limit) {
        List<OfflinePlayer> players = new ArrayList<>();

        // 获取所有在线和离线玩家
        for (Player player : Bukkit.getOnlinePlayers()) {
            players.add(player);
        }

        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            if (!players.contains(offlinePlayer)) {
                players.add(offlinePlayer);
            }
        }

        // 确保占位符格式正确
        String formattedPlaceholder = formatPlaceholder(placeholder);

        // 获取每个玩家的占位符值并按要求排序
        List<PlayerPlaceholderPair> pairs = players.stream()
                .map(player -> new PlayerPlaceholderPair(player,
                        PlaceholderAPI.setPlaceholders(player, formattedPlaceholder)))
                .collect(Collectors.toList());

        // 根据排序方式进行排序
        if ("htl".equalsIgnoreCase(sortOrder)) {
            // 从高到低排序 (尝试数值排序，失败则按字符串排序)
            pairs.sort((p1, p2) -> {
                try {
                    double v1 = Double.parseDouble(p1.getValue());
                    double v2 = Double.parseDouble(p2.getValue());
                    return Double.compare(v2, v1);
                } catch (NumberFormatException e) {
                    return p2.getValue().compareTo(p1.getValue());
                }
            });
        } else if ("lth".equalsIgnoreCase(sortOrder)) {
            // 从低到高排序
            pairs.sort((p1, p2) -> {
                try {
                    double v1 = Double.parseDouble(p1.getValue());
                    double v2 = Double.parseDouble(p2.getValue());
                    return Double.compare(v1, v2);
                } catch (NumberFormatException e) {
                    return p1.getValue().compareTo(p2.getValue());
                }
            });
        } else if ("atz".equalsIgnoreCase(sortOrder)) {
            // 从A到Z排序
            pairs.sort(Comparator.comparing(p -> p.getPlayer().getName(), String.CASE_INSENSITIVE_ORDER));
        } else if ("zta".equalsIgnoreCase(sortOrder)) {
            // 从Z到A排序
            pairs.sort((p1, p2) -> p2.getPlayer().getName().compareToIgnoreCase(p1.getPlayer().getName()));
        }

        // 应用限制并格式化结果
        return pairs.stream()
                .limit(limit > 0 ? limit : pairs.size())
                .map(pair -> pair.getPlayer().getName() + ": " + pair.getValue())
                .collect(Collectors.toList());
    }

    // 处理占位符格式的辅助方法
    private static String formatPlaceholder(String placeholder) {
        // 如果占位符已经包含正确的百分号，直接返回
        if (placeholder.startsWith("%") && placeholder.endsWith("%")) {
            return placeholder;
        }

        // 去除所有百分号，然后重新添加一对百分号
        placeholder = placeholder.replace("%", "");
        return "%" + placeholder + "%";
    }

    private static OfflinePlayer getPlayerByName(String name) {
        // 先查找在线玩家
        Player player = Bukkit.getPlayerExact(name);
        if (player != null) {
            return player;
        }

        // 再查找离线玩家
        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            if (offlinePlayer.getName() != null && offlinePlayer.getName().equalsIgnoreCase(name)) {
                return offlinePlayer;
            }
        }

        return null;
    }

    private static class PlayerPlaceholderPair {
        private final OfflinePlayer player;
        private final String value;

        public PlayerPlaceholderPair(OfflinePlayer player, String value) {
            this.player = player;
            this.value = value;
        }

        public OfflinePlayer getPlayer() {
            return player;
        }

        public String getValue() {
            return value;
        }
    }
}

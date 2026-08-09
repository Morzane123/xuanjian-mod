package top.xuanjian.guild.online;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.xuanjian.guild.network.ApiClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 功能3+9：在线玩家上报与查询。
 * 服务器启动/每心跳上报当前在线玩家，玩家通过 /xj online 查询各服务器在线状态。
 */
public class OnlineManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("xuanjianmod");
    private final ApiClient api;
    private String serverIp;

    public OnlineManager(ApiClient api, String serverIp) {
        this.api = api;
        this.serverIp = serverIp;
    }

    public void updateServerIp(String serverIp) {
        this.serverIp = serverIp;
    }

    public String getServerIp() {
        return serverIp;
    }

    /** 上报本服务器在线玩家（由心跳定时调用） */
    public void reportOnline(List<OnlinePlayer> players) {
        if (serverIp == null || serverIp.isBlank()) return;
        Map<String, Object> body = new HashMap<>();
        body.put("serverIp", serverIp);
        List<Map<String, String>> list = new ArrayList<>();
        for (OnlinePlayer p : players) {
            Map<String, String> m = new HashMap<>();
            m.put("uuid", p.uuid.toString());
            m.put("name", p.name);
            list.add(m);
        }
        body.put("players", list);
        api.post("/api/mod/online/report", body);
    }

    /** 查询指定服务器的在线玩家 */
    public List<OnlinePlayer> queryOnline(String ip) {
        List<OnlinePlayer> result = new ArrayList<>();
        JsonObject resp = api.get("/api/mod/online?serverIp=" + encode(ip));
        if (resp == null) return result;
        try {
            JsonArray arr = resp.getAsJsonArray("players");
            for (int i = 0; i < arr.size(); i++) {
                JsonObject o = arr.get(i).getAsJsonObject();
                result.add(new OnlinePlayer(
                        UUID.fromString(o.get("uuid").getAsString()),
                        o.get("name").getAsString()));
            }
        } catch (Exception e) {
            LOGGER.warn("解析在线玩家失败: {}", e.getMessage());
        }
        return result;
    }

    private static String encode(String s) {
        try {
            return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    public static class OnlinePlayer {
        public final UUID uuid;
        public final String name;

        public OnlinePlayer(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }
    }
}

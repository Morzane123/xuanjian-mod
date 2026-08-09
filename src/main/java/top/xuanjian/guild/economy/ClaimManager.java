package top.xuanjian.guild.economy;

import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.xuanjian.guild.network.ApiClient;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 功能8：游戏内贡献点申报
 */
public class ClaimManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("xuanjianmod");
    private final ApiClient api;

    public ClaimManager(ApiClient api) {
        this.api = api;
    }

    /** 提交申报 */
    public JsonObject submit(UUID uuid, int amount, String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put("uuid", uuid.toString());
        body.put("amount", amount);
        body.put("reason", reason);
        return api.post("/api/mod/claims", body);
    }
}

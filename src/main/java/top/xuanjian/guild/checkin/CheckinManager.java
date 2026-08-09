package top.xuanjian.guild.checkin;

import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.xuanjian.guild.network.ApiClient;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 功能1：登录后自动签到官网（每日一次，官网侧幂等）
 */
public class CheckinManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("xuanjianmod");
    private final ApiClient api;

    public CheckinManager(ApiClient api) {
        this.api = api;
    }

    /**
     * 执行签到
     * @return 成功返回响应 JSON，失败返回 null
     */
    public JsonObject checkin(UUID uuid, String playerName) {
        Map<String, Object> body = new HashMap<>();
        body.put("uuid", uuid.toString());
        body.put("playerName", playerName);
        JsonObject resp = api.post("/api/mod/checkin", body);
        if (resp == null) {
            LOGGER.info("[签到] {} 请求失败", playerName);
        }
        return resp;
    }

    /** 查询今日是否已签到 */
    public boolean hasCheckedIn(UUID uuid) {
        JsonObject resp = api.get("/api/mod/checkin/status?uuid=" + uuid);
        if (resp == null) return false;
        try {
            return resp.get("checkedIn").getAsBoolean();
        } catch (Exception e) {
            return false;
        }
    }
}

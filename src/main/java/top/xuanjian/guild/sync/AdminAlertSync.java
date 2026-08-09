package top.xuanjian.guild.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.xuanjian.guild.network.ApiClient;

import java.util.ArrayList;
import java.util.List;

/**
 * 功能10：当有新的贡献点申报待审核时，向游戏内在线管理员发送提醒（轮询增量）
 * 双通道：
 *  - 服务器模组（带 X-Server-Key）：返回 claims + adminUuids（全部管理员已绑定角色）
 *  - 客户端模组（管理员玩家，带 uuid）：仅自己，非管理员返回空
 */
public class AdminAlertSync {
    private static final Logger LOGGER = LoggerFactory.getLogger("xuanjianmod");
    private final ApiClient api;
    private int lastId = 0;
    /** 首次拉取仅推进游标不播报，避免模组启动/服务器重启后全量提示历史申报 */
    private boolean initialized = false;
    /** 官网管理员已绑定的游戏角色 uuid（服务器通道返回） */
    private final List<String> adminUuids = new ArrayList<>();

    public AdminAlertSync(ApiClient api) {
        this.api = api;
    }

    /**
     * 拉取自上次以来新增的待审申报（服务端调用）
     * @return 新增申报列表（id/nickname/amount/reason）
     */
    public List<JsonObject> pollNew() {
        return pollNew(null);
    }

    /**
     * 拉取待审申报（客户端管理员调用：传入当前玩家 uuid）
     * @param uuid 客户端当前玩家 uuid；服务端传 null（带 X-Server-Key）
     */
    public List<JsonObject> pollNew(String uuid) {
        List<JsonObject> result = new ArrayList<>();
        String path = "/api/mod/admin/claims?since=" + lastId
                + (uuid == null || uuid.isBlank() ? "" : "&uuid=" + uuid);
        JsonObject resp = api.get(path);
        if (resp == null) return result; // 403/网络失败：静默（客户端非管理员）
        try {
            if (resp.has("adminUuids") && resp.get("adminUuids").isJsonArray()) {
                adminUuids.clear();
                for (JsonElement e : resp.getAsJsonArray("adminUuids")) {
                    adminUuids.add(e.getAsString());
                }
            }
            JsonArray arr = resp.getAsJsonArray("claims");
            for (int i = 0; i < arr.size(); i++) {
                JsonObject o = arr.get(i).getAsJsonObject();
                int id = o.get("id").getAsInt();
                if (id > lastId) lastId = id;
                result.add(o);
            }
        } catch (Exception e) {
            LOGGER.warn("解析审核提醒失败: {}", e.getMessage());
        }
        if (!initialized) {
            initialized = true;
            return new ArrayList<>();
        }
        return result;
    }

    /** 官网管理员已绑定的游戏角色 uuid 列表（仅服务器通道返回有效） */
    public List<String> getAdminUuids() {
        return adminUuids;
    }
}

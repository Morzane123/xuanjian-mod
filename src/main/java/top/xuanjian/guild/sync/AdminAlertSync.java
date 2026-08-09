package top.xuanjian.guild.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.xuanjian.guild.network.ApiClient;

import java.util.ArrayList;
import java.util.List;

/**
 * 功能10：当有新的贡献点申报待审核时，向游戏内在线管理员发送提醒（轮询增量）
 */
public class AdminAlertSync {
    private static final Logger LOGGER = LoggerFactory.getLogger("xuanjianmod");
    private final ApiClient api;
    private int lastId = 0;

    public AdminAlertSync(ApiClient api) {
        this.api = api;
    }

    /**
     * 拉取自上次以来新增的待审申报
     * @return 新增申报列表（id/nickname/amount/reason）
     */
    public List<JsonObject> pollNew() {
        List<JsonObject> result = new ArrayList<>();
        JsonObject resp = api.get("/api/mod/admin/claims?since=" + lastId);
        if (resp == null) return result;
        try {
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
        return result;
    }
}

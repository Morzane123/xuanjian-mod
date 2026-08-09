package top.xuanjian.guild.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.xuanjian.guild.network.ApiClient;

import java.util.ArrayList;
import java.util.List;

/**
 * 功能5：官网日报、决策更新时游戏内同步广播（轮询增量）
 */
public class UpdateSync {
    private static final Logger LOGGER = LoggerFactory.getLogger("xuanjianmod");
    private final ApiClient api;
    private int lastId = 0;

    public UpdateSync(ApiClient api) {
        this.api = api;
    }

    /**
     * 拉取自上次以来新增的日报/决策
     * @return 新增公告列表（title/type/postId/content）
     */
    public List<JsonObject> pollNew() {
        List<JsonObject> result = new ArrayList<>();
        JsonObject resp = api.get("/api/mod/updates?since=" + lastId);
        if (resp == null) return result;
        try {
            JsonArray arr = resp.getAsJsonArray("updates");
            for (int i = 0; i < arr.size(); i++) {
                JsonObject o = arr.get(i).getAsJsonObject();
                int id = o.get("id").getAsInt();
                if (id > lastId) lastId = id;
                result.add(o);
            }
        } catch (Exception e) {
            LOGGER.warn("解析更新同步失败: {}", e.getMessage());
        }
        return result;
    }
}

package top.xuanjian.guild.task;

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
 * 功能4：官方任务与玩家任务列表/接取/完成（验证码）
 */
public class TaskManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("xuanjianmod");
    private final ApiClient api;

    public TaskManager(ApiClient api) {
        this.api = api;
    }

    /** 任务列表 */
    public List<JsonObject> listTasks(UUID uuid) {
        List<JsonObject> result = new ArrayList<>();
        JsonObject resp = api.get("/api/mod/tasks?uuid=" + uuid);
        if (resp == null) return result;
        try {
            JsonArray arr = resp.getAsJsonArray("tasks");
            for (int i = 0; i < arr.size(); i++) {
                result.add(arr.get(i).getAsJsonObject());
            }
        } catch (Exception e) {
            LOGGER.warn("解析任务列表失败: {}", e.getMessage());
        }
        return result;
    }

    /** 我的任务 */
    public List<JsonObject> myTasks(UUID uuid) {
        List<JsonObject> result = new ArrayList<>();
        JsonObject resp = api.get("/api/mod/tasks/my?uuid=" + uuid);
        if (resp == null) return result;
        try {
            JsonArray arr = resp.getAsJsonArray("claims");
            for (int i = 0; i < arr.size(); i++) {
                result.add(arr.get(i).getAsJsonObject());
            }
        } catch (Exception e) {
            LOGGER.warn("解析我的任务失败: {}", e.getMessage());
        }
        return result;
    }

    /** 接取任务 */
    public JsonObject accept(UUID uuid, int taskId) {
        Map<String, Object> body = new HashMap<>();
        body.put("uuid", uuid.toString());
        return api.post("/api/mod/tasks/" + taskId + "/claim", body);
    }

    /** 提交验证码完成任务 */
    public JsonObject verify(UUID uuid, int taskId, String code) {
        Map<String, Object> body = new HashMap<>();
        body.put("uuid", uuid.toString());
        body.put("code", code);
        return api.post("/api/mod/tasks/" + taskId + "/complete", body);
    }
}

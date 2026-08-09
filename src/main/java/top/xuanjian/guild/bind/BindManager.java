package top.xuanjian.guild.bind;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.xuanjian.guild.network.ApiClient;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 绑定管理：维护 游戏UUID → 官网账号 的映射（本地持久化），
 * 并提供 /api/mod/bind/* 官网接口调用。
 */
public class BindManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("xuanjianmod");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** uuid.toString() -> 官网账号（用户名或昵称） */
    private final Map<String, String> bindings = new HashMap<>();
    private final Path bindingsFile;
    private final ApiClient api;

    public BindManager(Path configDir, ApiClient api) {
        this.bindingsFile = configDir.resolve("bindings.json");
        this.api = api;
        load();
    }

    @SuppressWarnings("unchecked")
    private void load() {
        if (!Files.exists(bindingsFile)) return;
        try (Reader reader = Files.newBufferedReader(bindingsFile)) {
            Type type = new TypeToken<HashMap<String, String>>() {}.getType();
            Map<String, String> map = GSON.fromJson(reader, type);
            if (map != null) bindings.putAll(map);
            LOGGER.info("已加载 {} 条绑定记录", bindings.size());
        } catch (IOException e) {
            LOGGER.error("加载绑定记录失败", e);
        }
    }

    private void save() {
        try (Writer writer = Files.newBufferedWriter(bindingsFile)) {
            GSON.toJson(bindings, writer);
        } catch (IOException e) {
            LOGGER.error("保存绑定记录失败", e);
        }
    }

    /** 发起绑定请求（官网向该账号邮箱发送确认邮件） */
    public JsonObject requestBind(UUID uuid, String playerName, String accountId) {
        Map<String, Object> body = new HashMap<>();
        body.put("uuid", uuid.toString());
        body.put("playerName", playerName);
        body.put("accountId", accountId);
        return api.post("/api/mod/bind/request", body);
    }

    /** 查询某玩家绑定状态 */
    public JsonObject getBindStatus(UUID uuid) {
        return api.get("/api/mod/bind/status?uuid=" + uuid);
    }

    /** 查询是否已绑定（本地缓存） */
    public boolean isBound(UUID uuid) {
        return bindings.containsKey(uuid.toString());
    }

    /**
     * 从官网同步绑定状态到本地缓存。
     * 邮件确认在官网完成，本地 bindings.json 需要主动同步才能识别已绑定。
     * @return 同步后是否已绑定
     */
    public boolean syncFromServer(UUID uuid) {
        try {
            JsonObject resp = getBindStatus(uuid);
            if (resp == null) return isBound(uuid); // 网络失败：沿用本地缓存
            boolean bound = resp.has("bound") && resp.get("bound").getAsBoolean();
            if (bound) {
                String accountId = resp.has("accountId") ? resp.get("accountId").getAsString() : "";
                recordBinding(uuid, accountId);
            } else {
                removeBinding(uuid);
            }
            return bound;
        } catch (Exception e) {
            LOGGER.warn("同步绑定状态失败: {}", e.getMessage());
            return isBound(uuid);
        }
    }

    /** 记录绑定成功（本地缓存） */
    public void recordBinding(UUID uuid, String accountId) {
        bindings.put(uuid.toString(), accountId);
        save();
    }

    /** 解除绑定 */
    public void removeBinding(UUID uuid) {
        if (bindings.remove(uuid.toString()) != null) save();
    }
}

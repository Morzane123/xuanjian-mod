package top.xuanjian.guild.economy;

import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.xuanjian.guild.network.ApiClient;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 贡献点余额查询与转账（转账需二次确认：先创建待确认转账，再确认执行）
 */
public class ContributionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("xuanjianmod");

    /** 待确认的转账：uuid -> 目标玩家名（并发安全：命令异步化后多线程访问） */
    private final Map<UUID, PendingTransfer> pendingTransfers = new ConcurrentHashMap<>();
    private final ApiClient api;

    public ContributionManager(ApiClient api) {
        this.api = api;
    }

    /** 查询余额 */
    public JsonObject getBalance(UUID uuid) {
        return api.get("/api/mod/balance?uuid=" + uuid);
    }

    /** 发起转账（进入待确认状态） */
    public boolean requestTransfer(UUID fromUuid, String toPlayer, int amount) {
        if (amount <= 0) return false;
        PendingTransfer pt = new PendingTransfer(toPlayer, amount, System.currentTimeMillis());
        pendingTransfers.put(fromUuid, pt);
        return true;
    }

    public PendingTransfer getPending(UUID uuid) {
        PendingTransfer pt = pendingTransfers.get(uuid);
        if (pt == null) return null;
        // 120 秒未确认自动过期
        if (System.currentTimeMillis() - pt.createdAt > 120_000) {
            pendingTransfers.remove(uuid);
            return null;
        }
        return pt;
    }

    /** 确认转账 */
    public JsonObject confirmTransfer(UUID fromUuid) {
        PendingTransfer pt = pendingTransfers.remove(fromUuid);
        if (pt == null) return null;
        Map<String, Object> body = new HashMap<>();
        body.put("uuid", fromUuid.toString());      // 供 playerAuth 鉴权
        body.put("fromUuid", fromUuid.toString());  // 供 transfer 逻辑使用
        body.put("toPlayer", pt.toPlayer);
        body.put("amount", pt.amount);
        return api.post("/api/mod/transfer", body);
    }

    /** 取消待确认转账 */
    public void cancelTransfer(UUID uuid) {
        pendingTransfers.remove(uuid);
    }

    public static class PendingTransfer {
        public final String toPlayer;
        public final int amount;
        public final long createdAt;

        PendingTransfer(String toPlayer, int amount, long createdAt) {
            this.toPlayer = toPlayer;
            this.amount = amount;
            this.createdAt = createdAt;
        }
    }
}

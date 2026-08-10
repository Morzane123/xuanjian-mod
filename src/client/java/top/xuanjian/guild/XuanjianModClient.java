package top.xuanjian.guild;

import com.google.gson.JsonObject;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.xuanjian.guild.command.ClientCommandActor;
import top.xuanjian.guild.command.XjCommand;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 客户端入口：将 /xj 命令注册到客户端命令树（无需服务器安装模组即可使用），
 * 并实现客户端自动签到、心跳上报、日报/决策同步与申报审核提醒（仅本地提示当前玩家）。
 * 个人功能（绑定/签到/任务/贡献点/申报/在线查看）全部直连官网 API。
 */
public class XuanjianModClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("xuanjianmod");

    private XuanjianMod mod;
    private long tickCounter = 0;
    /** 当前所在服务器地址（用于上下线上报） */
    private String currentServerAddress = null;

    @Override
    public void onInitializeClient() {
        mod = XuanjianMod.getInstance();
        if (mod == null) {
            LOGGER.warn("[xuanjianmod] 主入口未初始化，客户端功能跳过");
            return;
        }

        // 客户端注册 /xj 命令：本地命令树，输入即补全，不依赖服务器安装模组
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(new XjCommand<FabricClientCommandSource>(
                    mod, src -> ClientCommandActor.from(src.getPlayer())).build());
            LOGGER.info("[xuanjianmod] 客户端命令 /xj 注册完成");
        });

        // 客户端自动签到：加入任意服务器后自动执行官网签到（需已绑定）
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.player == null) return;
            UUID uuid = client.player.getUUID();
            currentServerAddress = currentServer(client);
            reportJoin(client); // 上线上报（白名单服务器）
            mod.getBindManager().syncFromServer(uuid); // 邮件确认后本地缓存可能未更新，先同步官网状态
            if (!mod.getBindManager().isBound(uuid)) return;
            String name = client.player.getName().getString();
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(3000); // 等待连接稳定
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                JsonObject resp = mod.getCheckinManager().checkin(uuid, name);
                client.execute(() -> {
                    if (client.player == null) return;
                    if (resp == null) {
                        client.player.sendSystemMessage(
                                Component.literal("§c[玄剑] 自动签到失败：官网服务不可用"));
                    } else if (resp.has("error")) {
                        String err = resp.get("error").getAsString();
                        if (err.contains("今日已签到")) {
                            client.player.sendSystemMessage(
                                    Component.literal("§7[玄剑] 今日已在官网签到"));
                        } else {
                            client.player.sendSystemMessage(
                                    Component.literal("§c[玄剑] 自动签到失败：" + err));
                        }
                    } else {
                        int reward = resp.has("rewardPoints") ? resp.get("rewardPoints").getAsInt() : 0;
                        int total = resp.has("totalContribution") ? resp.get("totalContribution").getAsInt() : 0;
                        client.player.sendSystemMessage(
                                Component.literal("§a[玄剑] 官网签到成功，获得 " + reward + " 贡献点（余额 " + total + "）"));
                    }
                });
            });
        });

        // 客户端下线上报（崩溃/断网时收不到，由在线记录 TTL 过期兜底）
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (currentServerAddress == null || client.player == null) return;
            UUID uuid = client.player.getUUID();
            String server = currentServerAddress;
            currentServerAddress = null;
            Map<String, Object> body = new HashMap<>();
            body.put("uuid", uuid.toString());
            body.put("server", server);
            CompletableFuture.runAsync(() -> mod.getApi().post("/api/mod/online/leave", body));
        });

        // 客户端周期任务：在线即心跳上报 + 日报/决策同步 + 申报审核提醒
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                tickCounter = 0;
                return;
            }
            tickCounter++;
            if (tickCounter % 20 != 0) return; // 每秒一次
            long seconds = tickCounter / 20;
            int heartbeatInterval = Math.max(mod.getConfig().getHeartbeatInterval(), 30);
            int syncInterval = Math.max(mod.getConfig().getSyncInterval(), 30);
            if (seconds % heartbeatInterval == 0) {
                doHeartbeat(client);
            }
            if (seconds % syncInterval == 0) {
                doSync(client);
            }
        });
    }

    /** 心跳上报：检测玩家在线即周期上报（仅已绑定角色参与官网活跃统计） */
    private void doHeartbeat(Minecraft client) {
        UUID uuid = client.player.getUUID();
        if (!mod.getBindManager().isBound(uuid)) return;
        Map<String, Object> body = new HashMap<>();
        body.put("players", List.of(uuid.toString()));
        CompletableFuture.runAsync(() -> mod.getApi().post("/api/mod/heartbeat", body));
        reportJoin(client); // 在线续报（刷新 mod_online 有效期）
    }

    /** 获取当前服务器地址（单机/未知返回 null） */
    private String currentServer(Minecraft client) {
        try {
            ServerData data = client.getCurrentServer();
            if (data != null && data.address != null && !data.address.isBlank()) {
                return data.address;
            }
        } catch (Exception e) {
            LOGGER.warn("获取服务器地址失败: {}", e.getMessage());
        }
        return null;
    }

    /** 上线上报（仅已绑定玩家；服务器地址不在官网白名单则后端忽略） */
    private void reportJoin(Minecraft client) {
        String server = currentServerAddress != null ? currentServerAddress : currentServer(client);
        if (server == null || server.isBlank()) return;
        UUID uuid = client.player.getUUID();
        if (!mod.getBindManager().isBound(uuid)) return;
        Map<String, Object> body = new HashMap<>();
        body.put("uuid", uuid.toString());
        body.put("server", server);
        CompletableFuture.runAsync(() -> mod.getApi().post("/api/mod/online/join", body));
    }

    /** 日报/决策同步 + 申报审核提醒（本地聊天栏提示当前玩家） */
    private void doSync(Minecraft client) {
        final UUID uuid = client.player.getUUID();
        CompletableFuture.runAsync(() -> {
            // 先同步官网绑定状态到本地缓存（邮件确认后自动生效）
            boolean bound = mod.getBindManager().syncFromServer(uuid);
            // 功能5：日报/决策更新（仅已绑定玩家才提示）
            try {
                if (bound) {
                    List<JsonObject> updates = mod.getUpdateSync().pollNew();
                    for (JsonObject u : updates) {
                        String type = u.has("type") ? u.get("type").getAsString() : "";
                        String title = u.has("title") ? u.get("title").getAsString() : "";
                        String typeText = "daily".equals(type) ? "日报" : "决策公示";
                        show(client, "§e[玄剑] 官网发布新" + typeText + "：§f" + title + " §7→ xuanjian.top");
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("客户端日报同步异常: {}", e.getMessage());
            }

            // 功能10：新申报审核提醒（带 uuid，仅官网管理员能拉取到）
            try {
                List<JsonObject> claims = mod.getAdminAlertSync().pollNew(uuid.toString());
                for (JsonObject c : claims) {
                    String nickname = c.has("nickname") ? c.get("nickname").getAsString() : "玩家";
                    int amount = c.has("amount") ? c.get("amount").getAsInt() : 0;
                    show(client, "§e[玄剑] 新的贡献点申报待审核：§f" + nickname + " §7申报 §a" + amount + " §7贡献点，请前往官网管理后台处理");
                }
            } catch (Exception e) {
                LOGGER.warn("客户端申报提醒同步异常: {}", e.getMessage());
            }
        });
    }

    /** 回渲染线程显示消息 */
    private void show(Minecraft client, String msg) {
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendSystemMessage(Component.literal(msg));
            }
        });
    }
}

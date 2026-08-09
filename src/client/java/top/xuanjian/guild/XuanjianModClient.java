package top.xuanjian.guild;

import com.google.gson.JsonObject;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.xuanjian.guild.command.ClientCommandActor;
import top.xuanjian.guild.command.XjCommand;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 客户端入口：将 /xj 命令注册到客户端命令树（无需服务器安装模组即可使用），
 * 并实现客户端自动签到。个人功能（绑定/签到/任务/贡献点/申报/在线查看）全部直连官网 API。
 */
public class XuanjianModClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("xuanjianmod");

    @Override
    public void onInitializeClient() {
        XuanjianMod mod = XuanjianMod.getInstance();
        if (mod == null) {
            LOGGER.warn("[xuanjianmod] 主入口未初始化，客户端命令注册跳过");
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
                        client.player.displayClientMessage(
                                Component.literal("§c[玄剑] 自动签到失败：官网服务不可用"), false);
                    } else if (resp.has("error")) {
                        String err = resp.get("error").getAsString();
                        if (err.contains("今日已签到")) {
                            client.player.displayClientMessage(
                                    Component.literal("§7[玄剑] 今日已在官网签到"), false);
                        } else {
                            client.player.displayClientMessage(
                                    Component.literal("§c[玄剑] 自动签到失败：" + err), false);
                        }
                    } else {
                        int reward = resp.has("rewardPoints") ? resp.get("rewardPoints").getAsInt() : 0;
                        int total = resp.has("totalContribution") ? resp.get("totalContribution").getAsInt() : 0;
                        client.player.displayClientMessage(
                                Component.literal("§a[玄剑] 官网签到成功，获得 " + reward + " 贡献点（余额 " + total + "）"), false);
                    }
                });
            });
        });
    }
}

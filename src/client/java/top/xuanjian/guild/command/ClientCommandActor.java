package top.xuanjian.guild.command;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * 客户端命令执行者：本地玩家，消息显示在客户端聊天栏
 */
public class ClientCommandActor implements CommandActor {

    private static final Logger LOGGER = LoggerFactory.getLogger("xuanjianmod");

    private final LocalPlayer player;

    private ClientCommandActor(LocalPlayer player) {
        this.player = player;
    }

    public static ClientCommandActor from(LocalPlayer player) {
        if (player == null) return null;
        return new ClientCommandActor(player);
    }

    @Override
    public UUID getUuid() {
        return player.getUUID();
    }

    @Override
    public String getPlayerName() {
        return player.getName().getString();
    }

    @Override
    public void sendMessage(String msg) {
        // 后台线程调用时回渲染线程显示，避免跨线程操作客户端
        Minecraft.getInstance().execute(() -> player.sendSystemMessage(Component.literal(msg)));
    }

    @Override
    public boolean isValid() {
        return player != null;
    }

    @Override
    public void openGui() {
        LOGGER.info("[xuanjianmod] 打开主界面");
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.gui.setScreen(new top.xuanjian.guild.gui.XuanjianMainScreen()));
    }

    @Override
    public void openSettings() {
        LOGGER.info("[xuanjianmod] 执行打开设置页");
        Minecraft mc = Minecraft.getInstance();
        // 26.2 的 Minecraft 没有公开的 screen 字段（26.1 才有），父界面传 null：
        // 设置页「返回」会落到主界面（见 XuanjianConfigScreen.onClose），而不是直接关掉 GUI。
        mc.execute(() -> mc.gui.setScreen(new top.xuanjian.guild.gui.XuanjianConfigScreen(null)));
    }
}

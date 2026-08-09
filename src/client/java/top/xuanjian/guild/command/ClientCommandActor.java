package top.xuanjian.guild.command;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * 客户端命令执行者：本地玩家，消息显示在客户端聊天栏
 */
public class ClientCommandActor implements CommandActor {

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
        player.displayClientMessage(Component.literal(msg), false);
    }

    @Override
    public boolean isValid() {
        return player != null;
    }
}

package top.xuanjian.guild.command;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
        player.sendSystemMessage(Component.literal(msg));
    }

    @Override
    public boolean isValid() {
        return player != null;
    }

    @Override
    public List<String> getLocalOnlinePlayers() {
        try {
            if (player.connection == null) return null;
            Collection<PlayerInfo> infos = player.connection.getOnlinePlayers();
            if (infos == null || infos.isEmpty()) return new ArrayList<>();
            List<String> names = new ArrayList<>();
            for (PlayerInfo info : infos) {
                names.add(info.getProfile().getName());
            }
            return names;
        } catch (Exception e) {
            return null; // 读取失败：回退官网查询
        }
    }
}

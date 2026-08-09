package top.xuanjian.guild.command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * 服务端命令执行者：从 CommandSourceStack 提取玩家，消息通过系统消息发送
 */
public class ServerCommandActor implements CommandActor {

    private final ServerPlayer player;

    private ServerCommandActor(ServerPlayer player) {
        this.player = player;
    }

    /** 从命令上下文解析；非玩家来源（控制台/命令方块）返回 null */
    public static ServerCommandActor from(CommandSourceStack source) {
        try {
            return new ServerCommandActor(source.getPlayerOrException());
        } catch (Exception e) {
            return null;
        }
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
}

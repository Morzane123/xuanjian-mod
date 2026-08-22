package top.xuanjian.guild.command;

import java.util.UUID;

/**
 * 命令执行者抽象：屏蔽服务端/客户端差异。
 * 服务端命令与客户端命令共用同一套业务逻辑，仅执行者实现不同。
 */
public interface CommandActor {

    /** 当前玩家 UUID */
    UUID getUuid();

    /** 当前玩家游戏名 */
    String getPlayerName();

    /** 向执行者发送消息（服务端=系统消息，客户端=本地聊天栏） */
    void sendMessage(String msg);

    /** 执行者是否有效（如尚未进入世界 / 不在服务器内） */
    boolean isValid();

    /** 打开信息面板（默认 no-op；客户端执行者实现） */
    default void openGui() {
    }

    /** 打开设置页（默认 no-op；客户端执行者实现） */
    default void openSettings() {
    }
}

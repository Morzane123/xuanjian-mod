package top.xuanjian.guild.command;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 命令执行者抽象：屏蔽服务端/客户端差异。
 * 服务端命令与客户端命令共用同一套业务逻辑，仅执行者实现不同。
 */
public interface CommandActor {

    /** 当前玩家 UUID */
    UUID getUuid();

    /** 当前玩家游戏名 */
    String getPlayerName();

    /**
     * 向执行者发送消息（服务端=系统消息，客户端=本地聊天栏）。
     * 实现需保证线程安全：无论从主线程还是后台线程调用都能正确送达。
     */
    void sendMessage(String msg);

    /** 执行者是否有效（如尚未进入世界 / 不在服务器内） */
    boolean isValid();

    /**
     * 在后台线程执行耗时任务（网络请求等），避免阻塞游戏主线程导致卡顿。
     * 任务内通过 {@link #sendMessage(String)} 回发消息（实现内部会切回主线程）。
     */
    default void runAsync(Runnable task) {
        CompletableFuture.runAsync(task);
    }

    /** 打开信息面板（仅客户端实现有效，服务端为 no-op） */
    default void openGui() {
    }

    /** 打开设置页（仅客户端实现有效，服务端为 no-op） */
    default void openSettings() {
    }
}

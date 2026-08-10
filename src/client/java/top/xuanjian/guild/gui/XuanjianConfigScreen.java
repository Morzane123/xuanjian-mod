package top.xuanjian.guild.gui;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.xuanjian.guild.XuanjianMod;
import top.xuanjian.guild.config.ModConfig;

/**
 * 玄剑公会模组设置页（基于 Cloth Config 自动生成）。
 * /xj settings 打开：官网地址、服务器信息、周期任务间隔等。
 * 保存时写回 ModConfig（config/xuanjianmod.properties）并重新应用。
 */
public class XuanjianConfigScreen {

    private XuanjianConfigScreen() {
    }

    public static Screen create(Screen parent) {
        XuanjianMod mod = XuanjianMod.getInstance();
        if (mod == null) {
            return parent != null ? parent : new XuanjianInfoScreen();
        }
        ModConfig config = mod.getConfig();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("玄剑公会模组设置"));
        builder.setSavingRunnable(() -> {
            config.save();
            mod.applyConfig();
        });

        ConfigEntryBuilder e = builder.entryBuilder();

        builder.getOrCreateCategory(Component.literal("官网"))
                .addEntry(e.startStrField(Component.literal("官网地址"), config.getApiBase())
                        .setDefaultValue("https://xuanjian.top")
                        .setSaveConsumer(config::setApiBase)
                        .build());

        builder.getOrCreateCategory(Component.literal("服务器"))
                .addEntry(e.startStrField(Component.literal("本服地址（可选）"), config.getServerIp())
                        .setTooltip(Component.literal("填写后 /xj online 优先查询本服在线玩家；留空则查询全网玄剑玩家"))
                        .setDefaultValue("")
                        .setSaveConsumer(config::setServerIp)
                        .build())
                .addEntry(e.startStrField(Component.literal("服务器 Key（已弃用）"), config.getServerKey())
                        .setTooltip(Component.literal("在线状态已改为客户端上下线上报，此配置不再使用，可留空"))
                        .setDefaultValue("")
                        .setSaveConsumer(config::setServerKey)
                        .build());

        builder.getOrCreateCategory(Component.literal("周期任务"))
                .addEntry(e.startIntField(Component.literal("同步间隔（秒）"), config.getSyncInterval())
                        .setDefaultValue(60).setMin(30)
                        .setSaveConsumer(config::setSyncInterval)
                        .build())
                .addEntry(e.startIntField(Component.literal("心跳间隔（秒）"), config.getHeartbeatInterval())
                        .setDefaultValue(1800).setMin(30)
                        .setSaveConsumer(config::setHeartbeatInterval)
                        .build());

        return builder.build();
    }
}

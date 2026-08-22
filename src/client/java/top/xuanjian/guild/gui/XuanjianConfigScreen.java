package top.xuanjian.guild.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.xuanjian.guild.XuanjianMod;
import top.xuanjian.guild.config.ModConfig;

/**
 * 玄剑公会模组设置页（vanilla 自写，不依赖任何外部库）。
 * /xj settings 打开：官网地址、本服地址、同步/心跳间隔等。
 * 保存时写回 ModConfig（config/xuanjianmod.properties）并重新应用。
 * 适配 Minecraft 26.1：渲染入口 render(GuiGraphics)，文本用 gui.drawString；
 * 屏幕切换用 Minecraft.setScreen（26.2 才移到 Minecraft.gui）。
 */
public class XuanjianConfigScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger("xuanjianmod");

    private final Screen parent;
    private EditBox apiBaseBox;
    private EditBox serverIpBox;
    private EditBox syncIntervalBox;
    private EditBox heartbeatIntervalBox;
    private String message = "";

    public XuanjianConfigScreen(Screen parent) {
        super(Component.literal("玄剑公会模组设置"));
        this.parent = parent;
        LOGGER.info("[xuanjianmod] 设置页创建成功");
    }

    public static Screen create(Screen parent) {
        return new XuanjianConfigScreen(parent);
    }

    @Override
    public void init() {
        LOGGER.info("[xuanjianmod] 设置页初始化，尺寸 {}x{}", this.width, this.height);
        int centerX = this.width / 2;
        int y = 40;
        ModConfig config = currentConfig();

        this.apiBaseBox = new EditBox(this.font, centerX - 140, y, 280, 20, Component.literal("官网地址"));
        this.apiBaseBox.setMaxLength(200);
        this.apiBaseBox.setValue(config != null ? config.getApiBase() : "https://xuanjian.top");
        this.apiBaseBox.setResponder(s -> message = "");
        addRenderableWidget(this.apiBaseBox);
        y += 28;

        this.serverIpBox = new EditBox(this.font, centerX - 140, y, 280, 20, Component.literal("本服地址"));
        this.serverIpBox.setMaxLength(120);
        this.serverIpBox.setValue(config != null ? config.getServerIp() : "");
        this.serverIpBox.setResponder(s -> message = "");
        addRenderableWidget(this.serverIpBox);
        y += 28;

        this.syncIntervalBox = new EditBox(this.font, centerX - 140, y, 280, 20, Component.literal("同步间隔（秒）"));
        this.syncIntervalBox.setMaxLength(6);
        this.syncIntervalBox.setFilter(s -> s.matches("\\d*"));
        this.syncIntervalBox.setValue(String.valueOf(config != null ? config.getSyncInterval() : 60));
        this.syncIntervalBox.setResponder(s -> message = "");
        addRenderableWidget(this.syncIntervalBox);
        y += 28;

        this.heartbeatIntervalBox = new EditBox(this.font, centerX - 140, y, 280, 20, Component.literal("心跳间隔（秒）"));
        this.heartbeatIntervalBox.setMaxLength(6);
        this.heartbeatIntervalBox.setFilter(s -> s.matches("\\d*"));
        this.heartbeatIntervalBox.setValue(String.valueOf(config != null ? config.getHeartbeatInterval() : 1800));
        this.heartbeatIntervalBox.setResponder(s -> message = "");
        addRenderableWidget(this.heartbeatIntervalBox);
        y += 40;

        int bottom = this.height - 30;
        addRenderableWidget(Button.builder(Component.literal("保存"), b -> save())
                .bounds(centerX - 128, bottom, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("返回"), b -> this.onClose())
                .bounds(centerX - 40, bottom, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("重置默认"), b -> resetDefaults())
                .bounds(centerX + 48, bottom, 80, 20).build());

        setInitialFocus(this.apiBaseBox);
    }

    private static ModConfig currentConfig() {
        XuanjianMod mod = XuanjianMod.getInstance();
        return mod != null ? mod.getConfig() : null;
    }

    private void save() {
        ModConfig config = currentConfig();
        if (config == null) {
            message = "§c模组主入口未初始化，无法保存";
            return;
        }
        int sync;
        int heartbeat;
        try {
            sync = Math.max(30, Integer.parseInt(syncIntervalBox.getValue().trim()));
            heartbeat = Math.max(30, Integer.parseInt(heartbeatIntervalBox.getValue().trim()));
        } catch (NumberFormatException e) {
            message = "§c间隔必须为数字（最小 30）";
            return;
        }
        String api = apiBaseBox.getValue().trim();
        if (api.isEmpty()) {
            message = "§c官网地址不能为空";
            return;
        }
        config.setApiBase(api);
        config.setServerIp(serverIpBox.getValue().trim());
        config.setSyncInterval(sync);
        config.setHeartbeatInterval(heartbeat);
        config.save();
        XuanjianMod mod = XuanjianMod.getInstance();
        if (mod != null) mod.applyConfig();
        message = "§a已保存并生效";
        LOGGER.info("[xuanjianmod] 设置已保存");
    }

    private void resetDefaults() {
        apiBaseBox.setValue("https://xuanjian.top");
        serverIpBox.setValue("");
        syncIntervalBox.setValue("60");
        heartbeatIntervalBox.setValue("1800");
        message = "§e已填回默认值，点击保存生效";
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        this.renderBackground(gui, mouseX, mouseY, delta);
        gui.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        int centerX = this.width / 2;
        int y = 40;
        gui.drawString(this.font, "官网地址", centerX - 140 - 6 - this.font.width("官网地址"), y + 6, 0xA0A0A0);
        y += 28;
        gui.drawString(this.font, "本服地址（可选）", centerX - 140 - 6 - this.font.width("本服地址（可选）"), y + 6, 0xA0A0A0);
        y += 28;
        gui.drawString(this.font, "同步间隔（秒，≥30）", centerX - 140 - 6 - this.font.width("同步间隔（秒，≥30）"), y + 6, 0xA0A0A0);
        y += 28;
        gui.drawString(this.font, "心跳间隔（秒，≥30）", centerX - 140 - 6 - this.font.width("心跳间隔（秒，≥30）"), y + 6, 0xA0A0A0);
        y += 40;
        gui.drawString(this.font, "服务器 Key 已弃用（在线状态改用客户端上下线上报），无需填写。", centerX - 140, y, 0x707070);
        if (!message.isEmpty()) {
            gui.drawString(this.font, message, centerX - 140, this.height - 52, 0xFFFFFF);
        }
        super.render(gui, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            save();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
package top.xuanjian.guild.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.xuanjian.guild.XuanjianMod;
import top.xuanjian.guild.config.ModConfig;
import top.xuanjian.guild.gui.widget.XjButton;

/**
 * 玄剑公会模组设置页（与主界面同一套视觉）。
 *
 * 26.2 约定同 {@link XuanjianMainScreen}：extractRenderState + 8 位 ARGB 颜色 +
 * {@code Minecraft.gui.setScreen(...)} 切界面。
 */
public class XuanjianConfigScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger("xuanjianmod");

    private static final int FIELD_W = 240;
    private static final int ROW_H = 26;

    private final Screen parent;
    private EditBox apiBaseBox;
    private EditBox serverIpBox;
    private EditBox syncIntervalBox;
    private EditBox heartbeatIntervalBox;
    private String message = "";
    private int messageColor = UiTheme.SUCCESS;

    private int px, py, pw, ph;

    public XuanjianConfigScreen(Screen parent) {
        super(Component.literal("玄剑公会模组设置"));
        this.parent = parent;
    }

    private void computeLayout() {
        pw = Math.max(300, Math.min(this.width - 40, 460));
        ph = Math.max(190, Math.min(this.height - 40, 240));
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
    }

    @Override
    protected void init() {
        computeLayout();
        ModConfig config = currentConfig();

        int fieldX = px + (pw - FIELD_W) / 2;
        int y = py + UiTheme.HEADER_H + 16;

        apiBaseBox = addField(fieldX, y, 200, "官网地址",
                config != null ? config.getApiBase() : ModConfig.DEFAULT_API_BASE);
        y += ROW_H;
        serverIpBox = addField(fieldX, y, 120, "本服地址（可选）",
                config != null ? config.getServerIp() : "");
        y += ROW_H;
        syncIntervalBox = addField(fieldX, y, 6, "同步间隔（秒）",
                String.valueOf(config != null ? config.getSyncInterval() : ModConfig.DEFAULT_SYNC_INTERVAL));
        y += ROW_H;
        heartbeatIntervalBox = addField(fieldX, y, 6, "心跳间隔（秒）",
                String.valueOf(config != null ? config.getHeartbeatInterval() : ModConfig.DEFAULT_HEARTBEAT_INTERVAL));

        int btnY = py + ph - 26;
        int centerX = px + pw / 2;
        this.addRenderableWidget(new XjButton(centerX - 126, btnY, 80, UiTheme.BTN_H, "保存",
                XjButton.Style.PRIMARY, this::save));
        this.addRenderableWidget(new XjButton(centerX - 40, btnY, 80, UiTheme.BTN_H, "返回",
                XjButton.Style.SECONDARY, this::onClose));
        this.addRenderableWidget(new XjButton(centerX + 46, btnY, 80, UiTheme.BTN_H, "重置默认",
                XjButton.Style.SECONDARY, this::resetDefaults));
    }

    private EditBox addField(int x, int y, int maxLength, String label, String value) {
        EditBox box = new EditBox(this.font, x, y, FIELD_W, 18, Component.literal(label));
        box.setMaxLength(maxLength);
        box.setValue(value == null ? "" : value);
        box.setResponder(s -> message = "");
        this.addRenderableWidget(box);
        return box;
    }

    private static ModConfig currentConfig() {
        XuanjianMod mod = XuanjianMod.getInstance();
        return mod != null ? mod.getConfig() : null;
    }

    private void note(String text, int color) {
        this.message = text;
        this.messageColor = color;
    }

    private void save() {
        ModConfig config = currentConfig();
        if (config == null) { note("模组主入口未初始化，无法保存", UiTheme.DANGER); return; }
        int sync, heartbeat;
        try {
            sync = Math.max(30, Integer.parseInt(syncIntervalBox.getValue().trim()));
            heartbeat = Math.max(30, Integer.parseInt(heartbeatIntervalBox.getValue().trim()));
        } catch (NumberFormatException e) {
            note("间隔必须为数字（最小 30）", UiTheme.DANGER);
            return;
        }
        String api = apiBaseBox.getValue().trim();
        if (api.isEmpty()) { note("官网地址不能为空", UiTheme.DANGER); return; }

        config.setApiBase(api);
        config.setServerIp(serverIpBox.getValue().trim());
        config.setSyncInterval(sync);
        config.setHeartbeatInterval(heartbeat);
        config.save();
        XuanjianMod mod = XuanjianMod.getInstance();
        if (mod != null) mod.applyConfig();
        note("已保存并生效", UiTheme.SUCCESS);
        LOGGER.info("[xuanjianmod] 设置已保存");
    }

    private void resetDefaults() {
        apiBaseBox.setValue(ModConfig.DEFAULT_API_BASE);
        serverIpBox.setValue("");
        syncIntervalBox.setValue(String.valueOf(ModConfig.DEFAULT_SYNC_INTERVAL));
        heartbeatIntervalBox.setValue(String.valueOf(ModConfig.DEFAULT_HEARTBEAT_INTERVAL));
        note("已填回默认值，点击「保存」后生效", UiTheme.WARNING);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        computeLayout();

        // 背景（在 widget 之下）
        UiTheme.panel(g, px, py, pw, ph);
        UiTheme.headerBar(g, px + 1, py + 1, pw - 2, UiTheme.HEADER_H - 1);

        // widget
        super.extractRenderState(g, mouseX, mouseY, delta);

        // 前景
        UiTheme.text(g, this.font, "模组设置", px + 10, UiTheme.vCenter(this.font, py, UiTheme.HEADER_H), UiTheme.TEXT);
        UiTheme.divider(g, px + 8, px + pw - 8, py + UiTheme.HEADER_H + 4);

        int labelRight = px + (pw - FIELD_W) / 2 - 10;
        int y = py + UiTheme.HEADER_H + 16;
        drawLabel(g, "官网地址", labelRight, y);
        y += ROW_H;
        drawLabel(g, "本服地址", labelRight, y);
        y += ROW_H;
        drawLabel(g, "同步间隔", labelRight, y);
        y += ROW_H;
        drawLabel(g, "心跳间隔", labelRight, y);
        y += ROW_H + 2;

        UiTheme.text(g, this.font, "同步间隔＝任务/余额轮询周期；心跳间隔＝在线状态上报周期（均≥30 秒）",
                px + 12, y, UiTheme.TEXT_MUTED);

        if (message != null && !message.isEmpty()) {
            UiTheme.textCenter(g, this.font, message, px + pw / 2, py + ph - 40, messageColor);
        }
    }

    /** 标签右对齐到输入框左侧，垂直与 18 高的输入框居中 */
    private void drawLabel(GuiGraphicsExtractor g, String text, int rightX, int boxY) {
        UiTheme.textRight(g, this.font, text, rightX, boxY + 5, UiTheme.TEXT_DIM);
    }

    @Override
    public void onClose() {
        // 26.2 切界面走 Minecraft.gui.setScreen（26.1 是 Minecraft.setScreen）。
        // 没有父界面时回到主界面，而不是直接把 GUI 关掉，避免「设置→返回」直接退出。
        Screen target = this.parent != null ? this.parent : new XuanjianMainScreen();
        this.minecraft.gui.setScreen(target);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

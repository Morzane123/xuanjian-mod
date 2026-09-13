package top.xuanjian.guild.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.xuanjian.guild.XuanjianMod;
import top.xuanjian.guild.config.ModConfig;

/**
 * 玄剑公会模组设置页（纯 vanilla Screen，不依赖任何外部库）。
 * /xj settings 打开：官网地址、本服地址、同步/心跳间隔。
 *
 * <p>Minecraft 26.1 客户端 GUI 约定同 {@link XuanjianInfoScreen}：
 * 渲染入口 {@code extractRenderState(GuiGraphicsExtractor, int, int, float)} + {@code super} 调用；
 * 颜色必须 8 位 ARGB（写成 0xFFFFFF 会因 alpha=0 完全透明而看不见）。
 */
public class XuanjianConfigScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger("xuanjianmod");

    private static final int C_TITLE = 0xFFFFFFFF;
    private static final int C_LABEL = 0xFFBFBFBF;
    private static final int C_HINT = 0xFF8C8C8C;
    private static final int C_OK = 0xFF55FF55;
    private static final int C_ERR = 0xFFFF5555;
    private static final int C_WARN = 0xFFFFDD55;
    private static final int C_PANEL = 0xC0000000;
    private static final int C_PANEL_BORDER = 0x55FFFFFF;

    private static final int FIELD_WIDTH = 240;
    private static final int LABEL_WIDTH = 116;
    private static final int ROW_HEIGHT = 26;

    private final Screen parent;
    private EditBox apiBaseBox;
    private EditBox serverIpBox;
    private EditBox syncIntervalBox;
    private EditBox heartbeatIntervalBox;
    private String message = "";
    private int messageColor = C_OK;

    public XuanjianConfigScreen(Screen parent) {
        super(Component.literal("玄剑公会模组设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        LOGGER.info("[xuanjianmod] 设置页初始化，尺寸 {}x{}", this.width, this.height);
        ModConfig config = currentConfig();

        int fieldX = this.width / 2 - FIELD_WIDTH / 2 + LABEL_WIDTH / 2;
        int y = 52;

        this.apiBaseBox = addField(fieldX, y, 200, "官网地址",
                config != null ? config.getApiBase() : ModConfig.DEFAULT_API_BASE);
        y += ROW_HEIGHT;
        this.serverIpBox = addField(fieldX, y, 120, "本服地址（可选）",
                config != null ? config.getServerIp() : "");
        y += ROW_HEIGHT;
        this.syncIntervalBox = addField(fieldX, y, 6, "同步间隔（秒）",
                String.valueOf(config != null ? config.getSyncInterval() : ModConfig.DEFAULT_SYNC_INTERVAL));
        y += ROW_HEIGHT;
        this.heartbeatIntervalBox = addField(fieldX, y, 6, "心跳间隔（秒）",
                String.valueOf(config != null ? config.getHeartbeatInterval() : ModConfig.DEFAULT_HEARTBEAT_INTERVAL));

        int centerX = this.width / 2;
        int bottom = this.height - 30;
        this.addRenderableWidget(Button.builder(Component.literal("保存"), b -> save())
                .bounds(centerX - 128, bottom, 80, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("返回"), b -> this.onClose())
                .bounds(centerX - 40, bottom, 80, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("重置默认"), b -> resetDefaults())
                .bounds(centerX + 48, bottom, 80, 20).build());
    }

    private EditBox addField(int x, int y, int maxLength, String label, String value) {
        EditBox box = new EditBox(this.font, x, y, FIELD_WIDTH, 20, Component.literal(label));
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
        if (config == null) {
            note("模组主入口未初始化，无法保存", C_ERR);
            return;
        }
        int sync;
        int heartbeat;
        try {
            sync = Math.max(30, Integer.parseInt(syncIntervalBox.getValue().trim()));
            heartbeat = Math.max(30, Integer.parseInt(heartbeatIntervalBox.getValue().trim()));
        } catch (NumberFormatException e) {
            note("间隔必须为数字（最小 30）", C_ERR);
            return;
        }
        String api = apiBaseBox.getValue().trim();
        if (api.isEmpty()) {
            note("官网地址不能为空", C_ERR);
            return;
        }
        config.setApiBase(api);
        config.setServerIp(serverIpBox.getValue().trim());
        config.setSyncInterval(sync);
        config.setHeartbeatInterval(heartbeat);
        config.save();
        XuanjianMod mod = XuanjianMod.getInstance();
        if (mod != null) {
            mod.applyConfig();
        }
        note("已保存并生效", C_OK);
        LOGGER.info("[xuanjianmod] 设置已保存");
    }

    private void resetDefaults() {
        apiBaseBox.setValue(ModConfig.DEFAULT_API_BASE);
        serverIpBox.setValue("");
        syncIntervalBox.setValue(String.valueOf(ModConfig.DEFAULT_SYNC_INTERVAL));
        heartbeatIntervalBox.setValue(String.valueOf(ModConfig.DEFAULT_HEARTBEAT_INTERVAL));
        note("已填回默认值，点击「保存」后生效", C_WARN);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // 必须调用 super：渲染已注册 widget（输入框/按钮）
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        graphics.centeredText(this.font, this.title, centerX, 20, C_TITLE);

        int panelLeft = centerX - FIELD_WIDTH / 2 - 24;
        int panelRight = centerX + FIELD_WIDTH / 2 + 24;
        int panelTop = 44;
        int panelBottom = this.height - 46;
        if (panelBottom > panelTop + 10) {
            graphics.fill(panelLeft, panelTop, panelRight, panelBottom, C_PANEL);
            graphics.outline(panelLeft, panelTop, panelRight, panelBottom, C_PANEL_BORDER);
        }

        int labelX = centerX - FIELD_WIDTH / 2 - 12;
        int y = 52;
        drawRightAligned(graphics, "官网地址", labelX, y, C_LABEL);
        y += ROW_HEIGHT;
        drawRightAligned(graphics, "本服地址（可选）", labelX, y, C_LABEL);
        y += ROW_HEIGHT;
        drawRightAligned(graphics, "同步间隔（秒，≥30）", labelX, y, C_LABEL);
        y += ROW_HEIGHT;
        drawRightAligned(graphics, "心跳间隔（秒，≥30）", labelX, y, C_LABEL);
        y += ROW_HEIGHT + 8;

        graphics.text(this.font, "服务器 Key 已弃用：在线状态改用客户端上下线上报，无需填写。",
                panelLeft + 12, y, C_HINT, true);
        graphics.text(this.font, "同步间隔＝任务/余额轮询周期；心跳间隔＝在线状态上报周期。",
                panelLeft + 12, y + this.font.lineHeight + 2, C_HINT, true);

        if (this.message != null && !this.message.isEmpty()) {
            graphics.centeredText(this.font, this.message, centerX, this.height - 52, messageColor);
        }
    }

    /** 标签右对齐到 labelRight，垂直与 20 高的输入框居中对齐 */
    private void drawRightAligned(GuiGraphicsExtractor graphics, String text, int labelRight, int boxY, int color) {
        graphics.text(this.font, text, labelRight - this.font.width(text), boxY + 6, color, true);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

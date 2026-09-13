package top.xuanjian.guild.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.xuanjian.guild.XuanjianMod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 玄剑公会信息面板（纯 vanilla Screen，不依赖任何外部库）。
 * /xj gui 打开：展示绑定状态、贡献点余额、当前在线的玄剑玩家。
 *
 * <p>Minecraft 26.1 客户端 GUI 约定（与 FabricMC/fabric-docs 的 CustomScreen 参考实现一致）：
 * <ul>
 *   <li>渲染入口是 {@code extractRenderState(GuiGraphicsExtractor, int, int, float)}，
 *       不是 1.21 的 {@code render(GuiGraphics, ...)}；必须调用 {@code super} 才会渲染已注册的 widget。</li>
 *   <li>绘制文字用 {@code graphics.text(font, str, x, y, color, shadow)} / {@code graphics.centeredText(...)}。</li>
 *   <li>背景遮罩（模糊/菜单背景）由框架在 {@code extractRenderStateWithTooltipAndSubtitles} 里调 {@code extractBackground} 完成，
 *       这里只需自己补一块半透明面板保证文字可读。</li>
 *   <li>切换界面用 {@code Minecraft.getInstance().setScreen(...)}（26.2 才移到 {@code Minecraft.gui}）。</li>
 *   <li><b>颜色必须是 8 位 ARGB</b>：26.1 的 {@code GuiGraphicsExtractor.text} 没有「alpha 为 0 时自动补 0xFF」的兜底，
 *       写成 {@code 0xFFFFFF} 会得到 alpha=0 的完全透明文字（这正是此前界面「打开但看不到字」的原因）。</li>
 * </ul>
 */
public class XuanjianInfoScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger("xuanjianmod");

    // 一律 8 位 ARGB
    private static final int C_TITLE = 0xFFFFFFFF;
    private static final int C_TEXT = 0xFFE6E6E6;
    private static final int C_DIM = 0xFFAAAAAA;
    private static final int C_OK = 0xFF55FF55;
    private static final int C_WARN = 0xFFFFDD55;
    private static final int C_ERR = 0xFFFF5555;
    private static final int C_PANEL = 0xC0000000;
    private static final int C_PANEL_BORDER = 0x55FFFFFF;

    /** 一行文本与其颜色 */
    private record Line(String text, int color) { }

    private final List<Line> lines = new ArrayList<>();
    private boolean loading = true;

    public XuanjianInfoScreen() {
        super(Component.literal("玄剑公会信息"));
    }

    @Override
    protected void init() {
        LOGGER.info("[xuanjianmod] 信息面板初始化，尺寸 {}x{}", this.width, this.height);
        int centerX = this.width / 2;
        int bottom = this.height - 28;
        this.addRenderableWidget(Button.builder(Component.literal("刷新"), b -> reload())
                .bounds(centerX - 128, bottom, 80, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("设置"), b ->
                        this.minecraft.setScreen(new XuanjianConfigScreen(this)))
                .bounds(centerX - 40, bottom, 80, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("关闭"), b -> this.onClose())
                .bounds(centerX + 48, bottom, 80, 20).build());
        reload();
    }

    /** 后台线程取数据，回到渲染线程刷新列表；失败也要给出可见反馈，不能一直停在「加载中」。 */
    private void reload() {
        loading = true;
        lines.clear();
        Minecraft mc = this.minecraft;
        final UUID uuid = mc.player != null ? mc.player.getUUID() : null;

        CompletableFuture
                .supplyAsync(() -> collect(uuid))
                .exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    LOGGER.warn("[xuanjianmod] 信息面板加载失败: {}", String.valueOf(cause));
                    List<Line> err = new ArrayList<>();
                    err.add(new Line("加载失败：" + cause.getClass().getSimpleName(), C_ERR));
                    String msg = cause.getMessage();
                    if (msg != null && !msg.isBlank()) {
                        err.add(new Line(msg.length() > 60 ? msg.substring(0, 60) + "…" : msg, C_DIM));
                    }
                    err.add(new Line("请检查官网地址与网络连接（/xj settings 可修改）", C_DIM));
                    return err;
                })
                .thenAccept(data -> mc.execute(() -> {
                    lines.clear();
                    lines.addAll(data);
                    loading = false;
                }));
    }

    /** 纯数据收集（在后台线程执行，不要碰任何 GUI 对象） */
    private List<Line> collect(UUID uuid) {
        List<Line> data = new ArrayList<>();
        if (uuid == null) {
            data.add(new Line("未进入世界，无法获取玩家信息", C_ERR));
            return data;
        }
        XuanjianMod mod = XuanjianMod.getInstance();
        if (mod == null) {
            data.add(new Line("模组主入口未初始化", C_ERR));
            return data;
        }

        boolean bound = mod.getBindManager().syncFromServer(uuid);
        data.add(bound
                ? new Line("绑定状态：已绑定官网账号", C_OK)
                : new Line("绑定状态：未绑定（使用 /xj bind <官网账号>）", C_WARN));

        if (!bound) {
            data.add(new Line("绑定官网账号后可查看余额与在线列表", C_DIM));
            return data;
        }

        JsonObject balance = mod.getContributionManager().getBalance(uuid);
        if (balance != null && !balance.has("error") && balance.has("balance")) {
            data.add(new Line("贡献点余额：" + balance.get("balance").getAsInt(), C_WARN));
        }

        data.add(new Line("当前在线的玄剑玩家：", C_TEXT));
        JsonObject online = mod.getApi().get("/api/mod/online");
        if (online == null || !online.has("players")) {
            data.add(new Line("  （官网服务暂不可用）", C_DIM));
            return data;
        }
        JsonArray arr = online.getAsJsonArray("players");
        if (arr.size() == 0) {
            data.add(new Line("  （暂无在线玩家）", C_DIM));
            return data;
        }
        for (int i = 0; i < arr.size() && i < 20; i++) {
            JsonObject o = arr.get(i).getAsJsonObject();
            String name = o.has("name") ? o.get("name").getAsString() : "?";
            String server = o.has("server") ? o.get("server").getAsString() : "";
            data.add(new Line("  " + name + (server.isEmpty() ? "" : "  @" + server), C_TEXT));
        }
        if (arr.size() > 20) {
            data.add(new Line("  … 共 " + arr.size() + " 人", C_DIM));
        }
        return data;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // 必须调用 super：它负责渲染所有已注册 widget（按钮）；背景由框架处理
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int left = 16;
        int right = this.width - 16;
        graphics.centeredText(this.font, this.title, this.width / 2, 12, C_TITLE);
        int y = 12 + this.font.lineHeight + 10;

        int panelTop = y - 5;
        int panelBottom = this.height - 42;
        if (panelBottom > panelTop + 10) {
            graphics.fill(left - 6, panelTop, right + 6, panelBottom, C_PANEL);
            graphics.outline(left - 6, panelTop, right + 6, panelBottom, C_PANEL_BORDER);
        }

        if (loading && lines.isEmpty()) {
            graphics.text(this.font, "正在加载…", left, y, C_DIM, true);
            return;
        }
        int maxY = panelBottom - this.font.lineHeight - 4;
        for (Line line : lines) {
            if (y > maxY) {
                graphics.text(this.font, "…", left, y, C_DIM, true);
                break;
            }
            graphics.text(this.font, line.text(), left, y, line.color(), true);
            y += this.font.lineHeight + 2;
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(null);
    }
}

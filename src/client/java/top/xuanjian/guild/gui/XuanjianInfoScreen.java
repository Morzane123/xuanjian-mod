package top.xuanjian.guild.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
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
 * 玄剑公会信息面板（vanilla Screen，不依赖其他 Mod）。
 * /xj gui 打开：展示绑定状态、贡献点余额、当前在线的玄剑玩家。
 * 网络数据在后台线程加载，渲染线程刷新，避免卡顿。
 * 文本使用标准 StringWidget 渲染（走 widget 渲染管线，兼容性最好）。
 */
public class XuanjianInfoScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger("xuanjianmod");

    /** 展示行：每行以 § 颜色码开头（§a/§e/§c/§7/§f），渲染时解析 */
    private final List<String> lines = new ArrayList<>();
    /** 已添加的文本行 widgets（用于刷新时移除重建） */
    private final List<AbstractWidget> textWidgets = new ArrayList<>();

    public XuanjianInfoScreen() {
        super(Component.literal("玄剑公会信息"));
        LOGGER.info("[xuanjianmod] 信息面板创建成功");
    }

    @Override
    public void init() {
        LOGGER.info("[xuanjianmod] 信息面板初始化，尺寸 {}x{}", this.width, this.height);
        int w = this.width;
        int bottom = this.height - 28;
        this.addRenderableWidget(Button.builder(Component.literal("刷新"), b -> reload())
                .bounds(w / 2 - 128, bottom, 80, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("设置"), b ->
                        Minecraft.getInstance().setScreen(XuanjianConfigScreen.create(this)))
                .bounds(w / 2 - 40, bottom, 80, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("关闭"), b ->
                        this.onClose())
                .bounds(w / 2 + 48, bottom, 80, 20).build());
        reload();
    }

    /** 后台加载数据并刷新展示 */
    private void reload() {
        lines.clear();
        lines.add("§7正在从官网加载数据...");
        rebuildTextWidgets();
        CompletableFuture.runAsync(() -> {
            List<String> data = new ArrayList<>();
            Minecraft mc = Minecraft.getInstance();
            UUID uuid = mc.player != null ? mc.player.getUUID() : null;
            if (uuid == null) {
                data.add("§c未进入服务器，无法获取玩家信息");
            } else {
                XuanjianMod mod = XuanjianMod.getInstance();
                if (mod == null) {
                    data.add("§c模组主入口未初始化");
                } else {
                    // 先同步官网绑定状态（邮件确认后本地缓存可能未更新）
                    boolean bound = mod.getBindManager().syncFromServer(uuid);
                    data.add(bound ? "§a绑定状态：已绑定官网账号" : "§e绑定状态：未绑定（/xj bind <官网账号>）");
                    if (bound) {
                        JsonObject balance = mod.getContributionManager().getBalance(uuid);
                        if (balance != null && !balance.has("error")) {
                            int b = balance.has("balance") ? balance.get("balance").getAsInt() : 0;
                            data.add("§e贡献点余额：§a" + b);
                        }
                        data.add("§e当前在线的玄剑玩家：");
                        JsonObject online = mod.getApi().get("/api/mod/online");
                        if (online != null && online.has("players")) {
                            JsonArray arr = online.getAsJsonArray("players");
                            if (arr.size() == 0) {
                                data.add("  §7（暂无在线玩家）");
                            }
                            for (int i = 0; i < arr.size() && i < 20; i++) {
                                JsonObject o = arr.get(i).getAsJsonObject();
                                String name = o.has("name") ? o.get("name").getAsString() : "?";
                                String server = o.has("server") ? o.get("server").getAsString() : "";
                                data.add("  §f" + name + (server.isEmpty() ? "" : " §7@" + server));
                            }
                            if (arr.size() > 20) {
                                data.add("  §7... 共 " + arr.size() + " 人");
                            }
                        } else {
                            data.add("  §7（官网服务不可用）");
                        }
                    } else {
                        data.add("§7绑定官网账号后可查看余额与在线列表");
                    }
                }
            }
            mc.execute(() -> {
                lines.clear();
                lines.addAll(data);
                rebuildTextWidgets();
            });
        });
    }

    /** 用 StringWidget 重建全部文本行（走标准 widget 渲染管线） */
    private void rebuildTextWidgets() {
        for (AbstractWidget w : textWidgets) {
            this.removeWidget(w);
        }
        textWidgets.clear();
        int y = 26;
        for (String line : lines) {
            StringWidget sw = new StringWidget(16, y, this.width - 32, 12,
                    Component.literal(textOf(line)), this.font);
            sw.setFGColor(colorOf(line));
            this.addRenderableWidget(sw);
            textWidgets.add(sw);
            y += 14;
        }
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        super.render(gui, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        super.onClose();
        LOGGER.info("[xuanjianmod] 信息面板关闭");
    }

    private static String textOf(String line) {
        if (line.length() > 2 && line.charAt(0) == '\u00a7') return line.substring(2);
        return line;
    }

    private static int colorOf(String line) {
        if (line.startsWith("\u00a7a")) return 0x55FF55;
        if (line.startsWith("\u00a7e")) return 0xFFFF55;
        if (line.startsWith("\u00a7c")) return 0xFF5555;
        if (line.startsWith("\u00a77")) return 0xAAAAAA;
        return 0xFFFFFF;
    }
}

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
 * 玄剑公会信息面板（vanilla Screen，不依赖其他 Mod）。
 * /xj gui 打开：展示绑定状态、贡献点余额、当前在线的玄剑玩家。
 * 网络数据在后台线程加载，渲染线程刷新，避免卡顿。
 * 适配 Minecraft 26.1：渲染入口为 extractRenderState(GuiGraphicsExtractor)，文本用 graphics.text(...)；
 * 屏幕切换用 Minecraft.setScreen（26.2 才移到 Minecraft.gui）。
 */
public class XuanjianInfoScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger("xuanjianmod");

    private final List<String> lines = new ArrayList<>();
    private boolean renderLogged = false;

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

    private void reload() {
        lines.clear();
        lines.add("§7正在从官网加载数据...");
        Minecraft mc = Minecraft.getInstance();
        final UUID uuid = mc.player != null ? mc.player.getUUID() : null;
        CompletableFuture.runAsync(() -> {
            List<String> data = new ArrayList<>();
            if (uuid == null) {
                data.add("§c未进入服务器，无法获取玩家信息");
            } else {
                XuanjianMod mod = XuanjianMod.getInstance();
                if (mod == null) {
                    data.add("§c模组主入口未初始化");
                } else {
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
            });
        });
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        if (!renderLogged) {
            renderLogged = true;
            LOGGER.info("[xuanjianmod] 信息面板开始渲染，当前行数 {}", lines.size());
        }
        if (lines.isEmpty()) {
            graphics.text(this.font, "数据加载中...", 16, 26, 0xFFFFFF, true);
            return;
        }
        int y = 26;
        int maxY = this.height - 36;
        for (String line : lines) {
            if (y > maxY) {
                graphics.text(this.font, "...", 16, y, 0xAAAAAA, true);
                break;
            }
            graphics.text(this.font, textOf(line), 16, y, colorOf(line), true);
            y += 14;
        }
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
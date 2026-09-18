package top.xuanjian.guild.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.xuanjian.guild.XuanjianMod;
import top.xuanjian.guild.gui.widget.XjButton;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 玄剑公会模组主界面：左侧导航 + 右侧内容，常用操作全部点选完成。
 *
 * 页面：账号 / 任务 / 申报 / 转账 / 在线
 * 写操作：签到、接取任务、提交验证码、提交申报、转账（二次确认）
 *
 * 26.1 约定：
 *  - 渲染入口 {@code extractRenderState(GuiGraphicsExtractor, int, int, float)}
 *  - 颜色必须 8 位 ARGB
 *  - 绘制顺序：先自绘背景 → 调 super 渲染 widget → 再自绘文字/装饰（保证层级正确）
 */
public class XuanjianMainScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger("xuanjianmod");

    private enum Page {
        ACCOUNT("账号"), TASKS("任务"), CLAIM("申报"), TRANSFER("转账"), ONLINE("在线");
        final String label;
        Page(String label) { this.label = label; }
    }

    private static final int TASKS_PER_PAGE = 6;

    private Page page = Page.ACCOUNT;

    /* ---- 数据 ---- */
    private JsonObject profile;
    private List<JsonObject> tasks = new ArrayList<>();
    private List<JsonObject> myTasks = new ArrayList<>();
    private List<JsonObject> online = new ArrayList<>();
    private boolean loading = true;
    private String loadError = "";

    /* ---- 提示 ---- */
    private String toast = "";
    private int toastColor = UiTheme.SUCCESS;
    private long toastUntil = 0;

    /* ---- 交互态 ---- */
    private int taskPage = 0;
    private int verifyTaskId = -1;
    private EditBox verifyBox;
    private EditBox claimAmountBox;
    private EditBox claimReasonBox;
    private EditBox transferTargetBox;
    private EditBox transferAmountBox;
    private boolean confirmTransfer = false;

    /* ---- 布局（每帧重算） ---- */
    private int px, py, pw, ph, contentX, contentY, contentW, contentH;

    public XuanjianMainScreen() {
        super(Component.literal("玄剑公会"));
    }

    /* ==================== 生命周期 ==================== */

    @Override
    protected void init() {
        computeLayout();
        buildPageWidgets();
        if (profile == null && loadError.isEmpty()) refreshAll();
    }

    private void computeLayout() {
        pw = Math.max(300, Math.min(this.width - 40, 460));
        ph = Math.max(190, Math.min(this.height - 40, 260));
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
        contentX = px + 8 + UiTheme.NAV_W + 8;
        contentY = py + UiTheme.HEADER_H + 8;
        contentW = pw - (contentX - px) - 8;
        contentH = ph - UiTheme.HEADER_H - 16;
    }

    /** 按当前页重建全部 widget（切页/改尺寸都要调） */
    private void buildPageWidgets() {
        this.clearWidgets();
        computeLayout();

        // 左侧导航
        int navX = px + 8;
        int navY = py + UiTheme.HEADER_H + 10;
        for (Page p : Page.values()) {
            this.addRenderableWidget(new XjButton(navX + 4, navY, UiTheme.NAV_W - 8, UiTheme.NAV_ITEM_H,
                    p.label, XjButton.Style.NAV, () -> {
                page = p;
                verifyTaskId = -1;
                confirmTransfer = false;
                toast = "";
                buildPageWidgets();
            }).selected(page == p));
            navY += UiTheme.NAV_ITEM_H + 2;
        }

        // 右上角：刷新 / 设置 / 关闭
        int btnY = py + 5;
        int right = px + pw - 6;
        this.addRenderableWidget(new XjButton(right - 40, btnY, 36, 20, "关闭", XjButton.Style.GHOST, this::onClose));
        right -= 42;
        this.addRenderableWidget(new XjButton(right - 40, btnY, 36, 20, "设置", XjButton.Style.GHOST, () ->
                this.minecraft.setScreen(new XuanjianConfigScreen(this))));
        right -= 42;
        this.addRenderableWidget(new XjButton(right - 40, btnY, 36, 20, "刷新", XjButton.Style.GHOST, this::refreshAll));

        switch (page) {
            case ACCOUNT -> buildAccountPage();
            case TASKS -> buildTasksPage();
            case CLAIM -> buildClaimPage();
            case TRANSFER -> buildTransferPage();
            case ONLINE -> { /* 只读，无 widget */ }
        }
    }

    // ---------- 各页 widget ----------

    private void buildAccountPage() {
        int y = contentY + 96;
        this.addRenderableWidget(new XjButton(contentX, y, 84, UiTheme.BTN_H, "官网签到", XjButton.Style.PRIMARY, this::doCheckin)
                .selected(false));
        this.addRenderableWidget(new XjButton(contentX + 90, y, 84, UiTheme.BTN_H, "打开官网", XjButton.Style.SECONDARY, () -> {
            // 26.1 没有 net.minecraft.Util，直接用 Screen 自带的受保护静态工具打开链接
            try {
                String base = XuanjianMod.getInstance().getApi().getBaseUrl();
                if (base == null || base.isBlank()) { showToast("官网地址未配置", UiTheme.WARNING); return; }
                clickUrlAction(this.minecraft, this, URI.create(base));
            } catch (Exception e) {
                showToast("无法打开浏览器", UiTheme.DANGER);
            }
        }));
    }

    private void buildTasksPage() {
        int rowY = contentY + 18;
        int rowH = 24;
        int from = taskPage * TASKS_PER_PAGE;
        UUID uuid = myUuid();

        // 已接取的任务：优先展示「提交验证码」
        for (int i = 0; i < myTasks.size() && i < TASKS_PER_PAGE; i++) {
            JsonObject c = myTasks.get(i);
            int id = intOf(c, "taskId", intOf(c, "id", -1));
            if (id < 0) continue;
            String status = strOf(c, "status", "");
            if (!"pending".equals(status)) continue;
            int bw = 62;
            this.addRenderableWidget(new XjButton(contentX + contentW - bw, rowY + 2, bw, 16, "填验证码",
                    XjButton.Style.SECONDARY, () -> { verifyTaskId = id; buildPageWidgets(); }));
            rowY += rowH;
        }

        // 可接取任务
        int fromIdx = taskPage * TASKS_PER_PAGE;
        for (int i = 0; i < TASKS_PER_PAGE; i++) {
            int idx = fromIdx + i;
            if (idx >= tasks.size()) break;
            JsonObject t = tasks.get(idx);
            int id = intOf(t, "id", -1);
            if (id < 0) continue;
            int bw = 50;
            final int taskId = id;
            this.addRenderableWidget(new XjButton(contentX + contentW - bw, rowY + 2, bw, 16, "接取",
                    XjButton.Style.PRIMARY, () -> doAccept(taskId)));
            rowY += rowH;
        }

        // 分页
        int totalPages = Math.max(1, (int) Math.ceil(tasks.size() / (double) TASKS_PER_PAGE));
        int pagerY = py + ph - 22;
        if (taskPage > 0) {
            this.addRenderableWidget(new XjButton(contentX, pagerY, 44, 16, "上一页", XjButton.Style.SECONDARY, () -> {
                taskPage--; buildPageWidgets();
            }));
        }
        if (taskPage < totalPages - 1) {
            this.addRenderableWidget(new XjButton(contentX + 50, pagerY, 44, 16, "下一页", XjButton.Style.SECONDARY, () -> {
                taskPage++; buildPageWidgets();
            }));
        }

        // 验证码输入
        if (verifyTaskId >= 0) {
            this.verifyBox = new EditBox(this.font, contentX, pagerY - 22, contentW - 60, 18, Component.literal("验证码"));
            this.verifyBox.setMaxLength(64);
            this.addRenderableWidget(this.verifyBox);
            this.addRenderableWidget(new XjButton(contentX + contentW - 54, pagerY - 22, 54, 18, "提交",
                    XjButton.Style.PRIMARY, this::doVerify));
        }
    }

    private void buildClaimPage() {
        int y = contentY + 18;
        this.claimAmountBox = new EditBox(this.font, contentX + 70, y, 70, 18, Component.literal("数量"));
        this.claimAmountBox.setMaxLength(6);
        this.addRenderableWidget(this.claimAmountBox);
        y += 26;
        this.claimReasonBox = new EditBox(this.font, contentX + 70, y, contentW - 70, 18, Component.literal("理由"));
        this.claimReasonBox.setMaxLength(200);
        this.addRenderableWidget(this.claimReasonBox);
        y += 30;
        this.addRenderableWidget(new XjButton(contentX + 70, y, 90, UiTheme.BTN_H, "提交申报",
                XjButton.Style.PRIMARY, this::doClaim));
    }

    private void buildTransferPage() {
        int y = contentY + 18;
        this.transferTargetBox = new EditBox(this.font, contentX + 70, y, contentW - 70, 18, Component.literal("对方游戏ID"));
        this.transferTargetBox.setMaxLength(32);
        this.addRenderableWidget(this.transferTargetBox);
        y += 26;
        this.transferAmountBox = new EditBox(this.font, contentX + 70, y, 70, 18, Component.literal("数量"));
        this.transferAmountBox.setMaxLength(6);
        this.addRenderableWidget(this.transferAmountBox);
        y += 30;

        if (!confirmTransfer) {
            this.addRenderableWidget(new XjButton(contentX + 70, y, 90, UiTheme.BTN_H, "确认转账",
                    XjButton.Style.PRIMARY, this::requestTransfer));
        } else {
            this.addRenderableWidget(new XjButton(contentX + 70, y, 70, UiTheme.BTN_H, "确认执行",
                    XjButton.Style.DANGER, this::confirmTransferNow));
            this.addRenderableWidget(new XjButton(contentX + 146, y, 60, UiTheme.BTN_H, "取消",
                    XjButton.Style.SECONDARY, () -> { confirmTransfer = false; buildPageWidgets(); }));
        }
    }

    /* ==================== 数据加载 ==================== */

    private UUID myUuid() {
        return this.minecraft.player != null ? this.minecraft.player.getUUID() : null;
    }

    private void refreshAll() {
        UUID uuid = myUuid();
        if (uuid == null) {
            loading = false;
            loadError = "未进入世界，无法获取账号信息";
            return;
        }
        XuanjianMod mod = XuanjianMod.getInstance();
        if (mod == null) {
            loading = false;
            loadError = "模组主入口未初始化";
            return;
        }

        loading = true;
        loadError = "";
        final String uuidStr = uuid.toString();

        CompletableFuture.supplyAsync(() -> {
            JsonObject prof = mod.getApi().get("/api/mod/profile?uuid=" + uuidStr);
            List<JsonObject> tk = new ArrayList<>(mod.getTaskManager().listTasks(uuid));
            List<JsonObject> my = new ArrayList<>(mod.getTaskManager().myTasks(uuid));
            List<JsonObject> on = new ArrayList<>();
            JsonObject o = mod.getApi().get("/api/mod/online");
            if (o != null && o.has("players")) {
                JsonArray arr = o.getAsJsonArray("players");
                for (int i = 0; i < arr.size(); i++) on.add(arr.get(i).getAsJsonObject());
            }
            return new Object[]{prof, tk, my, on};
        }).whenComplete((data, err) -> this.minecraft.execute(() -> {
            loading = false;
            if (err != null) {
                Throwable c = err.getCause() != null ? err.getCause() : err;
                loadError = "加载失败：" + c.getClass().getSimpleName();
                LOGGER.warn("[xuanjianmod] 主界面加载失败: {}", String.valueOf(c));
                buildPageWidgets();
                return;
            }
            @SuppressWarnings("unchecked")
            Object[] arr = (Object[]) data;
            profile = (JsonObject) arr[0];
            tasks = castList(arr[1]);
            myTasks = castList(arr[2]);
            online = castList(arr[3]);
            if (profile == null || profile.has("error")) {
                loadError = profile != null && profile.has("error")
                        ? profile.get("error").getAsString()
                        : "未绑定官网账号（使用 /xj bind <官网账号> 完成绑定）";
            }
            buildPageWidgets();
        }));
    }

    @SuppressWarnings("unchecked")
    private static List<JsonObject> castList(Object o) {
        return o instanceof List ? (List<JsonObject>) o : new ArrayList<>();
    }

    private void showToast(String msg, int color) {
        toast = msg == null ? "" : msg;
        toastColor = color;
        toastUntil = System.currentTimeMillis() + 4000;
    }

    /* ==================== 写操作 ==================== */

    private void doCheckin() {
        UUID uuid = myUuid();
        if (uuid == null) return;
        String name = this.minecraft.player != null ? this.minecraft.player.getName().getString() : "";
        CompletableFuture.supplyAsync(() -> XuanjianMod.getInstance().getCheckinManager().checkin(uuid, name))
                .whenComplete((resp, err) -> this.minecraft.execute(() -> {
                    if (err != null || resp == null) { showToast("签到失败：网络异常", UiTheme.DANGER); return; }
                    if (resp.has("error")) {
                        String e = resp.get("error").getAsString();
                        showToast(e, e.contains("已签到") ? UiTheme.WARNING : UiTheme.DANGER);
                    } else {
                        int reward = resp.has("rewardPoints") ? resp.get("rewardPoints").getAsInt() : 0;
                        showToast("签到成功，获得 " + reward + " 贡献点", UiTheme.SUCCESS);
                    }
                    refreshAll();
                }));
    }

    private void doAccept(int taskId) {
        UUID uuid = myUuid();
        if (uuid == null) return;
        CompletableFuture.supplyAsync(() -> XuanjianMod.getInstance().getTaskManager().accept(uuid, taskId))
                .whenComplete((resp, err) -> this.minecraft.execute(() -> {
                    if (err != null || resp == null) { showToast("接取失败：网络异常", UiTheme.DANGER); return; }
                    if (resp.has("error")) showToast(resp.get("error").getAsString(), UiTheme.DANGER);
                    else showToast("任务已接取", UiTheme.SUCCESS);
                    refreshAll();
                }));
    }

    private void doVerify() {
        UUID uuid = myUuid();
        if (uuid == null || verifyTaskId < 0 || verifyBox == null) return;
        String code = verifyBox.getValue().trim();
        if (code.isEmpty()) { showToast("请先填写验证码", UiTheme.WARNING); return; }
        final int id = verifyTaskId;
        CompletableFuture.supplyAsync(() -> XuanjianMod.getInstance().getTaskManager().verify(uuid, id, code))
                .whenComplete((resp, err) -> this.minecraft.execute(() -> {
                    if (err != null || resp == null) { showToast("提交失败：网络异常", UiTheme.DANGER); return; }
                    if (resp.has("error")) showToast(resp.get("error").getAsString(), UiTheme.DANGER);
                    else showToast("任务完成，奖励已发放", UiTheme.SUCCESS);
                    verifyTaskId = -1;
                    refreshAll();
                }));
    }

    private void doClaim() {
        UUID uuid = myUuid();
        if (uuid == null || claimAmountBox == null || claimReasonBox == null) return;
        int amount;
        try {
            amount = Integer.parseInt(claimAmountBox.getValue().trim());
        } catch (NumberFormatException e) {
            showToast("申报数量必须是数字", UiTheme.WARNING);
            return;
        }
        if (amount <= 0) { showToast("申报数量必须大于 0", UiTheme.WARNING); return; }
        String reason = claimReasonBox.getValue().trim();
        if (reason.length() < 10) { showToast("申报理由至少 10 个字符（当前 " + reason.length() + "）", UiTheme.WARNING); return; }

        CompletableFuture.supplyAsync(() -> XuanjianMod.getInstance().getClaimManager().submit(uuid, amount, reason))
                .whenComplete((resp, err) -> this.minecraft.execute(() -> {
                    if (err != null || resp == null) { showToast("提交失败：网络异常", UiTheme.DANGER); return; }
                    if (resp.has("error")) showToast(resp.get("error").getAsString(), UiTheme.DANGER);
                    else {
                        showToast("申报已提交，等待管理员审核", UiTheme.SUCCESS);
                        claimAmountBox.setValue("");
                        claimReasonBox.setValue("");
                    }
                    refreshAll();
                }));
    }

    private void requestTransfer() {
        if (transferTargetBox == null || transferAmountBox == null) return;
        String target = transferTargetBox.getValue().trim();
        int amount;
        try {
            amount = Integer.parseInt(transferAmountBox.getValue().trim());
        } catch (NumberFormatException e) {
            showToast("转账数量必须是数字", UiTheme.WARNING);
            return;
        }
        if (target.isEmpty()) { showToast("请填写对方游戏 ID", UiTheme.WARNING); return; }
        if (amount <= 0) { showToast("转账数量必须大于 0", UiTheme.WARNING); return; }

        UUID uuid = myUuid();
        if (uuid == null) return;
        XuanjianMod.getInstance().getContributionManager().requestTransfer(uuid, target, amount);
        confirmTransfer = true;
        buildPageWidgets();
    }

    private void confirmTransferNow() {
        UUID uuid = myUuid();
        if (uuid == null) return;
        CompletableFuture.supplyAsync(() -> XuanjianMod.getInstance().getContributionManager().confirmTransfer(uuid))
                .whenComplete((resp, err) -> this.minecraft.execute(() -> {
                    confirmTransfer = false;
                    if (err != null || resp == null) { showToast("转账失败：网络异常", UiTheme.DANGER); return; }
                    if (resp.has("error")) showToast(resp.get("error").getAsString(), UiTheme.DANGER);
                    else showToast("转账成功", UiTheme.SUCCESS);
                    refreshAll();
                }));
    }

    /* ==================== 渲染 ==================== */

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        computeLayout();

        // 1) 背景（在 widget 之下）
        UiTheme.panel(g, px, py, pw, ph);
        UiTheme.headerBar(g, px + 1, py + 1, pw - 2, UiTheme.HEADER_H - 1);
        UiTheme.roundedFill(g, px + 8, py + UiTheme.HEADER_H + 8, UiTheme.NAV_W, contentH, UiTheme.NAV_BG);

        // 2) widget（按钮、输入框）
        super.extractRenderState(g, mouseX, mouseY, delta);

        // 3) 前景文字与装饰
        UiTheme.text(g, this.font, "玄剑公会", px + 10, UiTheme.vCenter(this.font, py, UiTheme.HEADER_H), UiTheme.TEXT);

        if (loading) {
            UiTheme.textCenter(g, this.font, "正在从官网加载…", px + pw / 2, py + ph / 2, UiTheme.TEXT_MUTED);
        } else if (!loadError.isEmpty()) {
            UiTheme.textCenter(g, this.font, UiTheme.ellipsize(this.font, loadError, contentW - 20),
                    contentX + contentW / 2, contentY + 20, UiTheme.WARNING);
            UiTheme.textCenter(g, this.font, "在游戏内执行 /xj bind <官网账号> 完成绑定", contentX + contentW / 2, contentY + 38, UiTheme.TEXT_MUTED);
        } else {
            switch (page) {
                case ACCOUNT -> renderAccount(g);
                case TASKS -> renderTasks(g);
                case CLAIM -> renderClaim(g);
                case TRANSFER -> renderTransfer(g);
                case ONLINE -> renderOnline(g);
            }
        }

        // 提示条
        if (!toast.isEmpty() && System.currentTimeMillis() < toastUntil) {
            int tw = contentW;
            UiTheme.roundedFill(g, contentX, py + ph - 18, tw, 14, 0xCC000000);
            UiTheme.text(g, this.font, UiTheme.ellipsize(this.font, toast, tw - 8), contentX + 4, py + ph - 15, toastColor);
        }

        UiTheme.divider(g, px + 8, px + pw - 8, py + UiTheme.HEADER_H + 4);
    }

    private void renderAccount(GuiGraphicsExtractor g) {
        if (profile == null) return;
        JsonObject u = profile.has("user") ? profile.getAsJsonObject("user") : new JsonObject();

        // 头像
        int av = 32;
        int ax = contentX;
        int ay = contentY + 2;
        String avatarUrl = strOf(u, "avatar", "");
        Identifier tex = AvatarCache.get(avatarUrl);
        if (tex != null) {
            g.blit(tex, ax, ay, av, av, 0f, 0f, 1f, 1f);
            UiTheme.roundedOutline(g, ax, ay, av, av, 0x66FFFFFF);
        } else {
            UiTheme.roundedFill(g, ax, ay, av, av, 0x40FFFFFF);
            String initial = strOf(u, "nickname", "?");
            if (!initial.isEmpty()) {
                UiTheme.textCenter(g, this.font, initial.substring(0, 1), ax + av / 2,
                        ay + (av - this.font.lineHeight) / 2 + 1, UiTheme.TEXT_DIM);
            }
        }

        // 昵称 / 账号 / 游戏ID
        int tx = ax + av + 8;
        UiTheme.text(g, this.font, strOf(u, "nickname", "成员"), tx, ay + 2, UiTheme.TEXT);
        UiTheme.text(g, this.font, "@" + strOf(u, "username", ""), tx, ay + 14, UiTheme.TEXT_MUTED);
        String gameId = strOf(u, "gameId", "");
        if (!gameId.isEmpty()) {
            UiTheme.text(g, this.font, "游戏ID " + gameId, tx, ay + 25, UiTheme.TEXT_MUTED);
        }

        // 数据格
        int gy = contentY + 42;
        int half = contentW / 2;
        drawStat(g, contentX, gy, "贡献点", trimNum(profile, "user", "contribution"), UiTheme.WARNING);
        drawStat(g, contentX + half, gy, "排名", "#" + intOf(profile, "rank", 0) + " / " + intOf(profile, "totalMembers", 0), UiTheme.ACCENT);
        gy += 26;
        JsonObject title = profile.has("title") && !profile.get("title").isJsonNull() ? profile.getAsJsonObject("title") : null;
        drawStat(g, contentX, gy, "称号", title != null ? strOf(title, "name", "—") : "—",
                title != null ? UiTheme.parseColor(strOf(title, "color", ""), UiTheme.TEXT) : UiTheme.TEXT_MUTED);
        JsonObject gen = profile.has("generation") && !profile.get("generation").isJsonNull() ? profile.getAsJsonObject("generation") : null;
        drawStat(g, contentX + half, gy, "代系", gen != null ? strOf(gen, "name", "—") : "—",
                gen != null ? UiTheme.parseColor(strOf(gen, "color", ""), UiTheme.TEXT) : UiTheme.TEXT_MUTED);
        gy += 26;
        boolean checked = boolOf(profile, "checkedInToday", false);
        drawStat(g, contentX, gy, "今日签到", checked ? "已签到 · 连续 " + intOf(profile, "continuousDays", 0) + " 天" : "未签到",
                checked ? UiTheme.SUCCESS : UiTheme.WARNING);
        JsonObject ts = profile.has("taskStats") ? profile.getAsJsonObject("taskStats") : new JsonObject();
        drawStat(g, contentX + half, gy, "任务",
                "可接 " + intOf(ts, "available", 0) + " · 进行 " + intOf(ts, "mine", 0) + " · 完成 " + intOf(ts, "completed", 0),
                UiTheme.TEXT_DIM);
    }

    private void drawStat(GuiGraphicsExtractor g, int x, int y, String label, String value, int valueColor) {
        int w = contentW / 2 - 4;
        UiTheme.card(g, x, y, w, 22, false);
        UiTheme.text(g, this.font, label, x + 5, y + 3, UiTheme.TEXT_MUTED);
        UiTheme.text(g, this.font, UiTheme.ellipsize(this.font, value, w - 10 - this.font.width(label) - 4),
                x + 5 + this.font.width(label) + 4, y + 3, valueColor);
    }

    private void renderTasks(GuiGraphicsExtractor g) {
        int rowY = contentY + 18;
        int rowH = 24;
        if (tasks.isEmpty() && myTasks.isEmpty()) {
            UiTheme.text(g, this.font, "暂无可接取的任务", contentX, contentY + 4, UiTheme.TEXT_MUTED);
            return;
        }

        // 我的（待提交验证码）
        for (JsonObject c : myTasks) {
            if (rowY > py + ph - 46) break;
            if (!"pending".equals(strOf(c, "status", ""))) continue;
            String title = strOf(c, "title", "任务 #" + intOf(c, "taskId", 0));
            UiTheme.card(g, contentX, rowY, contentW, rowH - 2, false);
            UiTheme.text(g, this.font, "[进行中] " + UiTheme.ellipsize(this.font, title, contentW - 76), contentX + 5, rowY + 4, UiTheme.WARNING);
            rowY += rowH;
        }

        // 可接取
        int from = taskPage * TASKS_PER_PAGE;
        for (int i = 0; i < TASKS_PER_PAGE; i++) {
            int idx = from + i;
            if (idx >= tasks.size() || rowY > py + ph - 46) break;
            JsonObject t = tasks.get(idx);
            String title = strOf(t, "title", "任务");
            int reward = intOf(t, "reward", 0);
            UiTheme.card(g, contentX, rowY, contentW, rowH - 2, false);
            UiTheme.text(g, this.font, UiTheme.ellipsize(this.font, title, contentW - 76), contentX + 5, rowY + 4, UiTheme.TEXT);
            UiTheme.text(g, this.font, "+" + reward, contentX + contentW - 58, rowY + 4, UiTheme.SUCCESS);
            rowY += rowH;
        }

        int totalPages = Math.max(1, (int) Math.ceil(tasks.size() / (double) TASKS_PER_PAGE));
        UiTheme.text(g, this.font, "第 " + (taskPage + 1) + "/" + totalPages + " 页", contentX + 104, py + ph - 18, UiTheme.TEXT_MUTED);
        if (verifyTaskId >= 0) {
            UiTheme.text(g, this.font, "完成验证码：", contentX, py + ph - 18, UiTheme.TEXT_DIM);
        }
    }

    private void renderClaim(GuiGraphicsExtractor g) {
        int y = contentY + 21;
        UiTheme.text(g, this.font, "数量", contentX, y, UiTheme.TEXT_DIM);
        y += 26;
        UiTheme.text(g, this.font, "理由", contentX, y, UiTheme.TEXT_DIM);
        UiTheme.text(g, this.font, "（≥10 字，管理员审核后发放）", contentX + 70, y + 22, UiTheme.TEXT_MUTED);
    }

    private void renderTransfer(GuiGraphicsExtractor g) {
        int y = contentY + 21;
        UiTheme.text(g, this.font, "对方", contentX, y, UiTheme.TEXT_DIM);
        y += 26;
        UiTheme.text(g, this.font, "数量", contentX, y, UiTheme.TEXT_DIM);
        if (confirmTransfer) {
            UiTheme.text(g, this.font, "请再次确认：该操作不可撤销", contentX + 70, y + 24, UiTheme.DANGER);
        } else {
            UiTheme.text(g, this.font, "两步确认：先点「确认转账」，再确认执行", contentX + 70, y + 24, UiTheme.TEXT_MUTED);
        }
    }

    private void renderOnline(GuiGraphicsExtractor g) {
        if (online.isEmpty()) {
            UiTheme.text(g, this.font, "当前没有在线玩家", contentX, contentY + 4, UiTheme.TEXT_MUTED);
            return;
        }
        int y = contentY + 2;
        int col = 0;
        for (JsonObject o : online) {
            if (y > py + ph - 40) break;
            String name = strOf(o, "name", "?");
            String server = strOf(o, "server", "");
            String line = server.isEmpty() ? name : name + "  @" + server;
            UiTheme.text(g, this.font, UiTheme.ellipsize(this.font, line, contentW / 2 - 6),
                    contentX + col * (contentW / 2), y, UiTheme.TEXT_DIM);
            if (col == 0) { col = 1; } else { col = 0; y += 12; }
        }
        UiTheme.text(g, this.font, "共 " + online.size() + " 名在线玩家", contentX, py + ph - 18, UiTheme.TEXT_MUTED);
    }

    /* ==================== 工具 ==================== */

    private static String strOf(JsonObject o, String key, String def) {
        try {
            return o != null && o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
        } catch (Exception e) {
            return def;
        }
    }

    private static int intOf(JsonObject o, String key, int def) {
        try {
            return o != null && o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsInt() : def;
        } catch (Exception e) {
            return def;
        }
    }

    private static boolean boolOf(JsonObject o, String key, boolean def) {
        try {
            return o != null && o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsBoolean() : def;
        } catch (Exception e) {
            return def;
        }
    }

    private static String trimNum(JsonObject o, String objKey, String key) {
        try {
            double v = o.getAsJsonObject(objKey).get(key).getAsDouble();
            if (Math.abs(v - Math.rint(v)) < 0.005) return String.valueOf((long) Math.rint(v));
            return String.format("%.2f", v);
        } catch (Exception e) {
            return "0";
        }
    }

    /**
     * 按中文标签切换页面（供 gametest 与后续 /xj gui &lt;页&gt; 使用）。
     * 自绘按钮不是原版 Button，gametest 的 clickScreenButton 找不到它，故提供此入口。
     * @return 是否找到了该页面
     */
    public boolean showPage(String label) {
        if (label == null) return false;
        for (Page p : Page.values()) {
            if (p.label.equals(label)) {
                page = p;
                verifyTaskId = -1;
                confirmTransfer = false;
                toast = "";
                buildPageWidgets();
                return true;
            }
        }
        return false;
    }

    /** 当前页面标签（供 gametest 断言） */
    public String currentPageLabel() {
        return page.label;
    }

    /** 是否仍在异步加载（供 gametest 轮询） */
    public boolean isLoadingData() {
        return loading;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(null);
    }
}

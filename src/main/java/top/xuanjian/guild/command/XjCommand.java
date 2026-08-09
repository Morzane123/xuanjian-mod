package top.xuanjian.guild.command;

import com.google.gson.JsonObject;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import top.xuanjian.guild.XuanjianMod;
import top.xuanjian.guild.bind.BindManager;
import top.xuanjian.guild.economy.ClaimManager;
import top.xuanjian.guild.economy.ContributionManager;
import top.xuanjian.guild.online.OnlineManager;
import top.xuanjian.guild.task.TaskManager;

import java.util.List;
import java.util.function.Function;

/**
 * /xj 命令注册（双端通用）：
 * - 服务端注册 CommandRegistrationCallback，使用 ServerCommandActor（可全服广播）
 * - 客户端注册 ClientCommandRegistrationCallback，使用 ClientCommandActor（本地聊天栏显示）
 * 业务逻辑完全复用，仅执行者不同。
 */
public class XjCommand<S> {

    private final XuanjianMod mod;
    private final Function<S, CommandActor> actorFactory;

    public XjCommand(XuanjianMod mod, Function<S, CommandActor> actorFactory) {
        this.mod = mod;
        this.actorFactory = actorFactory;
    }

    public LiteralArgumentBuilder<S> build() {
        return LiteralArgumentBuilder.<S>literal("xj")
                .then(LiteralArgumentBuilder.<S>literal("help").executes(this::help))
                .then(LiteralArgumentBuilder.<S>literal("version").executes(this::version))
                .then(LiteralArgumentBuilder.<S>literal("bind")
                        .then(RequiredArgumentBuilder.<S, String>argument("account", StringArgumentType.word())
                                .executes(this::bind))
                        .then(LiteralArgumentBuilder.<S>literal("status").executes(this::bindStatus)))
                .then(LiteralArgumentBuilder.<S>literal("checkin").executes(this::checkin))
                .then(LiteralArgumentBuilder.<S>literal("task")
                        .then(LiteralArgumentBuilder.<S>literal("list").executes(this::taskList))
                        .then(LiteralArgumentBuilder.<S>literal("my").executes(this::taskMy))
                        .then(LiteralArgumentBuilder.<S>literal("accept")
                                .then(RequiredArgumentBuilder.<S, Integer>argument("id", IntegerArgumentType.integer(1))
                                        .executes(this::taskAccept)))
                        .then(LiteralArgumentBuilder.<S>literal("verify")
                                .then(RequiredArgumentBuilder.<S, Integer>argument("id", IntegerArgumentType.integer(1))
                                        .then(RequiredArgumentBuilder.<S, String>argument("code", StringArgumentType.word())
                                                .executes(this::taskVerify)))))
                .then(LiteralArgumentBuilder.<S>literal("cb")
                        .executes(this::cbBalance)
                        .then(LiteralArgumentBuilder.<S>literal("pay")
                                .then(RequiredArgumentBuilder.<S, String>argument("player", StringArgumentType.word())
                                        .then(RequiredArgumentBuilder.<S, Integer>argument("amount", IntegerArgumentType.integer(1))
                                                .executes(this::cbPay))))
                        .then(LiteralArgumentBuilder.<S>literal("confirm").executes(this::cbConfirm))
                        .then(LiteralArgumentBuilder.<S>literal("cancel").executes(this::cbCancel)))
                .then(LiteralArgumentBuilder.<S>literal("claim")
                        .then(RequiredArgumentBuilder.<S, Integer>argument("amount", IntegerArgumentType.integer(1))
                                .then(RequiredArgumentBuilder.<S, String>argument("reason", StringArgumentType.greedyString())
                                        .executes(this::claim))))
                .then(LiteralArgumentBuilder.<S>literal("online").executes(this::online));
    }

    /* ============ 执行者解析 ============ */

    private CommandActor actor(CommandContext<S> ctx) {
        return actorFactory.apply(ctx.getSource());
    }

    private boolean usable(CommandActor a) {
        return a != null && a.isValid();
    }

    /* ============ 命令实现 ============ */

    private int help(CommandContext<S> ctx) {
        CommandActor a = actor(ctx);
        if (!usable(a)) return 0;
        a.sendMessage("§e===== 玄剑公会联动模组指令 =====\n"
                + "§a/xj bind <官网账号> §f绑定官网账号（需邮箱确认）\n"
                + "§a/xj bind status §f查看绑定状态\n"
                + "§a/xj checkin §f手动签到\n"
                + "§a/xj task list §f任务列表\n"
                + "§a/xj task my §f我的任务\n"
                + "§a/xj task accept <id> §f接取任务\n"
                + "§a/xj task verify <id> <验证码> §f提交验证码\n"
                + "§a/xj cb §f贡献点余额\n"
                + "§a/xj cb pay <玩家> <金额> §f贡献点转账\n"
                + "§a/xj cb confirm §f确认转账\n"
                + "§a/xj cb cancel §f取消转账\n"
                + "§a/xj claim <数量> <理由> §f贡献点申报\n"
                + "§a/xj online §f查看在线玩家\n"
                + "§a/xj help §f帮助\n"
                + "§a/xj version §f版本");
        return Command.SINGLE_SUCCESS;
    }

    private int version(CommandContext<S> ctx) {
        CommandActor a = actor(ctx);
        if (!usable(a)) return 0;
        a.sendMessage("§e玄剑公会联动模组 v" + XuanjianMod.VERSION + "（Fabric）");
        return Command.SINGLE_SUCCESS;
    }

    private int bind(CommandContext<S> ctx) {
        CommandActor a = actor(ctx);
        if (!usable(a)) return 0;
        String account = StringArgumentType.getString(ctx, "account");
        BindManager bm = mod.getBindManager();
        JsonObject resp = bm.requestBind(a.getUuid(), a.getPlayerName(), account);
        if (resp == null) {
            a.sendMessage("§c绑定请求失败：官网服务不可用或账号不存在");
        } else if (resp.has("error")) {
            a.sendMessage("§c" + resp.get("error").getAsString());
        } else {
            a.sendMessage("§a绑定确认邮件已发送至官网账号「" + account + "」的邮箱，请查收并点击确认链接完成绑定。");
        }
        return Command.SINGLE_SUCCESS;
    }

    private int bindStatus(CommandContext<S> ctx) {
        CommandActor a = actor(ctx);
        if (!usable(a)) return 0;
        // 先同步官网绑定状态（邮件确认后本地缓存可能未更新）
        if (mod.getBindManager().syncFromServer(a.getUuid())) {
            a.sendMessage("§a当前已绑定官网账号。");
        } else {
            a.sendMessage("§e尚未绑定，请使用 §a/xj bind <官网账号> §e完成绑定。");
        }
        return Command.SINGLE_SUCCESS;
    }

    private int checkin(CommandContext<S> ctx) {
        CommandActor a = actor(ctx);
        if (!usable(a)) return 0;
        if (!mod.getBindManager().isBound(a.getUuid())) {
            a.sendMessage("§c请先使用 /xj bind 绑定官网账号");
            return Command.SINGLE_SUCCESS;
        }
        JsonObject resp = mod.getCheckinManager().checkin(a.getUuid(), a.getPlayerName());
        if (resp == null) {
            a.sendMessage("§c签到失败：官网服务不可用");
        } else if (resp.has("error")) {
            a.sendMessage("§c" + resp.get("error").getAsString());
        } else {
            int reward = resp.has("rewardPoints") ? resp.get("rewardPoints").getAsInt() : 0;
            int total = resp.has("totalContribution") ? resp.get("totalContribution").getAsInt() : 0;
            a.sendMessage("§a签到成功！获得 §e" + reward + " §a贡献点，当前余额 §e" + total);
        }
        return Command.SINGLE_SUCCESS;
    }

    private int taskList(CommandContext<S> ctx) {
        CommandActor a = actor(ctx);
        if (!usable(a)) return 0;
        List<JsonObject> tasks = mod.getTaskManager().listTasks(a.getUuid());
        if (tasks.isEmpty()) {
            a.sendMessage("§e当前没有可接取的任务。");
            return Command.SINGLE_SUCCESS;
        }
        StringBuilder sb = new StringBuilder("§e===== 官方任务列表 =====\n");
        for (JsonObject t : tasks) {
            int id = t.get("id").getAsInt();
            String title = t.has("title") ? t.get("title").getAsString() : "";
            int reward = t.has("reward") ? t.get("reward").getAsInt() : 0;
            String status = t.has("myStatus") && !t.get("myStatus").isJsonNull() ? t.get("myStatus").getAsString() : "";
            sb.append("§a[").append(id).append("] §f").append(title)
              .append(" §e(+").append(reward).append("点)");
            if (!status.isEmpty()) {
                sb.append(" §7[").append(status.equals("pending") ? "已接取" : "已完成").append("]");
            }
            sb.append("\n");
        }
        a.sendMessage(sb.toString());
        return Command.SINGLE_SUCCESS;
    }

    private int taskMy(CommandContext<S> ctx) {
        CommandActor a = actor(ctx);
        if (!usable(a)) return 0;
        List<JsonObject> claims = mod.getTaskManager().myTasks(a.getUuid());
        if (claims.isEmpty()) {
            a.sendMessage("§e您尚未接取任何任务。");
            return Command.SINGLE_SUCCESS;
        }
        StringBuilder sb = new StringBuilder("§e===== 我的任务 =====\n");
        for (JsonObject c : claims) {
            String title = c.has("title") ? c.get("title").getAsString() : "";
            String status = c.has("status") ? c.get("status").getAsString() : "";
            sb.append("§f").append(title).append(" §7[").append(status).append("]\n");
        }
        a.sendMessage(sb.toString());
        return Command.SINGLE_SUCCESS;
    }

    private int taskAccept(CommandContext<S> ctx) {
        CommandActor a = actor(ctx);
        if (!usable(a)) return 0;
        int id = IntegerArgumentType.getInteger(ctx, "id");
        JsonObject resp = mod.getTaskManager().accept(a.getUuid(), id);
        if (resp == null) {
            a.sendMessage("§c接取失败：官网服务不可用");
        } else if (resp.has("error")) {
            a.sendMessage("§c" + resp.get("error").getAsString());
        } else {
            a.sendMessage("§a任务接取成功！完成任务请使用 /xj task verify " + id + " <验证码>");
        }
        return Command.SINGLE_SUCCESS;
    }

    private int taskVerify(CommandContext<S> ctx) {
        CommandActor a = actor(ctx);
        if (!usable(a)) return 0;
        int id = IntegerArgumentType.getInteger(ctx, "id");
        String code = StringArgumentType.getString(ctx, "code");
        JsonObject resp = mod.getTaskManager().verify(a.getUuid(), id, code);
        if (resp == null) {
            a.sendMessage("§c提交失败：官网服务不可用");
        } else if (resp.has("error")) {
            a.sendMessage("§c" + resp.get("error").getAsString());
        } else {
            int reward = resp.has("reward") ? resp.get("reward").getAsInt() : 0;
            a.sendMessage("§a任务完成！获得 §e" + reward + " §a贡献点");
        }
        return Command.SINGLE_SUCCESS;
    }

    private int cbBalance(CommandContext<S> ctx) {
        CommandActor a = actor(ctx);
        if (!usable(a)) return 0;
        if (!mod.getBindManager().isBound(a.getUuid())) {
            a.sendMessage("§c请先使用 /xj bind 绑定官网账号");
            return Command.SINGLE_SUCCESS;
        }
        JsonObject resp = mod.getContributionManager().getBalance(a.getUuid());
        if (resp == null) {
            a.sendMessage("§c余额查询失败：官网服务不可用");
        } else if (resp.has("error")) {
            a.sendMessage("§c" + resp.get("error").getAsString());
        } else {
            int balance = resp.has("balance") ? resp.get("balance").getAsInt() : 0;
            a.sendMessage("§e当前贡献点余额：§a" + balance);
        }
        return Command.SINGLE_SUCCESS;
    }

    private int cbPay(CommandContext<S> ctx) {
        CommandActor a = actor(ctx);
        if (!usable(a)) return 0;
        if (!mod.getBindManager().isBound(a.getUuid())) {
            a.sendMessage("§c请先使用 /xj bind 绑定官网账号");
            return Command.SINGLE_SUCCESS;
        }
        String toPlayer = StringArgumentType.getString(ctx, "player");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        ContributionManager cm = mod.getContributionManager();
        if (!cm.requestTransfer(a.getUuid(), toPlayer, amount)) {
            a.sendMessage("§c转账金额必须大于0");
            return Command.SINGLE_SUCCESS;
        }
        a.sendMessage("§e确认向 §f" + toPlayer + " §e转账 §a" + amount + " §e贡献点？\n"
                + "输入 §a/xj cb confirm §e确认，或 §c/xj cb cancel §e取消（120秒内）");
        return Command.SINGLE_SUCCESS;
    }

    private int cbConfirm(CommandContext<S> ctx) {
        CommandActor a = actor(ctx);
        if (!usable(a)) return 0;
        JsonObject resp = mod.getContributionManager().confirmTransfer(a.getUuid());
        if (resp == null) {
            a.sendMessage("§c没有待确认的转账，或已过期。");
        } else if (resp.has("error")) {
            a.sendMessage("§c" + resp.get("error").getAsString());
        } else {
            a.sendMessage("§a转账成功！");
        }
        return Command.SINGLE_SUCCESS;
    }

    private int cbCancel(CommandContext<S> ctx) {
        CommandActor a = actor(ctx);
        if (!usable(a)) return 0;
        mod.getContributionManager().cancelTransfer(a.getUuid());
        a.sendMessage("§e已取消转账。");
        return Command.SINGLE_SUCCESS;
    }

    private int claim(CommandContext<S> ctx) {
        CommandActor a = actor(ctx);
        if (!usable(a)) return 0;
        if (!mod.getBindManager().isBound(a.getUuid())) {
            a.sendMessage("§c请先使用 /xj bind 绑定官网账号");
            return Command.SINGLE_SUCCESS;
        }
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        String reason = StringArgumentType.getString(ctx, "reason");
        JsonObject resp = mod.getClaimManager().submit(a.getUuid(), amount, reason);
        if (resp == null) {
            a.sendMessage("§c申报失败：官网服务不可用");
        } else if (resp.has("error")) {
            a.sendMessage("§c" + resp.get("error").getAsString());
        } else {
            a.sendMessage("§a申报提交成功！请等待管理员审核。");
        }
        return Command.SINGLE_SUCCESS;
    }

    private int online(CommandContext<S> ctx) {
        CommandActor a = actor(ctx);
        if (!usable(a)) return 0;
        OnlineManager om = mod.getOnlineManager();
        List<OnlineManager.OnlinePlayer> players = om.queryOnline(om.getServerIp() == null ? "" : om.getServerIp());
        if (players.isEmpty()) {
            a.sendMessage("§e当前服务器暂无在线玩家数据。");
            return Command.SINGLE_SUCCESS;
        }
        StringBuilder sb = new StringBuilder("§e===== 在线玩家（" + players.size() + "）=====\n");
        for (OnlineManager.OnlinePlayer p : players) {
            sb.append("§f").append(p.name).append("\n");
        }
        a.sendMessage(sb.toString());
        return Command.SINGLE_SUCCESS;
    }
}

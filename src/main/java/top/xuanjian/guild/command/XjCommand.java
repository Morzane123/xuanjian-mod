package top.xuanjian.guild.command;

import com.google.gson.JsonObject;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import top.xuanjian.guild.XuanjianMod;
import top.xuanjian.guild.bind.BindManager;
import top.xuanjian.guild.checkin.CheckinManager;
import top.xuanjian.guild.economy.ClaimManager;
import top.xuanjian.guild.economy.ContributionManager;
import top.xuanjian.guild.online.OnlineManager;
import top.xuanjian.guild.task.TaskManager;

import java.util.List;
import java.util.UUID;

/**
 * /xj 命令注册：bind / checkin / task / cb / claim / online / help / version
 */
public class XjCommand {

    private final XuanjianMod mod;

    public XjCommand(XuanjianMod mod) {
        this.mod = mod;
    }

    public void register(CommandDispatcherLike dispatcher) {
        dispatcher.register(
                Commands.literal("xj")
                        .then(Commands.literal("help").executes(this::help))
                        .then(Commands.literal("version").executes(this::version))
                        .then(Commands.literal("bind")
                                .then(Commands.argument("account", StringArgumentType.word())
                                        .executes(this::bind))
                                .then(Commands.literal("status").executes(this::bindStatus)))
                        .then(Commands.literal("checkin").executes(this::checkin))
                        .then(Commands.literal("task")
                                .then(Commands.literal("list").executes(this::taskList))
                                .then(Commands.literal("my").executes(this::taskMy))
                                .then(Commands.literal("accept")
                                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                                .executes(this::taskAccept)))
                                .then(Commands.literal("verify")
                                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                                .then(Commands.argument("code", StringArgumentType.word())
                                                        .executes(this::taskVerify)))))
                        .then(Commands.literal("cb")
                                .executes(this::cbBalance)
                                .then(Commands.literal("pay")
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                        .executes(this::cbPay))))
                                .then(Commands.literal("confirm").executes(this::cbConfirm))
                                .then(Commands.literal("cancel").executes(this::cbCancel)))
                        .then(Commands.literal("claim")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                .executes(this::claim))))
                        .then(Commands.literal("online").executes(this::online))
        );
    }

    /* ============ 命令实现 ============ */

    private int help(CommandContext<CommandSourceStack> ctx) {
        reply(ctx, "§e===== 玄剑公会联动模组指令 =====\n"
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

    private int version(CommandContext<CommandSourceStack> ctx) {
        reply(ctx, "§e玄剑公会联动模组 v" + XuanjianMod.VERSION + "（Fabric）");
        return Command.SINGLE_SUCCESS;
    }

    private int bind(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        String account = StringArgumentType.getString(ctx, "account");
        BindManager bm = mod.getBindManager();
        JsonObject resp = bm.requestBind(player.getUUID(), player.getName().getString(), account);
        if (resp == null) {
            reply(ctx, "§c绑定请求失败：官网服务不可用或账号不存在");
        } else if (resp.has("error")) {
            reply(ctx, "§c" + resp.get("error").getAsString());
        } else {
            reply(ctx, "§a绑定确认邮件已发送至官网账号「" + account + "」的邮箱，请查收并点击确认链接完成绑定。");
        }
        return Command.SINGLE_SUCCESS;
    }

    private int bindStatus(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        UUID uuid = player.getUUID();
        if (mod.getBindManager().isBound(uuid)) {
            reply(ctx, "§a当前已绑定官网账号。");
        } else {
            reply(ctx, "§e尚未绑定，请使用 §a/xj bind <官网账号> §e完成绑定。");
        }
        return Command.SINGLE_SUCCESS;
    }

    private int checkin(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        UUID uuid = player.getUUID();
        if (!mod.getBindManager().isBound(uuid)) {
            reply(ctx, "§c请先使用 /xj bind 绑定官网账号");
            return Command.SINGLE_SUCCESS;
        }
        JsonObject resp = mod.getCheckinManager().checkin(uuid, player.getName().getString());
        if (resp == null) {
            reply(ctx, "§c签到失败：官网服务不可用");
        } else if (resp.has("error")) {
            reply(ctx, "§c" + resp.get("error").getAsString());
        } else {
            int reward = resp.has("rewardPoints") ? resp.get("rewardPoints").getAsInt() : 0;
            int total = resp.has("totalContribution") ? resp.get("totalContribution").getAsInt() : 0;
            reply(ctx, "§a签到成功！获得 §e" + reward + " §a贡献点，当前余额 §e" + total);
        }
        return Command.SINGLE_SUCCESS;
    }

    private int taskList(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        UUID uuid = player.getUUID();
        List<JsonObject> tasks = mod.getTaskManager().listTasks(uuid);
        if (tasks.isEmpty()) {
            reply(ctx, "§e当前没有可接取的任务。");
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
        reply(ctx, sb.toString());
        return Command.SINGLE_SUCCESS;
    }

    private int taskMy(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        UUID uuid = player.getUUID();
        List<JsonObject> claims = mod.getTaskManager().myTasks(uuid);
        if (claims.isEmpty()) {
            reply(ctx, "§e您尚未接取任何任务。");
            return Command.SINGLE_SUCCESS;
        }
        StringBuilder sb = new StringBuilder("§e===== 我的任务 =====\n");
        for (JsonObject c : claims) {
            String title = c.has("title") ? c.get("title").getAsString() : "";
            String status = c.has("status") ? c.get("status").getAsString() : "";
            sb.append("§f").append(title).append(" §7[").append(status).append("]\n");
        }
        reply(ctx, sb.toString());
        return Command.SINGLE_SUCCESS;
    }

    private int taskAccept(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        UUID uuid = player.getUUID();
        int id = IntegerArgumentType.getInteger(ctx, "id");
        JsonObject resp = mod.getTaskManager().accept(uuid, id);
        if (resp == null) {
            reply(ctx, "§c接取失败：官网服务不可用");
        } else if (resp.has("error")) {
            reply(ctx, "§c" + resp.get("error").getAsString());
        } else {
            reply(ctx, "§a任务接取成功！完成任务请使用 /xj task verify " + id + " <验证码>");
        }
        return Command.SINGLE_SUCCESS;
    }

    private int taskVerify(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        UUID uuid = player.getUUID();
        int id = IntegerArgumentType.getInteger(ctx, "id");
        String code = StringArgumentType.getString(ctx, "code");
        JsonObject resp = mod.getTaskManager().verify(uuid, id, code);
        if (resp == null) {
            reply(ctx, "§c提交失败：官网服务不可用");
        } else if (resp.has("error")) {
            reply(ctx, "§c" + resp.get("error").getAsString());
        } else {
            int reward = resp.has("reward") ? resp.get("reward").getAsInt() : 0;
            reply(ctx, "§a任务完成！获得 §e" + reward + " §a贡献点");
        }
        return Command.SINGLE_SUCCESS;
    }

    private int cbBalance(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        UUID uuid = player.getUUID();
        if (!mod.getBindManager().isBound(uuid)) {
            reply(ctx, "§c请先使用 /xj bind 绑定官网账号");
            return Command.SINGLE_SUCCESS;
        }
        JsonObject resp = mod.getContributionManager().getBalance(uuid);
        if (resp == null) {
            reply(ctx, "§c余额查询失败：官网服务不可用");
        } else if (resp.has("error")) {
            reply(ctx, "§c" + resp.get("error").getAsString());
        } else {
            int balance = resp.has("balance") ? resp.get("balance").getAsInt() : 0;
            reply(ctx, "§e当前贡献点余额：§a" + balance);
        }
        return Command.SINGLE_SUCCESS;
    }

    private int cbPay(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        UUID uuid = player.getUUID();
        if (!mod.getBindManager().isBound(uuid)) {
            reply(ctx, "§c请先使用 /xj bind 绑定官网账号");
            return Command.SINGLE_SUCCESS;
        }
        String toPlayer = StringArgumentType.getString(ctx, "player");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        ContributionManager cm = mod.getContributionManager();
        if (!cm.requestTransfer(uuid, toPlayer, amount)) {
            reply(ctx, "§c转账金额必须大于0");
            return Command.SINGLE_SUCCESS;
        }
        reply(ctx, "§e确认向 §f" + toPlayer + " §e转账 §a" + amount + " §e贡献点？\n"
                + "输入 §a/xj cb confirm §e确认，或 §c/xj cb cancel §e取消（120秒内）");
        return Command.SINGLE_SUCCESS;
    }

    private int cbConfirm(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        UUID uuid = player.getUUID();
        JsonObject resp = mod.getContributionManager().confirmTransfer(uuid);
        if (resp == null) {
            reply(ctx, "§c没有待确认的转账，或已过期。");
        } else if (resp.has("error")) {
            reply(ctx, "§c" + resp.get("error").getAsString());
        } else {
            reply(ctx, "§a转账成功！");
        }
        return Command.SINGLE_SUCCESS;
    }

    private int cbCancel(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        mod.getContributionManager().cancelTransfer(player.getUUID());
        reply(ctx, "§e已取消转账。");
        return Command.SINGLE_SUCCESS;
    }

    private int claim(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        UUID uuid = player.getUUID();
        if (!mod.getBindManager().isBound(uuid)) {
            reply(ctx, "§c请先使用 /xj bind 绑定官网账号");
            return Command.SINGLE_SUCCESS;
        }
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        String reason = StringArgumentType.getString(ctx, "reason");
        JsonObject resp = mod.getClaimManager().submit(uuid, amount, reason);
        if (resp == null) {
            reply(ctx, "§c申报失败：官网服务不可用");
        } else if (resp.has("error")) {
            reply(ctx, "§c" + resp.get("error").getAsString());
        } else {
            reply(ctx, "§a申报提交成功！请等待管理员审核。");
        }
        return Command.SINGLE_SUCCESS;
    }

    private int online(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        OnlineManager om = mod.getOnlineManager();
        List<OnlineManager.OnlinePlayer> players = om.queryOnline(om.getServerIp() == null ? "" : om.getServerIp());
        if (players.isEmpty()) {
            reply(ctx, "§e当前服务器暂无在线玩家数据。");
            return Command.SINGLE_SUCCESS;
        }
        StringBuilder sb = new StringBuilder("§e===== 在线玩家（" + players.size() + "）=====\n");
        for (OnlineManager.OnlinePlayer p : players) {
            sb.append("§f").append(p.name).append("\n");
        }
        reply(ctx, sb.toString());
        return Command.SINGLE_SUCCESS;
    }

    /* ============ 工具 ============ */

    private ServerPlayer getPlayer(CommandContext<CommandSourceStack> ctx) {
        try {
            return ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            return null;
        }
    }

    private void reply(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
    }

    /** 兼容接口，避免直接依赖 CommandDispatcher 泛型 */
    public interface CommandDispatcherLike {
        void register(com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> node);
    }
}

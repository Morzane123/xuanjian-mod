package top.xuanjian.guild.gametest;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.xuanjian.guild.gui.XuanjianConfigScreen;
import top.xuanjian.guild.gui.XuanjianMainScreen;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 客户端 gametest：真实启动一次 Minecraft 客户端，打开模组 GUI 并逐页截图。
 *
 * 目的：把「界面到底长什么样、按钮点不点得动」变成 CI 里可复查的东西。
 * 截图输出到 build/run/clientGameTest/screenshots/，CI 作为 artifact 上传供人工查看。
 *
 * 程序可判定的断言（不依赖人工看图）：
 *  1. 主界面能打开，且确实创建了 widget
 *  2. 异步加载能在超时内结束
 *  3. 五个页面都能切换
 *  4. 每张截图「不是纯色」——采样到的颜色数过少即视为界面没画出来
 *  5. 设置页能打开且有 widget
 *  6. 结束时回到标题界面（gametest 框架的收尾要求）
 *
 * 兼容性说明：
 *  - 自绘按钮不是原版 Button，context.clickScreenButton 找不到它们，因此切页走
 *    XuanjianMainScreen.showPage(...) 这个公开入口。
 *  - 不读取 Minecraft 的当前界面字段（26.1 是 mc.screen，26.2 改为 mc.gui），
 *    改为在 setScreen 的 supplier 里缓存自己创建的界面实例，26.1 / 26.2 通用。
 */
public class XuanjianGuiClientGameTest implements FabricClientGameTest {

    private static final Logger LOGGER = LoggerFactory.getLogger("xuanjianmod-gametest");

    /** 页面中文标签 -> 截图用的 ASCII 文件名（避免中文文件名在不同环境下的坑） */
    private static final String[][] PAGES = {
            {"任务", "2-tasks"},
            {"申报", "3-claim"},
            {"转账", "4-transfer"},
            {"在线", "5-online"},
    };

    @Override
    public void runTest(ClientGameTestContext context) {
        List<String> shots = new ArrayList<>();

        // ---------- 主界面 ----------
        AtomicReference<XuanjianMainScreen> mainRef = new AtomicReference<>();
        context.setScreen(() -> {
            XuanjianMainScreen s = new XuanjianMainScreen();
            mainRef.set(s);
            return s;
        });
        context.waitForScreen(XuanjianMainScreen.class);
        context.waitTicks(10);

        XuanjianMainScreen main = mainRef.get();
        if (main == null) throw new AssertionError("主界面实例未被创建");

        // 等异步加载结束（最多 ~200 tick）
        for (int i = 0; i < 200; i++) {
            Boolean loading = context.computeOnClient(mc -> main.isLoadingData());
            if (loading == null || !loading) break;
            context.waitTick();
        }

        Boolean stillLoading = context.computeOnClient(mc -> main.isLoadingData());
        Integer widgets = context.computeOnClient(mc -> countWidgets(main));
        int widgetCount = widgets == null ? 0 : widgets;
        LOGGER.info("[gametest] 主界面: 仍在加载={} widget数={}", stillLoading, widgetCount);

        if (Boolean.TRUE.equals(stillLoading)) throw new AssertionError("主界面异步加载未在超时内完成");
        if (widgetCount <= 0) throw new AssertionError("主界面没有任何 widget（按钮/输入框）");

        shots.add(capture(context, "1-account"));

        // ---------- 逐页切换 ----------
        for (String[] page : PAGES) {
            final String label = page[0];
            Boolean ok = context.computeOnClient(mc -> main.showPage(label));
            context.waitTicks(6);

            String current = context.computeOnClient(mc -> main.currentPageLabel());
            LOGGER.info("[gametest] 切页 {} -> ok={} current={}", label, ok, current);

            if (!Boolean.TRUE.equals(ok)) throw new AssertionError("无法切换到页面：" + label);
            if (!label.equals(current)) throw new AssertionError("切页后 currentPageLabel 不符：" + current);

            shots.add(capture(context, page[1]));
        }

        // ---------- 设置页 ----------
        AtomicReference<XuanjianConfigScreen> cfgRef = new AtomicReference<>();
        context.setScreen(() -> {
            XuanjianConfigScreen s = new XuanjianConfigScreen(null);
            cfgRef.set(s);
            return s;
        });
        context.waitForScreen(XuanjianConfigScreen.class);
        context.waitTicks(8);

        XuanjianConfigScreen cfg = cfgRef.get();
        if (cfg == null) throw new AssertionError("设置页实例未被创建");
        Integer cfgWidgets = context.computeOnClient(mc -> countWidgets(cfg));
        LOGGER.info("[gametest] 设置页 widget数={}", cfgWidgets);
        if (cfgWidgets == null || cfgWidgets <= 0) throw new AssertionError("设置页没有任何 widget");

        shots.add(capture(context, "6-config"));

        // ---------- 收尾：框架要求测试结束时停在标题界面 ----------
        // 无世界时 setScreen(null) 会被原版还原成 TitleScreen
        context.setScreen(() -> null);
        context.waitTicks(10);

        for (String s : shots) LOGGER.info("[gametest] 截图: {}", s);
        LOGGER.info("[gametest] 全部通过，共 {} 张截图", shots.size());
    }

    /* ==================== 截图 + 「不是纯色」断言 ==================== */

    private static String capture(ClientGameTestContext ctx, String name) {
        Path p = ctx.takeScreenshot(name);
        if (p == null) throw new AssertionError("截图失败：" + name);

        // 截图落盘可能滞后于 tick，等文件真正可用
        Path file = waitForFile(p);
        if (file == null) throw new AssertionError("截图文件未生成：" + p);

        int colors = countDistinctColors(file);
        LOGGER.info("[gametest] 截图 {} -> {} （采样色数 {}）", name, file.getFileName(), colors);
        if (colors < 8) throw new AssertionError("画面疑似空白：截图 " + name + " 采样色数仅 " + colors);

        return file.toString().replace('\\', '/');
    }

    private static Path waitForFile(Path p) {
        for (int i = 0; i < 60; i++) {
            try {
                if (Files.exists(p) && Files.size(p) > 0) return p;
            } catch (Exception ignored) {
                // 继续等待
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return Files.exists(p) ? p : null;
    }

    /** 隔点采样统计不同颜色数；纯色/空白画面只会得到 1~2 */
    private static int countDistinctColors(Path png) {
        try (InputStream in = Files.newInputStream(png); NativeImage img = NativeImage.read(in)) {
            Set<Integer> colors = new HashSet<>();
            int w = img.getWidth();
            int h = img.getHeight();
            for (int y = 0; y < h; y += 3) {
                for (int x = 0; x < w; x += 3) {
                    colors.add(img.getPixel(x, y));
                    if (colors.size() > 64) return colors.size();
                }
            }
            return colors.size();
        } catch (Exception e) {
            throw new AssertionError("读取截图失败 " + png + "：" + e.getMessage(), e);
        }
    }

    /* ==================== 反射读取控件数量（避开版本差异） ==================== */

    private static int countWidgets(Object screen) {
        try {
            Method m = screen.getClass().getMethod("children");
            Object r = m.invoke(screen);
            if (r instanceof List<?> list) return list.size();
        } catch (Exception ignored) {
            // 回退：反射读 renderables
        }
        Object v = field(screen, "renderables");
        return v instanceof List<?> list ? list.size() : 0;
    }

    private static Object field(Object target, String name) {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}

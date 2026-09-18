package top.xuanjian.guild.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 模组 GUI 主题与绘制工具。
 *
 * 统一配色、间距与「圆角卡片 / 面板 / 分隔线」的画法，供所有界面复用。
 *
 * ⚠️ 颜色一律 8 位 ARGB：26.x 的 {@code GuiGraphicsExtractor.text} 不会在 alpha=0 时补 0xFF，
 * 写成 0xFFFFFF 会得到完全透明的文字。
 *
 * 圆角做法：不依赖贴图，用「上下各内缩 1px 的两次 fill」抹掉四角像素，
 * 视觉上即为轻微圆角，成本为零。
 */
public final class UiTheme {

    private UiTheme() { }

    /* ==================== 配色 ==================== */

    /** 主面板背景（深色半透明，压住游戏画面保证可读） */
    public static final int PANEL_BG = 0xE6111A2B;
    public static final int PANEL_BORDER = 0x3D7EA8FF;

    /** 卡片 */
    public static final int CARD_BG = 0x24FFFFFF;
    public static final int CARD_BG_HOVER = 0x40FFFFFF;
    public static final int CARD_BG_ACTIVE = 0x552563EB;
    public static final int CARD_BORDER = 0x22FFFFFF;
    public static final int CARD_BORDER_HOVER = 0x66FFFFFF;

    /** 顶部标题条渐变 */
    public static final int HEADER_TOP = 0xFF1D4ED8;
    public static final int HEADER_BOTTOM = 0xFF1E3A8A;

    /** 文字 */
    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_DIM = 0xFFC3CEDF;
    public static final int TEXT_MUTED = 0xFF8496AE;
    public static final int TEXT_DISABLED = 0xFF5A6B80;

    /** 语义色 */
    public static final int ACCENT = 0xFF7CB0FF;
    public static final int SUCCESS = 0xFF4ADE80;
    public static final int WARNING = 0xFFFBBF24;
    public static final int DANGER = 0xFFF87171;

    /** 导航栏 */
    public static final int NAV_ACTIVE = 0xFF2563EB;
    public static final int NAV_HOVER = 0x2EFFFFFF;
    public static final int NAV_BG = 0x33000000;

    /** 分隔线 / 滚动条 */
    public static final int DIVIDER = 0x24FFFFFF;
    public static final int SCROLLBAR = 0x55FFFFFF;

    /* ==================== 尺寸 ==================== */

    public static final int PAD = 12;        // 面板内边距
    public static final int GAP = 8;         // 元素间距
    public static final int NAV_W = 108;     // 左侧导航宽度
    public static final int NAV_ITEM_H = 26;
    public static final int BTN_H = 20;
    public static final int HEADER_H = 30;

    /* ==================== 绘制工具 ==================== */

    /** 带描边的圆角面板 */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        roundedFill(g, x, y, w, h, PANEL_BG);
        roundedOutline(g, x, y, w, h, PANEL_BORDER);
    }

    /** 顶部标题条（渐变） */
    public static void headerBar(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fillGradient(x, y, x + w, y + h, HEADER_TOP, HEADER_BOTTOM);
    }

    /** 卡片 */
    public static void card(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hover) {
        roundedFill(g, x, y, w, h, hover ? CARD_BG_HOVER : CARD_BG);
        roundedOutline(g, x, y, w, h, hover ? CARD_BORDER_HOVER : CARD_BORDER);
    }

    /** 选中态卡片 */
    public static void cardActive(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        roundedFill(g, x, y, w, h, CARD_BG_ACTIVE);
        roundedOutline(g, x, y, w, h, ACCENT);
    }

    /** 横向分隔线 */
    public static void divider(GuiGraphicsExtractor g, int x1, int x2, int y) {
        g.fill(x1, y, x2, y + 1, DIVIDER);
    }

    /** 竖向强调条（卡片左侧装饰） */
    public static void accentBar(GuiGraphicsExtractor g, int x, int y, int h, int color) {
        g.fill(x, y, x + 2, y + h, color);
    }

    /** 圆角填充：两次内缩 fill 抹掉四角 */
    public static void roundedFill(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        if (w <= 2 || h <= 2) { g.fill(x, y, x + w, y + h, color); return; }
        g.fill(x + 1, y, x + w - 1, y + h, color);       // 主体（左右各内缩 1）
        g.fill(x, y + 1, x + w, y + h - 1, color);       // 补上下（上下各内缩 1）
    }

    /** 圆角描边 */
    public static void roundedOutline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        if (w <= 2 || h <= 2) { g.outline(x, y, x + w, y + h, color); return; }
        g.fill(x + 1, y, x + w - 1, y + 1, color);            // 上
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, color);    // 下
        g.fill(x, y + 1, x + 1, y + h - 1, color);            // 左
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);    // 右
    }

    /** 进度条 / 占比条 */
    public static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, double ratio, int color) {
        roundedFill(g, x, y, w, h, 0x33FFFFFF);
        int fillW = (int) Math.max(0, Math.min(1, ratio) * w);
        if (fillW > 0) roundedFill(g, x, y, fillW, h, color);
    }

    /* ==================== 文本 ==================== */

    public static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color) {
        g.text(font, s, x, y, color, true);
    }

    /** 右对齐（x 为右边界） */
    public static void textRight(GuiGraphicsExtractor g, Font font, String s, int rightX, int y, int color) {
        g.text(font, s, rightX - font.width(s), y, color, true);
    }

    /** 居中（centerX 为中心） */
    public static void textCenter(GuiGraphicsExtractor g, Font font, String s, int centerX, int y, int color) {
        g.centeredText(font, s, centerX, y, color);
    }

    /** 垂直居中于 [top, top+h) 区间 */
    public static int vCenter(Font font, int top, int h) {
        return top + (h - font.lineHeight) / 2 + 1;
    }

    /** 超长截断并加省略号 */
    public static String ellipsize(Font font, String s, int maxWidth) {
        if (s == null) return "";
        if (font.width(s) <= maxWidth) return s;
        StringBuilder sb = new StringBuilder();
        int ellipsisW = font.width("…");
        for (int i = 0; i < s.length(); i++) {
            if (font.width(sb.toString() + s.charAt(i)) + ellipsisW > maxWidth) break;
            sb.append(s.charAt(i));
        }
        return sb + "…";
    }

    /** 0xRRGGBB 字符串（官网称号/代系颜色）→ ARGB int，失败返回 fallback */
    public static int parseColor(String hex, int fallback) {
        if (hex == null) return fallback;
        String s = hex.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() == 3) {
            s = "" + s.charAt(0) + s.charAt(0) + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2);
        }
        if (s.length() != 6) return fallback;
        try {
            return 0xFF000000 | Integer.parseInt(s, 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}

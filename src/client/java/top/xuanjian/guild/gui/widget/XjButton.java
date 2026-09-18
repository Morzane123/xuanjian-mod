package top.xuanjian.guild.gui.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import top.xuanjian.guild.gui.UiTheme;

/**
 * 玄剑风格按钮：不依赖原版按钮贴图，全部自绘，支持多种样式与加载态。
 *
 * 26.x 的 {@code AbstractWidget.extractRenderState} 是 final，自定义外观必须重写
 * {@code extractWidgetRenderState}；点击处理重写 {@code onClick(MouseButtonEvent, boolean)}。
 */
public class XjButton extends AbstractWidget {

    public enum Style {
        /** 主操作：蓝底白字 */
        PRIMARY,
        /** 次要操作：半透明底 + 描边 */
        SECONDARY,
        /** 危险操作：红字红框 */
        DANGER,
        /** 幽灵按钮：无底仅文字 */
        GHOST,
        /** 侧边导航项：选中态为实心蓝 */
        NAV
    }

    private final Style style;
    private final Runnable action;
    private String label;
    private boolean loading = false;
    /** NAV 样式下是否处于选中态 */
    private boolean selected = false;

    public XjButton(int x, int y, int width, int height, String label, Style style, Runnable action) {
        super(x, y, width, height, Component.literal(label == null ? "" : label));
        this.label = label == null ? "" : label;
        this.style = style;
        this.action = action;
    }

    /** 简易构造：默认高度 */
    public static XjButton of(int x, int y, int width, String label, Style style, Runnable action) {
        return new XjButton(x, y, width, UiTheme.BTN_H, label, style, action);
    }

    public XjButton selected(boolean v) {
        this.selected = v;
        return this;
    }

    public void setLabel(String label) {
        this.label = label == null ? "" : label;
        setMessage(Component.literal(this.label));
    }

    public void setLoading(boolean v) {
        this.loading = v;
        this.active = !v;
    }

    public boolean isLoading() {
        return loading;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (!this.visible) return;
        boolean hover = this.isHoveredOrFocused() && this.active;
        int x = getX(), y = getY(), w = this.width, h = this.height;

        int bg;
        int border;
        int textColor;

        switch (style) {
            case PRIMARY -> {
                bg = this.active ? (hover ? 0xFF3B82F6 : UiTheme.NAV_ACTIVE) : 0x552B3A55;
                border = this.active ? 0x807CB0FF : 0x33FFFFFF;
                textColor = this.active ? UiTheme.TEXT : UiTheme.TEXT_DISABLED;
            }
            case DANGER -> {
                bg = hover && this.active ? 0x40FF5555 : 0x22FF5555;
                border = 0x80F87171;
                textColor = this.active ? UiTheme.DANGER : UiTheme.TEXT_DISABLED;
            }
            case GHOST -> {
                bg = hover && this.active ? 0x22FFFFFF : 0x00000000;
                border = 0x00000000;
                textColor = this.active ? UiTheme.TEXT_DIM : UiTheme.TEXT_DISABLED;
            }
            case NAV -> {
                bg = selected ? UiTheme.NAV_ACTIVE : (hover ? UiTheme.NAV_HOVER : 0x00000000);
                border = selected ? 0x807CB0FF : 0x00000000;
                textColor = selected ? UiTheme.TEXT : (hover ? UiTheme.TEXT : UiTheme.TEXT_DIM);
            }
            default -> {
                bg = hover && this.active ? UiTheme.CARD_BG_HOVER : UiTheme.CARD_BG;
                border = hover && this.active ? UiTheme.CARD_BORDER_HOVER : UiTheme.CARD_BORDER;
                textColor = this.active ? UiTheme.TEXT : UiTheme.TEXT_DISABLED;
            }
        }

        if (bg != 0x00000000) UiTheme.roundedFill(g, x, y, w, h, bg);
        if (border != 0x00000000) UiTheme.roundedOutline(g, x, y, w, h, border);

        String shown = loading ? "处理中…" : label;
        UiTheme.textCenter(g, net.minecraft.client.Minecraft.getInstance().font,
                shown, x + w / 2, UiTheme.vCenter(net.minecraft.client.Minecraft.getInstance().font, y, h), textColor);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (!this.active) return;
        playDownSound(net.minecraft.client.Minecraft.getInstance().getSoundManager());
        if (action != null) action.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}

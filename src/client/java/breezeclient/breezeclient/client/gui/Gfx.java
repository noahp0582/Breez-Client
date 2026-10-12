package breezeclient.breezeclient.client.gui;

import breezeclient.breezeclient.core.gui.ColorMath;
import breezeclient.breezeclient.core.gui.GuiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Thin drawing helper: theme colors, rounded rectangles, text. All coordinates are in GUI space (already scaled). */
public final class Gfx {
	public final GuiGraphics g;
	public final Font font;
	public final GuiTheme theme;

	public Gfx(GuiGraphics g, GuiTheme theme) {
		this.g = g;
		this.font = Minecraft.getInstance().font;
		this.theme = theme;
	}

	public int accent() { return theme.accent.get(); }
	public int text() { return theme.textColor.get(); }
	public int dim() { return ColorMath.mix(theme.textColor.get(), 0xFF000000, 0.35); }
	public int panel() { return theme.panelColor(); }
	public int title() { return ColorMath.withAlpha(theme.titleColor(), Math.max(200, ColorMath.alpha(theme.panelColor()))); }
	public int hover() { return 0x22FFFFFF; }
	public int widgetBg() { return ColorMath.mix(theme.background.get() | 0xFF000000, 0xFFFFFFFF, 0.1); }

	public int lineHeight() { return 9; }

	public int width(String s) { return font.width(s); }

	public void rect(int x, int y, int w, int h, int c) {
		if (w > 0 && h > 0) g.fill(x, y, x + w, y + h, c);
	}

	/** Rounded rectangle (radius 2) built from non-overlapping fills so translucent colors stay even. */
	public void rrect(int x, int y, int w, int h, int c) {
		if (!theme.rounded.get() || w < 6 || h < 6) {
			rect(x, y, w, h, c);
			return;
		}
		rect(x + 2, y, w - 4, 1, c);
		rect(x + 1, y + 1, w - 2, 1, c);
		rect(x, y + 2, w, h - 4, c);
		rect(x + 1, y + h - 2, w - 2, 1, c);
		rect(x + 2, y + h - 1, w - 4, 1, c);
	}

	/** Top-to-bottom gradient. */
	public void gradient(int x, int y, int w, int h, int top, int bottom) {
		if (w > 0 && h > 0) g.fillGradient(x, y, x + w, y + h, top, bottom);
	}

	/** Rounded rectangle with a top-to-bottom gradient. */
	public void rgradient(int x, int y, int w, int h, int top, int bottom) {
		if (!theme.rounded.get() || w < 6 || h < 6) {
			gradient(x, y, w, h, top, bottom);
			return;
		}
		rect(x + 2, y, w - 4, 1, top);
		rect(x + 1, y + 1, w - 2, 1, top);
		gradient(x, y + 2, w, h - 4, top, bottom);
		rect(x + 1, y + h - 2, w - 2, 1, bottom);
		rect(x + 2, y + h - 1, w - 4, 1, bottom);
	}

	/** Soft drop shadow around a panel: four rings that fade out, drawn outside the panel so its own color is untouched. */
	public void shadow(int x, int y, int w, int h) {
		if (!theme.shadows.get()) return;
		for (int i = 1; i <= 4; i++) {
			int c = (46 - i * 10) << 24;
			int sx = x - i, sy = y - i + 2, sw = w + i * 2, sh = h + i * 2;
			rect(sx + 1, sy, sw - 2, 1, c);
			rect(sx + 1, sy + sh - 1, sw - 2, 1, c);
			rect(sx, sy + 1, 1, sh - 2, c);
			rect(sx + sw - 1, sy + 1, 1, sh - 2, c);
		}
	}

	/** A color lightened toward white (t > 0) or darkened toward black (t < 0), alpha kept. */
	public static int shade(int argb, double t) {
		int a = argb & 0xFF000000;
		return a | (ColorMath.mix(argb | 0xFF000000, t >= 0 ? 0xFFFFFFFF : 0xFF000000, Math.abs(t)) & 0xFFFFFF);
	}

	public void outline(int x, int y, int w, int h, int c) {
		rect(x, y, w, 1, c);
		rect(x, y + h - 1, w, 1, c);
		rect(x, y + 1, 1, h - 2, c);
		rect(x + w - 1, y + 1, 1, h - 2, c);
	}

	public void text(String s, int x, int y, int color) { g.drawString(font, s, x, y, color, false); }

	public void textShadow(String s, int x, int y, int color) { g.drawString(font, s, x, y, color, true); }

	public void textCentered(String s, int cx, int y, int color) { text(s, cx - width(s) / 2, y, color); }

	/** Text clipped with an ellipsis to fit {@code maxW} pixels. */
	public String fit(String s, int maxW) {
		if (width(s) <= maxW) return s;
		String e = "..";
		int ew = width(e);
		StringBuilder sb = new StringBuilder();
		for (char ch : s.toCharArray()) {
			if (width(sb.toString() + ch) + ew > maxW) break;
			sb.append(ch);
		}
		return sb + e;
	}

	public void scissor(int x, int y, int w, int h) { g.enableScissor(x, y, x + Math.max(0, w), y + Math.max(0, h)); }

	public void endScissor() { g.disableScissor(); }

	public boolean in(int mx, int my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }
}

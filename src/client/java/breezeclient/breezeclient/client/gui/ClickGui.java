package breezeclient.breezeclient.client.gui;

import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.ModuleManager;
import breezeclient.breezeclient.core.config.ConfigManager;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.ColorSetting;
import breezeclient.breezeclient.core.settings.EnumSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import breezeclient.breezeclient.core.settings.Setting;
import breezeclient.breezeclient.core.settings.StringListSetting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ClickGui extends Screen {
	private static final int W = 120;
	private static final int ROW = 14;
	private static final int HEAD = 16;

	private static final Map<Category, int[]> POSITIONS = new LinkedHashMap<>();
	private static final Set<String> EXPANDED = new HashSet<>();

	private final ModuleManager modules;
	private final ConfigManager config;
	private Category dragging;
	private int dragDx, dragDy;
	private NumberSetting slider;
	private int sliderX;

	public ClickGui(ModuleManager modules, ConfigManager config) {
		super(Component.literal("Breez"));
		this.modules = modules;
		this.config = config;
		int x = 10;
		for (Category c : Category.values()) {
			POSITIONS.computeIfAbsent(c, k -> new int[] {0, 10});
			if (POSITIONS.get(c)[0] == 0) POSITIONS.get(c)[0] = x;
			x += W + 8;
		}
	}

	@Override
	public boolean isPauseScreen() { return false; }

	@Override
	public void onClose() {
		config.save();
		super.onClose();
	}

	private List<Module> in(Category c) {
		List<Module> out = new ArrayList<>();
		for (Module m : modules.all()) if (m.category() == c) out.add(m);
		return out;
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float delta) {
		Font font = this.font;
		for (Category c : Category.values()) {
			int[] p = POSITIONS.get(c);
			int x = p[0], y = p[1];
			g.fill(x, y, x + W, y + HEAD, 0xFF1B6B8F);
			g.drawString(font, c.label(), x + 4, y + 4, 0xFFFFFFFF, true);
			y += HEAD;
			for (Module m : in(c)) {
				boolean hover = mx >= x && mx < x + W && my >= y && my < y + ROW;
				int bg = m.enabled() ? 0xE0208FB0 : (hover ? 0xE0303030 : 0xE0181818);
				g.fill(x, y, x + W, y + ROW, bg);
				g.drawString(font, m.name(), x + 4, y + 3, 0xFFFFFFFF, false);
				y += ROW;
				if (EXPANDED.contains(m.name())) {
					for (Setting<?> s : m.settings()) {
						g.fill(x, y, x + W, y + ROW, 0xE0101010);
						drawSetting(g, font, s, x, y);
						y += ROW;
					}
				}
			}
		}
	}

	private void drawSetting(GuiGraphics g, Font font, Setting<?> s, int x, int y) {
		String label;
		if (s instanceof BoolSetting b) {
			label = s.name() + ": " + (b.get() ? "ON" : "OFF");
		} else if (s instanceof NumberSetting n) {
			double frac = (n.get() - n.min()) / (n.max() - n.min());
			g.fill(x + 2, y + ROW - 3, x + 2 + (int) ((W - 4) * frac), y + ROW - 1, 0xFF30C0E0);
			label = s.name() + ": " + (n.max() - n.min() > 20 ? String.valueOf(n.asInt()) : String.format("%.2f", n.get()));
		} else if (s instanceof EnumSetting<?> e) {
			label = s.name() + ": " + e.get().name();
		} else if (s instanceof StringListSetting l) {
			label = s.name() + ": " + l.get().size() + " items";
		} else if (s instanceof ColorSetting c) {
			label = s.name();
			g.fill(x + W - 14, y + 3, x + W - 4, y + ROW - 3, c.get() | 0xFF000000);
		} else {
			label = s.name();
		}
		g.drawString(font, label, x + 6, y + 2, 0xFFCCCCCC, false);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x(), my = event.y();
		int button = event.button();
		for (Category c : Category.values()) {
			int[] p = POSITIONS.get(c);
			int x = p[0], y = p[1];
			if (mx >= x && mx < x + W && my >= y && my < y + HEAD) {
				dragging = c;
				dragDx = (int) mx - x;
				dragDy = (int) my - y;
				return true;
			}
			y += HEAD;
			for (Module m : in(c)) {
				if (mx >= x && mx < x + W && my >= y && my < y + ROW) {
					if (button == 0) m.toggle();
					else if (button == 1) {
						if (!EXPANDED.remove(m.name())) EXPANDED.add(m.name());
					}
					return true;
				}
				y += ROW;
				if (EXPANDED.contains(m.name())) {
					for (Setting<?> s : m.settings()) {
						if (mx >= x && mx < x + W && my >= y && my < y + ROW) {
							clickSetting(s, x, mx, button);
							return true;
						}
						y += ROW;
					}
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void clickSetting(Setting<?> s, int x, double mx, int button) {
		if (s instanceof BoolSetting b) b.toggle();
		else if (s instanceof EnumSetting<?> e) e.next();
		else if (s instanceof NumberSetting n) {
			slider = n;
			sliderX = x;
			setSlider(mx);
		}
	}

	private void setSlider(double mx) {
		double frac = Math.max(0, Math.min(1, (mx - sliderX - 2) / (W - 4)));
		slider.set(slider.min() + frac * (slider.max() - slider.min()));
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging != null) {
			int[] p = POSITIONS.get(dragging);
			p[0] = (int) event.x() - dragDx;
			p[1] = (int) event.y() - dragDy;
			return true;
		}
		if (slider != null) {
			setSlider(event.x());
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = null;
		slider = null;
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == 344) { // right shift closes
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}
}

package breezeclient.breezeclient.client.gui;

import breezeclient.breezeclient.core.gui.ColorMath;
import breezeclient.breezeclient.core.gui.Fuzzy;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.ColorSetting;
import breezeclient.breezeclient.core.settings.EnumSetting;
import breezeclient.breezeclient.core.settings.KeySetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import breezeclient.breezeclient.core.settings.StringListSetting;
import breezeclient.breezeclient.core.settings.StringSetting;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** The widget set: checkbox, button, text box, slider, dropdown, key bind, color picker, list editor. */
public final class Controls {
	private Controls() {}

	public static final int ROW_H = 13;

	// Label --------------------------------------------------------------------------------------------------------

	public static class Label extends Widget {
		private final Supplier<String> text;
		private final boolean dim;

		public Label(Supplier<String> text, boolean dim) {
			this.text = text;
			this.dim = dim;
		}

		@Override
		protected int height(Gfx g, int w) { return ROW_H - 2; }

		@Override
		public void render(Gfx g, int mx, int my) { g.text(g.fit(text.get(), w), x, y + 2, dim ? g.dim() : g.text()); }
	}

	// Button --------------------------------------------------------------------------------------------------------

	public static class Button extends Widget {
		private final Supplier<String> label;
		private final Runnable action;
		public boolean accentFill;

		public Button(String label, Runnable action) { this(() -> label, action); }

		public Button(Supplier<String> label, Runnable action) {
			this.label = label;
			this.action = action;
		}

		@Override
		public void render(Gfx g, int mx, int my) {
			double hov = Anim.to(this, hit(mx, my));
			int bg = accentFill ? ColorMath.mix(g.theme.accentFaded(0.75), g.accent(), hov) : ColorMath.mix(g.widgetBg(), g.accent() & 0x00FFFFFF | 0xAA000000, hov * 0.75);
			g.rgradient(x, y, w, h, Gfx.shade(bg, 0.1), bg);
			g.textCentered(g.fit(label.get(), w - 4), x + w / 2, y + 2, g.text());
		}

		@Override
		public boolean click(int mx, int my, int button) {
			if (button != 0 || !hit(mx, my)) return false;
			action.run();
			return true;
		}
	}

	// Checkbox ---------------------------------------------------------------------------------------------------------

	public static class Checkbox extends Widget {
		private final BooleanSupplier get;
		private final Consumer<Boolean> set;

		public Checkbox(BooleanSupplier get, Consumer<Boolean> set) {
			this.get = get;
			this.set = set;
		}

		public Checkbox(BoolSetting s) { this(s::get, s::set); }

		@Override
		public void render(Gfx g, int mx, int my) {
			int bx = x + w - 17, by = y + 2;
			double on = Anim.to(this, get.getAsBoolean());
			int track = ColorMath.mix(hit(mx, my) ? ColorMath.mix(g.widgetBg(), 0xFFFFFFFF, 0.15) : g.widgetBg(), g.theme.accentFaded(0.85) | 0xFF000000, on);
			g.rrect(bx, by, 16, 9, track);
			g.rrect(bx + 1 + (int) Math.round(on * 7), by + 1, 7, 7, ColorMath.mix(0xFFB8B8C0, 0xFFFFFFFF, on));
		}

		@Override
		public boolean click(int mx, int my, int button) {
			if (button != 0 || !hit(mx, my)) return false;
			set.accept(!get.getAsBoolean());
			return true;
		}
	}

	// Text box ----------------------------------------------------------------------------------------------------------

	public static class TextBox extends Widget {
		public String text = "";
		public String placeholder = "";
		public int cursor;
		public Consumer<String> onCommit = s -> {};
		public Consumer<String> onChange = s -> {};
		public Runnable onCancel = () -> {};

		public TextBox() {}

		public TextBox(String initial) {
			this.text = initial;
			this.cursor = initial.length();
		}

		public void setText(String t) {
			text = t;
			cursor = Math.min(cursor, t.length());
		}

		public boolean isFocused() { return Widget.focused() == this; }

		@Override
		public void render(Gfx g, int mx, int my) {
			boolean f = isFocused();
			g.rrect(x, y, w, h, g.widgetBg());
			if (f) g.rect(x + 1, y + h - 1, w - 2, 1, g.accent());
			String shown = text;
			int tw = w - 6;
			int start = 0;
			while (start < shown.length() && g.width(shown.substring(start, cursor)) > tw - 2) start++;
			String vis = shown.substring(start);
			vis = g.fit(vis, tw);
			if (text.isEmpty() && !f) g.text(placeholder, x + 3, y + 2, g.dim());
			else g.text(vis, x + 3, y + 2, g.text());
			if (f && (System.currentTimeMillis() / 500) % 2 == 0) {
				int cx = x + 3 + g.width(shown.substring(start, Math.max(start, cursor)));
				g.rect(cx, y + 2, 1, 9, g.text());
			}
		}

		@Override
		public boolean click(int mx, int my, int button) {
			if (!hit(mx, my)) {
				if (isFocused()) Widget.clearFocus();
				return false;
			}
			Widget.setFocus(this);
			cursor = text.length();
			return true;
		}

		@Override
		public boolean key(int key, int mods) {
			if (!isFocused()) return false;
			boolean ctrl = (mods & 2) != 0;
			switch (key) {
				case 259 -> { // backspace
					if (cursor > 0) {
						text = text.substring(0, cursor - 1) + text.substring(cursor);
						cursor--;
						onChange.accept(text);
					}
				}
				case 261 -> { // delete
					if (cursor < text.length()) {
						text = text.substring(0, cursor) + text.substring(cursor + 1);
						onChange.accept(text);
					}
				}
				case 263 -> cursor = Math.max(0, cursor - 1);
				case 262 -> cursor = Math.min(text.length(), cursor + 1);
				case 268 -> cursor = 0;
				case 269 -> cursor = text.length();
				case 257, 335 -> {
					Widget.clearFocus();
					onCommit.accept(text);
				}
				case 256 -> {
					Widget.clearFocus();
					onCancel.run();
				}
				case 86 -> {
					if (ctrl) {
						String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
						if (clip != null) insert(clip.replace("\n", " ").replace("\r", ""));
					}
				}
				case 65 -> {
					if (ctrl) cursor = text.length();
				}
				default -> {
					return true; // swallow other keys while typing
				}
			}
			return true;
		}

		@Override
		public boolean typed(int codepoint) {
			if (!isFocused() || codepoint < 32 || codepoint == 127) return false;
			insert(Character.toString(codepoint));
			return true;
		}

		private void insert(String s) {
			text = text.substring(0, cursor) + s + text.substring(cursor);
			cursor += s.length();
			onChange.accept(text);
		}
	}

	// Slider ---------------------------------------------------------------------------------------------------------------

	public static class Slider extends Widget {
		private final NumberSetting s;
		private boolean dragging;
		private TextBox edit;
		private final boolean integral;

		public Slider(NumberSetting s) {
			this.s = s;
			this.integral = s.min() == Math.floor(s.min()) && s.max() == Math.floor(s.max()) && s.defaultValue() == Math.floor(s.defaultValue())
				&& s.max() - s.min() >= 10;
		}

		public static String format(double v, boolean integral) {
			if (integral || Math.abs(v - Math.rint(v)) < 1e-9) return String.valueOf((long) Math.rint(v));
			String t = String.format("%.2f", v);
			return t.contains(".") ? t.replaceAll("0+$", "").replaceAll("\\.$", "") : t;
		}

		@Override
		public void render(Gfx g, int mx, int my) {
			if (edit != null) {
				edit.layout(g, x, y, w);
				edit.render(g, mx, my);
				return;
			}
			g.rrect(x, y, w, h, g.widgetBg());
			double frac = (s.get() - s.min()) / (s.max() - s.min());
			int fw = Math.max(2, (int) (w * frac));
			g.rgradient(x, y, fw, h, Gfx.shade(g.theme.accentFaded(0.85), 0.14), g.theme.accentFaded(0.7));
			if (dragging || hit(mx, my)) g.rect(Math.min(x + w - 2, x + fw - 1), y + 1, 2, h - 2, 0xDDFFFFFF);
			g.textCentered(format(s.get(), integral), x + w / 2, y + 2, g.text());
		}

		private void apply(int mx) {
			double frac = Math.max(0, Math.min(1, (mx - x) / (double) w));
			double v = s.min() + frac * (s.max() - s.min());
			double step = integral ? 1 : (s.max() - s.min() <= 10 ? 0.01 : 0.05);
			s.set(Math.round(v / step) * step);
		}

		@Override
		public boolean click(int mx, int my, int button) {
			if (edit != null) {
				boolean in = edit.click(mx, my, button);
				if (!edit.isFocused()) edit = null;
				return in;
			}
			if (!hit(mx, my)) return false;
			if (button == 1) {
				edit = new TextBox(format(s.get(), integral));
				edit.onCommit = t -> {
					try {
						s.set(Double.parseDouble(t.trim()));
					} catch (NumberFormatException ignored) {
						// keep old value
					}
					edit = null;
				};
				edit.onCancel = () -> edit = null;
				Widget.setFocus(edit);
				return true;
			}
			dragging = true;
			apply(mx);
			return true;
		}

		@Override
		public void drag(int mx, int my) { if (dragging) apply(mx); }

		@Override
		public void release(int mx, int my) { dragging = false; }

		@Override
		public boolean key(int key, int mods) { return edit != null && edit.key(key, mods); }

		@Override
		public boolean typed(int cp) { return edit != null && edit.typed(cp); }
	}

	// Dropdown ---------------------------------------------------------------------------------------------------------------

	public static class Dropdown extends Widget {
		private final Supplier<String> current;
		private final Supplier<List<String>> options;
		private final Consumer<String> choose;
		private boolean open;

		public Dropdown(EnumSetting<?> s) { this(() -> s.get().name(), s::names, s::select); }

		public Dropdown(Supplier<String> current, Supplier<List<String>> options, Consumer<String> choose) {
			this.current = current;
			this.options = options;
			this.choose = choose;
		}

		@Override
		protected int height(Gfx g, int w) { return ROW_H + (open ? options.get().size() * ROW_H : 0); }

		@Override
		public void render(Gfx g, int mx, int my) {
			g.rrect(x, y, w, ROW_H, g.widgetBg());
			g.text(g.fit(pretty(current.get()), w - 14), x + 3, y + 2, g.text());
			g.text(open ? "^" : "v", x + w - 9, y + 2, g.dim());
			if (!open) return;
			int yy = y + ROW_H;
			for (String o : options.get()) {
				boolean sel = o.equals(current.get());
				if (my >= yy && my < yy + ROW_H && mx >= x && mx < x + w) g.rect(x, yy, w, ROW_H, g.hover());
				g.text(pretty(o), x + 6, yy + 2, sel ? g.accent() : g.text());
				yy += ROW_H;
			}
		}

		private static String pretty(String s) {
			String t = s.toLowerCase().replace('_', ' ');
			return t.isEmpty() ? t : Character.toUpperCase(t.charAt(0)) + t.substring(1);
		}

		@Override
		public boolean click(int mx, int my, int button) {
			if (button != 0 || !hit(mx, my)) {
				if (open && button == 0) open = false;
				return false;
			}
			if (my < y + ROW_H) {
				open = !open;
				return true;
			}
			int idx = (my - y - ROW_H) / ROW_H;
			List<String> o = options.get();
			if (idx >= 0 && idx < o.size()) choose.accept(o.get(idx));
			open = false;
			return true;
		}
	}

	// Key bind ---------------------------------------------------------------------------------------------------------------------

	public static class KeyBind extends Widget {
		private final IntSupplier get;
		private final IntConsumer set;
		private boolean listening;

		public KeyBind(IntSupplier get, IntConsumer set) {
			this.get = get;
			this.set = set;
		}

		public KeyBind(KeySetting s) { this(s::get, s::set); }

		public static String keyName(int key) {
			if (key < 0) return "None";
			try {
				return InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
			} catch (RuntimeException e) {
				return "Key " + key;
			}
		}

		@Override
		public void render(Gfx g, int mx, int my) {
			g.rrect(x, y, w, ROW_H, listening ? g.theme.accentFaded(0.5) : g.widgetBg());
			g.textCentered(listening ? "Press a key..." : keyName(get.getAsInt()), x + w / 2, y + 2, g.text());
		}

		@Override
		public boolean click(int mx, int my, int button) {
			if (!hit(mx, my)) {
				listening = false;
				return false;
			}
			if (button == 1) {
				set.accept(-1);
				listening = false;
				return true;
			}
			listening = true;
			Widget.setFocus(this);
			return true;
		}

		@Override
		public boolean key(int key, int mods) {
			if (!listening) return false;
			listening = false;
			Widget.clearFocus();
			if (key == 256) return true; // escape cancels
			set.accept(key == 259 || key == 261 ? -1 : key);
			return true;
		}
	}

	// Color picker -----------------------------------------------------------------------------------------------------------------------

	public static class ColorPicker extends Widget {
		private final ColorSetting s;
		private boolean open;
		private double hue, sat, val;
		private int drag; // 0 none, 1 sv, 2 hue, 3 alpha
		private final TextBox hex = new TextBox();
		private boolean synced;

		public ColorPicker(ColorSetting s) {
			this.s = s;
			hex.onCommit = t -> {
				Integer c = ColorMath.parseHex(t);
				if (c != null) {
					s.set(c);
					pull();
				}
			};
		}

		private void pull() {
			double[] hsv = ColorMath.toHsv(s.get());
			if (hsv[1] > 0 || hsv[2] > 0) hue = hsv[0];
			sat = hsv[1];
			val = hsv[2];
			hex.setText(ColorMath.toHex(s.get()));
		}

		private void push() {
			s.set(ColorMath.fromHsv(hue, sat, val, ColorMath.alpha(s.get())));
			hex.setText(ColorMath.toHex(s.get()));
		}

		@Override
		protected int height(Gfx g, int w) { return ROW_H + (open ? 66 : 0); }

		@Override
		public void render(Gfx g, int mx, int my) {
			if (!synced) {
				pull();
				synced = true;
			}
			int c = s.get();
			g.rrect(x, y, w, ROW_H, g.widgetBg());
			g.rect(x + 3, y + 3, 14, ROW_H - 6, 0xFF000000 | c);
			g.text(ColorMath.toHex(c), x + 22, y + 2, g.text());
			g.text(open ? "^" : "v", x + w - 9, y + 2, g.dim());
			if (!open) return;
			int top = y + ROW_H + 3, boxW = Math.max(40, w - 52), boxH = 58;
			// saturation / value square
			int cols = Math.min(boxW, 48);
			for (int i = 0; i < cols; i++) {
				int x1 = x + i * boxW / cols, x2 = x + (i + 1) * boxW / cols;
				int topC = ColorMath.fromHsv(hue, i / (double) (cols - 1), 1, 255);
				g.g.fillGradient(x1, top, x2, top + boxH, topC, 0xFF000000);
			}
			int px = x + (int) (sat * (boxW - 1)), py = top + (int) ((1 - val) * (boxH - 1));
			g.outline(px - 2, py - 2, 5, 5, 0xFFFFFFFF);
			// hue bar
			int hx = x + boxW + 6;
			for (int i = 0; i < 6; i++) {
				g.g.fillGradient(hx, top + i * boxH / 6, hx + 9, top + (i + 1) * boxH / 6, ColorMath.fromHsv(i * 60, 1, 1, 255), ColorMath.fromHsv((i + 1) * 60, 1, 1, 255));
			}
			g.rect(hx - 1, top + (int) (hue / 360.0 * (boxH - 1)), 11, 1, 0xFFFFFFFF);
			// alpha bar
			int ax = hx + 14;
			g.g.fillGradient(ax, top, ax + 9, top + boxH, 0xFF000000 | c, 0x00000000 | (c & 0xFFFFFF));
			g.outline(ax - 1, top - 1, 11, boxH + 2, g.dim());
			g.rect(ax - 1, top + (int) ((1 - ColorMath.alpha(c) / 255.0) * (boxH - 1)), 11, 1, 0xFFFFFFFF);
		}

		private int boxW() { return Math.max(40, w - 52); }

		private void dragTo(int mx, int my) {
			int top = y + ROW_H + 3, boxH = 58, boxW = boxW();
			if (drag == 1) {
				sat = Math.max(0, Math.min(1, (mx - x) / (double) (boxW - 1)));
				val = 1 - Math.max(0, Math.min(1, (my - top) / (double) (boxH - 1)));
				push();
			} else if (drag == 2) {
				hue = Math.max(0, Math.min(359.9, (my - top) / (double) (boxH - 1) * 360));
				push();
			} else if (drag == 3) {
				int a = (int) Math.round((1 - Math.max(0, Math.min(1, (my - top) / (double) (boxH - 1)))) * 255);
				s.set(ColorMath.withAlpha(s.get(), a));
				hex.setText(ColorMath.toHex(s.get()));
			}
		}

		@Override
		public boolean click(int mx, int my, int button) {
			if (!hit(mx, my)) return false;
			if (my < y + ROW_H) {
				open = !open;
				return true;
			}
			if (!open || button != 0) return true;
			int hx = x + boxW() + 6, ax = hx + 14;
			drag = mx < x + boxW() ? 1 : (mx >= hx && mx < hx + 10 ? 2 : (mx >= ax && mx < ax + 10 ? 3 : 0));
			if (drag != 0) dragTo(mx, my);
			return true;
		}

		@Override
		public void drag(int mx, int my) { if (drag != 0) dragTo(mx, my); }

		@Override
		public void release(int mx, int my) { drag = 0; }
	}

	// List editor (string / block / item / entity lists) --------------------------------------------------------------------------------------

	/**
	 * A list of ids shown as removable chips. "+ Add" opens a picker: a category button, a search box and a scrollable list of
	 * everything that can be added; click an entry to add it, click again to remove it. Lists of free text (names) can still
	 * be typed, and offer the online players when the setting has an options supplier.
	 */
	public static class ListEditor extends Widget {
		private static final int VISIBLE = 9;

		private static List<String> blocks, items, entities;

		private final StringListSetting s;
		private boolean adding;
		private final TextBox box = new TextBox();
		private final List<int[]> chipRects = new ArrayList<>();
		private int addY;

		private int category;
		private List<String> filtered = List.of();
		private int scrollRow;
		private boolean dirty = true;
		private int listY, catW;
		private int revealFrames;

		/** Developer test hooks: open every picker at once and click an entry without a mouse. */
		public static boolean devOpen;
		public static ListEditor last;

		public void devPick(int index) {
			if (index >= 0 && index < filtered.size()) toggle(filtered.get(index));
		}

		public int devEntries() { return filtered.size(); }

		public ListEditor(StringListSetting s) {
			this.s = s;
			this.adding = devOpen;
			if (devOpen) revealFrames = 6;
			last = this;
			box.placeholder = pickerAvailable() ? "search..." : "type a name and press Enter";
			box.onChange = t -> {
				dirty = true;
				scrollRow = 0;
			};
			box.onCommit = t -> {
				String pick = t.trim();
				if (pick.isBlank()) return;
				List<String> src = source();
				if (src != null && s.kind() != StringListSetting.Kind.PLAIN) {
					// typed text picks the best match (or the exact id)
					String n = normalize(pick);
					if (src.contains(n)) pick = n;
					else {
						List<String> sg = Fuzzy.rank(pick, src, 1);
						if (sg.isEmpty()) return;
						pick = sg.get(0);
					}
				} else if (src != null) {
					for (String o : src) if (o.equalsIgnoreCase(pick)) pick = o;
				}
				s.add(s.format(pick));
				box.setText("");
				dirty = true;
			};
			box.onCancel = () -> adding = false;
		}

		private static String normalize(String t) {
			String l = t.trim().toLowerCase();
			return l.contains(":") || l.isEmpty() ? l : "minecraft:" + l;
		}

		private static List<String> all(StringListSetting.Kind k) {
			if (blocks == null) {
				blocks = new ArrayList<>();
				for (var id : BuiltInRegistries.BLOCK.keySet()) blocks.add(id.toString());
				items = new ArrayList<>();
				for (var id : BuiltInRegistries.ITEM.keySet()) items.add(id.toString());
				entities = new ArrayList<>();
				for (var id : BuiltInRegistries.ENTITY_TYPE.keySet()) entities.add(id.toString());
				java.util.Collections.sort(blocks);
				java.util.Collections.sort(items);
				java.util.Collections.sort(entities);
			}
			return switch (k) {
				case BLOCK -> blocks;
				case ITEM -> items;
				case ENTITY -> entities;
				default -> List.of();
			};
		}

		/** Everything the picker offers, or null for a free text list with no options. */
		private List<String> source() {
			List<String> o = s.optionList();
			if (o != null) return o;
			return s.kind() == StringListSetting.Kind.PLAIN ? null : all(s.kind());
		}

		private boolean pickerAvailable() { return s.optionList() != null || s.kind() != StringListSetting.Kind.PLAIN; }

		private static String shortName(String id) { return id.startsWith("minecraft:") ? id.substring(10) : id; }

		// ---- categories ----

		private static final String[] BLOCK_CATS = {"All", "Ores", "Storage", "Redstone", "Building", "Special"};
		private static final String[] ITEM_CATS = {"All", "Valuables", "Storage", "Redstone", "Building", "Special"};

		private String[] cats() {
			if (s.optionList() != null) return new String[] {"All"};
			return switch (s.kind()) {
				case BLOCK -> BLOCK_CATS;
				case ITEM -> ITEM_CATS;
				default -> new String[] {"All"};
			};
		}

		private static boolean has(String p, String... words) {
			for (String w : words) if (p.contains(w)) return true;
			return false;
		}

		private static boolean inCategory(String cat, String id) {
			String p = shortName(id);
			return switch (cat) {
				case "Ores" -> has(p, "_ore") || p.equals("ancient_debris") || p.startsWith("raw_") || p.contains("debris");
				case "Valuables" -> has(p, "netherite", "diamond", "emerald", "totem", "elytra", "golden_apple", "beacon", "nether_star", "trident",
					"mace", "heavy_core", "enchanted_book", "shulker", "ancient_debris", "spawner", "dragon", "wither");
				case "Storage" -> has(p, "chest", "barrel", "shulker", "hopper", "dropper", "dispenser", "furnace", "smoker", "crafter", "decorated_pot", "bundle");
				case "Redstone" -> has(p, "redstone", "piston", "observer", "repeater", "comparator", "lever", "button", "pressure_plate", "tnt",
					"daylight", "target", "note_block", "rail", "tripwire", "sculk_sensor", "lightning_rod", "dropper", "dispenser", "hopper");
				case "Building" -> has(p, "planks", "bricks", "concrete", "glass", "wool", "terracotta", "slab", "stairs", "_wall", "fence", "door",
					"carpet", "_log", "_wood", "quartz", "prismarine", "copper");
				case "Special" -> has(p, "spawner", "beacon", "bedrock", "obsidian", "portal", "command_block", "structure", "barrier", "conduit",
					"anchor", "lodestone", "trial", "vault", "reinforced", "sculk", "end_", "ancient", "amethyst", "_bed", "bed_", "dragon_egg");
				default -> true;
			};
		}

		private void refresh() {
			List<String> src = source();
			if (src == null) {
				filtered = List.of();
				return;
			}
			String cat = cats()[Math.min(category, cats().length - 1)];
			List<String> pool = src;
			if (!cat.equals("All")) {
				pool = new ArrayList<>();
				for (String id : src) if (inCategory(cat, id)) pool.add(id);
			}
			String q = box.text.trim();
			filtered = q.isEmpty() ? pool : Fuzzy.rank(q, pool, 400);
			scrollRow = Math.max(0, Math.min(scrollRow, Math.max(0, filtered.size() - VISIBLE)));
			dirty = false;
		}

		/** Whether an entry (an unformatted id or name) is already in the list. */
		private boolean added(String option) {
			for (String it : s.get()) {
				if (it.equalsIgnoreCase(option) || it.toLowerCase().startsWith(option.toLowerCase() + "=")) return true;
			}
			return false;
		}

		private void toggle(String option) {
			if (added(option)) {
				for (String it : new ArrayList<>(s.get())) {
					if (it.equalsIgnoreCase(option) || it.toLowerCase().startsWith(option.toLowerCase() + "=")) s.remove(it);
				}
			} else {
				s.add(s.format(option));
			}
		}

		// ---- layout ----

		@Override
		protected int height(Gfx g, int w) {
			int rows = layoutChips(g, w, false);
			int hgt = rows * (ROW_H + 1) + ROW_H + 2;
			if (adding) {
				if (pickerAvailable() && (dirty || s.optionList() != null)) refresh();
				hgt += ROW_H + 2; // category + search row
				if (pickerAvailable()) hgt += VISIBLE * ROW_H + ROW_H + 2;
			}
			return hgt;
		}

		private int layoutChips(Gfx g, int w, boolean record) {
			if (record) chipRects.clear();
			int cx = 0, row = 0;
			for (String it : s.get()) {
				int cw = g.width(shortName(it)) + 18;
				if (cx > 0 && cx + cw > w) {
					cx = 0;
					row++;
				}
				if (record) chipRects.add(new int[] {cx, row, cw});
				cx += cw + 3;
			}
			return s.get().isEmpty() ? 0 : row + 1;
		}

		@Override
		public void render(Gfx g, int mx, int my) {
			int rows = layoutChips(g, w, true);
			for (int i = 0; i < s.get().size(); i++) {
				int[] r = chipRects.get(i);
				int cx = x + r[0], cy = y + r[1] * (ROW_H + 1);
				g.rrect(cx, cy, r[2], ROW_H, g.widgetBg());
				g.text(shortName(s.get().get(i)), cx + 4, cy + 2, g.text());
				boolean hx = mx >= cx + r[2] - 12 && mx < cx + r[2] && my >= cy && my < cy + ROW_H;
				g.text("x", cx + r[2] - 9, cy + 2, hx ? 0xFFFF5555 : g.dim());
			}
			addY = y + rows * (ROW_H + 1);
			if (adding && revealFrames > 0) {
				revealFrames--;
				Widget.reveal = this;
			}
			boolean hov = mx >= x && mx < x + 44 && my >= addY && my < addY + ROW_H;
			g.rrect(x, addY, 44, ROW_H, hov ? g.theme.accentFaded(0.6) : g.widgetBg());
			g.textCentered(adding ? "Close" : "+ Add", x + 22, addY + 2, g.text());
			if (!adding) return;

			int rowY = addY + ROW_H + 2;
			String[] cs = cats();
			boolean showCat = cs.length > 1;
			catW = showCat ? 78 : 0;
			if (showCat) {
				boolean ch = mx >= x && mx < x + catW && my >= rowY && my < rowY + ROW_H;
				g.rrect(x, rowY, catW, ROW_H, ch ? g.theme.accentFaded(0.6) : g.widgetBg());
				g.text(g.fit(cs[Math.min(category, cs.length - 1)] + " v", catW - 6), x + 4, rowY + 2, g.text());
			}
			box.layout(g, x + catW + (showCat ? 3 : 0), rowY, w - catW - (showCat ? 3 : 0));
			box.render(g, mx, my);
			if (!pickerAvailable()) return;

			listY = rowY + ROW_H + 2;
			int listH = VISIBLE * ROW_H;
			g.rect(x, listY, w, listH, 0x55000000);
			int shown = Math.min(VISIBLE, Math.max(0, filtered.size() - scrollRow));
			for (int i = 0; i < shown; i++) {
				String id = filtered.get(scrollRow + i);
				int ry = listY + i * ROW_H;
				boolean hv = mx >= x && mx < x + w - 5 && my >= ry && my < ry + ROW_H;
				boolean on = added(id);
				if (hv) g.rect(x, ry, w - 5, ROW_H, g.hover());
				g.text(g.fit(shortName(id), w - 24), x + 4, ry + 2, on ? g.accent() : g.text());
				if (on) g.text("+", x + w - 16, ry + 2, g.accent());
			}
			if (filtered.isEmpty()) g.text(box.text.isBlank() ? "Nothing to pick from." : "No match.", x + 4, listY + 2, g.dim());
			if (filtered.size() > VISIBLE) {
				int thumb = Math.max(8, listH * VISIBLE / filtered.size());
				int track = listH - thumb;
				int ty = listY + (filtered.size() - VISIBLE == 0 ? 0 : track * scrollRow / (filtered.size() - VISIBLE));
				g.rect(x + w - 4, listY, 3, listH, 0x44FFFFFF);
				g.rect(x + w - 4, ty, 3, thumb, g.accent());
			}
			g.text(filtered.size() + (filtered.size() == 1 ? " entry" : " entries") + "  scroll, click to add/remove", x + 2, listY + listH + 2, g.dim());
		}

		@Override
		public boolean click(int mx, int my, int button) {
			if (adding && box.click(mx, my, button)) return true;
			if (!hit(mx, my)) return false;
			for (int i = 0; i < chipRects.size() && i < s.get().size(); i++) {
				int[] r = chipRects.get(i);
				int cx = x + r[0], cy = y + r[1] * (ROW_H + 1);
				if (mx >= cx && mx < cx + r[2] && my >= cy && my < cy + ROW_H) {
					if (mx >= cx + r[2] - 12) s.remove(s.get().get(i));
					return true;
				}
			}
			if (mx >= x && mx < x + 44 && my >= addY && my < addY + ROW_H) {
				adding = !adding;
				if (adding) {
					dirty = true;
					scrollRow = 0;
					revealFrames = 4;
					Widget.setFocus(box);
				}
				return true;
			}
			if (adding) {
				int rowY = addY + ROW_H + 2;
				String[] cs = cats();
				if (cs.length > 1 && mx >= x && mx < x + catW && my >= rowY && my < rowY + ROW_H) {
					category = (category + 1) % cs.length;
					scrollRow = 0;
					dirty = true;
					return true;
				}
				if (pickerAvailable() && my >= listY && my < listY + VISIBLE * ROW_H && mx < x + w - 5) {
					int idx = scrollRow + (my - listY) / ROW_H;
					if (idx >= 0 && idx < filtered.size()) toggle(filtered.get(idx));
				}
			}
			return true;
		}

		@Override
		public boolean scroll(int mx, int my, double amount) {
			if (!adding || !pickerAvailable() || filtered.size() <= VISIBLE) return false;
			if (mx < x || mx >= x + w || my < listY || my >= listY + VISIBLE * ROW_H) return false;
			int step = amount > 0 ? -3 : 3;
			scrollRow = Math.max(0, Math.min(scrollRow + step, filtered.size() - VISIBLE));
			return true;
		}

		@Override
		public boolean key(int key, int mods) { return adding && box.key(key, mods); }

		@Override
		public boolean typed(int cp) { return adding && box.typed(cp); }
	}

	// String setting text field ----------------------------------------------------------------------------------------------------------------

	public static class StringField extends TextBox {
		private final StringSetting s;

		public StringField(StringSetting s) {
			super(s.get());
			this.s = s;
			this.onChange = t -> s.set(t);
			this.onCommit = t -> s.set(t);
		}

		@Override
		public void render(Gfx g, int mx, int my) {
			if (!isFocused() && !text.equals(s.get())) setText(s.get());
			super.render(g, mx, my);
		}
	}
}

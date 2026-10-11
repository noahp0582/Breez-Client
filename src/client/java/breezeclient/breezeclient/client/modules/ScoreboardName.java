package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.session.Guard;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.EnumSetting;
import breezeclient.breezeclient.core.settings.StringSetting;
import breezeclient.breezeclient.core.text.LegacyFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.scores.PlayerScoreEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Shows something else (or nothing) where your name appears: on the sidebar scoreboard (line text, score text and title,
 * which is where servers with custom sidebars such as DonutSMP-style stat boards put it), in the tab list, and in chat and
 * action bar messages. Only occurrences of your name are replaced: scores, numbers and everything other players see stay
 * exactly as the server sends them, and it is purely what is drawn on your own screen, so it is for clips and screenshots.
 */
public class ScoreboardName extends Module {
	public enum Mode { REPLACE, HIDE_LINE }

	private static ScoreboardName instance;
	/** True only while the sidebar is being built, so the name hook does not touch chat, nametags or the tab list. */
	private static boolean inSidebar;

	private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.REPLACE), "General",
		"REPLACE shows the text below instead of your name. HIDE_LINE removes your line from the sidebar completely.");
	private final StringSetting text = add(new StringSetting("Text", "&7Hidden"), "General",
		"What to show instead of your name. Supports color codes like &a (green), &c (red), &l (bold), &r (reset).",
		() -> mode.get() == Mode.REPLACE);
	private final BoolSetting sidebar = add(new BoolSetting("Sidebar scoreboard", true), "Where", "Apply to the scoreboard on the side of the screen.");
	private final BoolSetting scoreText = add(new BoolSetting("Score text and title", true), "Where",
		"Also replace your name when it appears inside the score text or the title of the sidebar (custom stat boards do this). Numbers are never touched.",
		() -> mode.get() == Mode.REPLACE);
	private final BoolSetting tabList = add(new BoolSetting("Tab list", true), "Where", "Also replace your name in the player list (Tab).",
		() -> mode.get() == Mode.REPLACE);
	private final BoolSetting chat = add(new BoolSetting("Chat and action bar", true), "Where",
		"Also replace your name in chat lines and action bar messages the server sends (for example /pay and join messages).",
		() -> mode.get() == Mode.REPLACE);

	public ScoreboardName() {
		super("ScoreboardName", Category.MISC);
		instance = this;
	}

	private static ScoreboardName on() { return instance != null && instance.enabled() ? instance : null; }

	private static String ownName() {
		var p = Minecraft.getInstance().player;
		return p == null ? "" : p.getName().getString();
	}

	public static void sidebarBegin() { inSidebar = on() != null && on().sidebar.get(); }

	public static void sidebarEnd() { inSidebar = false; }

	/** A styled component for the configured text. */
	private MutableComponent custom(Style base) {
		MutableComponent out = Component.empty();
		for (LegacyFormat.Segment s : LegacyFormat.parse(text.get())) {
			Style st = Style.EMPTY;
			if (s.color() >= 0) st = st.withColor(s.color());
			if (s.bold()) st = st.withBold(true);
			if (s.italic()) st = st.withItalic(true);
			if (s.underlined()) st = st.withUnderlined(true);
			if (s.strikethrough()) st = st.withStrikethrough(true);
			if (s.obfuscated()) st = st.withObfuscated(true);
			out.append(Component.literal(s.text()).withStyle(st.applyTo(base)));
		}
		return out;
	}

	/** Rebuilds a (team formatted) name, replacing your name by the custom text and keeping every other style. */
	private Component rewrite(Component original) {
		return NameReplace.replace(original, ownName(), this::custom);
	}

	// ---- hooks used by the mixins ----

	/** For PlayerTeam.formatNameForTeam while the sidebar is built. Returns the input when nothing changes. */
	public static MutableComponent sidebarName(MutableComponent formatted) {
		ScoreboardName m = on();
		if (m == null || !inSidebar || m.mode.get() != Mode.REPLACE) return formatted;
		Component r = m.rewrite(formatted);
		return r == formatted ? formatted : r.copy();
	}

	/** For Scoreboard.listPlayerScores while the sidebar is built: drops your own line in HIDE_LINE mode. */
	public static Collection<PlayerScoreEntry> sidebarEntries(Collection<PlayerScoreEntry> entries) {
		ScoreboardName m = on();
		if (m == null || !inSidebar || m.mode.get() != Mode.HIDE_LINE) return entries;
		String me = ownName();
		if (me.isEmpty()) return entries;
		List<PlayerScoreEntry> kept = new ArrayList<>(entries.size());
		for (PlayerScoreEntry e : entries) {
			// custom sidebars keep an internal holder id as owner and show a display name, so check both
			Component shown = e.ownerName();
			boolean mine = e.owner().equalsIgnoreCase(me)
				|| (shown != null && LegacyFormat.indexOfName(shown.getString(), me, 0) >= 0);
			if (!mine) kept.add(e);
		}
		return kept.size() == entries.size() ? entries : kept;
	}

	/** For the tab list. */
	public static Component tabName(Component original) {
		ScoreboardName m = on();
		if (m == null || !m.tabList.get() || m.mode.get() != Mode.REPLACE) return original;
		return m.rewrite(original);
	}

	/** For the score text and the title of the sidebar (flag-gated like the line names). */
	public static MutableComponent sidebarScoreText(MutableComponent formatted) {
		ScoreboardName m = on();
		if (m == null || !m.scoreText.get()) return formatted;
		String me = ownName();
		if (!me.isEmpty() && me.chars().allMatch(Character::isDigit)) return formatted; // a numeric name must never touch the numbers
		return sidebarName(formatted);
	}

	public static Component sidebarTitle(Component title) {
		ScoreboardName m = on();
		if (m == null || !inSidebar || !m.scoreText.get() || m.mode.get() != Mode.REPLACE) return title;
		return m.rewrite(title);
	}

	public static void registerEvents() {
		net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> Guard.call("ScoreboardName chat", message, () -> {
			ScoreboardName m = on();
			if (m == null || !m.chat.get() || m.mode.get() != Mode.REPLACE) return message;
			return m.rewrite(message);
		}));
	}

	/** Ticks never run inside a sidebar draw, so a flag left behind by an interrupted draw is cleared here. */
	@Override
	protected void onTick() { inSidebar = false; }

	@Override
	protected void onDisable() { inSidebar = false; }
}

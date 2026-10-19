package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.mixin.DeathScreenAccessor;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Guard;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import breezeclient.breezeclient.core.settings.StringSetting;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

/**
 * Types a message in chat after you kill someone or die. A kill is someone you hit in the last few seconds dying, or a
 * death message in chat that names both of you. Messages can contain {player} (who you killed) and {killer} (who killed
 * you); several messages separated by | are picked at random. A message starting with / is sent as a command.
 */
public class AutoMessage extends Module {
	private static final long KILL_WINDOW_MS = 6000, SAME_VICTIM_MS = 5000;

	private final BoolSetting onKill = add(new BoolSetting("On kill", true), "Kill", "Send a message when you kill someone.");
	private final StringSetting killMessage = add(new StringSetting("Kill message", "gg {player}"), "Kill",
		"What to type. {player} is who you killed; separate several with | to pick one at random.", onKill::get);
	private final BoolSetting playersOnly = add(new BoolSetting("Players only", true), "Kill", "Only count kills of players, not mobs.", onKill::get);
	private final BoolSetting onDeath = add(new BoolSetting("On death", false), "Death", "Send a message when you die.");
	private final StringSetting deathMessage = add(new StringSetting("Death message", "gg"), "Death",
		"What to type. {killer} is who killed you (empty if it was not a player); separate several with | to pick one at random.", onDeath::get);
	private final NumberSetting delay = add(new NumberSetting("Delay (s)", 1.0, 0, 30), "Timing", "How long to wait before typing.");
	private final NumberSetting randomDelay = add(new NumberSetting("Random extra delay (s)", 0.5, 0, 10), "Timing",
		"Adds up to this many seconds on top, so it does not look scripted.");
	private final NumberSetting cooldown = add(new NumberSetting("Cooldown (s)", 3, 0, 60), "Timing",
		"At most one message this often; extra kills in that time are skipped so you do not spam chat.");

	private static AutoMessage instance;
	private final Random random = new Random();
	/** Living entities you hit, by entity id, with when. */
	private final Map<Integer, Hit> hits = new HashMap<>();
	/** Victim name to when their kill was counted, so the health check and the chat message do not both fire. */
	private final Map<String, Long> counted = new HashMap<>();
	private final ArrayDeque<Pending> queue = new ArrayDeque<>();
	private boolean wasDead;
	/** Who killed you, read off the death screen once it shows; null until then. */
	private String killer;
	private long lastSent;
	private int kills, deaths;

	private record Hit(LivingEntity entity, String name, long at) {}

	private record Pending(String text, long due) {}

	public AutoMessage() {
		super("AutoMessage", Category.MISC);
		instance = this;
	}

	@Override
	public String info() { return kills + "/" + deaths; }

	public static void registerEvents() {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			AutoMessage a = instance;
			if (a != null && a.enabled() && level.isClientSide() && player == Minecraft.getInstance().player && entity instanceof LivingEntity le)
				a.hits.put(le.getId(), new Hit(le, le.getName().getString(), System.currentTimeMillis()));
			return InteractionResult.PASS;
		});
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> Guard.run("AutoMessage chat", () -> {
			AutoMessage a = instance;
			if (a != null && a.enabled() && !overlay) a.onGameMessage(message.getString());
		}));
	}

	@Override
	protected void onEnable() {
		hits.clear();
		counted.clear();
		queue.clear();
		var p = Minecraft.getInstance().player;
		wasDead = p != null && p.isDeadOrDying();
	}

	/** A death message naming you and someone you just hit counts as your kill, for servers that hide other players' health. */
	private void onGameMessage(String text) {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null || !onKill.get()) return;
		if (!text.contains(p.getName().getString())) return;
		long now = System.currentTimeMillis();
		for (Hit h : hits.values()) {
			if (now - h.at() > KILL_WINDOW_MS || !text.startsWith(h.name())) continue; // death messages start with the victim
			if (playersOnly.get() && !(h.entity() instanceof Player)) continue;
			kill(h.name(), now);
			return;
		}
	}

	private void kill(String victim, long now) {
		Long before = counted.get(victim);
		if (before != null && now - before < SAME_VICTIM_MS) return;
		counted.put(victim, now);
		kills++;
		schedule(killMessage.get().replace("{player}", victim), now);
	}

	private void schedule(String template, long now) {
		String[] options = template.split("\\|");
		String text = options[random.nextInt(options.length)].trim();
		if (text.isEmpty()) return;
		long wait = Math.round((delay.get() + random.nextDouble() * randomDelay.get()) * 1000);
		queue.add(new Pending(text, now + wait));
	}

	@Override
	protected void onTick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) {
			hits.clear();
			queue.clear();
			return;
		}
		long now = System.currentTimeMillis();

		// kills: someone you hit lately is now dead
		for (Iterator<Hit> it = hits.values().iterator(); it.hasNext(); ) {
			Hit h = it.next();
			if (now - h.at() > KILL_WINDOW_MS) {
				it.remove();
			} else if (h.entity().isDeadOrDying()) {
				it.remove();
				if (onKill.get() && (!playersOnly.get() || h.entity() instanceof Player)) kill(h.name(), now);
			}
		}
		counted.values().removeIf(t -> now - t > SAME_VICTIM_MS);

		// your own death
		boolean dead = p.isDeadOrDying();
		if (dead && !wasDead) {
			deaths++;
			hits.clear();
			killer = null;
			if (onDeath.get()) schedule(deathMessage.get(), now); // {killer} is filled in when it is sent: the death screen may not be up yet
		}
		wasDead = dead;
		if (killer == null && mc.screen instanceof DeathScreen) killer = killer(mc);

		Pending next = queue.peek();
		if (next == null || now < next.due()) return;
		queue.poll();
		if (now - lastSent < cooldown.get() * 1000) return;
		lastSent = now;
		send(p, next.text().replace("{killer}", killer == null ? killer(mc) : killer));
	}

	/** The online player named in the death screen's message, or "" when no player killed you. */
	private static String killer(Minecraft mc) {
		if (!(mc.screen instanceof DeathScreen ds) || mc.getConnection() == null) return "";
		var cause = ((DeathScreenAccessor) ds).breez$causeOfDeath();
		if (cause == null) return "";
		String text = cause.getString(), self = mc.player.getName().getString(), best = "";
		for (var info : mc.getConnection().getOnlinePlayers()) {
			String name = info.getProfile().name();
			if (!name.equals(self) && name.length() > best.length() && text.contains(name)) best = name;
		}
		return best;
	}

	private static void send(LocalPlayer p, String text) {
		if (text.startsWith("/")) p.connection.sendCommand(text.substring(1));
		else p.connection.sendChat(text);
	}
}

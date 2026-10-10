package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;

/** Disconnects when your health is low, after totem pops, or when you are in lava, before the next hit lands. */
public class AutoLog extends Module {
	private static AutoLog instance;

	private final NumberSetting health = add(new NumberSetting("Health at or below", 6, 0, 20), "Triggers",
		"Disconnect when your health (two per heart) falls to this. 0 turns it off.");
	private final NumberSetting pops = add(new NumberSetting("Totem pops", 0, 0, 6), "Triggers",
		"Disconnect after this many totem pops within 60 seconds. 0 turns it off.");
	private final BoolSetting lava = add(new BoolSetting("In lava", true), "Triggers", "Disconnect when you are standing in lava.");
	private final BoolSetting reconnect = add(new BoolSetting("Allow auto-reconnect", false), "Behavior",
		"Let AutoReconnect rejoin afterwards. Off by default so you are not dropped back into the fight.");

	private final ArrayDeque<Long> popTimes = new ArrayDeque<>();

	public AutoLog() {
		super("AutoLog", Category.SAFETY);
		instance = this;
	}

	/** Called when the game shows the totem animation for you. */
	public static void onTotemPop() {
		AutoLog m = instance;
		if (m != null && m.enabled()) m.popTimes.addLast(System.currentTimeMillis());
	}

	@Override
	protected void onEnable() { popTimes.clear(); }

	@Override
	protected void onTick() {
		var p = Minecraft.getInstance().player;
		if (p == null || p.isDeadOrDying()) return;
		if (health.asInt() > 0 && p.getHealth() <= health.asInt()) {
			trigger("health " + String.format("%.1f", p.getHealth()));
			return;
		}
		long now = System.currentTimeMillis();
		while (!popTimes.isEmpty() && now - popTimes.peekFirst() > 60_000) popTimes.removeFirst();
		if (pops.asInt() > 0 && popTimes.size() >= pops.asInt()) {
			trigger(popTimes.size() + " totem pops");
			return;
		}
		if (lava.get() && p.isInLava()) trigger("in lava");
	}

	private void trigger(String why) {
		popTimes.clear();
		AlertWebhook.safety("AutoLog: " + why);
		KillSwitch.disconnect("AutoLog: " + why, reconnect.get());
	}
}

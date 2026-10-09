package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.humanize.RotationController;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.humanize.ActionLimiter;
import breezeclient.breezeclient.core.humanize.Rotation;
import breezeclient.breezeclient.core.macro.MacroParser;
import breezeclient.breezeclient.core.macro.MacroStep;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.List;
import java.util.Random;

/** A user macro. Enabling it runs the script; it switches itself off when finished (or loops until toggled off). */
public class MacroModule extends Module {
	private final String script;
	private final ActionLimiter limiter = new ActionLimiter(new Random());

	private List<MacroStep> steps = List.of();
	private boolean loop;
	private int idx;
	private int wait;
	private int hold;
	private KeyMapping held;
	private boolean waitingLook;
	private boolean jumpPending;
	private boolean sneaking;

	public MacroModule(String name, String script) {
		super(name, Category.MISC);
		this.script = script;
		setDescription("Macro: " + script);
	}

	public String script() { return script; }

	@Override
	protected boolean persistsEnabled() { return false; }

	@Override
	protected void onEnable() {
		MacroParser.Result r = MacroParser.parse(script);
		if (!r.errors().isEmpty()) {
			EspModule.say("Macro " + name() + ": " + String.join("; ", r.errors()));
			setEnabled(false);
			return;
		}
		steps = r.steps();
		loop = r.loop();
		idx = 0;
		wait = 0;
		hold = 0;
		waitingLook = false;
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		if (held != null) held.setDown(false);
		held = null;
		mc.options.keyJump.setDown(false);
		if (sneaking) mc.options.keyShift.setDown(false);
		sneaking = false;
		if (waitingLook) RotationController.clear();
		waitingLook = false;
	}

	@Override
	protected void onTick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null) return;
		if (jumpPending) {
			mc.options.keyJump.setDown(false);
			jumpPending = false;
		}
		if (hold > 0 && --hold == 0 && held != null) {
			held.setDown(false);
			held = null;
		}
		if (hold > 0) return;
		if (waitingLook) {
			if (RotationController.busy()) return;
			waitingLook = false;
		}
		if (wait > 0) {
			wait--;
			return;
		}
		while (true) {
			if (idx >= steps.size()) {
				if (loop && !steps.isEmpty()) {
					idx = 0;
					wait = Math.max(wait, 1); // never spin without yielding a tick
					return;
				}
				setEnabled(false);
				return;
			}
			MacroStep s = steps.get(idx++);
			if (run(mc, p, s)) return; // step consumed time
		}
	}

	/** @return true when the step needs the runner to yield for at least a tick. */
	private boolean run(Minecraft mc, LocalPlayer p, MacroStep s) {
		switch (s.op()) {
			case "say" -> {
				String t = s.args().get(0);
				if (t.startsWith("/")) p.connection.sendCommand(t.substring(1));
				else p.connection.sendChat(t);
				return false;
			}
			case "wait" -> {
				int n = s.intArg(0);
				wait = n == 0 ? 0 : limiter.nextDelayTicks(n, n * 0.1, 1);
				return true;
			}
			case "slot" -> {
				p.getInventory().setSelectedSlot(s.intArg(0) - 1);
				return false;
			}
			case "use", "attack" -> {
				held = s.op().equals("use") ? mc.options.keyUse : mc.options.keyAttack;
				held.setDown(true);
				hold = s.intArg(0);
				return true;
			}
			case "look" -> {
				RotationController.aimAt(new Rotation(s.floatArg(0), s.floatArg(1)), 18f);
				waitingLook = true;
				return true;
			}
			case "turn" -> {
				Rotation cur = RotationController.current();
				RotationController.aimAt(new Rotation(cur.yaw() + s.floatArg(0), Math.max(-90f, Math.min(90f, cur.pitch() + s.floatArg(1)))), 18f);
				waitingLook = true;
				return true;
			}
			case "sneak" -> {
				sneaking = s.args().get(0).equals("on");
				mc.options.keyShift.setDown(sneaking);
				return false;
			}
			case "jump" -> {
				mc.options.keyJump.setDown(true);
				jumpPending = true;
				return true;
			}
			default -> {
				return false;
			}
		}
	}
}

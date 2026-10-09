package breezeclient.breezeclient.core.macro;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Macro script language. Steps are separated by ';' or newlines.
 * <pre>
 *   say &lt;text&gt;          chat message, or a command when it starts with '/'
 *   wait &lt;ticks&gt;        pause
 *   slot &lt;1-9&gt;          select hotbar slot
 *   use [ticks]         hold use (right click), default 1 tick
 *   attack [ticks]      hold attack (left click), default 1 tick
 *   look &lt;yaw&gt; &lt;pitch&gt;  turn the camera to an absolute angle
 *   turn &lt;dyaw&gt; &lt;dpitch&gt; turn the camera relative to now
 *   sneak on|off
 *   jump
 *   loop                restart from the first step (must be last)
 * </pre>
 */
public final class MacroParser {
	private MacroParser() {}

	public record Result(List<MacroStep> steps, List<String> errors, boolean loop) {}

	public static Result parse(String script) {
		List<MacroStep> steps = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		boolean loop = false;
		boolean sawLoop = false;
		for (String raw : script.split("[;\\n]")) {
			String line = raw.trim();
			if (line.isEmpty()) continue;
			if (sawLoop) {
				errors.add("'loop' must be the last step");
				break;
			}
			int sp = line.indexOf(' ');
			String op = (sp < 0 ? line : line.substring(0, sp)).toLowerCase(Locale.ROOT);
			String rest = sp < 0 ? "" : line.substring(sp + 1).trim();
			String[] words = rest.isEmpty() ? new String[0] : rest.split("\\s+");
			switch (op) {
				case "say" -> {
					if (rest.isEmpty()) errors.add("say needs text");
					else steps.add(new MacroStep(op, List.of(rest)));
				}
				case "wait" -> {
					if (words.length != 1 || !isInt(words[0], 0, 1_000_000)) errors.add("wait needs a tick count >= 0: " + line);
					else steps.add(new MacroStep(op, List.of(words[0])));
				}
				case "slot" -> {
					if (words.length != 1 || !isInt(words[0], 1, 9)) errors.add("slot needs 1-9: " + line);
					else steps.add(new MacroStep(op, List.of(words[0])));
				}
				case "use", "attack" -> {
					if (words.length == 0) steps.add(new MacroStep(op, List.of("1")));
					else if (words.length == 1 && isInt(words[0], 1, 1_000_000)) steps.add(new MacroStep(op, List.of(words[0])));
					else errors.add(op + " takes an optional tick count >= 1: " + line);
				}
				case "look", "turn" -> {
					if (words.length != 2 || !isFloat(words[0]) || !isFloat(words[1])) errors.add(op + " needs two numbers: " + line);
					else steps.add(new MacroStep(op, List.of(words[0], words[1])));
				}
				case "sneak" -> {
					if (words.length != 1 || !(words[0].equalsIgnoreCase("on") || words[0].equalsIgnoreCase("off"))) errors.add("sneak needs on|off: " + line);
					else steps.add(new MacroStep(op, List.of(words[0].toLowerCase(Locale.ROOT))));
				}
				case "jump" -> steps.add(new MacroStep(op, List.of()));
				case "loop" -> {
					loop = true;
					sawLoop = true;
				}
				default -> errors.add("unknown step '" + op + "'");
			}
		}
		if (steps.isEmpty() && errors.isEmpty()) errors.add("macro is empty");
		return new Result(steps, errors, loop);
	}

	private static boolean isInt(String s, int lo, int hi) {
		try {
			int v = Integer.parseInt(s);
			return v >= lo && v <= hi;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	private static boolean isFloat(String s) {
		try {
			float f = Float.parseFloat(s);
			return !Float.isNaN(f) && !Float.isInfinite(f);
		} catch (NumberFormatException e) {
			return false;
		}
	}
}

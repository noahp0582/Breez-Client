package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.core.text.LegacyFormat;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Optional;
import java.util.function.Function;

/** The one place that replaces a player's name inside a styled chat component. Whole names only; every other style is kept. */
final class NameReplace {
	/** Shown instead of a message the name hooks could not process, so a failure never lets your real name through. */
	static final Component FAILED = Component.literal("[message hidden: name protection failed]");

	private NameReplace() {}

	/** @param replacement builds the text to put in, given the style of the text it replaces */
	static Component replace(Component original, String name, Function<Style, Component> replacement) {
		if (name == null || name.isEmpty() || LegacyFormat.indexOfName(original.getString(), name, 0) < 0) return original;
		MutableComponent out = Component.empty();
		original.visit((style, str) -> {
			int from = 0, idx = LegacyFormat.indexOfName(str, name, 0);
			while (idx >= 0) {
				if (idx > from) out.append(Component.literal(str.substring(from, idx)).withStyle(style));
				out.append(replacement.apply(style));
				from = idx + name.length();
				idx = LegacyFormat.indexOfName(str, name, from);
			}
			if (from < str.length()) out.append(Component.literal(str.substring(from)).withStyle(style));
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}
}

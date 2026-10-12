package breezeclient.breezeclient.core.text;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads money amounts the way server shops write them: "$1.5K", "1,250", "2.3m", "Price: $500". */
public final class PriceParser {
	private static final Pattern AMOUNT = Pattern.compile("\\$?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([kmbt])?(?![a-z])", Pattern.CASE_INSENSITIVE);

	private PriceParser() {}

	/** The first amount in the text, or NaN when there is none. */
	public static double parse(String text) {
		if (text == null) return Double.NaN;
		return first(text, 0);
	}

	private static double first(String text, int from) {
		Matcher m = AMOUNT.matcher(text);
		if (!m.find(from)) return Double.NaN;
		double v;
		try {
			v = Double.parseDouble(m.group(1).replace(",", ""));
		} catch (NumberFormatException e) {
			return Double.NaN;
		}
		String suffix = m.group(2) == null ? "" : m.group(2).toLowerCase(Locale.ROOT);
		return v * switch (suffix) {
			case "k" -> 1e3;
			case "m" -> 1e6;
			case "b" -> 1e9;
			case "t" -> 1e12;
			default -> 1;
		};
	}

	/**
	 * The price of a listing from its tooltip lines: the amount after the word price, cost or buy; failing that, the first
	 * amount written with a dollar sign. NaN when no line has one, so a listing is never bought on a guess.
	 */
	public static double fromLore(List<String> lines) {
		for (String raw : lines) {
			String l = raw.toLowerCase(Locale.ROOT);
			for (String word : new String[] {"price", "cost", "buy"}) {
				int at = l.indexOf(word);
				if (at < 0) continue;
				double v = first(raw, at + word.length());
				if (!Double.isNaN(v)) return v;
			}
		}
		for (String raw : lines) {
			int at = raw.indexOf('$');
			if (at < 0) continue;
			double v = first(raw, at);
			if (!Double.isNaN(v)) return v;
		}
		return Double.NaN;
	}

	/** 1500 -> "$1.5K". */
	public static String format(double v) {
		if (Double.isNaN(v)) return "?";
		String[] suffix = {"", "K", "M", "B", "T"};
		int i = 0;
		double a = Math.abs(v);
		while (a >= 1000 && i < suffix.length - 1) {
			a /= 1000;
			i++;
		}
		String n = a == Math.floor(a) ? String.valueOf((long) a) : String.format(Locale.ROOT, "%.2f", a).replaceAll("0+$", "").replaceAll("\\.$", "");
		return (v < 0 ? "-$" : "$") + n + suffix[i];
	}
}

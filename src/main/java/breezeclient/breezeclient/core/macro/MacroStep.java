package breezeclient.breezeclient.core.macro;

import java.util.List;

public record MacroStep(String op, List<String> args) {
	public int intArg(int i) { return Integer.parseInt(args.get(i)); }

	public float floatArg(int i) { return Float.parseFloat(args.get(i)); }
}

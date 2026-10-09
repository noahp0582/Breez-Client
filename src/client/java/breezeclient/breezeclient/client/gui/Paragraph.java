package breezeclient.breezeclient.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Word-wrapped text block. */
public class Paragraph extends Widget {
	private final Supplier<String> text;
	private final boolean dim;
	private List<String> lines = List.of();

	public Paragraph(String text, boolean dim) { this(() -> text, dim); }

	public Paragraph(Supplier<String> text, boolean dim) {
		this.text = text;
		this.dim = dim;
	}

	public static List<String> wrap(Gfx g, String text, int maxW) {
		List<String> out = new ArrayList<>();
		for (String para : text.split("\n")) {
			StringBuilder line = new StringBuilder();
			for (String word : para.split(" ")) {
				String test = line.length() == 0 ? word : line + " " + word;
				if (line.length() > 0 && g.width(test) > maxW) {
					out.add(line.toString());
					line = new StringBuilder(word);
				} else {
					line = new StringBuilder(test);
				}
			}
			out.add(line.toString());
		}
		return out;
	}

	@Override
	protected int height(Gfx g, int w) {
		lines = wrap(g, text.get(), w);
		return lines.size() * 10;
	}

	@Override
	public void render(Gfx g, int mx, int my) {
		int yy = y;
		for (String l : lines) {
			g.text(l, x, yy, dim ? g.dim() : g.text());
			yy += 10;
		}
	}
}

package breezeclient.breezeclient.core;

public enum Category {
	COMBAT("Combat"), RENDER("Render"), WORLD("World"), SAFETY("Safety"), MOVEMENT("Movement"), MISC("Misc");

	private final String label;

	Category(String label) { this.label = label; }

	public String label() { return label; }
}

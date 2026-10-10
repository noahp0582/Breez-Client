package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.gui.GuiState;

/** Friends are kept in the Config tab's list; this is the one place the rest of the client asks about them. */
public final class Friends {
	public static final int COLOR = 0xFF55FF55;

	private Friends() {}

	public static boolean is(String name) { return name != null && GuiState.CLIENT.friends.contains(name); }
}

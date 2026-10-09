package breezeclient.breezeclient.client.gui;

import breezeclient.breezeclient.core.settings.Setting;

import java.util.List;

/** A tab that is just one window of grouped settings (Config, GUI). */
public class SettingsScreen extends BreezScreen {
	private final String windowId;
	private final String windowTitle;
	private final List<Setting<?>> settings;
	private final String blurb;

	protected SettingsScreen(String tab, String windowId, String windowTitle, String blurb, List<Setting<?>> settings) {
		super(tab);
		this.windowId = windowId;
		this.windowTitle = windowTitle;
		this.blurb = blurb;
		this.settings = settings;
	}

	@Override
	protected void build() {
		int w = 300;
		Window win = new Window(windowId, windowTitle, w, 14, TAB_H + 10);
		win.maxH = vh - TAB_H - 20;
		if (!blurb.isBlank()) win.content.add(new Paragraph(blurb, true));
		ModuleScreen.addSettings(win.content, windowId, settings);
		windows.add(win);
	}
}

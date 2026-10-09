package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;

/** Shows default skins instead of real ones, so recordings and screenshots do not reveal who is who. Local only. */
public class SkinProtect extends Module {
	private static SkinProtect instance;

	public SkinProtect() {
		super("SkinProtect", Category.MISC);
		instance = this;
	}

	public static boolean active() { return instance != null && instance.enabled(); }
}

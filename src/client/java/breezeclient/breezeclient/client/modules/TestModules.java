package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.humanize.RotationController;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.ModuleManager;
import breezeclient.breezeclient.core.RenderFrame;
import breezeclient.breezeclient.core.humanize.Rotation;
import net.minecraft.client.Minecraft;

/** Temporary glue-proof modules; removed once real modules exist. */
public final class TestModules {
	private TestModules() {}

	public static void register(ModuleManager m) {
		m.register(new BoxTest());
		m.register(new AimTest());
	}

	static class BoxTest extends Module {
		BoxTest() { super("BoxTest", Category.RENDER); }

		@Override
		protected void onRender(RenderFrame f) {
			var p = Minecraft.getInstance().player;
			if (p == null) return;
			f.drawBox(0, p.getY(), 0, 1, p.getY() + 1, 1, 0xFFFF3030);
			f.drawText(0.5, p.getY() + 1.5, 0.5, "box", 0xFFFFFFFF);
		}
	}

	static class AimTest extends Module {
		AimTest() { super("AimTest", Category.MISC); }

		@Override
		protected void onEnable() { RotationController.aimAt(new Rotation(0f, 0f), 20f); }

		@Override
		protected void onTick() {
			if (!RotationController.busy()) setEnabled(false);
		}
	}
}

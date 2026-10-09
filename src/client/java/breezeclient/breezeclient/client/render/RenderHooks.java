package breezeclient.breezeclient.client.render;

import breezeclient.breezeclient.core.ModuleManager;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;

public final class RenderHooks {
	private RenderHooks() {}

	/** Latest frame helper, for modules that want filled boxes (cast RenderFrame to RenderUtil). */
	public static void register(ModuleManager modules) {
		WorldRenderEvents.END_MAIN.register(context -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.level == null || mc.player == null) return;
			Camera camera = mc.gameRenderer.getMainCamera();
			RenderUtil util = new RenderUtil(context.matrices(), context.consumers(), camera.position(), camera.rotation(), 2.0f);
			modules.render(util);
			if (context.consumers() instanceof MultiBufferSource.BufferSource bs) bs.endBatch();
		});
	}
}

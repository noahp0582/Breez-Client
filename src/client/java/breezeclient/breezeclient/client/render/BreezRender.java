package breezeclient.breezeclient.client.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

/** Custom through-walls pipelines and render types. */
public final class BreezRender {
	private BreezRender() {}

	public static final RenderPipeline LINES_PIPELINE = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
			.withLocation("pipeline/breez_lines_no_depth")
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.withDepthWrite(false)
			.build()
	);

	public static final RenderPipeline QUADS_PIPELINE = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation("pipeline/breez_quads_no_depth")
			.withCull(false)
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.withDepthWrite(false)
			.build()
	);

	public static final RenderType LINES = RenderType.create("breez_lines", RenderSetup.builder(LINES_PIPELINE).createRenderSetup());

	public static final RenderType QUADS = RenderType.create("breez_quads", RenderSetup.builder(QUADS_PIPELINE).sortOnUpload().createRenderSetup());
}

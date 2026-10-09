package breezeclient.breezeclient.client.render;

import breezeclient.breezeclient.core.RenderFrame;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Draws through-walls boxes, lines and text in world coordinates. One instance per frame. */
public final class RenderUtil implements RenderFrame {
	private final PoseStack.Pose pose;
	private final MultiBufferSource consumers;
	private final Vec3 cam;
	private final org.joml.Quaternionf camRotation;
	private final float lineWidth;

	public RenderUtil(PoseStack stack, MultiBufferSource consumers, Vec3 cam, org.joml.Quaternionf camRotation, float lineWidth) {
		this.pose = stack.last();
		this.consumers = consumers;
		this.cam = cam;
		this.camRotation = camRotation;
		this.lineWidth = lineWidth;
	}

	@Override
	public void drawBox(double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
		float ax = (float) (Math.min(x1, x2) - cam.x), bx = (float) (Math.max(x1, x2) - cam.x);
		float ay = (float) (Math.min(y1, y2) - cam.y), by = (float) (Math.max(y1, y2) - cam.y);
		float az = (float) (Math.min(z1, z2) - cam.z), bz = (float) (Math.max(z1, z2) - cam.z);
		VertexConsumer vc = consumers.getBuffer(BreezRender.LINES);
		edge(vc, ax, ay, az, bx, ay, az, argb);
		edge(vc, ax, by, az, bx, by, az, argb);
		edge(vc, ax, ay, bz, bx, ay, bz, argb);
		edge(vc, ax, by, bz, bx, by, bz, argb);
		edge(vc, ax, ay, az, ax, by, az, argb);
		edge(vc, bx, ay, az, bx, by, az, argb);
		edge(vc, ax, ay, bz, ax, by, bz, argb);
		edge(vc, bx, ay, bz, bx, by, bz, argb);
		edge(vc, ax, ay, az, ax, ay, bz, argb);
		edge(vc, bx, ay, az, bx, ay, bz, argb);
		edge(vc, ax, by, az, ax, by, bz, argb);
		edge(vc, bx, by, az, bx, by, bz, argb);
	}

	/** Translucent filled box; alpha comes from the argb value. */
	public void drawFilledBox(double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
		float ax = (float) (Math.min(x1, x2) - cam.x), bx = (float) (Math.max(x1, x2) - cam.x);
		float ay = (float) (Math.min(y1, y2) - cam.y), by = (float) (Math.max(y1, y2) - cam.y);
		float az = (float) (Math.min(z1, z2) - cam.z), bz = (float) (Math.max(z1, z2) - cam.z);
		VertexConsumer vc = consumers.getBuffer(BreezRender.QUADS);
		quad(vc, ax, ay, az, bx, ay, az, bx, ay, bz, ax, ay, bz, argb);
		quad(vc, ax, by, az, ax, by, bz, bx, by, bz, bx, by, az, argb);
		quad(vc, ax, ay, az, ax, by, az, bx, by, az, bx, ay, az, argb);
		quad(vc, ax, ay, bz, bx, ay, bz, bx, by, bz, ax, by, bz, argb);
		quad(vc, ax, ay, az, ax, ay, bz, ax, by, bz, ax, by, az, argb);
		quad(vc, bx, ay, az, bx, by, az, bx, by, bz, bx, ay, bz, argb);
	}

	@Override
	public void drawLine(double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
		edge(consumers.getBuffer(BreezRender.LINES),
			(float) (x1 - cam.x), (float) (y1 - cam.y), (float) (z1 - cam.z),
			(float) (x2 - cam.x), (float) (y2 - cam.y), (float) (z2 - cam.z), argb);
	}

	@Override
	public void drawText(double x, double y, double z, String text, int argb) {
		Font font = Minecraft.getInstance().font;
		Matrix4f m = new Matrix4f()
			.translation((float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z))
			.rotate(camRotation)
			.scale(0.025f, -0.025f, 0.025f);
		float w = -font.width(text) / 2f;
		font.drawInBatch(text, w, 0f, argb | 0xFF000000, false, m, consumers, Font.DisplayMode.SEE_THROUGH, 0x66000000, 0xF000F0);
	}

	private void edge(VertexConsumer vc, float x1, float y1, float z1, float x2, float y2, float z2, int argb) {
		float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
		float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len == 0f) return;
		dx /= len;
		dy /= len;
		dz /= len;
		vc.addVertex(pose, x1, y1, z1).setColor(argb).setNormal(pose, dx, dy, dz).setLineWidth(lineWidth);
		vc.addVertex(pose, x2, y2, z2).setColor(argb).setNormal(pose, dx, dy, dz).setLineWidth(lineWidth);
	}

	private void quad(VertexConsumer vc, float x1, float y1, float z1, float x2, float y2, float z2,
					  float x3, float y3, float z3, float x4, float y4, float z4, int argb) {
		vc.addVertex(pose, x1, y1, z1).setColor(argb);
		vc.addVertex(pose, x2, y2, z2).setColor(argb);
		vc.addVertex(pose, x3, y3, z3).setColor(argb);
		vc.addVertex(pose, x4, y4, z4).setColor(argb);
	}
}

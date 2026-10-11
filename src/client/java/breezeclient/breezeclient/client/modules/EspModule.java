package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.chat.ChatCommands;
import breezeclient.breezeclient.client.render.RenderUtil;
import breezeclient.breezeclient.client.world.ChunkScanner;
import breezeclient.breezeclient.client.world.ScanTarget;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.RenderFrame;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.HashSet;
import java.util.Set;

/** Base for modules that highlight blocks found by a ChunkScanner target. */
public abstract class EspModule extends Module {
	protected final NumberSetting range = add(new NumberSetting("Range", 128, 16, 512));
	protected final BoolSetting tracers = add(new BoolSetting("Tracers", false));
	protected final BoolSetting fill = add(new BoolSetting("Fill", true));

	protected ScanTarget target;
	private final Set<Long> seen = new HashSet<>();
	private int ticks;

	protected EspModule(String name, Category category) { super(name, category); }

	protected abstract ScanTarget createTarget();

	/** ARGB color for a hit. */
	protected abstract int colorOf(BlockState state);

	/** Called once per newly found position (rate limited). Default: nothing. */
	protected void onFound(BlockPos pos, BlockState state) {}

	/** Whether a found block is shown at all (for modules with a rule the block state alone cannot express). */
	protected boolean accept(BlockPos pos, BlockState state) { return true; }

	@Override
	protected void onEnable() {
		target = createTarget();
		seen.clear();
		ChunkScanner.add(target);
	}

	@Override
	protected void onDisable() {
		if (target != null) ChunkScanner.remove(target);
		target = null;
	}

	@Override
	public String info() { return target == null ? "" : String.valueOf(target.count()); }

	@Override
	protected void onTick() {
		if (target == null || ++ticks % 20 != 0) return;
		var level = Minecraft.getInstance().level;
		if (level == null) return;
		int[] budget = {5};
		for (var list : target.chunks()) {
			for (BlockPos p : list) {
				if (seen.add(p.asLong()) && budget[0] > 0) {
					budget[0]--;
					onFound(p, level.getBlockState(p));
				}
			}
		}
	}

	/** Finds and alerts: a merged pop-up by default, chat only when "Alerts in chat" is on in the Config tab. */
	protected static void say(String s) {
		if (breezeclient.breezeclient.client.gui.GuiState.CLIENT.alertsInChat.get()) ChatCommands.print(s);
		else breezeclient.breezeclient.client.gui.Toasts.show("Breez", s);
	}

	@Override
	protected void onRender(RenderFrame f) {
		Minecraft mc = Minecraft.getInstance();
		if (target == null || mc.level == null) return;
		Camera cam = mc.gameRenderer.getMainCamera();
		Vec3 pos = cam.position();
		Vector3fc fwd = cam.forwardVector();
		Vec3 start = new Vec3(pos.x + fwd.x() * 0.3, pos.y + fwd.y() * 0.3, pos.z + fwd.z() * 0.3);
		boolean doFill = fill.get() && f instanceof RenderUtil;
		target.forEachWithin(pos, range.get(), p -> {
			BlockState s = mc.level.getBlockState(p);
			if (!accept(p, s)) return;
			int color = colorOf(s);
			double e = 0.002;
			f.drawBox(p.getX() - e, p.getY() - e, p.getZ() - e, p.getX() + 1 + e, p.getY() + 1 + e, p.getZ() + 1 + e, color | 0xFF000000);
			if (doFill) {
				((RenderUtil) f).drawFilledBox(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1, (color & 0xFFFFFF) | 0x40000000);
			}
			if (tracers.get()) f.drawLine(start.x, start.y, start.z, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, color | 0xFF000000);
		});
	}
}

package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.gui.Toasts;
import breezeclient.breezeclient.client.render.RenderUtil;
import breezeclient.breezeclient.client.world.ChunkScanner;
import breezeclient.breezeclient.client.world.ScanTarget;
import breezeclient.breezeclient.client.world.Waypoints;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.RenderFrame;
import breezeclient.breezeclient.core.geode.GeodeAnalyzer;
import breezeclient.breezeclient.core.geode.GeodeAnalyzer.Geode;
import breezeclient.breezeclient.core.geode.GeodeAnalyzer.Stage;
import breezeclient.breezeclient.core.gui.ColorMath;
import breezeclient.breezeclient.core.nav.Pos;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.ColorSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds amethyst geodes and judges their age from the buds growing on budding amethyst. When every crystal (or at least
 * the configured percentage) has grown into a full cluster, it notifies you.
 */
public class AmethystDetector extends Module {
	private final NumberSetting threshold = add(new NumberSetting("Fully grown at (%)", 90, 50, 100), "Geodes",
		"A geode counts as fully grown when at least this share of its growing crystals are full clusters.");
	private final NumberSetting minBuds = add(new NumberSetting("Min growing crystals", 4, 1, 60), "Geodes",
		"Ignore geodes with fewer crystals than this (tiny or half-loaded geodes).");
	private final NumberSetting linkDist = add(new NumberSetting("Geode link distance", 4, 2, 8), "Geodes",
		"Budding blocks closer than this belong to the same geode.");

	private final BoolSetting toast = add(new BoolSetting("Toast when fully grown", true), "Alerts", "Pop-up notification the first time a geode is fully grown.");
	private final BoolSetting chat = add(new BoolSetting("Also chat message", false), "Alerts", "Also print the notification in chat.");
	private final BoolSetting waypoint = add(new BoolSetting("Waypoint when fully grown", true), "Alerts", "Save a waypoint for fully grown geodes.");
	private final BoolSetting sighting = add(new BoolSetting("Notify on first sighting", false), "Alerts", "Also notify when any geode is first seen, with its growth.");

	private final NumberSetting range = add(new NumberSetting("Range", 128, 16, 512), "Render", "How far away geodes are drawn.");
	private final BoolSetting labels = add(new BoolSetting("Geode labels", true), "Render", "Show growth percent, crystal counts and estimated age above each geode.");
	private final BoolSetting boxClusters = add(new BoolSetting("Box full clusters", true), "Render", "Outline fully grown crystals.");
	private final BoolSetting boxBuds = add(new BoolSetting("Box growing buds", false), "Render", "Outline small, medium and large buds.");
	private final BoolSetting boxBudding = add(new BoolSetting("Box budding blocks", false), "Render", "Outline the budding amethyst blocks.");
	private final BoolSetting fill = add(new BoolSetting("Fill", true), "Render", "Translucent fill inside the boxes.");
	private final BoolSetting tracers = add(new BoolSetting("Tracers", false), "Render", "Lines from the crosshair to each geode.");
	private final ColorSetting clusterColor = add(new ColorSetting("Cluster color", 0xFFC77DFF), "Colors", "Color of full clusters.");
	private final ColorSetting budColor = add(new ColorSetting("Bud color", 0xFF8E5DD6), "Colors", "Color of growing buds.");
	private final ColorSetting buddingColor = add(new ColorSetting("Budding color", 0xFFE0AAFF), "Colors", "Color of budding blocks.");

	private static AmethystDetector instance;

	private ScanTarget target;
	private int ticks;
	private List<Geode> geodes = List.of();
	private final List<BlockPos> clusterPos = new ArrayList<>(), budPos = new ArrayList<>(), buddingPos = new ArrayList<>();
	private final Set<Pos> notified = new HashSet<>();

	public AmethystDetector() {
		super("AmethystDetector", Category.WORLD);
		instance = this;
	}

	/** Geodes from the latest scan, best-grown first. */
	public static List<Geode> geodes() {
		if (instance == null) return List.of();
		List<Geode> copy = new ArrayList<>(instance.geodes);
		copy.sort((a, b) -> Double.compare(b.grownPercent(), a.grownPercent()));
		return copy;
	}

	public static void resetNotified() {
		if (instance != null) instance.notified.clear();
	}

	@Override
	protected void onEnable() {
		target = new ScanTarget(AmethystDetector::isAmethystCrystal);
		ChunkScanner.add(target);
		notified.clear();
		geodes = List.of();
	}

	@Override
	protected void onDisable() {
		if (target != null) ChunkScanner.remove(target);
		target = null;
		geodes = List.of();
	}

	private static boolean isAmethystCrystal(BlockState s) {
		return s.is(Blocks.BUDDING_AMETHYST) || s.is(Blocks.AMETHYST_CLUSTER) || s.is(Blocks.LARGE_AMETHYST_BUD)
			|| s.is(Blocks.MEDIUM_AMETHYST_BUD) || s.is(Blocks.SMALL_AMETHYST_BUD);
	}

	@Override
	public String info() { return geodes.isEmpty() ? "" : String.valueOf(geodes.size()); }

	@Override
	protected void onTick() {
		if (target == null || ++ticks % 40 != 0) return;
		var level = Minecraft.getInstance().level;
		if (level == null) return;
		List<Pos> budding = new ArrayList<>();
		List<GeodeAnalyzer.Bud> buds = new ArrayList<>();
		clusterPos.clear();
		budPos.clear();
		buddingPos.clear();
		for (var list : target.chunks()) {
			for (BlockPos p : list) {
				BlockState s = level.getBlockState(p);
				Pos q = new Pos(p.getX(), p.getY(), p.getZ());
				if (s.is(Blocks.BUDDING_AMETHYST)) {
					budding.add(q);
					buddingPos.add(p);
				} else if (s.is(Blocks.AMETHYST_CLUSTER)) {
					buds.add(new GeodeAnalyzer.Bud(q, Stage.CLUSTER));
					clusterPos.add(p);
				} else if (s.is(Blocks.LARGE_AMETHYST_BUD)) {
					buds.add(new GeodeAnalyzer.Bud(q, Stage.LARGE));
					budPos.add(p);
				} else if (s.is(Blocks.MEDIUM_AMETHYST_BUD)) {
					buds.add(new GeodeAnalyzer.Bud(q, Stage.MEDIUM));
					budPos.add(p);
				} else if (s.is(Blocks.SMALL_AMETHYST_BUD)) {
					buds.add(new GeodeAnalyzer.Bud(q, Stage.SMALL));
					budPos.add(p);
				}
			}
		}
		var mut = new BlockPos.MutableBlockPos();
		geodes = GeodeAnalyzer.analyze(budding, buds, pos -> {
			BlockState st = level.getBlockState(mut.set(pos.x(), pos.y(), pos.z()));
			return st.isAir() || (st.is(Blocks.WATER) && st.getFluidState().isSource());
		}, linkDist.asInt());

		for (Geode g : geodes) {
			boolean ready = g.fullyGrown(threshold.get(), minBuds.asInt());
			boolean first = notified.add(g.anchor());
			if (ready && !readySeen.contains(g.anchor())) {
				readySeen.add(g.anchor());
				announce("Amethyst geode fully grown", String.format("%.0f%% grown (%d clusters) at %d %d %d", g.grownPercent(), g.cluster(), g.center().x(), g.center().y(), g.center().z()), g, true);
			} else if (first && sighting.get() && g.buds() >= minBuds.asInt()) {
				announce("Amethyst geode found", String.format("%.0f%% grown at %d %d %d", g.grownPercent(), g.center().x(), g.center().y(), g.center().z()), g, false);
			}
		}
	}

	private final Set<Pos> readySeen = new HashSet<>();

	private void announce(String title, String body, Geode g, boolean ready) {
		if (toast.get()) Toasts.show(title, body, ready ? 0xFFC77DFF : 0);
		if (chat.get()) EspModule.say(title + ": " + body);
		if (ready && waypoint.get()) Waypoints.add("Geode " + Math.round(g.grownPercent()) + "%", new BlockPos(g.center().x(), g.center().y(), g.center().z()));
	}

	@Override
	protected void onRender(RenderFrame f) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;
		var cam = mc.gameRenderer.getMainCamera();
		Vec3 cp = cam.position();
		var fwd = cam.forwardVector();
		double r2 = range.get() * range.get();
		boolean doFill = fill.get() && f instanceof RenderUtil;
		if (boxClusters.get()) drawBoxes(f, clusterPos, clusterColor.get(), cp, r2, doFill);
		if (boxBuds.get()) drawBoxes(f, budPos, budColor.get(), cp, r2, doFill);
		if (boxBudding.get()) drawBoxes(f, buddingPos, buddingColor.get(), cp, r2, false);
		for (Geode g : geodes) {
			double dx = g.center().x() + 0.5 - cp.x, dy = g.center().y() + 0.5 - cp.y, dz = g.center().z() + 0.5 - cp.z;
			if (dx * dx + dy * dy + dz * dz > r2) continue;
			int color = ColorMath.mix(0xFFFF5555, 0xFF55FF77, g.grownPercent() / 100.0);
			if (labels.get()) {
				String label = String.format("Geode %.0f%% grown | %dC %dL %dM %dS | %d open | ~%.1fh", g.grownPercent(), g.cluster(), g.large(), g.medium(), g.small(), g.openSlots(), g.estHours());
				f.drawText(g.center().x() + 0.5, g.center().y() + 3.0, g.center().z() + 0.5, label, color);
			}
			if (tracers.get()) f.drawLine(cp.x + fwd.x() * 0.3, cp.y + fwd.y() * 0.3, cp.z + fwd.z() * 0.3, g.center().x() + 0.5, g.center().y() + 0.5, g.center().z() + 0.5, color);
		}
	}

	private static void drawBoxes(RenderFrame f, List<BlockPos> list, int color, Vec3 cam, double r2, boolean doFill) {
		for (BlockPos p : list) {
			double dx = p.getX() + 0.5 - cam.x, dy = p.getY() + 0.5 - cam.y, dz = p.getZ() + 0.5 - cam.z;
			if (dx * dx + dy * dy + dz * dz > r2) continue;
			f.drawBox(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1, color | 0xFF000000);
			if (doFill) ((RenderUtil) f).drawFilledBox(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1, (color & 0xFFFFFF) | 0x40000000);
		}
	}
}

package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.gui.Gfx;
import breezeclient.breezeclient.client.hud.HudElements;
import breezeclient.breezeclient.client.world.BlockIds;
import breezeclient.breezeclient.client.world.ChunkScanner;
import breezeclient.breezeclient.client.world.ScanTarget;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.RenderFrame;
import breezeclient.breezeclient.core.physics.Trajectory;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.ColorSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import breezeclient.breezeclient.core.settings.StringListSetting;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Render and HUD modules. They share this file. */
public final class RenderModules {
	private RenderModules() {}

	// ---------------------------------------------------------------------------------------------------------------

	/** Tells you when one of the listed blocks shows up within range: a pop-up with the position, and a sound if you like. */
	public static class BlockNotifier extends Module {
		private final StringListSetting blocks = add(new StringListSetting("Blocks", List.of("minecraft:spawner", "minecraft:ancient_debris", "minecraft:diamond_ore",
			"minecraft:deepslate_diamond_ore")).kind(StringListSetting.Kind.BLOCK), "Blocks", "Blocks to tell you about.");
		private final NumberSetting range = add(new NumberSetting("Range", 64, 8, 256), "Detection", "Only blocks this close to you count.");
		private final BoolSetting sound = add(new BoolSetting("Sound", true), "Alerts", "Play a short chime.");
		private final BoolSetting waypoint = add(new BoolSetting("Waypoint", false), "Alerts", "Save a waypoint for each find.");

		private ScanTarget target;
		private List<String> applied = List.of();
		private final Set<Long> seen = new HashSet<>();
		private Object seenLevel;
		private int ticks;

		public BlockNotifier() { super("BlockNotifier", Category.RENDER); }

		@Override
		public String info() { return target == null ? "" : String.valueOf(target.count()); }

		@Override
		protected void onEnable() {
			applied = List.copyOf(blocks.get());
			var set = BlockIds.resolve(applied);
			target = ScanTarget.ofBlocks(set);
			ChunkScanner.add(target);
			seen.clear();
		}

		@Override
		protected void onDisable() {
			if (target != null) ChunkScanner.remove(target);
			target = null;
		}

		@Override
		protected void onTick() {
			Minecraft mc = Minecraft.getInstance();
			LocalPlayer p = mc.player;
			if (target == null || p == null || mc.level == null) return;
			if (mc.level != seenLevel) { // a new world or dimension: positions announced before mean nothing now
				seenLevel = mc.level;
				seen.clear();
			}
			if (!applied.equals(blocks.get())) {
				applied = List.copyOf(blocks.get());
				var set = BlockIds.resolve(applied);
				target.setPredicate(s -> set.contains(s.getBlock()));
				seen.clear();
			}
			if (++ticks % 10 != 0) return;
			int[] budget = {3};
			double r = range.get();
			Vec3 pos = p.position();
			target.forEachWithin(pos, r, b -> {
				if (budget[0] <= 0 || !seen.add(b.asLong())) return;
				budget[0]--;
				String id = BlockIds.idOf(mc.level.getBlockState(b).getBlock()).replace("minecraft:", "").replace('_', ' ');
				EspModule.say(id + " at " + b.getX() + " " + b.getY() + " " + b.getZ() + " (" + (int) Math.sqrt(p.distanceToSqr(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5)) + " blocks)");
				if (sound.get()) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 1.2f));
				if (waypoint.get()) breezeclient.breezeclient.client.world.Waypoints.add(id, b);
			});
		}
	}

	// ---------------------------------------------------------------------------------------------------------------

	/** Draws where an ender pearl will land: for the one in your hand, and for pearls already in the air. */
	public static class PearlTrajectory extends Module {
		/** Same physics as the builder's pearl aim: vanilla applies gravity and drag before each move. */
		private static final double POWER = breezeclient.breezeclient.core.physics.PearlAim.POWER;

		private final BoolSetting held = add(new BoolSetting("Held pearl", true), "Show", "Preview the throw while you hold an ender pearl.");
		private final BoolSetting flying = add(new BoolSetting("Pearls in flight", true), "Show", "Predict the landing of pearls that are already thrown (yours and other players').");
		private final NumberSetting steps = add(new NumberSetting("Max steps", 120, 20, 300), "Detail", "How many ticks of flight to follow.");
		private final ColorSetting color = add(new ColorSetting("Color", 0xFF55FFFF), "Colors", "Path of your own throw.");
		private final ColorSetting otherColor = add(new ColorSetting("In-flight color", 0xFFFFAA33), "Colors", "Path of pearls already in the air.");

		public PearlTrajectory() { super("PearlTrajectory", Category.RENDER); }

		private Trajectory.Collider collider(Entity owner) {
			var level = Minecraft.getInstance().level;
			return (a, b) -> {
				var hit = level.clip(new ClipContext(new Vec3(a.x(), a.y(), a.z()), new Vec3(b.x(), b.y(), b.z()), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
				if (hit.getType() == HitResult.Type.MISS) return null;
				Vec3 l = hit.getLocation();
				return new Trajectory.Point(l.x, l.y, l.z);
			};
		}

		private void draw(RenderFrame f, Trajectory.Result r, Vec3 from, int argb) {
			Vec3 prev = from;
			for (Trajectory.Point p : r.points()) {
				f.drawLine(prev.x, prev.y, prev.z, p.x(), p.y(), p.z(), argb);
				prev = new Vec3(p.x(), p.y(), p.z());
			}
			if (r.landing() != null) {
				var l = r.landing();
				f.drawBox(l.x() - 0.25, l.y() - 0.25, l.z() - 0.25, l.x() + 0.25, l.y() + 0.25, l.z() + 0.25, argb);
			}
		}

		@Override
		protected void onRender(RenderFrame f) {
			Minecraft mc = Minecraft.getInstance();
			LocalPlayer p = mc.player;
			if (p == null || mc.level == null) return;
			if (held.get() && (p.getMainHandItem().is(Items.ENDER_PEARL) || p.getOffhandItem().is(Items.ENDER_PEARL))) {
				float yaw = p.getYRot() * (float) (Math.PI / 180.0), pitch = p.getXRot() * (float) (Math.PI / 180.0);
				double dx = -Math.sin(yaw) * Math.cos(pitch), dy = -Math.sin(pitch), dz = Math.cos(yaw) * Math.cos(pitch);
				Vec3 move = p.getKnownMovement();
				var vel = new Trajectory.Point(dx * POWER + move.x, dy * POWER + (p.onGround() ? 0 : move.y), dz * POWER + move.z);
				Vec3 start = new Vec3(p.getX(), p.getEyeY() - 0.1f, p.getZ());
				var r = breezeclient.breezeclient.core.physics.PearlAim.fly(new Trajectory.Point(start.x, start.y, start.z), vel, steps.asInt(), collider(p));
				draw(f, r, start, color.get() | 0xFF000000);
			}
			if (flying.get()) {
				int shown = 0;
				for (Entity e : mc.level.entitiesForRendering()) {
					if (!(e instanceof ThrownEnderpearl) || e.isRemoved() || shown >= 16) continue;
					shown++;
					Vec3 v = e.getDeltaMovement();
					Vec3 start = e.position();
					var r = breezeclient.breezeclient.core.physics.PearlAim.fly(new Trajectory.Point(start.x, start.y, start.z), new Trajectory.Point(v.x, v.y, v.z), steps.asInt(), collider(e));
					draw(f, r, start, otherColor.get() | 0xFF000000);
				}
			}
		}
	}

	// ---------------------------------------------------------------------------------------------------------------

	/** Draws where an arrow (or trident) will hit: for the bow, crossbow or trident in your hand, and for arrows already flying. */
	public static class BowTrajectory extends Module {
		private static final double GRAVITY = 0.05, DRAG = 0.99;

		private final BoolSetting held = add(new BoolSetting("Held weapon", true), "Show", "Preview the shot while you hold a bow, loaded crossbow or trident.");
		private final BoolSetting fullPower = add(new BoolSetting("Before drawing", true), "Show", "Show the full-power path while holding a bow you have not started drawing yet.", held::get);
		private final BoolSetting flying = add(new BoolSetting("Arrows in flight", false), "Show", "Predict where arrows and tridents already in the air will hit (yours and others').");
		private final BoolSetting entities = add(new BoolSetting("Hit entities", true), "Detail", "Stop the path at players and mobs in the way, and outline the one it would hit.");
		private final NumberSetting steps = add(new NumberSetting("Max steps", 150, 20, 400), "Detail", "How many ticks of flight to follow.");
		private final ColorSetting color = add(new ColorSetting("Color", 0xFFFFFF55), "Colors", "Path of your own shot.");
		private final ColorSetting hitColor = add(new ColorSetting("Hit color", 0xFFFF5555), "Colors", "Path and outline when the shot would hit an entity.", entities::get);
		private final ColorSetting otherColor = add(new ColorSetting("In-flight color", 0xFFFFAA33), "Colors", "Path of arrows already in the air.", flying::get);

		private Entity hitEntity;
		/** What the last drawn path of the held weapon ended on; read by the self-test. */
		private volatile Trajectory.Point heldLanding;
		private volatile Entity heldHit;

		public BowTrajectory() { super("BowTrajectory", Category.RENDER); }

		public Trajectory.Point heldLanding() { return heldLanding; }

		public Entity heldHit() { return heldHit; }

		/** Launch speed of the held weapon in blocks per tick, or 0 when it would not shoot an arrow right now. */
		private double power(LocalPlayer p, ItemStack stack) {
			if (stack.is(Items.BOW)) {
				if (p.isUsingItem() && p.getUseItem() == stack) return Math.max(0.1f, BowItem.getPowerForTime(p.getTicksUsingItem())) * 3.0;
				return fullPower.get() ? 3.0 : 0;
			}
			if (stack.is(Items.CROSSBOW)) {
				var charged = stack.get(DataComponents.CHARGED_PROJECTILES);
				return charged == null || charged.isEmpty() || charged.contains(Items.FIREWORK_ROCKET) ? 0 : 3.15; // rockets fly straight under their own power
			}
			if (stack.is(Items.TRIDENT)) return p.isUsingItem() && p.getUseItem() == stack && p.getTicksUsingItem() >= TridentItem.THROW_THRESHOLD_TIME ? 2.5 : 0;
			return 0;
		}

		private Trajectory.Collider collider(Entity owner) {
			var level = Minecraft.getInstance().level;
			hitEntity = null;
			Entity shooter = owner instanceof Projectile pr ? pr.getOwner() : owner;
			return (a, b) -> {
				Vec3 from = new Vec3(a.x(), a.y(), a.z()), to = new Vec3(b.x(), b.y(), b.z());
				var block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
				if (block.getType() != HitResult.Type.MISS) to = block.getLocation();
				if (entities.get()) {
					EntityHitResult e = ProjectileUtil.getEntityHitResult(level, owner, from, to, new AABB(from, to).inflate(1.0),
						t -> t != owner && t != shooter && t.canBeHitByProjectile() && !t.isSpectator(), 0.0f);
					if (e != null) {
						hitEntity = e.getEntity();
						Vec3 l = e.getLocation();
						return new Trajectory.Point(l.x, l.y, l.z);
					}
				}
				return block.getType() == HitResult.Type.MISS ? null : new Trajectory.Point(to.x, to.y, to.z);
			};
		}

		private void draw(RenderFrame f, Trajectory.Result r, Vec3 from, int argb) {
			if (hitEntity != null) {
				argb = hitColor.get() | 0xFF000000;
				AABB b = hitEntity.getBoundingBox();
				f.drawBox(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, argb);
			}
			Vec3 prev = from;
			for (Trajectory.Point p : r.points()) {
				f.drawLine(prev.x, prev.y, prev.z, p.x(), p.y(), p.z(), argb);
				prev = new Vec3(p.x(), p.y(), p.z());
			}
			if (r.landing() != null) {
				var l = r.landing();
				f.drawBox(l.x() - 0.2, l.y() - 0.2, l.z() - 0.2, l.x() + 0.2, l.y() + 0.2, l.z() + 0.2, argb);
			}
		}

		@Override
		protected void onRender(RenderFrame f) {
			Minecraft mc = Minecraft.getInstance();
			LocalPlayer p = mc.player;
			if (p == null || mc.level == null) return;
			if (held.get()) {
				double power = power(p, p.getMainHandItem());
				if (power == 0) power = power(p, p.getOffhandItem());
				if (power > 0) { // same aim and inherited motion as Projectile.shootFromRotation
					float yaw = p.getYRot() * (float) (Math.PI / 180.0), pitch = p.getXRot() * (float) (Math.PI / 180.0);
					double dx = -Math.sin(yaw) * Math.cos(pitch), dy = -Math.sin(pitch), dz = Math.cos(yaw) * Math.cos(pitch);
					Vec3 move = p.getKnownMovement();
					var vel = new Trajectory.Point(dx * power + move.x, dy * power + (p.onGround() ? 0 : move.y), dz * power + move.z);
					Vec3 start = new Vec3(p.getX(), p.getEyeY() - 0.1f, p.getZ());
					var r = Trajectory.simulate(new Trajectory.Point(start.x, start.y, start.z), vel, GRAVITY, DRAG, steps.asInt(), collider(p));
					heldLanding = r.landing();
					heldHit = hitEntity;
					draw(f, r, start, color.get() | 0xFF000000);
				} else {
					heldLanding = null;
					heldHit = null;
				}
			}
			if (flying.get()) {
				int shown = 0;
				for (Entity e : mc.level.entitiesForRendering()) {
					if (!(e instanceof AbstractArrow) || e.isRemoved() || shown >= 16) continue;
					Vec3 v = e.getDeltaMovement();
					if (v.lengthSqr() < 1.0e-4) continue; // stuck in a block
					shown++;
					Vec3 start = e.position();
					var r = Trajectory.simulate(new Trajectory.Point(start.x, start.y, start.z), new Trajectory.Point(v.x, v.y, v.z), GRAVITY, DRAG, steps.asInt(), collider(e));
					draw(f, r, start, otherColor.get() | 0xFF000000);
				}
			}
		}
	}

	// ---------------------------------------------------------------------------------------------------------------

	/** Draws the true hitbox of other entities (what the server uses), so you can compare it with what HitBox makes you aim at. */
	public static class RealHitBox extends Module {
		private final BoolSetting players = add(new BoolSetting("Players", true), "Targets", "Draw player hitboxes.");
		private final BoolSetting mobs = add(new BoolSetting("Mobs", false), "Targets", "Draw other living entities too.");
		private final BoolSetting eye = add(new BoolSetting("Eye line", true), "Detail", "Mark the eye height with a line across the box.");
		private final NumberSetting range = add(new NumberSetting("Range", 24, 4, 128), "Detail", "Only entities this close are drawn.");
		private final ColorSetting color = add(new ColorSetting("Color", 0xFF55FF55), "Colors", "Box color.");
		private final ColorSetting eyeColor = add(new ColorSetting("Eye color", 0xFFFF5555), "Colors", "Eye line color.", eye::get);

		public RealHitBox() { super("RealHitBox", Category.RENDER); }

		@Override
		protected void onRender(RenderFrame f) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.level == null || mc.player == null) return;
			double r2 = range.get() * range.get();
			for (Entity e : mc.level.entitiesForRendering()) {
				if (e == mc.player || e == mc.getCameraEntity() || !(e instanceof LivingEntity) || e.isSpectator() || e.isInvisible() && !(e instanceof Player)) continue;
				if (e instanceof Player ? !players.get() : !mobs.get()) continue;
				if (e.distanceToSqr(mc.player) > r2) continue;
				AABB b = e.getBoundingBox(); // the real box: no HitBox or StaticHitBoxes margin
				f.drawBox(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, color.get() | 0xFF000000);
				if (eye.get()) {
					double y = e.getEyeY();
					f.drawBox(b.minX, y, b.minZ, b.maxX, y, b.maxZ, eyeColor.get() | 0xFF000000);
				}
			}
		}
	}

	// ---------------------------------------------------------------------------------------------------------------

	/** A HUD panel for whoever you last hit or are aiming at: name, health bar, armor and distance. */
	public static class TargetHud extends Module {
		private static TargetHud instance;
		private static LivingEntity target;
		private static long targetTime;

		private final NumberSetting hold = add(new NumberSetting("Hold (s)", 4, 1, 30), "General", "How long the panel stays after you stop looking at or hitting the target.");
		private final BoolSetting playersOnly = add(new BoolSetting("Players only", false), "General", "Ignore mobs and animals.");

		public TargetHud() {
			super("TargetHUD", Category.RENDER);
			instance = this;
		}

		public static void registerEvents() {
			AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
				if (level.isClientSide() && entity instanceof LivingEntity le && player == Minecraft.getInstance().player) {
					target = le;
					targetTime = System.currentTimeMillis();
				}
				return InteractionResult.PASS;
			});
			HudElements.register(new HudElements.Element() {
				public String id() { return "targethud"; }
				public String label() { return "Target HUD"; }
				public int[] defaultPos() { return new int[] {160, 60}; }
				public boolean rightAnchored() { return false; }
				public int[] draw(Gfx g, int x, int y, boolean preview) { return instance == null ? new int[] {0, 0} : instance.drawPanel(g, x, y, preview); }
			});
		}

		@Override
		protected void onTick() {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null) return;
			if (mc.hitResult instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity le && le.isAlive() && le != mc.player) {
				if (!(playersOnly.get() && !(le instanceof Player))) {
					target = le;
					targetTime = System.currentTimeMillis();
				}
			}
		}

		private int[] drawPanel(Gfx g, int x, int y, boolean preview) {
			if (!enabled() && !preview) return new int[] {0, 0};
			LivingEntity t = target;
			boolean live = t != null && t.isAlive() && !t.isRemoved() && System.currentTimeMillis() - targetTime < hold.get() * 1000;
			String name = live ? t.getName().getString() : "Target";
			float hp = live ? t.getHealth() : 20f, max = live ? Math.max(1f, t.getMaxHealth()) : 20f;
			if (!live && !preview) return new int[] {0, 0};
			int armor = live ? t.getArmorValue() : 0;
			Minecraft mc = Minecraft.getInstance();
			double dist = live && mc.player != null ? Math.sqrt(t.distanceToSqr(mc.player)) : 0;
			int w = 124, h = 36;
			g.rrect(x, y, w, h, 0xA0101018);
			g.text(g.fit(name, w - 8), x + 4, y + 4, g.text());
			int barW = w - 8;
			float frac = Math.max(0f, Math.min(1f, hp / max));
			g.rect(x + 4, y + 16, barW, 5, 0xFF303030);
			int col = frac > 0.6f ? 0xFF55FF55 : frac > 0.3f ? 0xFFFFAA00 : 0xFFFF5555;
			g.rect(x + 4, y + 16, Math.max(1, (int) (barW * frac)), 5, col);
			g.text(String.format(Locale.ROOT, "%.1f / %.0f", hp, max), x + 4, y + 24, g.dim());
			String right = armor + " armor  " + (int) dist + "m";
			g.text(right, x + w - g.width(right) - 4, y + 24, g.dim());
			return new int[] {w, h};
		}
	}

	// ---------------------------------------------------------------------------------------------------------------

	/** A HUD line with the music or record that is playing. */
	public static class MusicHud extends Module {
		private static MusicHud instance;
		private static volatile String track = "";
		private static volatile SoundInstance current;
		private static boolean listening;

		private final BoolSetting records = add(new BoolSetting("Music discs", true), "General", "Also show jukebox records.");

		public MusicHud() {
			super("MusicHUD", Category.RENDER);
			instance = this;
		}

		public static void registerEvents() {
			HudElements.register(new HudElements.Element() {
				public String id() { return "musichud"; }
				public String label() { return "Music HUD"; }
				public int[] defaultPos() { return new int[] {4, 52}; }
				public boolean rightAnchored() { return false; }
				public int[] draw(Gfx g, int x, int y, boolean preview) {
					MusicHud m = instance;
					if (m == null) return new int[] {0, 0};
					String s = m.line();
					if (s.isEmpty()) {
						if (!preview) return new int[] {0, 0};
						s = "Now playing: music.game";
					}
					g.text(s, x, y, g.text());
					return new int[] {g.width(s), 9};
				}
			});
		}

		/** "music.game.creative" or "music_disc.cat" -> a short readable name. */
		static String pretty(String path) {
			String s = path.startsWith("music_disc.") ? "Disc: " + path.substring("music_disc.".length()) : path.startsWith("music.") ? path.substring("music.".length()) : path;
			return s.replace('.', ' ').replace('_', ' ');
		}

		@Override
		protected void onEnable() { ensureListener(); }

		@Override
		protected void onTick() { ensureListener(); } // retried here: at startup the sound manager is not built yet

		private void ensureListener() {
			if (listening) return;
			var sounds = Minecraft.getInstance().getSoundManager();
			if (sounds == null) return;
			sounds.addListener((sound, events, range) -> {
				MusicHud m = instance;
				if (m == null || !m.enabled()) return;
				SoundSource src = sound.getSource();
				if (src == SoundSource.MUSIC || (src == SoundSource.RECORDS && m.records.get())) {
					track = pretty(sound.getIdentifier().getPath());
					current = sound;
				}
			});
			listening = true;
		}

		private String line() {
			if (!enabled()) return "";
			SoundInstance s = current;
			if (s == null || track.isEmpty()) return "";
			if (!Minecraft.getInstance().getSoundManager().isActive(s)) return "";
			return "Now playing: " + track;
		}

		@Override
		public String info() { return line().isEmpty() ? "" : track; }
	}
}

package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.core.humanize.ActionLimiter;
import breezeclient.breezeclient.core.text.PriceParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Buys one kind of item from a chest-menu auction house (DonutSMP style): opens it with a command, searches through the
 * sign button, picks the cheapest listing that is within the price limit and presses confirm. It only ever clicks a
 * listing whose price it could read, and it counts money as spent the moment it presses confirm.
 */
final class AuctionBuyer {
	enum Result { WORKING, BOUGHT, NOTHING, FAILED }

	private enum State { OPEN, SEARCH, SIGN, RESULTS, CONFIRM, WAIT_ITEM, CLOSE }

	/** How the auction house is driven; read from the owning module's settings. */
	record Config(String command, boolean signSearch, String searchLabel, String confirmLabel, double clickDelay) {}

	private static final int MAX_PURCHASES = 8;

	private final ActionLimiter limiter = new ActionLimiter(new Random());
	private Config config;
	private Item item;
	private String query;
	private int wanted;
	private double maxEach;
	private double budget;
	private State state;
	private Result outcome;
	private int timer;
	private int wait;
	private boolean signSearch;
	private boolean typed;
	private boolean clickedConfirm;
	private int countBefore;
	private double pendingPrice;
	private int purchases;
	private double spent;
	private double cheapestSeen;
	private String note = "";

	/** Money committed since {@link #start}. */
	double spent() { return spent; }

	/** Why the last run ended the way it did, for the chat line. */
	String note() { return note; }

	/**
	 * @param wanted  stop once this many are in the inventory
	 * @param maxEach highest price for a single item (listing price divided by its stack size)
	 * @param budget  highest total this run may spend
	 */
	void start(Config config, Item item, int wanted, double maxEach, double budget) {
		this.config = config;
		this.item = item;
		this.query = new ItemStack(item).getHoverName().getString();
		this.wanted = wanted;
		this.maxEach = maxEach;
		this.budget = budget;
		this.signSearch = config.signSearch();
		this.spent = 0;
		this.purchases = 0;
		this.cheapestSeen = Double.NaN;
		this.note = "";
		this.outcome = null;
		go(State.OPEN, 0);
	}

	private void go(State s, int ticks) {
		state = s;
		timer = 0;
		wait = ticks;
		typed = false;
		clickedConfirm = false;
	}

	private int pause() { return limiter.nextDelayTicks(config.clickDelay() + 2, 1, 2); }

	private static AbstractContainerScreen<?> container(Minecraft mc) {
		if (mc.player != null && mc.screen instanceof AbstractContainerScreen<?> cs && cs.getMenu() != mc.player.inventoryMenu) return cs;
		return null;
	}

	private static int count(LocalPlayer p, Item item) {
		int n = 0;
		for (int i = 0; i < 36; i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (s.is(item)) n += s.getCount();
		}
		return n;
	}

	private static List<String> lore(ItemStack s) {
		List<String> out = new ArrayList<>();
		ItemLore l = s.get(DataComponents.LORE);
		if (l != null) for (Component c : l.lines()) out.add(c.getString());
		return out;
	}

	private static String name(ItemStack s) { return s.getHoverName().getString().toLowerCase(Locale.ROOT); }

	private Result end(Minecraft mc, Result r, String why) {
		if (mc.player != null && mc.screen != null) mc.player.closeContainer();
		note = why;
		outcome = r;
		return r;
	}

	Result tick(Minecraft mc, LocalPlayer p) {
		if (outcome != null) return outcome;
		if (mc.gameMode == null || mc.getConnection() == null) return end(mc, Result.FAILED, "not connected");
		if (wait > 0) {
			wait--;
			return Result.WORKING;
		}
		timer++;
		var cs = container(mc);
		switch (state) {
			case OPEN -> {
				if (timer == 1) {
					if (mc.screen != null) p.closeContainer();
					String c = config.command().trim();
					if (c.isEmpty()) return end(mc, Result.FAILED, "the auction command is blank");
					if (!signSearch) c = c + " " + query;
					if (c.startsWith("/")) mc.getConnection().sendCommand(c.substring(1));
					else mc.getConnection().sendChat(c);
				} else if (cs != null) {
					go(signSearch ? State.SEARCH : State.RESULTS, pause() + 6);
				} else if (timer > 100) {
					return end(mc, Result.FAILED, "'" + config.command() + "' did not open a window");
				}
			}
			case SEARCH -> {
				if (cs == null) return end(mc, Result.FAILED, "the auction window closed");
				String label = config.searchLabel().trim().toLowerCase(Locale.ROOT);
				Slot button = null;
				for (Slot s : cs.getMenu().slots) {
					if (s.container == p.getInventory() || s.getItem().isEmpty()) continue;
					boolean named = !label.isEmpty() && name(s.getItem()).contains(label);
					if (named || (button == null && (s.getItem().is(ItemTags.SIGNS) || s.getItem().is(ItemTags.HANGING_SIGNS)))) {
						button = s;
						if (named) break;
					}
				}
				if (button == null) {
					if (timer < 40) return Result.WORKING; // the window may still be filling
					signSearch = false; // no search button here: search with the command instead
					go(State.OPEN, pause());
					return Result.WORKING;
				}
				mc.gameMode.handleInventoryMouseClick(cs.getMenu().containerId, button.index, 0, ClickType.PICKUP, p);
				go(State.SIGN, 0);
			}
			case SIGN -> {
				if (mc.screen instanceof AbstractSignEditScreen sign) {
					if (!typed) {
						query.codePoints().forEach(cp -> sign.charTyped(new CharacterEvent(cp, 0)));
						typed = true;
						wait = pause() + 4;
					} else {
						sign.onClose(); // sends what was typed, like the Done button
						go(State.RESULTS, pause() + 6);
					}
				} else if (timer > 60) {
					signSearch = false; // the button did not open a sign (some servers ask in chat): use the command
					go(State.OPEN, pause());
				}
			}
			case RESULTS -> {
				if (cs == null) {
					if (timer > 100) return end(mc, Result.FAILED, "the search results did not open");
					return Result.WORKING;
				}
				double left = budget - spent;
				Slot best = null;
				double bestUnit = Double.MAX_VALUE, bestPrice = 0;
				for (Slot s : cs.getMenu().slots) {
					ItemStack st = s.getItem();
					if (s.container == p.getInventory() || !st.is(item)) continue;
					double price = PriceParser.fromLore(lore(st));
					if (Double.isNaN(price) || price <= 0) continue; // never buy on a guess
					double unit = price / Math.max(1, st.getCount());
					if (Double.isNaN(cheapestSeen) || unit < cheapestSeen) cheapestSeen = unit;
					if (unit > maxEach || price > left) continue;
					if (unit < bestUnit) {
						bestUnit = unit;
						bestPrice = price;
						best = s;
					}
				}
				if (best == null) {
					if (timer < 30) return Result.WORKING; // listings can arrive a moment after the window
					if (purchases > 0) return end(mc, Result.BOUGHT, "bought " + purchases + " listing(s) for " + PriceParser.format(spent));
					String seen = Double.isNaN(cheapestSeen) ? "no priced listing of " + query
						: "cheapest " + query + " is " + PriceParser.format(cheapestSeen) + " each, limit " + PriceParser.format(maxEach)
						+ (left < budget ? ", " + PriceParser.format(left) + " of the budget left" : "");
					return end(mc, Result.NOTHING, seen);
				}
				countBefore = count(p, item);
				pendingPrice = bestPrice;
				mc.gameMode.handleInventoryMouseClick(cs.getMenu().containerId, best.index, 0, ClickType.PICKUP, p);
				go(State.CONFIRM, pause() + 4);
			}
			case CONFIRM -> {
				if (count(p, item) > countBefore) { // no confirm step on this server: the click bought it
					spent += pendingPrice;
					purchases++;
					go(State.CLOSE, pause());
					return Result.WORKING;
				}
				if (cs == null) return timer > 60 ? end(mc, Result.FAILED, "the confirm window did not open") : Result.WORKING;
				String label = config.confirmLabel().trim().toLowerCase(Locale.ROOT);
				Slot button = null;
				for (Slot s : cs.getMenu().slots) {
					ItemStack st = s.getItem();
					if (s.container == p.getInventory() || st.isEmpty()) continue;
					if (st.is(item)) {
						double shown = PriceParser.fromLore(lore(st));
						// the confirm window shows a dearer listing than the one picked: walk away
						if (!Double.isNaN(shown) && shown > pendingPrice * 1.001 && shown / Math.max(1, st.getCount()) > maxEach)
							return end(mc, Result.FAILED, "the price changed to " + PriceParser.format(shown));
					}
					if (button == null && !label.isEmpty() && name(st).contains(label)) button = s;
				}
				if (button == null) return timer > 60 ? end(mc, Result.FAILED, "no '" + config.confirmLabel() + "' button in the confirm window") : Result.WORKING;
				mc.gameMode.handleInventoryMouseClick(cs.getMenu().containerId, button.index, 0, ClickType.PICKUP, p);
				spent += pendingPrice; // committed from here on, whatever the server answers
				purchases++;
				go(State.WAIT_ITEM, 0);
			}
			case WAIT_ITEM -> {
				if (count(p, item) > countBefore) go(State.CLOSE, pause());
				else if (timer > 80) return end(mc, Result.FAILED, "paid " + PriceParser.format(pendingPrice) + " but no " + query + " arrived (check /ah for a collection box)");
			}
			case CLOSE -> {
				if (mc.screen != null) p.closeContainer();
				if (count(p, item) >= wanted || purchases >= MAX_PURCHASES || budget - spent <= 0)
					return end(mc, Result.BOUGHT, "bought " + purchases + " listing(s) for " + PriceParser.format(spent));
				go(State.OPEN, pause() + 10);
			}
		}
		return Result.WORKING;
	}

	/** Closes whatever window the buyer has open; for when its owner is switched off mid-purchase. */
	void abort(Minecraft mc) {
		if (outcome == null && state != null) end(mc, Result.FAILED, "stopped");
	}
}

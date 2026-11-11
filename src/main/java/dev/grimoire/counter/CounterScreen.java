/*
 * Grimoire Counter - Copyright (c) 2026 Yggdrasjll. All rights reserved.
 * Not to be copied, modified, decompiled, reused or shared without Yggdrasjll's written permission. See LICENSE.txt.
 */
package dev.grimoire.counter;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The counter's page: its switches, every carry with − and + to count by hand, a row to add one, and your price list.
 * "Move the counter" lets you drag the counter where you want it on screen (the wheel sizes it).
 */
public final class CounterScreen extends Screen {
	static final int GILT = 0xFFFFD45C, MOSS = 0xFF7FCF6A, MINT = 0xFF8CF0C8, EDGE = 0xFF2A3A62, TEXT = 0xFFEFE8D4, MUTED = 0xFF93A3BD, DIM = 0xFF6F7F9C, RED = 0xFFFF7A7A, ORANGE = 0xFFFFA238;
	static int alpha(int c, int a) { return (Math.max(0, Math.min(255, a)) << 24) | (c & 0xFFFFFF); }
	static boolean in(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }
	static String clip(Font f, String s, int w) { if (s == null || w <= 0) return ""; if (f.width(s) <= w) return s; return f.plainSubstrByWidth(s, Math.max(0, w - f.width("…"))) + "…"; }

	/** what can be clicked this frame: {x, y, w, h} and what it does */
	final List<Object[]> hits = new ArrayList<>(); String tip = null;
	int scroll = 0, maxScroll = 0;
	/** the row for a new carry */
	static String addName = "", addAmount = "1"; static int addType = 0, addTier = 3;
	/** the text being typed in: "name", "amount" or a price row ("void|4"); null: none */
	String focus = null;
	/** the text bar being typed in: where its caret is and where the selection began (the same place: nothing selected), the first
	 *  character shown when the text is wider than the bar, the bar's place on screen, and the click that is still being dragged */
	int caret = 0, anchor = 0, fStart = 0; int[] fBox = null; boolean selecting = false, clickShift = false, clickDbl = false; double clickX = 0; String prevFocus = null;
	/** dragging the counter on screen */
	boolean moving = false, dragging = false; double grabX, grabY; int[] hudBox = {0, 0, 0, 0};

	static final String[][] TYPES = {{"void", "Voidgloom"}, {"blaze", "Inferno"}, {"rev", "Revenant"}, {"tara", "Tarantula"}, {"sven", "Sven"}, {"vamp", "Riftstalker"}, {"kuudra", "Kuudra"}, {"dungeon", "Dungeon"}};
	static final String[] FLOORS = {"e", "f1", "f2", "f3", "f4", "f5", "f6", "f7", "m1", "m2", "m3", "m4", "m5", "m6", "m7"};
	static String[] tiers(String type) { return type.equals("dungeon") ? FLOORS : new String[]{"1", "2", "3", "4", "5"}; }
	static String tierName(String type, String tier) { return type.equals("kuudra") ? Counter.KUUDRA_NAMES[Integer.parseInt(tier)] : type.equals("dungeon") ? (tier.equals("e") ? "Entrance" : tier.toUpperCase(Locale.ROOT)) : "T" + tier; }

	public CounterScreen() { super(Component.literal("Grimoire Counter")); }
	@Override public boolean isPauseScreen() { return false; }
	@Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float pt) {}
	@Override public void onClose() { Counter.flush(true); super.onClose(); }

	int btn(GuiGraphicsExtractor g, Font f, String label, int x, int y, int mx, int my, boolean on, String about, Runnable r) {
		int w = f.width(label) + 14; boolean hv = in(mx, my, x, y, w, 16);
		g.fill(x, y, x + w, y + 16, hv ? alpha(0x1C3060, 240) : on ? alpha(0x16264E, 235) : alpha(0x0E1A38, 220)); g.outline(x, y, w, 16, hv ? GILT : on ? MINT : alpha(MOSS, 150));
		g.text(f, label, x + 7, y + 4, hv ? 0xFFFFFFFF : on ? MINT : TEXT, false); hits.add(new Object[]{new int[]{x, y, w, 16}, r});
		if (hv && about != null) tip = about;
		return w + 4;
	}
	int field(GuiGraphicsExtractor g, Font f, String key, String value, String hint, int x, int y, int w, int mx, int my) {
		boolean on = key.equals(focus), hv = in(mx, my, x, y, w, 16);
		g.fill(x, y, x + w, y + 16, alpha(0x000000, 170)); g.outline(x, y, w, 16, on ? GILT : hv ? MINT : alpha(EDGE, 230));
		if (!on) g.text(f, f.plainSubstrByWidth(value.isEmpty() ? hint : value, w - 8), x + 4, y + 4, value.isEmpty() ? DIM : TEXT, false);
		else { // the bar you're typing in: its selection, its text from the first character that fits, its caret
			int len = value.length(); caret = Math.max(0, Math.min(len, caret)); anchor = Math.max(0, Math.min(len, anchor)); int room = w - 8;
			int start = Math.max(0, Math.min(fStart, caret)); while (start < caret && f.width(value.substring(start, caret)) > room - 2) start++;
			fStart = start; String vis = f.plainSubstrByWidth(value.substring(start), room); int end = start + vis.length();
			int sa = Math.max(Math.min(caret, anchor), start), sb = Math.min(Math.max(caret, anchor), end);
			if (sb > sa) g.fill(x + 4 + f.width(value.substring(start, sa)), y + 3, x + 4 + f.width(value.substring(start, sb)), y + 13, 0xFF2D5FCC);
			g.text(f, vis, x + 4, y + 4, 0xFFFFFFFF, false);
			if (System.currentTimeMillis() / 500 % 2 == 0 && caret <= end) { int cx = x + 4 + f.width(value.substring(start, caret)); g.fill(cx, y + 3, cx + 1, y + 13, 0xFFFFFFFF); }
			fBox = new int[]{x, y, w, 16};
		}
		hits.add(new Object[]{new int[]{x, y, w, 16}, (Runnable) () -> { // a click: the caret goes where you clicked (Shift: the selection reaches there; twice: everything is selected)
			boolean same = key.equals(prevFocus); focus = key; String v = value(key); if (!same) fStart = 0; fBox = new int[]{x, y, w, 16};
			int at = indexAt(v, clickX); if (clickDbl) { anchor = 0; caret = v.length(); } else { if (!(same && clickShift)) anchor = at; caret = at; selecting = true; } }});
		return w + 4;
	}
	/** the character of the bar's text nearest to a place on screen */
	int indexAt(String v, double sx) {
		if (fBox == null) return v.length(); Font f = Minecraft.getInstance().font; int start = Math.max(0, Math.min(v.length(), fStart)); String s = v.substring(start);
		int rel = (int) Math.round(sx) - (fBox[0] + 4); if (rel <= 0) return start; int n = f.plainSubstrByWidth(s, rel).length();
		if (n < s.length() && rel - f.width(s.substring(0, n)) > f.width(s.substring(n, n + 1)) / 2) n++;
		return start + n;
	}
	/** what a bar takes and how much of it: a player's name, a count, or prices */
	static boolean takes(String key, char ch) { return key.equals("deathmsg") ? ch >= ' ' && ch != 127 && ch != '§' : key.equals("name") ? Character.isLetterOrDigit(ch) && ch < 128 || ch == '_' : key.equals("amount") ? ch >= '0' && ch <= '9' : ch >= '0' && ch <= '9' || ch == '.' || ch == ',' || ch == ' '; }
	static int most(String key) { return key.equals("deathmsg") ? 120 : key.equals("name") ? 16 : key.equals("amount") ? 4 : 40; }
	/** the text from a to b becomes this (only what the bar takes, as much as still fits); the caret ends after it */
	void edit(int a, int b, String ins) {
		if (focus == null) return; String v = value(focus); a = Math.max(0, Math.min(v.length(), a)); b = Math.max(a, Math.min(v.length(), b));
		StringBuilder ok = new StringBuilder(); int room = most(focus) - (v.length() - (b - a));
		for (int i = 0; i < ins.length() && ok.length() < room; i++) if (takes(focus, ins.charAt(i))) ok.append(ins.charAt(i));
		if (ok.length() == 0 && a == b) return;
		set(focus, v.substring(0, a) + ok + v.substring(b)); caret = anchor = a + ok.length();
	}
	/** typing goes into this bar, all of its text selected */
	void focusOn(String key) { focus = key; fStart = 0; anchor = 0; caret = key == null ? 0 : value(key).length(); }

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
		Font f = font; int W = width, H = height; hits.clear(); tip = null; var D = Counter.D;
		if (moving) { // only the counter, to drag
			g.fill(0, 0, W, H, alpha(0x040814, 120));
			int[] wh = CounterHud.draw(g, true); hudBox = new int[]{D.hudX, D.hudY, Math.round(wh[0] * D.hudScale), Math.round(wh[1] * D.hudScale)};
			g.outline(hudBox[0] - 1, hudBox[1] - 1, hudBox[2] + 2, hudBox[3] + 2, GILT);
			String s = "Drag the counter where you want it · wheel: its size (" + Math.round(D.hudScale * 100) + "%) · Esc or Enter when you're done";
			var hint = f.split(Component.literal(s), W - 20); int hy = H - 12 - hint.size() * 10;
			for (var ln : hint) { g.text(f, ln, (W - f.width(ln)) / 2, hy, TEXT, true); hy += 10; }
			return;
		}
		g.fill(0, 0, W, H, alpha(0x040814, 150));
		int pw = Math.min(W - 20, 640), px = (W - pw) / 2, py = 10, ph = H - 20;
		g.fill(px, py, px + pw, py + ph, alpha(0x081024, 236)); g.outline(px, py, pw, ph, alpha(GILT, 210)); g.fill(px, py, px + pw, py + 2, GILT);
		g.pose().pushMatrix(); g.pose().translate(px + 12, py + 10); g.pose().scale(1.5f, 1.5f); g.text(f, "Grimoire Counter", 0, 0, GILT, true); g.pose().popMatrix();
		{ String close = "Close"; int cx0 = px + pw - f.width(close) - 14 - 8; btn(g, f, close, cx0, py + 8, mx, my, false, null, this::onClose);
			int sx = px + 12 + (int) (f.width("Grimoire Counter") * 1.5f) + 10; g.text(f, clip(f, "by Yggdrasjll · the carry counter from Grimoire", cx0 - sx - 6), sx, py + 15, DIM, false); }
		int x = px + 12, w = pw - 24, top = py + 32, bottom = py + ph - 8;
		g.enableScissor(px + 1, top, px + pw - 1, bottom);
		int y = top + 4 - scroll;

		// ---- the switches
		int bx = x;
		bx += btn(g, f, (D.auto ? "✔ " : "✘ ") + "Count by itself", bx, y, mx, my, D.auto, "Slayer bosses, dungeon runs and Kuudra runs are counted for your clients when Hypixel itself confirms them. Off: only what you count by hand.", () -> { D.auto = !D.auto; Counter.save(); });
		bx += btn(g, f, (D.hud ? "✔ " : "✘ ") + "Counter on screen", bx, y, mx, my, D.hud, "The list of your carries while you play.", () -> { D.hud = !D.hud; Counter.save(); });
		if (bx + f.width("✔ My own bosses") + 14 > x + w) { bx = x; y += 20; }
		bx += btn(g, f, (D.self ? "✔ " : "✘ ") + "My own bosses", bx, y, mx, my, D.self, "Add yourself as the client and your own slayer bosses are counted too: each \"SLAYER QUEST COMPLETE!\" in chat is one, if the quest on your scoreboard is the boss and tier being counted.", () -> { D.self = !D.self; Counter.save(); });
		if (bx + f.width("✔ Count in party chat") + 14 > x + w) { bx = x; y += 20; }
		bx += btn(g, f, (D.announce ? "✔ " : "✘ ") + "Count in party chat", bx, y, mx, my, D.announce, "Each time a carry counts (by itself or by hand), \"Name 3/10 - Grimoire\" is said in your party chat, like typing it yourself.", () -> { D.announce = !D.announce; Counter.save(); });
		if (bx + f.width("✔ Progress lines in chat") + 14 > x + w) { bx = x; y += 20; }
		bx += btn(g, f, (D.chatLines ? "✔ " : "✘ ") + "Progress lines in chat", bx, y, mx, my, D.chatLines, "A line in your own chat for each count, a run that started, and a run that wasn't the booked one. Only you see them.", () -> { D.chatLines = !D.chatLines; Counter.save(); });
		if (bx + f.width("✔ Carries from trades") + 14 > x + w) { bx = x; y += 20; }
		bx += btn(g, f, (D.tradeSuggest ? "✔ " : "✘ ") + "Carries from trades", bx, y, mx, my, D.tradeSuggest, "When someone trades you coins, the carries your price list says that amount buys are offered in chat: one click adds them.", () -> { D.tradeSuggest = !D.tradeSuggest; Counter.save(); });
		if (bx + f.width("✔ Kill time") + 14 > x + w) { bx = x; y += 20; }
		bx += btn(g, f, (D.killTime ? "✔ " : "✘ ") + "Kill time", bx, y, mx, my, D.killTime, "How long each slayer boss took, as a line in your chat with each count: since it spawned, and since its first hit.", () -> { D.killTime = !D.killTime; Counter.save(); });
		if (bx + f.width("✔ Floating kill time") + 14 > x + w) { bx = x; y += 20; }
		bx += btn(g, f, (D.killText ? "✔ " : "✘ ") + "Floating kill time", bx, y, mx, my, D.killText && D.killTime, "The kill time floats where the boss died for a few seconds. Only on your screen. Needs Kill time.", () -> { D.killText = !D.killText; Counter.save(); });
		if (bx + f.width("✔ Boss spawned line") + 14 > x + w) { bx = x; y += 20; }
		bx += btn(g, f, (D.spawnLine ? "✔ " : "✘ ") + "Boss spawned line", bx, y, mx, my, D.spawnLine, "A line in your chat when a slayer client's boss has spawned, saying whose it is. Only you see it.", () -> { D.spawnLine = !D.spawnLine; Counter.save(); });
		if (bx + f.width("✔ Client death warning") + 14 > x + w) { bx = x; y += 20; }
		bx += btn(g, f, (D.deathWarn ? "✔ " : "✘ ") + "Client death warning", bx, y, mx, my, D.deathWarn, "When a slayer client dies while their boss is up (or their boss vanishes and so do they): a red line in your chat, a title and a sound. The boss is gone and nothing counts: they need to spawn a new one - or press [count 1 anyway] on that line.", () -> { D.deathWarn = !D.deathWarn; Counter.save(); });
		if (bx + f.width("Move the counter") + 14 > x + w) { bx = x; y += 20; }
		btn(g, f, "Move the counter", bx, y, mx, my, false, "Drag it where you want it on screen; the wheel sizes it.", () -> { moving = true; focus = null; });
		y += 24;
		{ // your own words for a boss you count though its client died
			String lab = "Said when you count a boss its client died on:"; int lw = f.width(lab) + 6;
			if (lw + 150 > w) { g.text(f, lab, x, y, MUTED, false); y += 11; lw = 0; } else g.text(f, lab, x, y + 4, MUTED, false);
			if (in(mx, my, x, y, w, 16)) tip = "After you press [count 1 anyway] on a client's death line, this is said in your party chat with the count behind it: \"your words / Name 3/10 - Grimoire\". Left empty, only the usual count is said (if Count in party chat is on).";
			field(g, f, "deathmsg", D.deathMsg == null ? "" : D.deathMsg, "nothing extra (type your message)", x + lw, y, w - lw, mx, my);
		}
		y += 26;

		// ---- the carries: counting by hand
		g.text(f, "Carries", x, y, GILT, true); g.fill(x + f.width("Carries") + 6, y + 4, x + w, y + 5, alpha(GILT, 70)); y += 13;
		if (D.carries.isEmpty()) { g.text(f, "None yet: add one below, or /gcounter add voidgloom 4 Name 10.", x, y + 2, DIM, false); y += 16; }
		for (Counter.Carry c : new ArrayList<>(D.carries)) {
			g.fill(x, y, x + w, y + 22, alpha(0x0E1A38, 200)); g.outline(x, y, w, 22, alpha(EDGE, 255));
			// right to left: remove · more/fewer ordered · the count with − and +
			int rx = x + w - 4;
			rx -= f.width("×") + 14; btn(g, f, "×", rx, y + 3, mx, my, false, "Remove this carry (nothing is said in party chat).", () -> Counter.remove(c)); rx -= 6;
			rx -= f.width("+") + 14; btn(g, f, "+", rx, y + 3, mx, my, false, "One more done for " + c.player + ".", () -> Counter.plus(c)); rx -= 2;
			String cnt = c.done + "/" + c.total; rx -= f.width(cnt) + 8; g.text(f, cnt, rx + 4, y + 7, c.done >= c.total ? MOSS : TEXT, true); rx -= 2;
			rx -= f.width("−") + 14; btn(g, f, "−", rx, y + 3, mx, my, false, "One fewer done for " + c.player + ".", () -> Counter.minus(c)); rx -= 10;
			rx -= f.width("more") + 14; btn(g, f, "more", rx, y + 3, mx, my, false, "One more ordered (" + (c.total + 1) + " in all). Shift: ten more.", () -> Counter.setTotal(c, c.total + (shift() ? 10 : 1))); rx -= 2;
			rx -= f.width("fewer") + 14; btn(g, f, "fewer", rx, y + 3, mx, my, false, "One fewer ordered. Shift: ten fewer.", () -> Counter.setTotal(c, c.total - (shift() ? 10 : 1)));
			if (Counter.hasRefused(c)) { rx -= f.width("count it") + 14 + 6; btn(g, f, "count it", rx, y + 3, mx, my, true, "A boss of theirs died but wasn't counted (another tier, or no owner name on it): count it.", () -> Counter.claim(c.player)); }
			// the words on the left take the room the buttons leave
			int tx = x + 6, room = rx - 6; g.text(f, clip(f, c.player, room - tx), tx, y + 3, MINT, true); tx += f.width(c.player) + 6;
			if (tx < room) { g.text(f, clip(f, c.label(), room - tx), tx, y + 3, MUTED, false); tx += f.width(c.label()) + 6; }
			if (Counter.bossUp(c) && tx + f.width("SPAWNED") < room) { g.text(f, "SPAWNED", tx, y + 3, ORANGE, true); tx += f.width("SPAWNED") + 6; }
			double unit = Counter.unitPrice(c); if (unit > 0 && room - tx > 30) g.text(f, clip(f, Counter.coins(unit * c.total) + " for all · " + Counter.coins(unit * (c.total - c.done)) + " to go", room - tx), tx, y + 3, DIM, false);
			int bw = Math.max(20, rx - x - 12); g.fill(x + 6, y + 16, x + 6 + bw, y + 18, alpha(0x16264E, 255)); g.fill(x + 6, y + 16, x + 6 + (int) (bw * Math.min(1, c.total == 0 ? 0 : (double) c.done / c.total)), y + 18, MOSS);
			y += 25;
		}
		y += 4;

		// ---- a new one
		g.text(f, "Add a carry", x, y, GILT, true); g.fill(x + f.width("Add a carry") + 6, y + 4, x + w, y + 5, alpha(GILT, 70)); y += 13;
		bx = x;
		bx += field(g, f, "name", addName, "Player's name", bx, y, 110, mx, my);
		String type = TYPES[addType][0]; String[] ts = tiers(type); if (addTier >= ts.length) addTier = ts.length - 1;
		bx += btn(g, f, TYPES[addType][1] + " ▸", bx, y, mx, my, false, "Click: the next kind of carry · right click: the one before.", () -> { addType = (addType + (lastButton == 1 ? TYPES.length - 1 : 1)) % TYPES.length; addTier = Math.min(addTier, tiers(TYPES[addType][0]).length - 1); });
		bx += btn(g, f, tierName(type, ts[addTier]) + " ▸", bx, y, mx, my, false, "Click: the next tier or floor · right click: the one before.", () -> { int n = tiers(TYPES[addType][0]).length; addTier = (addTier + (lastButton == 1 ? n - 1 : 1)) % n; });
		g.text(f, "×", bx + 2, y + 4, MUTED, false); bx += 12;
		bx += field(g, f, "amount", addAmount, "1", bx, y, 40, mx, my);
		bx += btn(g, f, "Add", bx, y, mx, my, true, null, this::addNow);
		y += 20;
		// players near you, to pick the name with a click
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null) { bx = x; int shown = 0;
			for (var p : mc.level.players()) { if (p == mc.player || p.getUUID().version() != 4) continue; String n = p.getGameProfile().name(); if (bx + f.width(n) + 14 > x + w || shown >= 12) break; bx += btn(g, f, n, bx, y, mx, my, n.equalsIgnoreCase(addName), "Near you: click to use this name.", () -> addName = n); shown++; }
			if (shown > 0) y += 20; }
		y += 6;

		// ---- the price list
		g.text(f, "Prices", x, y, GILT, true); int hx = x + f.width("Prices") + 6;
		g.text(f, clip(f, "millions of coins per carry · several, separated by commas: the first is your usual one", x + w - hx), hx, y, DIM, false); y += 13;
		int cols = w >= 560 ? 3 : 2, cw = (w - (cols - 1) * 8) / cols, i = 0;
		for (String[] r : Counter.PRICE_ROWS) {
			int cx = x + (i % cols) * (cw + 8), cy = y + (i / cols) * 19; String key = r[0] + "|" + r[1];
			g.text(f, Counter.rowLabel(r[0], r[1]), cx, cy + 4, TEXT, false);
			field(g, f, key, D.prices.getOrDefault(key, ""), "—", cx + 92, cy, cw - 92, mx, my);
			i++;
		}
		y += ((i + cols - 1) / cols) * 19 + 8;
		for (var ln : f.split(Component.literal("Commands: /gcounter · /gcounter add <kind> <tier> <player> <amount> · /gcounter plus, minus, remove or claim <player>"), w)) { g.text(f, ln, x, y, DIM, false); y += 10; }
		y += 2;
		g.disableScissor();
		maxScroll = Math.max(0, y + scroll - bottom + 4); scroll = Math.max(0, Math.min(maxScroll, scroll));
		if (tip != null && in(mx, my, px, top, pw, bottom - top)) g.setTooltipForNextFrame(f, f.split(Component.literal(tip), Math.min(300, W - 40)), mx, my);
	}

	int lastButton = 0;
	static boolean shift() { return Minecraft.getInstance().hasShiftDown(); }
	void addNow() {
		String n = addName.trim(); int amount; try { amount = Integer.parseInt(addAmount.trim()); } catch (Exception e) { amount = 0; }
		if (!n.matches("\\w{1,16}")) { Counter.info("Type the player's name first.", ChatFormatting.RED); focusOn("name"); return; }
		if (amount < 1) { Counter.info("How many carries? Type a number.", ChatFormatting.RED); focusOn("amount"); return; }
		String type = TYPES[addType][0]; String[] kt = {type.equals("kuudra") ? "kuudra" : type.equals("dungeon") ? "dungeon" : "slayer", type, tiers(type)[addTier]};
		Counter.add(n, kt, amount); addName = ""; addAmount = "1"; focus = null;
	}

	@Override public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
		lastButton = e.button();
		if (moving) { if (in(e.x(), e.y(), hudBox[0], hudBox[1], hudBox[2], hudBox[3])) { dragging = true; grabX = e.x() - Counter.D.hudX; grabY = e.y() - Counter.D.hudY; } return true; }
		String was = focus; focus = null; prevFocus = was; clickX = e.x(); clickDbl = dbl && e.button() == 0; clickShift = e.hasShiftDown(); selecting = false;
		for (int i = hits.size() - 1; i >= 0; i--) { int[] r = (int[]) hits.get(i)[0]; if (in(e.x(), e.y(), r[0], r[1], r[2], r[3])) { ((Runnable) hits.get(i)[1]).run(); break; } }
		if (was != null && !was.equals(focus)) Counter.save();
		return true;
	}
	@Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (selecting && focus != null && !moving) { caret = indexAt(value(focus), e.x()); return true; } // dragging over a bar's text selects it
		if (moving && dragging) { Counter.D.hudX = (int) Math.max(0, Math.min(width - 20, e.x() - grabX)); Counter.D.hudY = (int) Math.max(0, Math.min(height - 12, e.y() - grabY)); }
		return true;
	}
	@Override public boolean mouseReleased(MouseButtonEvent e) { selecting = false; if (dragging) { dragging = false; Counter.save(); } return true; }
	@Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (moving) { Counter.D.hudScale = Math.max(0.5f, Math.min(3f, Math.round((Counter.D.hudScale + (sy > 0 ? 0.1f : -0.1f)) * 10) / 10f)); Counter.save(); return true; }
		scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (sy * 24))); return true;
	}
	@Override public boolean keyPressed(KeyEvent e) {
		int k = e.key();
		if (moving) { if (k == GLFW.GLFW_KEY_ESCAPE || k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) { moving = false; dragging = false; Counter.save(); } return true; }
		if (focus != null) {
			String v = value(focus); int len = v.length(); caret = Math.max(0, Math.min(len, caret)); anchor = Math.max(0, Math.min(len, anchor));
			int a = Math.min(caret, anchor), b = Math.max(caret, anchor); boolean shift = e.hasShiftDown(), ctrl = e.hasControlDown();
			if (k == GLFW.GLFW_KEY_ESCAPE) { focus = null; Counter.save(); return true; }
			if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) { if (focus.equals("name") || focus.equals("amount")) addNow(); else { focus = null; Counter.save(); } return true; }
			if (k == GLFW.GLFW_KEY_TAB) { if (focus.equals("name")) focusOn("amount"); else if (focus.equals("amount")) focusOn("name"); return true; }
			// the keys every text bar answers to: select all, copy, cut, paste; the arrows, Home and End (Shift selects); Backspace and Delete
			if (e.isSelectAll()) { anchor = 0; caret = len; return true; }
			if (e.isCopy()) { if (a != b) Minecraft.getInstance().keyboardHandler.setClipboard(v.substring(a, b)); return true; }
			if (e.isCut()) { if (a != b) { Minecraft.getInstance().keyboardHandler.setClipboard(v.substring(a, b)); edit(a, b, ""); } return true; }
			if (e.isPaste()) { String clip = Minecraft.getInstance().keyboardHandler.getClipboard(); edit(a, b, clip == null ? "" : clip.replace("\n", " ").replace("\r", "")); return true; }
			if (k == GLFW.GLFW_KEY_LEFT) { caret = !shift && a != b ? a : ctrl ? 0 : Math.max(0, caret - 1); if (!shift) anchor = caret; return true; }
			if (k == GLFW.GLFW_KEY_RIGHT) { caret = !shift && a != b ? b : ctrl ? len : Math.min(len, caret + 1); if (!shift) anchor = caret; return true; }
			if (k == GLFW.GLFW_KEY_HOME || k == GLFW.GLFW_KEY_UP) { caret = 0; if (!shift) anchor = caret; return true; }
			if (k == GLFW.GLFW_KEY_END || k == GLFW.GLFW_KEY_DOWN) { caret = len; if (!shift) anchor = caret; return true; }
			if (k == GLFW.GLFW_KEY_BACKSPACE) { if (a != b) edit(a, b, ""); else if (caret > 0) edit(ctrl ? 0 : caret - 1, caret, ""); return true; }
			if (k == GLFW.GLFW_KEY_DELETE) { if (a != b) edit(a, b, ""); else if (caret < len) edit(caret, ctrl ? len : caret + 1, ""); return true; }
			return true;
		}
		return super.keyPressed(e);
	}
	@Override public boolean charTyped(CharacterEvent e) {
		if (focus == null) return false;
		String ch = e.codepointAsString(); int len = value(focus).length(); caret = Math.max(0, Math.min(len, caret)); anchor = Math.max(0, Math.min(len, anchor));
		edit(Math.min(caret, anchor), Math.max(caret, anchor), ch); // typing takes the place of what is selected
		return true;
	}
	String value(String key) { return key.equals("deathmsg") ? (Counter.D.deathMsg == null ? "" : Counter.D.deathMsg) : key.equals("name") ? addName : key.equals("amount") ? addAmount : Counter.D.prices.getOrDefault(key, ""); }
	void set(String key, String v) {
		if (key.equals("deathmsg")) { Counter.D.deathMsg = v; Counter.save(); return; }
		if (key.equals("name")) addName = v; else if (key.equals("amount")) addAmount = v;
		else { if (v.isBlank()) Counter.D.prices.remove(key); else Counter.D.prices.put(key, v); }
	}
}

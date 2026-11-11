/*
 * Grimoire Counter - Copyright (c) 2026 Yggdrasjll. All rights reserved.
 * Not to be copied, modified, decompiled, reused or shared without Yggdrasjll's written permission. See LICENSE.txt.
 */
package dev.grimoire.counter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The carry counter (slayer / dungeon / Kuudra), with Grimoire's own rules. Read-only: it only reads chat, the
 * scoreboard, the tab list and the name tags your client already shows. A carry counts only when Hypixel itself
 * confirms the kill or the run:
 *  - Slayer: Hypixel shows each slayer boss's owner with it. A boss with a client's name on it that dies near you
 *    (its death animation, or its name vanishing with almost no health left) counts one boss for them, for the carry
 *    that matches the boss's own type and tier. Each boss counts once; one that despawns with health left (quest
 *    failed, the client died, you left) never counts. LOOT SHARE lines count nothing.
 *  - Dungeons: the run started with the client in the instance, the floor is read from the scoreboard, and the end
 *    screen shows "☠ Defeated <boss> in ..." (a failed run has no such line, so it never counts).
 *  - Kuudra: the run started (Elle's "Okay adventurers" line) with the client present, the tier is read from the
 *    scoreboard, and "KUUDRA DOWN!" (a "DEFEAT" never counts).
 */
public final class Counter {
	private Counter() {}

	public static final class Carry {
		public String player, kind, type, tier; // kind: slayer|dungeon|kuudra
		public int total, done;
		public long started, last;
		public String label() {
			return switch (kind) {
				case "slayer" -> SLAYER_NAMES.getOrDefault(type, type) + " T" + tier;
				case "dungeon" -> tier.equals("e") ? "Entrance" : tier.toUpperCase(Locale.ROOT);
				default -> "Kuudra " + KUUDRA_NAMES[Integer.parseInt(tier)];
			};
		}
	}
	public static final class Data {
		public List<Carry> carries = new ArrayList<>();
		/** the counter on screen · counting by itself · the count in party chat · progress lines in your own chat · carries offered when a client trades you coins */
		public boolean hud = true, auto = true, announce = false, chatLines = true, tradeSuggest = true, self = true;
		public int hudX = 6, hudY = 40; public float hudScale = 1f;
		/** "type|tier" -> prices in millions per carry, in priority order, e.g. "1.3, 2.3, 2, 1.5" */
		public Map<String, String> prices = new LinkedHashMap<>();
	}

	public static final String[][] PRICE_ROWS = {
		{"void", "3"}, {"void", "4"}, {"blaze", "2"}, {"blaze", "3"}, {"blaze", "4"}, {"rev", "4"}, {"rev", "5"}, {"tara", "4"}, {"tara", "5"}, {"sven", "4"},
		{"kuudra", "1"}, {"kuudra", "2"}, {"kuudra", "3"}, {"kuudra", "4"}, {"kuudra", "5"},
		{"dungeon", "f4"}, {"dungeon", "f5"}, {"dungeon", "f6"}, {"dungeon", "f7"}, {"dungeon", "m1"}, {"dungeon", "m2"}, {"dungeon", "m3"}, {"dungeon", "m4"}, {"dungeon", "m5"}, {"dungeon", "m6"}, {"dungeon", "m7"},
		// every other carry the counter knows (after the usual ones, so a trade is matched to those first)
		{"void", "2"}, {"void", "1"}, {"blaze", "1"}, {"rev", "3"}, {"rev", "2"}, {"rev", "1"}, {"tara", "3"}, {"tara", "2"}, {"tara", "1"}, {"sven", "3"}, {"sven", "2"}, {"sven", "1"},
		{"vamp", "5"}, {"vamp", "4"}, {"vamp", "3"}, {"vamp", "2"}, {"vamp", "1"}, {"dungeon", "f3"}, {"dungeon", "f2"}, {"dungeon", "f1"}, {"dungeon", "e"}};
	public static final Map<String, String> SLAYER_NAMES = Map.of("void", "Voidgloom", "blaze", "Inferno", "rev", "Revenant", "tara", "Tarantula", "sven", "Sven", "vamp", "Riftstalker");
	public static final String[] KUUDRA_NAMES = {"", "Basic", "Hot", "Burning", "Fiery", "Infernal"};
	static final Map<String, String> BOSS_TYPE = Map.of("Voidgloom Seraph", "void", "Inferno Demonlord", "blaze", "Revenant Horror", "rev", "Atoned Horror", "rev", "Tarantula Broodfather", "tara", "Conjoined Brood", "tara", "Sven Packmaster", "sven", "Riftstalker Bloodfiend", "vamp");

	public static String rowLabel(String type, String tier) { return switch (type) { case "kuudra" -> "Kuudra " + KUUDRA_NAMES[Integer.parseInt(tier)]; case "dungeon" -> tier.equals("e") ? "Entrance" : tier.toUpperCase(Locale.ROOT); default -> SLAYER_NAMES.getOrDefault(type, type) + " T" + tier; }; }
	/** Command words for a price row (what /gcounter add expects). */
	public static String cmdType(String type) { return switch (type) { case "rev" -> "revenant"; case "tara" -> "tarantula"; case "vamp" -> "vampire"; default -> type; }; }
	public static String cmdTier(String type, String tier) { return type.equals("kuudra") ? KUUDRA_NAMES[Integer.parseInt(tier)].toLowerCase(Locale.ROOT) : tier; }
	public static List<Double> priceList(String key) {
		List<Double> l = new ArrayList<>(); String v = D.prices.get(key); if (v == null) return l;
		for (String x : v.split("[,; ]+")) { try { double d = Double.parseDouble(x.trim().toLowerCase(Locale.ROOT).replace("m", "")); if (d > 0) l.add(d); } catch (Exception ignored) {} }
		return l;
	}
	/** The price list's row for a carry ("void|4", "dungeon|f7", "kuudra|3"). */
	public static String priceKey(Carry c) { return switch (c.kind) { case "slayer" -> c.type + "|" + c.tier; case "dungeon" -> "dungeon|" + c.tier; default -> "kuudra|" + c.tier; }; }
	/** Coins per carry: the first price in the list for that row (0: none set). */
	public static double unitPrice(Carry c) { List<Double> pl = priceList(priceKey(c)); return pl.isEmpty() ? 0 : pl.get(0) * 1e6; }

	// ---------- persistence: config/grimoire-counter/counter.json ----------
	static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	static Path file() { return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("grimoire-counter").resolve("counter.json"); }
	public static Data D = load();
	static Data load() {
		Data d = null; try { d = GSON.fromJson(Files.readString(file()), Data.class); } catch (Exception ignored) {}
		if (d == null) d = new Data();
		if (d.carries == null) d.carries = new ArrayList<>();
		if (d.prices == null) d.prices = new LinkedHashMap<>();
		if (d.hudScale <= 0) d.hudScale = 1f;
		d.carries.removeIf(c -> c == null || c.player == null || c.kind == null || c.type == null || c.tier == null);
		return d;
	}
	static long dirtyAt = 0;
	/** Asked for after every change; written a moment later (once), through a temporary file so a crash never leaves half a file. */
	public static void save() { if (dirtyAt == 0) dirtyAt = System.currentTimeMillis(); }
	static void flush(boolean now) {
		if (dirtyAt == 0 || (!now && System.currentTimeMillis() - dirtyAt < 1500)) return;
		dirtyAt = 0;
		try { Path f = file(); Files.createDirectories(f.getParent()); Path tmp = f.resolveSibling("counter.json.tmp"); Files.writeString(tmp, GSON.toJson(D)); Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING); } catch (Exception ignored) {}
	}

	// ---------- adding a carry ----------
	/** Returns {kind, type, tier} or null. */
	public static String[] parse(String type, String tier) {
		String t = type.toLowerCase(Locale.ROOT), l = tier.toLowerCase(Locale.ROOT).replace("tier", "").replace("t", "").trim();
		String slayer = switch (t) {
			case "void", "voidgloom", "eman", "enderman" -> "void";
			case "blaze", "inferno", "demonlord" -> "blaze";
			case "rev", "revenant", "zombie" -> "rev";
			case "tara", "tarantula", "spider" -> "tara";
			case "sven", "wolf" -> "sven";
			case "vamp", "vampire", "riftstalker" -> "vamp";
			default -> null;
		};
		if (slayer != null) { if (!l.matches("[1-5]")) return null; return new String[]{"slayer", slayer, l}; }
		if (t.equals("kuudra")) {
			String k = switch (tier.toLowerCase(Locale.ROOT)) { case "basic", "t1", "1" -> "1"; case "hot", "t2", "2" -> "2"; case "burning", "t3", "3" -> "3"; case "fiery", "t4", "4" -> "4"; case "infernal", "t5", "5" -> "5"; default -> null; };
			return k == null ? null : new String[]{"kuudra", "kuudra", k};
		}
		if (t.equals("dungeon") || t.equals("dungeons") || t.equals("catacombs") || t.equals("cata")) {
			String f = tier.toLowerCase(Locale.ROOT);
			if (f.equals("e") || f.equals("entrance") || f.equals("f0")) return new String[]{"dungeon", "dungeon", "e"};
			if (f.matches("[fm][1-7]")) return new String[]{"dungeon", "dungeon", f};
			return null;
		}
		return null;
	}
	/** A new carry for a client, or more of the one they have (a different one of the same kind replaces it). */
	public static Carry add(String player, String[] kt, int amount) {
		Carry c = find(player, kt[0]);
		if (c != null && c.type.equals(kt[1]) && c.tier.equals(kt[2])) { c.total += amount; save(); return c; }
		if (c != null) D.carries.remove(c);
		c = new Carry(); c.player = player; c.kind = kt[0]; c.type = kt[1]; c.tier = kt[2]; c.total = amount;
		D.carries.add(c); save();
		return c;
	}
	public static Carry find(String player, String kind) { for (Carry c : D.carries) if (c.player.equalsIgnoreCase(player) && (kind == null || c.kind.equals(kind))) return c; return null; }
	public static void remove(Carry c) { D.carries.remove(c); save(); }

	// ---------- what Hypixel says and shows ----------
	static final Pattern DUNGEON_END = Pattern.compile("^\\s*(?:Master Mode )?(?:The )?Catacombs - (?:Entrance|Floor [IVX]{1,4})\\s*$");
	static final Pattern DUNGEON_WIN = Pattern.compile("^\\s*☠ Defeated .+ in \\d");
	static final Pattern MORT = Pattern.compile("^\\[NPC] Mort: Here, I found this map when I first entered the dungeon\\.$");
	static final Pattern ELLE_START = Pattern.compile("^\\[NPC] Elle: Okay adventurers, I will go and fish up Kuudra!$");
	static final Pattern KUUDRA_DOWN = Pattern.compile("^\\s*KUUDRA DOWN!\\s*$");
	static final Pattern KUUDRA_DEFEAT = Pattern.compile("^\\s*DEFEAT\\s*$");
	static final Pattern SB_FLOOR = Pattern.compile("The Catacombs \\((E|[FM][1-7])\\)");
	static final Pattern SB_KUUDRA = Pattern.compile("Kuudra's Hollow \\(T([1-5])\\)");
	static final Pattern BOSS_TAG = Pattern.compile("(?:☠ )?(Voidgloom Seraph|Inferno Demonlord|Revenant Horror|Atoned Horror|Tarantula Broodfather|Conjoined Brood|Sven Packmaster|Riftstalker Bloodfiend)(?: ([IVX]{1,3}))?\\s+(.*)$");
	/** The owner line Hypixel puts with a slayer boss ("Spawned by: Name"; also read: "Owner: Name", "Summoned by Name"). */
	static final Pattern SPAWNED_BY = Pattern.compile("(?i)^(?:spawned by|owner|summoned by|spawner)\\s*:?\\s*(?:\\[[^\\]]+]\\s*)?(\\w{1,16})$");
	/** The owner written into the boss's own tag: "Name's Voidgloom Seraph IV …". */
	static final Pattern OWNER_IN_TAG = Pattern.compile("(?:^|☠ |\\s)(?:\\[[^\\]]+]\\s*)?(\\w{1,16})['’`]s? (?:Voidgloom|Inferno|Revenant|Atoned|Tarantula|Conjoined|Sven|Riftstalker)");
	/** A line with only a player's name (a rank may come first): an owner line too, when that player is in your world. */
	static final Pattern NAME_ONLY = Pattern.compile("^(?:\\[[^\\]]+]\\s*)?(\\w{3,16})$");
	/** Hypixel's owner line above a slayer boss: "Name's SLAYER BOSS" (a rank may come first), or "Your SLAYER BOSS".
	 *  A name ending in s gets only an apostrophe: "Names' SLAYER BOSS". */
	static final Pattern SLAYER_BOSS_LINE = Pattern.compile("(?i)^(?:\\[[^\\]]+]\\s*)?(\\w{1,16})['’`]s?\\s+slayer\\s+boss\\W*$"), YOUR_BOSS_LINE = Pattern.compile("(?i)^your\\s+slayer\\s+boss$");
	/** Mayor Jerry's perk dresses slayer bosses as Jerrys: "Owner's Blue Jerry  2 Hits" (the hits phase of a Voidgloom) or with hearts. */
	static final Pattern JERRY_TAG = Pattern.compile("^(?:☠ )?(\\w{1,16})['’`]s? (?:\\w+ )?Jerry\\s+(.*)$");
	/** The owner a name line states (null: it isn't one). players: names of the players in your world, lower-case. */
	static String ownerLine(String t, Set<String> players) {
		if (t == null) return null; Matcher m = SPAWNED_BY.matcher(t); if (m.find()) return m.group(1);
		m = SLAYER_BOSS_LINE.matcher(t); if (m.find()) return m.group(1);
		if (YOUR_BOSS_LINE.matcher(t).find()) { var p = Minecraft.getInstance().player; return p == null ? null : p.getName().getString(); }
		m = NAME_ONLY.matcher(t); if (m.find() && players.contains(m.group(1).toLowerCase(Locale.ROOT))) return m.group(1);
		return null;
	}
	/** When an owner line was last seen next to any boss (yours or a stranger's). */
	static long anyOwnerLineAt = 0;

	static final class Boss { String owner, type, tier, name; double firstHp = -1, lastHp = -1; Vec3 pos; long seen, counted, goneAt, spawnAt, hitAt, dyingAt; int tagId, mobId = -1; boolean guessed; }
	static final Map<Integer, Boss> bosses = new HashMap<>();
	static String dungeonFloor = null; static Set<String> dungeonStart = new HashSet<>(); static long dungeonEndAt = 0; static boolean dungeonStarted = false;
	static String kuudraTier = null; static Set<String> kuudraStart = new HashSet<>(); static boolean kuudraStarted = false;
	static long lastScan = 0, lastGuess = 0; static Object lastLevel = null;

	static String strip(String s) { String p = ChatFormatting.stripFormatting(s == null ? "" : s); return (p == null ? "" : p).replaceAll("§.", "").replace(' ', ' ').trim(); }

	// ---------- chat ----------
	/** The system chat line being handled right now (see ChatPacketMixin); cleared once the normal hook has seen it. */
	public static volatile Component pendingPacket = null;
	public static void onChat(Component msg, boolean overlay) {
		{ Component pp = pendingPacket; if (pp != null && (pp == msg || pp.getString().equals(msg.getString()))) pendingPacket = null; }
		if (overlay) return;
		String s = strip(msg.getString());
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> { try { handle(s); } catch (Throwable ignored) {} });
	}
	static final Pattern TRADE_DONE = Pattern.compile("^Trade completed with (?:\\[[^]]+] )?(\\w{1,16})!$");
	static final Pattern TRADE_COINS = Pattern.compile("^\\+ ([\\d.,]+)([kKmMbB]?) [Cc]oins$");
	static String trader = null; static long traderAt = 0;
	static final Pattern QUEST_DONE = Pattern.compile("^\\s*SLAYER QUEST COMPLETE!\\s*$");
	/** Your own bosses: with yourself as the client, "SLAYER QUEST COMPLETE!" in chat is Hypixel saying your boss died -
	 *  one for your carry. The quest on your scoreboard says which boss it was, so another type or tier isn't counted;
	 *  and a boss its name tag already counted isn't counted again. */
	static final Pattern OWN_QUEST = Pattern.compile("(Voidgloom Seraph|Inferno Demonlord|Revenant Horror|Atoned Horror|Tarantula Broodfather|Conjoined Brood|Sven Packmaster|Riftstalker Bloodfiend)(?: ([IVX]{1,3}))?\\s*$");
	/** "SLAYER BOSS! The Inferno Demonlord IV spawned!": what the one whose quest it is is told when their boss comes */
	static final Pattern OWN_SPAWN = Pattern.compile("SLAYER BOSS!?\\W*(?:The )?(Voidgloom Seraph|Inferno Demonlord|Revenant Horror|Atoned Horror|Tarantula Broodfather|Conjoined Brood|Sven Packmaster|Riftstalker Bloodfiend)(?: ([IVX]{1,3}))?\\b.*spawned");
	static String ownType = null, ownTier = null; static long ownSpawnAt = 0;
	static void ownBoss(long now) {
		Minecraft mc = Minecraft.getInstance();
		String me = mc.player != null ? mc.player.getGameProfile().name() : mc.getUser().getName(); Carry c = find(me, "slayer"); if (c == null || c.done >= c.total) return;
		String lk = me.toLowerCase(Locale.ROOT); Long last = lastLoot.get(lk); if (last != null && now - last < GHOST_MS) return;
		// which boss it was: the "SLAYER BOSS! The … spawned!" line you were sent when it came, else the quest on your scoreboard
		if (ownType != null && now - ownSpawnAt < 1_200_000L) { String type = ownType, tier = ownTier; ownType = null;
			if (!(type.equals(c.type) && tier.equals(c.tier))) { progress("Your boss wasn't counted: it was a " + SLAYER_NAMES.getOrDefault(type, type) + " T" + tier + ", not your " + c.label() + ".", ChatFormatting.GRAY); return; } }
		else if (mc.level != null) try { for (String line : sidebar(mc)) { Matcher q = OWN_QUEST.matcher(line.trim()); if (!q.find()) continue;
			String type = BOSS_TYPE.get(q.group(1)), tier = q.group(2) == null ? "5" : String.valueOf(roman(q.group(2)));
			if (type != null && !(type.equals(c.type) && tier.equals(c.tier))) { progress("Your boss wasn't counted: it was a " + SLAYER_NAMES.getOrDefault(type, type) + " T" + tier + ", not your " + c.label() + ".", ChatFormatting.GRAY); return; }
			break; } } catch (Throwable ignored) {}
		lastLoot.put(lk, now); count(c, "");
	}
	static void handle(String s) {
		Matcher m; long now = System.currentTimeMillis();
		heard(s);
		if (s.contains("SLAYER BOSS") && !s.matches("^(?:\\[[^\\]]+] ?)*\\w{1,16}: .*") && !s.startsWith("Party >") && !s.startsWith("Guild >") && (m = OWN_SPAWN.matcher(s)).find()) { ownType = BOSS_TYPE.get(m.group(1)); ownTier = m.group(2) == null ? "5" : String.valueOf(roman(m.group(2))); ownSpawnAt = now; }
		if ((m = TRADE_DONE.matcher(s)).find()) { trader = m.group(1); traderAt = now; return; }
		if (trader != null && now - traderAt < 3000 && (m = TRADE_COINS.matcher(s)).find()) { tradeCoins(trader, m.group(1), m.group(2)); trader = null; return; }
		if (!D.auto) return;
		if (D.self && QUEST_DONE.matcher(s).find()) { ownBoss(now); return; }
		if (MORT.matcher(s).find()) { dungeonStarted = true; dungeonStart = present(); if (dungeonFloor != null) startMsg("dungeon", dungeonFloor.toLowerCase(Locale.ROOT), dungeonStart); return; }
		if (DUNGEON_END.matcher(s).find()) { dungeonEndAt = now; return; }
		if (DUNGEON_WIN.matcher(s).find() && now - dungeonEndAt < 8000 && dungeonStarted) { dungeonStarted = false; dungeonWin(); return; }
		if (ELLE_START.matcher(s).find()) { kuudraStarted = true; kuudraStart = present(); if (kuudraTier != null) startMsg("kuudra", kuudraTier, kuudraStart); return; }
		if (KUUDRA_DOWN.matcher(s).find() && kuudraStarted) { kuudraStarted = false; kuudraWin(); return; }
		if (KUUDRA_DEFEAT.matcher(s).find() && kuudraStarted) { kuudraStarted = false; if (D.carries.stream().anyMatch(c -> c.kind.equals("kuudra"))) progress("Kuudra run failed: no carries counted.", ChatFormatting.RED); }
	}

	/** Coins received in a trade: the carries whose price fits the amount exactly, offered as buttons in chat. */
	static void tradeCoins(String who, String num, String unit) {
		if (!D.tradeSuggest) return;
		double v; try { v = Double.parseDouble(num.replace(",", "")); } catch (Exception e) { return; }
		v *= switch (unit.toLowerCase(Locale.ROOT)) { case "k" -> 1e3; case "m" -> 1e6; case "b" -> 1e9; default -> 1; };
		List<String[]> hits = new ArrayList<>(); // type, tier, count, price
		for (String[] r : PRICE_ROWS) {
			for (double pm : priceList(r[0] + "|" + r[1])) {
				double n = v / (pm * 1e6); long rn = Math.round(n);
				if (rn >= 1 && rn <= 500 && Math.abs(n - rn) < 0.005) { hits.add(new String[]{r[0], r[1], String.valueOf(rn), fmtM(pm)}); break; } // the first price, in your order, that fits
			}
		}
		Minecraft mc = Minecraft.getInstance(); if (mc.player == null) return;
		String coins = fmtM(v / 1e6) + "M";
		if (hits.isEmpty()) { info("Received " + coins + " from " + who + ": no carry price fits that amount (set your prices with /gcounter).", ChatFormatting.GRAY); return; }
		Carry ex = find(who, null); // a carry this client already has goes first
		if (ex != null) hits.sort(Comparator.comparingInt(h -> rowLabel(h[0], h[1]).equals(ex.label()) ? 0 : 1));
		MutableComponent msg = prefix().append(Component.literal("Received " + coins + " from " + who + ". Add: ").withStyle(ChatFormatting.WHITE));
		for (int i = 0; i < Math.min(6, hits.size()); i++) {
			String[] h = hits.get(i);
			String cmd = "gcounter add " + cmdType(h[0]) + " " + cmdTier(h[0], h[1]) + " " + who + " " + h[2];
			String tip = h[2] + " × " + h[3] + "M = " + coins + "\nClick to add to the counter";
			MutableComponent b = Component.literal("[" + h[2] + "× " + rowLabel(h[0], h[1]) + "]").withStyle(i == 0 ? ChatFormatting.GREEN : ChatFormatting.AQUA);
			b.withStyle(st -> st.withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand(cmd)).withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.literal(tip).withStyle(ChatFormatting.GRAY))));
			if (i > 0) msg.append(Component.literal(" "));
			msg.append(b);
		}
		mc.player.sendSystemMessage(msg);
	}
	/** "Name's", or "Names'" for a name ending in s (as Hypixel writes it). */
	public static String poss(String name) { return name == null ? "" : name + (name.endsWith("s") || name.endsWith("S") ? "'" : "'s"); }
	public static String fmtM(double m) { return m == Math.floor(m) ? String.valueOf((long) m) : String.valueOf(Math.round(m * 1000) / 1000.0); }

	// ---------- slayer: a boss with the client's name on it died ----------
	/** A boss of a client's that died but didn't count (another tier, or no owner name on it): [count it] counts it. */
	static final Map<String, Long> refused = new HashMap<>();
	static final Map<String, Long> lastLoot = new HashMap<>(); // when each client's last boss counted
	/** After a client's boss counted: tags seen again within this long are the same boss dying, not a new one. */
	static final long GHOST_MS = 8000;

	/** Reads a boss tag (name, slayer, tier, health) into b; false if it isn't one. */
	static boolean readTag(Boss b, Entity tag, long now) {
		String tagText = Tags.plain(tag); if (tagText == null) return false;
		Matcher tm = BOSS_TAG.matcher(tagText);
		String hpText;
		if (tm.find()) { b.name = tm.group(1); b.type = BOSS_TYPE.get(tm.group(1)); b.tier = tm.group(2) == null ? "5" : String.valueOf(roman(tm.group(2))); hpText = tm.group(3); }
		else {
			// a Jerry-skinned boss: the tag doesn't say which slayer it is, so the client's carry tells
			Matcher jm = JERRY_TAG.matcher(tagText); if (!jm.find()) return false;
			Carry cc = find(b.owner, "slayer");
			b.name = cc != null ? SLAYER_NAMES.getOrDefault(cc.type, "Slayer boss") : "Slayer boss"; b.type = cc != null ? cc.type : "void"; b.tier = cc != null ? cc.tier : "5"; hpText = jm.group(2);
		}
		double hp = hp(hpText); { double mx = hpMax(hpText); if (mx > 0 && mx > b.firstHp) b.firstHp = mx; } // (a tag that says what it started with: its health is known from the first look)
		if (hp >= 0) { if (b.firstHp < 0 || hp > b.firstHp) b.firstHp = hp; if (b.hitAt == 0 && b.firstHp > 0 && hp < b.firstHp) b.hitAt = now; b.lastHp = hp; }
		b.seen = now; b.tagId = tag.getId();
		return true;
	}
	/** Whether a mob can be this slayer's boss (its body is always the same kind of mob; null type: any). */
	static boolean bodyFits(Entity e, String type) {
		if (type == null) return true;
		return switch (type) {
			case "void" -> e instanceof net.minecraft.world.entity.monster.EnderMan;
			case "blaze" -> e instanceof net.minecraft.world.entity.monster.Blaze;
			case "rev" -> e instanceof net.minecraft.world.entity.monster.zombie.Zombie;
			case "tara" -> e instanceof net.minecraft.world.entity.monster.spider.Spider;
			case "sven" -> e instanceof net.minecraft.world.entity.animal.wolf.Wolf;
			default -> true;
		};
	}
	/** The boss mob a name tag floats over (not an armor stand or a player), of the kind that slayer's boss is. */
	static Entity mobUnder(Minecraft mc, Entity tag, String type) {
		Entity best = null; double bd = 99;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof LivingEntity) || e instanceof net.minecraft.world.entity.player.Player || e instanceof ArmorStand || !e.isAlive() || !bodyFits(e, type)) continue;
			double dy = tag.getY() - e.getY(); if (dy < -0.5 || dy > 4.5) continue;
			double dx = tag.getX() - e.getX(), dz = tag.getZ() - e.getZ(), h = dx * dx + dz * dz; if (h > 2.25) continue;
			if (h + dy * 0.1 < bd) { bd = h + dy * 0.1; best = e; }
		}
		return best;
	}
	/** Which of your clients (for this slayer) the boss is facing: the nearest one within 15° of where it looks, else 30°
	 *  (a young boss also 60°), within 15 blocks; a client who already has a boss alive isn't picked. */
	static String facing(Minecraft mc, Entity mob, String type, String tier) {
		Vec3 look = mob.getViewVector(1f).normalize(), eye = mob.getEyePosition();
		Set<String> busy = new HashSet<>(); for (Boss o : bosses.values()) if (o.owner != null && o.goneAt == 0 && System.currentTimeMillis() - o.seen < 2000) busy.add(o.owner.toLowerCase(Locale.ROOT));
		String best = null; double bestScore = 1e9; double[] cones = mob.tickCount <= 60 ? new double[]{15, 30, 60} : new double[]{15, 30};
		for (double cone : cones) {
			for (var p : mc.level.players()) {
				if (p.getUUID().version() != 4 || p.distanceToSqr(mob) > 225) continue;
				String name = p.getGameProfile().name();
				Carry c = find(name, "slayer"); if (c == null || (type != null && !type.equals(c.type)) || (tier != null && c.tier != null && !c.tier.equals(tier))) continue;
				if (busy.contains(name.toLowerCase(Locale.ROOT))) continue;
				Vec3 d = p.getEyePosition().subtract(eye); double len = d.length();
				double ang = len < 0.001 ? 0 : Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, look.dot(d.scale(1 / len))))));
				if (ang > cone) continue;
				double score = ang + len * 0.3; if (score < bestScore) { bestScore = score; best = c.player; }
			}
			if (best != null) return best;
		}
		return null;
	}
	/** A Tarantula's tag also disappears while it burrows: only treat it as dead if it vanished at low health. */
	static boolean burrowed(Boss b) { return "tara".equals(b.type) && !(b.firstHp > 0 && b.lastHp >= 0 && b.lastHp <= b.firstHp * 0.2); }
	/** A client's boss that died but isn't counted: a chat note with a [count it] button. */
	static void refuse(Carry c, String why, long now) {
		refused.put(c.player.toLowerCase(Locale.ROOT), now);
		Minecraft mc = Minecraft.getInstance(); if (mc.player == null) return;
		MutableComponent b = Component.literal("[count it]").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD).withStyle(st -> st
			.withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("gcounter claim " + c.player))
			.withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.literal("That boss was one of " + poss(c.player) + " " + c.label() + " carries: count it").withStyle(ChatFormatting.GRAY))));
		mc.player.sendSystemMessage(prefix().append(Component.literal(poss(c.player) + " boss died but wasn't counted: " + why + ". ").withStyle(ChatFormatting.GRAY)).append(b));
	}
	/** [count it] / /gcounter claim: counts the boss that wasn't counted (or +1 if there's none). */
	public static boolean claim(String player) {
		Carry c = find(player, "slayer"); if (c == null) c = find(player, null); if (c == null) return false;
		Long at = refused.remove(c.player.toLowerCase(Locale.ROOT));
		boolean fresh = at != null && System.currentTimeMillis() - at < 600_000;
		if (fresh) lastLoot.merge(c.player.toLowerCase(Locale.ROOT), at, Math::max);
		count(c, fresh ? " (claimed)" : " (claimed by hand)");
		return true;
	}
	public static boolean hasRefused(Carry c) { Long at = refused.get(c.player.toLowerCase(Locale.ROOT)); return at != null && System.currentTimeMillis() - at < 600_000; }

	// ---------- changes by hand (they post the new count in party chat too) ----------
	public static void plus(Carry c) { if (c.done >= c.total) return; count(c, " (added by hand)"); }
	public static void minus(Carry c) {
		if (c.done <= 0) return;
		c.done--;
		info(c.player + ": " + c.done + "/" + c.total + " " + c.label() + " (removed by hand)", ChatFormatting.AQUA);
		announceCount(c); save();
	}
	/** More or fewer carries ordered (never fewer than are done). */
	public static void setTotal(Carry c, int total) { if (total < 1) return; c.total = Math.max(total, c.done); save(); }

	// ---------- tick: scoreboard + boss name tags ----------
	public static void tick(Minecraft mc) {
		long now = System.currentTimeMillis();
		flush(false);
		if (mc.level == null || mc.player == null) return;
		if (mc.level != lastLevel) { lastLevel = mc.level; bosses.clear(); } // a new world: every tag vanished at once, nothing died
		if (now - lastScan >= 250) {
			lastScan = now;
			for (String line : sidebar(mc)) {
				Matcher m;
				if ((m = SB_FLOOR.matcher(line)).find()) dungeonFloor = m.group(1);
				if ((m = SB_KUUDRA.matcher(line)).find()) kuudraTier = m.group(1);
			}
		}
		drainAnnounce(mc, now);
		// boss name tags are read every tick (50 ms)
		boolean anySlayer = false; for (Carry c : D.carries) if (c.kind.equals("slayer")) anySlayer = true;
		if (!anySlayer || !D.auto) { bosses.clear(); return; }
		List<Entity> tags = new ArrayList<>(), owners = new ArrayList<>(); Map<Integer, String> ownerOf = new HashMap<>();
		Set<String> players = new HashSet<>(); for (var p : mc.level.players()) players.add(p.getGameProfile().name().toLowerCase(Locale.ROOT));
		for (Carry c : D.carries) players.add(c.player.toLowerCase(Locale.ROOT));
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof net.minecraft.world.entity.player.Player) continue;
			String n = Tags.plain(e); if (n == null || n.isEmpty()) continue; // armor stands, or the mob's own name
			boolean bossTag = BOSS_TAG.matcher(n).find() || JERRY_TAG.matcher(n).find();
			String own = bossTag ? null : ownerLine(n, players);
			if (own != null) { owners.add(e); ownerOf.put(e.getId(), own); } else if (bossTag) tags.add(e);
		}
		Set<Integer> alive = new HashSet<>(), usedTags = new HashSet<>();
		for (Entity o : owners) {
			String owner = ownerOf.get(o.getId());
			// the boss tag straight above or below it (Hypixel stacks the owner line with the boss's name)
			Entity tag = null; double bd = 1e9;
			for (Entity t : tags) { if (usedTags.contains(t.getId())) continue; double dx = t.getX() - o.getX(), dz = t.getZ() - o.getZ(), dy = Math.abs(t.getY() - o.getY()); double h = dx * dx + dz * dz; if (h > 2.25 || dy > 3.5) continue; double d = h + dy * dy * 0.25; if (d < bd) { bd = d; tag = t; } }
			if (tag == null) continue;
			usedTags.add(tag.getId()); anyOwnerLineAt = now; // this boss has its owner written next to it: never guessed to be anyone else's
			if (find(owner, "slayer") == null) continue; // a stranger's boss
			Boss b = bosses.computeIfAbsent(o.getId(), k -> { Boss nb = new Boss(); nb.spawnAt = now; return nb; });
			b.owner = owner; b.guessed = false;
			if (readTag(b, tag, now)) { b.pos = o.position(); alive.add(o.getId()); body(mc, b, tag, now); }
		}
		// the owner written into the boss's own tag ("Name's Voidgloom Seraph IV", "Name's Blue Jerry")
		for (Entity tag : tags) {
			if (usedTags.contains(tag.getId())) continue;
			String tt = Tags.plain(tag); Matcher om = OWNER_IN_TAG.matcher(tt); String owner = om.find() ? om.group(1) : null;
			if (owner == null) { Matcher jm = JERRY_TAG.matcher(tt); if (jm.find()) owner = jm.group(1); }
			if (owner == null) continue;
			usedTags.add(tag.getId()); anyOwnerLineAt = now; // its owner is in its own tag: never guessed to be anyone else's
			if (find(owner, "slayer") == null) continue; // a stranger's boss
			Boss b = bosses.computeIfAbsent(tag.getId(), k -> { Boss nb = new Boss(); nb.spawnAt = now; return nb; });
			b.owner = owner; b.guessed = false;
			if (readTag(b, tag, now)) { b.pos = tag.position(); alive.add(tag.getId()); body(mc, b, tag, now); }
		}
		// a boss tag with no owner next to it: whose is it? Bosses go for the player who spawned them, so the client it
		// faces (only your own clients count, so a stranger standing near can't be picked). While Hypixel writes owners
		// next to bosses (seen on any boss in the last 10 minutes) a boss without one isn't guessed at all: guessing only
		// stands in for owner names Hypixel doesn't show, and a guessed boss never counts by itself.
		boolean guessNow = now - lastGuess >= 200; if (guessNow) lastGuess = now;
		if (now - anyOwnerLineAt > 600_000) for (Entity tag : tags) {
			if (usedTags.contains(tag.getId())) continue;
			Boss b = bosses.get(tag.getId());
			if (b == null) {
				if (!guessNow) continue;
				Matcher tm = BOSS_TAG.matcher(Tags.plain(tag)); if (!tm.find()) continue;
				String type = BOSS_TYPE.get(tm.group(1)), tier = tm.group(2) == null ? "5" : String.valueOf(roman(tm.group(2)));
				Entity mob = mobUnder(mc, tag, type); if (mob == null || mob.tickCount > 100) continue;
				String owner = facing(mc, mob, type, tier); if (owner == null) continue;
				b = new Boss(); b.spawnAt = now; b.owner = owner; b.guessed = true; bosses.put(tag.getId(), b);
			}
			if (readTag(b, tag, now)) { b.pos = tag.position(); alive.add(tag.getId()); body(mc, b, tag, now); }
		}
		// bosses whose name tag disappeared: a death (or it despawned, or you walked away)
		Iterator<Map.Entry<Integer, Boss>> it = bosses.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, Boss> en = it.next(); Boss b = en.getValue();
			if (alive.contains(en.getKey())) { b.goneAt = 0; continue; }
			if (b.goneAt == 0) { b.goneAt = b.seen; died(mc, b, now); }
			if (now - b.goneAt > 8000) it.remove();
		}
	}
	/** Whether this client's slayer boss is up right now (the counter says so). */
	public static boolean bossUp(Carry c) {
		if (c == null || !c.kind.equals("slayer")) return false;
		long now = System.currentTimeMillis(); Long cnt = lastLoot.get(c.player.toLowerCase(Locale.ROOT));
		for (Boss b : bosses.values()) if (b.owner != null && b.owner.equalsIgnoreCase(c.player) && b.goneAt == 0 && now - b.seen < 1500 && !(cnt != null && b.spawnAt > cnt && now - cnt < GHOST_MS)) return true;
		return false;
	}

	/** The boss mob under a tracked tag, and whether it's playing its death animation (the surest sign it died). */
	static void body(Minecraft mc, Boss b, Entity tag, long now) {
		if (tag instanceof LivingEntity && !(tag instanceof ArmorStand)) b.mobId = tag.getId(); // the name is on the mob itself
		else if (b.mobId < 0 || !(mc.level.getEntity(b.mobId) instanceof LivingEntity)) { Entity m = mobUnder(mc, tag, b.type); if (m != null) b.mobId = m.getId(); }
		if (b.mobId >= 0 && mc.level.getEntity(b.mobId) instanceof LivingEntity le) {
			if (le.isDeadOrDying()) b.dyingAt = now;
			if (b.hitAt == 0 && le.hurtTime > 0) b.hitAt = now; // it flinched: the first hit (abilities too, not only left clicks)
		}
	}
	/** Whether a boss whose tag just vanished died: its death animation, or it was down to its last 8% (a Tarantula's
	 *  tag also vanishes while it burrows, so for it only the death animation or a low last read counts). */
	static boolean killed(Minecraft mc, Boss b, long now) {
		if (b.dyingAt > 0 && now - b.dyingAt < 3000) return true;
		if (b.mobId >= 0 && mc.level.getEntity(b.mobId) instanceof LivingEntity le && le.isDeadOrDying()) return true;
		return b.firstHp > 0 && b.lastHp >= 0 && b.lastHp <= b.firstHp * 0.08 && !burrowed(b);
	}
	/** A boss's tag vanished. If it died near you with a client's name on it: one boss for them, for the carry that
	 *  matches its type and tier. Each boss counts once (a tag Hypixel swaps on the same boss isn't a new one: a client
	 *  has only one boss at a time, so theirs must have appeared after their last count). */
	static void died(Minecraft mc, Boss b, long now) {
		if (b.owner == null || b.counted > 0 || b.type == null || b.pos == null) return;
		if (!killed(mc, b, now)) return; // gone with health left: despawned (quest failed, they died), or you walked off
		if (mc.player.position().distanceTo(b.pos) > 48) return;
		Carry booked = null, any = null;
		for (Carry k : D.carries) if (k.player.equalsIgnoreCase(b.owner) && k.kind.equals("slayer")) { if (any == null) any = k; if (b.type.equals(k.type) && (b.tier == null || b.tier.equals(k.tier))) { booked = k; break; } }
		if (any == null) return; // not one of your clients
		String lk = any.player.toLowerCase(Locale.ROOT); Long last = lastLoot.get(lk);
		if (last != null && b.spawnAt <= last + 500) { b.counted = last; return; } // the boss that already counted, under a new tag
		// the boss that just counted, its tags shown again for a moment as it dies: nobody spawns and kills another one within seconds
		if (last != null && now - last < GHOST_MS) { b.counted = last; return; }
		// a boss seen for an instant that never lost health in front of you: not a fight you saw (a tag flicker), never counted
		if (b.hitAt == 0 && b.seen - b.spawnAt < 2500) { b.counted = now; return; }
		if (b.guessed) { b.counted = now; refuse(any, "it had no owner name on it (it was only guessed to be theirs)", now); return; }
		if (booked == null) { b.counted = now; refuse(any, "it was a " + SLAYER_NAMES.getOrDefault(b.type, b.type) + " T" + b.tier + ", not their " + any.label(), now); return; }
		if (booked.done >= booked.total) return;
		b.counted = now; lastLoot.put(lk, now);
		count(booked, "");
	}
	/** Your left click on a mob next to a followed boss counts as the first damage (the health drop also does). */
	public static void onAttack(Entity target) {
		long now = System.currentTimeMillis();
		for (Boss b : bosses.values()) if (b.hitAt == 0 && b.pos != null && target.position().distanceTo(b.pos) < 4) b.hitAt = now;
	}
	/** /gcounter debug: what the counter can see right now (boss name tags, owner lines, followed bosses). */
	public static void debug() {
		Minecraft mc = Minecraft.getInstance(); if (mc.level == null || mc.player == null) return;
		List<Entity> named = new ArrayList<>();
		for (Entity e : mc.level.entitiesForRendering()) { if (e instanceof net.minecraft.world.entity.player.Player || e.distanceTo(mc.player) > 40) continue; String t = Tags.plain(e); if (t != null && !t.isEmpty()) named.add(e); }
		Set<String> pl = new HashSet<>(); for (var p : mc.level.players()) pl.add(p.getGameProfile().name().toLowerCase(Locale.ROOT));
		int n = 0;
		for (Entity b : named) {
			String t = Tags.plain(b); if (!BOSS_TAG.matcher(t).find()) continue;
			info("[boss] " + t, ChatFormatting.GREEN);
			for (Entity e : named) { if (e == b || e.distanceTo(b) > 5) continue; String nt = Tags.plain(e); String own = ownerLine(nt, pl); info("   near: \"" + nt + "\"" + (own != null ? " ← owner: " + own : ""), own != null ? ChatFormatting.AQUA : ChatFormatting.GRAY); }
			{ Matcher om = OWNER_IN_TAG.matcher(t); if (om.find()) info("   owner in the tag itself: " + om.group(1), ChatFormatting.AQUA); }
			if (++n >= 3) break;
		}
		if (n == 0) info("No slayer boss name within 40 blocks.", ChatFormatting.GRAY);
		for (Boss b : bosses.values()) info("Following " + poss(b.owner) + " " + SLAYER_NAMES.get(b.type) + " T" + b.tier + " hp " + (long) b.lastHp + "/" + (long) b.firstHp + (b.guessed ? " (guessed)" : ""), ChatFormatting.AQUA);
		info("Scoreboard: floor " + dungeonFloor + ", Kuudra tier " + kuudraTier + " · run started: dungeon " + dungeonStarted + ", Kuudra " + kuudraStarted, ChatFormatting.GRAY);
	}

	static int roman(String r) { return switch (r) { case "I" -> 1; case "II" -> 2; case "III" -> 3; case "IV" -> 4; case "V" -> 5; default -> 0; }; }
	// "134.5M/150M❤" (what it has left / what it started with, as an Inferno Demonlord's tag says it) or just "150M❤"
	static final Pattern HP_RE = Pattern.compile("([\\d.,]+)([kKmMbB]?)(?:\\s*/\\s*([\\d.,]+)([kKmMbB]?))?\\s*[❤\\ue000-\\uf8ff]");
	static double hpNum(String n, String unit) { double v; try { v = Double.parseDouble(n.replace(",", "")); } catch (Exception e) { return -1; } return switch (unit.toLowerCase(Locale.ROOT)) { case "k" -> v * 1e3; case "m" -> v * 1e6; case "b" -> v * 1e9; default -> v; }; }
	/** the health a tag says the boss started with ("…/150M❤"), or -1 when it only gives what is left */
	static double hpMax(String s) { Matcher m = HP_RE.matcher(s); return m.find() && m.group(3) != null ? hpNum(m.group(3), m.group(4)) : -1; } // ❤ or Hypixel's own-font heart glyph
	static double hp(String s) {
		Matcher m = HP_RE.matcher(s);
		if (!m.find()) return -1;
		return hpNum(m.group(1), m.group(2));
	}

	/** The sidebar's lines. */
	static List<String> sidebar(Minecraft mc) {
		List<String> out = new ArrayList<>();
		try {
			Scoreboard sb = mc.level.getScoreboard();
			Objective o = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
			if (o == null) return out;
			for (PlayerScoreEntry e : sb.listPlayerScores(o)) {
				PlayerTeam t = sb.getPlayersTeam(e.owner());
				String line = t == null ? e.ownerName().getString() : t.getPlayerPrefix().getString() + e.owner() + t.getPlayerSuffix().getString();
				out.add(strip(line));
			}
		} catch (Exception ignored) {}
		return out;
	}
	/** (the self-test's stand-in for the tab list) */
	static java.util.function.Supplier<Set<String>> presentTest = null;
	/** Names of the players in your current instance (tab list), lower-case. */
	static Set<String> present() {
		if (presentTest != null) return presentTest.get();
		Set<String> s = new HashSet<>();
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() == null) return s;
		for (PlayerInfo p : mc.getConnection().getOnlinePlayers()) {
			try { s.add(p.getProfile().name().toLowerCase(Locale.ROOT)); } catch (Exception ignored) {}
			try { if (p.getTabListDisplayName() != null) for (String w : strip(p.getTabListDisplayName().getString()).split("[^A-Za-z0-9_]+")) if (w.length() >= 3) s.add(w.toLowerCase(Locale.ROOT)); } catch (Exception ignored) {}
		}
		return s;
	}

	// ---------- dungeons and Kuudra ----------
	static void startMsg(String kind, String tier, Set<String> here) {
		for (Carry c : D.carries) if (c.kind.equals(kind) && here.contains(c.player.toLowerCase(Locale.ROOT)))
			progress((c.tier.equals(tier) ? "Run started for " : "Run started, but it isn't the booked " + c.label() + " for ") + c.player + (c.tier.equals(tier) ? " [" + c.label() + "]" : ""), c.tier.equals(tier) ? ChatFormatting.GRAY : ChatFormatting.GOLD);
	}
	static void dungeonWin() {
		String floor = dungeonFloor == null ? null : dungeonFloor.toLowerCase(Locale.ROOT);
		Set<String> now = present();
		for (Carry c : new ArrayList<>(D.carries)) {
			if (!c.kind.equals("dungeon")) continue;
			String p = c.player.toLowerCase(Locale.ROOT);
			if (!dungeonStart.contains(p) || !now.contains(p)) continue;
			if (floor == null || !floor.equals(c.tier)) { progress("Run finished for " + c.player + " but it was " + (floor == null ? "an unknown floor" : floor.toUpperCase(Locale.ROOT)) + ", not " + c.label() + ": not counted.", ChatFormatting.GOLD); continue; }
			count(c, "");
		}
	}
	static void kuudraWin() {
		Set<String> now = present();
		for (Carry c : new ArrayList<>(D.carries)) {
			if (!c.kind.equals("kuudra")) continue;
			String p = c.player.toLowerCase(Locale.ROOT);
			if (!kuudraStart.contains(p) || !now.contains(p)) continue;
			if (kuudraTier == null || !kuudraTier.equals(c.tier)) { progress("Kuudra finished for " + c.player + " but it was " + (kuudraTier == null ? "an unknown tier" : KUUDRA_NAMES[Integer.parseInt(kuudraTier)]) + ", not " + c.label() + ": not counted.", ChatFormatting.GOLD); continue; }
			count(c, "");
		}
	}

	// ---------- one more for a client ----------
	static void count(Carry c, String note) {
		long now = System.currentTimeMillis();
		if (c.started == 0) c.started = now;
		long took = c.last == 0 ? 0 : now - c.last; c.last = now;
		c.done++;
		if (c.done >= c.total) {
			progress("✔ Finished all " + c.total + " " + c.label() + " carries for " + c.player + " in " + dur(now - c.started) + note, ChatFormatting.GREEN);
			D.carries.remove(c);
		} else progress(c.player + ": " + c.done + "/" + c.total + " " + c.label() + (took > 0 ? " (" + dur(took) + ")" : "") + note, ChatFormatting.AQUA);
		announceCount(c);
		save();
	}

	/** The count in party chat (only if you turned it on): a normal party message, exactly like typing it yourself.
	 *  Messages are queued and sent at least 1.6 s apart, so two clients' bosses dying together post one after the other. */
	static final ArrayDeque<String> annQ = new ArrayDeque<>(); static long lastAnn = 0, annReady = 0, annSentAt = 0; static String annSent = null; static int annTries = 0;
	/** A count for the party chat. It waits a moment (said in the same instant as the kill, Hypixel can drop it as part of a
	 *  burst of commands), and it is checked: if the party chat hasn't shown it within a few seconds it is said once more. */
	static void announce(String text) { annQ.add(text); annReady = Math.max(annReady, System.currentTimeMillis() + 600); }
	static void drainAnnounce(Minecraft mc, long now) {
		if (mc.getConnection() == null) return;
		if (annSent != null) { // the one just said: seen in the party chat (heard() clears it), still waited for, said again, or given up
			if (now - annSentAt < 3500) return;
			if (sayAgain()) { annTries++; annSentAt = now; lastAnn = now; mc.getConnection().sendCommand("pc " + annSent); return; }
			if (annTries >= 2) info("The party chat never showed the count (said twice): " + annSent, ChatFormatting.GRAY);
			annSent = null;
		}
		if (annQ.isEmpty() || now < annReady || now - lastAnn < 1600) return;
		annSent = annQ.poll(); annTries = 1; annSentAt = now; lastAnn = now; mc.getConnection().sendCommand("pc " + annSent);
	}
	/** Said once and never shown: said again - with other clients' counts waiting behind it too - unless a newer count
	 *  for the same player is already waiting (that one says more). */
	static boolean sayAgain() {
		if (annSent == null || annTries >= 2) return false;
		int sp = annSent.indexOf(' '); String who = sp > 0 ? annSent.substring(0, sp + 1) : annSent;
		for (String q : annQ) if (q.startsWith(who)) return false;
		return true;
	}
	/** a chat line: the count just said, come back in the party chat - or Hypixel saying why it can't be said */
	static void heard(String s) {
		if (annSent == null) return;
		if (s.contains(annSent) || s.contains("not in a party") || s.contains("not currently in a party") || s.contains("same message twice") || s.contains("muted")) annSent = null;
	}
	static void announceCount(Carry c) { if (D.announce) announce(c.player + " " + c.done + "/" + c.total + " - Grimoire"); }

	// ---------- words in your own chat ----------
	static MutableComponent prefix() { return Component.literal("[Grimoire Counter] ").withStyle(ChatFormatting.LIGHT_PURPLE); }
	/** a progress line (only with "Progress lines in chat" on) */
	static void progress(String s, ChatFormatting col) { if (D.chatLines) info(s, col); }
	public static void info(String s, ChatFormatting col) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		mc.player.sendSystemMessage(prefix().append(Component.literal(s).withStyle(col)));
	}
	public static String dur(long ms) { long s = ms / 1000; return s >= 3600 ? (s / 3600) + "h " + (s % 3600 / 60) + "m" : s >= 60 ? (s / 60) + "m " + (s % 60) + "s" : s + "s"; }
	/** 1.3M · 650k · 12.5B */
	public static String coins(double v) {
		if (v >= 1e9) return trim(v / 1e9) + "B"; if (v >= 1e6) return trim(v / 1e6) + "M"; if (v >= 1e3) return trim(v / 1e3) + "k"; return String.valueOf((long) v);
	}
	static String trim(double d) { String s = String.format(Locale.ROOT, "%.2f", d); return s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s; }
}

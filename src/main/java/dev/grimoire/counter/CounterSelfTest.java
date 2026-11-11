/*
 * Grimoire Counter - Copyright (c) 2026 Yggdrasjll. All rights reserved.
 * Not to be copied, modified, decompiled, reused or shared without Yggdrasjll's written permission. See LICENSE.txt.
 */
package dev.grimoire.counter;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * The counter's check of its own rules, run only by its author with -Dgrimoire.counter.selftest=1 (never in play):
 * the chat lines of a dungeon run and a Kuudra run, the wrong floor, a client who isn't there, a failed run, the words
 * of an owner line and a boss tag, a price list. It works on a counter of its own: nothing of yours is read or saved.
 */
final class CounterSelfTest {
	private CounterSelfTest() {}
	static int t = 0; static final List<String> fails = new ArrayList<>(); static int checks = 0;
	static void check(String what, boolean ok) { checks++; if (!ok) fails.add(what); }
	static void say(String s) { System.out.println("COUNTERTEST " + s); }

	static void tick(Minecraft mc) {
		if (!(mc.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen) && !(mc.gui.screen() instanceof CounterScreen) && t < 60) return; // (once the game has finished loading)
		t++;
		if (t == 60) { try { rules(); } catch (Throwable e) { fails.add("crashed: " + e); } say(fails.isEmpty() ? "rules OK (" + checks + " checks)" : "rules FAILED: " + fails); }
		if (t == 140) { sample(); mc.gui.setScreen(new CounterScreen()); say("SHOT page"); }
		if (t == 200 && mc.gui.screen() instanceof CounterScreen cs) { cs.scroll = 9999; say("SHOT prices"); }
		if (t == 260 && mc.gui.screen() instanceof CounterScreen cs) { cs.moving = true; say("SHOT move"); }
		if (t == 320) { mc.gui.setScreen(null); say("done"); }
	}
	/** a counter of its own, in memory */
	static void fresh() { Counter.D = new Counter.Data(); Counter.D.chatLines = false; Counter.dungeonStarted = false; Counter.kuudraStarted = false; Counter.dungeonFloor = null; Counter.kuudraTier = null; Counter.dirtyAt = 0; }
	static int done(String p) { Counter.Carry c = Counter.find(p, null); return c == null ? -1 : c.done; }

	static void rules() {
		Counter.presentTest = () -> Set.of("alex", "steve");
		// --- what a carry is
		check("parse eman 4", java.util.Arrays.equals(Counter.parse("eman", "4"), new String[]{"slayer", "void", "4"}));
		check("parse inferno t3", java.util.Arrays.equals(Counter.parse("inferno", "t3"), new String[]{"slayer", "blaze", "3"}));
		check("parse kuudra infernal", java.util.Arrays.equals(Counter.parse("kuudra", "infernal"), new String[]{"kuudra", "kuudra", "5"}));
		check("parse dungeon m7", java.util.Arrays.equals(Counter.parse("dungeon", "M7"), new String[]{"dungeon", "dungeon", "m7"}));
		check("parse nonsense", Counter.parse("eman", "9") == null && Counter.parse("cow", "1") == null && Counter.parse("dungeon", "f8") == null);
		// --- a dungeon run: counted only when it started with the client there, on the booked floor, and was won
		fresh(); Counter.add("Alex", Counter.parse("dungeon", "f7"), 3); Counter.add("Bob", Counter.parse("dungeon", "f7"), 3); Counter.add("Steve", Counter.parse("dungeon", "m5"), 2);
		Counter.dungeonFloor = "F7";
		Counter.handle("[NPC] Mort: Here, I found this map when I first entered the dungeon.");
		Counter.handle("The Catacombs - Floor VII"); Counter.handle("☠ Defeated Necron in 05m 12s");
		check("f7 win counts Alex", done("Alex") == 1); check("f7 win: Bob wasn't there", done("Bob") == 0); check("f7 win isn't Steve's m5", done("Steve") == 0);
		Counter.handle("☠ Defeated Necron in 05m 12s"); check("the same end screen twice counts once", done("Alex") == 1);
		Counter.handle("[NPC] Mort: Here, I found this map when I first entered the dungeon."); Counter.handle("The Catacombs - Floor VII"); // a run that ends without "Defeated": failed
		check("a failed run counts nothing", done("Alex") == 1);
		Counter.dungeonStarted = false; Counter.handle("The Catacombs - Floor VII"); Counter.handle("☠ Defeated Necron in 04m 01s"); check("an end screen without a start counts nothing", done("Alex") == 1);
		Counter.dungeonFloor = "M5"; Counter.handle("[NPC] Mort: Here, I found this map when I first entered the dungeon."); Counter.handle("Master Mode The Catacombs - Floor V"); Counter.handle("☠ Defeated Livid in 03m 40s");
		check("m5 win counts Steve", done("Steve") == 1); check("m5 win isn't Alex's f7", done("Alex") == 1);
		// --- Kuudra
		fresh(); Counter.add("Alex", Counter.parse("kuudra", "infernal"), 2); Counter.add("Steve", Counter.parse("kuudra", "basic"), 2);
		Counter.kuudraTier = "5"; Counter.handle("[NPC] Elle: Okay adventurers, I will go and fish up Kuudra!"); Counter.handle("  DEFEAT  ");
		check("a Kuudra defeat counts nothing", done("Alex") == 0);
		Counter.handle("  KUUDRA DOWN!  "); check("KUUDRA DOWN without a start counts nothing", done("Alex") == 0);
		Counter.handle("[NPC] Elle: Okay adventurers, I will go and fish up Kuudra!"); Counter.handle("                               KUUDRA DOWN!");
		check("Infernal down counts Alex", done("Alex") == 1); check("Infernal down isn't Steve's Basic", done("Steve") == 0);
		Counter.handle("[NPC] Elle: Okay adventurers, I will go and fish up Kuudra!"); Counter.handle("KUUDRA DOWN!");
		check("the last one finishes the carry", Counter.find("Alex", null) == null);
		// --- by hand
		fresh(); Counter.Carry c = Counter.add("Alex", Counter.parse("void", "4"), 3); Counter.plus(c); Counter.plus(c); Counter.minus(c); check("plus plus minus = 1", c.done == 1);
		Counter.setTotal(c, 0); check("never fewer than one ordered", c.total == 3); Counter.add("Alex", Counter.parse("void", "4"), 2); check("more of the same adds up", c.total == 5);
		Counter.add("Alex", Counter.parse("void", "3"), 2); check("another tier replaces it", Counter.find("Alex", "slayer").tier.equals("3") && Counter.D.carries.size() == 1);
		Counter.D.auto = false; Counter.add("Alex", Counter.parse("dungeon", "f7"), 1); Counter.dungeonFloor = "F7"; Counter.handle("[NPC] Mort: Here, I found this map when I first entered the dungeon."); Counter.handle("The Catacombs - Floor VII"); Counter.handle("☠ Defeated Necron in 05m 12s");
		check("counting by itself off: nothing counts", done("Alex") >= 0 && Counter.find("Alex", "dungeon").done == 0);
		// --- the words on a slayer boss
		Set<String> here = Set.of("alex");
		check("owner: Spawned by", "Alex".equals(Counter.ownerLine("Spawned by: Alex", here)) && "Alex".equals(Counter.ownerLine("Spawned by: [MVP+] Alex", here)));
		check("owner: Name's SLAYER BOSS", "Alex".equals(Counter.ownerLine("Alex's SLAYER BOSS", here)) && "Tutmozes".equals(Counter.ownerLine("Tutmozes' SLAYER BOSS", here)));
		check("owner: a name alone only if they're here", "Alex".equals(Counter.ownerLine("Alex", here)) && Counter.ownerLine("Zed", here) == null);
		check("not an owner line", Counter.ownerLine("Voidgloom Seraph IV 30M❤", here) == null && Counter.ownerLine("LOOT SHARE", here) == null);
		var m = Counter.BOSS_TAG.matcher("☠ Voidgloom Seraph IV 12.5M❤"); check("boss tag", m.find() && m.group(1).equals("Voidgloom Seraph") && m.group(2).equals("IV") && Counter.hp(m.group(3)) == 12.5e6);
		m = Counter.BOSS_TAG.matcher("Inferno Demonlord III 150k❤"); check("boss tag 2", m.find() && Counter.BOSS_TYPE.get(m.group(1)).equals("blaze") && Counter.roman(m.group(2)) == 3 && Counter.hp(m.group(3)) == 150_000);
		m = Counter.OWNER_IN_TAG.matcher("☠ Alex's Voidgloom Seraph IV 30M❤"); check("owner in the tag", m.find() && m.group(1).equals("Alex"));
		check("health", Counter.hp("2,400❤") == 2400 && Counter.hp("1.2B❤") == 1.2e9 && Counter.hp("no health") == -1);
		// --- prices
		fresh(); Counter.D.prices = new LinkedHashMap<>(); Counter.D.prices.put("void|4", "1.3, 2.3; 2m");
		check("price list", Counter.priceList("void|4").equals(List.of(1.3, 2.3, 2.0)) && Counter.priceList("void|3").isEmpty());
		c = Counter.add("Alex", Counter.parse("void", "4"), 10); check("unit price", Counter.unitPrice(c) == 1.3e6);
		check("coins", Counter.coins(13e6).equals("13M") && Counter.coins(650_000).equals("650k") && Counter.coins(1.25e9).equals("1.25B"));
		check("party words", (c.player + " " + c.done + "/" + c.total + " - Grimoire").equals("Alex 0/10 - Grimoire"));
		// your own bosses: you as the client, counted from "SLAYER QUEST COMPLETE!" in chat
		fresh(); String me = net.minecraft.client.Minecraft.getInstance().getUser().getName(); Counter.lastLoot.clear();
		Counter.add(me, Counter.parse("void", "4"), 3); Counter.handle("  SLAYER QUEST COMPLETE!"); check("own boss from chat", done(me) == 1);
		Counter.handle("  SLAYER QUEST COMPLETE!"); check("own boss: the same one isn't counted twice", done(me) == 1);
		Counter.lastLoot.clear(); Counter.D.self = false; Counter.handle("  SLAYER QUEST COMPLETE!"); check("own bosses switched off", done(me) == 1); Counter.D.self = true;
		Counter.lastLoot.clear(); Counter.handle("Alex: SLAYER QUEST COMPLETE!"); check("a player saying it isn't it", done(me) == 1);
		Counter.presentTest = null;
	}
	/** a few carries to look at on the page */
	static void sample() {
		fresh(); Counter.D.chatLines = true; Counter.D.prices.put("void|4", "1.3, 2.3"); Counter.D.prices.put("blaze|3", "3.5, 3"); Counter.D.prices.put("dungeon|f7", "0.8"); Counter.D.prices.put("kuudra|5", "12");
		Counter.Carry a = Counter.add("Alex", Counter.parse("void", "4"), 10); a.done = 3; Counter.Carry b = Counter.add("Steve", Counter.parse("dungeon", "f7"), 5); b.done = 4; Counter.add("Efe", Counter.parse("kuudra", "infernal"), 2);
		Counter.dirtyAt = 0; // (never written: these aren't yours)
	}
}

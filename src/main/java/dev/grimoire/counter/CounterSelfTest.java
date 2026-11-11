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
		// an Inferno Demonlord's tag gives what it has left and what it started with
		check("health left / total", Counter.hp("134.5M/150M❤") == 134.5e6 && Counter.hpMax("134.5M/150M❤") == 150e6 && Counter.hpMax("30M❤") == -1 && Counter.hp("2,400/3k❤") == 2400);
		m = Counter.BOSS_TAG.matcher("☠ Inferno Demonlord IV 134.5M/150M❤"); check("inferno tag with both", m.find() && Counter.hp(m.group(3)) == 134.5e6 && Counter.roman(m.group(2)) == 4);
		// your own boss: the spawn line says which it was; another tier than the one being counted isn't counted
		fresh(); Counter.lastLoot.clear(); Counter.add(me, Counter.parse("blaze", "4"), 5);
		Counter.handle("  SLAYER BOSS! The Inferno Demonlord IV spawned!"); Counter.handle("  SLAYER QUEST COMPLETE!"); check("own Inferno T4 from the spawn line", done(me) == 1);
		Counter.lastLoot.clear(); Counter.handle("  SLAYER BOSS! The Inferno Demonlord III spawned!"); Counter.handle("  SLAYER QUEST COMPLETE!"); check("own boss of another tier isn't counted", done(me) == 1);
		Counter.lastLoot.clear(); Counter.handle("Party > [MVP+] Alex: SLAYER BOSS! The Inferno Demonlord II spawned!"); Counter.handle("  SLAYER QUEST COMPLETE!"); check("a player's words aren't the spawn line", done(me) == 2);
		// the party message: queued, said once, cleared when the party chat shows it
		Counter.annQ.clear(); Counter.annSent = null; Counter.D.announce = true; Counter.announceCount(Counter.find(me, "slayer")); check("party message queued", Counter.annQ.size() == 1 && Counter.annQ.peek().equals(me + " 2/5 - Grimoire"));
		Counter.annSent = Counter.annQ.poll(); Counter.handle("Party > [MVP+] " + me + ": " + me + " 2/5 - Grimoire"); check("seen in the party chat: done with it", Counter.annSent == null); Counter.D.announce = false;
		Counter.annQ.clear(); Counter.annSent = "Alex 2/5 - Grimoire"; Counter.annTries = 1; check("never shown: said again", Counter.sayAgain());
		Counter.annQ.add("Steve 4/10 - Grimoire"); check("said again with another client's count waiting", Counter.sayAgain());
		Counter.annQ.add("Alex 3/5 - Grimoire"); check("not said again when the same player's newer count waits", !Counter.sayAgain());
		Counter.annQ.clear(); Counter.annTries = 2; check("said twice: not a third time", !Counter.sayAgain()); Counter.annSent = null; Counter.annTries = 0;
		check("kill time in tenths", Counter.secs(21340).equals("21.3s") && Counter.secs(65230).equals("1m 05.2s"));
		Counter.Carry kl = new Counter.Carry(); kl.player = "Alex"; kl.kind = "slayer"; kl.type = "void"; kl.tier = "4";
		check("kill line: both", Counter.killLine(kl, 25400, 21300).equals("⏱ Alex's Voidgloom T4: 25.4s since spawn · 21.3s since first hit"));
		check("kill line: spawn only", Counter.killLine(kl, 25400, 0).equals("⏱ Alex's Voidgloom T4: 25.4s since spawn · first hit not seen"));
		check("kill line: hit only", Counter.killLine(kl, 0, 21300).equals("⏱ Alex's Voidgloom T4: spawn not seen · 21.3s since first hit"));
		fresh(); Counter.lastLoot.clear(); Counter.bosses.clear(); Counter.Carry kc = Counter.add(me, Counter.parse("blaze", "4"), 5);
		Counter.handle("  SLAYER BOSS! The Inferno Demonlord IV spawned!"); Counter.ownSpawnAt -= 30_000; Counter.handle("  SLAYER QUEST COMPLETE!");
		check("own boss: timed from the spawn line", done(me) == 1 && kc.lastKill != null && kc.lastKill.startsWith("30."));
		Counter.lastLoot.clear(); Counter.D.killTime = false; kc.lastKill = null; Counter.handle("  SLAYER BOSS! The Inferno Demonlord IV spawned!"); Counter.ownSpawnAt -= 30_000; Counter.handle("  SLAYER QUEST COMPLETE!");
		check("kill time switched off: counted, not timed", done(me) == 2 && kc.lastKill == null); Counter.D.killTime = true;
		fresh(); Counter.lastLoot.clear(); Counter.bosses.clear(); Counter.Carry dc = Counter.add("Alex", Counter.parse("void", "4"), 5);
		Counter.handle(" ☠ Alex was killed by Voidgloom Seraph."); check("client killed by their boss: warned", dc.deaths == 1 && dc.diedAt > 0 && done("Alex") == 0);
		Counter.handle(" ☠ Alex was killed by Voidgloom Seraph."); check("the same death isn't warned twice", dc.deaths == 1);
		dc.diedAt = 0; Counter.handle(" ☠ Alex fell into the void."); check("a death with no boss up isn't one", dc.deaths == 1);
		Counter.Boss ub = new Counter.Boss(); ub.owner = "Alex"; ub.type = "void"; ub.tier = "4"; ub.seen = System.currentTimeMillis(); ub.spawnAt = ub.seen - 9000; Counter.bosses.put(-77, ub);
		Counter.handle(" ☠ Alex fell into the void."); check("any death with their boss up: warned", dc.deaths == 2); Counter.bosses.clear();
		dc.diedAt = 0; Counter.handle(" ☠ Steve was killed by Voidgloom Seraph."); check("someone else's death isn't the client's", dc.deaths == 2);
		Counter.handle("Party > [MVP+] Alex: ☠ Alex was killed by Voidgloom Seraph."); check("a player typing a death line isn't one", dc.deaths == 2);
		// the client dies after their boss did
		dc.diedAt = 0; Counter.lastLoot.put("alex", System.currentTimeMillis()); Counter.handle(" ☠ Alex was killed by Voidgloom Seraph."); check("died after their boss counted: no warning", dc.deaths == 2);
		Counter.lastLoot.clear(); Counter.Boss db = new Counter.Boss(); db.owner = "Alex"; db.type = "void"; db.tier = "4"; db.seen = System.currentTimeMillis(); db.spawnAt = db.seen - 9000; db.dyingAt = db.seen; Counter.bosses.put(-78, db);
		Counter.handle(" ☠ Alex burned to death."); check("died while their boss was dying: held back", dc.deaths == 2 && Counter.dyingDeaths.containsKey("alex"));
		Counter.lastLoot.put("alex", System.currentTimeMillis()); Counter.deathTick(System.currentTimeMillis() + 2000); check("that boss counted: never warned", dc.deaths == 2 && Counter.dyingDeaths.isEmpty());
		Counter.lastLoot.clear(); Counter.handle(" ☠ Alex burned to death."); Counter.deathTick(System.currentTimeMillis() + 2000); check("that boss didn't count: warned after all", dc.deaths == 3);
		Counter.bosses.clear(); int before = done("Alex"); check("[count 1 anyway] counts one", Counter.claim("Alex") && done("Alex") == before + 1);
		Counter.annQ.clear(); Counter.annSent = null; Counter.D.announce = false; Counter.D.deathMsg = "you died, counted anyway"; dc.diedAt = 0; Counter.lastLoot.clear();
		Counter.handle(" ☠ Alex was killed by Voidgloom Seraph."); before = done("Alex"); Counter.claim("Alex");
		check("your words, then the count", done("Alex") == before + 1 && Counter.annQ.size() == 1 && Counter.annQ.peek().equals("you died, counted anyway / Alex " + (before + 1) + "/5 - Grimoire"));
		Counter.annQ.clear(); Counter.claim("Alex"); check("a count by hand that isn't a death: no words", Counter.annQ.isEmpty());
		Counter.D.deathMsg = ""; dc.diedAt = 0; Counter.lastLoot.clear(); Counter.handle(" ☠ Alex was killed by Voidgloom Seraph."); Counter.claim("Alex"); check("no words set and party count off: nothing said", Counter.annQ.isEmpty());
		Counter.annSent = "gg / Alex 2/5 - Grimoire"; Counter.annTries = 1; Counter.annQ.add("Alex 3/5 - Grimoire"); check("your words in front: still known as Alex's count", !Counter.sayAgain()); Counter.annQ.clear(); Counter.annSent = null; Counter.annTries = 0;
		// the text bars: typing, select all, copy, cut, paste, the arrows, Backspace and Delete
		CounterScreen cs = new CounterScreen(); CounterScreen.addName = ""; CounterScreen.addAmount = "1"; cs.focusOn("name");
		int CTRL = org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL, SHIFT = org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT;
		for (char ch : "Al ex!".toCharArray()) cs.charTyped(new net.minecraft.client.input.CharacterEvent(ch));
		check("typing takes only what a name takes", CounterScreen.addName.equals("Alex"));
		cs.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_A, 0, CTRL)); check("Ctrl+A selects everything", cs.anchor == 0 && cs.caret == 4);
		cs.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_C, 0, CTRL)); check("Ctrl+C copies it", "Alex".equals(net.minecraft.client.Minecraft.getInstance().keyboardHandler.getClipboard()));
		cs.charTyped(new net.minecraft.client.input.CharacterEvent('B')); check("typing replaces the selection", CounterScreen.addName.equals("B"));
		cs.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, CTRL)); check("Ctrl+V pastes at the caret", CounterScreen.addName.equals("BAlex"));
		cs.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_HOME, 0, 0)); cs.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT, 0, SHIFT)); check("Shift+arrow selects", cs.anchor == 0 && cs.caret == 1);
		cs.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_X, 0, CTRL)); check("Ctrl+X cuts", CounterScreen.addName.equals("Alex") && "B".equals(net.minecraft.client.Minecraft.getInstance().keyboardHandler.getClipboard()));
		cs.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE, 0, 0)); cs.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_END, 0, 0)); cs.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE, 0, 0));
		check("Delete and Backspace", CounterScreen.addName.equals("le"));
		cs.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_TAB, 0, 0)); check("Tab goes to the count, all selected", "amount".equals(cs.focus) && cs.anchor == 0 && cs.caret == 1);
		net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard("1a2b3c4d5"); cs.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, CTRL)); check("pasting into the count keeps only digits, four at most", CounterScreen.addAmount.equals("1234"));
		cs.focusOn("void|4"); cs.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_A, 0, CTRL)); for (char ch : "1.5, 2".toCharArray()) cs.charTyped(new net.minecraft.client.input.CharacterEvent(ch)); check("a price bar the same way", Counter.D.prices.get("void|4").equals("1.5, 2"));
		CounterScreen.addName = ""; CounterScreen.addAmount = "1";
		Counter.presentTest = null;
	}
	/** a few carries to look at on the page */
	static void sample() {
		fresh(); Counter.D.chatLines = true; Counter.D.prices.put("void|4", "1.3, 2.3"); Counter.D.prices.put("blaze|3", "3.5, 3"); Counter.D.prices.put("dungeon|f7", "0.8"); Counter.D.prices.put("kuudra|5", "12");
		Counter.Carry a = Counter.add("Alex", Counter.parse("void", "4"), 10); a.done = 3; Counter.Carry b = Counter.add("Steve", Counter.parse("dungeon", "f7"), 5); b.done = 4; Counter.add("Efe", Counter.parse("kuudra", "infernal"), 2);
		Counter.dirtyAt = 0; // (never written: these aren't yours)
	}
}

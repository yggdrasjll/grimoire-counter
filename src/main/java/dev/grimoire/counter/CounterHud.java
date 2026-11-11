/*
 * Grimoire Counter - Copyright (c) 2026 Yggdrasjll. All rights reserved.
 * Not to be copied, modified, decompiled, reused or shared without Yggdrasjll's written permission. See LICENSE.txt.
 */
package dev.grimoire.counter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/** The counter on screen: each client, their carry, how many are done, a bar. */
final class CounterHud {
	private CounterHud() {}
	static final int BORDER = 0xFF2A3A62, GILT = 0xFFFFD45C, TEXT = 0xFFEFE8D4, MUTED = 0xFF93A3BD, BAR = 0xFF16264E, GREEN = 0xFF7FCF6A, CYAN = 0xFF8CF0C8, ORANGE = 0xFFFFA238;

	/** Draws at the saved place; returns {width, height} before scaling. preview: with an example row when there is no carry. */
	static int[] draw(GuiGraphicsExtractor g, boolean preview) {
		Minecraft mc = Minecraft.getInstance(); Font f = mc.font; var D = Counter.D;
		List<Counter.Carry> rows = D.carries;
		if (rows.isEmpty() && !preview) return new int[]{0, 0};
		if (rows.isEmpty()) { rows = new ArrayList<>(); Counter.Carry c = new Counter.Carry(); c.player = "Client"; c.kind = "slayer"; c.type = "void"; c.tier = "4"; c.total = 10; c.done = 3; rows.add(c); }
		int w = f.width("Grimoire · Counter") + 16;
		for (Counter.Carry c : rows) w = Math.max(w, f.width(c.player + "  " + c.label() + "  " + c.done + "/" + c.total + "  SPAWNED") + 22);
		int h = 16 + rows.size() * 16 + 2;
		g.pose().pushMatrix();
		g.pose().translate(D.hudX, D.hudY); g.pose().scale(D.hudScale, D.hudScale);
		g.fill(0, 0, w, h, 0xD0081024); g.outline(0, 0, w, h, BORDER); g.fill(0, 0, 2, h, GILT);
		g.text(f, "Grimoire", 7, 4, GILT, true); g.text(f, " · Counter", 7 + f.width("Grimoire"), 4, MUTED, true);
		int y = 16;
		for (Counter.Carry c : rows) {
			int x = 7;
			g.text(f, c.player, x, y, CYAN, true); x += f.width(c.player) + 6;
			g.text(f, c.label(), x, y, MUTED, true); x += f.width(c.label()) + 6;
			if (Counter.bossUp(c)) g.text(f, "SPAWNED", x, y, ORANGE, true); // their boss is up
			String cnt = c.done + "/" + c.total;
			g.text(f, cnt, w - 7 - f.width(cnt), y, c.done >= c.total ? GREEN : TEXT, true);
			int bw = w - 14; g.fill(7, y + 10, 7 + bw, y + 12, BAR);
			g.fill(7, y + 10, 7 + (int) (bw * Math.min(1, c.total == 0 ? 0 : (double) c.done / c.total)), y + 12, GREEN);
			y += 16;
		}
		g.pose().popMatrix();
		return new int[]{w, h};
	}
}

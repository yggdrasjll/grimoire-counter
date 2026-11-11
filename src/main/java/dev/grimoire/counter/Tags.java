/*
 * Grimoire Counter - Copyright (c) 2026 Yggdrasjll. All rights reserved.
 * Not to be copied, modified, decompiled, reused or shared without Yggdrasjll's written permission. See LICENSE.txt.
 */
package dev.grimoire.counter;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;

/** The text floating over an entity (its custom name: armor stands, mobs), without colour codes. */
final class Tags {
	private Tags() {}
	/** per entity: the name it had and its plain text, so a name that didn't change isn't read again (game thread only) */
	private static final HashMap<Integer, Object[]> PLAIN = new HashMap<>(); private static long prunedAt = 0;
	static String plain(Entity e) {
		if (!e.hasCustomName()) return null;
		Component c = e.getCustomName(); if (c == null) return null;
		Object[] k = PLAIN.get(e.getId()); if (k != null && k[0] == c) return (String) k[1];
		String p = ChatFormatting.stripFormatting(c.getString()); String s = (p == null ? "" : p).replace(' ', ' ').trim();
		PLAIN.put(e.getId(), new Object[]{c, s});
		long now = System.currentTimeMillis(); if (PLAIN.size() > 4000 && now - prunedAt > 10_000) { prunedAt = now; PLAIN.clear(); }
		return s;
	}
}

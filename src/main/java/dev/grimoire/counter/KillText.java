/*
 * Grimoire - Copyright (c) 2026 Yggdrasjll. All rights reserved.
 * Not to be copied, modified, decompiled, reused or shared without Yggdrasjll's written permission. See LICENSE.txt.
 */
package dev.grimoire.counter;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * A floating "21.3s" where a carry boss died, for a few seconds. It's a text display that exists only on your screen
 * (added to your own client world, never sent to the server) and can't be clicked or hit.
 */
public final class KillText {
	private KillText() {}
	record Shown(Entity e, long until, Vec3 base, long born) {}
	static final List<Shown> shown = new ArrayList<>();
	static int nextId = Integer.MIN_VALUE + 1000;

	static Object get(Class<?> c, String f) throws Exception { Field x = c.getDeclaredField(f); x.setAccessible(true); return x.get(null); }
	@SuppressWarnings("unchecked")
	static <T> void set(Entity e, Class<?> c, String f, T v) throws Exception { e.getEntityData().set((EntityDataAccessor<T>) get(c, f), v); }

	static Vec3 lastAt = null; static long lastShown = 0;
	public static void show(Vec3 at, String text) {
		Minecraft mc = Minecraft.getInstance(); if (mc.level == null || !Counter.D.killTime || !Counter.D.killText) return;
		long t0 = System.currentTimeMillis(); if (lastAt != null && t0 - lastShown < 2500 && lastAt.distanceTo(at) < 4) return; // the same boss, already labelled
		lastAt = at; lastShown = t0;
		try {
			Display.TextDisplay d = new Display.TextDisplay(net.minecraft.world.entity.EntityTypes.TEXT_DISPLAY, mc.level);
			d.setId(nextId++);
			Vec3 p = at.add(0, 0.6, 0); d.setPos(p.x, p.y, p.z);
			int col = 0xAA00AA;
			Method st = Display.TextDisplay.class.getDeclaredMethod("setText", Component.class); st.setAccessible(true);
			st.invoke(d, Component.literal("⏱ " + text).withStyle(s -> s.withColor(col).withBold(true)));
			Method bb = Display.class.getDeclaredMethod("setBillboardConstraints", Display.BillboardConstraints.class); bb.setAccessible(true);
			bb.invoke(d, Display.BillboardConstraints.CENTER); // always faces you
			set(d, Display.class, "DATA_SCALE_ID", new org.joml.Vector3f(1.8f, 1.8f, 1.8f));
			set(d, Display.class, "DATA_BRIGHTNESS_OVERRIDE_ID", (15 << 4) | (15 << 20)); // full bright, readable at night
			set(d, Display.TextDisplay.class, "DATA_BACKGROUND_COLOR_ID", 0x60000000);
			set(d, Display.TextDisplay.class, "DATA_STYLE_FLAGS_ID", (byte) 1); // text shadow
			mc.level.addEntity(d);
			long now = System.currentTimeMillis();
			shown.add(new Shown(d, now + 3000, p, now));
		} catch (Throwable ignored) {}
	}

	public static int debugCount() { return shown.size(); }
	/** Every tick: drift up a little, remove when the time is up. */
	public static void tick() {
		Minecraft mc = Minecraft.getInstance(); long now = System.currentTimeMillis();
		for (int i = shown.size() - 1; i >= 0; i--) {
			Shown s = shown.get(i);
			if (now >= s.until() || mc.level == null || s.e().level() != mc.level) { if (mc.level != null && s.e().level() == mc.level) mc.level.removeEntity(s.e().getId(), Entity.RemovalReason.DISCARDED); shown.remove(i); continue; }
			double up = Math.min(0.6, (now - s.born()) / 1000.0 * 0.25);
			s.e().setPos(s.base().x, s.base().y + up, s.base().z);
		}
	}
}

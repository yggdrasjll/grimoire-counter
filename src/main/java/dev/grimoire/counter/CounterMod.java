/*
 * Grimoire Counter - Copyright (c) 2026 Yggdrasjll. All rights reserved.
 * Not to be copied, modified, decompiled, reused or shared without Yggdrasjll's written permission. See LICENSE.txt.
 */
package dev.grimoire.counter;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * Grimoire Counter: Grimoire's carry counter as a mod of its own. It counts slayer bosses, dungeon runs and Kuudra
 * runs for each client only when Hypixel itself confirms them, keeps your price list, lets you count by hand on its
 * page (/gcounter, or its key) and can say the count in party chat. Display only: it never clicks, aims or moves.
 * With Grimoire itself installed it stays off: Grimoire already has the counter.
 */
public final class CounterMod implements ClientModInitializer {
	/** false with Grimoire installed (its own counter does the work; two would count and announce everything twice) */
	public static boolean active = true;
	/** the mod's own check of its rules, run by its author (-Dgrimoire.counter.selftest=1); never on in play */
	static final boolean SELFTEST = System.getProperty("grimoire.counter.selftest") != null;

	@Override public void onInitializeClient() {
		active = !FabricLoader.getInstance().isModLoaded("grimoire");
		net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(commands()));
		if (!active) return;
		KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("grimoire_counter", "main"));
		KeyMapping open = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.grimoire_counter.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, cat));
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			try { Counter.tick(mc); } catch (Throwable ignored) {}
			if (SELFTEST) CounterSelfTest.tick(mc);
			while (open.consumeClick()) if (mc.gui.screen() == null) mc.gui.setScreen(new CounterScreen());
		});
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> Counter.flush(true));
		net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((m, o) -> { try { Counter.onChat(m, o); } catch (Throwable ignored) {} });
		net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> { if (level.isClientSide()) { try { Counter.onAttack(entity); } catch (Throwable ignored) {} } return net.minecraft.world.InteractionResult.PASS; });
		net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("grimoire_counter", "counter"), (g, dt) -> {
			try { Minecraft mc = Minecraft.getInstance(); if (Counter.D.hud && mc.player != null && !(mc.gui.screen() instanceof CounterScreen)) CounterHud.draw(g, false); } catch (Throwable ignored) {}
		});
	}

	static final String[] TYPES = {"voidgloom", "inferno", "revenant", "tarantula", "sven", "vampire", "kuudra", "dungeon"};
	static final SuggestionProvider<FabricClientCommandSource> TYPE_S = (c, b) -> SharedSuggestionProvider.suggest(TYPES, b);
	static final SuggestionProvider<FabricClientCommandSource> TIER_S = (c, b) -> {
		String t = ""; try { t = StringArgumentType.getString(c, "type").toLowerCase(Locale.ROOT); } catch (Exception ignored) {}
		List<String> l = new ArrayList<>();
		if (t.equals("kuudra")) l.addAll(List.of("basic", "hot", "burning", "fiery", "infernal"));
		else if (t.startsWith("dungeon") || t.startsWith("cata")) { for (int i = 1; i <= 7; i++) l.add("f" + i); for (int i = 1; i <= 7; i++) l.add("m" + i); }
		else for (int i = 1; i <= 5; i++) l.add(String.valueOf(i));
		return SharedSuggestionProvider.suggest(l, b);
	};
	/** players in your lobby and your clients */
	static final SuggestionProvider<FabricClientCommandSource> PLAYER_S = (c, b) -> {
		List<String> l = new ArrayList<>(); Minecraft mc = Minecraft.getInstance();
		for (Counter.Carry k : Counter.D.carries) l.add(k.player);
		if (mc.level != null) for (var p : mc.level.players()) if (p.getUUID().version() == 4 && p != mc.player) l.add(p.getGameProfile().name());
		return SharedSuggestionProvider.suggest(l, b);
	};
	static final SuggestionProvider<FabricClientCommandSource> CLIENT_S = (c, b) -> { List<String> l = new ArrayList<>(); for (Counter.Carry k : Counter.D.carries) l.add(k.player); return SharedSuggestionProvider.suggest(l, b); };

	static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> commands() {
		return literal("gcounter")
			.executes(c -> { if (off()) return 0; Minecraft mc = Minecraft.getInstance(); mc.execute(() -> mc.gui.setScreen(new CounterScreen())); return 1; })
			.then(literal("add").then(argument("type", StringArgumentType.word()).suggests(TYPE_S).then(argument("tier", StringArgumentType.word()).suggests(TIER_S).then(argument("player", StringArgumentType.word()).suggests(PLAYER_S)
				.executes(c -> add(c, 1))
				.then(argument("amount", IntegerArgumentType.integer(1, 5000)).executes(c -> add(c, IntegerArgumentType.getInteger(c, "amount"))))))))
			.then(literal("plus").then(argument("player", StringArgumentType.word()).suggests(CLIENT_S).executes(c -> with(c, k -> { if (k.done >= k.total) Counter.info(k.player + " already has all " + k.total + ".", ChatFormatting.GRAY); else Counter.plus(k); }))))
			.then(literal("minus").then(argument("player", StringArgumentType.word()).suggests(CLIENT_S).executes(c -> with(c, Counter::minus))))
			.then(literal("remove").then(argument("player", StringArgumentType.word()).suggests(CLIENT_S).executes(c -> with(c, k -> { Counter.remove(k); Counter.info("Removed " + k.player + " (" + k.done + "/" + k.total + " " + k.label() + ").", ChatFormatting.GRAY); }))))
			.then(literal("claim").then(argument("player", StringArgumentType.word()).suggests(CLIENT_S).executes(c -> { if (off()) return 0; String p = StringArgumentType.getString(c, "player"); Minecraft.getInstance().execute(() -> { if (!Counter.claim(p)) Counter.info("No carry for " + p + ".", ChatFormatting.RED); }); return 1; })))
			.then(literal("debug").executes(c -> { if (off()) return 0; Minecraft.getInstance().execute(Counter::debug); return 1; }));
	}
	static boolean off() { if (active) return false; Counter.info("Grimoire is installed: its own Carries page does the counting, so Grimoire Counter stays off.", ChatFormatting.GRAY); return true; }
	static int add(CommandContext<FabricClientCommandSource> c, int amount) {
		if (off()) return 0;
		String type = StringArgumentType.getString(c, "type"), tier = StringArgumentType.getString(c, "tier"), player = StringArgumentType.getString(c, "player");
		String[] kt = Counter.parse(type, tier);
		if (kt == null) { Counter.info("Not a carry: " + type + " " + tier + ". Try: voidgloom 4, inferno 3, kuudra infernal, dungeon f7, dungeon m5.", ChatFormatting.RED); return 0; }
		if (!player.matches("\\w{1,16}")) { Counter.info("Not a player name: " + player, ChatFormatting.RED); return 0; }
		Minecraft.getInstance().execute(() -> { Counter.Carry k = Counter.add(player, kt, amount); Counter.info("Counting " + k.label() + " for " + k.player + ": " + k.done + "/" + k.total, ChatFormatting.GREEN); });
		return 1;
	}
	static int with(CommandContext<FabricClientCommandSource> c, java.util.function.Consumer<Counter.Carry> what) {
		if (off()) return 0;
		String p = StringArgumentType.getString(c, "player");
		Minecraft.getInstance().execute(() -> { Counter.Carry k = Counter.find(p, null); if (k == null) Counter.info("No carry for " + p + ".", ChatFormatting.RED); else what.accept(k); });
		return 1;
	}
}

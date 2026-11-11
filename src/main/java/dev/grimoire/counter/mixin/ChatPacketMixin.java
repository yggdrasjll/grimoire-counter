/*
 * Grimoire Counter - Copyright (c) 2026 Yggdrasjll. All rights reserved.
 * Not to be copied, modified, decompiled, reused or shared without Yggdrasjll's written permission. See LICENSE.txt.
 */
package dev.grimoire.counter.mixin;

import dev.grimoire.counter.Counter;
import dev.grimoire.counter.CounterMod;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every system chat line reaches the counter, even when another mod (a chat filter, chat tabs) hides it before the normal hook sees it. Read only. */
@Mixin(value = ClientPacketListener.class, priority = 100) // (first in line: read before any other mod can change or stop it)
public abstract class ChatPacketMixin {
	@Inject(method = "handleSystemChat", at = @At("HEAD"))
	private void grimoireCounter$head(ClientboundSystemChatPacket p, CallbackInfo ci) {
		if (CounterMod.active && !p.overlay()) Counter.pendingPacket = p.content();
	}
	@Inject(method = "handleSystemChat", at = @At("RETURN"))
	private void grimoireCounter$tail(ClientboundSystemChatPacket p, CallbackInfo ci) {
		if (!CounterMod.active || p.overlay()) return;
		var c = Counter.pendingPacket; Counter.pendingPacket = null;
		if (c != null && c == p.content()) { try { Counter.onChat(c, false); } catch (Throwable ignored) {} } // nobody let the normal hook see it
	}
}

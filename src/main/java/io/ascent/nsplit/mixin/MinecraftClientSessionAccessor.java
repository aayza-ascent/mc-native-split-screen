package io.ascent.nsplit.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Writable accessor for {@code MinecraftClient.session}, used by
 * {@link io.ascent.nsplit.session.OfflineSessionProvider} to swap in a child's offline
 * identity before it joins the host LAN. {@code @Mutable} is required because the field is
 * {@code final}.
 */
@Mixin(MinecraftClient.class)
public interface MinecraftClientSessionAccessor {
	@Mutable
	@Accessor("session")
	void nsplit$setSession(Session session);
}

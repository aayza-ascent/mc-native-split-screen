package io.ascent.nsplit.mixin;

import io.ascent.nsplit.NSplit;
import io.ascent.nsplit.host.HostState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes the integrated server accept offline clients when the host is starting couch
 * co-op. Vanilla "Open to LAN" leaves online-mode on, which rejects the offline children;
 * this flips it off at publish time — and ONLY when {@link HostState#offlineLanRequested}
 * is set, so normal Open-to-LAN behaviour is unchanged.
 *
 * <p>Reimplemented from the OfflineLAN approach (that project is GPLv3, so we do not copy
 * its code). The handler captures only the return-callback to avoid coupling to the exact
 * {@code openToLan} parameter list across the 1.21.x range.
 *
 * <p>TODO(S3): verify the Yarn method name {@code openToLan} and {@code setOnlineMode} apply
 * cleanly on the target version; gate behind a build smoke test once multiversion lands.
 */
@Mixin(IntegratedServer.class)
public abstract class IntegratedServerOnlineModeMixin {
	@Inject(method = "openToLan", at = @At("HEAD"))
	private void nsplit$forceOfflineLan(CallbackInfoReturnable<Boolean> cir) {
		if (!HostState.offlineLanRequested) {
			return;
		}
		((MinecraftServer) (Object) this).setOnlineMode(false);
		NSplit.LOG.info("[host] integrated server online-mode disabled for couch co-op LAN");
	}
}

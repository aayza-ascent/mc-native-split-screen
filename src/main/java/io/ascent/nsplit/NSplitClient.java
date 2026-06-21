package io.ascent.nsplit;

import io.ascent.nsplit.client.ChildBootstrap;
import io.ascent.nsplit.host.HostCoordinator;
import net.fabricmc.api.ClientModInitializer;

/**
 * Single client entrypoint, dual-role. The same jar runs as the host coordinator by
 * default, or as a spawned split-screen child when {@code -Dnsplit.client=true} is present
 * (set by {@link io.ascent.nsplit.launch.InstanceLauncher}).
 */
public final class NSplitClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		if (NSplit.isChild()) {
			NSplit.LOG.info("Native Split Screen v{} — CHILD mode", NSplit.MOD_ID);
			ChildBootstrap.init();
		} else {
			NSplit.LOG.info("Native Split Screen v{} — HOST mode", NSplit.MOD_ID);
			HostCoordinator.init();
		}
	}
}

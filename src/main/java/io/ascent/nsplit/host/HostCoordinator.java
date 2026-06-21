package io.ascent.nsplit.host;

import io.ascent.nsplit.NSplit;
import io.ascent.nsplit.controller.ControllerAssigner;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * Host-mode init. Registers the {@code /couchcoop} command (S3 entry point), the
 * controller-assignment poll (Phase 1), and a world-close reset. Phase 2 adds an "Open
 * Couch Co-op" button to the pause screen as a friendlier entry than the command.
 */
public final class HostCoordinator {
	private HostCoordinator() {
	}

	public static void init() {
		ControllerAssigner.init();
		CouchCoopCommands.register();
		// Integrated server stop = host world closed: clear session + un-force offline mode.
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SessionCoordinator.get().reset());
		NSplit.LOG.info("[host] ready. In a singleplayer world run /couchcoop start, then /couchcoop add.");
	}
}

package io.ascent.nsplit.host;

import io.ascent.nsplit.NSplit;
import io.ascent.nsplit.client.CouchCoopKeybind;
import io.ascent.nsplit.controller.ControllerAssigner;
import io.ascent.nsplit.controller.JoinGesturePoll;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * Host-mode init. Registers the {@code /couchcoop} command + Join screen keybind, the
 * "press Start to join" controller poll, the controller assigner, and a world-close reset.
 */
public final class HostCoordinator {
	private HostCoordinator() {
	}

	public static void init() {
		ControllerAssigner.init();
		CouchCoopCommands.register();
		JoinGesturePoll.register();
		CouchCoopKeybind.register();
		// Integrated server stop = host world closed: clear session + un-force offline mode.
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SessionCoordinator.get().reset());
		NSplit.LOG.info("[host] ready. Run /couchcoop (or bind the 'Open Couch Co-op Screen' key) "
				+ "in a singleplayer world; press Start on a controller to join.");
	}
}

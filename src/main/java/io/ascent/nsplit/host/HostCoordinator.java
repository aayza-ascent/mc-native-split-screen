package io.ascent.nsplit.host;

import io.ascent.nsplit.NSplit;
import io.ascent.nsplit.controller.ControllerAssigner;

/**
 * Host-mode init. Wires the controller-assignment poll and (Phase 2) the "Open Couch
 * Co-op" entry point in the pause/title screen.
 */
public final class HostCoordinator {
	private HostCoordinator() {
	}

	public static void init() {
		ControllerAssigner.init();
		NSplit.LOG.info("[host] ready. TODO(S3): add an 'Open Couch Co-op' action that calls "
				+ "IntegratedServer.openToLan(...) then SessionCoordinator.get().beginHosting(port).");
	}
}

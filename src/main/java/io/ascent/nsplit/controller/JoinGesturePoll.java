package io.ascent.nsplit.controller;

import io.ascent.nsplit.NSplit;
import io.ascent.nsplit.host.SessionCoordinator;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import java.util.List;

/**
 * "Press Start on an unassigned controller to join." Polls each client tick while hosting:
 * any connected pad (other than the host's and already-assigned pads) that presses Start
 * spawns a new local player bound to it — the console-style hot-join gesture.
 *
 * <p>Controlify-guarded via {@link ControllerAssigner}; a no-op when Controlify is absent or
 * the host isn't running couch co-op.
 */
public final class JoinGesturePoll {
	private JoinGesturePoll() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			SessionCoordinator coord = SessionCoordinator.get();
			if (!coord.isHosting() || !ControllerAssigner.available()) {
				return;
			}
			List<String> joined = ControllerAssigner.detectJoinPresses(coord.assignedControllerUids());
			for (String uid : joined) {
				NSplit.LOG.info("[host] join gesture from controller {} ({})", uid, ControllerAssigner.nameOf(uid));
				coord.addPlayer(uid);
			}
		});
	}
}

package io.ascent.nsplit.client;

import io.ascent.nsplit.NSplit;

/**
 * Child-side controller binding (Phase 1). Stub until the Controlify dependency is added.
 *
 * <p>Planned mechanism: {@code ControlifyApi.get().setCurrentController(<match uid>)} and
 * force {@code out_of_focus_input=true} so this (usually unfocused) child window still reads
 * its pad — the make-or-break behaviour validated by spike S1.
 */
public final class ControllerBinder {
	private ControllerBinder() {
	}

	public static void bind(String controllerUid) {
		NSplit.LOG.info("[child] TODO(Phase1) bind controller uid={} "
				+ "(ControlifyApi.setCurrentController + out_of_focus_input=true)", controllerUid);
	}
}

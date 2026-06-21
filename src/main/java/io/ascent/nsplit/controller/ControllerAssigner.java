package io.ascent.nsplit.controller;

import io.ascent.nsplit.NSplit;

/**
 * Host-side controller assignment (Phase 1). Stub for now — implemented once the Controlify
 * dependency is added (see build.gradle / README).
 *
 * <p>Planned mechanism:
 * <ul>
 *   <li>Force the Controlify SDL backend and {@code out_of_focus_input=true} in every
 *       instance (the make-or-break toggle for background controller input on macOS).</li>
 *   <li>Subscribe {@code ControlifyEvents.CONTROLLER_STATE_UPDATE}; when an unassigned,
 *       non-active pad presses Start, open the Join UX bound to {@code controller.info().uid()}
 *       and call {@link io.ascent.nsplit.host.SessionCoordinator#addPlayer(String)}.</li>
 *   <li>Prefer DIFFERENT controller models per player — identical Bluetooth pads collapse to
 *       one UID (Controlify #853/#784), which breaks per-player routing (plan risk R2).</li>
 * </ul>
 */
public final class ControllerAssigner {
	private ControllerAssigner() {
	}

	public static void init() {
		NSplit.LOG.info("[host] ControllerAssigner stub — add Controlify dep, then implement "
				+ "CONTROLLER_STATE_UPDATE poll + SDL/out_of_focus_input enforcement (Phase 1).");
	}
}

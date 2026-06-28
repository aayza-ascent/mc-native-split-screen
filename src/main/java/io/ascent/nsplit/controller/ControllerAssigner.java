package io.ascent.nsplit.controller;

import io.ascent.nsplit.NSplit;
import io.ascent.nsplit.controller.compat.ControlifyCompat;
import net.fabricmc.loader.api.FabricLoader;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Host-side controller assignment. Controlify is optional — every entry that touches it is
 * guarded by {@link #available()} so {@link ControlifyCompat} (and the Controlify classes it
 * references) is only classloaded when Controlify is present.
 *
 * <p>Players join by pressing Start on a controller (see {@code JoinGesturePoll}); the host's
 * own pad and its duplicate macOS enumerations are excluded by device GUID inside
 * {@link ControlifyCompat#startJustPressed(Set)}. "Add Player" on the Join screen adds a
 * keyboard player instead (no controller).
 */
public final class ControllerAssigner {
	private static final String CONTROLIFY = "controlify";

	private ControllerAssigner() {
	}

	public static boolean available() {
		return FabricLoader.getInstance().isModLoaded(CONTROLIFY);
	}

	public static void init() {
		if (available()) {
			NSplit.LOG.info("[host] Controlify detected — press Start on a NEW controller to join "
					+ "(your own pad and its macOS duplicate enumerations are excluded by GUID). "
					+ "Use different controller models per player.");
		} else {
			NSplit.LOG.info("[host] Controlify not installed — controller assignment disabled.");
		}
	}

	/** Enables out-of-focus input on this instance so unfocused tiles still read their pad. */
	public static void enableBackgroundInput() {
		if (available()) {
			ControlifyCompat.enableBackgroundInput();
		}
	}

	/** UID of the host's own active controller, if any (excluded from join detection). */
	public static Optional<String> currentControllerUid() {
		return available() ? ControlifyCompat.currentControllerUid() : Optional.empty();
	}

	/** UIDs (not in {@code excluded}) that pressed Start this tick — the join gesture. */
	public static List<String> detectJoinPresses(Set<String> excluded) {
		return available() ? ControlifyCompat.startJustPressed(excluded) : List.of();
	}

	/** Human-readable controller name for {@code uid}, or the uid if unknown/absent. */
	public static String nameOf(String uid) {
		return available() ? ControlifyCompat.nameOf(uid) : uid;
	}
}

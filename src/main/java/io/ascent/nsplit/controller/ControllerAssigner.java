package io.ascent.nsplit.controller;

import io.ascent.nsplit.NSplit;
import io.ascent.nsplit.controller.compat.ControlifyCompat;
import net.fabricmc.loader.api.FabricLoader;

import java.util.Optional;
import java.util.Set;

/**
 * Host-side controller assignment. Controlify is optional — every entry that touches it is
 * guarded by {@link #available()} so {@link ControlifyCompat} (and the Controlify classes it
 * references) is only classloaded when Controlify is present.
 *
 * <p>Current model: {@code /couchcoop add} auto-assigns the next connected, unassigned
 * controller to the new player. A "press Start on an unassigned pad to join" poll is a
 * follow-up; it needs Controlify's per-controller input/binding API and is best validated
 * alongside spike S1.
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
			NSplit.LOG.info("[host] Controlify detected — controllers auto-assign on /couchcoop add. "
					+ "Use DIFFERENT controller models per player; identical pads share a UID (plan R2).");
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

	/** Next connected controller UID not in {@code assigned}, if Controlify is present. */
	public static Optional<String> pickUnassigned(Set<String> assigned) {
		if (!available()) {
			return Optional.empty();
		}
		Optional<String> uid = ControlifyCompat.firstUnassignedUid(assigned);
		uid.ifPresent(u -> NSplit.LOG.info("[host] assigning controller {} ({})", u, ControlifyCompat.nameOf(u)));
		if (uid.isEmpty()) {
			NSplit.LOG.warn("[host] no unassigned controller connected — player will spawn without a pad.");
		}
		return uid;
	}
}

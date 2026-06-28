package io.ascent.nsplit.controller.compat;

import dev.isxander.controlify.Controlify;
import dev.isxander.controlify.controller.ControllerEntity;
import dev.isxander.controlify.controller.input.GamepadInputs;
import dev.isxander.controlify.controller.input.InputComponent;
import dev.isxander.controlify.controllermanager.ControllerManager;
import io.ascent.nsplit.NSplit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The ONLY class that references Controlify types directly. Every caller must first check
 * {@code FabricLoader.isModLoaded("controlify")} (see {@link io.ascent.nsplit.controller.ControllerAssigner}
 * and {@link io.ascent.nsplit.client.ControllerBinder}) so this class — and the Controlify
 * classes it pulls in — is never classloaded when Controlify is absent. Controlify is an
 * optional, compile-only dependency.
 *
 * <p>Verified against Controlify 2.5.0+1.21.1 via javap: {@code Controlify.instance()},
 * {@code getControllerManager():Optional<ControllerManager>}, {@code ControllerManager
 * .getConnectedControllers():List<ControllerEntity>}, {@code ControllerEntity.uid()/name()},
 * {@code setCurrentController(ControllerEntity, boolean)}, {@code config().globalSettings()
 * .outOfFocusInput} + {@code config().save()}.
 */
public final class ControlifyCompat {
	private ControlifyCompat() {
	}

	private static Optional<ControllerManager> manager() {
		try {
			return Controlify.instance().getControllerManager();
		} catch (Throwable t) {
			NSplit.LOG.warn("[controlify] not ready yet: {}", t.toString());
			return Optional.empty();
		}
	}

	/** UIDs present on the previous join poll — debounces freshly-(re)appeared phantom enumerations. */
	private static volatile Set<String> seenLastPoll = Set.of();

	/** Human-readable controller name for logging (falls back to the UID). */
	public static String nameOf(String uid) {
		return manager().stream()
				.flatMap(m -> m.getConnectedControllers().stream())
				.filter(c -> c.uid().equals(uid))
				.map(ControllerEntity::name)
				.findFirst()
				.orElse(uid);
	}

	/**
	 * Makes this instance read its controller while unfocused — the make-or-break setting
	 * for couch co-op on macOS (plan R1; SDL background events). Persists to controlify.json.
	 */
	public static void enableBackgroundInput() {
		try {
			var settings = Controlify.instance().config().globalSettings();
			if (!settings.outOfFocusInput) {
				settings.outOfFocusInput = true;
				Controlify.instance().config().save();
				NSplit.LOG.info("[controlify] enabled out-of-focus input");
			}
		} catch (Throwable t) {
			NSplit.LOG.warn("[controlify] could not enable out-of-focus input: {}", t.toString());
		}
	}

	/** UID of Controlify's currently-active controller (the host's own pad), if any. */
	public static Optional<String> currentControllerUid() {
		try {
			return Controlify.instance().getCurrentController().map(ControllerEntity::uid);
		} catch (Throwable t) {
			return Optional.empty();
		}
	}

	/**
	 * "Press Start to join": UIDs of connected controllers that are genuinely NEW physical
	 * devices (not the host's pad, not an already-assigned device, not a duplicate enumeration)
	 * and whose START button went down this tick.
	 *
	 * <p>macOS enumerates one physical pad under several UIDs (MFI + HIDAPI, and a fresh UID per
	 * re-plug), so excluding only assigned UIDs lets the host's own pad spawn phantom players.
	 * We therefore exclude by device GUID (the host's current controller + every assigned
	 * controller), debounce controllers absent last poll (their previous-state is garbage),
	 * dedupe per GUID, and ignore any candidate while the host is itself holding START (the same
	 * physical press mirrored onto a duplicate enumeration).
	 */
	public static List<String> startJustPressed(Set<String> assignedUids) {
		ControllerManager mgr = manager().orElse(null);
		if (mgr == null) {
			seenLastPoll = Set.of();
			return List.of();
		}
		List<ControllerEntity> controllers = mgr.getConnectedControllers();
		Set<String> excludedGuids = excludedGuids(controllers, assignedUids);
		boolean hostHoldingStart = currentControllerStartDown();

		Set<String> nowSeen = new HashSet<>();
		Set<String> firedGuids = new HashSet<>();
		List<String> out = new ArrayList<>();
		for (ControllerEntity c : controllers) {
			String uid = c.uid();
			nowSeen.add(uid);
			if (assignedUids.contains(uid) || excludedGuids.contains(c.guid())) {
				continue;
			}
			if (!seenLastPoll.contains(uid) || firedGuids.contains(c.guid())) {
				continue; // debounce freshly-appeared / duplicate enumerations
			}
			Optional<InputComponent> input = c.input();
			if (input.isEmpty()) {
				continue;
			}
			boolean rising = input.get().stateNow().isButtonDown(GamepadInputs.START_BUTTON)
					&& !input.get().stateThen().isButtonDown(GamepadInputs.START_BUTTON);
			if (rising && !hostHoldingStart) {
				out.add(uid);
				firedGuids.add(c.guid());
			}
		}
		seenLastPoll = nowSeen;
		return out;
	}

	/** GUIDs that must not join: the host's current controller plus every assigned controller. */
	private static Set<String> excludedGuids(List<ControllerEntity> controllers, Set<String> assignedUids) {
		Set<String> guids = new HashSet<>();
		try {
			Controlify.instance().getCurrentController().ifPresent(c -> guids.add(c.guid()));
		} catch (Throwable ignored) {
		}
		for (ControllerEntity c : controllers) {
			if (assignedUids.contains(c.uid())) {
				guids.add(c.guid());
			}
		}
		return guids;
	}

	private static boolean currentControllerStartDown() {
		try {
			return Controlify.instance().getCurrentController()
					.flatMap(ControllerEntity::input)
					.map(in -> in.stateNow().isButtonDown(GamepadInputs.START_BUTTON))
					.orElse(false);
		} catch (Throwable t) {
			return false;
		}
	}

	/** Binds this instance's active controller to {@code uid}. Returns true on success. */
	public static boolean bindCurrentController(String uid) {
		try {
			Optional<ControllerManager> mgr = manager();
			if (mgr.isEmpty()) {
				return false;
			}
			Optional<ControllerEntity> match = mgr.get().getConnectedControllers().stream()
					.filter(c -> c.uid().equals(uid))
					.findFirst();
			if (match.isEmpty()) {
				NSplit.LOG.warn("[controlify] controller uid {} not connected; cannot bind", uid);
				return false;
			}
			// 2nd arg is the "hotplug" flag; false = silent programmatic selection.
			Controlify.instance().setCurrentController(match.get(), false);
			NSplit.LOG.info("[controlify] bound current controller to {} ({})", uid, match.get().name());
			return true;
		} catch (Throwable t) {
			NSplit.LOG.warn("[controlify] bind failed for {}: {}", uid, t.toString());
			return false;
		}
	}
}

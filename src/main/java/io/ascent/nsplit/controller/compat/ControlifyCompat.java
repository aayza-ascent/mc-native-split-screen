package io.ascent.nsplit.controller.compat;

import dev.isxander.controlify.Controlify;
import dev.isxander.controlify.controller.ControllerEntity;
import dev.isxander.controlify.controller.input.ControllerStateView;
import dev.isxander.controlify.controller.input.GamepadInputs;
import dev.isxander.controlify.controller.input.InputComponent;
import dev.isxander.controlify.controllermanager.ControllerManager;
import io.ascent.nsplit.NSplit;

import java.util.ArrayList;
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

	/** First connected controller UID not already in {@code assigned}, if any. */
	public static Optional<String> firstUnassignedUid(Set<String> assigned) {
		return manager().stream()
				.flatMap(m -> m.getConnectedControllers().stream())
				.map(ControllerEntity::uid)
				.filter(uid -> !assigned.contains(uid))
				.findFirst();
	}

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
	 * UIDs of connected controllers (excluding {@code excluded}) whose START button went down
	 * this tick (rising edge) — the "press Start to join" gesture. Compares Controlify's
	 * current vs previous state view.
	 */
	public static List<String> startJustPressed(Set<String> excluded) {
		List<String> out = new ArrayList<>();
		List<ControllerEntity> controllers = manager()
				.map(ControllerManager::getConnectedControllers)
				.orElse(List.of());
		for (ControllerEntity c : controllers) {
			if (excluded.contains(c.uid())) {
				continue;
			}
			Optional<InputComponent> input = c.input();
			if (input.isEmpty()) {
				continue;
			}
			ControllerStateView now = input.get().stateNow();
			ControllerStateView then = input.get().stateThen();
			if (now.isButtonDown(GamepadInputs.START_BUTTON) && !then.isButtonDown(GamepadInputs.START_BUTTON)) {
				out.add(c.uid());
			}
		}
		return out;
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

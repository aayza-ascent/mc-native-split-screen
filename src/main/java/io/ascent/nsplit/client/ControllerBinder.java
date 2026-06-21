package io.ascent.nsplit.client;

import io.ascent.nsplit.NSplit;
import io.ascent.nsplit.controller.compat.ControlifyCompat;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Child-side controller binding. Guarded so {@link ControlifyCompat} is only touched when
 * Controlify is installed. Enables out-of-focus input (so this usually-unfocused child still
 * reads its pad — plan R1) and binds Controlify's active controller to the assigned UID.
 */
public final class ControllerBinder {
	private ControllerBinder() {
	}

	public static void bind(String controllerUid) {
		if (controllerUid == null || controllerUid.isBlank()) {
			return;
		}
		if (!FabricLoader.getInstance().isModLoaded("controlify")) {
			NSplit.LOG.info("[child] Controlify not installed; cannot bind controller {}", controllerUid);
			return;
		}
		ControlifyCompat.enableBackgroundInput();
		ControlifyCompat.bindCurrentController(controllerUid);
	}
}

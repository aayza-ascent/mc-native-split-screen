package io.ascent.nsplit.client;

import io.ascent.nsplit.client.screen.CouchCoopScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Registers the "Open Couch Co-op Screen" keybind (unbound by default — set it in
 * Controls). Opens {@link CouchCoopScreen}. {@code /couchcoop} opens the same screen.
 */
public final class CouchCoopKeybind {
	private CouchCoopKeybind() {
	}

	public static void register() {
		KeyBinding key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.nsplit.open_couch_coop",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_UNKNOWN,
				"key.categories.nsplit"));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (key.wasPressed()) {
				client.setScreen(new CouchCoopScreen(client.currentScreen));
			}
		});
	}
}

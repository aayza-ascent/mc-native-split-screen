package io.ascent.nsplit.client;

import io.ascent.nsplit.client.screen.CouchCoopScreen;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Adds a small "Couch Co-op" button to the vanilla pause menu (top-left, out of the way of
 * the centered button column) that opens {@link CouchCoopScreen}. Uses Fabric's screen
 * events + {@code Screens.getButtons} — no mixin required.
 */
public final class PauseMenuButton {
	private PauseMenuButton() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof GameMenuScreen)) {
				return;
			}
			ButtonWidget button = ButtonWidget.builder(
							Text.literal("Couch Co-op"),
							b -> client.setScreen(new CouchCoopScreen(screen)))
					.dimensions(4, 4, 120, 20)
					.build();
			Screens.getButtons(screen).add(button);
		});
	}
}

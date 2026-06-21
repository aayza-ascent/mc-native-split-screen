package io.ascent.nsplit.client;

import io.ascent.nsplit.NSplit;
import io.ascent.nsplit.session.OfflineSessionProvider;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

/**
 * On a child instance, waits for the title screen, installs the offline session, then
 * auto-joins the host's LAN world. Ordering matters: the session must be swapped before the
 * network handshake, which is why this runs on the title screen rather than at client init.
 *
 * <p>Joining is one-shot: {@link io.ascent.nsplit.client.ChildBootstrap}'s connection-event
 * handlers signal readiness (on join) and exit the process (on disconnect).
 */
public final class AutoConnector {
	private static boolean attempted = false;

	private AutoConnector() {
	}

	public static void register(String username, String server) {
		if (server == null || server.isBlank()) {
			NSplit.LOG.warn("[child] no {} set; cannot auto-join", NSplit.PROP_SERVER);
			return;
		}
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (attempted || !(screen instanceof TitleScreen)) {
				return;
			}
			attempted = true;

			OfflineSessionProvider.installOffline(username);

			ServerInfo info = new ServerInfo("Couch Host", server, ServerInfo.ServerType.LAN);
			ServerAddress address = ServerAddress.parse(server);
			NSplit.LOG.info("[child] auto-connecting to {} as {}", server, username);
			ConnectScreen.connect(new TitleScreen(), client, address, info, false, null);
		});
	}
}

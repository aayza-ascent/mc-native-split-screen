package io.ascent.nsplit.client;

import io.ascent.nsplit.NSplit;
import io.ascent.nsplit.session.OfflineSessionProvider;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screen.TitleScreen;

/**
 * Watches for the title screen on a child instance, installs the offline session, then
 * auto-joins the host's LAN world. Ordering matters: the session must be swapped before the
 * network handshake, which is why this runs on the title screen rather than at client init.
 */
public final class AutoConnector {
	private static boolean joined = false;

	private AutoConnector() {
	}

	public static void register(String username, String server) {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (joined || !(screen instanceof TitleScreen)) {
				return;
			}
			joined = true;

			OfflineSessionProvider.installOffline(username);

			// TODO(S3): connect to the host LAN, e.g.
			//   ServerInfo info = new ServerInfo("Couch Host", server, ServerInfo.ServerType.LAN);
			//   ConnectScreen.connect(new TitleScreen(), client, ServerAddress.parse(server), info, false, null);
			// and on disconnect call client.stop() so the child process exits cleanly.
			NSplit.LOG.info("[child] TODO(S3) auto-connect to {} via ConnectScreen.connect(...)", server);

			ChildBootstrap.signalReady();
		});
	}
}

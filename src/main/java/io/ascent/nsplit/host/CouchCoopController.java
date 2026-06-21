package io.ascent.nsplit.host;

import io.ascent.nsplit.NSplit;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Optional;

/**
 * Shared host actions invoked by both the {@code /couchcoop} command and the Join screen.
 * Must run on the client (render) thread.
 */
public final class CouchCoopController {
	private CouchCoopController() {
	}

	/**
	 * Opens the current singleplayer world to LAN in offline mode and begins coordinating.
	 *
	 * @return an error message to show the user, or empty on success
	 */
	public static Optional<Text> startHosting() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!mc.isIntegratedServerRunning() || mc.getServer() == null) {
			return Optional.of(Text.literal("Open a singleplayer world first."));
		}
		if (SessionCoordinator.get().isHosting()) {
			return Optional.of(Text.literal("Already hosting."));
		}

		IntegratedServer server = mc.getServer();
		GameMode gameMode = mc.interactionManager != null
				? mc.interactionManager.getCurrentGameMode()
				: GameMode.SURVIVAL;

		int port;
		try (ServerSocket probe = new ServerSocket(0)) {
			port = probe.getLocalPort();
		} catch (IOException e) {
			return Optional.of(Text.literal("Could not allocate a LAN port: " + e.getMessage()));
		}

		// Must be set BEFORE openToLan so IntegratedServerOnlineModeMixin flips online-mode off.
		HostState.offlineLanRequested = true;
		if (!server.openToLan(gameMode, true, port)) {
			HostState.offlineLanRequested = false;
			return Optional.of(Text.literal("openToLan failed (already open?)."));
		}

		int actual = server.getServerPort();
		SessionCoordinator.get().beginHosting(actual);
		NSplit.LOG.info("[host] couch co-op started, LAN offline on port {}", actual);
		return Optional.empty();
	}
}

package io.ascent.nsplit.host;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.ascent.nsplit.NSplit;
import io.ascent.nsplit.window.TileLayout;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Locale;

/**
 * Client command {@code /couchcoop start|add|layout <h|v|grid>} — the S3/spike entry point
 * for hosting couch co-op, ahead of the Phase-2 Join UI.
 *
 * <ul>
 *   <li>{@code start} — opens the current singleplayer world to LAN in offline mode and
 *       begins coordinating (see {@link SessionCoordinator#beginHosting(int)}).</li>
 *   <li>{@code add} — spawns the next local player (unassigned controller for now).</li>
 *   <li>{@code layout} — switches the tile arrangement and retiles live.</li>
 * </ul>
 *
 * Commands execute on the client (render) thread, so direct GLFW/Minecraft calls are safe.
 */
public final class CouchCoopCommands {
	private CouchCoopCommands() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(ClientCommandManager.literal("couchcoop")
						.then(ClientCommandManager.literal("start").executes(ctx -> {
							start(ctx.getSource()::sendFeedback);
							return 1;
						}))
						.then(ClientCommandManager.literal("add").executes(ctx -> {
							add(ctx.getSource()::sendFeedback);
							return 1;
						}))
						.then(ClientCommandManager.literal("layout")
								.then(ClientCommandManager.argument("mode", StringArgumentType.word())
										.executes(ctx -> {
											layout(StringArgumentType.getString(ctx, "mode"), ctx.getSource()::sendFeedback);
											return 1;
										})))));
	}

	@FunctionalInterface
	private interface Feedback {
		void send(Text message);
	}

	private static void start(Feedback fb) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!mc.isIntegratedServerRunning() || mc.getServer() == null) {
			fb.send(Text.literal("[NativeSplit] Open a singleplayer world first."));
			return;
		}
		if (SessionCoordinator.get().isHosting()) {
			fb.send(Text.literal("[NativeSplit] Already hosting. Use /couchcoop add."));
			return;
		}

		IntegratedServer server = mc.getServer();
		GameMode gameMode = mc.interactionManager != null
				? mc.interactionManager.getCurrentGameMode()
				: GameMode.SURVIVAL;

		int port;
		try (ServerSocket probe = new ServerSocket(0)) {
			port = probe.getLocalPort();
		} catch (IOException e) {
			fb.send(Text.literal("[NativeSplit] Could not allocate a LAN port: " + e.getMessage()));
			return;
		}

		// Must be set BEFORE openToLan so IntegratedServerOnlineModeMixin flips online-mode off.
		HostState.offlineLanRequested = true;
		boolean ok = server.openToLan(gameMode, true, port);
		if (!ok) {
			HostState.offlineLanRequested = false;
			fb.send(Text.literal("[NativeSplit] openToLan failed (already open?)."));
			return;
		}

		int actual = server.getServerPort();
		SessionCoordinator.get().beginHosting(actual);
		fb.send(Text.literal("[NativeSplit] Hosting couch co-op on localhost:" + actual
				+ " (offline). Use /couchcoop add to spawn a player."));
		NSplit.LOG.info("[host] couch co-op started, LAN offline on port {}", actual);
	}

	private static void add(Feedback fb) {
		if (!SessionCoordinator.get().isHosting()) {
			fb.send(Text.literal("[NativeSplit] Not hosting yet — run /couchcoop start first."));
			return;
		}
		SessionCoordinator.get().addPlayer(null);
		fb.send(Text.literal("[NativeSplit] Spawning a player instance (this takes a few seconds)..."));
	}

	private static void layout(String mode, Feedback fb) {
		TileLayout layout = switch (mode.toLowerCase(Locale.ROOT)) {
			case "h", "horizontal" -> TileLayout.HORIZONTAL;
			case "v", "vertical" -> TileLayout.VERTICAL;
			case "g", "grid" -> TileLayout.GRID;
			default -> null;
		};
		if (layout == null) {
			fb.send(Text.literal("[NativeSplit] Unknown layout '" + mode + "' (use horizontal|vertical|grid)."));
			return;
		}
		SessionCoordinator.get().setLayout(layout);
		fb.send(Text.literal("[NativeSplit] Layout set to " + layout + "."));
	}
}

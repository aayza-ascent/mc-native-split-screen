package io.ascent.nsplit.host;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.ascent.nsplit.NSplit;
import io.ascent.nsplit.client.screen.CouchCoopScreen;
import io.ascent.nsplit.launch.ChildSpec;
import io.ascent.nsplit.launch.GameDirManager;
import io.ascent.nsplit.launch.InstanceLauncher;
import io.ascent.nsplit.window.TileLayout;
import io.ascent.nsplit.window.WindowTiler;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Client command {@code /couchcoop start|add|layout <h|v|grid>|screen|dryrun} — entry points
 * for hosting couch co-op alongside the Join screen and the controller "press Start" gesture.
 * Commands run on the client (render) thread, so direct GLFW/Minecraft calls are safe.
 */
public final class CouchCoopCommands {
	private CouchCoopCommands() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(ClientCommandManager.literal("couchcoop")
						.executes(ctx -> {
							openScreen();
							return 1;
						})
						.then(ClientCommandManager.literal("start").executes(ctx -> {
							start(ctx.getSource()::sendFeedback);
							return 1;
						}))
						.then(ClientCommandManager.literal("add").executes(ctx -> {
							add(ctx.getSource()::sendFeedback);
							return 1;
						}))
						.then(ClientCommandManager.literal("screen").executes(ctx -> {
							openScreen();
							return 1;
						}))
						.then(ClientCommandManager.literal("layout")
								.then(ClientCommandManager.argument("mode", StringArgumentType.word())
										.executes(ctx -> {
											layout(StringArgumentType.getString(ctx, "mode"), ctx.getSource()::sendFeedback);
											return 1;
										})))
						.then(ClientCommandManager.literal("dryrun").executes(ctx -> {
							dryrun(ctx.getSource()::sendFeedback);
							return 1;
						}))
						.then(ClientCommandManager.literal("displays").executes(ctx -> {
							displays(ctx.getSource()::sendFeedback);
							return 1;
						}))
						.then(ClientCommandManager.literal("monitor")
								.then(ClientCommandManager.argument("player", IntegerArgumentType.integer(1, NSplit.MAX_PLAYERS))
										.then(ClientCommandManager.argument("display", IntegerArgumentType.integer(0, 15))
												.executes(ctx -> {
													int player = IntegerArgumentType.getInteger(ctx, "player");
													int display = IntegerArgumentType.getInteger(ctx, "display");
													SessionCoordinator.get().setPlayerMonitor(player, display);
													ctx.getSource().sendFeedback(Text.literal(
															"[NativeSplit] Player " + player + " -> display " + display
																	+ " (use the Per Display layout to apply)."));
													return 1;
												}))))));
	}

	@FunctionalInterface
	private interface Feedback {
		void send(Text message);
	}

	private static void start(Feedback fb) {
		Optional<Text> error = CouchCoopController.startHosting();
		if (error.isPresent()) {
			fb.send(Text.literal("[NativeSplit] " + error.get().getString()));
			return;
		}
		fb.send(Text.literal("[NativeSplit] Hosting couch co-op (offline). Press Start on a "
				+ "controller to join, or /couchcoop add."));
	}

	private static void add(Feedback fb) {
		if (!SessionCoordinator.get().isHosting()) {
			fb.send(Text.literal("[NativeSplit] Not hosting yet — run /couchcoop start first."));
			return;
		}
		SessionCoordinator.get().addPlayer(null);
		fb.send(Text.literal("[NativeSplit] Spawning a player instance (this takes a few seconds)..."));
	}

	private static void openScreen() {
		MinecraftClient mc = MinecraftClient.getInstance();
		mc.execute(() -> mc.setScreen(new CouchCoopScreen(mc.currentScreen)));
	}

	/** Writes the reconstructed child launch command to a file — diagnostic for spike S4. */
	private static void dryrun(Feedback fb) {
		try {
			Path dir = GameDirManager.prepare("dryrun");
			ChildSpec spec = new ChildSpec(2, "Player2", UUID.randomUUID(), "localhost:25565", null, dir);
			List<String> cmd = InstanceLauncher.buildCommand(spec);
			Path out = GameDirManager.hostGameDir().resolve(".nsplit-instances").resolve("launch-dryrun.txt");
			Files.writeString(out, String.join(" \\\n  ", cmd));
			fb.send(Text.literal("[NativeSplit] Wrote child launch command (" + cmd.size()
					+ " args) to " + out));
			NSplit.LOG.info("[host] dryrun launch command written to {}", out);
		} catch (Exception e) {
			fb.send(Text.literal("[NativeSplit] dryrun failed: " + e.getMessage()));
		}
	}

	private static void displays(Feedback fb) {
		List<String> names = WindowTiler.monitorNames();
		if (names.isEmpty()) {
			fb.send(Text.literal("[NativeSplit] No displays detected."));
			return;
		}
		fb.send(Text.literal("[NativeSplit] Displays (use /couchcoop monitor <player> <index>):"));
		for (int i = 0; i < names.size(); i++) {
			fb.send(Text.literal("  " + i + ": " + names.get(i)));
		}
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

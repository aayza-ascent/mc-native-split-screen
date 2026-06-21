package io.ascent.nsplit.client;

import io.ascent.nsplit.NSplit;
import io.ascent.nsplit.ipc.IpcClient;
import io.ascent.nsplit.ipc.IpcMessage;
import io.ascent.nsplit.window.WindowTiler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;

import java.util.UUID;

/**
 * Child-mode init: reads the {@code -Dnsplit.*} config the host passed, registers the
 * auto-join watcher and connection lifecycle, connects the IPC channel back to the host,
 * and applies host-pushed window rects / controller binds.
 */
public final class ChildBootstrap {
	private static IpcClient ipc;
	private static String controllerUid;
	private static volatile boolean joined = false;

	private ChildBootstrap() {
	}

	public static void init() {
		String username = NSplit.prop(NSplit.PROP_USERNAME, "Player2");
		String server = NSplit.prop(NSplit.PROP_SERVER, null);
		UUID childId = UUID.fromString(NSplit.prop(NSplit.PROP_UUID, UUID.randomUUID().toString()));
		controllerUid = NSplit.prop(NSplit.PROP_CONTROLLER_UID, null);

		NSplit.LOG.info("[child] username={} server={} controller={}", username, server, controllerUid);

		// Installs the offline session, then auto-joins, when the title screen first appears.
		AutoConnector.register(username, server);
		registerConnectionLifecycle();

		try {
			ipc = IpcClient.connect(childId, ChildBootstrap::onHostMessage);
		} catch (Exception e) {
			NSplit.LOG.warn("[child] IPC connect failed (host not coordinating?): {}", e.toString());
		}
	}

	private static void registerConnectionLifecycle() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			joined = true;
			NSplit.LOG.info("[child] joined host world");
			signalReady();
			if (controllerUid != null) {
				ControllerBinder.bind(controllerUid);
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			if (!joined) {
				// Connect failed before we ever joined — keep the window so the error is visible.
				return;
			}
			NSplit.LOG.info("[child] disconnected from host; exiting child process");
			if (ipc != null) {
				ipc.send(IpcMessage.of(IpcMessage.Type.CLOSING));
			}
			client.execute(client::stop);
		});
	}

	private static void onHostMessage(IpcMessage msg) {
		switch (msg.type()) {
			case RECT -> applyRect(new WindowTiler.Rect(
					msg.intArg(0), msg.intArg(1), msg.intArg(2), msg.intArg(3)));
			case BIND -> ControllerBinder.bind(msg.arg(0));
			default -> {
			}
		}
	}

	private static void applyRect(WindowTiler.Rect rect) {
		MinecraftClient mc = MinecraftClient.getInstance();
		// GLFW window ops must run on the render thread.
		mc.execute(() -> WindowTiler.apply(mc.getWindow().getHandle(), rect));
	}

	private static void signalReady() {
		if (ipc != null) {
			ipc.send(IpcMessage.of(IpcMessage.Type.READY));
		}
	}
}

package io.ascent.nsplit.host;

import io.ascent.nsplit.NSplit;
import io.ascent.nsplit.ipc.IpcMessage;
import io.ascent.nsplit.ipc.IpcServer;
import io.ascent.nsplit.launch.ChildSpec;
import io.ascent.nsplit.launch.GameDirManager;
import io.ascent.nsplit.launch.InstanceLauncher;
import io.ascent.nsplit.session.OfflineIdentity;
import io.ascent.nsplit.window.TileLayout;
import io.ascent.nsplit.window.WindowTiler;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Host-side orchestrator: tracks child instances, spawns them, routes window rects and
 * controller binds over IPC, and retiles on join/leave. Pure coordination — the few
 * Minecraft touch-points (reading the LAN port, applying the host's own window rect) are
 * called in from an S3 glue hook so this class stays mapping-light and testable.
 */
public final class SessionCoordinator {
	private static final SessionCoordinator INSTANCE = new SessionCoordinator();

	public static SessionCoordinator get() {
		return INSTANCE;
	}

	private IpcServer ipc;
	private int lanPort = -1;
	private TileLayout layout = TileLayout.HORIZONTAL;
	private final Map<UUID, ChildHandle> children = new LinkedHashMap<>();

	private SessionCoordinator() {
	}

	/** A spawned child and its state. */
	public record ChildHandle(int slot, String username, Process process, Path gameDir,
							   String controllerUid, boolean ready) {
		ChildHandle withReady() {
			return new ChildHandle(slot, username, process, gameDir, controllerUid, true);
		}
	}

	/**
	 * Called once the host has opened its world to LAN (online-mode already forced off via
	 * {@link HostState}). Starts the IPC server. TODO(S3): wire the actual call site —
	 * a mixin/glue around {@code IntegratedServer.openToLan} that reads the chosen port.
	 */
	public synchronized void beginHosting(int lanPort) {
		this.lanPort = lanPort;
		HostState.offlineLanRequested = true;
		if (ipc == null) {
			try {
				ipc = IpcServer.start(this::onChildMessage);
			} catch (Exception e) {
				NSplit.LOG.error("[host] failed to start IPC server", e);
			}
		}
		NSplit.LOG.info("[host] hosting couch co-op on LAN port {}", lanPort);
	}

	/** Adds a local player bound to {@code controllerUid} ({@code null} = unassigned yet). */
	public synchronized void addPlayer(String controllerUid) {
		if (lanPort <= 0) {
			NSplit.LOG.warn("[host] addPlayer before beginHosting(); open the world to LAN first");
			return;
		}
		if (children.size() + 1 >= NSplit.MAX_PLAYERS) {
			NSplit.LOG.warn("[host] max players reached ({})", NSplit.MAX_PLAYERS);
			return;
		}
		int slot = children.size() + 2; // host occupies slot 1
		String username = OfflineIdentity.childUsername(slot);
		UUID childId = UUID.randomUUID();
		try {
			Path gameDir = GameDirManager.prepare(username);
			ChildSpec spec = new ChildSpec(slot, username, childId,
					"localhost:" + lanPort, controllerUid, gameDir);
			Process p = InstanceLauncher.launch(spec);
			children.put(childId, new ChildHandle(slot, username, p, gameDir, controllerUid, false));
			NSplit.LOG.info("[host] spawned slot {} ({})", slot, username);
		} catch (Exception e) {
			NSplit.LOG.error("[host] failed to add player slot " + slot, e);
		}
	}

	private synchronized void onChildMessage(UUID childId, IpcMessage msg) {
		switch (msg.type()) {
			case HELLO -> NSplit.LOG.info("[host] child {} connected", childId);
			case READY -> {
				markReady(childId);
				retile();
			}
			case CLOSING -> {
				children.remove(childId);
				retile();
			}
			default -> {
			}
		}
	}

	private void markReady(UUID childId) {
		ChildHandle h = children.get(childId);
		if (h == null) {
			return;
		}
		children.put(childId, h.withReady());
		if (h.controllerUid() != null && ipc != null) {
			ipc.send(childId, IpcMessage.of(IpcMessage.Type.BIND, h.controllerUid()));
		}
	}

	/**
	 * Recomputes the grid for (host + children) and pushes each child's rect over IPC.
	 *
	 * <p>TODO(S3): {@link WindowTiler#primaryWorkArea()} and the host's own
	 * {@link WindowTiler#apply} call GLFW and MUST be marshalled onto the render thread
	 * (via {@code MinecraftClient.execute}); do that in the glue layer that invokes retile.
	 */
	public synchronized void retile() {
		if (ipc == null) {
			return;
		}
		int count = children.size() + 1;
		WindowTiler.Rect[] tiles = WindowTiler.computeTiles(count, layout, WindowTiler.primaryWorkArea());
		// tiles[0] is the host's own rect (applied by the S3 glue hook on the render thread).
		int i = 1;
		for (Map.Entry<UUID, ChildHandle> e : children.entrySet()) {
			if (i >= tiles.length) {
				break;
			}
			WindowTiler.Rect r = tiles[i++];
			ipc.send(e.getKey(), IpcMessage.of(IpcMessage.Type.RECT,
					String.valueOf(r.x()), String.valueOf(r.y()),
					String.valueOf(r.w()), String.valueOf(r.h())));
		}
		NSplit.LOG.info("[host] retiled for {} players ({})", count, layout);
	}

	public synchronized void setLayout(TileLayout layout) {
		this.layout = layout;
		retile();
	}

	public synchronized WindowTiler.Rect hostTile() {
		int count = children.size() + 1;
		return WindowTiler.computeTiles(count, layout, WindowTiler.primaryWorkArea())[0];
	}
}

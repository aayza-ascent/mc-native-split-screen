package io.ascent.nsplit.host;

import io.ascent.nsplit.NSplit;
import io.ascent.nsplit.controller.ControllerAssigner;
import io.ascent.nsplit.ipc.IpcMessage;
import io.ascent.nsplit.ipc.IpcServer;
import io.ascent.nsplit.launch.ChildSpec;
import io.ascent.nsplit.launch.GameDirManager;
import io.ascent.nsplit.launch.InstanceLauncher;
import io.ascent.nsplit.session.OfflineIdentity;
import io.ascent.nsplit.window.TileLayout;
import io.ascent.nsplit.window.WindowTiler;
import net.minecraft.client.MinecraftClient;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
	private String hostControllerUid;
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
		// Host window becomes unfocused once children spawn — keep reading its own pad.
		ControllerAssigner.enableBackgroundInput();
		this.hostControllerUid = ControllerAssigner.currentControllerUid().orElse(null);
		NSplit.LOG.info("[host] hosting couch co-op on LAN port {} (host pad: {})", lanPort, hostControllerUid);
	}

	public synchronized boolean isHosting() {
		return lanPort > 0;
	}

	public synchronized TileLayout getLayout() {
		return layout;
	}

	/** Snapshot of spawned children for UI display. */
	public synchronized List<ChildHandle> childHandles() {
		return new ArrayList<>(children.values());
	}

	/** Controller UIDs already in use (children + the host's own pad). */
	public synchronized Set<String> assignedControllerUids() {
		Set<String> s = children.values().stream()
				.map(ChildHandle::controllerUid)
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());
		if (hostControllerUid != null) {
			s.add(hostControllerUid);
		}
		return s;
	}

	/** Clears state when the host world closes, so a later world isn't forced offline. */
	public synchronized void reset() {
		lanPort = -1;
		hostControllerUid = null;
		HostState.offlineLanRequested = false;
		children.clear();
		if (ipc != null) {
			ipc.close();
			ipc = null;
		}
		NSplit.LOG.info("[host] couch co-op session reset");
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
		Set<String> assigned = assignedControllerUids();
		if (controllerUid == null) {
			// No pad specified (e.g. keyboard "Add Player") — auto-pick the next free one.
			controllerUid = ControllerAssigner.pickUnassigned(assigned).orElse(null);
		} else if (assigned.contains(controllerUid)) {
			NSplit.LOG.warn("[host] controller {} already assigned; ignoring duplicate join", controllerUid);
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
			final UUID id = childId;
			p.onExit().thenAccept(proc -> onChildExited(id, proc.exitValue()));
			NSplit.LOG.info("[host] spawned slot {} ({}); child boot takes a few seconds", slot, username);
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

	/** Logged when a child process exits — surfaces relaunch/connect failures (spike S4). */
	private synchronized void onChildExited(UUID childId, int exitCode) {
		ChildHandle h = children.remove(childId);
		if (h == null) {
			return;
		}
		if (h.ready()) {
			NSplit.LOG.info("[host] child slot {} ({}) exited (code {})", h.slot(), h.username(), exitCode);
		} else {
			NSplit.LOG.warn("[host] child slot {} ({}) exited BEFORE joining (code {}). Likely a "
							+ "relaunch/auth/connect failure — inspect the child console output above.",
					h.slot(), h.username(), exitCode);
		}
		retile();
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
	 * Recomputes the grid for (host + children), applies the host's own tile, and pushes
	 * each child's rect over IPC. State is snapshotted under lock, then all GLFW work runs
	 * on the render thread via {@code MinecraftClient.execute} (GLFW is render-thread-only).
	 */
	public void retile() {
		final List<UUID> order;
		final TileLayout lay;
		synchronized (this) {
			if (ipc == null) {
				return;
			}
			order = new ArrayList<>(children.keySet());
			lay = layout;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		mc.execute(() -> {
			int count = order.size() + 1;
			WindowTiler.Rect[] tiles = WindowTiler.computeTiles(count, lay, WindowTiler.primaryWorkArea());
			WindowTiler.apply(mc.getWindow().getHandle(), tiles[0]); // host occupies slot 1
			IpcServer server = ipc;
			for (int i = 0; i < order.size() && i + 1 < tiles.length; i++) {
				WindowTiler.Rect r = tiles[i + 1];
				if (server != null) {
					server.send(order.get(i), IpcMessage.of(IpcMessage.Type.RECT,
							String.valueOf(r.x()), String.valueOf(r.y()),
							String.valueOf(r.w()), String.valueOf(r.h())));
				}
			}
			NSplit.LOG.info("[host] retiled for {} players ({})", count, lay);
		});
	}

	public synchronized void setLayout(TileLayout layout) {
		this.layout = layout;
		retile();
	}
}

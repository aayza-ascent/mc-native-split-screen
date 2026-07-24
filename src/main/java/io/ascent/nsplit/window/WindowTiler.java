package io.ascent.nsplit.window;

import io.ascent.nsplit.NSplit;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes split-screen tile rectangles and positions borderless GLFW windows into them.
 *
 * <p><b>Retina trap (see plan R4):</b> all geometry here is in <i>logical screen points</i>
 * — {@code glfwGetMonitorWorkarea} and {@code glfwSetWindowMonitor} both use logical points.
 * Never feed framebuffer pixels (2× on Apple Silicon) into these calls or every tile is
 * double-sized and spills off-screen.
 *
 * <p>The work area (not the full display rect) is used so tiles respect the macOS menu bar
 * and Dock. A truly edge-to-edge grid additionally needs the user to auto-hide both — see
 * README. These calls touch only LWJGL/GLFW (mapping-independent) but MUST run on the
 * render/main thread (caller's responsibility — enforced by GLFW on macOS).
 */
public final class WindowTiler {
	private WindowTiler() {
	}

	/** A rectangle in logical screen points. */
	public record Rect(int x, int y, int w, int h) {
	}

	/** Primary-monitor work area in logical points (excludes menu bar/Dock when shown). */
	public static Rect primaryWorkArea() {
		long monitor = GLFW.glfwGetPrimaryMonitor();
		if (monitor == MemoryUtil.NULL) {
			return new Rect(0, 0, 1280, 720);
		}
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer x = stack.mallocInt(1);
			IntBuffer y = stack.mallocInt(1);
			IntBuffer w = stack.mallocInt(1);
			IntBuffer h = stack.mallocInt(1);
			GLFW.glfwGetMonitorWorkarea(monitor, x, y, w, h);
			return new Rect(x.get(0), y.get(0), w.get(0), h.get(0));
		}
	}

	/**
	 * Tile rectangles for {@code count} players (1..MAX) within {@code area}, ordered by
	 * slot (index 0 = slot 1/host). For 2 players the orientation honours {@code layout};
	 * 3–4 players always use the quadrant grid.
	 */
	public static Rect[] computeTiles(int count, TileLayout layout, Rect area) {
		count = Math.max(1, Math.min(count, NSplit.MAX_PLAYERS));
		final int ax = area.x(), ay = area.y(), aw = area.w(), ah = area.h();

		return switch (count) {
			case 1 -> new Rect[]{area};
			case 2 -> layout == TileLayout.VERTICAL
					? new Rect[]{
							new Rect(ax, ay, aw / 2, ah),
							new Rect(ax + aw / 2, ay, aw - aw / 2, ah)}
					: new Rect[]{
							new Rect(ax, ay, aw, ah / 2),
							new Rect(ax, ay + ah / 2, aw, ah - ah / 2)};
			case 3 -> new Rect[]{
					new Rect(ax, ay, aw / 2, ah / 2),
					new Rect(ax + aw / 2, ay, aw - aw / 2, ah / 2),
					new Rect(ax, ay + ah / 2, aw, ah - ah / 2)};
			default -> new Rect[]{
					new Rect(ax, ay, aw / 2, ah / 2),
					new Rect(ax + aw / 2, ay, aw - aw / 2, ah / 2),
					new Rect(ax, ay + ah / 2, aw / 2, ah - ah / 2),
					new Rect(ax + aw / 2, ay + ah / 2, aw - aw / 2, ah - ah / 2)};
		};
	}

	/** Number of connected monitors (>= 1). Render-thread only. */
	public static int monitorCount() {
		PointerBuffer monitors = GLFW.glfwGetMonitors();
		return monitors == null ? 1 : Math.max(1, monitors.limit());
	}

	/** Connected monitor names (index-aligned with the monitor indices used elsewhere). */
	public static List<String> monitorNames() {
		PointerBuffer monitors = GLFW.glfwGetMonitors();
		List<String> names = new ArrayList<>();
		if (monitors == null) {
			return names;
		}
		for (int i = 0; i < monitors.limit(); i++) {
			String nm = GLFW.glfwGetMonitorName(monitors.get(i));
			names.add(nm != null && !nm.isBlank() ? nm : ("Display " + (i + 1)));
		}
		return names;
	}

	/**
	 * Full-monitor rectangle per player for the "Per Display" layout, where
	 * {@code monitorIndexPerSlot[i]} is the monitor assigned to slot i (clamped to the available
	 * range). Logical points via each monitor's work area; {@code null} if monitors can't be
	 * enumerated. Render-thread only.
	 */
	public static Rect[] monitorRects(int[] monitorIndexPerSlot) {
		PointerBuffer monitors = GLFW.glfwGetMonitors();
		if (monitors == null || monitors.limit() == 0) {
			return null;
		}
		int n = monitors.limit();
		Rect[] out = new Rect[monitorIndexPerSlot.length];
		for (int i = 0; i < out.length; i++) {
			int mi = Math.max(0, Math.min(monitorIndexPerSlot[i], n - 1));
			long mon = monitors.get(mi);
			try (MemoryStack stack = MemoryStack.stackPush()) {
				IntBuffer x = stack.mallocInt(1);
				IntBuffer y = stack.mallocInt(1);
				IntBuffer w = stack.mallocInt(1);
				IntBuffer h = stack.mallocInt(1);
				GLFW.glfwGetMonitorWorkarea(mon, x, y, w, h);
				out[i] = new Rect(x.get(0), y.get(0), w.get(0), h.get(0));
			}
		}
		return out;
	}

	/**
	 * Makes the window borderless and moves/resizes it to {@code rect} (logical points).
	 * Must be called on the render/main thread.
	 *
	 * @param windowHandle raw GLFW handle (from {@code MinecraftClient.getWindow().getHandle()})
	 */
	public static void apply(long windowHandle, Rect rect) {
		if (windowHandle == MemoryUtil.NULL) {
			return;
		}
		// Borderless so tiles abut cleanly. Toggling DECORATED at runtime is flaky on Cocoa
		// (plan R5) — never do it while in native fullscreen; ensure a windowed state first.
		GLFW.glfwSetWindowAttrib(windowHandle, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
		GLFW.glfwSetWindowMonitor(windowHandle, MemoryUtil.NULL,
				rect.x(), rect.y(), rect.w(), rect.h(), GLFW.GLFW_DONT_CARE);
		NSplit.LOG.debug("[tiler] applied rect {}x{} @ ({},{})", rect.w(), rect.h(), rect.x(), rect.y());
	}
}

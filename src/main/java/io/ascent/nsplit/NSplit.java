package io.ascent.nsplit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mod-wide constants, the host/child role switch, and the system-property contract
 * shared between a host coordinator JVM and the child instances it spawns.
 *
 * <p>This class is intentionally free of any Minecraft API so it is safe to touch from
 * either the host or a child very early in startup, and so its behaviour is stable across
 * the 1.21.1–1.21.11 range (none of these names are remapped).
 */
public final class NSplit {
	public static final String MOD_ID = "nsplit";
	public static final Logger LOG = LoggerFactory.getLogger("NativeSplitScreen");

	/** Localhost-only control channel the host binds and children connect back to. */
	public static final String IPC_HOST = "127.0.0.1";
	public static final int IPC_PORT = 4761;

	public static final int MAX_PLAYERS = 4;

	// ---- System-property contract (host -> child, see InstanceLauncher) -------------
	/** {@code true} on a spawned child JVM; absent/false on the host. */
	public static final String PROP_CHILD = "nsplit.client";
	/** {@code host:port} of the host's open-to-LAN integrated server. */
	public static final String PROP_SERVER = "nsplit.server";
	/** Distinct offline username for this child (drives the server-side offline UUID). */
	public static final String PROP_USERNAME = "nsplit.username";
	/** Stable per-child id used to correlate the IPC connection with its ChildHandle. */
	public static final String PROP_UUID = "nsplit.uuid";
	/** Controlify controller UID this child should bind to (also re-sent over IPC). */
	public static final String PROP_CONTROLLER_UID = "nsplit.controllerUid";

	private NSplit() {
	}

	/** True when running inside a spawned split-screen child instance. */
	public static boolean isChild() {
		return Boolean.getBoolean(PROP_CHILD);
	}

	public static boolean isMac() {
		return System.getProperty("os.name", "").toLowerCase().contains("mac");
	}

	/** Reads a child config property, or {@code def} if unset. */
	public static String prop(String key, String def) {
		String v = System.getProperty(key);
		return (v == null || v.isBlank()) ? def : v;
	}
}

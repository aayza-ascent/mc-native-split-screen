package io.ascent.nsplit.launch;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Everything the host needs to spawn one child instance. Immutable value passed from
 * {@code SessionCoordinator} to {@link InstanceLauncher}.
 *
 * @param slot          1-based player slot (host is 1; children are 2..MAX_PLAYERS)
 * @param username      distinct offline username (see OfflineIdentity)
 * @param childId       per-child correlation id, echoed back over IPC as {@code Hello}
 * @param serverAddress {@code host:port} of the host's open-to-LAN server
 * @param controllerUid Controlify controller UID to bind, or {@code null} if unassigned
 * @param gameDir       isolated game directory for this child
 */
public record ChildSpec(
		int slot,
		String username,
		UUID childId,
		String serverAddress,
		String controllerUid,
		Path gameDir
) {
}

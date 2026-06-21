package io.ascent.nsplit.session;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Deterministic offline-account identity, matching vanilla's
 * {@code UUIDUtil.createOfflinePlayerUUID(name)}.
 *
 * <p>An {@code online-mode=false} server recomputes a joining player's UUID from the
 * username via this exact scheme, so two child instances MUST use distinct usernames —
 * identical names produce identical UUIDs and the second is kicked as a duplicate login.
 *
 * <p>Mapping-independent (pure JDK), so this is the single source of truth for offline
 * identity on both host and child.
 */
public final class OfflineIdentity {
	private OfflineIdentity() {
	}

	/** Vanilla offline UUID = nameUUIDFromBytes("OfflinePlayer:" + name). */
	public static UUID offlineUuid(String username) {
		return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * Generates a distinct couch-co-op username for slot N (1-based). The host is slot 1
	 * and keeps its real account; children are slot 2..MAX. Kept short and filename-safe
	 * because it also names the child's isolated game directory.
	 */
	public static String childUsername(int slot) {
		return "Player" + slot;
	}
}

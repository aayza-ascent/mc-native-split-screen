package io.ascent.nsplit.session;

import io.ascent.nsplit.NSplit;
import io.ascent.nsplit.mixin.MinecraftClientSessionAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;

import java.util.Optional;
import java.util.UUID;

/**
 * Installs a child's offline identity into the live client before it joins the host LAN.
 *
 * <p>The access token is a dummy ({@code "invalidtoken"}); an {@code online-mode=false}
 * server never validates it and recomputes the UUID from the username, so usernames must be
 * distinct per child (see {@link OfflineIdentity}).
 */
public final class OfflineSessionProvider {
	private OfflineSessionProvider() {
	}

	public static void installOffline(String username) {
		UUID uuid = OfflineIdentity.offlineUuid(username);
		Session session = new Session(
				username,
				uuid,
				"invalidtoken",
				Optional.empty(),
				Optional.empty(),
				Session.AccountType.LEGACY
		);
		((MinecraftClientSessionAccessor) MinecraftClient.getInstance()).nsplit$setSession(session);
		NSplit.LOG.info("[child] installed offline session for {} ({})", username, uuid);

		// TODO(S3): after swapping the session, also refresh dependent online services so
		// stale state doesn't leak — reset UserApiService, SocialInteractionsManager and
		// ProfileKeyPairManager as AuthMe's SessionUtils does.
	}
}

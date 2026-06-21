package io.ascent.nsplit.host;

/**
 * Tiny shared flag read by {@link io.ascent.nsplit.mixin.IntegratedServerOnlineModeMixin}.
 * Set true by {@link SessionCoordinator#beginHosting(int)} so the online-mode override only
 * fires for couch co-op, never for an ordinary "Open to LAN".
 */
public final class HostState {
	public static volatile boolean offlineLanRequested = false;

	private HostState() {
	}
}

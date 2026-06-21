package io.ascent.nsplit.ipc;

import java.util.Arrays;

/**
 * One line of the host&lt;-&gt;child control protocol. Deliberately a tiny newline-delimited
 * text format (no JSON/codec dependency, mapping-independent) carried over a localhost-only
 * socket. No session tokens ever cross this channel — children authenticate themselves
 * offline — so there is nothing sensitive on the wire.
 *
 * <p>Wire form: {@code TYPE arg0 arg1 ...\n} (space-separated; args are single tokens).
 */
public record IpcMessage(Type type, String[] args) {

	public enum Type {
		/** child -&gt; host: announces its child id so the host can map socket -&gt; ChildHandle. */
		HELLO,
		/** child -&gt; host: the child has joined the world and its window exists; triggers retile. */
		READY,
		/** host -&gt; child: apply this window rectangle (logical points). args: x y w h */
		RECT,
		/** host -&gt; child: bind to this Controlify controller UID. args: uid */
		BIND,
		/** child -&gt; host: the child is shutting down; remove it and retile. */
		CLOSING
	}

	public static IpcMessage of(Type type, String... args) {
		return new IpcMessage(type, args);
	}

	public String encode() {
		StringBuilder sb = new StringBuilder(type.name());
		for (String a : args) {
			sb.append(' ').append(a);
		}
		return sb.toString();
	}

	public static IpcMessage decode(String line) {
		String[] parts = line.trim().split("\\s+");
		Type type = Type.valueOf(parts[0]);
		return new IpcMessage(type, Arrays.copyOfRange(parts, 1, parts.length));
	}

	public String arg(int i) {
		return args[i];
	}

	public int intArg(int i) {
		return Integer.parseInt(args[i]);
	}
}

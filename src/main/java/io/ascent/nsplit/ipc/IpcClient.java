package io.ascent.nsplit.ipc;

import io.ascent.nsplit.NSplit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Child-side control client. Connects back to the host's {@link IpcServer}, sends
 * {@code HELLO <childId>}, and dispatches inbound {@code RECT}/{@code BIND} messages to a
 * handler on a background thread.
 */
public final class IpcClient implements AutoCloseable {
	private final Socket socket;
	private final Writer out;
	private volatile boolean running = true;

	private IpcClient(Socket socket, Writer out) {
		this.socket = socket;
		this.out = out;
	}

	/**
	 * Connects to the host, announces {@code childId}, and starts the read loop.
	 *
	 * @param onMessage invoked for each inbound message on a daemon thread
	 */
	public static IpcClient connect(UUID childId, Consumer<IpcMessage> onMessage) throws IOException {
		Socket socket = new Socket();
		socket.connect(new InetSocketAddress(NSplit.IPC_HOST, NSplit.IPC_PORT), 5000);
		Writer out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
		IpcClient client = new IpcClient(socket, out);
		client.send(IpcMessage.of(IpcMessage.Type.HELLO, childId.toString()));

		Thread t = new Thread(() -> client.readLoop(socket, onMessage), "nsplit-ipc-client");
		t.setDaemon(true);
		t.start();
		NSplit.LOG.info("[ipc] child {} connected to host", childId);
		return client;
	}

	private void readLoop(Socket socket, Consumer<IpcMessage> onMessage) {
		try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while (running && (line = in.readLine()) != null) {
				onMessage.accept(IpcMessage.decode(line));
			}
		} catch (Exception e) {
			if (running) {
				NSplit.LOG.warn("[ipc] child read loop ended: {}", e.toString());
			}
		}
	}

	public void send(IpcMessage msg) {
		try {
			synchronized (out) {
				out.write(msg.encode());
				out.write('\n');
				out.flush();
			}
		} catch (IOException e) {
			NSplit.LOG.warn("[ipc] child send failed: {}", e.toString());
		}
	}

	@Override
	public void close() {
		running = false;
		try {
			socket.close();
		} catch (IOException ignored) {
		}
	}
}

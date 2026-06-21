package io.ascent.nsplit.ipc;

import io.ascent.nsplit.NSplit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Host-side localhost control server. Binds {@code 127.0.0.1:IPC_PORT}, accepts one
 * connection per child, and tracks an open {@link Writer} per child id so the coordinator
 * can push {@code RECT}/{@code BIND} messages.
 *
 * <p>Bound to the loopback interface only; never exposed off-host.
 */
public final class IpcServer implements AutoCloseable {
	private final ServerSocket serverSocket;
	private final Map<UUID, Writer> writers = new ConcurrentHashMap<>();
	private volatile boolean running = true;

	/** Called on a background thread for every inbound message: (childId, message). */
	private final BiConsumer<UUID, IpcMessage> onMessage;

	private IpcServer(ServerSocket serverSocket, BiConsumer<UUID, IpcMessage> onMessage) {
		this.serverSocket = serverSocket;
		this.onMessage = onMessage;
	}

	public static IpcServer start(BiConsumer<UUID, IpcMessage> onMessage) throws IOException {
		ServerSocket ss = new ServerSocket(NSplit.IPC_PORT, 16, InetAddress.getByName(NSplit.IPC_HOST));
		IpcServer server = new IpcServer(ss, onMessage);
		Thread t = new Thread(server::acceptLoop, "nsplit-ipc-accept");
		t.setDaemon(true);
		t.start();
		NSplit.LOG.info("[ipc] host listening on {}:{}", NSplit.IPC_HOST, NSplit.IPC_PORT);
		return server;
	}

	private void acceptLoop() {
		while (running) {
			try {
				Socket socket = serverSocket.accept();
				Thread t = new Thread(() -> handle(socket), "nsplit-ipc-conn");
				t.setDaemon(true);
				t.start();
			} catch (IOException e) {
				if (running) {
					NSplit.LOG.warn("[ipc] accept failed: {}", e.toString());
				}
			}
		}
	}

	private void handle(Socket socket) {
		UUID childId = null;
		try (socket;
			 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
			Writer out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
			String line;
			while ((line = in.readLine()) != null) {
				IpcMessage msg = IpcMessage.decode(line);
				if (msg.type() == IpcMessage.Type.HELLO) {
					childId = UUID.fromString(msg.arg(0));
					writers.put(childId, out);
				}
				if (childId != null) {
					onMessage.accept(childId, msg);
				}
			}
		} catch (Exception e) {
			NSplit.LOG.warn("[ipc] connection error: {}", e.toString());
		} finally {
			if (childId != null) {
				writers.remove(childId);
			}
		}
	}

	/** Sends a message to a specific child; no-op if it has disconnected. */
	public void send(UUID childId, IpcMessage msg) {
		Writer w = writers.get(childId);
		if (w == null) {
			return;
		}
		try {
			synchronized (w) {
				w.write(msg.encode());
				w.write('\n');
				w.flush();
			}
		} catch (IOException e) {
			NSplit.LOG.warn("[ipc] send to {} failed: {}", childId, e.toString());
			writers.remove(childId);
		}
	}

	@Override
	public void close() {
		running = false;
		try {
			serverSocket.close();
		} catch (IOException ignored) {
		}
	}
}

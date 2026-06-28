package io.ascent.nsplit.launch;

import io.ascent.nsplit.NSplit;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Spawns a second full Minecraft client JVM that reuses this install's JDK, classpath,
 * Fabric loader and mod set, then auto-joins the host's offline LAN world as a distinct
 * offline player.
 *
 * <p>The command is reconstructed manually rather than from
 * {@code ProcessHandle.current().info().commandLine()} — that API is best-effort and
 * routinely drops {@code -cp}, which would silently spawn a child with no classpath. The
 * recipe (java.home + java.class.path + KnotClient + FabricLoader launch args, with
 * {@code --gameDir} rewritten) mirrors the proven Hot-Join launcher.
 *
 * <p>Mapping-independent: touches only the JDK and the (stable) Fabric Loader API, so it
 * is unaffected by the 1.21.5/1.21.6 client refactors.
 */
public final class InstanceLauncher {
	private static final String KNOT_CLIENT = "net.fabricmc.loader.impl.launch.knot.KnotClient";

	private InstanceLauncher() {
	}

	/** Builds and starts the child process. Caller owns the returned {@link Process}. */
	public static Process launch(ChildSpec spec) throws IOException {
		List<String> cmd = buildCommand(spec);
		NSplit.LOG.info("[launch] slot {} ({}) -> {} (dev={})",
				spec.slot(), spec.username(), spec.serverAddress(),
				FabricLoader.getInstance().isDevelopmentEnvironment());
		NSplit.LOG.info("[launch] {}", abbreviate(cmd));

		ProcessBuilder pb = new ProcessBuilder(cmd)
				.directory(spec.gameDir().toFile())
				.redirectOutput(ProcessBuilder.Redirect.INHERIT)
				.redirectError(ProcessBuilder.Redirect.INHERIT);
		// macOS: avoid double-enumeration of Xbox/Switch pads (GameController + HIDAPI), which
		// doubles controller input. Keep only the GameController path (also gives background input).
		pb.environment().put("SDL_JOYSTICK_HIDAPI_XBOX", "0");
		pb.environment().put("SDL_JOYSTICK_HIDAPI_SWITCH", "0");
		return pb.start();
	}

	/** Visible for testing/spike S4 — assembles the full argv without launching. */
	public static List<String> buildCommand(ChildSpec spec) {
		List<String> cmd = new ArrayList<>();

		// 1) Same JVM binary as the host (guarantees an identical, arm64-native runtime).
		cmd.add(javaBinary());

		// 2) JVM args.
		cmd.add("-Xmx2G");
		// macOS requires GLFW/AppKit on the process's first thread. Each spawned process
		// gets its own first thread, so this is correct and required per-child.
		if (NSplit.isMac()) {
			cmd.add("-XstartOnFirstThread");
		}
		// Per-child config consumed by NSplitClient on the child.
		cmd.add(prop(NSplit.PROP_CHILD, "true"));
		cmd.add(prop(NSplit.PROP_SERVER, spec.serverAddress()));
		cmd.add(prop(NSplit.PROP_USERNAME, spec.username()));
		cmd.add(prop(NSplit.PROP_UUID, spec.childId().toString()));
		if (spec.controllerUid() != null && !spec.controllerUid().isBlank()) {
			cmd.add(prop(NSplit.PROP_CONTROLLER_UID, spec.controllerUid()));
		}

		// Dev-environment relaunch: launch KnotClient directly (not via DevLaunchInjector)
		// and forward the properties Loom's launch.cfg would otherwise apply. The
		// mod-under-development lives on java.class.path (build/classes/java/main +
		// build/resources/main) and is discovered from there, so only PATH-origin mods NOT
		// already on the classpath need -Dfabric.addMods. fabric.remapClasspathFile is
		// REQUIRED in dev — KnotClient uses it to remap the intermediary classpath to named.
		// No-ops in production. (Verified against this project's .gradle/loom-cache/launch.cfg.)
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			cmd.add("-Dfabric.development=true");
			String addMods = classpathOriginMods();
			if (!addMods.isEmpty()) {
				cmd.add("-Dfabric.addMods=" + addMods);
			}
			forwardIfPresent(cmd, "fabric.remapClasspathFile");
			forwardIfPresent(cmd, "fabric.classPathGroups");   // only set in multi-mod projects
			forwardIfPresent(cmd, "log4j.configurationFile");  // keep child logs consistent
			forwardIfPresent(cmd, "log4j2.formatMsgNoLookups");
		}

		// 3) Classpath + Fabric main class.
		String classpath = System.getProperty("java.class.path", "");
		if (classpath.isBlank()) {
			throw new IllegalStateException("java.class.path is empty; cannot reconstruct the child classpath");
		}
		cmd.add("-cp");
		cmd.add(classpath);
		cmd.add(KNOT_CLIENT);

		// 4) Program args = host's launch args with --gameDir pointed at the child dir.
		cmd.addAll(rewriteGameDir(FabricLoader.getInstance().getLaunchArguments(false), spec.gameDir()));
		return cmd;
	}

	/** Renders the command for logging, collapsing the long classpath to an entry count. */
	private static String abbreviate(List<String> cmd) {
		List<String> out = new ArrayList<>(cmd.size());
		for (int i = 0; i < cmd.size(); i++) {
			String a = cmd.get(i);
			if (i > 0 && "-cp".equals(cmd.get(i - 1)) && a.length() > 80) {
				out.add("<classpath: " + a.split(java.io.File.pathSeparator).length + " entries>");
			} else {
				out.add(a);
			}
		}
		return String.join(" ", out);
	}

	private static String javaBinary() {
		Path home = Paths.get(System.getProperty("java.home"), "bin");
		for (String name : new String[]{"javaw.exe", "java.exe", "java"}) {
			Path p = home.resolve(name);
			if (Files.isExecutable(p)) {
				return p.toString();
			}
		}
		// Fall back to bare "java" on PATH.
		return "java";
	}

	private static String prop(String key, String value) {
		return "-D" + key + "=" + value;
	}

	private static void forwardIfPresent(List<String> cmd, String key) {
		String v = System.getProperty(key);
		if (v != null && !v.isBlank()) {
			cmd.add("-D" + key + "=" + v);
		}
	}

	/**
	 * Replaces the value following {@code --gameDir} with the child's directory, or appends
	 * the pair if absent. A copy is returned; the source array is untouched.
	 */
	private static List<String> rewriteGameDir(String[] launchArgs, Path gameDir) {
		List<String> out = new ArrayList<>(List.of(launchArgs));
		int idx = out.indexOf("--gameDir");
		if (idx >= 0 && idx + 1 < out.size()) {
			out.set(idx + 1, gameDir.toString());
		} else {
			out.add("--gameDir");
			out.add(gameDir.toString());
		}
		return out;
	}

	/**
	 * Absolute paths of PATH-origin mods not already on the JVM classpath, joined with the
	 * platform path separator — fed to the child via {@code -Dfabric.addMods}.
	 */
	private static String classpathOriginMods() {
		Set<String> classpath = new LinkedHashSet<>(List.of(
				System.getProperty("java.class.path", "").split(java.io.File.pathSeparator)));
		Set<String> add = new LinkedHashSet<>();
		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			String id = mod.getMetadata().getId();
			if (id.equals("minecraft") || id.equals("java") || id.equals("fabricloader") || id.equals("mixinextras")) {
				continue;
			}
			if (mod.getOrigin().getKind() != ModOrigin.Kind.PATH) {
				continue;
			}
			for (Path path : mod.getOrigin().getPaths()) {
				String abs = path.toAbsolutePath().toString();
				if (!classpath.contains(abs)) {
					add.add(abs);
				}
			}
		}
		return String.join(java.io.File.pathSeparator, add);
	}
}

package io.ascent.nsplit.launch;

import io.ascent.nsplit.NSplit;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

/**
 * Creates and seeds an isolated game directory per child, under
 * {@code <gameDir>/.nsplit-instances/<username>/}.
 *
 * <p>Per-child isolation is the documented fix for shared-config clobbering (e.g. two
 * instances racing on {@code options.txt} or {@code controlify.json}; cf. Controlify #66).
 * Selected directories/files are copied once, on first creation, so each child boots with
 * the host's resource packs, shader packs and options but writes its own state.
 *
 * <p>Mapping-independent (JDK + Fabric Loader API only).
 */
public final class GameDirManager {
	private static final String INSTANCES_DIR = ".nsplit-instances";

	/** Top-level entries copied from the host gameDir into a fresh child gameDir. */
	private static final List<String> SEED_ENTRIES = List.of(
			"options.txt",
			"config",
			"resourcepacks",
			"shaderpacks",
			"controlify-natives"
	);

	private GameDirManager() {
	}

	/** Host game directory root (e.g. the Minecraft instance dir). */
	public static Path hostGameDir() {
		return FabricLoader.getInstance().getGameDir();
	}

	/**
	 * Returns the child's game directory, creating and seeding it from the host on first
	 * use. Idempotent: an already-seeded directory is returned untouched.
	 */
	public static Path prepare(String username) throws IOException {
		Path host = hostGameDir();
		Path childDir = host.resolve(INSTANCES_DIR).resolve(sanitize(username));
		boolean fresh = !Files.exists(childDir);
		Files.createDirectories(childDir);

		if (fresh) {
			NSplit.LOG.info("[gamedir] seeding new child dir {}", childDir);
			for (String entry : SEED_ENTRIES) {
				copyIfPresent(host.resolve(entry), childDir.resolve(entry));
			}
		}
		return childDir;
	}

	private static void copyIfPresent(Path src, Path dst) {
		if (!Files.exists(src)) {
			return;
		}
		try {
			if (Files.isDirectory(src)) {
				copyTree(src, dst);
			} else {
				Files.createDirectories(dst.getParent());
				Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			NSplit.LOG.warn("[gamedir] failed to seed {} -> {}: {}", src, dst, e.toString());
		}
	}

	private static void copyTree(Path srcRoot, Path dstRoot) throws IOException {
		try (Stream<Path> walk = Files.walk(srcRoot)) {
			for (Path src : (Iterable<Path>) walk::iterator) {
				Path dst = dstRoot.resolve(srcRoot.relativize(src).toString());
				if (Files.isDirectory(src)) {
					Files.createDirectories(dst);
				} else {
					Files.createDirectories(dst.getParent());
					Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
	}

	private static String sanitize(String name) {
		return name.replaceAll("[^a-zA-Z0-9_.-]", "_");
	}
}

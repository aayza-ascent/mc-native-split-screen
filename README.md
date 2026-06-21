# Native Split Screen

Console-style **local couch co-op** for Minecraft Java Edition — a single client-side Fabric
`.jar` that opens the host world to a private offline LAN, then launches and tiles additional
Minecraft client instances (one controller each). macOS Apple Silicon is the primary target;
Controlify provides the controller layer.

> **What this is (and isn't).** This is the *pragmatic bundled multi-process* approach: each
> player is a **real, separate Minecraft client** whose window is tiled into a grid — not one
> window with multiple in-engine viewports. True in-process split-screen was assessed as a
> multi-year effort and a dead-end on macOS (single GL context + Cocoa first-thread rule); see
> the implementation plan. The upside: independent camera / inventory / HUD / pause are **free**
> because each player is a genuine client.

Full design, risks, and roadmap: `~/.claude/plans/i-want-to-create-zazzy-pony.md`.

## Status

Phase 0 scaffold. The build produces a loadable client mod, but **gameplay is gated on the
Phase-0 spikes below** — run **S1 first**, it is the project's kill switch.

| Area | State |
|---|---|
| Process launch / classpath reconstruction (`InstanceLauncher`) | implemented |
| Per-child isolated game dir + seeding (`GameDirManager`) | implemented |
| Host↔child IPC (`IpcServer`/`IpcClient`, localhost-only) | implemented |
| Window tiling math + GLFW apply (`WindowTiler`) | implemented |
| Offline identity + session swap (`OfflineIdentity`, `OfflineSessionProvider`, accessor mixin) | implemented |
| Offline-LAN online-mode override (`IntegratedServerOnlineModeMixin`) | implemented (refmap-resolved; runtime spike S3 pending) |
| Host hook: open offline LAN + begin hosting (`/couchcoop` command) | implemented (S3) |
| Auto-connect to host LAN (`AutoConnector` → `ConnectScreen.connect`) | implemented (S3); JOIN/DISCONNECT lifecycle in `ChildBootstrap` |
| Controller enumeration/assignment (`ControllerAssigner`, `ControllerBinder`) | **stub — Phase 1** (needs Controlify dep) |
| "Open Couch Co-op" button + full Join UX | **stub — Phase 2** (command is the interim entry) |

Search the source for `TODO(S3)` and `Phase1`/`Phase 2` to find every open seam.

## Build

```bash
./gradlew build          # compile + jar (downloads MC, mappings, Java 21 toolchain on first run)
./gradlew runClient      # launch a dev client (host mode)
```

- Java 21 is auto-provisioned by the Gradle toolchain (foojay), so no local JDK 21 is needed.
- Output jar: `build/libs/native-split-screen-<version>.jar`.

## Phase-0 spikes (run before building gameplay)

Each is a throwaway experiment with a go/no-go kill criterion.

- **S1 — controller in an *unfocused* window (make-or-break).** Two instances, Controlify SDL
  backend + `out_of_focus_input=true`, two **different-model** pads. Focus window A; confirm pad
  B drives player B and pad A drives player A, independently. **No-go → stop.**
- **S2 — borderless tiled grid, Retina-correct.** Verify `WindowTiler` produces non-doubled
  side-by-side tiles from `glfwGetMonitorWorkarea` (logical points), menu bar + Dock auto-hidden.
- **S3 — offline child auto-join (code in place; run it).** With two JDK-24 terminals or via
  `runClient`: launch the dev client, create/open a singleplayer world, then run
  **`/couchcoop start`** (opens the world to LAN with `online-mode=false`) and **`/couchcoop add`**
  (spawns a child instance that injects an offline session and auto-joins). Expect a second window
  that joins as `Player2` with a distinct UUID and no duplicate-login kick. `/couchcoop layout
  horizontal|vertical|grid` retiles. **Note:** in the Loom dev env the child relaunch relies on
  `-Dfabric.addMods` forwarding (see `InstanceLauncher`) — that path itself is **spike S4**.
- **S4 — one-jar dual-role relaunch in dev.** `InstanceLauncher` spawns a child from `runClient`
  (forwarding `-Dfabric.addMods`); confirm the child loads this mod and enters child mode.

## Version lanes & next dependencies

- Current lane: **MC 1.21.1**, Yarn mappings, Loom 1.11.8, Loader 0.16.14, Fabric API 0.116.12.
- **Controlify** (`dev.isxander:controlify:3.0.0+lts`, covers 1.21–1.21.11) is commented out in
  `build.gradle`; uncomment when implementing `ControllerAssigner` (Phase 1).
- **Multiversion 1.21.1→1.21.11** (Stonecutter) and the **Mojang-mappings + Parchment** migration
  are Phase 5 — the render pipeline is never touched, so only a few seams need gating
  (`openToLan`/`setOnlineMode`, `ConnectScreen.connect`, `Window`, `ServerInfo`). The scaffold
  uses Yarn for 1.21.1 dev velocity; migrate at Phase 5 per the plan.

## Layout

```
src/main/java/io/ascent/nsplit/
  NSplit.java               role switch + system-property contract (mapping-free)
  NSplitClient.java         dual-role client entrypoint
  launch/                   InstanceLauncher, ChildSpec, GameDirManager   (mapping-free)
  ipc/                      IpcServer, IpcClient, IpcMessage              (mapping-free)
  window/                   WindowTiler, TileLayout                       (LWJGL/GLFW only)
  session/                  OfflineIdentity, OfflineSessionProvider
  host/                     SessionCoordinator, HostCoordinator, HostState
  client/                   ChildBootstrap, AutoConnector, ControllerBinder
  controller/               ControllerAssigner (stub)
  mixin/                    MinecraftClientSessionAccessor, IntegratedServerOnlineModeMixin
```

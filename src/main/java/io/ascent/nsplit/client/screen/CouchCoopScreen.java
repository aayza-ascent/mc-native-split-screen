package io.ascent.nsplit.client.screen;

import io.ascent.nsplit.NSplit;
import io.ascent.nsplit.controller.ControllerAssigner;
import io.ascent.nsplit.host.CouchCoopController;
import io.ascent.nsplit.host.SessionCoordinator;
import io.ascent.nsplit.host.SessionCoordinator.ChildHandle;
import io.ascent.nsplit.window.TileLayout;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Optional;

/**
 * Console-style Join screen: shows the player slots, lets the host start the offline-LAN
 * session, cycle the split layout, and add a keyboard player. Controllers join hands-free
 * via the "press Start" gesture ({@link io.ascent.nsplit.controller.JoinGesturePoll}); this
 * screen is the visual + keyboard-fallback front end.
 */
public final class CouchCoopScreen extends Screen {
	private final Screen parent;
	private String status = "";
	private int monitors = 1;
	private List<String> monitorNamesList = List.of();

	private ButtonWidget startButton;
	private ButtonWidget addButton;
	private ButtonWidget layoutButton;
	private ButtonWidget[] monitorButtons;

	public CouchCoopScreen(Screen parent) {
		super(Text.literal("Native Split Screen — Couch Co-op"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		monitors = io.ascent.nsplit.window.WindowTiler.monitorCount(); // render thread — safe
		monitorNamesList = io.ascent.nsplit.window.WindowTiler.monitorNames();
		int bw = 240;
		int bx = this.width / 2 - bw / 2;

		layoutButton = ButtonWidget.builder(layoutLabel(SessionCoordinator.get().getLayout()), b -> {
			TileLayout next = cycle(SessionCoordinator.get().getLayout());
			SessionCoordinator.get().setLayout(next);
			b.setMessage(layoutLabel(next));
		}).dimensions(bx, this.height - 116, bw, 20).build();

		addButton = ButtonWidget.builder(Text.literal("Add Player (keyboard)"), b -> {
			SessionCoordinator.get().addPlayer(null);
			status = "Spawning a player instance (takes a few seconds)...";
		}).dimensions(bx, this.height - 92, bw, 20).build();

		startButton = ButtonWidget.builder(Text.literal("Start Couch Co-op"), b -> onStart())
				.dimensions(bx, this.height - 68, bw, 20).build();

		ButtonWidget doneButton = ButtonWidget.builder(Text.literal("Done"), b -> close())
				.dimensions(bx, this.height - 32, bw, 20).build();

		addDrawableChild(layoutButton);
		addDrawableChild(addButton);
		addDrawableChild(startButton);
		addDrawableChild(doneButton);

		// Per-player monitor assignment (Per-Display layout). One cycle button per slot, shown
		// only when Per-Display is active and 2+ monitors exist (toggled in render()).
		monitorButtons = new ButtonWidget[NSplit.MAX_PLAYERS];
		for (int s = 1; s <= NSplit.MAX_PLAYERS; s++) {
			final int slot = s;
			ButtonWidget b = ButtonWidget.builder(Text.literal(monitorButtonLabel(slot)), btn -> {
				int cnt = Math.max(1, monitors);
				SessionCoordinator.get().setPlayerMonitor(slot,
						(SessionCoordinator.get().getPlayerMonitor(slot) + 1) % cnt);
				btn.setMessage(Text.literal(monitorButtonLabel(slot)));
			}).dimensions(bx, 152 + (slot - 1) * 22, bw, 20).build();
			b.visible = false;
			monitorButtons[slot - 1] = b;
			addDrawableChild(b);
		}
	}

	private void onStart() {
		Optional<Text> error = CouchCoopController.startHosting();
		status = error.map(Text::getString)
				.orElse("Hosting on LAN (offline). Press Start on a controller to join.");
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		SessionCoordinator coord = SessionCoordinator.get();
		List<ChildHandle> children = coord.childHandles();
		int count = children.size() + 1;
		boolean perDisplay = coord.getLayout() == TileLayout.PER_DISPLAY && monitors >= 2;

		// Widget state must be set BEFORE super.render() draws the widgets.
		startButton.active = !coord.isHosting();
		addButton.active = coord.isHosting();
		if (monitorButtons != null) {
			for (int s = 1; s <= NSplit.MAX_PLAYERS; s++) {
				ButtonWidget b = monitorButtons[s - 1];
				boolean show = perDisplay && s <= count;
				b.visible = show;
				b.active = show;
				if (show) {
					b.setMessage(Text.literal(monitorButtonLabel(s)));
				}
			}
		}

		super.render(ctx, mouseX, mouseY, delta); // background + widgets

		int cx = this.width / 2;
		ctx.drawCenteredTextWithShadow(textRenderer, getTitle(), cx, 18, 0xFFFFFF);
		String sub = coord.isHosting()
				? "Hosting on LAN (offline). Press Start on a controller to join."
				: "Open a singleplayer world, then Start Couch Co-op.";
		ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(sub), cx, 34, 0xA0A0A0);

		String disp = "Displays detected: " + monitors;
		if (coord.getLayout() == TileLayout.PER_DISPLAY && monitors < count) {
			disp += "  (need one per player — using grid)";
		}
		ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(disp), cx, 46,
				monitors >= 2 ? 0x80C0FF : 0xA0A0A0);

		int y = 60;
		ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("P1 — Host (you)"), cx, y, 0x55FF55);
		y += 20;
		for (int slot = 2; slot <= NSplit.MAX_PLAYERS; slot++) {
			ChildHandle h = childAt(children, slot);
			Text line;
			int color;
			if (h != null) {
				String pad = h.controllerUid() == null ? "keyboard" : ControllerAssigner.nameOf(h.controllerUid());
				String state = h.ready() ? "in game" : "starting…";
				line = Text.literal("P" + slot + " — " + h.username() + "  [" + pad + "]  " + state);
				color = h.ready() ? 0x55FF55 : 0xFFAA00;
			} else if (coord.isHosting()) {
				line = Text.literal("P" + slot + " — empty (press Start to join)");
				color = 0x808080;
			} else {
				line = Text.literal("P" + slot + " — —");
				color = 0x505050;
			}
			ctx.drawCenteredTextWithShadow(textRenderer, line, cx, y, color);
			y += 20;
		}

		if (perDisplay) {
			ctx.drawCenteredTextWithShadow(textRenderer,
					Text.literal("Assign players to displays:"), cx, 138, 0x80C0FF);
		}

		if (!status.isEmpty()) {
			ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(status), cx, this.height - 140, 0xFFFF55);
		}
	}

	private String monitorButtonLabel(int slot) {
		int idx = SessionCoordinator.get().getPlayerMonitor(slot);
		String name = (idx >= 0 && idx < monitorNamesList.size())
				? monitorNamesList.get(idx)
				: ("Display " + (idx + 1));
		return "P" + slot + "  →  " + name;
	}

	@Override
	public void close() {
		this.client.setScreen(parent);
	}

	private static ChildHandle childAt(List<ChildHandle> children, int slot) {
		for (ChildHandle h : children) {
			if (h.slot() == slot) {
				return h;
			}
		}
		return null;
	}

	private static TileLayout cycle(TileLayout layout) {
		return switch (layout) {
			case HORIZONTAL -> TileLayout.VERTICAL;
			case VERTICAL -> TileLayout.GRID;
			case GRID -> TileLayout.PER_DISPLAY;
			case PER_DISPLAY -> TileLayout.HORIZONTAL;
		};
	}

	private static Text layoutLabel(TileLayout layout) {
		String name = switch (layout) {
			case HORIZONTAL -> "Horizontal (top/bottom)";
			case VERTICAL -> "Vertical (left/right)";
			case GRID -> "Grid";
			case PER_DISPLAY -> "Per Display (one per monitor)";
		};
		return Text.literal("Layout: " + name);
	}
}

package io.ascent.nsplit.window;

/** Hot-switchable split arrangements (see plan: Horizontal / Vertical / 4-grid / Custom). */
public enum TileLayout {
	/** 2 players stacked top/bottom; falls back to grid for 3–4. */
	HORIZONTAL,
	/** 2 players side-by-side left/right; falls back to grid for 3–4. */
	VERTICAL,
	/** Quadrant grid (3 = two-top + one-bottom, 4 = quadrants). */
	GRID
}

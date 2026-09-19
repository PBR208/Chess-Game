package ui.theme;

/*
 * Purpose: Theme holds the colours every screen shares, so the look of the program lives in one file
 * instead of being spelled out again on each panel. It also owns the three colours the board marks
 * squares with, which are chosen so they can be told apart by players who cannot separate red from
 * green, the most common form of colour blindness. Those three differ in brightness as well as in
 * hue, so they are still distinguishable on a monochrome screen or to somebody who sees no colour.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.1
 */

import java.awt.Color;

public final class Theme {

    private Theme() {
    }

    public static final Color BG = new Color(28, 28, 30);
    public static final Color PANEL_BG = new Color(38, 38, 42);
    public static final Color FG = new Color(255, 255, 255);
    public static final Color ACCENT = new Color(81, 168, 0);
    public static final Color MUTED = new Color(110, 110, 115);

    // shared "neutral/secondary" button background
    public static final Color BUTTON_SECONDARY = new Color(60, 60, 65);

    // The board highlights come from the Okabe and Ito palette, which was picked so that every pair
    // of its colours stays distinguishable with any of the common kinds of colour blindness. The
    // hints used to be the same green as the accent, and green against a red check marker is exactly
    // the pair a red green blind player cannot separate, so the hints are blue now.

    // squares a picked up piece may move to
    public static final Color HINT = new Color(0, 114, 178, 200);

    // the two squares of the move that was just played
    public static final Color LAST_MOVE = new Color(230, 159, 0, 160);

    // the square of a king that is in check
    public static final Color CHECK = new Color(213, 94, 0, 200);
}

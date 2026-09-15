package ui.board;

/*
 * Purpose: PromoteGUI is the small modal dialog a player sees when a pawn reaches the last rank.
 * It shows the four promotion pieces as sprite buttons and hands the picked piece back to the
 * caller. I keep it as a separate dialog so the rules engine never touches Swing and only receives
 * the answer through SwingPromotionChooser. Every button also carries the piece name as its
 * component name, so the icon-only buttons stay identifiable for tests and accessibility tools.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import engine.pieces.Piece;
import ui.theme.Theme;
import ui.theme.UiComponents;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class PromoteGUI extends JDialog {

    public enum Choice {
        QUEEN, ROOK, BISHOP, KNIGHT
    }

    private Choice choice;

    // Sprite column indices for each piece type
    private static final int QUEEN_SPRITE = 1;
    private static final int ROOK_SPRITE = 4;
    private static final int BISHOP_SPRITE = 2;
    private static final int KNIGHT_SPRITE = 3;

    /**
     * Builds the promotion dialog for White.
     * <p>
     * Older callers and the dialog tests don't say which side promotes. I forward to the full
     * constructor with White's pieces.
     * <p>
     * Time complexity: O(s^2) where s is the tile size, for scaling the four sprites.
     * Space complexity: O(s^2) for the four scaled icon images.
     *
     * @param pParent   frame the modal dialog belongs to and is centered on; may be null, in
     *                  which case Swing falls back to its shared hidden owner frame
     * @param pTileSize edge length of one board tile in pixels, expected to be greater than 0
     * @throws HeadlessException if the JVM has no display, since dialogs need a real window
     */
    public PromoteGUI(JFrame pParent, int pTileSize) {
        this(pParent, pTileSize, true);
    }

    /**
     * Builds the promotion dialog with one button per promotion piece in the promoting side's colour.
     * <p>
     * The player has to decide what a promoting pawn becomes before the move can finish, and the
     * choices should look like that player's own pieces. I slice each piece icon from the white or
     * black row of the shared sprite sheet, wrap it in a themed button named after the piece, wire
     * every button to store its choice and close the dialog, and then size the dialog to four tiles
     * and center it over the parent frame.
     * <p>
     * Time complexity: O(s^2) where s is the tile size, dominated by scaling the four sprites.
     * Space complexity: O(s^2) for the four scaled icon images.
     *
     * @param pParent   frame the modal dialog belongs to and is centered on; may be null, in
     *                  which case Swing falls back to its shared hidden owner frame
     * @param pTileSize edge length of one board tile in pixels, expected to be greater than 0
     * @param pWhite    true if White promotes, false if Black does
     * @throws HeadlessException if the JVM has no display, since dialogs need a real window
     */
    public PromoteGUI(JFrame pParent, int pTileSize, boolean pWhite) {
        // modal, so the move waits for the player's choice
        super(pParent, true);

        // spacing between the four buttons scales with the tile size
        int gap = pTileSize / 8;
        setLayout(new GridLayout(1, 4, gap, gap));
        setUndecorated(true);
        getContentPane().setBackground(Theme.PANEL_BG);

        BufferedImage spritesheet = Piece.getSpritesheet();
        int scale = Piece.getSpritesheetScale();

        // each button shows the promoting side's piece and is named after it, since it has no text
        JButton queen = createPieceButton(getPieceImage(spritesheet, scale, QUEEN_SPRITE, pTileSize, pWhite), "Queen");
        JButton rook = createPieceButton(getPieceImage(spritesheet, scale, ROOK_SPRITE, pTileSize, pWhite), "Rook");
        JButton bishop = createPieceButton(getPieceImage(spritesheet, scale, BISHOP_SPRITE, pTileSize, pWhite), "Bishop");
        JButton knight = createPieceButton(getPieceImage(spritesheet, scale, KNIGHT_SPRITE, pTileSize, pWhite), "Knight");

        queen.addActionListener(e -> {
            choice = Choice.QUEEN;
            dispose();
        });
        rook.addActionListener(e -> {
            choice = Choice.ROOK;
            dispose();
        });
        bishop.addActionListener(e -> {
            choice = Choice.BISHOP;
            dispose();
        });
        knight.addActionListener(e -> {
            choice = Choice.KNIGHT;
            dispose();
        });

        add(queen);
        add(rook);
        add(bishop);
        add(knight);

        // four tiles wide, one tile high, centered over the board
        setSize(pTileSize * 4, pTileSize);
        setLocationRelativeTo(pParent);
    }

    /**
     * Cuts one piece icon out of the sprite sheet and scales it to the tile size.
     * <p>
     * The dialog shows the pieces a pawn can become in the colour of the promoting side, but it
     * always cut from the white row before. The sheet has the white pieces in its top row and the
     * black pieces in the row below. I cut the square for the requested column and colour, scale it
     * smoothly and draw it into a new image with transparency, so the button can use it as its icon.
     * <p>
     * Time complexity: O(s^2) where s is the tile size. Space complexity: O(s^2) for the scaled icon.
     *
     * @param pSpritesheet shared sprite sheet with six columns and two rows, never null
     * @param pScale       edge length of one sprite in the sheet in pixels, greater than 0
     * @param pSpriteCol   sprite column of the piece, 0 to 5
     * @param pTileSize    edge length of the icon in pixels, greater than 0
     * @param pWhite       true for the white row of the sheet, false for the black row
     * @return the scaled icon image, never null
     * @throws java.awt.image.RasterFormatException if the requested sprite lies outside the sheet
     */
    private BufferedImage getPieceImage(BufferedImage pSpritesheet, int pScale, int pSpriteCol, int pTileSize,
                                        boolean pWhite) {
        // white pieces sit in the top row, black pieces in the row below
        int spriteRow = pWhite ? 0 : pScale;
        BufferedImage sprite = pSpritesheet.getSubimage(pSpriteCol * pScale, spriteRow, pScale, pScale);
        Image scaled = sprite.getScaledInstance(pTileSize, pTileSize, Image.SCALE_SMOOTH);
        BufferedImage result = new BufferedImage(pTileSize, pTileSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = result.createGraphics();
        g2d.drawImage(scaled, 0, 0, null);
        g2d.dispose();
        return result;
    }

    /**
     * Creates one themed, icon-only promotion button.
     * <p>
     * Each option has to match the dark UI and still be identifiable without visible text. I put
     * the icon on a flat button, store the piece name as both the component name and the
     * accessible name, and then apply the theme colours, cursor and hover effect.
     * <p>
     * Time complexity: O(1). Space complexity: O(1) apart from the button itself.
     *
     * @param pIcon piece image shown on the button, never null
     * @param pName piece name such as "Queen", used to identify the button; never null
     * @return the fully styled button, never null
     */
    private JButton createPieceButton(BufferedImage pIcon, String pName) {
        // the sprite is the only visible label
        JButton btn = new JButton(new ImageIcon(pIcon));
        // name the button so tests and screen readers can find it without text
        btn.setName(pName);
        btn.getAccessibleContext().setAccessibleName(pName);
        btn.setBackground(Theme.BUTTON_SECONDARY);
        btn.setForeground(Theme.FG);
        btn.setContentAreaFilled(false);
        btn.setOpaque(true);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        UiComponents.addHoverEffect(btn);
        return btn;
    }

    public Choice showDialog() {
        setVisible(true);
        return choice;
    }
}
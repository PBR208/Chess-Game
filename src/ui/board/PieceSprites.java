package ui.board;

/*
 * Purpose: PieceSprites owns everything about drawing chess pieces. It loads the shared sprite sheet
 * from the classpath once, cuts the square for a piece type and colour out of it and scales that
 * square to the size the board currently uses. It speaks the engine core's piece codes, so the board
 * can draw straight from a position without turning bitboards back into objects first. Scaled
 * sprites are cached per board, because the scaling is by far the most expensive part of painting.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 2.0
 */

import engine.core.Pieces;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

public class PieceSprites {

    // classpath location of the sprite sheet, the same on every OS
    public static final String SPRITE_SHEET_PATH = "/resources/pieces.png";

    // the sheet holds one column per piece type, white pieces above black ones
    private static final int SHEET_COLUMNS = 6;

    private static final BufferedImage SHEET;
    private static final int SHEET_SCALE;

    static {
        // no piece can be drawn without sprites, so a missing sheet has to fail loudly
        SHEET = loadSpriteSheet(SPRITE_SHEET_PATH);
        // six columns of equal width, one per piece type
        SHEET_SCALE = SHEET.getWidth() / SHEET_COLUMNS;
    }

    // edge length in pixels that every sprite of this instance is scaled to
    private final int tileSize;

    // one cached sprite per colour and piece type, filled on first use
    private final BufferedImage[][] cache = new BufferedImage[2][Pieces.TYPE_COUNT];

    /**
     * Creates a sprite source that scales every piece to one square size.
     * <p>
     * A board draws all of its pieces at the size of its own squares, and that size never changes
     * while a game is on screen. I remember the square size and start with an empty cache, so the
     * first request for a piece scales it and every later one reuses the result.
     * <p>
     * Time complexity: O(1). Space complexity: O(1) for the empty cache.
     *
     * @param pTileSize edge length of one board square in pixels, greater than 0
     * @throws IllegalArgumentException if pTileSize is not greater than 0
     */
    public PieceSprites(int pTileSize) {
        // a sprite without pixels cannot be drawn
        if (pTileSize <= 0) {
            throw new IllegalArgumentException("tile size " + pTileSize + " must be greater than 0");
        }
        this.tileSize = pTileSize;
    }

    /**
     * Loads a sprite sheet image from the classpath.
     * <p>
     * Every piece is drawn from one shared sheet, so the game cannot start without it. I open the
     * resource relative to the classpath root, read it with ImageIO and close the stream again. A
     * missing resource, data no image reader understands and read errors all end in an
     * IllegalStateException that names the resource, instead of an unrelated error much later.
     * <p>
     * Time complexity: O(w * h) for decoding an image of width w and height h.
     * Space complexity: O(w * h) for the decoded image.
     *
     * @param pResourcePath absolute classpath path starting with a slash, such as
     *                      "/resources/pieces.png"; never null
     * @return the decoded sprite sheet, never null
     * @throws IllegalStateException if the resource is missing, unreadable or not an image
     */
    public static BufferedImage loadSpriteSheet(String pResourcePath) {
        try (InputStream in = PieceSprites.class.getResourceAsStream(pResourcePath)) {
            // usually means the build did not copy the resources next to the classes
            if (in == null) {
                throw new IllegalStateException("Sprite sheet " + pResourcePath + " was not found on the classpath. "
                        + "The resources folder has to be packaged together with the compiled classes.");
            }
            BufferedImage image = ImageIO.read(in);
            // ImageIO returns null when no reader understands the data
            if (image == null) {
                throw new IllegalStateException("Sprite sheet " + pResourcePath + " is not a readable image.");
            }
            return image;
        } catch (IOException e) {
            throw new IllegalStateException("Sprite sheet " + pResourcePath + " could not be read.", e);
        }
    }

    /**
     * Returns the sprite column a piece type occupies in the sheet.
     * <p>
     * The sheet was drawn in its own order, which is not the order the engine numbers piece types
     * in, and the promotion dialog and the replay board both have to cut from the right column. I
     * keep that mapping here, so nothing else needs to know how the image is laid out.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pPieceType piece type without colour, Pieces.PAWN up to Pieces.KING
     * @return the column index in the sheet, 0 to 5
     * @throws IllegalArgumentException if pPieceType is not a piece type
     */
    public static int spriteColumn(int pPieceType) {
        // the sheet order is king, queen, bishop, knight, rook, pawn
        return switch (pPieceType) {
            case Pieces.KING -> 0;
            case Pieces.QUEEN -> 1;
            case Pieces.BISHOP -> 2;
            case Pieces.KNIGHT -> 3;
            case Pieces.ROOK -> 4;
            case Pieces.PAWN -> 5;
            default -> throw new IllegalArgumentException("piece type " + pPieceType + " has no sprite");
        };
    }

    /**
     * Returns the piece image for one type and colour, scaled to this instance's square size.
     * <p>
     * Painting a position asks for up to 32 sprites on every repaint, so scaling them each time
     * would be wasteful. I return the cached image when there is one, and otherwise cut the square
     * for the requested column and colour out of the sheet, scale it smoothly, draw it into a new
     * image with transparency and keep that for later calls.
     * <p>
     * Time complexity: O(1) for a cached sprite, O(s^2) for the first request of a type and colour
     * with a square size of s. Space complexity: O(s^2) per cached sprite.
     *
     * @param pPieceType piece type without colour, Pieces.PAWN up to Pieces.KING
     * @param pWhite     true for the white row of the sheet, false for the black row
     * @return the scaled sprite, never null
     * @throws IllegalArgumentException if pPieceType is not a piece type
     */
    public BufferedImage spriteFor(int pPieceType, boolean pWhite) {
        int colourRow = pWhite ? 0 : 1;
        BufferedImage cached = cache[colourRow][pPieceType];
        // every sprite is scaled once per board
        if (cached != null) {
            return cached;
        }

        // white pieces sit in the top row of the sheet, black pieces in the row below
        BufferedImage sprite = SHEET.getSubimage(spriteColumn(pPieceType) * SHEET_SCALE,
                colourRow * SHEET_SCALE, SHEET_SCALE, SHEET_SCALE);
        Image scaled = sprite.getScaledInstance(tileSize, tileSize, Image.SCALE_SMOOTH);

        // drawing into a new image turns the scaled instance into pixels I can cache
        BufferedImage result = new BufferedImage(tileSize, tileSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = result.createGraphics();
        g2d.drawImage(scaled, 0, 0, null);
        g2d.dispose();

        cache[colourRow][pPieceType] = result;
        return result;
    }

    /**
     * Returns the piece image for a piece code, scaled to this instance's square size.
     * <p>
     * A position stores whole pieces, colour included, so the board has a piece code rather than a
     * type and a colour. I split the code and hand it on.
     * <p>
     * Time complexity: O(1) for a cached sprite, O(s^2) for the first request.
     * Space complexity: O(s^2) per cached sprite.
     *
     * @param pPiece piece code, Pieces.WHITE_PAWN up to Pieces.BLACK_KING
     * @return the scaled sprite, never null
     * @throws IllegalArgumentException if pPiece is not a piece code
     */
    public BufferedImage spriteForPiece(int pPiece) {
        return spriteFor(Pieces.typeOf(pPiece), Pieces.isWhite(pPiece));
    }

    /**
     * Returns the shared sprite sheet.
     * <p>
     * The replay board and the promotion dialog cut their own squares out of the sheet at sizes that
     * have nothing to do with a running game, so they need the raw image. I hand out the loaded sheet
     * itself, which is never modified anywhere.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return the loaded sprite sheet, never null
     */
    public static BufferedImage getSheet() {
        return SHEET;
    }

    /**
     * Returns the edge length of one square in the sprite sheet.
     * <p>
     * Callers that cut their own sprites need to know how large one piece is in the sheet.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return edge length of one sprite in the sheet in pixels, greater than 0
     */
    public static int getSheetScale() {
        return SHEET_SCALE;
    }

    /**
     * Returns the square size this instance scales its sprites to.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return edge length of one board square in pixels, greater than 0
     */
    public int getTileSize() {
        return tileSize;
    }
}

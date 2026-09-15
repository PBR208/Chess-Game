package engine.pieces;

/*
 * Purpose: Piece is the base class of all six chess pieces. It holds the position, colour, type and
 * first-move flag every piece needs and defines the movement hooks the concrete pieces override.
 * It also owns the shared sprite sheet, which is loaded once from the classpath so the lookup
 * works the same on Windows, macOS and Linux. If the sheet is missing, loading fails right away
 * with a message that names the resource, instead of an unrelated error later on.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import ui.board.Board;

public class Piece {

    protected int col, row;
    protected int xPos, yPos;

    protected boolean isWhite;
    protected PieceType type;
    protected int value;

    private boolean isFirstMove = true;

    // classpath location of the sprite sheet, the same on every OS
    public static final String SPRITE_SHEET_PATH = "/resources/pieces.png";

    protected static final BufferedImage img;
    protected static final int imgScale;

    static {
        // no piece can be drawn without sprites, so a missing sheet has to fail loudly
        img = loadSpriteSheet(SPRITE_SHEET_PATH);
        // the sheet has six columns, one per piece type
        imgScale = img.getWidth() / 6;
    }

    /**
     * Loads a sprite sheet image from the classpath.
     * <p>
     * Every piece is drawn from one shared sheet, so the game cannot start without it. I open the
     * resource relative to the classpath root, read it with ImageIO and close the stream again. A
     * missing resource, data no image reader understands and read errors all end in an
     * IllegalStateException that names the resource. Before, a missing sheet surfaced as an
     * ExceptionInInitializerError caused by "input == null!", which gave no hint about the cause.
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
        try (InputStream in = Piece.class.getResourceAsStream(pResourcePath)) {
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

    protected Image front;
    protected Board b;

    protected Piece(Board b, int col, int row, boolean isWhite, PieceType type, int spriteCol) {
        this.b = b;
        this.col = col;
        this.row = row;
        this.xPos = col * b.getTileSize();
        this.yPos = row * b.getTileSize();
        this.isWhite = isWhite;
        this.type = type;
        this.front = img.getSubimage(spriteCol * imgScale, isWhite ? 0 : imgScale, imgScale, imgScale)
                .getScaledInstance(b.getTileSize(), b.getTileSize(), BufferedImage.SCALE_SMOOTH);
    }

    // GETTER

    public int getCol() {
        return col;
    }

    public int getRow() {
        return row;
    }

    public boolean isWhite() {
        return isWhite;
    }

    public PieceType getType() {
        return type;
    }

    public boolean isFirstMove() {
        return isFirstMove;
    }

    public int getxPos() {
        return xPos;
    }

    public int getyPos() {
        return yPos;
    }

    public static BufferedImage getSpritesheet() {
        return img;
    }

    public static int getSpritesheetScale() {
        return imgScale;
    }

    // SETTER

    public void setCol(int col) {
        this.col = col;
    }

    public void setRow(int row) {
        this.row = row;
    }

    public void setxPos(int xPos) {
        this.xPos = xPos;
    }

    public void setyPos(int yPos) {
        this.yPos = yPos;
    }

    public void setFirstMove(boolean firstMove) {
        isFirstMove = firstMove;
    }

    // HELPER

    public void moveTo(int col, int row) {
        this.col = col;
        this.row = row;
        this.xPos = col * b.getTileSize();
        this.yPos = row * b.getTileSize();
        this.isFirstMove = false;
    }

    public boolean isValidMovement(int col, int row) {
        return true;
    }

    public boolean isValidCollide(int col, int row) {
        return false;
    }

    public void paint(Graphics2D g2d, int x, int y) {
        g2d.drawImage(front, x, y, null);
    }
}

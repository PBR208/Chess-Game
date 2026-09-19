package ui.board;

/*
 * Purpose: LowTimeSound plays the short beep that tells a player their time is nearly gone, which is
 * the one moment in a game where looking at the clock is the thing they have least attention for. I
 * synthesise the tone rather than shipping an audio file, so the game keeps its promise of carrying
 * no assets it does not need and no dependencies at all. A machine without a sound card, which is
 * every build server, has to keep playing chess quietly instead of failing, so nothing in here ever
 * throws and the beep plays on a thread of its own rather than on the one drawing the board.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.awt.GraphicsEnvironment;

public final class LowTimeSound {

    // long enough to hear, short enough not to talk over the game
    private static final int DURATION_MS = 180;
    // a plain speech quality rate is more than enough for one tone
    private static final float SAMPLE_RATE = 44_100f;
    // roughly the A above the treble staff, which carries without being shrill
    private static final double FREQUENCY_HZ = 880.0;
    // well below full volume, because a warning should not make anybody jump
    private static final double AMPLITUDE = 0.25;

    private LowTimeSound() {
    }

    /**
     * Tells whether this machine should be making sounds at all.
     * <p>
     * A build server has neither a screen nor a sound card, and a test run must never wait on an
     * audio device that will not arrive. A machine without a display is treated as one without
     * sound, which is exactly right for the place this matters, and costs a player nothing, because
     * a player has a display by definition.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return true when a warning should be played
     */
    public static boolean isAvailable() {
        return !GraphicsEnvironment.isHeadless();
    }

    /**
     * Plays the low time warning, if this machine can play anything at all.
     * <p>
     * The warning arrives while the clock is ticking down, which is to say while the board is being
     * drawn, so it must not hold that up for as long as a sound lasts. I hand the tone to a daemon
     * thread and return at once, and a machine that cannot play it says nothing. A daemon thread
     * also means a half played beep can never keep the game from closing.
     * <p>
     * Time complexity: O(1) for the caller. Space complexity: O(n) for the n samples of the tone.
     */
    public static void play() {
        // nothing to play on, and nothing to wait for
        if (!isAvailable()) {
            return;
        }
        Thread player = new Thread(LowTimeSound::beep, "low-time-sound");
        // a beep must never be the reason the game stays open
        player.setDaemon(true);
        player.start();
    }

    /**
     * Opens a line, plays the tone and closes the line again.
     * <p>
     * Everything about sound is a machine's own business: the line may be missing, busy or refused,
     * and none of that is worth interrupting a chess game over. So every failure ends here, and the
     * game simply carries on without the warning.
     * <p>
     * Time complexity: O(n) for the n samples written. Space complexity: O(n) for the samples.
     */
    private static void beep() {
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 8, 1, true, false);
        try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
            line.open(format);
            line.start();
            byte[] samples = tone();
            line.write(samples, 0, samples.length);
            // let the line finish rather than cutting the tone off when it closes
            line.drain();
        } catch (Exception problem) {
            // a machine without working sound still plays chess, it just does so quietly
        }
    }

    /**
     * Builds the samples of one short tone.
     * <p>
     * A single sine wave is the whole sound, so it can be worked out rather than stored. I walk the
     * samples of the duration and take the sine at each one, scaled well below the loudest value a
     * byte can hold.
     * <p>
     * Time complexity: O(n) for the n samples. Space complexity: O(n) for the array.
     *
     * @return the tone as signed eight bit samples, never null
     */
    private static byte[] tone() {
        int count = (int) (SAMPLE_RATE * DURATION_MS / 1000);
        byte[] samples = new byte[count];
        for (int index = 0; index < count; index++) {
            double angle = 2.0 * Math.PI * FREQUENCY_HZ * index / SAMPLE_RATE;
            samples[index] = (byte) (Math.sin(angle) * Byte.MAX_VALUE * AMPLITUDE);
        }
        return samples;
    }
}

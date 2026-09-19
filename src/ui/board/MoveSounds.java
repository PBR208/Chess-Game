package ui.board;

/*
 * Purpose: MoveSounds gives a short click when a move is played, so a player learns that the move
 * was accepted without having to look for what changed on the board. The sound is worked out in code
 * rather than loaded from a file, because the project carries no audio assets and takes no outside
 * dependencies, and a few hundred samples of a decaying tone is all a click is. Every failure is
 * swallowed on purpose: a machine with no sound card, a device another program has taken, or a
 * runtime built without audio must cost a player nothing more than the sound itself.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

public class MoveSounds {

    // samples per second. A click needs no fidelity, and a low rate keeps the buffer small.
    private static final float SAMPLE_RATE = 22_050f;

    // pitch of the click, low enough not to be shrill and high enough to cut through
    private static final double FREQUENCY_HZ = 440.0;

    // how long the click lasts in seconds, short enough never to run into the next move
    private static final double DURATION_SECONDS = 0.05;

    // loudest sample value, well under the 127 an 8 bit sample allows, so the click is not harsh
    private static final double AMPLITUDE = 90.0;

    private boolean enabled = true;

    // The prepared click, or null when this machine has no audio to play it with. It is shared by
    // every board, because the click is the same one everywhere and an audio line is a resource the
    // mixer has a limited number of. A line per board would run a long test run out of them and
    // leave every one of them open, since a board has no moment at which it is finished with.
    private static Clip clip;

    // whether opening the line has already been tried, so a machine without audio is asked once
    private static boolean prepared;

    /**
     * Plays the click that says a move was made.
     * <p>
     * The sound is prepared on the first move rather than when the board is built, so a game that
     * nobody plays never touches the audio system at all. Restarting the clip from its beginning
     * lets quick moves follow each other without waiting for the previous click to finish.
     * <p>
     * Time complexity: O(1) after the first call, which is O(n) for the n samples of the click.
     * Space complexity: O(n) for the prepared click, about a thousand bytes.
     */
    public void playMove() {
        if (!enabled) {
            return;
        }
        prepare();
        // no audio on this machine simply means no sound
        if (clip == null) {
            return;
        }
        try {
            clip.stop();
            clip.setFramePosition(0);
            clip.start();
        } catch (RuntimeException e) {
            // a device that disappears while the game runs, such as headphones being unplugged
            clip = null;
        }
    }

    /**
     * Opens the audio line and fills it with the click, once.
     * <p>
     * Everything here can fail on a machine without a sound card, in a runtime built without audio
     * or when another program holds the device, and none of that is worth interrupting a game for.
     * A failure leaves the clip null, which every later move reads as silence without trying again.
     * <p>
     * Time complexity: O(n) for the n samples. Space complexity: O(n) for them.
     */
    private void prepare() {
        if (prepared) {
            return;
        }
        prepared = true;
        try {
            byte[] samples = click();
            // 8 bit signed mono, the simplest format every mixer understands
            AudioFormat format = new AudioFormat(SAMPLE_RATE, 8, 1, true, false);
            Clip opened = AudioSystem.getClip();
            opened.open(format, samples, 0, samples.length);
            clip = opened;
        } catch (Exception e) {
            // no sound card, no mixer, no permission or no audio in this runtime at all
            clip = null;
        }
    }

    /**
     * Works out the samples of the click.
     * <p>
     * A click is a short tone that dies away quickly. I take a sine wave at a fixed pitch and fade
     * it out along a curve rather than a straight line, because a tone that stops abruptly is heard
     * as a pop. Squaring the remaining fraction of the sound is that curve.
     * <p>
     * Time complexity: O(n) for the n samples. Space complexity: O(n) for them.
     *
     * @return the click as 8 bit signed mono samples, never null
     */
    private static byte[] click() {
        int length = (int) (SAMPLE_RATE * DURATION_SECONDS);
        byte[] samples = new byte[length];
        for (int index = 0; index < length; index++) {
            double wave = Math.sin(2 * Math.PI * FREQUENCY_HZ * index / SAMPLE_RATE);
            // how much of the sound is left, squared so it fades away instead of stopping
            double remaining = 1.0 - (double) index / length;
            samples[index] = (byte) (wave * remaining * remaining * AMPLITUDE);
        }
        return samples;
    }

    /**
     * Turns the move sound on or off.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pEnabled true to hear moves, false for silence
     */
    public void setEnabled(boolean pEnabled) {
        this.enabled = pEnabled;
    }

    /**
     * Tells whether moves are set to be heard.
     * <p>
     * This says what the player asked for, not whether the machine can actually produce a sound.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return true while the move sound is switched on
     */
    public boolean isEnabled() {
        return enabled;
    }
}

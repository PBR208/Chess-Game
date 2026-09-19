package engine.model;

/*
 * Purpose: ClockMode says how a clock treats the time around a move, which is the part of a time
 * control that decides how games actually feel. Sudden death gives a player nothing but their
 * starting time, a Fischer increment adds a fixed amount after every move whether it was needed or
 * not, Bronstein gives back only the time a move really used up to the delay, and a simple delay
 * makes the clock wait before it starts counting at all. The rules engine never reads this, it only
 * travels with the rest of the settings, but the clock and the saved game both need to know it.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

public enum ClockMode {

    /** The starting time is everything a player gets, nothing is ever added. */
    SUDDEN_DEATH,

    /** A fixed increment is added after every move, used or not, which is the usual online mode. */
    FISCHER,

    /** The time a move really used is given back afterwards, but never more than the delay. */
    BRONSTEIN,

    /** Each turn the clock waits out the delay before it starts counting the player's time. */
    SIMPLE_DELAY;

    /**
     * Tells whether this mode works with a delay rather than an increment.
     * <p>
     * The setup screen asks for one number, and which of the two it means depends on the mode. Both
     * Bronstein and a simple delay are measured as a delay per move, while a Fischer clock is
     * measured as an increment and sudden death uses neither.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return true for Bronstein and simple delay
     */
    public boolean usesDelay() {
        return this == BRONSTEIN || this == SIMPLE_DELAY;
    }
}

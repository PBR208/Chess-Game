package gui;

import javax.swing.*;
import java.awt.*;

public class ChessClock {

    private static final long START_TIME_MS = 10 * 60 * 1000L; // 10 minutes
    private static final long LOW_TIME_MS = 30 * 1000L;       // red at < 30 s

    private final boolean isWhite;
    private long timeMs = START_TIME_MS;
    private boolean running = false;

    private final Runnable onRepaint;
    private final TimeExpiredCallback onExpired;
    private final Timer timer;


    public interface TimeExpiredCallback {
        void onExpired(boolean isWhiteExpired);
    }

    public ChessClock(boolean isWhite, Runnable onRepaint, TimeExpiredCallback onExpired) {
        this.isWhite = isWhite;
        this.onRepaint = onRepaint;
        this.onExpired = onExpired;

        timer = new Timer(100, e -> tick());
        timer.start(); // always spinning; only counts while running == true
    }

    public void start() {
        running = true;
    }

    public void stop() {
        running = false;
    }

    public void reset() {
        running = false;
        timeMs = START_TIME_MS;
    }

    private void tick() {
        if (!running) return;

        timeMs = Math.max(0, timeMs - 100);
        onRepaint.run();

        if (timeMs == 0) {
            stop();
            onExpired.onExpired(isWhite);
        }
    }
}

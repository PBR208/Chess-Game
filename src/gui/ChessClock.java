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

    public void draw(Graphics2D g2d, int yOffset, int width, int height) {
        int pad = height / 6;

        //Background
        g2d.setColor(running ? new Color(45, 45, 48) : new Color(28, 28, 30));
        g2d.fillRect(0, yOffset, width, height);

        //Green left border on the active clock
        if (running) {
            g2d.setColor(new Color(81, 168, 0));
            g2d.fillRect(0, yOffset, 4, height);
        }

        //Thin separator between clock and board edge
        g2d.setColor(new Color(60, 60, 60));
        g2d.fillRect(0, running ? yOffset + height - 1 : yOffset, width, 1);

        //Player colour swatch (small filled square)
        int swatchSize = height / 4;
        int swatchX = pad + 4; // clear of the green active border
        int swatchY = yOffset + (height - swatchSize) / 2;

        g2d.setColor(isWhite ? new Color(232, 235, 239) : new Color(40, 40, 42));
        g2d.fillRect(swatchX, swatchY, swatchSize, swatchSize);
        g2d.setColor(new Color(90, 90, 90));
        g2d.drawRect(swatchX, swatchY, swatchSize, swatchSize);

        //Player label
        g2d.setFont(new Font("Arial", Font.BOLD, height / 5));
        g2d.setColor(running ? Color.WHITE : new Color(110, 110, 110));

        FontMetrics fmLabel = g2d.getFontMetrics();
        String label = isWhite ? "WHITE" : "BLACK";
        int labelX = swatchX + swatchSize + pad / 2;
        int labelY = yOffset + (height + fmLabel.getAscent() - fmLabel.getDescent()) / 2;
        g2d.drawString(label, labelX, labelY);

        //Time display
        long totalSec = timeMs / 1000;
        String timeText = String.format("%02d:%02d", totalSec / 60, totalSec % 60);

        Color timeColor;
        if (!running) timeColor = new Color(90, 90, 90);
        else if (timeMs < LOW_TIME_MS) timeColor = new Color(210, 55, 55); // red under 30 s
        else timeColor = Color.WHITE;

        g2d.setFont(new Font("Arial", Font.BOLD, height / 2));
        g2d.setColor(timeColor);

        FontMetrics fmTime = g2d.getFontMetrics();
        int timeX = width - pad - fmTime.stringWidth(timeText);
        int timeY = yOffset + (height + fmTime.getAscent() - fmTime.getDescent()) / 2;
        g2d.drawString(timeText, timeX, timeY);
    }
}

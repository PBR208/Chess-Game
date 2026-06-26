package gui;

public class ChessClock {

    public interface TimeExpiredCallback {
        void onExpired(boolean isWhiteExpired);
    }
}

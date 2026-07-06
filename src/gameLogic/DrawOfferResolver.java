package gameLogic;

public interface DrawOfferResolver {
    void notifyForcedDraw();      // 75-move rule: informational only, game always ends

    boolean offerDraw();          // 50-move rule: true if the player claimed/accepted the draw
}
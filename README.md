<div align="center">

# ♟ Chess Game — Java Edition

**A fully hand-built chess application — written from scratch to learn.**

[![Release](https://img.shields.io/github/v/release/PBR208/Chess-Game?style=for-the-badge&logo=github&label=Release&color=brightgreen)](https://github.com/PBR208/Chess-Game/releases/latest)
[![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.java.com)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](LICENSE)
[![Status](https://img.shields.io/badge/Status-Active-blue?style=for-the-badge)]()
[![Made with ♥](https://img.shields.io/badge/Made%20with-%E2%99%A5-red?style=for-the-badge)]()

</div>

---

> ![Chess Game Screenshot](docs/example.png)

---

## 🧠 What I Learned

This project was built from scratch as a deliberate learning exercise — no chess libraries, no tutorials, no engine
borrowed from elsewhere. Every line of logic was written by hand. These are the concrete skills I developed through it:

| Area                         | What I practised                                                                                                                                                         |
|------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **OOP & Inheritance**        | Designed a `Piece` base class extended by six concrete types; `GameController` and `CheckScanner` operate on the abstract type through polymorphism                      |
| **Game State Management**    | Tracking turn ownership, en passant eligibility, first-move flags, the 50/75-move counter, and clock state — all kept consistent across moves and game resets            |
| **Algorithm Design**         | Legal move generation with path-blocking (`isValidCollide`), simulation-based check detection (move → scan → undo), checkmate/stalemate via exhaustive move search       |
| **Coordinate Systems**       | Separating logical grid coordinates from pixel positions, then adding a perspective flip so the current player is always at the bottom                                   |
| **Java Swing & Graphics2D**  | Custom `paintComponent` rendering with anti-aliasing, a live `ChessClock` drawn with `Graphics2D`, sprite sheet slicing with `getSubimage`, and modal `JDialog` overlays |
| **Event-Driven Programming** | `MouseListener` and `MouseMotionListener` wired to game logic; `javax.swing.Timer` driving a 100 ms clock tick via a functional callback interface                       |
| **Refactoring**              | Split an early "god class" `Board` into `Board`, `GameController`, `CheckScanner`, and `Input`; introduced enums (`DrawResult`, `Choice`) to replace magic strings       |
| **Testing**                  | Built a minimal test runner from scratch — named tests, assertion helpers, auto-dismissing modal dialogs via `Timer`-scheduled `doClick()`                               |
| **Git Workflow**             | Feature branching, PRs per feature (`enPassantFix`, `clock`, `50MoveRule`, `boardFlip`, …), tagged releases                                                              |

---

## 📖 About

A playable two-player chess game written entirely in Java. The goal was to understand how a non-trivial application gets
built from the ground up — from the board model through to a working graphical interface — without relying on any
external chess logic.

Both players share the same screen. The board flips after each move so the active player always faces their own pieces
from the bottom, mimicking a physical board rotation.

---

## ✨ Features

| ✅ Implemented                                         | ❌ Not Yet Implemented                                   |
|-------------------------------------------------------|---------------------------------------------------------|
| All six piece types with correct movement rules       | Notation log (PGN)                                      |
| Legal move generation — self-check moves filtered out | Threefold repetition draw                               |
| Check & checkmate detection                           | Insufficient material draw (K vs K, etc.)               |
| Stalemate detection                                   | AI opponent                                             |
| Castling — kingside & queenside with full validation  | Sound effects                                           |
| En passant                                            | Configurable time controls (increment, custom duration) |
| Pawn promotion with piece-selector dialog             | Online / network play                                   |
| 50-move draw claim / 75-move forced draw              |                                                         |
| Chess clock — 10 min per player, turns red under 30 s |                                                         |
| Board perspective flip after each move                |                                                         |
| Move highlighting on piece selection                  |                                                         |
| End screen on checkmate, stalemate, or time loss      |                                                         |
| Piece sprites loaded from a sprite sheet              |                                                         |
| Move history                                          |                                                         |

---

## 🏗️ Project Structure

```
src/
├── main/
│   └── Main.java               # Entry point — creates JFrame and adds Board
├── gameLogic/
│   ├── GameController.java     # Turn management, move execution, game-end checks
│   ├── CheckScanner.java       # Simulate-and-undo check detection
│   ├── Input.java              # Mouse event → game action
│   └── Move.java               # Value object: piece + target square + captured piece
├── gui/
│   ├── Board.java              # JPanel: renders board, clocks, pieces; owns piece list
│   ├── ChessClock.java         # Timer-driven clock with Graphics2D rendering
│   ├── EndScreen.java          # Result dialog (checkmate / stalemate / time)
│   ├── FiftyRuleDraw.java      # 50/75-move draw dialog
|   ├── MoveLogPanel.java       # Displays the moveLog of the current game
│   └── PromoteGUI.java         # Promotion piece selector dialog
├── pieces/
│   ├── Piece.java              # Abstract base: position, colour, sprite, move hooks
│   ├── King.java               # ±1 in any direction + castling
│   ├── Queen.java              # Rook + Bishop combined
│   ├── Rook.java               # Horizontal / vertical sliding
│   ├── Bishop.java             # Diagonal sliding
│   ├── Knight.java             # L-shape jump (no collision check)
│   └── Pawn.java               # Forward push, diagonal capture, en passant, promotion
└── test/
    └── GameTest.java           # Standalone GUI test runner (no external framework)
```

---

## 🔬 How Check Detection Works

Before any move is committed, the engine temporarily applies it, scans every opponent piece to see if it can now reach
the friendly king, then restores the board. Only moves that leave the king safe are legal:

```java
// CheckScanner.java
public boolean isKingLeftInCheck(Move move) {
    // 1. Apply the move tentatively
    piece.setCol(move.getNewCol());
    piece.setRow(move.getNewRow());
    if (captured != null) b.removePiece(captured);

    // 2. Scan all opponent pieces
    boolean inCheck = isKingInCheckRN(piece.isWhite());

    // 3. Undo — restore original state
    piece.setCol(oldCol);
    piece.setRow(oldRow);
    if (captured != null) b.getPieces().add(captured);

    return inCheck;
}
```

Checkmate is declared when the king is in check **and** this simulation returns `true` for every possible move of every
friendly piece.

---

## 🚀 Getting Started

### Prerequisites

- Java **JDK 17** or later
- Any Java IDE (IntelliJ IDEA, Eclipse, VS Code with Java Extension Pack)

### Running the Game

**Clone the repository:**

```bash
git clone https://github.com/PBR208/Chess-Game.git
cd Chess-Game
```

**Compile from the command line:**

```bash
javac -d out src/**/*.java
```

**Run:**

```bash
java -cp out main.Main
```

**Or open in an IDE:** import the project folder and run `Main.java` directly.

> Alternatively, download the pre-built `.jar` from
> the [latest release](https://github.com/PBR208/Chess-Game/releases/latest) and run it with `java -jar Chess-Game.jar`.

---

## 🎮 How to Play

1. Launch the application — the board appears with pieces in starting position
2. Click one of your pieces to select it — valid moves are highlighted in green
3. Click a highlighted square to move
4. The board flips so the other player faces their own pieces from the bottom
5. The clocks switch automatically; a player who runs out of time loses
6. The game ends on checkmate, stalemate, time loss, or a 50/75-move draw

White always moves first.

---

## 🗺️ Roadmap

- [x] Board initialisation and piece placement
- [x] All six piece types with correct movement rules
- [x] Legal move filtering (no self-check)
- [x] Check and checkmate detection
- [x] Stalemate detection
- [x] Interactive GUI with click-to-move and move highlighting
- [x] Castling (kingside and queenside)
- [x] En passant
- [x] Pawn promotion with piece-selector dialog
- [x] 50-move draw claim / 75-move forced draw
- [x] Chess clock (10 min per player)
- [x] Board perspective flip
- [ ] Move history / notation log
- [ ] Threefold repetition and insufficient material draws
- [ ] AI opponent
- [ ] Configurable time controls

---

## 🤝 Contributing

This is a personal learning project — issues, ideas, and suggestions are always welcome. Feel free
to [open an issue](https://github.com/PBR208/Chess-Game/issues).

---

## 📄 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.

---

<div align="center">

Made with ♟ and a lot of patience.

</div>
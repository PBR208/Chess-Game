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

| Area                            | What I practised                                                                                                                                                         |
|---------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **OOP & Inheritance**           | Designed a `Piece` base class extended by six concrete types; `GameController` and `CheckScanner` operate on the abstract type through polymorphism                      |
| **Game State Management**       | Tracking turn ownership, en passant eligibility, first-move flags, the 50/75-move counter, and clock state — all kept consistent across moves and game resets            |
| **Algorithm Design**            | Legal move generation with path-blocking (`isValidCollide`), simulation-based check detection (move → scan → undo), checkmate/stalemate via exhaustive move search       |
| **Coordinate Systems**          | Separating logical grid coordinates from pixel positions, then adding a perspective flip so the current player is always at the bottom                                   |
| **Java Swing & Graphics2D**     | Custom `paintComponent` rendering with anti-aliasing, a live `ChessClock` drawn with `Graphics2D`, sprite sheet slicing with `getSubimage`, and modal `JDialog` overlays |
| **Event-Driven Programming**    | `MouseListener` and `MouseMotionListener` wired to game logic; `javax.swing.Timer` driving a 100 ms clock tick via a functional callback interface                       |
| **Refactoring**                 | Split an early "god class" `Board` into `Board`, `GameController`, `CheckScanner`, and `Input`; introduced enums (`DrawResult`, `Choice`) to replace magic strings       |
| **Persistence & Serialization** | Hand-rolled PGN writer/parser (`PgnManager`), FEN generation and loading (`FenGenerator`/`FenLoader`), and algebraic notation output (`NotationHelper`)                  |
| **UI Navigation & State**       | A `CardLayout`/panel-swapping menu system (`MainMenu` → `NewGamePanel` → `Board` / `PastGamesPanel` → `ReplayPanel`) driven by a config object (`GameConfig`)            |
| **Testing**                     | Built a minimal test runner from scratch — named tests, assertion helpers, auto-dismissing modal dialogs via `Timer`-scheduled `doClick()`                               |
| **Git Workflow**                | Feature branching, PRs per feature (`enPassantFix`, `clock`, `50MoveRule`, `boardFlip`, …), tagged releases                                                              |

---

## 📖 About

A playable two-player chess game written entirely in Java. The goal was to understand how a non-trivial application gets
built from the ground up — from the board model through to a working graphical interface — without relying on any
external chess logic.

Both players share the same screen. The board flips after each move so the active player always faces their own pieces
from the bottom, mimicking a physical board rotation.

The app now has a proper front end: a main menu leads into a **New Game** setup screen (player names, time-control
presets or a custom clock) and a **Past Games** library, where every finished game is saved to disk as a PGN file and
can be reopened later, either as a plain move log or stepped through move-by-move on a mini replay board.

---

## ✨ Features

| ✅ Implemented                                                                                           | ❌ Not Yet Implemented                     |
|---------------------------------------------------------------------------------------------------------|-------------------------------------------|
| All six piece types with correct movement rules                                                         | Threefold repetition draw                 |
| Legal move generation — self-check moves filtered out                                                   | Insufficient material draw (K vs K, etc.) |
| Check & checkmate detection                                                                             | AI opponent                               |
| Stalemate detection                                                                                     | Sound effects                             |
| Castling — kingside & queenside with full validation                                                    | Online / network play                     |
| En passant                                                                                              |                                           |
| Pawn promotion with piece-selector dialog                                                               |                                           |
| 50-move draw claim / 75-move forced draw                                                                |                                           |
| Chess clock with configurable time controls — bullet/blitz/rapid/classical presets or a custom duration |                                           |
| Board perspective flip after each move                                                                  |                                           |
| Move highlighting on piece selection                                                                    |                                           |
| End screen on checkmate, stalemate, or time loss                                                        |                                           |
| Piece sprites loaded from a sprite sheet                                                                |                                           |
| Move history / live move log panel                                                                      |                                           |
| Main menu with New Game & Past Games navigation                                                         |                                           |
| Custom player names per game                                                                            |                                           |
| PGN export — every finished game auto-saved to `games/`                                                 |                                           |
| FEN generation & parsing for board positions                                                            |                                           |
| Past-games library with saved move logs                                                                 |                                           |
| Move-by-move replay viewer for saved games                                                              |                                           |

---

## 🏗️ Project Structure

```
src/
├── main/
│   └── Main.java               # Entry point — owns the JFrame, swaps in menu/game/library panels
├── gameLogic/
│   ├── GameController.java     # Turn management, move execution, game-end checks
│   ├── CheckScanner.java       # Simulate-and-undo check detection
│   ├── Input.java              # Mouse event → game action
│   ├── Move.java                # Value object: piece + target square + captured piece
│   ├── GameConfig.java          # Player names + time control, passed from menu into a game
│   ├── GameRecord.java          # Immutable record of a finished/loaded game (moves, FENs, result)
│   ├── NotationHelper.java      # Move → algebraic notation (O-O, Nf3, exd5, e8=Q, …)
│   ├── FenGenerator.java        # Board position → FEN string after every move
│   ├── FenLoader.java           # FEN string → piece grid, used by the replay viewer
│   └── PgnManager.java          # Saves finished games as .pgn files and reloads them
├── gui/
│   ├── Board.java               # JPanel: renders board, clocks, pieces; owns piece list
│   ├── ChessClock.java          # Timer-driven clock with Graphics2D rendering
│   ├── EndScreen.java           # Result dialog (checkmate / stalemate / time)
│   ├── FiftyRuleDraw.java       # 50/75-move draw dialog
│   ├── MoveLogPanel.java        # Live move log shown next to the board during play
│   ├── PromoteGUI.java          # Promotion piece selector dialog
│   ├── MainMenu.java            # Landing screen — New Game / Past Games
│   ├── NewGamePanel.java        # Player names + time-control presets/custom clock, then starts a game
│   ├── PastGamesPanel.java      # Split-pane library of saved games (list + move log / replay toggle)
│   └── ReplayPanel.java         # Mini board that scrubs through a saved game's FEN history
├── pieces/
│   ├── Piece.java               # Abstract base: position, colour, sprite, move hooks
│   ├── King.java                # ±1 in any direction + castling
│   ├── Queen.java               # Rook + Bishop combined
│   ├── Rook.java                # Horizontal / vertical sliding
│   ├── Bishop.java              # Diagonal sliding
│   ├── Knight.java              # L-shape jump (no collision check)
│   └── Pawn.java                # Forward push, diagonal capture, en passant, promotion
└── test/
    └── GameTest.java            # Standalone GUI test runner (no external framework)
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

1. Launch the application — you land on the main menu
2. Choose **New Game**, enter each player's name, and pick a time control (a preset like Blitz 5+0, or a custom
   minutes/seconds duration) — then **Start**
3. Click one of your pieces to select it — valid moves are highlighted in green
4. Click a highlighted square to move
5. The board flips so the other player faces their own pieces from the bottom
6. The clocks switch automatically; a player who runs out of time loses
7. The game ends on checkmate, stalemate, time loss, or a 50/75-move draw — the result is saved automatically as a
   PGN file in a `games/` folder next to where you ran the app

White always moves first.

From the main menu, **Past Games** opens a library of every saved game. Select one to view its full move log, or
switch to the **Replay** tab to step through the position move-by-move on a mini board.

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
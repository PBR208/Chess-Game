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

| Area                            | What I practised                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
|---------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **OOP & Inheritance**           | Designed a `Piece` base class extended by six concrete types; `GameController` and `CheckScanner` operate on the abstract type through polymorphism                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| **Game State Management**       | Tracking turn ownership, en passant eligibility, first-move flags, the 50/75-move counter, and clock state — all kept consistent across moves and game resets                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| **Algorithm Design**            | Legal move generation with path-blocking (`isValidCollide`), simulation-based check detection (move → scan → undo), checkmate/stalemate via exhaustive move search                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| **Coordinate Systems**          | Separating logical grid coordinates from pixel positions, then adding a perspective flip so the current player is always at the bottom                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| **Java Swing & Graphics2D**     | Custom `paintComponent` rendering with anti-aliasing, a live `ChessClock` drawn with `Graphics2D`, sprite sheet slicing with `getSubimage`, and modal `JDialog` overlays                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| **Event-Driven Programming**    | `MouseListener` and `MouseMotionListener` wired to game logic; `javax.swing.Timer` driving a clock tick via a functional callback interface                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| **Persistence & Serialization** | Hand-rolled PGN writer/parser (`PgnManager`), FEN generation and loading (`FenGenerator`/`FenLoader`), and algebraic notation output (`NotationHelper`)                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| **UI Navigation & State**       | A panel-swapping menu system (`MainMenu` → `NewGamePanel` → `Board` / `PastGamesPanel` → `ReplayPanel`) driven by a config object (`GameConfig`)                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| **Dependency Inversion**        | `GameController` no longer constructs `PromoteGUI`/`FiftyRuleDraw` directly — it depends on `PromotionChooser`/`DrawOfferResolver` interfaces, with `Swing*` classes supplying the real dialogs. The rules engine has zero Swing imports                                                                                                                                                                                                                                                                                                                                                                                   |
| **Separation of Concerns**      | Split what was one `Board` class doing four jobs (rendering, position data, coordinate flipping, clock ownership) into `Board` (rendering) + `BoardState` (position data); split move-history bookkeeping out of `GameController` into `MoveHistory`; pulled the repeated dark-theme styling out of four GUI panels into `Theme`/`UiComponents`; then carried that same separation all the way through the package layout — `engine.imports`, `engine.model`, `engine.persistence`, and `engine.pieces` hold zero-Swing rules/data code, while `ui.board`, `ui.menu`, and `ui.theme` hold everything that touches a window |
| **Refactoring**                 | Introduced enums (`DrawResult`, `Choice`) to replace magic strings; every refactor here was done as a small, isolated, behavior-preserving change verified by a full recompile each time                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| **Testing**                     | Built a minimal test runner from scratch — named tests, assertion helpers, auto-dismissing modal dialogs via `Timer`-scheduled `doClick()` — and grew it alongside the refactors above so the newly-decoupled engine classes are now testable without any dialog simulation at all                                                                                                                                                                                                                                                                                                                                         |
| **Git Workflow**                | Feature branching, PRs per feature (`enPassantFix`, `clock`, `50MoveRule`, `boardFlip`, …), tagged releases                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |

---

## 📖 About

A playable two-player chess game written entirely in Java. The goal was to understand how a non-trivial application gets
built from the ground up — from the board model through to a working graphical interface — without relying on any
external chess logic.

Both players share the same screen. The board flips after each move so the active player always faces their own pieces
from the bottom, mimicking a physical board rotation.

The app has a full front end: a main menu leads into a **New Game** setup screen (player names, time-control presets
or a custom clock) and a **Past Games** library, where every finished game is saved to disk as a PGN file and can be
reopened later, either as a plain move log or stepped through move-by-move on a mini replay board.

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
├── app/
│   └── Main.java                     # Entry point — owns the JFrame, swaps in menu/game/library panels
├── engine/
│   ├── imports/                      # Rules engine — zero Swing/AWT imports
│   │   ├── GameController.java       # Turn management, move execution, game-end rule checks
│   │   ├── CheckScanner.java         # Simulate-and-undo check detection
│   │   ├── Move.java                 # Value object: piece + target square + captured piece
│   │   ├── BoardState.java           # Position data (pieces list, grid, en passant tile)
│   │   ├── MoveHistory.java          # Bundles moveLog + fenHistory + notation + move-log-panel updates
│   │   ├── NotationHelper.java       # Move → algebraic notation (O-O, Nf3, exd5, e8=Q, …)
│   │   ├── FenGenerator.java         # BoardState → FEN string after every move
│   │   ├── PromotionChooser.java     # Interface: "give me a promotion choice" — no Swing dependency
│   │   └── DrawOfferResolver.java    # Interface: "offer/notify a draw" — no Swing dependency
│   ├── model/
│   │   ├── GameConfig.java           # Player names + time control, passed from menu into a game
│   │   └── GameRecord.java           # Immutable record of a finished/loaded game (moves, FENs, result)
│   ├── persistence/
│   │   ├── FenLoader.java            # FEN string → piece grid, used by the replay viewer
│   │   └── PgnManager.java           # Saves finished games as .pgn files and reloads them
│   └── pieces/
│       ├── Piece.java                # Base class: position, colour, sprite, move hooks
│       ├── PieceType.java            # Enum of the six piece types + sprite-sheet display name
│       ├── King.java                 # ±1 in any direction + castling
│       ├── Queen.java                # Rook + Bishop combined
│       ├── Rook.java                 # Horizontal / vertical sliding
│       ├── Bishop.java               # Diagonal sliding
│       ├── Knight.java               # L-shape jump (no collision check)
│       └── Pawn.java                 # Forward push, diagonal capture, en passant, promotion
├── ui/
│   ├── board/                        # The live game screen
│   │   ├── Board.java                # JPanel: renders board, clocks, pieces; delegates position data to BoardState
│   │   ├── ChessClock.java           # Timer-driven clock with Graphics2D rendering
│   │   ├── EndScreen.java            # Result dialog (checkmate / stalemate / time)
│   │   ├── FiftyRuleDraw.java        # 50/75-move draw dialog
│   │   ├── PromoteGUI.java           # Promotion piece selector dialog
│   │   ├── SwingPromotionChooser.java   # Implements PromotionChooser using PromoteGUI
│   │   ├── SwingDrawOfferResolver.java  # Implements DrawOfferResolver using FiftyRuleDraw
│   │   ├── MoveLogPanel.java         # Live move log shown next to the board during play
│   │   └── Input.java                # Mouse event → game action
│   ├── menu/                         # App navigation screens
│   │   ├── MainMenu.java             # Landing screen — New Game / Past Games
│   │   ├── NewGamePanel.java         # Player names + time-control presets/custom clock, then starts a game
│   │   ├── PastGamesPanel.java       # Split-pane library of saved games (list + move log / replay toggle)
│   │   └── ReplayPanel.java          # Mini board that scrubs through a saved game's FEN history
│   └── theme/
│       ├── Theme.java                # Shared dark-theme color palette for the menu-style screens
│       └── UiComponents.java         # Shared button styling/hover-effect factory
└── test/
    └── GameTest.java                 # Standalone test runner (no external framework) — see Testing below
```

**Why `engine` and `ui` are separate top-level packages, not just separate classes:** nothing under `engine/imports`,
`engine/model`, `engine/persistence`, or `engine/pieces` imports anything from `ui`. That's not just a naming
convention — it's checkable: `GameController`, `CheckScanner`, `Move`, `BoardState`, `MoveHistory`, `NotationHelper`,
and `FenGenerator` all run and are fully tested without a display of any kind. `ui/board` and `ui/menu` are the only
places a `JFrame`/`JDialog` gets created.

**Why `BoardState` is separate from `Board`:** `Board` is a `JPanel` — it renders, owns the two clocks, and handles
coordinate flipping. Before this split, it *also* owned the raw pieces list, the grid, and the en passant tile
directly, which meant `GameController`, `CheckScanner`, and `FenGenerator` all needed a live Swing component just to
ask "what's on this square?". `BoardState` holds exactly that position data with zero AWT/Swing imports.

**Why `PromotionChooser`/`DrawOfferResolver` exist:** `GameController` used to build `new PromoteGUI(...)` and
`new FiftyRuleDraw(...)` directly, which meant "decide what a pawn promotes to" was inseparable from "show a modal
dialog." These two interfaces let `GameController` ask an abstraction instead; `SwingPromotionChooser` and
`SwingDrawOfferResolver` are the real, dialog-backed answers `Board` supplies, but a test (or any future non-Swing
front end) can supply its own.

**One remaining crack, flagged rather than hidden:** `engine.pieces.Piece` still takes a `ui.board.Board` in its
constructor, to read tile size and slice its sprite. That's an upward dependency from `engine` into `ui` that the
package split doesn't fully remove — decoupling piece rendering from piece construction would close it, but that's
a bigger, separate change than a folder reorganization.

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
    if (captured != null) state.removePiece(captured);

    // 2. Scan all opponent pieces
    boolean inCheck = isKingInCheckRN(piece.isWhite());

    // 3. Undo — restore original state
    piece.setCol(oldCol);
    piece.setRow(oldRow);
    if (captured != null) state.addPiece(captured);

    return inCheck;
}
```

Checkmate is declared when the king is in check **and** this simulation returns `true` for every possible move of every
friendly piece.

> **Known limitation:** this simulate/undo only updates the *moving piece's* own position — it doesn't update
> `BoardState`'s grid. That's fine for checking a king's own destination square, but it means a *discovered* attack
> (a sliding piece moving away and exposing its own king to a pin) isn't detected. This is a pre-existing
> characteristic of `CheckScanner`, not something introduced by the position-data refactor above — flagged here
> rather than silently worked around.

---

## ✅ Testing

`test/GameTest.java` is a from-scratch test runner (no JUnit) with over 115 named test cases, grouped by the class
they exercise — `GameConfig`, `GameRecord`, `BoardState`, `Move`/`CheckScanner`, `NotationHelper`, `FenGenerator`,
`MoveHistory`, `PieceType`, `ChessClock`, the dialogs, the menu panels, and `GameController`'s full rules engine
(moves, captures, castling, en passant, promotion, checkmate, stalemate, and the 50/75-move draw rules).

Because `GameController` depends on the `PromotionChooser`/`DrawOfferResolver` interfaces rather than concrete
dialogs, the rules-engine tests drive promotion and draw scenarios with small fake implementations instead of
simulating dialog clicks — no `Timer`-scheduled `doClick()` needed for any of that coverage. The dialog classes
themselves (`PromoteGUI`, `FiftyRuleDraw`, `EndScreen`) and the two `Swing*` wrapper classes are still tested the
old way, since they're the parts that genuinely need a real window.

**Run the tests** (requires a display, since the dialog-based tests build real `JFrame`/`JDialog` windows):

```bash
javac -d out $(find src -name "*.java")
java -cp out test.GameTest
```

In an environment with no display available at all, only the tests that construct a `JFrame` or show a modal dialog
will fail — everything else (all of `BoardState`, `Move`, `CheckScanner`, `NotationHelper`, `FenGenerator`,
`MoveHistory`, `PieceType`, `Theme`/`UiComponents`, and `GameController`'s rules-engine tests) runs and passes without
one, since none of it needs a real window.

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
javac -d out $(find src -name "*.java")
```

**Run:**

```bash
java -cp out app.Main
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
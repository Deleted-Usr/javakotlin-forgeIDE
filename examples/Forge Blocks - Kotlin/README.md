# Forge Blocks

Forge Blocks is a small, dependency-free falling-block puzzle game written in
Kotlin and built to be explored inside ForgeIDE. Everything is drawn with
Java2D, so the only requirement is a Kotlin compiler on your `PATH`.

## Open and run it

1. Launch ForgeIDE and choose **File > Open Project...**.
2. Select the `Forge Blocks - Kotlin` directory.
3. Open `src/forgeblocks/Main.kt` in the project tree.
4. Choose **Build > Run** (or use the Run toolbar button).

ForgeIDE compiles every `.kt` file with `kotlinc` into `out`, opens the game in
a separate window, and reports build or runtime output in its console.

## Controls

- **A / D** or **Left / Right** — move
- **W**, **Up**, or **X** — rotate
- **S** or **Down** — soft drop
- **Space** — hard drop
- **P** — pause
- **R** — restart

Clear lines to score; every ten lines raises the level and speeds gravity up.

## Where to look

The project is deliberately small, and each file shows off a different piece
of Kotlin:

- `Tetromino.kt` — the seven shapes are drawn as text grids and rotated with
  `generateSequence`; `Cell` and `Piece` are immutable data classes moved with
  `copy`.
- `Board.kt` — an immutable well where locking a piece and clearing rows
  return a new board via `filterNot` and `List` builders.
- `Game.kt` — the rules. A `sealed interface State` with exhaustive `when`,
  the ghost piece computed from a sequence, and a "seven bag" randomiser.
- `GamePanel.kt` — Swing wiring with a tiny `bind("LEFT", "A") { ... }` DSL
  and `Graphics2D` extension functions for drawing.

Try adding a shape to `Shape`, changing the scoring in `Game.pointsFor`, or
the gravity curve in `Game.fallInterval`, then run again.

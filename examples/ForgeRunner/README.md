# Forge Runner

Forge Runner is a tiny, dependency-free side-scrolling platformer built to be
explored inside ForgeIDE. Its visuals are drawn entirely with Java2D, so there
are no assets or libraries to install.

## Open and run it

1. Launch ForgeIDE and choose **File > Open Project...**.
2. Select the `examples/ForgeRunner` directory.
3. Open `src/forgerunner/ForgeRunner.java` in the project tree.
4. Choose **Build > Run** (or use the Run toolbar button).

ForgeIDE compiles every source file into `out`, opens the game in a separate
window, and reports build or runtime output in its console.

## Controls

- **A / D** or **Left / Right** — move
- **W**, **Up**, or **Space** — jump
- **Shift** — air dash
- **P** — pause
- **R** — restart

Collect sparks, bounce on slimes, cross the gaps, and reach the glowing forge.
Try changing the constants in `Player.java` or the platforms in `Level.java`,
then run again. Switch ForgeIDE's theme while browsing the files to see syntax
colours and the rest of the workbench update together.

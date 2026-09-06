# Forge Strike

Forge Strike is a small, asset-free top-down arena shooter for C++17 and SFML. 
It deliberately shares Forge Runner's dusk-purple landscape, ember-orange
highlights, mint enemies, particles, dash trails, and clean geometric shapes.

## Controls

- **WASD** or **arrow keys** — move
- **Mouse** — aim
- **Left mouse** or **Space** — fire
- **Shift** — dash in the movement direction
- **P** — pause
- **R** — restart
- **Escape** — quit

Survive escalating waves, collect the orange energy dropped by enemies, and
keep the forge core alive. The ring around the player shows weapon cooldown;
the bars at the upper-left show player health, core health, and dash charge.

## Build with SFML 3

Install SFML 3 and CMake, then run:

```sh
cmake -S . -B build
cmake --build build
```

If SFML is installed outside the default search path, set `SFML_DIR` to the
directory containing `SFMLConfig.cmake` when configuring.

If CMake is not installed, you can set to compile and run directly with g++
in the project settings, just make sure to include `-lsfml-graphics -lsfml-window -lsfml-system`
in the linker flags field, and make sure the SFML include directory is set
inside the Include Directories list in the project settings.

## Project structure

The example is intentionally split into conventional C++ declaration and
implementation pairs, making it useful for testing project navigation:

- `Game.hpp` / `Game.cpp` — application loop and object coordination
- `Player.hpp` / `Player.cpp` — input, movement, dash, health, and drawing
- `Enemy.hpp` / `Enemy.cpp` — targeting, movement, damage, and drawing
- `Projectile.hpp` / `Projectile.cpp` — bullet lifetime and rendering
- `EnergyPickup.hpp` / `EnergyPickup.cpp` — attraction and collection
- `ForgeCore.hpp` / `ForgeCore.cpp` — the objective's state and visuals
- `ParticleSystem.hpp` / `ParticleSystem.cpp` — reusable bursts and trails
- `Math.hpp` / `Math.cpp` — shared vector helpers

`Palette.hpp` holds the shared inline colour constants, while `main.cpp` is a
deliberately small entry point. This gives ForgeIDE plenty of includes,
declarations, definitions, and related files to navigate when C++ support lands.
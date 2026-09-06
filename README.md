## Forge IDE

A small integrated development environment (IDE) built entirely in Java Swing and AWT,
only using one library (FlatLaF) to handle UI Themes and dark mode. Each class is 
documented extensively using Oracle's own in-code documentation system: JavaDocs.

TODO:
  - Add sections about 
    - features 
    - project structure
    - class structure
    - documentation

### Example project

[`examples/Forge Runner - Java`](examples/Forge%20Runner%20-%20Java) is a small side-scrolling Java2D
platformer packaged as a Forge project. Open that directory in ForgeIDE, select
`src/forgerunner/ForgeRunner.java`, and press Run.

[`examples/Forge Strike - C++`](examples/Forge%20Strike%20-%20C%2B%2B) is a
top-down SFML arena shooter built with CMake, using procedural graphics and no
game assets. It is run by the C++ language plugin in `modules/forge-lang-cpp`,
which detects the `CMakeLists.txt` and builds through CMake; a project without
one is compiled by invoking `g++` directly.

I think you’ve identified the important distinction now: **Forge needs an identity, but not every interesting feature 
needs to become that identity.**

The Kotlin sketch DSL, Focus Mode, scripting, LSP support, portability, and learning-oriented features can all belong 
in Forge without Forge becoming “the Processing IDE”, “the distraction-free IDE”, or “the LSP IDE.”

After seeing where 1.1.0 has ended up, I think there’s a stronger direction hiding underneath all of those ideas:

> **Forge should be a small, understandable, extensible IDE that makes programming tools visible instead of magical.**

That gives Forge a reason to exist without requiring it to beat IntelliJ at being IntelliJ or VS Code at being VS 
Code.

### I wouldn’t make creative coding the core identity

Your DSL is genuinely nice:

```kotlin
fun main() = sketch("Bouncing Ball", width = 800, height = 600) {
    var x = 100.0
    var dx = 4.0

    draw {
        background(Color.BLACK)
        fill(Color.ORANGE)
        circle(x, height / 2.0, radius = 30.0)

        x += dx
        if (x !in 30.0..width - 30.0) dx = -dx
    }

    onKeyPressed(Key.SPACE) { dx *= 1.5 }
}
```

I could absolutely imagine using that.

But I agree with your concern. If that becomes *Forge itself*, suddenly you're maintaining:

* an IDE
* a rendering API
* input handling
* timing/game loops
* drawing primitives
* potentially audio
* assets
* maybe physics eventually
* documentation for an entire creative-coding framework

And then people start evaluating Forge partly as a graphics framework rather than as an IDE.

I would instead make that a **separate Forge project/library**, perhaps:

**Forge Sketch**

or

**Forge Canvas**

Then Forge could have first-class support for it through a project template:

```text
New Project

Java
Kotlin
C++

Creative Coding
  Kotlin Sketch
```

That gives you the fun part without coupling the identities.

It could even become a fantastic *demonstration* of Forge's extensibility:

> “Forge Sketch is not built into the IDE. It's a normal library/project type that integrates through Forge's 
> extension APIs.”

That is much more interesting architecturally.

---

## The learning direction is stronger than “a better BlueJ”

I wouldn't brand Forge as an educational IDE.

That has a hidden downside: once somebody becomes experienced, an “educational IDE” can sound like something they're 
supposed to graduate away from.

Instead, take the *good ideas* from educational IDEs:

* show what compiler command is running
* show what a classpath actually contains
* make project structure understandable
* explain errors clearly
* expose build stages
* visualize classes/modules
* make toolchains easy to inspect
* make project configuration approachable

…but keep Forge capable enough that somebody doesn't have to leave just because they know what they're doing.

So rather than:

> Forge — IDE for learning programming

I'd think:

> **Forge — IDE for understanding your program.**

There is a subtle but important difference.

A beginner benefits enormously from that.

So does someone learning Rust after already knowing Java.

And frankly, so does an experienced programmer debugging why CMake, `JAVA_HOME`, a classpath, or a compiler invocation 
is behaving strangely.

You've encountered those exact kinds of problems yourself while building Forge.

---

# I also agree with you about the `.kts` problem

I think “hackable IDE” sounds cooler than it actually becomes if interpreted as:

```text
Drop a Kotlin script in a directory → modify arbitrary Forge UI
```

Because then you need an API for:

* adding menus
* removing menus
* adding tool windows
* actions
* project events
* editor access
* syntax highlighting
* settings
* persistence
* threads/EDT handling
* lifetime management
* unloading
* API compatibility

At that point…

**Congratulations, you've invented a plugin SDK.** 😄

And your existing language plugin system is already moving in that direction much more cleanly.

Where `.kts` *does* sound excellent is **small automation**.

For example:

```text
.forge/
└── scripts/
    ├── format-project.kts
    ├── generate-assets.kts
    └── package-release.kts
```

Forge could expose a deliberately tiny API:

```kotlin
forgeScript {
    action("Generate Assets") {
        exec("./tools/generate-assets")
        refreshProject()
    }
}
```

Then scripts appear under:

```text
Tools
└── Project Scripts
    ├── Generate Assets
    └── Package Release
```

That's hackability without handing scripts the keys to your entire Swing hierarchy.

And for **language plugins**, Kotlin becomes even more interesting.

You could eventually support a lightweight language definition like:

```kotlin
language("Lua") {
    extensions("lua")

    lexer {
        keywords("function", "local", "end", "if", "then")
        lineComment("--")
        strings("\"", "'")
    }

    run {
        executable("lua")
        arguments { file.path }
    }
}
```

That's a genuinely distinctive idea.

A user who wants basic Forge support for some obscure language might be able to write 50 lines of Kotlin rather than 
implement a full Java plugin.

Then, if they need completion/debugging/etc., they graduate to the proper plugin API.

**That is a version of “hackable Forge” I really like.**

---

# LSP: absolutely consider it, but don't make it the identity

Claude's reasoning there makes sense.

Implementing LSP potentially gives Forge access to:

* completion
* diagnostics
* hover information
* go-to-definition
* references
* document symbols
* rename
* formatting

across a huge number of languages.

And it saves you from writing a Java-grade semantic analyser for every language you support.

I think Forge should probably support LSP eventually.

But:

> **LSP should be an implementation capability of Forge, not the reason Forge exists.**

Otherwise you end up competing directly on:

```text
Forge
VS Code
Zed
Sublime + LSP
Neovim + LSP
Kate
...
```

and the question becomes:

> “Why wouldn't I just use VS Code?”

Instead, Forge could say:

> “This language plugin uses `clangd` for code intelligence.”

And then—very Forge-like—let the user inspect it:

```text
C++ LANGUAGE SERVICES

Syntax highlighting
  Forge C++ Lexer

Code intelligence
  clangd 22.1
  Status: Running

Compiler
  g++ 15.2

Build system
  CMake 4.2

Debugger
  Not configured

[ View LSP Log ]
[ Restart clangd ]
[ Show Launch Command ]
```

Now LSP reinforces your identity of **transparent tooling**.

That's much more interesting than pretending the language server doesn't exist.

---

# Portability is an excellent secondary trait

The portable idea fits Forge surprisingly well.

Especially because Forge is already relatively self-contained and doesn't need to become some huge cloud-connected 
ecosystem.

You could support two installation modes:

```text
NORMAL MODE

%APPDATA%/Forge/
├── settings
├── plugins
└── cache
```

versus:

```text
PORTABLE MODE

Forge/
├── Forge.exe
├── runtime/
├── plugins/
├── config/
├── cache/
└── portable.flag
```

Presence of:

```text
portable.flag
```

could simply tell Forge:

> Don't touch the host machine's user configuration. Put everything here.

That would be fantastic for:

* USB drives
* school computers
* testing Forge
* carrying a configured development environment around
* keeping multiple Forge versions isolated

But again, I see that as:

**“Forge happens to be really portable.”**

Not:

**“Forge is the portable IDE.”**

It makes the product nicer without constraining its future.

---

# And Focus Mode is exactly what you said: a feature

I wouldn't even make it merely a checkbox.

I'd fold it into the workspace system we discussed.

Something like:

```text
Workspace

✓ Code
  Run
  Debug
  Focus
```

Focus:

```text
┌──────────────────────────────────────────┐
│                                          │
│                                          │
│             editor only                  │
│                                          │
│                                          │
└──────────────────────────────────────────┘
```

And then optionally:

```text
Focus Mode

[x] Hide project explorer
[x] Hide toolbar
[x] Hide status bar
[x] Hide breadcrumbs
[x] Dim non-active methods
[x] Center editor
[ ] Hide line numbers
```

“Launch in Focus workspace” can then be a startup preference.

That scales better than special-casing focus behavior throughout Forge.

---

# So what *should* the identity be?

I think there are three concepts from all these proposals that actually reinforce one another rather than pulling 
Forge in different directions:

### **1. Understandable**

Forge makes normally-hidden IDE behavior visible.

Build plans.

Commands.

Toolchains.

Language servers.

Classpath.

Project structure.

Execution history.

Why a project is broken.

### **2. Extensible**

But not “every Swing object is exposed to arbitrary scripts.”

Instead there are layers:

```text
Simple
  .kts automation / language definitions

↓ need more power

Plugin API
  LanguageProvider
  Toolchain
  settings
  actions

↓ eventually

Full Forge Plugin SDK
```

That's much healthier.

### **3. Lightweight in philosophy**

And I don't necessarily mean memory usage.

I mean Forge doesn't try to control everything.

Open a folder.

Use external compilers.

Use `clangd`.

Use CMake.

Use `javac`.

Show the actual command.

Put plugins wherever you want.

Run portably if you want.

Forge becomes the **workbench connecting these tools**, rather than an enormous black box attempting to replace them.

---

That gives you room for a pretty interesting architecture:

```text
                    FORGE

          ┌──────────────────────┐
          │       Editor         │
          └──────────┬───────────┘
                     │
              Language Service
                     │
        ┌────────────┼────────────┐
        │            │            │
      Lexer         LSP       Toolchain
        │            │            │
      Forge       clangd        CMake
                  jdtls         javac
                  etc.          kotlinc

        Everything remains inspectable.
```

And then extensions can live around that.

---

# Where Forge Sketch fits

This is why I **wouldn't abandon that idea**.

Quite the opposite.

I'd make Forge Sketch one of the first projects that proves this architecture works.

Imagine Forge 1.4 or something:

```text
New Project
─────────────────────

Java Application
Kotlin Application
C++ Application

Examples
  Bouncing Ball
  Conway's Game of Life

Installed Templates
  Forge Sketch
```

You create one and get:

```text
MySketch/
├── src/
│   └── Main.kt
├── libs/
│   └── forge-sketch.jar
└── .forge/
```

Then running it produces your canvas.

No special rendering code in Forge itself.

No special editor.

No “game engine mode.”

Forge just understands the project exceptionally well.

In fact, I'd go one step further:

**don't keep Forge Sketch inside the ForgeIDE repository.**

Put it in something like:

```text
willclay/
├── ForgeIDE
├── ForgeSketch
└── forge-language-plugins
```

Now you've started building that **Forge ecosystem** idea you've mentioned before, without turning ForgeIDE into a 
monolith.

---

## The direction I'd take from 1.1

If I were deciding what to spend the next few releases on, I'd roughly think:

**1.2 — Understanding**

Forge Doctor, execution receipts, build plans, Project X-Ray, improved toolchain detection.

**1.3 — Extensibility**

Plugin manager, richer `LanguageProvider`, extension metadata, project automation `.kts`, maybe experimental simple 
language scripts.

**1.4 — Intelligence**

Initial LSP client. Start with one language where it provides a very obvious benefit, probably C++/`clangd`, rather 
than immediately attempting twelve languages.

**1.5 — Experience**

Workspace profiles, Focus workspace, better navigation, visual polish, portable mode.

And independently:

**Forge Sketch 0.1**

A fun separate project that dogfoods Kotlin support and gives you an excuse to build something visual without 
distorting Forge's architecture.

That roadmap also has a really nice property:

Every release makes **Forge itself better**, rather than attempting to match another entry in IntelliJ's 500-feature 
checklist.

So I don't think you need to choose between Claude's six identities.

Take pieces from several of them, but make the core identity simpler:

> **Forge is a transparent, approachable programming workbench: powerful enough for real projects, small enough to 
> understand, and designed so the tools underneath your code never disappear behind the IDE.**

That direction feels much more compatible with the Forge you've actually built than turning it into either “little 
IntelliJ” or “little VS Code.”

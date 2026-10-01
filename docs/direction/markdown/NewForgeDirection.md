I think the direction is starting to become clearer precisely because you *don't* need to choose one of those ideas wholesale.

If I were shaping Forge from 1.1 onward, I'd separate everything into three categories:

| Role                     | What belongs there                                                          |
| ------------------------ | --------------------------------------------------------------------------- |
| **Forge's identity**     | Understandable, approachable, extensible IDE for actually building projects |
| **Flagship experiences** | Creative coding / Forge Sketches, learning-oriented features                |
| **Supporting features**  | Focus Mode, portable mode, LSP, scripting                                   |

That distinction solves a lot of the tension you're running into.

## I wouldn't turn Forge into “the Processing competitor”

I really like your Kotlin DSL.

This:

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

is exactly the sort of thing I'd expect to enjoy using.

But I wouldn't make **Creative Coding** the definition of Forge.

I'd make it a first-class Forge project type:

```text
New Project

Java Application
Kotlin Application
C++ Application

Creative
  Forge Sketch
  Java2D Sketch

Learning
  Java Playground

Other
  Empty Project
```

Then you can build something genuinely polished around it.

For example, a Forge Sketch project could automatically give you:

```text
┌───────────────────┬──────────────────────┐
│                   │                      │
│   Code Editor     │    Live Preview      │
│                   │                      │
│                   │      ●               │
│                   │                      │
├───────────────────┴──────────────────────┤
│ FPS: 60    800×600     Running           │
└──────────────────────────────────────────┘
```

With:

* live restart,
* screenshots,
* animation pause,
* colour picker,
* mouse-coordinate inspector,
* asset browser,
* drawing API documentation,
* maybe eventually shaders/audio/input.

That would be an **excellent Forge-specific feature** without trapping Forge inside that niche.

And because you're using Kotlin, you get a really nice language for this style of API.

---

# The “better BlueJ” idea is actually surprisingly compatible

I wouldn't make Forge look like BlueJ.

That's the important distinction.

Instead, steal BlueJ's underlying philosophy:

> programming tools should help people understand what the program actually consists of.

This fits beautifully with the direction we discussed earlier.

Imagine opening a beginner Java project and having an optional **Explore** workspace:

```text
PROJECT STRUCTURE

             ┌──────────────┐
             │     Game     │
             └──────┬───────┘
                    │ owns
           ┌────────▼────────┐
           │     Player      │
           └───┬─────────┬───┘
               │         │
           uses│         │uses
               ▼         ▼
          Inventory    Weapon
```

Click `Player`.

Forge shows:

```text
Player
────────────────────────

Fields
  int health
  Vector2 position
  Inventory inventory

Methods
  move(double x, double y)
  damage(int amount)
  attack()

Extends
  Entity

Used by
  Game
  Level
```

Then:

**Open Source**

That's useful for a learner.

But it's also useful to an experienced programmer.

That's an important quality for Forge features: **don't make “learning” synonymous with “beginner-only.”**

---

# I agree with your concern about `.kts` hackability

There's a key architectural distinction here.

You don't want:

```text
write script
     ↓
prototype cool thing
     ↓
rewrite it as Java/Kotlin
     ↓
modify Forge core
     ↓
recompile Forge
```

That's not really a script extension system.

It's a prototype system.

What you actually want is:

```text
Forge Core
    │
    ├── Extension API
    │
    └── Script Host
            │
            ├── my-extension.kts
            ├── git-tools.kts
            └── silly-clock.kts
```

And the script stays the extension.

For example, suppose the API eventually supported:

```kotlin
forgeExtension("clock") {

    action("Show Time") {
        println(LocalTime.now())
    }

    menu("Tools") {
        action("Show Time")
    }

    toolWindow("Clock") {
        label("Hello from Kotlin")
    }
}
```

Forge loads that `.kts` dynamically.

There's no copying anything into the IDE.

That's what would make scripting genuinely interesting.

Your extension API becomes the boundary.

Something like:

```text
ExtensionContext
├── actions
├── menus
├── toolWindows
├── notifications
├── project
├── editor
├── settings
└── languages
```

Then both compiled plugins **and** scripts can talk to the same system.

That actually leads somewhere very interesting architecturally:

```text
             Forge Extension API
                    ▲
          ┌─────────┴─────────┐
          │                   │
     Plugin JARs          Kotlin Scripts
          │                   │
      serious              lightweight
      extensions           customisation
```

That's a much better version of “Hackable Forge.”

And because you've already built an action system, services, workbench, language providers and plugin discovery, this 
isn't completely disconnected from Forge's existing architecture.

---

# `.kts` could be especially good for language definitions

This part of your idea I really like.

Imagine being able to write:

```kotlin
language {
    id = "lua"
    name = "Lua"

    extensions = setOf(".lua")

    comments {
        line = "--"
        block = "--[[" to "]]"
    }

    keywords {
        +"function"
        +"local"
        +"end"
        +"if"
        +"then"
        +"else"
    }

    run {
        command("lua", file)
    }
}
```

Now obviously real language support gets much more complicated than this.

But that could represent **Level 1 language support**.

Forge might eventually distinguish:

```text
Language support levels

Basic
  Syntax highlighting
  File recognition
  Run command

Toolchain
  Build
  Run
  Project settings

Smart
  Completion
  Diagnostics
  Navigation

Full
  Refactoring
  Semantic analysis
```

A `.kts` file could comfortably create Basic support.

A JAR plugin could implement deeper Forge APIs.

And an LSP could provide Smart support.

Now these apparently competing ideas suddenly fit together.

---

# This is where I think LSP belongs

I would **absolutely investigate LSP**.

I would **not make LSP the identity of Forge**.

There's a big difference.

Otherwise Forge becomes:

> Swing VS Code.

And I agree with your concern. That's a hard market to distinguish yourself in.

Instead, I'd make LSP another backend supported by the Forge language architecture.

Something conceptually like:

```java
public interface LanguageIntelligence
{
    CompletionProvider completion();

    DiagnosticsProvider diagnostics();

    NavigationProvider navigation();
}
```

Then Java could eventually have:

```text
JavaLanguage
    ↓
Forge Java intelligence
```

while some less-important language might use:

```text
GoLanguage
    ↓
LspLanguageIntelligence
    ↓
gopls
```

You could even have:

```text
Kotlin
├── Forge lexer
├── Forge toolchain
└── LSP intelligence
```

That means Forge doesn't surrender its architecture to LSP.

**Forge consumes LSP.**

Huge difference.

It lets Forge support a bunch of languages without you personally writing:

* completion engines,
* semantic parsers,
* diagnostics systems,
* symbol resolvers,

for twenty languages.

Meanwhile languages you care deeply about can still have custom implementations.

---

# Portable mode is fantastic — but also not the identity

This one strikes me as a classic Forge feature.

Imagine:

```text
ForgeIDE/
├── ForgeIDE.exe
├── runtime/
├── plugins/
├── config/
├── projects/
└── forge-portable
```

Forge sees:

```text
forge-portable
```

and switches from:

```text
%APPDATA%/ForgeIDE
~/.forge
```

to:

```text
./config
./plugins
```

Done.

No installer.

No registry dependency.

No user-directory pollution.

Copy it to:

```text
E:\Development\ForgeIDE\
```

and everything goes with you.

That's genuinely useful for schools, computer labs, USB drives and messing around on another computer.

But again:

**great Forge capability, weak overall identity.**

That's okay.

Not every good feature has to explain what the entire IDE stands for.

---

# Focus Mode should definitely just be a feature

I agree almost completely with your conclusion here.

I'd probably take it slightly further than a checkbox, though.

Instead of only:

```text
☑ Enable Focus Mode
```

I'd incorporate it into those Workspaces we discussed:

```text
Workspace

● Code
○ Run
○ Explore
○ Focus
```

Focus could automatically:

* hide project tree,
* hide toolbar,
* hide status clutter,
* hide breadcrumbs if desired,
* centre the editor,
* increase editor margins,
* optionally dim unrelated code.

Then have:

```text
Settings → Appearance → Focus

☑ Dim code outside current method
☑ Hide gutter except current line
☑ Hide editor tabs when only one file is open
☑ Centre editor content
Dim intensity: ─────●────
```

That gives you the cool calm-IDE experience without turning Forge into a product whose main selling point is whitespace.

---

# So what should Forge actually be?

This is where I've landed after seeing 1.1 and hearing these ideas.

I wouldn't describe Forge as:

> lightweight IntelliJ

or:

> educational IDE

or:

> creative coding IDE

or:

> hackable IDE

or:

> polyglot IDE.

I'd describe the philosophy more like:

> **Forge is an approachable programming environment that exposes how your projects and tools actually work, while 
> staying easy to extend and experiment with.**

And then Forge has several pillars.

### 1. **Understand**

The ideas we discussed earlier:

```text
Forge Doctor
Project X-Ray
Execution Plans
Execution Receipts
visible compiler commands
toolchain diagnostics
class relationships
```

Forge tries not to hide the machinery.

### 2. **Create**

Your creative-coding idea:

```text
Forge Sketches
Java2D/Kotlin DSL
live previews
small experiments
scratch projects
```

Forge should make starting something fun extremely cheap.

### 3. **Extend**

Your current plugin architecture evolves into:

```text
Plugin JARs
Kotlin scripts
language scripts
extension points
optional LSP adapters
```

You don't have to fork Forge to make Forge yours.

### 4. **Focus**

Workspaces:

```text
Code
Run
Explore
Focus
```

Users decide how much IDE they want on screen.

### 5. **Take it anywhere**

Portable mode and good offline support.

No account requirement.

No cloud dependency for core functionality.

Compiler/toolchain stays local.

---

And interestingly, **Learning becomes an emergent property of all of this**.

You don't need giant banners saying:

> 🎓 LEARN JAVA WITH FORGE!

Someone learning Java benefits because Forge shows:

```text
javac Main.java
```

instead of mysteriously pressing Build.

They can inspect the classes.

They can see why their JDK isn't detected.

They can visualize their project.

They can create a bouncing ball in ten lines.

But an experienced developer also benefits from all of those things.

That's much stronger than deliberately making Forge “the beginner IDE.”

---

## And I think your Sketch DSL is worth building

Not necessarily *right now* as Forge's new reason for existence.

But I genuinely think it could become one of those features where someone sees a screenshot or 20-second demo 
and goes:

> “Wait, Forge can do **that**?”

Imagine eventually:

```kotlin
fun main() = sketch("Particles", 1280, 720) {

    val particles = mutableListOf<Particle>()

    onMousePressed {
        repeat(20) {
            particles += Particle(mouseX, mouseY)
        }
    }

    draw {
        background("#151515")

        particles.forEach {
            it.update()
            circle(it.x, it.y, 5)
        }
    }
}
```

Press **Run**.

The editor splits.

Particles appear beside your code.

Modify `5` → `15`.

Save.

Preview reloads.

That's a very demonstrable Forge feature.

And because you're not positioning the *entire IDE* around creative coding, the user can then close that project 
and open:

```text
ForgeIDE itself
```

with Java, Kotlin, C++, Rust and whatever else.

---

If I were deciding the next few major versions, I'd therefore aim roughly for:

**1.2 — Understand**
Forge Doctor, execution history/plans, project inspection.

**1.3 — Workspaces**
Code/Run/Explore/Focus, better tool-window infrastructure.

**1.4 — Extend**
formal extension API, plugin manager, basic `.kts` scripting.

**1.5 — Create**
Forge Sketches, Kotlin creative-coding DSL, preview workspace.

And sometime around there, start experimenting with an **LSP adapter** rather than rewriting language intelligence 
for every language on Earth.

That path doesn't require you to decide whether Forge is “BlueJ vs Processing vs VS Code vs IntelliJ.”

It lets Forge gradually become its own thing.

And I think that's actually the most promising direction you've considered so far: **don't beat IntelliJ at being 
IntelliJ. Build the IDE whose features make sense because *Forge* has them.**

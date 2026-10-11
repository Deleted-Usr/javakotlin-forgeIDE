# Forge Census

Forge Census counts the lines in a folder of source code, sorted by language,
and splits them into code, comments, and blank lines. It is one Rust package
with two programs: a console tool with no dependencies, and a window built with
[egui](https://github.com/emilk/egui). Point it at ForgeIDE's own repository to
see what Forge is made of.

The only requirement is a Rust toolchain with `cargo` on your `PATH`
(Rust 1.95 or newer, which egui needs). Cargo downloads everything else.

## Open and run it

1. Launch ForgeIDE and choose **File > Open Project...**.
2. Select the `Forge Census - Rust` directory.
3. Pick a run configuration from the toolbar and press **Run**:
   - **Census (window)** runs `src/bin/census-gui.rs` and opens the window.
   - **Census (console)** runs `src/main.rs` and prints a table to Forge's console.

Both start by counting the project's own folder. In the window, use
**Browse…** or type a path and press **Scan**. For the console version, add a
folder as a program argument in the run configuration, such as `../..` for the
whole ForgeIDE repository.

The first run takes a minute or two, because Cargo downloads and compiles
egui and its dependencies. After that, only your own code is recompiled.
Every `cargo` command Forge runs is shown in the console, so you can copy it
into a terminal and get exactly the same result.

```text
Language     Files     Lines      Code  Comments     Blank
----------------------------------------------------------
Java           200    25,504    18,081     3,347     4,076
Kotlin          74     7,981     5,598     1,149     1,234
...
```

## What `Cargo.toml` is doing

Open `Cargo.toml` first, because it decides what gets built:

- **`[dependencies]`** lists `eframe` (egui's window framework) and `rfd`
  (the system's folder dialog), both marked `optional = true`.
- **`[features]`** defines a `gui` switch that turns those two on, and
  `default = ["gui"]` turns it on unless you say otherwise.
- **`[[bin]]`** says `census-gui` needs the `gui` feature. Without it, Cargo
  skips that program instead of failing to compile it.
- **`[profile.dev.package."*"]`** optimises the dependencies even in debug
  builds, because unoptimised egui is sluggish.

Try this: open the Rust page of the project settings and tick **No default
features**. Run the console configuration again and look at the console. The
command now includes `--no-default-features`, egui is not compiled at all,
and the console program builds in a few seconds. The window configuration
can't run until you untick the box again.

## Where to look

The library (`src/lib.rs`) does all the counting, and the two programs only
show the results. Nothing in the library knows about windows, which is why the
console program still builds when the `gui` feature is off.

- `language.rs`: an `enum Language` with a `match` for each question you can
  ask about a language. If you add a language, the compiler shows you every
  `match` you still need to update.
- `count.rs`: sorts each line into code, comments, or blank. A small state
  machine follows block comments across lines and skips over strings. It also
  has `#[test]`s, which you can run with `cargo test` in a terminal.
- `scan.rs`: walks the folder with `std::fs`. It uses `Result` and `?` for
  errors, but a file that can't be read is recorded as a `Problem` and the
  scan carries on.
- `report.rs`: implements `Display`, so `main.rs` can just write
  `print!("{census}")`.
- `main.rs`: the console program. It reads its arguments and returns an
  `ExitCode`.
- `bin/census-gui.rs`: the window. Its `Status` enum shows how enums can carry
  data. It scans on a background thread and sends the result back over a
  channel, which is Rust's version of Swing's "don't block the EDT" rule.

//! Forge Census counts the lines in a folder of source code, by language.
//!
//! This library holds everything the two programs share:
//!
//! - `src/main.rs` prints a table to the console.
//! - `src/bin/census-gui.rs` shows the same numbers in a window.
//!
//! Nothing in here knows about windows or egui. That is why the console
//! program still builds when the `gui` feature is turned off: the library
//! never needed those crates in the first place.

mod count;
mod language;
mod report;
mod scan;

// The modules above are private, so these lines choose what the programs
// can see: `forge_census::scan` rather than `forge_census::scan::scan`.
pub use count::LineCounts;
pub use language::{CommentSyntax, Language};
pub use report::thousands;
pub use scan::{Census, Problem, Totals, scan};

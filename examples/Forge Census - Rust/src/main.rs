//! The console version of Forge Census.
//!
//! Run it with a folder as its program argument, or with none to count the
//! current folder: `cargo run --bin forge-census -- ../..`

use std::env;
use std::path::PathBuf;
use std::process::ExitCode;
use std::time::Instant;

use forge_census::thousands;

fn main() -> ExitCode {
    // `nth(0)` would be the program itself, so the first argument is `nth(1)`.
    // `args_os` rather than `args`, because a folder name does not have to
    // be valid Unicode, and `args` would crash on one that is not.
    let folder = env::args_os()
        .nth(1)
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from("."));

    let started = Instant::now();

    match forge_census::scan(&folder) {
        Ok(census) => {
            print!("{census}");
            println!();
            println!(
                "Counted {} files in {} ms.",
                thousands(census.total().files),
                started.elapsed().as_millis()
            );

            for problem in &census.problems {
                eprintln!("Skipped {}: {}", problem.path.display(), problem.error);
            }

            ExitCode::SUCCESS
        }
        Err(error) => {
            eprintln!("Could not scan {}: {error}", folder.display());
            ExitCode::FAILURE
        }
    }
}

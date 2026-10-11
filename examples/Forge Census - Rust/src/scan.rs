//! Walking a folder and adding up every file Forge Census recognises.

use std::collections::HashMap;
use std::fs;
use std::io;
use std::ops::AddAssign;
use std::path::{self, Path, PathBuf};

use crate::count::{LineCounts, count_lines};
use crate::language::Language;

/// Folders full of generated files rather than source code. Hidden folders
/// such as `.git` and `.gradle` are skipped as well.
const SKIPPED_FOLDERS: [&str; 5] = ["target", "build", "out", "dist", "node_modules"];

/// The numbers for one language.
#[derive(Debug, Clone, Copy, Default)]
pub struct Totals {
    pub files: usize,
    pub lines: LineCounts,
}

impl AddAssign for Totals {
    fn add_assign(&mut self, other: Totals) {
        self.files += other.files;
        self.lines += other.lines;
    }
}

/// A file or folder that could not be read. The scan carries on without it.
#[derive(Debug)]
pub struct Problem {
    pub path: PathBuf,
    pub error: io::Error,
}

/// The result of scanning one folder.
#[derive(Debug)]
pub struct Census {
    pub root: PathBuf,
    pub languages: HashMap<Language, Totals>,
    pub problems: Vec<Problem>,
}

impl Census {
    /// Every language found, the one with the most code first.
    ///
    /// A `HashMap` has no order of its own, so we copy its entries into a
    /// `Vec` and sort that.
    pub fn ranked(&self) -> Vec<(Language, Totals)> {
        let mut rows: Vec<(Language, Totals)> = self
            .languages
            .iter()
            .map(|(&language, &totals)| (language, totals))
            .collect();

        rows.sort_by(|a, b| {
            b.1.lines
                .code
                .cmp(&a.1.lines.code)
                .then_with(|| a.0.name().cmp(b.0.name()))
        });

        rows
    }

    /// Every language added together.
    pub fn total(&self) -> Totals {
        let mut total = Totals::default();
        for totals in self.languages.values() {
            total += *totals;
        }
        total
    }
}

/// Counts every recognised file under `root`.
///
/// This fails only if `root` itself is not a folder. Anything deeper that
/// cannot be read becomes a [`Problem`] in the result instead, because one
/// locked file should not throw away the whole scan.
pub fn scan(root: &Path) -> io::Result<Census> {
    // `absolute` turns `.` into a full path for display, without the `\\?\`
    // prefix that `canonicalize` adds on Windows.
    let root = path::absolute(root)?;

    if !root.is_dir() {
        return Err(io::Error::new(
            io::ErrorKind::NotADirectory,
            "this is not a folder",
        ));
    }

    let mut census = Census {
        root: root.clone(),
        languages: HashMap::new(),
        problems: Vec::new(),
    };

    visit(&root, &mut census);

    Ok(census)
}

fn visit(folder: &Path, census: &mut Census) {
    let entries = match fs::read_dir(folder) {
        Ok(entries) => entries,
        Err(error) => {
            census.problems.push(Problem {
                path: folder.to_path_buf(),
                error,
            });
            return;
        }
    };

    for entry in entries {
        // Each entry can fail on its own, for example if a file is deleted mid-scan.
        let entry = match entry {
            Ok(entry) => entry,
            Err(error) => {
                census.problems.push(Problem {
                    path: folder.to_path_buf(),
                    error,
                });
                continue;
            }
        };

        let path = entry.path();

        // `file_type` does not follow symbolic links, so a link pointing back
        // up the tree cannot send the scan round in circles forever.
        let file_type = match entry.file_type() {
            Ok(file_type) => file_type,
            Err(error) => {
                census.problems.push(Problem { path, error });
                continue;
            }
        };

        if file_type.is_dir() {
            if !is_skipped(&path) {
                visit(&path, census);
            }
        } else if file_type.is_file()
            && let Some(language) = Language::from_path(&path)
        {
            count_file(&path, language, census);
        }
    }
}

fn count_file(path: &Path, language: Language, census: &mut Census) {
    match fs::read(path) {
        Ok(bytes) => {
            // `read_to_string` would refuse a file that is not valid UTF-8.
            // `from_utf8_lossy` swaps any bad bytes for a placeholder, so the
            // lines still get counted.
            let text = String::from_utf8_lossy(&bytes);

            // `entry(...).or_default()` finds this language's totals, or
            // starts them at zero the first time the language turns up.
            let totals = census.languages.entry(language).or_default();
            totals.files += 1;
            totals.lines += count_lines(&text, &language.comments());
        }
        Err(error) => census.problems.push(Problem {
            path: path.to_path_buf(),
            error,
        }),
    }
}

fn is_skipped(folder: &Path) -> bool {
    let Some(name) = folder.file_name().and_then(|name| name.to_str()) else {
        return false;
    };

    name.starts_with('.') || SKIPPED_FOLDERS.contains(&name)
}

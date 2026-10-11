//! The plain-text table the console program prints.

use std::fmt;

use crate::scan::{Census, Totals};

/// Implementing `Display` is what lets `main.rs` write `print!("{census}")`.
impl fmt::Display for Census {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        writeln!(f, "Forge Census of {}", self.root.display())?;
        writeln!(f)?;

        if self.languages.is_empty() {
            return writeln!(f, "No source files found.");
        }

        // `<` pads on the right (left-aligned), `>` pads on the left (right-aligned).
        writeln!(
            f,
            "{:<10} {:>7} {:>9} {:>9} {:>9} {:>9}",
            "Language", "Files", "Lines", "Code", "Comments", "Blank"
        )?;

        let rule = "-".repeat(58);
        writeln!(f, "{rule}")?;

        for (language, totals) in self.ranked() {
            row(f, language.name(), &totals)?;
        }

        writeln!(f, "{rule}")?;
        row(f, "Total", &self.total())
    }
}

fn row(f: &mut fmt::Formatter, name: &str, totals: &Totals) -> fmt::Result {
    writeln!(
        f,
        "{:<10} {:>7} {:>9} {:>9} {:>9} {:>9}",
        name,
        thousands(totals.files),
        thousands(totals.lines.total()),
        thousands(totals.lines.code),
        thousands(totals.lines.comments),
        thousands(totals.lines.blank)
    )
}

/// `12345` becomes `"12,345"`.
pub fn thousands(number: usize) -> String {
    let digits = number.to_string();
    let mut grouped = String::with_capacity(digits.len() + digits.len() / 3);

    for (index, digit) in digits.chars().enumerate() {
        if index > 0 && (digits.len() - index).is_multiple_of(3) {
            grouped.push(',');
        }
        grouped.push(digit);
    }

    grouped
}

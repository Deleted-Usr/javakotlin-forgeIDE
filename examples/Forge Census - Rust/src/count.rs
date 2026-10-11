//! Sorting the lines of one file into code, comments and blank lines.
//!
//! This is a line counter, not a compiler, so it takes a few shortcuts.
//! It understands `"double-quoted"` strings, so `"http://"` is not mistaken
//! for a comment, but not single-quoted or multi-line strings, and it does
//! not track Rust's nested `/* /* */ */` comments. Real code rarely trips
//! over any of these, and the counts stay close enough to be useful.

use std::ops::AddAssign;

use crate::language::CommentSyntax;

#[derive(Debug, Clone, Copy, Default, PartialEq, Eq)]
pub struct LineCounts {
    /// Lines with any code on them, even if a comment follows it.
    pub code: usize,
    /// Lines that hold only a comment.
    pub comments: usize,
    pub blank: usize,
}

impl LineCounts {
    pub fn total(&self) -> usize {
        self.code + self.comments + self.blank
    }
}

/// Lets two counts be added with `+=`, which keeps the totals code tidy.
impl AddAssign for LineCounts {
    fn add_assign(&mut self, other: LineCounts) {
        self.code += other.code;
        self.comments += other.comments;
        self.blank += other.blank;
    }
}

pub fn count_lines(text: &str, syntax: &CommentSyntax) -> LineCounts {
    let mut counts = LineCounts::default();

    // A block comment can start on one line and end several lines later,
    // so whether we are inside one has to carry over from line to line.
    let mut in_block = false;

    for line in text.lines() {
        match classify(line, syntax, &mut in_block) {
            LineKind::Code => counts.code += 1,
            LineKind::Comment => counts.comments += 1,
            LineKind::Blank => counts.blank += 1,
        }
    }

    counts
}

enum LineKind {
    Code,
    Comment,
    Blank,
}

/// A comment found partway through a line.
enum Marker {
    Line,
    /// A block comment opens here; the number is the length of its opener.
    BlockOpen(usize),
}

fn classify(line: &str, syntax: &CommentSyntax, in_block: &mut bool) -> LineKind {
    let mut rest = line.trim();
    if rest.is_empty() {
        return LineKind::Blank;
    }

    let mut has_code = false;
    let mut has_comment = false;

    // Walk along the line, jumping from comment to comment, until nothing is left.
    while !rest.is_empty() {
        if *in_block {
            has_comment = true;

            // We can only be inside a block comment if the language has them.
            let (_, close) = syntax
                .block
                .expect("only languages with block comments can be inside one");

            match rest.find(close) {
                Some(end) => {
                    rest = rest[end + close.len()..].trim_start();
                    *in_block = false;
                }
                None => break, // The comment carries on to the next line.
            }
            continue;
        }

        match next_comment(rest, syntax) {
            Some((start, Marker::Line)) => {
                has_code |= start > 0;
                has_comment = true;
                break;
            }
            Some((start, Marker::BlockOpen(opener))) => {
                has_code |= start > 0;
                has_comment = true;
                *in_block = true;
                rest = &rest[start + opener..];
            }
            None => {
                has_code = true;
                break;
            }
        }
    }

    if has_code {
        LineKind::Code
    } else if has_comment {
        LineKind::Comment
    } else {
        LineKind::Blank
    }
}

/// Finds where the next comment starts in `text`, skipping over anything
/// inside double quotes.
fn next_comment(text: &str, syntax: &CommentSyntax) -> Option<(usize, Marker)> {
    let mut in_string = false;
    let mut escaped = false;

    // `char_indices` gives byte positions, which is what slicing a `&str` needs.
    for (index, character) in text.char_indices() {
        if in_string {
            if escaped {
                escaped = false;
            } else if character == '\\' {
                escaped = true;
            } else if character == '"' {
                in_string = false;
            }
            continue;
        }

        if character == '"' {
            in_string = true;
            continue;
        }

        let rest = &text[index..];

        // Check for a block comment first: Lua's `--[[` also begins with its line comment `--`.
        if let Some((open, _)) = syntax.block
            && rest.starts_with(open)
        {
            return Some((index, Marker::BlockOpen(open.len())));
        }
        if let Some(line) = syntax.line
            && rest.starts_with(line)
        {
            return Some((index, Marker::Line));
        }
    }

    None
}

// `cargo test` compiles and runs these; a normal build leaves them out.
#[cfg(test)]
mod tests {
    use super::*;
    use crate::language::Language;

    fn counts(language: Language, text: &str) -> (usize, usize, usize) {
        let counts = count_lines(text, &language.comments());
        (counts.code, counts.comments, counts.blank)
    }

    #[test]
    fn code_followed_by_a_comment_is_code() {
        assert_eq!(
            counts(Language::Rust, "let x = 1; // one\n// just a comment\n\n"),
            (1, 1, 1)
        );
    }

    #[test]
    fn block_comments_can_span_lines() {
        let text = "/* first\n   middle\n   last */\nfn main() {}";
        assert_eq!(counts(Language::Rust, text), (1, 3, 0));
    }

    #[test]
    fn comment_markers_inside_strings_are_ignored() {
        assert_eq!(
            counts(Language::Kotlin, "val glob = \"src/**/*.kt\"\nval x = 2"),
            (2, 0, 0)
        );
    }

    #[test]
    fn lua_block_comments_are_not_line_comments() {
        assert_eq!(
            counts(Language::Lua, "--[[ start\nstill a comment ]]\nprint(1)"),
            (1, 2, 0)
        );
    }
}

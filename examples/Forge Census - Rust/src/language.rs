//! Which language a file is written in, and how that language writes comments.

use std::fmt;
use std::path::Path;

/// Every language Forge Census recognises.
///
/// To add one, add a variant here. The compiler will then point at every
/// `match` that does not handle it yet, so none can be forgotten.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum Language {
    Rust,
    Kotlin,
    Java,
    Cpp,
    C,
    Python,
    Lua,
    Toml,
    Markdown,
    Json,
}

/// How a language marks comments. `None` means it has no comment of that kind.
pub struct CommentSyntax {
    /// Starts a comment that runs to the end of the line, like `//`.
    pub line: Option<&'static str>,
    /// Opens and closes a comment that may span lines, like `/*` and `*/`.
    pub block: Option<(&'static str, &'static str)>,
}

impl Language {
    /// Works out the language from the file's extension, or `None` for any
    /// file Forge Census does not count.
    pub fn from_path(path: &Path) -> Option<Language> {
        // `?` returns `None` early: no extension, or one that is not valid Unicode.
        let extension = path.extension()?.to_str()?.to_ascii_lowercase();

        match extension.as_str() {
            "rs"         => Some(Language::Rust),
            "kt" | "kts" => Some(Language::Kotlin),
            "java"       => Some(Language::Java),
            "cpp" | "cc" | "cxx" | "hpp" | "hh" => Some(Language::Cpp),
            // ".h" files are counted as C though they may contain C++.
            "c" | "h"  => Some(Language::C),
            "py"   => Some(Language::Python),
            "lua"  => Some(Language::Lua),
            "toml" => Some(Language::Toml),
            "md"   => Some(Language::Markdown),
            "json" => Some(Language::Json),
            _      => None,
        }
    }

    pub fn name(self) -> &'static str {
        match self {
            Language::Rust     => "Rust",
            Language::Kotlin   => "Kotlin",
            Language::Java     => "Java",
            Language::Cpp      => "C++",
            Language::C        => "C",
            Language::Python   => "Python",
            Language::Lua      => "Lua",
            Language::Toml     => "TOML",
            Language::Markdown => "Markdown",
            Language::Json     => "JSON",
        }
    }

    pub fn comments(self) -> CommentSyntax {
        match self {
            Language::Rust | Language::Kotlin | Language::Java | Language::Cpp | Language::C => {
                CommentSyntax {
                    line: Some("//"),
                    block: Some(("/*", "*/")),
                }
            }
            Language::Python | Language::Toml => CommentSyntax {
                line: Some("#"),
                block: None,
            },
            Language::Lua => CommentSyntax {
                line: Some("--"),
                block: Some(("--[[", "]]")),
            },
            Language::Markdown => CommentSyntax {
                line: None,
                block: Some(("<!--", "-->")),
            },
            Language::Json => CommentSyntax {
                line: None,
                block: None,
            },
        }
    }
}

/// Lets a `Language` be printed with `{}`, just like a number or a string.
impl fmt::Display for Language {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        f.write_str(self.name())
    }
}

package com.willclay.forgeide.lang.api;

import com.willclay.forgeide.editor.DocCommentMarkdown;
import com.willclay.forgeide.highlighting.SyntaxHighlighter;
import com.willclay.forgeide.highlighting.Token;

import java.util.List;

/// Turns source text into coloured spans.
///
/// One method, and deliberately no state: the highlighter tokenizes the whole
/// document on every pass (see [SyntaxHighlighter]), so an implementation
/// that remembered where it got to last time would be remembering a lie.
@FunctionalInterface
public interface Lexer
{
    /// @return every coloured span, in ascending order and never overlapping
    List<Token> tokenize(String text);

    /// Turns one doc comment this lexer coloured as
    /// [com.willclay.forgeide.highlighting.TokenType#DOC_COMMENT] into Markdown,
    /// so the editor can show it rendered.
    ///
    /// It lives on the lexer because the lexer is what decided the text is a
    /// doc comment, so it is also the one that knows how that comment is
    /// written. The default understands the `/** */` and `///` family (Javadoc,
    /// KDoc, rustdoc, Doxygen); a language whose comments look nothing like
    /// that, such as Python's docstrings, overrides it.
    ///
    /// @param comment the comment exactly as written, markers and all
    /// @return Markdown, or an empty string when the comment says nothing
    default String docCommentMarkdown(String comment)
    {
        return DocCommentMarkdown.toMarkdown(comment);
    }

    /// Claims nothing. What a file no language recognises is highlighted with.
    Lexer PLAIN = text -> List.of();
}

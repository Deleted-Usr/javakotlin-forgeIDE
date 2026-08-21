package com.willclay.forgeide.lang.api;

import com.willclay.forgeide.highlighting.SyntaxHighlighter;
import com.willclay.forgeide.highlighting.Token;

import java.util.List;

/**
 * Turns source text into coloured spans.
 * <p>
 * One method, and deliberately no state: the highlighter tokenizes the whole
 * document on every pass (see {@link SyntaxHighlighter}), so an implementation
 * that remembered where it got to last time would be remembering a lie.
 */
@FunctionalInterface
public interface Lexer
{
    /** @return every coloured span, in ascending order and never overlapping */
    List<Token> tokenize(String text);

    /** Claims nothing. What a file no language recognises is highlighted with. */
    Lexer PLAIN = text -> List.of();
}

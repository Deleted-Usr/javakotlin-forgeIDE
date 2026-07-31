package main.java.com.willclay.forgeide.highlighting;

/**
 * The state the lexer can be in when it crosses a line boundary. This is the
 * whole trick behind incremental lexing: if you know the state a line *ends*
 * in, you can start lexing any later line without looking at anything above it.
 *
 * Only constructs that legally span lines belong here. Java strings and char
 * literals cannot, so they never produce a carry-over state — an unterminated
 * one is simply an ERROR token that stops at the newline.
 */
public enum LexState
{
    NORMAL,
    IN_BLOCK_COMMENT,
    IN_TEXT_BLOCK
}

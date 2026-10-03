package com.willclay.forgeide.highlighting;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Finds TO-DO spans inside the comments a lexer has already found.
///
/// A TO-DO may continue onto following comment lines, the way IntelliJ allows,
/// as long as each continuation line is indented further than the TO-DO marker.
/// Each line gets its own span, so the comment prefix (`//`, ` *`) at the
/// start of a continuation line keeps its normal comment colour.
///
/// No Swing here: the highlighter and the TO-DO panel both use it.
public final class TodoFinder
{
    /// From the marker to the end of the line, stopping before a closing `*/`
    public static final Pattern MARKER = Pattern.compile("\\b(?:todo|fixme)\\b[^\\n]*?(?=\\*/|\\n|$)",  Pattern.CASE_INSENSITIVE);

    /// Characters that can make up a comment prefix in any language this
    /// IDE is likely to see: `//`, `*`, `#`, `--`, `;`.
    private static final String COMMENT_PREFIX = "/*#-;!";

    private TodoFinder() { }

    /// @param tokens the lexer's output for `text`, in ascending order
    /// @return one span per line of every TO-DO, in ascending order
    public static List<Token> find(String text, List<Token> tokens)
    {
        List<Token> todos = new ArrayList<>();
        int lastEnd = 0; // so continuation lines are never searched twice

        for (Token token : tokens)
        {
            if (!isComment(token) || token.end() <= lastEnd) continue;

            Matcher matcher = MARKER.matcher(text);
            matcher.region(Math.max(token.start(), lastEnd), token.end());

            while (matcher.find())
            {
                todos.add(new Token(TokenType.TODO, matcher.start(), matcher.end()));
                lastEnd = followContinuation(text, tokens, matcher.start(), matcher.end(), todos);

                // The continuation may have run past this token (line
                // comments are one token per line), so carry on from here.
                if (lastEnd >= token.end()) break;
                matcher.region(lastEnd, token.end());
            }
        }

        return todos;
    }

    /// Where the run of comment lines containing `offset` ends.
    ///
    /// Editing one line of a TO-DO can change the colour of the lines below it
    /// (delete the word "TO-DO" and its continuation lines go back to grey), so
    /// a repaint has to reach this far, not just the edited line.
    public static int endOfCommentLines(String text, List<Token> tokens, int offset)
    {
        int end = lineEnd(text, offset);

        while (end < text.length())
        {
            int nextStart    = end + 1;
            int nextEnd      = lineEnd(text, nextStart);
            int firstVisible = skip(text, nextStart, nextEnd, " \t");

            if (firstVisible == nextEnd || commentAt(tokens, firstVisible) == null)
            {
                break;
            }

            end = nextEnd;
        }

        return end;
    }

    /// Adds a span for each continuation line after a TO-DO.
    ///
    /// @return the offset just after the last line that belonged to the TO-DO
    private static int followContinuation(String text, List<Token> tokens, int markerStart, int end, List<Token> todos)
    {
        int markerColumn = markerStart - lineStart(text, markerStart);

        while (true)
        {
            int newline = text.indexOf('\n', end);

            if (newline < 0) return end;

            int lineStart = newline + 1;
            int lineEnd   = lineEnd(text, lineStart);

            // The line must start inside a comment, or it is code and the TO-DO is over.
            int firstVisible = skip(text, lineStart, lineEnd, " \t");

            Token comment = commentAt(tokens, firstVisible);
            if (comment == null) return end;

            int body    = skip(text, skip(text, firstVisible, lineEnd, COMMENT_PREFIX), lineEnd, " \t");
            int bodyEnd = Math.min(lineEnd, comment.end());

            // A closing */ belongs to the comment, not the TO-DO
            int close = text.lastIndexOf("*/", bodyEnd);
            if (close >= body && close + 2 == bodyEnd) bodyEnd = close;

            boolean blank         = body >= bodyEnd;
            boolean indented      = body - lineStart > markerColumn;
            boolean startsNewTodo = MARKER.matcher(text).region(body, bodyEnd).lookingAt();

            if (blank || !indented || startsNewTodo) return end;

            todos.add(new Token(TokenType.TODO, body, bodyEnd));
            end = bodyEnd;
        }
    }

    /// Binary search, since the token list can be thousands long.
    private static Token commentAt(List<Token> tokens, int offset)
    {
        int low = 0;
        int high = tokens.size() - 1;

        while (low <= high)
        {
            int middle = (low + high) >>> 1;
            Token token = tokens.get(middle);

            if (token.end() <= offset)
            {
                low = middle + 1;
            }
            else if (token.start() > offset)
            {
                high = middle - 1;
            }
            else
            {
                return isComment(token) ? token : null;
            }
        }

        return null;
    }

    private static boolean isComment(Token token)
    {
        return token.type() == TokenType.COMMENT || token.type() == TokenType.DOC_COMMENT;
    }

    private static int skip(String text, int from, int limit, String characters)
    {
        while (from < limit && characters.indexOf(text.charAt(from)) >= 0)
        {
            from++;
        }

        return from;
    }

    private static int lineStart(String text, int offset)
    {
        return text.lastIndexOf('\n', offset - 1) + 1;
    }

    private static int lineEnd(String text, int offset)
    {
        int newline = text.indexOf('\n', offset);
        return newline < 0 ? text.length() : newline;
    }
}

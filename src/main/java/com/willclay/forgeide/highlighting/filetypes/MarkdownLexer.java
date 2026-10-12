package com.willclay.forgeide.highlighting.filetypes;

import com.willclay.forgeide.highlighting.Token;
import com.willclay.forgeide.highlighting.TokenType;
import com.willclay.forgeide.lang.api.Lexer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Colours the source side of a Markdown file.
///
/// Markdown is built out of lines: a heading, a list item, a quote and a
/// fence are each recognised by how a line *starts*. So, unlike the other
/// lexers, this one reads a line at a time and only looks inside a line for
/// inline code and links. The one thing it carries from line to line is
/// whether it is inside a fenced code block.
///
/// | Text                         | Colour                        |
/// |------------------------------|-------------------------------|
/// | `# Heading`                  | keyword                       |
/// | fence lines, `> quotes`      | comment                       |
/// | fenced code, `` `code` ``    | string                        |
/// | `-`, `*`, `1.`, `---`        | punctuation                   |
/// | `[text](url)`                | call for the text, string for the url |
///
/// These follow the Markdown preview's own source colours, plus links. Bold
/// and italic are left plain, because the editor has no colour that means
/// "emphasis", and borrowing one would suggest something that is not there.
public final class MarkdownLexer implements Lexer
{
    /// A backtick fence's info string may not contain a backtick, which is what
    /// keeps `` ```code``` `` on one line from opening a block.
    private static final Pattern FENCE = Pattern.compile(" {0,3}(?:(`{3,})[^`]*|(~{3,}).*)");

    /// One to six `#`, then a space or nothing: `#hashtag` is not a heading.
    private static final Pattern HEADING = Pattern.compile(" {0,3}#{1,6}(?:[ \\t].*)?");

    /// Three or more of the same `-`, `*` or `_`, with optional spaces between.
    private static final Pattern THEMATIC_BREAK = Pattern.compile(" {0,3}([-*_])(?:[ \\t]*\\1){2,}[ \\t]*");

    private static final Pattern BLOCK_QUOTE = Pattern.compile(" {0,3}>.*");

    /// `-`, `*`, `+`, `1.` or `1)`, followed by a space or the end of the line.
    private static final Pattern LIST_MARKER = Pattern.compile("[ \\t]*(?:[-*+]|\\d{1,9}[.)])(?=[ \\t]|$)");

    /// `[text](url)`, or an image: `![alt](src)`.
    private static final Pattern LINK = Pattern.compile("!?\\[[^\\]\\n]*]\\([^)\\n]*\\)");

    /// `<https://example.com>`
    private static final Pattern AUTOLINK = Pattern.compile("<(?:https?|mailto):[^>\\s]*>");

    @Override
    public List<Token> tokenize(String text)
    {
        List<Token> tokens = new ArrayList<>();

        String fence = null; // the opening run of ` or ~ while inside a fenced block
        int lineStart = 0;

        while (lineStart <= text.length())
        {
            int newline = text.indexOf('\n', lineStart);
            int lineEnd = newline < 0 ? text.length() : newline;
            if (lineEnd > lineStart && text.charAt(lineEnd - 1) == '\r') lineEnd--;

            String line = text.substring(lineStart, lineEnd);

            if (fence != null)
            {
                if (closesFence(line, fence))
                {
                    addLine(tokens, TokenType.COMMENT, line, lineStart);
                    fence = null;
                }
                else
                {
                    addLine(tokens, TokenType.STRING, line, lineStart);
                }
            }
            else
            {
                fence = blockLine(line, lineStart, tokens);
            }

            if (newline < 0) break;
            lineStart = newline + 1;
        }

        return List.copyOf(tokens);
    }

    /// Colours one line outside a fenced block.
    ///
    /// @return the fence this line opens, or null if it opens none
    private static String blockLine(String line, int offset, List<Token> tokens)
    {
        Matcher fence = FENCE.matcher(line);
        if (fence.matches())
        {
            addLine(tokens, TokenType.COMMENT, line, offset);
            return fence.group(1) != null ? fence.group(1) : fence.group(2);
        }

        if (HEADING.matcher(line).matches())
        {
            addLine(tokens, TokenType.KEYWORD, line, offset);
        }
        else if (THEMATIC_BREAK.matcher(line).matches())
        {
            // Checked before list items, so `- - -` is a rule rather than a list.
            addLine(tokens, TokenType.PUNCTUATION, line, offset);
        }
        else if (BLOCK_QUOTE.matcher(line).matches())
        {
            addLine(tokens, TokenType.COMMENT, line, offset);
        }
        else
        {
            int textStart = 0;

            Matcher marker = LIST_MARKER.matcher(line);
            if (marker.lookingAt())
            {
                int markerStart = firstNonSpace(line);
                tokens.add(new Token(TokenType.PUNCTUATION, offset + markerStart, offset + marker.end()));
                textStart = marker.end();
            }

            inline(line, textStart, offset, tokens);
        }

        return null;
    }

    /// Inline code and links, left to right so the tokens stay in order. Code
    /// is checked first because `` `[not](a link)` `` is code, not a link.
    private static void inline(String line, int from, int offset, List<Token> tokens)
    {
        Matcher link = LINK.matcher(line);
        Matcher autolink = AUTOLINK.matcher(line);
        int index = from;

        while (index < line.length())
        {
            char current = line.charAt(index);

            if (current == '\\')
            {
                // \` and \[ are escaped: the character after is just text.
                index += 2;
            }
            else if (current == '`')
            {
                int run = runLength(line, index, '`');
                int close = closingRun(line, index + run, run);

                if (close >= 0)
                {
                    tokens.add(new Token(TokenType.STRING, offset + index, offset + close + run));
                    index = close + run;
                }
                else
                {
                    index += run; // unmatched backticks are just text
                }
            }
            else if (startsLink(line, index) && link.region(index, line.length()).lookingAt())
            {
                int textEnd = line.indexOf("](", index) + 1;

                tokens.add(new Token(TokenType.METHOD_CALL, offset + index, offset + textEnd));
                tokens.add(new Token(TokenType.STRING, offset + textEnd, offset + link.end()));
                index = link.end();
            }
            else if (current == '<' && autolink.region(index, line.length()).lookingAt())
            {
                tokens.add(new Token(TokenType.STRING, offset + index, offset + autolink.end()));
                index = autolink.end();
            }
            else
            {
                index++;
            }
        }
    }

    private static boolean startsLink(String line, int index)
    {
        char current = line.charAt(index);

        return current == '[' || (current == '!' && index + 1 < line.length() && line.charAt(index + 1) == '[');
    }

    /// A closing fence uses the same character, at least as many of them,
    /// and nothing else on the line.
    private static boolean closesFence(String line, String fence)
    {
        String trimmed = line.strip();
        int indent = line.length() - line.stripLeading().length();
        char character = fence.charAt(0);

        return indent <= 3
                && trimmed.length() >= fence.length()
                && trimmed.chars().allMatch(c -> c == character);
    }

    /// The backtick run that closes inline code: exactly `length` backticks,
    /// so ``` `` a ` b `` ``` holds a single backtick inside.
    private static int closingRun(String line, int from, int length)
    {
        int index = from;

        while (index < line.length())
        {
            if (line.charAt(index) == '`')
            {
                int run = runLength(line, index, '`');
                if (run == length) return index;
                index += run;
            }
            else
            {
                index++;
            }
        }

        return -1;
    }

    private static int runLength(String line, int from, char character)
    {
        int index = from;
        while (index < line.length() && line.charAt(index) == character) index++;

        return index - from;
    }

    /// Colours a whole line, leaving out its indentation.
    private static void addLine(List<Token> tokens, TokenType type, String line, int offset)
    {
        int start = firstNonSpace(line);
        if (start < line.length()) tokens.add(new Token(type, offset + start, offset + line.length()));
    }

    private static int firstNonSpace(String line)
    {
        int index = 0;
        while (index < line.length() && (line.charAt(index) == ' ' || line.charAt(index) == '\t')) index++;

        return index;
    }
}

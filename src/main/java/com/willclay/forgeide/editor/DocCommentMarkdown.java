package com.willclay.forgeide.editor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Turns the raw text of a doc comment into Markdown, so it can be shown rendered.
///
/// Every doc format Forge highlights today is a dialect of "Markdown plus tags":
///
/// | Format                  | Comment markers        | Body     | Tags                  |
/// |-------------------------|------------------------|----------|-----------------------|
/// | rustdoc                 | `///`, `//!`, `/**`    | Markdown | none                  |
/// | KDoc                    | `/** */`               | Markdown | `@param`, `[Symbol]`  |
/// | Javadoc (Markdown)      | `///`                  | Markdown | `@param`, `{@link}`   |
/// | Javadoc (classic)       | `/** */`               | HTML     | `@param`, `{@code}`   |
/// | Doxygen                 | `/** */`, `/*!`, `///` | mixed    | `@param`, `\param`    |
///
/// So one converter covers them all: strip the comment markers, pull the block
/// tags out into a "Params / Returns / Throws" section, and translate the
/// handful of HTML tags and `{@inline}` tags that classic Javadoc uses. What
/// is left is handed to the Markdown renderer as it is.
///
/// Doxygen adds a few commands of its own on top: `@code` ... `@endcode`
/// (as a block, or inside a line), `@f$` and `@f[` formulas, one-word styling (`@b`, `@p`, `@c`, `@e`, `@ref`), and tags such
/// as `@pre`, `@retval` and `@sa`. Following Doxygen, a tag ends at a blank
/// line, so `@brief Short.` can be followed by a paragraph of detail.
///
/// **Why this lives in the core and not in each language plugin.** These
/// comment syntaxes are shared by every language that currently emits doc
/// comments (Java, Kotlin, Rust, C++). A language whose doc comments look
/// nothing like this — Python docstrings, say — will need a hook on the
/// `Language` API instead; that is the moment to add one, not before.
///
/// No Swing here, so the conversion can be read (and tested) on its own.
public final class DocCommentMarkdown
{
    /// A block tag at the start of a line: `@param x`, `\return`, `@param[in] x`.
    private static final Pattern BLOCK_TAG = Pattern.compile("^[@\\\\](\\w+)(?:\\[[^]]*])?\\s*(.*)$");

    /// Doxygen commands that style a word inside a sentence (`@b bold`,
    /// `@p param`, `\c code`, `@ref Symbol`) or start a list item (`@li`).
    /// They are never block tags, even when they begin a line.
    private static final Set<String> INLINE_COMMANDS = Set.of("a", "b", "c", "e", "em", "p", "ref", "li");

    /// `@b word`, `\p name`. The word stops before trailing punctuation, so
    /// `@p count.` puts the full stop outside the code. Inline code (the first
    /// alternative) is matched first so a `\c` inside backticks stays literal.
    private static final Pattern CODE_OR_INLINE_COMMAND = Pattern.compile(
            "(?<!`)(`+)(?!`)(.*?)(?<!`)\\1(?!`)"
            + "|(?<![\\w\\\\@{])[@\\\\](a|b|c|e|em|p|ref)\\s+(\\S+?)(?=[.,;:!?]*(?:\\s|$))"
            + "(?:\\s+\"([^\"]*)\")?"
    );

    /// Doxygen's `@li item` at the start of a line.
    private static final Pattern LIST_ITEM = Pattern.compile("^(\\s*)[@\\\\]li\\s+");

    /// Any list item: `@li`, `- `, `* `, `+ ` or `1. `.
    private static final Pattern LIST_LINE = Pattern.compile("\\s*(?:[@\\\\]li|[-*+]|\\d+\\.)\\s");

    /// `@code`, `\code{.cpp}`, `@verbatim` and their closing commands, alone on a line.
    private static final Pattern CODE_START = Pattern.compile("^[@\\\\](?:code|verbatim)(?:\\{\\.?(\\w+)})?\\s*$");
    private static final Pattern CODE_END   = Pattern.compile("^[@\\\\]end(?:code|verbatim)\\s*$");

    /// Code or a formula written inside a line, shown as inline code:
    /// `@code x++ @endcode`, `@verbatim a*b @endverbatim`, `@f$ x^2 @f$` and
    /// `@f( \alpha @f)`. A display formula, `@f[ ... @f]` or
    /// `@f{align}{ ... @f}`, is shown inline too when it shares its line with text.
    private static final Pattern INLINE_CODE = Pattern.compile(
            "[@\\\\]code(?:\\{\\.?\\w+})?\\s+(.*?)\\s*[@\\\\]endcode"
            + "|[@\\\\]verbatim\\s+(.*?)\\s*[@\\\\]endverbatim"
            + "|[@\\\\]f\\$(.*?)[@\\\\]f\\$"
            + "|[@\\\\]f\\((.*?)[@\\\\]f\\)"
            + "|[@\\\\]f(?:\\[|\\{[^}]*}\\{?)(.*?)[@\\\\]f[]}]"
    );

    /// The start and end of a display formula: `@f[` ... `@f]`, or
    /// `@f{eqnarray*}{` ... `@f}`. Forge cannot typeset LaTeX, so the formula is
    /// shown as written, in a code block labelled "math".
    private static final Pattern MATH_START = Pattern.compile("[@\\\\]f(?:\\[|\\{[^}]*}\\{?)");
    private static final Pattern MATH_END   = Pattern.compile("[@\\\\]f[]}]");

    /// Member-group markers `@{` and `@}`, which only matter to Doxygen's output.
    private static final Pattern GROUP_MARKER = Pattern.compile("^[@\\\\][{}]\\s*$");

    /// Commands that tell Doxygen *where* the comment belongs (`@file`,
    /// `@class Foo`, `@ingroup io`) rather than saying anything a reader of
    /// the code needs, so the rendered comment leaves them out.
    private static final Set<String> HIDDEN_TAGS = Set.of(
            "inheritdoc", "file", "class", "struct", "union", "fn", "var", "typedef", "namespace",
            "def", "enum", "ingroup", "defgroup", "addtogroup", "weakgroup", "internal",
            "endinternal", "cond", "endcond", "private", "public", "protected",
            "nosubgrouping", "hideinitializer", "showinitializer"
    );

    /// A Markdown reference such as `[String]` or `[Vec::new]` that is not
    /// followed by a link target, which in KDoc, rustdoc and Markdown Javadoc
    /// means "link to this symbol". Inline code is matched first so its brackets
    /// stay literal; the lookbehind also skips `array[index]` and escaped brackets.
    private static final Pattern CODE_OR_SYMBOL_LINK = Pattern.compile(
            "(?<!`)(`+)(?!`)(.*?)(?<!`)\\1(?!`)" + "|(?<![\\w\\]\\\\])\\[([^\\[\\]]+)](?![(\\[:])"
    );

    /// Qualified names and optional method parameters, not arbitrary bracketed
    /// text such as C++ lambda captures (`[&]`, `[=]`, `[&, value]`).
    private static final Pattern SYMBOL_REFERENCE = Pattern.compile(
            "#?[\\p{L}_$][\\p{L}\\p{N}_$]*(?:(?:\\.|::|#)[\\p{L}_$][\\p{L}\\p{N}_$]*)*"
            + "(?:\\([^\\[\\]`]*\\))?"
    );

    /// What comes before the box of a task-list item: `- ` or `* `.
    private static final Pattern TASK_PREFIX = Pattern.compile("\\s*[-*+]\\s+");

    private static final Pattern PRE_CODE = Pattern.compile("<pre>\\s*\\{@code\\s?(.*?)}\\s*</pre>", Pattern.DOTALL);
    private static final Pattern PRE      = Pattern.compile("<pre>(.*?)</pre>", Pattern.DOTALL);

    private DocCommentMarkdown() { }

    /// @param comment the comment exactly as written, markers and all — one
    ///                block comment, or a run of `///` lines with the
    ///                indentation between them
    /// @return Markdown, or an empty string when the comment says nothing
    public static String toMarkdown(String comment)
    {
        List<String> lines = doxygenBlocks(stripMarkers(comment));

        List<String> body              = new ArrayList<>();
        Map<String, List<String>> tags = new LinkedHashMap<>();
        splitTags(lines, body, tags);

        StringBuilder markdown = new StringBuilder(convertInline(String.join("\n", body)).strip());
        appendTags(markdown, tags);

        return markdown.toString().strip();
    }

    // --- Comment markers --- //

    /// Removes `///`, `/**`, `*/` and the ` * ` down the left of a block,
    /// then the indentation the lines have in common.
    private static List<String> stripMarkers(String comment)
    {
        String text       = comment.replace("\r\n", "\n").strip();
        boolean lineStyle = text.startsWith("///") || text.startsWith("//!");

        List<String> lines = new ArrayList<>();

        if (lineStyle)
        {
            for (String line : text.split("\n", -1))
            {
                String trimmed = line.stripLeading();
                if (trimmed.startsWith("///") || trimmed.startsWith("//!"))
                {
                    trimmed = trimmed.substring(3);
                }

                lines.add(trimmed);
            }

            return dedent(lines);
        }

        // "/**" or "/*!" at the front, "*/" at the back.
        if (text.startsWith("/**") || text.startsWith("/*!"))
        {
            text = text.substring(3);
        }

        if (text.endsWith("*/"))
        {
            text = text.substring(0, text.length() - 2);
        }

        boolean inFence = false;
        for (String line : text.split("\n", -1))
        {
            String trimmed = line.stripLeading();
            if (trimmed.startsWith("*"))
            {
                trimmed = trimmed.substring(1);
            }

            // Classic Javadoc is HTML, where indentation means nothing — but in
            // Markdown four spaces make a code block. Only fenced code keeps it.
            if (!inFence)
            {
                trimmed = trimmed.stripLeading();
            }

            if (trimmed.stripLeading().startsWith("```"))
            {
                inFence = !inFence;
            }

            lines.add(trimmed);
        }

        return dedent(lines);
    }

    /// Removes the leading spaces every non-blank line shares — the space after
    /// `///` or ` *`, usually, but sometimes more.
    private static List<String> dedent(List<String> lines)
    {
        int common = Integer.MAX_VALUE;

        for (String line : lines)
        {
            if (line.isBlank()) continue;

            int indent = 0;
            while (indent < line.length() && line.charAt(indent) == ' ')
            {
                indent++;
            }

            common = Math.min(common, indent);
        }

        if (common == Integer.MAX_VALUE || common == 0)
        {
            return lines;
        }

        List<String> result = new ArrayList<>();
        for (String line : lines)
        {
            result.add(line.isBlank() ? "" : line.substring(common));
        }

        return result;
    }

    // --- Doxygen blocks --- //

    /// Rewrites Doxygen's code and formula commands as the Markdown the rest
    /// of the converter already understands, and drops the `@{` / `@}` group
    /// markers:
    ///
    /// - `@code{.cpp}` ... `@endcode` on lines of their own: a fenced code block
    /// - `@f[` ... `@f]`: a fenced block labelled `math`, even mid-line
    /// - `@code x @endcode` or `@f$ x @f$` inside a line: inline code
    ///
    /// This runs before the tags are split out, so that `@f$` or `@code` at
    /// the start of a line is never mistaken for a block tag. Markdown fences
    /// and the inside of a code block are left exactly as written.
    private static List<String> doxygenBlocks(List<String> lines)
    {
        List<String> result = new ArrayList<>();

        boolean inCode  = false; // between @code and @endcode
        boolean inMath  = false; // between @f[ and @f]
        boolean inFence = false; // inside a Markdown ``` fence

        for (String line : lines)
        {
            String trimmed = line.strip();

            if (inCode)
            {
                inCode = !CODE_END.matcher(trimmed).matches();
                result.add(inCode ? line : "```");

                continue;
            }

            if (inMath)
            {
                Matcher end = MATH_END.matcher(line);
                if (!end.find())
                {
                    result.add(trimmed);
                    continue;
                }

                addIfText(result, line.substring(0, end.start()).strip());
                result.add("```");
                inMath = false;

                // Text after the closing @f] carries on as an ordinary line.
                addIfText(result, inlineCode(line.substring(end.end())).strip());
                continue;
            }

            if (trimmed.startsWith("```"))
            {
                inFence = !inFence;
            }

            if (inFence || trimmed.startsWith("```"))
            {
                result.add(line);
                continue;
            }

            Matcher codeStart = CODE_START.matcher(trimmed);
            if (codeStart.matches())
            {
                inCode = true;
                result.add("```" + (codeStart.group(1) == null ? "" : codeStart.group(1)));

                continue;
            }

            if (GROUP_MARKER.matcher(trimmed).matches()) continue;

            // A display formula with a line to itself is still a block.
            Matcher whole = INLINE_CODE.matcher(trimmed);
            if (whole.matches() && whole.group(5) != null)
            {
                result.addAll(List.of("```math", whole.group(5).strip(), "```"));
                continue;
            }

            line = inlineCode(line);

            // A display formula that opens here and closes on a later line.
            Matcher mathStart = MATH_START.matcher(line);
            if (mathStart.find())
            {
                addIfText(result, line.substring(0, mathStart.start()).stripTrailing());
                result.add("```math");
                addIfText(result, line.substring(mathStart.end()).strip());

                inMath = true;

                continue;
            }

            result.add(line);
        }

        // An @code or @f[ that is never closed still gets a closed fence.
        if (inCode || inMath) result.add("```");

        return result;
    }

    /// Every same-line code or formula command in a line, as inline code.
    private static String inlineCode(String line)
    {
        return INLINE_CODE.matcher(line).replaceAll(match ->
        {
            for (int group = 1; group <= match.groupCount(); group++)
            {
                if (match.group(group) != null)
                {
                    return Matcher.quoteReplacement(codeSpan(match.group(group).strip()));
                }
            }

            return "";
        });
    }

    /// Wraps text in enough backticks that any backticks inside it stay
    /// literal: `` `a` `` needs two, with spaces so they do not touch.
    private static String codeSpan(String code)
    {
        if (code.isEmpty()) return "";

        int longest = 0;
        int run     = 0;

        for (char character : code.toCharArray())
        {
            run     = character == '`' ? run + 1 : 0;
            longest = Math.max(longest, run);
        }

        String ticks   = "`".repeat(longest + 1);
        String padding = code.startsWith("`") || code.endsWith("`") ? " " : "";

        return ticks + padding + code + padding + ticks;
    }

    private static void addIfText(List<String> lines, String text)
    {
        if (!text.isBlank()) lines.add(text);
    }

    // --- Block tags --- //

    /// Everything outside a tag is the body. A tag runs until the next tag or
    /// a blank line — Doxygen's rule, which lets `@brief` be followed by a
    /// paragraph of detail — so a description can still wrap onto several
    /// lines. Tags inside a fenced code block are code — `@Override` in an
    /// example — not tags.
    ///
    /// `@brief`, `@details` and `@par` are paragraphs of the body under another
    /// name, so they stay where they were written instead of moving into the
    /// tag sections at the end.
    private static void splitTags(List<String> lines, List<String> body, Map<String, List<String>> tags)
    {
        boolean       inFence      = false;
        boolean       previousCode = false;
        StringBuilder currentTag   = null;
        String        currentName  = null;

        for (String line : lines)
        {
            boolean fenceLine = line.stripLeading().startsWith("```");
            boolean codeLine  = fenceLine || inFence;

            if (fenceLine)
            {
                inFence = !inFence;
            }

            Matcher tag = codeLine ? null : BLOCK_TAG.matcher(line.strip());

            if (tag != null && tag.matches() && !INLINE_COMMANDS.contains(tag.group(1).toLowerCase()))
            {
                addTag(tags, body, currentName, currentTag);

                currentName = tag.group(1).toLowerCase();
                currentTag  = new StringBuilder(tag.group(2));

                // @par's title is the rest of its line; the paragraph follows.
                if (currentName.equals("par")) currentTag.append('\n');

                previousCode = false;
                continue;
            }

            if (currentTag != null && line.isBlank() && !codeLine)
            {
                addTag(tags, body, currentName, currentTag);

                currentName = null;
                currentTag  = null;
            }

            if (currentTag != null)
            {
                // Code and list items keep their own lines; prose is joined into one.
                boolean keepLines = codeLine || previousCode || LIST_LINE.matcher(line).lookingAt();
                currentTag.append(keepLines ? '\n' : ' ').append(codeLine ? line : line.strip());
            }
            else
            {
                body.add(line);
            }

            previousCode = codeLine;
        }

        addTag(tags, body, currentName, currentTag);
    }

    private static void addTag(Map<String, List<String>> tags, List<String> body, String name, StringBuilder text)
    {
        if (name == null) return;

        String content = text.toString().strip();

        switch (name)
        {
            case "brief", "short", "details" ->
            {
                paragraph(body, content);
                return;
            }

            case "par" ->
            {
                String[] parts = text.toString().split("\n", 2);
                String title   = parts[0].strip();
                String rest    = parts.length > 1 ? parts[1].strip() : "";

                paragraph(body, title.isEmpty() ? rest : "**" + title + "**" + (rest.isEmpty() ? "" : "\n\n" + rest));
                return;
            }
        }

        // Synonyms, so `@returns` and `\return` land in the same section.
        String key = switch (name)
        {
            case "returns",   "return", "result" -> "return";
            case "exception", "throws", "throw"  -> "throws";
            case "tparam"                        -> "param";
            case "remarks"                       -> "remark";
            case "authors"                       -> "author";

            default -> name;
        };

        tags.computeIfAbsent(key, ignored -> new ArrayList<>()).add(content);
    }

    /// Adds text to the body as a paragraph of its own.
    private static void paragraph(List<String> body, String text)
    {
        if (text.isBlank()) return;

        if (!body.isEmpty() && !body.getLast().isBlank())
        {
            body.add("");
        }

        body.addAll(List.of(text.split("\n", -1)));
    }

    /// The heading for a tag that has no section of its own.
    private static String heading(String tag)
    {
        return switch (tag)
        {
            case "pre"  -> "Precondition";
            case "post" -> "Postcondition";
            case "todo" -> "To do";

            default -> capitalise(tag);
        };
    }

    /// Writes the tags in the order a reader looks for them: parameters,
    /// what comes back, what can go wrong, then anything else.
    private static void appendTags(StringBuilder markdown, Map<String, List<String>> tags)
    {
        appendNamedList (markdown, "Params",        tags.get("param"));
        appendNamedList (markdown, "Properties",    tags.get("property"));
        appendText      (markdown, "Returns",       tags.get("return"));
        appendNamedList (markdown, "Return values", tags.get("retval"));
        appendNamedList (markdown, "Throws",        tags.get("throws"));

        for (Map.Entry<String, List<String>> tag : tags.entrySet())
        {
            String name = tag.getKey();

            switch (name)
            {
                case "param", "property", "return", "retval", "throws", "see", "sa" -> { }

                default ->
                {
                    if (!HIDDEN_TAGS.contains(name)) appendText(markdown, heading(name), tag.getValue());
                }
            }
        }

        List<String> targets = new ArrayList<>();
        for (String target : tags.getOrDefault("see", List.of()))
        {
            targets.add(seeTarget(target));
        }

        // Doxygen's \sa is a plain list of references: "\sa open(), close()".
        for (String target : tags.getOrDefault("sa", List.of()))
        {
            targets.add(convertInline(target));
        }

        if (!targets.isEmpty())
        {
            section(markdown, "**See also:** " + String.join(", ", targets));
        }
    }

    /// `@param name description` as a list item with the name as code.
    private static void appendNamedList(StringBuilder markdown, String heading, List<String> entries)
    {
        if (entries == null || entries.isEmpty())
        {
            return;
        }

        StringBuilder list = new StringBuilder("**").append(heading).append(":**\n");

        for (String entry : entries)
        {
            String[] parts     = entry.split("\\s+", 2);
            String name        = parts[0];
            String description = parts.length > 1 ? convertInline(parts[1]) : "";

            list.append("- `").append(name).append('`');

            if (!description.isBlank())
            {
                list.append(" – ").append(description);
            }

            list.append('\n');
        }

        section(markdown, list.toString());
    }

    private static void appendText(StringBuilder markdown, String heading, List<String> entries)
    {
        if (entries == null) return;

        for (String entry : entries)
        {
            String text = convertInline(entry);
            section(markdown, "**" + heading + ":**" + (text.isBlank() ? "" : " " + text));
        }
    }

    /// `@see Foo#bar` names a symbol, `@see <a href>` is a link and
    /// `@see "Some book"` is plain text.
    private static String seeTarget(String target)
    {
        if (target.startsWith("<") || target.startsWith("\""))
        {
            return convertInline(target);
        }

        String[] parts = target.split("\\s+", 2);
        return parts.length > 1 ? convertInline(parts[1]) : "`" + symbol(parts[0]) + "`";
    }

    private static void section(StringBuilder markdown, String text)
    {
        if (text.isBlank()) return;

        if (!markdown.isEmpty())
        {
            markdown.append("\n\n");
        }

        markdown.append(text.strip());
    }

    // --- Inline translation --- //

    /// Classic Javadoc's HTML and `{@tags}`, and KDoc's `[Symbol]` links,
    /// rewritten as the Markdown that means the same thing. Fenced code is left
    /// exactly as written.
    private static String convertInline(String text)
    {
        text = PRE_CODE.matcher(text).replaceAll(match -> Matcher.quoteReplacement("\n```\n" + match.group(1).strip() + "\n```\n"));
        text = PRE.matcher(text).replaceAll(match -> Matcher.quoteReplacement("\n```\n" + match.group(1).strip() + "\n```\n"));

        StringBuilder result = new StringBuilder();
        boolean inFence      = false;

        for (String line : text.split("\n", -1))
        {
            if (!result.isEmpty())
            {
                result.append('\n');
            }

            if (line.stripLeading().startsWith("```"))
            {
                inFence = !inFence;
                result.append(line);

                continue;
            }

            result.append(inFence ? decodeEntities(line) : convertLine(line));
        }

        return result.toString();
    }

    private static String convertLine(String line)
    {
        line = convertInlineTags(line);

        line = line.replaceAll("(?i)</?p>",                                 "\n\n")
                   .replaceAll("(?i)<br\\s*/?>",                            "  \n")
                   .replaceAll("(?i)</?(code|tt)>",                         "`")
                   .replaceAll("(?i)</?(b|strong)>",                        "**")
                   .replaceAll("(?i)</?(i|em)>",                            "*")
                   .replaceAll("(?i)</?(ul|ol)>",                           "\n")
                   .replaceAll("(?i)<li>",                                  "\n- ")
                   .replaceAll("(?i)</li>",                                 "")
                   .replaceAll("(?i)<a\\s+href=\"([^\"]*)\"\\s*>(.*?)</a>", "[$2]($1)");

        line = convertDoxygenInline(line);

        String withTags = line;
        line = CODE_OR_SYMBOL_LINK.matcher(withTags).replaceAll(match ->
        {
            if (match.group(1) != null) return Matcher.quoteReplacement(match.group());

            String inner = match.group(3);

            // "- [ ]" and "- [x]" are task-list boxes, not links — but KDoc's
            // [x] anywhere else is a link to the parameter x.
            boolean taskBox = (inner.isBlank() || inner.equalsIgnoreCase("x")) && TASK_PREFIX.matcher(withTags.substring(0, match.start())).matches();

            if (taskBox || !SYMBOL_REFERENCE.matcher(inner).matches())
            {
                return Matcher.quoteReplacement(match.group());
            }

            return Matcher.quoteReplacement("`" + symbol(inner) + "`");
        });

        return decodeEntities(line);
    }

    /// Doxygen's one-word styling commands, as Markdown:
    ///
    /// | Command              | Markdown    |
    /// |----------------------|-------------|
    /// | `@b word`            | bold        |
    /// | `@e`, `@em`, `@a`    | italic      |
    /// | `@c word`, `@p name` | inline code |
    /// | `@ref name "label"`  | the label, or the name as code |
    /// | `@li item`           | a list item |
    private static String convertDoxygenInline(String line)
    {
        line = LIST_ITEM.matcher(line).replaceFirst("$1- ");

        return CODE_OR_INLINE_COMMAND.matcher(line).replaceAll(match ->
        {
            if (match.group(1) != null)
            {
                return Matcher.quoteReplacement(match.group());
            }

            String word  = match.group(4);
            String label = match.group(5);

            String styled = switch (match.group(3))
            {
                case "b"            -> "**" + word + "**";
                case "e", "em", "a" -> "*" + word + "*";
                case "ref"          -> label != null ? label : "`" + symbol(word) + "`";

                default -> "`" + word + "`"; // c, p
            };

            // Only @ref takes a quoted label; for the others the quote is just text.
            if (label != null && !match.group(3).equals("ref"))
            {
                styled += " \"" + label + "\"";
            }

            return Matcher.quoteReplacement(styled);
        });
    }

    /// `{@code x}`, `{@link Foo#bar label}` and friends. A small scanner
    /// rather than a regular expression, because `{@code Map<K, V> {}}` has
    /// braces inside it and only counting them finds the right end.
    private static String convertInlineTags(String line)
    {
        StringBuilder result = new StringBuilder();
        int index = 0;

        while (index < line.length())
        {
            int start = line.indexOf("{@", index);
            if (start < 0) break;

            int end = matchingBrace(line, start);
            if (end < 0) break;

            result.append(line, index, start);

            String content  = line.substring(start + 2, end);
            String[] parts  = content.split("\\s+", 2);
            String argument = parts.length > 1 ? parts[1].strip() : "";

            result.append(switch (parts[0])
            {
                case "code", "value"     -> argument.isEmpty() ? "" : "`" + argument + "`";
                case "literal"           -> argument;
                case "link", "linkplain" -> link(argument);

                default -> ""; // {@inheritDoc}, {@docRoot}: nothing useful to show
            });

            index = end + 1;
        }

        result.append(line.substring(index));
        return result.toString();
    }

    /// @return the index of the `}` closing the brace at `open`, or -1
    private static int matchingBrace(String line, int open)
    {
        int depth = 0;

        for (int index = open; index < line.length(); index++)
        {
            char character = line.charAt(index);

            if (character == '{') depth++;
            if (character == '}' && --depth == 0) return index;
        }

        return -1;
    }

    /// `{@link Foo#bar}` shows the symbol as code; `{@link Foo#bar the bar
    /// method}` shows the label. Neither is clickable yet: following a symbol
    /// needs an index of the project's code, which Forge does not have.
    private static String link(String argument)
    {
        String[] parts = argument.split("\\s+", 2);
        if (parts.length > 1)
        {
            return parts[1];
        }

        return "`" + symbol(parts[0]) + "`";
    }

    /// `Foo#bar(int)` reads better as `Foo.bar(int)`, and `#bar` as `bar`.
    private static String symbol(String reference)
    {
        if (reference.startsWith("#"))
        {
            return reference.substring(1);
        }

        return reference.replace('#', '.');
    }

    private static String decodeEntities(String line)
    {
        return line.replace("&lt;",   "<")
                   .replace("&gt;",   ">")
                   .replace("&quot;", "\"")
                   .replace("&nbsp;", " ")
                   .replace("&#64;",  "@")
                   .replace("&amp;",  "&");
    }

    private static String capitalise(String word)
    {
        return word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }
}

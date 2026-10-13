package com.willclay.forgeide.editor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /// A Markdown reference such as `[String]` or `[Vec::new]` that is not
    /// followed by a link target, which in KDoc, rustdoc and Markdown Javadoc
    /// means "link to this symbol". The lookbehind skips `array[index]`.
    private static final Pattern SYMBOL_LINK = Pattern.compile("(?<![\\w\\]])\\[([^\\[\\]]+)](?![(\\[:])");

    /// What comes before the box of a task-list item: `- ` or `* `.
    private static final Pattern TASK_PREFIX = Pattern.compile("\\s*[-*+]\\s+");

    private static final Pattern PRE_CODE = Pattern.compile("<pre>\\s*\\{@code\\s?(.*?)}\\s*</pre>", Pattern.DOTALL);
    private static final Pattern PRE = Pattern.compile("<pre>(.*?)</pre>", Pattern.DOTALL);

    private DocCommentMarkdown() { }

    /// @param comment the comment exactly as written, markers and all — one
    ///                block comment, or a run of `///` lines with the
    ///                indentation between them
    /// @return Markdown, or an empty string when the comment says nothing
    public static String toMarkdown(String comment)
    {
        List<String> lines = stripMarkers(comment);

        List<String> body = new ArrayList<>();
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
        String text = comment.replace("\r\n", "\n").strip();
        boolean lineStyle = text.startsWith("///") || text.startsWith("//!");

        List<String> lines = new ArrayList<>();

        if (lineStyle)
        {
            for (String line : text.split("\n", -1))
            {
                String trimmed = line.stripLeading();
                if (trimmed.startsWith("///") || trimmed.startsWith("//!")) trimmed = trimmed.substring(3);

                lines.add(trimmed);
            }

            return dedent(lines);
        }

        // "/**" or "/*!" at the front, "*/" at the back.
        if (text.startsWith("/**") || text.startsWith("/*!")) text = text.substring(3);
        if (text.endsWith("*/")) text = text.substring(0, text.length() - 2);

        boolean inFence = false;
        for (String line : text.split("\n", -1))
        {
            String trimmed = line.stripLeading();
            if (trimmed.startsWith("*")) trimmed = trimmed.substring(1);

            // Classic Javadoc is HTML, where indentation means nothing — but in
            // Markdown four spaces make a code block. Only fenced code keeps it.
            if (!inFence) trimmed = trimmed.stripLeading();
            if (trimmed.stripLeading().startsWith("```")) inFence = !inFence;

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
            while (indent < line.length() && line.charAt(indent) == ' ') indent++;
            common = Math.min(common, indent);
        }

        if (common == Integer.MAX_VALUE || common == 0) return lines;

        List<String> result = new ArrayList<>();
        for (String line : lines) result.add(line.isBlank() ? "" : line.substring(common));

        return result;
    }

    // --- Block tags --- //

    /// Everything before the first tag is the body. Each tag runs until the
    /// next one, so a description can wrap onto several lines. Tags inside a
    /// fenced code block are code — `@Override` in an example — not tags.
    private static void splitTags(List<String> lines, List<String> body, Map<String, List<String>> tags)
    {
        boolean inFence = false;
        StringBuilder currentTag = null;
        String currentName = null;

        for (String line : lines)
        {
            if (line.stripLeading().startsWith("```")) inFence = !inFence;

            Matcher tag = inFence ? null : BLOCK_TAG.matcher(line.strip());

            if (tag != null && tag.matches())
            {
                addTag(tags, currentName, currentTag);

                currentName = tag.group(1).toLowerCase();
                currentTag = new StringBuilder(tag.group(2));
                continue;
            }

            if (currentTag != null) currentTag.append(' ').append(line.strip());
            else body.add(line);
        }

        addTag(tags, currentName, currentTag);
    }

    private static void addTag(Map<String, List<String>> tags, String name, StringBuilder text)
    {
        if (name == null) return;

        // Synonyms, so `@returns` and `\return` land in the same section.
        String key = switch (name)
        {
            case "returns", "return", "result" -> "return";
            case "exception", "throws", "throw" -> "throws";
            case "tparam" -> "param";
            default -> name;
        };

        tags.computeIfAbsent(key, ignored -> new ArrayList<>()).add(text.toString().strip());
    }

    /// Writes the tags in the order a reader looks for them: parameters,
    /// what comes back, what can go wrong, then anything else.
    private static void appendTags(StringBuilder markdown, Map<String, List<String>> tags)
    {
        // Doxygen's \brief and \details are just the body under another name.
        for (String body : List.of("brief", "details"))
        {
            for (String text : tags.getOrDefault(body, List.of())) section(markdown, convertInline(text));
        }

        appendNamedList(markdown, "Params", tags.get("param"));
        appendNamedList(markdown, "Properties", tags.get("property"));
        appendText(markdown, "Returns", tags.get("return"));
        appendNamedList(markdown, "Throws", tags.get("throws"));

        for (Map.Entry<String, List<String>> tag : tags.entrySet())
        {
            switch (tag.getKey())
            {
                case "brief", "details", "param", "property", "return", "throws", "see", "inheritdoc" -> { }
                default -> appendText(markdown, capitalise(tag.getKey()), tag.getValue());
            }
        }

        List<String> see = tags.get("see");
        if (see != null)
        {
            List<String> targets = new ArrayList<>();
            for (String target : see) targets.add(seeTarget(target));

            section(markdown, "**See also:** " + String.join(", ", targets));
        }
    }

    /// `@param name description` as a list item with the name as code.
    private static void appendNamedList(StringBuilder markdown, String heading, List<String> entries)
    {
        if (entries == null || entries.isEmpty()) return;

        StringBuilder list = new StringBuilder("**").append(heading).append(":**\n");

        for (String entry : entries)
        {
            String[] parts = entry.split("\\s+", 2);
            String name = parts[0];
            String description = parts.length > 1 ? convertInline(parts[1]) : "";

            list.append("- `").append(name).append('`');
            if (!description.isBlank()) list.append(" – ").append(description);
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
        if (target.startsWith("<") || target.startsWith("\"")) return convertInline(target);

        String[] parts = target.split("\\s+", 2);
        return parts.length > 1 ? convertInline(parts[1]) : "`" + symbol(parts[0]) + "`";
    }

    private static void section(StringBuilder markdown, String text)
    {
        if (text.isBlank()) return;

        if (!markdown.isEmpty()) markdown.append("\n\n");
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
        boolean inFence = false;

        for (String line : text.split("\n", -1))
        {
            if (!result.isEmpty()) result.append('\n');

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

        line = line.replaceAll("(?i)</?p>", "\n\n")
                .replaceAll("(?i)<br\\s*/?>", "  \n")
                .replaceAll("(?i)</?(code|tt)>", "`")
                .replaceAll("(?i)</?(b|strong)>", "**")
                .replaceAll("(?i)</?(i|em)>", "*")
                .replaceAll("(?i)</?(ul|ol)>", "\n")
                .replaceAll("(?i)<li>", "\n- ")
                .replaceAll("(?i)</li>", "")
                .replaceAll("(?i)<a\\s+href=\"([^\"]*)\"\\s*>(.*?)</a>", "[$2]($1)");

        String withTags = line;
        line = SYMBOL_LINK.matcher(withTags).replaceAll(match ->
        {
            String inner = match.group(1);

            // "- [ ]" and "- [x]" are task-list boxes, not links — but KDoc's
            // [x] anywhere else is a link to the parameter x.
            boolean taskBox = (inner.isBlank() || inner.equalsIgnoreCase("x"))
                    && TASK_PREFIX.matcher(withTags.substring(0, match.start())).matches();
            if (taskBox) return Matcher.quoteReplacement(match.group());

            return Matcher.quoteReplacement(inner.startsWith("`") ? inner : "`" + symbol(inner) + "`");
        });

        return decodeEntities(line);
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

            String content = line.substring(start + 2, end);
            String[] parts = content.split("\\s+", 2);
            String argument = parts.length > 1 ? parts[1].strip() : "";

            result.append(switch (parts[0])
            {
                case "code", "value" -> argument.isEmpty() ? "" : "`" + argument + "`";
                case "literal" -> argument;
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
        if (parts.length > 1) return parts[1];

        return "`" + symbol(parts[0]) + "`";
    }

    /// `Foo#bar(int)` reads better as `Foo.bar(int)`, and `#bar` as `bar`.
    private static String symbol(String reference)
    {
        if (reference.startsWith("#")) return reference.substring(1);

        return reference.replace('#', '.');
    }

    private static String decodeEntities(String line)
    {
        return line.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&nbsp;", " ")
                .replace("&#64;", "@")
                .replace("&amp;", "&");
    }

    private static String capitalise(String word)
    {
        return word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }
}

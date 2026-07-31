package com.willclay.forgeide.highlighting;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;

/**
 * Helpers for reading a Swing {@link Document} a line at a time.
 *
 * Both the cache and the painter need a line's bounds, and both have to cope
 * with the same quirk: the last line's end offset runs one past the document's
 * length. Doing it in one place means only one place can get it wrong.
 */
final class DocumentLines
{
    private DocumentLines() { }

    static int count(Document doc)
    {
        return doc.getDefaultRootElement().getElementCount();
    }

    static int startOffset(Element root, int line)
    {
        return root.getElement(line).getStartOffset();
    }

    static int endOffset(Document doc, Element root, int line)
    {
        return Math.min(root.getElement(line).getEndOffset(), doc.getLength());
    }

    /** The line's text, without its trailing line separator - the lexer works on content only. */
    static String text(Document doc, Element root, int line) throws BadLocationException
    {
        int start = startOffset(root, line);
        int end = endOffset(doc, root, line);

        if (end <= start) return "";

        return stripLineSeparator(doc.getText(start, end - start));
    }

    private static String stripLineSeparator(String text)
    {
        if (text.endsWith("\n")) text = text.substring(0, text.length() - 1);
        if (text.endsWith("\r")) text = text.substring(0, text.length() - 1);

        return text;
    }
}

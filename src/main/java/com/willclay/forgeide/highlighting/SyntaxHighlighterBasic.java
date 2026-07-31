package main.java.com.willclay.forgeide.highlighting;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SyntaxHighlighterBasic
{
    private final SimpleAttributeSet keywordStyle;
    private final SimpleAttributeSet stringStyle;
    private final SimpleAttributeSet commentStyle;
    private final SimpleAttributeSet defaultStyle;

    // NOTE: no spaces inside the alternation, and no trailing "|".
    // The original pattern compiled alternatives like "class " and a bare " ",
    // which meant the \b anchors almost never lined up correctly.
    private static final Pattern KEYWORD_PATTERN = Pattern.compile("\\b(" +
            "abstract|assert|boolean|break|byte|case|catch|char|class|const|" +
            "continue|default|do|double|else|enum|extends|final|finally|float|" +
            "for|goto|if|implements|import|instanceof|int|interface|long|native|" +
            "new|package|private|protected|public|return|short|static|strictfp|" +
            "super|switch|synchronized|this|throw|throws|transient|try|void|" +
            "volatile|while|true|false|null" +
            ")\\b");

    // Strings, allowing escaped quotes inside
    private static final Pattern STRING_PATTERN = Pattern.compile("\"([^\"\\\\\\n]|\\\\.)*\"");

    // Line comments and block comments
    private static final Pattern COMMENT_PATTERN = Pattern.compile("//[^\\n]*|/\\*(.|\\R)*?\\*/");

    public SyntaxHighlighterBasic()
    {
        keywordStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(keywordStyle, Color.BLUE);
        StyleConstants.setBold(keywordStyle, true);

        stringStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(stringStyle, new Color(0, 153, 51)); // Green

        commentStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(commentStyle, Color.GRAY);
        StyleConstants.setItalic(commentStyle, true);

        defaultStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(defaultStyle, Color.WHITE);
        StyleConstants.setBold(defaultStyle, false);
        StyleConstants.setItalic(defaultStyle, false);
    }

    public void applyHighlighting(JTextPane editor)
    {
        StyledDocument doc = editor.getStyledDocument();

        String text;
        try
        {
            // Read from the document rather than editor.getText() so that the
            // offsets returned by the Matcher line up exactly with document offsets.
            text = doc.getText(0, doc.getLength());
        }
        catch (BadLocationException e)
        {
            return;
        }

        // Reset everything back to plain black
        doc.setCharacterAttributes(0, doc.getLength(), defaultStyle, true);

        applyPattern(doc, KEYWORD_PATTERN.matcher(text), keywordStyle);
        applyPattern(doc, STRING_PATTERN.matcher(text), stringStyle);

        // Comments last so that "//" inside a string doesn't win, and so a
        // commented-out keyword is greyed rather than left blue.
        applyPattern(doc, COMMENT_PATTERN.matcher(text), commentStyle);
    }

    private void applyPattern(StyledDocument doc, Matcher matcher, AttributeSet style)
    {
        while (matcher.find())
        {
            doc.setCharacterAttributes(
                    matcher.start(),
                    matcher.end() - matcher.start(),
                    style,
                    false
            );
        }
    }
}
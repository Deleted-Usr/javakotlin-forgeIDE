package com.willclay.forgeide.ui.editor;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.Objects;

/// The typing conveniences an editor is expected to have: brackets and quotes
/// that close themselves, a closing character that steps over the one already
/// there, a backspace that removes both halves of an empty pair, and a Return
/// that lands the caret where the next line belongs.
///
/// **Everything is one document edit.** Undo in a text component works in
/// document edits, so an insertion assembled from several calls would take
/// several presses of Ctrl+Z to unpick — and the caret would visibly stagger
/// backwards through text the user typed in one keystroke. Each behaviour here
/// builds the whole string first and inserts it once.
///
/// **Nothing is remembered between keystrokes.** A closing bracket is stepped
/// over whenever the character after the caret is that bracket, rather than only
/// when this class was the one that put it there. Tracking which characters were
/// inserted automatically would mean holding markers that every edit, undo and
/// reload has to keep honest, and the payoff is one uncommon case: deliberately
/// typing a second `)` directly in front of an existing one. Stepping over is
/// also what a reader of this code would predict, which counts for more here
/// than being right in that case.
///
/// **The rules for when *not* to help.** Auto-closing is only ever an
/// improvement when the closing character is going somewhere it would have been
/// typed anyway. So a pair is only inserted in front of whitespace, a closing
/// bracket, punctuation, or the end of the line — never in front of a word,
/// where it would split the word being edited. Quotes carry two extra
/// conditions: not after a backslash, which escapes the quote rather than
/// opening a string, and not directly after a letter, so that the apostrophe in
/// `don't` stays an apostrophe.
///
/// Bindings go in the component's own input map, which sits in front of the one
/// the look and feel installs. That leaves every other key — and every other
/// component, the console included — exactly as it was.
public final class SmartTyping
{
    private static final String OPENERS = "([{";
    private static final String CLOSERS = ")]}";
    private static final String QUOTES = "\"'";

    /// Characters a pair is willing to be inserted in front of, beyond
    /// whitespace and the end of the document.
    private static final String CLOSE_BEFORE = ")]}>,;:.";

    private final JTextComponent editor;

    private int tabSize = 4;
    private boolean insertSpaces = true;

    public SmartTyping(JTextComponent editor)
    {
        this.editor = Objects.requireNonNull(editor, "editor");

        install();
    }

    /// Sets the indent that Tab inserts and that Return lines up with.
    public void setIndent(int tabSize, boolean insertSpaces)
    {
        if (tabSize < 1) throw new IllegalArgumentException("tabSize must be positive");

        this.tabSize = tabSize;
        this.insertSpaces = insertSpaces;
    }

    private void install()
    {
        InputMap inputs = editor.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actions = editor.getActionMap();

        for (char opener : (OPENERS + QUOTES).toCharArray())
        {
            bind(inputs, actions, opener, event -> opened(opener));
        }

        for (char closer : CLOSERS.toCharArray())
        {
            bind(inputs, actions, closer, event -> closed(closer));
        }

        bind(inputs, actions, KeyEvent.VK_TAB, "forge.tab", event -> editor.replaceSelection(indentUnit()));
        bind(inputs, actions, KeyEvent.VK_ENTER, "forge.enter", event -> insertBreak());
        bind(inputs, actions, KeyEvent.VK_BACK_SPACE, "forge.backspace", this::deletePrevious);
    }

    // --- Typing --- //

    /// An opening bracket, or either quote — which is its own closer.
    private void opened(char opener)
    {
        if (surroundSelection(opener)) return;

        int caret = editor.getCaretPosition();
        char previous = charAt(caret - 1);
        char next = charAt(caret);

        // A quote cannot be told apart from its own partner by looking at it, so
        // the one after the caret may be the end of the string being typed.
        if (isQuote(opener) && next == opener)
        {
            editor.setCaretPosition(caret + 1);
            return;
        }

        if (!shouldClose(opener, previous, next))
        {
            editor.replaceSelection(String.valueOf(opener));
            return;
        }

        editor.replaceSelection(String.valueOf(opener) + closerFor(opener));
        editor.setCaretPosition(editor.getCaretPosition() - 1);
    }

    /// A closing bracket, which steps over the one already in front of the caret.
    private void closed(char closer)
    {
        int caret = editor.getCaretPosition();

        if (!hasSelection() && charAt(caret) == closer)
        {
            editor.setCaretPosition(caret + 1);
            return;
        }

        editor.replaceSelection(String.valueOf(closer));
    }

    /// Wraps the selection rather than replacing it, so that selecting an
    /// expression and typing `(` brackets it.
    private boolean surroundSelection(char opener)
    {
        if (!hasSelection()) return false;

        String selected = editor.getSelectedText();
        if (selected == null) return false;

        int start = editor.getSelectionStart();

        editor.replaceSelection(opener + selected + closerFor(opener));

        // Leaving the original text selected means a second bracket wraps it
        // again, which is how nesting a call inside another one is done.
        editor.select(start + 1, start + 1 + selected.length());

        return true;
    }

    private static boolean shouldClose(char opener, char previous, char next)
    {
        if (previous == '\\') return false;
        if (next != 0 && !Character.isWhitespace(next) && CLOSE_BEFORE.indexOf(next) < 0) return false;
        if (!isQuote(opener)) return true;

        return !Character.isLetterOrDigit(previous) && previous != '_' && previous != opener;
    }

    // --- Return --- //

    /// Carries the current line's indentation onto the next one, adds a level
    /// after an opening brace, and opens a block out when the caret is sitting
    /// inside an empty pair of them.
    private void insertBreak()
    {
        if (!editor.isEditable()) return;

        int start = editor.getSelectionStart();
        String indent = indentOfLineAt(start);
        char previous = charAt(start - 1);
        char next = charAt(editor.getSelectionEnd());

        if (previous != '{')
        {
            editor.replaceSelection("\n" + indent);
            return;
        }

        String inner = indent + indentUnit();

        if (next != '}')
        {
            editor.replaceSelection("\n" + inner);
            return;
        }

        editor.replaceSelection("\n" + inner + "\n" + indent);
        editor.setCaretPosition(start + 1 + inner.length());
    }

    /// The whitespace a line starts with, up to the caret — so pressing Return
    /// halfway through the indentation does not invent the rest of it.
    private String indentOfLineAt(int offset)
    {
        Element root = editor.getDocument().getDefaultRootElement();
        Element line = root.getElement(root.getElementIndex(offset));

        int start = line.getStartOffset();
        String text = textOf(start, Math.max(0, Math.min(offset, line.getEndOffset()) - start));

        int end = 0;
        while (end < text.length() && (text.charAt(end) == ' ' || text.charAt(end) == '\t')) end++;

        return text.substring(0, end);
    }

    private String indentUnit()
    {
        return insertSpaces ? " ".repeat(tabSize) : "\t";
    }

    // --- Backspace --- //

    /// Deletes both halves of an empty pair, and otherwise does what backspace
    /// has always done.
    private void deletePrevious(ActionEvent event)
    {
        if (deletedPair()) return;

        // Looked up rather than kept: a change of look and feel replaces the
        // action map this inherits from, and a reference captured at
        // construction would go on calling the old one.
        Action fallback = editor.getActionMap().get(DefaultEditorKit.deletePrevCharAction);

        if (fallback != null) fallback.actionPerformed(event);
        else deleteOneCharacter();
    }

    private boolean deletedPair()
    {
        if (hasSelection() || !editor.isEditable()) return false;

        int caret = editor.getCaretPosition();
        if (!isPair(charAt(caret - 1), charAt(caret))) return false;

        try
        {
            editor.getDocument().remove(caret - 1, 2);
            return true;
        }
        catch (BadLocationException exception)
        {
            return false;
        }
    }

    /// Only reached if the editor kit has no delete action, which it always has.
    private void deleteOneCharacter()
    {
        if (hasSelection())
        {
            editor.replaceSelection("");
            return;
        }

        int caret = editor.getCaretPosition();
        if (caret == 0 || !editor.isEditable()) return;

        try
        {
            editor.getDocument().remove(caret - 1, 1);
        }
        catch (BadLocationException ignored)
        {
            // The caret was past the end of a document that has since shrunk.
        }
    }

    // --- Character helpers --- //

    private static boolean isQuote(char character)
    {
        return QUOTES.indexOf(character) >= 0;
    }

    private static char closerFor(char opener)
    {
        int index = OPENERS.indexOf(opener);

        return index < 0 ? opener : CLOSERS.charAt(index);
    }

    private static boolean isPair(char opener, char closer)
    {
        if (opener == 0 || closer == 0) return false;

        return isQuote(opener) ? opener == closer : closerFor(opener) == closer && OPENERS.indexOf(opener) >= 0;
    }

    private boolean hasSelection()
    {
        return editor.getSelectionStart() != editor.getSelectionEnd();
    }

    /// @return the character at this offset, or 0 outside the document
    private char charAt(int offset)
    {
        Document document = editor.getDocument();
        if (offset < 0 || offset >= document.getLength()) return 0;

        String text = textOf(offset, 1);

        return text.isEmpty() ? 0 : text.charAt(0);
    }

    private String textOf(int offset, int length)
    {
        try
        {
            return editor.getDocument().getText(offset, length);
        }
        catch (BadLocationException exception)
        {
            return "";
        }
    }

    // --- Binding --- //

    private static void bind(InputMap inputs, ActionMap actions, char character, ActionListener behaviour)
    {
        bind(inputs, actions, KeyStroke.getKeyStroke(character), "forge.type." + character, behaviour);
    }

    private static void bind(InputMap inputs, ActionMap actions, int keyCode, String name, ActionListener behaviour)
    {
        bind(inputs, actions, KeyStroke.getKeyStroke(keyCode, 0), name, behaviour);
    }

    private static void bind(InputMap inputs, ActionMap actions, KeyStroke key, String name, ActionListener behaviour)
    {
        inputs.put(key, name);
        actions.put(name, new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent event)
            {
                behaviour.actionPerformed(event);
            }
        });
    }
}

package com.willclay.forgeide.ui.editor;

import com.willclay.forgeide.ui.editor.docs.DocCommentModel;
import com.willclay.forgeide.ui.editor.docs.DocCommentModel.DocComment;

import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.BoxView;
import javax.swing.text.ComponentView;
import javax.swing.text.Element;
import javax.swing.text.IconView;
import javax.swing.text.LabelView;
import javax.swing.text.ParagraphView;
import javax.swing.text.Position;
import javax.swing.text.StyleConstants;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.Objects;

/// Builds the views a styled document is drawn with, replacing the one that
/// draws a line so that a folded line can take up no room.
///
/// Swing has no notion of hidden text. What it does have is a view per element,
/// each of which is asked how much space it needs and then told to paint itself
/// in that space. A line that answers "no height" and then declines to paint is,
/// as far as everything above it is concerned, not there: the lines below move
/// up, the scrollbar shortens, and `modelToView2D` — which the gutter and
/// the caret both use — reports the new positions without being told anything.
///
/// That is the whole trick, and it is why folding needed no changes to the
/// document, the highlighter or the undo history. The text is still all there;
/// only its view is missing.
///
/// **Rendered doc comments use the same trick.** A rendered comment's later
/// lines are hidden just like folded ones, and its first line answers with the
/// height of the rendered block and paints that instead of its text. Again the
/// document never changes; see [DocCommentModel].
///
/// The rest of this factory reproduces what [javax.swing.text.StyledEditorKit]
/// would have built, because overriding the factory means replacing all of it
/// rather than adding to it.
public final class FoldingViewFactory implements ViewFactory
{
    private final ForgeEditorPane editor;

    /// @param editor the editor whose models decide how each line is drawn. Only
    ///               its lazily created getters are used, because the factory is
    ///               built while the editor's constructor is still running and
    ///               its fields are not yet assigned
    public FoldingViewFactory(ForgeEditorPane editor)
    {
        this.editor = Objects.requireNonNull(editor, "editor");
    }

    @Override
    public View create(Element element)
    {
        String kind = element.getName();
        if (kind == null) return new LabelView(element);

        return switch (kind)
        {
            case AbstractDocument.ContentElementName -> new LabelView(element);
            case AbstractDocument.ParagraphElementName -> new FoldableLineView(element, editor);
            case AbstractDocument.SectionElementName -> new BoxView(element, View.Y_AXIS);
            case StyleConstants.ComponentElementName -> new ComponentView(element);
            case StyleConstants.IconElementName -> new IconView(element);
            default -> new LabelView(element);
        };
    }

    /// One line of the document, which can be asked to occupy no height, or to
    /// stand in for a whole rendered doc comment.
    ///
    /// Every override guards the same conditions and every one of them is needed.
    /// Reporting zero height without declining to paint draws the line on top of
    /// its neighbour; declining to paint without reporting zero height leaves a
    /// blank gap where the line used to be.
    private static final class FoldableLineView extends ParagraphView
    {
        private final ForgeEditorPane editor;

        private FoldableLineView(Element element, ForgeEditorPane editor)
        {
            super(element);

            this.editor = editor;
        }

        /// A hidden line takes no width either, so a long line inside a fold or
        /// a rendered comment does not leave a scrollbar for text you cannot see.
        @Override
        public float getPreferredSpan(int axis)
        {
            if (isHidden()) return 0;

            DocComment comment = renderedComment();
            if (comment != null) return axis == Y_AXIS ? editor.getDocCommentPainter().height(comment) : 0;

            return super.getPreferredSpan(axis);
        }

        @Override
        public float getMinimumSpan(int axis)
        {
            if (isHidden()) return 0;

            DocComment comment = renderedComment();
            if (comment != null) return axis == Y_AXIS ? editor.getDocCommentPainter().height(comment) : 0;

            return super.getMinimumSpan(axis);
        }

        @Override
        public float getMaximumSpan(int axis)
        {
            if (axis == Y_AXIS && isHidden()) return 0;

            DocComment comment = renderedComment();
            if (comment != null && axis == Y_AXIS) return editor.getDocCommentPainter().height(comment);

            return super.getMaximumSpan(axis);
        }

        @Override
        public void paint(Graphics graphics, Shape allocation)
        {
            if (isHidden()) return;

            DocComment comment = renderedComment();
            if (comment != null)
            {
                editor.getDocCommentPainter().paint(graphics, comment, allocation.getBounds(), getStartOffset());
                return;
            }

            super.paint(graphics, allocation);
        }

        /// A caret that ends up inside a folded region has nowhere to be drawn.
        /// Answering with an empty rectangle at the top of the line keeps
        /// callers — the caret, the gutter, scroll-to-visible — from throwing;
        /// [ForgeEditorPane] separately makes sure the caret does not stay there.
        ///
        /// A rendered comment answers with its whole block, which is what lets
        /// the gutter line its marks up with the block and forward clicks to it.
        @Override
        public Shape modelToView(int position, Shape allocation, Position.Bias bias)
                throws BadLocationException
        {
            Rectangle bounds = allocation.getBounds();

            if (isHidden()) return new Rectangle(bounds.x, bounds.y, 0, 0);
            if (renderedComment() != null) return new Rectangle(bounds.x, bounds.y, 0, bounds.height);

            return super.modelToView(position, allocation, bias);
        }

        /// A click on a rendered comment lands on its first character; the
        /// editor then moves the caret out, since the source is not on screen.
        @Override
        public int viewToModel(float x, float y, Shape allocation, Position.Bias[] biasReturn)
        {
            if (renderedComment() == null) return super.viewToModel(x, y, allocation, biasReturn);

            biasReturn[0] = Position.Bias.Forward;
            return getStartOffset();
        }

        private boolean isHidden()
        {
            int line = lineIndex();

            return line >= 0 && editor.isLineHidden(line);
        }

        /// @return the comment drawn in place of this line, or null
        private DocComment renderedComment()
        {
            int line = lineIndex();
            if (line < 0 || editor.getDocCommentPainter() == null) return null;

            return editor.getDocCommentModel().renderedAt(line);
        }

        private int lineIndex()
        {
            Element root = getElement().getParentElement();

            return root == null ? -1 : root.getElementIndex(getStartOffset());
        }
    }
}

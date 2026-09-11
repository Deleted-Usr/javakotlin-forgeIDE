package com.willclay.forgeide.ui.editor;

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
import java.util.function.Supplier;

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
/// The rest of this factory reproduces what [javax.swing.text.StyledEditorKit]
/// would have built, because overriding the factory means replacing all of it
/// rather than adding to it.
public final class FoldingViewFactory implements ViewFactory
{
    private final Supplier<FoldingModel> folding;

    /// @param folding supplies the model, rather than holding it, because the
    ///                factory is built while the text pane's constructor is
    ///                still running and its fields are not yet assigned
    public FoldingViewFactory(Supplier<FoldingModel> folding)
    {
        this.folding = Objects.requireNonNull(folding, "folding");
    }

    @Override
    public View create(Element element)
    {
        String kind = element.getName();
        if (kind == null) return new LabelView(element);

        return switch (kind)
        {
            case AbstractDocument.ContentElementName -> new LabelView(element);
            case AbstractDocument.ParagraphElementName -> new FoldableLineView(element, folding);
            case AbstractDocument.SectionElementName -> new BoxView(element, View.Y_AXIS);
            case StyleConstants.ComponentElementName -> new ComponentView(element);
            case StyleConstants.IconElementName -> new IconView(element);
            default -> new LabelView(element);
        };
    }

    /// One line of the document, which can be asked to occupy no height.
    ///
    /// Every override guards the same condition and every one of them is needed.
    /// Reporting zero height without declining to paint draws the line on top of
    /// its neighbour; declining to paint without reporting zero height leaves a
    /// blank gap where the line used to be.
    private static final class FoldableLineView extends ParagraphView
    {
        private final Supplier<FoldingModel> folding;

        private FoldableLineView(Element element, Supplier<FoldingModel> folding)
        {
            super(element);

            this.folding = folding;
        }

        @Override
        public float getPreferredSpan(int axis)
        {
            return axis == Y_AXIS && isHidden() ? 0 : super.getPreferredSpan(axis);
        }

        @Override
        public float getMinimumSpan(int axis)
        {
            return axis == Y_AXIS && isHidden() ? 0 : super.getMinimumSpan(axis);
        }

        @Override
        public float getMaximumSpan(int axis)
        {
            return axis == Y_AXIS && isHidden() ? 0 : super.getMaximumSpan(axis);
        }

        @Override
        public void paint(Graphics graphics, Shape allocation)
        {
            if (isHidden()) return;

            super.paint(graphics, allocation);
        }

        /// A caret that ends up inside a folded region has nowhere to be drawn.
        /// Answering with an empty rectangle at the top of the line keeps
        /// callers — the caret, the gutter, scroll-to-visible — from throwing;
        /// [ForgeEditorPane] separately makes sure the caret does not stay there.
        @Override
        public Shape modelToView(int position, Shape allocation, Position.Bias bias)
                throws BadLocationException
        {
            if (!isHidden()) return super.modelToView(position, allocation, bias);

            Rectangle bounds = allocation.getBounds();
            return new Rectangle(bounds.x, bounds.y, 0, 0);
        }

        private boolean isHidden()
        {
            FoldingModel model = folding.get();
            if (model == null) return false;

            Element root = getElement().getParentElement();
            if (root == null) return false;

            return model.isHidden(root.getElementIndex(getStartOffset()));
        }
    }
}

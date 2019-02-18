package de.unistuttgart.ims.coref.annotator;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import javax.swing.text.LayeredHighlighter;

import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.set.sorted.MutableSortedSet;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.factory.Maps;
import org.eclipse.collections.impl.factory.SortedSets;
import org.eclipse.collections.impl.tuple.Tuples;

import de.unistuttgart.ims.coref.annotator.api.v1.DetachedMentionPart;
import de.unistuttgart.ims.coref.annotator.api.v1.Mention;
import de.unistuttgart.ims.coref.annotator.uima.AnnotationComparator;

class HighlightManager {
	Map<Object, Object> underlineMap = Maps.mutable.empty();
	Map<Annotation, Object> highlightMap = new HashMap<Annotation, Object>();
	DefaultHighlighter hilit;

	RangedCounter spanCounter = new RangedCounter();
	JTextComponent textComponent;

	public HighlightManager(JTextComponent component) {
		hilit = new DefaultHighlighter();
		hilit.setDrawsLayeredHighlights(false);
		textComponent = component;
		textComponent.setHighlighter(hilit);
	}

	@Deprecated
	public void clearAndDrawAllAnnotations(JCas jcas) {
		hilit.removeAllHighlights();
		underlineMap.clear();
		spanCounter.clear();
		for (Mention m : JCasUtil.select(jcas, Mention.class)) {
			highlight(m, new Color(m.getEntity().getColor()), false, false, null);
			if (m.getDiscontinuous() != null)
				highlight(m.getDiscontinuous(), new Color(m.getEntity().getColor()), true, false, null);

		}
		textComponent.repaint();
	}

	protected int underline(Annotation a, Color c, boolean dotted, boolean repaint) {
		Span span = new Span(a);

		try {
			if (underlineMap.containsKey(a)) {
				Object hi = underlineMap.get(a);
				spanCounter.subtract(span, hi);
				hilit.removeHighlight(hi);
			}
			int n = spanCounter.getNextLevel(span);
			Object hi = hilit.addHighlight(a.getBegin(), a.getEnd(), new UnderlinePainter(c, n * 3, dotted));
			spanCounter.add(span, hi, n);
			underlineMap.put(a, hi);
			// TODO: this is overkill, but didn't work otherwise
			if (repaint)
				textComponent.repaint();
			return n;
		} catch (BadLocationException e) {
			Annotator.logger.catching(e);
			return -1;
		}
	}

	protected int underline(Annotation a, Color c, boolean dotted, boolean repaint, int n) {
		Span span = new Span(a);
		try {
			if (underlineMap.containsKey(a)) {
				Object hi = underlineMap.get(a);
				spanCounter.subtract(span, hi);
				hilit.removeHighlight(hi);
			}
			Object hi = hilit.addHighlight(a.getBegin(), a.getEnd(), new UnderlinePainter(c, n * 3, dotted));
			spanCounter.add(span, hi, n);
			underlineMap.put(a, hi);
			// TODO: this is overkill, but didn't work otherwise
			if (repaint)
				textComponent.repaint();
			return n;
		} catch (BadLocationException e) {
			Annotator.logger.catching(e);
			return -1;
		}
	}

	protected int underline(Pair<Annotation, Annotation> pair, Color c, boolean dotted, boolean repaint, int n) {
		Span span = new Span(pair.getOne().getEnd(), pair.getTwo().getBegin());
		try {
			if (underlineMap.containsKey(pair)) {
				Object hi = underlineMap.get(pair);
				spanCounter.subtract(span, hi);
				hilit.removeHighlight(hi);
			}
			Object hi = hilit.addHighlight(span.begin, span.end, new UnderlinePainter(c, n * 3, dotted, 1));
			spanCounter.add(span, hi, n);
			underlineMap.put(pair, hi);
			// TODO: this is overkill, but didn't work otherwise
			if (repaint)
				textComponent.repaint();
			return n;
		} catch (BadLocationException e) {
			Annotator.logger.catching(e);
			return -1;
		}
	}

	protected void highlight(Annotation a, Color c, boolean dotted, boolean repaint,
			LayeredHighlighter.LayerPainter painter) {
		if (painter == null)
			throw new NullPointerException();
		Object hi = highlightMap.get(a);
		if (hi != null) {
			hilit.removeHighlight(hi);
		}
		try {
			hi = hilit.addHighlight(a.getBegin(), a.getEnd(), painter);
			highlightMap.put(a, hi);
			// TODO: this is overkill, but didn't work otherwise
			if (repaint)
				textComponent.repaint();

		} catch (BadLocationException e) {
			Annotator.logger.catching(e);
		}
	}

	public Highlighter getHighlighter() {
		return hilit;
	}

	public void highlight(Annotation a) {
		highlight(a, new Color(255, 255, 150));
	}

	public void highlight(Annotation a, Color c) {
		highlight(a, c, false, false, new DefaultHighlighter.DefaultHighlightPainter(c));
	}

	public void underline(Annotation a) {
		if (a instanceof Mention)
			underline((Mention) a);
		else if (a instanceof DetachedMentionPart)
			underline((DetachedMentionPart) a);
	}

	public void underline(DetachedMentionPart dmp) {
		hilit.setDrawsLayeredHighlights(true);
		underline(dmp, new Color(dmp.getMention().getEntity().getColor()), true, true);
		hilit.setDrawsLayeredHighlights(false);
	}

	public void underline(Mention m) {
		hilit.setDrawsLayeredHighlights(true);

		if (underlineMap.containsKey(m)) {
			Object hi = underlineMap.get(m);
			spanCounter.subtract(new Span(m), hi);
			hilit.removeHighlight(hi);
		}
		MutableSortedSet<Annotation> annotations = SortedSets.mutable.of(new AnnotationComparator(), m);
		if (m.getAdditionalExtent() != null)
			annotations.addAllIterable(Util.toIterable(m.getAdditionalExtent()));
		MutableList<Annotation> list = annotations.toList();
		int minLevel = list.collect(a -> spanCounter.getNextLevel(new Span(a))).min();
		Color color = new Color(m.getEntity().getColor());
		for (int i = 0; i < list.size(); i++) {
			underline(list.get(i), color, false, true, minLevel);
			if (i > 0)
				underline(Tuples.pair(list.get(i - 1), list.get(i)), color, false, true, minLevel);
		}

		if (m.getDiscontinuous() != null)
			underline(m.getDiscontinuous(), new Color(m.getEntity().getColor()), true, true);
		hilit.setDrawsLayeredHighlights(false);
	}

	public void underline(Annotation m, Color color) {
		if (m instanceof Mention)
			underline((Mention) m, color);
		else {
			hilit.setDrawsLayeredHighlights(true);
			underline(m, color, false, true);
			hilit.setDrawsLayeredHighlights(false);
		}
	}

	public void underline(Mention m, Color color) {
		hilit.setDrawsLayeredHighlights(true);
		underline(m, color, false, true);
		if (m.getDiscontinuous() != null)
			underline(m.getDiscontinuous(), color, true, true);
		hilit.setDrawsLayeredHighlights(false);
	}

	public void underline(Mention m, boolean repaint) {
		hilit.setDrawsLayeredHighlights(true);
		underline(m, new Color(m.getEntity().getColor()), false, false);
		if (m.getDiscontinuous() != null)
			underline(m.getDiscontinuous(), new Color(m.getEntity().getColor()), true, false);
		hilit.setDrawsLayeredHighlights(false);
	}

	public void unUnderline(Annotation a) {
		Object hi = underlineMap.get(a);
		Span span = new Span(a);
		if (span != null)
			spanCounter.subtract(span, hi);
		if (hi != null)
			hilit.removeHighlight(hi);

	}

	public void unHighlight() {
		highlightMap.values().forEach(o -> hilit.removeHighlight(o));
	}

	public void unHighlight(Annotation a) {
		Object hi = highlightMap.get(a);
		if (hi != null)
			hilit.removeHighlight(hi);

	}

	@Deprecated
	public void undraw(Annotation a) {
		Object hi = underlineMap.get(a);
		Span span = new Span(a);
		if (span != null)
			spanCounter.subtract(span, hi);
		if (hi != null)
			hilit.removeHighlight(hi);
	}

}
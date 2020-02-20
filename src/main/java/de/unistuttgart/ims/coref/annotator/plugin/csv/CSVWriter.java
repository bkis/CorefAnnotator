package de.unistuttgart.ims.coref.annotator.plugin.csv;

import java.io.IOException;
import java.io.Writer;
import java.util.NoSuchElementException;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang.StringUtils;
import org.apache.uima.UimaContext;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.impl.factory.Lists;

import de.unistuttgart.ims.coref.annotator.Annotator;
import de.unistuttgart.ims.coref.annotator.RangedHashSetValuedHashMap;
import de.unistuttgart.ims.coref.annotator.Util;
import de.unistuttgart.ims.coref.annotator.api.v1.Entity;
import de.unistuttgart.ims.coref.annotator.api.v1.EntityGroup;
import de.unistuttgart.ims.coref.annotator.api.v1.Flag;
import de.unistuttgart.ims.coref.annotator.api.v1.Line;
import de.unistuttgart.ims.coref.annotator.api.v1.Mention;
import de.unistuttgart.ims.coref.annotator.plugin.csv.CsvExportPlugin.ContextUnit;
import de.unistuttgart.ims.coref.annotator.plugins.SingleFileWriter;
import de.unistuttgart.ims.coref.annotator.uima.AnnotationComparator;

public class CSVWriter extends SingleFileWriter {

	private static final String ENTITY_GROUP = "entityGroup";
	private static final String ENTITY_LABEL = "entityLabel";
	private static final String ENTITY_NUM = "entityNum";
	private static final String SURFACE = "surface";
	private static final String END = "end";
	private static final String END_LINE = "end_line";
	private static final String BEGIN = "begin";
	private static final String BEGIN_LINE = "begin_line";
	private static final String CONTEXT_LEFT = "leftContext";
	private static final String CONTEXT_RIGHT = "rightContext";

	public static final String PARAM_CONTEXTWIDTH = "context width";
	@ConfigurationParameter(name = PARAM_CONTEXTWIDTH, defaultValue = "0", mandatory = false)
	int optionContextWidth = 0;

	public static final String PARAM_TRIM_WHITESPACE = "trim whitespace";
	@ConfigurationParameter(name = PARAM_TRIM_WHITESPACE, defaultValue = "true", mandatory = false)
	boolean optionTrimWhitespace = true;

	public static final String PARAM_REPLACE_NEWLINES = "replace newlines";
	@ConfigurationParameter(name = PARAM_REPLACE_NEWLINES, defaultValue = "false", mandatory = false)
	boolean optionReplaceNewlines = false;

	public static final String PARAM_CONTEXT_UNIT = "PARAM_CONTEXT_UNIT";
	@ConfigurationParameter(name = PARAM_CONTEXT_UNIT, defaultValue = "CHARACTER")
	CsvExportPlugin.ContextUnit optionContextUnit = ContextUnit.CHARACTER;

	public static final String PARAM_INCLUDE_LINE_NUMBERS = "PARAM_INCLUDE_LINE_NUMBERS";
	@ConfigurationParameter(name = PARAM_INCLUDE_LINE_NUMBERS, defaultValue = "false")
	boolean optionIncludeLineNumbers = false;

	Iterable<Entity> entities = null;
	String replacementForNewlines = " ";

	@Override
	public void initialize(final UimaContext context) throws ResourceInitializationException {
		super.initialize(context);
		configure();
	}

	public void configure() {
		if (optionContextUnit == ContextUnit.LINE)
			replacementForNewlines = " // ";
	}

	@Override
	public void write(JCas jcas, Writer os) throws IOException {

		ImmutableList<Mention> allMentions = Lists.mutable.withAll(JCasUtil.select(jcas, Mention.class))
				.sortThis(new AnnotationComparator()).toImmutable();
		ImmutableList<Flag> allFlags = Lists.immutable.withAll(JCasUtil.select(jcas, Flag.class));

		ImmutableList<Flag> mentionFlags = allFlags
				.select(f -> f.getTargetClass().equalsIgnoreCase(Mention.class.getName()));
		ImmutableList<Flag> entityFlags = allFlags
				.select(f -> f.getTargetClass().equalsIgnoreCase(Entity.class.getName()));

		RangedHashSetValuedHashMap<Line> lineIndex = new RangedHashSetValuedHashMap<Line>();
		if (optionIncludeLineNumbers) {
			for (Line line : JCasUtil.select(jcas, Line.class))
				lineIndex.add(line);
		}

		if (entities == null)
			entities = JCasUtil.select(jcas, Entity.class);

		try (CSVPrinter p = new CSVPrinter(os, CSVFormat.EXCEL)) {
			// this is the header row
			p.print(BEGIN);
			p.print(END);
			if (optionIncludeLineNumbers) {
				p.print(BEGIN_LINE);
				p.print(END_LINE);
			}
			if (optionContextWidth > 0) {
				p.print(CONTEXT_LEFT);
			}
			p.print(SURFACE);
			if (optionContextWidth > 0) {
				p.print(CONTEXT_RIGHT);
			}
			p.print(ENTITY_NUM);
			p.print(ENTITY_LABEL);
			p.print(ENTITY_GROUP);
			for (Flag flag : entityFlags) {
				p.print(Annotator.getStringWithDefault(flag.getLabel(), flag.getLabel()));
			}
			for (Flag flag : mentionFlags) {
				p.print(Annotator.getStringWithDefault(flag.getLabel(), flag.getLabel()));
			}
			p.println();
			int entityNum = 0;
			for (Entity entity : entities) {
				for (Mention mention : allMentions.select(m -> m.getEntity() == entity)) {
					String surface = mention.getCoveredText();
					if (mention.getDiscontinuous() != null)
						surface += " " + mention.getDiscontinuous().getCoveredText();
					if (optionReplaceNewlines)
						surface = surface.replaceAll(" ?[\n\r\f]+ ?", replacementForNewlines);
					p.print(mention.getBegin());
					p.print(mention.getEnd());
					if (optionIncludeLineNumbers) {
						try {
							p.print(JCasUtil.selectPreceding(Line.class, mention, 1).get(0).getNumber());
						} catch (Exception e) {
							p.print(-1);
						}
						try {
							p.print(JCasUtil.selectFollowing(Line.class, mention, 1).get(0).getNumber() - 1);
						} catch (IllegalStateException e) {
							p.print(-1);
						}
					}
					if (optionContextWidth > 0) {
						try {

							String contextString = getContext(jcas, mention, true);
							if (optionReplaceNewlines)
								contextString = contextString.replaceAll(" ?[\n\r\f]+ ?", replacementForNewlines);
							p.print(contextString);
						} catch (NoSuchElementException e) {
							p.print("");

						}
					}
					p.print((optionTrimWhitespace ? surface.trim() : surface));
					if (optionContextWidth > 0) {
						try {
							String contextString = getContext(jcas, mention, false);
							if (optionReplaceNewlines)
								contextString = contextString.replaceAll(" ?[\n\r\f]+ ?", replacementForNewlines);
							p.print(contextString);
						} catch (NoSuchElementException e) {
							p.print("");

						}
					}
					p.print(entityNum);
					p.print(entity.getLabel());
					p.print((entity instanceof EntityGroup));
					for (Flag flag : entityFlags) {
						p.print(Util.isX(entity, flag.getKey()));
					}
					for (Flag flag : mentionFlags) {
						p.print(Util.isX(mention, flag.getKey()));
					}
					p.println();
				}
				entityNum++;
			}
		}
	}

	protected String getContext(JCas jcas, Mention mention, boolean backward) {
		String text = jcas.getDocumentText();
		switch (optionContextUnit) {
		case LINE:
			if (backward) {
				int leftEnd = Lists.immutable.withAll(JCasUtil.selectPreceding(Line.class, mention, optionContextWidth))
						.collect(l -> l.getBegin()).min();
				if (optionTrimWhitespace) {
					return text.substring(leftEnd, mention.getBegin()).trim();
				} else {
					return text.substring(leftEnd, mention.getBegin());
				}
			} else {
				int rightEnd = Lists.immutable
						.withAll(JCasUtil.selectFollowing(Line.class, mention, optionContextWidth))
						.collect(l -> l.getEnd()).max();
				if (optionTrimWhitespace) {
					return text.substring(mention.getEnd(), rightEnd).trim();
				} else {
					return text.substring(mention.getEnd(), rightEnd);
				}
			}
		default:
			if (backward)
				if (optionTrimWhitespace) {
					return StringUtils.right(text.substring(0, mention.getBegin()).trim(), optionContextWidth);

				} else {
					return StringUtils.right(text, optionContextWidth);
				}
			else if (optionTrimWhitespace) {
				return StringUtils.left(text.substring(mention.getEnd()).trim(), optionContextWidth);
			} else {
				return StringUtils.left(text, optionContextWidth);
			}
		}
	}

	public Iterable<Entity> getEntities() {
		return entities;
	}

	public void setEntities(Iterable<Entity> entities) {
		this.entities = entities;
	}

	public int getOptionContextWidth() {
		return optionContextWidth;
	}

	public void setOptionContextWidth(int optionContextWidth) {
		this.optionContextWidth = optionContextWidth;
	}

	public boolean isOptionTrimWhitespace() {
		return optionTrimWhitespace;
	}

	public void setOptionTrimWhitespace(boolean optionTrimWhitespace) {
		this.optionTrimWhitespace = optionTrimWhitespace;
	}

	public boolean isOptionReplaceNewlines() {
		return optionReplaceNewlines;
	}

	public void setOptionReplaceNewlines(boolean optionReplaceNewlines) {
		this.optionReplaceNewlines = optionReplaceNewlines;
	}

	public CsvExportPlugin.ContextUnit getOptionContextUnit() {
		return optionContextUnit;
	}

	public void setOptionContextUnit(CsvExportPlugin.ContextUnit optionContextUnit) {
		this.optionContextUnit = optionContextUnit;
	}

	public boolean isOptionIncludeLineNumbers() {
		return optionIncludeLineNumbers;
	}

	public void setOptionIncludeLineNumbers(boolean optionIncludeLineNumbers) {
		this.optionIncludeLineNumbers = optionIncludeLineNumbers;
	}

}

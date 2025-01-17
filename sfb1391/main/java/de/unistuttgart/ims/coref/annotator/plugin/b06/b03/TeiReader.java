package de.unistuttgart.ims.coref.annotator.plugin.b06.b03;

import java.io.IOException;
import java.io.InputStream;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.factory.AnnotationFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.dkpro.core.api.io.ResourceCollectionReaderBase;
import org.dkpro.core.api.resources.CompressionUtils;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.factory.Maps;
import org.eclipse.collections.impl.factory.Sets;

import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.unistuttgart.ims.coref.annotator.Annotator;
import de.unistuttgart.ims.coref.annotator.ColorProvider;
import de.unistuttgart.ims.coref.annotator.TypeSystemVersion;
import de.unistuttgart.ims.coref.annotator.api.format.Bold;
import de.unistuttgart.ims.coref.annotator.api.format.Head;
import de.unistuttgart.ims.coref.annotator.api.format.Italic;
import de.unistuttgart.ims.coref.annotator.api.format.WideSpacing;
import de.unistuttgart.ims.coref.annotator.api.sfb1391.LineBreak;
import de.unistuttgart.ims.coref.annotator.api.sfb1391.Milestone;
import de.unistuttgart.ims.coref.annotator.api.v2.Entity;
import de.unistuttgart.ims.coref.annotator.api.v2.Line;
import de.unistuttgart.ims.coref.annotator.api.v2.Mention;
import de.unistuttgart.ims.coref.annotator.api.v2.MentionSurface;
import de.unistuttgart.ims.coref.annotator.api.v2.Segment;
import de.unistuttgart.ims.coref.annotator.plugin.tei.TeiStylePlugin;
import de.unistuttgart.ims.coref.annotator.uima.UimaUtil;
import de.unistuttgart.ims.uima.io.xml.GenericXmlReader;
import de.unistuttgart.ims.uima.io.xml.type.XMLElement;

public class TeiReader extends ResourceCollectionReaderBase {

	public static final String PARAM_DOCUMENT_ID = "Document Id";

	@ConfigurationParameter(name = PARAM_DOCUMENT_ID, mandatory = true)
	String documentId = null;

	private static final String CHAPTER = "chapter";
	private static final String STANZA = "stanza";
	private static final String TYPE = "type";
	private static final String N = "n";
	private static final String RS = "rs";

	@Override
	public void getNext(CAS aCAS) {
		ColorProvider colorProvider = new ColorProvider();

		JCas jcas;
		try {
			jcas = aCAS.getJCas();
		} catch (CASException e2) {
			Annotator.logger.catching(e2);
			return;
		}

		MutableMap<String, Entity> entityMap = Maps.mutable.empty();
		MutableMap<String, Mention> mentionMap = Maps.mutable.empty();

		GenericXmlReader<DocumentMetaData> gxr = new GenericXmlReader<DocumentMetaData>(DocumentMetaData.class);
		gxr.setTextRootSelector(null);
		gxr.setPreserveWhitespace(true);

		// set the document title
		gxr.addGlobalRule("titleStmt > title", (d, e) -> d.setDocumentTitle(e.text()));

		gxr.addGlobalRule("langUsage[usage=100]", (d, e) -> jcas.setDocumentLanguage(e.attr("ident")));

		gxr.addRule("[ref]", MentionSurface.class, (ms, e) -> {
			// retrieve mention id
			String mentionId = null;
			if (e.hasAttr("prev"))
				mentionId = e.attr("prev");
			else if (e.hasAttr("id"))
				mentionId = e.attr("id");

			// create or retrieve mention
			Mention m = null;
			if (mentionId != null)
				m = mentionMap.get(mentionId);
			if (m == null) {
				m = new Mention(jcas);
				m.addToIndexes();
				m.setSurface(new FSArray<MentionSurface>(jcas, 0));
				mentionMap.put(mentionId, m);
			}
			ms.setMention(m);
			m.setSurface(UimaUtil.addTo(jcas, m.getSurface(), ms));

			// retrieve entity id
			String entityId = e.attr("ref").substring(1);

			// create or retrieve entity
			Entity entity = entityMap.get(entityId);
			if (entity == null) {
				entity = new Entity(jcas);
				entity.addToIndexes();
				entity.setColor(colorProvider.getNextColor().getRGB());
				// TODO: read old label from XML
				entity.setLabel(UimaUtil.getCoveredText(m));
				entity.setXmlId(entityId);
				entityMap.put(entityId, entity);
			}
			m.setEntity(entity);
		});

		gxr.addRule("head", Head.class);
		gxr.addRule("emph", Italic.class);
		gxr.addRule("[rend*=bold]", Bold.class);
		gxr.addRule("[rend*=italic]", Italic.class);
		gxr.addRule("[rend*=wide-spacing]", WideSpacing.class);
		gxr.addRule("lb", LineBreak.class, (lineBreak, element) -> lineBreak.setN(element.attr("n")));
		gxr.addRule("milestone", Milestone.class, (ms, element) -> {
			if (element.hasAttr(N))
				ms.setN(element.attr(N));
			ms.setMilestoneType(element.attr(TYPE));
		});
		gxr.addRule("teiCorpus > TEI", Segment.class, (segment, element) -> {
			segment.setLabel(element.select("teiHeader > fileDesc > title").text());
		});

		Resource res = nextFile();

		// Read XMI file
		try (InputStream is = CompressionUtils.getInputStream(res.getLocation(), res.getInputStream())) {
			gxr.read(jcas, is);
		} catch (IOException e1) {
			Annotator.logger.catching(e1);
		}

		if (JCasUtil.exists(jcas, DocumentMetaData.class))
			DocumentMetaData.get(jcas).setDocumentId(documentId);
		else
			DocumentMetaData.create(jcas).setDocumentId(documentId);

		UimaUtil.getMeta(jcas).setStylePlugin(TeiStylePlugin.class.getName());
		UimaUtil.getMeta(jcas).setTypeSystemVersion(TypeSystemVersion.getCurrent().toString());

		for (XMLElement element : Sets.immutable.withAll(JCasUtil.select(jcas, XMLElement.class))) {
			if (element.getTag().equalsIgnoreCase(RS))
				element.removeFromIndexes();
		}

		// fix lines
		for (LineBreak lb : JCasUtil.select(jcas, LineBreak.class)) {
			Milestone nextMilestone = null;
			nextMilestone = JCasUtil.selectFollowing(Milestone.class, lb, 1).get(0);
			if (nextMilestone != null) {
				Line line = AnnotationFactory.createAnnotation(jcas, lb.getEnd(), nextMilestone.getBegin(), Line.class);
				try {
					line.setNumber(Integer.valueOf(lb.getN()));
				} catch (NumberFormatException e) {
					line.setNumber(Integer.MIN_VALUE);
					// catch silently
				}
			}
		}

		// create segment annotations
		for (Milestone ms : JCasUtil.select(jcas, Milestone.class)) {
			if (ms.getMilestoneType() == null || !ms.getMilestoneType().equalsIgnoreCase(CHAPTER))
				continue;
			Milestone nextMS = getNextMilestone(ms, CHAPTER);
			if (nextMS != null) {
				Segment seg = AnnotationFactory.createAnnotation(jcas, ms.getEnd(), nextMS.getBegin(), Segment.class);
				seg.setLabel(ms.getN());
			} else {
				Segment seg = AnnotationFactory.createAnnotation(jcas, ms.getEnd(), jcas.getDocumentText().length(),
						Segment.class);
				seg.setLabel(ms.getN());
			}
		}

		// create segment annotations for stanzas
		for (Milestone ms : JCasUtil.select(jcas, Milestone.class)) {
			if (ms.getMilestoneType() == null || !ms.getMilestoneType().equalsIgnoreCase(STANZA))
				continue;
			Milestone nextMS = getNextMilestone(ms, STANZA);
			if (nextMS != null) {
				Segment seg = AnnotationFactory.createAnnotation(jcas, ms.getEnd(), nextMS.getBegin(), Segment.class);
				seg.setLabel(ms.getN());
			} else {
				Segment seg = AnnotationFactory.createAnnotation(jcas, ms.getEnd(), jcas.getDocumentText().length(),
						Segment.class);
				seg.setLabel(ms.getN());
			}
		}

		// We skip this for now
		// TODO: need a new way to display many small segments
		if (false)
			for (Milestone ms : JCasUtil.select(jcas, Milestone.class)) {
				Milestone nextMilestone = null;
				nextMilestone = JCasUtil.selectFollowing(Milestone.class, ms, 1).get(0);
				if (nextMilestone != null) {
					Segment seg = AnnotationFactory.createAnnotation(jcas, ms.getEnd(), nextMilestone.getBegin(),
							Segment.class);
					seg.setLabel(ms.getN());
				}
			}
	}

	Milestone getNextMilestone(Milestone current, String type) {
		for (Milestone ms : JCasUtil.selectFollowing(Milestone.class, current, Integer.MAX_VALUE)) {
			if (ms != current && ms.getMilestoneType() != null && ms.getMilestoneType().equalsIgnoreCase(type))
				return ms;
		}
		return null;

	}

}

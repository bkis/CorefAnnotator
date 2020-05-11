package de.unistuttgart.ims.coref.annotator.plugin.quadrama.tei;

import java.io.IOException;
import java.io.InputStream;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.dkpro.core.api.io.ResourceCollectionReaderBase;
import org.dkpro.core.api.resources.CompressionUtils;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.factory.Maps;
import org.eclipse.collections.impl.factory.Sets;
import org.jsoup.nodes.Element;

import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.unistuttgart.ims.coref.annotator.Annotator;
import de.unistuttgart.ims.coref.annotator.ColorProvider;
import de.unistuttgart.ims.coref.annotator.TypeSystemVersion;
import de.unistuttgart.ims.coref.annotator.Util;
import de.unistuttgart.ims.coref.annotator.api.v1.Entity;
import de.unistuttgart.ims.coref.annotator.api.v1.Mention;
import de.unistuttgart.ims.coref.annotator.api.v1.Segment;
import de.unistuttgart.ims.coref.annotator.plugin.quadrama.QDStylePlugin;
import de.unistuttgart.ims.drama.api.Speaker;
import de.unistuttgart.ims.uima.io.xml.GenericXmlReader;
import de.unistuttgart.ims.uima.io.xml.type.XMLElement;

public class TeiReader extends ResourceCollectionReaderBase {

	public static final String PARAM_DOCUMENT_ID = "Document Id";

	@ConfigurationParameter(name = PARAM_DOCUMENT_ID, mandatory = true)
	String documentId = null;

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

		GenericXmlReader<DocumentMetaData> gxr = new GenericXmlReader<DocumentMetaData>(DocumentMetaData.class);
		gxr.setTextRootSelector(null);
		gxr.setPreserveWhitespace(true);

		// set the document title
		gxr.addGlobalRule("titleStmt > title:first-child", (d, e) -> d.setDocumentTitle(e.text()));

		// characters declared in the header (GerDraCor)
		gxr.addGlobalRule("profileDesc [xml:id]", Mention.class, (m, e) -> {
			Entity cf = new Entity(jcas);
			cf.addToIndexes();
			cf.setLabel(e.attr("xml:id"));
			cf.setColor(colorProvider.getNextColor().getRGB());
			entityMap.put(e.attr("xml:id"), cf);
			m.setEntity(cf);
		});

		// other entities declared in the text (QuaDramA legacy)
		gxr.addGlobalRule("text [xml:id]", Mention.class, (m, e) -> {
			Entity cf = new Entity(jcas);
			cf.addToIndexes();
			cf.setLabel(e.attr("xml:id"));
			cf.setColor(colorProvider.getNextColor().getRGB());
			entityMap.put(e.attr("xml:id"), cf);
			m.setEntity(cf);
		});

		gxr.addRule("speaker", Speaker.class);

		// entity references
		gxr.addRule("text rs[ref]", Mention.class, (m, e) -> {
			String id = e.attr("ref").substring(1);
			Entity entity = entityMap.get(id);
			if (entity == null) {
				entity = new Entity(jcas);
				entity.addToIndexes();
				entity.setColor(colorProvider.getNextColor().getRGB());
				entity.setLabel(m.getCoveredText());
				entity.setXmlId(id);
				entityMap.put(id, entity);
			}
			m.setEntity(entity);
		});

		// entity references (QuaDramA legacy)
		gxr.addRule("text name[ref]", Mention.class, (m, e) -> {
			String id = e.attr("ref").substring(1);
			Entity entity = entityMap.get(id);
			if (entity == null) {
				entity = new Entity(jcas);
				entity.addToIndexes();
				entity.setColor(colorProvider.getNextColor().getRGB());
				entity.setLabel(m.getCoveredText());
				entity.setXmlId(id);
				entityMap.put(id, entity);
			}
			m.setEntity(entity);
		});

		gxr.addRule("text speaker", Mention.class, (m, e) -> {
			Element parent = e.parent();
			if (parent.hasAttr("who")) {
				String id = parent.attr("who").substring(1);
				Entity entity = entityMap.get(id);
				if (entity == null) {
					entity = new Entity(jcas);
					entity.addToIndexes();
					entity.setLabel(m.getCoveredText());
					entity.setColor(colorProvider.getNextColor().getRGB());
					entityMap.put(id, entity);
				}
				m.setEntity(entity);
			}
		});

		gxr.addRule("[type=act]", Segment.class, (s, e) -> {
			Element titleElement = e.selectFirst("div > desc > title");
			if (titleElement == null)
				titleElement = e.selectFirst("head");
			if (titleElement != null)
				s.setLabel(titleElement.text());
		});
		gxr.addRule("[type=scene]", Segment.class, (s, e) -> {
			Element titleElement = e.selectFirst("div > desc > title");
			if (titleElement == null)
				titleElement = e.selectFirst("head");
			if (titleElement != null)
				s.setLabel(titleElement.text());
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

		// set meta data
		Util.getMeta(jcas).setStylePlugin(QDStylePlugin.class.getName());
		Util.getMeta(jcas).setTypeSystemVersion(TypeSystemVersion.getCurrent().toString());

		// Remove <rs> und <name> elements from XML structure (they'll be added later)
		for (XMLElement element : Sets.immutable.withAll(JCasUtil.select(jcas, XMLElement.class))) {
			if (element.getTag().equalsIgnoreCase("rs") && element.getSelector().contains("> text >"))
				element.removeFromIndexes();
			if (element.getTag().equalsIgnoreCase("name") && element.getSelector().contains("> text >")) {
				element.removeFromIndexes();
			}
		}
	}

}
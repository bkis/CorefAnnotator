package de.unistuttgart.ims.coref.annotator.plugin.quadrama.tei;

import java.io.File;

import javax.swing.filechooser.FileFilter;

import org.apache.commons.io.IOUtils;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.component.NoOpAnnotator;
import org.apache.uima.fit.factory.AggregateBuilder;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.resource.ResourceInitializationException;

import de.unistuttgart.ims.coref.annotator.ExtensionFilters;
import de.unistuttgart.ims.coref.annotator.FileFilters;
import de.unistuttgart.ims.coref.annotator.plugins.AbstractImportPlugin;
import de.unistuttgart.ims.coref.annotator.plugins.UimaImportPlugin;
import javafx.stage.FileChooser.ExtensionFilter;

public class QuadramaTeiImportPlugin extends AbstractImportPlugin implements UimaImportPlugin {

	@Override
	public String getDescription() {
		try {
			return IOUtils.toString(getClass().getResourceAsStream("description.txt"), "UTF-8");
		} catch (Exception e) {
			// TODO: find out why this doesn't work
			// e.printStackTrace();
		}
		return "";
	}

	@Override
	public String getName() {
		return "QuaDramA/TEI";
	}

	@Override
	public AnalysisEngineDescription getImporter() throws ResourceInitializationException {
		AggregateBuilder b = new AggregateBuilder();
		b.add(AnalysisEngineFactory.createEngineDescription(NoOpAnnotator.class));
		return b.createAggregateDescription();
	}

	@Override
	public CollectionReaderDescription getReader(File f) throws ResourceInitializationException {
		return CollectionReaderFactory.createReaderDescription(TeiReader.class, TeiReader.PARAM_SOURCE_LOCATION,
				f.getAbsoluteFile(), TeiReader.PARAM_LANGUAGE, "de", TeiReader.PARAM_DOCUMENT_ID, f.getName());
	}

	@Override
	public FileFilter getFileFilter() {
		return FileFilters.xml;
	}

	@Override
	public String getSuffix() {
		return ".xml";
	}

	@Override
	public ExtensionFilter getExtensionFilter() {
		return ExtensionFilters.tei;
	}

}

package de.unistuttgart.ims.coref.annotator.plugin.versions.v1;

import java.io.File;

import javax.swing.filechooser.FileFilter;

import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.factory.AggregateBuilder;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.resource.ResourceInitializationException;
import org.dkpro.core.io.xmi.XmiReader;

import de.unistuttgart.ims.coref.annotator.ExtensionFilters;
import de.unistuttgart.ims.coref.annotator.FileFilters;
import de.unistuttgart.ims.coref.annotator.plugins.AbstractImportPlugin;
import de.unistuttgart.ims.coref.annotator.plugins.UimaImportPlugin;
import javafx.stage.FileChooser.ExtensionFilter;

public class Plugin extends AbstractImportPlugin implements UimaImportPlugin {

	@Override
	public String getDescription() {
		return "Import v1 files and convert them to file format v2";
	}

	@Override
	public String getName() {
		return "Convert: v1 → v2";
	}

	@Override
	public AnalysisEngineDescription getImporter() throws ResourceInitializationException {
		AggregateBuilder b = new AggregateBuilder();
		b.add(AnalysisEngineFactory.createEngineDescription(V1_To_V2.class));
		return b.createAggregateDescription();
	}

	@Override
	public FileFilter getFileFilter() {
		return FileFilters.xmi_gz;
	}

	@Override
	public ExtensionFilter getExtensionFilter() {
		return ExtensionFilters.xmi_gz;
	}

	@Override
	public String getSuffix() {
		return ".xmi";
	}

	@Override
	public CollectionReaderDescription getReader(File f) throws ResourceInitializationException {
		return CollectionReaderFactory.createReaderDescription(XmiReader.class, XmiReader.PARAM_LENIENT, true,
				XmiReader.PARAM_ADD_DOCUMENT_METADATA, false, XmiReader.PARAM_SOURCE_LOCATION, f.getAbsolutePath());
	}

}

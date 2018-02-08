package de.unistuttgart.ims.coref.annotator.plugins;

import java.io.File;

import javax.swing.filechooser.FileFilter;

import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.resource.ResourceInitializationException;

public interface IOPlugin {
	String getDescription();

	String getName();

	AnalysisEngineDescription getImporter() throws ResourceInitializationException;

	AnalysisEngineDescription getExporter() throws ResourceInitializationException;

	CollectionReaderDescription getReader(File f) throws ResourceInitializationException;

	StylePlugin getStylePlugin();

	FileFilter getFileFilter();
}

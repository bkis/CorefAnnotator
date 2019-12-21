package de.unistuttgart.ims.coref.annotator.plugins;

import java.io.File;
import java.util.function.Consumer;

import javax.swing.filechooser.FileFilter;

import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.resource.ResourceInitializationException;
import org.kordamp.ikonli.Ikon;

import javafx.stage.FileChooser.ExtensionFilter;

public interface IOPlugin extends Plugin {

	AnalysisEngineDescription getImporter() throws ResourceInitializationException;

	AnalysisEngineDescription getExporter() throws ResourceInitializationException;

	CollectionReaderDescription getReader(File f) throws ResourceInitializationException;

	AnalysisEngineDescription getWriter(File f) throws ResourceInitializationException;

	Class<? extends StylePlugin> getStylePlugin();

	FileFilter getFileFilter();

	ExtensionFilter getExtensionFilter();

	String getSuffix();

	String[] getSupportedLanguages();

	Consumer<File> getPostExportAction();

	Ikon getIkon();

}

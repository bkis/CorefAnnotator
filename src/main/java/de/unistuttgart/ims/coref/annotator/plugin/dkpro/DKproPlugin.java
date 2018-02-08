package de.unistuttgart.ims.coref.annotator.plugin.dkpro;

import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.resource.ResourceInitializationException;

import de.unistuttgart.ims.coref.annotator.plugins.DefaultIOPlugin;
import de.unistuttgart.ims.coref.annotator.plugins.StylePlugin;

/**
 * TODO: Compile without access to java classes
 *
 */
public class DKproPlugin extends DefaultIOPlugin {

	@Override
	public String getDescription() {
		return null;
	}

	@Override
	public String getName() {
		return "DKpro";
	}

	@Override
	public AnalysisEngineDescription getImporter() throws ResourceInitializationException {
		return AnalysisEngineFactory.createEngineDescription(ImportDKpro.class);
	}

	@Override
	public AnalysisEngineDescription getExporter() throws ResourceInitializationException {
		return null;
	}

	@Override
	public StylePlugin getStylePlugin() {
		return null;
	}

}
package de.unistuttgart.ims.coref.annotator.plugin.proc.stanford;

import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.fit.factory.AggregateBuilder;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.resource.ResourceInitializationException;
import org.dkpro.core.stanfordnlp.StanfordNamedEntityRecognizer;
import org.dkpro.core.tokit.BreakIteratorSegmenter;
import org.kordamp.ikonli.Ikon;

import de.unistuttgart.ims.coref.annotator.plugins.ProcessingPlugin;

public class NerPlugin implements ProcessingPlugin {

	@Override
	public String getDescription() {
		return "Stanford Named Entity Recognition ";
	}

	@Override
	public String getName() {
		return "Stanford NER";
	}

	@Override
	public AnalysisEngineDescription getEngineDescription() throws ResourceInitializationException {
		AggregateBuilder b = new AggregateBuilder();
		b.add(AnalysisEngineFactory.createEngineDescription(BreakIteratorSegmenter.class));
		b.add(AnalysisEngineFactory.createEngineDescription(StanfordNamedEntityRecognizer.class));

		return b.createAggregateDescription();
	}

	@Override
	public String[] getSupportedLanguages() {
		return new String[] { "en", "de", "es", "fr", "it", "nl", "ru" };
	}

	@Override
	public Ikon getIkon() {
		return null;
	}

}

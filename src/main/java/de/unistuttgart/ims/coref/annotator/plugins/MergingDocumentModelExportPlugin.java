package de.unistuttgart.ims.coref.annotator.plugins;

public interface MergingDocumentModelExportPlugin extends DocumentModelExportPlugin {

	void writeHeader(Appendable appendable);

}

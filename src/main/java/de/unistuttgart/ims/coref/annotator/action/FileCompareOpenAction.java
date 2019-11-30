package de.unistuttgart.ims.coref.annotator.action;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.concurrent.ExecutionException;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;

import org.apache.uima.UIMAException;
import org.apache.uima.jcas.JCas;
import org.kordamp.ikonli.materialdesign.MaterialDesign;

import de.unistuttgart.ims.coref.annotator.Annotator;
import de.unistuttgart.ims.coref.annotator.CompareMentionsWindow;
import de.unistuttgart.ims.coref.annotator.Strings;
import de.unistuttgart.ims.coref.annotator.comp.SelectTwoFiles;
import de.unistuttgart.ims.coref.annotator.profile.Parser;
import de.unistuttgart.ims.coref.annotator.profile.Profile;
import de.unistuttgart.ims.coref.annotator.worker.JCasLoader;
import de.unistuttgart.ims.coref.annotator.worker.MultiDocumentModelLoader;

public class FileCompareOpenAction extends IkonAction {

	private static final long serialVersionUID = 1L;

	public FileCompareOpenAction() {
		super(Strings.ACTION_COMPARE, MaterialDesign.MDI_COMPARE);
		putValue(Action.SHORT_DESCRIPTION, Annotator.getString(Strings.ACTION_COMPARE_TOOLTIP));
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		JDialog dialog = new SelectTwoFiles(new RunComparisonAction());
		dialog.setVisible(true);
		dialog.pack();
	}

	class RunComparisonAction extends AbstractAction {

		private static final long serialVersionUID = 1L;

		public RunComparisonAction() {
			putValue(Action.NAME, "Compare");
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			SelectTwoFiles stf = (SelectTwoFiles) SwingUtilities.getWindowAncestor((Component) e.getSource());

			SwingUtilities.invokeLater(new Runnable() {

				@Override
				public void run() {
					CompareMentionsWindow cmw;

					try {

						cmw = new CompareMentionsWindow(Annotator.app, stf.getFiles().size());
						cmw.setIndeterminateProgress();
						cmw.setVisible(true);
						cmw.setFiles(stf.getFiles());
						JCas[] jcass = new JCas[stf.getFiles().size()];

						for (int i = 0; i < stf.getFiles().size(); i++) {
							final int j = i;
							File profileFile = new File(stf.getFiles().get(i).getParentFile(), "profile.xml");
							final Profile profile = new Parser().getProfileOrNull(profileFile);

							JCasLoader jcasLoader = new JCasLoader(stf.getFiles().get(i), jcas -> {
								// cmw.setJCas(jcas, stf.getNames().get(j), j);
							}, ex -> {
								cmw.setVisible(false);
								cmw.dispose();
								Annotator.app.warnDialog(ex.getLocalizedMessage(), "Loading Error");
							});
							jcasLoader.execute();
							try {
								jcass[i] = jcasLoader.get();
							} catch (InterruptedException | ExecutionException e1) {
								// TODO Auto-generated catch block
								e1.printStackTrace();
							}

						}

						MultiDocumentModelLoader mdl = new MultiDocumentModelLoader(
								model -> cmw.setMultiDocumentModel(model), jcass);
						mdl.execute();

						cmw.setVisible(true);
						cmw.pack();
						SwingUtilities.getWindowAncestor((Component) e.getSource()).setVisible(false);
					} catch (UIMAException e1) {
						Annotator.logger.catching(e1);
					}
				}

			});

		}

	}

}

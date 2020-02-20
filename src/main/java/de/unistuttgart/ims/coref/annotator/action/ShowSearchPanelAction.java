package de.unistuttgart.ims.coref.annotator.action;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.Action;
import javax.swing.KeyStroke;

import org.kordamp.ikonli.materialdesign.MaterialDesign;

import de.unistuttgart.ims.coref.annotator.Annotator;
import de.unistuttgart.ims.coref.annotator.DocumentWindow;
import de.unistuttgart.ims.coref.annotator.Strings;

public class ShowSearchPanelAction extends DocumentWindowAction {
	private static final long serialVersionUID = 1L;

	Annotator mainApplication;

	public ShowSearchPanelAction(Annotator mainApplication, DocumentWindow dw) {
		super(dw, Strings.ACTION_SEARCH, MaterialDesign.MDI_FILE_FIND);
		putValue(Action.SHORT_DESCRIPTION, Annotator.getString("action.search.tooltip"));
		putValue(Action.ACCELERATOR_KEY,
				KeyStroke.getKeyStroke(KeyEvent.VK_F, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
		this.mainApplication = mainApplication;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		getTarget().showSearch();
	}
}

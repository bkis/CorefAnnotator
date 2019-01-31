package de.unistuttgart.ims.coref.annotator.action;

import java.awt.event.ActionEvent;

import javax.swing.Action;

import org.kordamp.ikonli.materialdesign.MaterialDesign;

import de.unistuttgart.ims.coref.annotator.Annotator;
import de.unistuttgart.ims.coref.annotator.Constants;
import de.unistuttgart.ims.coref.annotator.Constants.Strings;
import de.unistuttgart.ims.coref.annotator.Util;
import de.unistuttgart.ims.coref.annotator.api.v1.Entity;
import de.unistuttgart.ims.coref.annotator.document.DocumentModel;

public class AddFlagAction extends TargetedIkonAction<DocumentModel> {

	private static final long serialVersionUID = 1L;

	public AddFlagAction(DocumentModel dw) {
		super(dw, Constants.Strings.ACTION_ADD_FLAG, MaterialDesign.MDI_FLAG);
		putValue(Action.SHORT_DESCRIPTION, Annotator.getString(Strings.ACTION_ADD_FLAG_TOOLTIP));
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		getTarget().getFlagModel().addFlag("New Flag", Entity.class, Util.randomEnum(MaterialDesign.class));
	}

}

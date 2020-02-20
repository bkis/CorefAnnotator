package de.unistuttgart.ims.coref.annotator.action;

import java.awt.Color;
import java.awt.event.ActionEvent;

import javax.swing.Action;
import javax.swing.JColorChooser;

import org.kordamp.ikonli.materialdesign.MaterialDesign;

import de.unistuttgart.ims.coref.annotator.Annotator;
import de.unistuttgart.ims.coref.annotator.CATreeNode;
import de.unistuttgart.ims.coref.annotator.DocumentWindow;
import de.unistuttgart.ims.coref.annotator.Strings;
import de.unistuttgart.ims.coref.annotator.document.op.UpdateEntityColor;

public class ChangeColorForEntity extends TargetedOperationIkonAction<DocumentWindow> {

	private static final long serialVersionUID = 1L;

	public ChangeColorForEntity(DocumentWindow dw) {
		super(dw, Strings.ACTION_SET_COLOR, MaterialDesign.MDI_FORMAT_COLOR_FILL);
		putValue(Action.SHORT_DESCRIPTION, Annotator.getString(Strings.ACTION_SET_COLOR_TOOLTIP));
		operationClass = UpdateEntityColor.class;
	}

	@Override
	public void actionPerformed(ActionEvent e) {

		CATreeNode etn = (CATreeNode) getTarget().getTree().getLastSelectedPathComponent();
		Color color = new Color(etn.getEntity().getColor());

		Color newColor = JColorChooser.showDialog(getTarget(), Annotator.getString(Strings.DIALOG_CHANGE_COLOR_PROMPT),
				color);
		if (color != newColor) {
			getTarget().getDocumentModel().edit(new UpdateEntityColor(etn.getEntity(), newColor.getRGB()));
		}

	}

}
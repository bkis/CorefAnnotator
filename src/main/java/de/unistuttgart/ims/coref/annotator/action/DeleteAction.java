package de.unistuttgart.ims.coref.annotator.action;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.Action;
import javax.swing.KeyStroke;
import javax.swing.tree.TreePath;

import org.apache.uima.cas.FeatureStructure;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.factory.Lists;
import org.kordamp.ikonli.materialdesign.MaterialDesign;

import de.unistuttgart.ims.coref.annotator.Annotator;
import de.unistuttgart.ims.coref.annotator.CATreeNode;
import de.unistuttgart.ims.coref.annotator.CATreeSelectionEvent;
import de.unistuttgart.ims.coref.annotator.Constants.Strings;
import de.unistuttgart.ims.coref.annotator.DocumentWindow;
import de.unistuttgart.ims.coref.annotator.api.v1.DetachedMentionPart;
import de.unistuttgart.ims.coref.annotator.api.v1.Entity;
import de.unistuttgart.ims.coref.annotator.api.v1.EntityGroup;
import de.unistuttgart.ims.coref.annotator.api.v1.Mention;
import de.unistuttgart.ims.coref.annotator.document.op.Operation;
import de.unistuttgart.ims.coref.annotator.document.op.RemoveEntities;
import de.unistuttgart.ims.coref.annotator.document.op.RemoveEntitiesFromEntityGroup;
import de.unistuttgart.ims.coref.annotator.document.op.RemoveMention;
import de.unistuttgart.ims.coref.annotator.document.op.RemovePart;

public class DeleteAction extends TargetedIkonAction<DocumentWindow> implements CAAction {

	private static final long serialVersionUID = 1L;

	public DeleteAction(DocumentWindow documentWindow) {
		super(documentWindow, Strings.ACTION_DELETE, MaterialDesign.MDI_DELETE);
		putValue(Action.SHORT_DESCRIPTION, Annotator.getString(Strings.ACTION_DELETE_TOOLTIP));
		putValue(Action.ACCELERATOR_KEY,
				KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));

	}

	@Override
	public void actionPerformed(ActionEvent e) {
		MutableList<TreePath> selection = Lists.mutable.of(getTarget().getTree().getSelectionPaths());
		CATreeNode node = (CATreeNode) getTarget().getTree().getSelectionPath().getLastPathComponent();
		FeatureStructure fs = node.getFeatureStructure();
		Operation operation = null;
		if (fs instanceof Entity) {
			FeatureStructure parentFs = node.getParent().getFeatureStructure();
			if (parentFs instanceof EntityGroup) {
				operation = new RemoveEntitiesFromEntityGroup((EntityGroup) parentFs, node.getEntity());
			} else if (node.isLeaf()) {
				operation = new RemoveEntities(getTarget().getSelectedEntities());
			}
		} else if (fs instanceof Mention) {
			operation = new RemoveMention(selection.collect(tp -> (CATreeNode) tp.getLastPathComponent())
					.collect(tn -> (Mention) tn.getFeatureStructure()));
		} else if (fs instanceof DetachedMentionPart) {
			operation = new RemovePart(((DetachedMentionPart) fs).getMention(), (DetachedMentionPart) fs);
		}

		if (operation != null)
			this.getTarget().getDocumentModel().edit(operation);
		else
			for (TreePath tp : getTarget().getTree().getSelectionPaths())
				deleteSingle((CATreeNode) tp.getLastPathComponent());
	}

	private void deleteSingle(CATreeNode tn) {
		Operation operation = null;
		if (tn.getFeatureStructure() instanceof Mention) {
			int row = getTarget().getTree().getLeadSelectionRow() - 1;
			getTarget().getDocumentModel().edit(new RemoveMention(tn.getFeatureStructure()));
			getTarget().getTree().setSelectionRow(row);
		} else if (tn.getFeatureStructure() instanceof EntityGroup) {
			getTarget().getDocumentModel().edit(new RemoveEntities(tn.getFeatureStructure()));
		} else if (tn.getFeatureStructure() instanceof DetachedMentionPart) {
			DetachedMentionPart dmp = (DetachedMentionPart) tn.getFeatureStructure();
			getTarget().getDocumentModel().edit(new RemovePart(dmp.getMention(), dmp));
		} else if (tn.isEntity()) {
			FeatureStructure parentFs = tn.getParent().getFeatureStructure();
			if (parentFs instanceof EntityGroup) {
				operation = new RemoveEntitiesFromEntityGroup((EntityGroup) parentFs, tn.getEntity());
			} else if (tn.isLeaf()) {
				getTarget().getDocumentModel().edit(new RemoveEntities(tn.getEntity()));
			}
		}
		if (operation != null)
			getTarget().getDocumentModel().edit(operation);
	}

	@Override
	public void setEnabled(CATreeSelectionEvent l) {
		setEnabled(l.isDetachedMentionPart() || l.isMention() || (l.isEntityGroup() && l.isLeaf())
				|| (l.isEntity() && l.isLeaf()));

	}

}
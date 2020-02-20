package de.unistuttgart.ims.coref.annotator.action;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.Action;
import javax.swing.KeyStroke;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;

import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.jcas.tcas.Annotation;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Sets;
import org.kordamp.ikonli.materialdesign.MaterialDesign;

import de.unistuttgart.ims.coref.annotator.Annotator;
import de.unistuttgart.ims.coref.annotator.CATreeNode;
import de.unistuttgart.ims.coref.annotator.DocumentWindow;
import de.unistuttgart.ims.coref.annotator.Strings;
import de.unistuttgart.ims.coref.annotator.TreeSelectionUtil;
import de.unistuttgart.ims.coref.annotator.api.v1.DetachedMentionPart;
import de.unistuttgart.ims.coref.annotator.api.v1.Entity;
import de.unistuttgart.ims.coref.annotator.api.v1.EntityGroup;
import de.unistuttgart.ims.coref.annotator.api.v1.Mention;
import de.unistuttgart.ims.coref.annotator.document.op.Operation;
import de.unistuttgart.ims.coref.annotator.document.op.RemoveEntities;
import de.unistuttgart.ims.coref.annotator.document.op.RemoveEntitiesFromEntityGroup;
import de.unistuttgart.ims.coref.annotator.document.op.RemoveMention;
import de.unistuttgart.ims.coref.annotator.document.op.RemovePart;

public class DeleteAction extends TargetedIkonAction<DocumentWindow> implements CaretListener, TreeSelectionListener {

	private static final long serialVersionUID = 1L;

	FeatureStructure featureStructure = null;
	boolean enabledByText;
	boolean enabledByTree;
	boolean disabledByModel;

	public DeleteAction(DocumentWindow documentWindow) {
		super(documentWindow, Strings.ACTION_DELETE, MaterialDesign.MDI_DELETE);
		putValue(Action.SHORT_DESCRIPTION, Annotator.getString(Strings.ACTION_DELETE_TOOLTIP));
		putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0));
	}

	public DeleteAction(DocumentWindow documentWindow, FeatureStructure featureStructure) {
		super(documentWindow, Strings.ACTION_DELETE, MaterialDesign.MDI_DELETE);
		putValue(Action.SHORT_DESCRIPTION, Annotator.getString(Strings.ACTION_DELETE_TOOLTIP));
		putValue(Action.ACCELERATOR_KEY,
				KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
		this.featureStructure = featureStructure;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		FeatureStructure fs = featureStructure;
		MutableList<Operation> operations = Lists.mutable.empty();
		if (fs == null) {
			if (e.getSource() == getTarget().getTextPane()) {
				int low = getTarget().getTextPane().getSelectionStart();
				int high = getTarget().getTextPane().getSelectionEnd();
				MutableSet<? extends Annotation> annotations = Sets.mutable
						.withAll(getTarget().getDocumentModel().getCoreferenceModel().getMentions(low));
				MutableSet<Mention> mentions = annotations.selectInstancesOf(Mention.class)
						.select(a -> a.getBegin() == low && a.getEnd() == high);

				MutableMap<Entity, MutableSet<Mention>> mentionsByEntity = mentions.aggregateBy(m -> m.getEntity(),
						() -> Sets.mutable.empty(), (set, mention) -> {
							set.add(mention);
							return set;
						});
				mentionsByEntity.forEachValue(s -> operations.add(new RemoveMention(s)));
			} else {
				MutableList<TreePath> selection = Lists.mutable.of(getTarget().getTree().getSelectionPaths());
				CATreeNode node = (CATreeNode) getTarget().getTree().getSelectionPath().getLastPathComponent();
				fs = node.getFeatureStructure();

				if (fs instanceof Entity) {
					FeatureStructure parentFs = node.getParent().getFeatureStructure();
					if (parentFs instanceof EntityGroup) {
						operations.add(new RemoveEntitiesFromEntityGroup((EntityGroup) parentFs, node.getEntity()));
					} else if (node.isLeaf()) {
						operations.add(new RemoveEntities(getTarget().getSelectedEntities()));
					}
				} else if (fs instanceof Mention) {
					operations.add(new RemoveMention(selection.collect(tp -> (CATreeNode) tp.getLastPathComponent())
							.collect(tn -> (Mention) tn.getFeatureStructure())));
				} else if (fs instanceof DetachedMentionPart) {
					operations.add(new RemovePart(((DetachedMentionPart) fs).getMention(), (DetachedMentionPart) fs));
				}
			}
		} else if (featureStructure instanceof Mention) {
			operations.add(new RemoveMention((Mention) featureStructure));
		} else if (featureStructure instanceof Entity) {
			operations.add(new RemoveEntities((Entity) featureStructure));
		} else if (featureStructure instanceof DetachedMentionPart) {
			DetachedMentionPart dmp = (DetachedMentionPart) featureStructure;
			operations.add(new RemovePart(dmp.getMention(), dmp));
		}
		if (!operations.isEmpty())
			operations.forEach(op -> getTarget().getDocumentModel().edit(op));

	}

	@Override
	public void caretUpdate(CaretEvent e) {
		enabledByText = e.getDot() != e.getMark();
		disabledByModel = false;
		setStatus();
	}

	@Override
	public void valueChanged(TreeSelectionEvent e) {
		TreeSelectionUtil tsu = new TreeSelectionUtil();
		tsu.collectData(e);
		enabledByTree = tsu.isDetachedMentionPart() || tsu.isMention() || (tsu.isEntityGroup() && tsu.isLeaf())
				|| (tsu.isEntity() && tsu.isLeaf());
		disabledByModel = tsu.getFeatureStructures().collect(fs -> getOperation(fs))
				.collect(op -> getTarget().getDocumentModel().isBlocked(op)).contains(true);
		setStatus();

	}

	protected void setStatus() {
		setEnabled(!disabledByModel && (enabledByText || enabledByTree));
	}

	protected Class<? extends Operation> getOperation(FeatureStructure fs) {
		if (fs instanceof Mention)
			return RemoveMention.class;
		else if (fs instanceof Entity)
			return RemoveEntities.class;
		else if (fs instanceof DetachedMentionPart)
			return RemovePart.class;
		return null;
	}

}
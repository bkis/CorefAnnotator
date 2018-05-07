package de.unistuttgart.ims.coref.annotator.inspector;

import java.awt.BorderLayout;

import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListModel;

import de.unistuttgart.ims.coref.annotator.DocumentWindow;
import de.unistuttgart.ims.coref.annotator.comp.PanelList;

public class Inspector extends JDialog {

	private static final long serialVersionUID = 1L;

	PanelList<Issue, JPanel> issueList;

	public Inspector(DocumentWindow dw) {
		super(dw);

		DefaultListModel<Issue> listModel = new DefaultListModel<Issue>();
		issueList = new PanelList<Issue, JPanel>(new IssuePanelFactory());

		listModel.addListDataListener(issueList);
		Checker checker = new Checker(dw.getDocumentModel(), this, listModel);
		checker.execute();

		add(new JScrollPane(issueList, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
				JScrollPane.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);

		setModalityType(ModalityType.MODELESS);

	}

	public void setListModel(ListModel<Issue> listModel) {
		listModel.addListDataListener(issueList);

	}

}
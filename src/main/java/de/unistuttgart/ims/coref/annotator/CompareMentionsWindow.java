package de.unistuttgart.ims.coref.annotator;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Consumer;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.SpringLayout;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.text.StyleContext;

import org.apache.uima.UIMAException;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.multimap.set.MutableSetMultimap;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Maps;
import org.eclipse.collections.impl.factory.Multimaps;
import org.eclipse.collections.impl.factory.Sets;
import org.kordamp.ikonli.materialdesign.MaterialDesign;
import org.kordamp.ikonli.swing.FontIcon;

import com.google.common.base.Objects;

import de.unistuttgart.ims.coref.annotator.action.CloseAction;
import de.unistuttgart.ims.coref.annotator.action.CopyAction;
import de.unistuttgart.ims.coref.annotator.action.FileImportAction;
import de.unistuttgart.ims.coref.annotator.action.FileSelectOpenAction;
import de.unistuttgart.ims.coref.annotator.action.HelpAction;
import de.unistuttgart.ims.coref.annotator.action.SelectedFileOpenAction;
import de.unistuttgart.ims.coref.annotator.api.v2.Entity;
import de.unistuttgart.ims.coref.annotator.api.v2.Flag;
import de.unistuttgart.ims.coref.annotator.api.v2.Mention;
import de.unistuttgart.ims.coref.annotator.api.v2.MentionSurface;
import de.unistuttgart.ims.coref.annotator.comp.BoundLabel;
import de.unistuttgart.ims.coref.annotator.comp.ColorIcon;
import de.unistuttgart.ims.coref.annotator.comp.EntityLabel;
import de.unistuttgart.ims.coref.annotator.comp.SpringUtilities;
import de.unistuttgart.ims.coref.annotator.document.CoreferenceModelListener;
import de.unistuttgart.ims.coref.annotator.document.DocumentModel;
import de.unistuttgart.ims.coref.annotator.document.Event;
import de.unistuttgart.ims.coref.annotator.document.FeatureStructureEvent;
import de.unistuttgart.ims.coref.annotator.document.op.AddMentionsToNewEntity;
import de.unistuttgart.ims.coref.annotator.plugins.ImportPlugin;
import de.unistuttgart.ims.coref.annotator.plugins.StylePlugin;
import de.unistuttgart.ims.coref.annotator.profile.Profile;
import de.unistuttgart.ims.coref.annotator.uima.UimaUtil;
import de.unistuttgart.ims.coref.annotator.worker.DocumentModelLoader;

public class CompareMentionsWindow extends AbstractTextWindow
		implements HasTextView, CoreferenceModelListener, PreferenceChangeListener {
	public class TextCaretListener implements CaretListener {

		@Override
		public void caretUpdate(CaretEvent e) {
			if (textPane.getSelectionStart() != textPane.getSelectionEnd()) {
				Span span = new Span(textPane.getSelectionStart(), textPane.getSelectionEnd());
				stats.setAgreementInSpan(getAgreementInSpan(span));
			}
		}
	}

	class CopyMentionAction extends AbstractAction {

		private static final long serialVersionUID = 1L;

		Span span;

		public CopyMentionAction(Span span) {
			this.span = span;
			putValue(Action.NAME, "add");
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			targetModel.edit(new AddMentionsToNewEntity(span));
		}

	}

	class AnnotatorStatistics {
		int mentions = 0;
		int entities = 0;
		int entityGroups = 0;
		int lastMention = 0;
		int length = 0;

		public void analyze(JCas jcas, Consumer<Mention> cons) {
			length = jcas.getDocumentText().length();
			for (Mention m : JCasUtil.select(jcas, Mention.class)) {
				mentions++;
				if (UimaUtil.getEnd(m) > lastMention)
					lastMention = UimaUtil.getEnd(m);
				cons.accept(m);
			}
			for (Entity e : JCasUtil.select(jcas, Entity.class)) {
				entities++;
				if (UimaUtil.isGroup(e))
					entityGroups++;
			}
		}
	}

	class TextMouseListener implements MouseListener {

		protected JMenu getMentionMenu(Mention mention) {
			JMenu menu = new JMenu(mention.getEntity().getLabel());
			// menu.add(new DeleteAction(null, mention));
			// menu.add(new CopyMentionAction(new Span(mention)));
			return menu;
		}

		@Override
		public void mouseClicked(MouseEvent e) {
			if (SwingUtilities.isRightMouseButton(e)) {
				JPopupMenu pMenu = new JPopupMenu();
				int offset = textPane.viewToModel2D(e.getPoint());

				ImmutableSet<Mention> intersectMentions = intersectModel.getIntersection(documentModels.getFirst());
				intersectMentions = intersectMentions
						.select(m -> UimaUtil.getBegin(m) <= offset && offset <= UimaUtil.getEnd(m));

				if (!intersectMentions.isEmpty()) {
					pMenu.add(Annotator.getString(Strings.COMPARE_CONTEXTMENU_INTERSECTION));
					for (Mention m : intersectMentions) {
						pMenu.add(new MentionPanel(m, null));
					}
				}

				for (int i = 0; i < documentModels.size(); i++) {
					DocumentModel dm = documentModels.get(i);

					RichIterable<Mention> mentions = intersectModel.spanMentionMap.get(dm)
							.reject((s, m) -> intersectModel.getSpanIntersection().contains(s))
							.select((s, m) -> UimaUtil.getBegin(m) <= offset && offset <= UimaUtil.getEnd(m))
							.valuesView();
					if (!mentions.isEmpty()) {
						if (!intersectMentions.isEmpty())
							pMenu.addSeparator();
						pMenu.add(files.get(i).getName());
						for (Mention m : mentions) {
							pMenu.add(new MentionPanel(m, dm));
						}
					}

				}

				pMenu.show(e.getComponent(), e.getX(), e.getY());

			}
		}

		@Override
		public void mouseEntered(MouseEvent e) {
		}

		@Override
		public void mouseExited(MouseEvent e) {
		}

		@Override
		public void mousePressed(MouseEvent e) {

		}

		@Override
		public void mouseReleased(MouseEvent e) {
		}
	}

	private static final long serialVersionUID = 1L;
	MutableList<String> annotatorIds;
	MutableList<Action> openActions;

	AbstractAction copyAction;

	MutableList<JCas> jcas;
	MutableList<File> files;
	int numberOfLoadedJCas = 0;
	int numberOfLoadedDocumentModels = 0;
	Annotator mainApplication;
	JPanel rSidebar;
	JLabel modeDescription;
	JPanel agreementPanel = null;

	MutableList<AnnotatorStatistics> annotatorStats;
	MutableList<MutableSetMultimap<Entity, Mention>> entityMentionMaps;

	AgreementStatistics stats = new AgreementStatistics();

	StyleContext styleContext = new StyleContext();

	JCas targetJCas;

	DocumentModel targetModel;

	boolean textIsSet = false;
	int size = 0;
	Color[] colors;
	JMenu fileMenu;
	IntersectModel intersectModel;
	Mode mode;
	protected SpringLayout rSidebarlayout;

	enum Mode {
		Span, Span_EntityName
	}

	public CompareMentionsWindow(Annotator mainApplication, int size) throws UIMAException {
		this.mainApplication = mainApplication;
		this.jcas = Lists.mutable.withNValues(size, () -> null);
		this.files = Lists.mutable.withNValues(size, () -> null);
		this.annotatorIds = Lists.mutable.withNValues(size, () -> null);
		this.openActions = Lists.mutable.withNValues(size, () -> null);
		this.annotatorStats = Lists.mutable.withNValues(size, () -> null);
		this.documentModels = Lists.mutable.withNValues(size, () -> null);
		this.entityMentionMaps = Lists.mutable.withNValues(size, () -> Multimaps.mutable.set.empty());
		this.colors = new Color[size];
		ColorProvider cp = new ColorProvider();
		for (int i = 0; i < colors.length; i++) {
			this.colors[i] = cp.getNextColor();
		}
		this.mode = (Annotator.app.getPreferences().getBoolean(Constants.CFG_COMPARE_BY_ENTITY_NAME,
				Defaults.CFG_COMPARE_BY_ENTITY_NAME) ? Mode.Span_EntityName : Mode.Span);
		this.size = size;
		this.initialiseMenu();
		this.initializeWindow();
		this.targetJCas = JCasFactory.createJCas();
	}

	protected void drawAll(JCas jcas, Color color) {
		for (Mention m : JCasUtil.select(jcas, Mention.class)) {
			highlightManager.underline(m, color);

		}

	}

	protected void drawAllAnnotations() {
		if (numberOfLoadedDocumentModels < size)
			return;
		intersectModel = new IntersectModel();
		intersectModel.documentModels = documentModels.toImmutable();
		intersectModel.calculateIntersection();

		Span overlapping = intersectModel.getOverlappingPart();

		ImmutableSet<Spans> intersection = intersectModel.getSpanIntersection();

		for (Mention m : intersectModel.getIntersection(documentModels.getFirst())) {
			highlightManager.underline(m, Color.gray.brighter());
		}
		int agreed = intersection.size();
		int total = agreed;
		int totalInOverlappingPart = agreed;
		int index = 0;
		for (DocumentModel dm : documentModels) {
			Set<Spans> spans = intersectModel.spanMentionMap.get(dm).keySet();
			for (Spans s : spans) {
				if (!intersection.contains(s)) {
					if (mode == Mode.Span_EntityName)
						highlightManager.underline(intersectModel.spanMentionMap.get(dm).get(s));
					else
						highlightManager.underline(intersectModel.spanMentionMap.get(dm).get(s), colors[index]);
					total++;
					if (overlapping.contains(s))
						totalInOverlappingPart++;
				}
			}
			index++;
		}

		stats.setTotal(total);
		stats.setAgreed(agreed);
		stats.setTotalInOverlappingPart(totalInOverlappingPart);

	}

	protected void ensureSameTexts() throws NotComparableException {
		MutableList<JCas> ll = jcas.select(j -> j != null);
		for (int i = 1; i < ll.size(); i++) {
			if (!ll.get(i).getDocumentText().equals(ll.get(i - 1).getDocumentText()))
				throw new NotComparableException(Annotator.getString(Strings.COMPARE_NOT_COMPARABLE));
		}
	}

	protected double getAgreementInSpan(Span s) {
		MutableList<MutableSet<Span>> mapList = Lists.mutable.empty();

		int total = 0;
		int index = 0;
		for (JCas jcas : jcas) {
			MutableSet<Span> map1 = Sets.mutable.empty();

			Annotation sel = new Annotation(jcas);
			sel.setBegin(s.begin);
			sel.setEnd(s.end);

			;
			for (Mention m : Lists.immutable.withAll(JCasUtil.selectCovered(MentionSurface.class, sel))
					.collect(ms -> ms.getMention())) {
				if (Annotator.app.getPreferences().getBoolean(Constants.CFG_IGNORE_SINGLETONS_WHEN_COMPARING,
						Defaults.CFG_IGNORE_SINGLETONS_WHEN_COMPARING)
						&& entityMentionMaps.get(index).get(m.getEntity()).size() <= 1)
					continue;

				// TODO: this currently only checks the first mention
				map1.add(new Span(UimaUtil.getFirst(m)));
				total++;
			}
			mapList.add(map1);
			index++;
		}

		MutableSet<Span> intersection = Sets.mutable.withAll(mapList.getFirst());
		for (int i = 1; i < mapList.size(); i++) {
			intersection = intersection.intersect(mapList.get(i));
		}
		int agreed = intersection.size();

		total = total - ((jcas.size() - 1) * intersection.size());

		return 100 * agreed / (double) total;

	}

	protected JPanel getAgreementPanel() {
		if (agreementPanel == null) {
			JPanel panel = new JPanel();
			SpringLayout layout = new SpringLayout();
			panel.setLayout(layout);

			Border border = BorderFactory.createTitledBorder(Annotator.getString(Strings.STAT_AGR_TITLE));
			panel.setBorder(border);

			JLabel desc;
			desc = new JLabel(Annotator.getString(Strings.STAT_KEY_TOTAL) + ":", SwingConstants.RIGHT);
			desc.setToolTipText(Annotator.getString(Strings.STAT_KEY_TOTAL_TOOLTIP));
			panel.add(desc);
			JLabel valueLabel = new BoundLabel(stats, "total", o -> o.toString(), stats.total());

			panel.add(valueLabel);

			desc = new JLabel(Annotator.getString(Strings.STAT_KEY_AGREED) + ":", SwingConstants.RIGHT);
			desc.setToolTipText(Annotator.getString(Strings.STAT_KEY_AGREED_TOOLTIP));
			panel.add(desc);
			panel.add(new BoundLabel(stats, "agreed", o -> String.format("%1$,3d", o), stats.getAgreed()));

			desc = new JLabel(Annotator.getString(Strings.STAT_KEY_AGREED_OVERALL) + ":", SwingConstants.RIGHT);
			desc.setToolTipText(Annotator.getString(Strings.STAT_KEY_AGREED_OVERALL_TOOLTIP));
			panel.add(desc);
			JLabel percTotalLabel = new JLabel(String.format("%1$3.1f%%", 100 * stats.agreed / (double) stats.total),
					SwingConstants.RIGHT);
			panel.add(percTotalLabel);

			desc = new JLabel(Annotator.getString(Strings.STAT_KEY_AGREED_PARALLEL) + ":", SwingConstants.RIGHT);
			desc.setToolTipText(Annotator.getString(Strings.STAT_KEY_AGREED_PARALLEL_TOOLTIP));
			panel.add(desc);
			JLabel percOvrLabel = new JLabel(
					String.format("%1$3.1f%%", 100 * stats.agreed / (double) stats.totalInOverlappingPart),
					SwingConstants.RIGHT);
			panel.add(percOvrLabel);

			desc = new JLabel(Annotator.getString(Strings.STAT_KEY_AGREED_SELECTED) + ":", SwingConstants.RIGHT);
			desc.setToolTipText(Annotator.getString(Strings.STAT_KEY_AGREED_SELECTED_TOOLTIP));
			panel.add(desc);
			JLabel selectedAgreementLabel = new BoundLabel(stats, "agreementInSpan",
					o -> String.format("%1$3.1f%%", o));
			selectedAgreementLabel.setHorizontalAlignment(SwingConstants.RIGHT);
			panel.add(selectedAgreementLabel);

			stats.addPropertyChangeListener(new PropertyChangeListener() {

				@Override
				public void propertyChange(PropertyChangeEvent evt) {

					if (evt.getPropertyName().equals("total") || evt.getPropertyName().equals("agreed")) {
						percTotalLabel.setText(
								String.format("%1$3.1f%%", 100 * stats.getAgreed() / (double) stats.getTotal()));
					}
					if (evt.getPropertyName().equals("totalInOverlappingPart")
							|| evt.getPropertyName().equals("agreed")) {
						percOvrLabel.setText(String.format("%1$3.1f%%",
								100 * stats.getAgreed() / (double) stats.getTotalInOverlappingPart()));
					}
				}

			});

			HelpAction ha = new HelpAction(HelpWindow.Topic.COMPARE);
			ha.putValue(Action.NAME, Annotator.getString(Strings.COMPARE_SHOW_AGREEMENT_HELP));
			JButton helpButton = new JButton(ha);
			panel.add(helpButton);

			// empty label in the bottom right
			panel.add(new JLabel());

			SpringUtilities.makeCompactGrid(panel, 6, 2, // rows, cols
					5, 5, // initialX, initialY
					15, 3);// xPad, yPad

			agreementPanel = panel;

		}
		return agreementPanel;
	}

	protected JPanel getAnnotatorPanel(int index) {

		AnnotatorStatistics stats = annotatorStats.get(index);

		JPanel panel = new JPanel();
		SpringLayout layout = new SpringLayout();
		panel.setLayout(layout);
		Border border = BorderFactory.createTitledBorder(annotatorIds.get(index));
		panel.setBorder(border);
		// panel.setPreferredSize(new Dimension(200, 100));
		// panel.setMinimumSize(new Dimension(200, 100));
		JLabel desc;

		// color
		desc = new JLabel(Annotator.getString(Strings.STAT_KEY_COLOR) + ":", SwingConstants.RIGHT);
		desc.setToolTipText(Annotator.getString(Strings.STAT_KEY_COLOR_TOOLTIP));
		panel.add(desc);
		panel.add(new JLabel(new ColorIcon(30, 10, colors[index]), SwingConstants.RIGHT));

		// number of mentions
		desc = new JLabel(Annotator.getString(Strings.STAT_KEY_MENTIONS) + ":", SwingConstants.RIGHT);
		desc.setToolTipText(Annotator.getString(Strings.STAT_KEY_MENTIONS_TOOLTIP));
		panel.add(desc);
		panel.add(new JLabel(String.valueOf(stats.mentions), SwingConstants.RIGHT));

		// number of entities
		desc = new JLabel(Annotator.getString(Strings.STAT_KEY_ENTITIES) + ":", SwingConstants.RIGHT);
		desc.setToolTipText(Annotator.getString(Strings.STAT_KEY_ENTITIES_TOOLTIP));
		panel.add(desc);
		panel.add(new JLabel(String.valueOf(stats.entities), SwingConstants.RIGHT));

		// annotation position
		desc = new JLabel(Annotator.getString(Strings.STAT_KEY_POSITION) + ":", SwingConstants.RIGHT);
		desc.setToolTipText(Annotator.getString(Strings.STAT_KEY_POSITION_TOOLTIP));
		panel.add(desc);
		panel.add(new JLabel(
				String.format("%1$,3d (%2$3.1f%%)", stats.lastMention, 100 * stats.lastMention / (double) stats.length),
				SwingConstants.RIGHT));

		panel.add(new JLabel(Annotator.getString(Strings.ACTION_OPEN) + ":", SwingConstants.RIGHT));
		panel.add(new JButton(openActions.get(index)));

		// layout
		SpringUtilities.makeCompactGrid(panel, 5, 2, 5, 5, 15, 3);

		return panel;
	}

	@Override
	public Span getSelection() {
		return new Span(textPane.getSelectionStart(), textPane.getSelectionEnd());
	}

	@Override
	public String getText() {
		return textPane.getText();
	}

	protected void initialiseActions() {
		copyAction = new CopyAction(this);
	};

	protected void initialiseMenu() {

		JMenu helpMenu = new JMenu(Annotator.getString(Strings.MENU_HELP));
		helpMenu.add(mainApplication.helpAction);

		menuBar.add(initialiseMenuFile());
		menuBar.add(initialiseMenuView());
		menuBar.add(initialiseMenuSettings());

		menuBar.add(helpMenu);

		setJMenuBar(menuBar);

		Annotator.logger.info("Initialised menus");
	}

	protected synchronized void initialiseText(JCas jcas2) {
		if (textIsSet)
			return;
		textPane.setText(jcas2.getDocumentText().replaceAll("\r", " "));
		textPane.setCaretPosition(0);
		textIsSet = true;

		StyleManager.styleCharacter(textPane.getStyledDocument(), StyleManager.getDefaultCharacterStyle());
		StyleManager.styleParagraph(textPane.getStyledDocument(), StyleManager.getDefaultParagraphStyle());
	}

	@Override
	protected void initializeWindow() {

		super.initializeWindow();
		Caret caret = new Caret();

		textPane.setPreferredSize(new Dimension(500, 800));
		textPane.setCaret(caret);
		textPane.getCaret().setVisible(true);
		textPane.addFocusListener(caret);
		textPane.setCaretPosition(0);
		// mentionsTextPane.addMouseListener(new TextMouseListener());
		textPane.getInputMap().put(
				KeyStroke.getKeyStroke(KeyEvent.VK_C, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()),
				copyAction);
		textPane.addCaretListener(new TextCaretListener());
		textPane.addMouseListener(new TextMouseListener());

		modeDescription = new JLabel();
		switch (mode) {
		case Span_EntityName:
			modeDescription.setText(Annotator.getString(Strings.COMPARE_MODE_DESCRIPTION_SPAN_ENTITYNAME));
			break;
		default:
			modeDescription.setText(Annotator.getString(Strings.COMPARE_MODE_DESCRIPTION_SPAN));
			break;
		}
		rSidebarlayout = new SpringLayout();

		rSidebar = new JPanel();
		rSidebar.setLayout(rSidebarlayout);
		rSidebar.setPreferredSize(new Dimension(210, 750));
		rSidebar.setMaximumSize(new Dimension(250, 750));
		rSidebar.add(modeDescription);

		rSidebarlayout.putConstraint(SpringLayout.NORTH, modeDescription, 5, SpringLayout.NORTH, rSidebar);
		rSidebarlayout.putConstraint(SpringLayout.WEST, modeDescription, 5, SpringLayout.WEST, rSidebar);
		rSidebarlayout.putConstraint(SpringLayout.EAST, modeDescription, -5, SpringLayout.EAST, rSidebar);

		JSplitPane mentionsPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, textPanel, rSidebar);
		mentionsPane.setDividerLocation(500);

		// tabbedPane.add("Mentions", mentionsPane);
		// add(tabbedPane, BorderLayout.CENTER);
		add(mentionsPane, BorderLayout.CENTER);
		highlightManager = new HighlightManager(textPane);
		setPreferredSize(new Dimension(800, 800));
		pack();
	}

	protected void finishLoading() {
		if (numberOfLoadedDocumentModels >= files.size()) {
			// Style
			StylePlugin sPlugin = null;
			try {
				sPlugin = Annotator.app.getPluginManager().getStylePlugin(getDocumentModel().getStylePlugin());
			} catch (ClassNotFoundException e1) {
				Annotator.logger.catching(e1);
			}

			if (sPlugin == null)
				sPlugin = Annotator.app.getPluginManager().getDefaultStylePlugin();

			StyleManager.styleParagraph(textPane.getStyledDocument(), StyleManager.getDefaultParagraphStyle());
			switchStyle(sPlugin);

			// show profile, if needed
			if (getDocumentModel().getProfile() != null)
				if (getDocumentModel().getProfile().getName() != null)
					miscLabel2.setText(Annotator.getString(Strings.STATUS_PROFILE) + ": "
							+ getDocumentModel().getProfile().getName());
				else
					miscLabel2.setText(Annotator.getString(Strings.STATUS_PROFILE) + ": " + "Unknown");
			miscLabel2.repaint();

			stopIndeterminateProgress();

		}
	}

	public void setDocumentModel(DocumentModel cm, int index) {
		documentModels.set(index, cm);
		if (tableOfContents != null)
			tableOfContents.setModel(cm.getSegmentModel());
		numberOfLoadedDocumentModels++;
		finishLoading();
		drawAllAnnotations();

		getAgreementPanel();
		rSidebar.add(agreementPanel);
		rSidebarlayout.putConstraint(SpringLayout.SOUTH, agreementPanel, -5, SpringLayout.SOUTH, rSidebar);
		rSidebarlayout.putConstraint(SpringLayout.WEST, agreementPanel, 5, SpringLayout.WEST, rSidebar);
		rSidebarlayout.putConstraint(SpringLayout.EAST, agreementPanel, -5, SpringLayout.EAST, rSidebar);
		// rSidebarlayout.putConstraint(SpringLayout.NORTH, agreementPanel, -50,
		// SpringLayout.SOUTH,
		// rSidebar.getComponent(rSidebar.getComponentCount() - 1));
		rSidebar.validate();
	}

	public void setJCas(JCas jcas, String annotatorId, int index) throws NotComparableException {
		setJCas(jcas, annotatorId, index, null);
	}

	public void setJCas(JCas jcas, String annotatorId, int index, Profile profile) throws NotComparableException {
		this.jcas.set(index, jcas);
		this.ensureSameTexts();
		this.annotatorIds.set(index, annotatorId);
		this.annotatorStats.set(index, new AnnotatorStatistics());
		this.annotatorStats.get(index).analyze(jcas, m -> {
			entityMentionMaps.get(index).put(m.getEntity(), m);
		});
		numberOfLoadedJCas++;
		if (!textIsSet)
			initialiseText(jcas);
		JPanel annotatorPanel = getAnnotatorPanel(index);
		Component previousComponent = rSidebar.getComponent(rSidebar.getComponentCount() - 1);
		rSidebar.add(annotatorPanel);
		rSidebarlayout.putConstraint(SpringLayout.NORTH, annotatorPanel, 5, SpringLayout.SOUTH, previousComponent);
		rSidebarlayout.putConstraint(SpringLayout.EAST, annotatorPanel, -5, SpringLayout.EAST, rSidebar);
		rSidebarlayout.putConstraint(SpringLayout.WEST, annotatorPanel, 5, SpringLayout.WEST, rSidebar);

		DocumentModelLoader dml = new DocumentModelLoader(cm -> setDocumentModel(cm, index), jcas);
		dml.setProfile(profile);
		dml.execute();
		revalidate();
	}

	@Override
	protected void entityEventMove(FeatureStructureEvent event) {

	}

	@Override
	protected void entityEventMerge(FeatureStructureEvent event) {

	}

	@Override
	protected void entityEventOp(FeatureStructureEvent event) {

	}

	protected JMenu initialiseMenuFile() {
		JMenu fileImportMenu = new JMenu(Annotator.getString(Strings.MENU_FILE_IMPORT_FROM));

		PluginManager pm = mainApplication.getPluginManager();
		for (ImportPlugin plugin : pm.getIOPluginObjects().selectInstancesOf(ImportPlugin.class)) {
			fileImportMenu.add(new FileImportAction(mainApplication, plugin));

		}

		fileMenu = new JMenu(Annotator.getString(Strings.MENU_FILE));
		fileMenu.add(new FileSelectOpenAction(mainApplication));
		fileMenu.add(mainApplication.getRecentFilesMenu());
		fileMenu.add(fileImportMenu);
		fileMenu.add(new CloseAction());
		fileMenu.add(mainApplication.quitAction);

		return fileMenu;
	}

	public void setFile(File file, int index) {
		this.files.set(index, file);
		this.openActions.set(index, new SelectedFileOpenAction(Annotator.app, file));

	}

	public void setFiles(Iterable<File> files) {
		this.files = Lists.mutable.withAll(files);
		this.openActions = this.files.collect(f -> new SelectedFileOpenAction(Annotator.app, f));
		JMenu currentFilesMenu = new JMenu(Annotator.getString(Strings.ACTION_OPEN));
		this.openActions.forEach(a -> currentFilesMenu.add(a));
		fileMenu.add(currentFilesMenu, 1);
	}

	@Override
	public void preferenceChange(PreferenceChangeEvent evt) {
		if (evt.getKey() == Constants.CFG_IGNORE_SINGLETONS_WHEN_COMPARING) {
			highlightManager.hilit.removeAllHighlights();
			drawAllAnnotations();
		} else if (evt.getKey() == Constants.CFG_COMPARE_BY_ENTITY_NAME) {
			this.mode = (Annotator.app.getPreferences().getBoolean(Constants.CFG_COMPARE_BY_ENTITY_NAME,
					Defaults.CFG_COMPARE_BY_ENTITY_NAME) ? Mode.Span_EntityName : Mode.Span);

			switch (mode) {
			case Span_EntityName:
				modeDescription.setText(Annotator.getString(Strings.COMPARE_MODE_DESCRIPTION_SPAN_ENTITYNAME));
				break;
			default:
				modeDescription.setText(Annotator.getString(Strings.COMPARE_MODE_DESCRIPTION_SPAN));
				break;
			}

			entityMentionMaps = Lists.mutable.withNValues(size, () -> Multimaps.mutable.set.empty());
			highlightManager.hilit.removeAllHighlights();
			drawAllAnnotations();

		} else
			super.preferenceChange(evt);
	}

	static class ExtendedSpan extends Spans {

		public String entityLabel;

		public ExtendedSpan(Mention annotation) {
			super(annotation);
			this.entityLabel = annotation.getEntity().getLabel();
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(super.spans, this.entityLabel);
		}

		@Override
		public boolean equals(Object obj) {
			if (!this.getClass().equals(obj.getClass())) {
				return false;
			}
			ExtendedSpan that = (ExtendedSpan) obj;
			return this.spans.equals(that.spans) && this.entityLabel.contentEquals(that.entityLabel);
		}
	}

	static class MentionPanel extends JPanel implements PreferenceChangeListener, CoreferenceModelListener {

		private static final long serialVersionUID = 1L;

		Boolean showText = null;

		Entity entity;
		Mention mention;
		DocumentModel documentModel;

		boolean includeEntityFlags = false;
		boolean includeMentionFlags = true;

		public MentionPanel(Mention mention, DocumentModel documentModel) {
			this.mention = mention;
			this.entity = mention.getEntity();
			this.documentModel = documentModel;

			this.setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
			this.setAlignmentX(LEFT_ALIGNMENT);
			this.setOpaque(false);
			// this.setBorder(BorderFactory.createLineBorder(Color.MAGENTA));
			this.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
			if (documentModel != null)
				this.setToolTipText(documentModel.getCoreferenceModel().getToolTipText(entity));

			initialize();

		}

		protected void initialize() {
			Annotator.logger.traceEntry();
			JLabel mainLabel = new EntityLabel(entity);

			add(mainLabel);
			if (includeEntityFlags)
				if (entity.getFlags() != null && documentModel != null)
					for (Flag flag : entity.getFlags()) {
						addFlag(this, flag, Color.BLACK);
					}
			if (includeMentionFlags)
				if (mention.getFlags() != null && documentModel != null)
					for (Flag flag : mention.getFlags()) {
						addFlag(this, flag, Color.BLACK);
					}
		}

		protected void addFlag(JPanel panel, Flag flag, Color color) {
			JLabel l = new JLabel();
			if (color != null)
				l.setForeground(color);
			if (isShowText())
				l.setText(Annotator.getStringWithDefault(flag.getLabel(), flag.getLabel()));
			l.setToolTipText(Annotator.getStringWithDefault(flag.getLabel(), flag.getLabel()));
			l.setIcon(FontIcon.of(MaterialDesign.valueOf(flag.getIcon()), color));
			panel.add(Box.createRigidArea(new Dimension(5, 5)));
			panel.add(l);
		}

		public Boolean getShowText() {
			return showText;
		}

		protected boolean isShowText() {
			if (showText == null)
				return Annotator.app.getPreferences().getBoolean(Constants.CFG_SHOW_TEXT_LABELS,
						Defaults.CFG_SHOW_TEXT_LABELS);
			return showText;
		}

		@Override
		public void preferenceChange(PreferenceChangeEvent evt) {
			removeAll();
			initialize();
			revalidate();
		}

		public void setShowText(Boolean showText) {
			this.showText = showText;
		}

		@Override
		public void entityEvent(FeatureStructureEvent event) {
			Annotator.logger.traceEntry();
			if (event.getType() == Event.Type.Update) {
				if (event.getArguments().contains(entity)) {
					removeAll();
					initialize();
					revalidate();
				}
			}

		}
	}

	public class IntersectModel {
		ImmutableList<DocumentModel> documentModels;
		MutableSet<Spans> spanIntersection = null;
		MutableMap<DocumentModel, MutableMap<Spans, Mention>> spanMentionMap = Maps.mutable.empty();
		MutableSetMultimap<Span, Mention> spanAllMentionsMap = Multimaps.mutable.set.empty();
		Span annotatedRange = new Span(Integer.MAX_VALUE, Integer.MIN_VALUE);
		Span overlappingPart = new Span(Integer.MIN_VALUE, Integer.MAX_VALUE);

		public void calculateIntersection() {
			for (DocumentModel dm : documentModels) {
				spanMentionMap.put(dm, Maps.mutable.empty());
				JCas jcas = dm.getJcas();
				MutableSet<Spans> spans = Sets.mutable.empty();

				for (Mention m : JCasUtil.select(jcas, Mention.class)) {
					if (Annotator.app.getPreferences().getBoolean(Constants.CFG_IGNORE_SINGLETONS_WHEN_COMPARING,
							Defaults.CFG_IGNORE_SINGLETONS_WHEN_COMPARING)
							&& dm.getCoreferenceModel().getSingletons().contains(m.getEntity()))
						continue;
					Spans span;
					if (mode == Mode.Span_EntityName)
						span = new ExtendedSpan(m);
					else
						span = new Spans(m);
					for (Span sp : span)
						spanAllMentionsMap.put(sp, m);

					spanMentionMap.get(dm).put(span, m);
					spans.add(span);

					if (UimaUtil.getEnd(m) > annotatedRange.end)
						annotatedRange.end = UimaUtil.getEnd(m);
					if (UimaUtil.getBegin(m) < annotatedRange.begin)
						annotatedRange.begin = UimaUtil.getBegin(m);

				}
				if (spanIntersection == null)
					spanIntersection = Sets.mutable.withAll(spans);
				else
					spanIntersection = spanIntersection.intersect(spans);
				if (overlappingPart.begin < annotatedRange.begin)
					overlappingPart.begin = annotatedRange.begin;
				if (overlappingPart.end > annotatedRange.end)
					overlappingPart.end = annotatedRange.end;

			}

		}

		public ImmutableSet<Mention> getIntersection(DocumentModel dm) {
			return spanIntersection.collect(s -> spanMentionMap.get(dm).get(s)).toImmutable();
		}

		public ImmutableSet<Spans> getSpanIntersection() {
			return spanIntersection.toImmutable();
		}

		public Span getAnnotatedRange() {
			return annotatedRange;
		}

		public Span getOverlappingPart() {
			return overlappingPart;
		}

	}

	public static class NotComparableException extends Exception {

		public NotComparableException(String string) {
			super(string);
		}

		private static final long serialVersionUID = 1L;

	}

	public MentionSurface getNextMentionSurface(int position) {
		try {
			return documentModels.collect(dm -> dm.getCoreferenceModel())
					.collect(cm -> cm.getNextMentionSurface(position)).reject(ms -> ms == null)
					.minBy(m -> m.getBegin());
		} catch (NoSuchElementException e) {
			return null;
		}
	}

	public MentionSurface getPreviousMentionSurface(int position) {
		try {
			return documentModels.collect(dm -> dm.getCoreferenceModel())
					.collect(cm -> cm.getPreviousMentionSurface(position)).reject(ms -> ms == null)
					.maxBy(m -> m.getEnd());
		} catch (NoSuchElementException e) {
			return null;
		}
	}

}

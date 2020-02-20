package de.unistuttgart.ims.coref.annotator;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Consumer;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.uima.fit.factory.TypeSystemDescriptionFactory;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Sets;
import org.kordamp.ikonli.materialdesign.MaterialDesign;
import org.kordamp.ikonli.swing.FontIcon;

import com.ibm.icu.text.MessageFormat;

import de.unistuttgart.ims.coref.annotator.UpdateCheck.Version;
import de.unistuttgart.ims.coref.annotator.action.ExitAction;
import de.unistuttgart.ims.coref.annotator.action.FileCompareOpenAction;
import de.unistuttgart.ims.coref.annotator.action.FileImportAction;
import de.unistuttgart.ims.coref.annotator.action.FileMergeOpenAction;
import de.unistuttgart.ims.coref.annotator.action.FileSelectOpenAction;
import de.unistuttgart.ims.coref.annotator.action.HelpAction;
import de.unistuttgart.ims.coref.annotator.action.SelectedFileOpenAction;
import de.unistuttgart.ims.coref.annotator.action.ShowLogWindowAction;
import de.unistuttgart.ims.coref.annotator.plugins.ConfigurableImportPlugin;
import de.unistuttgart.ims.coref.annotator.plugins.DefaultImportPlugin;
import de.unistuttgart.ims.coref.annotator.plugins.ImportPlugin;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;

public class Annotator {

	public static final Logger logger = LogManager.getLogger(Annotator.class);

	static ResourceBundle rbundle;

	Set<DocumentWindow> openFiles = Sets.mutable.empty();

	public MutableList<File> recentFiles;

	TypeSystemDescription typeSystemDescription;

	PluginManager pluginManager = new PluginManager();

	protected JFrame opening;
	JPanel statusBar;
	JPanel recentFilesPanel;

	LogWindow logWindow = null;
	UpdateCheck updateCheck = new UpdateCheck();

	AbstractAction openAction, quitAction = new ExitAction(), helpAction = new HelpAction();
	AbstractAction openCompareAction;

	Preferences preferences = Preferences.userNodeForPackage(Annotator.class);

	public static Annotator app;

	static Boolean javafx = null;

	public static void main(String[] args) {
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				try {
					try {
						UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
					} catch (Exception e) {
						Annotator.logger.error("Could not set look and feel {}.", e.getMessage());
					}

					app = new Annotator();
					app.showOpening();
				} catch (ResourceInitializationException e) {
					logger.catching(e);
				}
			}

		});
	}

	@SuppressWarnings("unused")
	public Annotator() throws ResourceInitializationException {
		logger.trace("Application startup. Version " + Version.get().toString());
		if (Annotator.javafx())
			new JFXPanel();
		this.pluginManager.init();
		this.recentFiles = loadRecentFiles();

		try {
			if (!preferences.nodeExists(Constants.CFG_ANNOTATOR_ID))
				if (System.getProperty("user.name") != null)
					preferences.put(Constants.CFG_ANNOTATOR_ID, System.getProperty("user.name"));
				else
					preferences.put(Constants.CFG_ANNOTATOR_ID, Defaults.CFG_ANNOTATOR_ID);

		} catch (BackingStoreException e) {
			Annotator.logger.catching(e);
		}

		this.initialiseActions();
		this.initialiseTypeSystem();
		this.initialiseDialogs();

	}

	protected void initialiseDialogs() {

		opening = getOpeningDialog();
	}

	protected void initialiseActions() {
		openAction = new FileSelectOpenAction(this);
		openCompareAction = new FileCompareOpenAction();
	}

	public static String getAppName() {
		// return "CorefAnnotator";
		return Annotator.class.getPackage().getImplementationTitle();
	}

	protected JFrame getOpeningDialog() {
		int width = 300;
		JFrame opening = new JFrame();
		opening.setLocationByPlatform(true);
		opening.setTitle(Annotator.class.getPackage().getImplementationTitle());
		opening.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				opening.dispose();
				handleQuitRequestWith();
			}
		});

		JPanel mainPanel = new JPanel();
		mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

		JPanel panel = new JPanel();
		panel.setBorder(BorderFactory.createTitledBorder(Annotator.getString("dialog.splash.default")));
		panel.setPreferredSize(new Dimension(width, 130));
		panel.add(new JButton(openAction));
		panel.add(new JButton(quitAction));
		panel.add(new JButton(helpAction));
		panel.add(new JButton(new ShowLogWindowAction(this)));
		panel.add(new JButton(openCompareAction));
		panel.add(new JButton(new FileMergeOpenAction()));
		mainPanel.add(panel);

		mainPanel.add(Box.createVerticalStrut(10));
		recentFilesPanel = new JPanel();
		recentFilesPanel.setBorder(BorderFactory.createTitledBorder(Annotator.getString("dialog.splash.recent")));
		recentFilesPanel.setPreferredSize(new Dimension(width, 200));
		refreshRecents();
		mainPanel.add(recentFilesPanel);
		mainPanel.add(Box.createVerticalStrut(10));

		panel = new JPanel();
		panel.setBorder(BorderFactory.createTitledBorder(Annotator.getString("dialog.splash.import")));
		panel.setPreferredSize(new Dimension(width, 200));
		pluginManager.getIOPluginObjects().selectInstancesOf(ImportPlugin.class).forEachWith((p, pan) -> {
			AbstractAction importAction = new FileImportAction(this, p);
			pan.add(new JButton(importAction));
		}, panel);

		mainPanel.add(panel);

		for (Component c : mainPanel.getComponents())
			((JComponent) c).setAlignmentX(Component.CENTER_ALIGNMENT);

		JLabel versionLabel = new JLabel(Version.get().toString());

		statusBar = new JPanel();

		try {
			if (updateCheck.checkForUpdate()) {
				JButton button = new JButton();
				button.setText(Annotator.getString(Strings.STATUS_NOW_AVAILABLE) + ": "
						+ updateCheck.getRemoteVersion().toString());
				button.setIcon(FontIcon.of(MaterialDesign.MDI_NEW_BOX));
				button.addActionListener(new ActionListener() {

					@Override
					public void actionPerformed(ActionEvent e) {
						try {
							Desktop.getDesktop().browse(updateCheck.getReleasePage());
						} catch (IOException e1) {
							logger.catching(e1);
						}
					}

				});
				statusBar.add(button);
			} else {
				statusBar.add(versionLabel);
			}
		} catch (IOException e1) {
			logger.catching(e1);
			statusBar.add(versionLabel);
		}

		opening.getContentPane().add(mainPanel, BorderLayout.CENTER);
		opening.getContentPane().add(statusBar, BorderLayout.SOUTH);
		opening.pack();
		return opening;
	}

	protected void initialiseTypeSystem() throws ResourceInitializationException {
		typeSystemDescription = TypeSystemDescriptionFactory.createTypeSystemDescription();
	}

	public synchronized DocumentWindow open(final File file, ImportPlugin flavor, String language) {
		logger.trace("Creating new DocumentWindow");
		DocumentWindow v = new DocumentWindow();

		if (flavor instanceof ConfigurableImportPlugin)
			((ConfigurableImportPlugin) flavor).showImportConfigurationDialog(v, fl -> {
				v.loadFile(file, flavor, language);

				SwingUtilities.invokeLater(new Runnable() {

					@Override
					public void run() {
						openFiles.add(v);
						if (flavor instanceof DefaultImportPlugin)
							recentFiles.add(0, file);
						v.initialise();

					}
				});
			});
		else {
			v.loadFile(file, flavor, language);

			SwingUtilities.invokeLater(new Runnable() {

				@Override
				public void run() {
					openFiles.add(v);
					if (flavor instanceof DefaultImportPlugin)
						recentFiles.add(0, file);
					v.initialise();

				}

			});
		}
		return null;

	}

	public void close(DocumentWindow viewer) {
		openFiles.remove(viewer);
		viewer.dispose();
		if (openFiles.isEmpty())
			this.showOpening();
	};

	public void handleQuitRequestWith() {
		for (DocumentWindow v : openFiles)
			this.close(v);
		storeRecentFiles();
		try {
			preferences.sync();
		} catch (BackingStoreException e1) {
			logger.catching(e1);
		}
		System.exit(0);
	}

	public void warnDialog(String message, String title) {
		JOptionPane.showMessageDialog(null, message, title, JOptionPane.WARNING_MESSAGE);
	}

	public void showOpening() {
		this.opening.setVisible(true);
	}

	public void fileOpenDialog(Component parent, ImportPlugin flavor, boolean multi, Consumer<File[]> okCallback,
			Consumer<Object> cancelCallback, String title) {
		if (Annotator.javafx()) {
			Platform.runLater(new Runnable() {
				@Override
				public void run() {
					javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
					fileChooser.setTitle(title);
					fileChooser.setInitialDirectory(getCurrentDirectory());
					// fileChooser.getExtensionFilters().clear();
					fileChooser.getExtensionFilters().add(flavor.getExtensionFilter());
					File[] result;
					if (multi) {
						result = fileChooser.showOpenMultipleDialog(null).toArray(new File[] {});
						if (result != null) {
							setCurrentDirectory(result[0].getParentFile());
							okCallback.accept(result);
							return;
						}
					} else {
						result = new File[1];
						result[0] = fileChooser.showOpenDialog(null);
						if (result[0] != null) {
							setCurrentDirectory(result[0].getParentFile());
							okCallback.accept(result);
							return;
						}
					}
					cancelCallback.accept(null);
				}
			});
		} else {
			JFileChooser openDialog;
			openDialog = new JFileChooser();
			openDialog.setMultiSelectionEnabled(multi);
			openDialog.setFileFilter(FileFilters.xmi_gz);
			openDialog.setDialogTitle(title);
			openDialog.setFileFilter(flavor.getFileFilter());
			openDialog.setCurrentDirectory(getCurrentDirectory());
			int r = openDialog.showOpenDialog(parent);
			switch (r) {
			case JFileChooser.APPROVE_OPTION:
				File[] selectedFiles;
				if (multi)
					selectedFiles = openDialog.getSelectedFiles();
				else {
					selectedFiles = new File[1];
					selectedFiles[0] = openDialog.getSelectedFile();
				}
				setCurrentDirectory(selectedFiles[0].getParentFile());
				okCallback.accept(selectedFiles);
				break;
			default:
				cancelCallback.accept(null);
			}
		}
	}

	public void fileOpenDialog(Component parent, ImportPlugin flavor) {
		fileOpenDialog(parent, flavor, false, f -> open(f[0], flavor, Constants.X_UNSPECIFIED), o -> showOpening(),
				"Open files using " + flavor.getName() + " scheme");
	}

	public static String getString(String key, Object... parameters) {
		try {
			return getString(key, Locale.getDefault(), parameters);
		} catch (java.util.MissingResourceException e) {
			logger.catching(e);
			return key;
		}
	}

	public static String getString(String key, Locale locale) {
		if (rbundle == null)
			rbundle = ResourceBundle.getBundle("locales/strings", locale);
		return rbundle.getString(key);
	}

	public static String getString(String key, Locale locale, Object... parameters) {
		if (rbundle == null)
			rbundle = ResourceBundle.getBundle("locales/strings", locale);
		if (parameters.length > 0) {
			String s = rbundle.getString(key);
			return MessageFormat.format(s, parameters);
		}
		return rbundle.getString(key);
	}

	public static String getStringWithDefault(String key, String defaultValue) {
		try {
			return getString(key, Locale.getDefault());
		} catch (java.util.MissingResourceException e) {
			return defaultValue;
		}
	}

	public PluginManager getPluginManager() {
		return pluginManager;
	}

	private MutableList<File> loadRecentFiles() {
		MutableList<File> files = Lists.mutable.empty();
		String listOfFiles = preferences.get(Constants.PREF_RECENT, "");
		logger.debug(listOfFiles);
		String[] fileNames = listOfFiles.split(File.pathSeparator);
		for (String fileRef : fileNames) {
			File file = new File(fileRef);
			if (file.exists() && !files.contains(file)) {
				files.add(file);
			}

		}
		return files;
	}

	private void storeRecentFiles() {
		StringBuilder sb = new StringBuilder();
		for (int index = 0; index < recentFiles.size(); index++) {
			File file = recentFiles.get(index);
			if (sb.length() > 0) {
				sb.append(File.pathSeparator);
			}
			sb.append(file.getPath());
		}
		preferences.put(Constants.PREF_RECENT, sb.toString());
	}

	public JMenu getRecentFilesMenu() {
		JMenu m = new JMenu(Annotator.getString("menu.file.recent"));
		for (int i = 0; i < Math.min(20, recentFiles.size()); i++)
			m.add(new SelectedFileOpenAction(this, recentFiles.get(i)));
		return m;

	}

	public void refreshRecents() {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				recentFilesPanel.removeAll();
				for (int i = 0; i < Math.min(recentFiles.size(), 10); i++) {
					File f = recentFiles.get(i);
					recentFilesPanel.add(new JButton(new SelectedFileOpenAction(Annotator.this, f)));
				}
			}
		});
	}

	public Preferences getPreferences() {
		return preferences;
	}

	public LogWindow getLogWindow() {
		if (logWindow == null)
			logWindow = new LogWindow();
		return logWindow;
	}

	public File getCurrentDirectory() {
		File f = new File(preferences.get(Constants.CFG_CURRENT_DIRECTORY, System.getProperty("user.home")));
		if (!f.isDirectory())
			f = new File(System.getProperty("user.home"));
		return f;
	}

	public void setCurrentDirectory(File f) {
		preferences.put(Constants.CFG_CURRENT_DIRECTORY, f.getAbsolutePath());
		try {
			preferences.sync();
		} catch (BackingStoreException e1) {
			logger.catching(e1);
		}
	}

	@SuppressWarnings("unused")
	public static boolean javafx() {
		if (javafx == null)
			try {
				Class.forName("javafx.embed.swing.JFXPanel");
				new JFXPanel();
				javafx = true;
			} catch (Exception e) {
				javafx = false;
			}
		return javafx;
	}
}

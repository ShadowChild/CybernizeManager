/*
 * Created by JFormDesigner on Mon Jun 22 16:06:01 BST 2020
 */

package uk.co.innoxium.candor.window;

import com.formdev.flatlaf.FlatIconColors;
import com.github.f4b6a3.uuid.util.UuidConverter;
import net.miginfocom.swing.MigLayout;
import uk.co.innoxium.candor.game.Game;
import uk.co.innoxium.candor.game.GamesList;
import uk.co.innoxium.candor.mod.Mod;
import uk.co.innoxium.candor.mod.ModList;
import uk.co.innoxium.candor.mod.store.ModStore;
import uk.co.innoxium.candor.module.AbstractModule;
import uk.co.innoxium.candor.module.ModuleSelector;
import uk.co.innoxium.candor.module.RunConfig;
import uk.co.innoxium.candor.thread.ThreadModInstaller;
import uk.co.innoxium.candor.util.NativeDialogs;
import uk.co.innoxium.candor.util.Logger;
import uk.co.innoxium.candor.util.Resources;
import uk.co.innoxium.candor.util.WindowUtils;
import uk.co.innoxium.swing.util.DesktopUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;


public class ModScene extends JPanel {

    private final LinkedList<ThreadModInstaller> queuedMods = new LinkedList<>();

    public ModScene(String gameUuid) {

        // Set game stuff
        Game game = GamesList.getGameFromUUID(UuidConverter.fromString(gameUuid));
        assert game != null;
        AbstractModule module = ModuleSelector.getModuleForGame(game);
        module.setGame(new File(game.getGameExe()));
        module.setModsFolder(new File(game.getModsFolder()));
        ModStore.initialise();

        try {

            WindowUtils.mainFrame.setMinimumSize(new Dimension(1200, 768));
            ModStore.determineInstalledMods();
        } catch (IOException e) {

            Logger.info("This shouldn't happen, likely a corrupt mods.json :(");
            e.printStackTrace();
            System.exit(-1);
        }
        initComponents();
        ModStore.MODS.addListener(new ModList.ListChangeListener<Mod>() {

            @Override
            public void handleChange(String identifier, Mod mod, boolean result) {

                handleChange(identifier, Collections.singletonList(mod), result);
            }

            @Override
            public void handleChange(String identifier, Collection<? extends Mod> c, boolean result) {

                installedModsJList.setListData(ModStore.MODS.toArray());
                c.forEach(mod -> {

                    if(identifier.equals("install")) {

                        queuedMods.removeFirst();
                        if(queuedMods.size() > 0) {

                            queuedMods.getFirst().start();
                        }
                    }
                });
            }
        });
    }

    private void createUIComponents() {

        installedModsJList = new JList(ModStore.MODS.toArray());

        installedModsJList.setCellRenderer(new ListRenderer());
        installedModsJList.setFont(Resources.fantasque.deriveFont(24f));
        installedModsJList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        installedModsJList.setSelectionModel(new DefaultListSelectionModel() {

            private static final long serialVersionUID = 1L;

            boolean gestureStarted = false;

            @Override
            public void setSelectionInterval(int index0, int index1) {

                if(!gestureStarted) {

                    if (index0 == index1) {

                        if (isSelectedIndex(index0)) {

                            removeSelectionInterval(index0, index0);
                            return;
                        }
                    }
                    super.setSelectionInterval(index0, index1);
                }
                gestureStarted = true;
            }

            @Override
            public void addSelectionInterval(int index0, int index1) {

                if (index0==index1) {

                    if (isSelectedIndex(index0)) {

                        removeSelectionInterval(index0, index0);
                        return;
                    }
                    super.addSelectionInterval(index0, index1);
                }
            }

            @Override
            public void setValueIsAdjusting(boolean isAdjusting) {

                if (!isAdjusting) {

                    gestureStarted = false;
                }
            }
        });
        installedModsJList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {

                if(SwingUtilities.isLeftMouseButton(e) && ((JList<?>)e.getSource()).getModel().getSize() != 0) {

                    JList<?> list = (JList<?>) e.getSource();
                    int index = list.locationToIndex(e.getPoint());
                    Mod mod = (Mod) list.getModel().getElementAt(index);
//                    ((ListRenderer) list.getCellRenderer()).selected = !((ListRenderer) list.getCellRenderer()).selected;
                    list.repaint(list.getCellBounds(index, index));
                }
                if(SwingUtilities.isRightMouseButton(e) && ((JList<?>)e.getSource()).getModel().getSize() != 0) {

                    JList<?> list = (JList<?>) e.getSource();
                    int index = list.locationToIndex(e.getPoint());
                    list.setSelectedIndex(index);
                    JPopupMenu menu = new JPopupMenu("Options");
                    JMenuItem renameOption = new JMenuItem("Rename Mod");
                    renameOption.addActionListener(event -> {

                        Mod mod = (Mod) list.getModel().getElementAt(index);
                        System.out.println("Rename clicked on mod: " + mod.getName());
                        String newName = (String)JOptionPane.showInputDialog(WindowUtils.mainFrame,
                                "Please input the new name for the Mod " + mod.getReadableName(),
                                "Rename Mod",
                                JOptionPane.QUESTION_MESSAGE,
                                null,
                                null,
                                mod.getReadableName());
                        mod.setReadableName(newName);
                        ModStore.updateModState(mod, mod.getState());
                        ModStore.MODS.fireChangeToListeners("rename", mod, true);
                    });
                    menu.add(renameOption);
                    menu.show(list, e.getPoint().x, e.getPoint().y);
                }
            }
        });
    }

    private void settingsClicked(ActionEvent e) {

        // Disable while we don't have enough settings
//        JDialog dialog = new SettingsFrame();
//        dialog.setAlwaysOnTop(true);
//        dialog.setLocationRelativeTo(this);
//        dialog.setVisible(true);
    }

    private void addModClicked(ActionEvent e) {

        NativeDialogs.openMultiFileDialog(ModuleSelector.currentModule.getModFileFilterList()).forEach(file -> {

            ModStore.Result result = ModStore.addModFile(file);

            switch(result) {

                case DUPLICATE -> NativeDialogs.showErrorMessage("Mod is a Duplicate and already installed.\nIf updating, please uninstall old file first.");
                case FAIL -> NativeDialogs.showErrorMessage(String.format("Mod file %s could not be added.\nPlease try again.", file.getName()));
                // Fallthrough on default
                default -> {}
            }
        });
    }

    private void removeModsSelected(ActionEvent e) {

        if(NativeDialogs.showConfirmDialog("Remove Selected Mods")) {

            if(installedModsJList.getSelectedValuesList().isEmpty()) {
                NativeDialogs.showInfoDialog(
                        "Candor Mod Manager",
                        "You have not selected any mods to remove.",
                        "ok",
                        "warning",
                        false);
            }
            installedModsJList.getSelectedValuesList().forEach(o -> {

                try {

                    ModStore.removeModFile((Mod) o, true);
                } catch (IOException exception) {

                    exception.printStackTrace();
                }
            });
        }
    }

    private void installModsClicked(ActionEvent e) {

        doInstallMod((ArrayList<Mod>)installedModsJList.getSelectedValuesList());
    }

    private void runGameClicked(ActionEvent e) {
        
        RunConfig runConfig = ModuleSelector.currentModule.getDefaultRunConfig();
        
        ProcessBuilder builder = new ProcessBuilder();
        builder.command(runConfig.getStartCommand() + " " + runConfig.getProgramArgs());
        String workingDir = runConfig.getWorkingDir();
        if(workingDir != null && !workingDir.isEmpty()) {

            builder.directory(new File(workingDir));
        }
        try {

            Logger.info(builder.command().toString());
            Process process = builder.start();
        } catch (IOException ioException) {

            ioException.printStackTrace();
        }
    }


    // TODO: Add support for modules to determine how to toggle mods, e.g. via a plugin list for GameBryo games
    private void toggleSelectedMods(ActionEvent e) {

        if(NativeDialogs.showConfirmDialog("Toggle Selected Mods")) {

            if(installedModsJList.getSelectedValuesList().isEmpty()) {

                NativeDialogs.showInfoDialog(
                        "Candor Mod Manager",
                        "You have not selected any mods to toggle.",
                        "ok",
                        "warning",
                        false);
            }
            ArrayList<Mod> toInstall = new ArrayList<>();
            installedModsJList.getSelectedValuesList().forEach(o -> {

                Mod mod = (Mod)o;

                if(mod.getState() == Mod.State.ENABLED && ModuleSelector.currentModule.getModInstaller().uninstall(mod)) {

                    mod.setState(Mod.State.DISABLED);
                    ModStore.updateModState(mod, Mod.State.DISABLED);
                } else {

                    toInstall.add(mod);
                }
                if(toInstall.size() > 0) doInstallMod(toInstall);
//                try {
//
//                    Mod mod = (Mod)o;
//                    ModStore.removeModFile((Mod) o, false);
//                } catch (IOException exception) {
//
//                    exception.printStackTrace();
//                }
            });
        }
    }

    public void doInstallMod(ArrayList<Mod> mods) {

        // Any mods in here will enable the message box saying it was installed
        ArrayList<Mod> badMods = new ArrayList<>();

        // Instantiate a Thread for each mod selected
        mods.forEach(mod -> {

            if(mod.getState() == Mod.State.ENABLED) {

                badMods.add(mod);
            } else {

                ThreadModInstaller thread = new ThreadModInstaller(mod);

                queuedMods.add(thread);
            }
        });

        if(badMods.size() > 0) {

            StringBuilder builder = new StringBuilder();
            builder.append("The following mods are already installed and will be ignored: \n");
            for (Mod badMod : badMods) {

                builder.append(badMod.getReadableName()).append("\n");
            }

            NativeDialogs.showInfoDialog(
                    "Candor Mod Manager",
                    builder.toString(),
                    "ok",
                    "info",
                    true);
        }

        // install the mods one by one
        queuedMods.getFirst().start();
    }

    private void newGameClicked(ActionEvent e) {

        WindowUtils.setupEntryScene();
//        WindowUtils.setupGameSelectScene();
    }

    private void aboutClicked(ActionEvent e) {

        AboutDialog dialog = new AboutDialog(WindowUtils.mainFrame);
        dialog.setResizable(true);
        dialog.pack();
        dialog.setVisible(true);
    }

    private void openFolder(ActionEvent e) {

        switch(e.getActionCommand()) {

            case "game" -> DesktopUtil.openURL("", ModuleSelector.currentModule.getGame().getParent());
            case "mods" -> DesktopUtil.openURL("", ModuleSelector.currentModule.getModsFolder().getAbsolutePath());
            case "candor" -> DesktopUtil.openURL("", Resources.CANDOR_DATA_PATH.getAbsolutePath());
            default -> {}
        }
    }

    private void initComponents() {
        // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents
        createUIComponents();

        managerPanel = new JPanel();
        managerPaneMenu = new JPanel();
        gameLabel = new JLabel();
        addModButton = new JButton();
        removeModsButton = new JButton();
        installModsButton = new JButton();
        toggleButton = new JButton();
        listScrollPane = new JScrollPane();
        menuBar = new JMenuBar();
        fileMenu = new JMenu();
        applyModsMenuItem = new JMenuItem();
        loadNewGameMenuItem = new JMenuItem();
        settingsMenuItem = new JMenuItem();
        gameMenu = new JMenu();
        openGameFolderMenuItem = new JMenuItem();
        opemModsFolderMenuItem = new JMenuItem();
        launchGameMenuItem = new JMenuItem();
        runConfigsMenuItem = new JMenuItem();
        aboutMenu = new JMenu();
        aboutMenuItem = new JMenuItem();
        candorSettingButton = new JMenuItem();

        //======== this ========
        setLayout(new BorderLayout());

        //======== managerPanel ========
        {
            managerPanel.setLayout(new MigLayout(
                "fill,insets panel,hidemode 3",
                // columns
                "[fill]",
                // rows
                "[]" +
                "[]"));

            //======== managerPaneMenu ========
            {
                managerPaneMenu.setLayout(new FlowLayout(FlowLayout.LEFT));

                //---- gameLabel ----
                gameLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
                gameLabel.setText(ModuleSelector.currentModule.getReadableGameName().toUpperCase());
                managerPaneMenu.add(gameLabel);

                //---- addModButton ----
                addModButton.setText("Add Mod(s)");
                addModButton.setIcon(UIManager.getIcon("Tree.leafIcon"));
                addModButton.addActionListener(e -> addModClicked(e));
                managerPaneMenu.add(addModButton);

                //---- removeModsButton ----
                removeModsButton.setText("Remove Selected");
                removeModsButton.setIcon(null);
                removeModsButton.addActionListener(e -> removeModsSelected(e));
                managerPaneMenu.add(removeModsButton);

                //---- installModsButton ----
                installModsButton.setText("Install Selected Mod(s)");
                installModsButton.setIcon(UIManager.getIcon("FileView.floppyDriveIcon"));
                installModsButton.addActionListener(e -> installModsClicked(e));
                managerPaneMenu.add(installModsButton);

                //---- toggleButton ----
                toggleButton.setText("Toggle Enabled");
                toggleButton.addActionListener(e -> toggleSelectedMods(e));
                managerPaneMenu.add(toggleButton);
            }
            managerPanel.add(managerPaneMenu, "cell 0 0");

            //======== listScrollPane ========
            {
                listScrollPane.setViewportView(installedModsJList);
            }
            managerPanel.add(listScrollPane, "cell 0 1,dock center");
        }
        add(managerPanel, BorderLayout.CENTER);

        //======== menuBar ========
        {

            //======== fileMenu ========
            {
                fileMenu.setText("File");
                fileMenu.setMnemonic('F');

                //---- applyModsMenuItem ----
                applyModsMenuItem.setText("Apply Mod(s)");
                applyModsMenuItem.setMnemonic('A');
                fileMenu.add(applyModsMenuItem);

                //---- loadNewGameMenuItem ----
                loadNewGameMenuItem.setText("Load New Game");
                loadNewGameMenuItem.setMnemonic('L');
                loadNewGameMenuItem.addActionListener(e -> newGameClicked(e));
                fileMenu.add(loadNewGameMenuItem);

                //---- settingsMenuItem ----
                settingsMenuItem.setText("Settings");
                settingsMenuItem.setMnemonic('S');
                settingsMenuItem.addActionListener(e -> settingsClicked(e));
                fileMenu.add(settingsMenuItem);
            }
            menuBar.add(fileMenu);

            //======== gameMenu ========
            {
                gameMenu.setText("Game");
                gameMenu.setMnemonic('G');

                //---- openGameFolderMenuItem ----
                openGameFolderMenuItem.setText("Open Game Folder");
                openGameFolderMenuItem.setActionCommand("game");
                openGameFolderMenuItem.addActionListener(e -> openFolder(e));
                gameMenu.add(openGameFolderMenuItem);

                //---- opemModsFolderMenuItem ----
                opemModsFolderMenuItem.setText("Open Mods Folder");
                opemModsFolderMenuItem.setActionCommand("mods");
                opemModsFolderMenuItem.addActionListener(e -> openFolder(e));
                gameMenu.add(opemModsFolderMenuItem);

                //---- launchGameMenuItem ----
                launchGameMenuItem.setText("Launch Game");
                launchGameMenuItem.addActionListener(e -> runGameClicked(e));
                gameMenu.add(launchGameMenuItem);
                gameMenu.addSeparator();

                //---- runConfigsMenuItem ----
                runConfigsMenuItem.setText("Custom Run Config(s)");
                gameMenu.add(runConfigsMenuItem);
            }
            menuBar.add(gameMenu);

            //======== aboutMenu ========
            {
                aboutMenu.setText("About");

                //---- aboutMenuItem ----
                aboutMenuItem.setText("About Candor");
                aboutMenuItem.addActionListener(e -> aboutClicked(e));
                aboutMenu.add(aboutMenuItem);

                //---- candorSettingButton ----
                candorSettingButton.setText("Open Candor Folder");
                candorSettingButton.setActionCommand("candor");
                candorSettingButton.addActionListener(e -> openFolder(e));
                aboutMenu.add(candorSettingButton);
            }
            menuBar.add(aboutMenu);
        }
        add(menuBar, BorderLayout.NORTH);
        // JFormDesigner - End of component initialization  //GEN-END:initComponents
    }

    // JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables
    private JPanel managerPanel;
    private JPanel managerPaneMenu;
    private JLabel gameLabel;
    private JButton addModButton;
    private JButton removeModsButton;
    private JButton installModsButton;
    private JButton toggleButton;
    private JScrollPane listScrollPane;
    private JList installedModsJList;
    private JMenuBar menuBar;
    private JMenu fileMenu;
    private JMenuItem applyModsMenuItem;
    private JMenuItem loadNewGameMenuItem;
    private JMenuItem settingsMenuItem;
    private JMenu gameMenu;
    private JMenuItem openGameFolderMenuItem;
    private JMenuItem opemModsFolderMenuItem;
    private JMenuItem launchGameMenuItem;
    private JMenuItem runConfigsMenuItem;
    private JMenu aboutMenu;
    private JMenuItem aboutMenuItem;
    private JMenuItem candorSettingButton;
    // JFormDesigner - End of variables declaration  //GEN-END:variables

    static class ListRenderer extends JCheckBox implements ListCellRenderer<Mod> {

//        public boolean selected = false;

        @Override
        public Component getListCellRendererComponent(JList<? extends Mod> list, Mod value, int index, boolean isSelected, boolean cellHasFocus) {

            this.setEnabled(value.getState() == Mod.State.ENABLED);
            this.setSelected(value.getState() == Mod.State.ENABLED);
            this.setFont(list.getFont());
            this.setBackground(list.getBackground());
            this.setForeground(list.getForeground());

            if(isSelected) {

                this.setBackground(Color.decode(String.valueOf(FlatIconColors.OBJECTS_GREY.rgb)));
            }
            this.setText(value.getReadableName() + " [" + value.getState().name() + "]");

            return this;
        }
    }
}

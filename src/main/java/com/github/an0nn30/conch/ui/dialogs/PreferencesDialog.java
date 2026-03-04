package com.github.an0nn30.conch.ui.dialogs;

import com.github.an0nn30.conch.config.ConfigManager;
import com.github.an0nn30.conch.model.AppConfig;
import com.github.an0nn30.conch.terminal.FontUtil;
import com.github.an0nn30.conch.theme.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class PreferencesDialog extends JDialog {

    private final ConfigManager configManager;
    private final ThemeManager  themeManager;

    // Terminal page widget references (read on Apply)
    private JComboBox<String> fontCombo;
    private JSpinner          fontSizeSpinner;
    private JButton           bgColorBtn;
    private JButton           fgColorBtn;
    private Color             bgColor;
    private Color             fgColor;

    public PreferencesDialog(Window owner, ConfigManager configManager, ThemeManager themeManager) {
        super(owner, "Preferences", ModalityType.MODELESS);
        this.configManager = configManager;
        this.themeManager  = themeManager;
        setSize(680, 460);
        setMinimumSize(new Dimension(540, 380));
        setLocationRelativeTo(owner);
        buildUI();
    }

    private void buildUI() {
        // --- Sidebar --------------------------------------------------------
        DefaultListModel<String> model = new DefaultListModel<>();
        model.addElement("Appearance");
        model.addElement("Terminal");

        JList<String> sidebar = new JList<>(model);
        sidebar.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sidebar.setSelectedIndex(0);
        sidebar.setFixedCellWidth(160);
        sidebar.setBorder(new EmptyBorder(8, 8, 8, 8));
        sidebar.setFont(sidebar.getFont().deriveFont(13f));

        // --- Content area ---------------------------------------------------
        CardLayout cards = new CardLayout();
        JPanel content = new JPanel(cards);
        content.add(buildAppearancePage(), "Appearance");
        content.add(buildTerminalPage(),   "Terminal");

        sidebar.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String sel = sidebar.getSelectedValue();
                if (sel != null) cards.show(content, sel);
            }
        });

        // --- Layout ---------------------------------------------------------
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(sidebar), content);
        split.setDividerLocation(180);
        split.setDividerSize(1);
        split.setEnabled(false); // fixed width sidebar

        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        footer.add(close);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(split,  BorderLayout.CENTER);
        getContentPane().add(footer, BorderLayout.SOUTH);
    }

    // -----------------------------------------------------------------------
    // Appearance page
    // -----------------------------------------------------------------------

    private JPanel buildAppearancePage() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(24, 24, 24, 24));

        JLabel heading = new JLabel("Appearance");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 16f));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(heading);
        p.add(Box.createVerticalStrut(16));

        JLabel lbl = new JLabel("Theme:");
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(lbl);
        p.add(Box.createVerticalStrut(8));

        String current = configManager.getConfig().getTheme();
        ButtonGroup group = new ButtonGroup();

        // FlatLaf themes
        JLabel flatLabel = new JLabel("FlatLaf");
        flatLabel.setFont(flatLabel.getFont().deriveFont(Font.BOLD, 11f));
        flatLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(flatLabel);
        p.add(Box.createVerticalStrut(4));

        String[][] themes = {
                { ThemeManager.DARK,     "Dark" },
                { ThemeManager.DARCULA,  "Darcula" },
                { ThemeManager.INTELLIJ, "IntelliJ Light" },
                { ThemeManager.LIGHT,    "Light" },
        };
        for (String[] t : themes) {
            JRadioButton rb = new JRadioButton(t[1], t[0].equals(current));
            rb.setAlignmentX(Component.LEFT_ALIGNMENT);
            rb.addActionListener(e -> themeManager.changeTheme(t[0]));
            group.add(rb);
            p.add(rb);
            p.add(Box.createVerticalStrut(4));
        }

        // System / built-in LAFs
        p.add(Box.createVerticalStrut(8));
        JLabel sysLabel = new JLabel("System");
        sysLabel.setFont(sysLabel.getFont().deriveFont(Font.BOLD, 11f));
        sysLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(sysLabel);
        p.add(Box.createVerticalStrut(4));

        for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
            String className = info.getClassName();
            JRadioButton rb = new JRadioButton(info.getName(), className.equals(current));
            rb.setAlignmentX(Component.LEFT_ALIGNMENT);
            rb.addActionListener(e -> themeManager.changeTheme(className));
            group.add(rb);
            p.add(rb);
            p.add(Box.createVerticalStrut(4));
        }

        p.add(Box.createVerticalGlue());
        return p;
    }

    // -----------------------------------------------------------------------
    // Terminal page
    // -----------------------------------------------------------------------

    private JPanel buildTerminalPage() {
        AppConfig cfg = configManager.getConfig();

        JPanel outer = new JPanel(new BorderLayout());
        outer.setBorder(new EmptyBorder(24, 24, 16, 24));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.anchor = GridBagConstraints.WEST;
        int row = 0;

        // Heading
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 2;
        gc.insets = new Insets(0, 0, 16, 0);
        JLabel heading = new JLabel("Terminal");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 16f));
        form.add(heading, gc);
        gc.gridwidth = 1;
        row++;

        // Font family
        gc.gridx = 0; gc.gridy = row;
        gc.insets = new Insets(5, 0, 5, 12);
        gc.fill = GridBagConstraints.NONE; gc.weightx = 0;
        form.add(new JLabel("Font:"), gc);
        gc.gridx = 1;
        gc.insets = new Insets(5, 0, 5, 0);
        gc.fill = GridBagConstraints.HORIZONTAL; gc.weightx = 1.0;
        fontCombo = new JComboBox<>(FontUtil.availableMonoFonts(cfg.getFontName()));
        if (cfg.getFontName() != null && !cfg.getFontName().isBlank()) {
            fontCombo.setSelectedItem(cfg.getFontName());
        }
        form.add(fontCombo, gc);
        row++;

        // Font size
        gc.gridx = 0; gc.gridy = row;
        gc.insets = new Insets(5, 0, 5, 12);
        gc.fill = GridBagConstraints.NONE; gc.weightx = 0;
        form.add(new JLabel("Size:"), gc);
        gc.gridx = 1;
        gc.insets = new Insets(5, 0, 5, 0);
        gc.fill = GridBagConstraints.NONE;
        fontSizeSpinner = new JSpinner(new SpinnerNumberModel(cfg.getFontSize(), 8, 36, 1));
        fontSizeSpinner.setPreferredSize(new Dimension(80, fontSizeSpinner.getPreferredSize().height));
        form.add(fontSizeSpinner, gc);
        row++;

        // Background color
        bgColor = parseColor(cfg.getTerminalBackground(), Color.decode("#1E1E2E"));
        gc.gridx = 0; gc.gridy = row;
        gc.insets = new Insets(5, 0, 5, 12);
        gc.fill = GridBagConstraints.NONE; gc.weightx = 0;
        form.add(new JLabel("Background:"), gc);
        gc.gridx = 1;
        gc.insets = new Insets(5, 0, 5, 0);
        bgColorBtn = colorButton(bgColor);
        bgColorBtn.addActionListener(e -> {
            Color c = JColorChooser.showDialog(this, "Terminal Background", bgColor);
            if (c != null) { bgColor = c; bgColorBtn.setBackground(c); }
        });
        form.add(bgColorBtn, gc);
        row++;

        // Foreground color
        fgColor = parseColor(cfg.getTerminalForeground(), Color.decode("#CDD6F4"));
        gc.gridx = 0; gc.gridy = row;
        gc.insets = new Insets(5, 0, 5, 12);
        gc.fill = GridBagConstraints.NONE; gc.weightx = 0;
        form.add(new JLabel("Foreground:"), gc);
        gc.gridx = 1;
        gc.insets = new Insets(5, 0, 5, 0);
        fgColorBtn = colorButton(fgColor);
        fgColorBtn.addActionListener(e -> {
            Color c = JColorChooser.showDialog(this, "Terminal Foreground", fgColor);
            if (c != null) { fgColor = c; fgColorBtn.setBackground(c); }
        });
        form.add(fgColorBtn, gc);

        outer.add(form, BorderLayout.NORTH);

        JButton apply = new JButton("Apply");
        apply.addActionListener(e -> applyTerminalSettings());
        JPanel applyRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        applyRow.add(apply);
        outer.add(applyRow, BorderLayout.SOUTH);

        return outer;
    }

    private void applyTerminalSettings() {
        AppConfig cfg = configManager.getConfig();
        String selectedFont = (String) fontCombo.getSelectedItem();
        if (selectedFont != null) cfg.setFontName(selectedFont);
        cfg.setFontSize((Integer) fontSizeSpinner.getValue());
        cfg.setTerminalBackground(colorToHex(bgColor));
        cfg.setTerminalForeground(colorToHex(fgColor));
        configManager.saveConfig();
        JOptionPane.showMessageDialog(this,
                "Settings saved. Changes take effect in new terminal sessions.",
                "Applied", JOptionPane.INFORMATION_MESSAGE);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static JButton colorButton(Color c) {
        JButton btn = new JButton("      ");
        btn.setBackground(c);
        btn.setOpaque(true);
        btn.setBorderPainted(true);
        return btn;
    }

    private static Color parseColor(String hex, Color fallback) {
        try { return Color.decode(hex); } catch (Exception e) { return fallback; }
    }

    private static String colorToHex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }
}

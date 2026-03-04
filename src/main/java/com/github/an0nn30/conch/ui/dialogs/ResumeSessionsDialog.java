package com.github.an0nn30.conch.ui.dialogs;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Shown on startup when there were open SSH sessions at last close.
 * Returns the subset of session keys the user wants to re-open.
 */
public class ResumeSessionsDialog extends JDialog {

    private final List<JCheckBox> checkBoxes = new ArrayList<>();
    private boolean confirmed = false;
    private boolean dontAskAgain = false;

    public ResumeSessionsDialog(Window owner, List<String> sessionKeys) {
        super(owner, "Resume Sessions", ModalityType.APPLICATION_MODAL);
        setSize(420, Math.min(400, 140 + sessionKeys.size() * 28));
        setResizable(false);
        setLocationRelativeTo(owner);
        buildUI(sessionKeys);
    }

    private void buildUI(List<String> keys) {
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBorder(new EmptyBorder(16, 20, 8, 20));

        JLabel heading = new JLabel("You had open sessions last time. Resume them?");
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(heading);
        top.add(Box.createVerticalStrut(10));

        for (String key : keys) {
            JCheckBox cb = new JCheckBox(key, true);
            cb.setAlignmentX(Component.LEFT_ALIGNMENT);
            checkBoxes.add(cb);
            top.add(cb);
        }

        top.add(Box.createVerticalStrut(12));
        JCheckBox dontAsk = new JCheckBox("Don't ask again (always resume automatically)");
        dontAsk.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(dontAsk);

        JButton resume = new JButton("Resume Selected");
        JButton skip   = new JButton("Skip");
        resume.setDefaultCapable(true);
        getRootPane().setDefaultButton(resume);

        resume.addActionListener(e -> { confirmed = true;  dontAskAgain = dontAsk.isSelected(); dispose(); });
        skip.addActionListener(e   -> { confirmed = false; dontAskAgain = dontAsk.isSelected(); dispose(); });

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        btns.add(skip);
        btns.add(resume);

        setLayout(new BorderLayout());
        add(new JScrollPane(top), BorderLayout.CENTER);
        add(btns, BorderLayout.SOUTH);
    }

    /** True if the user clicked "Resume Selected". */
    public boolean isConfirmed() { return confirmed; }

    /** True if the user ticked "Don't ask again". */
    public boolean isDontAskAgain() { return dontAskAgain; }

    /** The session keys the user left checked. */
    public List<String> getSelectedKeys() {
        List<String> result = new ArrayList<>();
        for (JCheckBox cb : checkBoxes) {
            if (cb.isSelected()) result.add(cb.getText());
        }
        return result;
    }
}

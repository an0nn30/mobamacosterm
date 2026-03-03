package com.mobamacos.terminal;

import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import com.mobamacos.model.AppConfig;

import java.awt.*;

public class TerminalSettingsProvider extends DefaultSettingsProvider {
    private AppConfig config;

    public TerminalSettingsProvider(AppConfig config) {
        this.config = config;
    }

    public void setConfig(AppConfig config) {
        this.config = config;
    }

    @Override
    public Font getTerminalFont() {
        String name = config.getFontName();
        if (FontUtil.isAvailable(name)) {
            return new Font(name, Font.PLAIN, config.getFontSize());
        }
        return FontUtil.bestMonospace(config.getFontSize());
    }

    @Override
    public float getTerminalFontSize() {
        return config.getFontSize();
    }

    @Override
    public TextStyle getDefaultStyle() {
        return new TextStyle(
                parseTerminalColor(config.getTerminalForeground()),
                parseTerminalColor(config.getTerminalBackground())
        );
    }

    @Override
    public boolean useAntialiasing() { return true; }

    @Override
    public boolean audibleBell() { return false; }

    @Override
    public boolean copyOnSelect() { return false; }

    @Override
    public float getLineSpacing() { return 1.1f; }

    // ---- helpers -------------------------------------------------------

    private static TerminalColor parseTerminalColor(String hex) {
        try {
            Color c = Color.decode(hex);
            return new TerminalColor(c.getRed(), c.getGreen(), c.getBlue());
        } catch (Exception e) {
            return TerminalColor.WHITE;
        }
    }
}

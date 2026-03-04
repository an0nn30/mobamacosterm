package com.github.an0nn30.conch.plugin;

import com.github.an0nn30.conch.config.ConfigManager;
import com.github.an0nn30.conch.plugin.api.PluginApp;
import com.github.an0nn30.conch.plugin.api.PluginSession;
import com.github.an0nn30.conch.plugin.api.PluginUI;
import com.github.an0nn30.conch.ssh.SshSessionManager;
import com.github.an0nn30.conch.ui.SessionTabPane;
import groovy.lang.Binding;

import java.util.function.Consumer;

/**
 * Assembles the plugin binding: wires {@code ui}, {@code session}, and {@code app}
 * objects into a Groovy {@link Binding} ready for {@link PluginRunner}.
 */
public class PluginContext {

    private final SessionTabPane    tabPane;
    private final SshSessionManager sshManager;
    private final ConfigManager     configManager;

    public PluginContext(SessionTabPane tabPane, SshSessionManager sshManager,
                         ConfigManager configManager) {
        this.tabPane       = tabPane;
        this.sshManager    = sshManager;
        this.configManager = configManager;
    }

    /**
     * Creates a fresh Groovy {@link Binding} with {@code ui}, {@code session},
     * and {@code app} variables wired to the current application state.
     *
     * @param outputCallback receives text written by {@code ui.append()} — typically
     *                       piped to the ToolsPanel output area.
     */
    public Binding createBinding(Consumer<String> outputCallback) {
        PluginUI      ui      = new PluginUI(outputCallback);
        PluginSession session = new PluginSession(tabPane, sshManager, configManager);
        PluginApp     app     = new PluginApp(tabPane, configManager);

        Binding binding = new Binding();
        binding.setVariable("ui",      ui);
        binding.setVariable("session", session);
        binding.setVariable("app",     app);
        return binding;
    }
}

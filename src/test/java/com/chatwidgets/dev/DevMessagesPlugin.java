package com.chatwidgets.dev;

import com.chatwidgets.ChatWidgetPlugin;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;

import javax.inject.Inject;

/**
 * Dev-only plugin that injects test chat messages via {@link Client#addChatMessage}, so they flow
 * through a real {@code ChatMessage} event and render in both the native chatbox and our widgets.
 * Its UI is a Debug section in the Chat Widgets sidebar. Lives under src/test so the Plugin Hub
 * build never ships it; loaded by {@code ChatWidgetPluginTest}.
 */
@PluginDescriptor(name = "Chat Widgets Dev Messages", description = "Inject test chat messages for Chat Widgets", developerPlugin = true)
public class DevMessagesPlugin extends Plugin {

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private PluginManager pluginManager;

    @Override
    protected void startUp() {
        setDebugSection(new DevMessagesPanel(this::send));
    }

    @Override
    protected void shutDown() {
        setDebugSection(null);
    }

    // Looked up rather than injected: @PluginDependency only works for plugins that expose Guice
    // services. ChatWidgetPlugin keeps the section if it starts after us or restarts.
    private void setDebugSection(DevMessagesPanel section) {
        for (Plugin plugin : pluginManager.getPlugins()) {
            if (plugin instanceof ChatWidgetPlugin) {
                ((ChatWidgetPlugin) plugin).setDebugSection(section);
            }
        }
    }

    private void send(ChatMessageType type, String name, String message, String sender) {
        clientThread.invoke(() -> {
            if (client.getGameState() != GameState.LOGGED_IN || message.isEmpty()) {
                return;
            }
            client.addChatMessage(type, name, message, sender.isEmpty() ? null : sender);
        });
    }
}

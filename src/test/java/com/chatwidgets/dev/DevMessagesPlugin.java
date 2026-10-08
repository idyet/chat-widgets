package com.chatwidgets.dev;

import com.google.inject.Provides;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import javax.inject.Inject;

/**
 * Dev-only plugin that injects test chat messages via {@link Client#addChatMessage}, so they flow
 * through a real {@code ChatMessage} event and render in both the native chatbox and our widgets.
 * Lives under src/test so the Plugin Hub build never ships it; loaded by {@code ChatWidgetPluginTest}.
 */
@PluginDescriptor(name = "Chat Widgets Dev Messages", description = "Inject test chat messages for Chat Widgets", developerPlugin = true)
public class DevMessagesPlugin extends Plugin {

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ConfigManager configManager;

    @Inject
    private DevMessagesConfig config;

    @Provides
    DevMessagesConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(DevMessagesConfig.class);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!DevMessagesConfig.GROUP.equals(event.getGroup()) || !"true".equals(event.getNewValue())) {
            return;
        }
        if ("sendPreset".equals(event.getKey())) {
            configManager.setConfiguration(DevMessagesConfig.GROUP, "sendPreset", false);
            DevMessagePreset selected = config.preset();
            clientThread.invoke(() -> {
                for (DevMessagePreset p : DevMessagePreset.values()) {
                    if (p.type != null && (selected == DevMessagePreset.ALL || p == selected)) {
                        send(p.type, p.playerName, p.message, p.sender);
                    }
                }
            });
        } else if ("sendCustom".equals(event.getKey())) {
            configManager.setConfiguration(DevMessagesConfig.GROUP, "sendCustom", false);
            ChatMessageType type = config.customType();
            String name = config.customName();
            String message = config.customMessage();
            String sender = config.customSender();
            clientThread.invoke(() -> send(type, name, message, sender));
        }
    }

    private void send(ChatMessageType type, String name, String message, String sender) {
        if (client.getGameState() != GameState.LOGGED_IN || message.isEmpty()) {
            return;
        }
        client.addChatMessage(type, name, message, sender.isEmpty() ? null : sender);
    }
}

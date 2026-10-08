package com.chatwidgets;

import com.chatwidgets.dev.DevMessagesPlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ChatWidgetPluginTest {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(ChatWidgetPlugin.class, DevMessagesPlugin.class);
        RuneLite.main(args);
    }
}

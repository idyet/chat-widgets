package com.chatwidgets.dev;

import net.runelite.api.ChatMessageType;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

/**
 * Config for {@link DevMessagesPlugin}. RuneLite config has no buttons, so each "send" item is a
 * checkbox that the plugin resets to false right after sending.
 */
@ConfigGroup(DevMessagesConfig.GROUP)
public interface DevMessagesConfig extends Config {

    String GROUP = "chatwidgetsdev";

    @ConfigSection(name = "Preset", description = "Send a canned message that reproduces a known issue", position = 0)
    String presetSection = "preset";

    @ConfigSection(name = "Custom", description = "Send an arbitrary message", position = 1)
    String customSection = "custom";

    @ConfigItem(keyName = "preset", name = "Preset", description = "Message to send", section = presetSection, position = 0)
    default DevMessagePreset preset() {
        return DevMessagePreset.ALL;
    }

    @ConfigItem(keyName = "sendPreset", name = "Send preset", description = "Tick to send the selected preset", section = presetSection, position = 1)
    default boolean sendPreset() {
        return false;
    }

    @ConfigItem(keyName = "customType", name = "Type", description = "Chat message type", section = customSection, position = 0)
    default ChatMessageType customType() {
        return ChatMessageType.GAMEMESSAGE;
    }

    @ConfigItem(keyName = "customName", name = "Name", description = "Sender name (player chat types)", section = customSection, position = 1)
    default String customName() {
        return "";
    }

    @ConfigItem(keyName = "customSender", name = "Sender", description = "Channel name (clan / friends chat types)", section = customSection, position = 2)
    default String customSender() {
        return "";
    }

    @ConfigItem(keyName = "customMessage", name = "Message", description = "Raw message text, tags included", section = customSection, position = 3)
    default String customMessage() {
        return "";
    }

    @ConfigItem(keyName = "sendCustom", name = "Send custom", description = "Tick to send the custom message", section = customSection, position = 4)
    default boolean sendCustom() {
        return false;
    }
}

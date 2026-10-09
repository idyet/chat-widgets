package com.chatwidgets;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

import java.awt.Color;

/**
 * Global plugin settings exposed in RuneLite's config panel. These apply to all overlays.
 * Per-overlay settings (message types, fade, position, etc.) are managed separately via
 * {@link com.chatwidgets.overlay.OverlayConfig} and the sidebar panel.
 */
@ConfigGroup("chatwidgets")
public interface ChatWidgetConfig extends Config {

    @ConfigItem(keyName = "hideSidePanel", name = "Hide Side Panel", description = "Hide the Chat Widgets panel from the sidebar", position = -1)
    default boolean hideSidePanel() {
        return false;
    }

    @ConfigSection(name = "Appearance", description = "Global appearance settings shared across all widgets", position = 0, closedByDefault = false)
    String appearanceSection = "appearance";

    @ConfigSection(name = "Message Colours", description = "Text colour per message category", position = 1, closedByDefault = false)
    String coloursSection = "colours";

    @ConfigItem(keyName = "textShadow", name = "Text Shadow", description = "Draw shadow behind text", section = appearanceSection, position = 1)
    default boolean textShadow() {
        return true;
    }

    @ConfigItem(keyName = "wrapText", name = "Wrap Text", description = "Wrap long messages to multiple lines", section = appearanceSection, position = 2)
    default boolean wrapText() {
        return true;
    }

    @ConfigItem(keyName = "hidePrivateChat", name = "Hide Default Private Chat", description = "Hide the default split private chat widget", section = appearanceSection, position = 3)
    default boolean hidePrivateChat() {
        return true;
    }

    @ConfigItem(keyName = "smartPositioning", name = "Smart Positioning", description = "Automatically reposition widgets based on client mode and chatbox state", section = appearanceSection, position = 4)
    default boolean smartPositioning() {
        return true;
    }

    @ConfigItem(keyName = "collapseDuplicates", name = "Collapse Duplicates", description = "Merge consecutive identical messages into one with a count", section = appearanceSection, position = 5)
    default boolean collapseDuplicates() {
        return false;
    }

    @ConfigItem(keyName = "showChannelName", name = "Show Channel Names", description = "Show the channel name prefix for friends and clan chat messages, and the clan tag on clan notifications", section = appearanceSection, position = 6)
    default boolean showChannelName() {
        return true;
    }

    @ConfigItem(keyName = "useChatFilter", name = "Use Chat Filter", description = "Hide/censor messages using RuneLite's Chat Filter plugin's word & regex lists and Filter Type", section = appearanceSection, position = 7)
    default boolean useChatFilter() {
        return false;
    }

    @ConfigItem(keyName = "timestampFormat", name = "Timestamp Format", description = "Format for timestamps, shared by all widgets (e.g. [HH:mm:ss], [HH:mm]). Enable timestamps per widget in the side panel.", section = appearanceSection, position = 8)
    default String timestampFormat() {
        return "[HH:mm]";
    }

    @ConfigItem(keyName = "gameColour", name = "Game", description = "Text colour for game messages", section = coloursSection, position = 1)
    default Color gameColour() {
        return Color.WHITE;
    }

    @ConfigItem(keyName = "publicColour", name = "Public", description = "Text colour for public chat messages", section = coloursSection, position = 2)
    default Color publicColour() {
        return new Color(148, 148, 255);
    }

    @ConfigItem(keyName = "privateColour", name = "Private", description = "Text colour for private messages", section = coloursSection, position = 3)
    default Color privateColour() {
        return new Color(0, 255, 255);
    }

    @ConfigItem(keyName = "friendsColour", name = "Friends", description = "Text colour for friends chat messages", section = coloursSection, position = 4)
    default Color friendsColour() {
        return new Color(239, 80, 80);
    }

    @ConfigItem(keyName = "clanColour", name = "Clan", description = "Text colour for clan chat messages", section = coloursSection, position = 5)
    default Color clanColour() {
        return new Color(127, 0, 0);
    }

    @ConfigItem(keyName = "guestClanColour", name = "Guest Clan", description = "Text colour for guest clan chat messages", section = coloursSection, position = 6)
    default Color guestClanColour() {
        return new Color(0, 211, 0);
    }

    @ConfigItem(keyName = "gimClanColour", name = "GIM Clan", description = "Text colour for Group Ironman clan chat messages", section = coloursSection, position = 7)
    default Color gimClanColour() {
        return new Color(127, 0, 0);
    }

    @ConfigItem(keyName = "tradeColour", name = "Trade", description = "Text colour for trade messages", section = coloursSection, position = 8)
    default Color tradeColour() {
        return new Color(223, 32, 255);
    }

    @ConfigItem(keyName = "challengeColour", name = "Challenge", description = "Text colour for challenge request messages", section = coloursSection, position = 9)
    default Color challengeColour() {
        return new Color(255, 32, 223);
    }

    @ConfigItem(keyName = "didYouKnowColour", name = "Did You Know?", description = "Text colour for Did You Know messages", section = coloursSection, position = 10)
    default Color didYouKnowColour() {
        return new Color(255, 255, 0);
    }

    @ConfigItem(keyName = "broadcastColour", name = "Broadcast", description = "Text colour for broadcast messages", section = coloursSection, position = 11)
    default Color broadcastColour() {
        return Color.WHITE;
    }

    @ConfigItem(keyName = "autoColour", name = "Autochat", description = "Text colour for autochat messages", section = coloursSection, position = 12)
    default Color autoColour() {
        return new Color(64, 64, 255);
    }
}

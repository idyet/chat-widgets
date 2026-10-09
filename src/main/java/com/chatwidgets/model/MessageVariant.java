package com.chatwidgets.model;

/**
 * Which chatbox form a message was rendered in, as recovered from its {@code |}-separated payload
 * by {@link com.chatwidgets.ChatPipeParser}. Drives the clan tag shown on clan notifications.
 */
public enum MessageVariant {
    NORMAL,
    GIM,
    PVP_ARENA
}

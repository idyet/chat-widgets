package com.chatwidgets.model;

import net.runelite.api.ChatMessageType;

/**
 * Immutable representation of a chat message in the shared pool. Created via factory methods
 * that correspond to the three message shapes: system/game messages, sender-prefixed messages
 * (public, private, clan, friends chat), and login/logout notifications.
 *
 * <p>The only mutable field is {@code count}, used by the duplicate collapse feature to
 * track how many consecutive identical messages this entry represents.
 */
public class WidgetMessage {
    private final String message;
    private final long timestamp;
    private final ChatMessageType type;
    private final boolean bossKc;
    private final String sender;
    private final String channelName;
    private final boolean outgoing;
    private int count = 1;

    public static WidgetMessage gameMessage(String message, long timestamp, ChatMessageType type, boolean bossKc) {
        return new WidgetMessage(message, timestamp, type, bossKc, null, null, false);
    }

    public static WidgetMessage senderMessage(String sender, String channelName, String message, long timestamp,
            ChatMessageType type, boolean outgoing) {
        return new WidgetMessage(message, timestamp, type, false, sender, channelName, outgoing);
    }

    public static WidgetMessage loginNotification(String sender, String message, long timestamp) {
        return new WidgetMessage(message, timestamp, ChatMessageType.LOGINLOGOUTNOTIFICATION,
                false, sender, null, false);
    }

    private WidgetMessage(String message, long timestamp, ChatMessageType type, boolean bossKc,
            String sender, String channelName, boolean outgoing) {
        this.message = message;
        this.timestamp = timestamp;
        this.type = type;
        this.bossKc = bossKc;
        this.sender = sender;
        this.channelName = channelName;
        this.outgoing = outgoing;
    }

    public String getMessage() {
        return message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public ChatMessageType getType() {
        return type;
    }

    public boolean isBossKc() {
        return bossKc;
    }

    public String getSender() {
        return sender;
    }

    public String getChannelName() {
        return channelName;
    }

    public boolean isOutgoing() {
        return outgoing;
    }

    public int getCount() {
        return count;
    }

    public void incrementCount() {
        count++;
    }

    public void setCount(int count) {
        this.count = count;
    }
}

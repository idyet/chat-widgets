package com.chatwidgets.model;

import net.runelite.api.ChatMessageType;

/**
 * Immutable representation of a chat message in the shared pool. Created via factory methods
 * that correspond to the three message shapes: system/game messages, sender-prefixed messages
 * (public, private, clan, friends chat), and login/logout notifications.
 *
 * <p>The only mutable field is {@code count}, used by the duplicate collapse feature to
 * track how many consecutive identical messages this entry represents.
 *
 * <p>{@code message} is display text: any {@code |}-separated payload the chatbox hides has already
 * been split off by {@link com.chatwidgets.ChatPipeParser}, and what it carried is kept in
 * {@code variant}, {@code achievementTaskId} and {@code broadcastUrl}. {@code clanTag} is the clan
 * name snapshotted at capture for clan notifications ({@code null} when untagged).
 */
public class WidgetMessage {
    private final String message;
    private final long timestamp;
    private final ChatMessageType type;
    private final boolean bossKc;
    private final String sender;
    private final String channelName;
    private final boolean outgoing;
    private final int maxFadeSeconds;
    private final MessageVariant variant;
    private final Integer achievementTaskId;
    private final String broadcastUrl;
    private final String clanTag;
    private int count = 1;

    public static WidgetMessage gameMessage(String message, long timestamp, ChatMessageType type, boolean bossKc) {
        return gameMessage(message, timestamp, type, bossKc, MessageVariant.NORMAL, null, null, null);
    }

    public static WidgetMessage gameMessage(String message, long timestamp, ChatMessageType type, boolean bossKc,
            MessageVariant variant, Integer achievementTaskId, String broadcastUrl, String clanTag) {
        return new WidgetMessage(message, timestamp, type, bossKc, null, null, false, 0,
                variant, achievementTaskId, broadcastUrl, clanTag);
    }

    public static WidgetMessage senderMessage(String sender, String channelName, String message, long timestamp,
            ChatMessageType type, boolean outgoing) {
        return senderMessage(sender, channelName, message, timestamp, type, outgoing, MessageVariant.NORMAL);
    }

    public static WidgetMessage senderMessage(String sender, String channelName, String message, long timestamp,
            ChatMessageType type, boolean outgoing, MessageVariant variant) {
        return new WidgetMessage(message, timestamp, type, false, sender, channelName, outgoing, 0,
                variant, null, null, null);
    }

    public static WidgetMessage loginNotification(String sender, String message, long timestamp, int maxFadeSeconds) {
        return new WidgetMessage(message, timestamp, ChatMessageType.LOGINLOGOUTNOTIFICATION,
                false, sender, null, false, maxFadeSeconds, MessageVariant.NORMAL, null, null, null);
    }

    private WidgetMessage(String message, long timestamp, ChatMessageType type, boolean bossKc,
            String sender, String channelName, boolean outgoing, int maxFadeSeconds,
            MessageVariant variant, Integer achievementTaskId, String broadcastUrl, String clanTag) {
        this.message = message;
        this.timestamp = timestamp;
        this.type = type;
        this.bossKc = bossKc;
        this.sender = sender;
        this.channelName = channelName;
        this.outgoing = outgoing;
        this.maxFadeSeconds = maxFadeSeconds;
        this.variant = variant;
        this.achievementTaskId = achievementTaskId;
        this.broadcastUrl = broadcastUrl;
        this.clanTag = clanTag;
    }

    /**
     * Copy carrying a new body and every other field unchanged except {@code count}, which resets
     * to 1 (callers decide whether to carry it over).
     */
    public WidgetMessage withMessage(String newMessage) {
        return new WidgetMessage(newMessage, timestamp, type, bossKc, sender, channelName, outgoing,
                maxFadeSeconds, variant, achievementTaskId, broadcastUrl, clanTag);
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

    public int getMaxFadeSeconds() {
        return maxFadeSeconds;
    }

    public MessageVariant getVariant() {
        return variant;
    }

    /** Combat achievement task id from a {@code CA_ID:n|} payload, or {@code null}. */
    public Integer getAchievementTaskId() {
        return achievementTaskId;
    }

    /** Resolved {@code enum_63} URL for a linked broadcast, or {@code null}. */
    public String getBroadcastUrl() {
        return broadcastUrl;
    }

    /** Clan name shown as {@code [tag]} on clan notifications, or {@code null} when untagged. */
    public String getClanTag() {
        return clanTag;
    }
}

package com.chatwidgets;

import com.chatwidgets.model.MessageVariant;
import net.runelite.api.ChatMessageType;

import java.util.function.IntFunction;

/**
 * Mirrors how the chatbox cs2 ({@code rebuildchatbox}) splits certain message types at {@code |}
 * and hides one side, so hidden payloads ({@code CA_ID:565|}, GIM/PvP Arena prefixes, broadcast
 * link keys) never render raw in a widget. Pure: client lookups are passed in by the caller.
 *
 * <p>Per type:
 * <ul>
 *   <li>{@code GAMEMESSAGE}, {@code CLAN_MESSAGE}: split at the first {@code |}, show the right side;
 *       a {@code CA_ID:n} left side yields the achievement task id. {@code CLAN_MESSAGE} also
 *       recognises the GIM ({@code |text}) and PvP Arena ({@code p|text}) forms.</li>
 *   <li>{@code CLAN_GIM_MESSAGE}: GIM form, strip a leading {@code |} then split again.</li>
 *   <li>{@code CLAN_GIM_CHAT}, and {@code CLAN_CHAT} while in a GIM: strip a leading {@code |}.</li>
 *   <li>{@code BROADCAST}: reversed, keeps the left side; the right side's first char is a base-36
 *       key into {@code enum_63}, and a resolved URL prefixes the text with {@code <img=12> }.</li>
 *   <li>Everything else is untouched.</li>
 * </ul>
 *
 * <p>RuneLite retypes a {@code CLAN_MESSAGE} / {@code CLAN_CHAT} starting with {@code |} to its GIM
 * type and strips that {@code |} from the event message, but the {@link net.runelite.api.MessageNode}
 * value keeps it. GIM text can therefore arrive with or without the leading pipe (event vs node, e.g.
 * emoji seeding and next-tick reconcile), so it is stripped only when present. The {@code |} forms
 * under {@code CLAN_MESSAGE} / {@code CLAN_CHAT} are defensive, in case that retyping changes.
 */
public final class ChatPipeParser {

    private static final String CA_ID_PREFIX = "CA_ID:";
    private static final String BROADCAST_KEYS = "0123456789abcdefghijklmnopqrstuvwxyz";
    private static final String BROADCAST_ICON = "<img=12> ";

    private ChatPipeParser() {
    }

    /** Display text plus the payload recovered from the hidden side of the split. */
    public static final class Result {
        private final String text;
        private final MessageVariant variant;
        private final Integer achievementTaskId;
        private final String broadcastUrl;

        Result(String text, MessageVariant variant, Integer achievementTaskId, String broadcastUrl) {
            this.text = text;
            this.variant = variant;
            this.achievementTaskId = achievementTaskId;
            this.broadcastUrl = broadcastUrl;
        }

        static Result of(String text, MessageVariant variant) {
            return new Result(text, variant, null, null);
        }

        public String getText() {
            return text;
        }

        public MessageVariant getVariant() {
            return variant;
        }

        public Integer getAchievementTaskId() {
            return achievementTaskId;
        }

        public String getBroadcastUrl() {
            return broadcastUrl;
        }
    }

    /**
     * @param type               the message's chat type
     * @param raw                the message text as received (already trimmed)
     * @param inGim              whether the player is in a Group Ironman clan (gates {@code CLAN_CHAT})
     * @param broadcastUrlLookup {@code enum_63} lookup: base-36 key to URL, {@code null}/empty if unset
     */
    public static Result parse(ChatMessageType type, String raw, boolean inGim,
            IntFunction<String> broadcastUrlLookup) {
        switch (type) {
            case GAMEMESSAGE:
                return splitShowRight(raw, MessageVariant.NORMAL);
            case CLAN_MESSAGE:
                if (raw.startsWith("p|")) {
                    return Result.of(raw.substring(2), MessageVariant.PVP_ARENA);
                }
                if (raw.startsWith("|")) {
                    return gimNotification(raw);
                }
                return splitShowRight(raw, MessageVariant.NORMAL);
            case CLAN_GIM_MESSAGE:
                return gimNotification(raw);
            case CLAN_GIM_CHAT:
                return Result.of(stripLeadingPipe(raw), MessageVariant.GIM);
            case CLAN_CHAT:
                if (inGim && raw.startsWith("|")) {
                    return Result.of(stripLeadingPipe(raw), MessageVariant.GIM);
                }
                return untouched(raw);
            case BROADCAST:
                return broadcast(raw, broadcastUrlLookup);
            default:
                return untouched(raw);
        }
    }

    private static Result untouched(String raw) {
        return Result.of(raw, MessageVariant.NORMAL);
    }

    /** GIM form {@code [|]text} or {@code [|]CA_ID:n|text}: strip a leading pipe, then split again. */
    private static Result gimNotification(String raw) {
        return splitShowRight(stripLeadingPipe(raw), MessageVariant.GIM);
    }

    /** Splits at the first {@code |} when present, showing the right side; left side may carry a task id. */
    private static Result splitShowRight(String text, MessageVariant variant) {
        int pipe = text.indexOf('|');
        if (pipe < 0) {
            return Result.of(text, variant);
        }
        String left = text.substring(0, pipe);
        return new Result(text.substring(pipe + 1), variant, parseTaskId(left), null);
    }

    private static Result broadcast(String raw, IntFunction<String> urlLookup) {
        int pipe = raw.indexOf('|');
        if (pipe < 0) {
            return untouched(raw);
        }
        String left = raw.substring(0, pipe);
        String right = raw.substring(pipe + 1);
        if (right.isEmpty()) {
            return untouched(left);
        }
        int key = BROADCAST_KEYS.indexOf(right.charAt(0));
        String url = key < 0 ? null : urlLookup.apply(key);
        if (url == null || url.isEmpty()) {
            return untouched(left);
        }
        return new Result(BROADCAST_ICON + left, MessageVariant.NORMAL, null, url);
    }

    /** cs2 finds {@code CA_ID:} anywhere in the left side and reads the int after it. */
    private static Integer parseTaskId(String left) {
        int idx = left.indexOf(CA_ID_PREFIX);
        if (idx < 0) {
            return null;
        }
        String digits = left.substring(idx + CA_ID_PREFIX.length());
        if (!digits.matches("\\d{1,9}")) {
            return null;
        }
        return Integer.parseInt(digits);
    }

    private static String stripLeadingPipe(String text) {
        return text.startsWith("|") ? text.substring(1) : text;
    }
}

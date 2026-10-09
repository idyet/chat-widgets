package com.chatwidgets;

import com.chatwidgets.model.MessageVariant;
import net.runelite.api.ChatMessageType;
import org.junit.Test;

import java.util.function.IntFunction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Unit tests for {@link ChatPipeParser}, which mirrors the chatbox cs2's per-type {@code |}
 * handling ({@code rebuildchatbox}, {@code script632}, {@code chat_broadcast_parseurl}).
 */
public class ChatPipeParserTest {

    private static final String NEWS_URL = "https://secure.runescape.com/m=news/";

    /** Stand-in for {@code enum_63}: key 0 resolves, key 5 maps to an empty URL, the rest are unset. */
    private static final IntFunction<String> URLS = key -> {
        if (key == 0) {
            return NEWS_URL;
        }
        if (key == 5) {
            return "";
        }
        return null;
    };

    private static ChatPipeParser.Result parse(ChatMessageType type, String raw) {
        return ChatPipeParser.parse(type, raw, false, URLS);
    }

    private static void assertResult(ChatPipeParser.Result r, String text, MessageVariant variant,
            Integer taskId, String url) {
        assertEquals("text", text, r.getText());
        assertEquals("variant", variant, r.getVariant());
        assertEquals("achievementTaskId", taskId, r.getAchievementTaskId());
        assertEquals("broadcastUrl", url, r.getBroadcastUrl());
    }

    // --- GAMEMESSAGE ---

    @Test
    public void gameMessageCombatAchievementShowsRightAndKeepsTaskId() {
        assertResult(parse(ChatMessageType.GAMEMESSAGE, "CA_ID:565|Congratulations, you've completed a task."),
                "Congratulations, you've completed a task.", MessageVariant.NORMAL, 565, null);
    }

    @Test
    public void gameMessageWithoutPipeIsUntouched() {
        assertResult(parse(ChatMessageType.GAMEMESSAGE, "Welcome to Old School RuneScape."),
                "Welcome to Old School RuneScape.", MessageVariant.NORMAL, null, null);
    }

    @Test
    public void gameMessageLeadingPipeShowsRemainder() {
        assertResult(parse(ChatMessageType.GAMEMESSAGE, "|hello"), "hello", MessageVariant.NORMAL, null, null);
    }

    @Test
    public void gameMessageTrailingPipeLeavesBlankText() {
        assertResult(parse(ChatMessageType.GAMEMESSAGE, "a|"), "", MessageVariant.NORMAL, null, null);
    }

    @Test
    public void gameMessageSplitsAtFirstPipeOnly() {
        assertResult(parse(ChatMessageType.GAMEMESSAGE, "a|b|c"), "b|c", MessageVariant.NORMAL, null, null);
    }

    @Test
    public void gameMessageNonNumericTaskIdIsNullButTextStillSplit() {
        assertResult(parse(ChatMessageType.GAMEMESSAGE, "CA_ID:abc|Done."), "Done.", MessageVariant.NORMAL, null, null);
    }

    @Test
    public void gameMessageNonCaLeftPartIsDiscarded() {
        assertResult(parse(ChatMessageType.GAMEMESSAGE, "something|Shown."), "Shown.", MessageVariant.NORMAL, null, null);
    }

    @Test
    public void gameMessageCaIdFoundAnywhereInLeftPart() {
        assertResult(parse(ChatMessageType.GAMEMESSAGE, "xCA_ID:12|Shown."), "Shown.", MessageVariant.NORMAL, 12, null);
    }

    @Test
    public void gameMessagePluginTextIsMirroredLikeTheChatbox() {
        assertResult(parse(ChatMessageType.GAMEMESSAGE, "a | b"), " b", MessageVariant.NORMAL, null, null);
    }

    @Test
    public void gameMessageBlankAfterSplit() {
        assertResult(parse(ChatMessageType.GAMEMESSAGE, "CA_ID:1|"), "", MessageVariant.NORMAL, 1, null);
    }

    // --- CLAN_MESSAGE ---

    @Test
    public void clanMessagePvpArenaForm() {
        assertResult(parse(ChatMessageType.CLAN_MESSAGE, "p|PvP Arena test"),
                "PvP Arena test", MessageVariant.PVP_ARENA, null, null);
    }

    @Test
    public void clanMessagePvpArenaSplitsOnceOnly() {
        assertResult(parse(ChatMessageType.CLAN_MESSAGE, "p|a|b"), "a|b", MessageVariant.PVP_ARENA, null, null);
    }

    @Test
    public void clanMessageGimFormWithTaskId() {
        assertResult(parse(ChatMessageType.CLAN_MESSAGE, "|CA_ID:7|Group task."),
                "Group task.", MessageVariant.GIM, 7, null);
    }

    @Test
    public void clanMessageGimFormPlain() {
        assertResult(parse(ChatMessageType.CLAN_MESSAGE, "|Group note."), "Group note.", MessageVariant.GIM, null, null);
    }

    @Test
    public void clanMessageNormalWithTaskId() {
        assertResult(parse(ChatMessageType.CLAN_MESSAGE, "CA_ID:565|Bob completed a task."),
                "Bob completed a task.", MessageVariant.NORMAL, 565, null);
    }

    @Test
    public void clanMessageNormalWithoutPipe() {
        assertResult(parse(ChatMessageType.CLAN_MESSAGE, "Bob has joined."), "Bob has joined.",
                MessageVariant.NORMAL, null, null);
    }

    // --- GIM types ---

    @Test
    public void clanGimMessageStripsLeadingPipeThenSplitsAgain() {
        assertResult(parse(ChatMessageType.CLAN_GIM_MESSAGE, "|CA_ID:1|GIM test"),
                "GIM test", MessageVariant.GIM, 1, null);
    }

    @Test
    public void clanGimMessagePlain() {
        assertResult(parse(ChatMessageType.CLAN_GIM_MESSAGE, "|Bob has logged in."),
                "Bob has logged in.", MessageVariant.GIM, null, null);
    }

    /** RuneLite's event message: retyped to CLAN_GIM_MESSAGE with the leading | already stripped. */
    @Test
    public void clanGimMessageEventFormWithTaskId() {
        assertResult(parse(ChatMessageType.CLAN_GIM_MESSAGE, "CA_ID:1|GIM test"),
                "GIM test", MessageVariant.GIM, 1, null);
    }

    @Test
    public void clanGimMessageEventFormPlain() {
        assertResult(parse(ChatMessageType.CLAN_GIM_MESSAGE, "Bob has logged in."),
                "Bob has logged in.", MessageVariant.GIM, null, null);
    }

    /** RuneLite's event message: retyped to CLAN_GIM_CHAT with the leading | already stripped. */
    @Test
    public void clanGimChatEventFormUntouched() {
        assertResult(parse(ChatMessageType.CLAN_GIM_CHAT, "hi all"), "hi all", MessageVariant.GIM, null, null);
    }

    @Test
    public void clanGimChatStripsLeadingPipe() {
        assertResult(parse(ChatMessageType.CLAN_GIM_CHAT, "|hi all"), "hi all", MessageVariant.GIM, null, null);
    }

    @Test
    public void clanGimChatKeepsLaterPipes() {
        assertResult(parse(ChatMessageType.CLAN_GIM_CHAT, "|a|b"), "a|b", MessageVariant.GIM, null, null);
    }

    @Test
    public void clanChatLeadingPipeStrippedWhenInGim() {
        assertResult(ChatPipeParser.parse(ChatMessageType.CLAN_CHAT, "|hi", true, URLS),
                "hi", MessageVariant.GIM, null, null);
    }

    @Test
    public void clanChatLeadingPipeUntouchedWhenNotInGim() {
        assertResult(parse(ChatMessageType.CLAN_CHAT, "|hi"), "|hi", MessageVariant.NORMAL, null, null);
    }

    @Test
    public void clanChatWithoutLeadingPipeUntouchedEvenInGim() {
        assertResult(ChatPipeParser.parse(ChatMessageType.CLAN_CHAT, "a|b", true, URLS),
                "a|b", MessageVariant.NORMAL, null, null);
    }

    // --- BROADCAST ---

    @Test
    public void broadcastWithValidKeyKeepsLeftAndAddsIcon() {
        assertResult(parse(ChatMessageType.BROADCAST, "Test news|0"),
                "<img=12> Test news", MessageVariant.NORMAL, null, NEWS_URL);
    }

    @Test
    public void broadcastWithBadKeyCharKeepsLeftWithoutIcon() {
        assertResult(parse(ChatMessageType.BROADCAST, "Test news|!"), "Test news", MessageVariant.NORMAL, null, null);
    }

    @Test
    public void broadcastWithUppercaseKeyIsNotBase36() {
        assertResult(parse(ChatMessageType.BROADCAST, "Test news|A"), "Test news", MessageVariant.NORMAL, null, null);
    }

    @Test
    public void broadcastWithEmptyUrlKeepsLeftWithoutIcon() {
        assertResult(parse(ChatMessageType.BROADCAST, "Test news|5"), "Test news", MessageVariant.NORMAL, null, null);
    }

    @Test
    public void broadcastWithUnsetKeyKeepsLeftWithoutIcon() {
        assertResult(parse(ChatMessageType.BROADCAST, "Test news|z"), "Test news", MessageVariant.NORMAL, null, null);
    }

    @Test
    public void broadcastWithoutPipeIsUntouched() {
        assertResult(parse(ChatMessageType.BROADCAST, "Test news"), "Test news", MessageVariant.NORMAL, null, null);
    }

    @Test
    public void broadcastWithTrailingPipeKeepsLeftWithoutIcon() {
        assertResult(parse(ChatMessageType.BROADCAST, "Test news|"), "Test news", MessageVariant.NORMAL, null, null);
    }

    // --- Untouched types ---

    @Test
    public void playerChatTypesContainingPipeAreUntouched() {
        ChatMessageType[] types = {
                ChatMessageType.PUBLICCHAT, ChatMessageType.PRIVATECHAT, ChatMessageType.PRIVATECHATOUT,
                ChatMessageType.FRIENDSCHAT, ChatMessageType.CLAN_GUEST_CHAT, ChatMessageType.MODCHAT
        };
        for (ChatMessageType type : types) {
            assertResult(ChatPipeParser.parse(type, "|a|b", true, URLS), "|a|b", MessageVariant.NORMAL, null, null);
        }
    }

    @Test
    public void otherGameTypesAreUntouched() {
        assertResult(parse(ChatMessageType.SPAM, "CA_ID:1|x"), "CA_ID:1|x", MessageVariant.NORMAL, null, null);
        assertResult(parse(ChatMessageType.CLAN_GUEST_MESSAGE, "p|x"), "p|x", MessageVariant.NORMAL, null, null);
    }
}

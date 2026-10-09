package com.chatwidgets.overlay;

import com.chatwidgets.model.FontSize;
import com.chatwidgets.model.MessageVariant;
import com.chatwidgets.model.WidgetMessage;
import net.runelite.api.ChatMessageType;
import org.junit.BeforeClass;
import org.junit.Test;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Covers the non-sender headers {@link ChatRenderUtils#buildMessageLines} adds for {@code |}-parsed
 * messages: the always-on {@code Broadcast:} label and the {@code [clan tag]} on clan notifications,
 * gated by Show Channel Names. Needs only a headless {@link FontMetrics}.
 */
public class MessageHeaderTest {

    private static FontMetrics metrics;

    @BeforeClass
    public static void setUp() {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        metrics = g.getFontMetrics(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.dispose();
    }

    /** Renders one unwrapped line and concatenates its non-icon segment text. */
    private static String render(WidgetMessage msg, boolean showChannelName) {
        List<RenderLine> lines = ChatRenderUtils.buildMessageLines(msg, metrics, 1000, 0L, 0L, false,
                Color.WHITE, true, true, FontSize.REGULAR, null, false, null, showChannelName, null);
        assertEquals(1, lines.size());
        StringBuilder sb = new StringBuilder();
        for (TextSegment s : lines.get(0).segments) {
            if (s.iconId == -1) {
                sb.append(s.text);
            }
        }
        return sb.toString();
    }

    private static WidgetMessage clanNotification(ChatMessageType type, MessageVariant variant, String tag) {
        return WidgetMessage.gameMessage("Bob completed a task.", 0L, type, false, variant, null, null, tag);
    }

    @Test
    public void clanNotificationTaggedWhenChannelNamesShown() {
        WidgetMessage msg = clanNotification(ChatMessageType.CLAN_MESSAGE, MessageVariant.NORMAL, "My Clan");
        assertEquals("[My Clan] Bob completed a task.", render(msg, true));
    }

    @Test
    public void clanNotificationUntaggedWhenChannelNamesHidden() {
        WidgetMessage msg = clanNotification(ChatMessageType.CLAN_MESSAGE, MessageVariant.NORMAL, "My Clan");
        assertEquals("Bob completed a task.", render(msg, false));
    }

    @Test
    public void gimNotificationTaggedWithGroupName() {
        WidgetMessage msg = clanNotification(ChatMessageType.CLAN_GIM_MESSAGE, MessageVariant.GIM, "Iron Pals");
        assertEquals("[Iron Pals] Bob completed a task.", render(msg, true));
    }

    @Test
    public void clanNotificationWithoutTagHasNoBrackets() {
        WidgetMessage msg = clanNotification(ChatMessageType.CLAN_MESSAGE, MessageVariant.NORMAL, null);
        assertEquals("Bob completed a task.", render(msg, true));
    }

    @Test
    public void broadcastLabelShownRegardlessOfChannelNames() {
        WidgetMessage msg = WidgetMessage.gameMessage("<img=12> Test news", 0L, ChatMessageType.BROADCAST,
                false, MessageVariant.NORMAL, null, "https://example.com", null);
        // The <img=12> icon segment is skipped by render(); its trailing space is body text.
        assertEquals("Broadcast:  Test news", render(msg, false));
        assertEquals("Broadcast:  Test news", render(msg, true));
    }

    @Test
    public void plainGameMessageHasNoHeader() {
        WidgetMessage msg = WidgetMessage.gameMessage("Hello.", 0L, ChatMessageType.GAMEMESSAGE, false);
        assertEquals("Hello.", render(msg, true));
    }
}

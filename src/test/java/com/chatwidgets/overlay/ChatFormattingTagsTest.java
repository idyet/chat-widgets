package com.chatwidgets.overlay;

import com.chatwidgets.model.FontSize;
import org.junit.BeforeClass;
import org.junit.Test;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Covers in-game chat formatting tags and {@code <str_NAME=VALUE>} template variables in
 * {@link ChatRenderUtils#parseTextWithColoursAndIcons}. Expected behaviour mirrors the game's font
 * renderer ({@code AbstractFont.decodeTag}) and the client's template expansion.
 */
public class ChatFormattingTagsTest {

    private static FontMetrics metrics;

    @BeforeClass
    public static void setUp() {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        metrics = g.getFontMetrics(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.dispose();
    }

    private static List<TextSegment> parse(String input) {
        return ChatRenderUtils.parseTextWithColoursAndIcons(
                input, metrics, null, true, Color.WHITE, FontSize.REGULAR, null);
    }

    /** Returns the single text segment whose text is exactly {@code text}. */
    private static TextSegment segment(List<TextSegment> segments, String text) {
        TextSegment found = null;
        for (TextSegment s : segments) {
            if (s.iconId == -1 && s.text.equals(text)) {
                assertNull("more than one segment reads '" + text + "'", found);
                found = s;
            }
        }
        if (found == null) {
            throw new AssertionError("no segment reads '" + text + "' in " + textOf(segments));
        }
        return found;
    }

    /** Concatenates segment text, writing each line break as a newline. */
    private static String textOf(List<TextSegment> segments) {
        StringBuilder sb = new StringBuilder();
        for (TextSegment s : segments) {
            if (s.iconId == TextSegment.LINE_BREAK) {
                sb.append('\n');
            } else if (s.iconId == -1) {
                sb.append(s.text);
            }
        }
        return sb.toString();
    }

    @Test
    public void expandsQuestTemplateVariable() {
        assertEquals("Username has completed a quest: Crab Quest",
                textOf(parse("<str_quest_name_0=Crab Quest>Username has completed a quest: <str_quest_name_0>")));
    }

    /** The PvP Performance Tracker notice: every tag is hidden, only the text remains. */
    @Test
    public void hidesAllTagsInPluginUpdateNotice() {
        assertEquals("PvP Performance Tracker v1.9.0 Update: Added Pete Kayer fight tracking.",
                textOf(parse("<html><shad=000000><col=ee4500>PvP Performance Tracker</col> "
                        + "<col=fa1500><u>v1.9.0</u></col> <col=ee4500>Update:</col></shad> "
                        + "<col=842b00>Added Pete Kayer fight tracking.")));
    }

    @Test
    public void underlineUsesTagColourOrBlackWhenBare() {
        List<TextSegment> segs = parse("a<u>b</u>c<u=ff0000>d</u>");
        assertNull(segment(segs, "a").underlineColor);
        assertEquals(Color.BLACK, segment(segs, "b").underlineColor);
        assertNull(segment(segs, "c").underlineColor);
        assertEquals(new Color(0xff0000), segment(segs, "d").underlineColor);
    }

    @Test
    public void strikethroughUsesTagColourOrDarkRedWhenBare() {
        List<TextSegment> segs = parse("a<str>b</str>c<str=00ff00>d</str>");
        assertNull(segment(segs, "a").strikeColor);
        assertEquals(new Color(0x800000), segment(segs, "b").strikeColor);
        assertNull(segment(segs, "c").strikeColor);
        assertEquals(new Color(0x00ff00), segment(segs, "d").strikeColor);
    }

    /** A null shadow colour means "the base shadow", which the renderer resolves from config. */
    @Test
    public void shadowUsesTagColourOrBlackWhenBareAndEndRestoresBase() {
        List<TextSegment> segs = parse("a<shad>b</shad>c<shad=0000ff>d</shad>e");
        assertNull(segment(segs, "a").shadowColor);
        assertEquals(Color.BLACK, segment(segs, "b").shadowColor);
        assertNull(segment(segs, "c").shadowColor);
        assertEquals(new Color(0x0000ff), segment(segs, "d").shadowColor);
        assertNull(segment(segs, "e").shadowColor);
    }

    /** Formatting stacks independently: colour, underline and shadow all apply to the same text. */
    @Test
    public void formattingCombines() {
        TextSegment v = segment(parse("<shad=000000><col=fa1500><u>v1.9.0</u></col></shad> x"), "v1.9.0");
        assertEquals(new Color(0xfa1500), v.color);
        assertEquals(Color.BLACK, v.underlineColor);
        assertEquals(Color.BLACK, v.shadowColor);
    }

    /** <br> ends the line and resets colour, strikethrough, underline and shadow to base. */
    @Test
    public void lineBreakResetsAllFormatting() {
        List<TextSegment> segs = parse("<col=ff0000><str><u><shad=00ff00>a<br>b");
        assertEquals("a\nb", textOf(segs));
        TextSegment b = segment(segs, "b");
        assertEquals(Color.WHITE, b.color);
        assertNull(b.strikeColor);
        assertNull(b.underlineColor);
        assertNull(b.shadowColor);
    }

    /** The game font breaks the line at <n> just like <br>. */
    @Test
    public void newlineTagBreaksLineAndResetsFormatting() {
        List<TextSegment> segs = parse("<u>a<n>b");
        assertEquals("a\nb", textOf(segs));
        assertNull(segment(segs, "b").underlineColor);
    }

    @Test
    public void nonBreakingHyphenRendersAsHyphen() {
        assertEquals("Ice-cold", textOf(parse("Ice<nbh>cold")));
    }

    /**
     * Bare tags are exact matches: an undefined template reference or a look-alike tag must not
     * switch on strikethrough or underline, and is hidden like any unknown tag.
     */
    @Test
    public void lookAlikeTagsAreHiddenAndDoNotFormat() {
        List<TextSegment> segs = parse("a<str_region_0>b<strike>c<u2>d<shadow>e");
        assertEquals("abcde", textOf(segs));
        for (TextSegment s : segs) {
            assertNull(s.strikeColor);
            assertNull(s.underlineColor);
            assertNull(s.shadowColor);
        }
    }

    /** A tag with bad hex is ignored: the previous formatting stays in effect. */
    @Test
    public void badHexTagIsIgnored() {
        List<TextSegment> segs = parse("<col=ff0000>a<col=zz>b<u=zz>c");
        assertEquals("abc", textOf(segs));
        assertEquals(new Color(0xff0000), segment(segs, "abc").color);
        assertNull(segment(segs, "abc").underlineColor);
    }

    /** A '<' with no closing '>' is not a tag, so it stays visible. */
    @Test
    public void unterminatedTagStaysLiteral() {
        assertEquals("a <3 b", textOf(parse("a <3 b")));
    }

    /** Wrapping splits text into words and spaces; each piece keeps the source's formatting. */
    @Test
    public void wrappingKeepsFormatting() {
        List<TextSegment> segs = parse("<str><u=ff0000><shad=00ff00>one two three");
        int narrow = metrics.stringWidth("one two");
        List<List<TextSegment>> lines = ChatRenderUtils.wrapSegments(segs, metrics, narrow, narrow, Color.WHITE);
        assertEquals(2, lines.size());
        for (List<TextSegment> line : lines) {
            for (TextSegment s : line) {
                assertEquals(new Color(0x800000), s.strikeColor);
                assertEquals(new Color(0xff0000), s.underlineColor);
                assertEquals(new Color(0x00ff00), s.shadowColor);
            }
        }
    }
}

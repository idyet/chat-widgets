package com.chatwidgets.overlay;

import com.chatwidgets.model.FontSize;
import com.chatwidgets.model.WidgetMessage;
import net.runelite.api.ChatMessageType;
import net.runelite.api.IndexedSprite;
import net.runelite.client.config.ChatColorConfig;
import net.runelite.client.ui.FontManager;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stateless rendering utilities shared by all overlays. Handles two main concerns:
 *
 * <p><b>Text parsing</b> — Converts raw message strings containing OSRS markup into lists of
 * {@link TextSegment}s. Two parsers exist:
 * <ul>
 *   <li>{@link #parseTextWithIcons} — handles {@code <img=N>} icon tags and entity references
 *       ({@code <lt>}, {@code <gt>}). Used for sender names.</li>
 *   <li>{@link #parseTextWithColoursAndIcons}: additionally handles the game font's formatting
 *       tags ({@code <col>}, {@code <str>}, {@code <u>}, {@code <shad>}, {@code <br>}), named
 *       colour tags, and {@code <str_NAME=VALUE>} template variables. Used for message bodies.</li>
 * </ul>
 *
 * <p><b>Icon caching</b> — Converts RuneLite's {@link IndexedSprite} mod icons to
 * {@link BufferedImage} on first access and caches them. The cache is invalidated when
 * the modIcons array reference changes (e.g. after a plugin loads new icons).
 */
public final class ChatRenderUtils {

    private static final Pattern IMG_TAG_PATTERN = Pattern.compile("<img=(\\d+)>");
    private static final Pattern TEMPLATE_DEF_PATTERN = Pattern.compile("<(str_[^=<>]+)=([^<>]*)>");
    private static final Pattern TEMPLATE_REF_PATTERN = Pattern.compile("<(str_[^=<>]+)>");
    private static final int MAX_MESSAGE_LENGTH = 500;
    /** The game font's strikethrough colour for a bare {@code <str>} tag. */
    private static final Color DEFAULT_STRIKE_COLOR = new Color(0x800000);

    private static IndexedSprite[] cachedModIconsRef;
    private static final Map<Integer, BufferedImage> iconImageCache = new HashMap<>();

    private static final Set<ChatMessageType> SENDER_PREFIX_TYPES = EnumSet.of(
            ChatMessageType.PRIVATECHAT,
            ChatMessageType.PRIVATECHATOUT,
            ChatMessageType.MODPRIVATECHAT,
            ChatMessageType.PUBLICCHAT,
            ChatMessageType.MODCHAT,
            ChatMessageType.AUTOTYPER,
            ChatMessageType.MODAUTOTYPER,
            ChatMessageType.FRIENDSCHAT,
            ChatMessageType.CLAN_CHAT,
            ChatMessageType.CLAN_GUEST_CHAT,
            ChatMessageType.CLAN_GIM_CHAT
    );

    private static final Set<ChatMessageType> PM_TYPES = EnumSet.of(
            ChatMessageType.PRIVATECHAT,
            ChatMessageType.PRIVATECHATOUT,
            ChatMessageType.MODPRIVATECHAT
    );

    private ChatRenderUtils() {
    }

    public static FontMetrics setupGraphics(Graphics2D graphics, FontSize fontSize) {
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        Font baseFont = fontSize == FontSize.SMALL
                ? FontManager.getRunescapeSmallFont()
                : FontManager.getRunescapeFont();
        graphics.setFont(baseFont);
        return graphics.getFontMetrics();
    }

    public static int drawIcon(Graphics2D graphics, BufferedImage img, FontSize fontSize,
            FontMetrics metrics, int x, int y) {
        if (img == null) {
            return 0;
        }
        boolean isSmallFont = fontSize == FontSize.SMALL;
        int iconWidth = img.getWidth();
        int iconHeight = img.getHeight();

        if (isSmallFont) {
            iconWidth = (int) (iconWidth * 0.75);
            iconHeight = (int) (iconHeight * 0.75);
        }

        int iconY = y - iconHeight + metrics.getDescent() - 4;
        if (isSmallFont) {
            iconY += 2;
        }

        graphics.drawImage(img, x + 1, iconY, iconWidth, iconHeight, null);
        return iconWidth + 2;
    }

    /**
     * Draws a text segment with its font decorations, matching the game's font renderer: the
     * shadow is offset one pixel right and down, the strikethrough sits 70% of the ascent below
     * the glyph top, and the underline one pixel below the baseline. A segment without its own
     * shadow colour uses the base shadow ({@code drawShadow}: black or none).
     */
    public static int drawText(Graphics2D graphics, TextSegment segment, Color color, int alpha, int x, int y,
            boolean drawShadow, FontMetrics metrics) {
        String text = segment.text;
        int width = metrics.stringWidth(text);
        Color shadow = segment.shadowColor != null ? segment.shadowColor : (drawShadow ? Color.BLACK : null);
        if (shadow != null) {
            graphics.setColor(withAlpha(shadow, alpha));
            graphics.drawString(text, x + 2, y + 1);
        }
        graphics.setColor(withAlpha(color, alpha));
        graphics.drawString(text, x + 1, y);
        if (segment.strikeColor != null) {
            graphics.setColor(withAlpha(segment.strikeColor, alpha));
            graphics.fillRect(x + 1, y - metrics.getAscent() + (int) (metrics.getAscent() * 0.7), width, 1);
        }
        if (segment.underlineColor != null) {
            graphics.setColor(withAlpha(segment.underlineColor, alpha));
            graphics.fillRect(x + 1, y + 1, width, 1);
        }
        return width;
    }

    public static int calculateAlpha(WidgetMessage msg, long currentTime, long fadeOutMs) {
        if (fadeOutMs <= 0) {
            return 255;
        }
        long age = currentTime - msg.getTimestamp();
        if (age <= fadeOutMs) {
            return 255;
        }
        double fadeProgress = Math.min(1.0, (age - fadeOutMs) / 1200.0);
        return (int) (255 * (1.0 - fadeProgress));
    }

    public static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    /**
     * Returns a cached BufferedImage for the given mod icon index.
     * Converts and caches on first access; clears cache when the modIcons array reference changes.
     */
    public static BufferedImage getModIconImage(int iconId, IndexedSprite[] modIcons) {
        if (modIcons == null || iconId < 0 || iconId >= modIcons.length) {
            return null;
        }

        if (modIcons != cachedModIconsRef) {
            iconImageCache.clear();
            cachedModIconsRef = modIcons;
        }

        if (iconImageCache.containsKey(iconId)) {
            return iconImageCache.get(iconId);
        }

        BufferedImage img = indexedSpriteToImage(modIcons[iconId]);
        iconImageCache.put(iconId, img);
        return img;
    }

    public static BufferedImage indexedSpriteToImage(IndexedSprite sprite) {
        if (sprite == null) {
            return null;
        }
        try {
            int width = sprite.getWidth();
            int height = sprite.getHeight();
            if (width <= 0 || height <= 0) {
                return null;
            }
            byte[] pixels = sprite.getPixels();
            int[] palette = sprite.getPalette();
            if (pixels == null || palette == null || palette.length == 0) {
                return null;
            }
            int totalPixels = Math.min(pixels.length, width * height);
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            int[] imgPixels = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
            for (int i = 0; i < totalPixels; i++) {
                int index = pixels[i] & 0xFF;
                if (index != 0 && index < palette.length) {
                    imgPixels[i] = palette[index] | 0xFF000000;
                }
            }
            return img;
        } catch (Exception e) {
            return null;
        }
    }

    public static String formatTimestamp(long timestamp, String format) {
        try {
            return new SimpleDateFormat(format).format(new Date(timestamp));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Builds render lines for any message type. Handles sender prefixes for PMs,
     * public chat, friends chat, and clan chat automatically based on the message type.
     */
    public static List<RenderLine> buildMessageLines(WidgetMessage msg, FontMetrics metrics,
            int widgetWidth, long currentTime, long fadeOutMs, boolean wrapText, Color textColor,
            boolean retainContextualColours, boolean hideDuplicateCount,
            FontSize fontSize, IndexedSprite[] modIcons, boolean showTimestamp, String timestampFormat,
            boolean showChannelName, ChatColorConfig chatColorConfig) {

        List<RenderLine> lines = new ArrayList<>();
        int alpha = calculateAlpha(msg, currentTime, fadeOutMs);

        ChatMessageType type = msg.getType();
        boolean hasSenderPrefix = SENDER_PREFIX_TYPES.contains(type);
        boolean isPm = PM_TYPES.contains(type);
        boolean isLoginNotification = type == ChatMessageType.LOGINLOGOUTNOTIFICATION;

        // Build header (timestamp + optional sender prefix)
        List<TextSegment> headerSegments = new ArrayList<>();
        int headerWidth = 0;

        if (showTimestamp && timestampFormat != null && !timestampFormat.isEmpty()) {
            String ts = formatTimestamp(msg.getTimestamp(), timestampFormat + " ");
            if (ts != null) {
                int width = metrics.stringWidth(ts);
                Color tsColor = retainContextualColours ? Color.WHITE : textColor;
                headerSegments.add(new TextSegment(ts, -1, width, tsColor));
                headerWidth += width;
            }
        }

        if (hasSenderPrefix && !isLoginNotification && msg.getSender() != null) {
            Color nameColor = retainContextualColours ? Color.WHITE : textColor;
            if (isPm) {
                String prefix = msg.isOutgoing() ? "To " : "From ";
                headerSegments.add(new TextSegment(prefix, -1, metrics.stringWidth(prefix), textColor));
                headerWidth += metrics.stringWidth(prefix);
            }

            if (showChannelName && msg.getChannelName() != null && !msg.getChannelName().isEmpty()) {
                Color bracketColor = retainContextualColours ? Color.WHITE : textColor;
                Color channelTextColor = retainContextualColours ? new Color(144, 144, 255) : textColor;

                String open = "[";
                headerSegments.add(new TextSegment(open, -1, metrics.stringWidth(open), bracketColor));
                headerWidth += metrics.stringWidth(open);

                String channelText = msg.getChannelName();
                headerSegments.add(new TextSegment(channelText, -1, metrics.stringWidth(channelText), channelTextColor));
                headerWidth += metrics.stringWidth(channelText);

                String close = "] ";
                headerSegments.add(new TextSegment(close, -1, metrics.stringWidth(close), bracketColor));
                headerWidth += metrics.stringWidth(close);
            }

            List<TextSegment> senderSegments = parseTextWithIcons(msg.getSender(), metrics, modIcons,
                    nameColor, fontSize);
            for (TextSegment seg : senderSegments) {
                headerSegments.add(seg);
                headerWidth += seg.width;
            }

            headerSegments.add(new TextSegment(": ", -1, metrics.stringWidth(": "), nameColor));
            headerWidth += metrics.stringWidth(": ");
        }

        // Build message body
        // Expand templates before truncating, so a long definition can't push its value past the cut.
        String messageText = expandTemplates(msg.getMessage());
        if (messageText != null && messageText.length() > MAX_MESSAGE_LENGTH) {
            messageText = messageText.substring(0, MAX_MESSAGE_LENGTH) + "...";
        }

        if (msg.getCount() > 1 && !hideDuplicateCount) {
            messageText = messageText + " (" + msg.getCount() + ")";
        }

        List<TextSegment> messageSegments = parseTextWithColoursAndIcons(messageText, metrics, modIcons,
                retainContextualColours, textColor, fontSize, chatColorConfig);

        if (!wrapText) {
            List<TextSegment> singleLine = new ArrayList<>(headerSegments);
            singleLine.addAll(messageSegments);
            lines.add(new RenderLine(singleLine, alpha));
        } else {
            int firstLineRemaining = widgetWidth - headerWidth;
            List<List<TextSegment>> wrappedLines = wrapSegments(messageSegments, metrics,
                    firstLineRemaining, widgetWidth, textColor);
            addWrappedLines(lines, alpha, headerSegments, wrappedLines);
        }

        return lines;
    }

    public static void addWrappedLines(List<RenderLine> lines, int alpha, List<TextSegment> headerSegments,
            List<List<TextSegment>> wrappedLines) {
        if (wrappedLines.isEmpty()) {
            lines.add(new RenderLine(headerSegments, alpha));
        } else {
            List<TextSegment> firstLine = new ArrayList<>(headerSegments);
            firstLine.addAll(wrappedLines.get(0));
            lines.add(new RenderLine(firstLine, alpha));

            for (int i = 1; i < wrappedLines.size(); i++) {
                lines.add(new RenderLine(wrappedLines.get(i), alpha));
            }
        }
    }

    public static int calculateIconWidth(IndexedSprite[] modIcons, int iconId, FontSize fontSize) {
        int iconWidth = 13;
        if (modIcons != null && iconId >= 0 && iconId < modIcons.length && modIcons[iconId] != null) {
            iconWidth = modIcons[iconId].getWidth() + 1;
            if (fontSize == FontSize.SMALL) {
                iconWidth = (int) (iconWidth * 0.75);
            }
        }
        return iconWidth;
    }

    public static List<TextSegment> parseTextWithIcons(String text, FontMetrics metrics,
            IndexedSprite[] modIcons, Color textColor, FontSize fontSize) {
        List<TextSegment> segments = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return segments;
        }

        text = text.replace("<lt>", "<").replace("<gt>", ">").replace("<at>", "@");

        Matcher matcher = IMG_TAG_PATTERN.matcher(text);
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                String before = text.substring(lastEnd, matcher.start());
                segments.add(new TextSegment(before, -1, metrics.stringWidth(before), textColor));
            }

            try {
                int iconId = Integer.parseInt(matcher.group(1));
                int iconWidth = calculateIconWidth(modIcons, iconId, fontSize);
                segments.add(new TextSegment("", iconId, iconWidth, textColor));
            } catch (NumberFormatException e) {
                String raw = matcher.group(0);
                segments.add(new TextSegment(raw, -1, metrics.stringWidth(raw), textColor));
            }
            lastEnd = matcher.end();
        }

        if (lastEnd < text.length()) {
            String after = text.substring(lastEnd);
            segments.add(new TextSegment(after, -1, metrics.stringWidth(after), textColor));
        }

        return segments;
    }

    /**
     * Parses text with full chat formatting tag support, icon tags, and line breaks.
     * Used for system/game messages that can contain formatting. Tags follow the game's font
     * renderer ({@code AbstractFont.decodeTag}): only {@code col=}, {@code str=}, {@code u=},
     * {@code shad=} and {@code img=} are prefix matches, every bare tag must match exactly, and any
     * unrecognised tag is skipped silently. Template variables are expanded first (see
     * {@link #expandTemplates}). {@code retainContextualColours} gates only the text colour
     * ({@code <col>} tags); strikethrough, underline and shadow always keep their tag colours.
     */
    public static List<TextSegment> parseTextWithColoursAndIcons(String text, FontMetrics metrics,
            IndexedSprite[] modIcons, boolean retainContextualColours, Color textColor,
            FontSize fontSize, ChatColorConfig chatColorConfig) {
        List<TextSegment> segments = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return segments;
        }

        text = expandTemplates(text);
        SegmentBuilder builder = new SegmentBuilder(segments, metrics, textColor);
        int i = 0;

        while (i < text.length()) {
            char c = text.charAt(i);
            int tagEnd = c == '<' ? text.indexOf('>', i + 1) : -1;
            if (tagEnd < 0) {
                builder.append(c);
                i++;
                continue;
            }

            String tag = text.substring(i + 1, tagEnd);
            i = tagEnd + 1;

            if (tag.equals("br") || tag.equals("n")) {
                builder.lineBreak();
            } else if (tag.equals("lt")) {
                builder.append('<');
            } else if (tag.equals("gt")) {
                builder.append('>');
            } else if (tag.equals("nbh")) {
                builder.append('-');
            } else if (tag.equals("at")) {
                // RuneLite escapes a literal '@' as <at> so it isn't parsed as an @col@ colour code.
                builder.append('@');
            } else if (tag.equals("colNORMAL")) {
                builder.setColor(textColor);
            } else if (tag.equals("colHIGHLIGHT")) {
                if (chatColorConfig != null) {
                    Color highlight = chatColorConfig.transparentExamineHighlight();
                    builder.setColor(highlight != null ? highlight : textColor);
                }
            } else if (tag.startsWith("col=")) {
                Color color = parseHexColor(tag.substring(4));
                if (color != null && retainContextualColours) {
                    builder.setColor(color);
                }
            } else if (tag.equals("/col")) {
                if (retainContextualColours) {
                    builder.setColor(textColor);
                }
            } else if (tag.equals("str")) {
                builder.setStrike(DEFAULT_STRIKE_COLOR);
            } else if (tag.startsWith("str=")) {
                Color color = parseHexColor(tag.substring(4));
                if (color != null) {
                    builder.setStrike(color);
                }
            } else if (tag.equals("/str")) {
                builder.setStrike(null);
            } else if (tag.equals("u")) {
                builder.setUnderline(Color.BLACK);
            } else if (tag.startsWith("u=")) {
                Color color = parseHexColor(tag.substring(2));
                if (color != null) {
                    builder.setUnderline(color);
                }
            } else if (tag.equals("/u")) {
                builder.setUnderline(null);
            } else if (tag.equals("shad")) {
                builder.setShadow(Color.BLACK);
            } else if (tag.startsWith("shad=")) {
                Color color = parseHexColor(tag.substring(5));
                if (color != null) {
                    builder.setShadow(color);
                }
            } else if (tag.equals("/shad")) {
                builder.setShadow(null);
            } else if (tag.startsWith("img=")) {
                Integer iconId = parseIconId(tag.substring(4));
                if (iconId != null) {
                    builder.icon(iconId, calculateIconWidth(modIcons, iconId, fontSize));
                }
            }
            // Any other tag (e.g. <html>) is invisible in-game: skip it.
        }

        builder.flush();
        return segments;
    }

    /** Returns the icon id, or null if it isn't a number (the game's font then ignores the tag). */
    private static Integer parseIconId(String id) {
        try {
            return Integer.parseInt(id);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Color parseHexColor(String hex) {
        try {
            return new Color(Integer.parseInt(hex, 16));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Accumulates text under the current formatting state, flushing a segment on each change. */
    private static final class SegmentBuilder {
        private final List<TextSegment> segments;
        private final FontMetrics metrics;
        private final Color baseColor;
        private final StringBuilder text = new StringBuilder();
        private Color color;
        private Color strike;
        private Color underline;
        private Color shadow;

        SegmentBuilder(List<TextSegment> segments, FontMetrics metrics, Color baseColor) {
            this.segments = segments;
            this.metrics = metrics;
            this.baseColor = baseColor;
            this.color = baseColor;
        }

        void append(char c) {
            text.append(c);
        }

        void flush() {
            if (text.length() > 0) {
                String str = text.toString();
                segments.add(new TextSegment(str, -1, metrics.stringWidth(str), color,
                        strike, underline, shadow));
                text.setLength(0);
            }
        }

        void setColor(Color newColor) {
            flush();
            color = newColor;
        }

        void setStrike(Color newStrike) {
            flush();
            strike = newStrike;
        }

        void setUnderline(Color newUnderline) {
            flush();
            underline = newUnderline;
        }

        void setShadow(Color newShadow) {
            flush();
            shadow = newShadow;
        }

        void icon(int iconId, int width) {
            flush();
            segments.add(new TextSegment("", iconId, width, color));
        }

        /** A line break, which also resets all formatting to the line's base state. */
        void lineBreak() {
            flush();
            color = baseColor;
            strike = null;
            underline = null;
            shadow = null;
            segments.add(new TextSegment("", TextSegment.LINE_BREAK, 0, color));
        }
    }

    /**
     * Expands the game's template variables: each {@code <str_NAME=VALUE>} definition is removed
     * and every later {@code <str_NAME>} reference is replaced with its value. The client does this
     * before setting chatbox widget text, but the raw template is all RuneLite exposes, so it must
     * run before tag parsing (whose unknown-tag catch-all would otherwise delete the value).
     */
    public static String expandTemplates(String text) {
        if (text == null || !text.contains("<str_")) {
            return text;
        }

        Map<String, String> vars = new HashMap<>();
        Matcher defMatcher = TEMPLATE_DEF_PATTERN.matcher(text);
        StringBuffer withoutDefs = new StringBuffer();
        while (defMatcher.find()) {
            vars.put(defMatcher.group(1), defMatcher.group(2));
            defMatcher.appendReplacement(withoutDefs, "");
        }
        defMatcher.appendTail(withoutDefs);

        Matcher refMatcher = TEMPLATE_REF_PATTERN.matcher(withoutDefs);
        StringBuffer expanded = new StringBuffer();
        while (refMatcher.find()) {
            String value = vars.getOrDefault(refMatcher.group(1), refMatcher.group(0));
            refMatcher.appendReplacement(expanded, Matcher.quoteReplacement(value));
        }
        refMatcher.appendTail(expanded);
        return expanded.toString();
    }

    public static List<List<TextSegment>> wrapSegments(List<TextSegment> segments,
            FontMetrics metrics, int firstLineWidth, int subsequentLineWidth, Color textColor) {
        List<List<TextSegment>> lines = new ArrayList<>();
        List<TextSegment> currentLine = new ArrayList<>();
        int currentWidth = firstLineWidth;

        for (TextSegment segment : segments) {
            if (segment.iconId == TextSegment.LINE_BREAK) {
                if (!currentLine.isEmpty()) {
                    lines.add(currentLine);
                    currentLine = new ArrayList<>();
                }
                currentWidth = subsequentLineWidth;
            } else if (segment.iconId >= 0) {
                if (segment.width <= currentWidth) {
                    currentLine.add(segment);
                    currentWidth -= segment.width;
                } else {
                    if (!currentLine.isEmpty()) {
                        lines.add(currentLine);
                        currentLine = new ArrayList<>();
                        currentWidth = subsequentLineWidth;
                    }
                    currentLine.add(segment);
                    currentWidth -= segment.width;
                }
            } else {
                String[] words = segment.text.split(" ", -1);
                for (int wi = 0; wi < words.length; wi++) {
                    String word = words[wi];
                    if (word.isEmpty() && wi < words.length - 1) {
                        int spaceWidth = metrics.stringWidth(" ");
                        if (spaceWidth <= currentWidth) {
                            currentLine.add(segment.withText(" ", spaceWidth));
                            currentWidth -= spaceWidth;
                        }
                        continue;
                    }

                    int wordWidth = metrics.stringWidth(word);
                    int spaceWidth = metrics.stringWidth(" ");
                    boolean needsSpace = !currentLine.isEmpty() && wi > 0;
                    int neededWidth = wordWidth + (needsSpace ? spaceWidth : 0);

                    if (neededWidth <= currentWidth) {
                        if (needsSpace) {
                            currentLine.add(segment.withText(" ", spaceWidth));
                            currentWidth -= spaceWidth;
                        }
                        currentLine.add(segment.withText(word, wordWidth));
                        currentWidth -= wordWidth;
                    } else {
                        if (!currentLine.isEmpty()) {
                            lines.add(currentLine);
                            currentLine = new ArrayList<>();
                            currentWidth = subsequentLineWidth;
                        }
                        currentLine.add(segment.withText(word, wordWidth));
                        currentWidth -= wordWidth;
                    }
                }
            }
        }

        if (!currentLine.isEmpty()) {
            lines.add(currentLine);
        }
        return lines;
    }
}

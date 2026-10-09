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
 *   <li>{@link #parseTextWithColoursAndIcons} — additionally handles {@code <col=RRGGBB>},
 *       {@code </col>}, {@code <br>}, and named colour tags. Used for message bodies.</li>
 * </ul>
 *
 * <p><b>Icon caching</b> — Converts RuneLite's {@link IndexedSprite} mod icons to
 * {@link BufferedImage} on first access and caches them. The cache is invalidated when
 * the modIcons array reference changes (e.g. after a plugin loads new icons).
 */
public final class ChatRenderUtils {

    private static final Pattern IMG_TAG_PATTERN = Pattern.compile("<img=(\\d+)>");
    private static final Pattern COL_TAG_PATTERN = Pattern.compile("<col=([0-9a-fA-F]{6})>");
    private static final Pattern COL_NAMED_PATTERN = Pattern.compile("<col(NORMAL|HIGHLIGHT)>");
    private static final Pattern COL_UNKNOWN_PATTERN = Pattern.compile("<col[^>]*>");
    private static final Pattern COL_END_PATTERN = Pattern.compile("</col>");
    private static final Pattern BR_TAG_PATTERN = Pattern.compile("<br>");
    private static final int MAX_MESSAGE_LENGTH = 500;
    private static final Color CHANNEL_NAME_COLOUR = new Color(144, 144, 255);
    // cs2 rebuildchatbox colours "Broadcast:" 0x30ff30 on the transparent chatbox.
    private static final Color BROADCAST_LABEL_COLOUR = new Color(0x30ff30);
    private static final String BROADCAST_LABEL = "Broadcast: ";

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

    public static int drawText(Graphics2D graphics, String text, Color color, int alpha, int x, int y,
            boolean drawShadow, FontMetrics metrics) {
        if (drawShadow) {
            graphics.setColor(withAlpha(Color.BLACK, alpha));
            graphics.drawString(text, x + 2, y + 1);
        }
        graphics.setColor(withAlpha(color, alpha));
        graphics.drawString(text, x + 1, y);
        return metrics.stringWidth(text);
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

            if (showChannelName) {
                headerWidth += addChannelTag(headerSegments, msg.getChannelName(), metrics,
                        retainContextualColours, textColor);
            }

            List<TextSegment> senderSegments = parseTextWithIcons(msg.getSender(), metrics, modIcons,
                    nameColor, fontSize);
            for (TextSegment seg : senderSegments) {
                headerSegments.add(seg);
                headerWidth += seg.width;
            }

            headerSegments.add(new TextSegment(": ", -1, metrics.stringWidth(": "), nameColor));
            headerWidth += metrics.stringWidth(": ");
        } else if (type == ChatMessageType.BROADCAST) {
            // Always shown, like the chatbox's label (not a channel name, so not toggleable).
            Color labelColor = retainContextualColours ? BROADCAST_LABEL_COLOUR : textColor;
            headerSegments.add(new TextSegment(BROADCAST_LABEL, -1, metrics.stringWidth(BROADCAST_LABEL), labelColor));
            headerWidth += metrics.stringWidth(BROADCAST_LABEL);
        } else if (showChannelName) {
            // Clan notifications: [clan name], [GIM group name] or [PvP Arena], snapshotted at capture.
            headerWidth += addChannelTag(headerSegments, msg.getClanTag(), metrics,
                    retainContextualColours, textColor);
        }

        // Build message body
        String messageText = msg.getMessage();
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

    /**
     * Appends a {@code [name] } channel tag to {@code segments}, coloured like the chatbox's channel
     * names. No-op for a null or empty name.
     *
     * @return the width added
     */
    private static int addChannelTag(List<TextSegment> segments, String name, FontMetrics metrics,
            boolean retainContextualColours, Color textColor) {
        if (name == null || name.isEmpty()) {
            return 0;
        }
        Color bracketColor = retainContextualColours ? Color.WHITE : textColor;
        Color channelTextColor = retainContextualColours ? CHANNEL_NAME_COLOUR : textColor;

        String open = "[";
        String close = "] ";
        int openWidth = metrics.stringWidth(open);
        int nameWidth = metrics.stringWidth(name);
        int closeWidth = metrics.stringWidth(close);
        segments.add(new TextSegment(open, -1, openWidth, bracketColor));
        segments.add(new TextSegment(name, -1, nameWidth, channelTextColor));
        segments.add(new TextSegment(close, -1, closeWidth, bracketColor));
        return openWidth + nameWidth + closeWidth;
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
     * Parses text with full color tag support, icon tags, and line breaks.
     * Used for system/game messages that can contain color formatting.
     */
    public static List<TextSegment> parseTextWithColoursAndIcons(String text, FontMetrics metrics,
            IndexedSprite[] modIcons, boolean retainContextualColours, Color textColor,
            FontSize fontSize, ChatColorConfig chatColorConfig) {
        List<TextSegment> segments = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return segments;
        }

        Color currentColor = textColor;
        StringBuilder currentText = new StringBuilder();
        int i = 0;

        while (i < text.length()) {
            Matcher imgMatcher = IMG_TAG_PATTERN.matcher(text.substring(i));
            Matcher colMatcher = COL_TAG_PATTERN.matcher(text.substring(i));
            Matcher colNamedMatcher = COL_NAMED_PATTERN.matcher(text.substring(i));
            Matcher colEndMatcher = COL_END_PATTERN.matcher(text.substring(i));
            Matcher brMatcher = BR_TAG_PATTERN.matcher(text.substring(i));

            if (brMatcher.lookingAt()) {
                if (currentText.length() > 0) {
                    String str = currentText.toString();
                    segments.add(new TextSegment(str, -1, metrics.stringWidth(str), currentColor));
                    currentText = new StringBuilder();
                }
                segments.add(new TextSegment("", TextSegment.LINE_BREAK, 0, currentColor));
                i += brMatcher.end();
            } else if (imgMatcher.lookingAt()) {
                if (currentText.length() > 0) {
                    String str = currentText.toString();
                    segments.add(new TextSegment(str, -1, metrics.stringWidth(str), currentColor));
                    currentText = new StringBuilder();
                }
                try {
                    int iconId = Integer.parseInt(imgMatcher.group(1));
                    int iconWidth = calculateIconWidth(modIcons, iconId, fontSize);
                    segments.add(new TextSegment("", iconId, iconWidth, currentColor));
                } catch (NumberFormatException e) {
                    currentText.append(imgMatcher.group(0));
                }
                i += imgMatcher.end();
            } else if (colNamedMatcher.lookingAt()) {
                if (currentText.length() > 0) {
                    String str = currentText.toString();
                    segments.add(new TextSegment(str, -1, metrics.stringWidth(str), currentColor));
                    currentText = new StringBuilder();
                }
                String colorName = colNamedMatcher.group(1);
                if ("NORMAL".equals(colorName)) {
                    currentColor = textColor;
                } else if ("HIGHLIGHT".equals(colorName) && chatColorConfig != null) {
                    Color highlight = chatColorConfig.transparentExamineHighlight();
                    currentColor = highlight != null ? highlight : textColor;
                }
                i += colNamedMatcher.end();
            } else if (colMatcher.lookingAt() && retainContextualColours) {
                if (currentText.length() > 0) {
                    String str = currentText.toString();
                    segments.add(new TextSegment(str, -1, metrics.stringWidth(str), currentColor));
                    currentText = new StringBuilder();
                }
                try {
                    currentColor = Color.decode("#" + colMatcher.group(1));
                } catch (NumberFormatException e) {
                    currentColor = textColor;
                }
                i += colMatcher.end();
            } else if (colEndMatcher.lookingAt() && retainContextualColours) {
                if (currentText.length() > 0) {
                    String str = currentText.toString();
                    segments.add(new TextSegment(str, -1, metrics.stringWidth(str), currentColor));
                    currentText = new StringBuilder();
                }
                currentColor = textColor;
                i += colEndMatcher.end();
            } else if (colMatcher.lookingAt()) {
                i += colMatcher.end();
            } else if (colEndMatcher.lookingAt()) {
                i += colEndMatcher.end();
            } else if (text.startsWith("<lt>", i)) {
                currentText.append('<');
                i += 4;
            } else if (text.startsWith("<gt>", i)) {
                currentText.append('>');
                i += 4;
            } else if (text.startsWith("<at>", i)) {
                // RuneLite escapes a literal '@' as <at> so it isn't parsed as an @col@ colour code.
                currentText.append('@');
                i += 4;
            } else {
                Matcher colUnknownMatcher = COL_UNKNOWN_PATTERN.matcher(text.substring(i));
                if (colUnknownMatcher.lookingAt()) {
                    i += colUnknownMatcher.end();
                } else {
                    currentText.append(text.charAt(i));
                    i++;
                }
            }
        }

        if (currentText.length() > 0) {
            String str = currentText.toString();
            segments.add(new TextSegment(str, -1, metrics.stringWidth(str), currentColor));
        }

        return segments;
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
                            currentLine.add(new TextSegment(" ", -1, spaceWidth, segment.color));
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
                            currentLine.add(new TextSegment(" ", -1, spaceWidth, segment.color));
                            currentWidth -= spaceWidth;
                        }
                        currentLine.add(new TextSegment(word, -1, wordWidth, segment.color));
                        currentWidth -= wordWidth;
                    } else {
                        if (!currentLine.isEmpty()) {
                            lines.add(currentLine);
                            currentLine = new ArrayList<>();
                            currentWidth = subsequentLineWidth;
                        }
                        currentLine.add(new TextSegment(word, -1, wordWidth, segment.color));
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

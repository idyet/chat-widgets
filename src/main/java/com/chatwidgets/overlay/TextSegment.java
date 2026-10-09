package com.chatwidgets.overlay;

import java.awt.Color;

/**
 * A single renderable piece of a message line — either a text string with a colour, an icon
 * reference (identified by {@code iconId} into the client's modIcons array), or a line break
 * sentinel ({@link #LINE_BREAK}).
 *
 * <p>Text segments may also carry the in-game font decorations: a strikethrough colour
 * ({@code <str>}), an underline colour ({@code <u>}) and a shadow colour ({@code <shad>}). A null
 * strike or underline colour means none; a null shadow colour means the base shadow, which the
 * renderer resolves from config.
 */
public class TextSegment {
    public static final int LINE_BREAK = -2;

    public final String text;
    public final int iconId;
    public final int width;
    public final Color color;
    public final Color strikeColor;
    public final Color underlineColor;
    public final Color shadowColor;

    public TextSegment(String text, int iconId, int width, Color color) {
        this(text, iconId, width, color, null, null, null);
    }

    public TextSegment(String text, int iconId, int width, Color color,
            Color strikeColor, Color underlineColor, Color shadowColor) {
        this.text = text;
        this.iconId = iconId;
        this.width = width;
        this.color = color;
        this.strikeColor = strikeColor;
        this.underlineColor = underlineColor;
        this.shadowColor = shadowColor;
    }

    /** A copy of this segment's formatting applied to different text (used when wrapping). */
    public TextSegment withText(String newText, int newWidth) {
        return new TextSegment(newText, iconId, newWidth, color, strikeColor, underlineColor, shadowColor);
    }
}

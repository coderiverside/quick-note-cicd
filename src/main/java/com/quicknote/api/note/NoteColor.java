package com.quicknote.api.note;

/**
 * Fixed palette of supported note colours (spec Assumption A4).
 *
 * <p>{@link #DEFAULT} represents the "no colour" state; it is stored as NULL so
 * the column stays empty for the overwhelmingly common uncoloured note.
 */
public enum NoteColor {
    DEFAULT,
    RED,
    ORANGE,
    YELLOW,
    GREEN,
    TEAL,
    BLUE,
    PURPLE,
    PINK,
    BROWN,
    GRAY
}

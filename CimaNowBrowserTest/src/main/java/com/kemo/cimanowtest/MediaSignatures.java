package com.kemo.cimanowtest;

import java.nio.charset.StandardCharsets;

/** Small response signature check, not a full decoder or a playback guarantee. */
public final class MediaSignatures {
    private MediaSignatures() { }
    public static boolean matches(byte[] bytes, int size, boolean hls) {
        if (size < 0 || size > bytes.length) return false;
        if (hls) {
            String text = new String(bytes, 0, size, StandardCharsets.UTF_8);
            if (text.startsWith("\uFEFF")) text = text.substring(1);
            return text.trim().startsWith("#EXTM3U");
        }
        return size >= 12 && bytes[4] == 'f' && bytes[5] == 't' && bytes[6] == 'y' && bytes[7] == 'p';
    }
}

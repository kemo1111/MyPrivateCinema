package com.kemo.cimanowtest;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;
public class MediaSignaturesTest {
    @Test public void hlsManifest() {
        byte[] b = "#EXTM3U\n#EXT-X-VERSION:3\n".getBytes(StandardCharsets.UTF_8);
        assertTrue(MediaSignatures.matches(b, b.length, true));
    }
    @Test public void bomManifest() {
        byte[] b = "\uFEFF#EXTM3U\n".getBytes(StandardCharsets.UTF_8);
        assertTrue(MediaSignatures.matches(b, b.length, true));
    }
    @Test public void rejectHtml() {
        byte[] b = "<html>HTTP 200 error</html>".getBytes(StandardCharsets.UTF_8);
        assertFalse(MediaSignatures.matches(b, b.length, true));
        assertFalse(MediaSignatures.matches(b, b.length, false));
    }
    @Test public void mp4Header() {
        byte[] b = {0,0,0,24,'f','t','y','p','i','s','o','m'};
        assertTrue(MediaSignatures.matches(b, b.length, false));
        assertFalse(MediaSignatures.matches(b, b.length, true));
    }
    @Test public void shortAndInvalidBounds() {
        assertFalse(MediaSignatures.matches(new byte[0], 0, false));
        assertFalse(MediaSignatures.matches(new byte[1], 8, true));
        assertFalse(MediaSignatures.matches(new byte[1], -1, true));
    }
}

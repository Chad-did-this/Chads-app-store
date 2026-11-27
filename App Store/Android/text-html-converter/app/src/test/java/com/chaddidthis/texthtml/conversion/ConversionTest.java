package com.chaddidthis.texthtml.conversion;

import org.junit.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public final class ConversionTest {
    @Test
    public void htmlCodeSurvivesBothDirectionsWithExactBytes() throws Exception {
        byte[] original = ("<!doctype html>\r\n<meta charset=\"utf-8\">\r\n"
                + "<p>café 😀 &amp; text</p>\r\n<script>const x = '<tag>';</script>\r\n").getBytes(StandardCharsets.UTF_8);
        TextDocument source = TextEncoding.decode("my.game.txt", original);
        byte[] html = Conversion.convert(source, "html", false);
        TextDocument reopened = TextEncoding.decode("my.game.html", html);
        assertArrayEquals(original, html);
        assertArrayEquals(original, Conversion.convert(reopened, "txt", false));
        assertEquals("my.game.html", Conversion.outputName(source, "html"));
        assertEquals("my.game.txt", Conversion.outputName(reopened, "txt"));
    }

    @Test
    public void byteOrderMarksAndUtf16ArePreserved() throws Exception {
        for (String encoding : new String[] {"UTF-8", "UTF-16LE", "UTF-16BE"}) {
            TextDocument source = new TextDocument("notes.txt", "A😀\r\nB\té", encoding, true, true);
            byte[] original = TextEncoding.encode(source);
            TextDocument decoded = TextEncoding.decode("notes.txt", original);
            assertEquals(source.text, decoded.text);
            assertEquals(encoding, decoded.encoding);
            assertTrue(decoded.byteOrderMark);
            assertArrayEquals(original, Conversion.convert(decoded, "html", false));
        }
    }

    @Test
    public void plainTextPageEscapesMarkupAndKeepsWhitespace() throws Exception {
        String text = "  hello\n<script>alert('x')</script>\nA & B\t\"quote\"";
        TextDocument document = new TextDocument("A & B.txt", text, "UTF-8", false, true);
        String html = new String(Conversion.convert(document, "html", true), StandardCharsets.UTF_8);
        assertTrue(html.startsWith("<!doctype html>"));
        assertTrue(html.contains("<meta charset=\"utf-8\">"));
        assertTrue(html.contains("<title>A &amp; B</title>"));
        assertTrue(html.contains("<main>  hello\n&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;\nA &amp; B\t&quot;quote&quot;</main>"));
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("white-space:pre-wrap"));
        assertArrayEquals(text.getBytes(StandardCharsets.UTF_8), Conversion.convert(document, "txt", true));
    }

    @Test
    public void emptyImportedFilesCanBeConverted() throws Exception {
        TextDocument document = TextEncoding.decode("empty.txt", new byte[0]);
        assertArrayEquals(new byte[0], Conversion.convert(document, "html", false));
        assertArrayEquals(new byte[0], Conversion.convert(document, "txt", false));
    }

    @Test
    public void fileNamesGetOneExtensionAndNoPathSeparators() {
        assertEquals("index", Conversion.baseName(" "));
        assertEquals("notes", Conversion.baseName(" notes.HTML.txt "));
        assertEquals("my.page", Conversion.baseName("my.page.htm"));
        assertEquals("_CON", Conversion.baseName("CON.txt"));
        assertFalse(Conversion.baseName("../../outside.txt").contains("/"));
        assertFalse(Conversion.baseName("C:\\folder\\file.html").contains("\\"));
        assertEquals("text/html", Conversion.mimeType("html"));
        assertEquals("text/plain", Conversion.mimeType("txt"));
    }

    @Test(expected = IOException.class)
    public void malformedUtf8IsRejectedInsteadOfCorrupted() throws Exception {
        TextEncoding.decode("bad.txt", new byte[] {(byte) 0xC3, 0x28});
    }

    @Test(expected = IOException.class)
    public void binaryFilesAreRejected() throws Exception {
        TextEncoding.decode("binary.txt", new byte[] {65, 0, 66});
    }

    @Test(expected = IOException.class)
    public void utf32IsNotMisreadAsUtf16() throws Exception {
        TextEncoding.decode("utf32.txt", new byte[] {(byte) 0xFF, (byte) 0xFE, 0, 0, 65, 0, 0, 0});
    }

    @Test(expected = IllegalArgumentException.class)
    public void unsupportedExportFormatsAreRejected() throws Exception {
        Conversion.convert(TextDocument.blank(), "pdf", false);
    }
}

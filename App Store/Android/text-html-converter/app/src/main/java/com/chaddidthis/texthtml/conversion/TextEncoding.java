package com.chaddidthis.texthtml.conversion;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;

public final class TextEncoding {
    private TextEncoding() {}





    public static TextDocument decode(String name, byte[] bytes) throws IOException {
        String encoding = "UTF-8";
        int offset = 0;

        if (startsWith(bytes, 0xFF, 0xFE, 0x00, 0x00) || startsWith(bytes, 0x00, 0x00, 0xFE, 0xFF)) {
            throw new IOException("UTF-32 is not supported. Use a UTF-8 or UTF-16 text file.");
        }

        if (startsWith(bytes, 0xEF, 0xBB, 0xBF)) {
            offset = 3;
        } else if (startsWith(bytes, 0xFF, 0xFE)) {
            encoding = "UTF-16LE";
            offset = 2;
        } else if (startsWith(bytes, 0xFE, 0xFF)) {
            encoding = "UTF-16BE";
            offset = 2;
        }

        try {
            String text = Charset.forName(encoding).newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset)).toString();

            if (text.indexOf('\0') >= 0) {
                throw new IOException("This does not appear to be a TXT or HTML text file.");
            }

            return new TextDocument(name, text, encoding, offset > 0, true);
        } catch (CharacterCodingException error) {
            throw new IOException("Cannot read this encoding. Use a UTF-8 or BOM-marked UTF-16 text file.", error);
        }
    }





    public static byte[] encode(TextDocument document) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        if (document.byteOrderMark) {
            switch (document.encoding) {
                case "UTF-8": output.write(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}); break;
                case "UTF-16LE": output.write(new byte[] {(byte) 0xFF, (byte) 0xFE}); break;
                case "UTF-16BE": output.write(new byte[] {(byte) 0xFE, (byte) 0xFF}); break;
                default: throw new IOException("Unsupported text encoding.");
            }
        }

        try {
            ByteBuffer encoded = Charset.forName(document.encoding).newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(document.text));
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            output.write(bytes);
            return output.toByteArray();
        } catch (CharacterCodingException error) {
            throw new IOException("The text contains an incomplete Unicode character.", error);
        }
    }





    private static boolean startsWith(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) return false;

        for (int index = 0; index < prefix.length; index++) {
            if ((bytes[index] & 0xFF) != prefix[index]) return false;
        }

        return true;
    }
}

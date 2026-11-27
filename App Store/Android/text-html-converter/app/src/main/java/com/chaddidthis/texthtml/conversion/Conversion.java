package com.chaddidthis.texthtml.conversion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class Conversion {
    private Conversion() {}





    public static String baseName(String name) {
        String result = name.trim()
                .replaceAll("(?i)(?:\\.(?:txt|html?))+$", "")
                .replaceAll("[<>:\"/\\\\|?*\\x00-\\x1F]", "_")
                .replaceAll("[. ]+$", "");

        if (result.isEmpty()) return "index";
        if (result.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?")) result = "_" + result;
        return result;
    }





    public static String outputName(TextDocument document, String extension) {
        requireExtension(extension);
        return baseName(document.name) + "." + extension;
    }





    public static String mimeType(String extension) {
        requireExtension(extension);
        return extension.equals("html") ? "text/html" : "text/plain";
    }





    public static byte[] convert(TextDocument document, String extension, boolean wrapText) throws IOException {
        requireExtension(extension);

        if (extension.equals("html") && wrapText) {
            String html = "<!doctype html>\n<html lang=\"en\">\n<head>\n"
                    + "<meta charset=\"utf-8\">\n<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
                    + "<title>" + escape(baseName(document.name)) + "</title>\n"
                    + "<style>body{margin:0;padding:clamp(20px,5vw,56px);font:18px/1.7 system-ui,sans-serif;"
                    + "color:#18212b;background:#f7f9fc}main{max-width:850px;margin:auto;white-space:pre-wrap;overflow-wrap:anywhere}</style>\n"
                    + "</head>\n<body><main>" + escape(document.text) + "</main></body>\n</html>\n";
            return html.getBytes(StandardCharsets.UTF_8);
        }

        return TextEncoding.encode(document);
    }





    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }





    private static void requireExtension(String extension) {
        if (!extension.equals("html") && !extension.equals("txt")) {
            throw new IllegalArgumentException("Choose TXT or HTML.");
        }
    }
}

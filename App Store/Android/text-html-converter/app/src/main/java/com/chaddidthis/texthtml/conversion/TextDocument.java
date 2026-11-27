package com.chaddidthis.texthtml.conversion;

public final class TextDocument {
    public String name;
    public String text;
    public final String encoding;
    public final boolean byteOrderMark;
    public final boolean imported;

    public TextDocument(String name, String text, String encoding, boolean byteOrderMark, boolean imported) {
        this.name = name;
        this.text = text;
        this.encoding = encoding;
        this.byteOrderMark = byteOrderMark;
        this.imported = imported;
    }





    public static TextDocument blank() {
        return new TextDocument("index", "", "UTF-8", false, false);
    }
}

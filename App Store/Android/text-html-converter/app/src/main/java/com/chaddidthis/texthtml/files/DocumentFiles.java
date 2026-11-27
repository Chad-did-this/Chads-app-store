package com.chaddidthis.texthtml.files;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import com.chaddidthis.texthtml.conversion.TextDocument;
import com.chaddidthis.texthtml.conversion.TextEncoding;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

public final class DocumentFiles {
    public static final int MAX_FILE_BYTES = 5 * 1024 * 1024;
    public static final int MAX_SESSION_CHARACTERS = 10 * 1024 * 1024;
    public static final int MAX_FILES = 32;

    private DocumentFiles() {}





    public static TextDocument read(ContentResolver resolver, Uri uri) throws IOException {
        String name = displayName(resolver, uri);
        String lower = name.toLowerCase(Locale.ROOT);

        if (!lower.endsWith(".txt") && !lower.endsWith(".html") && !lower.endsWith(".htm")) {
            throw new IOException(name + ": choose a .txt, .html or .htm file.");
        }

        try (InputStream input = resolver.openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) throw new IOException("The selected file could not be opened.");
            byte[] buffer = new byte[8192];
            int count;

            while ((count = input.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Opening files was interrupted.");
                if (output.size() + count > MAX_FILE_BYTES) throw new IOException(name + ": maximum file size is 5 MB.");
                output.write(buffer, 0, count);
            }

            return TextEncoding.decode(name, output.toByteArray());
        }
    }





    public static void write(ContentResolver resolver, Uri uri, byte[] bytes) throws IOException {
        try (OutputStream output = resolver.openOutputStream(uri, "wt")) {
            if (output == null) throw new IOException("The destination could not be opened.");
            output.write(bytes);
            output.flush();
        }
    }





    public static Uri create(ContentResolver resolver, Uri tree, String mime, String name) throws IOException {
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
        Uri created = DocumentsContract.createDocument(resolver, parent, mime, name);
        if (created == null) throw new IOException("The selected folder could not create a file.");
        return created;
    }





    public static void removeFailedOutput(ContentResolver resolver, Uri uri) {
        try { DocumentsContract.deleteDocument(resolver, uri); } catch (Exception ignored) {}
    }





    private static String displayName(ContentResolver resolver, Uri uri) throws IOException {
        try (Cursor cursor = resolver.query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getString(0);
        }

        throw new IOException("This file provider did not supply a file name.");
    }
}

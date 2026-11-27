package com.chaddidthis.texthtml.home;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.chaddidthis.texthtml.conversion.Conversion;
import com.chaddidthis.texthtml.conversion.TextDocument;
import com.chaddidthis.texthtml.files.DocumentFiles;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ConverterState extends ViewModel {
    public final ArrayList<TextDocument> documents = new ArrayList<>();
    public final MutableLiveData<Integer> changes = new MutableLiveData<>(0);
    public int selected;
    public boolean wrapText;
    public boolean initialized;
    public boolean busy;
    public String message = "Open files, or paste text below. Your original files stay unchanged.";
    public boolean error;
    public String pendingExtension;
    public boolean pendingBatch;
    public boolean pendingWrap;
    public int pendingIndex;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public ConverterState() {
        documents.add(TextDocument.blank());
    }





    public TextDocument current() {
        return documents.get(selected);
    }





    public void announce(String text, boolean failed) {
        message = text;
        error = failed;
        changes.setValue(changes.getValue() + 1);
    }





    public void select(int index) {
        if (index < 0 || index >= documents.size() || index == selected) return;
        selected = index;
        announce("Selected " + current().name, false);
    }





    public void addBlank() {
        if (!current().imported && current().text.isEmpty()) return;
        if (documents.size() >= DocumentFiles.MAX_FILES) {
            announce("You can have up to 32 files open at once. Remove a file first.", true);
            return;
        }

        TextDocument document = TextDocument.blank();
        document.name = "untitled_" + (documents.size() + 1);
        documents.add(document);
        selected = documents.size() - 1;
        announce("New text. Paste or type below.", false);
    }





    public void removeCurrent() {
        documents.remove(selected);
        if (documents.isEmpty()) documents.add(TextDocument.blank());
        selected = Math.min(selected, documents.size() - 1);
        announce("Removed from this session. The original file was not deleted.", false);
    }





    public int editorLimit() {
        int remaining = DocumentFiles.MAX_SESSION_CHARACTERS;

        for (int index = 0; index < documents.size(); index++) {
            if (index != selected) remaining -= documents.get(index).text.length();
        }

        return Math.min(DocumentFiles.MAX_FILE_BYTES, Math.max(0, remaining));
    }





    public void importFiles(Context context, List<Uri> picked) {
        if (busy) return;
        ArrayList<Uri> unique = new ArrayList<>(new LinkedHashSet<>(picked));
        if (unique.isEmpty()) return;

        boolean replaceBlank = documents.size() == 1 && !current().imported && current().text.isEmpty();
        int existing = replaceBlank ? 0 : documents.size();
        if (existing + unique.size() > DocumentFiles.MAX_FILES) {
            announce("Choose fewer files. You can have up to 32 files open at once.", true);
            return;
        }

        int existingCharacters = 0;
        for (TextDocument document : documents) existingCharacters += document.text.length();
        final int initialCharacters = existingCharacters;
        ContentResolver resolver = context.getApplicationContext().getContentResolver();
        busy = true;
        announce("Opening " + unique.size() + " file(s)…", false);

        worker.execute(() -> {
            ArrayList<TextDocument> opened = new ArrayList<>();
            ArrayList<String> failures = new ArrayList<>();
            int characters = initialCharacters;

            for (Uri uri : unique) {
                try {
                    TextDocument document = DocumentFiles.read(resolver, uri);
                    if (characters + document.text.length() > DocumentFiles.MAX_SESSION_CHARACTERS) {
                        throw new IOException(document.name + ": this session is full. Save and remove some files first.");
                    }
                    characters += document.text.length();
                    opened.add(document);
                } catch (Exception failure) {
                    failures.add(reason(failure));
                }
            }

            main.post(() -> {
                busy = false;
                if (!opened.isEmpty()) {
                    if (replaceBlank) documents.clear();
                    selected = documents.size();
                    documents.addAll(opened);
                }
                String result = "Opened " + opened.size() + " file(s).";
                if (!failures.isEmpty()) result += " Skipped " + failures.size() + ": " + failures.get(0);
                announce(result, !failures.isEmpty());
            });
        });
    }





    public void prepareExport(String extension, boolean all) {
        pendingExtension = extension;
        pendingBatch = all;
        pendingWrap = wrapText;
        pendingIndex = selected;
        announce("Choose where to save the " + extension.toUpperCase(java.util.Locale.ROOT) + " output.", false);
    }





    public void cancelExport() {
        pendingExtension = null;
        announce("Save cancelled. Your text is still here.", false);
    }





    public void saveTo(Context context, Uri destination, boolean batchResult) {
        if (pendingExtension == null) {
            announce("Choose Save again to start a new export.", true);
            return;
        }
        if (batchResult != pendingBatch) {
            cancelExport();
            return;
        }

        String extension = pendingExtension;
        boolean batch = pendingBatch;
        boolean wrap = pendingWrap;
        ArrayList<TextDocument> exports = new ArrayList<>();
        if (batch) exports.addAll(documents); else exports.add(documents.get(pendingIndex));
        pendingExtension = null;
        busy = true;
        announce("Saving " + exports.size() + " file(s)…", false);
        ContentResolver resolver = context.getApplicationContext().getContentResolver();

        worker.execute(() -> {
            int saved = 0;
            ArrayList<String> failures = new ArrayList<>();

            for (TextDocument document : exports) {
                Uri output = batch ? null : destination;
                try {
                    byte[] bytes = Conversion.convert(document, extension, wrap);
                    if (batch) output = DocumentFiles.create(resolver, destination, Conversion.mimeType(extension), Conversion.outputName(document, extension));
                    DocumentFiles.write(resolver, output, bytes);
                    saved++;
                } catch (Exception failure) {
                    if (output != null) DocumentFiles.removeFailedOutput(resolver, output);
                    failures.add(document.name + ": " + reason(failure));
                }
            }

            int completed = saved;
            main.post(() -> {
                busy = false;
                String result = batch ? "Saved " + completed + " ." + extension + " file(s) in your selected folder."
                        : completed == 1 ? "Saved " + Conversion.outputName(exports.get(0), extension) + "." : "The file was not saved.";
                if (!failures.isEmpty()) result += " Failed " + failures.size() + ": " + failures.get(0);
                announce(result, !failures.isEmpty());
            });
        });
    }





    public void saveSession(File file) throws IOException {
        try {
            JSONArray entries = new JSONArray();
            for (TextDocument document : documents) {
                entries.put(new JSONObject().put("name", document.name).put("text", document.text)
                        .put("encoding", document.encoding).put("bom", document.byteOrderMark).put("imported", document.imported));
            }

            JSONObject saved = new JSONObject().put("documents", entries).put("selected", selected).put("wrap", wrapText)
                    .put("pending", pendingExtension == null ? "" : pendingExtension).put("batch", pendingBatch)
                    .put("pendingWrap", pendingWrap).put("pendingIndex", pendingIndex);

            File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(saved.toString().getBytes(StandardCharsets.UTF_8));
                output.getFD().sync();
            }
            if (!temporary.renameTo(file)) throw new IOException("Could not save the current session.");
        } catch (org.json.JSONException failure) {
            throw new IOException("Could not save the current session.", failure);
        }
    }





    public void restoreSession(File file) {
        if (!file.isFile()) return;

        try {
            if (file.length() > 80L * 1024 * 1024) throw new IOException("Session is too large.");
            JSONObject saved = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            JSONArray entries = saved.getJSONArray("documents");
            if (entries.length() < 1 || entries.length() > DocumentFiles.MAX_FILES) throw new IOException("Invalid session.");
            ArrayList<TextDocument> restored = new ArrayList<>();

            for (int index = 0; index < entries.length(); index++) {
                JSONObject document = entries.getJSONObject(index);
                restored.add(new TextDocument(document.getString("name"), document.getString("text"), document.getString("encoding"),
                        document.getBoolean("bom"), document.getBoolean("imported")));
            }

            documents.clear();
            documents.addAll(restored);
            selected = Math.max(0, Math.min(saved.optInt("selected"), documents.size() - 1));
            wrapText = saved.optBoolean("wrap");
            String extension = saved.optString("pending");
            pendingExtension = extension.equals("txt") || extension.equals("html") ? extension : null;
            pendingBatch = saved.optBoolean("batch");
            pendingWrap = saved.optBoolean("pendingWrap");
            pendingIndex = Math.max(0, Math.min(saved.optInt("pendingIndex"), documents.size() - 1));
            message = "Your text has been restored.";
        } catch (Exception failure) {
            message = "The previous session could not be restored. Open your source files again.";
            error = true;
        }
    }





    private static String reason(Exception failure) {
        String message = failure.getMessage();
        return message == null || message.isEmpty() ? "The file provider could not complete this operation." : message;
    }





    @Override
    protected void onCleared() {
        worker.shutdownNow();
    }
}

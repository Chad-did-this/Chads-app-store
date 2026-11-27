package com.chaddidthis.texthtml.home;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.lifecycle.ViewModelProvider;
import com.chaddidthis.texthtml.conversion.Conversion;
import com.chaddidthis.texthtml.conversion.TextDocument;
import java.io.File;
import java.util.ArrayList;
import java.util.UUID;

public final class MainActivity extends ComponentActivity {
    private static final int FOREGROUND = Color.rgb(237, 243, 250);
    private static final int MUTED = Color.rgb(176, 191, 208);
    private static final int ACCENT = Color.rgb(134, 228, 233);
    private final ArrayList<View> controls = new ArrayList<>();
    private ConverterState state;
    private LinearLayout page;
    private EditText editor;
    private EditText filename;
    private TextView counter;
    private TextView status;
    private Spinner files;
    private CheckBox wrap;
    private LinearLayout batchActions;
    private TextDocument displayed;
    private boolean updating;
    private String sessionName;

    private final ActivityResultLauncher<String[]> openFiles = registerForActivityResult(
            new ActivityResultContracts.OpenMultipleDocuments(), uris -> state.importFiles(getApplicationContext(), uris));

    private final ActivityResultLauncher<String> saveHtml = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/html"), uri -> receiveDestination(uri, false));

    private final ActivityResultLauncher<String> saveTxt = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/plain"), uri -> receiveDestination(uri, false));

    private final ActivityResultLauncher<Uri> saveFolder = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(), uri -> receiveDestination(uri, true));





    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        state = new ViewModelProvider(this).get(ConverterState.class);
        sessionName = savedInstanceState == null ? "session-" + UUID.randomUUID() + ".json" : savedInstanceState.getString("session");
        if (sessionName == null || !sessionName.matches("session-[a-f0-9-]+\\.json")) sessionName = "session-" + UUID.randomUUID() + ".json";

        if (!state.initialized) {
            if (savedInstanceState != null) state.restoreSession(new File(getCacheDir(), sessionName));
            state.initialized = true;
        }

        buildScreen();
        state.changes.observe(this, ignored -> render());
    }





    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(24), dp(20), dp(28));
        scroll.addView(page);
        setContentView(scroll);
        applyInsets(scroll);

        label(".txt    .html", 16, ACCENT);
        label("TXT & HTML Converter", 28, FOREGROUND);
        label("Open text files. Save as TXT or HTML.", 16, MUTED);
        gap(12);
        button(page, "Open TXT / HTML files", true, () -> {
            try { openFiles.launch(new String[] {"text/plain", "text/html", "application/xhtml+xml", "application/octet-stream"}); }
            catch (Exception failure) { state.announce("The file picker could not open. Check that your Files app is enabled.", true); }
        });

        LinearLayout editing = row(page);
        button(editing, "New text", false, () -> { state.addBlank(); editor.requestFocus(); });
        button(editing, "Remove selected", false, () -> state.removeCurrent());

        gap(12);
        label("Selected file", 16, FOREGROUND);
        files = new Spinner(this);
        files.setMinimumHeight(dp(48));
        controls.add(files);
        page.addView(files, width());
        files.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!updating) state.select(position);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        label("File name", 16, FOREGROUND);
        filename = field(false);
        filename.setHint("index");
        filename.setFilters(new InputFilter[] {new InputFilter.LengthFilter(180)});
        filename.addTextChangedListener(watcher(() -> {
            if (!updating) state.current().name = filename.getText().toString();
        }));
        label("The save button adds .txt or .html.", 14, MUTED);
        gap(10);

        label("Text or HTML code", 16, FOREGROUND);
        editor = field(true);
        editor.setHint("Paste or type here…");
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setMinLines(8);
        editor.setMaxLines(16);
        editor.addTextChangedListener(watcher(() -> {
            if (!updating) state.current().text = editor.getText().toString();
            updateCount();
        }));
        counter = label("", 14, MUTED);

        wrap = new CheckBox(this);
        wrap.setText("Make plain text into a readable HTML page");
        wrap.setTextColor(FOREGROUND);
        wrap.setTextSize(16);
        wrap.setMinHeight(dp(48));
        wrap.setButtonTintList(ColorStateList.valueOf(ACCENT));
        wrap.setOnCheckedChangeListener((button, checked) -> { if (!updating) state.wrapText = checked; });
        controls.add(wrap);
        page.addView(wrap, width());
        label("Leave this off when your TXT already contains HTML code. Saving as TXT keeps the full source code.", 14, MUTED);
        gap(12);

        button(page, "Save selected as .html", true, () -> beginExport("html", false));
        button(page, "Save selected as .txt", false, () -> beginExport("txt", false));
        batchActions = new LinearLayout(this);
        batchActions.setOrientation(LinearLayout.VERTICAL);
        page.addView(batchActions, width());
        button(batchActions, "Save all as .html", false, () -> beginExport("html", true));
        button(batchActions, "Save all as .txt", false, () -> beginExport("txt", true));

        status = label("", 16, Color.rgb(155, 232, 193));
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        gap(10);
        label("Works offline. Choose one or several files. Original files are kept; you choose where new copies are saved.", 14, MUTED);
    }





    private void render() {
        updating = true;
        boolean enabled = !state.busy && state.pendingExtension == null;
        for (View control : controls) control.setEnabled(enabled);
        status.setText(state.message);
        status.setTextColor(state.error ? Color.rgb(255, 181, 181) : Color.rgb(155, 232, 193));
        batchActions.setVisibility(state.documents.size() > 1 ? View.VISIBLE : View.GONE);
        wrap.setChecked(state.wrapText);

        ArrayList<String> names = new ArrayList<>();
        for (int index = 0; index < state.documents.size(); index++) names.add((index + 1) + ". " + state.documents.get(index).name);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        files.setAdapter(adapter);
        files.setSelection(state.selected, false);

        if (displayed != state.current()) {
            displayed = state.current();
            filename.setText(Conversion.baseName(displayed.name));
            editor.setFilters(new InputFilter[0]);
            editor.setText(displayed.text);
            editor.setSelection(0);
            editor.setFilters(new InputFilter[] {new InputFilter.LengthFilter(state.editorLimit())});
        }

        updateCount();
        updating = false;
    }





    private void beginExport(String extension, boolean all) {
        if (state.busy || state.pendingExtension != null) return;
        if (!all && !state.current().imported && state.current().text.isEmpty()) {
            state.announce("Open a file or paste text first.", true);
            return;
        }

        state.prepareExport(extension, all);

        try {
            if (all) {
                saveFolder.launch(null);
            } else {
                String name = Conversion.outputName(state.current(), extension);
                if (extension.equals("html")) saveHtml.launch(name); else saveTxt.launch(name);
            }
        } catch (Exception failure) {
            state.cancelExport();
            state.announce("The save picker could not open. Check that your Files app is enabled.", true);
        }
    }





    private void receiveDestination(Uri uri, boolean batch) {
        if (uri == null) state.cancelExport(); else state.saveTo(getApplicationContext(), uri, batch);
    }





    private void updateCount() {
        if (counter == null || editor == null) return;
        String text = editor.getText().toString();
        int characters = text.codePointCount(0, text.length());
        counter.setText(java.text.NumberFormat.getIntegerInstance().format(characters) + " characters");
    }





    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setPadding(0, dp(5), 0, dp(7));
        page.addView(view, width());
        return view;
    }





    private EditText field(boolean multiline) {
        EditText input = new EditText(this);
        input.setTextSize(16);
        input.setTextColor(FOREGROUND);
        input.setHintTextColor(MUTED);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        input.setSingleLine(!multiline);
        input.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(25, 33, 43));
        background.setCornerRadius(dp(10));
        background.setStroke(dp(1), Color.rgb(64, 84, 107));
        input.setBackground(background);
        controls.add(input);
        page.addView(input, width());
        return input;
    }





    private void button(LinearLayout parent, String text, boolean primary, Runnable action) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setMinHeight(dp(52));
        button.setTextColor(primary ? Color.rgb(14, 37, 45) : FOREGROUND);
        button.setBackgroundTintList(ColorStateList.valueOf(primary ? ACCENT : Color.rgb(38, 53, 69)));
        button.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams layout = parent.getOrientation() == LinearLayout.HORIZONTAL
                ? new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1)
                : width();
        layout.setMargins(0, dp(3), dp(4), dp(3));
        parent.addView(button, layout);
        controls.add(button);
    }





    private LinearLayout row(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(row, width());
        return row;
    }





    private void gap(int height) {
        page.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height)));
    }





    private LinearLayout.LayoutParams width() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }





    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }





    private TextWatcher watcher(Runnable changed) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable value) { changed.run(); }
        };
    }





    private void applyInsets(View root) {
        if (Build.VERSION.SDK_INT < 35) return;
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        root.requestApplyInsets();
    }





    @Override
    protected void onSaveInstanceState(Bundle outState) {
        try {
            state.saveSession(new File(getCacheDir(), sessionName));
            outState.putString("session", sessionName);
        } catch (java.io.IOException failure) {
            state.announce("Your text is open, but Android could not save a recovery copy. Save your output before closing.", true);
        }
        super.onSaveInstanceState(outState);
    }





    @Override
    protected void onDestroy() {
        if (isFinishing() && sessionName != null) {
            new File(getCacheDir(), sessionName).delete();
            new File(getCacheDir(), sessionName + ".tmp").delete();
        }
        super.onDestroy();
    }
}

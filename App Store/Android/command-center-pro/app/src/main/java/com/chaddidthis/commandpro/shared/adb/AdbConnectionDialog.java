package com.chaddidthis.commandpro.shared.adb;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;

public final class AdbConnectionDialog {
    public interface Operation {
        String run(LocalAdb adb) throws Exception;
    }

    public interface Runner {
        void run(String name, Operation operation);
    }

    private AdbConnectionDialog() {}

    public static void show(Activity activity, Runner runner) {
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(activity, 20), dp(activity, 12), dp(activity, 20), dp(activity, 12));

        note(form, "Enable Wireless debugging in Developer options. Paste each IP address and port together, exactly as Android shows it. Keep the pairing code window visible using split screen.");

        button(form, "Open Developer options", view -> {
            try {
                activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
            } catch (ActivityNotFoundException unavailable) {
                try {
                    activity.startActivity(new Intent(Settings.ACTION_SETTINGS));
                } catch (ActivityNotFoundException missing) {
                    toast(activity, "Android settings unavailable");
                }
            }
        });

        note(form, "1. Pair: use the address and PIN from ‘Pair device with pairing code’.");
        EditText pairingAddress = field(form, "Pairing IP address:port", false);
        EditText code = field(form, "Six-digit pairing PIN", true);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setTitle("Connect to this phone")
            .setView(scroll)
            .setNegativeButton("Close", null)
            .create();

        button(form, "PAIR", view -> {
            try {
                AdbEndpoint endpoint = AdbEndpoint.parse(pairingAddress.getText().toString());
                String pin = code.getText().toString().trim();
                if (!pin.matches("[0-9]{6}")) {
                    throw new IllegalArgumentException("Enter the six-digit pairing PIN separately");
                }
                code.setText("");
                dialog.dismiss();
                runner.run("Pairing", adb -> {
                    endpoint.requireThisPhone();
                    if (adb.isConnected()) adb.disconnect();
                    if (!adb.pair(endpoint.host, endpoint.port, pin)) throw new IOException("Pairing rejected");
                    return "Paired. Open CONNECT again and paste the IP address & port from the main Wireless debugging screen. Your pairing identity is saved.";
                });
            } catch (IllegalArgumentException invalid) {
                toast(activity, invalid.getMessage());
            }
        });

        note(form, "2. Connect: use ‘IP address & port’ from the main Wireless debugging screen. Its port is different from the pairing port. No PIN is needed here.");
        EditText connectionAddress = field(form, "Connection IP address:port", false);
        button(form, "CONNECT", view -> {
            try {
                AdbEndpoint endpoint = AdbEndpoint.parse(connectionAddress.getText().toString());
                dialog.dismiss();
                runner.run("Connecting", adb -> {
                    endpoint.requireThisPhone();
                    if (adb.isConnected()) adb.disconnect();
                    if (!adb.connect(endpoint.host, endpoint.port)) throw new IOException("Connection rejected");
                    return "Connected to this phone. Commands run as Android shell, not root.";
                });
            } catch (IllegalArgumentException invalid) {
                toast(activity, invalid.getMessage());
            }
        });

        button(form, "DISCONNECT", view -> {
            dialog.dismiss();
            runner.run("Disconnecting", adb -> {
                adb.disconnect();
                return "Disconnected. Disable Wireless debugging when finished.";
            });
        });

        dialog.show();
    }

    private static EditText field(LinearLayout parent, String hint, boolean password) {
        EditText field = new EditText(parent.getContext());
        field.setHint(hint);
        field.setSingleLine(true);
        field.setInputType(password
            ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD
            : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        if (password) {
            field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
            field.setSaveEnabled(false);
        }
        parent.addView(field);
        return field;
    }

    private static void note(LinearLayout parent, String text) {
        TextView note = new TextView(parent.getContext());
        note.setText(text);
        note.setTextSize(14);
        parent.addView(note);
    }

    private static void button(LinearLayout parent, String title, View.OnClickListener action) {
        Button button = new Button(parent.getContext());
        button.setText(title);
        button.setAllCaps(false);
        button.setOnClickListener(action);
        parent.addView(button);
    }

    private static void toast(Activity activity, String message) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}

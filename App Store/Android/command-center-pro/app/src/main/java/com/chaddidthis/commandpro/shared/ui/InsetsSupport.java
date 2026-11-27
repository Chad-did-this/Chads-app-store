package com.chaddidthis.commandpro.shared.ui;

import android.graphics.Insets;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;

public final class InsetsSupport {
    private InsetsSupport() {}

    public static void apply(View view) {
        if (Build.VERSION.SDK_INT < 35) return;
        final int left=view.getPaddingLeft(), top=view.getPaddingTop();
        final int right=view.getPaddingRight(), bottom=view.getPaddingBottom();
        view.setOnApplyWindowInsetsListener((v,insets)->{
            Insets safe=insets.getInsets(WindowInsets.Type.systemBars() |
                    WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            v.setPadding(left+safe.left,top+safe.top,right+safe.right,bottom+safe.bottom);
            return insets;
        });
        view.requestApplyInsets();
    }
}

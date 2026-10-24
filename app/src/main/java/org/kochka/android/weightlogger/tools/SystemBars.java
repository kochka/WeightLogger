package org.kochka.android.weightlogger.tools;

import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.kochka.android.weightlogger.R;

/* Edge-to-edge is mandatory when targeting Android 16 (API 36):
   the toolbar extends under the status bar, the content is padded
   to stay clear of the navigation bar, display cutout and keyboard. */
public class SystemBars {

  public static void setup(ComponentActivity activity) {
    // Light status bar icons over the colored toolbar
    EdgeToEdge.enable(activity, SystemBarStyle.dark(Color.TRANSPARENT));

    View root = ((ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0);
    View toolbar = activity.findViewById(R.id.actionbar);
    final int toolbarPaddingTop = toolbar.getPaddingTop();

    ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
      Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
      Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
      v.setPadding(bars.left, 0, bars.right, Math.max(bars.bottom, ime.bottom));
      toolbar.setPadding(toolbar.getPaddingLeft(), toolbarPaddingTop + bars.top, toolbar.getPaddingRight(), toolbar.getPaddingBottom());
      return WindowInsetsCompat.CONSUMED;
    });
  }
}

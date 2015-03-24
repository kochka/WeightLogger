package org.kochka.android.weightlogger.fragments;

import android.os.Bundle;
import android.preference.PreferenceFragment;

import org.kochka.android.weightlogger.R;

public class NestedPreferenceFragment extends PreferenceFragment {

  public static final int NESTED_SCREEN_FIELDS = 1;

  private static final String TAG_KEY = "NESTED_KEY";

  public static NestedPreferenceFragment newInstance(int key) {
    NestedPreferenceFragment fragment = new NestedPreferenceFragment();
    // supply arguments to bundle.
    Bundle args = new Bundle();
    args.putInt(TAG_KEY, key);
    fragment.setArguments(args);
    return fragment;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {

    super.onCreate(savedInstanceState);

    checkPreferenceResource();
  }

  private void checkPreferenceResource() {
    int key = getArguments().getInt(TAG_KEY);

    switch (key) {
      case NESTED_SCREEN_FIELDS:
        addPreferencesFromResource(R.xml.preferences_fields);
        break;

      default:
        break;
    }
  }
}
/*
  Copyright 2015 Sébastien Vrillaud
  
  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at
  
      http://www.apache.org/licenses/LICENSE-2.0
  
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
*/
package org.kochka.android.weightlogger;

import org.kochka.android.weightlogger.tools.SystemBars;

import org.kochka.android.weightlogger.fragments.NestedPreferenceFragment;
import org.kochka.android.weightlogger.fragments.PreferencesFragment;

import android.os.Bundle;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.res.ResourcesCompat;

import android.view.View;

public class EditPreferences extends AppCompatActivity implements PreferencesFragment.Callback {

  private static final String TAG_NESTED = "TAG_NESTED";

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.preferences);
    SystemBars.setup(this);

    Toolbar actionBar = (Toolbar) findViewById(R.id.actionbar);
    setSupportActionBar(actionBar);
    actionBar.setNavigationIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.baseline_arrow_back_24, getTheme()));
    actionBar.setNavigationOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        getOnBackPressedDispatcher().onBackPressed();
      }
    });
    getSupportActionBar().setTitle(R.string.pref);

    getFragmentManager().beginTransaction()
                        .replace(R.id.preferences, new PreferencesFragment()).commit();

    setResult(RESULT_OK);

    // Pop nested preference screens on back, otherwise let the system finish the activity
    OnBackPressedCallback nestedBackCallback = new OnBackPressedCallback(getFragmentManager().getBackStackEntryCount() > 0) {
      @Override
      public void handleOnBackPressed() {
        getFragmentManager().popBackStack();
      }
    };
    getOnBackPressedDispatcher().addCallback(this, nestedBackCallback);
    getFragmentManager().addOnBackStackChangedListener(() ->
      nestedBackCallback.setEnabled(getFragmentManager().getBackStackEntryCount() > 0));
  }

  @Override
  public void onNestedPreferenceSelected(int key) {
    getFragmentManager().beginTransaction().replace(R.id.preferences, NestedPreferenceFragment.newInstance(key), TAG_NESTED).addToBackStack(TAG_NESTED).commit();
  }
}
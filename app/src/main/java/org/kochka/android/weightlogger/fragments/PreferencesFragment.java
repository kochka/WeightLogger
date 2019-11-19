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
package org.kochka.android.weightlogger.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.text.InputType;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.kochka.android.weightlogger.R;
import org.kochka.android.weightlogger.data.Database;
import org.kochka.android.weightlogger.data.Measurement;
import org.kochka.android.weightlogger.tools.Export;
import org.kochka.android.weightlogger.tools.StorageNotMountedException;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Calendar;
import java.util.Locale;


public class PreferencesFragment extends PreferenceFragment implements Preference.OnPreferenceClickListener, SharedPreferences.OnSharedPreferenceChangeListener {

  private Callback mCallback;

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);

    if (activity instanceof Callback) {
      mCallback = (Callback) activity;
    } else {
      throw new IllegalStateException("URLCallback interface not implement");
    }
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    addPreferencesFromResource(R.xml.preferences);
    this.initSummaries(this.getPreferenceScreen());

    // Limit age preference to numbers
    ((EditTextPreference) findPreference("age")).getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);

    findPreference("optional_fields").setOnPreferenceClickListener(this);
    findPreference("db_backup").setOnPreferenceClickListener(this);
    findPreference("db_restore").setOnPreferenceClickListener(this);
    findPreference("db_purge").setOnPreferenceClickListener(this);
    findPreference("db_loadtest").setOnPreferenceClickListener(this);
    findPreference("about").setOnPreferenceClickListener(this);
  }

  @Override
  public void onResume() {
    super.onResume();
    getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
  }

  @Override
  public void onPause() {
    getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    super.onPause();
  }

  @Override
  public boolean onPreferenceClick(Preference preference) {
    if (preference.getKey().equals("optional_fields"))
      mCallback.onNestedPreferenceSelected(NestedPreferenceFragment.NESTED_SCREEN_FIELDS);
    else if (preference.getKey().equals("db_backup"))
      dbBackup();
    else if (preference.getKey().equals("db_restore"))
      dbRestore();
    else if (preference.getKey().equals("db_purge"))
      dbPurge();
    else if (preference.getKey().equals("db_loadtest"))
      dbLoadTest();
    else if (preference.getKey().equals("about"))
      showAbout();

    return false;
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    Preference pref = findPreference(key);
    this.setSummary(pref);
  }

  /* Set the summaries of all preferences */
  private void initSummaries(PreferenceGroup pg) {
    for (int i = 0; i < pg.getPreferenceCount(); ++i) {
      Preference p = pg.getPreference(i);
      if (p instanceof PreferenceGroup)
        this.initSummaries((PreferenceGroup) p); // recursion
      else
        this.setSummary(p);
    }
  }

  /* Set the summaries of the given preference */
  private void setSummary(Preference pref) {
    if (pref instanceof ListPreference) {
      ListPreference sPref = (ListPreference) pref;
      pref.setSummary(sPref.getEntry());
    } else if (pref instanceof EditTextPreference) {
      EditTextPreference sPref = (EditTextPreference) pref;
      if (sPref.getText() != null) {
        if (sPref.getText().length() == 0) {
          sPref.setText(null);
          pref.setSummary("");
        } else {
          if (sPref.getKey().indexOf("password") != -1) {
            String masked_pwd = "";
            for (int i = 0; i < sPref.getText().length(); i++)
              masked_pwd += "*";
            pref.setSummary(masked_pwd);
          } else
            pref.setSummary(sPref.getText());
        }
      }
    }
  }

  /* Backup database */
  private void dbBackup() {
    try {
      String filename = Export.database(getActivity());
      Toast.makeText(getActivity(), String.format(getString(R.string.pref_backup_database_ok), filename), Toast.LENGTH_SHORT).show();
    } catch (StorageNotMountedException e) {
      Toast.makeText(getActivity(), R.string.storage_not_mounted, Toast.LENGTH_SHORT).show();
    } catch (Exception e) {
      Toast.makeText(getActivity(), R.string.pref_backup_database_failed, Toast.LENGTH_SHORT).show();
    }
  }

  /* Restore database */
  private void dbRestore() {
    File dir = new File(Export.path(getActivity()));
    FilenameFilter filter = new FilenameFilter() {
      public boolean accept(File dir, String name) {
        return name.endsWith(".bkp");
      }
    };
    final String[] bkpList = dir.list(filter);

    if (bkpList != null && bkpList.length > 0) {
      AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
      builder.setTitle(R.string.pref_restore_database);
      builder.setItems(bkpList, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, final int item) {
          new AlertDialog.Builder(getActivity())
                  .setTitle(R.string.pref_restore_database)
                  .setMessage(String.format(getString(R.string.pref_restore_database_confirm), bkpList[item]))
                  .setIcon(android.R.drawable.ic_dialog_alert)
                  .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                      try {
                        Database.getRawInstance(getActivity()).importDatabase(Export.path(getActivity()) + "/" + bkpList[item]);
                        Toast.makeText(getActivity(), R.string.pref_restore_database_ok, Toast.LENGTH_SHORT).show();
                      } catch (Exception e) {
                        Toast.makeText(getActivity(), R.string.pref_restore_database_failed, Toast.LENGTH_SHORT).show();
                      }
                    }})
                  .setNegativeButton(android.R.string.no, null).show();
        }
      });
      AlertDialog alert = builder.create();
      alert.show();
    } else {
      Toast.makeText(getActivity(), R.string.pref_restore_database_nofiles, Toast.LENGTH_SHORT).show();
    }
  }

  /* Purge database */
  private void dbPurge() {
    new AlertDialog.Builder(getActivity())
            .setTitle(R.string.pref_purge_database)
            .setMessage(R.string.pref_purge_database_confirm)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int whichButton) {
                Measurement.purge(getActivity());
                Toast.makeText(getActivity(), R.string.pref_database_purged, Toast.LENGTH_SHORT).show();
              }})
            .setNegativeButton(android.R.string.no, null).show();
  }

  /* Load test data */
  private void dbLoadTest() {
    ProgressBar progressBar = (ProgressBar) getActivity().findViewById(R.id.progress_spinner);
    progressBar.setVisibility(View.VISIBLE);

    new Thread(new Runnable() {
      public void run() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -40);

        for (int i=0; i<40; i++){
          new Measurement(getActivity(),
                  (70 + (int)(Math.random() * 110)/10.0f),
                  (15 + (int)(Math.random() * 70)/10.0f),
                  (50 + (int)(Math.random() * 210)/10.0f),
                  (50 + (int)(Math.random() * 210)/10.0f),
                  (short)(1500 + (int)(Math.random() * 20000)/10),
                  (short)(1 + (int)(Math.random() * 90)/10),
                  (short)(1 + (int)(Math.random() * 200)/10),
                  (2 + (int)(Math.random() * 50)/10.0f),
                  (short)(20 + (int)(Math.random() * 410)/10),
                  cal.getTimeInMillis(),
                  false).save();
          cal.add(Calendar.DATE, 1);
        }

        getActivity().runOnUiThread(new Runnable() {
          public void run() {
            Toast.makeText(getActivity(), R.string.pref_test_data_loaded, Toast.LENGTH_SHORT).show();
            ProgressBar progressBar = (ProgressBar) getActivity().findViewById(R.id.progress_spinner);
            progressBar.setVisibility(View.GONE);
          }
        });
      }
    }).start();
  }

  /* Display "About" dialog */
  private void showAbout() {
    String message = "v" + getString(R.string.app_version);
    message += "\nSébastien Vrillaud © 2019";

    if (!getString(R.string.translator).equals("")) {
      message += "\n\n" + Locale.getDefault().getDisplayLanguage().substring(0,1).toUpperCase() + Locale.getDefault().getDisplayLanguage().substring(1);
      message += "\n" + getString(R.string.translator);
    }

    AlertDialog.Builder b = new AlertDialog.Builder(getActivity());
    b.setIcon(android.R.drawable.ic_menu_help);
    b.setTitle(R.string.app_name);
    b.setMessage(message);
    b.setPositiveButton(R.string.ok, null);
    b.show();
  }

  public interface Callback {
    public void onNestedPreferenceSelected(int key);
  }
}

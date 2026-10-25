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
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.kochka.android.weightlogger.R;
import org.kochka.android.weightlogger.data.Database;
import org.kochka.android.weightlogger.data.Measurement;
import org.kochka.android.weightlogger.tools.Export;
import org.kochka.android.weightlogger.tools.GarminConnect;
import org.kochka.android.weightlogger.tools.StorageNotMountedException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;


public class PreferencesFragment extends PreferenceFragment implements Preference.OnPreferenceClickListener, SharedPreferences.OnSharedPreferenceChangeListener {

  private static final int REQUEST_RESTORE = 1;

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
    findPreference("garmin_token_clear").setOnPreferenceClickListener(this);
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
    else if (preference.getKey().equals("garmin_token_clear")) {
      clearGarminTokens();
    } else if (preference.getKey().equals("db_backup"))
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

  /* Clear Garmin Tokens */
  private void clearGarminTokens() {
    if (GarminConnect.clearTokens(getActivity())) {
      Toast.makeText(getActivity(), R.string.gc_token_cleared, Toast.LENGTH_SHORT).show();
    }
    else {
      Toast.makeText(getActivity(), R.string.gc_token_clear_failed, Toast.LENGTH_SHORT).show();
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

  /* Restore database: backups made by a previous install are not readable
     through the file system since Android 11, let the user pick the file */
  private void dbRestore() {
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("*/*");
    startActivityForResult(intent, REQUEST_RESTORE);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == REQUEST_RESTORE && resultCode == Activity.RESULT_OK && data != null && data.getData() != null)
      confirmRestore(data.getData());
  }

  private void confirmRestore(final Uri uri) {
    new AlertDialog.Builder(getActivity())
            .setTitle(R.string.pref_restore_database)
            .setMessage(String.format(getString(R.string.pref_restore_database_confirm), displayName(uri)))
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int whichButton) {
                restore(uri);
              }})
            .setNegativeButton(android.R.string.no, null).show();
  }

  private void restore(Uri uri) {
    File backup = new File(getActivity().getCacheDir(), "restore.bkp");
    try {
      copy(getActivity().getContentResolver().openInputStream(uri), new FileOutputStream(backup));
      if (!isSQLiteFile(backup))
        throw new IOException("Not a database backup");
      Database.getRawInstance(getActivity()).importDatabase(backup.getPath());
      Toast.makeText(getActivity(), R.string.pref_restore_database_ok, Toast.LENGTH_SHORT).show();
    } catch (Exception e) {
      Toast.makeText(getActivity(), R.string.pref_restore_database_failed, Toast.LENGTH_SHORT).show();
    } finally {
      backup.delete();
    }
  }

  private String displayName(Uri uri) {
    try (Cursor cursor = getActivity().getContentResolver().query(uri, new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null)) {
      if (cursor != null && cursor.moveToFirst())
        return cursor.getString(0);
    } catch (Exception ignored) {
    }
    return uri.getLastPathSegment();
  }

  private static void copy(InputStream in, OutputStream out) throws IOException {
    try {
      byte[] buffer = new byte[8192];
      int length;
      while ((length = in.read(buffer)) > 0)
        out.write(buffer, 0, length);
    } finally {
      out.close();
      in.close();
    }
  }

  private static boolean isSQLiteFile(File file) throws IOException {
    byte[] header = new byte[16];
    try (FileInputStream in = new FileInputStream(file)) {
      return in.read(header) == header.length && Arrays.equals(header, "SQLite format 3\0".getBytes("US-ASCII"));
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
    message += "\nSébastien Vrillaud © 2026";

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

/*
  Copyright 2012 Sébastien Vrillaud
  
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

import java.io.File;
import java.util.LinkedList;

import org.kochka.android.weightlogger.adapter.MeasurementsListAdapter;
import org.kochka.android.weightlogger.data.Measurement;
import org.kochka.android.weightlogger.tools.Export;
import org.kochka.android.weightlogger.tools.GarminConnect;
import org.kochka.android.weightlogger.tools.StorageNotMountedException;

import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.melnykov.fab.FloatingActionButton;

public class WeightLoggerActivity extends ActionBarActivity {
  
  ListView mList;
  
  final int EXPORT_FIT = 0;
  final int EXPORT_CSV = 1;
  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);

    Toolbar actionBar = (Toolbar) findViewById(R.id.actionbar);
    setSupportActionBar(actionBar);
    
    mList = (ListView)findViewById(R.id.mListView);
    MeasurementsListAdapter adapter = new MeasurementsListAdapter(this);
    mList.setAdapter(adapter);
    
    mList.setOnItemClickListener(new OnItemClickListener() {
      public void onItemClick(AdapterView<?> a, View v, int position, long id) {
        Intent i = new Intent(WeightLoggerActivity.this, ShowMeasurementActivity.class);
        i.putExtra("position", position);
        startActivityForResult(i, 0);
      }
    });

    registerForContextMenu(mList);

    // Fab
    FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
    fab.attachToListView(mList);
    fab.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        startActivityForResult(new Intent(WeightLoggerActivity.this, EditMeasurementActivity.class), 0);
      }
    });
  }
   
  @Override
  public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
    if (v.getId() == R.id.mListView) {
      AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)menuInfo;
      menu.setHeaderTitle(((Measurement) mList.getItemAtPosition(info.position)).getFormatedRecordedAt());
      menu.add(Menu.NONE, 0, 0, R.string.edit);
      menu.add(Menu.NONE, 1, 1, R.string.delete);
    }
  }
  
  @Override
  public boolean onContextItemSelected(MenuItem item) {
    AdapterView.AdapterContextMenuInfo menuInfo;
    menuInfo = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
    final Measurement measurement = (Measurement) mList.getItemAtPosition(menuInfo.position);
    switch (item.getItemId()) {
      case 0:
        Intent i = new Intent(this, EditMeasurementActivity.class);
        i.putExtra("id", measurement.getId());
        startActivityForResult(i, 0);
        break;
      case 1:
        new AlertDialog.Builder(this)
        .setTitle(R.string.confirm_delete)
        .setIcon(android.R.drawable.ic_dialog_alert)
        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
              measurement.destroy();
              ((MeasurementsListAdapter) mList.getAdapter()).refresh();
              Toast.makeText(WeightLoggerActivity.this, R.string.record_deleted, Toast.LENGTH_SHORT).show();
            }})
        .setNegativeButton(android.R.string.no, null).show();
        break;
    }
    return true;
  }
  
  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (resultCode == ActionBarActivity.RESULT_OK) {
      ((MeasurementsListAdapter) mList.getAdapter()).refresh();
    }
  }
  
  /* Creates the menu items */
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
//    MenuItem mClose = menu.add(0, 1, 1, R.string.quit);
//    mClose.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
//    MenuItem mPref = menu.add(0, R.id.item_preferences, 2, R.string.pref);
//    mPref.setIcon(android.R.drawable.ic_menu_preferences);
//    return(super.onCreateOptionsMenu(menu));
    getMenuInflater().inflate(R.menu.home_actionbar, menu);
    return true;
  }
  
  /* Handles menu selections */
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.item_graph:
        startActivity((new Intent(this, GraphActivity.class)).putExtra("type", "line"));
        break;
      case R.id.item_export:
        export();
        break;
      case R.id.item_preferences:
    	  startActivityForResult(new Intent(this, EditPreferences.class), 0);
        break;
      case 1:
        this.finish();
        return true;
    }
    return(super.onOptionsItemSelected(item));
  }
  
  private void export(){
    final CharSequence[] items = {"FIT", "CSV", "Garmin Connect ©"};

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(R.string.export);
    builder.setItems(items, new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int item) {
        switch (item) {
          case 0:
            export(EXPORT_FIT, false);
            break;
          case 1:
            export(EXPORT_CSV, false);
            break;
          case 2:
            export(EXPORT_FIT, true);
            break;
          default:
            break;
        }
      }
    });
    AlertDialog alert = builder.create();
    alert.show();
  }

  private void export(int type, boolean garmin_upload) {
    
    class ExportThread implements Runnable {

      private int type;
      private boolean garmin_upload;
      private GarminConnect gc;
      
      public ExportThread(int type, boolean garmin_upload) {
        this.type = type;
        this.garmin_upload = garmin_upload;
      }
      
      @Override
      public void run() {
        try {         
          LinkedList<Measurement> measurements;
          String filename;
          String mime;
          String upload_message = "";
          int icon;
          
          if (type == EXPORT_FIT)         
            measurements = Measurement.getAllToExport(WeightLoggerActivity.this);
          else
            measurements = Measurement.getAll(WeightLoggerActivity.this);
          
          int measurements_count = measurements.size();
          if (measurements_count == 0) throw new Exception(getString(R.string.no_export));    

          // FIT
          if (type == EXPORT_FIT) {
            // Test connectivity & Garmin account
            if (garmin_upload) {
              ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
              if (cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnectedOrConnecting()) {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(WeightLoggerActivity.this);
                String username = preferences.getString("garmin_login", "").trim();
                String password = preferences.getString("garmin_password", "").trim();
                if (username.equals("") || password.equals("")) 
                  throw new Exception(getString(R.string.gc_configure_account));
                this.gc = new GarminConnect();
                if (!this.gc.signin(username, password))
                  throw new Exception(getString(R.string.gc_account_error));  
              } else
                throw new Exception(getString(R.string.network_error));  
            }
            
            // Build FIT file
            filename = Export.buildFitFile(WeightLoggerActivity.this, measurements);
            mime = "application/*";
            icon = R.drawable.ic_stat_notify_fit;
            Measurement.setAllAsExported(WeightLoggerActivity.this);
            
            // Upload FIT file
            if (garmin_upload) {
              if(this.gc.uploadFitFile(Export.path(WeightLoggerActivity.this) + File.separator + filename))
                upload_message = getString(R.string.export_upload_ok);
              else
                upload_message = getString(R.string.export_upload_failed);
              this.gc.close();
            }
            
          // CSV
          } else {
            filename = Export.buildCsvFile(WeightLoggerActivity.this, measurements);
            mime = "text/csv";
            icon = R.drawable.ic_stat_notify_csv;
          }
          
          // Notify
          NotificationManager notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
          String gen_text = getResources().getQuantityString(R.plurals.exported_records_count, measurements_count, measurements_count) + " : " + filename;
          
          Intent i = new Intent(Intent.ACTION_SEND);
          i.setType(mime);
          i.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.export_share_subject));
          i.putExtra(Intent.EXTRA_TEXT, gen_text);
          i.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(Export.path(WeightLoggerActivity.this) + File.separator + filename)));
          PendingIntent pi = PendingIntent.getActivity(WeightLoggerActivity.this, 0, i, 0);
          
          NotificationCompat.Builder nBuilder = new NotificationCompat.Builder(WeightLoggerActivity.this);
          nBuilder.setSmallIcon(icon);
          nBuilder.setContentTitle(getString(R.string.file_generated));
          nBuilder.setContentText((garmin_upload) ? upload_message : gen_text);
          nBuilder.setContentIntent(pi);
          
          notificationManager.notify(1, nBuilder.getNotification());
        } catch (StorageNotMountedException e) {
          displayToast(getString(R.string.storage_not_mounted));
        } catch (Exception e) {
          displayToast(e.getMessage());
        } finally {
          WeightLoggerActivity.this.runOnUiThread(new Runnable() { public void run() {
            ProgressBar progressBar = (ProgressBar) findViewById(R.id.progress_spinner);
            progressBar.setVisibility(View.GONE);
          }});
        }
      }
      
      private void displayToast(final String message) {
        WeightLoggerActivity.this.runOnUiThread(new Runnable() {
          public void run() {
            Toast.makeText(WeightLoggerActivity.this, message, Toast.LENGTH_SHORT).show();
          }
        });
      }
    }

    ProgressBar progressBar = (ProgressBar) findViewById(R.id.progress_spinner);
    progressBar.setVisibility(View.VISIBLE);
    
    Runnable r = new ExportThread(type, garmin_upload);
    new Thread(r).start();
  }
  
  public static Intent createIntent(Context context) {
    Intent i = new Intent(context, WeightLoggerActivity.class);
    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    return i;
  }
}
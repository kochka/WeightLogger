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
package org.kochka.android.weightlogger.tools;

import android.app.Activity;
import android.content.Context;
import android.content.IntentSender;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.request.DataDeleteRequest;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.result.DataReadResult;

import org.kochka.android.weightlogger.R;
import org.kochka.android.weightlogger.data.Measurement;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;


public class GoogleFit {

  public static final String TAG = "BasicHistoryApi";
  public static final int REQUEST_OAUTH = 16;
  private static final String DATE_FORMAT = "yyyy.MM.dd HH:mm:ss";

  private static GoogleFit sSingleton;

  private Context mContext = null;
  private GoogleApiClient mClient = null;
  private boolean authInProgress = false;

  private GoogleFit(Context context) {
    this.mContext = context;
  }

  public static synchronized GoogleFit getInstance(Context context) {
    if (sSingleton == null) sSingleton = new GoogleFit(context);
    return sSingleton;
  }

  public Context getContext() {
    return mContext;
  }

  public void sendData() {
    if (mClient == null)
      buildFitnessClient();

    if (mClient.isConnected())
      new sendDataTask().execute();
    else
      mClient.connect();
  }

  private void buildFitnessClient() {
    mClient = new GoogleApiClient.Builder(getContext())
            .addApi(Fitness.HISTORY_API)
            .addScope(new Scope(Scopes.FITNESS_BODY_READ_WRITE))
            .addScope(new Scope(Scopes.FITNESS_ACTIVITY_READ_WRITE))
            .addConnectionCallbacks(
                    new GoogleApiClient.ConnectionCallbacks() {
                      @Override
                      public void onConnected(Bundle bundle) {
                        Log.i(TAG, "Connected!!!");
                        new sendDataTask().execute();
                      }

                      @Override
                      public void onConnectionSuspended(int i) {
                        Log.i(TAG, "Connection suspended !");
                        if (i == ConnectionCallbacks.CAUSE_NETWORK_LOST) {
                          Log.i(TAG, "Connection lost.  Cause: Network Lost.");
                        } else if (i == ConnectionCallbacks.CAUSE_SERVICE_DISCONNECTED) {
                          Log.i(TAG, "Connection lost.  Reason: Service Disconnected");
                        }
                      }
                    }
            )
            .addOnConnectionFailedListener(
              new GoogleApiClient.OnConnectionFailedListener() {
                @Override
                public void onConnectionFailed(ConnectionResult result) {
                  Log.i(TAG, "Connection failed. Cause: " + result.toString());
                  if (!result.hasResolution()) {
                    // Show the localized error dialog
                    GooglePlayServicesUtil.getErrorDialog(result.getErrorCode(), (Activity) getContext(), 0).show();
                    return;
                  }

                  if (!authInProgress) {
                    try {
                      Log.i(TAG, "Attempting to resolve failed connection");
                      authInProgress = true;
                      result.startResolutionForResult((Activity) getContext(), REQUEST_OAUTH);
                    } catch (IntentSender.SendIntentException e) {
                      Log.e(TAG, "Exception while starting resolution activity", e);
                    }
                  }
                }
              }
            )
            .build();
  }

  private class sendDataTask extends AsyncTask<Void, Void, Void> {
    @Override
    protected void onPreExecute() {
      ((Activity) mContext).runOnUiThread(new Runnable() {
        public void run() {
          ProgressBar progressBar = (ProgressBar) ((Activity) mContext).findViewById(R.id.progress_spinner);
          progressBar.setVisibility(View.VISIBLE);
        }
      });
    }

    @Override
    protected void onPostExecute(Void result) {
      ((Activity) mContext).runOnUiThread(new Runnable() {
        public void run() {
          ProgressBar progressBar = (ProgressBar) ((Activity) mContext).findViewById(R.id.progress_spinner);
          progressBar.setVisibility(View.GONE);
          Toast.makeText(mContext, "Exported to Google FIT", Toast.LENGTH_SHORT).show();
        }
      });
    }

    @Override
    protected Void doInBackground(Void... params) {

      try {
        if (!mClient.isConnected()) throw new Exception("Client not connected !");

        DataSet d;
        LinkedList<Measurement> measurements = Measurement.getAll(mContext);

        Log.i(TAG, "Inserting weight data in the History API");
        if (!Fitness.HistoryApi.insertData(mClient, weightData(measurements)).await(1, TimeUnit.MINUTES).isSuccess())
          throw new Exception("There was a problem inserting  weight data.");

        d = bodyFatData(measurements);
        if (!d.isEmpty()) {
          if (!Fitness.HistoryApi.insertData(mClient, d).await(1, TimeUnit.MINUTES).isSuccess())
            throw new Exception("There was a problem inserting body fat data.");
        }

        d = basalMetabolicRate(measurements);
        if (!d.isEmpty()) {
          com.google.android.gms.common.api.Status insertStatus = Fitness.HistoryApi.insertData(mClient, d).await(1, TimeUnit.MINUTES);
          Log.i(TAG, insertStatus.getStatus().toString());
          if (!insertStatus.isSuccess())
            throw new Exception("There was a problem inserting basal metabolic rate data.");
        }

        Log.i(TAG, "Data insert was successful!");
      } catch (Exception e) {
        Log.e(TAG, e.getMessage());
        return null;
      }

      // For debugging
      //queryData();
      //deleteData();

      mClient.disconnect();

      return null;
    }
  }

  private DataSet weightData(LinkedList<Measurement> measurements) {
    DataSource dataSource = new DataSource.Builder()
            .setAppPackageName(getContext())
            .setDataType(DataType.TYPE_WEIGHT)
            .setType(DataSource.TYPE_RAW)
            .build();

    DataSet dataSet = DataSet.create(dataSource);
    DataPoint dataPoint;

    for (Measurement measurement : measurements) {
      dataPoint = dataSet.createDataPoint().setTimestamp(measurement.getRecordedAt().getTimeInMillis(), TimeUnit.MILLISECONDS);
      dataPoint.getValue(Field.FIELD_WEIGHT).setFloat(measurement.getWeight());
      dataSet.add(dataPoint);
    }

    return dataSet;
  }

  private DataSet bodyFatData(LinkedList<Measurement> measurements) {
    DataSource dataSource = new DataSource.Builder()
            .setAppPackageName(getContext())
            .setDataType(DataType.TYPE_BODY_FAT_PERCENTAGE)
            .setType(DataSource.TYPE_RAW)
            .build();

    DataSet dataSet = DataSet.create(dataSource);
    DataPoint dataPoint;

    for (Measurement measurement : measurements) {
      if (measurement.getBodyFat() != null) {
        dataPoint = dataSet.createDataPoint().setTimestamp(measurement.getRecordedAt().getTimeInMillis(), TimeUnit.MILLISECONDS);
        dataPoint.getValue(Field.FIELD_PERCENTAGE).setFloat(measurement.getBodyFat());
        dataSet.add(dataPoint);
      }
    }

    return dataSet;
  }

  private DataSet basalMetabolicRate(LinkedList<Measurement> measurements) {
    DataSource dataSource = new DataSource.Builder()
            .setAppPackageName(getContext())
            .setDataType(DataType.TYPE_BASAL_METABOLIC_RATE)
            .setType(DataSource.TYPE_RAW)
            .build();

    DataSet dataSet = DataSet.create(dataSource);
    DataPoint dataPoint;

    for (Measurement measurement : measurements) {
      if (measurement.getDailyCalorieIntake() != null) {
        dataPoint = dataSet.createDataPoint().setTimestamp(measurement.getRecordedAt().getTimeInMillis(), TimeUnit.MILLISECONDS);
        dataPoint.getValue(Field.FIELD_CALORIES).setFloat(measurement.getDailyCalorieIntake().floatValue());
        dataSet.add(dataPoint);
      }
    }

    return dataSet;
  }

  private void queryData() {
    Calendar cal = Calendar.getInstance();
    Date now = new Date();
    cal.setTime(now);
    long endTime = cal.getTimeInMillis();
    cal.add(Calendar.DAY_OF_MONTH, -6);
    long startTime = cal.getTimeInMillis();

    SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);

    DataType[] dataTypes = { DataType.TYPE_WEIGHT, DataType.TYPE_BODY_FAT_PERCENTAGE, DataType.TYPE_BASAL_METABOLIC_RATE };

    for (DataType dataType : dataTypes) {
      DataReadRequest readRequest = new DataReadRequest.Builder()
              .read(dataType)
              .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
              .build();

      DataReadResult dataReadResult = Fitness.HistoryApi.readData(mClient, readRequest).await(1, TimeUnit.MINUTES);

      Log.i(TAG, "== DATA POINTS - " + dataType.getName() + " ==");
      for (DataPoint dp : dataReadResult.getDataSet(dataType).getDataPoints()) {
        Log.i(TAG, "Data point:");
        Log.i(TAG, "\tType: " + dp.getDataType().getName());
        Log.i(TAG, "\tDate: " + dateFormat.format(dp.getTimestamp(TimeUnit.MILLISECONDS)));
        for (Field field : dp.getDataType().getFields()) {
          Log.i(TAG, "\tField value: " + dp.getValue(field));
        }
      }
    }
  }


  private void deleteData() {
    Log.i(TAG, "Deleting fitness data");

    Calendar cal = Calendar.getInstance();
    Date now = new Date();
    cal.setTime(now);
    long endTime = cal.getTimeInMillis();
    cal.add(Calendar.DAY_OF_YEAR, -6);
    long startTime = cal.getTimeInMillis();

    DataDeleteRequest request = new DataDeleteRequest.Builder()
            .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
            .addDataType(DataType.TYPE_WEIGHT)
            .addDataType(DataType.TYPE_BODY_FAT_PERCENTAGE)
            .addDataType(DataType.TYPE_BASAL_METABOLIC_RATE)
            .build();

    Fitness.HistoryApi.deleteData(mClient, request)
            .setResultCallback(new ResultCallback<Status>() {
              @Override
              public void onResult(Status status) {
                if (status.isSuccess()) {
                  Log.i(TAG, "Successfully deleted data");
                } else {
                  Log.i(TAG, "Failed to delete data");
                }
              }
            });
  }

}

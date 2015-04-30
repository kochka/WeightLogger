package org.kochka.android.weightlogger.tools;

import android.app.Activity;
import android.content.Context;
import android.content.IntentSender;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.data.Bucket;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.request.DataDeleteRequest;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.result.DataReadResult;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by kochka on 30/04/15.
 */
public class GoogleFit {

  public static final String TAG = "BasicHistoryApi";
  private static final int REQUEST_OAUTH = 1;
  private static final String DATE_FORMAT = "yyyy.MM.dd HH:mm:ss";

  private Context context = null;
  private GoogleApiClient mClient = null;
  private boolean authInProgress = false;

  public GoogleFit(Context context) {
    this.context = context;
    buildFitnessClient();
    mClient.connect();
  }

  public Context getContext() {
    return this.context;
  }

  private void buildFitnessClient() {
    // Create the Google API Client
    mClient = new GoogleApiClient.Builder(getContext())
            .addApi(Fitness.HISTORY_API)
            .addScope(new Scope(Scopes.FITNESS_BODY_READ_WRITE))
            .addConnectionCallbacks(
              new GoogleApiClient.ConnectionCallbacks() {
                @Override
                public void onConnected(Bundle bundle) {
                  Log.i(TAG, "Connected!!!");
                  // Now you can make calls to the Fitness APIs.  What to do?
                  // Look at some data!!
                  new InsertAndVerifyDataTask().execute();
                }

                @Override
                public void onConnectionSuspended(int i) {
                  Log.i(TAG, "Connection suspended !");
                  // If your connection to the sensor gets lost at some point,
                  // you'll be able to determine the reason and react to it here.
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
                // Called whenever the API client fails to connect.
                @Override
                public void onConnectionFailed(ConnectionResult result) {
                  Log.i(TAG, "Connection failed. Cause: " + result.toString());
                  if (!result.hasResolution()) {
                    // Show the localized error dialog
                    GooglePlayServicesUtil.getErrorDialog(result.getErrorCode(), (Activity) getContext(), 0).show();
                    return;
                  }
                  // The failure has a resolution. Resolve it.
                  // Called typically when the app is not yet authorized, and an
                  // authorization dialog is displayed to the user.
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

  private class InsertAndVerifyDataTask extends AsyncTask<Void, Void, Void> {
    protected Void doInBackground(Void... params) {
      //First, create a new dataset and insertion request.
      DataSet dataSet = insertWeightData();

      // [START insert_dataset]
      // Then, invoke the History API to insert the data and await the result, which is
      // possible here because of the {@link AsyncTask}. Always include a timeout when calling
      // await() to prevent hanging that can occur from the service being shutdown because
      // of low memory or other conditions.
      Log.i(TAG, "Inserting the dataset in the History API");
      com.google.android.gms.common.api.Status insertStatus =
              Fitness.HistoryApi.insertData(mClient, dataSet)
                      .await(1, TimeUnit.MINUTES);

      // Before querying the data, check to see if the insertion succeeded.
      if (!insertStatus.isSuccess()) {
        Log.i(TAG, "There was a problem inserting the dataset.");
        return null;
      }

      // At this point, the data has been inserted and can be read.
      Log.i(TAG, "Data insert was successful!");
      // [END insert_dataset]

      // Begin by creating the query.
      DataReadRequest readRequest = queryWeightData();

      // [START read_dataset]
      // Invoke the History API to fetch the data with the query and await the result of
      // the read request.
      DataReadResult dataReadResult =
              Fitness.HistoryApi.readData(mClient, readRequest).await(1, TimeUnit.MINUTES);
      // [END read_dataset]

      // For the sake of the sample, we'll print the data so we can see what we just added.
      // In general, logging fitness information should be avoided for privacy reasons.
      printData(dataReadResult);

      deleteData();

      return null;
    }
  }

  /**
   * Create and return a {@link DataSet} of step count data for the History API.
   */
  private DataSet insertWeightData() {
    Log.i(TAG, "Creating a new data insert request");

    // [START build_insert_data_request]
    // Set a start and end time for our data, using a start time of 1 hour before this moment.
    Calendar cal = Calendar.getInstance();
    Date now = new Date();
    cal.setTime(now);
    long endTime = cal.getTimeInMillis();
    cal.add(Calendar.HOUR_OF_DAY, -1);
    long startTime = cal.getTimeInMillis();

    // Create a data source
    DataSource dataSource = new DataSource.Builder()
            .setAppPackageName(getContext())
            .setDataType(DataType.TYPE_WEIGHT)
            .setType(DataSource.TYPE_RAW)
            .build();

    // Create a data set
    DataSet dataSet = DataSet.create(dataSource);
    DataPoint dataPoint = dataSet.createDataPoint().setTimeInterval(endTime, endTime, TimeUnit.MILLISECONDS);
    dataPoint.getValue(Field.FIELD_WEIGHT).setFloat(82.4f);
    dataSet.add(dataPoint);
    // [END build_insert_data_request]

    return dataSet;
  }

  private DataReadRequest queryWeightData() {
    // [START build_read_data_request]
    // Setting a start and end date using a range of 1 week before this moment.
    Calendar cal = Calendar.getInstance();
    Date now = new Date();
    cal.setTime(now);
    long endTime = cal.getTimeInMillis();
    cal.add(Calendar.DAY_OF_MONTH, -6);
    long startTime = cal.getTimeInMillis();

    SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
    Log.i(TAG, "Range Start: " + dateFormat.format(startTime));
    Log.i(TAG, "Range End: " + dateFormat.format(endTime));

    DataReadRequest readRequest = new DataReadRequest.Builder()
            .aggregate(DataType.TYPE_WEIGHT, DataType.AGGREGATE_WEIGHT_SUMMARY)
            .bucketByTime(1, TimeUnit.DAYS)
            .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
            .build();
    // [END build_read_data_request]

    return readRequest;
  }

  private void printData(DataReadResult dataReadResult) {
    // [START parse_read_data_result]
    // If the DataReadRequest object specified aggregated data, dataReadResult will be returned
    // as buckets containing DataSets, instead of just DataSets.
    if (dataReadResult.getBuckets().size() > 0) {
      Log.i(TAG, "Number of returned buckets of DataSets is: "
              + dataReadResult.getBuckets().size());
      for (Bucket bucket : dataReadResult.getBuckets()) {
        List<DataSet> dataSets = bucket.getDataSets();
        for (DataSet dataSet : dataSets) {
          dumpDataSet(dataSet);
        }
      }
    } else if (dataReadResult.getDataSets().size() > 0) {
      Log.i(TAG, "Number of returned DataSets is: "
              + dataReadResult.getDataSets().size());
      for (DataSet dataSet : dataReadResult.getDataSets()) {
        dumpDataSet(dataSet);
      }
    }
    // [END parse_read_data_result]
  }

  // [START parse_dataset]
  private void dumpDataSet(DataSet dataSet) {
    Log.i(TAG, "Data returned for Data type: " + dataSet.getDataType().getName());
    SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);

    for (DataPoint dp : dataSet.getDataPoints()) {
      Log.i(TAG, "Data point:");
      Log.i(TAG, "\tType: " + dp.getDataType().getName());
      Log.i(TAG, "\tStart: " + dateFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)));
      Log.i(TAG, "\tEnd: " + dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS)));
      for(Field field : dp.getDataType().getFields()) {
        Log.i(TAG, "\tField: " + field.getName() +
                " Value: " + dp.getValue(field));
      }
    }
  }
  // [END parse_dataset]

  private void deleteData() {
    Log.i(TAG, "Deleting today's weight data");

    // [START delete_dataset]
    // Set a start and end time for our data, using a start time of 1 day before this moment.
    Calendar cal = Calendar.getInstance();
    Date now = new Date();
    cal.setTime(now);
    long endTime = cal.getTimeInMillis();
    cal.add(Calendar.YEAR, -1);
    long startTime = cal.getTimeInMillis();

    //  Create a delete request object, providing a data type and a time interval
    DataDeleteRequest request = new DataDeleteRequest.Builder()
            .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
            .addDataType(DataType.TYPE_WEIGHT)
            .addDataType(DataType.AGGREGATE_WEIGHT_SUMMARY)
            .build();

    // Invoke the History API with the Google API client object and delete request, and then
    // specify a callback that will check the result.
    Fitness.HistoryApi.deleteData(mClient, request)
            .setResultCallback(new ResultCallback<Status>() {
              @Override
              public void onResult(Status status) {
                if (status.isSuccess()) {
                  Log.i(TAG, "Successfully deleted weight data");
                } else {
                  // The deletion will fail if the requesting app tries to delete data
                  // that it did not insert.
                  Log.i(TAG, "Failed to delete weight data");
                }
              }
            });
    // [END delete_dataset]
  }

}

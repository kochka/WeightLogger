package org.kochka.android.weightlogger.tools;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import com.dsi.ant.plugins.antplus.pcc.AntPlusWeightScalePcc;
import com.dsi.ant.plugins.antplus.pcc.AntPlusWeightScalePcc.AdvancedMeasurement;
import com.dsi.ant.plugins.antplus.pcc.AntPlusWeightScalePcc.IAdvancedMeasurementFinishedReceiver;
import com.dsi.ant.plugins.antplus.pcc.AntPlusWeightScalePcc.WeightScaleRequestStatus;
import com.dsi.ant.plugins.antplus.pcc.defines.DeviceState;
import com.dsi.ant.plugins.antplus.pcc.defines.EventFlag;
import com.dsi.ant.plugins.antplus.pcc.defines.RequestAccessResult;
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc.IDeviceStateChangeReceiver;
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc.IPluginAccessResultReceiver;
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle;

import org.kochka.android.weightlogger.data.Measurement;

import java.util.EnumSet;

/**
 * Created by kochka on 25/03/15.
 */
public class AntPlus {

  Context context;
  AntPlusWeightScalePcc wgtPcc = null;
  PccReleaseHandle<AntPlusWeightScalePcc> releaseHandle = null;

  IPluginAccessResultReceiver<AntPlusWeightScalePcc> mResultReceiver = new IPluginAccessResultReceiver<AntPlusWeightScalePcc>() {
    // Handle the result, connecting to events on success or reporting
    // failure to user.
    @Override
    public void onResultReceived(AntPlusWeightScalePcc result, RequestAccessResult resultCode, DeviceState initialDeviceState) {
      switch (resultCode) {
        case SUCCESS:
          wgtPcc = result;
          requestAdvancedMeasurement();
          break;
        case CHANNEL_NOT_AVAILABLE:
          displayToast("Channel Not Available");
          break;
        case ADAPTER_NOT_DETECTED:
          displayToast("ANT Adapter Not Available. Built-in ANT hardware or external adapter required.");
          break;
        case BAD_PARAMS:
          displayToast("Bad request parameters.");
          break;
        case OTHER_FAILURE:
          displayToast("RequestAccess failed. See logcat for details.");
          break;
        case DEPENDENCY_NOT_INSTALLED:
          AlertDialog.Builder adlgBldr = new AlertDialog.Builder(context);
          adlgBldr.setTitle("Missing Dependency");
          adlgBldr.setMessage("The required service\n\""
                              + AntPlusWeightScalePcc.getMissingDependencyName()
                              + "\"\n was not found. You need to install the ANT+ Plugins service or you may need to update your existing version if you already have it. Do you want to launch the Play Store to get it?");
          adlgBldr.setCancelable(true);
          adlgBldr.setPositiveButton("Go to Store", new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              Intent startStore = null;
              startStore = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + AntPlusWeightScalePcc.getMissingDependencyPackageName()));
              startStore.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
              context.startActivity(startStore);
            }
          });
          adlgBldr.setNegativeButton("Cancel", new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              dialog.dismiss();
            }
          });

          final AlertDialog waitDialog = adlgBldr.create();
          waitDialog.show();
          break;
        case USER_CANCELLED:
          //
          break;
        case UNRECOGNIZED:
          displayToast("Failed: UNRECOGNIZED. PluginLib Upgrade Required?");
          break;
        default:
          displayToast("Unrecognized result: " + resultCode);
          break;
      }
    }

    private void requestAdvancedMeasurement() {
      boolean submitted = wgtPcc.requestAdvancedMeasurement(new IAdvancedMeasurementFinishedReceiver() {
        @Override
        public void onAdvancedMeasurementFinished(long estTimestamp, EnumSet<EventFlag> eventFlags, final WeightScaleRequestStatus status, final AdvancedMeasurement aMeasurement) {
          ((Activity) context).runOnUiThread(new Runnable() {
            @Override
            public void run() {
              if(checkRequestResult(status)) {
                Measurement measurement = new Measurement(context);

                if(aMeasurement.bodyWeight.intValue() != -1)
                  measurement.setWeight(aMeasurement.bodyWeight.floatValue());

                if(aMeasurement.bodyFatPercentage.intValue() != -1)
                  measurement.setBodyFat(aMeasurement.bodyFatPercentage.floatValue());

                if(aMeasurement.hydrationPercentage.intValue() != -1)
                  measurement.setBodyWater(aMeasurement.hydrationPercentage.floatValue());

                if(aMeasurement.muscleMass.intValue() != -1)
                  measurement.setMuscleMass(aMeasurement.muscleMass.floatValue());

                if(aMeasurement.boneMass.intValue() != -1)
                  measurement.setBoneMass(aMeasurement.boneMass.floatValue());

                if(aMeasurement.activeMetabolicRate.intValue() != -1)
                  measurement.setDailyCalorieIntake(aMeasurement.activeMetabolicRate.shortValue());

                measurement.save();
              }
            }
          });
        }
      },
      null);
    }

    private boolean checkRequestResult(WeightScaleRequestStatus status) {
      switch(status) {
        case SUCCESS:
          return true;
        case FAIL_ALREADY_BUSY_EXTERNAL:
          displayToast("Fail: Busy");
          break;
        case FAIL_DEVICE_COMMUNICATION_FAILURE:
          displayToast("Fail: Comm Err");
          break;
        case FAIL_DEVICE_TRANSMISSION_LOST:
          displayToast("Fail: Trans Lost");
          break;
        case FAIL_PLUGINS_SERVICE_VERSION:
          displayToast("Failed: Plugin Service Upgrade Required?");
          break;
        case UNRECOGNIZED:
          displayToast("Failed: UNRECOGNIZED. PluginLib Upgrade Required?");
          break;
        default:
          displayToast("Fail: " + status);
          break;
      }
      return false;
    }
  };

  // Receives state changes and shows it on the status display line
  IDeviceStateChangeReceiver mDeviceStateChangeReceiver = new IDeviceStateChangeReceiver() {
    @Override
    public void onDeviceStateChange(final DeviceState newDeviceState) {
     Log.d("WL", wgtPcc.getDeviceName() + ": " + newDeviceState);
    }
  };

  public AntPlus(Context context) {
    this.context = context;
    resetPcc();
  }

  /**
   * Resets the PCC connection to request access again and clears any existing display data.
   */
  private void resetPcc() {
    //Release the old access if it exists
    if(releaseHandle != null)
      releaseHandle.close();

    releaseHandle = AntPlusWeightScalePcc.requestAccess((Activity) context, context, mResultReceiver, mDeviceStateChangeReceiver);
  }

  private void displayToast(final String message) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
  }
}

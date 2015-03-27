package org.kochka.android.weightlogger.tools;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;

import org.kochka.android.weightlogger.ShowMeasurementActivity;
import org.kochka.android.weightlogger.data.Measurement;

import java.util.ArrayList;

import info.hmm.hmmapiws.WSGattCallback;
import info.hmm.hmmapiws.WeightData;
import info.hmm.hmmapiws.WeightScaleBLECallback;
import info.hmm.hmmapiws.WeightScaleSettings;

/**
 * Created by kochka on 27/03/15.
 */
public class BleSmartLab implements BluetoothAdapter.LeScanCallback {

  private BluetoothAdapter mBluetoothAdapter;

  private Context mContext;


  public BleSmartLab(final Context context) {
    final BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
    mBluetoothAdapter = bluetoothManager.getAdapter();
    mContext = context;
  }

  /**
   * Starts scanning for temperature data. Call {@link #stopScan()} when done to save the power.
   */
  public void startScan() {

    mBluetoothAdapter.startLeScan(this);
  }

  /**
   * Starts scanning for temperature data. Call {@link #stopScan()} when done to save the power.
   */
  public void startScan(final long period) {
    mBluetoothAdapter.startLeScan(this);

    new Handler().postDelayed(new Runnable() {
      @Override
      public void run() {
        stopScan();
      }
    }, period);
  }

  /**
   * Stops scanning for temperature data from BLE sensors.
   */
  public void stopScan() {

    mBluetoothAdapter.stopLeScan(this);
  }

  @Override
  public void onLeScan(BluetoothDevice device, final int rssi, final byte[] scanRecord) {

    final BluetoothDevice dev = device;
    String s = device.getName();

    if(s != null) {

      if(s.startsWith("WS")) {
        ((Activity)mContext).runOnUiThread(new Runnable() {
          @Override
          public void run() {

            ArrayList<WeightScaleSettings> userSettings = new ArrayList<WeightScaleSettings>();


            WSGattCallback callback = new WSGattCallback(userSettings, WSGattCallback.WEIGHT_UNIT_KG, true, true, true);

            callback.addCallback(new WeightScaleBLECallback() {
              @Override
              public void onLastWeightReceived(final WeightData weightData) {
                ((Activity)mContext).runOnUiThread(new Runnable() {
                  @Override
                  public void run() {
                    Measurement measurement = new Measurement(mContext);
                    measurement.setWeight((float) weightData.getWeight());
                    measurement.setBodyFat((float) weightData.getBodyFat());
                    measurement.setBodyWater((float) weightData.getHydration());
                    measurement.setMuscleMass((float) weightData.getMuscle());
                    measurement.setBoneMass((float) weightData.getBone());
                    measurement.setDailyCalorieIntake((short) weightData.getAmr());

                    measurement.save();

                    Intent i = new Intent(mContext, ShowMeasurementActivity.class);
                    i.putExtra("position", Measurement.getPosition(mContext, measurement.getId()));
                    ((Activity) mContext).startActivityForResult(i, 0);
                  }
                });

              }

              @Override
              public void onWeightDataReceived(ArrayList<WeightData> weightDatas) {

              }

              @Override
              public void onWeightScaleConnected(BluetoothDevice bluetoothDevice) {

              }

              @Override
              public void onWeightScaleDisconnected(BluetoothDevice bluetoothDevice) {

              }
            });

            dev.connectGatt(mContext, false, callback);
          }
        });
      }
    }
  }
}

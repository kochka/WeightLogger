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
package org.kochka.android.weightlogger.tools;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;

import org.kochka.android.weightlogger.data.Database;
import org.kochka.android.weightlogger.data.Measurement;

import android.content.Context;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

import com.garmin.fit.DateTime;
import com.garmin.fit.FileEncoder;
import com.garmin.fit.WeightScaleMesg;

public class Export {
  
  public static String buildFitFile(Context context, LinkedList<Measurement> measurements) throws StorageNotMountedException {
    checkExternalStorage();
      
    createDirIfNotExists(context);
    SimpleDateFormat fileNameFormater = new SimpleDateFormat("yyyyMMdd_kkmmss");
    
    String filename = "ws_" + fileNameFormater.format(new Date()) + ".fit";
    FileEncoder encoder = new FileEncoder(new File(path(context), filename));

    WeightScaleMesg wm;
    for (Measurement measurement : measurements) {
      wm = new WeightScaleMesg();
      wm.setTimestamp(new DateTime(measurement.getRecordedAt().getTime()));
      wm.setWeight(measurement.getWeight());
      wm.setPercentFat(measurement.getBodyFat());   
      wm.setPercentHydration(measurement.getBodyWater());
      wm.setMuscleMass(measurement.getMuscleMass());
      if(measurement.getDailyCalorieIntake() != null)
        wm.setActiveMet((float) measurement.getDailyCalorieIntake());
      wm.setPhysiqueRating(measurement.getPhysiqueRating());
      wm.setVisceralFatRating(measurement.getVisceralFatRating());
      wm.setBoneMass(measurement.getBoneMass());
      wm.setMetabolicAge(measurement.getMetabolicAge());
      encoder.write(wm);
    }
    
    encoder.close();
    return filename;
  }
  
  public static String buildCsvFile(Context context, LinkedList<Measurement> measurements) throws StorageNotMountedException {
    checkExternalStorage();
    
    createDirIfNotExists(context);
    SimpleDateFormat fileNameFormater = new SimpleDateFormat("yyyyMMdd");
    
    String filename = "ws_" + fileNameFormater.format(new Date()) + ".csv";
    
    try {
      FileWriter fCsv = new FileWriter(new File(path(context), filename));
      fCsv.append("Recorded At,Weight,Body Fat,Body Water,Muscle Mass,Muscle Mass,Daily Calorie Intake,Physique Rating,Bone Mass,Metabolic Age\n");
      
      for (Measurement measurement : measurements) {
        fCsv.append(measurement.getFormatedRecordedAt() + " " + measurement.getFormatedRecordedAtTime()  + ",");
        fCsv.append(measurement.getConvertedWeight() + ",");
        fCsv.append(measurement.getBodyFat() + ",");
        fCsv.append(measurement.getBodyWater() + ",");
        fCsv.append(measurement.getConvertedMuscleMass() + ",");
        fCsv.append(measurement.getDailyCalorieIntake() + ",");
        fCsv.append(measurement.getPhysiqueRating() + ",");
        fCsv.append(measurement.getVisceralFatRating() + ",");
        fCsv.append(measurement.getConvertedBoneMass() + ",");
        fCsv.append(measurement.getMetabolicAge() + "\n");
      }
      
      fCsv.flush();
      fCsv.close();
    } catch (Exception e) {
      Log.e("WeightLogger :: ", e.toString());
    }
    
    return filename;
  }
  
  public static String database(Context context) throws StorageNotMountedException, IOException {
    checkExternalStorage();
    
    createDirIfNotExists(context);
    SimpleDateFormat fileNameFormater = new SimpleDateFormat("yyyyMMdd_kkmm");
    String filename = fileNameFormater.format(new Date()) + ".bkp";
    
    Database.getRawInstance(context).exportDatabase(path(context) + "/" + filename);

    return filename;
  }
  
  public static String path(Context context) {
    String export_dir = PreferenceManager.getDefaultSharedPreferences(context).getString("export_dir", "/WeightLogger");
    return Environment.getExternalStorageDirectory().getAbsolutePath() + export_dir;
  }
  
  private static void checkExternalStorage() throws StorageNotMountedException {
    if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
      throw new StorageNotMountedException();
  }
  
  private static void createDirIfNotExists(Context context) {
    File file = new File(Export.path(context));
    if (!file.exists()) {
      if (!file.mkdirs()) {
        Log.e("WeightLogger :: ", "Problem creating app folder");
      }
    }
  }
}

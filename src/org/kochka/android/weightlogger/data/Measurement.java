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
package org.kochka.android.weightlogger.data;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.LinkedList;

import org.kochka.android.weightlogger.R;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.preference.PreferenceManager;


public class Measurement {
  
  public static final String TABLE_NAME = "measurements";
  
  private Context context;

  private static final byte ID                  = 0;
  private static final byte WEIGHT              = 1;
  private static final byte BODY_FAT            = 2;
  private static final byte BODY_WATER          = 3;
  private static final byte MUSCLE_MASS         = 4;
  private static final byte DAILY_CALORY_INTAKE = 5;
  private static final byte PHYSIQUE_RATING     = 6;
  private static final byte VISCERAL_FAT_RATING = 7;
  private static final byte BONE_MASS           = 8;
  private static final byte METABOLIC_AGE       = 9;
  private static final byte RECORDED_AT         = 10;
  private static final byte EXPORTED            = 11;

  private static String UNIT = "kg";
  private static short MUSCULAR_MASS_UNIT = 1;
  private static short GENDER = 1;
  private static short AGE = 20;
  
  private static final float KG_IN_LB = 2.20462262f;
  private static final int BODY_FAT_MATRIX[][][] = {{{8,19,25},{11,21,28},{13,25,30}},{{21,33,39},{23,34,40},{24,36,42}}};
  
  private Integer id;
  private Float weight;
  private Float body_fat;
  private Float body_water;
  private Float muscle_mass;
  private Short daily_calorie_intake;
  private Short physique_rating;
  private Short visceral_fat_rating;
  private Float bone_mass;
  private Short metabolic_age;
  private GregorianCalendar recorded_at = new GregorianCalendar();
  private boolean exported;
  
  private boolean convert_to_lb = false;
  private boolean muscle_mass_in_percent = false;
  
  private DateFormat dateFormater = DateFormat.getDateInstance(DateFormat.LONG);
  private DateFormat timeFormater =  DateFormat.getTimeInstance(DateFormat.SHORT);
  
  public Measurement(Context context) {
    this.context = context;
    
  }

  public Measurement(Context context, boolean convert_to_lb, boolean muscle_mass_in_percent) {
    this(context);
    this.convert_to_lb = convert_to_lb;
    this.muscle_mass_in_percent = muscle_mass_in_percent;
  }
  
  public Measurement(Context context, Integer id, Float weight, Float body_fat, Float body_water, Float muscle_mass, Short daily_calorie_intake,
                     Short physique_rating, Short visceral_fat_rating, Float bone_mass, Short metabolic_age, Long recorded_at, boolean exported) {
    this(context);
    setId(id);
    setWeight(weight);
    setBodyFat(body_fat);
    setBodyWater(body_water);
    setMuscleMass(muscle_mass);
    setDailyCalorieIntake(daily_calorie_intake);
    setPhysiqueRating(physique_rating);
    setVisceralFatRating(visceral_fat_rating);
    setBoneMass(bone_mass);
    setMetabolicAge(metabolic_age);
    setRecordedAt(recorded_at);
    setExported(exported);
  }
  
  public Measurement(Context context, Integer id, Float weight, Float body_fat, Float body_water, Float muscle_mass, Short daily_calorie_intake,
      Short physique_rating, Short visceral_fat_rating, Float bone_mass, Short metabolic_age, Long recorded_at, boolean exported, boolean convert_to_lb, boolean muscle_mass_in_percent) {
    this(context, id, weight, body_fat, body_water, muscle_mass, daily_calorie_intake, physique_rating, visceral_fat_rating, bone_mass, metabolic_age, recorded_at, exported);
    this.convert_to_lb = convert_to_lb;
    this.muscle_mass_in_percent = muscle_mass_in_percent;
  }
  
  public Measurement(Context context, Float weight, Float body_fat, Float body_water, Float muscle_mass, Short daily_calorie_intake,
                     Short physique_rating, Short visceral_fat_rating, Float bone_mass, Short metabolic_age, Long recorded_at, boolean exported) {
    this(context, null, weight, body_fat, body_water, muscle_mass, daily_calorie_intake, physique_rating, visceral_fat_rating, bone_mass, metabolic_age, recorded_at, exported);
  }
   
  public Measurement(Context context, Float weight, Float body_fat, Float body_water, Float muscle_mass, Short daily_calorie_intake,
      Short physique_rating, Short visceral_fat_rating, Float bone_mass, Short metabolic_age, Long recorded_at, boolean exported, boolean convert_to_lb, boolean muscle_mass_in_percent) {
    this(context, null, weight, body_fat, body_water, muscle_mass, daily_calorie_intake, physique_rating, visceral_fat_rating, bone_mass, metabolic_age, recorded_at, exported, convert_to_lb, muscle_mass_in_percent);
  }
  // Getters & setters
  
  public Integer getId() {
    return id;
  }
  
  public void setId(Integer id) {
    this.id = id;
  }
  
  public Float getWeight() {
    return weight;
  }
  
  public Float getConvertedWeight() {
    return convertToUnit(weight, true);
  }
 
  public String getFormatedWeight() {
    return formatToUnit(weight);
  }
  
  public void setWeight(Float weight) {
    this.weight = weight;
  }
  
  public void setConvertedWeight(Float weight) {
    this.weight = convertToUnit(weight, false);
  }
  
  public Float getBodyFat() {
    return body_fat;
  }

  public String getFormatedBodyFat() {
    return body_fat + " %";
  }
  
  public String getBodyFatInfo() {
    int rates[];
    if (AGE <= 39)
      rates = BODY_FAT_MATRIX[GENDER -1][0];
    else if(AGE <= 59)
      rates = BODY_FAT_MATRIX[GENDER -1][1]; 
    else
      rates = BODY_FAT_MATRIX[GENDER -1][2];
    
    if (body_fat < rates[0])
      return context.getString(R.string.bf_underfat);
    else if(body_fat < rates[1])
      return context.getString(R.string.bf_healthy);
    else if(body_fat < rates[2])
      return context.getString(R.string.bf_overfat);
    else
      return context.getString(R.string.bf_obese);
  }
  
  public void setBodyFat(Float body_fat) {
    this.body_fat = body_fat;
  }
  
  public Float getBodyWater() {
    return body_water;
  }
  
  public String getFormatedBodyWater() {
    return body_water + " %";
  }

  public String getBodyWaterInfo() {
    if (GENDER == 1) {
      return (body_water >= 50 && body_water <= 65) ? context.getString(R.string.normal) : context.getString(R.string.abnormal);
    } else {
      return (body_water >= 45 && body_water <= 60) ? context.getString(R.string.normal) : context.getString(R.string.abnormal);  
    }
  }
  
  public void setBodyWater(Float body_water) {
    this.body_water = body_water;
  }
  
  public Float getMuscleMass() {
    return muscle_mass;
  }

  public Float getConvertedMuscleMass() {
    if (muscle_mass != null) {
      if (muscle_mass_in_percent) {
        if (weight != null && weight != 0)
          return Math.round(muscle_mass / weight * 1000) / 10.0f;
        else return 0.0f;
      } else {
        return convertToUnit(muscle_mass, true);
      }
    }
    return muscle_mass;
  }
  
  public String getFormatedMuscleMass() {
    if (muscle_mass_in_percent)
      return getConvertedMuscleMass() + " %";
    else
      return formatToUnit(muscle_mass);
  }

  public void setMuscleMass(Float muscle_mass) {
    this.muscle_mass = muscle_mass;
  }
  
  public void setConvertedMuscleMass(Float muscle_mass) throws Exception {
    if (muscle_mass != null) {
      if (muscle_mass_in_percent) {
        if (weight != null)
          this.muscle_mass =  weight * muscle_mass / 100;
        else
          throw new Exception("weight missing");
      } else {
        this.muscle_mass = convertToUnit(muscle_mass, false);
      }
    }
  }
  
  public Short getDailyCalorieIntake() {
    return daily_calorie_intake;
  }
  
  public String getFormatedDailyCalorieIntake() {
    return daily_calorie_intake + " kcal";
  }
  
  public void setDailyCalorieIntake(Short daily_calorie_intake) {
    this.daily_calorie_intake = daily_calorie_intake;
  }
  
  public Short getPhysiqueRating() {
    return physique_rating;
  }
  
  public String getPhysiqueRatingInfo() {
    try {
      return context.getApplicationContext().getResources().getStringArray(R.array.physique_rating)[physique_rating - 1];
    } catch (Exception e) {
      return "";
    }
  }
  
  public void setPhysiqueRating(Short physique_rating) {
    this.physique_rating = physique_rating;
  }
  
  public Short getVisceralFatRating() {
    return visceral_fat_rating;
  }
  
  public String getVisceralFatRatingInfo() {
    return (visceral_fat_rating <= 12) ? context.getString(R.string.vfr_healthy) : context.getString(R.string.vfr_excess);
  }
  
  public void setVisceralFatRating(Short visceral_fat_rating) {
    this.visceral_fat_rating = visceral_fat_rating;
  }

  public Float getBoneMass() {
    return bone_mass;
  }

  public Float getConvertedBoneMass() {
    return convertToUnit(bone_mass, true);
  }
  
  public String getFormatedBoneMass() {
    return formatToUnit(bone_mass);
  }

  public void setBoneMass(Float bone_mass) {
    this.bone_mass = bone_mass;
  }
  
  public void setConvertedBoneMass(Float bone_mass) {
    this.bone_mass = convertToUnit(bone_mass, false);
  }
  
  public Short getMetabolicAge() {
    return metabolic_age;
  }
  
  public String getFormatedMetabolicAge() {
    return metabolic_age + " " + context.getString(R.string.metabolic_age_years_old);
  }
  
  public void setMetabolicAge(Short metabolic_age) {
    this.metabolic_age = metabolic_age;
  }
  
  public GregorianCalendar getRecordedAt() {
    return recorded_at;
  }
  
  public void setRecordedAt(Long recorded_at) {
    this.recorded_at.setTimeInMillis(recorded_at);
  }

  public void setRecordedAt(int year, int month, int day) {
    this.recorded_at.set(year, month, day);
  }

  public void setRecordedAt(int hour, int minute) {
    this.recorded_at.set(Calendar.HOUR_OF_DAY, hour);
    this.recorded_at.set(Calendar.MINUTE, minute);
  }
  
  public String getFormatedRecordedAt() {
    return dateFormater.format(recorded_at.getTime());
  }

  public String getFormatedRecordedAtTime() {
    return timeFormater.format(recorded_at.getTime());
  }
  
  public boolean isExported() {
    return exported;
  }
  
  public void setExported(boolean exported) {
    this.exported = exported;
  }
  
  private Float convertToUnit(Float value, boolean get) {
    if (value != null) {
      if (convert_to_lb) {
        if (get)
          return Math.round(value * KG_IN_LB * 10) / 10.0f;
        else
          return value / KG_IN_LB;
      }
      return Math.round(value * 10) / 10.0f;
    }
    return value;
  }
  
  private String formatToUnit(Float value) {
    return convertToUnit(value, true) + (convert_to_lb ? " lbs" : " kg");
  }
  
  public boolean isNew() {
    return id == null;
  }
  
  // Save the measurement into database
  public boolean save() {
    return isNew() ? insert() : update();
  }
  
  // Delete the measurement from database
  public boolean destroy() {
    return delete(context, id);
  }
  
  // Insert the measurement
  private boolean insert() {
    int ai = (int) getDb(context).insert(TABLE_NAME, null, getContentValues());
    
    if (ai == -1)
      return false;
    else {
      id = ai;
      return true;
    }
  }
  
  // Update the measurement
  private boolean update() {
    return getDb(context).update(TABLE_NAME, getContentValues(), "id = " + id, null) > 0;
  }
  
  // Content values
  protected ContentValues getContentValues() {
    ContentValues values = new ContentValues();
    values.put("weight", weight);
    values.put("body_fat", body_fat);
    values.put("body_water", body_water);
    values.put("muscle_mass", muscle_mass);
    values.put("daily_calorie_intake", daily_calorie_intake);
    values.put("physique_rating", physique_rating);
    values.put("visceral_fat_rating", visceral_fat_rating);
    values.put("bone_mass", bone_mass);
    values.put("metabolic_age", metabolic_age);
    values.put("recorded_at", recorded_at.getTimeInMillis());
    values.put("exported", exported ? 1 : 0);
    return values;
  }
  
  public String toString(){
    SimpleDateFormat dateFormater = new SimpleDateFormat("yyyy-MM-dd");
    return "ID : " + id 
         + "\nWeight : " + weight 
         + "\nBody Fat : " + body_fat 
         + "\nBody water : " + body_water
         + "\nMuscle mass : " + muscle_mass 
         + "\nDaily calorie intake : " + daily_calorie_intake
         + "\nPhysique rating : " + physique_rating 
         + "\nVisceral fat rating : " + visceral_fat_rating 
         + "\nBone mass : " + bone_mass 
         + "\nMetabolic age : " + metabolic_age 
         + "\nRecorded at : " + dateFormater.format(recorded_at.getTime()) 
         + "\nExported : " + exported;
  }
  
  // Class methods
  
  // Get all measurements
  static public LinkedList<Measurement> getAll(Context context) {
    return getAll(context, null, true);
  }

  static public LinkedList<Measurement> getAll(Context context, String where) {
    return getAll(context, where, true);  
  }
  
  static public LinkedList<Measurement> getAll(Context context, String where, boolean orderDesc) {
    LinkedList<Measurement> measurements = new LinkedList<Measurement>();
    setPreferences(context);
    String order = orderDesc ? "DESC" : "ASC";
    Cursor cursor = getDb(context).query(TABLE_NAME, null, where, null, null, null, "recorded_at " + order);
    cursor.moveToFirst();
    while (!cursor.isAfterLast()) {
      Measurement m = getInstanceFromCursor(context, cursor);
      measurements.add(m);
      cursor.moveToNext();
    }
    cursor.close();
    return measurements;
  }

  // Get all measurements to export
  static public LinkedList<Measurement> getAllToExport(Context context) {
    return getAll(context, "exported = 0");
  }

  static public void setAllAsExported(Context context) {
    ContentValues values = new ContentValues();
    values.put("exported", 1);
    getDb(context).update(TABLE_NAME, values, "exported = 0", null);
  }
  
  
  static public Measurement getById(Context context, int id) {
    setPreferences(context);
    Cursor cursor = getDb(context).query(TABLE_NAME, null, "id="+id, null, null, null, null);
    cursor.moveToFirst();
    Measurement measurement = getInstanceFromCursor(context, cursor);
    cursor.close();
    return measurement;
  }

  static public Measurement getLast(Context context) {
    setPreferences(context);
    Cursor cursor = getDb(context).query(TABLE_NAME, null, null, null, null, null, "recorded_at DESC", "1");
    cursor.moveToFirst();
    Measurement measurement = getInstanceFromCursor(context, cursor);
    cursor.close();
    return measurement;
  }
  
  // Get count
  static public int getCount(Context context) {
    SQLiteStatement statement = getDb(context).compileStatement("SELECT COUNT(*) FROM " + TABLE_NAME);
    int result = (int) statement.simpleQueryForLong();
    statement.close();
    return result;
  }

  // Get count
  static public int getPosition(Context context, Integer id) {
    SQLiteStatement statement = getDb(context).compileStatement("SELECT COUNT(*) FROM " + TABLE_NAME 
                                                              + " WHERE recorded_at > (SELECT recorded_at FROM " + TABLE_NAME + " WHERE id = " + id + ")");
    int result = (int) statement.simpleQueryForLong();
    statement.close();
    return result;
  }
  
  // Delete the measurement from database
  static public boolean delete(Context context, Integer id) {
    if (id != null)
      return getDb(context).delete(TABLE_NAME, "id=" + id, null) > 0;
    else 
      return false;
  }
  
  // Purge table
  static public void purge(Context context) {
    getDb(context).execSQL("DELETE FROM " + TABLE_NAME +"; ");
    getDb(context).execSQL("DELETE FROM sqlite_sequence WHERE name=\"" + TABLE_NAME + "\"");
  }
  
  // Get database instance
  static private SQLiteDatabase getDb(Context context) {
    return Database.getInstance(context);
  }
  
  //Get database instance
  static private Measurement getInstanceFromCursor(Context context, Cursor cursor) {
    return new Measurement(context, cursor.getInt(ID), cursor.getFloat(WEIGHT), 
               (cursor.isNull(BODY_FAT)) ? null : cursor.getFloat(BODY_FAT), 
               (cursor.isNull(BODY_WATER)) ? null : cursor.getFloat(BODY_WATER), 
               (cursor.isNull(MUSCLE_MASS)) ? null : cursor.getFloat(MUSCLE_MASS), 
               (cursor.isNull(DAILY_CALORY_INTAKE)) ? null : cursor.getShort(DAILY_CALORY_INTAKE), 
               (cursor.isNull(PHYSIQUE_RATING)) ? null : cursor.getShort(PHYSIQUE_RATING), 
               (cursor.isNull(VISCERAL_FAT_RATING)) ? null : cursor.getShort(VISCERAL_FAT_RATING), 
               (cursor.isNull(BONE_MASS)) ? null : cursor.getFloat(BONE_MASS), 
               (cursor.isNull(METABOLIC_AGE)) ? null : cursor.getShort(METABOLIC_AGE), 
               cursor.getLong(RECORDED_AT), 
               cursor.getInt(EXPORTED) != 0,
               UNIT.equals("lb"),
               MUSCULAR_MASS_UNIT == 2);
  }
 
  static private void setPreferences(Context context) {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
    UNIT   = preferences.getString("unit", "kg");
    MUSCULAR_MASS_UNIT = Short.parseShort(preferences.getString("muscle_mass_unit", "1"));
    GENDER = Short.parseShort(preferences.getString("gender", "1"));
    AGE    = Short.parseShort(preferences.getString("age", "20"));
  }
}

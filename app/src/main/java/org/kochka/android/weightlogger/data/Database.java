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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class Database extends SQLiteOpenHelper {
  
  private static final String DATABASE_NAME = "WeightLogger";
  private static final int DATABASE_VERSION = 1;
  private static String DATABASE_PATH = "/data/data/org.kochka.android.weightlogger/databases/"+DATABASE_NAME;
  
  private static Database instance = null;
  
  public Database(Context context) {
    super(context, DATABASE_NAME, null, DATABASE_VERSION);
  }

  public static Database getRawInstance(Context context) {
    if (instance == null) {
      instance = new Database(context);
    }
    return instance;
  }
  
  public static SQLiteDatabase getInstance(Context context) {
    return getRawInstance(context).getWritableDatabase();
  }
  
  @Override
  public void onCreate(SQLiteDatabase db) {
    // Create measurements table
    db.execSQL("CREATE TABLE measurements (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
             + "weight REAL NOT NULL, body_fat REAL, body_water REAL, muscle_mass REAL,"
             + "daily_calorie_intake INTEGER, physique_rating INTEGER, visceral_fat_rating REAL,"
             + "bone_mass REAL, metabolic_age INTEGER, recorded_at INTEGER, exported INTEGER NOT NULL DEFAULT 0);"
             + "CREATE INDEX idx_recorded_at on measurements (recorded_at ASC);");
  }
 
  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    db.execSQL("DROP TABLE measurements;");
    onCreate(db);
  }
  
  public boolean exportDatabase(String dbPath) throws IOException {
    File newDb = new File(DATABASE_PATH);
    File oldDb = new File(dbPath);
    if (newDb.exists()) {
      copyFile(new FileInputStream(newDb), new FileOutputStream(oldDb));
      return true;
    }
    return false;
  }
  
  public boolean importDatabase(String dbPath) throws IOException {
    close();
    File newDb = new File(dbPath);
    File oldDb = new File(DATABASE_PATH);
    if (newDb.exists()) {
        copyFile(new FileInputStream(newDb), new FileOutputStream(oldDb));
        getWritableDatabase().close();
        return true;
    }
    return false;
  }
  
  private static void copyFile(FileInputStream fromFile, FileOutputStream toFile) throws IOException {
    try {
      byte[] buffer = new byte[1024];
      while (fromFile.read(buffer) > 0) {
        toFile.write(buffer);
      }
      toFile.flush();
    } finally {
      toFile.close();
      fromFile.close();
    }
  }
}
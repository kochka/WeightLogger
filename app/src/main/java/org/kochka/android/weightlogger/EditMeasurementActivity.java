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

import java.util.Calendar;
import java.util.GregorianCalendar;

import org.kochka.android.weightlogger.data.Measurement;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.text.format.DateFormat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.CheckBox;
import android.widget.TimePicker;
import android.widget.Toast;

public class EditMeasurementActivity extends ActionBarActivity {
  
  Measurement measurement;
  
  boolean body_fat_required;
  boolean body_water_required;
  boolean muscle_mass_required;
  boolean daily_calorie_intake_required;
  boolean physique_rating_required;
  boolean visceral_fat_rating_required;
  boolean bone_mass_required;
  boolean metabolic_age_required;
    
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.edit_measurement);

    Toolbar actionBar = (Toolbar) findViewById(R.id.actionbar);
    setSupportActionBar(actionBar);
    actionBar.setNavigationIcon(getResources().getDrawable(R.drawable.abc_ic_ab_back_mtrl_am_alpha));
    actionBar.setNavigationOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        onBackPressed();
      }
    });
    
    // Set recorded_at date
    findViewById(R.id.recorded_at_button).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        GregorianCalendar dt = measurement.getRecordedAt();
        (new DatePickerDialog(EditMeasurementActivity.this, dateSetListener, dt.get(Calendar.YEAR), dt.get(Calendar.MONTH), dt.get(Calendar.DAY_OF_MONTH))).show();
      }
    });
    
    Bundle b = getIntent().getExtras();
    // New
    if (b == null) {
      // Load preferences
      SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

      getSupportActionBar().setTitle(R.string.title_new);
      measurement = new Measurement(this, preferences.getString("unit", "kg").equals("lb"), Short.parseShort(preferences.getString("muscle_mass_unit", "1")) == 2);
      
      body_fat_required             = preferences.getBoolean("body_fat", true);
      body_water_required           = preferences.getBoolean("body_water", true);
      muscle_mass_required          = preferences.getBoolean("muscle_mass", true);
      daily_calorie_intake_required = preferences.getBoolean("daily_calorie_intake", true);
      physique_rating_required      = preferences.getBoolean("physique_rating", true);
      visceral_fat_rating_required  = preferences.getBoolean("visceral_fat_rating", true);
      bone_mass_required            = preferences.getBoolean("bone_mass", true);
      metabolic_age_required        = preferences.getBoolean("metabolic_age", true);
      findViewById(R.id.exported_row).setVisibility(View.GONE);

      if (preferences.getBoolean("preload", true) && Measurement.getCount(this) > 0) {
        preload();
        actionBar.setSubtitle(R.string.preload);
      }
    // Edit  
    } else {
      getSupportActionBar().setTitle(R.string.title_edit);
      measurement = Measurement.getById(this, b.getInt("id"));
      
      body_fat_required             = (measurement.getBodyFat() != null);
      body_water_required           = (measurement.getBodyWater() != null);
      muscle_mass_required          = (measurement.getMuscleMass() != null);
      daily_calorie_intake_required = (measurement.getDailyCalorieIntake() != null);
      physique_rating_required      = (measurement.getPhysiqueRating() != null);
      visceral_fat_rating_required  = (measurement.getVisceralFatRating() != null);
      bone_mass_required            = (measurement.getBoneMass() != null);
      metabolic_age_required        = (measurement.getMetabolicAge() != null);
    }
    
    loadFields();
    hideRows();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.edit_measurement_actionbar, menu);
    if (getIntent().getExtras() == null) {
      Toolbar actionBar = (Toolbar) findViewById(R.id.actionbar);
      actionBar.getMenu().findItem(R.id.item_delete).setVisible(false);
    }
    return true;
  }

  @Override
  public void onStop() {
    super.onStop();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.item_delete:
        destroyMeasurement();
        return true;
      case R.id.item_save:
        saveMeasurement();
        return true;       
    }
    return super.onOptionsItemSelected(item);
  }
  
  private void saveMeasurement() {
    if (unloadFields()) {
      measurement.save();
      Toast.makeText(this, R.string.record_saved, Toast.LENGTH_SHORT).show();
      setResult(RESULT_OK, new Intent().putExtra("position", Measurement.getPosition(this, measurement.getId())));
      finish();
    } else {
      Toast.makeText(this, R.string.missing_fields, Toast.LENGTH_SHORT).show();
    }
  }
  
  private void destroyMeasurement() {
    new AlertDialog.Builder(this)
    .setTitle(R.string.confirm_delete)
    .setIcon(android.R.drawable.ic_dialog_alert)
    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int whichButton) {
          measurement.destroy();
          setResult(RESULT_OK, new Intent().putExtra("deleted", true));
          finish();
          Toast.makeText(EditMeasurementActivity.this, R.string.record_deleted, Toast.LENGTH_SHORT).show();
        }})
    .setNegativeButton(android.R.string.no, null).show();
  }

  private void hideRows() {
    if (!body_fat_required)             findViewById(R.id.body_fat_row).setVisibility(View.GONE);
    if (!body_water_required)           findViewById(R.id.body_water_row).setVisibility(View.GONE);
    if (!muscle_mass_required)          findViewById(R.id.muscle_mass_row).setVisibility(View.GONE);
    if (!daily_calorie_intake_required) findViewById(R.id.daily_calorie_intake_row).setVisibility(View.GONE);
    if (!physique_rating_required)      findViewById(R.id.physique_rating_row).setVisibility(View.GONE);
    if (!visceral_fat_rating_required)  findViewById(R.id.visceral_fat_rating_row).setVisibility(View.GONE);
    if (!bone_mass_required)            findViewById(R.id.bone_mass_row).setVisibility(View.GONE);
    if (!metabolic_age_required)        findViewById(R.id.metabolic_age_row).setVisibility(View.GONE);
  }
  
  private void preload() {
    Measurement m = Measurement.getLast(this);
    measurement.setWeight(m.getWeight());
    if (body_fat_required)             measurement.setBodyFat(m.getBodyFat());
    if (body_water_required)           measurement.setBodyWater(m.getBodyWater());
    if (muscle_mass_required)          measurement.setMuscleMass(m.getMuscleMass());
    if (daily_calorie_intake_required) measurement.setDailyCalorieIntake(m.getDailyCalorieIntake());
    if (physique_rating_required)      measurement.setPhysiqueRating(m.getPhysiqueRating());
    if (visceral_fat_rating_required)  measurement.setVisceralFatRating(m.getVisceralFatRating());
    if (bone_mass_required)            measurement.setBoneMass(m.getBoneMass());
    if (metabolic_age_required)        measurement.setMetabolicAge(m.getMetabolicAge());   
  }
  
  private void loadFields() {
    ((TextView)findViewById(R.id.recorded_at)).setText(measurement.getFormatedRecordedAt());
    ((TextView)findViewById(R.id.recorded_at_time)).setText(measurement.getFormatedRecordedAtTime());
    if (measurement.getWeight() != null)             ((EditText)findViewById(R.id.weight)).setText(measurement.getConvertedWeight().toString());
    if (measurement.getBodyFat() != null)            ((EditText)findViewById(R.id.body_fat)).setText(measurement.getBodyFat().toString());
    if (measurement.getBodyWater() != null)          ((EditText)findViewById(R.id.body_water)).setText(measurement.getBodyWater().toString());
    if (measurement.getMuscleMass() != null)         ((EditText)findViewById(R.id.muscle_mass)).setText(measurement.getConvertedMuscleMass().toString());
    if (measurement.getDailyCalorieIntake() != null) ((EditText)findViewById(R.id.daily_calorie_intake)).setText(measurement.getDailyCalorieIntake().toString());
    if (measurement.getPhysiqueRating() != null)     ((EditText)findViewById(R.id.physique_rating)).setText(measurement.getPhysiqueRating().toString());
    if (measurement.getVisceralFatRating() != null)  ((EditText)findViewById(R.id.visceral_fat_rating)).setText(measurement.getVisceralFatRating().toString());
    if (measurement.getBoneMass() != null)           ((EditText)findViewById(R.id.bone_mass)).setText(measurement.getConvertedBoneMass().toString());
    if (measurement.getMetabolicAge() != null)       ((EditText)findViewById(R.id.metabolic_age)).setText(measurement.getMetabolicAge().toString());
    ((CheckBox)findViewById(R.id.exported)).setChecked(measurement.isExported());
  }
  
  private boolean unloadFields() {
    try {
      measurement.setConvertedWeight(Float.parseFloat(((EditText)findViewById(R.id.weight)).getText().toString()));
      if (body_fat_required)             measurement.setBodyFat(Float.parseFloat(((EditText)findViewById(R.id.body_fat)).getText().toString()));
      if (body_water_required)           measurement.setBodyWater(Float.parseFloat(((EditText)findViewById(R.id.body_water)).getText().toString()));
      if (muscle_mass_required)          measurement.setConvertedMuscleMass(Float.parseFloat(((EditText)findViewById(R.id.muscle_mass)).getText().toString()));
      if (daily_calorie_intake_required) measurement.setDailyCalorieIntake(Short.parseShort(((EditText)findViewById(R.id.daily_calorie_intake)).getText().toString()));
      if (physique_rating_required)      measurement.setPhysiqueRating(Short.parseShort(((EditText)findViewById(R.id.physique_rating)).getText().toString()));
      if (visceral_fat_rating_required)  measurement.setVisceralFatRating(Short.parseShort(((EditText)findViewById(R.id.visceral_fat_rating)).getText().toString()));
      if (bone_mass_required)            measurement.setConvertedBoneMass(Float.parseFloat(((EditText)findViewById(R.id.bone_mass)).getText().toString()));
      if (metabolic_age_required)        measurement.setMetabolicAge(Short.parseShort(((EditText)findViewById(R.id.metabolic_age)).getText().toString()));
      measurement.setExported(((CheckBox)findViewById(R.id.exported)).isChecked());
      return true;
    } catch (Exception e) {
      return false;
    }
  }
  
  /* Date picker set date listener */
  private DatePickerDialog.OnDateSetListener dateSetListener = new DatePickerDialog.OnDateSetListener() {
    public void onDateSet(DatePicker view, int year, int month, int day) {
      measurement.setRecordedAt(year, month, day);
      ((TextView)findViewById(R.id.recorded_at)).setText(measurement.getFormatedRecordedAt());
      
      GregorianCalendar dt = measurement.getRecordedAt();
      (new TimePickerDialog(EditMeasurementActivity.this, timeSetListener, dt.get(Calendar.HOUR_OF_DAY), dt.get(Calendar.MINUTE), DateFormat.is24HourFormat(EditMeasurementActivity.this))).show();
    }
  };

  /* Time picker set date listener */
  private TimePickerDialog.OnTimeSetListener timeSetListener = new TimePickerDialog.OnTimeSetListener() {
    public void onTimeSet(TimePicker view, int hour, int minute) {
      measurement.setRecordedAt(hour, minute);
      ((TextView)findViewById(R.id.recorded_at_time)).setText(measurement.getFormatedRecordedAtTime());
    }
  };
}
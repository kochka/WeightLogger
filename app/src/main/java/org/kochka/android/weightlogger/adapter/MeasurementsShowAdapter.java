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
package org.kochka.android.weightlogger.adapter;

import java.text.SimpleDateFormat;
import java.util.LinkedList;
import java.util.Locale;

import org.kochka.android.weightlogger.R;
import org.kochka.android.weightlogger.data.Measurement;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MeasurementsShowAdapter extends BaseAdapter {
  Context context;
  LinkedList<Measurement> measurements;
  LayoutInflater inflater;
  
  final SimpleDateFormat dayFormater = new SimpleDateFormat("EEEE", Locale.getDefault());
  
  public MeasurementsShowAdapter(Context context) {
    this.context  = context;
    this.inflater = LayoutInflater.from(context);
    setList();
  }
  
  public MeasurementsShowAdapter(Context context, LinkedList<Measurement> measurements) {
    this.context  = context;
    this.inflater = LayoutInflater.from(context);
    this.measurements = measurements;
  }
  
  @Override
  public int getCount() {
    return measurements.size();
  }

  @Override
  public Object getItem(int position) {
    return measurements.get(position);
  }

  @Override
  public long getItemId(int position) {
    return measurements.get(position).getId();
  }

  public void refresh() {
    setList();
    notifyDataSetChanged();
  }
  
  private void setList() {
    this.measurements = Measurement.getAll(context);
  }
  
  private class ViewHolder {
    TextView     tv_recorded_at_day;
    TextView     tv_recorded_at;
    TextView     tv_recorded_at_time;
    TextView     tv_weight;
    TextView     tv_body_fat;
    TextView     tv_body_fat_info;
    LinearLayout ll_body_fat;
    TextView     tv_body_water;
    TextView     tv_body_water_info;
    LinearLayout ll_body_water;
    TextView     tv_muscle_mass;
    LinearLayout ll_muscle_mass;
    TextView     tv_daily_calorie_intake;
    LinearLayout ll_daily_calorie_intake;
    TextView     tv_physique_rating;
    TextView     tv_physique_rating_info;
    LinearLayout ll_physique_rating;
    TextView     tv_visceral_fat_rating;
    TextView     tv_visceral_fat_rating_info;
    LinearLayout ll_visceral_fat_rating;
    TextView     tv_bone_mass;
    LinearLayout ll_bone_mass;
    TextView     tv_metabolic_age;
    LinearLayout ll_metabolic_age;
  }
  
  public View getView(int position, View convertView, ViewGroup parent) {
    ViewHolder holder;
    
    if (convertView == null) {
      holder = new ViewHolder();
      convertView                        = inflater.inflate(R.layout.show_measurement_view, null);
      holder.tv_recorded_at_day          = (TextView)convertView.findViewById(R.id.recorded_at_day);
      holder.tv_recorded_at              = (TextView)convertView.findViewById(R.id.recorded_at);
      holder.tv_recorded_at_time         = (TextView)convertView.findViewById(R.id.recorded_at_time);
      holder.tv_weight                   = (TextView)convertView.findViewById(R.id.weight);
      holder.tv_body_fat                 = (TextView)convertView.findViewById(R.id.body_fat);
      holder.tv_body_fat_info            = (TextView)convertView.findViewById(R.id.body_fat_info);
      holder.ll_body_fat                 = (LinearLayout)convertView.findViewById(R.id.body_fat_layout);
      holder.tv_body_water               = (TextView)convertView.findViewById(R.id.body_water);
      holder.tv_body_water_info          = (TextView)convertView.findViewById(R.id.body_water_info);
      holder.ll_body_water               = (LinearLayout)convertView.findViewById(R.id.body_water_layout);
      holder.tv_muscle_mass              = (TextView)convertView.findViewById(R.id.muscle_mass);
      holder.ll_muscle_mass              = (LinearLayout)convertView.findViewById(R.id.muscle_mass_layout);
      holder.tv_daily_calorie_intake     = (TextView)convertView.findViewById(R.id.daily_calorie_intake);
      holder.ll_daily_calorie_intake     = (LinearLayout)convertView.findViewById(R.id.daily_calorie_intake_layout);
      holder.tv_physique_rating          = (TextView)convertView.findViewById(R.id.physique_rating);
      holder.tv_physique_rating_info     = (TextView)convertView.findViewById(R.id.physique_rating_info);
      holder.ll_physique_rating          = (LinearLayout)convertView.findViewById(R.id.physique_rating_layout);
      holder.tv_visceral_fat_rating      = (TextView)convertView.findViewById(R.id.visceral_fat_rating);
      holder.tv_visceral_fat_rating_info = (TextView)convertView.findViewById(R.id.visceral_fat_rating_info);
      holder.ll_visceral_fat_rating      = (LinearLayout)convertView.findViewById(R.id.visceral_fat_rating_layout);
      holder.tv_bone_mass                = (TextView)convertView.findViewById(R.id.bone_mass);
      holder.ll_bone_mass                = (LinearLayout)convertView.findViewById(R.id.bone_mass_layout);
      holder.tv_metabolic_age            = (TextView)convertView.findViewById(R.id.metabolic_age);
      holder.ll_metabolic_age            = (LinearLayout)convertView.findViewById(R.id.metabolic_age_layout);
      convertView.setTag(holder);
    } else {
      holder = (ViewHolder) convertView.getTag();
    }
    
    Measurement measurement = measurements.get(position);
    holder.tv_recorded_at_day.setText(dayFormater.format(measurement.getRecordedAt().getTime()));
    holder.tv_recorded_at.setText(measurement.getFormatedRecordedAt());
    holder.tv_recorded_at_time.setText(measurement.getFormatedRecordedAtTime());
    holder.tv_weight.setText(measurement.getFormatedWeight());
    
    if (measurement.getBodyFat() == null)
      holder.ll_body_fat.setVisibility(View.GONE);
    else {
      holder.ll_body_fat.setVisibility(View.VISIBLE);
      holder.tv_body_fat.setText(measurement.getFormatedBodyFat());
      holder.tv_body_fat_info.setText(measurement.getBodyFatInfo());
    }
    
    if (measurement.getBodyWater() == null)
      holder.ll_body_water.setVisibility(View.GONE);
    else {
      holder.ll_body_water.setVisibility(View.VISIBLE);
      holder.tv_body_water.setText(measurement.getFormatedBodyWater());
      holder.tv_body_water_info.setText(measurement.getBodyWaterInfo());  
    }
    
    if (measurement.getMuscleMass() == null)
      holder.ll_muscle_mass.setVisibility(View.GONE);
    else {
      holder.ll_muscle_mass.setVisibility(View.VISIBLE);
      holder.tv_muscle_mass.setText(measurement.getFormatedMuscleMass());    
    }
    
    if (measurement.getDailyCalorieIntake() == null)
      holder.ll_daily_calorie_intake.setVisibility(View.GONE);
    else {
      holder.ll_daily_calorie_intake.setVisibility(View.VISIBLE);
      holder.tv_daily_calorie_intake.setText(measurement.getFormatedDailyCalorieIntake());
    }
    
    if (measurement.getPhysiqueRating() == null)
      holder.ll_physique_rating.setVisibility(View.GONE);
    else {
      holder.ll_physique_rating.setVisibility(View.VISIBLE);
      holder.tv_physique_rating.setText(measurement.getPhysiqueRating().toString());
      holder.tv_physique_rating_info.setText(measurement.getPhysiqueRatingInfo());
    }
    
    if (measurement.getVisceralFatRating() == null)
      holder.ll_visceral_fat_rating.setVisibility(View.GONE);
    else {
      holder.ll_visceral_fat_rating.setVisibility(View.VISIBLE);
      holder.tv_visceral_fat_rating.setText(measurement.getVisceralFatRating().toString());
      holder.tv_visceral_fat_rating_info.setText(measurement.getVisceralFatRatingInfo());
    }
    
    if (measurement.getBoneMass() == null)
      holder.ll_bone_mass.setVisibility(View.GONE);
    else {
      holder.ll_bone_mass.setVisibility(View.VISIBLE);
      holder.tv_bone_mass.setText(measurement.getFormatedBoneMass());
    }
    
    if (measurement.getMetabolicAge() == null)
      holder.ll_metabolic_age.setVisibility(View.GONE);
    else {
      holder.ll_metabolic_age.setVisibility(View.VISIBLE);
      holder.tv_metabolic_age.setText(measurement.getFormatedMetabolicAge());
    }
    
    return convertView;
  }
}
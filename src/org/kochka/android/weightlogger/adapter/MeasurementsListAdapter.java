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
import android.widget.TextView;

public class MeasurementsListAdapter extends BaseAdapter {
  Context context;
  LinkedList<Measurement> measurements;
  LayoutInflater inflater;
  
  final SimpleDateFormat dayFormater = new SimpleDateFormat("EEEE", Locale.getDefault());

  public MeasurementsListAdapter(Context context) {
    this.context  = context;
    this.inflater = LayoutInflater.from(context);
    setList();
  }
  
  public MeasurementsListAdapter(Context context, LinkedList<Measurement> measurements) {
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
  
  public void removeItem(int position) {
    measurements.remove(position);
    notifyDataSetChanged();
  }

  public void refresh() {
    setList();
    notifyDataSetChanged();
  }
  
  private void setList() {
    this.measurements = Measurement.getAll(context);
  }
  
  private class ViewHolder {
    TextView tv_day;
    TextView tv_date;
    TextView tv_weight;
  }

  public View getView(int position, View convertView, ViewGroup parent) {
    ViewHolder holder;
    
    if (convertView == null) {
      holder = new ViewHolder();
      convertView = inflater.inflate(R.layout.measurement, null);
      holder.tv_day    = (TextView) convertView.findViewById(R.id.day);
      holder.tv_date   = (TextView) convertView.findViewById(R.id.date);
      holder.tv_weight = (TextView) convertView.findViewById(R.id.weight);
      convertView.setTag(holder);
    } else {
      holder = (ViewHolder) convertView.getTag();
    }
    
    Measurement measurement = measurements.get(position);
    holder.tv_day.setText(dayFormater.format(measurement.getRecordedAt().getTime()));
    holder.tv_date.setText(measurement.getFormatedRecordedAt());
    holder.tv_weight.setText(measurement.getFormatedWeight());
    
    return convertView;
  }
}
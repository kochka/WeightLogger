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

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;

import org.kochka.android.weightlogger.data.Measurement;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.LinearLayout;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GraphViewSeries;
import com.jjoe64.graphview.GraphViewSeries.GraphViewData;
import com.jjoe64.graphs.LineGraphView;
import com.markupartist.android.widget.ActionBar;

public class GraphActivity extends Activity {

  LinearLayout graphLayout;
  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.graph);
    
    ActionBar actionBar = (ActionBar) findViewById(R.id.actionbar);
    
    getMenuInflater().inflate(R.menu.graph_actionbar, actionBar.asMenu());
    actionBar.findAction(R.id.actionbar_item_home).setIntent(WeightLoggerActivity.createIntent(this));
    
    actionBar.setTitle(R.string.graph_title);
    
    actionBar.setDisplayShowHomeEnabled(true);
    actionBar.setDisplayHomeAsUpEnabled(true);
    
    graphLayout = (LinearLayout) findViewById(R.id.graph);
     
    loadGraph(R.id.item_graph_weight);
  }
  
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() != R.id.actionbar_item_home)
      loadGraph(item.getItemId());
    return super.onOptionsItemSelected(item);
  }
  
  private void loadGraph(int item_id) {
    ActionBar actionBar = (ActionBar) findViewById(R.id.actionbar);
    ImageView logo = (ImageView) findViewById(R.id.graph_pic);
    
    // Hide active menu and show others
    for (int i=0; i < actionBar.getActionCount(); i++) actionBar.getActionAt(i).setVisible(true);
    actionBar.findAction(item_id).setVisible(false);
    
    // Display subtitle
    switch (item_id) {
      case R.id.item_graph_weight:
        actionBar.setSubtitle(R.string.weight);
        logo.setImageResource(R.drawable.ic_weight);
        break;
      case R.id.item_graph_body_fat:
        actionBar.setSubtitle(R.string.body_fat);
        logo.setImageResource(R.drawable.ic_body_fat);
        break;
      case R.id.item_graph_body_water:
        actionBar.setSubtitle(R.string.body_water);
        logo.setImageResource(R.drawable.ic_body_water);
        break;
      case R.id.item_graph_muscle_mass:
        actionBar.setSubtitle(R.string.muscle_mass);
        logo.setImageResource(R.drawable.ic_muscle_mass);
        break;
    }
    
    // Load data
    LinkedList<Measurement> measurements = Measurement.getAll(this, null, false);
    Measurement measurement;
    long dt;
    
    ArrayList<GraphViewData> data = new ArrayList<GraphViewData>();

    for (int i=0; i < measurements.size(); i++) {
      measurement = measurements.get(i);
      dt = measurement.getRecordedAt().getTime().getTime();
      switch (item_id) {
        case R.id.item_graph_weight:
          data.add(new GraphViewData(dt, measurement.getConvertedWeight()));
          break;
        case R.id.item_graph_body_fat:
          if (measurement.getBodyFat() != null)
            data.add(new GraphViewData(dt, measurement.getBodyFat()));
          break;
        case R.id.item_graph_body_water:
          if (measurement.getBodyWater() != null)
            data.add(new GraphViewData(dt, measurement.getBodyWater()));
          break;
        case R.id.item_graph_muscle_mass:
          if (measurement.getMuscleMass() != null)
            data.add(new GraphViewData(dt, measurement.getConvertedMuscleMass()));
          break;
      }
    }
    
    GraphViewSeries series = new GraphViewSeries("", Color.rgb(0, 171, 188), data);
    
    GraphView graphView = new MLineGraphView(this);
    
    // Calculate viewport size
    graphView.setViewPortSize(864000000);
    
    
    graphView.addSeries(series);
    graphView.moveViewPortStartToTheEnd();
    
    graphLayout.removeAllViews();
    graphLayout.addView(graphView);
  }
}

class MLineGraphView extends LineGraphView {
  private DateFormat dateFormatter;
  private NumberFormat numberFormatter;
  
  public MLineGraphView(Context context) {
    super(context);
    this.setDrawBackground(true);
    this.setScalable(true);
  }
  
  @Override  
  protected String formatLabel(double value, boolean isValueX) {
    if (dateFormatter == null) {
      dateFormatter = DateFormat.getDateInstance(DateFormat.SHORT);  
    }
    if (numberFormatter == null) {
      numberFormatter = NumberFormat.getNumberInstance();
      numberFormatter.setMaximumFractionDigits(1);
    }
   
    if (isValueX) {  
      return dateFormatter.format(new Date((long) value));  
    } else 
      return numberFormatter.format(value);
  }
}
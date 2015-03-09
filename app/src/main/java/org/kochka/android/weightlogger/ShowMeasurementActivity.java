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

import org.kochka.android.weightlogger.adapter.MeasurementsShowAdapter;
import org.kochka.android.weightlogger.data.Measurement;
import org.taptwo.android.widget.ViewFlow;

//import com.markupartist.android.widget.ActionBar;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;


public class ShowMeasurementActivity  extends Activity {

  ViewFlow viewFlow;
  boolean dataChanged = false;
  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.show_measurement);
    
//    ActionBar actionBar = (ActionBar) findViewById(R.id.actionbar);
//    
//    getMenuInflater().inflate(R.menu.show_measurement_actionbar, actionBar.asMenu());
//    actionBar.findAction(R.id.actionbar_item_home).setIntent(WeightLoggerActivity.createIntent(this));
//    
//    actionBar.setDisplayShowHomeEnabled(true);
//    actionBar.setDisplayHomeAsUpEnabled(true);
    
    Bundle b = getIntent().getExtras();
    
    MeasurementsShowAdapter adapter = new MeasurementsShowAdapter(this);
    viewFlow = (ViewFlow) findViewById(R.id.viewflow);
    viewFlow.setAdapter(adapter, b.getInt("position"));
  }
  
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.item_edit:
        editMeasurement();
        return true;     
    }
    return super.onOptionsItemSelected(item);
  }
  
  private void editMeasurement() {
    Intent i = new Intent(this, EditMeasurementActivity.class);
    i.putExtra("id", ((Measurement) viewFlow.getSelectedItem()).getId());
    startActivityForResult(i, 0);
  }
  
  @Override
  public void onBackPressed() {
    if (dataChanged) {
      setResult(RESULT_OK);
      finish();
    } else 
      super.onBackPressed();
  }
  
  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (resultCode == RESULT_OK && data != null) {
      if (data.getBooleanExtra("deleted", false)) {
        setResult(RESULT_OK);
        finish();
      } else {
        ((MeasurementsShowAdapter) viewFlow.getAdapter()).refresh();
        viewFlow.setSelection(data.getIntExtra("position", 1));
        dataChanged = true;
      }
    }
  }
}

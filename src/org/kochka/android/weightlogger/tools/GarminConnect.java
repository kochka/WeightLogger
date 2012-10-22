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
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.kochka.android.weightlogger.tools.SimpleMultipartEntity;

public class GarminConnect {
 
  private DefaultHttpClient httpclient;
    
  public boolean signin(final String username, final String password) {
    httpclient = new DefaultHttpClient();
    final String signin_url = "https://connect.garmin.com/signin";
    try {
      // Create session
      httpclient.execute(new HttpGet(signin_url)).getEntity().consumeContent();

      // Sign in
      HttpPost post = new HttpPost(signin_url);
      List<NameValuePair> nvp = new ArrayList<NameValuePair>();
      nvp.add(new BasicNameValuePair("javax.faces.ViewState", "j_id1"));
      nvp.add(new BasicNameValuePair("login", "login"));
      nvp.add(new BasicNameValuePair("login:signInButton", "Sign In"));
      nvp.add(new BasicNameValuePair("login:loginUsernameField", username));
      nvp.add(new BasicNameValuePair("login:password", password));
      post.setEntity(new UrlEncodedFormEntity(nvp));
      httpclient.execute(post).getEntity().consumeContent();
      
      return isSignedIn();
    } catch (Exception e) {
      httpclient.getConnectionManager().shutdown();
      return false;
    }
  }
  
  public boolean isSignedIn() {
    if (httpclient == null) return false;
    try {
      HttpEntity entity = httpclient.execute(new HttpGet("http://connect.garmin.com/user/username")).getEntity();
      JSONObject js_user = new JSONObject(EntityUtils.toString(entity));
      entity.consumeContent();
      return !js_user.getString("username").equals("");
    } catch (Exception e) {
      return false;
    }
  }
  
  public boolean uploadFitFile(File fitFile) {
    if (httpclient == null) return false;
    try {
      HttpPost post = new HttpPost("http://connect.garmin.com/proxy/upload-service-1.1/json/upload/.fit");
      SimpleMultipartEntity mpEntity = new SimpleMultipartEntity();
      mpEntity.addPart("responseContentType", "text/html");
      mpEntity.addPart("fitFile", fitFile);
      post.setEntity(mpEntity); 
      HttpEntity entity = httpclient.execute(post).getEntity();
      JSONObject js_upload = new JSONObject(EntityUtils.toString(entity));
      entity.consumeContent();
      if (js_upload.getJSONObject("detailedImportResult").getJSONArray("failures").length() != 0) throw new Exception("upload error");    
      
      return true;
    } catch (Exception e) {
      return false;
    }
  }
  
  public boolean uploadFitFile(String fitFilePath) {
    return uploadFitFile(new File(fitFilePath));
  }
  
  public void close() {
    if (httpclient != null) {
      httpclient.getConnectionManager().shutdown();
      httpclient = null;
    }
  }
}

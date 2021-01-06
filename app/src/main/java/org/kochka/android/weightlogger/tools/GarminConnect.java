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

import android.support.annotation.NonNull;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.HttpStatus;
import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.client.entity.UrlEncodedFormEntity;
import cz.msebera.android.httpclient.client.methods.CloseableHttpResponse;
import cz.msebera.android.httpclient.client.methods.HttpGet;
import cz.msebera.android.httpclient.client.methods.HttpPost;
import cz.msebera.android.httpclient.cookie.Cookie;
import cz.msebera.android.httpclient.entity.mime.HttpMultipartMode;
import cz.msebera.android.httpclient.entity.mime.MultipartEntityBuilder;
import cz.msebera.android.httpclient.impl.client.DefaultHttpClient;
import cz.msebera.android.httpclient.impl.conn.PoolingClientConnectionManager;
import cz.msebera.android.httpclient.impl.conn.SchemeRegistryFactory;
import cz.msebera.android.httpclient.message.BasicNameValuePair;
import cz.msebera.android.httpclient.params.BasicHttpParams;
import cz.msebera.android.httpclient.params.HttpParams;
import cz.msebera.android.httpclient.util.EntityUtils;
// Disable custom entity, need to find a fix to avoid heavy external Apache libs
// import org.kochka.android.weightlogger.tools.SimpleMultipartEntity;


public class GarminConnect {

  private static final String GET_TICKET_URL = "https://connect.garmin.com/modern/?ticket=";

  private static final Pattern LOCATION_PATTERN = Pattern.compile("location: (.*)");
  private static final String CSRF_TOKEN_PATTERN = "name=\"_csrf\" *value=\"([A-Z0-9]+)\"";
  private static final String TICKET_FINDER_PATTERN = "ticket=([^']+?)\";";
  public static final String FIT_FILE_UPLOAD_URL = "https://connect.garmin.com/modern/proxy/upload-service/upload/.fit";

  private DefaultHttpClient httpclient;

  public boolean signin(final String username, final String password) {
    PoolingClientConnectionManager conman = new PoolingClientConnectionManager(SchemeRegistryFactory.createDefault());
    conman.setMaxTotal(20);
    conman.setDefaultMaxPerRoute(20);
    httpclient = new DefaultHttpClient(conman);

    final String signin_url = "https://sso.garmin.com/sso/signin?service=" +
            "https%3A%2F%2Fconnect.garmin.com%2Fmodern%2F" +
            "&webhost=https%3A%2F2%Fconnect.garmin.com%2Fmodern%2F" +
            "&source=https%3A%2F%2Fconnect.garmin.com%2Fsignin" +
            "&redirectAfterAccountLoginUrl=https%3A%2F%2Fconnect.garmin.com%2Fmodern%2F" +
            "&redirectAfterAccountCreationUrl=https%3A%2F%2Fconnect.garmin.com%2Fmodern%2F" +
            "&gauthHost=https%3A%2F%2Fsso.garmin.com%2Fsso" +
            "&locale=en_US" +
            "&id=gauth-widget" +
            "&cssUrl=https%3A%2F%2Fconnect.garmin.com%2Fgauth-custom-v1.2-min.css" +
            "&privacyStatementUrl=https%3A%2F%2Fwww.garmin.com%2Fen-US%2Fprivacy%2Fconnect%2F" +
            "&clientId=GarminConnect" +
            "&rememberMeShown=true" +
            "&rememberMeChecked=false" +
            "&createAccountShown=true" +
            "&openCreateAccount=false" +
            "&displayNameShown=false" +
            "&consumeServiceTicket=false" +
            "&initialFocus=true" +
            "&embedWidget=false" +
            "&generateExtraServiceTicket=true" +
            "&generateTwoExtraServiceTickets=true" +
            "&generateNoServiceTicket=false" +
            "&globalOptInShown=true" +
            "&globalOptInChecked=false" +
            "&mobile=false" +
            "&connectLegalTerms=true" +
            "&showTermsOfUse=false" +
            "&showPrivacyPolicy=false" +
            "&showConnectLegalAge=false" +
            "&locationPromptShown=true" +
            "&showPassword=true" +
            "&useCustomHeader=false" +
            "&mfaRequired=false" +
            "&rememberMyBrowserShown=false" +
            "&rememberMyBrowserChecked=false";

    try {
      HttpParams params = new BasicHttpParams();
      params.setParameter("http.protocol.handle-redirects", false);

      // Create session
      HttpEntity loginEntity = httpclient.execute(new HttpGet(signin_url)).getEntity();
      String loginContent = EntityUtils.toString(loginEntity);
      String csrf = getCSRFToken(loginContent);

      // Sign in
      HttpPost post = new HttpPost(signin_url);
      post.setHeader("Referer", signin_url);
      post.setParams(params);
      List<NameValuePair> nvp = new ArrayList<>();
      nvp.add(new BasicNameValuePair("embed", "false"));
      nvp.add(new BasicNameValuePair("username", username));
      nvp.add(new BasicNameValuePair("password", password));
      nvp.add(new BasicNameValuePair("_csrf", csrf));
      post.setEntity(new UrlEncodedFormEntity(nvp));
      HttpEntity entity1 = httpclient.execute(post).getEntity();

      String responseAsString = EntityUtils.toString(entity1);
      String ticket = getTicketIdFromResponse(responseAsString);

      // Ticket
      HttpGet get = new HttpGet(GET_TICKET_URL + ticket);
      get.setParams(params);
      Header getTicketLocation = httpclient.execute(get).getFirstHeader("location");

      // Follow redirections
      get = createHttpGetFromLocationHeader(getTicketLocation);
      get.setParams(params);
      httpclient.execute(get);

      // Initialise session. Redirect manually, as there are two URLs
      // marked by HttpClient as duplicates (but in fact these differ by queryparams).
      /*
      RedirectStrategy oldRedirectStrategy = httpclient.getRedirectStrategy();
      httpclient.setRedirectStrategy(new NoRedirectStrategy());
      CloseableHttpResponse initSessionResponse = httpclient.execute(new HttpGet(LEGACY_INIT_SESSION_URL));
      Header initSessionLocation = initSessionResponse.getFirstHeader("location");
      for(int i=0; i<5; i++){
        get = createHttpGetFromLocationHeader(initSessionLocation);
        get.setParams(params);
        initSessionLocation = httpclient.execute(get).getFirstHeader("location");
      }
      httpclient.setRedirectStrategy(oldRedirectStrategy);
      */

      return isSignedIn(username);
    } catch (Exception e) {
      httpclient.getConnectionManager().shutdown();
      return false;
    }
  }

  @NonNull
  private HttpGet createHttpGetFromLocationHeader(Header h1) {
    Matcher matcher = LOCATION_PATTERN.matcher(h1.toString());
    matcher.find();
    String redirect = matcher.group(1);

    return new HttpGet(redirect);
  }

  private String getTicketIdFromResponse(String responseAsString) {
    return getFirstMatch(TICKET_FINDER_PATTERN, responseAsString);
  }

  private String getCSRFToken(String responseAsString) {
    return getFirstMatch(CSRF_TOKEN_PATTERN, responseAsString);
  }

  private String getFirstMatch(String regex, String within) {
    Pattern pattern = Pattern.compile(regex);
    Matcher matcher = pattern.matcher(within);
    matcher.find();
    return matcher.group(1);
  }

  public boolean isSignedIn(String username) {
    if (httpclient == null) return false;
    try {
      CloseableHttpResponse execute = httpclient.execute(new HttpGet("https://connect.garmin.com/modern/proxy/userprofile-service/socialProfile"));
      HttpEntity entity = execute.getEntity();
      String json = EntityUtils.toString(entity);
      JSONObject js_user = new JSONObject(json);
      entity.consumeContent();
      return js_user.getString("userName") != null && !js_user.getString("userName").isEmpty();
    } catch (Exception e) {
      return false;
    }
  }

  public boolean uploadFitFile(File fitFile) {
    if (httpclient == null) return false;
    try {
      HttpPost post = new HttpPost(FIT_FILE_UPLOAD_URL);

      post.setHeader("origin", "https://connect.garmin.com");
      post.setHeader("nk", "NT");
      post.setHeader("accept", "*/*");
      post.setHeader("referer", "https://connect.garmin.com/modern/import-data");
      post.setHeader("authority", "connect.garmin.com");
      post.setHeader("language", "EN");

      MultipartEntityBuilder multipartEntity = MultipartEntityBuilder.create();
      multipartEntity.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
      multipartEntity.addBinaryBody("file", fitFile);
      post.setEntity(multipartEntity.build());

      CloseableHttpResponse httpResponse = httpclient.execute(post);
      if(httpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_ACCEPTED){
        Header locationHeader = httpResponse.getFirstHeader("Location");
        String uploadStatusUrl = locationHeader.getValue();
        CloseableHttpResponse getStatusResponse = httpclient.execute(new HttpGet(uploadStatusUrl));
        String responseString = EntityUtils.toString(getStatusResponse.getEntity());
        JSONObject js_upload = new JSONObject(responseString);
      }

      HttpEntity entity = httpResponse.getEntity();
      String responseString = EntityUtils.toString(entity);
      JSONObject js_upload = new JSONObject(responseString);
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

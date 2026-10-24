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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.util.Base64;
import android.util.Log;
import android.widget.EditText;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cz.msebera.android.httpclient.HttpHeaders;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.HttpStatus;
import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.client.entity.UrlEncodedFormEntity;
import cz.msebera.android.httpclient.client.methods.HttpGet;
import cz.msebera.android.httpclient.client.methods.HttpPost;
import cz.msebera.android.httpclient.client.methods.HttpRequestBase;
import cz.msebera.android.httpclient.client.protocol.HttpClientContext;
import cz.msebera.android.httpclient.client.utils.URIBuilder;
import cz.msebera.android.httpclient.entity.ContentType;
import cz.msebera.android.httpclient.entity.StringEntity;
import cz.msebera.android.httpclient.entity.mime.HttpMultipartMode;
import cz.msebera.android.httpclient.entity.mime.MultipartEntityBuilder;
import cz.msebera.android.httpclient.impl.client.CloseableHttpClient;
import cz.msebera.android.httpclient.impl.client.HttpClientBuilder;
import cz.msebera.android.httpclient.impl.client.LaxRedirectStrategy;
import cz.msebera.android.httpclient.message.BasicNameValuePair;
import cz.msebera.android.httpclient.util.EntityUtils;

/*
  Garmin Connect authentication, based on the "widget" flow of python-garminconnect
  (https://github.com/cyberjunky/python-garminconnect):
  1. Sign in through the SSO embed widget (HTML form), handling MFA if required
  2. Exchange the SSO service ticket for a DI OAuth2 token on diauth.garmin.com
  3. Upload with the DI token, refreshed with its refresh token when expired
*/
public class GarminConnect {

  private static final String TAG = "GarminConnect";

  private static final String SSO_URL = "https://sso.garmin.com/sso";
  private static final String SSO_EMBED_URL = SSO_URL + "/embed";
  private static final String SSO_SIGNIN_URL = SSO_URL + "/signin";
  private static final String SSO_MFA_CODE_URL = SSO_URL + "/verifyMFA/mfaCode";
  private static final String SSO_MFA_VERIFY_URL = SSO_URL + "/verifyMFA/loginEnterMfaCode";
  private static final String DI_TOKEN_URL = "https://diauth.garmin.com/di-oauth2-service/oauth/token";
  private static final String DI_GRANT_TYPE = "https://connectapi.garmin.com/di-oauth2-service/oauth/grant/service_ticket";
  private static final String FIT_FILE_UPLOAD_URL = "https://connectapi.garmin.com/upload-service/upload";

  private static final List<String> DI_CLIENT_IDS = Arrays.asList(
          "GARMIN_CONNECT_MOBILE_ANDROID_DI_2025Q2",
          "GARMIN_CONNECT_MOBILE_ANDROID_DI_2024Q4",
          "GARMIN_CONNECT_MOBILE_ANDROID_DI",
          "GARMIN_CONNECT_MOBILE_IOS_DI"
  );

  // The SSO pages are meant to be displayed in a WebView: look like a browser to avoid Cloudflare challenges
  private static final String SSO_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36";
  private static final String NATIVE_USER_AGENT = "GCM-Android-5.23";
  private static final String NATIVE_X_GARMIN_USER_AGENT = "com.garmin.android.apps.connectmobile/5.23; ; Google/sdk_gphone64_arm64/google; Android/33; Dalvik/2.1.0";

  // Cloudflare flags immediate GET -> POST sequences as bot-like
  private static final int SIGNIN_DELAY_MIN_MS = 3000;
  private static final int SIGNIN_DELAY_MAX_MS = 8000;

  private static final Pattern CSRF_PATTERN = Pattern.compile("name=\"_csrf\"\\s+value=\"(.+?)\"");
  private static final Pattern TITLE_PATTERN = Pattern.compile("<title>(.+?)</title>");
  private static final Pattern TICKET_PATTERN = Pattern.compile("\\?ticket=(ST-[^\"&\\s]+)");
  private static final Pattern MFA_VARS_PATTERN = Pattern.compile("var\\s+(customerGuid|mfaMethod|locale|clientId|codeSentTo)\\s*=\\s*\"([^\"]*)\"\\s*;");

  private static final List<NameValuePair> EMBED_PARAMS = Arrays.asList(
          new BasicNameValuePair("id", "gauth-widget"),
          new BasicNameValuePair("embedWidget", "true"),
          new BasicNameValuePair("gauthHost", SSO_URL)
  );

  private static final List<NameValuePair> SIGNIN_PARAMS = Arrays.asList(
          new BasicNameValuePair("id", "gauth-widget"),
          new BasicNameValuePair("embedWidget", "true"),
          new BasicNameValuePair("gauthHost", SSO_EMBED_URL),
          new BasicNameValuePair("service", SSO_EMBED_URL),
          new BasicNameValuePair("source", SSO_EMBED_URL),
          new BasicNameValuePair("redirectAfterAccountLoginUrl", SSO_EMBED_URL),
          new BasicNameValuePair("redirectAfterAccountCreationUrl", SSO_EMBED_URL)
  );

  private static final String PREF_TOKEN = "garminDiToken";
  private static final String PREF_REFRESH_TOKEN = "garminDiRefreshToken";
  private static final String PREF_CLIENT_ID = "garminDiClientId";
  private static final String PREF_EXPIRY = "garminDiExpiryTimestamp";

  private CloseableHttpClient httpclient;
  private HttpClientContext httpContext;
  private SharedPreferences authPreferences;

  private String token;
  private String refreshToken;
  private String clientId;
  private long expiryTimestamp;

  private static SharedPreferences getAuthPreferences(Context context) {
    return context.getSharedPreferences(context.getApplicationContext().getPackageName() + ".garmintokens", Context.MODE_PRIVATE);
  }

  /* Remove stored tokens, including the OAuth1/OAuth2 ones of the previous authentication flow */
  public static boolean clearTokens(Context context) {
    return getAuthPreferences(context).edit().clear().commit();
  }

  public boolean signin(final String username, final String password, Activity currentActivity) {
    httpclient = HttpClientBuilder.create()
            .useSystemProperties()
            // Garmin redirects POSTs
            .setRedirectStrategy(new LaxRedirectStrategy())
            .build();
    httpContext = new HttpClientContext();
    authPreferences = getAuthPreferences(currentActivity);

    try {
      loadTokens();
      if (token != null && expiryTimestamp > now() + 60)
        return true;

      if (refreshToken != null && clientId != null) {
        if (refreshDiToken()) {
          saveTokens();
          return true;
        }
        clearTokens(currentActivity);
      }

      String ticket = ssoSignin(username, password, currentActivity);
      if (ticket == null || !exchangeServiceTicket(ticket))
        return false;
      saveTokens();
      return true;
    } catch (Exception e) {
      Log.w(TAG, "Sign in failed", e);
      close();
      return false;
    }
  }

  /* Sign in through the SSO embed widget, returns the service ticket */
  private String ssoSignin(String username, String password, Activity currentActivity) throws Exception {
    // Get session cookies
    executeSso(new HttpGet(buildURI(SSO_EMBED_URL, EMBED_PARAMS)));

    // Get the CSRF token
    HttpGet signinGet = new HttpGet(buildURI(SSO_SIGNIN_URL, SIGNIN_PARAMS));
    signinGet.setHeader(HttpHeaders.REFERER, SSO_EMBED_URL);
    String signinPage = executeSso(signinGet);
    String csrf = getFirstMatch(CSRF_PATTERN, signinPage);
    if (csrf == null)
      throw new Exception("Missing CSRF token");

    Thread.sleep(SIGNIN_DELAY_MIN_MS + (long) (Math.random() * (SIGNIN_DELAY_MAX_MS - SIGNIN_DELAY_MIN_MS)));

    // Submit credentials
    HttpPost signinPost = new HttpPost(buildURI(SSO_SIGNIN_URL, SIGNIN_PARAMS));
    signinPost.setHeader(HttpHeaders.REFERER, signinGet.getURI().toString());
    signinPost.setEntity(new UrlEncodedFormEntity(Arrays.asList(
            new BasicNameValuePair("username", username),
            new BasicNameValuePair("password", password),
            new BasicNameValuePair("embed", "true"),
            new BasicNameValuePair("_csrf", csrf)
    ), "UTF-8"));
    String response = executeSso(signinPost);
    String title = getTitle(response);

    if (isMfaPage(response, title)) {
      String mfaCsrf = getFirstMatch(CSRF_PATTERN, response);
      requestMfaCode(response, signinPost.getURI().toString());
      String mfaCode = promptMFAModalDialog(currentActivity);
      if (mfaCode.isEmpty() || mfaCsrf == null)
        return null;
      response = submitMfaCode(mfaCode, mfaCsrf, signinPost.getURI().toString());
      title = getTitle(response);
    }

    if (!title.equals("Success")) {
      Log.w(TAG, "Sign in failed, page title: " + title);
      return null;
    }
    return getFirstMatch(TICKET_PATTERN, response);
  }

  /* The MFA page has the same title as the sign in page, but exposes MFA variables */
  private boolean isMfaPage(String page, String title) {
    String lowerTitle = title.toLowerCase();
    return lowerTitle.contains("mfa") || (lowerTitle.contains("authentication application") && getMfaVars(page).containsKey("mfaMethod"));
  }

  private Map<String, String> getMfaVars(String page) {
    Map<String, String> vars = new HashMap<>();
    Matcher matcher = MFA_VARS_PATTERN.matcher(page);
    while (matcher.find())
      vars.put(matcher.group(1), matcher.group(2));
    return vars;
  }

  private String getVar(Map<String, String> vars, String name) {
    String value = vars.get(name);
    return value == null ? "" : value;
  }

  /* Email/SMS codes are not always sent on sign in: ask for one like the widget's "Request a new code" link */
  private void requestMfaCode(String mfaPage, String referer) throws Exception {
    Map<String, String> vars = getMfaVars(mfaPage);
    String method = getVar(vars, "mfaMethod");
    if (!(method.equalsIgnoreCase("email") || method.equalsIgnoreCase("sms")) || !getVar(vars, "codeSentTo").isEmpty())
      return;

    JSONObject payload = new JSONObject();
    payload.put("customerGuid", getVar(vars, "customerGuid"));
    payload.put("mfaMethod", method);
    payload.put("locale", getVar(vars, "locale"));

    HttpPost post = new HttpPost(buildURI(SSO_MFA_CODE_URL, Arrays.asList(new BasicNameValuePair("clientId", getVar(vars, "clientId")))));
    post.setHeader(HttpHeaders.REFERER, referer);
    post.setHeader(HttpHeaders.ACCEPT, "application/json, text/plain, */*");
    post.setEntity(new StringEntity(payload.toString(), ContentType.APPLICATION_JSON));
    executeSso(post);
  }

  private String submitMfaCode(String mfaCode, String csrf, String referer) throws Exception {
    HttpPost post = new HttpPost(buildURI(SSO_MFA_VERIFY_URL, SIGNIN_PARAMS));
    post.setHeader(HttpHeaders.REFERER, referer);
    post.setEntity(new UrlEncodedFormEntity(Arrays.asList(
            new BasicNameValuePair("mfa-code", mfaCode),
            new BasicNameValuePair("embed", "true"),
            new BasicNameValuePair("_csrf", csrf),
            new BasicNameValuePair("fromPage", "setupEnterMfaCode")
    ), "UTF-8"));
    return executeSso(post);
  }

  private String executeSso(HttpRequestBase request) throws Exception {
    request.setHeader(HttpHeaders.USER_AGENT, SSO_USER_AGENT);
    request.setHeader(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9");
    if (!request.containsHeader(HttpHeaders.ACCEPT))
      request.setHeader(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
    HttpResponse response = httpclient.execute(request, httpContext);
    String content = EntityUtils.toString(response.getEntity());
    int status = response.getStatusLine().getStatusCode();
    // 427 has been seen along 429 when Garmin blocks login attempts
    if (status == 429 || status == 427)
      throw new Exception("Rate limited by Garmin SSO (HTTP " + status + ")");
    return content;
  }

  /* Exchange the SSO service ticket for a DI OAuth2 token */
  private boolean exchangeServiceTicket(String ticket) throws Exception {
    for (String diClientId : DI_CLIENT_IDS) {
      JSONObject response = postDiToken(diClientId, Arrays.asList(
              new BasicNameValuePair("client_id", diClientId),
              new BasicNameValuePair("service_ticket", ticket),
              new BasicNameValuePair("grant_type", DI_GRANT_TYPE),
              new BasicNameValuePair("service_url", SSO_EMBED_URL)
      ));
      if (response != null) {
        clientId = diClientId;
        setTokens(response);
        return true;
      }
    }
    return false;
  }

  private boolean refreshDiToken() throws Exception {
    JSONObject response = postDiToken(clientId, Arrays.asList(
            new BasicNameValuePair("grant_type", "refresh_token"),
            new BasicNameValuePair("client_id", clientId),
            new BasicNameValuePair("refresh_token", refreshToken)
    ));
    if (response == null)
      return false;
    setTokens(response);
    return true;
  }

  private JSONObject postDiToken(String diClientId, List<NameValuePair> params) throws Exception {
    HttpPost post = new HttpPost(DI_TOKEN_URL);
    setNativeHeaders(post);
    post.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + Base64.encodeToString((diClientId + ":").getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
    post.setHeader(HttpHeaders.ACCEPT, "application/json");
    post.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
    HttpResponse response = httpclient.execute(post, httpContext);
    String content = EntityUtils.toString(response.getEntity());
    int status = response.getStatusLine().getStatusCode();
    if (status == 429)
      throw new Exception("Rate limited by Garmin DI token endpoint");
    if (status != HttpStatus.SC_OK) {
      Log.w(TAG, "DI token request failed for " + diClientId + ": HTTP " + status);
      return null;
    }
    return new JSONObject(content);
  }

  private void setTokens(JSONObject response) throws Exception {
    token = response.getString("access_token");
    refreshToken = response.optString("refresh_token", refreshToken);
    expiryTimestamp = getJwtExpiry(token);
    if (expiryTimestamp == 0)
      expiryTimestamp = now() + response.optLong("expires_in", 3600);
  }

  private long getJwtExpiry(String jwt) {
    try {
      String payload = new String(Base64.decode(jwt.split("\\.")[1], Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP), StandardCharsets.UTF_8);
      return new JSONObject(payload).optLong("exp", 0);
    } catch (Exception e) {
      return 0;
    }
  }

  private void loadTokens() {
    token = authPreferences.getString(PREF_TOKEN, null);
    refreshToken = authPreferences.getString(PREF_REFRESH_TOKEN, null);
    clientId = authPreferences.getString(PREF_CLIENT_ID, null);
    expiryTimestamp = authPreferences.getLong(PREF_EXPIRY, 0);
  }

  private void saveTokens() {
    authPreferences.edit()
            .putString(PREF_TOKEN, token)
            .putString(PREF_REFRESH_TOKEN, refreshToken)
            .putString(PREF_CLIENT_ID, clientId)
            .putLong(PREF_EXPIRY, expiryTimestamp)
            .commit();
  }

  private void setNativeHeaders(HttpRequestBase request) {
    request.setHeader(HttpHeaders.USER_AGENT, NATIVE_USER_AGENT);
    request.setHeader("X-Garmin-User-Agent", NATIVE_X_GARMIN_USER_AGENT);
    request.setHeader("X-Garmin-Paired-App-Version", "10861");
    request.setHeader("X-Garmin-Client-Platform", "Android");
    request.setHeader("X-App-Ver", "10861");
    request.setHeader("X-Lang", "en");
    request.setHeader("X-GCExperience", "GC5");
    request.setHeader(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9");
  }

  public boolean uploadFitFile(File fitFile) {
    if (httpclient == null || token == null) return false;
    try {
      HttpPost post = new HttpPost(FIT_FILE_UPLOAD_URL);
      setNativeHeaders(post);
      post.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
      post.setHeader(HttpHeaders.ACCEPT, "application/json");

      MultipartEntityBuilder multipartEntity = MultipartEntityBuilder.create();
      multipartEntity.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
      multipartEntity.addBinaryBody("file", fitFile);
      post.setEntity(multipartEntity.build());

      HttpResponse response = httpclient.execute(post, httpContext);
      String content = EntityUtils.toString(response.getEntity());
      int status = response.getStatusLine().getStatusCode();
      if (status == HttpStatus.SC_UNAUTHORIZED) {
        // Token refused: drop it so that the next export signs in again
        authPreferences.edit().remove(PREF_TOKEN).remove(PREF_EXPIRY).commit();
        return false;
      }
      if (status != HttpStatus.SC_OK && status != HttpStatus.SC_CREATED && status != HttpStatus.SC_ACCEPTED) {
        Log.w(TAG, "Upload failed: HTTP " + status);
        return false;
      }

      JSONObject result = new JSONObject(content).getJSONObject("detailedImportResult");
      return result.getJSONArray("failures").length() == 0;
    } catch (Exception e) {
      Log.w(TAG, "Upload failed", e);
      return false;
    }
  }

  public boolean uploadFitFile(String fitFilePath) {
    return uploadFitFile(new File(fitFilePath));
  }

  private String promptMFAModalDialog(Activity currentActivity) throws InterruptedException {
    BlockingQueue<String> inputQueue = new LinkedBlockingQueue<>();

    currentActivity.runOnUiThread(() -> {
      final EditText mfaInput = new EditText(currentActivity);
      mfaInput.setInputType(InputType.TYPE_CLASS_NUMBER);
      new AlertDialog.Builder(currentActivity)
              .setTitle("MFA")
              .setView(mfaInput)
              .setMessage("Enter the 6 digit MFA code you received by SMS or email:")
              .setPositiveButton("Submit", (dialog, id) -> inputQueue.add(mfaInput.getText().toString().trim()))
              .setNegativeButton("Cancel", (dialog, id) -> inputQueue.add("")) // Unblock the queue
              .setCancelable(false)
              .show();
    });
    return inputQueue.take();
  }

  private String buildURI(String root, List<NameValuePair> params) throws URISyntaxException {
    return new URIBuilder(root).addParameters(new ArrayList<>(params)).build().toString();
  }

  private String getTitle(String page) {
    String title = getFirstMatch(TITLE_PATTERN, page);
    return title == null ? "" : title;
  }

  private String getFirstMatch(Pattern pattern, String within) {
    Matcher matcher = pattern.matcher(within);
    return matcher.find() ? matcher.group(1) : null;
  }

  private static long now() {
    return System.currentTimeMillis() / 1000;
  }

  public void close() {
    if (httpclient != null) {
      try {
        httpclient.close();
      } catch (IOException ignored) {
      }
      httpclient = null;
    }
  }

}

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

import androidx.annotation.NonNull;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.InputType;
import android.util.Pair;
import android.widget.EditText;

import com.garmin.fit.Bool;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.HttpHeaders;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.HttpStatus;
import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.client.entity.UrlEncodedFormEntity;
import cz.msebera.android.httpclient.client.methods.HttpGet;
import cz.msebera.android.httpclient.client.methods.HttpPost;
import cz.msebera.android.httpclient.client.protocol.HttpClientContext;
import cz.msebera.android.httpclient.client.utils.URIBuilder;
import cz.msebera.android.httpclient.entity.mime.HttpMultipartMode;
import cz.msebera.android.httpclient.entity.mime.MultipartEntityBuilder;
import cz.msebera.android.httpclient.impl.client.CloseableHttpClient;
import cz.msebera.android.httpclient.impl.client.HttpClientBuilder;
import cz.msebera.android.httpclient.impl.client.LaxRedirectStrategy;
import cz.msebera.android.httpclient.impl.conn.PoolingHttpClientConnectionManager;
import cz.msebera.android.httpclient.message.BasicNameValuePair;
import cz.msebera.android.httpclient.util.EntityUtils;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import oauth.signpost.http.HttpRequest;
import oauth.signpost.signature.HmacSha1MessageSigner;
// Disable custom entity, need to find a fix to avoid heavy external Apache libs
// import org.kochka.android.weightlogger.tools.SimpleMultipartEntity;


public class GarminConnect {

  private class OAuth1Token {
    private String oauth1Token;
    private String oauth1TokenSecret;

    // These member are currently unused. Maybe they can be used to issue a new OAuth1 token without
    // requiring the user to re-enter an MFA code.
    private String mfaToken;
    private String mfaExpirationTimestamp;

    public OAuth1Token(String oauth1Token, String oauth1TokenSecret, String mfaToken, String mfaExpirationTimestamp) {
      this.oauth1Token = oauth1Token;
      this.oauth1TokenSecret = oauth1TokenSecret;
      this.mfaToken = mfaToken;
      this.mfaExpirationTimestamp = mfaExpirationTimestamp;
    }

    public String getOauth1Token() {
      return oauth1Token;
    }

    public String getOauth1TokenSecret() {
      return oauth1TokenSecret;
    }

    public String getMfaToken() {
      return mfaToken;
    }

    public String getMfaTokenExpirationTimestamp() {
      return mfaExpirationTimestamp;
    }

    public Boolean saveToSharedPreferences(SharedPreferences.Editor sharedPreferenceEditor) {
      // Todo: support EncryptedSharedPreferences.
      sharedPreferenceEditor.putString("garminOauth1Token", this.oauth1Token);
      sharedPreferenceEditor.putString("garminOauth1TokenSecret", this.oauth1TokenSecret);
      sharedPreferenceEditor.putString("garminOauth1MfaToken", this.mfaToken);
      // Todo: should the timestamp be stored as a numeric type?
      sharedPreferenceEditor.putString("garminOauth1MfaExpirationTimestamp", this.mfaExpirationTimestamp);
      return sharedPreferenceEditor.commit();
    }
  }

  // In outer scope as static methods in nested classes aren't supported in this version of the JDK.
  private Pair<Boolean,OAuth1Token> loadOauth1FromSharedPreferences(SharedPreferences sharedPreferences) {
    String token = sharedPreferences.getString("garminOauth1Token","");
    String tokenSecret = sharedPreferences.getString("garminOauth1TokenSecret","");
    String mfaToken = sharedPreferences.getString("garminOauth1MfaToken","");
    String mfaExpirationTimestamp = sharedPreferences.getString("garminOauth1MfaExpirationTimestamp","");

    OAuth1Token oAuth1Token = new OAuth1Token(token,tokenSecret,mfaToken,mfaExpirationTimestamp);
    // TODO: Add failure case if the token or secret are invalid.
    return new Pair<>(true, oAuth1Token);
  }

  public class Oauth2Token {
    private String oauth2Token;
    private String oauth2RefreshToken;
    private long timeOfExpiry;
    private long timeOfRefreshExpiry;


    public Oauth2Token(String oauth2Token, String oauth2RefreshToken, long timeOfExpiry, long timeOfRefreshExpiry) {
      this.oauth2Token = oauth2Token;
      this.oauth2RefreshToken = oauth2RefreshToken;
      this.timeOfExpiry = timeOfExpiry;
      this.timeOfRefreshExpiry = timeOfRefreshExpiry;
    }

    public String getOauth2Token() {
      return oauth2Token;
    }

    public Boolean saveToSharedPreferences(SharedPreferences.Editor sharedPreferenceEditor) {
      // Todo: support EncryptedSharedPreferences.
      sharedPreferenceEditor.putString("garminOauth2Token", this.oauth2Token);
      sharedPreferenceEditor.putString("garminOauth2RefreshToken", this.oauth2RefreshToken);
      sharedPreferenceEditor.putLong("garminOauth2ExpiryTimestamp", this.timeOfExpiry);
      sharedPreferenceEditor.putLong("garminOauth2RefreshExpiryTimestamp", this.timeOfRefreshExpiry);
      return sharedPreferenceEditor.commit();
    }
  }

  private Boolean loadOauth2FromSharedPreferences(SharedPreferences sharedPreferences) {
    long currentTime = System.currentTimeMillis() / 1000;
    String oauth2Token = sharedPreferences.getString("garminOauth2Token", "");
    String oauth2RefreshToken = sharedPreferences.getString("garminOauth2RefreshToken", "");
    long timeOfExpiry = sharedPreferences.getLong("garminOauth2ExpiryTimestamp",-1);
    long timeOfRefreshExpiry = sharedPreferences.getLong("garminOauth2RefreshExpiryTimestamp",-1);;

    if (oauth2Token == "" || timeOfExpiry < currentTime) {
      // Get a new oauth2 token using the saved oauth1 token.
      // According to https://github.com/matin/garth/blob/316787d1e3ff69c09725b2eb8ded748a4422abb3/garth/http.py#L167
      // Garmin Connect also just uses the OAuth1 token to get a new OAuth2 token.
      return  false;
    }

    this.oauth2Token = new Oauth2Token(oauth2Token,oauth2RefreshToken,timeOfExpiry, timeOfRefreshExpiry);
    return true;
  }

  private static final String GET_TICKET_URL = "https://connect.garmin.com/modern/?ticket=";

  // TODO Fetch oauth consumer_secret from here - is this viable from an Android app (extra perms etc)?.
  // TODO Will store in code for now as URL is public
  // TODO Secrets provided from @matin's https://thegarth.s3.amazonaws.com/oauth_consumer.json
  // TODO How to keep secrets secure in Android https://guides.codepath.com/android/storing-secret-keys-in-android

  private static final String OAUTH_CONSUMER_URL = "https://thegarth.s3.amazonaws.com/oauth_consumer.json";
  private static final String OAUTH1_CONSUMER_KEY = "fc3e99d2-118c-44b8-8ae3-03370dde24c0";
  private static final String OAUTH1_CONSUMER_SECRET = "E08WAR897WEy2knn7aFBrvegVAf0AFdWBBF";
  private static final String GET_OAUTH1_URL = "https://connectapi.garmin.com/oauth-service/oauth/preauthorized?";
  private static final String GET_OAUTH2_URL = "https://connectapi.garmin.com/oauth-service/oauth/exchange/user/2.0";
  private static final String FIT_FILE_UPLOAD_URL = "https://connectapi.garmin.com/upload-service/upload";
  private static final String SSO_URL = "https://sso.garmin.com/sso";
  private static final String SSO_EMBED_URL = SSO_URL + "/embed";
  private static final String SSO_SIGNIN_URL = SSO_URL + "/signin";
  private static final String SSO_MFA_URL = SSO_URL + "/verifyMFA/loginEnterMfaCode";
  private static final Pattern LOCATION_PATTERN = Pattern.compile("location: (.*)");
  private static final String CSRF_TOKEN_PATTERN = "name=\"_csrf\" *value=\"([A-Z0-9]+)\"";
  private static final String TICKET_FINDER_PATTERN = "ticket=([^']+?)\";";

  private static final String USER_AGENT = "com.garmin.android.apps.connectmobile";

  private final List<NameValuePair> EMBED_PARAMS = Arrays.asList(
          new BasicNameValuePair("id", "gauth-widget"),
          new BasicNameValuePair("embedWidget", "true"),
          new BasicNameValuePair("gauthHost", SSO_URL)
  );

  private final List<NameValuePair> SIGNIN_PARAMS = Arrays.asList(
          new BasicNameValuePair("id", "gauth-widget"),
          new BasicNameValuePair("embedWidget", "true"),
          new BasicNameValuePair("gauthHost", SSO_EMBED_URL),
          new BasicNameValuePair("service", SSO_EMBED_URL),
          new BasicNameValuePair("source", SSO_EMBED_URL),
          new BasicNameValuePair("redirectAfterAccountLoginUrl", SSO_EMBED_URL),
          new BasicNameValuePair("redirectAfterAccountCreationUrl", SSO_EMBED_URL)
  );

  // TODO: manage the HTTP client and context in a wrapper class
  private CloseableHttpClient httpclient;
  private HttpClientContext httpContext;

  private Oauth2Token oauth2Token;

  public boolean signin(final String username, final String password, Activity currentActivity) {
    PoolingHttpClientConnectionManager conman =  new PoolingHttpClientConnectionManager();
    conman.setMaxTotal(20);
    conman.setDefaultMaxPerRoute(20);

    HttpClientBuilder clientBuilder = HttpClientBuilder.create();
    clientBuilder.useSystemProperties();
    clientBuilder.setUserAgent(USER_AGENT);

    httpContext = new HttpClientContext();

    // We need a lax redirect strategy as Garmin will redirect POSTs.
    clientBuilder.setRedirectStrategy(new LaxRedirectStrategy());
    httpclient = clientBuilder.build();

    try {
      // Get the sharedPreferences that (may) contain our auth tokens.
      // TODO: make this an encrypted shared preferences object.
      SharedPreferences authPreferences = currentActivity.getSharedPreferences(currentActivity.getApplicationContext().getPackageName() + ".garmintokens", Context.MODE_PRIVATE);
      if (loadOauth2FromSharedPreferences(authPreferences)) {
        return true;
      }

      // Get cookies
      // TODO: are cookies actually passed between calls by the http client/context?
      HttpGet cookieGet = new HttpGet(buildURI(SSO_EMBED_URL,EMBED_PARAMS));
      httpclient.execute(cookieGet,httpContext);

      // Create a session.
      HttpGet sessionGetRequest = new HttpGet(buildURI(SSO_SIGNIN_URL, EMBED_PARAMS));
      sessionGetRequest.setHeader(HttpHeaders.REFERER, getLastUri());
      HttpResponse sessionResponse = httpclient.execute(sessionGetRequest, httpContext);
      HttpEntity sessionEntity = sessionResponse.getEntity();
      String sessionContent = EntityUtils.toString(sessionEntity);
      String csrf = getCSRFToken(sessionContent);

      // Sign in
      HttpPost loginPostRequest = new HttpPost(buildURI(SSO_SIGNIN_URL, SIGNIN_PARAMS));
      loginPostRequest.setHeader(HttpHeaders.REFERER, getLastUri());
      List<NameValuePair> loginPostEntity = Arrays.asList(
              new BasicNameValuePair("username", username),
              new BasicNameValuePair("password", password),
              new BasicNameValuePair("embed", "true"),
              new BasicNameValuePair("_csrf", csrf)
      );
      loginPostRequest.setEntity(new UrlEncodedFormEntity(loginPostEntity, "UTF-8"));
      HttpResponse loginResponse = httpclient.execute(loginPostRequest, httpContext);
      HttpEntity loginResponseEntity = loginResponse.getEntity();
      String loginContent = EntityUtils.toString(loginResponseEntity);

      String ticket = "";
      if (loginRequiresMFA(loginContent)) {
        csrf = getCSRFToken(loginContent);
        String mfaResponse = handleMfa(csrf, currentActivity);
        ticket = getTicketIdFromResponse(mfaResponse);
      } else {
        ticket = getTicketIdFromResponse(loginContent);
      }

      if (!isSignedIn(username)) {
        return  false;
      }

      // https://github.com/mttkay/signpost/blob/master/docs/GettingStarted.md
      // Using signpost's CommonsHttpOAuth instead of DefaultOAuth as per https://github.com/mttkay/signpost
      OAuthConsumer consumer = new CommonsHttpOAuthConsumer(OAUTH1_CONSUMER_KEY, OAUTH1_CONSUMER_SECRET);
      consumer.setMessageSigner(new HmacSha1MessageSigner());

      boolean success = getOAuth1Token(ticket, consumer);
      if (!success) {
        return false;
      }

      success = performOauth2exchange(consumer);
      if (!success) {
        return false;
      }

      return oauth2Token.saveToSharedPreferences(authPreferences.edit());

    } catch (Exception e) {
      httpclient.getConnectionManager().shutdown();
      return false;
    }
  }

  private String buildURI(String root, List<NameValuePair> params) throws URISyntaxException {
    URIBuilder uriBuilder = new URIBuilder(root);
    uriBuilder.addParameters(params);
    return uriBuilder.build().toString();
  }

  private boolean getOAuth1Token(String ticket, OAuthConsumer consumer) throws OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException, IOException, URISyntaxException {
    List<NameValuePair> oauth1TokenParams = Arrays.asList(
            new BasicNameValuePair("ticket", ticket),
            new BasicNameValuePair("login-url", "https://sso.garmin.com/sso/embed"),
            new BasicNameValuePair("accepts-mfa-tokens", "true")
    );
    String oauth1RequestURI = buildURI(GET_OAUTH1_URL,oauth1TokenParams);
    String signedOauth1RequestURI = consumer.sign(oauth1RequestURI);
    HttpGet getOauth1 = new HttpGet(signedOauth1RequestURI);
    getOauth1.setHeader(HttpHeaders.REFERER, getLastUri());
    HttpResponse response = httpclient.execute(getOauth1,httpContext);
    String oauth1ResponseAsString = EntityUtils.toString(response.getEntity());
    OAuth1Token oauth1Token = getOauth1FromResponse(oauth1ResponseAsString);
    consumer.setTokenWithSecret(oauth1Token.getOauth1Token(), oauth1Token.getOauth1TokenSecret());
    return true;
  }

  private boolean performOauth2exchange(OAuthConsumer consumer) throws OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException, IOException {
    // Exchange for oauth v2 token
    // We have to manually create a request object here because sign(String url) only signs GET
    // requests.
    org.apache.http.client.methods.HttpPost exchangeRequest = new org.apache.http.client.methods.HttpPost(GET_OAUTH2_URL);
    HttpRequest signedExchangeRequest = consumer.sign(exchangeRequest);

    HttpPost postOauth2 = new HttpPost(GET_OAUTH2_URL);
    postOauth2.setHeader(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded");
    postOauth2.setHeader(HttpHeaders.REFERER, getLastUri());
    postOauth2.setHeader(HttpHeaders.AUTHORIZATION, signedExchangeRequest.getHeader("Authorization"));
    HttpEntity oauth2Entity = httpclient.execute(postOauth2,httpContext).getEntity();
    String oauth2ResponseAsString = EntityUtils.toString(oauth2Entity);
    try {
      this.oauth2Token = getOauth2FromResponse(oauth2ResponseAsString);
    }
    catch (JSONException e) {
      return  false;
    }

    return true;
  }

  @NonNull
  private HttpGet createHttpGetFromLocationHeader(Header h1) {
    Matcher matcher = LOCATION_PATTERN.matcher(h1.toString());
    matcher.find();
    String redirect = matcher.group(1);

    return new HttpGet(redirect);
  }

  private OAuth1Token getOauth1FromResponse(String responseAsString) {
    // Garmin returns a bare query string. Turn it into a dummy URI for parsing.
    Uri uri = Uri.parse("http://invalid?"+responseAsString);
    String oauth1Token = uri.getQueryParameter("oauth_token");
    String oauth1TokenSecret = uri.getQueryParameter("oauth_token_secret");

    // The following args aren't always present but getQueryParameter will return just null if they
    // aren't.
    String mfaToken = uri.getQueryParameter("mfa_token");
    String mfaExpirationTimestamp = uri.getQueryParameter("mfa_expiration_timestamp");
    return new OAuth1Token(oauth1Token,oauth1TokenSecret, mfaToken, mfaExpirationTimestamp);
  }

  private Oauth2Token getOauth2FromResponse(String responseAsString) throws JSONException {
    long currentTime = System.currentTimeMillis() / 1000;

    // This time they return JSON.
    JSONObject response = new JSONObject(responseAsString);
    return new Oauth2Token(response.getString("access_token"),
            response.getString("refresh_token"),
            Integer.parseInt(response.getString("expires_in"))+currentTime,
            Integer.parseInt(response.getString("refresh_token_expires_in"))+currentTime);
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
      HttpResponse execute = httpclient.execute(new HttpGet("https://connect.garmin.com/modern/currentuser-service/user/info"),httpContext);
      HttpEntity entity = execute.getEntity();
      String json = EntityUtils.toString(entity);
      JSONObject js_user = new JSONObject(json);
      entity.consumeContent();
      return js_user.getString("username") != null && !js_user.getString("username").isEmpty();
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
      post.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + this.oauth2Token.getOauth2Token());

      MultipartEntityBuilder multipartEntity = MultipartEntityBuilder.create();
      multipartEntity.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
      multipartEntity.addBinaryBody("file", fitFile);
      post.setEntity(multipartEntity.build());

      HttpResponse httpResponse = httpclient.execute(post, httpContext);
      if(httpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_ACCEPTED){
        Header locationHeader = httpResponse.getFirstHeader("Location");
        String uploadStatusUrl = locationHeader.getValue();
        HttpResponse getStatusResponse = httpclient.execute(new HttpGet(uploadStatusUrl),httpContext);
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


  private String promptMFAModalDialog(Activity currentActivity) throws InterruptedException {

    BlockingQueue<String> inputQueue = new LinkedBlockingQueue<>();

    currentActivity.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        AlertDialog.Builder mfaModalBuilder = new AlertDialog.Builder(currentActivity);
        mfaModalBuilder.setTitle("MFA");
        final EditText mfaInput = new EditText(currentActivity);
        mfaInput.setInputType(InputType.TYPE_CLASS_TEXT);
        mfaModalBuilder.setView(mfaInput);
        mfaModalBuilder.setMessage("Enter the 6 digit MFA code you received by SMS or email:");
        mfaModalBuilder.setPositiveButton("Submit", new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialogInterface, int id) {
            String textInput = mfaInput.getText().toString();
            inputQueue.add(textInput);
          }
        });

        mfaModalBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialogInterface, int i) {
            inputQueue.add(""); // Add this so that the queue doesn't block.
          }
        });

        mfaModalBuilder.show();
      }
    });
    return inputQueue.take();
  }

private boolean loginRequiresMFA(String responseAsString) {
    // Determine whether we need MFA using the title of the response - it will contain the substring
    // 'MFA' if we get redirected to the MFA page.
    String pageTitlePattern = "<title>(.*?)</title>";
    String pageTitle = getFirstMatch(pageTitlePattern,responseAsString);
    if (pageTitle.toUpperCase().contains("MFA")) {
      return true;
    } else {
      return false;
    }
}

  private String handleMfa(String csrf, Activity currentActivity) throws InterruptedException, URISyntaxException, IOException {
    final String mfaCode = promptMFAModalDialog(currentActivity);

    URIBuilder mfaURIBuilder = new URIBuilder(SSO_MFA_URL);
    mfaURIBuilder.addParameters(SIGNIN_PARAMS);
    HttpPost loginPostRequest = new HttpPost(mfaURIBuilder.build());
    loginPostRequest.setHeader(HttpHeaders.REFERER, getLastUri());
    List<NameValuePair> loginPostEntity = Arrays.asList(
            new BasicNameValuePair("mfa-code", mfaCode),
            new BasicNameValuePair("embed", "true"),
            new BasicNameValuePair("_csrf", csrf),
            new BasicNameValuePair("fromPage", "setupEnterMfaCode")
    );
    loginPostRequest.setEntity(new UrlEncodedFormEntity(loginPostEntity, "UTF-8"));
    HttpResponse loginResponse = httpclient.execute(loginPostRequest,httpContext);
    int code = loginResponse.getStatusLine().getStatusCode();
    HttpEntity loginResponseEntity = loginResponse.getEntity();
    String loginContent = EntityUtils.toString(loginResponseEntity);
    return  loginContent;
  }

  private String getLastUri() {
    String target = this.httpContext.getTargetHost().toString();
    String partialUri = this.httpContext.getRequest().getRequestLine().getUri();
    return  target+partialUri;
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

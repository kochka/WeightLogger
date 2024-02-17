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

import android.net.Uri;
import android.support.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
import cz.msebera.android.httpclient.entity.mime.HttpMultipartMode;
import cz.msebera.android.httpclient.entity.mime.MultipartEntityBuilder;
import cz.msebera.android.httpclient.impl.client.DefaultHttpClient;
import cz.msebera.android.httpclient.impl.conn.PoolingClientConnectionManager;
import cz.msebera.android.httpclient.impl.conn.SchemeRegistryFactory;
import cz.msebera.android.httpclient.message.BasicNameValuePair;
import cz.msebera.android.httpclient.params.BasicHttpParams;
import cz.msebera.android.httpclient.params.CoreProtocolPNames;
import cz.msebera.android.httpclient.params.HttpParams;
import cz.msebera.android.httpclient.util.EntityUtils;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import oauth.signpost.http.HttpParameters;
import oauth.signpost.http.HttpRequest;
import oauth.signpost.signature.HmacSha1MessageSigner;
// Disable custom entity, need to find a fix to avoid heavy external Apache libs
// import org.kochka.android.weightlogger.tools.SimpleMultipartEntity;


public class GarminConnect {

  private class OAuth1Token {
    private String oauth1Token;
    private String oauth1TokenSecret;

    // TODO: add additional (optional) members for MFA.

    public OAuth1Token(String oauth1Token, String oauth1TokenSecret) {
      this.oauth1Token = oauth1Token;
      this.oauth1TokenSecret = oauth1TokenSecret;
    }

    public String getOauth1Token() {
      return oauth1Token;
    }

    public String getOauth1TokenSecret() {
      return oauth1TokenSecret;
    }
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

  private static final Pattern LOCATION_PATTERN = Pattern.compile("location: (.*)");
  private static final String CSRF_TOKEN_PATTERN = "name=\"_csrf\" *value=\"([A-Z0-9]+)\"";
  private static final String TICKET_FINDER_PATTERN = "ticket=([^']+?)\";";

  private static final String OAUTH1_FINDER_PATTERN = "token\":\"([a-z0-9]+?)\"";
  private static final String OAUTH2_FINDER_PATTERN = "token=([^']+?)\"";

  private static final String USER_AGENT = "com.garmin.android.apps.connectmobile";

  private DefaultHttpClient httpclient;
  // TODO: Make a class to hold expiry, refresh token, etc. Store this.
  private String oauth2Token;

  public boolean signin(final String username, final String password) {
    PoolingClientConnectionManager conman = new PoolingClientConnectionManager(SchemeRegistryFactory.createDefault());
    conman.setMaxTotal(20);
    conman.setDefaultMaxPerRoute(20);
    httpclient = new DefaultHttpClient(conman);
    httpclient.getParams().setParameter(CoreProtocolPNames.USER_AGENT, USER_AGENT);

    final String signin_url = "https://sso.garmin.com/sso/signin?id=gauth-widget&embedWidget=true" +
            "&gauthHost=https://sso.garmin.com/sso/embed&service=https://sso.garmin.com/sso/embed" +
            "&source=https://sso.garmin.com/sso/embed" +
            "&redirectAfterAccountLoginUrl=https://sso.garmin.com/sso/embed" +
            "&redirectAfterAccountCreationUrl=https://sso.garmin.com/sso/embed";

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
      // Todo: Handle MFA codes.
      String responseAsString = EntityUtils.toString(entity1);
      String ticket = getTicketIdFromResponse(responseAsString);

      if (!isSignedIn(username)) {
        return  false;
      }

      // https://github.com/mttkay/signpost/blob/master/docs/GettingStarted.md
      // Using signpost's CommonsHttpOAuth instead of DefaultOAuth as per https://github.com/mttkay/signpost
      OAuthConsumer consumer = new CommonsHttpOAuthConsumer(OAUTH1_CONSUMER_KEY, OAUTH1_CONSUMER_SECRET);
      consumer.setMessageSigner(new HmacSha1MessageSigner());

      OAuth1Token oauth1Token = getOAuth1Token(ticket, consumer);
      return performOauth2exchange(oauth1Token, consumer);

    } catch (Exception e) {
      httpclient.getConnectionManager().shutdown();
      return false;
    }
  }

  private OAuth1Token getOAuth1Token(String ticket, OAuthConsumer consumer) throws OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException, IOException {
    String theRequestStr = GET_OAUTH1_URL+ "ticket=" + ticket + "&login-url=https://sso.garmin.com/sso/embed&accepts-mfa-tokens=true";
    org.apache.http.client.methods.HttpGet theRequest = new org.apache.http.client.methods.HttpGet(theRequestStr);
    HttpRequest signedRequest = consumer.sign(theRequest);
    //String signed = consumer.sign(GET_OAUTH1_URL+"&accepts-mfa-tokens=true&ticket=" + ticket);
    HttpGet getOauth1 = new HttpGet(theRequestStr);
    //HttpParameters signingParams = consumer.getRequestParameters();
    getOauth1.addHeader("Authorization", signedRequest.getHeader("Authorization"));

    HttpResponse response = httpclient.execute(getOauth1);
    String oauth1ResponseAsString = EntityUtils.toString(response.getEntity());
    OAuth1Token oauth1Token = getOauth1FromResponse(oauth1ResponseAsString);
    return oauth1Token;
  }

  private boolean performOauth2exchange(OAuth1Token oauth1Token, OAuthConsumer consumer) throws OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException, IOException {
    // Exchange for oauth v2 token
    consumer.setTokenWithSecret(oauth1Token.getOauth1Token(), oauth1Token.getOauth1TokenSecret());

    org.apache.http.client.methods.HttpPost exchangeRequest = new org.apache.http.client.methods.HttpPost(GET_OAUTH2_URL);
    HttpRequest signedExchangeRequest = consumer.sign(exchangeRequest);

    HttpPost postOauth2 = new HttpPost(GET_OAUTH2_URL);
    postOauth2.addHeader(HttpHeaders.USER_AGENT, USER_AGENT);
    postOauth2.addHeader(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded");
    postOauth2.addHeader(HttpHeaders.AUTHORIZATION, signedExchangeRequest.getHeader("Authorization"));
    HttpEntity oauth2Entity = httpclient.execute(postOauth2).getEntity();
    String oauth2ResponseAsString = EntityUtils.toString(oauth2Entity);
    try {
      oauth2Token = getOauth2FromResponse(oauth2ResponseAsString);
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
    // TODO: add additional (optional) query parameters for MFA.
    return  new OAuth1Token(oauth1Token,oauth1TokenSecret);
  }

  private String getOauth2FromResponse(String responseAsString) throws JSONException {
    // This time they return JSON.
    JSONObject response = new JSONObject(responseAsString);
    return response.getString("access_token");
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
      HttpResponse execute = httpclient.execute(new HttpGet("https://connect.garmin.com/modern/currentuser-service/user/info"));
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
      post.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + this.oauth2Token);

      MultipartEntityBuilder multipartEntity = MultipartEntityBuilder.create();
      multipartEntity.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
      multipartEntity.addBinaryBody("file", fitFile);
      post.setEntity(multipartEntity.build());

      HttpResponse httpResponse = httpclient.execute(post);
      if(httpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_ACCEPTED){
        Header locationHeader = httpResponse.getFirstHeader("Location");
        String uploadStatusUrl = locationHeader.getValue();
        HttpResponse getStatusResponse = httpclient.execute(new HttpGet(uploadStatusUrl));
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

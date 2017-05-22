package org.kochka.android.weightlogger.tools;

import cz.msebera.android.httpclient.HttpRequest;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.ProtocolException;
import cz.msebera.android.httpclient.client.RedirectStrategy;
import cz.msebera.android.httpclient.client.methods.HttpUriRequest;
import cz.msebera.android.httpclient.protocol.HttpContext;

/**
 * Created by Kuba on 21.05.2017.
 */
class NoRedirectStrategy implements RedirectStrategy {
  @Override
  public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
    return false;
  }

  @Override
  public HttpUriRequest getRedirect(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
    return null;
  }
}

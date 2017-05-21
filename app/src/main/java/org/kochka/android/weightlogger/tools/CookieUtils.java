package org.kochka.android.weightlogger.tools;

import java.util.List;

import cz.msebera.android.httpclient.cookie.Cookie;

/**
 * Created by Kuba on 21.05.2017.
 */

class CookieUtils {
    static String findCookieValueByName(List<Cookie> cookies, String toBeFound) {
      for (Cookie cookie : cookies) {
        if (cookie.getName().equals(toBeFound)){
          return cookie.getValue();
        }
      }
      return null;
    }

    static String findCookieValueByNameAndPath(List<Cookie> cookies, String toBeFound, String path) {
      for (Cookie cookie : cookies) {
        if (cookie.getName().equals(toBeFound) && cookie.getPath().equals(path)){
          return cookie.getValue();
        }
      }
      return null;
    }
}

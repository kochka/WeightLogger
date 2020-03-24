package org.kochka.android.weightlogger.tools;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

public class PermissionHelper {
  private static final int PERMISSION_REQUEST_CODE = 1664;
  private PermissionHelperResultListener resultListener;

  public void setResultListener(PermissionHelperResultListener resultListener) {
    this.resultListener = resultListener;
  }

  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    if (requestCode == PERMISSION_REQUEST_CODE) {
      for (int i = 0; i < permissions.length; i++) {
        if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
          resultListener.onPermissionGranted(permissions[i]);
        }
        else {
          resultListener.onPermissionDenied(permissions[i]);
        }
      }
    }
  }

  public void checkPermission(Activity activity, String permission) {
    if (ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED) {
      resultListener.onPermissionGranted(permission);
    }
    else {
      ActivityCompat.requestPermissions(activity, new String[] {permission}, PERMISSION_REQUEST_CODE);
    }
  }
}

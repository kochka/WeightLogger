package org.kochka.android.weightlogger.tools;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

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
    // If we are above the API version where this is required, we have access by default.
    // This default permission is restrictive but sufficient for our needs.
    if (permission.equals(WRITE_EXTERNAL_STORAGE) && Build.VERSION.SDK_INT >= 29) {
      resultListener.onPermissionGranted(permission);
    }
    // If we are running on an older API version, we do need this permission
    else if (ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED) {
      resultListener.onPermissionGranted(permission);
    }
    else {
      ActivityCompat.requestPermissions(activity, new String[] {permission}, PERMISSION_REQUEST_CODE);
    }
  }
}

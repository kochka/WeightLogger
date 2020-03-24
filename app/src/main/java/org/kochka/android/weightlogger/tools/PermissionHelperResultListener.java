package org.kochka.android.weightlogger.tools;

public interface PermissionHelperResultListener {
  void onPermissionGranted(String permission);
  void onPermissionDenied(String permission);
}

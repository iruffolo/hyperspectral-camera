package com.example.android.camera2.video.fragments;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.navigation.NavArgs;
import java.lang.IllegalArgumentException;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.HashMap;

public class PreviewStabilizationFragmentArgs implements NavArgs {
  private final HashMap arguments = new HashMap();

  private PreviewStabilizationFragmentArgs() {
  }

  @SuppressWarnings("unchecked")
  private PreviewStabilizationFragmentArgs(HashMap argumentsMap) {
    this.arguments.putAll(argumentsMap);
  }

  @NonNull
  @SuppressWarnings("unchecked")
  public static PreviewStabilizationFragmentArgs fromBundle(@NonNull Bundle bundle) {
    PreviewStabilizationFragmentArgs __result = new PreviewStabilizationFragmentArgs();
    bundle.setClassLoader(PreviewStabilizationFragmentArgs.class.getClassLoader());
    if (bundle.containsKey("camera_id")) {
      String cameraId;
      cameraId = bundle.getString("camera_id");
      if (cameraId == null) {
        throw new IllegalArgumentException("Argument \"camera_id\" is marked as non-null but was passed a null value.");
      }
      __result.arguments.put("camera_id", cameraId);
    } else {
      throw new IllegalArgumentException("Required argument \"camera_id\" is missing and does not have an android:defaultValue");
    }
    if (bundle.containsKey("width")) {
      int width;
      width = bundle.getInt("width");
      __result.arguments.put("width", width);
    } else {
      throw new IllegalArgumentException("Required argument \"width\" is missing and does not have an android:defaultValue");
    }
    if (bundle.containsKey("height")) {
      int height;
      height = bundle.getInt("height");
      __result.arguments.put("height", height);
    } else {
      throw new IllegalArgumentException("Required argument \"height\" is missing and does not have an android:defaultValue");
    }
    if (bundle.containsKey("fps")) {
      int fps;
      fps = bundle.getInt("fps");
      __result.arguments.put("fps", fps);
    } else {
      throw new IllegalArgumentException("Required argument \"fps\" is missing and does not have an android:defaultValue");
    }
    if (bundle.containsKey("dynamicRange")) {
      long dynamicRange;
      dynamicRange = bundle.getLong("dynamicRange");
      __result.arguments.put("dynamicRange", dynamicRange);
    } else {
      throw new IllegalArgumentException("Required argument \"dynamicRange\" is missing and does not have an android:defaultValue");
    }
    return __result;
  }

  @SuppressWarnings("unchecked")
  @NonNull
  public String getCameraId() {
    return (String) arguments.get("camera_id");
  }

  @SuppressWarnings("unchecked")
  public int getWidth() {
    return (int) arguments.get("width");
  }

  @SuppressWarnings("unchecked")
  public int getHeight() {
    return (int) arguments.get("height");
  }

  @SuppressWarnings("unchecked")
  public int getFps() {
    return (int) arguments.get("fps");
  }

  @SuppressWarnings("unchecked")
  public long getDynamicRange() {
    return (long) arguments.get("dynamicRange");
  }

  @SuppressWarnings("unchecked")
  @NonNull
  public Bundle toBundle() {
    Bundle __result = new Bundle();
    if (arguments.containsKey("camera_id")) {
      String cameraId = (String) arguments.get("camera_id");
      __result.putString("camera_id", cameraId);
    }
    if (arguments.containsKey("width")) {
      int width = (int) arguments.get("width");
      __result.putInt("width", width);
    }
    if (arguments.containsKey("height")) {
      int height = (int) arguments.get("height");
      __result.putInt("height", height);
    }
    if (arguments.containsKey("fps")) {
      int fps = (int) arguments.get("fps");
      __result.putInt("fps", fps);
    }
    if (arguments.containsKey("dynamicRange")) {
      long dynamicRange = (long) arguments.get("dynamicRange");
      __result.putLong("dynamicRange", dynamicRange);
    }
    return __result;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) {
        return true;
    }
    if (object == null || getClass() != object.getClass()) {
        return false;
    }
    PreviewStabilizationFragmentArgs that = (PreviewStabilizationFragmentArgs) object;
    if (arguments.containsKey("camera_id") != that.arguments.containsKey("camera_id")) {
      return false;
    }
    if (getCameraId() != null ? !getCameraId().equals(that.getCameraId()) : that.getCameraId() != null) {
      return false;
    }
    if (arguments.containsKey("width") != that.arguments.containsKey("width")) {
      return false;
    }
    if (getWidth() != that.getWidth()) {
      return false;
    }
    if (arguments.containsKey("height") != that.arguments.containsKey("height")) {
      return false;
    }
    if (getHeight() != that.getHeight()) {
      return false;
    }
    if (arguments.containsKey("fps") != that.arguments.containsKey("fps")) {
      return false;
    }
    if (getFps() != that.getFps()) {
      return false;
    }
    if (arguments.containsKey("dynamicRange") != that.arguments.containsKey("dynamicRange")) {
      return false;
    }
    if (getDynamicRange() != that.getDynamicRange()) {
      return false;
    }
    return true;
  }

  @Override
  public int hashCode() {
    int result = 1;
    result = 31 * result + (getCameraId() != null ? getCameraId().hashCode() : 0);
    result = 31 * result + getWidth();
    result = 31 * result + getHeight();
    result = 31 * result + getFps();
    result = 31 * result + (int)(getDynamicRange() ^ (getDynamicRange() >>> 32));
    return result;
  }

  @Override
  public String toString() {
    return "PreviewStabilizationFragmentArgs{"
        + "cameraId=" + getCameraId()
        + ", width=" + getWidth()
        + ", height=" + getHeight()
        + ", fps=" + getFps()
        + ", dynamicRange=" + getDynamicRange()
        + "}";
  }

  public static class Builder {
    private final HashMap arguments = new HashMap();

    @SuppressWarnings("unchecked")
    public Builder(PreviewStabilizationFragmentArgs original) {
      this.arguments.putAll(original.arguments);
    }

    @SuppressWarnings("unchecked")
    public Builder(@NonNull String cameraId, int width, int height, int fps, long dynamicRange) {
      if (cameraId == null) {
        throw new IllegalArgumentException("Argument \"camera_id\" is marked as non-null but was passed a null value.");
      }
      this.arguments.put("camera_id", cameraId);
      this.arguments.put("width", width);
      this.arguments.put("height", height);
      this.arguments.put("fps", fps);
      this.arguments.put("dynamicRange", dynamicRange);
    }

    @NonNull
    public PreviewStabilizationFragmentArgs build() {
      PreviewStabilizationFragmentArgs result = new PreviewStabilizationFragmentArgs(arguments);
      return result;
    }

    @NonNull
    @SuppressWarnings("unchecked")
    public Builder setCameraId(@NonNull String cameraId) {
      if (cameraId == null) {
        throw new IllegalArgumentException("Argument \"camera_id\" is marked as non-null but was passed a null value.");
      }
      this.arguments.put("camera_id", cameraId);
      return this;
    }

    @NonNull
    @SuppressWarnings("unchecked")
    public Builder setWidth(int width) {
      this.arguments.put("width", width);
      return this;
    }

    @NonNull
    @SuppressWarnings("unchecked")
    public Builder setHeight(int height) {
      this.arguments.put("height", height);
      return this;
    }

    @NonNull
    @SuppressWarnings("unchecked")
    public Builder setFps(int fps) {
      this.arguments.put("fps", fps);
      return this;
    }

    @NonNull
    @SuppressWarnings("unchecked")
    public Builder setDynamicRange(long dynamicRange) {
      this.arguments.put("dynamicRange", dynamicRange);
      return this;
    }

    @SuppressWarnings("unchecked")
    @NonNull
    public String getCameraId() {
      return (String) arguments.get("camera_id");
    }

    @SuppressWarnings("unchecked")
    public int getWidth() {
      return (int) arguments.get("width");
    }

    @SuppressWarnings("unchecked")
    public int getHeight() {
      return (int) arguments.get("height");
    }

    @SuppressWarnings("unchecked")
    public int getFps() {
      return (int) arguments.get("fps");
    }

    @SuppressWarnings("unchecked")
    public long getDynamicRange() {
      return (long) arguments.get("dynamicRange");
    }
  }
}

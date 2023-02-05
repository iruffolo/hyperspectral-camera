package com.example.android.camera2.video.fragments;

import androidx.annotation.NonNull;
import androidx.navigation.ActionOnlyNavDirections;
import androidx.navigation.NavDirections;
import com.example.android.camera2.video.R;

public class PreviewFragmentDirections {
  private PreviewFragmentDirections() {
  }

  @NonNull
  public static NavDirections actionPreviewToPermissions() {
    return new ActionOnlyNavDirections(R.id.action_preview_to_permissions);
  }
}

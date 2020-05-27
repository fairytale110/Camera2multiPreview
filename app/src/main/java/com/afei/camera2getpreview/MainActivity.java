package com.afei.camera2getpreview;

import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.afei.camera2getpreview.util.Permission;
import com.afei.camera2getpreview.util.PermissionDialog;

public class MainActivity extends AppCompatActivity {

    private CameraFragment mCameraFragment;
    private CameraFragment mCameraFragment1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Permission.checkPermission(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Permission.isPermissionGranted(this)) {
            initCameraFragment();
        }
    }

    private void initCameraFragment() {
        if (mCameraFragment == null) {
            mCameraFragment = new CameraFragment();
            Bundle bundle0 = new Bundle();
            bundle0.putInt("id",2);
            mCameraFragment.setArguments(bundle0);
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, mCameraFragment)
                    .commit();
        }
        if (mCameraFragment1 == null) {
            mCameraFragment1 = new CameraFragment();
            Bundle bundle1 = new Bundle();
            bundle1.putInt("id",0);
            mCameraFragment1.setArguments(bundle1);
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container1, mCameraFragment1)
                    .commit();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == Permission.REQUEST_CODE) {
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    showPermissionDenyDialog();
                    return;
                }
            }
        }
    }

    private void showPermissionDenyDialog() {
        PermissionDialog dialog = new PermissionDialog();
        dialog.show(getSupportFragmentManager(), "PermissionDeny");
    }

}

package com.qbw.locationmanager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import pub.devrel.easypermissions.EasyPermissions;

import android.Manifest;
import android.location.Address;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;

import com.qbw.xlocationmanager.XLocationManager;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        String[] perms = new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
        };
        if (!EasyPermissions.hasPermissions(this, perms)) {
            EasyPermissions.requestPermissions(this, "", 0, perms);
        }
        XLocationManager.getInstance().init(this, true, true);
        XLocationManager.getInstance().tryGetLocationUntilSuccess();
        //XLocationManager.getInstance().showCurrentAddress();

        XLocationManager.GpsAddress address = XLocationManager.getInstance().getLastAddress();
        if (address != null) {
            Log.e("aaa", address.toString());
        }
        XLocationManager.Gps location = XLocationManager.getInstance().getLastLocation();

        if (location != null) {
            Log.e("bbb", location.toString());
        }
    }
}

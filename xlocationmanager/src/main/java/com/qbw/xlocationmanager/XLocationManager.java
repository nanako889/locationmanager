package com.qbw.xlocationmanager;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Handler;

import com.qbw.actionmanager.ActionManager;
import com.qbw.l.L;
import com.qbw.spm.P;

import java.util.List;

public class XLocationManager {
    private static XLocationManager sInst;
    private final String KEY_LAST_KNOWN_LOCATION_LAT = "xlocationmanager_key_last_known_location_lat";
    private final String KEY_LAST_KNOWN_LOCATION_LNG = "xlocationmanager_key_last_known_location_lng";
    private L sLog;
    private Context sContext;
    private Handler mHandler = new Handler();
    private android.location.LocationManager mLocationManager;

    private XLocationManager() {}

    public static XLocationManager getInstance() {
        if (sInst == null) {
            synchronized (XLocationManager.class) {
                if (sInst == null) {
                    sInst = new XLocationManager();
                }
            }
        }
        return sInst;
    }

    public void init(Context context, boolean showLog) {
        P.init(context, showLog);
        sContext = context;
        sLog = new L();
        sLog.setFilterTag("[xlocationmanager]");
        sLog.setEnabled(showLog);
        mLocationManager = (android.location.LocationManager) sContext.getSystemService(Context.LOCATION_SERVICE);
    }

    public Gps getLastGpsLocation() {
        Location location = getLastLocation(false);
        if (location != null) {
            return new Gps(location.getLatitude(), location.getLongitude());
        }
        String lat = P.getString(KEY_LAST_KNOWN_LOCATION_LAT);
        String lng = P.getString(KEY_LAST_KNOWN_LOCATION_LNG);
        sLog.w("use saved gps[%s,%s]", lat, lng);
        return new Gps(doubleValue(lat), doubleValue(lng));
    }

    public void tryGetLocationUntilSuccess() {
        mHandler.removeCallbacks(mGetLocatonRunn);
        mHandler.post(mGetLocatonRunn);
    }

    private Runnable mGetLocatonRunn = new Runnable() {
        @Override
        public void run() {
            Location location = getLastLocation(true);
            if (location == null) {
                mHandler.postDelayed(mGetLocatonRunn, 1500);
            }
        }
    };

    @SuppressLint("MissingPermission")
    public Location getLastLocation(boolean notifyGetLocation) {
        try {
            String locationProvider;
            //获取所有可用的位置提供器
            List<String> providers = mLocationManager.getProviders(true);

            for (String provider : providers) {
                //获取Location
                Location location = mLocationManager.getLastKnownLocation(provider);
                if (location != null && (int) location.getLatitude() != 0 && (int) location.getLongitude() != 0) {
                    sLog.i("save location,%s", location.toString());
                    P.putString(KEY_LAST_KNOWN_LOCATION_LAT, location.getLatitude() + "");
                    P.putString(KEY_LAST_KNOWN_LOCATION_LNG, location.getLongitude() + "");
                    if (notifyGetLocation) {
                        ActionManager.getInstance().triggerAction(new GetLocationAction());
                    }
                    return location;
                } else {
                    mLocationManager.requestSingleUpdate(provider,
                                                         PendingIntent.getBroadcast(sContext,
                                                                                    0,
                                                                                    new Intent(
                                                                                            "broadcast_last_location_changed"),
                                                                                    0));

                }
                sLog.w("provider[%s] location is null", provider);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void showCurrentLocation() {
        Location location = getLastLocation(false);
        if (location != null) {
            sLog.i(location.toString());
        } else {
            sLog.w("get current location failed!");
        }
    }

    /**
     * 判断GPS是否开启，GPS或者AGPS开启一个就认为是开启的
     *
     * @param context
     * @return true 表示开启
     */
    public final boolean isGpsOpened(final Context context) {
        return mLocationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER);
    }

    private double doubleValue(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            sLog.e(e);
        }
        return .0;
    }

    public static class GetLocationAction {

    }

    public static class Gps {

        private double mLat;
        private double mLng;

        public Gps(double lat, double lng) {
            mLat = lat;
            mLng = lng;
        }

        public double getLat() {
            return mLat;
        }

        public void setLat(double lat) {
            mLat = lat;
        }

        public double getLng() {
            return mLng;
        }

        public void setLng(double lng) {
            mLng = lng;
        }
    }

    /*public void requestLocationChanged() {
        mLocationManager.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER,
                                                2000,
                                                0,
                                                new LocationListener() {
                                                    @Override
                                                    public void onLocationChanged(Location location) {
                                                        XLog.d("lat:%f,lon:%f",
                                                               location.getLatitude(),
                                                               location.getLongitude());
                                                    }

                                                    @Override
                                                    public void onStatusChanged(String provider,
                                                                                int status,
                                                                                Bundle extras) {

                                                    }

                                                    @Override
                                                    public void onProviderEnabled(String provider) {

                                                    }

                                                    @Override
                                                    public void onProviderDisabled(String provider) {

                                                    }
                                                });
    }*/
}

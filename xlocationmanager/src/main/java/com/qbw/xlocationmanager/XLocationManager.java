package com.qbw.xlocationmanager;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.google.gson.annotations.SerializedName;
import com.qbw.actionmanager.ActionManager;
import com.qbw.l.L;
import com.qbw.spm.P;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Locale;

public class XLocationManager {
    private static XLocationManager sInst;
    private final String KEY_LAST_KNOWN_LOCATION = "xlocationmanager_key_last_known_location";
    private final String KEY_LAST_KNOWN_ADDRESS = "xlocationmanager_key_last_known_address";
    private L sLog;
    private Context sContext;
    private Handler mHandler = new Handler();
    private android.location.LocationManager mLocationManager;
    public static final String BROADCAST_SINGLE_UPDATE = "broadcast_last_location_changed";
    private int mMaxRetryCount = 10;
    private int mCurrentRetryCount;
    private HandlerThread mHandlerThread;
    private Handler mBackHander;

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

    public void init(Context context, boolean showLog, boolean isInitP) {
        if (isInitP) {
            P.init(context, showLog);
        }
        sContext = context;
        sLog = new L();
        sLog.setFilterTag("[xlocationmanager]");
        sLog.setEnabled(showLog);
        mHandlerThread = new HandlerThread("locationmanager");
        mHandlerThread.start();
        mBackHander = new Handler(mHandlerThread.getLooper());
        mLocationManager =
                (android.location.LocationManager) sContext.getSystemService(Context.LOCATION_SERVICE);
    }

    public Gps getLastLocation() {
        Location location = getCurrentLocation(false);
        if (location != null) {
            return new Gps(location.getLatitude(), location.getLongitude());
        }
        Gps gps = P.getObject(KEY_LAST_KNOWN_LOCATION, Gps.class);
        if (gps != null) {
            sLog.w("use saved gps[%s,%s]", gps.getLat(), gps.getLng());
        } else {
            gps = new Gps(0.0, 0.0);
        }
        return gps;
    }

    public GpsAddress getLastAddress() {
        GpsAddress address = P.getObject(KEY_LAST_KNOWN_ADDRESS, GpsAddress.class);
        if (address != null) {
            sLog.d("address from saved");
            return address;
        }
        return GpsAddress.from(getCurrentAddress());
    }

    public void tryGetLocationUntilSuccess() {
        mCurrentRetryCount = 0;
        mHandler.removeCallbacks(mGetLocatonRunn);
        mHandler.post(mGetLocatonRunn);
    }

    public void stopGetLocation() {
        mHandler.removeCallbacks(mGetLocatonRunn);
    }

    private Runnable mGetLocatonRunn = new Runnable() {
        @Override
        public void run() {
            sLog.w("mCurrentRetryCount %d, mMaxRetryCount %d", mCurrentRetryCount, mMaxRetryCount);
            if (mCurrentRetryCount > mMaxRetryCount) {
                return;
            }
            Location location = getCurrentLocation(true);
            if (location == null) {
                mCurrentRetryCount++;
                mHandler.postDelayed(mGetLocatonRunn, 1500);
            }
        }
    };

    @SuppressLint("MissingPermission")
    public Location getCurrentLocation(boolean notifyGetLocation) {
        try {
            //获取所有可用的位置提供器
            List<String> providers = mLocationManager.getProviders(true);
            if (providers.isEmpty()) {
                sLog.e("location provider is empty");
                return null;
            }
            for (String provider : providers) {
                sLog.i("privider[%s]", provider);
                //获取Location
                final Location location = mLocationManager.getLastKnownLocation(provider);
                if (location != null && (int) location.getLatitude() != 0 && (int) location.getLongitude() != 0) {
                    sLog.i("save location,%s", location.toString());
                    Gps savedGps = P.getObject(KEY_LAST_KNOWN_LOCATION, Gps.class);
                    Gps gps = new Gps(location.getLatitude(), location.getLongitude());
                    P.putObject(KEY_LAST_KNOWN_LOCATION, gps);
                    mBackHander.post(new Runnable() {
                        @Override
                        public void run() {
                            Address address = getAddressByLocation(location);
                            if (address != null) {
                                sLog.i("save address,%s", address.toString());
                            }
                            P.putObject(KEY_LAST_KNOWN_ADDRESS, GpsAddress.from(address));
                        }
                    });
                    if (notifyGetLocation) {
                        ActionManager.getInstance()
                                .triggerAction(new GetLocationAction(savedGps.getLat() != gps.getLat() || savedGps.getLng() != gps.getLng()));
                    }
                    return location;
                } else {
                    mLocationManager.requestSingleUpdate(provider,
                            PendingIntent.getBroadcast(sContext,
                                    0,
                                    new Intent(
                                            BROADCAST_SINGLE_UPDATE),
                                    0));

                }
                sLog.w("provider[%s] location is null", provider);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sLog.w(e);
        }
        return null;
    }

    public void setMaxRetryCount(int maxRetryCount) {
        mMaxRetryCount = maxRetryCount;
    }

    public void showCurrentAddress() {
        Address address = getCurrentAddress();
        if (address != null) {
            sLog.i(address.toString());
        }
    }

    public Address getCurrentAddress() {
        Location location = getCurrentLocation(false);
        if (location == null) {
            sLog.w("location is null");
            return null;
        }
        return getAddressByLocation(location);
    }

    public Address getAddressByLocation(Location location) {
        if (location == null) {
            return null;
        }
        return getAddress(location.getLatitude(), location.getLongitude());
    }

    public Address getAddress(double lat, double lng) {
        Geocoder geocoder = new Geocoder(sContext, Locale.getDefault());
        try {
            List<Address> addressList = geocoder.getFromLocation(lat,
                    lng,
                    1);
            if (addressList != null && !addressList.isEmpty()) {
                return addressList.get(0);
            } else {
                sLog.e("get [%f, %f] address failed",
                        lat,
                        lng);
            }
        } catch (IOException e) {
            e.printStackTrace();
            sLog.e(e);
        }
        return null;
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
        private boolean mIsLocationChanged;

        public GetLocationAction() {

        }

        public GetLocationAction(boolean isLocationChanged) {
            mIsLocationChanged = isLocationChanged;
        }

        public boolean isLocationChanged() {
            return mIsLocationChanged;
        }

        public void setLocationChanged(boolean locationChanged) {
            mIsLocationChanged = locationChanged;
        }
    }

    public static class Gps {

        @SerializedName("lat")
        private double mLat = 0.0;
        @SerializedName("lng")
        private double mLng = 0.0;

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

    public static class GpsAddress implements Serializable {
        @SerializedName("address")
        private String mAddress = "";
        @SerializedName("country")
        private String mCountry = "";
        @SerializedName("province")
        private String mProvince = "";
        @SerializedName("street")
        private String mStreet = "";
        @SerializedName("feature")
        private String mFeature="";

        public String getAddress() {
            return mAddress;
        }

        public void setAddress(String address) {
            mAddress = address;
        }

        public String getCountry() {
            return mCountry;
        }

        public void setCountry(String country) {
            mCountry = country;
        }

        public String getProvince() {
            return mProvince;
        }

        public void setProvince(String province) {
            mProvince = province;
        }

        public String getStreet() {
            return mStreet;
        }

        public void setStreet(String street) {
            mStreet = street;
        }

        public String getFeature() {
            return mFeature;
        }

        public void setFeature(String feature) {
            mFeature = feature;
        }

        private static GpsAddress from(Address address) {
            if (address == null || address.getMaxAddressLineIndex() < 0) {
                return new GpsAddress();
            }
            GpsAddress gpsAddress = new GpsAddress();
            gpsAddress.setAddress(address.getAddressLine(0));
            gpsAddress.setCountry(address.getCountryName());
            gpsAddress.setProvince(address.getAdminArea());
            gpsAddress.setStreet(address.getThoroughfare());
            gpsAddress.setFeature(address.getFeatureName());
            return gpsAddress;
        }
    }

    /*public void requestLocationChanged() {
        mLocationManager.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER,
                                                2000,
                                                0,
                                                new LocationListener() {
                                                    @Override
                                                    public void onLocationChanged(Location
                                                    location) {
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
                                                    public void onProviderDisabled(String
                                                    provider) {

                                                    }
                                                });
    }*/
}

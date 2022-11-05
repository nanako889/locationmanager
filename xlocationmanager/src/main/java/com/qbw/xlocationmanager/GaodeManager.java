package com.qbw.xlocationmanager;

import android.content.Context;
import android.os.Handler;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.qbw.actionmanager.ActionManager;
import com.qbw.l.L;
import com.qbw.spm.P;

public class GaodeManager {

    private static GaodeManager sInst;

    private GaodeManager() {}

    public static GaodeManager getInstance() {
        if (sInst == null) {
            synchronized (GaodeManager.class) {
                if (sInst == null) {
                    sInst = new GaodeManager();
                }
            }
        }
        return sInst;
    }

    private boolean mWorking;
    public AMapLocationClient mLocationClient;
    public AMapLocationListener mLocationListener;
    private AMapLocationClientOption mLocationOption;
    private int mMaxRetryCount;
    private int mCurrentRetryCount;
    private Handler mHandler = new Handler();
    private final String KEY_LAST_GAODE_LOCATION = "gaodemanager_key_last_gaode_location";

    public void init(Context context) {
        mLocationListener = new AMapLocationListener() {
            @Override
            public void onLocationChanged(AMapLocation amapLocation) {
                if (amapLocation != null) {
                    if (amapLocation.getErrorCode() == 0) {
                        mWorking = false;
                        P.putObject(KEY_LAST_GAODE_LOCATION, amapLocation);
                        ActionManager.getInstance().triggerAction(new GetGaodeLocationSuccess(amapLocation));
                    } else {
                        L.GL.e("mCurrentRetryCount:%d, errcode:%d, errInfo:%s", mCurrentRetryCount, amapLocation.getErrorCode(), amapLocation.getErrorInfo());
                        mCurrentRetryCount++;
                        if (mCurrentRetryCount > mMaxRetryCount) {
                            mWorking = false;
                            return;
                        }
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mLocationClient.startLocation();
                            }
                        }, 1000);
                    }
                } else {
                    mWorking = false;
                }
            }
        };
        mLocationClient = new AMapLocationClient(context);
        mLocationClient.setLocationListener(mLocationListener);
        mLocationOption = new AMapLocationClientOption();
        mLocationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
        mLocationOption.setOnceLocation(true);
        mLocationOption.setOnceLocationLatest(true);
        mLocationOption.setNeedAddress(true);
        mLocationClient.setLocationOption(mLocationOption);
    }

    public void startLocation() {
        startLocation(5);
    }

    public void startLocation(int maxRetryCount) {
        if (mWorking) {
            L.GL.w("gaode loacating...");
            return;
        }
        mMaxRetryCount = maxRetryCount;
        mCurrentRetryCount = 0;
        mWorking = true;
        mLocationClient.startLocation();
    }

    public AMapLocation getLastGaodeLocation() {
        return P.getObject(KEY_LAST_GAODE_LOCATION, AMapLocation.class);
    }

    public static class GetGaodeLocationSuccess {
        private AMapLocation mAMapLocation;

        public GetGaodeLocationSuccess(AMapLocation AMapLocation) {
            mAMapLocation = AMapLocation;
        }

        public AMapLocation getAMapLocation() {
            return mAMapLocation;
        }
    }
}

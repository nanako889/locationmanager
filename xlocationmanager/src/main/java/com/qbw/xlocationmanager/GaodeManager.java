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

/**
 * @author QBW
 * @date 11/1/21
 */
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
                        //可在其中解析amapLocation获取相应内容。
                        ActionManager.getInstance().triggerAction(new GetGaodeLocationSuccess(amapLocation));
                    } else {
                        //定位失败时，可通过ErrCode（错误码）信息来确定失败的原因，errInfo是错误信息，详见错误码表。
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
        //设置定位模式为AMapLocationMode.Hight_Accuracy，高精度模式。
        mLocationOption = new AMapLocationClientOption();
        mLocationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
        //获取一次定位结果：
        //该方法默认为false。
        mLocationOption.setOnceLocation(true);
        //获取最近3s内精度最高的一次定位结果：
        //设置setOnceLocationLatest(boolean b)接口为true，启动定位时SDK会返回最近3s内精度最高的一次定位结果。如果设置其为true，setOnceLocation(boolean b)接口也会被设置为true，反之不会，默认为false。
        mLocationOption.setOnceLocationLatest(true);
        //设置是否返回地址信息（默认返回地址信息）
        mLocationOption.setNeedAddress(true);
        mLocationClient.setLocationOption(mLocationOption);
    }

    public void startLocation() {
        startLocation(5);
    }

    public void startLocation(int maxRetryCount) {
        if (mWorking) {
            L.GL.w("正在通过高德定位...");
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

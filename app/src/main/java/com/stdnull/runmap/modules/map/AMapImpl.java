package com.stdnull.runmap.modules.map;

import android.Manifest;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.SystemClock;
import android.util.Log;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.maps.AMap;
import com.amap.api.maps.AMapUtils;
import com.amap.api.maps.CameraUpdate;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.LocationSource;
import com.amap.api.maps.UiSettings;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.CameraPosition;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.LatLngBounds;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.MyLocationStyle;
import com.amap.api.maps.model.PolylineOptions;
import com.amap.api.maps.utils.overlay.SmoothMoveMarker;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.geocoder.RegeocodeAddress;
import com.stdnull.runmap.GlobalApplication;
import com.stdnull.runmap.R;
import com.stdnull.runmap.model.BuildingPoint;
import com.stdnull.runmap.model.TrackPoint;
import com.stdnull.runmap.common.CFAsyncTask;
import com.stdnull.runmap.common.CFLog;
import com.stdnull.runmap.common.RMConfiguration;
import com.stdnull.runmap.common.TaskHanler;
import com.stdnull.runmap.lifecircle.LifeCycleMonitor;
import com.stdnull.runmap.managers.DataManager;
import com.stdnull.runmap.modules.permission.PermissionManager;
import com.stdnull.runmap.modules.permission.PermissionCallBack;
import com.stdnull.runmap.utils.SystemUtils;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

/**
 * 地图服务类
 * Created by chen on 2017/1/23.
 */

public class AMapImpl implements AMapStateListener, AMap.OnMarkerClickListener{

    public static final String TAG = "location";

    private AMap mAmap;

    private static AMapImpl mInstance;
    /**
     * 定位服务类实例，提供单次定位、持续定位、最后位置相关功能。
     */
    private AMapLocationClient mAmapLocationClient;
    /**
     * 定位参数设置，通过该实例可以对定位的相关参数进行设置
     */
    private AMapLocationClientOption mAmapLocationOption;
    /**
     * 定位间隔时长
     */
    private long mLocationInterval = 5 * RMConfiguration.SECOND;

    /**
     * 标志起点是否设置成功
     */
    private boolean hasStartPointSetted = false;


    /**
     * 帮助类，分解部分功能
     */
    private AmLocationHelper mLocationHelper;

    private LatLng mLastLocationPoint;
    private int mLastLocationIndex = 0;


    private boolean isClosed = false;

    private int mTrashDataCount = 0;

    private AMapStateListenerImpl mAmapStateListener;

    private AMapImpl() {
        mAmapStateListener = new AMapStateListenerImpl(this);

        mLocationHelper = new AmLocationHelper();
    }

    public static synchronized AMapImpl getInstance() {
        if (mInstance == null) {
            mInstance = new AMapImpl();
        }
        return mInstance;
    }

    //***********************初始化相关*************************************

    private void initLocationClient() {
        mAmapLocationClient = new AMapLocationClient(GlobalApplication.getAppContext());
        mAmapLocationClient.setLocationListener(mAmapStateListener);
        mAmapLocationClient.setLocationOption(mAmapLocationOption);
    }

    private void initLocationOptions() {
        mAmapLocationOption = new AMapLocationClientOption();
        //设置定位模式为AMapLocationMode.Hight_Accuracy，高精度模式。
        mAmapLocationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
        //设置定位间隔,单位毫秒,默认为2000ms，最低1000ms。
        mAmapLocationOption.setInterval(mLocationInterval);
        //单位是毫秒，默认30000毫秒，建议超时时间不要低于8000毫秒。
        mAmapLocationOption.setHttpTimeOut(RMConfiguration.HTTP_OUT_TIME);
        //开启缓存机制
        mAmapLocationOption.setLocationCacheEnable(false);
        //需要返回地址
        mAmapLocationOption.setNeedAddress(true);
        //当设置为true时，网络定位可以返回海拔、角度和速度
        mAmapLocationOption.setSensorEnable(true);

    }


    public void initAMap(AMap aMap, int flag) {
        this.mAmap = aMap;
        //照传入的CameraUpdate参数移动可视区域。
        mAmap.moveCamera(CameraUpdateFactory.zoomTo(19));
        //设置定位资源。如果不设置此定位资源则定位按钮不可点击。
        aMap.setLocationSource(mAmapStateListener);
        //显示室内地图
        aMap.showIndoorMap(true);
        if (flag == 0) {
            //设置最大缩放等级
            aMap.setMinZoomLevel(8);
        }
        //如果显示定位层，则界面上将出现定位按钮，如果未设置Location Source 则定位按钮不可点击。
        aMap.setMyLocationEnabled(true);
        //定位、移动到地图中心点，跟踪并根据方向旋转地图
        aMap.setMyLocationType(AMap.LOCATION_TYPE_MAP_FOLLOW);
        //定位中心原点
        MyLocationStyle style = new MyLocationStyle();
        style.radiusFillColor(Color.TRANSPARENT);
        style.strokeWidth(0);
        aMap.setMyLocationStyle(style);
        //去除缩放按钮
        UiSettings settings = aMap.getUiSettings();
        settings.setZoomControlsEnabled(false);

        Log.e("TAG","initAMap");
    }



    //*********************************************************************


    //***************************UI相关，绘制操作*****************************************

    public void clearAll() {
        mAmap.clear();
    }

    public void drawPolyLine(int color, LatLng... latLngs) {
        List<LatLng> tmp = Arrays.asList(latLngs);
        drawPolyLine(tmp, color);
    }

    public void drawPolyLine(List<LatLng> latLngs, int color) {
        CFLog.e(TAG, "draw new Poly");
        PolylineOptions options = new PolylineOptions();
        options.addAll(latLngs);
        options.color(color);
        options.width(20);
        mAmap.addPolyline(options);
    }


    public void drawPolyLineWithTexture(List<LatLng> latLngs, int textureId) {
        mAmap.addPolyline(new PolylineOptions().setCustomTexture(BitmapDescriptorFactory.fromResource(textureId))
                .addAll(latLngs)
                .useGradient(true)
                .width(18));
    }

    private Queue<SmoothMoveMarker> mMarkerLists = new LinkedList<>();

    public void drawTrackLine(List<LatLng> points, int currentCount, SmoothMoveMarker.MoveListener moveListener) {
        //寻找与起点距离最远的点
        SmoothMoveMarker pre = mMarkerLists.peek();
        if(pre != null){
            pre.setMoveListener(null);
            mMarkerLists.poll();
        }
        float maxDistance = 0;
        LatLng endPoint = null;
        for (int i = 1; i < points.size(); i++) {
            float distance = AMapUtils.calculateLineDistance(points.get(0), points.get(i));
            if (distance > maxDistance) {
                endPoint = points.get(i);
                maxDistance = distance;
            }
        }
        CFLog.e(TAG, "max distance = " + maxDistance);

        //代表构成的一个矩形区域，由两点决定
        LatLngBounds bounds = new LatLngBounds(points.get(0), endPoint);

        float pad = GlobalApplication.getAppContext().getResources().getDisplayMetrics().scaledDensity * RMConfiguration.MAP_PADDING;
        moveToSpecficCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition(points.get(0), 17, 0, 0)));
        mAmap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, (int) pad));

        drawSingleMaker(points.get(0), GlobalApplication.getAppContext().getString(R.string.string_start_point), -1);
        drawSingleMaker(points.get(points.size() - 1), GlobalApplication.getAppContext().getString(R.string.string_end_point), -1);
        if (currentCount == 0) {
            drawPolyLineWithTexture(points, R.mipmap.track_line_texture);
        } else {
            Random random = new Random(SystemClock.currentThreadTimeMillis());

            drawPolyLine(points, Color.rgb(random.nextInt(255), random.nextInt(255), random.nextInt(255)));
        }


        //按照指定的经纬度数据和时间，平滑移动
        SmoothMoveMarker smoothMarker = new SmoothMoveMarker(mAmap);
        // 设置滑动的图标
        smoothMarker.setDescriptor(BitmapDescriptorFactory.fromResource(R.mipmap.track_line_icon));
        // 设置滑动的轨迹点
        smoothMarker.setPoints(points);
        // 设置滑动的总时间
        smoothMarker.setTotalDuration(20);
        //设置监听
        smoothMarker.setMoveListener(moveListener);
        // 开始滑动
        smoothMarker.startSmoothMove();
        mMarkerLists.add(smoothMarker);
    }

    public void drawSingleMaker(LatLng latLng, String title, int iconId) {
        if (iconId != -1) {
            MarkerOptions options = new MarkerOptions().position(latLng).title(title);
            options.icon(BitmapDescriptorFactory.fromResource(iconId));
            mAmap.addMarker(options);
        } else {
            mAmap.addMarker(new MarkerOptions().position(latLng).title(title).snippet("DefaultMarker"));
        }
    }

    public void drawMarkers(List<BuildingPoint> buildingPointList) {
        for (int i = 0; i < buildingPointList.size(); i++) {
            BuildingPoint point = buildingPointList.get(i);
            MarkerOptions options = new MarkerOptions().position(point.getLatLng()).title(point.getBuildName());
            options.snippet("停留" + (point.getTime() / 1000 / 60) + "分钟");
            options.setFlat(true);
            Marker marker = mAmap.addMarker(options);
            marker.setInfoWindowEnable(true);
            marker.showInfoWindow();
        }
    }


    public void moveToSpecficCamera(CameraUpdate cameraUpdate) {
        mAmap.moveCamera(cameraUpdate);
    }


    /**
     * 获得新的定位地址时的处理，包括以下几步
     * 1、更新GPS信号强度信息
     * 2、判断是否非法数据
     * 4、构造数据类型添加数据点
     * 5、绘制线段
     * 6、经纬度逆编码
     *
     * @param aMapLocation
     */
    private boolean resolveLocationChanged(LocationSource.OnLocationChangedListener amListener, AMapLocation aMapLocation) {
        LatLng latLng = new LatLng(aMapLocation.getLatitude(), aMapLocation.getLongitude(), true);
        List<TrackPoint> trackPointList = DataManager.getInstance().getTrackPoints();
        //判断数据的合理性,每五分钟强制增加数据
        if (mLocationHelper.shouldAddLatLng(trackPointList, latLng, aMapLocation.getSpeed()) || mTrashDataCount > RMConfiguration.FORCE_COUNT) {
            mTrashDataCount = 0;
            amListener.onLocationChanged(aMapLocation);//显示系统定位蓝点
            //绘制轨迹
            if (trackPointList.size() > 0) {
                int color = mLocationHelper.getColorBySpeed(aMapLocation.getSpeed());
                drawPolyLine(color, trackPointList.get(trackPointList.size() - 1).getLocation(), latLng);
            } else if (mLastLocationPoint != null) {
                //当系统从后台进入时，需要将上次定位的点连接
               // drawPolyLine(trackPointList,latLng);
            }
            //每次单独cache一个缓存点及其数据索引
            mLastLocationPoint = latLng;
            mLastLocationIndex = trackPointList.size()-1;
            //添加数据
            TrackPoint trackPoint = new TrackPoint(latLng, SystemClock.elapsedRealtime());
            DataManager.getInstance().addTrackPoint(trackPoint);
            //位置逆编码
            requestRegeoAddress(aMapLocation, trackPoint);
            return true;
        }
        mTrashDataCount ++;
        return false;
    }

    private void requestRegeoAddress(AMapLocation aMapLocation, final TrackPoint trackPoint) {
        CFAsyncTask<RegeocodeAddress> task = new CFAsyncTask<RegeocodeAddress>() {
            @Override
            public RegeocodeAddress onTaskExecuted(Object... params) {
                if(!SystemUtils.isNetworkEnable(GlobalApplication.getAppContext())){
                    return null;
                }
                return mLocationHelper.regeocodeAddress((LatLonPoint) params[0]);
            }

            @Override
            public void onTaskFinished(RegeocodeAddress result) {
                if (result == null) {
                    return;
                }
                trackPoint.setBuildName(result.getBuilding());
                CFLog.e(TAG, "regeo building =" + result.getBuilding()
                        + "district = " + result.getDistrict() + " neiberhood = " + result.getNeighborhood()
                        + "street = " + result.getStreetNumber());
            }
        };
        LatLonPoint point = new LatLonPoint(aMapLocation.getLatitude(), aMapLocation.getLongitude());
        TaskHanler.getInstance().sendTask(task, point);
    }


    //************************事件通知相关*********************************
    @Override
    public void notifyServiceActive() {
        Log.e("TAG","notifyServiceActive");
        if (mAmapLocationClient != null) {
            return;
        }
        initLocationOptions();
        initLocationClient();
    }

    @Override
    public void notifyServiceDeactivate() {
        if (mAmapLocationClient != null) {
            mAmapLocationClient.unRegisterLocationListener(mAmapStateListener);
            mAmapLocationClient.onDestroy();
        }
        mAmapLocationClient = null;
        Log.e("TAG","notifyServiceDeactivate");
    }


    @Override
    public void notifyLocationChanged(LocationSource.OnLocationChangedListener amListener, AMapLocation aMapLocation) {
        if (isClosed) {
            return;
        }
        if (aMapLocation != null && aMapLocation.getErrorCode() == AMapLocation.LOCATION_SUCCESS) {
            if (aMapLocation.getProvider().equals("lbs") && !hasStartPointSetted) {
                return;
            }
            hasStartPointSetted = true;
            resolveLocationChanged(amListener, aMapLocation);
        } else {
            String errText = "定位失败," + aMapLocation.getErrorCode() + ": " + aMapLocation.getErrorInfo();
            CFLog.e(TAG, errText);
        }
    }

    @Override
    public void notifyMapLoaded() {

    }

    @Override
    public void notifyGPSSwitchChanged() {
    }



    public void setClosed(boolean closed) {
        isClosed = closed;
    }


    @Override
    public boolean onMarkerClick(Marker marker) {
        CFLog.e(TAG,"marker title = "+marker.getTitle());
        return false;
    }
}
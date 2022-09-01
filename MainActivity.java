package com.tokyonth.weather.activity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;

import com.amap.api.location.AMapLocationClient;
import com.aokj.sdk.advip.wxpay.ClearAdActivity;
import com.aokj.sdk.csj.CSJAdManagerHolder;
import com.aokj.sdk.gdt.GDTAdManagerHolder;
import com.aokj.sdk.lc.AdConfig;
import com.aokj.sdk.lc.AdConfigInterface;
import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.Fragment;
import androidx.appcompat.widget.Toolbar;
import androidx.viewpager2.widget.ViewPager2;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.gson.Gson;
import com.kongzue.dialog.interfaces.OnDialogButtonClickListener;
import com.kongzue.dialog.util.BaseDialog;
import com.kongzue.dialog.util.DialogSettings;
import com.kongzue.dialog.v3.MessageDialog;
import com.tbruyelle.rxpermissions2.RxPermissions;
import com.tokyonth.weather.BaseActivity;
import com.tokyonth.weather.R;
import com.tokyonth.weather.assembly.WeatherType;
import com.tokyonth.weather.adapter.WeatherPagerAdapter;
import com.tokyonth.weather.dynamic.DynamicWeatherView;
import com.tokyonth.weather.entirety.FragmentLifecycle;
import com.tokyonth.weather.fragment.WeatherPageDetailed;
import com.tokyonth.weather.model.bean.DefaultCity;
import com.tokyonth.weather.model.bean.SavedCity;
import com.tokyonth.weather.model.bean.Weather;
import com.tokyonth.weather.presenter.CityPresenter;
import com.tokyonth.weather.presenter.CityPresenterImpl;
import com.tokyonth.weather.presenter.LoadCitySituationListener;
import com.tokyonth.weather.presenter.LocationPresenter;
import com.tokyonth.weather.presenter.LocationPresenterImpl;
import com.tokyonth.weather.presenter.WeatherPresenter;
import com.tokyonth.weather.presenter.WeatherPresenterImpl;
import com.tokyonth.weather.utils.sundry.DateUtil;
import com.tokyonth.weather.utils.sundry.PreferencesLoader;
import com.tokyonth.weather.utils.helper.WeatherInfoHelper;
import com.tokyonth.weather.utils.file.FileUtil;
import com.tokyonth.weather.presenter.WeatherView;
import com.umeng.analytics.MobclickAgent;
import com.umeng.commonsdk.UMConfigure;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.litepal.crud.DataSupport;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends BaseActivity implements WeatherView {

    private CoordinatorLayout weather_basic_cdl;
    private WeatherPresenter weather_presenter;
    private DynamicWeatherView dynamic_weatherView;

    private TextView toolbar_tv_city;
    private ImageView default_city_iv;
    private WeatherPagerAdapter weather_page_adapter;

    public boolean isDefaultCity = true;
    private Weather offline_weather;

    public DefaultCity mDefaultCity;
    public SavedCity mSavedCity;

    public LinearLayout weather_basic;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AMapLocationClient.updatePrivacyShow(getApplicationContext(), true, true);
        AMapLocationClient.updatePrivacyAgree(getApplicationContext(), true);
        ///
        startData();
        setContentView(R.layout.activity_main);
        initLayout();
        initView();

        //友盟统计
        UMConfigure.preInit(getApplicationContext(), GDTAdManagerHolder.UMENG_KEY, getString(R.string.app_name));
        MobclickAgent.setPageCollectionMode(MobclickAgent.PageMode.AUTO);
        UMConfigure.init(getApplicationContext(), GDTAdManagerHolder.UMENG_KEY, getString(R.string.app_name), UMConfigure.DEVICE_TYPE_PHONE, null);
        GDTAdManagerHolder.checkAndRequestPermission(this);

        String weather_data = FileUtil.getFile(FileUtil.SAVE_WEATHER_NAME);
        assert weather_data != null;
        if (!weather_data.equals("")) {
            offline_weather = new Gson().fromJson(weather_data, Weather.class);
            // EventBus.getDefault().post(offline_weather);
            setWeatherBackground(offline_weather);
        }
        weather_presenter = new WeatherPresenterImpl(this);

        List<String> lackedPermission = new ArrayList<String>();
        if (!(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
            lackedPermission.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (!(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
            lackedPermission.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        // 快手SDK所需相关权限，存储权限，此处配置作用于流量分配功能，关于流量分配，详情请咨询商务;如果您的APP不需要快手SDK的流量分配功能，则无需申请SD卡权限
        if (!(checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)) {
            lackedPermission.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (!(checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)) {
            lackedPermission.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        if (lackedPermission.size() != 0) {//实例化这个权限请求框架，否则会报错
            DialogSettings.init();
            DialogSettings.style = DialogSettings.STYLE.STYLE_MIUI;
            DialogSettings.theme = DialogSettings.THEME.LIGHT;
            DialogSettings.tipTheme = DialogSettings.THEME.DARK;
            MessageDialog.build(MainActivity.this)
                    .setTitle("温馨提示")
                    .setMessage("使用该页面功能，需要访问存储权限(存储全国天气城市)、定位权限（定位当前位置获取天气），请允许！")
                    .setOkButton("确定", new OnDialogButtonClickListener() {
                        @Override
                        public boolean onClick(BaseDialog baseDialog, View v) {
                            RxPermissions rxPermissions = new RxPermissions(MainActivity.this);
                            rxPermissions.request(Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION,
                                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                            Manifest.permission.READ_EXTERNAL_STORAGE)
                                    .subscribe(granted -> {
                                        if (granted) {//申请成功
                                            LocationPresenter location_presenter = new LocationPresenterImpl();
                                            location_presenter.loadLocation(MainActivity.this);
                                        } else {//申请失败
                                            Snackbar.make(weather_basic_cdl, "请打开所需权限，才能正常使用", Snackbar.LENGTH_LONG)
                                                    .show();
                                        }
                                    });
                            return false;
                        }
                    })
                    .setCancelButton("取消", new OnDialogButtonClickListener() {
                        @Override
                        public boolean onClick(BaseDialog baseDialog, View v) {
                            Snackbar.make(weather_basic_cdl, "请打开所需权限，才能正常使用", Snackbar.LENGTH_LONG)
                                    .show();
                            return false;
                        }
                    })
                    .show();
        } else {
            LocationPresenter location_presenter = new LocationPresenterImpl();
            location_presenter.loadLocation(this);
        }

        if (AdConfig.isConfig) {
            AdConfig.getConfig(this, new AdConfigInterface() {
                @Override
                public void isAdConfig(boolean isAd) {
                    if (isAd)
                        if (AdConfig.isGDT(MainActivity.this)) {
                            GDTAdManagerHolder.loadUnifiedInterstitialAD(MainActivity.this);
                        } else {
                            CSJAdManagerHolder.loadFullScreenVideoAd(MainActivity.this);
                        }
                }
            });
        } else {
            if (AdConfig.isGDT(MainActivity.this)) {
                GDTAdManagerHolder.loadUnifiedInterstitialAD(MainActivity.this);
            } else {
                CSJAdManagerHolder.loadFullScreenVideoAd(MainActivity.this);
            }
        }
    }

    private void initLayout() {
        if (Build.VERSION.SDK_INT >= 21) {
            View decorView = getWindow().getDecorView();
            int option = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            decorView.setSystemUiVisibility(option);
            getWindow().setStatusBarColor(Color.TRANSPARENT);
        }

        WeatherPageDetailed weatherPageDetailed = new WeatherPageDetailed(this);
        List<Fragment> page_list = new ArrayList<>();
        page_list.add(weatherPageDetailed);
        weather_page_adapter = new WeatherPagerAdapter(getSupportFragmentManager(), new FragmentLifecycle(), page_list);
    }

    private void initView() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setOverflowIcon(getDrawable(R.drawable.ic_title_more));
        setSupportActionBar(toolbar);
        setTitle(null);

        weather_basic_cdl = findViewById(R.id.weather_basic_cdl);
        default_city_iv = findViewById(R.id.default_city_iv);
        toolbar_tv_city = findViewById(R.id.weather_city_name_tv);
        weather_basic = findViewById(R.id.main_ll);
        dynamic_weatherView = findViewById(R.id.dynamic_weather_view);
        ViewPager2 weather_pages = findViewById(R.id.viewpager2);
//        weather_pages.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
//        weather_pages.setUserInputEnabled(true);
        weather_pages.setAdapter(weather_page_adapter);
        weather_pages.setOffscreenPageLimit(2);
//        weather_pages.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
//            @Override
//            public void onPageSelected(int position) {
//                super.onPageSelected(position);
//                if (position == 1) {
//                    weather_refresh.setEnabled(false);
//                } else if (position == 0) {
//                    weather_refresh.setEnabled(true);
//                }
//            }
//
//        });

    }

    private void startData() {
        boolean isFirst = PreferencesLoader.getBoolean(PreferencesLoader.IMPORT_DATA, true);
        if (isFirst) {
            if (isNetworkConnected(this)) {
                CityPresenter cityPresenter = new CityPresenterImpl();
                cityPresenter.saveCityList(new LoadCitySituationListener() {
                    @Override
                    public void Success() {
                        Snackbar.make(weather_basic_cdl, getResources().getString(R.string.import_success), Snackbar.LENGTH_LONG)
                                .show();
                        PreferencesLoader.putBoolean(PreferencesLoader.IMPORT_DATA, false);
                    }

                    @Override
                    public void Fail() {
                        Snackbar.make(weather_basic_cdl, getResources().getString(R.string.import_failed), Snackbar.LENGTH_LONG)
                                .show();
                    }
                });
            } else {
                androidx.appcompat.app.AlertDialog mDialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle(getResources().getString(R.string.dialog_text_title))
                        .setMessage(getResources().getString(R.string.no_network_connection))
                        .setPositiveButton(getResources().getString(R.string.btn_exit), (dialogInterface, i) -> finish())
                        .create();
                mDialog.show();
                mDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.BLUE);
            }
        }
    }

    private boolean isNetworkConnected(Context context) {
        if (context != null) {
            ConnectivityManager mConnectivityManager = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo mNetworkInfo = mConnectivityManager.getActiveNetworkInfo();
            if (mNetworkInfo != null) {
                return mNetworkInfo.isAvailable();
            }
        }
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.weather_main_menu, menu);
        if (AdConfig.isConfig) {
            AdConfig.isAdOpenVip(this, new AdConfigInterface() {
                @Override
                public void isAdConfig(boolean isAd) {
                    if (isAd) {
                        menu.findItem(R.id.action_clear_ad).setVisible(true);
                    } else {
                        menu.findItem(R.id.action_clear_ad).setVisible(false);
                    }
                }
            });
        } else {
            menu.findItem(R.id.action_clear_ad).setVisible(true);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        Intent intent = new Intent();
        switch (id) {
            case R.id.action_flash:
                intent.setClass(MainActivity.this, FlashActivity.class);
                startActivity(intent);
                break;
            case R.id.action_map:
                break;
            case R.id.action_city:
                intent.setClass(MainActivity.this, CityActivity.class);
                startActivity(intent);
                break;
            case R.id.action_settings:
                intent.setClass(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
                break;
            case R.id.action_clear_ad:
                intent.setClass(MainActivity.this, ClearAdActivity.class);
                startActivity(intent);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Subscribe
    public void getLocation(DefaultCity defaultCity) {
        Log.e("xxxxxx", "xxxdefaultCity" + defaultCity.toString());
        weather_presenter.getLocationWeather(defaultCity);
        mDefaultCity = defaultCity;
        isDefaultCity = true;
        default_city_iv.setVisibility(View.VISIBLE);
    }

    @Subscribe
    public void getCity(SavedCity savedCity) {
        Log.e("xxxxxx", "xxxsavedCity" + savedCity.toString());
        weather_presenter.getWeather(savedCity);
        mSavedCity = savedCity;
        isDefaultCity = false;
        default_city_iv.setVisibility(View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        dynamic_weatherView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        dynamic_weatherView.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        dynamic_weatherView.onDestroy();
        MobclickAgent.onKillProcess(this);
    }

    @Override
    public void onBackPressed() {
        GDTAdManagerHolder.onBackPressedAd(this);
    }

    @Override
    public void showWeather(Weather weather) {
        if (weather.getStatus() == 0) {
            setWeatherBackground(weather);
            EventBus.getDefault().post(weather);

            String cityName = weather.getInfo().getCityName();
            toolbar_tv_city.setText(cityName);
        } else {
            Snackbar.make(weather_basic, "获取天气错误" + weather.getStatus(), Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    public void showErrorInfo(String error) {
        Snackbar.make(weather_basic, error + getResources().getString(R.string.load_last_time_msg), Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void showOffLine() {
        EventBus.getDefault().post(offline_weather);
    }

    public void setWeatherBackground(Weather weather) {
//        String img = weather.getInfo().getImg();
        String img = weather.getInfo().getHourlyList().get(0).getImg();
        int weatherType = WeatherInfoHelper.getWeatherType(img);

        List<Integer> list = WeatherInfoHelper.getSunriseSunset(weather);
        boolean isInTime = DateUtil.isCurrentInTimeScope(list.get(0), list.get(1), list.get(2), list.get(3));

        dynamic_weatherView.setDrawerType(WeatherType.getType(isInTime, weatherType));

    }
}

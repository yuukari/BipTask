package com.yuukari.biptask.activity;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;

import com.yuukari.biptask.AmazfitService;
import com.yuukari.biptask.Preferences;
import com.yuukari.biptask.R;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_LOCATION = 0x01;

    private BluetoothAdapter bluetoothAdapter;
    private Handler handler;

    private BroadcastReceiver broadcastReceiver;

    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        handler = new Handler();

        preferences = getSharedPreferences(Preferences.NAME, Context.MODE_PRIVATE);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){
            if (preferences.contains(Preferences.DEVICE_ADDRESS)) {
                if (!isServiceRunning(AmazfitService.class))
                    startService(new Intent(this, AmazfitService.class));

                startActivity(new Intent(this, LoggerActivity.class));
                finish();
            }
        } else {
            requestPermissions();
        }
    }

    private void requestPermissions(){
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                Toast.makeText(this, "Разрешение на получение геолокации требуется для подключения Bluetooth устройств.", Toast.LENGTH_LONG).show();
            }

            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_LOCATION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (preferences.contains(Preferences.DEVICE_ADDRESS) && !isServiceRunning(AmazfitService.class))
                        startService(new Intent(this, AmazfitService.class));
                } else {
                    Toast.makeText(this, "Похоже, что вы запретили приложению получать геолокацию. Без этого разрешения работа приложения невозможна.", Toast.LENGTH_SHORT).show();
                }
            break;
        }
    }

    public void connectClick(View view) {
        String deviceAddress = ((TextView)findViewById(R.id.Address)).getText().toString().toUpperCase();

        if (deviceAddress.isEmpty()){
            Toast.makeText(this, "Введите MAC-адрес в формате AA:BB:CC:DD:11:22", Toast.LENGTH_LONG).show();
            return;
        }

        if (deviceAddress.length() != 17){
            Toast.makeText(this, "Неверно введен MAC-адрес устройства. Введите MAC-адрес в формате AA:BB:CC:DD:11:22", Toast.LENGTH_LONG).show();
            return;
        }

        preferences
                .edit()
                .putString(Preferences.DEVICE_ADDRESS, deviceAddress)
                .apply();

        if (!isServiceRunning(AmazfitService.class))
            stopService(new Intent(this, AmazfitService.class));

        startService(new Intent(this, AmazfitService.class));
        startActivity(new Intent(this, LoggerActivity.class));
        finish();
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE))
            if (serviceClass.getName().equals(service.service.getClassName()))
                return true;

        return false;
    }
}

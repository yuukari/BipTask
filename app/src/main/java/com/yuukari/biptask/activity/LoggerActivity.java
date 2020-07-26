package com.yuukari.biptask.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;

import com.yuukari.biptask.AmazfitService;
import com.yuukari.biptask.Preferences;
import com.yuukari.biptask.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LoggerActivity extends AppCompatActivity {
    private BroadcastReceiver broadcastReceiver;

    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logger);

        preferences = getSharedPreferences(Preferences.NAME, Context.MODE_PRIVATE);

        ((TextView)findViewById(R.id.textDeviceAddress)).setText("Связь с часами установлена. Текущий MAC-адрес устройства: " + preferences.getString(Preferences.DEVICE_ADDRESS, null));
        ((TextView)findViewById(R.id.textLogs)).setMovementMethod(new ScrollingMovementMethod());

        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                Date date = new Date();
                String dateString = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(date);

                switch (action){
                    case AmazfitService.DEVICE_EVENT_HANDLE:
                        String value = intent.getStringExtra("value");
                        ((TextView)findViewById(R.id.textLogs)).append("[" + dateString + "] " + value + "\r\n");
                        break;
                    case AmazfitService.DEVICE_CONNECTED:
                        ((TextView)findViewById(R.id.textLogs)).append("[" + dateString + "] Часы подключены\r\n");
                        break;
                    case AmazfitService.DEVICE_DISCONNECTED:
                        ((TextView)findViewById(R.id.textLogs)).append("[" + dateString + "] Потеряна связь с часами\r\n");
                        break;
                }
            }
        };

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(AmazfitService.DEVICE_EVENT_HANDLE);
        intentFilter.addAction(AmazfitService.DEVICE_CONNECTED);
        intentFilter.addAction(AmazfitService.DEVICE_DISCONNECTED);

        registerReceiver(broadcastReceiver, intentFilter);

        MobileAds.initialize(this, new OnInitializationCompleteListener() {
            @Override
            public void onInitializationComplete(InitializationStatus initializationStatus) {
            }
        });

        //AdView adView = findViewById(R.id.AdView);
        //AdRequest adRequest = new AdRequest.Builder().build();
        //adView.loadAd(adRequest);
    }

    public void disconnectClick(View view) {
        preferences
                .edit()
                .remove(Preferences.DEVICE_ADDRESS)
                .apply();

        stopService(new Intent(this, AmazfitService.class));
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(broadcastReceiver);
        super.onDestroy();
    }
}
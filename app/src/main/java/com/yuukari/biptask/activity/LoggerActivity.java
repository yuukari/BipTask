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
import android.widget.CheckBox;
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
import java.util.ArrayList;
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

                boolean hex = ((CheckBox) findViewById(R.id.checkBoxHex)).isChecked();

                switch (action){
                    case AmazfitService.DEVICE_EVENT_HANDLE:
                        String messageType = intent.getStringExtra("type");

                        if (messageType.equals(AmazfitService.BIPTASK_MESSAGE_BASIC)) {
                            Integer value = Integer.valueOf(intent.getStringExtra("value"));
                            ((TextView) findViewById(R.id.textLogs)).append("[" + dateString + "] [Watch] Получен байт: " + (hex ? "0x" : "") + Integer.toString(value, hex ? 16 : 10).toUpperCase() + "\r\n");
                        } else if (messageType.equals(AmazfitService.BIPTASK_MESSAGE_BUTTON)) {
                            String count = intent.getStringExtra("value");
                            ((TextView) findViewById(R.id.textLogs)).append("[" + dateString + "] [Watch] Кнопка назад нажата " + count + " " + (count.equals("2") || count.equals("3") || count.equals("4") ? "раза" : "раз") + "\r\n");
                        } else if (messageType.equals(AmazfitService.BIPTASK_MESSAGE_BYTE)) {
                            Integer value = Integer.valueOf(intent.getStringExtra("value"));
                            String appId = intent.getStringExtra("applicationId");

                            ((TextView) findViewById(R.id.textLogs)).append("[" + dateString + "] [App " + appId + "] Получен байт: " + (hex ? "0x" : "") + Integer.toString(value, hex ? 16 : 10).toUpperCase() + "\r\n");
                        } else if (messageType.equals(AmazfitService.BIPTASK_MESSAGE_BYTES)) {
                            ArrayList<String> bytes = intent.getStringArrayListExtra("value");
                            String appId = intent.getStringExtra("applicationId");
                            String values = "";

                            for (int i = 0; i < bytes.size(); i++)
                                if (i + 1 == bytes.size())
                                    values += (hex ? "0x" : "") + Integer.toString(Integer.valueOf(bytes.get(i)), hex ? 16 : 10).toUpperCase();
                                else
                                    values += (hex ? "0x" : "") + Integer.toString(Integer.valueOf(bytes.get(i)), hex ? 16 : 10).toUpperCase() + ", ";

                            ((TextView) findViewById(R.id.textLogs)).append("[" + dateString + "] [App " + appId + "] Получен массив байт: [" + values + "]\r\n");
                        }
                        break;
                    case AmazfitService.DEVICE_CONNECTED:
                        ((TextView)findViewById(R.id.textLogs)).append("[" + dateString + "] [Watch] Часы подключены\r\n");
                        break;
                    case AmazfitService.DEVICE_DISCONNECTED:
                        ((TextView)findViewById(R.id.textLogs)).append("[" + dateString + "] [Watch] Потеряна связь с часами\r\n");
                        break;
                }
            }
        };

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(AmazfitService.DEVICE_EVENT_HANDLE);
        intentFilter.addAction(AmazfitService.DEVICE_CONNECTED);
        intentFilter.addAction(AmazfitService.DEVICE_DISCONNECTED);

        registerReceiver(broadcastReceiver, intentFilter);
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

    public void clearLogClick(View view) {
        ((TextView)findViewById(R.id.textLogs)).setText("");
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(broadcastReceiver);
        super.onDestroy();
    }
}
package com.yuukari.biptask;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.Notification.Builder;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.yuukari.biptask.activity.MainActivity;
import com.yuukari.biptask.tasker.Intents;
import com.yuukari.biptask.tasker.activity.TaskerEventActivity;

import java.util.UUID;

import tasker.TaskerPlugin;

public class AmazfitService extends Service {
    public static final String DEVICE_EVENT_HANDLE = "com.example.akirahome.DEVICE_EVENT_HANDLE";
    public static final String DEVICE_CONNECTED = "com.example.akirahome.DEVICE_CONNECTED";
    public static final String DEVICE_DISCONNECTED = "com.example.akirahome.DEVICE_DISCONNECTED";

    private Handler connectionWaitHandler = new Handler();

    private BluetoothAdapter bluetoothAdapter;
    private String deviceAddress;

    private SharedPreferences preferences;

    @Override
    public void onCreate() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        preferences = getSharedPreferences(Preferences.NAME, Context.MODE_PRIVATE);
        createBackgroundNotification();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("BLE", "Amazfit service started");

        if (preferences.contains(Preferences.DEVICE_ADDRESS)) {
            Log.i("BLE", "Device MAC found in preferences, trying to connect");
            deviceConnect();
        }

        return START_STICKY;
    }

    private void deviceConnect(){
        deviceAddress = preferences.getString(Preferences.DEVICE_ADDRESS, null);

        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);
        device.createBond();

        if (device.getBondState() == 12) {
            device.connectGatt(this, true, onGattCallback);
        } else {
            Log.i("BLE", "Unable to create boundary with device, running awaiting handler");

            connectionWaitHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    deviceConnect();
                }
            }, 10000);
        }
    }

    private BluetoothGattCallback onGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState){
            switch (newState){
                case BluetoothGatt.STATE_CONNECTING:
                    Log.i("BLE", "Device state changed to STATE_CONNECTING");
                    break;
                case BluetoothGatt.STATE_CONNECTED:
                    Log.i("BLE", "Device state changed to STATE_CONNECTED, try to discover services");
                    sendBroadcast(new Intent(DEVICE_CONNECTED));
                    gatt.discoverServices();
                    break;
                case BluetoothGatt.STATE_DISCONNECTING:
                    Log.i("BLE", "Device state changed to STATE_DISCONNECTING");
                    break;
                case BluetoothGatt.STATE_DISCONNECTED:
                    Log.i("BLE", "Device disconnected, starting background handler for awaiting connection");
                    sendBroadcast(new Intent(DEVICE_DISCONNECTED));

                    connectionWaitHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            deviceConnect();
                        }
                    }, 10000);
                    break;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status){
            for (BluetoothGattService service : gatt.getServices()) {
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString("00000010-0000-3512-2118-0009af100700"));

                if (characteristic != null){
                    Log.i("BLE CH", "Found back button characteristic " + characteristic.getUuid());

                    for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors()) {
                        Log.i("BLE DSC", "Enabling notifications on descriptor " + descriptor.getUuid());

                        gatt.setCharacteristicNotification(characteristic, true);

                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(descriptor);
                    }
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic){
            String value = String.valueOf(characteristic.getValue()[0]);

            Log.i("BLE CH", "Characteristic " + characteristic.getUuid() + " changed value to " + value);

            Intent intent = new Intent(DEVICE_EVENT_HANDLE);
            intent.putExtra("value", value);
            sendBroadcast(intent);

            Bundle taskerBundle = new Bundle();
            taskerBundle.putString(Intents.EXTRA_MESSAGE_DATA, value);

            Intent taskerIntent = new Intent(Intents.ACTION_REQUEST_QUERY).putExtra(Intents.EXTRA_ACTIVITY, TaskerEventActivity.class.getName());

            int taskerMessageID = TaskerPlugin.Event.addPassThroughMessageID(taskerIntent);
            TaskerPlugin.Event.addPassThroughData(taskerIntent, taskerBundle);

            sendBroadcast(taskerIntent);
        }
    };

    private void createBackgroundNotification(){
        PendingIntent activityIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);
        NotificationManager manager = (NotificationManager) getApplicationContext().getSystemService(NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= 26) {
            @SuppressLint("WrongConstant")
            NotificationChannel notificationChannel = new NotificationChannel("biptask", "BipTask background service", 1);

            notificationChannel.setDescription("BipTask background service notification");
            notificationChannel.setVibrationPattern(null);
            notificationChannel.enableVibration(true);
            notificationChannel.enableLights(false);
            notificationChannel.setLightColor(0);
            notificationChannel.setSound(null, null);

            NotificationManager notificationManager = (NotificationManager) getSystemService(NotificationManager.class);
            if (!notificationManager.getNotificationChannels().isEmpty()) {
                notificationManager.deleteNotificationChannel("biptask");
            }
            notificationManager.createNotificationChannel(notificationChannel);

            Builder builder = new Builder(this, "biptask");
            Notification notification = builder.setContentTitle("BipTask background service notification. You can hide this").setContentIntent(activityIntent).build();
            startForeground(1169, notification);
        } else {
            Builder builder = new Builder(this);
            @SuppressLint("WrongConstant")
            Notification notification = builder.setDefaults(5).setContentTitle("BipTask background service notification. You can hide this").setVibrate(null).setContentIntent(activityIntent).setPriority(-2).build();
            startForeground(1169, notification);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopSelf();
        stopForeground(true);

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = (NotificationManager) getSystemService(NotificationManager.class);
            manager.cancelAll();
        } else {
            NotificationManager manager = (NotificationManager) getApplicationContext().getSystemService(NOTIFICATION_SERVICE);
            manager.cancelAll();
        }

        Log.i("BLE", "Service destroyed");
    }
}
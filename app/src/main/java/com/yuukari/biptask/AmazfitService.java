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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import tasker.TaskerPlugin;

public class AmazfitService extends Service {
    public static final String DEVICE_EVENT_HANDLE = "com.example.akirahome.DEVICE_EVENT_HANDLE";
    public static final String DEVICE_CONNECTED = "com.example.akirahome.DEVICE_CONNECTED";
    public static final String DEVICE_DISCONNECTED = "com.example.akirahome.DEVICE_DISCONNECTED";

    public static final String BIPTASK_MESSAGE_BASIC = "BIPTASK_MESSAGE_BASIC";
    public static final String BIPTASK_MESSAGE_BUTTON = "BIPTASK_MESSAGE_BUTTON";
    public static final String BIPTASK_MESSAGE_BYTE = "BIPTASK_MESSAGE_BYTE";
    public static final String BIPTASK_MESSAGE_BYTES = "BIPTASK_MESSAGE_BYTES";
    public static final String BIPTASK_MESSAGE_TEXT = "BIPTASK_MESSAGE_TEXT";

    private Handler connectionWaitHandler = new Handler();

    private BluetoothAdapter bluetoothAdapter;
    private String deviceAddress;

    private SharedPreferences preferences;

    private String messageType = null;
    private boolean isMessageReceiving = false;
    private boolean firstByteReceiving = false;
    private int byteBuffer;
    private ArrayList<Integer> bytesReceived = new ArrayList<Integer>();
    private ArrayList<Integer> appId = new ArrayList<Integer>();

    private Handler buttonHandler = new Handler();
    private Integer buttonPressCount = 0;

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
                    gatt.disconnect();
                    gatt.close();

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
            Integer value = Integer.valueOf(characteristic.getValue()[0]);
            if (value < 0)
                value += 256;

            if (isMessageReceiving == true){
                if (messageType == null){
                    messageType = (value == 0xE0) ? BIPTASK_MESSAGE_BYTES : BIPTASK_MESSAGE_TEXT;
                    Log.i("BLE MSG", "Message has type " + messageType + ", receiving bytes");
                } else {
                    if (value == 0xDF){
                        Log.i("BLE MSG", "Received BipTask protocol stop byte");

                        Bundle taskerBundle = new Bundle();

                        if (bytesReceived.size() == 1){
                            Log.i("BLE MSG", "Received single byte, changed message type to BIPTASK_MESSAGE_BYTE");

                            Intent intent = new Intent(DEVICE_EVENT_HANDLE);
                            intent.putExtra("type", BIPTASK_MESSAGE_BYTE);
                            intent.putExtra("value", bytesReceived.get(0).toString());
                            intent.putExtra("applicationId", String.valueOf(appId.get(0) * appId.get(1) * appId.get(2)));
                            sendBroadcast(intent);

                            taskerBundle.putString(Intents.EXTRA_MESSAGE_TYPE, BIPTASK_MESSAGE_BYTE);
                            taskerBundle.putString(Intents.EXTRA_MESSAGE_DATA, bytesReceived.get(0).toString());
                            taskerBundle.putString(Intents.EXTRA_MESSAGE_APP_ID, String.valueOf(appId.get(0) * appId.get(1) * appId.get(2)));
                        } else {
                            ArrayList<String> bytesReceivedString = new ArrayList<String>();
                            for (int i = 0; i < bytesReceived.size(); i++)
                                bytesReceivedString.add(String.valueOf(bytesReceived.get(i)));

                            Intent intent = new Intent(DEVICE_EVENT_HANDLE);
                            intent.putExtra("type", messageType);
                            intent.putExtra("value", bytesReceivedString);
                            intent.putExtra("applicationId", String.valueOf(appId.get(0) * appId.get(1) * appId.get(2)));
                            sendBroadcast(intent);

                            taskerBundle.putString(Intents.EXTRA_MESSAGE_TYPE, messageType);
                            taskerBundle.putStringArrayList(Intents.EXTRA_MESSAGE_DATA, bytesReceivedString);
                            taskerBundle.putString(Intents.EXTRA_MESSAGE_APP_ID, String.valueOf(appId.get(0) * appId.get(1) * appId.get(2)));
                        }

                        Intent taskerIntent = new Intent(Intents.ACTION_REQUEST_QUERY).putExtra(Intents.EXTRA_ACTIVITY, TaskerEventActivity.class.getName());
                        int taskerMessageID = TaskerPlugin.Event.addPassThroughMessageID(taskerIntent);
                        TaskerPlugin.Event.addPassThroughData(taskerIntent, taskerBundle);
                        sendBroadcast(taskerIntent);

                        isMessageReceiving = false;
                        return;
                    }

                    if (firstByteReceiving){
                        byteBuffer = value - 0xE0;
                        Log.i("BLE MSG", "First byte received: " + byteBuffer);
                    } else {
                        int result = byteBuffer << 4;
                        result += value - 0xE0;

                        if (appId.size() < 3) {
                            Log.i("BLE MSG", "Second byte received: " + (value - 0xE0) + ", final result: " + result + ", writing to app id");
                            appId.add(result);
                        } else {
                            Log.i("BLE MSG", "Second byte received: " + (value - 0xE0) + ", final result: " + result + ", writing to received bytes array");
                            bytesReceived.add(result);
                        }
                    }

                    firstByteReceiving = !firstByteReceiving;
                }
            } else {
                if (value == 0xDE){
                    Log.i("BLE MSG", "Received BipTask protocol start byte");

                    messageType = null;
                    isMessageReceiving = true;
                    firstByteReceiving = true;
                    bytesReceived.clear();
                    appId.clear();
                } else {
                    if (value == 0x04){
                        buttonPressCount++;
                        Log.i("BLE MSG", "Back button pressed " + buttonPressCount + " times");

                        buttonHandler.removeCallbacks(handleButtonMessageSend);
                        buttonHandler.postDelayed(handleButtonMessageSend, 700);
                    } else {
                        Log.i("BLE MSG", "Received single byte: " + value);

                        Intent intent = new Intent(DEVICE_EVENT_HANDLE);
                        intent.putExtra("type", BIPTASK_MESSAGE_BASIC);
                        intent.putExtra("value", value.toString());
                        sendBroadcast(intent);

                        Bundle taskerBundle = new Bundle();
                        taskerBundle.putString(Intents.EXTRA_MESSAGE_TYPE, BIPTASK_MESSAGE_BASIC);
                        taskerBundle.putString(Intents.EXTRA_MESSAGE_DATA, value.toString());

                        Intent taskerIntent = new Intent(Intents.ACTION_REQUEST_QUERY).putExtra(Intents.EXTRA_ACTIVITY, TaskerEventActivity.class.getName());
                        int taskerMessageID = TaskerPlugin.Event.addPassThroughMessageID(taskerIntent);
                        TaskerPlugin.Event.addPassThroughData(taskerIntent, taskerBundle);
                        sendBroadcast(taskerIntent);
                    }
                }
            }
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

    private Runnable handleButtonMessageSend = new Runnable() {
        @Override
        public void run() {
            Intent intent = new Intent(DEVICE_EVENT_HANDLE);
            intent.putExtra("type", BIPTASK_MESSAGE_BUTTON);
            intent.putExtra("value", buttonPressCount.toString());
            sendBroadcast(intent);

            Bundle taskerBundle = new Bundle();
            taskerBundle.putString(Intents.EXTRA_MESSAGE_TYPE, BIPTASK_MESSAGE_BUTTON);
            taskerBundle.putString(Intents.EXTRA_MESSAGE_DATA, buttonPressCount.toString());

            Intent taskerIntent = new Intent(Intents.ACTION_REQUEST_QUERY).putExtra(Intents.EXTRA_ACTIVITY, TaskerEventActivity.class.getName());
            int taskerMessageID = TaskerPlugin.Event.addPassThroughMessageID(taskerIntent);
            TaskerPlugin.Event.addPassThroughData(taskerIntent, taskerBundle);
            sendBroadcast(taskerIntent);

            buttonPressCount = 0;
        }
    };

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
package com.yuukari.biptask.tasker.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.yuukari.biptask.AmazfitService;
import com.yuukari.biptask.tasker.Intents;

import java.util.ArrayList;

import tasker.TaskerPlugin;

public final class QueryReceiver extends BroadcastReceiver
{
    @Override
    public void onReceive(final Context context, final Intent intent)
    {
        int messageID = TaskerPlugin.Event.retrievePassThroughMessageID(intent);

        Bundle taskerBundle = TaskerPlugin.Event.retrievePassThroughData(intent);
        Bundle varsBundle = new Bundle();

        if (taskerBundle == null)
            return;

        String messageType = taskerBundle.getString(Intents.EXTRA_MESSAGE_TYPE);
        varsBundle.putString("%message_type", messageType);

        switch (messageType){
            case AmazfitService.BIPTASK_MESSAGE_BASIC:
                varsBundle.putString("%message_data", taskerBundle.getString(Intents.EXTRA_MESSAGE_DATA));
                TaskerPlugin.addVariableBundle(getResultExtras(true), varsBundle);
                break;
            case AmazfitService.BIPTASK_MESSAGE_BYTE:
                varsBundle.putString("%message_data", taskerBundle.getString(Intents.EXTRA_MESSAGE_DATA));
                varsBundle.putString("%message_app_id", taskerBundle.getString(Intents.EXTRA_MESSAGE_APP_ID));
                TaskerPlugin.addVariableBundle(getResultExtras(true), varsBundle);
                break;
            case AmazfitService.BIPTASK_MESSAGE_BYTES:
                ArrayList<String> bytesReceived = taskerBundle.getStringArrayList(Intents.EXTRA_MESSAGE_DATA);

                varsBundle.putStringArrayList("%message_data", bytesReceived);
                varsBundle.putInt("%message_length", bytesReceived.size());
                varsBundle.putString("%message_app_id", taskerBundle.getString(Intents.EXTRA_MESSAGE_APP_ID));
                TaskerPlugin.addVariableBundle(getResultExtras(true), varsBundle);
                break;
            case AmazfitService.BIPTASK_MESSAGE_BUTTON:
                varsBundle.putString("%button_count", taskerBundle.getString(Intents.EXTRA_MESSAGE_DATA));
                TaskerPlugin.addVariableBundle(getResultExtras(true), varsBundle);
                break;
        }

        setResultCode(Intents.RESULT_CONDITION_SATISFIED);
    }
}
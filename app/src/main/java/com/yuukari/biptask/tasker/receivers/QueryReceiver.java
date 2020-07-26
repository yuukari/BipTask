package com.yuukari.biptask.tasker.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.yuukari.biptask.tasker.Intents;

import tasker.TaskerPlugin;

public final class QueryReceiver extends BroadcastReceiver
{
    @Override
    public void onReceive(final Context context, final Intent intent)
    {
        int messageID = TaskerPlugin.Event.retrievePassThroughMessageID(intent);

        Bundle taskerBundle = TaskerPlugin.Event.retrievePassThroughData(intent);
        if (taskerBundle != null){
            String messageData = taskerBundle.getString(Intents.EXTRA_MESSAGE_DATA);

            Bundle varsBundle = new Bundle();
            varsBundle.putString("%message_data", messageData);

            TaskerPlugin.addVariableBundle(getResultExtras(true), varsBundle);

            setResultCode(Intents.RESULT_CONDITION_SATISFIED);
        }
    }
}
package com.yuukari.biptask.tasker.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.yuukari.biptask.R;
import com.yuukari.biptask.tasker.Intents;

public class TaskerEventActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tasker_event);

        /*
        String messageData = null;

        Bundle taskerBundle = getIntent().getBundleExtra(Intents.EXTRA_BUNDLE);
        if (taskerBundle != null){
            messageData = taskerBundle.getString(Intents.EXTRA_MESSAGE_DATA);
            ((TextView)findViewById(R.id.messageData)).setText(messageData);
        }
         */
        Toast.makeText(this, "Конфигурация BipTask сохранена", Toast.LENGTH_LONG).show();

        finish();
    }

    @Override
    public void finish() {
        Bundle taskerBundle = new Bundle();
        Intent resultIntent = new Intent();
        resultIntent.putExtra(Intents.EXTRA_BUNDLE, taskerBundle);
        resultIntent.putExtra(Intents.EXTRA_BLURB, "amazfitMessageReceived");

        setResult(RESULT_OK, resultIntent);

        super.finish();
    }
}
package com.android.media;

import android.content.Intent;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initListener();

        PathUtils.getInstance().initPath(MainActivity.this);
    }

    private void initListener() {
        findViewById(R.id.tv_1).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!MainService.isrun) {
                    Intent intent = new Intent(MainActivity.this, MainService.class);
                    startService(intent);
                }
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        MainService.getInstance().showWindow(1);
                    }
                }, 2000);

            }
        });

        findViewById(R.id.tv_2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (MainService.isrun) {
                    MainService.getInstance().closeCamera();

                    Intent intent = new Intent(MainActivity.this, MainService.class);
                    stopService(intent);
                }
            }
        });
        findViewById(R.id.tv_3).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (MainService.isrun) {
                    MainService.getInstance().showWindow(1);
                }

            }
        });

    }
}

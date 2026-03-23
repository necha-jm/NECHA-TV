package com.app.myapplication;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Handler;

public class NoInternetActivity extends AppCompatActivity {

    private Handler handler = new Handler();
    private Runnable internetChecker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_no_internet);

        startInternetCheck();
    }

    private void startInternetCheck() {

        internetChecker = new Runnable() {
            @Override
            public void run() {

                if (isInternetAvailable()) {

                    // Internet restored → restart SplashActivity
                    Intent intent = new Intent(NoInternetActivity.this, SplashActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();

                } else {
                    // Check again after 2 seconds
                    handler.postDelayed(this, 2000);
                }
            }
        };

        handler.postDelayed(internetChecker, 2000);
    }

    private boolean isInternetAvailable() {

        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm == null) return false;

        NetworkCapabilities nc =
                cm.getNetworkCapabilities(cm.getActiveNetwork());

        return nc != null &&
                nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (handler != null && internetChecker != null) {
            handler.removeCallbacks(internetChecker);
        }
    }
}
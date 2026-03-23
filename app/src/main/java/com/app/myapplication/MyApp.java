package com.app.myapplication;


import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;

import androidx.annotation.NonNull;


public class MyApp extends Application {

    private boolean isOfflineDialogShown = false;

    @Override
    public void onCreate() {
        super.onCreate();
        registerNetworkCallback();
    }

    private void registerNetworkCallback() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        cm.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                // Internet back
                if (isOfflineDialogShown) {
                    // Close NoInternetActivity
                    Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    isOfflineDialogShown = false;
                }
            }

            @Override
            public void onLost(Network network) {
                // Internet lost
                if (!isOfflineDialogShown) {
                    Intent intent = new Intent(getApplicationContext(), NoInternetActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    isOfflineDialogShown = true;
                }
            }
        });
    }
}

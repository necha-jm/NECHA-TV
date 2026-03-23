package com.app.myapplication;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;

public class SplashActivity extends AppCompatActivity {

    private LottieAnimationView lottieAnimationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash);

        lottieAnimationView = findViewById(R.id.lottieAnimation);

        // Wait 6 seconds for splash animation
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isInternetAvailable()) {

                // Internet OK → open intended screen
                Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                startActivity(intent);

            } else {

                // No internet → open blocking screen
                Intent intent = new Intent(SplashActivity.this, NoInternetActivity.class);
                startActivity(intent);
            }
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

        }, 6000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (lottieAnimationView != null) {
            lottieAnimationView.cancelAnimation();
        }
    }

    // Internet checker
    public boolean isInternetAvailable() {

        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm == null) return false;

        NetworkCapabilities nc =
                cm.getNetworkCapabilities(cm.getActiveNetwork());

        return nc != null &&
                nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
}
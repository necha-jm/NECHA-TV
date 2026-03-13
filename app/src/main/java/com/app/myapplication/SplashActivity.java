package com.app.myapplication;


import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;
import com.airbnb.lottie.LottieAnimationView;

public class SplashActivity extends AppCompatActivity {

    private LottieAnimationView lottieAnimationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Initialize Lottie view
        lottieAnimationView = findViewById(R.id.lottieAnimation);

        // Optional: Programmatically set animation (if not set in XML)
        // lottieAnimationView.setAnimation(R.raw.splash_anim);

        // Optional: Add animation listener
        lottieAnimationView.addAnimatorUpdateListener(animation -> {
            // You can track animation progress here
            float progress = animation.getAnimatedFraction();
            // Example: Fade in text based on progress
        });

        // Navigate after 3 seconds
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, PlayerActivity.class);
            startActivity(intent);
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }, 7000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up animation to prevent memory leaks
        if (lottieAnimationView != null) {
            lottieAnimationView.cancelAnimation();
        }
    }
}
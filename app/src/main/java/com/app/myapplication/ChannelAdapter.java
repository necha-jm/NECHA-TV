package com.app.myapplication;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.List;

public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.ViewHolder> {

    public interface OnChannelClick {
        void onClick(Channel channel);
    }

    private List<Channel> originalList;
    private List<Channel> filteredList;
    private OnChannelClick listener;

    public ChannelAdapter(List<Channel> list, OnChannelClick listener) {
        this.originalList = list;
        this.filteredList = new ArrayList<>(list);
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate your card layout instead of creating a simple TextView
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_channel, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Channel channel = filteredList.get(position);

        // Get channel name and handle long names
        String channelName = channel.getName();
        if (channelName.length() > 35) {
            channelName = channelName.substring(0, 32) + "...";
        }
        holder.channelName.setText(channelName);

        // Set channel stats based on URL or name
        String url = channel.getUrl();
        if (url != null) {
            if (url.contains("music") || channel.getName().toLowerCase().contains("music")) {
                holder.channelStats.setText("🎵 Music");
            } else if (url.contains("news") || channel.getName().toLowerCase().contains("news")) {
                holder.channelStats.setText("📰 News");
            } else if (channel.getName().toLowerCase().contains("sport")) {
                holder.channelStats.setText("⚽ Sports");
            } else {
                holder.channelStats.setText("📺 Live");
            }
        } else {
            holder.channelStats.setText("📺 Live");
        }

        // Set channel avatar based on type
        if (channel.getName().toLowerCase().contains("music")) {
            holder.channelAvatar.setImageResource(R.drawable.ic_music_placeholder);
        } else if (channel.getName().toLowerCase().contains("news")) {
            holder.channelAvatar.setImageResource(R.drawable.ic_news_placeholder);
        } else {
            holder.channelAvatar.setImageResource(R.drawable.ic_channel_placeholder);
        }

        // Card click listener
        holder.cardView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(channel);
            }
        });

        // Play button click listener
        holder.playButton.setOnClickListener(v -> {
            // Animate button
            v.animate()
                    .scaleX(0.9f)
                    .scaleY(0.9f)
                    .setDuration(20)
                    .withEndAction(() -> {
                        v.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(50)
                                .start();
                    })
                    .start();

            if (listener != null) {
                listener.onClick(channel);
            }
        });

        // Apply staggered animation for cards
        holder.cardView.setAlpha(0f);
        holder.cardView.setTranslationY(50f);
        holder.cardView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(50)
                .setStartDelay(position)
                .start();
    }

    @Override
    public int getItemCount() {
        return filteredList.size();
    }

    public void filter(String text) {
        filteredList.clear();
        if (text.isEmpty()) {
            filteredList.addAll(originalList);
        } else {
            text = text.toLowerCase();
            for (Channel c : originalList) {
                if (c.getName().toLowerCase().contains(text)) {
                    filteredList.add(c);
                }
            }
        }
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardView;
        ShapeableImageView channelAvatar;
        TextView channelName;
        TextView channelStats;
        Button playButton;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            channelAvatar = itemView.findViewById(R.id.channelAvatar);
            channelName = itemView.findViewById(R.id.channelName);
            channelStats = itemView.findViewById(R.id.channelStats);
            playButton = itemView.findViewById(R.id.playButton);
        }
    }
}
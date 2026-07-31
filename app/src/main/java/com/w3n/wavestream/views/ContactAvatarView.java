package com.w3n.wavestream.views;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;

import androidx.appcompat.widget.AppCompatTextView;

public class ContactAvatarView extends AppCompatTextView {
    public ContactAvatarView(Context context, String contactName) {
        super(context);
        setText(String.valueOf(contactName.charAt(0)));
        setGravity(Gravity.CENTER);
        setTextColor(Color.WHITE);
        setTextSize(18);
        setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        setBackground(createProfileBackground(contactName));
    }

    private GradientDrawable createProfileBackground(String contactName) {
        int[] colors = {
                Color.rgb(29, 103, 210),
                Color.rgb(21, 128, 112),
                Color.rgb(178, 88, 27),
                Color.rgb(140, 82, 170),
                Color.rgb(201, 63, 83),
                Color.rgb(67, 111, 86)
        };
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(colors[Math.abs(contactName.hashCode()) % colors.length]);
        return drawable;
    }
}

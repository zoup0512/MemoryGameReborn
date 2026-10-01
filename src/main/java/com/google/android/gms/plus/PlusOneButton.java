package com.google.android.gms.plus;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/**
 * Local replacement for the Google+ OneButton view. The original "rate us"
 * dialog showed a +1 button backed by Google Play Services; the offline
 * build renders an inert view instead, keeping the dialog layout intact.
 * Only the API surface used by the game remains.
 */
public class PlusOneButton extends FrameLayout {
    public interface OnPlusOneClickListener {
        void onPlusOneClick(Intent intent);
    }

    public PlusOneButton(Context context) {
        super(context);
    }

    public PlusOneButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void initialize(String url, OnPlusOneClickListener listener) {
        setVisibility(GONE);
    }
}

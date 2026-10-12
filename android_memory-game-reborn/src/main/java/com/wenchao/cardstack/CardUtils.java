package com.wenchao.cardstack;

import android.util.Log;
import android.view.View;
import android.widget.RelativeLayout.LayoutParams;

public class CardUtils {
    static final int DIRECTION_BOTTOM_LEFT = 2;
    static final int DIRECTION_BOTTOM_RIGHT = 3;
    static final int DIRECTION_TOP_LEFT = 0;
    static final int DIRECTION_TOP_RIGHT = 1;

    public static void scale(View v, int pixel) {
        LayoutParams params = (LayoutParams) v.getLayoutParams();
        params.leftMargin -= pixel;
        params.rightMargin -= pixel;
        params.topMargin -= pixel;
        params.bottomMargin -= pixel;
        v.setLayoutParams(params);
    }

    public static LayoutParams getMoveParams(View v, int upDown, int leftRight) {
        LayoutParams params = cloneParams((LayoutParams) v.getLayoutParams());
        params.leftMargin += leftRight;
        params.rightMargin -= leftRight;
        params.topMargin -= upDown;
        params.bottomMargin += upDown;
        return params;
    }

    public static void move(View v, int upDown, int leftRight) {
        v.setLayoutParams(getMoveParams(v, upDown, leftRight));
    }

    public static LayoutParams scaleFrom(View v, LayoutParams params, int pixel) {
        Log.d("pixel", "onScroll: " + pixel);
        params = cloneParams(params);
        params.leftMargin -= pixel;
        params.rightMargin -= pixel;
        params.topMargin -= pixel;
        params.bottomMargin -= pixel;
        Log.d("pixel", "onScroll: " + pixel);
        v.setLayoutParams(params);
        return params;
    }

    public static LayoutParams moveFrom(View v, LayoutParams params, int leftRight, int upDown) {
        params = cloneParams(params);
        params.leftMargin += leftRight;
        params.rightMargin -= leftRight;
        params.topMargin -= upDown;
        params.bottomMargin += upDown;
        v.setLayoutParams(params);
        return params;
    }

    public static LayoutParams cloneParams(LayoutParams params) {
        LayoutParams copy = new LayoutParams(params.width, params.height);
        copy.leftMargin = params.leftMargin;
        copy.topMargin = params.topMargin;
        copy.rightMargin = params.rightMargin;
        copy.bottomMargin = params.bottomMargin;
        int[] rules = params.getRules();
        for (int i = 0; i < rules.length; i++) {
            copy.addRule(i, rules[i]);
        }
        return copy;
    }

    public static float distance(float x1, float y1, float x2, float y2) {
        return (float) Math.sqrt((double) (((x1 - x2) * (x1 - x2)) + ((y1 - y2) * (y1 - y2))));
    }

    public static int direction(float x1, float y1, float x2, float y2) {
        if (x2 > x1) {
            if (y2 > y1) {
                return 3;
            }
            return 1;
        } else if (y2 > y1) {
            return 2;
        } else {
            return 0;
        }
    }
}

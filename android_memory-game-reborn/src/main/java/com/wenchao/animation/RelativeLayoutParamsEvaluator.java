package com.wenchao.animation;

import android.animation.TypeEvaluator;
import android.widget.RelativeLayout.LayoutParams;
import com.wenchao.cardstack.CardUtils;

public class RelativeLayoutParamsEvaluator implements TypeEvaluator<LayoutParams> {
    public LayoutParams evaluate(float fraction, LayoutParams start, LayoutParams end) {
        LayoutParams result = CardUtils.cloneParams(start);
        result.leftMargin = (int) (((float) result.leftMargin) + (((float) (end.leftMargin - start.leftMargin)) * fraction));
        result.rightMargin = (int) (((float) result.rightMargin) + (((float) (end.rightMargin - start.rightMargin)) * fraction));
        result.topMargin = (int) (((float) result.topMargin) + (((float) (end.topMargin - start.topMargin)) * fraction));
        result.bottomMargin = (int) (((float) result.bottomMargin) + (((float) (end.bottomMargin - start.bottomMargin)) * fraction));
        return result;
    }
}

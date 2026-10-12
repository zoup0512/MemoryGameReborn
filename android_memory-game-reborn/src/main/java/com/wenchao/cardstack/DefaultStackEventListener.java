package com.wenchao.cardstack;

import com.wenchao.cardstack.CardStack.CardEventListener;

public class DefaultStackEventListener implements CardEventListener {
    private float mThreshold;

    public DefaultStackEventListener(int i) {
        this.mThreshold = (float) i;
    }

    public boolean swipeEnd(int section, float distance) {
        return distance > this.mThreshold;
    }

    public boolean swipeStart(int section, float distance) {
        return false;
    }

    public boolean swipeContinue(int section, float distanceX, float distanceY) {
        return false;
    }

    public void discarded(int mIndex, int direction) {
    }

    public void topCardTapped() {
    }
}

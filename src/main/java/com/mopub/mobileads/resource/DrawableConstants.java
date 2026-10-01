package com.mopub.mobileads.resource;

/**
 * Local constant-only replacement for the MoPub drawable constants that the
 * decompiled game logic reuses for its own UI drawing (stroke widths, angles,
 * sizes). Values match the original MoPub 4.x SDK constants; no ad
 * functionality remains.
 */
public final class DrawableConstants {
    private DrawableConstants() {
    }

    public static class CtaButton {
        public static final int WIDTH_DIPS = 150;
        public static final int HEIGHT_DIPS = 44;
        public static final int TEXT_SIZE_SP = 20;
        public static final int OUTLINE_STROKE_WIDTH_DIPS = 2;
        public static final int CORNER_RADIUS_DIPS = 6;
    }

    public static class CloseButton {
        public static final int STROKE_WIDTH = 3;
        public static final int TEXT_SIZE_SP = 20;
        public static final int RADIUS_DIPS = 14;
        public static final int OFFSET_DIPS = 8;
    }

    public static class RadialCountdown {
        public static final int START_ANGLE = -90;
        public static final int SWEEP_ANGLE = 360;
        public static final int LOOP_DURATION = 1000;
    }
}

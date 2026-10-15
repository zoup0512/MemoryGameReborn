package com.cube.memorygames.api.online;

import android.content.Context;
import android.widget.ImageView;
import com.memory.brain.training.games.R;

/** Explicit resource references survive R8/resource shrinking; no reflection, emoji, URI or upload. */
public final class AvatarCatalog {
    private static final int[] DRAWABLES={R.drawable.profile_avatar_1,R.drawable.profile_avatar_2,
            R.drawable.profile_avatar_3,R.drawable.profile_avatar_4,R.drawable.profile_avatar_5,
            R.drawable.profile_avatar_6,R.drawable.profile_avatar_7,R.drawable.profile_avatar_8,
            R.drawable.profile_avatar_9,R.drawable.profile_avatar_10,R.drawable.profile_avatar_11,
            R.drawable.profile_avatar_12};
    private AvatarCatalog() {}
    public static String value(int index) { return "avatar_"+(index+1); }
    public static String label(Context c,int index) { return c.getResources().getStringArray(R.array.profile_avatar_names)[index]; }
    public static void apply(Context c,ImageView image,String value) {
        int index=ProfileRules.avatarIndex(value);
        image.clearColorFilter(); image.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        image.setImageResource(DRAWABLES[index]); image.setContentDescription(label(c,index));
    }
}

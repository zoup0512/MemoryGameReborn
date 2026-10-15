package com.cube.memorygames.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.support.v7.widget.RecyclerView.Adapter;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import butterknife.BindView;
import butterknife.ButterKnife;
import com.cube.memorygames.MainMenuActivity;
import com.cube.memorygames.MemoryApplicationModel;
import com.cube.memorygames.TrophiesActivity;
import com.cube.memorygames.api.local.LocalDataManager;
import com.cube.memorygames.api.local.model.LocalUser;
import com.memory.brain.training.games.R;
import java.util.ArrayList;
import java.util.List;

/**
 * Local "Menu" tab. The Google Play purchase entries (remove ads / pro /
 * unlock all games / coins), the online profile editing row and the top
 * ranks entry of the original app were removed; trophies stay as a local
 * feature.
 */
public class MenuAdapter extends Adapter<ViewHolder> {
    public static final String PREF_SKU_DETAILS = "skuDetailsInfo";
    public static final int PRO_COINS_AMOUNT = 1000;
    public static final String SKU_PRO_VERSION = "pro_version";
    public static final String SKU_PRO_VERSION_DISCOUNT = "pro_version05";
    public static final String SKU_REMOVE_ADS = "remove_ads";
    public static final String SKU_UNLIMITEDONLINE = "unlimitedonline";
    public static final String SKU_UNLOCK_GAMES = "unlock_games";
    private static final int TYPE_DIVIDER = 2;
    private static final int TYPE_ITEM = 0;
    private static final int TYPE_USER = 1;
    private List<Object> items;
    private MainMenuActivity mainMenuActivity;
    private Typeface typeface;

    public class DividerViewHolder extends ViewHolder {
        @BindView(R.id.divider)
        View divider;

        public DividerViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind((Object) this, itemView);
        }
    }

    private static class MenuItem {
        public int iconResId;
        public OnClickListener onClickListener;
        public int textResId;
        public boolean tint;

        public MenuItem(int iconResId, int textResId, OnClickListener onClickListener) {
            this(iconResId, textResId, onClickListener, true);
        }

        public MenuItem(int iconResId, int textResId, OnClickListener onClickListener, boolean tint) {
            this.iconResId = iconResId;
            this.textResId = textResId;
            this.onClickListener = onClickListener;
            this.tint = tint;
        }
    }

    public class MenuItemViewHolder extends ViewHolder {
        @BindView(R.id.icon)
        ImageView icon;
        @BindView(R.id.name)
        TextView name;

        public MenuItemViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind((Object) this, itemView);
        }
    }

    public class UserViewHolder extends ViewHolder {
        @BindView(R.id.profilePicture)
        ImageView profilePicture;
        @BindView(R.id.userName)
        TextView userName;

        public UserViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind((Object) this, itemView);
        }
    }

    public MenuAdapter(MainMenuActivity mainMenuActivity) {
        this.mainMenuActivity = mainMenuActivity;
        fillMenuItems();
        this.typeface = Typeface.createFromAsset(mainMenuActivity.getAssets(), "Roboto-Light.ttf");
        com.cube.memorygames.api.online.AccountManager.get(mainMenuActivity).cachedProfile(
                new com.cube.memorygames.api.online.AccountManager.Callback<com.cube.memorygames.api.online.AccountManager.UserProfile>() {
            public void onSuccess(com.cube.memorygames.api.online.AccountManager.UserProfile profile) { notifyDataSetChanged(); }
            public void onError(String message) { /* Do not replace a damaged/foreign identity with a guest. */ }
        });
    }
    private void openProfile() {
        mainMenuActivity.startActivity(new Intent(mainMenuActivity,com.cube.memorygames.api.online.AccountActivity.class));
    }

    public void fillMenuItems() {
        LocalUser localUser = MemoryApplicationModel.getInstance().getLocalDataManager().getLocalUser();
        this.items = new ArrayList();
        this.items.add(localUser);
        this.items.add(new MenuItem(R.drawable.ic_user_placeholder,R.string.profile_my,view -> openProfile()));
        this.items.add(new MenuItem(R.drawable.ic_reward, R.string.your_trophies, new OnClickListener() {
            public void onClick(View view) {
                MenuAdapter.this.mainMenuActivity.startActivity(new Intent(MenuAdapter.this.mainMenuActivity, TrophiesActivity.class));
            }
        }));
        this.items.add(new MenuItem(android.R.drawable.ic_menu_upload, R.string.diagnostics_upload, new OnClickListener() {
            public void onClick(View view) {
                com.cube.memorygames.diagnostics.DiagnosticUploadUi.show(MenuAdapter.this.mainMenuActivity);
            }
        }));
        this.items.add(Integer.valueOf(0));
        this.items.add(new MenuItem(R.drawable.ic_reward, R.string.rank, new OnClickListener() {
            public void onClick(View view) {
                MenuAdapter.this.mainMenuActivity.topClick();
            }
        }));
    }

    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        if (viewType == 0) {
            MenuItemViewHolder menuItemViewHolder = new MenuItemViewHolder(layoutInflater.inflate(R.layout.item_menu, parent, false));
            menuItemViewHolder.name.setTypeface(this.typeface);
            return menuItemViewHolder;
        } else if (viewType == 1) {
            ViewHolder userViewHolder = new UserViewHolder(layoutInflater.inflate(R.layout.item_menu_user, parent, false));
            ((UserViewHolder) userViewHolder).userName.setTypeface(this.typeface);
            return userViewHolder;
        } else if (viewType == 2) {
            return new DividerViewHolder(layoutInflater.inflate(R.layout.item_menu_divider, parent, false));
        } else {
            throw new RuntimeException("There is no type that matches the type " + viewType + " + make sure your using types correctly");
        }
    }

    public void onBindViewHolder(ViewHolder holder, int position) {
        if (getItemViewType(position) == 0) {
            MenuItemViewHolder menuItemViewHolder = (MenuItemViewHolder) holder;
            MenuItem menuItem = (MenuItem) this.items.get(position);
            menuItemViewHolder.name.setText(menuItem.textResId);
            menuItemViewHolder.icon.setImageResource(menuItem.iconResId);
            menuItemViewHolder.itemView.setOnClickListener(menuItem.onClickListener);
            if (menuItem.tint) {
                menuItemViewHolder.icon.setColorFilter(-16777216);
            } else {
                menuItemViewHolder.icon.clearColorFilter();
            }
        } else if (getItemViewType(position) == 1) {
            UserViewHolder userViewHolder = (UserViewHolder) holder;
            com.cube.memorygames.api.online.AccountManager manager=com.cube.memorygames.api.online.AccountManager.get(mainMenuActivity);
            com.cube.memorygames.api.online.AccountManager.UserProfile profile=manager.profile();
            com.cube.memorygames.api.online.OnlineMessages.AuthOk online=com.cube.memorygames.api.online.OnlineSession.get().getLastProfile();
            String name=profile!=null ? profile.nickname : online!=null ? online.nickname
                    : com.cube.memorygames.api.online.LocalProfile.nickname(mainMenuActivity);
            String avatar=profile!=null ? profile.avatarValue : online!=null ? online.avatarValue
                    : com.cube.memorygames.api.online.LocalProfile.avatar(mainMenuActivity);
            userViewHolder.userName.setText(name);
            com.cube.memorygames.api.online.AvatarCatalog.apply(mainMenuActivity,userViewHolder.profilePicture,avatar);
            userViewHolder.itemView.setContentDescription(mainMenuActivity.getString(R.string.profile_my));
            userViewHolder.itemView.setOnClickListener(v -> openProfile());
            userViewHolder.profilePicture.setOnClickListener(v -> openProfile());
        }
    }

    public int getItemViewType(int position) {
        Object item = this.items.get(position);
        if (item instanceof MenuItem) {
            return 0;
        }
        if (item instanceof LocalUser) {
            return 1;
        }
        return 2;
    }

    public int getItemCount() {
        return this.items.size();
    }
}

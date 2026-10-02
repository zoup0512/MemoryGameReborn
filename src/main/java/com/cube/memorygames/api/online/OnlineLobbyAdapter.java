package com.cube.memorygames.api.online;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cube.memorygames.MainMenuActivity;
import com.cube.memorygames.api.local.LocalDataManager;
import com.cube.memorygames.MemoryApplicationModel;
import com.memory.brain.training.games.R;

/**
 * RecyclerView adapter rendering the Online PK lobby inside the main menu's
 * Online tab: player card (nickname / ELO / rank / record), play button and
 * the original tutorial strings. Views are built in code (see
 * OnlineMatchActivity for the rationale).
 */
public class OnlineLobbyAdapter extends android.support.v7.widget.RecyclerView.Adapter {

    private static final int VIEW_TYPE_PROFILE = 0;
    private static final int VIEW_TYPE_TUTORIAL = 1;

    private final Context context;
    private final Typeface fontLight;
    private OnlineMessages.AuthOk profile;

    public OnlineLobbyAdapter(Context context) {
        this.context = context;
        this.fontLight = Typeface.createFromAsset(context.getAssets(), "Roboto-Light.ttf");
    }

    public void setProfile(OnlineMessages.AuthOk profile) {
        this.profile = profile;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? VIEW_TYPE_PROFILE : VIEW_TYPE_TUTORIAL;
    }

    @Override
    public int getItemCount() {
        return 6;
    }

    @Override
    public android.support.v7.widget.RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_PROFILE) {
            return new android.support.v7.widget.RecyclerView.ViewHolder(buildProfileView(parent)) {
            };
        }
        return new android.support.v7.widget.RecyclerView.ViewHolder(buildTutorialView(parent)) {
        };
    }

    @Override
    public void onBindViewHolder(android.support.v7.widget.RecyclerView.ViewHolder holder, int position) {
        if (position == 0) {
            bindProfile(holder.itemView);
        } else {
            int[] strings = {
                    R.string.online_tutorial_1, R.string.online_tutorial_2, R.string.online_tutorial_3,
                    R.string.online_tutorial_4, R.string.online_tutorial_5
            };
            ((TextView) holder.itemView).setText(strings[position - 1]);
        }
    }

    // --------------------------------------------------------------- views

    private View buildProfileView(ViewGroup parent) {
        LinearLayout root = new LinearLayout(this.context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(16), dp(24), dp(16), dp(8));
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this.context);
        title.setText(R.string.tab_online);
        title.setTypeface(this.fontLight, Typeface.BOLD);
        title.setTextSize(26.0f);
        title.setTextColor(Color.parseColor("#0d47a1"));
        title.setGravity(Gravity.CENTER);
        title.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showEndpointDialog();
                return true;
            }
        });
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView profileLine = new TextView(this.context);
        profileLine.setId(R.id.online_profile_line);
        profileLine.setGravity(Gravity.CENTER);
        profileLine.setTypeface(OnlineLobbyAdapter.this.fontLight);
        profileLine.setTextSize(16.0f);
        profileLine.setTextColor(Color.parseColor("#37474f"));
        profileLine.setPadding(0, dp(12), 0, 0);
        root.addView(profileLine, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button play = new Button(this.context);
        play.setId(R.id.online_play_button);
        play.setText(R.string.menuOnlinePlay);
        play.setTypeface(this.fontLight, Typeface.BOLD);
        play.setTextSize(18.0f);
        play.setTextColor(Color.WHITE);
        play.setBackgroundResource(R.drawable.online_button_bg);
        play.setAllCaps(false);
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        playParams.topMargin = dp(20);
        root.addView(play, playParams);

        return root;
    }

    private View buildTutorialView(ViewGroup parent) {
        TextView item = new TextView(this.context);
        item.setTypeface(this.fontLight);
        item.setTextSize(15.0f);
        item.setTextColor(Color.parseColor("#546e7a"));
        item.setPadding(dp(24), dp(6), dp(24), dp(6));
        item.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return item;
    }

    private void bindProfile(View view) {
        TextView profileLine = (TextView) view.findViewById(R.id.online_profile_line);
        Button play = (Button) view.findViewById(R.id.online_play_button);
        if (this.profile != null) {
            profileLine.setText(String.format(java.util.Locale.US, "%s · %s %d\n%s %d   %s %d / %d / %d",
                    this.profile.nickname, this.context.getString(R.string.online_elo), this.profile.elo,
                    rankName(this.profile.rank),
                    this.profile.wins + this.profile.losses + this.profile.draws,
                    this.context.getString(R.string.online_title_wins), this.profile.wins,
                    this.profile.losses, this.profile.draws));
        } else {
            profileLine.setText(R.string.online_not_connected);
        }
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int coins = MemoryApplicationModel.getInstance().getLocalDataManager()
                        .getLocalUser().money;
                if (coins < LocalDataManager.ONLINE_ENTRY_FEE) {
                    new AlertDialog.Builder(OnlineLobbyAdapter.this.context)
                            .setMessage(R.string.dialog_unlock_online_warning)
                            .setPositiveButton(R.string.ok, null)
                            .show();
                    return;
                }
                OnlineSession.get().init(OnlineLobbyAdapter.this.context);
                OnlineLobbyAdapter.this.context.startActivity(new Intent(
                        OnlineLobbyAdapter.this.context, OnlineMatchActivity.class));
            }
        });
    }

    // Debug-only endpoint switcher (long-press on the lobby title).
    private void showEndpointDialog() {
        Context ctx = this.context;
        boolean debug = (ctx.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (!debug) {
            return;
        }
        final EditText input = new EditText(ctx);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(OnlineConfig.getEndpoint(ctx));
        new AlertDialog.Builder(ctx)
                .setTitle("PK server endpoint")
                .setView(input)
                .setPositiveButton(R.string.ok, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        OnlineConfig.setEndpoint(OnlineLobbyAdapter.this.context,
                                input.getText().toString().trim());
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private String rankName(int rank) {
        // Rank indices follow the server: 0 bronze .. 5 master.
        String[] names = {"Bronze", "Silver", "Gold", "Platinum", "Diamond", "Master"};
        return rank >= 0 && rank < names.length ? names[rank] : "Bronze";
    }

    private int dp(int value) {
        return (int) (value * this.context.getResources().getDisplayMetrics().density + 0.5f);
    }
}

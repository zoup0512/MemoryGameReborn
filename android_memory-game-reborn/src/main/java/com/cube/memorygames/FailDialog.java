package com.cube.memorygames;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.os.Handler;
import android.view.View;
import android.widget.TextView;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import com.cube.memorygames.ui.TimerView;
import com.memory.brain.training.games.R;

public class FailDialog extends Dialog {
    private static final int DELAY = 5000;
    private OnDialogClickListener buyClickListener;
    @BindView(R.id.buyImage)
    View buyImage;
    @BindView(R.id.buyText)
    TextView buyText;
    private OnDialogClickListener exitCLickListener;
    Handler handler;
    private OnDialogClickListener restartClickListener;
    @BindView(R.id.restartContainer)
    View restartContainer;
    private Runnable runnable = new Runnable() {
        public void run() {
            FailDialog.this.onBackPressed();
        }
    };
    @BindView(R.id.timerView)
    TimerView timerView;

    public interface OnDialogClickListener {
        void onDialogClick(Dialog dialog);
    }

    public FailDialog(Activity activity, int buyStarsClickedCountCount, OnDialogClickListener buyClickListener, OnDialogClickListener restartClickListener, OnDialogClickListener exitCLickListener) {
        super(activity);
        this.buyClickListener = buyClickListener;
        this.restartClickListener = restartClickListener;
        this.exitCLickListener = exitCLickListener;
        getWindow().setBackgroundDrawableResource(17170445);
        getWindow().requestFeature(1);
        getWindow().clearFlags(2);
        setCancelable(false);
        setContentView(R.layout.dialog_fail);
        ButterKnife.bind((Dialog) this);
        this.timerView.showTimer(DELAY, true);
        this.handler = new Handler();
        this.handler.postDelayed(this.runnable, 5000);
        this.buyText.setText("30 " + getContext().getString(R.string.coins));
        if (buyStarsClickedCountCount >= 7) {
            this.buyImage.setEnabled(false);
        } else {
            this.buyImage.setEnabled(true);
        }
        setOnDismissListener(new OnDismissListener() {
            public void onDismiss(DialogInterface dialog) {
                FailDialog.this.handler.removeCallbacks(FailDialog.this.runnable);
            }
        });
    }

    public void dismiss() {
        this.handler.removeCallbacks(this.runnable);
        try {
            super.dismiss();
        } catch (Exception e) {
        }
    }

    public void onBackPressed() {
        if (this.exitCLickListener != null) {
            this.exitCLickListener.onDialogClick(this);
        }
    }

    @OnClick({R.id.restartImage})
    void restartContainerClick() {
        if (this.restartClickListener != null) {
            this.restartClickListener.onDialogClick(this);
        }
    }

    @OnClick({R.id.buyImage})
    void buyContainerClick() {
        if (this.buyClickListener != null) {
            this.handler.removeCallbacks(this.runnable);
            this.buyClickListener.onDialogClick(this);
        }
    }
}

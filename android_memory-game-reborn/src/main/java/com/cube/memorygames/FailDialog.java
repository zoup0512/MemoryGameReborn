package com.cube.memorygames;

import android.app.Activity;
import android.app.Dialog;
import android.view.View;
import android.widget.TextView;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import com.cube.memorygames.ui.DialogUi;
import com.memory.brain.training.games.R;

public class FailDialog extends Dialog {
    private static final int CONTINUE_COST = 30;
    private static final int MAX_CONTINUES = 7;
    private final int continuesUsed;
    private final OnDialogClickListener buyClickListener;
    private final OnDialogClickListener exitCLickListener;
    private final OnDialogClickListener restartClickListener;
    @BindView(R.id.buyImage) View buyImage;
    @BindView(R.id.buyText) TextView buyText;
    @BindView(R.id.dg_balance) TextView balance;
    @BindView(R.id.dg_continue_reason) TextView reason;

    public interface OnDialogClickListener {
        void onDialogClick(Dialog dialog);
    }

    public FailDialog(Activity activity, int buyStarsClickedCountCount, OnDialogClickListener buyClickListener, OnDialogClickListener restartClickListener, OnDialogClickListener exitCLickListener) {
        super(activity);
        this.continuesUsed = Math.max(0, buyStarsClickedCountCount);
        this.buyClickListener = buyClickListener;
        this.restartClickListener = restartClickListener;
        this.exitCLickListener = exitCLickListener;
        setCancelable(true);
        setCanceledOnTouchOutside(false);
        DialogUi.setContent(this, R.layout.dialog_fail);
        ButterKnife.bind((Dialog) this);
        this.buyText.setText(getContext().getString(R.string.dg_continue_cost, CONTINUE_COST));
        updateContinueState();
    }

    private boolean updateContinueState() {
        int money = MemoryApplicationModel.getInstance().getLocalDataManager().getLocalUser().money;
        this.balance.setText(getContext().getString(R.string.dg_balance, money));
        boolean available = this.continuesUsed < MAX_CONTINUES && money >= CONTINUE_COST;
        this.buyImage.setEnabled(available);
        this.buyImage.setAlpha(available ? 1.0f : 0.5f);
        if (this.continuesUsed >= MAX_CONTINUES) {
            this.reason.setText(getContext().getString(R.string.dg_continue_limit, MAX_CONTINUES));
        } else if (money < CONTINUE_COST) {
            this.reason.setText(getContext().getString(R.string.dg_continue_insufficient, CONTINUE_COST - money, MAX_CONTINUES - this.continuesUsed));
        } else {
            this.reason.setText(getContext().getString(R.string.dg_continue_remaining, MAX_CONTINUES - this.continuesUsed, MAX_CONTINUES));
        }
        return available;
    }

    @Override
    protected void onStart() {
        super.onStart();
        updateContinueState();
    }

    @Override
    public void onBackPressed() {
        endGameClick();
    }

    @OnClick(R.id.dg_end_game)
    void endGameClick() {
        if (this.exitCLickListener != null) {
            this.exitCLickListener.onDialogClick(this);
        }
    }

    @OnClick(R.id.restartImage)
    void restartContainerClick() {
        if (this.restartClickListener != null) {
            this.restartClickListener.onDialogClick(this);
        }
    }

    @OnClick(R.id.buyImage)
    void buyContainerClick() {
        if (updateContinueState() && this.buyClickListener != null) {
            this.buyClickListener.onDialogClick(this);
        }
    }
}

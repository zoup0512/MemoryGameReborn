package com.cube.memorygames.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import com.google.android.gms.plus.PlusOneButton;
import com.google.android.gms.plus.PlusOneButton.OnPlusOneClickListener;
import com.memory.brain.training.games.R;

public class RateDialog extends Dialog {
    Button buttonNo;
    Button buttonYes;
    ImageView lionImage;
    PlusOneButton mPlusOneButton;
    TextView title;
    TextView title2;

    public RateDialog(final Context context, int titleText, int buttonYesText, int buttonNoText, boolean showPlusButton, int lionImageRes, final View.OnClickListener listenerYes, final View.OnClickListener listenerNo) {
        super(context);
        DialogUi.setContent(this, R.layout.dialog_feedback);
        this.buttonNo = (Button) findViewById(R.id.dialog_button_no);
        this.buttonYes = (Button) findViewById(R.id.dialog_button_yes);
        this.lionImage = (ImageView) findViewById(R.id.lionImage);
        this.mPlusOneButton = (PlusOneButton) findViewById(R.id.plus_one_button);
        this.title = (TextView) findViewById(R.id.dialog_text);
        this.title2 = (TextView) findViewById(R.id.dialog_text2);
        Typeface typeFaceRobotoRegular = Typeface.createFromAsset(context.getAssets(), "Roboto-Regular.ttf");
        this.title.setTypeface(typeFaceRobotoRegular);
        this.buttonNo.setTypeface(typeFaceRobotoRegular);
        this.buttonYes.setTypeface(typeFaceRobotoRegular);
        String titleWithAppName = context.getString(R.string.rate_dialog_title_format, context.getString(titleText), context.getString(R.string.app_name));
        this.lionImage.setImageResource(lionImageRes);
        this.buttonNo.setText(buttonNoText);
        this.buttonYes.setText(buttonYesText);
        this.mPlusOneButton.setVisibility(8);
        if (showPlusButton) {
            this.title.setText(titleWithAppName);
            this.title2.setVisibility(0);
        } else {
            this.title.setText(titleText);
            this.title2.setVisibility(8);
        }
        this.buttonNo.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                RateDialog.this.dismiss();
                listenerNo.onClick(v);
            }
        });
        this.buttonYes.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                RateDialog.this.dismiss();
                listenerYes.onClick(v);
            }
        });
        this.mPlusOneButton.initialize(context.getString(R.string.plus_one_url), new OnPlusOneClickListener() {
            public void onPlusOneClick(Intent intent) {
                if (!(intent == null || ((Activity) context).isFinishing())) {
                    ((Activity) context).startActivityForResult(intent, 1001);
                }
                RateDialog.this.dismiss();
            }
        });
    }
}

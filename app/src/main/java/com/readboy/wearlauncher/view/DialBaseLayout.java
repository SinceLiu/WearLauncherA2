package com.readboy.wearlauncher.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.readboy.wearlauncher.LauncherApplication;
import com.readboy.wearlauncher.R;
import com.readboy.wearlauncher.utils.Utils;
import com.readboy.wearlauncher.utils.WatchController;

import java.util.Calendar;

/**
 * 时间、日期、天气（警报）、电话/未接提示泡、微聊/未读微聊信息、计步
 * TODO: document your custom view class.
 */
public abstract class DialBaseLayout extends RelativeLayout implements View.OnClickListener,
        WatchController.DateChangedCallback,
        WatchController.CallUnreadChangedCallback,
        WatchController.StepChangedCallback, WatchController.WeTalkUnreadChangedCallback {
    //打电话
    public static final String DIALER_PACKAGE_NAME =  "com.android.dialer";
    public static final String DIALER_CLASS_NAME =  "com.android.dialer.DialtactsActivityOriginal";
    //微聊
    protected  static final String WETALK_PACKAGE_NAME = "com.readboy.wetalk";
    protected static final String WETALK_CLASS_NAME = "com.readboy.wetalk.FirstStartActivity";

    protected LauncherApplication mApplication;
    protected Context mContext;
    protected WatchController mWatchController;

    TextView mDialerNum;
    TextView mWeTalkNum;
    Button mDialerBtn;
    Button mWetalkBtn;
    AnalogClock mAnalogClock;
    DigitClock mDigitClock;

    public DialBaseLayout(Context context) {
        super(context);
        init(context,null, 0);
    }

    public DialBaseLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context,attrs, 0);
    }

    public DialBaseLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context,attrs, defStyle);
    }

    private void init(Context context,AttributeSet attrs, int defStyle) {
        // Load attributes
        mContext = context;
        mApplication = (LauncherApplication) context.getApplicationContext();
        mWatchController = mApplication.getWatchController();
    }

    public abstract void addChangedCallback();
    public abstract void setButtonEnable();
    public abstract void onPause();
    public abstract void onResume();

    public void addDateChangedCallback(){
        mWatchController.addDateChangedCallback(this);
    }

    public void addCallUnreadChangedCallback(){
        mWatchController.addCallUnreadChangedCallback(this);
    }

    public void addStepChangedCallback(){
        mWatchController.addStepChangedCallback(this);
    }

    public void addWeTalkUnreadChangedCallback(){
        mWatchController.addWeTalkUnreadChangedCallback(this);
    }

    public void removeChangedCallback(){
        mWatchController.removeDateChangedCallback(this);
        mWatchController.removeCallUnreadChangedCallback(this);
        mWatchController.removeWeTalkUnreadChangedCallback(this);
        mWatchController.removeStepChangedCallback(this);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mDialerNum = (TextView) findViewById(R.id.text_id_dialer_num);
        mWeTalkNum = (TextView) findViewById(R.id.text_id_mss_num);
//        //按钮点击
        mDialerBtn = (Button) findViewById(R.id.btn_id_dialer);
        mWetalkBtn = (Button) findViewById(R.id.btn_id_mms);
        if(mDialerBtn != null){
            mDialerBtn.setOnClickListener(this);
        }
        if(mWetalkBtn != null){
            mWetalkBtn.setOnClickListener(this);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
//        addChangedCallback();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
//        removeChangedCallback();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.btn_id_dialer:
                Utils.startActivity(mContext, DIALER_PACKAGE_NAME, DIALER_CLASS_NAME);
                break;
            case R.id.btn_id_mms:
                Utils.startActivity(mContext,WETALK_PACKAGE_NAME, WETALK_CLASS_NAME);
                break;
        }
    }

    @Override
    public void onDateChange(int year, int month, int day, int week) {
    }

    @Override
    public void onCallUnreadChanged(int count) {

    }

    @Override
    public void onStepChange(int step) {

    }

    @Override
    public void onWeTalkUnreadChanged(int count) {

    }


    protected void setDate(){
        TextView mDateText = (TextView) findViewById(R.id.date_tvid);
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        /*int month = calendar.get(Calendar.MONTH) + 1;*/
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        int week = (calendar.get(Calendar.DAY_OF_WEEK) - 1) % WatchController.WEEK_NAME_CN_LONG.length;
        //String dateFormat = String.format("%d %s %d",day, WatchController.MONTHS_NAME_EN_SHORT[month],year);
        String dateFormat = String.format("%d/%d  %s",month+1,day,WatchController.WEEK_NAME_CN_LONG[week]);
        //String dateFormat = String.format("%s, %d  %s",
        //        WatchController.WEEK_NAME_EN_LONG[week],day,WatchController.MONTHS_NAME_EN_LONG[month]);
        mDateText.setText(dateFormat);
    }
}

package me.dreamheart.autoscalinglayout;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.readboy.wearlauncher.R;

/**
 * ViewGroup自动缩放组件
 */
public class ASViewGroupUtil {

    public static boolean DEBUG = false;
    private static boolean ScalingToolbar = false;

    private static final int TYPE_FIT_INSIDE = 0;
    private static final int TYPE_FIT_WIDTH = 1;
    private static final int TYPE_FIT_HEIGHT = 2;

    // 原设计宽高
    private int mDesignWidth;
    private int mDesignHeight;
    // 当前宽高
    private float mCurrentWidth;
    private float mCurrentHeight;
    // 是否开启自动缩放
    private boolean mAutoScaleEnable;
    // 缩放模式
    private int mScaleType;
    // 在onLayout时预处理缩放，可能会引发问题，默认关闭
    private boolean mPreScaling;

    // 直接用宽高初始化
    public void init(int designWidth, int designHeight){
        mDesignWidth = designWidth;
        mDesignHeight = designHeight;
        mCurrentWidth = mDesignWidth;
        mCurrentHeight = mDesignHeight;
        mAutoScaleEnable = true;
        mScaleType = TYPE_FIT_INSIDE;
        mPreScaling = false;
    }

    // 用AttributeSet初始化
    public void init(ViewGroup vg, AttributeSet attrs){
        mScaleType = TYPE_FIT_INSIDE;
        String scaleTypeStr = null;
        TypedArray a = null;
        try{
            a = vg.getContext().obtainStyledAttributes(
                    attrs, R.styleable.AutoScalingLayout);

            // 获得设计宽高
            mDesignWidth = a.getDimensionPixelOffset(R.styleable.AutoScalingLayout_designWidth, 0);
            mDesignHeight = a.getDimensionPixelOffset(R.styleable.AutoScalingLayout_designHeight, 0);
            // 是否开启自动缩放
            mAutoScaleEnable = a.getBoolean(R.styleable.AutoScalingLayout_autoScaleEnable, true);
            mPreScaling = a.getBoolean(R.styleable.AutoScalingLayout_preScaling, false);
            scaleTypeStr = a.getString(R.styleable.AutoScalingLayout_autoScaleType);
        }catch (Throwable e){
            // 用户使用jar时，没有R.styleable.AutoScalingLayout，需要根据字符串解析参数
            mAutoScaleEnable = true;
            mDesignWidth = 0;
            mDesignHeight = 0;
            for (int i = 0; i < attrs.getAttributeCount(); i++){
                if ("designWidth".equals(attrs.getAttributeName(i))){
                    String designWidthStr = attrs.getAttributeValue(i);
                    mDesignWidth = getDimensionPixelOffset(vg.getContext(), designWidthStr);
                }
                else if ("designHeight".equals(attrs.getAttributeName(i))) {
                    String designHeightStr = attrs.getAttributeValue(i);
                    mDesignHeight = getDimensionPixelOffset(vg.getContext(), designHeightStr);
                }
                else if ("autoScaleEnable".equals(attrs.getAttributeName(i))) {
                    String autoScaleEnableStr = attrs.getAttributeValue(i);
                    if (autoScaleEnableStr.equals("false"))
                        mAutoScaleEnable = false;
                }
                else if ("preScaling".equals(attrs.getAttributeName(i))) {
                    String preScalingStr = attrs.getAttributeValue(i);
                    if (preScalingStr.equals("true"))
                        mPreScaling = true;
                }
                else if ("autoScaleType".equals(attrs.getAttributeName(i))) {
                    scaleTypeStr = attrs.getAttributeValue(i);
                }
            }
        }finally {
            if(null != a)
                a.recycle();
        }

        if (null != scaleTypeStr){
            if (scaleTypeStr.equals("fitWidth"))
                mScaleType = TYPE_FIT_WIDTH;
            else if (scaleTypeStr.equals("fitHeight"))
                mScaleType = TYPE_FIT_HEIGHT;
        }

        mCurrentWidth = mDesignWidth;
        mCurrentHeight = mDesignHeight;

        // 背景为空时，不进入draw函数，这里必须设置默认背景
        if (null == vg.getBackground())
            vg.setBackgroundColor(Color.TRANSPARENT);

        if (DEBUG){
            Log.v("AutoScalingLayout", "mDesignWidth=" + mDesignWidth + " mDesignHeight=" + mDesignHeight);
            //Log.v("AutoScalingLayout", "1dp=" + getDimensionPixelOffset(vg.getContext(), "1dp") + "px");
        }
    }

    /**
     * 是否对Toolbar进行缩放
     * @return true or false
     */
    public static boolean isScalingToolbar() {
        return ScalingToolbar;
    }

    /**
     * 设置是否对Toolbar进行缩放
     * @param scalingToolbar true or false
     */
    public static void setScalingToolbar(boolean scalingToolbar) {
        ScalingToolbar = scalingToolbar;
    }

    /**
     * 是否开启自动缩放
     * @return true or false
     */
    public boolean isAutoScaleEnable(){
        return mAutoScaleEnable;
    }

    /**
     * 是否在onLayout时预处理缩放，可能会引发问题，默认关闭
     * @return true or false
     */
    public boolean isPreScaling() {
        return mPreScaling;
    }

    public void onLayout(ViewGroup vg, boolean changed, int l, int t, int r, int b) {
        if (isPreScaling())
            scaleSize(vg);
    }

    /**
     * 测量宽高(只有一方数值确定，另一方为WRAP_CONTENT才需要测量，用于保持纵横比)
     * @param vg ViewGroup
     * @param widthMeasureSpec  宽度
     * @param heightMeasureSpec 高度
     * @return 测量好的宽高
     */
    public int[] onMeasure(ViewGroup vg, int widthMeasureSpec, int heightMeasureSpec) {
        int measureSpecs[] = new int[2];
        measureSpecs[0] = widthMeasureSpec;
        measureSpecs[1] = heightMeasureSpec;

        if (!mAutoScaleEnable)
            return measureSpecs;

        int widthMode = View.MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = View.MeasureSpec.getMode(heightMeasureSpec);

        int width = View.MeasureSpec.getSize(widthMeasureSpec);
        int height = View.MeasureSpec.getSize(heightMeasureSpec);

        ViewGroup.LayoutParams params = vg.getLayoutParams();

        // 已知宽度
        boolean bScalingWidth = widthMode == View.MeasureSpec.EXACTLY && ViewGroup.LayoutParams.MATCH_PARENT == params.width;
        // 已知高度
        boolean bScalingHeight = heightMode == View.MeasureSpec.EXACTLY && ViewGroup.LayoutParams.MATCH_PARENT == params.height;

        if (bScalingHeight && bScalingWidth && TYPE_FIT_INSIDE == mScaleType){
            scaleSize(vg, width, height, TYPE_FIT_INSIDE);
        }
        else if (bScalingHeight && TYPE_FIT_HEIGHT == mScaleType){
            scaleSize(vg, width, height, TYPE_FIT_HEIGHT);
        }
        else if (bScalingWidth && TYPE_FIT_WIDTH == mScaleType){
            scaleSize(vg, width, height, TYPE_FIT_WIDTH);
        }

        if (0 == mDesignWidth || 0 == mDesignHeight)
            return measureSpecs;

        if ( TYPE_FIT_INSIDE != mScaleType)
            return measureSpecs;

        if (widthMode != View.MeasureSpec.EXACTLY
                && heightMode == View.MeasureSpec.EXACTLY
                && ViewGroup.LayoutParams.WRAP_CONTENT == params.width) {
            // 高度为match_parent或具体值，宽度wrap_content
            width = (height * mDesignWidth / mDesignHeight);
            measureSpecs[0] = View.MeasureSpec.makeMeasureSpec(width,
                    View.MeasureSpec.EXACTLY);
        }else if (widthMode == View.MeasureSpec.EXACTLY
                && heightMode != View.MeasureSpec.EXACTLY
                && ViewGroup.LayoutParams.WRAP_CONTENT == params.height) {
            // 宽度为match_parent或具体值，高度为wrap_content
            height = (width * mDesignHeight / mDesignWidth);
            measureSpecs[1] = View.MeasureSpec.makeMeasureSpec(height,
                    View.MeasureSpec.EXACTLY);
        }

        return measureSpecs;
    }

    /**
     * 缩放ViewGroup
     * @param vg    ViewGroup
     * @return true表示进行了缩放 false表示不需要缩放
     */
    public boolean scaleSize(ViewGroup vg) {
        if (!mAutoScaleEnable)
            return false;

        if (0 == mDesignWidth && TYPE_FIT_HEIGHT != mScaleType)
            return false;

        if (0 == mDesignHeight && TYPE_FIT_WIDTH != mScaleType)
            return false;

        // 当前宽高
        int width = vg.getWidth();
        int height = vg.getHeight();

        if (0 == width || 0 == height)
            return false;

        return scaleSize(vg, width, height, mScaleType);
    }

    /**
     * 缩放ViewGroup
     * @param vg    ViewGroup
     * @param width     当前宽度
     * @param height    当前高度
     * @param type      缩放类型
     * @return true表示进行了缩放 false表示不需要缩放
     */
    public boolean scaleSize(ViewGroup vg, int width, int height, int type) {
        //Log.i("ASViewGroupUtil", "scaleSize");
        // 如果大小改变则进行缩放
        if(width != this.mCurrentWidth || height != this.mCurrentHeight) {
            // 计算缩放比例
            float scale;

            if (TYPE_FIT_HEIGHT == type)
                scale = (float)height / this.mCurrentHeight;
            else if (TYPE_FIT_WIDTH == type)
                scale = (float)width / this.mCurrentWidth;
            else {
                float wScale = (float)width / this.mCurrentWidth;
                float hScale = (float)height / this.mCurrentHeight;
                scale = Math.min(wScale, hScale);
            }

            if (scale < 1.02 && scale > 0.98)
                return false;

            // 保存当前宽高
            this.mCurrentWidth = width;
            this.mCurrentHeight = height;
            if (DEBUG){
                Log.v("AutoScalingLayout", "scale=" + scale);
                Log.v("AutoScalingLayout", "width=" + width + " height=" + height);
            }

            //Log.i("ASViewGroupUtil", "scaleSize " + scale);
            // 缩放ViewGroup
            ScalingUtil.scaleViewAndChildren(vg, scale, 0);

            return true;
        }

        return false;
    }

    /**
     * 获取dimension的像素值
     * @param context View的Context
     * @param value dimension的字符串
     * @return 像素值
     */
    private int getDimensionPixelOffset(Context context, String value){
        if (value.endsWith("px")){
            float v = Float.parseFloat(value.substring(0, value.length() - 2));
            return (int)v;
        }else if (value.endsWith("dp")){
            float v = Float.parseFloat(value.substring(0, value.length() - 2));
            float density = context.getResources().getDisplayMetrics().density;
            return (int) (v * density + 0.5f);
        }else if (value.endsWith("dip")){
            float v = Float.parseFloat(value.substring(0, value.length() - 3));
            float density = context.getResources().getDisplayMetrics().density;
            return (int) (v * density + 0.5f);
        }
        return 0;
    }
}

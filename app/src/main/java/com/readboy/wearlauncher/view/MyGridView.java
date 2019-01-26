package com.readboy.wearlauncher.view;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.GridView;

public class MyGridView extends GridView {
    public MyGridView(Context context) {
        super(context);
    }

    public MyGridView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }


    // Android 4.4 GridView刷新后抢焦点bug
    @Override
    public boolean isInTouchMode() {
        if (19 == Build.VERSION.SDK_INT) {
//            return !(hasFocus() && !super.isInTouchMode());
            return true;
        } else {
            return super.isInTouchMode();
        }
    }
}

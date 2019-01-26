
package com.readboy.wearlauncher.notification;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.readboy.recyclerview.swipe.SwipeMenuRecyclerView;
import com.readboy.recyclerview.swipe.touch.OnItemMoveListener;
import com.readboy.recyclerview.swipe.touch.OnItemMovementListener;
import com.readboy.wearlauncher.R;

import android.os.ServiceManager;
import android.widget.Toast;
import android.app.readboy.ReadboyWearManager;

import com.android.internal.statusbar.IStatusBarService;

import java.util.ArrayList;

public class NotificationMonitor extends NotificationListenerService {
    private static final String TAG = "NotificationMonitor";
    private static final String TAG_PRE = "[NotificationMonitor] ";

    public static final String EXTRA_COMMAND = "command";
    public static final String EXTRA_NOTIFICATION = "notification";
    public static final String COMMAND_REMOVED = "removed";
    public static final String COMMAND_POSTED = "posted";

    public static final String ACTION_NLS_CONTROL = "com.readboy.notificationlistener.NLSCONTROL";
    public static final String ACTION_NLS_UPDATE = "com.readboy.notificationlistener.UPDATE";

    public static final int CREATE_WINDOW = 0;
    public static final int REMOVE_WINDOW = 1;
    public static final int UPDATE_WINDOW = 2;

    private NotificationMonitorReceiver mReceiver = new NotificationMonitorReceiver();

    public static NotificationMonitor INSTANCE;

    public static NotificationMonitor getNotificationMonitor() {
        return INSTANCE;
    }

    private ArrayList<StatusBarNotification> mNotificationList = new ArrayList<StatusBarNotification>();
    private FloatNotificationAdapter mAdapter;
    private WindowManager mWindowManager;
    private SwipeMenuRecyclerView mFloatNotificationWindow;
    private IStatusBarService mBarService;
    private boolean isNotificationVisible;
    private long mDuration = 3000;

    class NotificationMonitorReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action;
            if (intent != null && intent.getAction() != null) {
                action = intent.getAction();
                if (action.equals(ACTION_NLS_CONTROL)) {
                    String command = intent.getStringExtra("command");

                    if (TextUtils.equals(command, "cancel") && !TextUtils.isEmpty(intent.getStringExtra("key"))) {
                        String pkg = intent.getStringExtra("pkg");
                        String tag = intent.getStringExtra("tag");
                        int id = intent.getIntExtra("id", -1);
                        Log.e(TAG, "onReceive: cancel notification pkg = " + pkg
                                + " tag = " + tag + " id = " + id);
                        if (pkg == null || tag == null || id == -1) {
                            return;
                        }
                        cancelNotification(pkg, tag, id);
                    } else if (TextUtils.equals(command, "clearall")) {
                        NotificationMonitor.this.cancelAllNotifications();
                    } else if (TextUtils.equals(command, "list")) {
//                        for (StatusBarNotification sbn : NotificationMonitor.this.getActiveNotifications()) {
//                            Intent intent1 = new  Intent(ACTION_NLS_UPDATE);
//                            intent1.putExtra("command","list");
//                            intent1.putExtra("sbn",sbn);
//                            sendBroadcast(intent1);
//                        }
                    }
                }
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        INSTANCE = this;
        logNLS("onCreate...");
        mBarService = IStatusBarService.Stub.asInterface(
                ServiceManager.getService(Context.STATUS_BAR_SERVICE));
        mAdapter = new FloatNotificationAdapter(this);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_NLS_CONTROL);
        registerReceiver(mReceiver, filter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }

    @Override
    public IBinder onBind(Intent intent) {
        logNLS("onBind...");
        return super.onBind(intent);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        logNLS("onNotificationPosted: pkg = " + sbn.getPackageName()
                + " tag = " + sbn.getTag() + " id = " + sbn.getId());
        Intent intent1 = new Intent(ACTION_NLS_UPDATE);
        intent1.putExtra(EXTRA_COMMAND, COMMAND_POSTED);
        intent1.putExtra(EXTRA_NOTIFICATION, sbn);
        sendBroadcast(intent1);

        mNotificationList.clear();
        mNotificationList.add(sbn);
        if (sbn.getNotification().priority < Notification.PRIORITY_HIGH
                || shouldFilterOut(sbn)) {
            return;
        }
        ReadboyWearManager rwm = (ReadboyWearManager) getSystemService(Context.RBW_SERVICE);
        boolean isEnable = rwm.isClassForbidOpen();
        //上课禁用时不弹窗
        if (isEnable) {
            return;
        }
        wakeUp(this);
        if (isNotificationVisible) {
            mHandler.removeMessages(UPDATE_WINDOW);
            mHandler.removeMessages(REMOVE_WINDOW);
            mHandler.sendEmptyMessage(UPDATE_WINDOW);
            mHandler.sendEmptyMessageDelayed(REMOVE_WINDOW, mDuration);
        } else {
            mHandler.removeMessages(CREATE_WINDOW);
            mHandler.removeMessages(REMOVE_WINDOW);
            mHandler.sendEmptyMessage(CREATE_WINDOW);
            mHandler.sendEmptyMessageDelayed(REMOVE_WINDOW, mDuration);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        logNLS("onNotificationPosted: pkg = " + sbn.getPackageName()
                + " tag = " + sbn.getTag() + " id = " + sbn.getId());
        Intent intent1 = new Intent(ACTION_NLS_UPDATE);
        intent1.putExtra(EXTRA_COMMAND, COMMAND_REMOVED);
        intent1.putExtra(EXTRA_NOTIFICATION, sbn);
        sendBroadcast(intent1);
        mHandler.removeMessages(REMOVE_WINDOW);
        mHandler.sendEmptyMessage(REMOVE_WINDOW);
    }

    private static void logNLS(Object object) {
        Log.e(TAG, TAG_PRE + object.toString());
    }


    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case CREATE_WINDOW:
                    createFloatNotificationWindow();
                    break;
                case REMOVE_WINDOW:
                    removeFloatNotificationWindow();
                    break;
                case UPDATE_WINDOW:
                    mAdapter.notifyDataSetChanged();
                    break;
                default:
                    break;
            }
        }
    };

    public void createFloatNotificationWindow() {
        Log.e(TAG, "createFloatNotificationWindow");
        WindowManager windowManager = getWindowManager(getApplicationContext());
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        mFloatNotificationWindow = (SwipeMenuRecyclerView) (LayoutInflater.from(this).inflate(R.layout.notification_float_window,
                null, false));
        mFloatNotificationWindow.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        params.type = WindowManager.LayoutParams.TYPE_PHONE;
        params.width = 220;
        params.height = 70;
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        params.format = PixelFormat.RGBA_8888;
        params.y = 10;
        params.windowAnimations = R.style.NotifcationAnimation;
        mFloatNotificationWindow.setBackgroundColor(getResources().getColor(R.color.transparent));
        mFloatNotificationWindow.setAdapter(mAdapter);
        mFloatNotificationWindow.setItemViewSwipeEnabled(true);
        mFloatNotificationWindow.setOnItemMoveListener(onItemMoveListener);
        mFloatNotificationWindow.setOnItemMovementListener(onItemMovementListener);
        windowManager.addView(mFloatNotificationWindow, params);
        isNotificationVisible = true;
    }

    public void removeFloatNotificationWindow() {
        Log.e(TAG, "removeFloatNotificationWindow");
        if (mFloatNotificationWindow != null) {
            WindowManager windowManager = getWindowManager(getApplicationContext());
            try {
                windowManager.removeView(mFloatNotificationWindow);
            } catch (Exception e) {
                e.printStackTrace();
            }
            isNotificationVisible = false;
            mFloatNotificationWindow = null;
        }
    }


    /**
     * 当Item被移动之前。
     */
    private OnItemMovementListener onItemMovementListener = new OnItemMovementListener() {
        /**
         * 当Item在移动之前，获取拖拽的方向。
         * @param recyclerView     {@link RecyclerView}.
         * @param targetViewHolder target ViewHolder.
         */
        @Override
        public int onDragFlags(RecyclerView recyclerView, RecyclerView.ViewHolder targetViewHolder) {
            return OnItemMovementListener.INVALID;// 返回无效的方向。
        }

        @Override
        public int onSwipeFlags(RecyclerView recyclerView, RecyclerView.ViewHolder targetViewHolder) {
            return OnItemMovementListener.LEFT | OnItemMovementListener.RIGHT; // 可以右滑，左滑动删除。
        }
    };

    /**
     * 当Item移动的时候。
     */
    private OnItemMoveListener onItemMoveListener = new OnItemMoveListener() {
        @Override
        public boolean onItemMove(int fromPosition, int toPosition) {
            return false;
        }

        //item侧滑删除后回调, 调用Adapter.notifyItemRemoved()
        @Override
        public void onItemDismiss(int position) {
            StatusBarNotification sbn = mAdapter.getStatusBarNotification(position);
            Log.e(TAG, "onItemDismiss: sbn.pkg = " + sbn.getPackageName()
                    + " sbn.tag = " + sbn.getTag() + " sbn.id = " + sbn.getId());
            cancelNotification(sbn);
        }
    };

    private void cancelNotification(StatusBarNotification sbn) {
        if (sbn != null) {
//            NotificationMonitor.cancelNotificationByKey(sbn.getKey());
            try {
                mBarService.onNotificationClear(
                        sbn.getPackageName(),
                        sbn.getTag(),
                        sbn.getId());
            } catch (android.os.RemoteException ex) {
                // oh well
                Log.e(TAG, "cancelNotification: ex : " + ex.toString());
            }
        }
        Log.e(TAG, "cancelNotification: pkg = " + sbn.getPackageName()
                + " tag = " + sbn.getTag() + " id = " + sbn.getId());
    }


    /**
     * 如果WindowManager还未创建，则创建一个新的WindowManager返回。否则返回当前已创建的WindowManager。
     *
     * @param context 必须为应用程序的Context.
     * @return WindowManager的实例，用于控制在屏幕上添加或移除悬浮窗。
     */
    private WindowManager getWindowManager(Context context) {
        if (mWindowManager == null) {
            mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        }
        return mWindowManager;
    }

    /**
     * 过滤第三方应用的通知
     *
     * @return true if notification should filter, otherwise.
     */
    private boolean shouldFilterOut(StatusBarNotification notification) {
        String type = notification.getNotification().extras.getString("extra_type", "");
        if ("readboy".equalsIgnoreCase(type)) {
            return false;
        }
        return true;
    }

    private class FloatNotificationAdapter extends RecyclerView.Adapter<FloatNotificationViewHolder> {
        private final LayoutInflater mInflater;

        FloatNotificationAdapter(Context context) {
            mInflater = LayoutInflater.from(context);
        }

        @Override
        public FloatNotificationViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = mInflater.inflate(R.layout.notification_float_window_item, parent, false);
            return new FloatNotificationViewHolder(view);
        }

        @Override
        public void onBindViewHolder(FloatNotificationViewHolder holder, int position) {
            holder.bindNotification(mNotificationList.get(position));
        }

        @Override
        public int getItemCount() {
            return mNotificationList == null ? 0 : mNotificationList.size();
        }

        @Override
        public long getItemId(int position) {
            return mNotificationList.get(position).getId();
        }

        StatusBarNotification getStatusBarNotification(int position) {
            try {
                return mNotificationList.get(position);
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "getStatusBarNotification: e:" + e.toString() + ", position = " + position);
                return null;
            }
        }
    }

    private class FloatNotificationViewHolder extends RecyclerView.ViewHolder {
        private ImageView mIcon;
        private TextView mTitleView;
        private DateTimeView mTimeView;
        private TextView mContentView;
        private StatusBarNotification mStatusBarNotification;

        FloatNotificationViewHolder(final View itemView) {
            super(itemView);
            mIcon = (ImageView) itemView.findViewById(R.id.small_icon);
            mTitleView = (TextView) itemView.findViewById(R.id.content_title);
            mTimeView = (DateTimeView) itemView.findViewById(R.id.content_time);
            mContentView = (TextView) itemView.findViewById(R.id.content_text);
            View parent = itemView.findViewById(R.id.item_float_notification_parent);
            parent.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final StatusBarNotification sbn = mStatusBarNotification;
                    if (sbn == null) {
                        Log.e(TAG, "NotificationClicker called on an unclickable notification,");
                        return;
                    }
                    final PendingIntent intent = sbn.getNotification().contentIntent;

                    if (intent != null) {
                        try {
                            intent.send();
                            mBarService.onNotificationClick(
                                    sbn.getPackageName(),
                                    sbn.getTag(),
                                    sbn.getId());
                            cancelNotification(mStatusBarNotification);
                        } catch (PendingIntent.CanceledException e) {
                            Log.e(TAG, "Sending contentIntent failed: " + e);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        }

        void bindNotification(StatusBarNotification statusBarNotification) {
            mStatusBarNotification = statusBarNotification;
            Notification notification = statusBarNotification.getNotification();
            if (notification == null) {
                return;
            }
            String title = notification.extras.getString(Notification.EXTRA_TITLE);
            String content = notification.extras.getString(Notification.EXTRA_TEXT);
            boolean showWhen = notification.extras.getBoolean(Notification.EXTRA_SHOW_WHEN, false);
            long time = notification.when;
            mTitleView.setText(title);
            mContentView.setText(content);

            Drawable icon = NotificationActivity.getIcon(NotificationMonitor.this, statusBarNotification);
            if (icon != null) {
                mIcon.setImageDrawable(icon);
            } else {
                mIcon.setImageResource(R.drawable.app_icon_default);
            }
            if (showWhen && time != 0) {
                mTimeView.setTime(time);
            }
        }
    }

    public void wakeUp(Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        boolean screenOn = pm.isScreenOn();
        if (!screenOn) {
            PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP |
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "bright");
            wakeLock.acquire(1000);  //点亮屏幕
        }
    }
}

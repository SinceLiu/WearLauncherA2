package com.readboy.wearlauncher.notification;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import com.android.internal.statusbar.IStatusBarService;
import com.readboy.recyclerview.swipe.SwipeMenuRecyclerView;
import com.readboy.recyclerview.swipe.touch.OnItemMoveListener;
import com.readboy.recyclerview.swipe.touch.OnItemMovementListener;
import com.readboy.wearlauncher.R;
import com.readboy.wearlauncher.utils.Utils;
import com.readboy.wearlauncher.view.SwipeDismissLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import static com.readboy.wearlauncher.notification.NotificationMonitor.EXTRA_COMMAND;
import static com.readboy.wearlauncher.notification.NotificationMonitor.EXTRA_NOTIFICATION;
import static com.readboy.wearlauncher.notification.NotificationMonitor.COMMAND_REMOVED;
import static com.readboy.wearlauncher.notification.NotificationMonitor.COMMAND_POSTED;

/**
 * Created by oubin on 2017/7/12.
 */

public class NotificationActivity extends Activity {

    private static final String TAG = "NotificationActivity";
    private NotificationReceiver mReceiver = new NotificationReceiver();

    private static final String ENABLED_NOTIFICATION_LISTENERS
            = Settings.Secure.ENABLED_NOTIFICATION_LISTENERS;

    SwipeDismissLayout mSwipeDismissLayout;
    private SwipeMenuRecyclerView mRecyclerView;
    private NotificationAdapter mAdapter;
    private AnimationDrawable mNoDataAnimation;
    private View mNoDataView;

    private IStatusBarService mBarService;
    private boolean isSendTo = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification);
        assignView();
        initData();
        registerReceiver();
    }

    private void assignView() {
        mSwipeDismissLayout = (SwipeDismissLayout) findViewById(R.id.swipe);
        mSwipeDismissLayout.setOnDismissedListener(new SwipeDismissLayout.OnDismissedListener() {
            @Override
            public boolean onDismissed(SwipeDismissLayout layout) {
                finish();
                return true;
            }
        });

        mNoDataView = findViewById(R.id.no_data_parent);
        mNoDataAnimation = (AnimationDrawable) findViewById(R.id.no_data_animation).getBackground();
        initRecyclerView();

        findViewById(R.id.btn_left).setOnTouchListener(openFactoryModeOps);
        findViewById(R.id.btn_right).setOnTouchListener(openFactoryModeOps);
    }

/// add by cwj for open factory mode @{
    int mMinCountdown = 5;
    int mMaxCountdown = 8;
    int mLeftCountdown = 0;
    int mRightCountdown = 0;

    private static final int RESET_FACTORY_COUNT = 1;
    Handler mHandler = new Handler() {
        public void handleMessage(android.os.Message msg) {
            switch (msg.what) {
                case RESET_FACTORY_COUNT:
                    initCount();
                    break;
                default:
                    break;
            }
        }
    };
    private void initCount(){
        mLeftCountdown = 0;
        mRightCountdown = 0;
    }
    private long resetTime = 1000;
    private View.OnTouchListener openFactoryModeOps = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (motionEvent.getAction()==MotionEvent.ACTION_DOWN){
                switch (view.getId()) {
                    case R.id.btn_left:
                        mHandler.removeMessages(RESET_FACTORY_COUNT);
                        mHandler.sendEmptyMessageDelayed(RESET_FACTORY_COUNT, resetTime);
                        mLeftCountdown++;
                        Log.d("FACTORY_COUNT", "mLeftCountdown:"+ mLeftCountdown
                                +" mRightCountdown:"+ mRightCountdown);
                        if (mLeftCountdown>mMaxCountdown)
                            initCount();
                        break;
                    case R.id.btn_right:
                        mHandler.removeMessages(RESET_FACTORY_COUNT);
                        Log.d("FACTORY_COUNT", "mLeftCountdown:"+ mLeftCountdown
                                +" mRightCountdown:"+ mRightCountdown);
                        if (mLeftCountdown >=mMinCountdown&&mLeftCountdown<=mMaxCountdown) {
                            mRightCountdown++;
                            if (mRightCountdown==mMinCountdown) {
                                openFactoryMode(NotificationActivity.this);
                                initCount();
                            } else {
                                mHandler.sendEmptyMessageDelayed(RESET_FACTORY_COUNT, resetTime);
                            }
                        } else {
                            initCount();
                        }
                        break;
                }
            }
            return false;
        }
    };
    //打开工厂模式
    private static void openFactoryMode(Context context) {
        Intent intent = new Intent("android.provider.Telephony.SECRET_CODE",
                Uri.parse("android_secret_code://" + "83789"));
        context.sendBroadcast(intent);
    }
/// @}

    private void initData() {
        Log.e(TAG, "initData: NotificationMonitor" + NotificationMonitor.getNotificationMonitor());
        if (NotificationMonitor.getNotificationMonitor() != null) {
            StatusBarNotification[] datas
                    = NotificationMonitor.getNotificationMonitor().getActiveNotifications();
            if (datas.length > 0) {
                mAdapter.updateData(datas);
                mAdapter.notifyDataSetChanged();
            } else {
                showNoMsgView();
            }
        } else {
            Log.e(TAG, "initData: NotificationMonitor == null");
            showNoMsgView();
        }

        mBarService = IStatusBarService.Stub.asInterface(
                ServiceManager.getService(Context.STATUS_BAR_SERVICE));
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!isSendTo) {
            finish();
        }
        exit();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, R.anim.activity_exit_top);
    }

    private void exit() {
        mNoDataAnimation.stop();
    }

    private void initRecyclerView() {
        mRecyclerView = (SwipeMenuRecyclerView) findViewById(R.id.notification_recycler_view);
        mRecyclerView.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        mAdapter = new NotificationAdapter(this);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setItemViewSwipeEnabled(true);
        mRecyclerView.setOnItemMoveListener(onItemMoveListener);
        mRecyclerView.setOnItemMovementListener(onItemMovementListener);
        RecyclerView.ItemDecoration itemDecoration = new RecyclerView.ItemDecoration() {

            @Override
            public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                super.getItemOffsets(outRect, view, parent, state);
                outRect.set(0, 0, 0, 6);
                int position = parent.getChildAdapterPosition(view);
                if (position == state.getItemCount() - 1) {
                    outRect.bottom = 0;
                }
            }
        };
        mRecyclerView.addItemDecoration(itemDecoration);
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
            NotificationViewHolder viewHolder = (NotificationViewHolder) targetViewHolder;
            if (filterNotification(viewHolder.mStatusBarNotification)) {
                return OnItemMovementListener.INVALID;
            }
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
                    +" sbn.tag = " + sbn.getTag()+" sbn.id = " + sbn.getId());
            cancelNotification(sbn);
        }
    };

    @Deprecated
    private void cancelNotification(final int position) {
        StatusBarNotification statusBarNotification = mAdapter.getStatusBarNotification(position);
        if (statusBarNotification != null) {
            if (filterNotification(statusBarNotification)) {
                return;
            }
//            NotificationMonitor.cancelNotificationByKey(statusBarNotification.getKey());
            cancelNotification(statusBarNotification);
            Log.e(TAG, "cancelNotification: pkg = " + statusBarNotification.getPackageName()
                    +" tag = " + statusBarNotification.getTag()+" id = " + statusBarNotification.getId());
        }
    }

    private void cancelNotification(StatusBarNotification sbn) {
        if (sbn != null && !filterNotification(sbn)) {
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
                +" tag = " + sbn.getTag()+" id = " + sbn.getId());
    }

    private void clickNotification(StatusBarNotification sbn) {
        if (sbn != null) {
            try {
                mBarService.onNotificationClick(
                        sbn.getPackageName(),
                        sbn.getTag(),
                        sbn.getId());
            } catch (RemoteException e) {
                e.printStackTrace();
                Log.e(TAG, "clickNotification: e = " + e.toString());
            }
        }
    }

    private void registerReceiver() {
        Log.e(TAG, "registerReceiver: ");
        IntentFilter filter = new IntentFilter();
        filter.addAction(NotificationMonitor.ACTION_NLS_UPDATE);
        registerReceiver(mReceiver, filter);
    }

    //是否要过滤掉，禁止侧滑
    private boolean filterNotification(StatusBarNotification notification) {
        int flags = notification.getNotification().flags;
//        Log.e(TAG, "filterNotification: flags hex = " + Integer.toHexString(flags));
        if (!notification.isClearable()) {
            return true;
        }
        return false;
    }

    private boolean ontainPermission() {
        String className = this.getPackageName() + "/"
                + "com.readboy.wearlauncher.notification.NotificationMonitor";
        return Settings.Secure.putString(getContentResolver(), ENABLED_NOTIFICATION_LISTENERS, className);
    }

    private boolean isEnabled() {
        String pkgName = this.getPackageName();
        final String flat = Settings.Secure.getString(getContentResolver(),
                ENABLED_NOTIFICATION_LISTENERS);
        if (!TextUtils.isEmpty(flat)) {
            final String[] names = flat.split(":");
            for (int i = 0; i < names.length; i++) {
                final ComponentName cn = ComponentName.unflattenFromString(names[i]);
                if (cn != null) {
                    if (TextUtils.equals(pkgName, cn.getPackageName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    class NotificationReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.e(TAG, "onReceive: action = " + intent.getAction());
            if (TextUtils.equals(intent.getAction(), NotificationMonitor.ACTION_NLS_UPDATE)) {
                String command = intent.getStringExtra(EXTRA_COMMAND);
                Log.e(TAG, "onReceive: command = " + command);
                StatusBarNotification notification = intent.getParcelableExtra(EXTRA_NOTIFICATION);
                if (notification == null) {
                    Log.e(TAG, "onReceive: statusBarNotification is null by intent extra");
                    return;
                }
                if (command.equals(COMMAND_POSTED)){
                    putNotification(notification);
                } else if (command.equals(COMMAND_REMOVED)){
                    removeNotification(notification);
                } else{
                    Log.e(TAG, "onReceive: command not support, command = " + command);
                }
            }
        }
    }

    private void putNotification(StatusBarNotification notification) {
        mAdapter.putItem(notification);
        if (mAdapter.getItemCount() == 1) {
            hideNoMsgView();
        }
    }

    private void removeNotification(StatusBarNotification notification) {
        mAdapter.removeItem(notification);
        if (mAdapter.getItemCount() == 0) {
//            showNoMsgView();
            finish();
        }
    }

    private void hideNoMsgView() {
        mNoDataAnimation.stop();
        mNoDataView.setVisibility(View.GONE);
    }

    private void showNoMsgView() {
        mNoDataAnimation.start();
        mNoDataView.setVisibility(View.VISIBLE);
    }

    private static boolean isSystemNotification(StatusBarNotification sbn) {
        String sbnPackage = sbn.getPackageName();
        return "android".equals(sbnPackage) || "com.android.systemui".equals(sbnPackage);
    }

    private class NotificationAdapter extends RecyclerView.Adapter<NotificationViewHolder> {
        private static final String TAG = "NotificationAdapter";
        private static final int ITEM_TYPE_NORMAL = 0;
        private static final int ITEM_TYPE_PROGRESS = 1;

        //过滤前
        private ArrayMap<Integer, StatusBarNotification> mNotificationsMap = new ArrayMap<Integer, StatusBarNotification>();
        //过滤后，界面显示的内容
        private ArrayList<StatusBarNotification> mNotificationList = new ArrayList<StatusBarNotification>();

        private final LayoutInflater mInflater;

        NotificationAdapter(Context context) {
            mInflater = LayoutInflater.from(context);
        }

        @Override
        public NotificationViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if (viewType == ITEM_TYPE_NORMAL) {
                View view = mInflater.inflate(R.layout.item_notification, parent, false);
                return new NotificationViewHolder(view);
            } else {
                View view = mInflater.inflate(R.layout.item_notification_progress, parent, false);
                return new NotificationProgressViewHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(NotificationViewHolder holder, int position) {
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

        @Override
        public int getItemViewType(int position) {
            StatusBarNotification statusBarNotification = mNotificationList.get(position);
            Notification notification = statusBarNotification.getNotification();
            int progress = notification.extras.getInt(Notification.EXTRA_PROGRESS, -1);
            int maxProgress = notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX, -1);
            Log.e(TAG, "getItemViewType: progress = " + progress + ", maxProgress = " + maxProgress);
            if (progress >= 0 && maxProgress > 0) {
                return ITEM_TYPE_PROGRESS;
            } else {
                return ITEM_TYPE_NORMAL;
            }
        }

        void updateData(StatusBarNotification[] datas) {
            mNotificationsMap.clear();
            for (StatusBarNotification data : datas) {
                String type = data.getNotification().extras.getString("extra_type", "");
                Log.e(TAG, "updateData: id = " + data.getId());
                Log.e(TAG, "updateData: type = " + type + ", packagesName = " + data.getPackageName());
                if (!shouldFilterOut(data)) {
                    mNotificationsMap.put(data.getId(), data);
                }
            }
            Log.e(TAG, "updateData: size = " + mNotificationsMap.size());
            filterAndSort();
            if (mNotificationList.size() > 0) {
                hideNoMsgView();
            } else {
                showNoMsgView();
            }

            notifyDataSetChanged();
        }

        void removeItem(StatusBarNotification notification) {
            StatusBarNotification sbn = mNotificationsMap.remove(notification.getId());
            if (sbn == null) {
                Log.e(TAG, "removeItem: notification not exit in mNotificationMap");
                return;
            }

            //region find position in List
            int pointer = -1;
            int size = mNotificationList.size();
            for (int i = 0; i < size; i++) {
                if (notification.getId() == mNotificationList.get(i).getId()) {
                    Log.e(TAG, "removeItem: equals");
                    pointer = i;
                    break;
                }
            }
            if (pointer >= 0) {
                mNotificationList.remove(pointer);
                notifyItemRemoved(pointer);
                Log.e(TAG, "removeItem: notifyItemRemoved");
            } else {
                notifyDataSetChanged();
                Log.e(TAG, "removeItem: notifyDataSetChanged, " +
                        "notification null exit, id = " + notification.getId());
            }
            //endregion
        }

        void putItem(StatusBarNotification notification) {
            mNotificationsMap.put(notification.getId(), notification);
            filterAndSort();
            notifyDataSetChanged();
        }

        private void filterAndSort() {
            mNotificationList.clear();
            final int N = mNotificationsMap.size();
            for (int i = 0; i < N; i++) {
                StatusBarNotification sbn = mNotificationsMap.valueAt(i);
                if (shouldFilterOut(sbn)) {
                    continue;
                }

                mNotificationList.add(sbn);
            }

            Collections.sort(mNotificationList, mComparator);
        }

        /**
         * 过滤第三方应用的通知
         *
         * @return true if notification should filter, otherwise.
         */
        private boolean shouldFilterOut(StatusBarNotification notification) {
            String type = notification.getNotification().extras.getString("extra_type", "");
            Log.e(TAG, "shouldFilterOut: id = " + notification.getId() + ", type = " + type);
            if ("readboy".equalsIgnoreCase(type)) {
                return false;
            }
            return true;
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

        private final Comparator<StatusBarNotification> mComparator = new Comparator<StatusBarNotification>() {

            @Override
            public int compare(StatusBarNotification na, StatusBarNotification nb) {
                return (int) (nb.getNotification().when - na.getNotification().when);
            }
        };
    }

    class NotificationViewHolder extends RecyclerView.ViewHolder {
        private static final String TAG = "NotificationViewHolder";

        private View itemView;
        private ImageView mIcon;
        private TextView mTitle;
        private DateTimeView mTimeView;
        private TextView mText;
        private StatusBarNotification mStatusBarNotification;

        NotificationViewHolder(final View itemView) {
            super(itemView);
            mIcon = (ImageView) itemView.findViewById(R.id.small_icon);
            mTitle = (TextView) itemView.findViewById(R.id.content_title);
            mTimeView = (DateTimeView) itemView.findViewById(R.id.content_time);
            mText = (TextView) itemView.findViewById(R.id.content_text);
            View parent = itemView.findViewById(R.id.item_notification_parent);
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
                            isSendTo = true;
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
            mTitle.setText(title);
            mText.setText(content);
//            int smallIcon = notification.extras.getInt(Notification.EXTRA_SMALL_ICON);
//            if (smallIcon != 0) {
//                mIcon.setImageResource(smallIcon);
//            } else {
//                mIcon.setImageResource(R.drawable.app_icon_default);
//            }
            Drawable icon = getIcon(NotificationActivity.this, statusBarNotification);
            if (icon!=null) {
                mIcon.setImageDrawable(icon);
            } else {
                mIcon.setImageResource(R.drawable.app_icon_default);
            }
            if (showWhen && time != 0) {
                mTimeView.setTime(time);
            }
        }
    }

    public static Drawable getIcon(Context context, StatusBarNotification statusBarNotification) {
        Resources r = null;

        if (statusBarNotification.getPackageName() != null) {
            try {
                int userId = statusBarNotification.getUser().getIdentifier();
                if (userId == UserHandle.USER_ALL) {
                    userId = UserHandle.USER_OWNER;
                }
                r = context.getPackageManager()
                        .getResourcesForApplicationAsUser(statusBarNotification.getPackageName(), userId);
            } catch (PackageManager.NameNotFoundException ex) {
                Log.e(TAG, "Icon package not found: " + statusBarNotification.getPackageName());
                return null;
            }
        } else {
            r = context.getResources();
        }

        if (statusBarNotification.getNotification().icon == 0) {
            return null;
        }

        try {
            return r.getDrawable(statusBarNotification.getNotification().icon);
        } catch (RuntimeException e) {
            Log.w(TAG, "Icon not found in "
                    + (statusBarNotification.getPackageName() != null ? statusBarNotification.getNotification().icon : "<system>")
                    + ": " + Integer.toHexString(statusBarNotification.getNotification().icon));
        }

        return null;
    }

    class NotificationProgressViewHolder extends NotificationViewHolder {

        private ProgressBar mProgressBar;
        private TextView mProgressTv;

        NotificationProgressViewHolder(View itemView) {
            super(itemView);
            mProgressBar = (ProgressBar) itemView.findViewById(R.id.notification_progress_bar);
            mProgressTv = (TextView) itemView.findViewById(R.id.notification_progress);

        }

        @Override
        void bindNotification(StatusBarNotification statusBarNotification) {
            super.bindNotification(statusBarNotification);
            Notification notification = statusBarNotification.getNotification();
            int progress = notification.extras.getInt(Notification.EXTRA_PROGRESS, 0);
            int maxProgress = notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX, -1);
            mProgressBar.setMax(maxProgress);
            mProgressBar.setProgress(progress);
            Log.e(TAG, "bindNotification: progress = " + progress);
            int percent = progress * 100 / maxProgress;
            mProgressTv.setText(String.valueOf(percent + "%"));
        }
    }

}

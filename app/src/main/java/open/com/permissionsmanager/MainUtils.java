package open.com.permissionsmanager;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.os.SystemClock;

import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static open.com.permissionsmanager.ValidatePermissionsBroadcastReceiver.GENERIC_REQUEST_CODE;

import androidx.core.app.NotificationCompat;



public class MainUtils {

    public static final String SCAN = "SCAN";
    public static final String SHARED_PREFERENCES_KEY_IGNORED_APPLICATIONS_WARN_TIMESTAMP = "SHARED_PREFERENCES_KEY_IGNORED_APPLICATIONS_WARN_TIMESTAMP";
    public static final String SHARED_PREFERENCES_KEY_LAST_ALARM_TIME = "SHARED_PREFERENCES_KEY_LAST_ALARM_TIME";
    public static final String SHARED_PREF_KEY_LAST_SCAN_TIME = "LAST_SCAN_TIME";
    public static final int ONE_MINUTE = 60 * 1000;
    public static final int FIVE_MINUTES = 1 * ONE_MINUTE;
    public static final long ALARM_INTERVAL = ONE_MINUTE * 30;

    public static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(context.getString(R.string.permissions_manager), context.MODE_PRIVATE);
    }

    private static Intent getIntentToBroadcastValidatePermissions(Context context){
        return new Intent(context.getApplicationContext(), ValidatePermissionsBroadcastReceiver.class)
                    .setAction(SCAN);
    }

    private static boolean hasItBeenAlarmIntervalSinceLastAlarm(Context context) {
        return (getSharedPreferences(context).getLong("SHARED_PREFERENCES_KEY_LAST_ALARM_TIME", 0)  + ALARM_INTERVAL) <  System.currentTimeMillis();
    }

    public static void setAlarm(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, GENERIC_REQUEST_CODE, getIntentToBroadcastValidatePermissions(context), FLAG_UPDATE_CURRENT| FLAG_IMMUTABLE);
        alarmManager.cancel(pendingIntent);
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + ALARM_INTERVAL, pendingIntent);

    }

    public static void sort(List<AndroidApplication> applications) {
        Collections.sort(applications, new Comparator<AndroidApplication>() {
            @Override
            public int compare(AndroidApplication app1, AndroidApplication app2) {
                return app2.getWarnablePermissions().size() - app1.getWarnablePermissions().size();
            }
        });
    }

    public static long getLastIgnoredApplicationsWarningNotifiedInstance(Context context){
        return getSharedPreferences(context)
                .getLong(SHARED_PREFERENCES_KEY_IGNORED_APPLICATIONS_WARN_TIMESTAMP, 0);

    }

    public static void setLastIgnoredApplicationsWarningNotifiedInstance(Context context, long timestamp){
        getSharedPreferences(context)
                .edit()
                .putLong(SHARED_PREFERENCES_KEY_IGNORED_APPLICATIONS_WARN_TIMESTAMP, timestamp)
                .apply();
    }

    public static Calendar getCalendarInstanceRelativeFromNow(int field, int amount){
        Calendar instance = Calendar.getInstance();
        instance.add(field, amount);
        return instance;
    }

    public static Calendar getCalendarInstanceWith(long timestamp){
        Calendar instance = Calendar.getInstance();
        instance.setTimeInMillis(timestamp);
        return instance;
    }

    public static void updateLastAlarmTime(Context context) {
        getSharedPreferences(context).edit().putLong(SHARED_PREFERENCES_KEY_LAST_ALARM_TIME, System.currentTimeMillis()).apply();
    }

    public static boolean areScanResultsOlderThan5Mins(Context context) {
        Date last_scan_time = new Date(getSharedPreferences(context).getLong(SHARED_PREF_KEY_LAST_SCAN_TIME, 0));
        Date fiveMinsAgo = new Date(System.currentTimeMillis() - FIVE_MINUTES);
        return fiveMinsAgo.after(last_scan_time);
    }

    public static void updateLastScanTime(Context context) {
        getSharedPreferences(context)
                .edit()
                .putLong(SHARED_PREF_KEY_LAST_SCAN_TIME, System.currentTimeMillis())
                .apply();
    }

    public static void notify(String title, String content, int notificationCode, Context context) {
        final NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification notification = new NotificationCompat.Builder(context, "6684")
                .setSmallIcon(R.drawable.ic_warning_black_24dp)
                .setTicker(content)
                .setContentText(content)
                .setContentTitle(title)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .build();
        notificationManager.notify(6684, notification);
    }
}

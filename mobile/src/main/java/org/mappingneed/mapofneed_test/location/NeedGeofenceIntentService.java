package org.mappingneed.mapofneed_test.location;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Intent;
import android.content.Context;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import org.mappingneed.mapofneed_test.MainMapActivity;
import org.mappingneed.mapofneed_test.R;

import java.util.List;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p/>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
public class NeedGeofenceIntentService extends IntentService {
    public static final String TAG = "NeedGeofenceIntentServ";

    public static final String ACTION_NEARBYNEED = "org.mappingneed.mapofneed_test.location.action.NEARBYNEED";

    private static final int NOTIFICATION_ID_NEARBYNEED = 10;

    public NeedGeofenceIntentService() {
        super("NeedGeofenceIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_NEARBYNEED.equals(action)) {
                handleActionNearbyNeed(GeofencingEvent.fromIntent(intent));
            }
        }
    }

    private void handleActionNearbyNeed(GeofencingEvent geofencingEvent) {
        if (geofencingEvent.hasError()) {
            String errorMessage = "Geofencing error code:" + geofencingEvent.getErrorCode();
            Log.e(TAG, errorMessage);
            return;
        }

        int geofenceTransition = geofencingEvent.getGeofenceTransition();
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            pushNotification();
        }
    }

    private void pushNotification() {
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_need_nearby)
                        .setContentTitle(getResources().getString(R.string.need_nearby_title))
                        .setContentText(getResources().getString(R.string.need_nearby_body))
                        .setVibrate(new long[] { 20, 250, 50, 250 })
                        .setLights(0xFF00FFFF, 1000, 5000);
        Intent resultIntent = new Intent(this, MainMapActivity.class);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(MainMapActivity.class);
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        notificationBuilder.setContentIntent(resultPendingIntent);
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(NOTIFICATION_ID_NEARBYNEED, notificationBuilder.build());
    }
}

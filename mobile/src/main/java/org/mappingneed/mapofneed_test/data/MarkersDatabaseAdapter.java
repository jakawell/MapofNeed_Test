package org.mappingneed.mapofneed_test.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * Created by Jesse on 3/20/2015.
 */
public class MarkersDatabaseAdapter {

    private Context mContext;
    private SQLiteDatabase mMarkersDatabase;

    public MarkersDatabaseAdapter(Context context) {
        mContext = context;
    }

    public void open() {
        MarkersDatabaseOpenHelper openHelper = new MarkersDatabaseOpenHelper(mContext);
        mMarkersDatabase = openHelper.getWritableDatabase();
    }

    public void close() {
        mMarkersDatabase.close();
        mMarkersDatabase = null;
    }

    public Cursor getAllMarkers() {
        return mMarkersDatabase.rawQuery("SELECT * FROM " + MarkersDatabaseOpenHelper.TABLE_NAME + ";", null);
    }

    public void addMarker(String key, String value, double latitude, double longitude) {
        ContentValues values = new ContentValues();
        values.put("Key", key);
        values.put("Value", value);
        values.put("Latitude", latitude);
        values.put("Longitude", longitude);
        mMarkersDatabase.insert(MarkersDatabaseOpenHelper.TABLE_NAME, null, values);
    }

    public void updateMarkerValue(String key, String value) {
        ContentValues values = new ContentValues();
        values.put("Value", value);
        mMarkersDatabase.update(MarkersDatabaseOpenHelper.TABLE_NAME, values, "Key = ?", new String[] { key });
    }

    public void removeMarker(String key) {
        mMarkersDatabase.delete(MarkersDatabaseOpenHelper.TABLE_NAME, "Key = ?", new String[] { key });
    }
}

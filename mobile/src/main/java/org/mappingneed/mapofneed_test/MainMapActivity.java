package org.mappingneed.mapofneed_test;

import android.app.PendingIntent;
import android.content.Intent;
import android.database.Cursor;
import android.location.Location;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.*;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;

import org.mappingneed.mapofneed_test.data.MarkersDatabaseAdapter;
import org.mappingneed.mapofneed_test.location.NeedGeofenceIntentService;

import java.util.ArrayList;
import java.util.UUID;

public class MainMapActivity extends FragmentActivity implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private GoogleApiClient mGoogleApiClient;
    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private SlidingUpPanelLayout mSlidePanel;
    private TextView mSlidePanelInfoName;
    private EditText mSlidePanelNameEdit;
    private Marker mSelectedMarker;

    private PendingIntent mGeofencePendingIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_map);
        mSlidePanel = (SlidingUpPanelLayout)findViewById(R.id.sliding_layout);
        mSlidePanelInfoName = (TextView)findViewById(R.id.info_name);
        mSlidePanelNameEdit = (EditText)findViewById(R.id.marker_name_box);
        final Button saveButton = (Button)findViewById(R.id.save_marker_button);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveMarker();
            }
        });
        final Button deleteButton = (Button)findViewById(R.id.delete_marker_button);
        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteMarker();
            }
        });
        buildGoogleApiClient();
        setUpMapIfNeeded();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mGoogleApiClient != null){
            mGoogleApiClient.connect();
        }
    }

    @Override
    protected void onStop() {
        if (mGoogleApiClient != null){
            mGoogleApiClient.disconnect();
        }
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    /**
     * Sets up the map if it is possible to do so (i.e., the Google Play services APK is correctly
     * installed) and the map has not already been instantiated.. This will ensure that we only ever
     * call {@link #setUpMap()} once when {@link #mMap} is not null.
     * <p/>
     * If it isn't installed {@link SupportMapFragment} (and
     * {@link com.google.android.gms.maps.MapView MapView}) will show a prompt for the user to
     * install/update the Google Play services APK on their device.
     * <p/>
     * A user can return to this FragmentActivity after following the prompt and correctly
     * installing/updating/enabling the Google Play services. Since the FragmentActivity may not
     * have been completely destroyed during this process (it is likely that it would only be
     * stopped or paused), {@link #onCreate(Bundle)} may not be called again so we should call this
     * method in {@link #onResume()} to guarantee that it will be called.
     */
    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
                mMap.setMyLocationEnabled(true);
            }
        }
    }

    /**
     * This should only be called once and when we are sure that {@link #mMap} is not null.
     */
    private void setUpMap() {
        MarkersDatabaseAdapter markersDb = new MarkersDatabaseAdapter(this);
        markersDb.open();
        Cursor markers = markersDb.getAllMarkers();
        while (markers.moveToNext()) {
            UUID id = UUID.fromString(markers.getString(0));
            String title = markers.getString(1);
            double latitude = markers.getDouble(2);
            double longitude = markers.getDouble(3);
            LatLng point = new LatLng(latitude, longitude);
            mMap.addMarker(new MarkerOptions().position(point).title(title).snippet(id.toString()));

            addGeofence(id.toString(), latitude, longitude);
        }
        markersDb.close();

        mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(LatLng latLng) {
                addPoint(latLng);
            }
        });
        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                deselectPoint();
            }
        });
        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                selectPoint(marker, false);
                return false;
            }
        });
    }

   private PendingIntent getGeofencePendingIntent() {
        if (mGeofencePendingIntent == null) {
            Intent intent = new Intent(this, NeedGeofenceIntentService.class);
            intent.setAction(NeedGeofenceIntentService.ACTION_NEARBYNEED);
            mGeofencePendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        return mGeofencePendingIntent;
    }

    private void addGeofence(String id, double latitude, double longitude) {
        if (mGoogleApiClient == null || !mGoogleApiClient.isConnected())
            return;

        Geofence newGeoFence = new Geofence.Builder()
                .setRequestId(id)
                .setCircularRegion(latitude, longitude, 150)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .build();
        GeofencingRequest.Builder builder = new GeofencingRequest.Builder();
        builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER);
        builder.addGeofence(newGeoFence);
        LocationServices.GeofencingApi.addGeofences(mGoogleApiClient, builder.build(), getGeofencePendingIntent());
    }

    private void removeGeofence(String id) {
        if (mGoogleApiClient == null || !mGoogleApiClient.isConnected())
            return;

        ArrayList<String> stupidList = new ArrayList<>();
        stupidList.add(id);
        LocationServices.GeofencingApi.removeGeofences(mGoogleApiClient, stupidList);
    }

    private void addPoint(LatLng latLng) {
        if (mMap != null) {
            UUID id = UUID.randomUUID();
            String title = "";
            MarkersDatabaseAdapter markersDb = new MarkersDatabaseAdapter(this);
            markersDb.open();
            markersDb.addMarker(id.toString(), title, latLng.latitude, latLng.longitude);
            markersDb.close();

            Marker newMarker = mMap.addMarker(new MarkerOptions().position(latLng).title(title).snippet(id.toString()));
            newMarker.showInfoWindow();
            CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, 17);
            mMap.animateCamera(cameraUpdate);
            selectPoint(newMarker, true);

            addGeofence(id.toString(), latLng.latitude, latLng.longitude);
        }
    }

    private void selectPoint(Marker marker, boolean expandPanel) {
        mSelectedMarker = marker;
        mSlidePanel.setPanelState(expandPanel ? SlidingUpPanelLayout.PanelState.ANCHORED : SlidingUpPanelLayout.PanelState.COLLAPSED);
        setInfoText(marker.getTitle());
        if (marker.getTitle().startsWith("New Location!")) {
            mSlidePanelNameEdit.setText("");
        }
        else {
            mSlidePanelNameEdit.setText(marker.getTitle());
        }
    }

    private void deselectPoint() {
        mSlidePanel.setPanelState(SlidingUpPanelLayout.PanelState.HIDDEN);
    }

    private void setInfoText(CharSequence text) {
        if (mSlidePanelInfoName != null) {
            mSlidePanelInfoName.setText(text);
        }
    }

    private void saveMarker() {
        if (mSelectedMarker != null && mSlidePanelNameEdit != null) {
            MarkersDatabaseAdapter markersDatabaseAdapter = new MarkersDatabaseAdapter(this);
            markersDatabaseAdapter.open();
            markersDatabaseAdapter.updateMarkerValue(mSelectedMarker.getSnippet(), mSlidePanelNameEdit.getText().toString());
            markersDatabaseAdapter.close();

            mSelectedMarker.hideInfoWindow();
            mSelectedMarker.setTitle(mSlidePanelNameEdit.getText().toString());
            mSelectedMarker.showInfoWindow();
            setInfoText(mSlidePanelNameEdit.getText());
        }
    }

    private void deleteMarker() {
        if (mSelectedMarker != null) {
            removeGeofence(mSelectedMarker.getSnippet());

            mSlidePanel.setPanelState(SlidingUpPanelLayout.PanelState.HIDDEN);
            MarkersDatabaseAdapter markersDatabaseAdapter = new MarkersDatabaseAdapter(this);
            markersDatabaseAdapter.open();
            markersDatabaseAdapter.removeMarker(mSelectedMarker.getSnippet());
            markersDatabaseAdapter.close();
            mSelectedMarker.remove();
            mSelectedMarker = null;
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        Location lastLocation = LocationServices.FusedLocationApi.getLastLocation(
                mGoogleApiClient);
        if (lastLocation != null && mMap != null) {
            LatLng latLng = new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude());
            CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, 17);
            mMap.animateCamera(cameraUpdate);
        }

        MarkersDatabaseAdapter markersDb = new MarkersDatabaseAdapter(this);
        markersDb.open();
        Cursor markers = markersDb.getAllMarkers();
        while (markers.moveToNext()) {
            UUID id = UUID.fromString(markers.getString(0));
            double latitude = markers.getDouble(2);
            double longitude = markers.getDouble(3);
            addGeofence(id.toString(), latitude, longitude);
        }
        markersDb.close();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Toast.makeText(this, R.string.fail_toast, Toast.LENGTH_LONG).show();
    }
}

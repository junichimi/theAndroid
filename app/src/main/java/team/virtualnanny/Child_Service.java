package team.virtualnanny;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;


public class Child_Service extends Service {

    public static double StepSize = 0.6;
    private MyLocationListener myLocationListener;
    private LocationManager myManager;
    private Location lastLocation;
    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener mAuthListener;
    private DatabaseReference userRef;
    private String currentUserID;
    private int numSteps = 0;
    boolean busyFlag = false;
    int LOCATION_UPDATE_INTERVAL = 5 * 1000;  // 5 is the number of seconds
    int LOCATION_HISTORY_UPDATE_INTERVAL = 600 * 1000; // 600 is the number of seconds = 10 minutes
    int historyCounter = 0;
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        Log.d("service","onCreate");
        super.onCreate();

        mAuth = FirebaseAuth.getInstance();
        mAuthListener = new FirebaseAuth.AuthStateListener() { // stop running if it finds that user is not logged in anymore
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user == null) {
                    Toast.makeText(getApplicationContext(), "Service is stopped", Toast.LENGTH_SHORT).show();
                    Child_Service.this.stopSelf();
                }
            }
        };
        mAuth.addAuthStateListener(mAuthListener);
		
        currentUserID = FirebaseAuth.getInstance().getCurrentUser().getUid();

        userRef = FirebaseDatabase.getInstance().getReference().child("users").child(currentUserID);

        userRef.child("numSteps").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                numSteps = Integer.parseInt(dataSnapshot.getValue().toString());
                createNotificationForStartForeground();
                myLocationListener = new MyLocationListener();
                myManager = (LocationManager) getSystemService(LOCATION_SERVICE);
                if(!checkLocationPermission()){
                    return;
                }
                myManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, LOCATION_UPDATE_INTERVAL, 0, myLocationListener);
                myManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, LOCATION_UPDATE_INTERVAL, 0, myLocationListener);
                Log.d("service","i have reached here");
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {}
        });
    }


    @Override
    public void onDestroy() {
        Log.d("service","onDestroy");
        super.onDestroy();
        if (myManager != null && myLocationListener != null) {
            myManager.removeUpdates(myLocationListener);
        }
		
        if (mAuthListener != null) {
            mAuth.removeAuthStateListener(mAuthListener);
        }
		
    }

    private boolean checkLocationPermission(){
        Log.d("service","checkLocationPermission");
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(getApplicationContext(), "Need permission to use internet", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void createNotificationForStartForeground() {
        Log.d("service","createNotificationForStartForeground");

        Intent intent = new Intent(getApplicationContext(), MapsActivity.class);
        intent.putExtra("fromNotification", true);
        PendingIntent pIntent = PendingIntent.getActivity(getApplicationContext(), (int) System.currentTimeMillis(), intent, 0);
        Notification notification = new NotificationCompat.Builder(this)
                .setContentTitle(getResources().getString(R.string.app_name))
                .setContentText("Virtual Nanny Service Started")
                .setContentIntent(pIntent)
                .setAutoCancel(false)
                .setOngoing(true)
                .build();
        startForeground(1337, notification);
    }
    private void setLastLocation(Location location){
        Log.d("service","setLastLocation");
        busyFlag = true;
        float distance = 0;
        if(this.lastLocation != null){
            distance = lastLocation.distanceTo(location);
        }else {
            this.lastLocation = location;
        }

        if(distance < 8) { // if distance walked is less than 25 meters
            numSteps += ((int)(distance / StepSize));
        }

        double lastLatitude = location.getLatitude();
        double lastLongitude = location.getLatitude();

        Map<String, Object> numStepObject = new HashMap<String, Object>(); //
        numStepObject.put("numSteps", numSteps);
        numStepObject.put("lastLatitude", lastLatitude);
        numStepObject.put("lastLongitude", lastLongitude);
        userRef.updateChildren(numStepObject); // updates numSteps, latitude and longitude of child

        if(historyCounter > LOCATION_HISTORY_UPDATE_INTERVAL) {
            Map<String, Object> locationHistory = new HashMap<String, Object>(); //
            locationHistory.put("Latitude", numSteps);
            locationHistory.put("Longitude", lastLatitude);
            String timestamp = String.valueOf(System.currentTimeMillis());

            userRef.child("locationHistory").child(timestamp).updateChildren(locationHistory);

//            userRef.child("locationHistory").
            historyCounter = 0;
        }


        this.lastLocation = location;
        Toast.makeText(Child_Service.this, "Number of steps:"+numSteps, Toast.LENGTH_LONG).show();
        busyFlag = false;

    }


    class MyLocationListener implements LocationListener {
        @Override
        public void onLocationChanged(Location location) {
            Log.d("service","onLocationChanged");
            if(location == null){
                return;
            }

            historyCounter += LOCATION_UPDATE_INTERVAL;
            //Toast.makeText(Guardian_Service.this, "Latitude:"+location.getLatitude()+"Longitude:"+location.getLongitude(), Toast.LENGTH_LONG).show();
            if (location.getSpeed() * 60 * 60 / 1000 < 10 && location.getSpeed() * 60 * 60 / 1000 > 2 && busyFlag == false) {
                setLastLocation(location);
            }
        }

        @Override
        public void onStatusChanged(String s, int i, Bundle bundle) {

        }

        @Override
        public void onProviderEnabled(String s) {

        }

        @Override
        public void onProviderDisabled(String s) {

        }
    }
}

package team.virtualnanny;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.telephony.SmsManager;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.security.Guard;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

public class Guardian_SetFenceActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener mAuthListener;
    private DatabaseReference mDatabase;
    private ProgressDialog progress;
    private List<Db_fence> existingFences;
    private List<Marker> existingMarkers;
    private List<Circle> existingCircles;
    private String currentUserID;

    private Marker lastMarkerClicked = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        existingFences = new ArrayList<Db_fence>();
        existingMarkers = new ArrayList<Marker>();
        existingCircles = new ArrayList<Circle>();

        progress = new ProgressDialog(Guardian_SetFenceActivity.this);
        progress.setTitle("Loading");
        progress.setMessage("Wait while loading...");
        progress.setCancelable(false); // disable dismiss by tapping outside of the dialog

        mDatabase = FirebaseDatabase.getInstance().getReference();
        mAuth = FirebaseAuth.getInstance();
        mAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user == null) {
                    Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    finish();
                    startActivity(intent);
                }
            }
        };
        currentUserID = mAuth.getCurrentUser().getUid();

        setContentView(R.layout.guardian_set_fence);
        setTitle("Set Geo fence");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true); // enables back button on the action bar
        getSupportActionBar().setBackgroundDrawable(new ColorDrawable(0xFF000000)); // sets the actions bar as black


        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);


    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.geofence, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                break;
            case R.id.action_add:
                Toast.makeText(getApplicationContext(), "Click on anywhere on the map to add the center of the fence",
                        Toast.LENGTH_LONG).show();

                mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
                    @Override
                    public void onMapClick(LatLng point) {
                        Location location = new Location("Test");
                        location.setLatitude(point.latitude);
                        location.setLongitude(point.longitude);
                        location.setTime(new Date().getTime()); //Set time as current Date

                        //Convert Location to LatLng
                        LatLng newLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                        mMap.moveCamera(CameraUpdateFactory.newLatLng(newLatLng));

                        MarkerOptions markerOptions = new MarkerOptions()
                                .position(newLatLng)
                                .title(newLatLng.toString());

                        final Marker marker = mMap.addMarker(markerOptions);

                        AlertDialog.Builder builder = new AlertDialog.Builder(Guardian_SetFenceActivity.this);
                        builder.setTitle("Add Fence");

                        LinearLayout layout = new LinearLayout(Guardian_SetFenceActivity.this);
                        layout.setOrientation(LinearLayout.VERTICAL);

                        final EditText input_fenceName = new EditText(Guardian_SetFenceActivity.this);
                        input_fenceName.setInputType(InputType.TYPE_CLASS_TEXT);
                        input_fenceName.setHint("Fence Name");
                        layout.addView(input_fenceName);
                        CircleOptions circleOptions = new CircleOptions()
                                .center(newLatLng)
                                .radius(0)
                                .zIndex(20);
                        final Circle mapCircle = mMap.addCircle(circleOptions);
                        final SeekBar input_radius = new SeekBar(Guardian_SetFenceActivity.this);
                        input_radius.setMax(10000);
                        input_radius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                            @Override
                            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                                Log.d("seekbar", ""+progress);
                                mapCircle.setRadius(progress);
                            }

                            @Override
                            public void onStartTrackingTouch(SeekBar seekBar) {

                            }

                            @Override
                            public void onStopTrackingTouch(SeekBar seekBar) {

                            }
                        });
                        //input_radius.setInputType(InputType.TYPE_CLASS_NUMBER);
                        //input_radius.setHint("Input Radius");
                        layout.addView(input_radius);

                        final RadioGroup rg = new RadioGroup(Guardian_SetFenceActivity.this); //create the RadioGroup
                        rg.setOrientation(RadioGroup.HORIZONTAL);//or RadioGroup.VERTICAL

                        final RadioButton rb_safezone = new RadioButton(Guardian_SetFenceActivity.this);
                        rb_safezone.setText(R.string.fence_safezone);
                        rb_safezone.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                mapCircle.setStrokeColor(Color.GREEN);
                            }
                        });
                        rg.addView(rb_safezone);

                        final RadioButton rb_dangerzone = new RadioButton(Guardian_SetFenceActivity.this);
                        rb_dangerzone.setText(R.string.fence_dangerzone);
                        rb_dangerzone.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                mapCircle.setStrokeColor(Color.RED);
                            }
                        });
                        rg.addView(rb_dangerzone);


                        layout.addView(rg);
                        builder.setView(layout);

                        // Set up the buttons
                        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            final String fenceName = input_fenceName.getText().toString().trim();
                            final int checkedButton = rg.getCheckedRadioButtonId();
                            final double radius = mapCircle.getRadius();
                            final double newMarkerLongitude = marker.getPosition().longitude;
                            final double newMarkerLatitude = marker.getPosition().latitude;
                            boolean flag = true;

                            if(fenceName.isEmpty()) {
                                Toast.makeText(Guardian_SetFenceActivity.this, "Please do not leave fence Name empty",Toast.LENGTH_SHORT).show();
                                flag = false;
                            }else if(checkedButton == -1) {
                                Toast.makeText(Guardian_SetFenceActivity.this, "Please select fence safety",Toast.LENGTH_SHORT).show();
                                flag = false;
                            } else {
                                // latitude is y
                                // longitude is x
                                // checks if fences will overlap
                                if(existingFences != null && !existingFences.isEmpty()) {
                                    for (Db_fence fence : existingFences) {
                                        double x2 = newMarkerLongitude;
                                        double x1 = fence.getLongitude();
                                        double y2 = newMarkerLatitude;
                                        double y1 = fence.getLatitude();

                                        Location loc1 = new Location("");
                                        loc1.setLatitude(fence.getLatitude());
                                        loc1.setLongitude(fence.getLongitude());

                                        Location loc2 = new Location("");
                                        loc2.setLatitude(newMarkerLatitude);
                                        loc2.setLongitude(newMarkerLongitude);

                                        float distance = loc1.distanceTo(loc2);
//                                        double distance = Math.sqrt(Math.pow((x2 - x1), 2) + Math.pow((y2 - y1), 2));
                                        double radius1 = fence.getRadius();
                                        double radius2 = mapCircle.getRadius();

                                        Log.d("radius1",""+radius1);
                                        Log.d("radius2",""+radius2);
                                        Log.d("distance",""+distance);

                                        if (distance < radius1 + radius2) {
                                            Toast.makeText(Guardian_SetFenceActivity.this, "Circles are overlapping", Toast.LENGTH_SHORT).show();
                                            flag = false;
                                            break;
                                        }
                                    }
                                }
                            }

                            if(flag == false ) {
                                marker.remove();
                                mapCircle.remove();
                                return;
                            }

                            progress.show();
                            final DatabaseReference users = mDatabase.child("users");

                            users.child(currentUserID).addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(DataSnapshot dataSnapshot) {

                                    View radioButton = rg.findViewById(checkedButton);
                                    int safetyID = rg.indexOfChild(radioButton) + 1;


                                    Map<String, Object> fenceProperties = new HashMap<String, Object>(); //
                                    fenceProperties.put("radius", radius);
                                    fenceProperties.put("safety", safetyID);
                                    fenceProperties.put("longitude",newMarkerLongitude);
                                    fenceProperties.put("latitude",newMarkerLatitude);

                                    users.child(currentUserID).child("Fences").child(fenceName).updateChildren(fenceProperties);
                                    marker.setTitle(fenceName);
                                    Toast.makeText(Guardian_SetFenceActivity.this, "Fence has been added.",Toast.LENGTH_SHORT).show();

                                    existingCircles.add(mapCircle);
                                    existingMarkers.add(marker);
                                    existingFences.add(new Db_fence(radius,safetyID,newMarkerLongitude,newMarkerLatitude));
                                    progress.dismiss();
                                }

                                @Override
                                public void onCancelled(DatabaseError databaseError) {
                                    marker.remove();
                                    mapCircle.remove();
                                    progress.dismiss();
                                }
                            });
                            }
                        });
                        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            mMap.setOnMapClickListener(null);
                            dialog.cancel();
                            }
                        });

                        AlertDialog dialog = builder.create();
                        dialog.setCancelable(false);
                        WindowManager.LayoutParams wmlp = dialog.getWindow().getAttributes();

                        wmlp.gravity = Gravity.BOTTOM;
                        dialog.show();

                    }
                });
                break;
            case R.id.action_delete:
                // check if there was a marker clicked
                if(!lastMarkerClicked.equals(null)) {

                    for(int x = 0; x < existingMarkers.size();x++) {
                        if(lastMarkerClicked.equals(existingMarkers.get(x))) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(Guardian_SetFenceActivity.this);
                            builder.setTitle("Are you sure you want to delete Geofence '"+lastMarkerClicked.getTitle()+"'");

                            // Set up the buttons
                            final int finalX = x;
                            builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {

                                    String fenceName = existingMarkers.get(finalX).getTitle();

                                    existingMarkers.get(finalX).remove();
                                    existingCircles.get(finalX).remove();

                                    existingMarkers.remove(finalX);
                                    existingFences.remove(finalX);
                                    existingCircles.remove(finalX);
                                    mDatabase.child("users").child(currentUserID).child("Fences").child(fenceName).removeValue();
                                }
                            });
                            builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.cancel();
                                }
                            });

                            builder.show();

                            break;
                        }
                    }



                } else {
                    Toast.makeText(getApplicationContext(), "Please click on a marker and click on the delete button",
                            Toast.LENGTH_SHORT).show();
                }

                break;
            case R.id.action_search:
                AlertDialog.Builder builder = new AlertDialog.Builder(Guardian_SetFenceActivity.this);
                builder.setTitle("Please input the name of the geolocation:");

                final EditText input = new EditText(Guardian_SetFenceActivity.this);

                builder.setView(input);

                builder.setPositiveButton("Search", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String fenceName = input.getText().toString();
                        Log.d("Fence", fenceName);
                        for(Marker marker: existingMarkers) {
                            if(marker.getTitle().equals(fenceName)) {
                                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.getPosition(), 12.0f));
                                return;
                            }
                        }
                        Toast.makeText(getApplicationContext(), fenceName + " does not exist",
                                Toast.LENGTH_LONG).show();

                    }
                });
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

                builder.show();

                break;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);

        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                lastMarkerClicked = marker;
                return false;
            }
        });


        LatLng upCebu = new LatLng(10.3226, 123.8986);
//        mMap.moveCamera(CameraUpdateFactory.newLatLng(upCebu));
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(upCebu, 12.0f));

        progress.show();
        String currentUserID = mAuth.getCurrentUser().getUid();
        mDatabase.child("users").child(currentUserID).child("Fences").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists()) {
                    for(DataSnapshot datasnapshot : dataSnapshot.getChildren()) {
                        Db_fence fence = datasnapshot.getValue(Db_fence.class);

                        String fenceName = datasnapshot.getKey();
                        double fenceLatitude = fence.getLatitude();
                        double fenceLongitude = fence.getLongitude();

                        LatLng newLatLng = new LatLng(fenceLatitude, fenceLongitude);
                        MarkerOptions markerOptions = new MarkerOptions()
                                .position(newLatLng)
                                .title(fenceName);

                        final Marker marker = mMap.addMarker(markerOptions);

                        CircleOptions circleOptions = new CircleOptions()
                                .center(newLatLng)
                                .strokeColor(fence.getSafety() == 1 ? Color.GREEN : Color.RED)
                                .radius(fence.getRadius())
                                .zIndex(20);
                        final Circle mapCircle = mMap.addCircle(circleOptions);

                        existingMarkers.add(marker);
                        existingCircles.add(mapCircle);
                        existingFences.add(fence);
                    }
                }
                progress.dismiss();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

    }

    @Override
    public void onStart() {
        super.onStart();
        mAuth.addAuthStateListener(mAuthListener);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mAuthListener != null) {
            mAuth.removeAuthStateListener(mAuthListener);
        }
	}	
	
}

package com.example.urbansensorapp;

import androidx.fragment.app.FragmentActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener;

import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;


import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.Toast;
import android.graphics.Color;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MapsScreen extends FragmentActivity implements OnMapReadyCallback, OnMarkerClickListener {

    private GoogleMap mMap;
    private ScrollView currentTrains;
    private ArrayList<String> stationNames;
    private ArrayList<String> stationCodes;
    private ArrayList<String> stationLatitudes;
    private ArrayList<String> stationLongitudes;
    private Polyline mPolyline;
    private LatLng currentLocation;
    private Marker durationMarker;
    private String stationDataUrl = "http://api.irishrail.ie/realtime/realtime.asmx/" +
            "getStationDataByCodeXML_WithNumMins?NumMins=20&StationCode=";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);




        setContentView(R.layout.activity_google_map);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setOnMarkerClickListener(this);

        double currentLat = Double.valueOf(getIntent().getStringExtra("Current Latitude"));
        double currentLong = Double.valueOf(getIntent().getStringExtra("Current Longitude"));

        stationNames = (ArrayList<String>) getIntent().getSerializableExtra("Station Names");
        stationCodes = (ArrayList<String>) getIntent().getSerializableExtra("Station Codes");
        stationLatitudes = (ArrayList<String>) getIntent().getSerializableExtra("Station Latitudes");
        stationLongitudes = (ArrayList<String>) getIntent().getSerializableExtra("Station Longitudes");

        System.out.println(stationNames);
        // Add a marker in Sydney, Australia, and move the camera.
        LatLng sydney = new LatLng(-34, 151);
        LatLng Karachi = new LatLng(24.9056, 67.0822);
        LatLng dublin = new LatLng(24.9056, 67.0822);
        currentLocation = new LatLng(currentLat,currentLong);

        mMap.addMarker(new MarkerOptions().position(currentLocation).title("Current Location")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));

        for(int i = 0; i < stationCodes.size(); i++){
            mMap.addMarker(new MarkerOptions().position(new LatLng(Double.valueOf(
                    stationLatitudes.get(i)), Double.valueOf(stationLongitudes.get(i))))
                    .title(stationNames.get(i)));
        }

        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation,11.0f));
    }

    @Override
    public boolean onMarkerClick(final Marker marker) {

        if (mPolyline != null) {
            mPolyline.remove();
        }
        if(durationMarker != null){
            durationMarker.remove();
        }

        String url = getDirectionsUrl(currentLocation, marker.getPosition());

        RequestQueue ExampleRequestQueue = Volley.newRequestQueue(MapsScreen.this);

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest
                (Request.Method.GET, url, null, new Response.Listener<JSONObject>() {

                    @Override
                    public void onResponse(JSONObject response) {
                        //textView.setText("Response: " + response.toString());
                        Log.d("json",response.toString());
                        DirectionsJSONParser parser = new DirectionsJSONParser();
                        List<List<HashMap<String, String>>> routes = parser.parse(response);

                        ArrayList<LatLng> points = null;
                        PolylineOptions lineOptions = null;

                        // Traversing through all the routes
                        for(int i=0;i<routes.size();i++){
                            points = new ArrayList<LatLng>();
                            lineOptions = new PolylineOptions();

                            // Fetching i-th route
                            List<HashMap<String, String>> path = routes.get(i);

                            // Fetching all the points in i-th route
                            for(int j=0;j<path.size();j++){
                                HashMap<String,String> point = path.get(j);

                                double lat = Double.parseDouble(point.get("lat"));
                                double lng = Double.parseDouble(point.get("lng"));
                                LatLng position = new LatLng(lat, lng);

                                points.add(position);
                            }

                            // Adding all the points in the route to LineOptions
                            lineOptions.addAll(points);
                            lineOptions.width(8);
                            lineOptions.color(Color.RED);
                        }

                        BitmapDescriptor transparent = BitmapDescriptorFactory.fromResource(R.drawable.ic_action_name);
                        MarkerOptions options = new MarkerOptions()
                                .position(points.get(points.size()/2))
                                .title(parser.duration)
                                .icon(transparent)
                                .anchor((float) 0.5, (float) 0.5); //puts the info window on the polyline

                        durationMarker = mMap.addMarker(options);
                        durationMarker.showInfoWindow();

                        marker.setSnippet(parser.duration);
                        marker.showInfoWindow();


                        // Drawing polyline in the Google Map for the i-th route
                        if(lineOptions != null) {
                            if(mPolyline != null){
                                mPolyline.remove();
                            }
                            mPolyline = mMap.addPolyline(lineOptions);

                        }else
                            Toast.makeText(getApplicationContext(),"No route is found", Toast.LENGTH_LONG).show();
                    }
                }, new Response.ErrorListener() {

                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.d("json","hey");

                        // TODO: Handle error

                    }
                });


        ExampleRequestQueue.add(jsonObjectRequest);

        // Return false to indicate that we have not consumed the event and that we wish
        // for the default behavior to occur (which is for the camera to move such that the
        // marker is centered and for the marker's info window to open, if it has one).
        return false;
    }


    private String getDirectionsUrl(LatLng origin, LatLng dest) {

        // Origin of route
        String str_origin = "origin=" + origin.latitude + "," + origin.longitude;
        // Destination of route
        String str_dest = "destination=" + dest.latitude + "," + dest.longitude;
        String mode = "mode=walking";
        String key = "key=AIzaSyAEuLTXaShSqD41B1X0V4dmMLP-RDFhZ44";
        // Building the parameters to the web service
        String parameters = str_origin + "&" + str_dest + "&" + mode + "&" + key;

        // Output format
        String output = "json";

        // Building the url to the web service
        String url = "https://maps.googleapis.com/maps/api/directions/" + output + "?" + parameters;

        return url;
    }



}

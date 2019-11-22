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


import android.content.Intent;
import android.os.AsyncTask;
import android.os.Handler;
import android.view.View;


import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Color;

import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class MapsScreen extends FragmentActivity implements OnMapReadyCallback, OnMarkerClickListener {

    private GoogleMap mMap;
    private ArrayList<String> stationNames;
    private ArrayList<String> stationCodes;
    private ArrayList<String> stationLatitudes;
    private ArrayList<String> stationLongitudes;
    private ArrayList<String> listOfTrains;
    private ArrayList<String> listofTrainTimes;
    private Polyline mPolyline;
    private LatLng currentLocation;
    private String newSnippet;
    private Marker durationMarker;
    private String passUrl;
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

        // Get current cooridnates
        double currentLat = Double.valueOf(getIntent().getStringExtra("Current Latitude"));
        double currentLong = Double.valueOf(getIntent().getStringExtra("Current Longitude"));

        stationNames = (ArrayList<String>) getIntent().getSerializableExtra("Station Names");
        stationCodes = (ArrayList<String>) getIntent().getSerializableExtra("Station Codes");
        stationLatitudes = (ArrayList<String>) getIntent().getSerializableExtra("Station Latitudes");
        stationLongitudes = (ArrayList<String>) getIntent().getSerializableExtra("Station Longitudes");

        System.out.println(stationNames);

        currentLocation = new LatLng(currentLat,currentLong);

        // Add current location marker
        mMap.addMarker(new MarkerOptions().position(currentLocation).title("Current Location")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));

        // Overwrite marker infowindow to make it bigger
        // Adpated from https://stackoverflow.com/questions/13904651/android-google-maps-v2-how-to-add-marker-with-multiline-snippet
        mMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {

            @Override
            public View getInfoWindow(Marker arg0) {
                return null;
            }

            @Override
            public View getInfoContents(Marker marker) {

                LinearLayout info = new LinearLayout(MapsScreen.this);
                info.setOrientation(LinearLayout.VERTICAL);

                TextView title = new TextView(MapsScreen.this);
                title.setTextColor(Color.BLACK);
                title.setGravity(Gravity.CENTER);
                title.setTypeface(null, Typeface.BOLD);
                title.setText(marker.getTitle());

                TextView snippet = new TextView(MapsScreen.this);
                snippet.setTextColor(Color.GRAY);
                snippet.setText(marker.getSnippet());

                info.addView(title);
                info.addView(snippet);

                return info;
            }
        });

        // Add marker for every station
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

        if (!marker.getTitle().equals("Current Location")) {


            String markerStation = marker.getTitle();
            String markerStationCode = stationCodes.get(stationNames.indexOf(markerStation));

            passUrl = stationDataUrl + markerStationCode;

            // Get the trains due at the station via request to Irish Rail API
            new OncomingTrains().execute(passUrl);

            try {
                // Wait 1 second
                Thread.sleep(1000);

            } catch (Exception e) {
                e.printStackTrace();
            }
            newSnippet = "";

            // Populate snippet in marker window
            for (int i = 0; i < listOfTrains.size(); i++) {
                newSnippet += listOfTrains.get(i) + ": " + listofTrainTimes.get(i) + " mins" + "\n";
            }

            newSnippet = newSnippet.trim();

            // Url for Directions API Request
            String url = getDirectionsUrl(currentLocation, marker.getPosition());

            RequestQueue ExampleRequestQueue = Volley.newRequestQueue(MapsScreen.this);

            // Json Request from Directions API based on marker clicked and current location
            JsonObjectRequest jsonObjectRequest = new JsonObjectRequest
                    (Request.Method.GET, url, null, new Response.Listener<JSONObject>() {

                        @Override
                        public void onResponse(JSONObject response) {
                            Log.d("json", response.toString());
                            DirectionsJSONParser parser = new DirectionsJSONParser();
                            List<List<HashMap<String, String>>> routes = parser.parse(response);

                            ArrayList<LatLng> points = null;
                            PolylineOptions lineOptions = null;

                            // Traversing through all the routes
                            for (int i = 0; i < routes.size(); i++) {
                                points = new ArrayList<LatLng>();
                                lineOptions = new PolylineOptions();

                                // Fetching i-th route
                                List<HashMap<String, String>> path = routes.get(i);

                                // Fetching all the points in i-th route
                                for (int j = 0; j < path.size(); j++) {
                                    HashMap<String, String> point = path.get(j);

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

                            // Add marker to display duration (decided against this as only one marker
                            // can be open at a time
                            BitmapDescriptor transparent = BitmapDescriptorFactory.fromResource(R.drawable.ic_action_name);
                            MarkerOptions options = new MarkerOptions()
                                    .position(points.get(points.size() / 2))
                                    .title(parser.duration)
                                    .icon(transparent)
                                    .anchor((float) 0.5, (float) 0.5); //puts the info window on the polyline

                            durationMarker = mMap.addMarker(options);
                            durationMarker.showInfoWindow();

                            // Show marker window with distance and serving trains
                            marker.setSnippet("Estimated Travel Time: " + parser.duration + "\n" + newSnippet);
                            marker.showInfoWindow();


                            // Drawing polyline in the Google Map for the i-th route
                            if (lineOptions != null) {
                                if (mPolyline != null) {
                                    mPolyline.remove();
                                }
                                mPolyline = mMap.addPolyline(lineOptions);

                            } else
                                Toast.makeText(getApplicationContext(), "No route is found", Toast.LENGTH_LONG).show();
                        }
                    }, new Response.ErrorListener() {

                        @Override
                        public void onErrorResponse(VolleyError error) {
                            Log.d("json", "hey");
                        }
                    });
            ExampleRequestQueue.add(jsonObjectRequest);

        }

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
        // Method of travel
        String mode = "mode=walking";
        // API Key
        String key = "key=AIzaSyAEuLTXaShSqD41B1X0V4dmMLP-RDFhZ44";
        // Parameters for url to be requested through http
        String parameters = str_origin + "&" + str_dest + "&" + mode + "&" + key;

        // Output format
        String output = "json";

        // Building the url to the web service
        String url = "https://maps.googleapis.com/maps/api/directions/" + output + "?" + parameters;

        return url;
    }

    private class OncomingTrains extends AsyncTask<String, Integer, String> {

        @Override
        protected String doInBackground(String... url) {

            listOfTrains = new ArrayList<>();
            listofTrainTimes = new ArrayList<>();

            try {

                // Reading XML from Irish rail real time url
                // Source for XML/Dom instructions: https://howtodoinjava.com/xml/read-xml-dom-parser-example/
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();
                Document getAllCurrentTrainsDoc = db.parse(new URL(url[0]).openStream());

                System.out.println(url[0]);
                getAllCurrentTrainsDoc.getDocumentElement().normalize();

                NodeList trainsList = getAllCurrentTrainsDoc.getElementsByTagName("objStationData");


                for(int i = 0; i < trainsList.getLength(); i++){

                    Node node = trainsList.item(i);
                    Element eElement = (Element) node;

                    String trainDestination = eElement.getElementsByTagName("Destination").item(0).getTextContent();
                    String trainMins = eElement.getElementsByTagName("Duein").item(0).getTextContent();


                    listOfTrains.add(trainDestination);
                    listofTrainTimes.add(trainMins);
                }

            }
            catch (Exception e) {
                e.printStackTrace();
            }

            return "this string is passed to onPostExecute";
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
        }
    }



}

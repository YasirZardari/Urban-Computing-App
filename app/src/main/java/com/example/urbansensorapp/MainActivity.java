package com.example.urbansensorapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.content.Intent;
import android.os.*;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;


import java.math.RoundingMode;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.math.BigDecimal;


import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Element;


import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class MainActivity extends AppCompatActivity {

    private float pressureVal;
    private FusedLocationProviderClient fusedLocationClient;
    private SensorManager sensorManager;
    private Sensor pressureSensor;
    private boolean started = false;
    private Handler handler = new Handler();
    private File fileName;
    private File folder;
    private FileWriter fw;
    private List<String> data;
    private LocationManager mLocationManager;

    private String latitude;
    private String longitude;
    private String[] ListofStations;

    private String startTime;

    // Open data sources
    private String stationURL = "http://api.irishrail.ie/realtime/realtime.asmx/getStationDataByCodeXML?StationCode=perse&NumMins=30";
    private String stationList = "http://api.irishrail.ie/realtime/realtime.asmx/getAllStationsXML";
    private String currentTrainsURL = "http://api.irishrail.ie/realtime/realtime.asmx/getCurrentTrainsXML";

    private Button startButton;
    private TextView resultTextView;
    private TextView latitudeTextView;
    private TextView longitudeTextView;
    private Button stopButton;

    // Train Data from Cloud
    private ArrayList<String> stationCodes = new ArrayList<>();
    private ArrayList<String> stationNames = new ArrayList<>();
    private ArrayList<String> stationLatitudes = new ArrayList<>();
    private ArrayList<String> stationLongitudes = new ArrayList<>();


    private HashMap<String,String> airPressureTable= new HashMap<String,String>();


    // References to Firebase Cloud
    DatabaseReference reffAirPressure;
    DatabaseReference reffStationData;
    DatabaseReference reffCurrentTrainsData;




    // Sensor listener. Any change to current pressure triggers this method which
    // stores the new value in a variable used later in the program.
    SensorEventListener sensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            float[] values = sensorEvent.values;
            pressureVal = values[0];
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {

        }
    };


    // Location listener. Any change to current GPS cooridnates triggers this method which
    // stores the new values in variables used later in the program.
    LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            longitude = new BigDecimal(location.getLongitude()).setScale(3, RoundingMode.HALF_EVEN).toString();
            latitude = new BigDecimal(location.getLatitude()).setScale(3, RoundingMode.HALF_EVEN).toString();
            Log.d("latitude", latitude);

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
    };

    private void DataFusion(){

        stationCodes = new ArrayList<>();
        stationNames = new ArrayList<>();
        stationLatitudes = new ArrayList<>();
        stationLongitudes = new ArrayList<>();

        Log.d("stretch", "1");
        DatabaseReference getStationsRef = FirebaseDatabase.getInstance().getReference().child("Open Data").child("List of Stations");

        Log.d("stretch", "2");

        //Get datasnapshot at your "users" root node
        getStationsRef.addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        //Get map of users in datasnapshot
                        collectCurrentStations((Map<String,Object>) dataSnapshot.getValue());
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        //handle databaseError
                    }
                });

    }

    private void collectCurrentStations(Map<String,Object> trains) {

        //iterate through each user, ignoring their UID
        for (Map.Entry<String, Object> entry : trains.entrySet()){

            //Get user map
            Map singleTrain = (Map) entry.getValue();

            Double currentStationLat = Double.valueOf((String) singleTrain.get("Station Latitude"));
            Double currentStaionLog = Double.valueOf((String) singleTrain.get("Station Longitude"));

            float[] results = new float[1];

            double currLat = Double.parseDouble(latitude);
            double currLon = Double.parseDouble(longitude);

            // Get distance between two points
            Location.distanceBetween(currLat, currLon, currentStationLat, currentStaionLog, results);
            float distanceInMeters = results[0];


            if (distanceInMeters < 30000) {
                stationCodes.add((String) singleTrain.get("Station Code"));
                stationNames.add((String) singleTrain.get("Station Name"));
                stationLatitudes.add((String) singleTrain.get("Station Latitude"));
                stationLongitudes.add((String) singleTrain.get("Station Longitude"));
            }
        }

        System.out.println(stationNames.toString());
        System.out.println(stationCodes.toString());
        System.out.println(stationLatitudes.toString());
        System.out.println(stationLongitudes.toString());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        reffAirPressure = FirebaseDatabase.getInstance().getReference("Sensor Data/Air Pressure By GPS");
        reffStationData = FirebaseDatabase.getInstance().getReference("Open Data/List of Stations");
        reffCurrentTrainsData = FirebaseDatabase.getInstance().getReference("Open Data/Current Trains Running");


        // Check permission for location access.
        if (ActivityCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            return;
        }

        // Location Manager configuration. Update value with difference of 1 metre.
        mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0,
                1, locationListener);


        // Get initial coordinates from GPS. This is done as GPS will only update when it changes.
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {

                        if (location != null) {
                            longitude = new BigDecimal(location.getLongitude()).setScale(4, RoundingMode.HALF_EVEN).toString();
                            latitude = new BigDecimal(location.getLatitude()).setScale(4, RoundingMode.HALF_EVEN).toString();
                        }
                    }
                });

        // Make directory in phone storage
        folder = Environment.getExternalStorageDirectory();
        File dir = new File (folder.getAbsolutePath() + "/dir1/dir2");
        dir.mkdirs();


        // Write csv file with appropriate headings
        fileName = new File(dir,"UrbanData.csv");

        try{
            fw = new FileWriter(fileName);

            fw.append("AirPressure");
            fw.append(',');

            fw.append("Longtitude");
            fw.append(',');

            fw.append("Latitude");
            fw.append("\n");

        } catch (Exception e) {
        }

        // Initialise buttons and text views
        startButton = findViewById(R.id.startButton);
        resultTextView = findViewById((R.id.resultTextView));
        latitudeTextView = findViewById((R.id.latitudeTextView));
        longitudeTextView = findViewById((R.id.longitudeTextView));

        Button updateTrainButton = findViewById(R.id.UpdateTrainButton);
        Button getTrainBtn = findViewById(R.id.GetTrainDataButton);
        Button openMapButton = findViewById(R.id.OpenMapButton);



        stopButton = findViewById(R.id.stopButton);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);

        startButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                startTime = (Calendar.getInstance().getTime().toString());

                // Run service which allows the app to run in background
                startService(new Intent(MainActivity.this,MyService.class));


                start();
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                try{
                    fw.close();
                }catch (Exception e) {
                }
                stopService(new Intent(MainActivity.this,MyService.class));
                stop();

            }
        });

        updateTrainButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                // Run async class which runs code getting info from open data source
                new StationInfo().execute(stationURL, stationList, currentTrainsURL);
            }
        });

        getTrainBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                //DataFusion();
                // Run async class which runs code getting info from open data source
                //new StationInfo().execute(stationURL, stationList, currentTrainsURL);
            }
        });

        openMapButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                DataFusion();
                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    public void run() {
                        // Actions to do after 5 seconds
                        Intent intent = new Intent(MainActivity.this, MapsScreen.class);
                        intent.putExtra("Current Latitude", latitude);
                        intent.putExtra("Current Longitude", longitude);
                        intent.putExtra("Station Names", stationNames);
                        intent.putExtra("Station Codes", stationCodes);
                        intent.putExtra("Station Latitudes", stationLatitudes);
                        intent.putExtra("Station Longitudes", stationLongitudes);
                        startActivity(intent);
                    }
                }, 3000);


                // Run async class which runs code getting info from open data source
                //new StationInfo().execute(stationURL, stationList, currentTrainsURL);
            }
        });


    }
    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(sensorEventListener, pressureSensor, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(sensorEventListener);
    }


    private Runnable runnable = new Runnable() {
        @Override
        public void run() {

            // Update text views with current air pressure and GPS values
            resultTextView.setText(String.format("%.2f mbar", pressureVal));
            latitudeTextView.setText(latitude);
            longitudeTextView.setText(longitude);

            String setLat = latitude.replace('.',',');
            String setLog = longitude.replace('.',',');

            // Here I am pushing my data to the firebase realtime database. It stores the data every second, with
            // the current time used as the data's parent node.
            reffAirPressure.child(setLat + "-" + setLog).child("Air Pressure").setValue(pressureVal);
            reffAirPressure.child(setLat + "-" + setLog).child("Latitude").setValue(latitude);
            reffAirPressure.child(setLat + "-" + setLog).child("Longitude").setValue(longitude);

            // Write to CSV file
            try{
                fw.append(String.valueOf(pressureVal));
                fw.append(",");
                fw.append(longitude);
                fw.append(",");
                fw.append(latitude);
                fw.append("\n");
            } catch (Exception e) {
            }


            if(started) {
                start();
            }
        }
    };

    public void stop() {
        started = false;
        handler.removeCallbacks(runnable);
    }

    public void start() {
        started = true;
        handler.postDelayed(runnable, 2000);
    }


    // I used this example on how to create an sync class, which is required to do any network
    // functions, like reading the xml from Irish Rail's website
    // https://stackoverflow.com/questions/25647881/android-asynctask-example-and-explanation
    private class StationInfo extends AsyncTask<String, Integer, String> {

        // This is run in a background thread
        @Override
        protected String doInBackground(String... url) {

            try {

                // Reading XML from Irish rail real time url
                // Source for XML/Dom instructions: https://howtodoinjava.com/xml/read-xml-dom-parser-example/
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();
                Document getAllStationsdoc = db.parse(new URL(url[1]).openStream());
                Document getCurrentTrains = db.parse(new URL(url[2]).openStream());

                getAllStationsdoc.getDocumentElement().normalize();
                getCurrentTrains.getDocumentElement().normalize();

                NodeList stationList = getAllStationsdoc.getElementsByTagName("objStation");
                NodeList currentTrainsList = getCurrentTrains.getElementsByTagName("objTrainPositions");


                //ListofStations = new String[nList.getLength()];

                // Getting all station objects
                int count = 1;
                reffStationData.removeValue();
                for(int i = 0; i < stationList.getLength(); i++){

                    Node node = stationList.item(i);
                    Element eElement = (Element) node;

                    String stationName = eElement.getElementsByTagName("StationDesc").item(0).getTextContent();
                    String stationLatitude = eElement.getElementsByTagName("StationLatitude").item(0).getTextContent();
                    String stationLongitude = eElement.getElementsByTagName("StationLongitude").item(0).getTextContent();
                    String stationCode = eElement.getElementsByTagName("StationCode").item(0).getTextContent();

                    HashMap<String,String> stationData = new HashMap<>();

                    stationData.put("Station Name", stationName);
                    stationData.put("Station Code", stationCode);
                    stationData.put("Station Latitude", stationLatitude);
                    stationData.put("Station Longitude", stationLongitude);

                    reffStationData.push().setValue(stationData);

                }
                reffCurrentTrainsData.removeValue();
                for(int i = 0; i < currentTrainsList.getLength(); i++){
                    Node node = currentTrainsList.item(i);
                    Element eElement = (Element) node;

                    String trainStatus = eElement.getElementsByTagName("TrainStatus").item(0).getTextContent();
                    String trainLatitude = eElement.getElementsByTagName("TrainLatitude").item(0).getTextContent();
                    String trainLogitude = eElement.getElementsByTagName("TrainLongitude").item(0).getTextContent();
                    String trainCode = eElement.getElementsByTagName("TrainCode").item(0).getTextContent();
                    String trainDate = eElement.getElementsByTagName("TrainDate").item(0).getTextContent();
                    String publicMessage = eElement.getElementsByTagName("PublicMessage").item(0).getTextContent();
                    String direction = eElement.getElementsByTagName("Direction").item(0).getTextContent();


                    trainLatitude = new BigDecimal(trainLatitude).setScale(3, RoundingMode.HALF_EVEN).toString();
                    trainLogitude = new BigDecimal(trainLogitude).setScale(3, RoundingMode.HALF_EVEN).toString();

                    HashMap<String,String> trainData = new HashMap<>();

                    trainData.put("Train Status",trainStatus);
                    trainData.put("Train Latitude",trainLatitude);
                    trainData.put("Train Longitude",trainLogitude);
                    trainData.put("Train Code",trainCode);
                    trainData.put("Train Data",trainDate);
                    trainData.put("Public Message",publicMessage);
                    trainData.put("Direction", direction);

                    reffCurrentTrainsData.push().setValue(trainData);

                }

            }
            catch (Exception e) {
                e.printStackTrace();
            }

            return "this string is passed to onPostExecute";
        }

        // This is called from background thread but runs in UI
        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);

            // Do things like update the progress bar
        }

        // This runs in UI when background thread finishes
        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);

            // Do things like hide the progress bar or change a TextView
        }
    }


}


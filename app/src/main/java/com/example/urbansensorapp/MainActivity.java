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

import java.net.URL;
import java.util.Calendar;

import com.example.urbansensorapp.R;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.w3c.dom.Document;

import java.io.File;
import java.io.FileWriter;
import java.util.List;

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
    private String[] timeArray;

    private String startTime;

    private String stationURL = "http://api.irishrail.ie/realtime/realtime.asmx/getStationDataByCodeXML?StationCode=perse&NumMins=30";

    private Button startButton;
    private TextView resultTextView;
    private TextView latitudeTextView;
    private TextView longitudeTextView;
    private Button stopButton;

    DatabaseReference reffAirPressure;


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
            longitude = Double.toString(location.getLongitude());
            latitude = Double.toString(location.getLatitude());
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



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        reffAirPressure = FirebaseDatabase.getInstance().getReference("Sensor Data/Air Pressure By GPS");
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
                            longitude = Double.toString(location.getLongitude());
                            latitude = Double.toString(location.getLatitude());
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

        stopButton = findViewById(R.id.stopButton);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);

        startButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                startTime = (Calendar.getInstance().getTime().toString());
                startService(new Intent(MainActivity.this,MyService.class));
                //reff.child(Calendar.getInstance().getTime().toString());
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



            timeArray = Calendar.getInstance().getTime().toString().trim().split("\\s+");
            new StationInfo().execute(stationURL);

            // Update text views with current air pressure and GPS values
            resultTextView.setText(String.format("%.2f mbar", pressureVal));
            latitudeTextView.setText(latitude);
            longitudeTextView.setText(longitude);
            reffAirPressure.child((Calendar.getInstance().getTime().toString())).child("Air Pressure").setValue(pressureVal);
            reffAirPressure.child((Calendar.getInstance().getTime().toString())).child("Latitude").setValue(latitude);
            reffAirPressure.child((Calendar.getInstance().getTime().toString())).child("Longitude").setValue(longitude);

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
        handler.postDelayed(runnable, 1000);
    }


    private class StationInfo extends AsyncTask<String, Integer, String> {

//        // Runs in UI before background thread is called
//        @Override
//        protected void onPreExecute() {
//            super.onPreExecute();
//
//            // Do something like display a progress bar
//        }

        // This is run in a background thread
        @Override
        protected String doInBackground(String... url) {

            try {

                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();
                Document doc = db.parse(new URL(url[0]).openStream());
                doc.getDocumentElement().normalize();
                //System.out.println("Root element :" + doc.getDocumentElement().getNodeName());
                Log.d("urltaggy", "Root element :" + doc.getDocumentElement().getNodeName());


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


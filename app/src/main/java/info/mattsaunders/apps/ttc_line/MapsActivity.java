package info.mattsaunders.apps.ttc_line;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;

public class MapsActivity extends FragmentActivity implements
        GooglePlayServicesClient.ConnectionCallbacks,
        GooglePlayServicesClient.OnConnectionFailedListener,
        LocationListener {

    /**-----------
     * QA and stability:
     * -----------
     * TODO: PRIORITY - after being open a while (possible other causes) - vehicle updating loop fails to continue - gets stuck after "Getting Vehicles on route: ##"
     * --- seems to be a timeout thing - maybe the timeout length is too long? maybe use setConnectionTimeout - problem resolved itself after it timed out finally
     * TODO: fix: some stops that have no id are not overlapping - despite the extra tag on their stop tag - they are removed currently, but should be shown
     * --- need to differentiate between these and those that overlap
     * --- use routeTag and stopTag method for calling API to get predictions (currently using stopId method)
     * TODO: research: find out why some stops are included in list, have a valid ID, but don't return predictions
     * --- could be blue night stops possibly?
     *
     * -----------
     * Features:
     * -----------
     * TODO: remove or change stops that are inactive (ie blue night stops during daytime)
     * TODO: show vehicle direction in image - with arrow or set flat and rotate icon
     * TODO: change vehicle marker color if vehicle has not moved much over time - ie turn vehicle marker red if vehicle appears to be stuck somewhere
     * TODO: activity to list nearby stops and predictions for next vehicles
     * TODO: attempt to determine if user is on a streetcar/bus, and present user with list of predicted times of arrival for upcoming stops
     * TODO: visual indicator in UI for user to know how the refreshing of info is going - spinner maybe?
     * TODO: subway schedule activity
     *
     * -----------
     * Performance:
     * -----------
     * TODO: remove unnecessary variables from TransitRoute object class to save memory
     * TODO: remove superfluous print/log commands
     */

    // Global variables
    Location mCurrentLocation;
    LocationClient mLocationClient;
    LocationRequest mLocationRequest;
    boolean mUpdatesRequested;
    LatLng userLocation;
    CameraPosition cameraPosition;
    LatLngBounds bounds;

    //TTC info
    ArrayList<TransitRoute> routes = null;
    ArrayList<TransitStop> stops = null;
    ArrayList<TransitVehicle> vehicles = new ArrayList<TransitVehicle>();

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private SharedPreferences mPrefs;
    private SharedPreferences.Editor mEditor;
    private static boolean firstLaunch = true;

    // Request code, used in onActivityResult
    private final static int
            CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
    // Milliseconds per second
    private static final int MILLISECONDS_PER_SECOND = 1000;
    // Update frequency in seconds
    public static final int UPDATE_INTERVAL_IN_SECONDS = 5;
    // Update frequency in milliseconds
    private static final long UPDATE_INTERVAL = MILLISECONDS_PER_SECOND * UPDATE_INTERVAL_IN_SECONDS;
    // The fastest update frequency, in seconds
    private static final int FASTEST_INTERVAL_IN_SECONDS = 1;
    // A fast frequency ceiling in milliseconds
    private static final long FASTEST_INTERVAL = MILLISECONDS_PER_SECOND * FASTEST_INTERVAL_IN_SECONDS;
    //Default location:
    private static final LatLng TORONTO = new LatLng(43.7000,-79.4000);

    //API constants:
    public final static String apiURL = "http://webservices.nextbus.com/service/publicXMLFeed?command=";
    public final static String apiAgency = "&a=ttc";
    //API variables:
    public String apiCommand;
    public String apiParam1; //store routeID value for api call
    public String apiParam2; //store time value for api call
    public static boolean gotRouteInfo = false;

    //Marker list:
    private HashMap<String, Marker> visibleMarkers = new HashMap<String, Marker>();
    private HashMap<String, Marker> visibleMarkersVehicle = new HashMap<String, Marker>();
    private HashMap<String, LatLng> visibleVehicleOldLocation = new HashMap<String, LatLng>();

    //Visible route list:
    private ArrayList<String> visibleRouteList = new ArrayList<String>();

    //Runnable vars for updating vehicle locations:
    private static Handler mHandler = new Handler();
    private static Runnable mViewUpdater;
    private static Runnable mUpdater;
    private Thread t;
    private static boolean loopVehicleInfo = true;
    private static boolean vehicleListBuilt = false;
    private static final int sleepTime = 5000;

    //Progressbar:
    ProgressDialog progress;

    @Override
    protected void onStart() {
        super.onStart();
        // Connect the client.
        mLocationClient.connect();
    }

    @Override
    protected void onPause() {
        loopVehicleInfo = false;
        t.interrupt();
        // Save the current setting for updates
        mEditor.putBoolean("KEY_UPDATES_ON", mUpdatesRequested);
        mEditor.commit();
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (mLocationClient.isConnected()) {
            /*
             * Remove location updates for a listener.
             * The current Activity is the listener, so
             * the argument is "this".
             */
            mLocationClient.removeLocationUpdates(this);
        }
        // Disconnecting the client invalidates it.
        mLocationClient.disconnect();
        super.onStop();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        mLocationRequest = LocationRequest.create();
        mLocationClient = new LocationClient(this, this, this);
        setUpMapIfNeeded();

        // Use high accuracy
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        // Set the update interval to 5 seconds
        mLocationRequest.setInterval(UPDATE_INTERVAL);
        // Set the fastest update interval to 1 second
        mLocationRequest.setFastestInterval(FASTEST_INTERVAL);
        // Start with updates turned off
        mUpdatesRequested = false;
        // Open the shared preferences
        mPrefs = getSharedPreferences("SharedPreferences",
                Context.MODE_PRIVATE);
        // Get a SharedPreferences editor
        mEditor = mPrefs.edit();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
        /*
         * Get any previous setting for location updates
         * Gets "false" if an error occurs
         */
        if (mPrefs.contains("KEY_UPDATES_ON")) {
            mUpdatesRequested =
                    mPrefs.getBoolean("KEY_UPDATES_ON", false);

            // Otherwise, turn off location updates
        } else {
            mEditor.putBoolean("KEY_UPDATES_ON", false);
            mEditor.commit();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu items for use in the action bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_activity_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch (item.getItemId()) {
            case R.id.action_settings:
                openSettings();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void openSettings() {
        System.out.println("Settings selected");
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Download Info")
                .setTitle("Refresh route and stop info?");
        // Add the buttons
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User clicked OK button
                getRoutes();
            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User cancelled the dialog
            }
        });
        // Set other dialog properties

        // Create the AlertDialog
        AlertDialog dialog = builder.create();

        //Show dialog:
        dialog.show();
    }

    private void getRoutes() {
        gotRouteInfo = false;
        loopVehicleInfo = false;
        apiCommand = "routeList";
        String urlString = apiURL + apiCommand + apiAgency;
        new CallAPI().execute(urlString,"routeList");
    }
    private ArrayList<TransitVehicle> parseVehicleList(XmlPullParser parser) throws XmlPullParserException, IOException {
        int eventType = parser.getEventType();
        ArrayList<TransitVehicle> result = new ArrayList<TransitVehicle>();
        while( eventType!= XmlPullParser.END_DOCUMENT) {
            String name = null;
            switch(eventType)
            {
                case XmlPullParser.START_TAG:
                    name = parser.getName();
                    if( name.equals("Error")) {
                        System.out.println("Web API Error!");
                        System.out.println(parser.getAttributeValue(0));
                    }
                    else if ( name.equals("vehicle")) {
                        if (parser.getAttributeCount() > 3) {
                            //create new transit vehicle object
                            //check attributes:
                            String vehicleRouteId = "";
                            String vehicleId = "";
                            double lat = 0;
                            double lng = 0;
                            int sec = 0;
                            for (int x = 0; x < parser.getAttributeCount(); x++){
                                if (parser.getAttributeName(x).equals("id")) {
                                    vehicleId = parser.getAttributeValue(x);
                                } else if (parser.getAttributeName(x).equals("routeTag")) {
                                    vehicleRouteId = parser.getAttributeValue(x);
                                } else if (parser.getAttributeName(x).equals("lat")) {
                                    lat = Double.parseDouble(parser.getAttributeValue(x));
                                } else if (parser.getAttributeName(x).equals("lon")) {
                                    lng = Double.parseDouble(parser.getAttributeValue(x));
                                } else if (parser.getAttributeName(x).equals("secsSinceReport")) {
                                    sec = Integer.parseInt(parser.getAttributeValue(x));
                                }

                            }
                            TransitVehicle tempVehicle = new TransitVehicle(vehicleId, vehicleRouteId, new LatLng(lat,lng), sec); //subway stations don't have this one---parser.getAttributeValue(4)

                            //add new transit vehicle object to list
                            result.add(tempVehicle);
                        }
                    }
                    break;
                case XmlPullParser.END_TAG:
                    break;
            } // end switch
            eventType = parser.next();
        } // end while
        return result;
    }
    private ArrayList<TransitRoute> parseRouteList(XmlPullParser parser) throws XmlPullParserException, IOException {
        int eventType = parser.getEventType();
        ArrayList<TransitRoute> result = new ArrayList<TransitRoute>();
        while( eventType!= XmlPullParser.END_DOCUMENT) {
            String name = null;
            switch(eventType)
            {
                case XmlPullParser.START_TAG:
                    name = parser.getName();
                    if( name.equals("Error")) {
                        System.out.println("Web API Error!");
                    }
                    else if ( name.equals("route")) {
                        //create new transit route object
                        TransitRoute tempRoute = new TransitRoute(parser.getAttributeValue(0), parser.getAttributeValue(1));
                        //add new transit route object to list
                        result.add(tempRoute);
                    }
                    break;
                case XmlPullParser.END_TAG:
                    break;
            } // end switch
            eventType = parser.next();
        } // end while
        return result;
    }
    private ArrayList<TransitStop> parseStopList(XmlPullParser parser) throws XmlPullParserException, IOException {
        int eventType = parser.getEventType();
        ArrayList<TransitStop> result = new ArrayList<TransitStop>();
        while( eventType!= XmlPullParser.END_DOCUMENT) {
            String name = null;
            switch(eventType)
            {
                case XmlPullParser.START_TAG:
                    name = parser.getName();
                    if( name.equals("Error")) {
                        System.out.println("Web API Error!");
                        System.out.println(parser.getAttributeValue(0));
                    }
                    else if ( name.equals("stop")) {
                        if (parser.getAttributeCount() > 3) {
                            String stopID;
                            try { stopID = parser.getAttributeValue(4); } catch (IndexOutOfBoundsException e) { stopID = "0"; }
                            //create new transit route object
                            TransitStop tempStop = new TransitStop(parser.getAttributeValue(0),
                                    parser.getAttributeValue(1),
                                    new LatLng(Double.parseDouble(parser.getAttributeValue(2)), Double.parseDouble(parser.getAttributeValue(3))),
                                   stopID); //subway stations don't have this one---parser.getAttributeValue(4)

                            //add new transit route object to list
                            result.add(tempStop);
                        }
                    }
                    break;
                case XmlPullParser.END_TAG:
                    break;
            } // end switch
            eventType = parser.next();
        } // end while
        return result;
    }
    private ArrayList<TransitPrediction> parsePredictions(XmlPullParser parser) throws XmlPullParserException, IOException {
        int eventType = parser.getEventType();
        ArrayList<TransitPrediction> result = new ArrayList<TransitPrediction>();
        while( eventType!= XmlPullParser.END_DOCUMENT) {
            String name = null;
            switch(eventType)
            {
                case XmlPullParser.START_TAG:
                    name = parser.getName();
                    if( name.equals("Error")) {
                        System.out.println("Web API Error!");
                    }
                    else if ( name.equals("prediction")) {
                        //System.out.println(parser.getAttributeValue(2));
                        String route = "";
                        for (int x = 2; x < parser.getAttributeCount(); x++) {
                            if (parser.getAttributeName(x).equals("branch")) {
                                route = parser.getAttributeValue(x);
                                break;
                            }
                        }
                        result.add(new TransitPrediction(parser.getAttributeValue(2),route));
                    }
                    break;
                case XmlPullParser.END_TAG:
                    break;
            } // end switch
            eventType = parser.next();
        } // end while
        return result;
    }
    private class CallAPI extends AsyncTask<String, String, String> {
        @Override
        protected void onPreExecute() {
            progress = new ProgressDialog(MapsActivity.this);
            progress.setMessage("Updating route and stop info");
            progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progress.setCancelable(false);
            //progress.setIndeterminate(true);
            progress.show();
        }
        @Override
        protected String doInBackground(String... params) {
            String urlString = params[0]; // URL to call
            String command = params[1]; //Which command is being executed
            String resultToDisplay = "";
            BufferedInputStream in = null;
            HttpURLConnection urlConnection;
            InputStream input;

            Log.i("Executing background API task", "Command is " + command);

            // HTTP Get
            try {
                URL url = new URL(urlString);
                urlConnection = (HttpURLConnection) url.openConnection();
                input = urlConnection.getInputStream(); //API result
                //Save route list XML file:
                Utilities.writeXMLFile(input);
                //Now load the data we just saved:
                //in = new BufferedInputStream(input);
                in = Utilities.readXMLFile();

            } catch (Exception e) {
                System.out.println(e.getMessage());
                return e.getMessage();
            }

            // Parse XML
            XmlPullParserFactory pullParserFactory;
            try {
                pullParserFactory = XmlPullParserFactory.newInstance();
                XmlPullParser parser = pullParserFactory.newPullParser();

                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                parser.setInput(in, null);

                //Process data from routeList command:
                routes = parseRouteList(parser);
                System.out.println("----------------Built route list---------------");
                //Set progress bar:
                int downProgress = 2;
                progress.setProgress(downProgress/2);
                int downProgressIncrement = 198/routes.size(); //routes.size is roughly 180
                //System.out.println("INCREMENT: " + downProgressIncrement + "/" + " interval " + routes.size());
                System.out.println("--------------Building stops list--------------");
                for (TransitRoute route : routes) {
                    //Update progress bar:
                    downProgress += downProgressIncrement;
                    progress.setProgress(downProgress/2);
                    System.out.println("Getting stops on " + route.getRouteId() + " " + route.getRouteName());

                    apiCommand = "routeConfig";
                    apiParam1 = "&r=" + route.getRouteId();
                    urlString = apiURL + apiCommand + apiAgency + apiParam1;

                    try {
                        URL url = new URL(urlString);
                        urlConnection = (HttpURLConnection) url.openConnection();
                        input = urlConnection.getInputStream(); //API result
                        //Save stops list XML file:
                        Utilities.FILENAME_EXT = "_" + route.getRouteId();
                        Utilities.writeXMLFile(input);
                        in = Utilities.readXMLFile();
                    } catch (Exception e) {
                        System.out.println(e.getMessage());
                        return e.getMessage();
                    }

                    pullParserFactory = XmlPullParserFactory.newInstance();
                    parser = pullParserFactory.newPullParser();

                    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                    parser.setInput(in, null);

                    stops = parseStopList(parser);
                    route.setStopsList(stops);
                    stops = null;
                }
                System.out.println("----------------Built stops list---------------");
                for (TransitRoute route : routes) {
                    System.out.println("Route name: " + route.getRouteName());
                    System.out.println("Number of stops: " + route.getStopsList().size());
                }
                progress.setProgress(99);
                System.out.println("Number of routes: " + routes.size());
                //End process data from routeList command
            } catch (XmlPullParserException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return resultToDisplay;
        }

        protected void onPostExecute(String result) {
            progress.hide();

            Log.i("API call complete", "Result = " + result);
            gotRouteInfo = true;
            displayStops();

            //begin updating vehicle info again:
            loopVehicleInfo = true;
            apiCommand = "vehicleLocations";
            getVehicleUpdates();
            t= new Thread(mUpdater);
            t.start();
            updateVehicleView();
            mHandler.post(mViewUpdater);
        }
    }
    private class CallAPIForPrediction extends AsyncTask<String, String, ArrayList<TransitPrediction>> {
        @Override
        protected ArrayList<TransitPrediction> doInBackground(String... params) {
            String stop = params[0];
            String key = params[1];
            String command = "predictions"; //Which command is being executed
            String urlString = apiURL + command + apiAgency + "&stopId=" + stop; // URL to call
            //String resultToDisplay = "";
            ArrayList<TransitPrediction> results = new ArrayList<TransitPrediction>();
            BufferedInputStream in = null;

            Log.i("Executing background API task", "Command is " + command);

            // HTTP Get
            try {
                URL url = new URL(urlString);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection(); //API result
                in = new BufferedInputStream(urlConnection.getInputStream());
            } catch (Exception e) {
                System.out.println("FAILED TO RETRIEVE URL");
                e.printStackTrace();
                System.out.println(e.getMessage());
                return null;
            }

            // Parse XML
            XmlPullParserFactory pullParserFactory;
            try {
                pullParserFactory = XmlPullParserFactory.newInstance();
                XmlPullParser parser = pullParserFactory.newPullParser();

                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                parser.setInput(in, null);
                results = parsePredictions(parser);
                //Add key to results:
                results.add(new TransitPrediction(key,"KEY"));
            } catch (XmlPullParserException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            //return resultToDisplay;
            return results;
        }

        protected void onPostExecute(ArrayList<TransitPrediction> results) {
            //add predictions to Marker snippet:
            String key = results.get(results.size()-1).getTime(); //get the key stored in the last TransitPrediction object, under the time variable (where the route variable is "KEY"
            results.remove(results.size()-1); //remove the last variable
            Marker marker = visibleMarkers.get(key);
            if (marker != null) {
                String newSnippet = marker.getSnippet().split("--")[0] + "--" + "Next vehicle(s):";

                //build list of all routes found for this stop:
                ArrayList<String> stopRoutes = new ArrayList<String>();
                for (TransitPrediction result : results) {
                    if (!stopRoutes.contains(result.getRoute())) {
                        stopRoutes.add(result.getRoute());
                    }
                }
                for (String route : stopRoutes) {
                    //Start a new line and add the route number
                    newSnippet += "\n" + route + ": ";
                    for (TransitPrediction result : results) {
                        if (result.getRoute().equals(route)) {
                            //put prediction on one line of snipppet
                            newSnippet += result.getTime() + " | ";
                        }
                    }
                    newSnippet += "minutes";
                }
                if (results.size() < 1) { newSnippet += "\n" + "NOT IN SERVICE"; }
                marker.setSnippet(newSnippet);
                marker.hideInfoWindow();
                marker.showInfoWindow();
                Log.i("API call complete", "Result = " + newSnippet);
            } else { System.out.println("MARKER IS NULL: " + key); }
        }
    }

    private void getVehicleUpdates() { //set API command before beginning to vehicleLocation
        mUpdater = new Runnable() {
            @Override
            public void run() {
                //Set apiParam2 to time:
                apiParam2 = "&t=0";
                ArrayList<String> tempRouteList = new ArrayList<String>();
                while (loopVehicleInfo) {
                    System.out.println("Begin updating vehicle info:");
                    vehicleListBuilt = false;
                    vehicles.clear();
                    //Perform connection, get data, update view:
                    //for (TransitRoute route : routes) {
                    tempRouteList.clear();
                    tempRouteList = new ArrayList<String>(visibleRouteList);
                    for (String id : tempRouteList) {
                        //apiParam1 = "&r=" + route.getRouteId();
                        System.out.println("Getting Vehicles on route: " + id);
                        apiParam1 = "&r=" + id;
                        String urlString = apiURL + apiCommand + apiAgency + apiParam1 + apiParam2; // URL to call (url + command + agency tag + route tag + time (or 0))
                        BufferedInputStream in = null;

                        // HTTP Get
                        try {
                            URL url = new URL(urlString);
                            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection(); //API result
                            in = new BufferedInputStream(urlConnection.getInputStream());
                        } catch (Exception e) {
                            System.out.println("FAILED TO RETRIEVE URL");
                            e.printStackTrace();
                            System.out.println(e.getMessage());
                            break;
                        }
                        System.out.println("Got API info");
                        // Parse XML
                        XmlPullParserFactory pullParserFactory;
                        try {
                            pullParserFactory = XmlPullParserFactory.newInstance();
                            XmlPullParser parser = pullParserFactory.newPullParser();

                            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                            parser.setInput(in, null);

                            //Process data from vehicleLocation command:
                            ArrayList<TransitVehicle> tempVehicles = parseVehicleList(parser);
                            for (TransitVehicle vehicle : tempVehicles) {
                                vehicles.add(vehicle);
                            }
                        } catch (XmlPullParserException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    vehicleListBuilt = true;
                    /*
                    for (TransitVehicle vehicle : vehicles) {
                        System.out.println("VEHICLE LOCATION: "
                                + vehicle.getVehicleRoute()
                                + " ID#" + vehicle.getId()
                                + " at " + vehicle.getLocation()
                                + " last reported: " + vehicle.getSecSinceReport() + "s ago");
                    }
                    */
                    System.out.println("Number of vehicles in list " + vehicles.size());
                    if (!loopVehicleInfo) { return; }
                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        return;
                    }
                }
            }
        };
    }
    private void displayStops() {
        float zoom = mMap.getCameraPosition().zoom;
        visibleRouteList.clear();
        if (zoom > 14.5) {
            for (TransitRoute route : routes) {
                stops = route.getStopsList();
                for (TransitStop stop : stops) {
                    LatLng stopLoc = stop.getLocation();
                    if (bounds.contains(stopLoc)) {
                        if (stop.getStopTag().split("_").length > 1) {  //Skip displaying stop if it's an _ar stop (allows the one underneath to show) (some don't have one underneath - these edge cases need to be dealt with)
                            System.out.println("Found an _ in " + stop.getStopTag() + " with id: " + stop.getStopId() + " called " + stop.getStopTitle());
                            break;
                        } //Consider replacing this with if (stop.getStopId().equals("0") { break; } to guarantee no illegitimate stops are shown (at risk of removing stops that should exist)
                        if (!visibleMarkers.containsKey(stop.getStopTag())) {
                            visibleMarkers.put(stop.getStopTag(), mMap.addMarker(new MarkerOptions()
                                            .position(stopLoc)
                                            .title(stop.getStopTitle() + " - " + stop.getStopTag())
                                            //.flat(true)
                                            .snippet(stop.getStopId())
                                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.stop))
                            ));
                        }
                        if (!visibleRouteList.contains(route.getRouteId())) { visibleRouteList.add(route.getRouteId()); }
                    } else if (visibleMarkers.containsKey(stop.getStopTag())) { //if not visible, check if already displayed
                        //System.out.println("Removing marker " + stop.getStopTitle() + " at " + stop.getLocation());
                        visibleMarkers.get(stop.getStopTag()).remove(); //remove marker from map
                        visibleMarkers.remove(stop.getStopTag()); //remove marker from hash map of markers
                    }
                }
            }
        } else { mMap.clear(); }
    }
    private void displayVehicles() {
        for (TransitVehicle vehicle : vehicles) {
            LatLng vehLoc = vehicle.getLocation();
            if (bounds.contains(vehLoc)) {
                if (!visibleMarkersVehicle.containsKey(vehicle.getId())) {
                    visibleMarkersVehicle.put(vehicle.getId(), mMap.addMarker(new MarkerOptions()
                                    .position(vehLoc)
                                    .title(vehicle.getVehicleRoute() + " - " + vehicle.getId())
                                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.bus))
                    ));
                    visibleVehicleOldLocation.put(vehicle.getId(), vehLoc);
                } else if (!vehLoc.equals(visibleVehicleOldLocation.get(vehicle.getId()))) {   //check if vehicle has moved since last time, but is still on screen.
                    System.out.println("VEHICLE MOVED: " + vehicle.getVehicleRoute() + " " + vehicle.getId() + " from " + visibleVehicleOldLocation.get(vehicle.getId()) + " to " + vehicle.getLocation());
                    visibleVehicleOldLocation.put(vehicle.getId(), vehLoc);
                    //visibleMarkersVehicle.get(vehicle.getId()).setPosition(vehLoc);
                    animateMarker(visibleMarkersVehicle.get(vehicle.getId()), vehLoc, false);
                }
            } else if (visibleMarkersVehicle.containsKey(vehicle.getId())) { //if not visible, check if already displayed
                System.out.println("Removing vehicle marker " + vehicle.getId() + ":" + vehicle.getVehicleRoute() + " at " + vehicle.getLocation());
                visibleMarkersVehicle.get(vehicle.getId()).remove(); //remove marker from map
                visibleMarkersVehicle.remove(vehicle.getId()); //remove marker from hash map of markers

            }
        }
    }

    public void animateMarker(final Marker marker, final LatLng toPosition,
                              final boolean hideMarker) {
        final Handler handler = new Handler();
        final long start = SystemClock.uptimeMillis();
        Projection proj = mMap.getProjection();
        Point startPoint = proj.toScreenLocation(marker.getPosition());
        final LatLng startLatLng = proj.fromScreenLocation(startPoint);
        final long duration = 500;

        final Interpolator interpolator = new LinearInterpolator();

        handler.post(new Runnable() {
            @Override
            public void run() {
                long elapsed = SystemClock.uptimeMillis() - start;
                float t = interpolator.getInterpolation((float) elapsed
                        / duration);
                double lng = t * toPosition.longitude + (1 - t)
                        * startLatLng.longitude;
                double lat = t * toPosition.latitude + (1 - t)
                        * startLatLng.latitude;
                marker.setPosition(new LatLng(lat, lng));

                if (t < 1.0) {
                    // Post again 16ms later.
                    handler.postDelayed(this, 16);
                } else {
                    if (hideMarker) {
                        marker.setVisible(false);
                    } else {
                        marker.setVisible(true);
                    }
                }
            }
        });
    }

    private void updateVehicleView() {
        mViewUpdater = new Runnable() {
            @Override
            public void run() {
                System.out.println("UPDATING VEHICLE VIEW!");
                if (!loopVehicleInfo) { return; }
                mHandler.postDelayed(this, sleepTime / 2);
                if (vehicleListBuilt) {
                    displayVehicles();
                }
            }
        };
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
            }
        }
        if (!t.isAlive()) {
            loopVehicleInfo = true;
            apiCommand = "vehicleLocations";
            getVehicleUpdates();
            t= new Thread(mUpdater);
            t.start();
            updateVehicleView();
            mHandler.post(mViewUpdater);
        }
    }

    /**
     * This is where we can add markers or lines, add listeners or move the camera. In this case, we
     * just add a marker near Africa.
     * <p/>
     * This should only be called once and when we are sure that {@link #mMap} is not null.
     */
    private void setUpMap() {
        if (servicesConnected()) {
            Log.i("Services Connected ", "TRUE");
            if (Utilities.checkXMLFile()) {
                routes = Utilities.parseSavedXML();
            } else {
                getRoutes(); //Get route info
            }
            mMap.setMyLocationEnabled(true);
            //Set custom marker info window:
            mMap.setInfoWindowAdapter(new CustomInfoWindow(getLayoutInflater()));
            bounds = mMap.getProjection().getVisibleRegion().latLngBounds;
            mMap.setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener() {
                @Override
                public void onCameraChange(CameraPosition position) {
                    bounds = mMap.getProjection().getVisibleRegion().latLngBounds;
                    //Log.i("Camera bounds", "Area shown " + bounds.toString());
                    if (gotRouteInfo) {
                        if (vehicleListBuilt) { displayVehicles(); }
                        //mMap.clear();
                        //visibleMarkersVehicle.clear();
                        //visibleMarkers.clear();
                        displayStops();
                    }
                }
            });
            mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener()
            {
                @Override
                public boolean onMarkerClick(com.google.android.gms.maps.model.Marker marker)
                {
                    marker.showInfoWindow();
                    if (visibleMarkers.containsValue(marker)) {
                        String key = Utilities.getKeyFromValue(marker, visibleMarkers);
                        //System.out.println("MARKER CLICKED! " + marker.getId() + " " + marker.getTitle() + " = stop tag: " + key);
                        String id = marker.getSnippet().split("--")[0];
                        System.out.println("Marker has id # " + id);
                        //Get predictions for stop
                        //ArrayList<String> predictions =
                        new CallAPIForPrediction().execute(id,key);
                    }
                    return true;
                }
            });
            apiCommand = "vehicleLocations";
            getVehicleUpdates();
            t= new Thread(mUpdater);
            t.start();
            updateVehicleView();
            mHandler.post(mViewUpdater);
        }
    }

    private void updateMap() {
        //Store current location
        mCurrentLocation = mLocationClient.getLastLocation();
        //Build LatLng object to store current user location:
        if (mCurrentLocation != null) {
            userLocation = new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude());
        } else {
            userLocation = TORONTO;
        }
        // Construct a CameraPosition focusing on userLocation and animate the camera to that position.
        if (firstLaunch) {
            cameraPosition = new CameraPosition.Builder()
                    .target(userLocation)       // Sets the center of the map to User's location
                    .zoom(17)                   // Sets the zoom
                    .bearing(0)                 // Sets the orientation of the camera to east
                    .tilt(30)                   // Sets the tilt of the camera to 30 degrees
                    .build();                   // Creates a CameraPosition from the builder
            //Move camera to current location:
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
            firstLaunch = false;
        }
        bounds = mMap.getProjection().getVisibleRegion().latLngBounds;

    }

    /*
    * Called by Location Services when the request to connect the
    * client finishes successfully. At this point, you can
    * request the current location or start periodic updates
    */
    @Override
    public void onConnected(Bundle dataBundle) {
        // Display the connection status
        Toast.makeText(this, "Connected", Toast.LENGTH_SHORT).show();

        Log.d("Setting up map", "Location Services connected " + mLocationClient.isConnected());
        Log.d("Setting up map", "Getting user location " + mLocationClient.getLastLocation());
        //Call method to get location:
        updateMap();

        // If already requested, start periodic updates
        if (mUpdatesRequested) {
            mLocationClient.requestLocationUpdates(mLocationRequest, this);
        }
    }

    /*
     * Called by Location Services if the connection to the
     * location client drops because of an error.
     */
    @Override
    public void onDisconnected() {
        // Display the connection status
        Toast.makeText(this, "Disconnected. Please re-connect.",
                Toast.LENGTH_SHORT).show();
    }

    // Define the callback method that receives location updates
    @Override
    public void onLocationChanged(Location location) {
        // Report to the UI that the location was updated
        String msg = "Updated Location: " +
                Double.toString(location.getLatitude()) + "," +
                Double.toString(location.getLongitude());
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }


    /*
     * Called by Location Services if the attempt to
     * Location Services fails.
     */
    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        /*
         * Google Play services can resolve some errors it detects.
         * If the error has a resolution, try sending an Intent to
         * start a Google Play services activity that can resolve
         * error.
         */
        if (connectionResult.hasResolution()) {
            try {
                // Start an Activity that tries to resolve the error
                connectionResult.startResolutionForResult(this, CONNECTION_FAILURE_RESOLUTION_REQUEST);
                /*
                 * Thrown if Google Play services canceled the original
                 * PendingIntent
                 */
            } catch (IntentSender.SendIntentException e) {
                // Log the error
                e.printStackTrace();
            }
        } else {
            /*
             * If no resolution is available, display a dialog to the
             * user with the error.
             */
            showErrorDialog(connectionResult.getErrorCode());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Decide what to do based on the original request code
        switch (requestCode) {
            case CONNECTION_FAILURE_RESOLUTION_REQUEST :
            /*
             * If the result code is Activity.RESULT_OK, try
             * to connect again
             */
                switch (resultCode) {
                    case Activity.RESULT_OK :
                    /*
                     * Try the request again
                     */
                        break;
                }
        }
    }

    // Define a DialogFragment that displays the error dialog
    public static class ErrorDialogFragment extends DialogFragment {
        // Global field to contain the error dialog
        private Dialog mDialog;
        // Default constructor. Sets the dialog field to null
        public ErrorDialogFragment() {
            super();
            mDialog = null;
        }
        // Set the dialog to display
        public void setDialog(Dialog dialog) {
            mDialog = dialog;
        }
        // Return a Dialog to the DialogFragment.
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return mDialog;
        }
    }

    public void showErrorDialog(int errorCode){
        Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(errorCode, this, CONNECTION_FAILURE_RESOLUTION_REQUEST);

        // If Google Play services can provide an error dialog
        if (errorDialog != null) {
            // Create a new DialogFragment for the error dialog
            ErrorDialogFragment errorFragment = new ErrorDialogFragment();
            // Set the dialog in the DialogFragment
            errorFragment.setDialog(errorDialog);
            // Show the error dialog in the DialogFragment
            errorFragment.show(getSupportFragmentManager(), "Location Updates");
        }
    }

    private boolean servicesConnected() {
        // Check that Google Play services is available
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        // If Google Play services is available
        if (ConnectionResult.SUCCESS == resultCode) {
            // In debug mode, log the status
            Log.d("Location Updates",
                    "Google Play services is available.");
            // Continue
            return true;
            // Google Play services was not available for some reason.
            // resultCode holds the error code.
        } else {
            // Get the error dialog from Google Play services
            Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(resultCode, this, CONNECTION_FAILURE_RESOLUTION_REQUEST);

            // If Google Play services can provide an error dialog
            if (errorDialog != null) {
                // Create a new DialogFragment for the error dialog
                ErrorDialogFragment errorFragment =
                        new ErrorDialogFragment();
                // Set the dialog in the DialogFragment
                errorFragment.setDialog(errorDialog);
                // Show the error dialog in the DialogFragment
                errorFragment.show(getSupportFragmentManager(),
                        "Location Updates");
            }

            return false;
        }
    }
//End of file
}

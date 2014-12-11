package info.mattsaunders.apps.ttc_line;

import android.os.Environment;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * File utilities to store and retrieve info, stored in JSON files
 */
public class Utilities {
    public final static String FILEDIR = "/TTC-Line/";
    public final static String FILENAME = "Route_info";
    public static String FILENAME_EXT = "";

    public static ArrayList<TransitRoute> parseSavedXML() {
        //Open saved XML file:
        FILENAME_EXT = "";
        BufferedInputStream in = readXMLFile();
        System.out.println("Loading saved TTC route data");
        ArrayList<TransitRoute> results = null;
        ArrayList<TransitStop> stops;
        // Parse XML
        XmlPullParserFactory pullParserFactory;
        try {
            pullParserFactory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = pullParserFactory.newPullParser();

            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(in, null);

            //Process data from routeList command:
            results = parseRouteList(parser);
            System.out.println("----------------Built route list---------------");
            System.out.println("--------------Building stops list--------------");
            for (TransitRoute route : results) {
                System.out.println("Loading stops on " + route.getRouteId() + " " + route.getRouteName());

                //Open file input stream of local XML file for route stops
                FILENAME_EXT = "_" + route.getRouteId();
                in = readXMLFile();

                pullParserFactory = XmlPullParserFactory.newInstance();
                parser = pullParserFactory.newPullParser();

                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                parser.setInput(in, null);

                stops = parseStopList(parser, route.getRouteId());

                for (TransitStop stop : stops) {
                    if (!MapsActivity.masterStopList.containsKey(stop.getStopTag())) {
                        MapsActivity.masterStopList.put(stop.getStopTag(), stop);
                    } else {
                        MapsActivity.masterStopList.get(stop.getStopTag()).setRoutesServed(MapsActivity.masterStopList.get(stop.getStopTag()).getRoutesServed() + "," + route.getRouteId());
                    }
                }


                route.setStopsList(stops);
            }
            System.out.println("----------------Built stops list---------------");
            for (TransitRoute route : results) {
                System.out.println("Route name: " + route.getRouteName());
                System.out.println("Number of stops: " + route.getStopsList().size());
            }
            System.out.println("Number of routes: " + results.size());
            //End process data from saved XML file
        } catch (XmlPullParserException | IOException e) {
            e.printStackTrace();
        }
        MapsActivity.gotRouteInfo = true;
        return results;
    }
    private static ArrayList<TransitRoute> parseRouteList(XmlPullParser parser) throws XmlPullParserException, IOException {
        int eventType = parser.getEventType();
        ArrayList<TransitRoute> result = new ArrayList<>();
        while( eventType!= XmlPullParser.END_DOCUMENT) {
            String name;
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
    private static ArrayList<TransitStop> parseStopList(XmlPullParser parser, String routeId) throws XmlPullParserException, IOException {
        int eventType = parser.getEventType();
        ArrayList<TransitStop> result = new ArrayList<>();
        while( eventType!= XmlPullParser.END_DOCUMENT) {
            String name;
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
                                    stopID, routeId); //subway stations don't have this one---parser.getAttributeValue(4)

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

    public static void writeXMLFile(InputStream in) {
        try {
            File sdcard = Environment.getExternalStorageDirectory();
            File dir = new File(sdcard.getAbsolutePath() + FILEDIR);
            dir.mkdir();
            File file = new File(dir, FILENAME + FILENAME_EXT);
            FileOutputStream fos = new FileOutputStream(file);
            byte[] buffer = new byte[1024];
            int n;
            while ( (n = in.read(buffer)) != -1) {
                fos.write(buffer, 0, n);
            }
            fos.close();
        } catch (Exception ex) {
            Log.e("Failed to open file", FILENAME);
            ex.printStackTrace();
        }
    }
    public static BufferedInputStream readXMLFile() {
    BufferedInputStream in;
    try {
        File sdcard = Environment.getExternalStorageDirectory();
        File dir = new File(sdcard.getAbsolutePath() + FILEDIR);
        File file = new File(dir, FILENAME + FILENAME_EXT);
        try {
            FileInputStream fis = new FileInputStream(file);
            in = new BufferedInputStream(fis);
            return in;
        } catch (FileNotFoundException ex) {
            Log.e("Failed to load file: file not found", file.toString());
        }
    } catch (Exception ex) {
        Log.e("Failed to find directory", ex.toString());
    }
    return null;
    }
    public static boolean checkXMLFile() {
        File sdcard = Environment.getExternalStorageDirectory();
        File dir = new File(sdcard.getAbsolutePath() + FILEDIR);
        File file = new File(dir, FILENAME + FILENAME_EXT);
        return file.exists();
    }

    public static String getKeyFromValue (Marker marker, HashMap<String, Marker> hm) {
        for (String key : hm.keySet()) {
            if (hm.get(key).equals(marker)) {
                return key;
            }
        }
        return null;
    }

    /*
    public static JSONObject bundleToJsonObject(Bundle bundle) {
        try {
            JSONObject output = new JSONObject();
            for( String key : bundle.keySet() ){
                Object object = bundle.get(key);
                if(object instanceof Integer || object instanceof String)
                    output.put(key, object);
                else
                    throw new RuntimeException("only Integer and String can be extracted");
            }
            return output;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public static Bundle JsonObjectToBundle(JSONObject jsonObject) {
        try {
            Bundle bundle = new Bundle();
            Iterator<?> keys = jsonObject.keys();
            while( keys.hasNext() ){
                String key = (String)keys.next();
                Object object = jsonObject.get(key);
                if(object instanceof String)
                    bundle.putString(key, (String) object);
                else if(object instanceof Integer)
                    bundle.putInt(key, (Integer) object);
                else
                    throw new RuntimeException("only Integer and String can be re-extracted");
            }
            return bundle;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public static void writeJsonFile(JSONObject data) {
        try {
            File sdcard = Environment.getExternalStorageDirectory();
            File dir = new File(sdcard.getAbsolutePath() + "/PuSSHd/");
            dir.mkdir();
            File file = new File(dir, FILENAME);
            FileOutputStream fos = new FileOutputStream(file);
            try {
                fos.write(data.toString().getBytes());
            } catch (Exception ex) {
                Log.e("Failed to save data", data.toString());
                ex.printStackTrace();
            }
            fos.close();
        } catch (Exception ex) {
            Log.e("Failed to open file", FILENAME);
            ex.printStackTrace();
        }
    }

    public static JSONObject readJsonFile() {
        String json;
        try {
            File sdcard = Environment.getExternalStorageDirectory();
            File dir = new File(sdcard.getAbsolutePath() + FILEDIR);
            File file = new File(dir, FILENAME);
            try {
                FileInputStream fis = new FileInputStream(file);
                InputStreamReader fileRead = new InputStreamReader(fis);
                BufferedReader reader = new BufferedReader(fileRead);
                String str;
                StringBuilder buf = new StringBuilder();
                try {
                    while ((str = reader.readLine()) != null) {
                        buf.append(str);
                    }
                    fis.close();
                    json = buf.toString();
                    return new JSONObject(json);
                } catch (Exception ex) {
                    Log.e("Failed to read file", ex.toString());
                }
            } catch (FileNotFoundException ex) {
                Log.e("Failed to load file: file not found", file.toString());
            }
        } catch (Exception ex) {
            Log.e("Failed to find directory", ex.toString());
        }
        return null;
    }
    */
}

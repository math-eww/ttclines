package info.mattsaunders.apps.ttc_line;

import java.util.ArrayList;

/**
 * Transit Route object
 */
public class TransitRoute {
    private String routeId;
    private String routeName;
    private double latMin;
    private double latMax;
    private double lngMin;
    private double lngMax;
    private ArrayList<TransitStop> stopsList;

    public TransitRoute(String routeId, String routeName) {
        this.routeId = routeId;
        this.routeName = routeName;
    }

    public void setLatMin(double latMin){
        this.latMin = latMin;
    }
    public void setLatMax(double latMax){
        this.latMax = latMax;
    }
    public void setLngMin(double lngMin){
        this.lngMin = lngMin;
    }
    public void setLngMax(double lngMax){
        this.lngMax = lngMax;
    }
    public void setStopsList(ArrayList<TransitStop> stopsList) {
        this.stopsList = stopsList;
    }

    public String getRouteId(){
        return routeId;
    }
    public String getRouteName(){
        return routeName;
    }
    public ArrayList<TransitStop> getStopsList() {
        return stopsList;
    }
    public double getLatMin(){
        return latMin;
    }
    public double getLatMax(){
        return latMax;
    }
    public double getLngMin(){
        return lngMin;
    }
    public double getLngMax(){
        return lngMax;
    }
}

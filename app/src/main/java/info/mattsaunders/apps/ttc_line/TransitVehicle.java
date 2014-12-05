package info.mattsaunders.apps.ttc_line;

import com.google.android.gms.maps.model.LatLng;

/**
 * Transit Vehicle object
 */
public class TransitVehicle {
    private String id;
    private String routeTag;
    private LatLng location;
    private int secSinceReport;

    public TransitVehicle(String id, String routeTag, LatLng location, int secSinceReport) {
        this.id = id;
        this.routeTag = routeTag;
        this.location = location;
        this.secSinceReport = secSinceReport;
    }

    public String getVehicleRoute(){
        return routeTag;
    }
    public String getId(){
        return id;
    }
    public LatLng getLocation(){
        return location;
    }
    public int getSecSinceReport() { return secSinceReport; }
}

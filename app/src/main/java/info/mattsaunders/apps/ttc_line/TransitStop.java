package info.mattsaunders.apps.ttc_line;

import com.google.android.gms.maps.model.LatLng;

/**
 * Transit Stop object
 */
public class TransitStop {
    private String stopTag;
    private String stopTitle;
    private LatLng location;
    private String stopId;

    public TransitStop(String stopTag, String stopTitle, LatLng location, String stopId) {
        this.stopTitle = stopTitle;
        this.stopTag = stopTag;
        this.stopId = stopId;
        this.location = location;
    }

    public String getStopTag(){
        return stopTag;
    }
    public String getStopId(){
        return stopId;
    }
    public LatLng getLocation(){
        return location;
    }
    public String getStopTitle() { return stopTitle; }
}

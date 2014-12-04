package info.mattsaunders.apps.ttc_line;

import com.google.android.gms.maps.model.LatLng;

/**
 * Transit Vehicle object
 */
public class TransitVehicle {
    private String name;
    private int id;
    private LatLng location;

    public TransitVehicle(String name, int id, LatLng location) {
        this.name = name;
        this.id = id;
        this.location = location;
    }

    public String getVehicleName(){
        return name;
    }
    public int getId(){
        return id;
    }
    public LatLng getLocation(){
        return location;
    }
}

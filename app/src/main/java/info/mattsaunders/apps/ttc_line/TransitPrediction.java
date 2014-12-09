package info.mattsaunders.apps.ttc_line;

/**
 * Object to store prediction data
 */
public class TransitPrediction {
    private String time;
    private String route;

    public TransitPrediction(String time, String route) {
        this.time = time;
        this.route = route;
    }

    public String getTime() { return time; }
    public String getRoute() { return route; }
}

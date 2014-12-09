package info.mattsaunders.apps.ttc_line;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.Marker;

/**
 * Customized info window (for when marker item is clicked)
 * - to allow multiple lines - one for each TTC line that serves that particular stop
 * ie stop on Queen street might have 501, 502, 503
 */
public class CustomInfoWindow implements GoogleMap.InfoWindowAdapter {
    private View popup=null;
    private LayoutInflater inflater=null;

    CustomInfoWindow(LayoutInflater inflater) {
        this.inflater=inflater;
    }

    @Override
    public View getInfoWindow(Marker marker) {
        return(null);
    }
    @Override
    public View getInfoContents(Marker marker) {
        if (popup == null) {
            popup=inflater.inflate(R.layout.info_window_layout, null);
        }

        TextView tv=(TextView)popup.findViewById(R.id.title);
        tv.setText(marker.getTitle());
        tv=(TextView)popup.findViewById(R.id.snippet);
        tv.setText(marker.getSnippet());

        return(popup);
    }
}

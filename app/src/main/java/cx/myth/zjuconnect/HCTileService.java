package cx.myth.zjuconnect;

import android.content.Context;
import android.content.SharedPreferences;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class HCTileService extends TileService {
    private SharedPreferences mPrefs;
    private SharedPreferences.OnSharedPreferenceChangeListener mListener;

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        mPrefs = getSharedPreferences("tile_prefs", Context.MODE_PRIVATE);
        mListener = (sharedPreferences, key) -> {
            if ("tile_state".equals(key)) {
                Tile tile = getQsTile();
                String state = sharedPreferences.getString(key, "");
                switch (state) {
                    case "cx.myth.zjuconnect.LOGIN_FAILED":
                    case "cx.myth.zjuconnect.STACK_STOPPED":
                    case "cx.myth.zjuconnect.STOP_VPN":
                        tile.setState(Tile.STATE_INACTIVE);
                        break;
                    case "cx.myth.zjuconnect.LOGIN_SUCCEEDED":
                        tile.setState(Tile.STATE_ACTIVE);
                        break;
                }
                tile.updateTile();
            }
        };

        mPrefs.registerOnSharedPreferenceChangeListener(mListener);
        getQsTile().updateTile();
    }

    @Override
    public void onTileRemoved() {
        super.onTileRemoved();
        if (mListener != null && mPrefs != null) {
            mPrefs.unregisterOnSharedPreferenceChangeListener(mListener);
        }
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
    }

    @Override
    public void onClick() {
        super.onClick();
    }
}

package cx.myth.zjuconnect;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class HCTileService extends TileService {
    private SharedPreferences mPrefs;
    private final SharedPreferences.OnSharedPreferenceChangeListener mListener = (sharedPreferences, key) -> {
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
    private boolean registered = false;

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        if (mPrefs == null) {
            mPrefs = getSharedPreferences("tile_prefs", Context.MODE_PRIVATE);
        }
        if (!registered) {
            mPrefs.registerOnSharedPreferenceChangeListener(mListener);
            registered = true;
        }
        getQsTile().updateTile();
    }

    @Override
    public void onTileRemoved() {
        super.onTileRemoved();
        if (registered) {
            mPrefs.unregisterOnSharedPreferenceChangeListener(mListener);
            registered = false;
        }
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        if (!registered) {
            if (mPrefs == null) {
                mPrefs = getSharedPreferences("tile_prefs", Context.MODE_PRIVATE);
            }
            mPrefs.registerOnSharedPreferenceChangeListener(mListener);
            registered = true;
        }
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
    }

    @Override
    public void onClick() {
        super.onClick();

        // VPN 运行中
        if (MainActivity.isRunning) {
            if (mPrefs == null) {
                mPrefs = getSharedPreferences("tile_prefs", Context.MODE_PRIVATE);
            }
            SharedPreferences.Editor mEditor = mPrefs.edit();
            mEditor.putString("tile_state", "cx.myth.zjuconnect.STOP_VPN");
            mEditor.apply();
            stopService(new Intent(this, MyVpnService.class));
        } else {
            SharedPreferences sharedPreferences = getSharedPreferences("MySettings", Context.MODE_PRIVATE);
            String username = sharedPreferences.getString("username", "");
            String password = sharedPreferences.getString("password", "");
            new Thread(() -> {
                String host = "stuvpn.fudan.edu.cn";

                try {
                    InetAddress address = InetAddress.getByName(host);
                    host = address.getHostAddress();
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }

                String hostIp = host;

                Intent intent = new Intent(this, MyVpnService.class);

                intent.putExtra("server", hostIp + ":443");
                intent.putExtra("username", username);
                intent.putExtra("password", password);
                startService(intent);
            }).start();
        }
    }
}

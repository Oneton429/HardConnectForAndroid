package cx.myth.zjuconnect;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mobile.Mobile;

public class MyVpnService extends VpnService {
    private ParcelFileDescriptor tun;
    private final ExecutorService executors = Executors.newFixedThreadPool(1);
    private SharedPreferences mPrefs;
    private SharedPreferences.Editor mEditor;
    private final SharedPreferences.OnSharedPreferenceChangeListener mListener = (sharedPreferences, key) ->  {
        if ("tile_state".equals(key))  {
            String state = sharedPreferences.getString(key, "");
            if (state.equals("cx.myth.zjuconnect.STOP_VPN")) {
                stop();
                stopSelf();
            }
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        if (mPrefs == null) {
             mPrefs = getSharedPreferences("tile_prefs", Context.MODE_PRIVATE);
        }
        mEditor = mPrefs.edit();
        mPrefs.registerOnSharedPreferenceChangeListener(mListener);

        new Thread(() -> {
            String ip = Mobile.login(intent.getStringExtra("server"), intent.getStringExtra("username"), intent.getStringExtra("password"));
            if (ip.isEmpty()) {
                mEditor.putString("tile_state", "cx.myth.zjuconnect.LOGIN_FAILED");
                mEditor.apply();
                stopSelf();
                return;
            }
            mEditor.putString("tile_state", "cx.myth.zjuconnect.LOGIN_SUCCEEDED");
            mEditor.apply();
            MainActivity.isRunning = true;

            Builder builder = new Builder().addAddress(ip, 8).addRoute("10.0.0.0", 8).addDnsServer("114.114.114.114").setMtu(1400);
            tun = builder.establish();

            executors.submit(() -> {
                Mobile.startStack(tun.getFd());
                mEditor.putString("tile_state", "cx.myth.zjuconnect.STACK_STOPPED");
                mEditor.apply();
                stop();
                stopSelf();
            });
        }).start();

        return START_STICKY;
    }

    public void stop() {
        if (tun != null) {
            try {
                tun.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (executors != null) {
            executors.shutdownNow();
        }
    }

    @Override
    public void onDestroy() {
        stop();

        super.onDestroy();
    }
}
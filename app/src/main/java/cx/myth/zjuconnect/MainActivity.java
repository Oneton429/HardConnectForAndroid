package cx.myth.zjuconnect;

import static android.Manifest.permission.POST_NOTIFICATIONS;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.snackbar.Snackbar;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

import cx.myth.zjuconnect.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;
    private NotificationManager notificationManager;
    private Notification notification;
    private SharedPreferences mPrefs;
    private SharedPreferences.Editor mEditor;
    static public boolean isRunning = false;
    private final SharedPreferences.OnSharedPreferenceChangeListener mListener = (sharedPreferences, key) -> {
        if ("tile_state".equals(key)) {
            String state = sharedPreferences.getString(key, "");
            runOnUiThread(() -> {
                switch (state) {
                    case "cx.myth.zjuconnect.LOGIN_FAILED":
                        isRunning = false;
                        binding.fab.setImageResource(android.R.drawable.ic_media_play);
                        Snackbar.make(binding.getRoot(), R.string.login_failed, Snackbar.LENGTH_SHORT).setAnchorView(binding.fab).show();
                        binding.fab.setEnabled(true);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            notificationManager.cancel(1);
                        }
                        break;
                    case "cx.myth.zjuconnect.STACK_STOPPED":
                        isRunning = false;
                        binding.fab.setImageResource(android.R.drawable.ic_media_play);
                        Snackbar.make(binding.getRoot(), R.string.stopped, Snackbar.LENGTH_SHORT).setAnchorView(binding.fab).show();
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            notificationManager.cancel(1);
                        }
                        break;
                    case "cx.myth.zjuconnect.LOGIN_SUCCEEDED":
                        isRunning = true;
                        binding.fab.setImageResource(android.R.drawable.ic_media_pause);
                        Snackbar.make(binding.getRoot(), R.string.started, Snackbar.LENGTH_SHORT).setAnchorView(binding.fab).show();
                        binding.fab.setEnabled(true);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            notificationManager.notify(1, notification);
                        }
                        break;
                    case "cx.myth.zjuconnect.STOP_VPN":
                        isRunning = false;
                        binding.fab.setImageResource(android.R.drawable.ic_media_play);
                        Snackbar.make(binding.getRoot(), R.string.stopped, Snackbar.LENGTH_SHORT).setAnchorView(binding.fab).show();
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            notificationManager.cancel(1);
                        }
                        break;
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            MenuItem item = binding.toolbar.getMenu().findItem(R.id.action_about);
            if (item != null) {
                item.setVisible(destination.getId() == R.id.FirstFragment);
            }

            if (destination.getId() == R.id.FirstFragment) {
                binding.fab.show();
            } else {
                binding.fab.hide();
            }
        });

        SharedPreferences sharedPreferences = getSharedPreferences("MySettings", Context.MODE_PRIVATE);
        String value = sharedPreferences.getString("username", "");
        ((EditText) findViewById(R.id.usernameEditText)).setText(value);
        value = sharedPreferences.getString("password", "");
        ((EditText) findViewById(R.id.passwordEditText)).setText(value);

        Intent explicitIntent = new Intent("cx.myth.zjuconnect.LOGIN_FAILED");
        explicitIntent.setPackage("cx.myth.zjuconnect");
        if (mPrefs == null) {
            mPrefs = getSharedPreferences("tile_prefs", Context.MODE_PRIVATE);
        }
        mEditor = mPrefs.edit();
        mPrefs.registerOnSharedPreferenceChangeListener(mListener);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("zjuconnect", "Notification", NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "zjuconnect")
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("HardConnect")
                    .setContentText(getResources().getString(R.string.connected));

            builder.setOngoing(true);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, POST_NOTIFICATIONS) == PackageManager.PERMISSION_DENIED) {
                    ActivityCompat.requestPermissions(this, new String[]{POST_NOTIFICATIONS}, 1);
                }
            }

            notification = builder.build();
        }

        ActivityResultLauncher<Intent> getPermission = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != RESULT_OK) {
                Snackbar.make(binding.getRoot(), R.string.ask_permission, Snackbar.LENGTH_SHORT).setAnchorView(binding.fab).show();
                return;
            }

            startVpnService();
        });

        binding.fab.setOnClickListener(view -> {
            Intent intent = MyVpnService.prepare(this);
            if (intent != null) {
                getPermission.launch(intent);
            } else {
                if (!isRunning) {
                    startVpnService();
                } else {
                    mEditor.putString("tile_state", "cx.myth.zjuconnect.STOP_VPN");
                    mEditor.apply();
                }
            }
        });

        // 更新UI状态（在打开应用前使用快捷磁贴启用VPN）
        String state = mPrefs.getString("tile_state", "");
        mEditor.putString("tile_state", "");
        mEditor.apply();
        if (isRunning) {
            mEditor.putString("tile_state", state);
            mEditor.apply();
        }
    }

    private void startVpnService() {
        new Thread(() -> {
            String host = "stuvpn.fudan.edu.cn";

            try {
                InetAddress address = InetAddress.getByName(host);
                host = address.getHostAddress();
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }

            String hostIp = host;
            runOnUiThread(() -> {
                Intent intent = new Intent(this, MyVpnService.class);

                intent.putExtra("server", hostIp + ":443");
                intent.putExtra("username", ((EditText) findViewById(R.id.usernameEditText)).getText().toString());
                intent.putExtra("password", ((EditText) findViewById(R.id.passwordEditText)).getText().toString());
                startService(intent);

                binding.fab.setEnabled(false);
            });
        }).start();

        SharedPreferences sharedPreferences = getSharedPreferences("MySettings", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("username", ((EditText) findViewById(R.id.usernameEditText)).getText().toString());
        editor.putString("password", ((EditText) findViewById(R.id.passwordEditText)).getText().toString());
        editor.apply();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        if (Objects.requireNonNull(navController.getCurrentDestination()).getId() == R.id.FirstFragment) {
            if (id == R.id.action_about) {
                navController.navigate(R.id.action_FirstFragment_to_AboutFragment);
            }

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration) || super.onSupportNavigateUp();
    }
}
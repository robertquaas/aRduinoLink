package com.arbotics.databot.sciencejournal;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.support.v4.content.LocalBroadcastManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.View;

import java.sql.Struct;
import java.util.HashMap;
import java.util.List;

public class DatabotSelect extends Activity {

    private TextView bleDataText = null;
    private TextView bleStateText = null;
    private Intent dbIntent = null;
    boolean isDbServiceBound = false;
    boolean mServiceBound = false;
    private DatabotService mDatabotService;
    private Integer mButtonIdIndex = 0;
    private HashMap<Integer, DatabotSelectButton> buttons = new HashMap<>();

    final private String TAG = this.getClass().getSimpleName();
    private ScrollView scrollViewDevices = null;
    private LinearLayout scrollViewLayout = null;

    public static PendingIntent getPendingIntent(Context context) {
        int flags = 0;
        Intent intent = new Intent(context, DatabotSelect.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        return PendingIntent.getActivity(context, 0, intent, flags);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.databotselect_dialog);

        establishDataBotConnection();

        scrollViewDevices = findViewById(R.id.scrollviewdevices);

        scrollViewLayout = new LinearLayout(this);
        scrollViewLayout.setOrientation(LinearLayout.VERTICAL);
        scrollViewDevices.addView(scrollViewLayout);

        final int PERMISSION_REQUEST_COARSE_LOCATION = 456;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
        }

        bleStateText = (TextView) findViewById(R.id.ble_state_display);
        bleDataText = (TextView) findViewById(R.id.ble_packet_display);

        LocalBroadcastManager.getInstance(this).registerReceiver(mBleMessageReciever,
                new IntentFilter("ble-packet-data"));

        LocalBroadcastManager.getInstance(this).registerReceiver(mDeviceMessageReciever,
                new IntentFilter("ble-device-data"));

        Button scanButton = findViewById(R.id.scanbutton);
        Button bleButton = findViewById(R.id.blebutton);
        Button doneButton = findViewById(R.id.donebutton);

        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Button clicked = (Button) v;

            }
        });

        bleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Button clicked = (Button) v;
                for ( Integer key : buttons.keySet()){
                    scrollViewLayout.removeView(buttons.get(key));
                }
                buttons.clear();
                mDatabotService.disconnect();
                mDatabotService.selectedDevice = null;
                mDatabotService.toggleBluetooth();
            }
        });

        doneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendScienceJournalMessage("");
                finish();
            }
        });

    }

    @Override
    protected void onDestroy() {
        // Unregister since the activity is about to be closed.
        Log.i(TAG, "DatabotSelect dialog(?) activity destroyed");
        sendScienceJournalMessage("");
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBleMessageReciever);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mDeviceMessageReciever);
        disconnectDataBotConnection();
        super.onDestroy();
    }

    private void disconnectDataBotConnection() {
        if(mdbServiceConnection!=null) {
            unbindService(mdbServiceConnection);
        }
        isDbServiceBound = false;
    }

    private void establishDataBotConnection() {
        dbIntent = new Intent(this, DatabotService.class);
        bindService(dbIntent, mdbServiceConnection, Context.BIND_AUTO_CREATE);
        isDbServiceBound = true;
    }

    public void sendScienceJournalMessage(String message) {
        Intent intent = new Intent("scienceJournalService");
        intent.putExtra("updateTimeSinceDialog", message);
        LocalBroadcastManager.getInstance(DatabotSelect.this).sendBroadcast(intent);
    }

    private BroadcastReceiver mBleMessageReciever = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Get extra data included in the Intent
            String message = intent.getStringExtra("state");
            if(message != null){
                bleStateText.setText(message);
            }
            message = intent.getStringExtra("packet");
            if(message != null){
                bleDataText.setText(message);
            }
            //Log.d("receiver", "Got message: " + message);
        }
    };

    private BroadcastReceiver mDeviceMessageReciever = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Get extra data included in the Intent
            String message = intent.getStringExtra("clear");
            if(message!=null){
                scrollViewLayout.removeAllViews();
            }
            String[] messages = intent.getStringArrayExtra("newDevice");
            //Log.d("receiver", "Got message: " + message);
            DatabotSelectButton button = new DatabotSelectButton(DatabotSelect.this, messages[0], messages[1], messages[2], mButtonIdIndex);
            if(buttons.put(mButtonIdIndex, button) == null){
                scrollViewLayout.addView(button);
            }
            mButtonIdIndex++;
            button.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Button clicked = (Button) v;
                    DatabotSelectButton deviceButton = buttons.get(clicked.getId());
                    mDatabotService.setSelectedDevice(deviceButton.name + deviceButton.UUID);
                }
            });
        }
    };

    private ServiceConnection mdbServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mServiceBound = false;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            DatabotService.DatabotBinder myBinder = (DatabotService.DatabotBinder) service;
            mDatabotService = myBinder.getService();
            mServiceBound = true;

            List<DatabotDevice> devices = mDatabotService.getDevices();
            if(devices != null) {
                for (DatabotDevice device : mDatabotService.getDevices()) {
                    DatabotSelectButton button = new DatabotSelectButton(DatabotSelect.this, device.name, device.UUID, device.rssi, mButtonIdIndex);
                    if (buttons.put(mButtonIdIndex, button) == null) {
                        scrollViewLayout.addView(button);
                    }
                    mButtonIdIndex++;
                    button.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            Button clicked = (Button) v;
                            DatabotSelectButton deviceButton = buttons.get(clicked.getId());
                            mDatabotService.setSelectedDevice(deviceButton.name + deviceButton.UUID);
                        }
                    });
                }
            }
        }
    };

}
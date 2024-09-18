package com.example.kuljeetsingh.loneworker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Vibrator;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.os.Handler;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainLWActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener, SensorEventListener{
    private static final String TAG = MainLWActivity.class.getSimpleName();
    private static final int MAX_BLE_SCAN_TIME = 3000;


    IntentFilter intentFilter;

    String[] perms = {"android.permission.COARSE_LOCATION","android.permission.FINE_LOCATION"};
    static final int PERMITTED = 565;
    private LwBLEDeviceScanner bleScanner;
    private LwBLEService lwBLEService;

    private int mScanTime;
    private  Toast toast;

    private SensorManager mSensorManager;
    private Sensor  mMotionSensor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_lw);
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mMotionSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(this,mMotionSensor,SensorManager.SENSOR_DELAY_NORMAL);
        mScanTime = MAX_BLE_SCAN_TIME;
        intentFilter = LwBLEService.getIntentFilter();
        registerReceiver(lwb_BroadcastReceiver,intentFilter);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); // Stay Awake
    }

    public void onScanClick(View v) {
        ActivityCompat.requestPermissions(this,perms,PERMITTED);
    }

    public void onConnectClick(View v){
        String devName=((Spinner) findViewById(R.id.SpinDevices)).getSelectedItem().toString();
        Button btnNotify = (Button) findViewById(R.id.btnNotify);
        LwBLEDevice bdev = bleScanner.getDeviceByName(devName);
        Log.d(TAG,"Connecting to "+bdev.getDeviceName());
        lwBLEService = new LwBLEService(getApplicationContext());
        btnNotify.setEnabled(false);
        if(!lwBLEService.connectDevice(bdev)) {
            Log.e(TAG,"Error !! Cannot Connect");
        }
       else{
            btnNotify.setEnabled(true);
        }

    }
    public void onNotifyClick(View v){
        String devName=((Spinner) findViewById(R.id.SpinDevices)).getSelectedItem().toString();
        LwBLEDevice bdev = bleScanner.getDeviceByName(devName);
    //    Log.d(TAG,"Notifying Characteristics of "+bdev.getDeviceName());
        if(bdev.isConnected()){
            Log.d(TAG,"Notify Characteristics of  "+bdev.getDeviceName());
            lwBLEService.notifyGattServices(bdev);
       //     Context ctx = getApplicationContext();
         //   Intent i = new Intent(ctx, LwBLEService.class);
           // ctx.startService(i);
            lwBLEService.startPublishing();
            TextView tvReading = (TextView) findViewById(R.id.tvReading);
            tvReading.setText("Publishing data stream...");
        }
    }

    @Override
    public void onRequestPermissionsResult(int permsRequestCode, String[] permissions, int[] grantResults){
        switch(permsRequestCode){
            case PERMITTED:
                Log.d(TAG,"Permission Granted..");
                final Button btnScan = (Button) findViewById(R.id.btnScan);
                final Button btnConnect = (Button) findViewById(R.id.btnConnect);
                if (bleScanner == null) {
                    bleScanner = new LwBLEDeviceScanner(this);
                }
                if (!bleScanner.isScanning()) {
                    btnScan.setText("Stop");
                    bleScanner.StartScan(mScanTime);
                    Handler hdlr = new Handler();
                        Runnable r = new Runnable(){
                            @Override
                            public void run() {
                                btnScan.setText("Start");
                                if(!bleScanner.getBLEDevices().isEmpty()){
                                    btnConnect.setEnabled(true);
                                }
                            }
                        };
                        hdlr.postDelayed(r,mScanTime);
                    } else {
                    btnScan.setText("Start");
                    bleScanner.StopScan();
                    }
                break;
        }
    }



    private void updateList()
    {
        Spinner mSpinner = (Spinner) findViewById(R.id.SpinDevices);
        if(bleScanner==null) return;
        ArrayList<LwBLEDevice> mBLEDevices = bleScanner.getBLEDevices();

        List<String> devNames = new ArrayList<>();
        for(int i=0;i<mBLEDevices.size();i++){
            devNames.add(mBLEDevices.get(i).getDeviceName());

            ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this,R.layout.support_simple_spinner_dropdown_item,devNames);

            dataAdapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);

            mSpinner.setAdapter(dataAdapter);
        }

    }
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id){
        String item = parent.getSelectedItem().toString();
        Log.d(TAG,"Item Selected "+ item);
    }

    @Override
    public void onNothingSelected(AdapterView<?> arg0){

    }

    private final BroadcastReceiver lwb_BroadcastReceiver = new BroadcastReceiver(){

        public void onReceive(Context ctx, Intent intent){
            String action=intent.getAction();
            Log.d(TAG, "Received Notification" + action);


            updateList();
            if(action.equals(LwBLEDeviceScanner.ACTION_STATE_CHANGED)) return;
            String devName=((Spinner) findViewById(R.id.SpinDevices)).getSelectedItem().toString();
            LwBLEDevice bdev = bleScanner.getDeviceByName(devName);

            if(action.equals(LwBLEService.ACTION_DATA_AVAILABLE)){
                TextView tvHRReading = (TextView) findViewById(R.id.tvHRReading);
                tvHRReading.setText(String.format(" %d ",((HeartRateSensor)bdev).getLatestValue()));
            }
            else if(action.equals(LwBLEService.ACTION_LOCATION_CHANGED)){
                TextView tvLATReading = (TextView) findViewById(R.id.tvLATReading);
                tvLATReading.setText(String.format(" %f ",lwBLEService.getLatitude()));
                TextView tvLNGReading = (TextView) findViewById(R.id.tvLNGReading);
                tvLNGReading.setText(String.format(" %f ",lwBLEService.getLongitude()));
            }
            else if(action.contains("ACTION_GATT_")) {
                gattResponseHandler(bdev,action);
            }
            else if(action.contains("ACTION_MQTT_")) {
                mqttResponseHandler(intent);
            }
        }

        private void gattResponseHandler(LwBLEDevice bdev,String action) {
            TextView tvConnected = (TextView) findViewById(R.id.txtConnected);
            Button btnConnect = (Button) findViewById(R.id.btnConnect);

            if(action.equals(LwBLEService.ACTION_GATT_CONNECTED)||action.equals(LwBLEService.ACTION_GATT_SERVICES_DISCOVERED)){
                tvConnected.setText("Connected");
                btnConnect.setText("Disconnect");
                bdev.setConnected(true);
            }
            else if(action.equals(LwBLEService.ACTION_GATT_DISCONNECTED)){
                tvConnected.setText("Disconnected");
                btnConnect.setText("Connect");
                bdev.setConnected(false);
            }
            else if(action.equals(LwBLEService.ACTION_GATT_CONNECTION_FAILED)){
                tvConnected.setText("Connect Failed!");
                btnConnect.setText("Connect");
                bdev.setConnected(false);
                lwBLEService.stopPublishing();
            }
            else if(action.equals(LwBLEService.ACTION_GATT_DISCONNECTION_FAILED)){
                tvConnected.setText("Disconnect Failed!");
                btnConnect.setText("Disconnect");
                bdev.setConnected(true);
            }

        }

        private void mqttResponseHandler(Intent intent){
            String action = intent.getAction();

            if(action.equals(LwMQTTService.ACTION_MQTT_TOKEN_ARRIVED)){
                    String token = intent.getStringExtra(LwMQTTService.EXTRA_TOKEN_STRING);
                    String payload = intent.getStringExtra(LwMQTTService.EXTRA_PAYLOAD_STRING);
                    Log.d(TAG,"Received token : "+token +" | Payload : "+payload);
                if(token.equals(LwMQTTService.TOKEN_ALERT)) raiseAlert(payload);
            }
            else if(action.equals(LwMQTTService.ACTION_MQTT_TOKEN_DELIVERED)){
                String token = intent.getStringExtra(LwMQTTService.EXTRA_TOKEN_STRING);
                Log.d(TAG,"Token delivered : "+token);
            }
            else if(action.equals(LwMQTTService.ACTION_MQTT_CONNECTION_SUCCESS)){
                Log.d(TAG,"MQTT Connection established");
                lwBLEService.receiveAlerts();
            }
            else if(action.equals(LwMQTTService.ACTION_MQTT_CONNECTION_FAILED)){
                Log.e(TAG,"MQTT Connection failed !!");
                lwBLEService.stopPublishing();
            }
            else if(action.equals(LwMQTTService.ACTION_MQTT_DISCONNECT_SUCCESS)){
                Log.d(TAG,"MQTT Connection disconnected.");
                lwBLEService.stopPublishing();
            }
            else if(action.equals(LwMQTTService.ACTION_MQTT_DISCONNECT_FAILED)){
                Log.e(TAG,"MQTT Disconnect failed !!");
            }
            else if(action.equals(LwMQTTService.ACTION_MQTT_SUBSCRIBE_SUCCESS)){
                Log.d(TAG,"MQTT Subscribed success");
            }
            else if(action.equals(LwMQTTService.ACTION_MQTT_SUBSCRIBE_FAILED)){
                Log.e(TAG,"MQTT Subscribed failed !!");
                lwBLEService.stopPublishing();
            }
            else if(action.equals(LwMQTTService.ACTION_MQTT_UNSUBSCRIBE_SUCCESS)){
                Log.d(TAG,"MQTT Unsubscribe sucess");
            }
            else if(action.equals(LwMQTTService.ACTION_MQTT_UNSUBSCRIBE_FAILED)){
                Log.e(TAG,"MQTT Unsubscribed failed !!");
            }

        }
    };

    void raiseAlert(String message){
        toast = Toast.makeText(getApplicationContext(),"ALERT : "+message+ " !!",Toast.LENGTH_SHORT);
        toast.show();
        Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        Ringtone r = RingtoneManager.getRingtone(getApplicationContext(),notification);
        r.play();
        Vibrator v = (Vibrator) getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(500);
    }

    protected void onPause(){
        super.onPause();
        mSensorManager.unregisterListener(this);
        unregisterReceiver(lwb_BroadcastReceiver);
    }

    protected void onResume(){
        super.onResume();
        mSensorManager.registerListener(this,mMotionSensor,SensorManager.SENSOR_DELAY_NORMAL);
        registerReceiver(lwb_BroadcastReceiver,intentFilter);
    }
    //***************** Motion Sensor ************************
    long lastMotionUpdate;
    float lastx,lasty,lastz;


    @Override
    public void onSensorChanged(SensorEvent event) {
        Sensor rSensor = event.sensor;
        if(rSensor.getType()==Sensor.TYPE_ACCELEROMETER)
        {
            long curTime = System.currentTimeMillis();
            if((curTime - lastMotionUpdate) > 100) {
                lastMotionUpdate= curTime;
                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];

                if(Math.abs(x+y+z-lastx-lasty-lastz) > 10)
                {
                    TextView tvMOVReading = (TextView) findViewById(R.id.tvMOVReading);
                    tvMOVReading.setText("Detected");
                    Log.d(TAG,"motion detected..");
                    if(lwBLEService!=null)  lwBLEService.setMotionDetected(true);
                    lastx=x;
                    lasty=y;
                    lastz=z;
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor,int accuracy){

    }
}

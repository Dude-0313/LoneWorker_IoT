package com.example.kuljeetsingh.loneworker;

import android.Manifest;
import android.app.Service;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/**
 * Created by kuljeetsingh on 19/6/17.
 */

public class LwBLEService extends Service implements LocationListener{

    private static final String TAG = LwBLEService.class.getSimpleName();

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    private static final int NOTIFICATION_DURATION = 1000;
    private static final String MQTT_USER = "username";
    private static final String MQTT_PASSWD = "";

    private Status status = Status.FOUND;
    private boolean mDisconnecting;
    private Context mContext;
    private LwBLEDevice bleDevice;
    private BluetoothGatt mLwBLEGatt;
    private LwMQTTService lwMQTTService=null;

    HeartRateSensor mHeartRateSensor;
    boolean mMotionDetected=false;
    boolean mRunning=false;
    boolean mPublishing = false;
    public enum Status {
        FOUND,
        CONNECTING,
        RECONNECTING,
        DISCONNECTED,
        CONNECTED,
        CONNECTION_FAILED,
        DISCONNECTION_FAILED,
        SERVICES_DISCOVERING,
        SERVICES_DISCOVERED,
        ACTION_DATA_AVAILABLE,
        ACTION_LOCATION_CHANGED,
        EXTRA_DEVICE_ADDRESS;
        public boolean isConnected() {
            return (this == CONNECTED) ||
                    (this == SERVICES_DISCOVERED) ||
                    (this == SERVICES_DISCOVERING);
        }

        public String getIntent() {
            return String.format("com.example.kuljeetsingh.ble.ACTION_GATT_%s", this.toString());
        }
    }
    public static final String ACTION_GATT_CONNECTED = Status.CONNECTED.getIntent();
    public static final String ACTION_GATT_CONNECTING = Status.CONNECTING.getIntent();
    public static final String ACTION_GATT_RECONNECTING = Status.RECONNECTING.getIntent();
    public static final String ACTION_GATT_DISCONNECTED = Status.DISCONNECTED.getIntent();
    public static final String ACTION_GATT_CONNECTION_FAILED = Status.CONNECTION_FAILED.getIntent();
    public static final String ACTION_GATT_DISCONNECTION_FAILED = Status.DISCONNECTION_FAILED.getIntent();
    public static final String ACTION_GATT_SERVICES_DISCOVERING = Status.SERVICES_DISCOVERING.getIntent();
    public static final String ACTION_GATT_SERVICES_DISCOVERED = Status.SERVICES_DISCOVERED.getIntent();
    public static final String ACTION_DATA_AVAILABLE = Status.ACTION_DATA_AVAILABLE.getIntent();
    public static final String ACTION_LOCATION_CHANGED = Status.ACTION_LOCATION_CHANGED.getIntent();
    public static final String EXTRA_DEVICE_ADDRESS =  Status.EXTRA_DEVICE_ADDRESS.getIntent();


    private void updateStatus(Status s) {
        status = s;
        Log.d(TAG, "Update status of d=" + bleDevice.getDeviceName() + " to " + status.toString());
        broadcastUpdate(new Intent(s.getIntent()));
    }

    private final BluetoothGattCallback mLwBLEGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Connect or Disconnect Failed!! Error :" + Integer.toString(status));

                if (mDisconnecting)
                    updateStatus(Status.DISCONNECTION_FAILED);
                else
                    updateStatus(Status.CONNECTION_FAILED);
                return;
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, String.format("(%s) Device Connected", bleDevice.getDeviceAddress()));

                broadcastUpdate(new Intent(ACTION_GATT_CONNECTED));
                gatt.discoverServices();

                updateStatus(Status.SERVICES_DISCOVERING);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, String.format("(%s) Device Disconnected", bleDevice.getDeviceAddress()));
                updateStatus(Status.DISCONNECTED);


                if (mLwBLEGatt != null) {
                    mLwBLEGatt.close();
                    mLwBLEGatt = null;
                }

                if (!mDisconnecting) {
                    connectDevice(bleDevice);  // Reconnecting...
                    updateStatus(Status.RECONNECTING);
                } else {
                    mDisconnecting = false;
                }
            }
        }


        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {

            updateStatus(Status.SERVICES_DISCOVERED);

        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
           // Log.d(TAG, "*************** Characteristic Changed *****************8");
            if (mHeartRateSensor == null) return;
            BluetoothGattService[] lookupServices = mHeartRateSensor.getBleServices();
            for (BluetoothGattService service : lookupServices) {
            //    Log.d(TAG, String.format("Checking for server service %s", service.getUuid().toString()));
                BluetoothGattService foundService = mLwBLEGatt.getService(service.getUuid());
                if (foundService == null)
                    continue;

            //    Log.d(TAG, "Found service..");
            //    Log.d(TAG, "Finding characteristics...");

                for (BluetoothGattCharacteristic christic : service.getCharacteristics()) {
                    BluetoothGattCharacteristic foundCharacteristic = foundService.getCharacteristic(christic.getUuid());

                    if (foundCharacteristic == null)
                        continue;

              //      Log.d(TAG, String.format("Characteristic found: %s", foundCharacteristic.getUuid()));
                    mHeartRateSensor.updateValue(characteristic);
                    broadcastUpdate(new Intent(ACTION_DATA_AVAILABLE));
                }
            }
        }
    };

    LwBLEService(Context ctx)
    {
        mContext=ctx;
        lwMQTTService = new LwMQTTService();
    }

    public static IntentFilter getIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_GATT_CONNECTED);
        intentFilter.addAction(ACTION_DATA_AVAILABLE);
        intentFilter.addAction(ACTION_LOCATION_CHANGED);
        intentFilter.addAction(ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(ACTION_GATT_CONNECTION_FAILED);
        intentFilter.addAction(ACTION_GATT_DISCONNECTION_FAILED);
        intentFilter.addAction(ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(ACTION_GATT_SERVICES_DISCOVERING);
        intentFilter.addAction(ACTION_GATT_RECONNECTING);
        intentFilter.addAction(ACTION_GATT_CONNECTING);
        intentFilter.addAction(LwBLEDeviceScanner.ACTION_STATE_CHANGED);
        LwMQTTService.getIntentFilter(intentFilter);
        return intentFilter;
    }

    private void broadcastUpdate(final Intent intent) {
        intent.putExtra(EXTRA_DEVICE_ADDRESS, bleDevice.getDeviceAddress());
        mContext.sendBroadcast(intent);
    }



    public boolean connectDevice(LwBLEDevice bdev){
        if(bdev==null) return false;
        bleDevice = bdev;
        if(bleDevice.getDeviceAddress()==null) return false;
        if(bleDevice.isConnected()) return true;
        mDisconnecting=false;
        mLwBLEGatt = bleDevice.getBaseDevice().connectGatt(mContext,false,mLwBLEGattCallback);
        lwMQTTService.openConnection(mContext,MQTT_USER, MQTT_PASSWD);
        return true;
    }

    public void disconnectDevice(LwBLEDevice bdev) {
        if (bdev == null ) {
            Log.w(TAG, "Device not initialized !!");
            return;
        }
        mLwBLEGatt.disconnect();
    }

    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enabled){

            if (mLwBLEGatt == null) {
                Log.w(TAG, "BluetoothAdapter not initialized");
                return;
            }
            mLwBLEGatt.setCharacteristicNotification(characteristic, enabled);

        try {
            // This is specific to Heart Rate Measurement.
            Log.d(TAG, "Compare in "+characteristic.getUuid()+" and "+HeartRateSensor.UUID_SENSOR_HEART_RATE_MEASUREMENT);
            if (HeartRateSensor.UUID_SENSOR_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid().toString())) {
                BluetoothGattDescriptor descriptor = characteristic
                        .getDescriptor(UUID
                                .fromString(HeartRateSensor.CLIENT_CHARACTERISTIC_CONFIG));
                descriptor
                        .setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                mLwBLEGatt.writeDescriptor(descriptor);
                Log.d(TAG,
                        "Setting up notification for heartrate....");

            }
        } catch (Exception e) {
            Log.d(TAG,
                    "Exception while setting up notification for heartrate.", e);
        }
    }

    public void notifyGattServices(LwBLEDevice bdev) {
        BluetoothGattService[] lookupServices=null;
        if (bdev == null) return;
        if (bdev.getSensorName() == HeartRateSensor.SensorName) {
            mHeartRateSensor = (HeartRateSensor) bdev;
            lookupServices = mHeartRateSensor.getBleServices();
        }
        for (BluetoothGattService service : lookupServices) {
            Log.d(TAG, String.format("Checking for server service %s", service.getUuid().toString()));
            BluetoothGattService foundService = mLwBLEGatt.getService(service.getUuid());
            if (foundService == null)
                continue;

            Log.d(TAG, "Found service..");
            Log.d(TAG, "Finding characteristics...");

            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                BluetoothGattCharacteristic foundCharacteristic = foundService.getCharacteristic(characteristic.getUuid());

                if (foundCharacteristic == null)
                    continue;

                Log.d(TAG, String.format("Characteristic found: %s", foundCharacteristic.getUuid()));
                setCharacteristicNotification(foundCharacteristic, true);
            }
        }
    }


    public class LocalBinder extends Binder {
        LwBLEService getService() {
            return LwBLEService.this;
        }
    }
    private final IBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) { return mBinder;}

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        return START_STICKY;
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        mRunning=false;
    }

    public void startPublishing(){
        Log.d(TAG,"Starting the publication service..");
        final Location location1 = getLocation();

        Runnable r = new Runnable() {
            @Override
            public void run() {
        //        Location location1 ;
                int mHRV=0 ;
                while(mPublishing){
                    try {
                        Thread.sleep(NOTIFICATION_DURATION);
                    }catch (Exception e){}
          //          location1 = getLocation();
                    String timestamp=getCurrentTimeStamp();
                    mHRV=mHeartRateSensor.getLatestValue();
                    Log.d(TAG,String.format("Time : %s, HeartRate : %d, Latitude : %f, Longitude : %f, Motion detected : %s ",
                            timestamp,mHRV,location1.getLatitude(),location1.getLongitude(),mMotionDetected));
                    lwMQTTService.publishTopic(LwMQTTService.TOKEN_TIMESTAMP,timestamp);
                    lwMQTTService.publishTopic(LwMQTTService.TOKEN_HEARTRATE,String.format("%d",mHRV));
                    lwMQTTService.publishTopic(LwMQTTService.TOKEN_LOCATION_LAT,String.format("%f",location1.getLatitude()));
                    lwMQTTService.publishTopic(LwMQTTService.TOKEN_LOCATION_LONG,String.format("%f",location1.getLongitude()));
                    lwMQTTService.publishTopic(LwMQTTService.TOKEN_MOVEMENT,String.format("%s",mMotionDetected));
                }
            }
        };

        Thread t = new Thread(r);
        mPublishing = true;
        t.start();
    }
    public void stopPublishing(){
        mPublishing=false ;
    }

    public void receiveAlerts(){
        lwMQTTService.subscribeTopic(LwMQTTService.TOKEN_ALERT);
    }

    public String getCurrentTimeStamp(){
        try {

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String currentDateTime = dateFormat.format(new Date()); // Find todays date

            return currentDateTime;
        } catch (Exception e) {
            e.printStackTrace();

            return null;
        }
    }

    public boolean IsPublishing(){return mPublishing;}

//***************** Location Service ************************
    boolean isGPSEnabled = false;
    boolean isNetworkEnabled = false;
    boolean canGetLocation = false;

    Location mLocation;
    double longitude;
    double latitude;
    protected LocationManager locationManager;

    // The minimum distance to change Updates in meters
    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 1; // 1 meter

    // The minimum time between updates in milliseconds
    private static final long MIN_TIME_BW_UPDATES = 1000 * 10; // 10 Seconds

    public Location getLocation() {
        try {
            locationManager = (LocationManager) mContext.getSystemService(LOCATION_SERVICE);
            isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            System.out.println("GPS :" + isGPSEnabled);
            System.out.println("Network :" + isNetworkEnabled);
            if (isGPSEnabled || isNetworkEnabled) {
                this.canGetLocation = true;
                if(Build.VERSION.SDK_INT > 23)
                if((ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_COARSE_LOCATION)!= PackageManager.PERMISSION_GRANTED)
                        && (ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION)!= PackageManager.PERMISSION_GRANTED)){
                    Log.e(TAG,"Permissions not granted !!");
                    return null;
                }
                if (isNetworkEnabled) {
                    locationManager.requestLocationUpdates (LocationManager.NETWORK_PROVIDER, MIN_TIME_BW_UPDATES, MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
                    if (locationManager != null) {
                        mLocation = locationManager.getLastKnownLocation (LocationManager.NETWORK_PROVIDER);
                        if (mLocation != null) {
                            longitude = mLocation.getLongitude();
                            latitude = mLocation.getLatitude();
                        }
                    }
                }
                if (isGPSEnabled) {
                    if (mLocation == null) {
                        locationManager.requestLocationUpdates (LocationManager.GPS_PROVIDER, MIN_TIME_BW_UPDATES, MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
                        if (locationManager != null) {
                            mLocation = locationManager.getLastKnownLocation (LocationManager.GPS_PROVIDER);
                            if (mLocation != null) {
                                longitude = mLocation.getLongitude();
                                latitude = mLocation.getLatitude();
                            }
                        }
                    }
                }
                System.out.println("Longitude: " + longitude + "  Latitude :" + latitude);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return mLocation;
    }

    public double getLongitude()
    {
        return mLocation.getLongitude();
    }

    public double getLatitude()
    {
        return mLocation.getLatitude();
    }

    public void setMotionDetected(boolean flag) { mMotionDetected=flag;}

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }
    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onLocationChanged(Location location) {
        broadcastUpdate(new Intent(ACTION_LOCATION_CHANGED));
        Log.d(TAG,"Location Changed ..");
        mLocation = location;
    }

    @Override
    public void onProviderDisabled(String provider) {

    }
}

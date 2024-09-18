package com.example.kuljeetsingh.loneworker;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
//import android.bluetooth.le.ScanCallback;
import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;

/**
 * Created by kuljeetsingh on 19/6/17.
 */

public class HeartRateSensor extends LwBLEDevice implements InterfaceSensor {

    public static String SensorName = "Heart Rate Sensor";
    public static String SensorType = "Polar H7";

    private boolean mIsUpdated = false;
    private int mValue = 0;
    private Date timestamp= new Date();
    private ArrayList<BluetoothGattService> mBleServices;


    public static final String UUID_SENSOR_SERVICE = "0000180d-0000-1000-8000-00805f9b34fb";
    public static final String UUID_SENSOR_HEART_RATE_MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb";
    public static final String UUID_SENSOR_BODY_LOCATION = "00002a38-0000-1000-8000-00805f9b34fb";
    public static final String UUID_HEART_RATE_CONTROL_POINT = "00002A39-0000-1000-8000-00805f9b34fb";
    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    private static final int EXPENDED_ENERGY_FORMAT = BluetoothGattCharacteristic.FORMAT_UINT16;
    private static final int INITIAL_EXPENDED_ENERGY = 0;

    private BluetoothGattService mHeartRateService;
    private BluetoothGattCharacteristic mHeartRateMeasurementCharacteristic;
    private BluetoothGattCharacteristic mBodySensorLocationCharacteristic;
    private BluetoothGattCharacteristic mHeartRateControlPointCharacteristic;

    private static final int COUNTDOWNVAL = 10;
    private int countdown=COUNTDOWNVAL;

    HeartRateSensor(Context ctx, BluetoothDevice btdev, int devrssi){
        super(ctx,btdev,devrssi);
        mSensorName=SensorName;
        mBleServices = new ArrayList<>();
        mHeartRateService = new BluetoothGattService(
                UUID.fromString(UUID_SENSOR_SERVICE),
                BluetoothGattService.SERVICE_TYPE_PRIMARY);
        mHeartRateMeasurementCharacteristic = new BluetoothGattCharacteristic(
                UUID.fromString(UUID_SENSOR_HEART_RATE_MEASUREMENT),
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,0);
        mHeartRateControlPointCharacteristic =  new BluetoothGattCharacteristic(
                UUID.fromString(UUID_HEART_RATE_CONTROL_POINT),
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE);
        mBodySensorLocationCharacteristic= new BluetoothGattCharacteristic(
                UUID.fromString(UUID_SENSOR_BODY_LOCATION),
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);
        mHeartRateService.addCharacteristic(mHeartRateMeasurementCharacteristic);
        mBleServices.add(mHeartRateService);
    }

    public BluetoothGattService[] getBleServices() {
        return mBleServices.toArray(new BluetoothGattService[mBleServices.size()]);
    }

    @Override
    public void updateValue(BluetoothGattCharacteristic characteristic) {
        timestamp = new Date();
        int tmpVal = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
     //   Log.d(SensorName,String.format("HRS Reading : %d",mValue));
        mValue = tmpVal;
        mIsUpdated = true;
        countdown=COUNTDOWNVAL;
    }


    @Override
    public boolean isUpdated(){
        return mIsUpdated;
    }

    @Override
    public int getLatestValue(){
        if(!mIsUpdated) {
            countdown--;
            if(countdown <0 ) {
                mValue=0;
                countdown=0;
            }
        }
        mIsUpdated=false;
        return (int)((double) mValue * 4.8152);
    }


}

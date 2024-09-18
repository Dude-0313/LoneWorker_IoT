package com.example.kuljeetsingh.loneworker;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;


/**
 * Created by kuljeetsingh on 12/6/17.
 */

public class LwBLEDeviceScanner {

    private static final String TAG = LwBLEDeviceScanner.class.getSimpleName();
    public final static String ACTION_STATE_CHANGED = "com.example.kuljeetsingh.ble.ACTION_STATE_CHANGED";

    private  BluetoothAdapter mBLEAdapter;
    private BluetoothLeScanner bleScanner;
   // private Settings mSettings;
    private boolean mIsScanning = false;
    private Handler mHandler;
    private ArrayList<LwBLEDevice> mLeDevices;

    private Context mContext;

    private ScanCallback scanCB =
            new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    Log.d(TAG,"<<onScanResult>>");
                    super.onScanResult(callbackType, result);
                    if(!mLeDevices.contains(result.getDevice()))
                    {
                        Log.d(TAG,"Found Device " + result.toString());
                        for(int i=0;i<mLeDevices.size();i++) {
                            if(mLeDevices.get(i).getDeviceAddress().equals(result.getDevice().getAddress())) return;
                        }
                        if(result.toString().contains(HeartRateSensor.SensorType)) {
                            Log.d(TAG,"Add Device " + result.toString());
                            LwBLEDevice foundLeDevice = new HeartRateSensor(mContext, result.getDevice(), result.getRssi());
                            mLeDevices.add(foundLeDevice);
                            NotifyChange();
                        }
                    }
                }

                @Override
                public void onBatchScanResults(List<ScanResult> results) {
                    Log.d(TAG,"<<onBatchScanResults>>");
                    super.onBatchScanResults(results);
                }

                @Override
                public void onScanFailed(int errorCode) {
                    Log.d(TAG,"<<onScanFailed>>");
                    super.onScanFailed(errorCode);
                    mLeDevices.clear();
                }
            };

    LwBLEDeviceScanner(Context ctx){
        final BluetoothManager mBTManager = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        mBLEAdapter = mBTManager.getAdapter();
        if(mBLEAdapter==null)
        {
            Toast.makeText(ctx, "Bluetooth Not Supported !!", Toast.LENGTH_SHORT).show();
            throw new RuntimeException("Bluetooth Unavailable");
        }
        bleScanner = mBLEAdapter.getBluetoothLeScanner();
        if(bleScanner==null)
        {
            throw new RuntimeException("BLE Not Supported");
        }
        mHandler = new Handler();
        mLeDevices = new ArrayList<>();
        mContext = ctx;
    }

    public void StartScan(int timePeriod){
        if(!mIsScanning){
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mIsScanning = false;
                    bleScanner.stopScan(scanCB);
                    Log.i(TAG,"Stopping BLE Scan..");
                }
            },timePeriod);
            mIsScanning=true;
            Log.i(TAG,"Starting BLE Scan..");
            bleScanner.startScan(scanCB);
        }
    }


    public void StopScan() {
        mIsScanning = false;
        bleScanner.stopScan(scanCB);
        Log.i(TAG,"Starting BLE Scan..");
        mLeDevices.clear();
    }

    public boolean isScanning(){ return mIsScanning;}

    void NotifyChange()
    {
        Intent intent = new Intent();
        intent.setAction(ACTION_STATE_CHANGED);
        mContext.sendBroadcast(intent);
    }

    public ArrayList<LwBLEDevice> getBLEDevices(){
        return mLeDevices;
    }

    public LwBLEDevice getDeviceByName(String dname){
        if(mLeDevices.isEmpty()) return null;
        for(int d=0;d<mLeDevices.size();d++)
        if(mLeDevices.get(d).getDeviceName().equals(dname)) return mLeDevices.get(d);
        return null;
    }
}

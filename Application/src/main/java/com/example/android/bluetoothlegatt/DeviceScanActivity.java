/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.bluetoothlegatt;

import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 */
public class DeviceScanActivity extends ListActivity {
    private LeDeviceListAdapter mLeDeviceListAdapter;
    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private Handler mHandler;

    private static final int REQUEST_ENABLE_BT = 1;
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActionBar().setTitle(R.string.title_devices);
        mHandler = new Handler();

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        if (!mScanning) { // 如果不是正在 Scan
            menu.findItem(R.id.menu_stop).setVisible(false); // 看不到 STOP
            menu.findItem(R.id.menu_scan).setVisible(true);  // 可以開始 SCAN
            menu.findItem(R.id.menu_refresh).setActionView(null);
        } else { // 正在 SCAN
            menu.findItem(R.id.menu_stop).setVisible(true);   // 可以 STOP
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(
                    R.layout.actionbar_indeterminate_progress); // 轉轉轉
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_scan: // 如果按的是 SCAN
                mLeDeviceListAdapter.clear(); // 因為這個 clear()
                                                //  畫面的資料先全部消失
                scanLeDevice(true);
                break;
            case R.id.menu_stop:
                scanLeDevice(false);
                // Mark??? 10/31/2015
                // 不要執行scanLeDevice就好了
                //  為什麼要設計成調用一個不執行的function?

                break;
        }
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }

        // Initializes list view adapter.
        mLeDeviceListAdapter = new LeDeviceListAdapter();
        setListAdapter(mLeDeviceListAdapter);
        scanLeDevice(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onPause() {
        super.onPause();
        scanLeDevice(false);
        mLeDeviceListAdapter.clear();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        final BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);
        if (device == null) return;
        final Intent intent = new Intent(this, DeviceControlActivity.class);
        intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, device.getName());
        intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());
        if (mScanning) {
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
            mScanning = false;
        }
        startActivity(intent);
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);// depreciated API 21
                    invalidateOptionsMenu(); // => SCAN
                }
            }, SCAN_PERIOD);// 預設 SCAN 十秒
            mScanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else { // 如果按的是 STOP
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback); //畫面會停在SCAN了多少個算多少個
        }
        invalidateOptionsMenu(); //
    }

    // Adapter for holding devices found through scanning.
    private class LeDeviceListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> mLeDevices;
        private LayoutInflater mInflator;

        public LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<BluetoothDevice>();
            mInflator = DeviceScanActivity.this.getLayoutInflater();
        }

        public void addDevice(BluetoothDevice device) {
            if(!mLeDevices.contains(device)) {
                mLeDevices.add(device);
            }
        }

        public BluetoothDevice getDevice(int position) {
            return mLeDevices.get(position);
        }

        public void clear() {
            mLeDevices.clear();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.listitem_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = (TextView) view.findViewById(R.id.device_address);
                viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            BluetoothDevice device = mLeDevices.get(i);
            final String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0)
                viewHolder.deviceName.setText(deviceName);
            else
                viewHolder.deviceName.setText(R.string.unknown_device);
            viewHolder.deviceAddress.setText(device.getAddress());

            return view;
        }
    }

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {

        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, final byte[] scanRecord) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
//                    Log.d("markchen"," 這裡是要加device"+ device+" 同時有 rssi 和 scanRecord");
//                    Log.d("markchen","  scanRecord is "+ scanRecord.toString());
//                    Log.d("markchen","  scanRecord size is "+ scanRecord.length);

                    byte b;
                    StringBuilder sb=new StringBuilder();
                    for (int i=0;i<scanRecord.length;i++){
                        b=scanRecord[i];
//                        Log.d("markchen","  i="+ i+" "+b+" "+ String.format("%3d %02X  ", b,b));
                        sb.append(String.format("%02X ", b));
                    }
                    Log.d("markchen","  scanRec is "+sb.toString());

                    byte[] scanRecordBeaconType=new byte[9];
                    System.arraycopy(scanRecord,0,scanRecordBeaconType,0,9);




              //      02 01 06 1a ff 4c 00 02 15  # Apple's fixed iBeacon advertising prefix
                    String strIBeaconPrefix="0201061aff4c000215";
                    Hex hex=new Hex() ;
                    byte[] bytesIBeacon= new byte[9];
                    try {
                        bytesIBeacon = hex.decodeHex(strIBeaconPrefix.toCharArray());

                    } catch (DecoderException e) {
                        e.printStackTrace();
                    }
                    // byte b;
                    StringBuilder sb2=new StringBuilder();
                    for (int i=0;i<bytesIBeacon.length;i++){
                        b=bytesIBeacon[i];
//                        Log.d("markchen", "bytesIBeacon  i=" + i + " " + b + " " + String.format("%3d %02X  ", b,b));
                        sb2.append(String.format("%02X ", b));
                    }
//                    Log.d("markchen","  bytesIBeacon ==> "+sb2.toString());

                   if ( Arrays.equals(scanRecordBeaconType,bytesIBeacon)){
                       Log.d("markchen","  Is iBeacon ==>  yes ***************");
                       mLeDeviceListAdapter.addDevice(device);
                       mLeDeviceListAdapter.notifyDataSetChanged();

                   }else{
                       Log.d("markchen","  Is iBeacon ==>  NO !!!!!!!!");

                   }

                }
            });
        }
    };

    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
    }
}
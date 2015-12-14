package gov.nist.perfloc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.os.Vibrator;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellLocation;
import android.telephony.CellSignalStrengthCdma;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;

//import gov.nist.perfloc.WiFi.WiFiRSSIMeasurement;

public class MainActivity extends Activity {

    Vibrator v;
    private final Handler handler = new Handler();

    // View related definitions
    ListView SensorListView;
    ExpandableListView expListView;

    ToggleButton StartStopButton;

    TextView numAPs, WiFiScanTime, tv_dotcounter, tv_sensor_event_counter, tv_timer_sec;

    // Expandable List Adapter related definitions
    ExpandableListAdapter expListAdapter;
    List<String> listDataHeader;
    HashMap<String, List<String>> listDataChild;
    List<String> WiFiList, CellList, SensorList_text;

    // WiFi Scanning related definitions
    WifiManager wifi;
    String wifis[];
    WifiScanReceiver wifiReceiver;
    long time_prev, time_current, time_diff;
    int dot_current = 0;

    // Cell Tower and Telephony related definitions
    TelephonyManager tm;

    // Sensor related definitions
    HashMap<String, float[]> sensorHashMap;
    HashMap<String, Long> sensorLastTimestampHashMap;
    HashMap<String, Long> sensorCurrentTimestampHashMap;

    //SensorManager mSensorManager;
    //List<Sensor> sensorList;
    //float s_accelerometer[], s_gravity[], s_gyroscope[], s_proximity[], s_light[],
    //        s_magneticfield[], s_magneticfielduncalibrated[], s_geomagneticrotationvector[], s_rotationvector[], s_linearacceleration[];

    private ExecutorService pool;
    volatile int dotCounter = 0;
    volatile long senor_event_counter = 0;
    private int sensor_sampling_frequency = SensorManager.SENSOR_DELAY_FASTEST;

    volatile int numberOfAPs=0, WiFiScanDuration=0, numberOfCellTowers=0, numberOfSensors=0;

    String  WiFi_output_file = android.os.Build.MODEL + "_WiFi",
            Sensors_output_file = android.os.Build.MODEL + "_Sensors",
            Dots_output_file = android.os.Build.MODEL + "_Dots";
    File dirname = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    String protocol_buffer_file_extension = "pbs"; // (p)rotocol(b)uffer(s)erialized

    BufferedOutputStream WiFi_BOS, Sensor_BOS, Dots_BOS;

    //String a; a.replaceAll("\\s+","");


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyWakelockTag");
        wakeLock.acquire();
        Log.v("WakeLock (isHeld?): ", wakeLock.isHeld() + ".");


        WiFi_output_file = dirname + "/" + WiFi_output_file.replaceAll("\\s+","") + "." + protocol_buffer_file_extension;
        Sensors_output_file = dirname + "/" + Sensors_output_file.replaceAll("\\s+","") + "." + protocol_buffer_file_extension;
        Dots_output_file = dirname + "/" + Dots_output_file.replaceAll("\\s+","") + "." + protocol_buffer_file_extension;

        createOutputFiles();

        v = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);

        StartStopButton = (ToggleButton) findViewById(R.id.toggleButton);

        numAPs = (TextView) findViewById(R.id.numAPs);
        WiFiScanTime = (TextView) findViewById(R.id.WiFiScanTime);
        tv_dotcounter = (TextView) findViewById(R.id.dotCounter);
        tv_sensor_event_counter = (TextView) findViewById(R.id.sensor_event_counter);
        tv_sensor_event_counter.setText(tv_sensor_event_counter + "");
        findViewById(R.id.title_sensor_event_counter);
        tv_timer_sec = (TextView) findViewById(R.id.timer_sec);

        expListView = (ExpandableListView) findViewById(R.id.expandableListView);

        // Initial view of the Expandable List View
        prepareListData();
        expListAdapter = new ExpandableListViewAdapter(getApplicationContext(), listDataHeader, listDataChild);
        expListView.setAdapter(expListAdapter);


        wifi=(WifiManager)getSystemService(Context.WIFI_SERVICE);
        wifiReceiver = new WifiScanReceiver();

        final PhoneStateListener phone = new PhoneStateListener() {
            @Override
            public void onCellInfoChanged(List<CellInfo> cellInfo) {
                int i = 0;
                super.onCellInfoChanged(cellInfo);
                TextView call_info = (TextView)findViewById(R.id.cellInfo);
                if (cellInfo != null) {
                    for (i = 0; i < cellInfo.size(); i++);
                    call_info.setText(i + "Cell Info Available");
                    } else {
                    call_info.setText("No Cell Info Available");
                    }
                }
            @Override
            public void onDataActivity(int direction) {
                super.onDataActivity(direction);
                TextView data_activity = (TextView)findViewById(R.id.dataActivity);
                switch (direction) {
                    case TelephonyManager.DATA_ACTIVITY_NONE:
                        data_activity.setText("No Data Activity");
                        break;
                    case TelephonyManager.DATA_ACTIVITY_IN:
                        data_activity.setText("Incoming Data Activity");
                        break;
                    case TelephonyManager.DATA_ACTIVITY_OUT:
                        data_activity.setText("Outgoing Data Activity");
                        break;
                    case TelephonyManager.DATA_ACTIVITY_INOUT:
                        data_activity.setText("Bi-directional Data Activity");
                        break;
                    case TelephonyManager.DATA_ACTIVITY_DORMANT:
                        data_activity.setText("Dormant Data Activity");
                        break;
                    default:
                        break;
                    }
                }
            @Override
            public void onServiceStateChanged(ServiceState serviceState) {
                super.onServiceStateChanged(serviceState);
                TextView service_state = (TextView)findViewById(R.id.serviceState);
                switch (serviceState.getState()) {
                    case ServiceState.STATE_IN_SERVICE:
                        service_state.setText("Phone in service");
                        break;
                    case ServiceState.STATE_OUT_OF_SERVICE:
                        service_state.setText("Phone out of service");
                        break;
                    case ServiceState.STATE_EMERGENCY_ONLY:
                        service_state.setText("Emergency service only");
                        break;
                    case ServiceState.STATE_POWER_OFF:
                        service_state.setText("Powered Off");
                        break;
                    }
                }
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                super.onCallStateChanged(state, incomingNumber);
                TextView callState = (TextView)findViewById(R.id.callState);
                switch (state) {
                    case TelephonyManager.CALL_STATE_IDLE:
                        callState.setText("Call State is IDLE");
                        break;
                    case TelephonyManager.CALL_STATE_RINGING:
                        callState.setText("Call State is RINGING");
                        break;
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                        callState.setText("Call State is OFFHOOK");
                        break;
                    default:
                        break;
                    }
                }
            @Override
            public void onCellLocationChanged(CellLocation location) {
                super.onCellLocationChanged(location);
                TextView cell_location = (TextView)findViewById(R.id.cellLocation);
                cell_location.setText(location.toString());
                }
            @Override
            public void onCallForwardingIndicatorChanged(boolean cfi) {
                super.onCallForwardingIndicatorChanged(cfi);
                TextView call_forwarding = (TextView)findViewById(R.id.callForwarding);
                if (cfi)
                    call_forwarding.setText("Call forward ON");
                else
                    call_forwarding.setText("Call forward OFF");
                }
            @Override
            public void onMessageWaitingIndicatorChanged(boolean mwi) {
                super.onMessageWaitingIndicatorChanged(mwi);
                TextView message_waiting = (TextView)findViewById(R.id.messageWaiting);
                if (mwi)
                        message_waiting.setText("Call forward ON");
                    else
                        message_waiting.setText("Call forward OFF");
                }
            };

        tm  = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        tm.listen(phone,PhoneStateListener.LISTEN_CALL_FORWARDING_INDICATOR |
                PhoneStateListener.LISTEN_CALL_STATE |
                PhoneStateListener.LISTEN_CELL_INFO |
                PhoneStateListener.LISTEN_CELL_LOCATION |
                PhoneStateListener.LISTEN_DATA_ACTIVITY |
                PhoneStateListener.LISTEN_DATA_CONNECTION_STATE |
                PhoneStateListener.LISTEN_MESSAGE_WAITING_INDICATOR |
                PhoneStateListener.LISTEN_SERVICE_STATE |
                PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);

        final readBuiltinSensors rbs = new readBuiltinSensors(MainActivity.this, 100);
        final applicationTimer aT = new applicationTimer();
        //pool = Executors.newFixedThreadPool(2); // Either use a thread pool, or create a separate thread each time and call Thread.start()
        //final Thread tRBS = new Thread(rbs);

        StartStopButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
          //      Thread tRBS = new Thread(rbs);
                if (isChecked) {
                    registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
                    wifi.startScan();

                    Thread tRBS = new Thread(rbs);
                    tRBS.start();

                    Thread taT = new Thread (aT);
                    taT.start();

          //          pool.execute(rbs);
                    Log.v("POOL: ", "Runnable executed.");
                } else {
                    unregisterReceiver(wifiReceiver);

                    rbs.terminate();
                    aT.terminate();
                    Log.v("POOL: ", "Runnable stopped.");
                }
                v.vibrate(500);
            }

        });

        IntentFilter filter = new IntentFilter(Intent.ACTION_MEDIA_BUTTON);
        filter.setPriority(10000);
        registerReceiver(new MediaButtonIntentReceiver(), filter);
    }

    public class MediaButtonIntentReceiver extends BroadcastReceiver {

        public MediaButtonIntentReceiver() {
            super();
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.v("MEDIA BUTTON", "Button pressed1!...");
            String intentAction = intent.getAction();
            if (!Intent.ACTION_MEDIA_BUTTON.equals(intentAction)) {
                return;
            }
            KeyEvent event = (KeyEvent)intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            if (event == null) {
                return;
            }
            int action = event.getAction();
            if (action == KeyEvent.ACTION_DOWN) {
                // do something
                Log.v("MEDIA BUTTON", "Button pressed2!...");
                //StartStopButton.toggle();
                dotCounter++;
                tv_dotcounter.setText(dotCounter +"");
            }
            abortBroadcast();
        }
    }

    private boolean createOutputFiles(){
        try {
            WiFi_BOS = new BufferedOutputStream(new FileOutputStream(WiFi_output_file));
            Sensor_BOS = new BufferedOutputStream(new FileOutputStream(Sensors_output_file));
            Dots_BOS = new BufferedOutputStream(new FileOutputStream(Dots_output_file));
        } catch (IOException e) {
            Log.e("BOS_File", e.toString());
        }
        return true;
    }

    private void prepareListData() {
        listDataHeader = new ArrayList<String>();
        listDataChild = new HashMap<String, List<String>>();

        // Adding headers:
        listDataHeader.add(numberOfAPs + " Access Points, delay: " + WiFiScanDuration + "ms");
        listDataHeader.add(numberOfCellTowers + " Cell Towers");
        listDataHeader.add(numberOfSensors + " Sensors");

        // Adding child data
        //WiFiList = new ArrayList<String>();
        //CellList = new ArrayList<String>();
        //SensorList_text = new ArrayList<String>();

        listDataChild.put(listDataHeader.get(0), WiFiList);
        listDataChild.put(listDataHeader.get(1), CellList);
        listDataChild.put(listDataHeader.get(2), SensorList_text);

    }

    private void prepareLTEprotobuf() {

    }

    private class WifiScanReceiver extends BroadcastReceiver {
        public void onReceive(Context c, Intent intent) {
            /* Time frequency */
            time_prev = time_current;
            time_current = System.currentTimeMillis();
            time_diff = (time_current - time_prev);
            //WiFiScanTime.setText("\u0394".toUpperCase() + "t(ms):" + time_diff);

            /* Scan WiFi and display */
            List<ScanResult> wifiScanList = wifi.getScanResults();
            wifis = new String[wifiScanList.size()*2];

            for (int i = 0; i < wifiScanList.size(); i++) {
                //wifis[i] = ((wifiScanList.get(i)).SSID) + " " + ((wifiScanList.get(i)).BSSID) + " " + ((wifiScanList.get(i)).toString());
                wifis[i*2] = ((wifiScanList.get(i)).toString());

                // Test Protocol Buffers
                /*WiFiRSSIMeasurement.Builder wifi_info = WiFiRSSIMeasurement.newBuilder();
                wifi_info.setAp(WiFiRSSIMeasurement.AccessPoint.newBuilder()
                                .setBssi(wifiScanList.get(i).BSSID)
                                .setRssi(wifiScanList.get(i).level)
                );
                wifi_info.setMeasurementDevice(android.os.Build.MODEL);
                wifi_info.setTimestamp(time_current);
                wifi_info.setDotId(dot_current);
                // wifi_info.build().writeTo();
                //wifi_info.build().writeDelimitedTo(null); //WiFiRSSIMeasurement.parseDelimitedFrom(null);
                try {
                    wifi_info.build().writeDelimitedTo(WiFi_BOS);
                } catch(IOException e){
                    Log.v("WiFi_BOS write", e.toString());
                }
                wifis[i*2 + 1] = wifi_info.toString();*/
            }
            wifi.startScan();

            numAPs.setText("# APs: " + wifiScanList.size());

            WiFiList = Arrays.asList(wifis);
            numberOfAPs = wifiScanList.size();
            WiFiScanDuration = (int) time_diff;

            // Get CellInfo and display
            boolean cellularSensingOn = true;
            if (cellularSensingOn) {

                List<CellInfo> cInfoList = tm.getAllCellInfo();

                String[] AllCellStrings = new String[cInfoList.size()];
                Integer CellDBM = new Integer(-999);
                Integer CellASU = new Integer(-999);
                Integer CellLEVEL = new Integer(-999);
                String CellString = "not_yet_assigned";

                for (final CellInfo info : cInfoList) {
                    if (info instanceof CellInfoGsm) {
                        final CellSignalStrengthGsm gsm = ((CellInfoGsm) info).getCellSignalStrength();
                        // do what you need
                        CellString = "GSM: " + gsm.toString() + "\n" + ((CellInfoGsm) info).getCellIdentity();
                        //iRSS = gsm * 2 - 113;
                        CellDBM = gsm.getDbm();
                    } else if (info instanceof CellInfoCdma) {
                        final CellSignalStrengthCdma cdma = ((CellInfoCdma) info).getCellSignalStrength();
                        // do what you need
                        CellString = "CDMA: " + cdma.toString() + "\n" + ((CellInfoCdma) info).getCellIdentity();
                        //iRSS = cdma * 2 - 113;
                        CellDBM = cdma.getDbm();
                    } else if (info instanceof CellInfoLte) {
                        final CellSignalStrengthLte lte = ((CellInfoLte) info).getCellSignalStrength();
                        // do what you need
                        CellString = "LTE: " + lte.toString() + "\n" + ((CellInfoLte) info).getCellIdentity().toString();
                        //iRSS = lte * 2 - 113;
                        CellDBM = lte.getDbm();
                        CellASU = lte.getAsuLevel();
                        CellLEVEL = lte.getLevel();
                    } else if (info instanceof CellInfoWcdma) {
                        final CellSignalStrengthWcdma wcdma = ((CellInfoWcdma) info).getCellSignalStrength();
                        // do what you need
                        CellString = "WCDMA: " + wcdma.toString() + "\n" + ((CellInfoWcdma) info).getCellIdentity().toString();
                        //iRSS = wcdma * 2 - 113;
                        CellDBM = wcdma.getDbm();
                    } else {
                        CellString = "Unknown type of cell signal!";
                    }
                    AllCellStrings[cInfoList.indexOf(info)] = CellString + "\n DBM=" + CellDBM +
                                                                            "\n ASU=" + CellASU +
                                                                            "\n LEVEL=" + CellLEVEL ;
                }
                CellList = Arrays.asList(AllCellStrings);
                numberOfCellTowers = cInfoList.size();
            } else CellList = new ArrayList<String>();

            prepareListData();

            boolean WiFiGroupExpanded = expListView.isGroupExpanded(0);
            boolean CellGroupExpanded = expListView.isGroupExpanded(1);
            boolean SensorGroupExpanded = expListView.isGroupExpanded(2);

            //int mListPosition = expListView.getVerticalScrollbarPosition();

            expListAdapter = new ExpandableListViewAdapter(getApplicationContext(), listDataHeader, listDataChild);
            expListView.setAdapter(expListAdapter);

            if (WiFiGroupExpanded) expListView.expandGroup(0);
            if (CellGroupExpanded) expListView.expandGroup(1);
            if (SensorGroupExpanded) expListView.expandGroup(2);

            //expListView.setVerticalScrollbarPosition(mListPosition);
        }
    }
    /* For ignoring Orientation change! */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
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

        //noinspection SimplifiableIfStatement
        /*if (id == R.id.action_settings) {
            return true;
        }*/

        switch(item.getItemId()) {
            case R.id.action_settings:
                return true;
            case R.id.flush_files:
                try {
                    WiFi_BOS.flush();
                    Sensor_BOS.flush();
                    Dots_BOS.flush();
                } catch (IOException e) {
                    Log.v("Flush BOS:",e.toString());
                }
                //sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(outFile)));
                File wifiFile = new File(WiFi_output_file);
                File sensorFile = new File(Sensors_output_file);
                File dotFile = new File(Dots_output_file);
                //if (wifiFile.exists())
                //  getContentResolver().delete(Uri.fromFile(new File(WiFi_output_file)), null, null);
                //sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(wifiFile)));
                //sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(sensorFile)));
                //sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(dotFile)));
                //MediaStore.Files();
                MediaScannerConnection.scanFile(
                        getApplicationContext(),
                        new String[]{wifiFile.getAbsolutePath(), sensorFile.getAbsolutePath(), dotFile.getAbsolutePath()},
                        null,
                        new MediaScannerConnection.OnScanCompletedListener() {
                            @Override
                            public void onScanCompleted(String path, Uri uri) {
                                Log.v("Media Scanner", "file " + path + " was scanned successfully: " + uri);
                            }
                        }
                );
                return true;
            case R.id.renew_files:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage("Are you sure?").setPositiveButton("Yes", dialogClickListener)
                        .setNegativeButton("No", dialogClickListener).show();

                return true;
            case R.id.set_sensor_freq:
                AlertDialog.Builder freq_dialog_builder = new AlertDialog.Builder(this);
                final EditText input = new EditText(this);
                input.setInputType(InputType.TYPE_NUMBER_VARIATION_NORMAL | InputType.TYPE_CLASS_NUMBER);
                freq_dialog_builder.setView(input);

                freq_dialog_builder.setMessage("Change Sensor sampling frequency (microseconds)\\ Current:" + sensor_sampling_frequency).setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        sensor_sampling_frequency = Integer.parseInt(input.getText().toString());
                        Toast.makeText(getApplicationContext(), "Set to: " + sensor_sampling_frequency,
                                        Toast.LENGTH_SHORT).show();
                    }
                });
                freq_dialog_builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        Toast.makeText(getApplicationContext(), "not changed.", Toast.LENGTH_SHORT).show();
                    }
                });

                freq_dialog_builder.show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which){
                case DialogInterface.BUTTON_POSITIVE:
                    //Yes button clicked
                    createOutputFiles();
                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    //No button clicked
                    // do nothing
                    //Toast toast = Toast.makeText(getApplicationContext(), "cancelled", Toast.LENGTH_SHORT);
                    //toast.show();
                    Toast.makeText(getApplicationContext(), "cancelled", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };


    private class readBuiltinSensors implements Runnable {

        public Activity activity;

        private volatile boolean running;

        private volatile int measuringFrequency;

        // User motion bools
        private volatile boolean userRunning = false;
        private volatile boolean userTurning = false;


        SensorManager mSensorManager;

        List<Sensor> sensorList;

        volatile int flag = 0;

        float s_accelerometer[], s_gravity[], s_gyroscope[], s_proximity[], s_light[],
              s_magneticfield[], s_magneticfielduncalibrated[], s_geomagneticrotationvector[], s_rotationvector[], s_linearacceleration[];


        private SensorEventListener mSensorListener = new SensorEventListener() {

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }

            @Override
            public void onSensorChanged(SensorEvent event) {
                senor_event_counter++;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        tv_sensor_event_counter.setText(senor_event_counter+"");
                    }
                });

                Sensor cSensor = event.sensor;
                //noAPs_tv.setText("" + cSensor.getType());
                String sensorHashKey = event.sensor.getType() + ": " +  event.sensor.getStringType(); // wakeup + non-wakeup sensors together
                //String sensorHashKey = event.sensor.getType() + ": " +  event.sensor.getName(); // wakeup + non-wakeup sensors separate
                //String sensorHashKey = event.sensor.getType() + "" ;
                sensorHashMap.put(sensorHashKey, event.values);
                sensorLastTimestampHashMap.put(sensorHashKey,sensorCurrentTimestampHashMap.get(sensorHashKey));
                sensorCurrentTimestampHashMap.put(sensorHashKey, new Long(event.timestamp));
                if(event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
                    float[] time_x = {(float) Calendar.getInstance().get(Calendar.SECOND)};
                    sensorHashMap.put(sensorHashKey, time_x);
                }
                switch (cSensor.getType()){
                    case Sensor.TYPE_ACCELEROMETER:
                        s_accelerometer = event.values; // 3 values
                        break;

                    case Sensor.TYPE_PROXIMITY:
                        s_proximity = event.values; // 1 value
                        break;

                    case Sensor.TYPE_LIGHT:
                        s_light = event.values; // 1 value
                        break;

                    case Sensor.TYPE_MAGNETIC_FIELD:
                        s_magneticfield = event.values; // ?? values
                        break;

                    case Sensor.TYPE_GYROSCOPE:
                        s_gyroscope = event.values; // 3 values
                        break;

                    case Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED:
                        s_magneticfielduncalibrated = event.values; // 6 values
                        break;

                    case Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR:
                        s_geomagneticrotationvector = event.values; // ?? values
                        break;

                    case Sensor.TYPE_ROTATION_VECTOR:
                        s_rotationvector = event.values; // 4 values
                        break;

                    case Sensor.TYPE_GRAVITY:
                        s_gravity = event.values; // 3 values
                        break;

                    case Sensor.TYPE_LINEAR_ACCELERATION:
                        s_linearacceleration = event.values; // 3 values
                        break;

                    case Sensor.TYPE_ORIENTATION: // Deprecated.
                        //TYPE_ORIENTATION is deprecated.  use SensorManager.getOrientation() instead.
                        break;

                    default:
                        //noAPs_tv.setText("Sensor error:" + cSensor.getName());
                        //DisplayRotation is unknown to me //Onur
                        break;

                }
            }
        };

        public void terminate() {
            unRegisterSensors(mSensorManager);
            running = false;
        }

        public void incrementFlag() {
            flag = flag +1;
        }

        public void setUserRunning (boolean action) {
            userRunning = action;
        }

        public void setUserTurning (boolean action) {
            userTurning = action;
        }

        public readBuiltinSensors(Activity _activity, int _measuringFrequency) {
            //this.activity = _activity;
            measuringFrequency = _measuringFrequency;

            //_slv = (ListView)findViewById(R.id.sensor_listView);

            mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            sensorList = mSensorManager.getSensorList(Sensor.TYPE_ALL);

            /* Below code is to remove wake-up sensors from sensorList
                List<Sensor> sensorsToRegister = new ArrayList<Sensor>();
                for (Sensor s : sensorList) {
                  if (!s.isWakeUpSensor())
                      sensorsToRegister.add(s);
                }
                sensorList = sensorsToRegister;
            */

            //registerSensors(mSensorManager, sensorList);
            //numberOfSensors = sensorList.size();

            SensorList_text = new ArrayList<String>();
            sensorHashMap = new HashMap<String, float[]>();
            sensorLastTimestampHashMap = new  HashMap<String, Long>();
            sensorCurrentTimestampHashMap = new  HashMap<String, Long>();

            for (int i = 0; i < sensorList.size(); i++) {
                String sensorHashKey = sensorList.get(i).getType() + ": " + sensorList.get(i).getStringType(); // wakeup + non-wakeup sensors together
                //String sensorHashKey = sensorList.get(i).getType() + ": " + sensorList.get(i).getName(); // wakeup + non-wakeup sensors separate
                //String sensorHashKey = sensorList.get(i).getType() + "";
                sensorHashMap.put(sensorHashKey, null);
                //SensorList_text.add("" + sensorList.get(i).getName());
            }

            List<String> keyList = new ArrayList<String>(sensorHashMap.keySet());
            SensorList_text = new ArrayList<String>();
            for (int i = 0; i < keyList.size();i++){
                SensorList_text.add(keyList.get(i) + ": " + Arrays.toString(sensorHashMap.get(keyList.get(i))));
            }
            numberOfSensors = sensorHashMap.size();
            handler.post(new Runnable() {
                @Override
                public void run() {
                    //slv.setAdapter(new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_list_item_1, sensors));
                    WiFiScanTime.setText("Ready! " + Arrays.toString(s_light));

                    prepareListData();
                    expListAdapter = new ExpandableListViewAdapter(getApplicationContext(), listDataHeader, listDataChild);
                    expListView.setAdapter(expListAdapter);


                }
            });
        }

        @Override
        public void run() {
            //scanSwitch.toggle();

            running = true;

            registerSensors(mSensorManager, sensorList);

            while (running) {
                final String sensors[] = new String[5];
                sensors[0] = "Acceleration: " + Arrays.toString(s_accelerometer);
                sensors[1] = "Gyroscope: " + Arrays.toString(s_gyroscope);
                sensors[2] = "Magnetic Field: " + Arrays.toString(s_magneticfield);
                sensors[3] = "Light: " + Arrays.toString(s_light);
                sensors[4] = "Linear Acc.: " + Arrays.toString(s_linearacceleration);

                //SensorList_text = Arrays.asList(sensors);

                List<String> keyList = new ArrayList<String>(sensorHashMap.keySet());
                SensorList_text = new ArrayList<String>();
                for (int i = 0; i < keyList.size();i++){
                    if (sensorLastTimestampHashMap.get(keyList.get(i)) != null )
                    SensorList_text.add(keyList.get(i) + ": " + Arrays.toString(sensorHashMap.get(keyList.get(i))) +
                                         " in " + (sensorCurrentTimestampHashMap.get(keyList.get(i)) - sensorLastTimestampHashMap.get(keyList.get(i)))/1000000 + "msec" );
                }

                Collections.sort(SensorList_text, String.CASE_INSENSITIVE_ORDER);

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        //slv.setAdapter(new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_list_item_1, sensors));
                        if (s_light != null)
                            WiFiScanTime.setText(" Light: " + s_light[0]);

                        prepareListData();

                        /*boolean WiFiGroupExpanded = expListView.isGroupExpanded(0);
                        boolean CellGroupExpanded = expListView.isGroupExpanded(1);
                        boolean SensorGroupExpanded = expListView.isGroupExpanded(2);

                        expListAdapter = new ExpandableListViewAdapter(getApplicationContext(), listDataHeader, listDataChild);
                        expListView.setAdapter(expListAdapter);

                        if (WiFiGroupExpanded) expListView.expandGroup(0);
                        if (CellGroupExpanded) expListView.expandGroup(1);
                        if (SensorGroupExpanded) expListView.expandGroup(2);*/

                    }
                });

                try {
                    Thread.sleep(measuringFrequency);
                } catch (InterruptedException e) {
                    Log.e("THREAD_SLEEP", e.toString());
                }

                try {
                    String line = Math.ceil(System.currentTimeMillis() / 1) + " " + s_accelerometer[0] + " " + s_accelerometer[1] + " " + s_accelerometer[2] +
                            " " + s_gyroscope[0] + " " + s_gyroscope[1] + " " + s_gyroscope[2] + " " +
                            " " + userRunning + " " + userTurning + " " + flag + "\n";
                    //testFileWriter = new FileWriter(testFile);
                    //testFileWriter.append(line);
                    //testFileWriter.flush();
                    //fos.write(measurements.getBytes());
                    //Log.v("FILE_WRITE", line + " is written to " + testFile.getAbsolutePath());
                    //sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(outFile)));
                } catch (Exception e) {
                    Log.e("FILE", e.toString());
                }
            } // while running
            if (!running) {
                try {
                    //testFileWriter.flush();
                    //testFileWriter.close();
                    //Log.v("FILE_WRITE", "File writer closed: " + testFile.getAbsolutePath() + "\n");
                } catch (Exception e) {
                    Log.e("FILE", e.toString());
                }
            }
        }

        private void registerSensors (SensorManager tSensorManager, List<Sensor> listOfSensors){
            //SENSOR_DELAY_GAME = 20.000 microsecond
            //SENSOR_DELAY_NORMAL = 180.000 microsecond
            //SENSOR_DELAY_UI = 60.000 microsecond mSensorManager.SENSOR_DELAY_UI
            //SENSOR_DELAY_FASTEST = As fast as possible

            for (int i = 0; i < listOfSensors.size(); i++) {
                mSensorManager.registerListener(mSensorListener, listOfSensors.get(i), sensor_sampling_frequency);
            }
        }

        private void unRegisterSensors (SensorManager tSensorManager){
                mSensorManager.unregisterListener(mSensorListener);
        }

        private class  SensorReading {
            public String sensorName;
            public int[] sensorValues;
        }


    }

    private class applicationTimer implements Runnable {

        int timer_sec = 0;
        boolean running;

        public applicationTimer() {

        }

        public void terminate(){
            running = false;
        }

        @Override
        public void run() {
            running = true;

            while (running) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Log.e("THREAD_SLEEP", e.toString());
                }
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        //slv.setAdapter(new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_list_item_1, sensors));
                        timer_sec++;
                        tv_timer_sec.setText( (int) Math.floor(timer_sec / 60) + " min : " +
                                              (timer_sec%60) + " sec");
                    }
                });
            }
        }

    }

    @Override
    public void onPause() {
        super.onPause();  // Always call the superclass method first

        //StartStopButton.setChecked(false);
    }

    @Override
    public void onStop() {
        super.onStop();  // Always call the superclass method first

        //StartStopButton.setChecked(false);
    }

}

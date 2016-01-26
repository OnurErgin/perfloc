package gov.nist.perfloc;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.MediaScannerConnection;
import android.media.ToneGenerator;
import android.media.session.MediaSession;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.os.Vibrator;
import android.telephony.CellIdentityCdma;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityWcdma;
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
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.text.InputType;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends Activity {

    String TITLE = "Perfloc";
    String GPS_STATUS = "GPS Off";

    public enum State {
        PAUSED,     // Just in case..
        RUNNING,    // Everything is being recorded
        WAITING,    // Waiting for the first button press to start measurements.
        STOPPED     // Initial or Final state
    }

    volatile State state = State.STOPPED;

    Vibrator v;
    private final Handler main_handler = new Handler();
    ToneGenerator toneG;
    static int VOLUME = 100;

    // View related definitions
    ExpandableListView expListView;
    ToggleButton StartStopButton;
    Switch gpsSwitch, sensorSwitch;
    Button pushButton;

    static TextView numAPs,
            WiFiScanTime,
            tv_dotcounter,
            tv_sensor_event_counter,
            tv_timer_sec,
            tv_bottomView,
            tv_metadata;
    static CheckBox geomagnetic_checkbox;

    // Expandable List Adapter related definitions
    ExpandableListAdapter expListAdapter;
    List<String> listDataHeader;
    HashMap<String, List<String>> listDataChild;
    List<String> WiFiList_text,
                CellList_text,
                SensorList_text,
                DotList_text;


    // WiFi Scanning related definitions
    WifiManager wifi;
    WifiScanReceiver wifiReceiver;
    long time_prev, time_current;
    long last_button_press_time, current_button_press_time;

    // Cell Tower and Telephony related definitions
    TelephonyManager telephony_manager;
    HashMap<String,String> phoneStateHashMap;
    SignalStrength signalStrength;
    int lteDbm = Integer.MAX_VALUE, lteAsu = Integer.MAX_VALUE, lteLevel = Integer.MAX_VALUE;
    int cdmaAsu = Integer.MAX_VALUE, cdmaDbm = Integer.MAX_VALUE, cdmaEcio = Integer.MAX_VALUE, cdmaLevel = Integer.MAX_VALUE,
        evdoAsu = Integer.MAX_VALUE, evdoDbm = Integer.MAX_VALUE, evdoEcio = Integer.MAX_VALUE, evdoLevel = Integer.MAX_VALUE, evdoSnr = Integer.MAX_VALUE,
        s_Level = Integer.MAX_VALUE, s_Dbm = Integer.MAX_VALUE; // for samsung

    long prevLte_time = 0, currentLte_time=0;

    // Location Services: GPS
    LocationManager locationManager;
    LocationListener locationListener;
    boolean gotGpsFix;
    long last_gps_fix_time;

    // Sensor related definitions
    SensorHandler rbs;

    //HashMap<Sensor, float[]> sensorHashMap; // 21Ocak
    HashMap<Sensor, float[]> sensorHashMap;
    HashMap<Sensor, Long> sensorLastTimestampHashMap;
    HashMap<Sensor, Long> sensorCurrentTimestampHashMap;

    // Verbose info
    HashMap<String, String> onScreenVerbose;

    private int SENSOR_DELAY = 10000;//SensorManager.SENSOR_DELAY_FASTEST; // 10000 microseconds = 100 Hz
                                        // SENSOR_DELAY_FASTEST (instead of 100Hz) causes too many data to be produced

    volatile int dotCounter, gpsFixCounter;

    volatile int numberOfAPs=0,
                WiFiScanDuration=0,
                numberOfCellTowers=0,
                numberOfSensors=0;

    String  WiFi_output_file,//     = "_WiFi",
            Sensors_output_file,//  = "_Sensors",
            Dots_output_file,//     = "_Dots",
            Cellular_output_file,// = "_Cellular",
            Metadata_output_file,// = "_Metadata";
            Gps_output_file;

    String device_identifier, protocol_buffer_file_extension, current_file_prefix = "1";
    //String TITLE = "(" + current_file_prefix + ")";
    File dirname;

    List<Uri> indexed_URIs;

    BufferedOutputStream WiFi_BOS, Sensor_BOS, Dots_BOS, Cellular_BOS, Metadata_BOS, Gps_BOS;


    // Protocol Buffer ArrayLists
    List<DotData.DotReading> list_dot_readings;
    List<WifiData.WiFiReading> list_wifi_readings;
    List<CellularData.CellularReading> list_cellular_readings;
    List<SensorData.SensorReading> list_sensor_readings;

    int seq_nr_dot, seq_nr_wifi, seq_nr_cellular, seq_nr_gpsfix;
    long seq_nr_sensorevent;

    MeasurementTimer aT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Hide Navigation buttons
        hideSystemUI();
        UiChangeListener();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        v = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
        toneG = new ToneGenerator(AudioManager.STREAM_ALARM, VOLUME);

        setWakeLock(true);

        resetCounters();
        initializeLists();
        //setGpsListener(true);

        askNewFilePrefix();
        createOutputFiles(current_file_prefix);

        // Find Views
        StartStopButton = (ToggleButton) findViewById(R.id.toggleButton);
        gpsSwitch       = (Switch) findViewById(R.id.gps_switch);
        numAPs          = (TextView) findViewById(R.id.numAPs);
        WiFiScanTime    = (TextView) findViewById(R.id.WiFiScanTime);
        tv_dotcounter   = (TextView) findViewById(R.id.dotCounter);
        tv_timer_sec    = (TextView) findViewById(R.id.timer_sec);
        tv_bottomView   = (TextView) findViewById(R.id.bottomView); tv_bottomView.setMovementMethod(new ScrollingMovementMethod());
        tv_sensor_event_counter = (TextView) findViewById(R.id.sensor_event_counter);
        tv_metadata     = (TextView) findViewById(R.id.tv_metadata);
        expListView     = (ExpandableListView) findViewById(R.id.expandableListView);
        geomagnetic_checkbox   = (CheckBox) findViewById(R.id.geomagnetic_checkbox);
         sensorSwitch    = (Switch) findViewById(R.id.sensor_switch);
        pushButton      = (Button) findViewById(R.id.push_button);

        // Start WiFi Service
        wifi=(WifiManager)getSystemService(Context.WIFI_SERVICE);
        wifiReceiver = new WifiScanReceiver();

        // Get Telephony service
        telephony_manager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        listenPhoneState(telephony_manager);

        // Get Location Manager
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);


        //final MeasurementTimer aT = new MeasurementTimer(main_handler); // 21ocak

        StartStopButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    //state = State.WAITING; //21Ocak
                    //registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)); //21Ocak

                    /* 21Ocak
                    wifi.startScan();
                    time_current = System.currentTimeMillis();


                    Thread taT = new Thread(aT);
                    taT.start();
                    */

                    //aT = new MeasurementTimer(main_handler); // 21Ocak

                    // Stopped => Waiting
                    if (state == State.STOPPED) { // 21Ocak
                        changeState(State.WAITING);
                        saveMetadata();
                    }
                    else
                    if (state == State.PAUSED) // Dummy state
                        changeState(State.RUNNING);

                    Log.v("StartStopButton", "State = " + state.name());

                } else {

                    //state = State.PAUSED;

                    if (state == State.RUNNING) {
                        //goStoppedFromRunning();
                        changeState(State.STOPPED);
                    }
                    else
                    if (state == State.WAITING)
                        changeState(State.STOPPED);

                    /* 21Ocak
                    unregisterReceiver(wifiReceiver);

                    aT.terminate();
                    flushFiles();
                    sensorSwitch.setChecked(false);
                    //gpsSwitch.setChecked(false);
                    */
                    Log.v("StartStopButton", "Runnable stopped.");
                }
                v.vibrate(500);
            }

        });

        rbs = new SensorHandler(main_handler, 500);
        sensorSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isON) {
                if (isON) {
                    StartStopButton.setEnabled(true);

                    Thread tRBS = new Thread(rbs);
                    tRBS.start();
                }
                else {
                    rbs.terminate();

                    StartStopButton.setChecked(false);
                    StartStopButton.setEnabled(false);
                }
            }
        });

        gpsSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                boolean success  = setGpsListener(checked);

                if (!success) {
                    // Either checked or unchecked, set the button unchecked if the location services are Off
                    compoundButton.setChecked(false);
                }

                if (checked && success) {
                    // setTitle(TITLE + "    GPS searching...");
                    GPS_STATUS = "GPS Searching!";
                }
                else {
                    GPS_STATUS = "GPS off";
                }
                makeTitle();
            }
        });

        /* Prevent other activities, such as MusicPlaybackService, from catching MEDIA_BUTTON events */
        if ("lge".equalsIgnoreCase(Build.BRAND)||true) {
            MediaSession mediaSession = new MediaSession(this, "Perfloc MediaSession TAG");
                mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS);
                //mediaSession.setCallback(this);

                Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
                //Intent mediaButtonIntent = new Intent(getApplicationContext(), MediaButtonIntentReceiver.class);
                PendingIntent pIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, mediaButtonIntent, 0);
                mediaSession.setMediaButtonReceiver(pIntent);
                mediaSession.setActive(true);
        }


     /*   IntentFilter filter = new IntentFilter(Intent.ACTION_MEDIA_BUTTON);
        filter.setPriority(10000);
        registerReceiver(new MediaButtonIntentReceiver(), filter);
*/

/*
        IntentFilter filter1 = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        filter1.setPriority(Integer.MAX_VALUE);
        registerReceiver(new HeadsetIntentReceiver(), filter1);
*/

        // Initial view of the Expandable List View
        updateExpandableListView();
        makeTitle();

        pushButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                onKeyDown(KeyEvent.KEYCODE_HEADSETHOOK, new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_HEADSETHOOK));
            }
        });


    } // onCreate()

    private void makeTitle () {
        setTitle(current_file_prefix + "\t| " + GPS_STATUS + "\t| " + state.name());
    }
    /*public class HeadsetIntentReceiver extends BroadcastReceiver {

        public HeadsetIntentReceiver() {
            super();
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i("HEADSET", "Plugged!! or unplugged");
            String TAG = "Inent: ";

            if (intent.getAction().equals(Intent.ACTION_HEADSET_PLUG)) {
                int state = intent.getIntExtra("state", -1);
                switch (state) {
                    case 0:
                        Log.d(TAG, "Headset is unplugged");
                        break;
                    case 1:
                        Log.d(TAG, "Headset is plugged");
                        break;
                    default:
                        Log.d(TAG, "I have no idea what the headset state is");
                }
            }
        }
    }*/

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_HEADSETHOOK) {

            current_button_press_time = System.currentTimeMillis();
            if (current_button_press_time - last_button_press_time < 500) { // No presses faster than 0.5 sec
                last_button_press_time = current_button_press_time;
                return false;
            }

            last_button_press_time = current_button_press_time;

            Log.v("__MEDIA_BUTTON__", "DOWN Pressed.");

            dotCounter++;
            tv_dotcounter.setText(dotCounter + "");

            markDot();

            // This is safe (seq_nr_dot is 0) because there is no Running -> Waiting
            if (state == State.WAITING)
                changeState(State.RUNNING);

            //ToneGenerator toneG = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
            //toneG.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200);
            if (seq_nr_dot % 2 == 0)
                toneG.startTone(ToneGenerator.TONE_PROP_BEEP, 200);
            else
                toneG.startTone(ToneGenerator.TONE_CDMA_ONE_MIN_BEEP, 100);
            //toneG.startTone(ToneGenerator.TONE_PROP_BEEP2, 200);

        }
        return true;
    }

    private void setWakeLock( boolean _set ) {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyWakelockTag");
        if (_set) {
            wakeLock.acquire();
            Log.v("WakeLock (isHeld?): ", wakeLock.isHeld() + ".");
        }
        else
            wakeLock.release();
    }

    private void resetCounters () {
        seq_nr_dot = 0;
        seq_nr_wifi = 0;
        seq_nr_cellular = 0;
        seq_nr_sensorevent = 0;
        seq_nr_gpsfix = 0;

        numberOfAPs = 0;
        WiFiScanDuration = 0;
        numberOfCellTowers = 0;
        numberOfSensors = 0;

        dotCounter = 0;
        gpsFixCounter = 0;
        gotGpsFix = false;

        last_button_press_time = System.currentTimeMillis() - 5000;
        current_button_press_time = System.currentTimeMillis();
    }

    private void initializeLists () {
        list_cellular_readings = new ArrayList<>();
        list_dot_readings = new ArrayList<>();
        list_wifi_readings = new ArrayList<>();
        list_sensor_readings = new ArrayList<>();

        onScreenVerbose = new HashMap<>();

        WiFiList_text = new ArrayList<>();
        DotList_text = new ArrayList<>();

        indexed_URIs = new ArrayList<>();
    }

    private void retrieveStrings() {
        WiFi_output_file     = getString(R.string.wifi_file_name);     // "_WiFi",
        Sensors_output_file  = getString(R.string.sensors_file_name);   //"_Sensors",
        Dots_output_file     = getString(R.string.dots_file_name);      //"_Dots",
        Cellular_output_file = getString(R.string.cellular_file_name);  //"_Cellular",
        Metadata_output_file = getString(R.string.metadata_file_name);  //"_Metadata";
        Gps_output_file      = getString(R.string.gps_file_name);

        device_identifier = Build.MANUFACTURER + "_" + Build.MODEL + "_" + Build.BRAND;

        //dirname = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

        /*
         * DIRECTORY_PICTURES is chosen because the LG phone is programmable in Camera(PTP) mode only
         * Otherwise one has to inconveniently switch between PTP and Media Sync (MTP) modes
         * each time the produced files need to be downloaded before or after an app re-install
         */
        dirname = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        protocol_buffer_file_extension = getString(R.string.file_extension);

    }

    private boolean createOutputFiles(String prefix){

        retrieveStrings();

        WiFi_output_file     = dirname + "/" + prefix + "_" + device_identifier + WiFi_output_file      + "." + protocol_buffer_file_extension;
        Sensors_output_file  = dirname + "/" + prefix + "_" + device_identifier + Sensors_output_file   + "." + protocol_buffer_file_extension;
        Dots_output_file     = dirname + "/" + prefix + "_" + device_identifier + Dots_output_file      + "." + protocol_buffer_file_extension;
        Cellular_output_file = dirname + "/" + prefix + "_" + device_identifier + Cellular_output_file  + "." + protocol_buffer_file_extension;
        Metadata_output_file = dirname + "/" + prefix + "_" + device_identifier + Metadata_output_file  + "." + protocol_buffer_file_extension;
        Gps_output_file      = dirname + "/" + prefix + "_" + device_identifier + Gps_output_file       + "." + protocol_buffer_file_extension;

        WiFi_output_file     = WiFi_output_file.replaceAll("\\s+", "");
        Sensors_output_file  = Sensors_output_file.replaceAll("\\s+", "");
        Dots_output_file     = Dots_output_file.replaceAll("\\s+", "");
        Cellular_output_file = Cellular_output_file.replaceAll("\\s+", "");
        Metadata_output_file = Metadata_output_file.replaceAll("\\s+", "");
        Gps_output_file      = Gps_output_file.replaceAll("\\s+", "");

        try {
            WiFi_BOS     = new BufferedOutputStream(new FileOutputStream(WiFi_output_file));
            Sensor_BOS   = new BufferedOutputStream(new FileOutputStream(Sensors_output_file));
            Dots_BOS     = new BufferedOutputStream(new FileOutputStream(Dots_output_file));
            Cellular_BOS = new BufferedOutputStream(new FileOutputStream(Cellular_output_file));
            Metadata_BOS = new BufferedOutputStream(new FileOutputStream(Metadata_output_file));
            Gps_BOS      = new BufferedOutputStream(new FileOutputStream(Gps_output_file));
        } catch (IOException e) {
            Log.e("BOS_File", e.toString());
        }

        return true;
    }

    private boolean setGpsListener (boolean _set) {

        // Check if GPS enabled:


        if ( ! locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            //show toast
            AlertDialog.Builder gps_warning_dialog = new AlertDialog.Builder(this);

            gps_warning_dialog.setTitle("Location is not Enabled!");
            gps_warning_dialog.setMessage("Swipe down and enable \"location services\"").setPositiveButton("OK", null);
                    //.setCancelable(false)
            gps_warning_dialog.show();
            return false;
        }

        if (_set) {
            //locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
            locationListener = getLocationListener();
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        }
        else {
            locationManager.removeUpdates(locationListener);
            showGpsFound(false);
        }
        return true;
    }

    public void markDot() {

        //list_dot_readings.add(prepareDotProtobuf(++seq_nr_dot));

        if (state != State.STOPPED) { // RUNNING or WAITING

            if (state == State.RUNNING) {
                seq_nr_dot = seq_nr_dot + 1;
            }

            DotData.DotReading dot_reading = prepareDotProtobuf(seq_nr_dot);

            try {
                dot_reading.writeDelimitedTo(Dots_BOS);
            } catch (IOException e) {
                Log.e("Dots_BOS exception:", e.toString());
            }

            DotList_text.add(dot_reading.toString());

            onScreenVerbose.put("seq_nr_dot", Integer.toString(dot_reading.getDotNr()));

        }

    }

    public class MediaButtonIntentReceiver extends BroadcastReceiver {

        public MediaButtonIntentReceiver() {
            super();
            Log.v("MediaB.IntentReceiver:", "Intent Receiver for Media Button Registered");
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

                Log.v("MEDIA BUTTON", "Button pressed2!...");
                //StartStopButton.toggle();
                markDot();

                /*
                try {
                    Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                    Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
                    r.play();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                */
            }
        }
    }



    private boolean unIndexFiles () {
        for (Uri uri : indexed_URIs) {
            getContentResolver().delete(uri, null, null);
        }
        indexed_URIs.clear();

        return true;
    }

    private void stopEverything() {

        // Stop the Sensors
        sensorSwitch.setChecked(false);

        // Stop Wi-Fi
        unregisterReceiver(wifiReceiver);

        // Stop Timer (that also contains cellular measurements)
        aT.terminate();

        flushFiles();

        //gpsSwitch.setChecked(false);
    }

    private void changeState (State nextState) {  // 21Ocak
        String _LOG = "State change";
        Log.v(_LOG, state.name() + " => " + nextState.name());

        switch (state) {
            case RUNNING:
                switch (nextState) {
                    case RUNNING:
                        Log.wtf(_LOG, state.name() + " ===>> " + nextState.name() + " is weird!!!");
                        break;
                    case STOPPED:
                        state = State.STOPPED;

                        stopEverything();
                        break;
                    case WAITING:
                        state = State.WAITING;
                        break;
                    default:
                        break;
                }
                break;
            case WAITING:
                switch (nextState) {
                    case RUNNING:
                        wifi.startScan();
                        time_current = System.currentTimeMillis();

                        Thread taT = new Thread(aT);
                        taT.start();

                        state = State.RUNNING;
                        break;
                    case WAITING:
                        Log.wtf(_LOG, state.name() + " ===>> " + nextState.name() + " is weird!!!");
                        break;
                    case PAUSED:
                        break;
                    case STOPPED:
                        state = State.STOPPED;
                        stopEverything();
                        break;
                    default:
                        Log.wtf(_LOG,"Unhandled or Weird state change");
                        break;
                }
                break;
            case STOPPED:
                switch (nextState) {
                    case RUNNING:
                        break;
                    case WAITING:
                        // Register WiFi
                        registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

                        // Prepare timer
                        aT = new MeasurementTimer(main_handler);

                        // Waiting for button press
                        state = State.WAITING;
                        break;
                    case PAUSED:
                        break;
                    case STOPPED:
                        break;
                    default:
                        Log.wtf(_LOG,"Unhandled or Weird state change");
                        break;
                }
                break;
            case PAUSED:
                break;

            default:
                Log.wtf(_LOG,"Unhandled or Weird state change");
                switch (nextState) {
                    case RUNNING:
                        break;
                    case WAITING:
                        break;
                    case PAUSED:
                        break;
                    case STOPPED:
                        break;
                    default:
                        Log.wtf(_LOG,"Unhandled or Weird state change");
                        break;
                }
                break;

        }
        makeTitle();
    }

    private void goRunningFromWaiting () { // 21Ocak
        wifi.startScan();
        time_current = System.currentTimeMillis();

        Thread taT = new Thread(aT);
        taT.start();

        state = State.RUNNING;
    }

    private void goStoppedFromRunning () { // 21Ocak
        state = State.STOPPED;

        unregisterReceiver(wifiReceiver);

        aT.terminate();
        flushFiles();
        sensorSwitch.setChecked(false);
        //gpsSwitch.setChecked(false);
    }

    private void goStoppedFromWaitin () {}

    /**
     *  Not needed anymore
     */
    private void writeProtobufsToFile () {
        try {
            for (DotData.DotReading d : list_dot_readings){
                d.writeDelimitedTo(Dots_BOS);
            }
            /*for (SensorData.SensorReading s : list_sensor_readings) {
                s.writeDelimitedTo(Sensor_BOS);
            }*/
            for (WifiData.WiFiReading w : list_wifi_readings) {
                w.writeDelimitedTo(WiFi_BOS);
            }
            for (CellularData.CellularReading c : list_cellular_readings) {
                c.writeDelimitedTo(Cellular_BOS);
            }
        }
        catch(IOException e) {
            Log.e("WriteProtoBufs2File", e.toString());
        }

    }

    private void updateExpandableListView (){
        boolean WiFiGroupExpanded = false;
        boolean CellGroupExpanded = false;
        boolean SensorGroupExpanded = false;
        boolean DotGroupExpanded = false;

        if (expListAdapter != null) {
            WiFiGroupExpanded = expListView.isGroupExpanded(0);
            CellGroupExpanded = expListView.isGroupExpanded(1);
            SensorGroupExpanded = expListView.isGroupExpanded(2);
            DotGroupExpanded = expListView.isGroupExpanded(3);
            //int mListPosition = expListView.getVerticalScrollbarPosition();
        }

        prepareListData(); // Prepare the String Lists of headers and HashMaps of their children
        expListAdapter = new ExpandableListViewAdapter(getApplicationContext(), listDataHeader, listDataChild);
        expListView.setAdapter(expListAdapter);

        //expListView.setVerticalScrollbarPosition(mListPosition);
        if (WiFiGroupExpanded) expListView.expandGroup(0);
        if (CellGroupExpanded) expListView.expandGroup(1);
        if (SensorGroupExpanded) expListView.expandGroup(2);
        if (DotGroupExpanded) expListView.expandGroup(3);
    }

    private void prepareListData() {
        listDataHeader = new ArrayList<String>();
        listDataChild = new HashMap<String, List<String>>();

        // Adding headers:
        listDataHeader.add(numberOfAPs + " Access Points, delay: " + WiFiScanDuration + "ms");
        listDataHeader.add(numberOfCellTowers + " Cell Towers");
        listDataHeader.add(SensorList_text.size() + "/" + numberOfSensors + " Sensors, " + seq_nr_sensorevent + " written");
        listDataHeader.add("Dot: " + seq_nr_dot);

        listDataChild.put(listDataHeader.get(0), WiFiList_text);
        listDataChild.put(listDataHeader.get(1), CellList_text);
        listDataChild.put(listDataHeader.get(2), SensorList_text);
        listDataChild.put(listDataHeader.get(3), DotList_text);

    }

    private DotData.DotReading prepareDotProtobuf(int dot_number) {
        DotData.DotReading.Builder dot_info = DotData.DotReading.newBuilder();

        dot_info.setDotNr(dot_number)
                .setTimestamp(System.currentTimeMillis());

        return dot_info.build();
    }

    /**
     *
     * @param wifiScanList
     * @return

    private GpsData.GpsReading prepareGpsProtobuf ()
    {

    }
     */

    private WifiData.WiFiReading prepareWiFiProtobuf (List<ScanResult> wifiScanList) {

        WifiData.WiFiReading.Builder wifi_data = WifiData.WiFiReading.newBuilder();

        wifi_data.setSequenceNr(seq_nr_wifi)
                .setTimestamp(System.currentTimeMillis())
                .setLastDotNr(seq_nr_dot);

        for (ScanResult sr : wifiScanList) {
            WifiData.WiFiReading.WiFiAP.Builder wifi_ap = WifiData.WiFiReading.WiFiAP.newBuilder();

            wifi_ap.setBssid(sr.BSSID)
                    .setSsid(sr.SSID)
                    .setCapabilities(sr.capabilities)
                    .setFrequency(sr.frequency)
                    .setRssi(sr.level)
                    .setTimestamp(sr.timestamp);

            wifi_data.addWifiAp(wifi_ap);
        }

        return wifi_data.build();
    }

    private CellularData.CellularReading prepareCellularProtobuf (List<CellInfo> cInfoList) {
        Log.i("PROTOBUF", "This is NOT!!! Samsung");
        CellularData.CellularReading.Builder cellular_data = CellularData.CellularReading.newBuilder();

        cellular_data.setSequenceNr(seq_nr_cellular)
                     .setTimestamp(System.currentTimeMillis())
                .setLastDotNr(seq_nr_dot);

        for (CellInfo info : cInfoList){

            CellularData.CellularReading.CellInfo.Builder cell_info = CellularData.CellularReading.CellInfo.newBuilder();

            cell_info.setTimestamp(info.getTimeStamp())
                     .setRegistered(info.isRegistered());

            if (info instanceof CellInfoGsm) {

                cell_info.setNetworkType(CellularData.CellularReading.NetworkType.GSM);

                CellIdentityGsm cid_gsm = ((CellInfoGsm) info).getCellIdentity();

                CellularData.CellularReading.CellIdentityGsm.Builder gsm_identity = CellularData.CellularReading.CellIdentityGsm.newBuilder();
                gsm_identity.setCid(cid_gsm.getCid())
                        .setLac(cid_gsm.getLac())
                        .setMcc(cid_gsm.getMcc())
                        .setMnc(cid_gsm.getMnc())
                        .setPsc(cid_gsm.getPsc())
                        .setHashCode(cid_gsm.hashCode());

                CellSignalStrengthGsm signalstrength_gsm = ((CellInfoGsm) info).getCellSignalStrength();

                CellularData.CellularReading.CellSignalStrengthGsm.Builder gsm_ss = CellularData.CellularReading.CellSignalStrengthGsm.newBuilder();
                gsm_ss.setAsuLevel(signalstrength_gsm.getAsuLevel())
                        .setDbm(signalstrength_gsm.getDbm())
                        .setLevel(signalstrength_gsm.getLevel())
                        .setHashCode(signalstrength_gsm.hashCode());

                cell_info.setGsmIdentity(gsm_identity);
                cell_info.setGsmSignalStrength(gsm_ss);

            } else if (info instanceof CellInfoCdma) {

                cell_info.setNetworkType(CellularData.CellularReading.NetworkType.CDMA);

                CellIdentityCdma cid_cdma = ((CellInfoCdma) info).getCellIdentity();

                CellularData.CellularReading.CellIdentityCmda.Builder cdma_identity = CellularData.CellularReading.CellIdentityCmda.newBuilder();
                cdma_identity.setBasestationId(cid_cdma.getBasestationId())
                         .setLatitude(cid_cdma.getLatitude())
                         .setLongitude(cid_cdma.getLongitude())
                         .setNetworkId(cid_cdma.getNetworkId())
                         .setSystemId(cid_cdma.getSystemId())
                         .setHashCode(cid_cdma.hashCode());

                CellSignalStrengthCdma signalstrength_cdma = ((CellInfoCdma) info).getCellSignalStrength();

                CellularData.CellularReading.CellSignalStrengthCdma.Builder cdma_ss = CellularData.CellularReading.CellSignalStrengthCdma.newBuilder();
                cdma_ss.setAsuLevel(signalstrength_cdma.getAsuLevel())
                        .setCdmaDbm(signalstrength_cdma.getCdmaDbm())
                        .setCdmaEcio(signalstrength_cdma.getCdmaEcio())
                        .setCdmaLevel(signalstrength_cdma.getCdmaLevel())
                        .setDbm(signalstrength_cdma.getDbm())
                        .setEvdoDbm(signalstrength_cdma.getEvdoDbm())
                        .setEvdoEcio(signalstrength_cdma.getEvdoEcio())
                        .setEvdoLevel(signalstrength_cdma.getEvdoLevel())
                        .setEvdoSnr(signalstrength_cdma.getEvdoSnr())
                        .setLevel(signalstrength_cdma.getLevel())
                        .setHashCode(signalstrength_cdma.hashCode());

                cell_info.setCdmaIdentity(cdma_identity);
                cell_info.setCdmaSignalStrength(cdma_ss);

            } else if (info instanceof CellInfoLte) {

                cell_info.setNetworkType(CellularData.CellularReading.NetworkType.LTE);

                CellIdentityLte cid_lte = ((CellInfoLte) info).getCellIdentity();

                CellularData.CellularReading.CellIdentityLte.Builder lte_identity = CellularData.CellularReading.CellIdentityLte.newBuilder();
                lte_identity.setCi(cid_lte.getCi())
                        .setMcc(cid_lte.getMcc())
                        .setMnc(cid_lte.getMnc())
                        .setPci(cid_lte.getPci())
                        .setTac(cid_lte.getTac())
                        .setHashCode(cid_lte.hashCode());

                CellSignalStrengthLte signalstrength_lte = ((CellInfoLte) info).getCellSignalStrength();

                CellularData.CellularReading.CellSignalStrengthLte.Builder lte_ss = CellularData.CellularReading.CellSignalStrengthLte.newBuilder();
                lte_ss.setAsuLevel(signalstrength_lte.getAsuLevel())
                        .setDbm(signalstrength_lte.getDbm())
                        .setLevel(signalstrength_lte.getLevel())
                        .setTimingAdvance(signalstrength_lte.getTimingAdvance())
                        .setHashCode(signalstrength_lte.hashCode());

                cell_info.setLteIdentity(lte_identity);
                cell_info.setLteSignalStrength(lte_ss);

            } else if (info instanceof CellInfoWcdma) {

                cell_info.setNetworkType(CellularData.CellularReading.NetworkType.WCDMA);

                CellIdentityWcdma cid_wcdma = ((CellInfoWcdma) info).getCellIdentity();

                CellularData.CellularReading.CellIdentityWcdma.Builder wcdma_identity = CellularData.CellularReading.CellIdentityWcdma.newBuilder();
                wcdma_identity.setCid(cid_wcdma.getCid())
                          .setLac(cid_wcdma.getLac())
                          .setMcc(cid_wcdma.getMcc())
                          .setMnc(cid_wcdma.getMnc())
                          .setPsc(cid_wcdma.getPsc())
                          .setHashCode(cid_wcdma.hashCode());

                CellSignalStrengthWcdma signalstrength_wcdma = ((CellInfoWcdma) info).getCellSignalStrength();

                CellularData.CellularReading.CellSignalStrengthWcdma.Builder wcdma_ss = CellularData.CellularReading.CellSignalStrengthWcdma.newBuilder();
                wcdma_ss.setAsuLevel(signalstrength_wcdma.getAsuLevel())
                        .setDbm(signalstrength_wcdma.getDbm())
                        .setLevel(signalstrength_wcdma.getLevel())
                        .setHashCode(signalstrength_wcdma.hashCode());

                cell_info.setWcdmaIdentity(wcdma_identity);
                cell_info.setWcdmaSignalStrength(wcdma_ss);
            }

            cellular_data.addCellularInfo(cell_info);
        }

        return cellular_data.build();
    }

    private CellularData.CellularReading prepareCellularProtobufForSamsung (List<CellInfo> cInfoList) {
        Log.i("PROTOBUF", "This is Samsung");
        CellularData.CellularReading.Builder cellular_data = CellularData.CellularReading.newBuilder();

        cellular_data.setSequenceNr(seq_nr_cellular)
                .setTimestamp(System.currentTimeMillis())
                .setLastDotNr(seq_nr_dot);

        for (CellInfo info : cInfoList){

            CellularData.CellularReading.CellInfo.Builder cell_info = CellularData.CellularReading.CellInfo.newBuilder();

            cell_info.setTimestamp(info.getTimeStamp())
                    .setRegistered(info.isRegistered());

            if (info instanceof CellInfoGsm) {

                cell_info.setNetworkType(CellularData.CellularReading.NetworkType.GSM);

                CellIdentityGsm cid_gsm = ((CellInfoGsm) info).getCellIdentity();

                CellularData.CellularReading.CellIdentityGsm.Builder gsm_identity = CellularData.CellularReading.CellIdentityGsm.newBuilder();
                gsm_identity.setCid(cid_gsm.getCid())
                        .setLac(cid_gsm.getLac())
                        .setMcc(cid_gsm.getMcc())
                        .setMnc(cid_gsm.getMnc())
                        .setPsc(cid_gsm.getPsc())
                        .setHashCode(cid_gsm.hashCode());

                CellSignalStrengthGsm signalstrength_gsm = ((CellInfoGsm) info).getCellSignalStrength();

                CellularData.CellularReading.CellSignalStrengthGsm.Builder gsm_ss = CellularData.CellularReading.CellSignalStrengthGsm.newBuilder();
                gsm_ss.setAsuLevel(signalstrength_gsm.getAsuLevel())
                        .setDbm(signalstrength_gsm.getDbm())
                        .setLevel(signalstrength_gsm.getLevel())
                        .setHashCode(signalstrength_gsm.hashCode());

                cell_info.setGsmIdentity(gsm_identity);
                cell_info.setGsmSignalStrength(gsm_ss);

            } else if (info instanceof CellInfoCdma) {

                cell_info.setNetworkType(CellularData.CellularReading.NetworkType.CDMA);

                CellIdentityCdma cid_cdma = ((CellInfoCdma) info).getCellIdentity();

                CellularData.CellularReading.CellIdentityCmda.Builder cdma_identity = CellularData.CellularReading.CellIdentityCmda.newBuilder();
                cdma_identity.setBasestationId(cid_cdma.getBasestationId())
                        .setLatitude(cid_cdma.getLatitude())
                        .setLongitude(cid_cdma.getLongitude())
                        .setNetworkId(cid_cdma.getNetworkId())
                        .setSystemId(cid_cdma.getSystemId())
                        .setHashCode(cid_cdma.hashCode());

                CellSignalStrengthCdma signalstrength_cdma = ((CellInfoCdma) info).getCellSignalStrength();

                CellularData.CellularReading.CellSignalStrengthCdma.Builder cdma_ss = CellularData.CellularReading.CellSignalStrengthCdma.newBuilder();

                // Bug in Samsung API returns invalid values for signal strength
                // We can get the data only for the registered network
                if (info.isRegistered()) {
                    cdma_ss.setAsuLevel(cdmaAsu)
                            .setCdmaDbm(cdmaDbm)
                            .setCdmaEcio(cdmaEcio)
                            .setCdmaLevel(cdmaLevel)
                            .setDbm(s_Dbm)
                            .setEvdoDbm(evdoDbm)
                            .setEvdoEcio(evdoEcio)
                            .setEvdoLevel(evdoLevel)
                            .setEvdoSnr(evdoSnr)
                            .setLevel(s_Level);
                } else {
                    cdma_ss.setAsuLevel(signalstrength_cdma.getAsuLevel())
                            .setCdmaDbm(signalstrength_cdma.getCdmaDbm())
                            .setCdmaEcio(signalstrength_cdma.getCdmaEcio())
                            .setCdmaLevel(signalstrength_cdma.getCdmaLevel())
                            .setDbm(signalstrength_cdma.getDbm())
                            .setEvdoDbm(signalstrength_cdma.getEvdoDbm())
                            .setEvdoEcio(signalstrength_cdma.getEvdoEcio())
                            .setEvdoLevel(signalstrength_cdma.getEvdoLevel())
                            .setEvdoSnr(signalstrength_cdma.getEvdoSnr())
                            .setLevel(signalstrength_cdma.getLevel());
                }
                cdma_ss.setHashCode(signalstrength_cdma.hashCode());

                cell_info.setCdmaIdentity(cdma_identity);
                cell_info.setCdmaSignalStrength(cdma_ss);

            } else if (info instanceof CellInfoLte) {

                cell_info.setNetworkType(CellularData.CellularReading.NetworkType.LTE);

                CellIdentityLte cid_lte = ((CellInfoLte) info).getCellIdentity();

                CellularData.CellularReading.CellIdentityLte.Builder lte_identity = CellularData.CellularReading.CellIdentityLte.newBuilder();
                lte_identity.setCi(cid_lte.getCi())
                        .setMcc(cid_lte.getMcc())
                        .setMnc(cid_lte.getMnc())
                        .setPci(cid_lte.getPci())
                        .setTac(cid_lte.getTac())
                        .setHashCode(cid_lte.hashCode());

                CellSignalStrengthLte signalstrength_lte = ((CellInfoLte) info).getCellSignalStrength();

                CellularData.CellularReading.CellSignalStrengthLte.Builder lte_ss = CellularData.CellularReading.CellSignalStrengthLte.newBuilder();

                // Bug in Samsung API returns invalid values for signal strength
                // We can get the data only for the registered network
                if (info.isRegistered()) {
                    lte_ss.setAsuLevel(lteAsu)
                            .setDbm(lteDbm)
                            .setLevel(lteLevel);
                }
                else {
                    lte_ss.setAsuLevel(signalstrength_lte.getAsuLevel())
                            .setDbm(signalstrength_lte.getDbm())
                            .setLevel(signalstrength_lte.getLevel());
                }
                lte_ss.setTimingAdvance(signalstrength_lte.getTimingAdvance())
                            .setHashCode(signalstrength_lte.hashCode());

                cell_info.setLteIdentity(lte_identity);
                cell_info.setLteSignalStrength(lte_ss);

            } else if (info instanceof CellInfoWcdma) {

                cell_info.setNetworkType(CellularData.CellularReading.NetworkType.WCDMA);

                CellIdentityWcdma cid_wcdma = ((CellInfoWcdma) info).getCellIdentity();

                CellularData.CellularReading.CellIdentityWcdma.Builder wcdma_identity = CellularData.CellularReading.CellIdentityWcdma.newBuilder();
                wcdma_identity.setCid(cid_wcdma.getCid())
                        .setLac(cid_wcdma.getLac())
                        .setMcc(cid_wcdma.getMcc())
                        .setMnc(cid_wcdma.getMnc())
                        .setPsc(cid_wcdma.getPsc())
                        .setHashCode(cid_wcdma.hashCode());

                CellSignalStrengthWcdma signalstrength_wcdma = ((CellInfoWcdma) info).getCellSignalStrength();

                CellularData.CellularReading.CellSignalStrengthWcdma.Builder wcdma_ss = CellularData.CellularReading.CellSignalStrengthWcdma.newBuilder();
                wcdma_ss.setAsuLevel(signalstrength_wcdma.getAsuLevel())
                        .setDbm(signalstrength_wcdma.getDbm())
                        .setLevel(signalstrength_wcdma.getLevel())
                        .setHashCode(signalstrength_wcdma.hashCode());

                cell_info.setWcdmaIdentity(wcdma_identity);
                cell_info.setWcdmaSignalStrength(wcdma_ss);
            }

            cellular_data.addCellularInfo(cell_info);
        }

        return cellular_data.build();
    }

    private SensorData.SensorReading prepareSensorProtobuf (SensorEvent event) {
        // 1 event, 1 SensorReading !

        // Create Sensor Data
        SensorData.SensorReading.Builder sensor_data = SensorData.SensorReading.newBuilder();

        sensor_data.setTimestamp(System.currentTimeMillis())
                    .setSequenceNr(seq_nr_sensorevent)
                    .setLastDotNr(seq_nr_dot);

        // Create SensorEvent
        SensorData.SensorReading.SensorEvent.Builder sensor_event = SensorData.SensorReading.SensorEvent.newBuilder();

        sensor_event.setSensorType(event.sensor.getType())
                    .setTimestamp(event.timestamp)
                    .setAccuracy(event.accuracy);

        // Fill values
        SensorData.SensorReading.SensorEvent.SensorValues.Builder sensor_values = SensorData.SensorReading.SensorEvent.SensorValues.newBuilder();
        for (int i=0; i < event.values.length; i++)
            sensor_values.addValue(event.values[i]);

        sensor_event.setValues(sensor_values);  // sensor_event is ready
        sensor_data.setSensorEvent(sensor_event);   // sensor_data is ready

        return sensor_data.build();
    }

    private  GpsData.GpsReading prepareGpsProtobuf (Location location) {

        // Create GPS Reading Data
        GpsData.GpsReading.Builder gps_data = GpsData.GpsReading.newBuilder();

        gps_data.setSequenceNr(seq_nr_gpsfix)
                .setTimestamp(System.currentTimeMillis())
                .setLastDotNr(seq_nr_dot);

        // Fill in Location
        GpsData.GpsReading.Location.Builder _location = GpsData.GpsReading.Location.newBuilder();

        _location.setAccuracy(location.getAccuracy())
                .setAltitude(location.getAltitude())
                .setBearing(location.getBearing())
                .setElapsedRealtimeNanos(location.getElapsedRealtimeNanos())
                .setLatitude(location.getLatitude())
                .setLongitude(location.getLongitude())
                .setProvider(location.getProvider())
                .setSpeed(location.getSpeed())
                .setTime(location.getTime())
                .setHasAccuracy(location.hasAccuracy())
                .setHasAltitude(location.hasAltitude())
                .setHasBearing(location.hasBearing())
                .setHasSpeed(location.hasSpeed());

        gps_data.setLocation(_location);

        return gps_data.build();
    }

    private void captureCellularScan() {
        // Get CellInfo and display

        /* Increment cellular scan counter */
        seq_nr_cellular = seq_nr_cellular + 1;

        /* Prepare protocol buffer and write into file */
        CellularData.CellularReading cellular_reading;
        if (Build.MANUFACTURER.equalsIgnoreCase("samsung"))
            cellular_reading = prepareCellularProtobufForSamsung(telephony_manager.getAllCellInfo());
        else
            cellular_reading = prepareCellularProtobuf(telephony_manager.getAllCellInfo());

        try {
            cellular_reading.writeDelimitedTo(Cellular_BOS);
        } catch (IOException e) {
            Log.e("Cellular_BOS exception:", e.toString());
        }
        //list_cellular_readings.add(cellular_reading);

        onScreenVerbose.put("seq_nr_cellular", Integer.toString(cellular_reading.getSequenceNr()));

        List<CellularData.CellularReading.CellInfo> cell_info_list = cellular_reading.getCellularInfoList();

        CellList_text = new ArrayList<String>();

        for (CellularData.CellularReading.CellInfo cInfo : cell_info_list) {
            CellList_text.add(cInfo.toString());
        }

        numberOfCellTowers = cell_info_list.size();
    }

    private class WifiScanReceiver extends BroadcastReceiver {
        public void onReceive(Context c, Intent intent) {

            /* Calculate elapsed time between consecutive WiFi scans */
            time_prev = time_current;
            time_current = System.currentTimeMillis();
            WiFiScanDuration = (int) (time_current - time_prev);

            /* Re-scan WiFi */
            //wifi.startScan();

            if (state == State.RUNNING) {       // This check must be unnecessary
            /* Increment WiFi Scan Counter */
                seq_nr_wifi = seq_nr_wifi + 1;

                WifiData.WiFiReading wifi_reading = prepareWiFiProtobuf(wifi.getScanResults());

            /* Write scan results into file */
                try {
                    wifi_reading.writeDelimitedTo(WiFi_BOS);
                } catch (IOException e) {
                    Log.e("WiFi_BOS exception:", e.toString());
                }
                //list_wifi_readings.add(wifi_reading);

                /* Re-scan WiFi */
                wifi.startScan();

                // Display
                onScreenVerbose.put("seq_nr_wifi", Integer.toString(wifi_reading.getSequenceNr()));
                displayWiFiScan(wifi_reading.getWifiApList());
            }
        }
    }

    /* Display WiFi Scan */
    private void displayWiFiScan (List<WifiData.WiFiReading.WiFiAP> wifi_ap_list) {
        WiFiList_text.clear();
        for (WifiData.WiFiReading.WiFiAP ap : wifi_ap_list) {
            WiFiList_text.add(ap.toString());
        }
        numberOfAPs = wifi_ap_list.size();
        numAPs.setText("# APs: " + numberOfAPs);

        if (numberOfAPs == 0) WiFiList_text = new ArrayList<>();
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
                changeBeepVolume();

                return true;
            case R.id.flush_files:

                return flushFiles();
            case R.id.renew_files:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage("DELETING ALL FILES!\nAre you sure?").setPositiveButton("Yes", renewDialogClickListener)
                        .setNegativeButton("No", renewDialogClickListener).show();

                return true;
            case R.id.unindex_files:
                unIndexFiles();
                //indexed_URIs = new ArrayList<>();
                return true;
            case R.id.set_sensor_freq:
                AlertDialog.Builder freq_dialog_builder = new AlertDialog.Builder(this);
                final EditText input = new EditText(this);
                input.setInputType(InputType.TYPE_NUMBER_VARIATION_NORMAL | InputType.TYPE_CLASS_NUMBER);
                freq_dialog_builder.setView(input);

                freq_dialog_builder.setMessage("Change Sensor sampling frequency (microseconds)\\ Current:" + SENSOR_DELAY).setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        String t_input =  input.getText().toString();
                        if ( t_input.length() == 0 ) {
                            Toast.makeText(getApplicationContext(), "Error: empty input" , Toast.LENGTH_LONG).show();

                        } else {
                            SENSOR_DELAY = Integer.parseInt(t_input);
                            Toast.makeText(getApplicationContext(), "Set to: " + SENSOR_DELAY,
                                    Toast.LENGTH_SHORT).show();
                        }
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
            case R.id.set_prefix:
                    askNewFilePrefix();
                return true;
            case R.id.count_messages:
                countMessagesInOutputFiles();
                return true;
            case R.id.save_metadata:

                saveMetadata();

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    DialogInterface.OnClickListener renewDialogClickListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which){
                case DialogInterface.BUTTON_POSITIVE:
                    //Yes button clicked
                    Toast.makeText(getApplicationContext(), "Deleting files in:\n" + dirname.getName(), Toast.LENGTH_SHORT).show();

                    unIndexFiles();
                    for(File file: dirname.listFiles()) {
                        file.delete();
                        Log.v("Deleted", file.getName());
                        //Toast.makeText(getApplicationContext(), "Deleted:\n" + file.getName(), Toast.LENGTH_SHORT).show();
                    }
                    createOutputFiles(current_file_prefix);
                    list_dot_readings.clear();      // unnecessary
                    list_sensor_readings.clear();   // unnecessary

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

    public class SaveMetaDataTask extends AsyncTask <String, Integer, String> {
        @Override
        protected void onPreExecute() {
            tv_metadata.setText("Preparing Metadata.");
        }
        @Override
        protected String doInBackground(String... params) {
            ScenarioDefinition scenario_metadata = new ScenarioDefinition(Integer.parseInt(current_file_prefix), rbs.sensorList, rbs.mSensorManager, Metadata_BOS);

            // This while loop's condition must never be true
            while (scenario_metadata.pressure_value_count < scenario_metadata.pressure_sensor_max_sample_size) {
                // wait until enough samples are taken, then proceed.
                try {
                    Thread.sleep(100);
                    Log.v(this.getClass().getName(),"Pressure_index=" + scenario_metadata.pressure_value_count);
                }catch (Exception e) {Log.wtf(this.getClass().getName(),"sleep problem");}
            }
            scenario_metadata.prepare(0, rbs.sensorList, Metadata_BOS);
            Log.v("SaveMetadata", "doInBackground....done.");
            return null;
        }

        @Override
        protected void onPostExecute(String s) {
            Toast.makeText(getApplicationContext(), "Metadata saved.", Toast.LENGTH_SHORT).show();
            flushFiles();
            Log.v("Save Metadata", "Flushed");
            tv_metadata.setText("Metadata is saved.");

        }
    }
    private boolean saveMetadata () {

        if (!sensorSwitch.isChecked()) {
            //Dialog
            new AlertDialog.Builder(this).setMessage("Start the Sensors first!!")
                    .setNeutralButton("Close Dialog",null)
                    .setCancelable(true)
                                         .show();
            return false;
        }

        //tv_metadata.setText("Preparing Metadata.");
        new SaveMetaDataTask().execute();

        return true;
    }

    private void askNewFilePrefix() {
        AlertDialog.Builder prefix_dialog_builder = new AlertDialog.Builder(this);
        final EditText prefix_input = new EditText(this);
        prefix_input.setInputType(InputType.TYPE_NUMBER_VARIATION_NORMAL | InputType.TYPE_CLASS_NUMBER);
        prefix_dialog_builder.setView(prefix_input);

        prefix_dialog_builder.setMessage("Set file prefix\n Current:" + current_file_prefix).setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String t_input = prefix_input.getText().toString();
                if (t_input.length() == 0) {
                    Toast.makeText(getApplicationContext(), "Error: empty input", Toast.LENGTH_LONG).show();

                } else {
                    current_file_prefix = t_input;
                    Toast.makeText(getApplicationContext(), "Set to: " + current_file_prefix,
                            Toast.LENGTH_SHORT).show();

                    makeTitle();

                    createOutputFiles(current_file_prefix);
                }
            }
        });
        prefix_dialog_builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
                Toast.makeText(getApplicationContext(), "not changed", Toast.LENGTH_SHORT).show();
            }
        });

        prefix_input.requestFocus();

        prefix_dialog_builder.show();

        //This is for openning the keyboard automatically. doesn't work properly.
        //getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    }

    private void changeBeepVolume() {
        final AlertDialog.Builder volume_dialog_builder = new AlertDialog.Builder(this);

        final SeekBar volumeSeek = new SeekBar(this);

        volume_dialog_builder.setView(volumeSeek);

        volume_dialog_builder.setMessage("Set volume:\n Current:" + VOLUME).setNeutralButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        volumeSeek.setProgress(VOLUME);

        volumeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                //Do something here with new value

                VOLUME = progress;

                //Log.v("SeekBar Volume", "" + progress);
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
                toneG = new ToneGenerator(AudioManager.STREAM_ALARM, VOLUME);
                toneG.startTone(ToneGenerator.TONE_PROP_BEEP, 200);
            }
        });

        volume_dialog_builder.show();
    }

    private boolean flushFiles () {
        unIndexFiles();

        //writeProtobufsToFile();

        try {
            WiFi_BOS.flush();
            Sensor_BOS.flush();
            Dots_BOS.flush();
            Cellular_BOS.flush();
            Gps_BOS.flush();
            Metadata_BOS.flush();
        } catch (IOException e) {
            Log.v("Flush BOS:",e.toString());
            return false;
        }

        //sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(outFile)));
        File wifiFile = new File(WiFi_output_file);
        File sensorFile = new File(Sensors_output_file);
        File dotFile = new File(Dots_output_file);
        File gpsFile = new File(Gps_output_file);
        File metadataFile = new File(Metadata_output_file);
        File cellularFile = new File(Cellular_output_file);

        MediaScannerConnection.scanFile(
                getApplicationContext(),
                new String[]{wifiFile.getAbsolutePath(), sensorFile.getAbsolutePath(), dotFile.getAbsolutePath(),
                             gpsFile.getAbsolutePath(), metadataFile.getAbsolutePath(), cellularFile.getAbsolutePath()},
                null,
                new MediaScannerConnection.OnScanCompletedListener() {
                    @Override
                    public void onScanCompleted(String path, Uri uri) {
                        Log.v("Media Scanner", "file " + path + " was scanned successfully: " + uri);
                        indexed_URIs.add(uri);
                    }
                }
        );
        Toast.makeText(getApplicationContext(), "Files Flushed", Toast.LENGTH_SHORT).show();
        return true;
    }

    private class SensorHandler implements Runnable {

        Handler mHandler;

        public List<Sensor> sensorList;

        private volatile boolean running;
        private volatile int measuringFrequency;
        public SensorManager mSensorManager;
        volatile int flag = 0;

        float[] s_light;

        public SensorHandler(Handler _handler, int _measuringFrequency) {

            Log.v("Sensor Handler", "started");

            mHandler = _handler;
            measuringFrequency = _measuringFrequency;

            mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

            sensorList = getDefaultSensorList();

            sensorHashMap = new HashMap<>();
            sensorLastTimestampHashMap = new  HashMap<Sensor, Long>();
            sensorCurrentTimestampHashMap = new  HashMap<Sensor, Long>();

            for (Sensor _s : sensorList) {
                sensorHashMap.put(_s, null);
            }

            if (sensorList.size() != sensorHashMap.size())
                Log.wtf("DEADLY ERROR!!!!", "defaultSensorList.size() != sensorHashMap.size() " + sensorList.size() + " != " + sensorHashMap.size());

            List<Sensor> keyList = new ArrayList<>(sensorHashMap.keySet());

            SensorList_text = new ArrayList<String>();
            for (int i = 0; i < keyList.size();i++){
                SensorList_text.add(keyList.get(i) + ": " + Arrays.toString(sensorHashMap.get(keyList.get(i))) + ", isWakeup?"  + keyList.get(i).isWakeUpSensor());
            }
            numberOfSensors = sensorHashMap.size();
            mHandler.post(new Runnable() {
                @Override
                public void run() {

                    WiFiScanTime.setText("Ready! " + Arrays.toString(s_light));

                    updateExpandableListView();
                }
            });
        }

        public List<Sensor> getDefaultSensorList () {

            List<Sensor> sensorList = mSensorManager.getSensorList(Sensor.TYPE_ALL);
            List<Sensor> defaultSensorList = new ArrayList<>();
            List<Sensor> nullSensorList = new ArrayList<>();

            for (int i = 0; i < sensorList.size(); i++) {

                Sensor default_sensor = mSensorManager.getDefaultSensor(sensorList.get(i).getType());

                if (! defaultSensorList.contains(default_sensor)) {
                    if ( default_sensor != null) {
                        defaultSensorList.add(default_sensor);
                    } else {
                        boolean newSensorType = true;
                        for (Sensor _s : nullSensorList) {
                            if (_s.getType() == sensorList.get(i).getType() )
                                newSensorType = false;
                        }
                        nullSensorList.add(sensorList.get(i));
                        if (newSensorType) {
                            defaultSensorList.add(sensorList.get(i));
                        }
                    }
                }

            }
            sensorList = defaultSensorList;
            return sensorList;
        }

        public void sortSensorListText () {
            List<Sensor> keyList = new ArrayList<>(sensorHashMap.keySet());

            SensorList_text = new ArrayList<String>();

            for (int i = 0; i < keyList.size();i++){
                if (sensorLastTimestampHashMap.get(keyList.get(i)) != null )
                    SensorList_text.add(keyList.get(i) + ":\n" + Arrays.toString(sensorHashMap.get(keyList.get(i))) +
                            " in " + (sensorCurrentTimestampHashMap.get(keyList.get(i)) - sensorLastTimestampHashMap.get(keyList.get(i)))/1000000 + "msec" + ", isWakeup: " );
            }

            Collections.sort(SensorList_text, String.CASE_INSENSITIVE_ORDER);
        }

        public void startSensors() {
            registerSensors(mSensorManager, sensorList);
        }

        public void stopSensors() {
            unRegisterSensors(mSensorManager);
        }

        public void showAccuracies () {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    for (Sensor _s : sensorList) {
                        if (_s.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                            geomagnetic_checkbox.setText("Geomagn_resltn: " + _s.getResolution());
                            break;
                        }
                    }
                    //prepareListData();
                }
            });
        }

        @Override
        public void run() {

            Log.v("Sensor", "thread started to run.");

            running = true;

            // Reset sensorHashMap
            sensorHashMap = new HashMap<>();

            startSensors();

            while (running) {

                sortSensorListText();

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (s_light != null)
                            WiFiScanTime.setText(" Light: " + s_light[0]);
                        //prepareListData();
                        if (state != State.RUNNING) // I don't remember why.. ok, to cross-check the pressure sensor values.
                            ;//updateExpandableListView();
                    }
                });

                try {
                    Thread.sleep(measuringFrequency);
                } catch (InterruptedException e) {
                    Log.e("THREAD_SLEEP", e.toString());
                }

            } // while running
        }

        private void registerSensors (SensorManager tSensorManager, List<Sensor> listOfSensors){
            //SENSOR_DELAY_GAME = 20.000 microsecond
            //SENSOR_DELAY_NORMAL = 180.000 microsecond
            //SENSOR_DELAY_UI = 60.000 microsecond mSensorManager.SENSOR_DELAY_UI
            //SENSOR_DELAY_FASTEST = As fast as possible

            for (int i = 0; i < listOfSensors.size(); i++) {
                mSensorManager.registerListener(mSensorListener, listOfSensors.get(i), SENSOR_DELAY);
            }

            Log.v(this.getClass().getName(), "sensors registered.");
        }

        private void unRegisterSensors (SensorManager tSensorManager){
                mSensorManager.unregisterListener(mSensorListener);
        }
        volatile long senor_event_counter = 0;
        private SensorEventListener mSensorListener = new SensorEventListener() {


            @Override
            public void onAccuracyChanged(Sensor sensor, final int accuracy) {
                final Sensor _s = sensor;
                final int _acc = accuracy;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (_s.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                                geomagnetic_checkbox.setText("Magnetic_Field: " + _acc);
                            Log.i("Magnetic_Field", "Accuracy changed to: " + accuracy);

                            // Set checked if accuracy is high
                            geomagnetic_checkbox.setChecked(accuracy == SensorManager.SENSOR_STATUS_ACCURACY_HIGH);
                        }

                    }
                });
            }

            @Override
            public void onSensorChanged(SensorEvent event) {
                senor_event_counter++;


                //list_sensor_readings.add(prepareSensorProtobuf(event));

                if (state == State.RUNNING) {
                    seq_nr_sensorevent = seq_nr_sensorevent + 1;

                    SensorData.SensorReading sensor_event = prepareSensorProtobuf(event);
                    try {
                        sensor_event.writeDelimitedTo(Sensor_BOS);
                    } catch (IOException e) {
                        Log.e("Sensor_BOS exception", e.toString());
                    }

                    // For displaying on the screen
                    onScreenVerbose.put("seq_nr_sensorevent", Long.toString(sensor_event.getSequenceNr()));
                }

                //new SensorDataDisplayer().execute(event);

                float[] sensor_values_for_hashmap;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        tv_sensor_event_counter.setText(senor_event_counter + "");
                    }
                });

                if(event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
                    float[] time_x = {(float) Calendar.getInstance().get(Calendar.SECOND)};
                    sensor_values_for_hashmap = time_x;
                }
                else
                    sensor_values_for_hashmap = event.values;

                if (event.sensor.getType() == Sensor.TYPE_LIGHT)
                    s_light = event.values;

                Sensor sensorHashKey = event.sensor;
                sensorHashMap.put(sensorHashKey, sensor_values_for_hashmap);
                sensorLastTimestampHashMap.put(sensorHashKey,sensorCurrentTimestampHashMap.get(sensorHashKey));
                sensorCurrentTimestampHashMap.put(sensorHashKey, new Long(event.timestamp));

            }
        };

        // This AsyncTask class was implemented to separate part of the task from SensorEventListener
        // to improve the speed, but it had no effect, if not negative.
        public class SensorDataDisplayer extends AsyncTask <SensorEvent, Integer, String> {
            @Override
            protected String doInBackground(SensorEvent... params) {

                SensorEvent ev = params[0];
                float[] sensor_values_for_hashmap;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        tv_sensor_event_counter.setText(senor_event_counter + "");
                    }
                });

                if(ev.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
                    float[] time_x = {(float) Calendar.getInstance().get(Calendar.SECOND)};
                    sensor_values_for_hashmap = time_x;
                }
                else
                    sensor_values_for_hashmap = ev.values;

                if (ev.sensor.getType() == Sensor.TYPE_LIGHT)
                    s_light = ev.values;

                Sensor sensorHashKey = ev.sensor;
                sensorHashMap.put(sensorHashKey, sensor_values_for_hashmap);
                sensorLastTimestampHashMap.put(sensorHashKey,sensorCurrentTimestampHashMap.get(sensorHashKey));
                sensorCurrentTimestampHashMap.put(sensorHashKey, new Long(ev.timestamp));

                return null;
            }
        }
        // Terminate the running thread
        public void terminate() {
            //unRegisterSensors(mSensorManager);
            stopSensors();
            running = false;
        }
    }

    private class MeasurementTimer implements Runnable {

        final int ONE_SECOND = 1000; // in milliseconds


        int timer_sec = 0;
        boolean running;

        Handler mHandler;

        public MeasurementTimer(Handler _handler) {
            mHandler = _handler;
        }

        public void terminate(){
            running = false;
        }

        @Override
        public void run() {
            running = true;

            while (running) {
                try {
                    captureCellularScan();
                    Thread.sleep(1000);

                } catch (InterruptedException e) {
                    Log.e("THREAD_SLEEP", e.toString());
                }
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        //slv.setAdapter(new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_list_item_1, sensors));
                        timer_sec++;
                        tv_timer_sec.setText((int) Math.floor(timer_sec / 60) + " min : " + (timer_sec % 60) + " sec" + "\n");

                        updateExpandableListView();
                        tv_bottomView.setText(onScreenVerbose.toString().replaceAll(", ", "\n"));

                        if ( System.currentTimeMillis() - last_gps_fix_time > 10*ONE_SECOND) {
                            // Turn GPS coloring/indications off
                            showGpsFound(false);
                        }
                    }
                });
            }
        }

    }

    @Override
    public void onPause() {
        super.onPause();  // Always call the superclass method first


        /*
        ActivityManager activityManager = (ActivityManager) getApplicationContext()
                .getSystemService(Context.ACTIVITY_SERVICE);

        activityManager.moveTaskToFront(getTaskId(), 0);
        */

        //StartStopButton.setChecked(false);
    }

    @Override
    public void onStop() {
        super.onStop();  // Always call the superclass method first
        v.vibrate(50); v.vibrate(50); v.vibrate(50); v.vibrate(50);
        //StartStopButton.setChecked(false);
    }

    @Override
    public void onBackPressed() {
        // Ignore Back Button, not necessary
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
  //              | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    public void UiChangeListener()
    {
        final View decorView = getWindow().getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                    decorView.setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            //| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            //| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            //| View.SYSTEM_UI_FLAG_FULLSCREEN
                            //| View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    );
                }
            }
        });
    }

    private void showGpsFound(boolean show) {
        if (show) {
            gotGpsFix = true;
            StartStopButton.setBackgroundColor(Color.GREEN);
            //setTitle(TITLE + "   GPS signal found.");
            GPS_STATUS = "GPS found";
        }
        else {
            gotGpsFix = false;
            StartStopButton.setBackgroundColor(Color.LTGRAY);
            if (gpsSwitch.isChecked()) {
                //setTitle(TITLE + "   GPS searching.");
                GPS_STATUS = "GPS searching";
            }
        }
        makeTitle();
    }

    private LocationListener getLocationListener() {
        // Define a listener that responds to location updates

        Log.v("LocationListener", " Started with providers: " + locationManager.getAllProviders().toString());

        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                // Called when a new location is found by the network location provider.

                last_gps_fix_time = System.currentTimeMillis();

                if (!gotGpsFix) {
                    showGpsFound(true);
                }

                if (state == State.RUNNING) {
                    seq_nr_gpsfix++;
                    GpsData.GpsReading gps_location = prepareGpsProtobuf(location);
                    try {
                        gps_location.writeDelimitedTo(Gps_BOS);
                    } catch (IOException e) {
                        Log.e("Gps_BOS exception", e.toString());
                    }
                }

                onScreenVerbose.put("seq_nr_gpsfix", Integer.toString(seq_nr_gpsfix));
                onScreenVerbose.put("GPS FIX: ", ++gpsFixCounter + ": " + location.toString());
                tv_bottomView.setText(onScreenVerbose.toString().replaceAll(", ", "\n"));
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {}

            public void onProviderEnabled(String provider) {}

            public void onProviderDisabled(String provider) {}
        };
        return locationListener;
    }

    private PhoneStateListener getPhoneStateListener() {
        final String sCellInfo = "Cell Info #";
        final String sDataActivity = "Data Activity";
        final String sServiceState = "Service State";
        final String sCallState = "Call State";
        final String sCellLocation = "Cell Location";
        final String sCallForwarding = "Call Forwarding";
        final String sMessageWaiting = "Message Waiting";

        phoneStateHashMap = new HashMap<>();

        final PhoneStateListener phone = new PhoneStateListener() {
            @Override
            public void onCellInfoChanged(List<CellInfo> cellInfo) {
                int i;
                super.onCellInfoChanged(cellInfo);
                if (cellInfo != null) {
                    for (i = 0; i < cellInfo.size(); i++);
                    phoneStateHashMap.put(sCellInfo,"" + i);
                } else {
                    phoneStateHashMap.put(sCellInfo,"none");
                }
            }
            @Override
            public void onDataActivity(int direction) {
                super.onDataActivity(direction);
                switch (direction) {
                    case TelephonyManager.DATA_ACTIVITY_NONE:
                        phoneStateHashMap.put(sDataActivity,"none");
                        break;
                    case TelephonyManager.DATA_ACTIVITY_IN:
                        phoneStateHashMap.put(sDataActivity, "incoming");
                        break;
                    case TelephonyManager.DATA_ACTIVITY_OUT:
                        phoneStateHashMap.put(sDataActivity, "outgoing");
                        break;
                    case TelephonyManager.DATA_ACTIVITY_INOUT:
                        phoneStateHashMap.put(sDataActivity, "in/out");
                        break;
                    case TelephonyManager.DATA_ACTIVITY_DORMANT:
                        phoneStateHashMap.put(sDataActivity, "dormant");
                        break;
                    default:
                        break;
                }
            }
            @Override
            public void onServiceStateChanged(ServiceState serviceState) {
                super.onServiceStateChanged(serviceState);
                switch (serviceState.getState()) {
                    case ServiceState.STATE_IN_SERVICE:
                        phoneStateHashMap.put(sServiceState, "in Service");
                        break;
                    case ServiceState.STATE_OUT_OF_SERVICE:
                        phoneStateHashMap.put(sServiceState, "out of Service");
                        break;
                    case ServiceState.STATE_EMERGENCY_ONLY:
                        phoneStateHashMap.put(sServiceState, "emergency only");
                        break;
                    case ServiceState.STATE_POWER_OFF:
                        phoneStateHashMap.put(sServiceState, "powered off");
                        break;
                }
            }
            @Override
            public void onCallStateChanged(int callState, String incomingNumber) {
                super.onCallStateChanged(callState, incomingNumber);
                switch (callState) {
                    case TelephonyManager.CALL_STATE_IDLE:
                        phoneStateHashMap.put(sCallState,"idle");
                        break;
                    case TelephonyManager.CALL_STATE_RINGING:
                        phoneStateHashMap.put(sCallState, "ringing");
                        break;
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                        phoneStateHashMap.put(sCallState, "idle");
                        break;
                    default:
                        break;
                }
            }
            @Override
            public void onCellLocationChanged(CellLocation location) {
                super.onCellLocationChanged(location);
                phoneStateHashMap.put(sCellLocation, location.toString());
            }
            @Override
            public void onCallForwardingIndicatorChanged(boolean cfi) {
                super.onCallForwardingIndicatorChanged(cfi);
                if (cfi)
                    phoneStateHashMap.put(sCallForwarding, "ON");
                else
                    phoneStateHashMap.put(sCallForwarding, "OFF");
            }
            @Override
            public void onMessageWaitingIndicatorChanged(boolean mwi) {
                super.onMessageWaitingIndicatorChanged(mwi);
                if (mwi)
                    phoneStateHashMap.put(sMessageWaiting, "TRUE");
                else
                    phoneStateHashMap.put(sMessageWaiting, "FALSE");
            }
            @Override
            public void onSignalStrengthsChanged(SignalStrength _signalStrength )
            {
                signalStrength = _signalStrength;
                super.onSignalStrengthsChanged(signalStrength);
                String sSignal = signalStrength.toString();

                String[] parts = sSignal.split(" ");

                /*
                TextView tv_phoneState = (TextView) findViewById(R.id.phone_state);
                tv_phoneState.setText("\n\n" + "parts[8]="+ parts[8] + ", parts[9]="+ parts[9] + ", rssi=" + (Integer.parseInt(parts[8])*2-113)
                                        + "\n" + sSignal.toString());
                */
                getSignalStrength();
            }
        };
        TextView tv_phoneState = (TextView) findViewById(R.id.phone_state);
        tv_phoneState.setText(phoneStateHashMap.toString());
        return phone;
    }

    private void getSignalStrength()
    {
        try
        {
            Method[] methods = android.telephony.SignalStrength.class.getMethods();

            for (Method mthd : methods)
            {   //Log.i("LTE_TAG", "Method Name: " + mthd.getName() );
                /*if (mthd.getName().equals("getLteSignalStrength"))
                {
                    int LTEsignalStrength = (Integer) mthd.invoke(signalStrength, new Object[] {});
                    Log.i("LTE_TAG", "signalStrength = " + (LTEsignalStrength*2-113));
                    //return;
                }*/
                if (mthd.getName().equals("getLteRsrp"))
                {
                    int LTErsrp = (Integer) mthd.invoke(signalStrength, new Object[] {});
                    lteDbm = LTErsrp;
                    currentLte_time = System.currentTimeMillis();
                    //Log.i("LTE_TAG", "LteRsrp = " + lteDbm + ", time = " + (currentLte_time - prevLte_time));

                    prevLte_time = currentLte_time;
                    //return;
                }
                 else if (mthd.getName().equals("getLteAsuLevel"))
                {
                    int LteAsuLevel = (Integer) mthd.invoke(signalStrength, new Object[] {});
                    lteAsu = LteAsuLevel;
                    //Log.i("LTE_TAG", "LteAsuLevel = " + lteAsu);
                }

                else if (mthd.getName().equals("getLteLevel"))
                {
                    int LteLevel = (Integer) mthd.invoke(signalStrength, new Object[] {});
                    lteLevel = LteLevel;
                    //Log.i("LTE_TAG", "LteLevel = " + lteLevel);
                }
                 // CDMA
                else if (mthd.getName().equals("getCdmaDbm"))
                {
                    int CdmaDbm = (Integer) mthd.invoke(signalStrength, new Object[] {});
                    cdmaDbm = CdmaDbm;
                    //Log.i("LTE_TAG", "CdmaDbm = " + cdmaDbm);
                }
                else if (mthd.getName().equals("getCdmaAsuLevel"))
                {
                    int CdmaAsuLevel = (Integer) mthd.invoke(signalStrength, new Object[] {});
                    cdmaAsu = CdmaAsuLevel;
                    //Log.i("LTE_TAG", "CdmaAsuLevel = " + cdmaAsu);
                }
                else if (mthd.getName().equals("getCdmaEcio"))
                {
                    int CdmaEcio = (Integer) mthd.invoke(signalStrength, new Object[] {});
                    cdmaEcio = CdmaEcio;
                    //Log.i("LTE_TAG", "CdmaEcio = " + cdmaEcio);
                }
                else if (mthd.getName().equals("getCdmaLevel"))
                {
                    int CdmaLevel = (Integer) mthd.invoke(signalStrength, new Object[] {});
                    cdmaLevel = CdmaLevel;
                    //Log.i("LTE_TAG", "CdmaLevel = " + cdmaLevel);
                }
                 // EVDO
                else if (mthd.getName().equals("getEvdoDbm"))
                {
                    int EvdoDbm = (Integer) mthd.invoke(signalStrength, new Object[] {});
                    evdoDbm = EvdoDbm;
                    //Log.i("LTE_TAG", "EvdoDbm = " + evdoDbm);
                }
                else if (mthd.getName().equals("getEvdoAsuLevel"))
                {
                    int EvdoAsuLevel = (Integer) mthd.invoke(signalStrength, new Object[] {});
                    evdoAsu = EvdoAsuLevel;
                    //Log.i("LTE_TAG", "EdmaAsuLevel = " + evdoAsu);
                }
                else if (mthd.getName().equals("getEvdoEcio"))
                {
                    int EvdoEcio = (Integer) mthd.invoke(signalStrength, new Object[] {});
                    evdoEcio = EvdoEcio;
                    //Log.i("LTE_TAG", "EvdoEcio = " + evdoEcio);
                }
                else if (mthd.getName().equals("getEvdoLevel"))
                {
                    int EvdoLevel = (Integer) mthd.invoke(signalStrength, new Object[] {});
                    evdoLevel = EvdoLevel;
                    //Log.i("LTE_TAG", "EvdoLevel = " + evdoLevel);
                }
                else if (mthd.getName().equals("getEvdoSnr"))
                {
                    int EvdoSnr = (Integer) mthd.invoke(signalStrength, new Object[] {});
                    evdoSnr = EvdoSnr;
                    //Log.i("LTE_TAG", "EvdoSnr = " + evdoSnr);
                }
                else if (mthd.getName().equals("getLevel"))
                {
                    int Level = (Integer) mthd.invoke(signalStrength, new Object[] {});
                    s_Level = Level;
                    //Log.i("LTE_TAG", "Level = " + s_Level);
                }
                else if (mthd.getName().equals("getDbm"))
                {
                    int Dbm = (Integer) mthd.invoke(signalStrength, new Object[] {});
                    s_Dbm = Dbm;
                    //Log.i("LTE_TAG", "Dbm = " + s_Dbm);
                }

            }
        }
        catch (Exception e)
        {
            Log.e("LTE_TAG", "Exception: " + e.toString());
        }
    }

    private void listenPhoneState(TelephonyManager _tm) {
        _tm.listen(getPhoneStateListener(),PhoneStateListener.LISTEN_CALL_FORWARDING_INDICATOR |
                PhoneStateListener.LISTEN_CALL_STATE |
                PhoneStateListener.LISTEN_CELL_INFO |
                PhoneStateListener.LISTEN_CELL_LOCATION |
                PhoneStateListener.LISTEN_DATA_ACTIVITY |
                PhoneStateListener.LISTEN_DATA_CONNECTION_STATE |
                PhoneStateListener.LISTEN_MESSAGE_WAITING_INDICATOR |
                PhoneStateListener.LISTEN_SERVICE_STATE |
                PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
    }

    private void countMessagesInOutputFiles() {
        int dot_messages = 0;
        int sensor_messages = 0;
        int wifi_messages = 0;
        int cell_messages = 0;
        int gps_messages = 0;
        int metadata_messages = 0;
        MetaData.Metadata meta_data = null;

        FileInputStream WiFi_FIS = null, Sensor_FIS = null, Cell_FIS = null,
                        Dot_FIS = null, Gps_FIS = null, Metadata_FIS = null;

        try {
            WiFi_FIS = new FileInputStream(WiFi_output_file);
            Sensor_FIS = new FileInputStream (Sensors_output_file);
            Cell_FIS = new FileInputStream(Cellular_output_file);
            Dot_FIS = new FileInputStream(Dots_output_file);
            Gps_FIS = new FileInputStream(Gps_output_file);
            Metadata_FIS = new FileInputStream(Metadata_output_file);

        } catch (IOException e) {
            Log.e("FIS: ", e.toString());
        }

        AlertDialog.Builder indicate_counting = new AlertDialog.Builder(this);
        indicate_counting.setMessage("Counting the output").show();

        try {
            while (WifiData.WiFiReading.parseDelimitedFrom(WiFi_FIS) != null) {
                wifi_messages++;
            }
            WiFi_FIS.close();

            while (SensorData.SensorReading.parseDelimitedFrom(Sensor_FIS) != null) {
                sensor_messages++;
            }
            Sensor_FIS.close();

            while (CellularData.CellularReading.parseDelimitedFrom(Cell_FIS) != null) {
                cell_messages++;
            }
            Cell_FIS.close();

            while (DotData.DotReading.parseDelimitedFrom(Dot_FIS) != null) {
                dot_messages++;
            }
            Dot_FIS.close();

            while (GpsData.GpsReading.parseDelimitedFrom(Gps_FIS) != null) {
                gps_messages++;
            }
            Gps_FIS.close();

            meta_data = MetaData.Metadata.parseFrom(Metadata_FIS);
            if (meta_data != null) {
                metadata_messages++;
            }
            Metadata_FIS.close();

        } catch (IOException e) {
            Log.e("Read_FIS", e.toString());
        }

        //indicate_counting.create().dismiss();

        AlertDialog.Builder message_count_dialog_builder = new AlertDialog.Builder(this);

        message_count_dialog_builder.setTitle("Summary:");
        message_count_dialog_builder.setMessage(
                           "WiFi_Messages = " + Integer.toString(wifi_messages) +
                        "\n Sensor_Messages = " + Integer.toString(sensor_messages) +
                        "\n Cellular_Messages = " + Integer.toString(cell_messages) +
                        "\n Dot_Messages = " + Integer.toString(dot_messages) +
                        "\n Gps_Messages = " + Integer.toString(gps_messages) +
                        "\n Metadata_Message = " + Integer.toString(metadata_messages) +
                        "\n \n" +
                        "\n Metadata: " + meta_data.toString()

                )
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                        }
                                    });
        message_count_dialog_builder.show();

    }
}

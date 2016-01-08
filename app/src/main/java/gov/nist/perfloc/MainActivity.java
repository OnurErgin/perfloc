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
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.MediaScannerConnection;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
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
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends Activity {

    Vibrator v;
    private final Handler main_handler = new Handler();
    static AudioManager am;

    // View related definitions
    ExpandableListView expListView;
    ToggleButton StartStopButton;
    static TextView numAPs,
            WiFiScanTime,
            tv_dotcounter,
            tv_sensor_event_counter,
            tv_timer_sec,
            tv_bottomView;

    // Expandable List Adapter related definitions
    ExpandableListAdapter expListAdapter;
    List<String> listDataHeader;
    HashMap<String, List<String>> listDataChild;
    List<String> WiFiList_text,
            CellList_text,
            SensorList_text;


    // WiFi Scanning related definitions
    WifiManager wifi;
    WifiScanReceiver wifiReceiver;
    long time_prev, time_current;

    // Cell Tower and Telephony related definitions
    TelephonyManager telephony_manager;

    // Location Services: GPS
    LocationManager locationManager;

    // Sensor related definitions
     SensorHandler rbs;

    HashMap<Sensor, float[]> sensorHashMap; // 112
    HashMap<Sensor, Long> sensorLastTimestampHashMap; // 112
    HashMap<Sensor, Long> sensorCurrentTimestampHashMap; // 112

    private int SENSOR_DELAY = SensorManager.SENSOR_DELAY_FASTEST;

    volatile int dotCounter = 0;


    // Verbose info
    HashMap<String, String> onScreenVerbose;

    volatile int numberOfAPs=0,
                WiFiScanDuration=0,
                numberOfCellTowers=0,
                numberOfSensors=0;

    /*String  WiFi_output_file     = getString(R.string.wifi_file_name),     // "_WiFi",
            Sensors_output_file  = getString(R.string.sensors_file_name),   //"_Sensors",
            Dots_output_file     = getString(R.string.dots_file_name),      //"_Dots",
            Cellular_output_file = getString(R.string.cellular_file_name),  //"_Cellular",
            Metadata_output_file = getString(R.string.metadata_file_name);  //"_Metadata";
*/
    String  WiFi_output_file     = "_WiFi",
            Sensors_output_file  = "_Sensors",
            Dots_output_file     = "_Dots",
            Cellular_output_file = "_Cellular",
            Metadata_output_file = "_Metadata";

    String device_identifier = Build.MANUFACTURER + "_" + Build.MODEL + "_" + Build.BRAND;

    File dirname = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    String protocol_buffer_file_extension = "pbs"; // (p)rotocol(b)uffer(s)erialized
    String current_file_prefix = "1";
    List<Uri> indexed_URIs;

    BufferedOutputStream WiFi_BOS,
                        Sensor_BOS,
                        Dots_BOS,
                        Cellular_BOS,
                        Metadata_BOS;


    // Protocol Buffer ArrayLists
    List<DotData.DotReading> list_dot_readings;
    List<WifiData.WiFiReading> list_wifi_readings;
    List<CellularData.CellularReading> list_cellular_readings;
    List<SensorData.SensorReading> list_sensor_readings;

    int seq_nr_dot = 0;
    int seq_nr_wifi = 0;
    int seq_nr_cellular = 0;
    int seq_nr_sensorevent = 0;
    int seq_nr_gpsfix = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyWakelockTag");
        wakeLock.acquire();
        Log.v("WakeLock (isHeld?): ", wakeLock.isHeld() + ".");

        onScreenVerbose = new HashMap<>();

        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        LocationListener locationListener = getLocationListener();
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);


        list_cellular_readings = new ArrayList<>();
        list_dot_readings = new ArrayList<>();
        list_wifi_readings = new ArrayList<>();
        list_sensor_readings = new ArrayList<>();

        createOutputFiles(current_file_prefix);

        v = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);

        StartStopButton = (ToggleButton) findViewById(R.id.toggleButton);

        numAPs = (TextView) findViewById(R.id.numAPs);
        WiFiScanTime = (TextView) findViewById(R.id.WiFiScanTime);
        tv_dotcounter = (TextView) findViewById(R.id.dotCounter);
        tv_sensor_event_counter = (TextView) findViewById(R.id.sensor_event_counter);
            //tv_sensor_event_counter.setText(tv_sensor_event_counter + "");
        findViewById(R.id.title_sensor_event_counter);
        tv_timer_sec = (TextView) findViewById(R.id.timer_sec);
        tv_bottomView = (TextView) findViewById(R.id.bottomView);

        expListView = (ExpandableListView) findViewById(R.id.expandableListView);

        WiFiList_text = new ArrayList<>();

        wifi=(WifiManager)getSystemService(Context.WIFI_SERVICE);
        wifiReceiver = new WifiScanReceiver();


        telephony_manager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        //listenPhoneState(telephony_manager);

         rbs = new SensorHandler(main_handler, 100);
        final MeasurementTimer aT = new MeasurementTimer(main_handler);
        //pool = Executors.newFixedThreadPool(2); // Either use a thread pool, or create a separate thread each time and call Thread.start()
        //final Thread tRBS = new Thread(rbs);

        StartStopButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                //      Thread tRBS = new Thread(rbs);
                if (isChecked) {
                    registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
                    wifi.startScan();
                    time_current = System.currentTimeMillis();

                    Thread tRBS = new Thread(rbs);
                    tRBS.start();

                    Thread taT = new Thread(aT);
                    taT.start();

                    //          pool.execute(rbs);
                    Log.v("POOL: ", "Runnable executed.");
                } else {
                    unregisterReceiver(wifiReceiver);

                    rbs.terminate();
                    aT.terminate();
                    flushFiles();
                    Log.v("POOL: ", "Runnable stopped.");
                }
                v.vibrate(500);
            }

        });


        /*if ("lge".equalsIgnoreCase(Build.BRAND)||true) {
            MediaSession mediaSession = new MediaSession(this, "Perfloc MediaSession TAG");
                mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS);
                //mediaSession.setCallback(this);
                //Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
                Intent mediaButtonIntent = new Intent(getApplicationContext(), MediaButtonIntentReceiver.class);
                PendingIntent pIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, mediaButtonIntent, 0);
                mediaSession.setMediaButtonReceiver(pIntent);
                mediaSession.setActive(true);
        }
*/

        /*IntentFilter filter = new IntentFilter(Intent.ACTION_MEDIA_BUTTON);
        filter.setPriority(Integer.MAX_VALUE);
        registerReceiver(new MediaButtonIntentReceiver(), filter);
*/

        am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        //am.setMode(AudioManager.MODE_NORMAL);
        //am.setSpeakerphoneOn(true);
       // am.registerMediaButtonEventReceiver(MediaButtonIntentReceiver);
/*
        try {
            Log.e("AudioMAnagaer:", am.toString());
        } catch (NullPointerException e) {
            Log.e("Null pointer:", e.toString());
        }
        for (int i=0; i<1; i++)
            am.playSoundEffect(AudioManager.FX_KEYPRESS_INVALID);
*/

/*
        IntentFilter filter1 = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        filter1.setPriority(Integer.MAX_VALUE);
        registerReceiver(new HeadsetIntentReceiver(), filter1);
*/

        if(false ) {
            final MediaSession session = new MediaSession(getApplicationContext(), "TAG");
            PlaybackState state = new PlaybackState.Builder()
                    .setActions(
                            PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PLAY_PAUSE |
                                    PlaybackState.ACTION_PLAY_FROM_MEDIA_ID | PlaybackState.ACTION_PAUSE |
                                    PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                    .build();
            session.setPlaybackState(state);

            session.setCallback(new MediaSession.Callback() {
                @Override
                public boolean onMediaButtonEvent(final Intent mediaButtonIntent) {
                    Log.i("TAG", "GOT EVENT");
                    return super.onMediaButtonEvent(mediaButtonIntent);
                }
            });

            session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS |
                    MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);

            session.setActive(true);
        }

        // Initial view of the Expandable List View
        updateExpandableListView();
    }
    AudioManager.OnAudioFocusChangeListener afChangeListener =
            new AudioManager.OnAudioFocusChangeListener() {
                public void onAudioFocusChange(int focusChange) {
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        // Pause playback
                    } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                        // Resume playback
                    } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                        //am.unregisterMediaButtonEventReceiver(RemoteControlReceiver);
                        //am.abandonAudioFocus(afChangeListener);
                        // Stop playback
                    }
                }
            };
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
            Log.v("__MEDIA_BUTTON__", "DOWN Pressed.");
            am.playSoundEffect(AudioManager.FX_KEYPRESS_INVALID);
            markDot();
        }
        return true;
    }

    public void markDot() {
        dotCounter++;
        tv_dotcounter.setText(dotCounter + "");
        //list_dot_readings.add(prepareDotProtobuf(++seq_nr_dot));
        DotData.DotReading dot_reading = prepareDotProtobuf(++seq_nr_dot);

        try {
            dot_reading.writeDelimitedTo(Dots_BOS);
        } catch (IOException e) {
            Log.e("Dots_BOS exception:", e.toString());
        }

        onScreenVerbose.put("seq_nr_dot", Integer.toString(dot_reading.getDotNr()));
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

                am.playSoundEffect(AudioManager.FX_KEYPRESS_INVALID);

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

    private boolean createOutputFiles(String prefix){

        WiFi_output_file     = dirname + "/" + prefix + "_" + device_identifier + WiFi_output_file      + "." + protocol_buffer_file_extension;
        Sensors_output_file  = dirname + "/" + prefix + "_" + device_identifier + Sensors_output_file   + "." + protocol_buffer_file_extension;
        Dots_output_file     = dirname + "/" + prefix + "_" + device_identifier + Dots_output_file      + "." + protocol_buffer_file_extension;
        Cellular_output_file = dirname + "/" + prefix + "_" + device_identifier + Cellular_output_file  + "." + protocol_buffer_file_extension;
        Metadata_output_file = dirname + "/" + prefix + "_" + device_identifier + Metadata_output_file  + "." + protocol_buffer_file_extension;

        WiFi_output_file     = WiFi_output_file.replaceAll("\\s+","");
        Sensors_output_file  = Sensors_output_file.replaceAll("\\s+","");
        Dots_output_file     = Dots_output_file.replaceAll("\\s+","");
        Cellular_output_file = Cellular_output_file.replaceAll("\\s+","");
        Metadata_output_file = Metadata_output_file.replaceAll("\\s+","");

        try {
            WiFi_BOS = new BufferedOutputStream(new FileOutputStream(WiFi_output_file));
            Sensor_BOS = new BufferedOutputStream(new FileOutputStream(Sensors_output_file));
            Dots_BOS = new BufferedOutputStream(new FileOutputStream(Dots_output_file));
            Cellular_BOS = new BufferedOutputStream(new FileOutputStream(Cellular_output_file));
            Metadata_BOS = new BufferedOutputStream(new FileOutputStream(Metadata_output_file));
        } catch (IOException e) {
            Log.e("BOS_File", e.toString());
        }

        indexed_URIs = new ArrayList<>();

        return true;
    }

    private boolean unIndexFiles () {
        for (Uri uri : indexed_URIs) {
            getContentResolver().delete(uri, null, null);
        }
        indexed_URIs.clear();

        return true;
    }

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

        if (expListAdapter != null) {
            WiFiGroupExpanded = expListView.isGroupExpanded(0);
            CellGroupExpanded = expListView.isGroupExpanded(1);
            SensorGroupExpanded = expListView.isGroupExpanded(2);
            //int mListPosition = expListView.getVerticalScrollbarPosition();
        }

        prepareListData(); // Prepare the String Lists of headers and HashMaps of their children
        expListAdapter = new ExpandableListViewAdapter(getApplicationContext(), listDataHeader, listDataChild);
        expListView.setAdapter(expListAdapter);

        //expListView.setVerticalScrollbarPosition(mListPosition);
        if (WiFiGroupExpanded) expListView.expandGroup(0);
        if (CellGroupExpanded) expListView.expandGroup(1);
        if (SensorGroupExpanded) expListView.expandGroup(2);
    }

    private void prepareListData() {
        listDataHeader = new ArrayList<String>();
        listDataChild = new HashMap<String, List<String>>();

        // Adding headers:
        listDataHeader.add(numberOfAPs + " Access Points, delay: " + WiFiScanDuration + "ms");
        listDataHeader.add(numberOfCellTowers + " Cell Towers");
        listDataHeader.add(SensorList_text.size() + "/" + numberOfSensors + " Sensors");

        listDataChild.put(listDataHeader.get(0), WiFiList_text);
        listDataChild.put(listDataHeader.get(1), CellList_text);
        listDataChild.put(listDataHeader.get(2), SensorList_text);

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

    private void captureCellularScan() {
        // Get CellInfo and display

        /* Increment cellular scan counter */
        seq_nr_cellular = seq_nr_cellular + 1;

        /* Prepare protocol buffer and write into file */
        CellularData.CellularReading cellular_reading = prepareCellularProtobuf(telephony_manager.getAllCellInfo());

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

            /* Increment WiFi Scan Counter */
            seq_nr_wifi = seq_nr_wifi + 1;

            WifiData.WiFiReading wifi_reading = prepareWiFiProtobuf( wifi.getScanResults() );

            /* Write scan results into file */
            try {
                wifi_reading.writeDelimitedTo(WiFi_BOS);
            } catch (IOException e) {
                Log.e("WiFi_BOS exception:", e.toString());
            }
            //list_wifi_readings.add(wifi_reading);

            /* Re-scan WiFi */
            wifi.startScan();
            captureCellularScan();

            onScreenVerbose.put("seq_nr_wifi", Integer.toString(wifi_reading.getSequenceNr()));

            displayWiFiScan(wifi_reading.getWifiApList());


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
                return true;
            case R.id.flush_files:

                return flushFiles();
            case R.id.renew_files:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage("Are you sure?").setPositiveButton("Yes", renewDialogClickListener)
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
                AlertDialog.Builder prefix_dialog_builder = new AlertDialog.Builder(this);
                final EditText prefix_input = new EditText(this);
                prefix_input.setInputType(InputType.TYPE_NUMBER_VARIATION_NORMAL | InputType.TYPE_CLASS_NUMBER);
                prefix_dialog_builder .setView(prefix_input);

                prefix_dialog_builder .setMessage("Set file prefix// Current:" + current_file_prefix).setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String t_input = prefix_input.getText().toString();
                        if ( t_input.length() == 0 ) {
                            Toast.makeText(getApplicationContext(), "Error: empty input" , Toast.LENGTH_LONG).show();

                        } else {
                            current_file_prefix = t_input;
                            Toast.makeText(getApplicationContext(), "Set to: " + current_file_prefix,
                                    Toast.LENGTH_SHORT).show();

                            createOutputFiles(current_file_prefix);
                        }
                    }
                });
                prefix_dialog_builder .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        Toast.makeText(getApplicationContext(), "not changed.", Toast.LENGTH_SHORT).show();
                    }
                });

                prefix_dialog_builder.show();
                return true;
            case R.id.count_messages:
                countMessagesInOutputFiles();
                return true;
            case R.id.save_metadata:
                //ScenarioDefinition scenario_metadata = new ScenarioDefinition(Integer.parseInt(current_file_prefix),null,Metadata_BOS);
                ScenarioDefinition scenario_metadata = new ScenarioDefinition(0, rbs.sensorList, Metadata_BOS);
                //scenario_metadata.prepareMetadataProtoBuf(0, rbs.sensorList);
                //scenario_metadata.writeMetadataToFile(Metadata_BOS);
                scenario_metadata.prepare(0, rbs.sensorList, Metadata_BOS);

                File MetadataFile = new File(Metadata_output_file);

                MediaScannerConnection.scanFile(
                        getApplicationContext(),
                        new String[]{MetadataFile.getAbsolutePath()},
                        null,
                        new MediaScannerConnection.OnScanCompletedListener() {
                            @Override
                            public void onScanCompleted(String path, Uri uri) {
                                Log.v("Media Scanner", "file " + path + " was scanned successfully: " + uri);
                                indexed_URIs.add(uri);
                            }
                        }
                );

                /*File dotFile = new File(Dots_output_file);

                MediaScannerConnection.scanFile(
                        getApplicationContext(),
                        new String[]{wifiFile.getAbsolutePath(), sensorFile.getAbsolutePath(), dotFile.getAbsolutePath()},
                        null,
                        new MediaScannerConnection.OnScanCompletedListener() {
                            @Override
                            public void onScanCompleted(String path, Uri uri) {
                                Log.v("Media Scanner", "file " + path + " was scanned successfully: " + uri);
                                indexed_URIs.add(uri);
                            }
                        }
                );*/
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

                    unIndexFiles();
                    for(File file: dirname.listFiles()) file.delete();
                    createOutputFiles(current_file_prefix);
                    list_dot_readings.clear();
                    list_sensor_readings.clear();

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

    private boolean flushFiles () {
        unIndexFiles();

        //writeProtobufsToFile();

        try {
            WiFi_BOS.flush();
            Sensor_BOS.flush();
            Dots_BOS.flush();
            Cellular_BOS.flush();
        } catch (IOException e) {
            Log.v("Flush BOS:",e.toString());
            return false;
        }

        //sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(outFile)));
        File wifiFile = new File(WiFi_output_file);
        File sensorFile = new File(Sensors_output_file);
        File dotFile = new File(Dots_output_file);

        MediaScannerConnection.scanFile(
                getApplicationContext(),
                new String[]{wifiFile.getAbsolutePath(), sensorFile.getAbsolutePath(), dotFile.getAbsolutePath()},
                null,
                new MediaScannerConnection.OnScanCompletedListener() {
                    @Override
                    public void onScanCompleted(String path, Uri uri) {
                        Log.v("Media Scanner", "file " + path + " was scanned successfully: " + uri);
                        indexed_URIs.add(uri);
                    }
                }
        );
        return true;
    }

    private class SensorHandler implements Runnable {

        Handler mHandler;

        public List<Sensor> sensorList;

        private volatile boolean running;
        private volatile int measuringFrequency;
        SensorManager mSensorManager;
        volatile int flag = 0;

        float[] s_light;

        public SensorHandler(Handler _handler, int _measuringFrequency) {

            mHandler = _handler;
            measuringFrequency = _measuringFrequency;


            mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

            sensorList = getDefaultSensorList();

            //SensorList_text = new ArrayList<String>();
            // sensorHashMap = new HashMap<String, float[]>(); // 112
            sensorHashMap = new HashMap<Sensor, float[]>();
            sensorLastTimestampHashMap = new  HashMap<Sensor, Long>(); // 112
            sensorCurrentTimestampHashMap = new  HashMap<Sensor, Long>(); // 112

            for (Sensor _s : sensorList) {
                sensorHashMap.put(_s, null);
            }

            if (sensorList.size() != sensorHashMap.size())
                Log.e("DEADLY ERROR!!!!", "defaultSensorList.size() != sensorHashMap.size() : " + sensorList.size() + " != " + sensorHashMap.size() );

            //List<String> keyList = new ArrayList<String>(sensorHashMap.keySet()); //112
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

            List<Sensor> defaultSensorList = new ArrayList<>(); //112

            List<Sensor> nullSensorList = new ArrayList<>(); //112

            for (int i = 0; i < sensorList.size(); i++) {

                Sensor default_sensor = mSensorManager.getDefaultSensor(sensorList.get(i).getType()); // 112

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

        @Override
        public void run() {

            running = true;

            registerSensors(mSensorManager, sensorList);

            while (running) {

                List<Sensor> keyList = new ArrayList<>(sensorHashMap.keySet());

                SensorList_text = new ArrayList<String>();

                for (int i = 0; i < keyList.size();i++){
                    if (sensorLastTimestampHashMap.get(keyList.get(i)) != null )
                        SensorList_text.add(keyList.get(i) + ": " + Arrays.toString(sensorHashMap.get(keyList.get(i))) +
                                " in " + (sensorCurrentTimestampHashMap.get(keyList.get(i)) - sensorLastTimestampHashMap.get(keyList.get(i)))/1000000 + "msec" + ", isWakeup: " );
                }

                Collections.sort(SensorList_text, String.CASE_INSENSITIVE_ORDER);

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (s_light != null)
                            WiFiScanTime.setText(" Light: " + s_light[0]);
                        //prepareListData();
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
        }

        private void unRegisterSensors (SensorManager tSensorManager){
            mSensorManager.unregisterListener(mSensorListener);
        }

        private SensorEventListener mSensorListener = new SensorEventListener() {

            volatile long senor_event_counter = 0;
            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }

            @Override
            public void onSensorChanged(SensorEvent event) {
                senor_event_counter++;

                //list_sensor_readings.add(prepareSensorProtobuf(event));

                seq_nr_sensorevent = seq_nr_sensorevent + 1;

                SensorData.SensorReading sensor_event = prepareSensorProtobuf(event);
                try {
                    sensor_event.writeDelimitedTo(Sensor_BOS);
                } catch (IOException e) {
                    Log.e("Sensor_BOS exception:", e.toString());
                }

                // For displaying on the screen

                onScreenVerbose.put("seq_nr_sensorevent", Integer.toString(sensor_event.getSequenceNr()));

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        tv_sensor_event_counter.setText(senor_event_counter + "");
                    }
                });

                Sensor sensorHashKey = event.sensor;
                sensorHashMap.put(sensorHashKey, event.values);
                sensorLastTimestampHashMap.put(sensorHashKey,sensorCurrentTimestampHashMap.get(sensorHashKey));
                sensorCurrentTimestampHashMap.put(sensorHashKey, new Long(event.timestamp));

                if(event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
                    float[] time_x = {(float) Calendar.getInstance().get(Calendar.SECOND)};
                    sensorHashMap.put(sensorHashKey, time_x);
                }
                else if (event.sensor.getType() == Sensor.TYPE_LIGHT)
                    s_light = event.values;
            }
        };

        public void terminate() {
            unRegisterSensors(mSensorManager);
            running = false;
        }
    }

    private class MeasurementTimer implements Runnable {

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

    @Override
    public void onBackPressed() {
        // Ignore Back Button, not necessary
    }

    private LocationListener getLocationListener() {
        // Define a listener that responds to location updates
        Log.v("LocationListener: ", "Location Listener started.");
        Log.v("LocationListener: ", " Started with providers: " + locationManager.getAllProviders().toString());

        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                // Called when a new location is found by the network location provider.

                seq_nr_gpsfix++;

                onScreenVerbose.put("seq_nr_gpsfix", Integer.toString(seq_nr_gpsfix));
                onScreenVerbose.put("GPS FIX: ", location.toString());
                tv_bottomView.setText(onScreenVerbose.toString());
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {}

            public void onProviderEnabled(String provider) {}

            public void onProviderDisabled(String provider) {}
        };
        return locationListener;
    }

    private PhoneStateListener getPhoneStateListener() {
        final PhoneStateListener phone = new PhoneStateListener() {
            @Override
            public void onCellInfoChanged(List<CellInfo> cellInfo) {
                int i;
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
        return phone;
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

        FileInputStream WiFi_FIS = null, Sensor_FIS = null, Cell_FIS = null, Dot_FIS = null;

        try {
            WiFi_FIS = new FileInputStream(WiFi_output_file);
            Sensor_FIS = new FileInputStream (Sensors_output_file);
            Cell_FIS = new FileInputStream(Cellular_output_file);
            Dot_FIS = new FileInputStream(Dots_output_file);
        } catch (IOException e) {
            Log.e("FIS: ", e.toString());
        }

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

        } catch (IOException e) {
            Log.e("Read_FIS: ", e.toString());
        }

        AlertDialog.Builder message_count_dialog_builder = new AlertDialog.Builder(this);

        message_count_dialog_builder.setMessage(
                "WiFi_Messages = " + Integer.toString(wifi_messages) +
                        "\nSensor_Messages = " + Integer.toString(sensor_messages) +
                        "\nCellular_Messages = " + Integer.toString(cell_messages) +
                        "\nDot_Messages = " + Integer.toString(dot_messages)
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

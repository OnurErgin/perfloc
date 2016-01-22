package gov.nist.perfloc;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Created by moe on 1/5/2016.
 */
public class ScenarioDefinition {

    private String TAG_VERBOSE = this.getClass().getName();

    public String LICENSE = "GPL-Test-License";
    public String EXPERIMENT_DESC = "Indoor Scenario";
    public String ENVIRONMENT_DESC = "NIST-222";
    public String INTERFERENCE_DESC = "";
    public String ADDITIONAL_INFO = "";

    private Sensor pressureSensor;

    public int pressure_sensor_sample_size = 5,
                pressure_sensor_max_sample_size = 100;

    float[] pressure_values;
    public int pressure_value_count = 0;
    public int average_pressure = 0;

    public MetaData.Metadata metadata;

    public ScenarioDefinition (int _id, List<Sensor> sensors, SensorManager mSensorManager, BufferedOutputStream Metadata_BOS)  {
        Log.i(TAG_VERBOSE, "object created " + _id +
                            sensors.toString().replaceAll("\\}, ", "\\}\n") + " " +
                            mSensorManager.toString() + " " +
                            Metadata_BOS.toString());

        pressureSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);

        if (pressureSensor != null) {

            pressure_values = new float[pressure_sensor_sample_size];

            mSensorManager.registerListener(mSensorListener, pressureSensor, SensorManager.SENSOR_DELAY_FASTEST);

                // Wait until enough observations are made and unregister the listener
                while (pressure_value_count <= pressure_sensor_max_sample_size) {
                    try {
                        Thread.sleep(10);
                    } catch (Exception e) {
                        Log.wtf(TAG_VERBOSE, "sleep problem");
                    }

                }

            mSensorManager.unregisterListener(mSensorListener);

            Log.v(TAG_VERBOSE,
                    "Average Pressure: " + getPressureValueAvg() + " from " + Arrays.toString(pressure_values)
            );
        } else {
            pressure_sensor_max_sample_size = 0; // You no samples can be taken.
            Log.w(TAG_VERBOSE,"The pressure sensor is NULL.");
        }

    }

    public float getPressureValueAvg () {
        if (pressureSensor == null || pressure_value_count == 0)
            return 0f;
        else {
            float sum = 0;
            for (float f : pressure_values)
                sum += f;
            return sum / (float) pressure_values.length;
        }
    }
    public void prepare (int _id, List<Sensor> sensors, BufferedOutputStream Metadata_BOS) {
        metadata = prepareMetadataProtoBuf(_id, sensors);
        Log.i(TAG_VERBOSE, metadata.toString());
        writeMetadataToFile(Metadata_BOS);
    }

    public MetaData.Metadata prepareMetadataProtoBuf (int _id, List<Sensor> sensors) {
        MetaData.Metadata.Builder metadata = MetaData.Metadata.newBuilder();

        metadata.setMeasurementId(_id);
        metadata.setTimeCreated(System.currentTimeMillis());
        metadata.setLicense(LICENSE);
        metadata.setExperimentDuration(0);
        metadata.setExperimentDescription(EXPERIMENT_DESC);
        metadata.setEnvironmentDescription(ENVIRONMENT_DESC);
        metadata.setInterferenceDescription(INTERFERENCE_DESC);
        metadata.setAdditionalInfo(ADDITIONAL_INFO);
        metadata.setInitialAveragePressure(getPressureValueAvg());

        MetaData.Metadata.DeviceDescription.Builder device_description = MetaData.Metadata.DeviceDescription.newBuilder();
        device_description.setBoard(Build.BOARD)
                .setBrand(Build.BRAND)
                .setDevice(Build.DEVICE)
                .setDisplay(Build.DISPLAY)
                .setFingerprint(Build.FINGERPRINT)
                .setHardware(Build.HARDWARE)
                .setId(Build.ID)
                .setManufacturer(Build.MANUFACTURER)
                .setModel(Build.MODEL)
                .setProduct(Build.PRODUCT)
                .setSerial(Build.SERIAL)
                .setRadioVersion(Build.getRadioVersion());
        metadata.setDevice(device_description);

        for (Sensor s : sensors) {
            MetaData.Metadata.Sensor.Builder sensor = MetaData.Metadata.Sensor.newBuilder();
            sensor.setType(s.getType())
                    .setStringType(s.getStringType())
                    .setName(s.getName())
                    .setIsWakeupSensor(s.isWakeUpSensor())
                    .setVendor(s.getVendor())
                    .setVersion(s.getVersion())
                    .setResolution(s.getResolution())
                    .setReportingMode(s.getReportingMode())
                    .setPower(s.getPower())
                    .setFifoMaxEventCount(s.getFifoMaxEventCount())
                    .setFifoReservedEventCount(s.getFifoReservedEventCount())
                    .setMaximumRange(s.getMaximumRange())
                    .setMinDelay(s.getMinDelay());

            metadata.addSensor(sensor);
        }

        return metadata.build();
    }

    public void writeMetadataToFile (BufferedOutputStream Metadata_BOS) {

        try {
            metadata.writeTo(Metadata_BOS);
            Metadata_BOS.flush();

            Log.v(TAG_VERBOSE, "Put into file");
        } catch (IOException e) {
            Log.e(TAG_VERBOSE + "Exception: ", e.toString());
        }
    }

    private SensorEventListener mSensorListener = new SensorEventListener() {


        @Override
        public void onAccuracyChanged(Sensor sensor, final int accuracy) {

        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            Log.v(TAG_VERBOSE, "Event received from: " + event.sensor.getStringType());

            Log.v(TAG_VERBOSE,event.toString());

            if (event.sensor.getType() == Sensor.TYPE_PRESSURE) {
                pressure_values[(pressure_value_count++) % pressure_sensor_sample_size] = event.values[0];

                Log.v(TAG_VERBOSE, event.sensor.getStringType() + "_values[" +
                        (pressure_value_count -1) % pressure_sensor_sample_size + "] = " +
                        pressure_values[(pressure_value_count -1)%pressure_sensor_sample_size] +
                        ", pressure_value_count=" + pressure_value_count);

            }
        }

    };

}

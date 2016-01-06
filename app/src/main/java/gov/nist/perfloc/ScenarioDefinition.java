package gov.nist.perfloc;

import android.hardware.Sensor;
import android.os.Build;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Created by moe on 1/5/2016.
 */
public class ScenarioDefinition {

    private String TAG_VERBOSE = "Metadata: ";

    public String LICENSE = "GPL-Test-License";
    public String EXPERIMENT_DESC = "Indoor Scenario";
    public String ENVIRONMENT_DESC = "NIST-222";
    public String INTERFERENCE_DESC = "";
    public String ADDITIONAL_INFO = "";

    public MetaData.Metadata metadata;

    public ScenarioDefinition (int _id, List<Sensor> sensors, BufferedOutputStream Metadata_BOS) {
        Log.v (TAG_VERBOSE, "object created" + _id + sensors.toString().replaceAll("\\}, ","\\}\n") + Metadata_BOS.toString());
    }

    public void prepare (int _id, List<Sensor> sensors, BufferedOutputStream Metadata_BOS) {
        metadata = prepareMetadataProtoBuf(_id, sensors);
        Log.v(TAG_VERBOSE, metadata.toString());
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
}

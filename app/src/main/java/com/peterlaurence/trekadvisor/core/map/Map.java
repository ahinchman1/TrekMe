package com.peterlaurence.trekadvisor.core.map;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;

import com.peterlaurence.trekadvisor.core.map.gson.MapGson;
import com.peterlaurence.trekadvisor.core.projection.Projection;
import com.qozix.tileview.graphics.BitmapProvider;

import java.io.File;
import java.util.List;

/**
 * A {@code Map} contains all the information that defines a map. That includes :
 * <ul>
 * <li>The name that will appear in the map choice list </li>
 * <li>The directory that contains image data and configuration file </li>
 * <li>The calibration method along with calibration points </li>
 * <li>Points of interest </li>
 * ...
 * </ul>
 *
 * @author peterLaurence
 */
public class Map implements Parcelable {

    /* The configuration file of the map, named map.json */
    private final File mConfigFile;

    /* The directory of the map, which contains the configuration file */
    private final File mDirectory;

    private final Bitmap mImage;

    private BitmapProvider mBitmapProvider;

    private MapBounds mMapBounds;

    /* The Java Object corresponding to the json configuration file */
    private MapGson mMapGson;

    private CalibrationStatus mCalibrationStatus = CalibrationStatus.NONE;

    private static final String UNDEFINED = "undefined";

    public enum CalibrationStatus {
        OK, NONE, ERROR
    }

    public Map(MapGson mapGson, File jsonFile) {
        mMapGson = mapGson;
        mConfigFile = jsonFile;
        mDirectory = jsonFile.getParentFile();
        mImage = getBitmapFromFile(new File(jsonFile.getParent(), mapGson.thumbnail));
    }

    /**
     * Retrieve a {@link Bitmap} given its relative path from the external storage directory.
     * Usually, this directory is "/storage/emulated/0".
     * For example : if {@code imagePath} is "trekavisor/maps/paris/paris.jpg", the full path of
     * the file that will be retrieved is "/storage/emulated/0/trekavisor/maps/paris/paris.jpg".
     * <p/>
     * NB : a nice way to generate thumbnails of maps is by using vipsthumbnail. Example :
     * vipsthumbnail big-image.jpg --interpolator bicubic --size 256x256 --crop
     *
     * @param imagePath the relative path of the image
     * @return the {@link Bitmap} object
     */
    public static Bitmap getBitmapFromPath(String imagePath) {
        File sd = Environment.getExternalStorageDirectory();
        System.out.println(sd.getAbsolutePath());
        File image = new File(sd, imagePath);
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        return BitmapFactory.decodeFile(image.getAbsolutePath(), bmOptions);
    }

    public static Bitmap getBitmapFromFile(File file) {
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        return BitmapFactory.decodeFile(file.getAbsolutePath(), bmOptions);
    }

    public Bitmap getDownSample() {
        // TODO : parametrize that
        File imageFile = new File(mDirectory, "down_sample.jpg");
        try {
            return BitmapFactory.decodeFile(imageFile.getPath());
        } catch (OutOfMemoryError | Exception e) {
            // maybe log here
        }
        return null;
    }

    public @Nullable String getProjectionName() {
        if (mMapGson.calibration.projection != null) {
            return mMapGson.calibration.projection.getName();
        }
        return null;
    }

    public Projection getProjection() {
        return mMapGson.calibration.projection;
    }

    public void setProjection(Projection projection) {
        mMapGson.calibration.projection = projection;
    }

    public void setBitmapProvider(BitmapProvider bitmapProvider) {
        mBitmapProvider = bitmapProvider;
    }

    public BitmapProvider getBitmapProvider() {
        return mBitmapProvider;
    }

    public void clearCalibrationPoints() {
        mMapGson.calibration.calibration_points.clear();
    }

    /**
     * A MapBounds object holds the projected coordinates of :
     * <ul>
     * <li>The top-left corner of the map : (projectionX0, projectionY0)</li>
     * <li>The bottom-right corner of the map : (projectionX1, projectionY1)</li>
     * </ul>
     */
    public static class MapBounds {
        public double projectionX0;
        public double projectionY0;
        public double projectionX1;
        public double projectionY1;

        public MapBounds(double x0, double y0, double x1, double y1) {
            projectionX0 = x0;
            projectionY0 = y0;
            projectionX1 = x1;
            projectionY1 = y1;
        }
    }

    public
    @Nullable
    MapBounds getMapBounds() {
        return mMapBounds;
    }

    public void setMapBounds(MapBounds mapBounds) {
        mMapBounds = mapBounds;
    }

    public void calibrate() {
        /* Init the projection */
        if (mMapGson.calibration.projection != null) {
            mMapGson.calibration.projection.init();
        }

        switch (getCalibrationMethod()) {
            case SIMPLE_2_POINTS:
                List<MapGson.Calibration.CalibrationPoint> calibrationPoints = mMapGson.calibration.calibration_points;
                if (calibrationPoints != null && calibrationPoints.size() >= 2) {
                    setMapBounds(MapCalibrator.simple2PointsCalibration(calibrationPoints.get(0),
                            calibrationPoints.get(1)));
                }
                mCalibrationStatus = CalibrationStatus.OK;
                break;
            default:
                mCalibrationStatus = CalibrationStatus.ERROR;
        }
    }

    public File getDirectory() {
        return mDirectory;
    }

    public String getName() {
        return mMapGson.name;
    }

    public void setName(String newName) {
        mMapGson.name = newName;
    }

    public String getDescription() {
        return "";
    }

    public CalibrationStatus getCalibrationStatus() {
        return mCalibrationStatus;
    }

    public Bitmap getImage() {
        return mImage;
    }

    public List<MapGson.Level> getLevelList() {
        return mMapGson.levels;
    }

    public MapLoader.CALIBRATION_METHOD getCalibrationMethod() {
        return MapLoader.CALIBRATION_METHOD.fromCalibrationName(
                mMapGson.calibration.calibration_method);
    }

    public String getOrigin() {
        try {
            return mMapGson.provider.generated_by;
        } catch (NullPointerException e) {
            return UNDEFINED;
        }
    }

    /**
     * Get the number of calibration that should be defined.
     */
    public int getCalibrationPointsNumber() {
        switch (getCalibrationMethod()) {
            case SIMPLE_2_POINTS:
                return 2;
            default:
                return 0;
        }
    }

    /**
     * Get the calibration points defined.
     */
    public List<MapGson.Calibration.CalibrationPoint> getCalibrationPoints() {
        return mMapGson.calibration.calibration_points;
    }

    /**
     * Set the calibration points.
     */
    public void setCalibrationPoints(List<MapGson.Calibration.CalibrationPoint> calibrationPoints) {
        mMapGson.calibration.calibration_points = calibrationPoints;
    }

    public String getImageExtension() {
        return mMapGson.provider.image_extension;
    }

    public int getWidthPx() {
        return mMapGson.size.x;
    }

    public int getHeightPx() {
        return mMapGson.size.y;
    }

    public final MapGson getMapGson() {
        return mMapGson;
    }

    public final File getConfigFile() {
        return mConfigFile;
    }

    protected Map(Parcel in) {
        mConfigFile = new File(in.readString());
        mDirectory = new File(in.readString());
        mImage = in.readParcelable(Bitmap.class.getClassLoader());
    }

    public static final Creator<Map> CREATOR = new Creator<Map>() {
        @Override
        public Map createFromParcel(Parcel in) {
            return new Map(in);
        }

        @Override
        public Map[] newArray(int size) {
            return new Map[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mConfigFile.getAbsolutePath());
        dest.writeString(mDirectory.getAbsolutePath());
        dest.writeParcelable(mImage, flags);
    }
}
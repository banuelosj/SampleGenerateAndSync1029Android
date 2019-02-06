package com.example.jose9489.mygenerategeodatabase1029;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.esri.android.map.FeatureLayer;
import com.esri.android.map.Layer;
import com.esri.android.map.MapView;
import com.esri.android.map.ags.ArcGISFeatureLayer;
import com.esri.android.map.ags.ArcGISLocalTiledLayer;
import com.esri.core.ags.FeatureServiceInfo;
import com.esri.core.geodatabase.Geodatabase;
import com.esri.core.geodatabase.GeodatabaseFeatureServiceTable;
import com.esri.core.geodatabase.GeodatabaseFeatureTable;
import com.esri.core.geodatabase.GeodatabaseFeatureTableEditErrors;
import com.esri.core.map.CallbackListener;
import com.esri.core.tasks.geodatabase.GenerateGeodatabaseParameters;
import com.esri.core.tasks.geodatabase.GeodatabaseStatusCallback;
import com.esri.core.tasks.geodatabase.GeodatabaseStatusInfo;
import com.esri.core.tasks.geodatabase.GeodatabaseSyncTask;
import com.esri.core.tasks.geodatabase.SyncGeodatabaseParameters;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.StringReader;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private String TAG = "MainActivity...";

    //all that is needed for loading local basemap
    private MapView mapView;
    private ArcGISLocalTiledLayer localTiledLayer;
    private File demoDataFile;
    private String offlineDataSDCardDirName;
    private String filename, tpkFileName;
    private String localGDBFilePath;
    private String pathToTPK;
    protected String OFFLINE_FILE_EXTENSION = ".geodatabase";

    //code for generating geodatabase
    String fLayerUrl;
    String fServiceUrl;
    private GeodatabaseFeatureServiceTable featureServiceTable;
    private FeatureLayer indoFeatureLayer;
    private GeodatabaseSyncTask gdbSyncTask;
    private ProgressDialog mProgressDialog;
    private static Context mContext;

    private Button generateButton, syncButton;

    //syncing
    SyncGeodatabaseParameters syncParams;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MainActivity.setContext(this);

        mapView = (MapView) findViewById(R.id.map);

        //request mobile permissions
        String[] reqPermission = new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE };
        int requestCode = 2;
        // For API level 23+ request permission at runtime
        if (ContextCompat.checkSelfPermission(MainActivity.this, reqPermission[0]) != PackageManager.PERMISSION_GRANTED) {
            // request permission
            ActivityCompat.requestPermissions(MainActivity.this, reqPermission, requestCode);
            Log.i(TAG,"requesting permission");
        }

        //get sdcard resource names
        demoDataFile = Environment.getExternalStorageDirectory();
        offlineDataSDCardDirName = this.getResources().getString(R.string.config_data_sdcard_offline_dir);
        filename = this.getResources().getString(R.string.config_geodatabase_name);
        tpkFileName = this.getResources().getString(R.string.config_local_basemap);

        localGDBFilePath = createGeodatabaseFilePath();
        pathToTPK = createTPKFilePath();

        loadLocalBasemap();

        //create service layer
        fServiceUrl = this.getResources().getString(R.string.featureservice_url);
        featureServiceTable = new GeodatabaseFeatureServiceTable(fServiceUrl,1);

        //need to initialize the geodatabasefeatueservicetable before adding the feature layer on the mapview
        featureServiceTable.initialize(new CallbackListener<GeodatabaseFeatureServiceTable.Status>() {
            @Override
            public void onCallback(GeodatabaseFeatureServiceTable.Status status) {
                indoFeatureLayer = new FeatureLayer(featureServiceTable);
                mapView.addLayer(indoFeatureLayer);
            }

            @Override
            public void onError(Throwable throwable) {
                showToast("Error initializing FeatureServiceTable");
                Log.i(TAG,"failed to initialize feature service table");
            }
        });//end initialize

        //generateButton
        generateButton = findViewById(R.id.genGeodatabaseButton);
        generateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //code after the button press
                Log.i(TAG,"just clicked the button...");
                downloadData(fServiceUrl);
            }
        });//onclick listener

        //remove button
        syncButton = findViewById(R.id.syncButton);
        syncButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG,"sync button has been pressed");
                syncGeodatabase();
            }
        });

        mProgressDialog = new ProgressDialog(MainActivity.this);
        mProgressDialog.setTitle("Generating Geodatabase...");

    }//end onCreate

    private void loadLocalBasemap(){
        try{
            localTiledLayer = new ArcGISLocalTiledLayer(pathToTPK);
            mapView.addLayer(localTiledLayer);
        }catch(Exception ex){
            Log.i(TAG,"failed to load local basemap: " + ex.getMessage());
            return;
        }
    }

    private String createGeodatabaseFilePath(){
        return demoDataFile.getAbsolutePath() + File.separator + offlineDataSDCardDirName + File.separator + filename + OFFLINE_FILE_EXTENSION;
    }

    private String createTPKFilePath(){
        return demoDataFile.getAbsolutePath() + File.separator + offlineDataSDCardDirName + File.separator + tpkFileName;
    }

    private void downloadData(String url){
        // create a dialog to update user on progress
        mProgressDialog.show();

        Log.i(TAG,"downloading data...");
        gdbSyncTask = new GeodatabaseSyncTask(url,null);
        gdbSyncTask.fetchFeatureServiceInfo(new CallbackListener<FeatureServiceInfo>() {
            @Override
            public void onCallback(FeatureServiceInfo featureServiceInfo) {
                if(featureServiceInfo.isSyncEnabled()){
                    Log.i(TAG,"service is sync enabled!");
                    createGeodatabase(featureServiceInfo);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                Log.i(TAG,"error fetching feature service info: "+ throwable.getMessage());
                mProgressDialog.dismiss();
            }
        });//fetch
    }//downloadData

    private void createGeodatabase(FeatureServiceInfo featureServerInfo){
        GenerateGeodatabaseParameters params = new GenerateGeodatabaseParameters(featureServerInfo, mapView.getExtent(),mapView.getSpatialReference());

        //callback that fires when the task is completed or failed
        CallbackListener<String> gdbResponseCallback = new CallbackListener<String>() {
            @Override
            public void onCallback(String path) {
                Log.i(TAG,"Inside create geodatabase callback");
                Log.i(TAG, "Geodatabase is: " + path);
                mProgressDialog.dismiss();
                //update map with local feature layer from geodatabase
                updateFeatureLayer(path);
            }

            @Override
            public void onError(Throwable throwable) {
                Log.i(TAG, "error creating geodatabase: " + throwable.getMessage());
                mProgressDialog.dismiss();
                showToast("Failed to generate geodatabase");
            }
        };

        GeodatabaseStatusCallback statusCallback = new GeodatabaseStatusCallback() {
            @Override
            public void statusUpdated(GeodatabaseStatusInfo geodatabaseStatusInfo) {
                String progress = geodatabaseStatusInfo.getStatus().toString();
                Log.i(TAG,progress);
                // get activity context
                Context context = MainActivity.getContext();
                // create activity from context
                MainActivity activity = (MainActivity) context;
                // update progress bar on main thread
                showProgressBar(activity, progress);
            }
        };

        //path to geodatabase file localGDBFilePath;
        submitTask(params, localGDBFilePath, statusCallback, gdbResponseCallback);
    }//createGeodatabase

    private void submitTask(GenerateGeodatabaseParameters params, String file, GeodatabaseStatusCallback statusCallback, CallbackListener<String> gdbResponseCallback){
        //submit task
        gdbSyncTask.generateGeodatabase(params, file, false, statusCallback, gdbResponseCallback);
    }

    //adding feature layer from local godatabase
    private void updateFeatureLayer(String featureLayerPath){
        //create a new geodatabase
        Geodatabase localGdb = null;
        try{
            localGdb = new Geodatabase(featureLayerPath);
            //make sure to remove any existing layers on the mapview first
            removeLayers();
        }catch (FileNotFoundException e){
            Log.i(TAG,"Failed to find local gdb featurelayer: "+ e.getMessage());
        }

        Log.i(TAG,"num of tables..: " + localGdb.getGeodatabaseTables().size());

        //if feature table has geometry, add it to the mapview
        if(localGdb != null){
            for (GeodatabaseFeatureTable gdbFeatureTable: localGdb.getGeodatabaseTables()){
                if(gdbFeatureTable.hasGeometry()){
                    Log.i(TAG, "adding local feature layer");
                    mapView.addLayer(new FeatureLayer(gdbFeatureTable));
                }
            }
            showToast("Success");
        }
    }//end update feature layer

    private void removeLayers(){
        Log.i(TAG,"removing layers...");
        if(mapView.getLayers().length > 0){
            for(Layer layer: mapView.getLayers()){
                //if the layer name is not null, then it is not the basemap
                if(layer.getName() != null){
                    mapView.removeLayer(layer);
                }
            }
        }
        else{
            Log.i(TAG,"There are no layers in this map...");
        }
    }//remove layers

    public void showToast(final String message) {
        // Show toast message on the main thread only; this function can be
        // called from query callbacks that run on background threads.
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showProgressBar(final MainActivity activity, final String message){
        activity.runOnUiThread(new Runnable(){

            @Override
            public void run() {
                mProgressDialog.setMessage(message);
            }

        });
    }


    // methods to ensure context is available when updating the progress dialog
    public static Context getContext(){
        return mContext;
    }

    public static void setContext(Context context){
        mContext = context;
    }

    //syncing.....
    private void syncGeodatabase(){
        Log.i(TAG,"syncing.....");

        try{
            //create local geodatabase
            Geodatabase gdb  = new Geodatabase(localGDBFilePath);

            //sync parameters
            syncParams = gdb.getSyncParameters();

            CallbackListener<Map<Integer, GeodatabaseFeatureTableEditErrors>> syncResponseCallback = new CallbackListener<Map<Integer, GeodatabaseFeatureTableEditErrors>>() {
                @Override
                public void onCallback(Map<Integer, GeodatabaseFeatureTableEditErrors> objs) {
                    mProgressDialog = new ProgressDialog(MainActivity.this);
                    mProgressDialog.setTitle("Syncing...");

                    if(objs != null){
                        if(objs.size() > 0){
                            showToast("Sync Completed With Errors");
                            Log.i(TAG,"sync completed with errors...");
                        }else{
                            showToast("Sync Completed Without Errors!!");
                        }
                    }else{
                        showToast("Sync completed without errors!");
                    }
                    mProgressDialog.dismiss();
                }

                @Override
                public void onError(Throwable throwable) {
                    Log.i(TAG,"failed in sync callback: "+ throwable.getMessage());
                    showToast(throwable.getMessage());
                    mProgressDialog.dismiss();
                }
            };

            GeodatabaseStatusCallback statusCallback = new GeodatabaseStatusCallback() {
                @Override
                public void statusUpdated(GeodatabaseStatusInfo geodatabaseStatusInfo) {
                    if(!geodatabaseStatusInfo.isDownloading()){
                        showToast(geodatabaseStatusInfo.getStatus().toString());
                    }
                }
            };

            //performs synchronization
            Log.i(TAG, "Finally syncing....");
            gdbSyncTask.syncGeodatabase(syncParams, gdb, statusCallback, syncResponseCallback);

        }catch(Exception ex){
            mProgressDialog.dismiss();
            Log.i(TAG,"error with syncing: " + ex.getMessage());
            ex.printStackTrace();
        }
    }//sync


    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.unpause();
    }

}

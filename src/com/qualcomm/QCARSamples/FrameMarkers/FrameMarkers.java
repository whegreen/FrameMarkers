/*==============================================================================
Copyright (c) 2010-2013 QUALCOMM Austria Research Center GmbH.
All Rights Reserved.

@file
    FrameMarkers.java

@brief
    Sample for FrameMarkers

==============================================================================*/


package com.qualcomm.QCARSamples.FrameMarkers;

import java.util.Vector;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;
import android.widget.Toast;

import com.qualcomm.QCAR.QCAR;


/** The main activity for the FrameMarkers sample. */
public class FrameMarkers extends Activity
{
    // Menu item string constants:
    private static final String MENU_ITEM_ACTIVATE_CONT_AUTO_FOCUS =
        "Activate Cont. Auto focus";
    private static final String MENU_ITEM_DEACTIVATE_CONT_AUTO_FOCUS =
        "Deactivate Cont. Auto focus";

    // Focus mode constants:
    private static final int FOCUS_MODE_NORMAL = 0;
    private static final int FOCUS_MODE_CONTINUOUS_AUTO = 1;

    // Application status constants:
    private static final int APPSTATUS_UNINITED         = -1;
    private static final int APPSTATUS_INIT_APP         = 0;
    private static final int APPSTATUS_INIT_QCAR        = 1;
    private static final int APPSTATUS_INIT_APP_AR      = 2;
    private static final int APPSTATUS_INIT_TRACKER     = 3;
    private static final int APPSTATUS_INITED           = 4;
    private static final int APPSTATUS_CAMERA_STOPPED   = 5;
    private static final int APPSTATUS_CAMERA_RUNNING   = 6;

    // Name of the native dynamic libraries to load:
    private static final String NATIVE_LIB_SAMPLE = "FrameMarkers";
    private static final String NATIVE_LIB_QCAR = "QCAR";

    // Our OpenGL view:
    private QCARSampleGLView mGlView;

    // The view to display the sample splash screen:
    private ImageView mSplashScreenView;

    // The handler and runnable for the splash screen time out task:
    private Handler mSplashScreenHandler;
    private Runnable mSplashScreenRunnable;

    // The minimum time the splash screen should be visible:
    private static final long MIN_SPLASH_SCREEN_TIME = 2000;

    // The time when the splash screen has become visible:
    long mSplashScreenStartTime = 0;

    // Our renderer:
    private FrameMarkersRenderer mRenderer;

    // Display size of the device:
    private int mScreenWidth = 0;
    private int mScreenHeight = 0;

    // The current application status:
    private int mAppStatus = APPSTATUS_UNINITED;

    // The async task to initialize the QCAR SDK:
    private InitQCARTask mInitQCARTask;

    // An object used for synchronizing QCAR initialization, dataset loading and
    // the Android onDestroy() life cycle event. If the application is destroyed
    // while a data set is still being loaded, then we wait for the loading:
    // operation to finish before shutting down QCAR.
    private Object mShutdownLock = new Object();

    // QCAR initialization flags:
    private int mQCARFlags = 0;

    // The textures we will use for rendering:
    private Vector<Texture> mTextures;
    private int mSplashScreenImageResource = 0;

    // The current focus mode selected:
    private int mFocusMode;

    /** Static initializer block to load native libraries on start-up. */
    static
    {
        loadLibrary(NATIVE_LIB_QCAR);
        loadLibrary(NATIVE_LIB_SAMPLE);
    }


    /** An async task to initialize QCAR asynchronously. */
    private class InitQCARTask extends AsyncTask<Void, Integer, Boolean>
    {
        // Initialize with invalid value
        private int mProgressValue = -1;

        protected Boolean doInBackground(Void... params)
        {
            // Prevent the onDestroy() method to overlap with initialization:
            synchronized (mShutdownLock)
            {
                QCAR.setInitParameters(FrameMarkers.this, mQCARFlags);

                do
                {
                    // QCAR.init() blocks until an initialization step is
                    // complete, then it proceeds to the next step and reports
                    // progress in percents (0 ... 100%)
                    // If QCAR.init() returns -1, it indicates an error.
                    // Initialization is done when progress has reached 100%.
                    mProgressValue = QCAR.init();

                    // Publish the progress value:
                    publishProgress(mProgressValue);

                    // We check whether the task has been canceled in the
                    // meantime (by calling AsyncTask.cancel(true))
                    // and bail out if it has, thus stopping this thread.
                    // This is necessary as the AsyncTask will run to completion
                    // regardless of the status of the component that
                    // started is.
                } while (!isCancelled() && mProgressValue >= 0
                         && mProgressValue < 100);

                return (mProgressValue > 0);
            }
        }


        protected void onProgressUpdate(Integer... values)
        {
            // Do something with the progress value "values[0]", e.g. update
            // splash screen, progress bar, etc.
        }


        protected void onPostExecute(Boolean result)
        {
            // Done initializing QCAR, proceed to next application
            // initialization status:
            if (result)
            {
                DebugLog.LOGD("InitQCARTask::onPostExecute: QCAR " +
                              "initialization successful");

                updateApplicationStatus(APPSTATUS_INIT_TRACKER);
            }
            else
            {
                // Create dialog box for display error:
                AlertDialog dialogError = new AlertDialog.Builder(
                    FrameMarkers.this
                ).create();

                dialogError.setButton
                (
                    DialogInterface.BUTTON_POSITIVE,
                    "Close",
                    new DialogInterface.OnClickListener()
                    {
                        public void onClick(DialogInterface dialog, int which)
                        {
                            // Exiting application:
                            System.exit(1);
                        }
                    }
                );

                String logMessage;

                // NOTE: Check if initialization failed because the device is
                // not supported. At this point the user should be informed
                // with a message.
                if (mProgressValue == QCAR.INIT_DEVICE_NOT_SUPPORTED)
                {
                    logMessage = "Failed to initialize QCAR because this " +
                        "device is not supported.";
                }
                else
                {
                    logMessage = "Failed to initialize QCAR.";
                }

                // Log error:
                DebugLog.LOGE("InitQCARTask::onPostExecute: " + logMessage +
                                " Exiting.");

                // Show dialog box with error message:
                dialogError.setMessage(logMessage);
                dialogError.show();
            }
        }
    }


    private void storeScreenDimensions()
    {
        // Query display dimensions:
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mScreenWidth = metrics.widthPixels;
        mScreenHeight = metrics.heightPixels;
    }


    /** Called when the activity first starts or the user navigates back
     * to an activity. */
    protected void onCreate(Bundle savedInstanceState)
    {
        DebugLog.LOGD("FrameMarkers::onCreate");
        super.onCreate(savedInstanceState);

        // Set the splash screen image to display during initialization:
        mSplashScreenImageResource = R.drawable.splash_screen;

        // Load any sample specific textures:
        mTextures = new Vector<Texture>();
        loadTextures();

        // Query the QCAR initialization flags:
        mQCARFlags = getInitializationFlags();

        // Update the application status to start initializing application
        updateApplicationStatus(APPSTATUS_INIT_APP);
    }


    /** We want to load specific textures from the APK, which we will later
    use for rendering. */
    private void loadTextures()
    {
        //mTextures.add(Texture.loadTextureFromApk("letter_Q.png", getAssets()));
        //mTextures.add(Texture.loadTextureFromApk("letter_C.png", getAssets()));
        mTextures.add(Texture.loadTextureFromApk("letter_A.png", getAssets()));
        //mTextures.add(Texture.loadTextureFromApk("letter_R.png", getAssets()));
    }


    /** Configure QCAR with the desired version of OpenGL ES. */
    private int getInitializationFlags()
    {
        return QCAR.GL_20;
    }


    /** Native tracker initialization and deinitialization. */
    public native int initTracker();
    public native void deinitTracker();


    /** Native methods for starting and stopping the camera. */
    private native void startCamera();
    private native void stopCamera();

    /** Native method for setting / updating the projection matrix for
      * AR content rendering */
    private native void setProjectionMatrix();


   /** Called when the activity will start interacting with the user.*/
    protected void onResume()
    {
        DebugLog.LOGD("FrameMarkers::onResume");
        super.onResume();

        // QCAR-specific resume operation:
        QCAR.onResume();

        // We may start the camera only if the QCAR SDK has already been
        // initialized:
        if (mAppStatus == APPSTATUS_CAMERA_STOPPED)
        {
            updateApplicationStatus(APPSTATUS_CAMERA_RUNNING);
        }

        // Resume the GL view:
        if (mGlView != null)
        {
            mGlView.setVisibility(View.VISIBLE);
            mGlView.onResume();
        }
    }

    public void onConfigurationChanged(Configuration config)
    {
        DebugLog.LOGD("FrameMarkers::onConfigurationChanged");
        super.onConfigurationChanged(config);

        storeScreenDimensions();

        // Set projection matrix:
        if (QCAR.isInitialized() && (mAppStatus == APPSTATUS_CAMERA_RUNNING))
            setProjectionMatrix();
    }


    /** Called when the system is about to start resuming a previous activity.*/
    protected void onPause()
    {
        DebugLog.LOGD("FrameMarkers::onPause");
        super.onPause();

        if (mGlView != null)
        {
            mGlView.setVisibility(View.INVISIBLE);
            mGlView.onPause();
        }

        if (mAppStatus == APPSTATUS_CAMERA_RUNNING)
        {
            updateApplicationStatus(APPSTATUS_CAMERA_STOPPED);
        }

        // QCAR-specific pause operation:
        QCAR.onPause();
    }


    /** Native function to deinitialize the application.*/
    private native void deinitApplicationNative();


    /** The final call you receive before your activity is destroyed.*/
    protected void onDestroy()
    {
        DebugLog.LOGD("FrameMarkers::onDestroy");
        super.onDestroy();

        // Dismiss the splash screen time out handler:
        if (mSplashScreenHandler != null)
        {
            mSplashScreenHandler.removeCallbacks(mSplashScreenRunnable);
            mSplashScreenRunnable = null;
            mSplashScreenHandler = null;
        }

        // Cancel potentially running tasks:
        if (mInitQCARTask != null &&
            mInitQCARTask.getStatus() != InitQCARTask.Status.FINISHED)
        {
            mInitQCARTask.cancel(true);
            mInitQCARTask = null;
        }

        // Ensure that the asynchronous operations to initialize QCAR does
        // not overlap:
        synchronized (mShutdownLock) {

            // Do application deinitialization in native code:
            deinitApplicationNative();

            // Unload texture:
            mTextures.clear();
            mTextures = null;

            // Deinit the tracker:
            deinitTracker();

            // Deinitialize QCAR SDK:
            QCAR.deinit();
        }

        System.gc();
    }


    /** NOTE: this method is synchronized because of a potential concurrent
     * access by FrameMarkers::onResume() and InitQCARTask::onPostExecute(). */
    private synchronized void updateApplicationStatus(int appStatus)
    {
        // Exit if there is no change in status:
        if (mAppStatus == appStatus)
            return;

        // Store new status value
        mAppStatus = appStatus;

        // Execute application state-specific actions:
        switch (mAppStatus)
        {
            case APPSTATUS_INIT_APP:
                // Initialize application elements that do not rely on QCAR
                // initialization:
                initApplication();

                // Proceed to next application initialization status:
                updateApplicationStatus(APPSTATUS_INIT_QCAR);
                break;

            case APPSTATUS_INIT_QCAR:
                // Initialize QCAR SDK asynchronously to avoid blocking the
                // main (UI) thread.
                // This task instance must be created and invoked on the UI
                // thread and it can be executed only once!
                try
                {
                    mInitQCARTask = new InitQCARTask();
                    mInitQCARTask.execute();
                }
                catch (Exception e)
                {
                    DebugLog.LOGE("Initializing QCAR SDK failed");
                }
                break;

            case APPSTATUS_INIT_TRACKER:

                // Initialize the marker tracker and create markers:
                if (initTracker() >= 0)
                {
                    // Proceed to next application initialization status:
                    updateApplicationStatus(APPSTATUS_INIT_APP_AR);
                }
                break;

            case APPSTATUS_INIT_APP_AR:
                // Initialize Augmented Reality-specific application elements
                // that may rely on the fact that the QCAR SDK has been
                // already initialized:
                initApplicationAR();

                // Proceed to next application initialization status:
                updateApplicationStatus(APPSTATUS_INITED);
                break;

            case APPSTATUS_INITED:
                // Hint to the virtual machine that it would be a good time to
                // run the garbage collector:
                //
                // NOTE: This is only a hint. There is no guarantee that the
                // garbage collector will actually be run.
                System.gc();

                // The elapsed time since the splash screen was visible:
                long splashScreenTime = System.currentTimeMillis() -
                                            mSplashScreenStartTime;
                long newSplashScreenTime = 0;
                if (splashScreenTime < MIN_SPLASH_SCREEN_TIME)
                {
                    newSplashScreenTime = MIN_SPLASH_SCREEN_TIME -
                                            splashScreenTime;
                }

                // Request a callback function after a given timeout to dismiss
                // the splash screen:
                mSplashScreenHandler = new Handler();
                mSplashScreenRunnable =
                    new Runnable() {
                        public void run()
                        {
                            // Hide the splash screen:
                            mSplashScreenView.setVisibility(View.INVISIBLE);

                            // Activate the renderer:
                            mRenderer.mIsActive = true;

                            // Now add the GL surface view. It is important
                            // that the OpenGL ES surface view gets added
                            // BEFORE the camera is started and video
                            // background is configured.
                            addContentView(mGlView, new LayoutParams(
                                            LayoutParams.MATCH_PARENT,
                                            LayoutParams.MATCH_PARENT));

                            // Start the camera:
                            updateApplicationStatus(APPSTATUS_CAMERA_RUNNING);
                        }
                };

                mSplashScreenHandler.postDelayed(mSplashScreenRunnable,
                                                    newSplashScreenTime);
                break;

            case APPSTATUS_CAMERA_STOPPED:
                // Call the native function to stop the camera:
                stopCamera();
                break;

            case APPSTATUS_CAMERA_RUNNING:
                // Call the native function to start the camera:
                startCamera();
                setProjectionMatrix();

                // Set continuous auto-focus if supported by the device,
                // otherwise default back to regular auto-focus mode.
                // This will be activated by a tap to the screen in this
                // application.
                mFocusMode = FOCUS_MODE_CONTINUOUS_AUTO;
                if(!setFocusMode(mFocusMode))
                {
                    mFocusMode = FOCUS_MODE_NORMAL;
                    setFocusMode(mFocusMode);
                }

                break;

            default:
                throw new RuntimeException("Invalid application state");
        }
    }


    /** Tells native code whether we are in portait or landscape mode */
    private native void setActivityPortraitMode(boolean isPortrait);


    /** Initialize application GUI elements that are not related to AR. */
    private void initApplication()
    {
        // Set the screen orientation:
        int screenOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;

        // Apply screen orientation:
        setRequestedOrientation(screenOrientation);

        // Pass on screen orientation info to native code:
        setActivityPortraitMode(
            screenOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        storeScreenDimensions();

        // As long as this window is visible to the user, keep the device's
        // screen turned on and bright.
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Create and add the splash screen view:
        mSplashScreenView = new ImageView(this);
        mSplashScreenView.setImageResource(mSplashScreenImageResource);
        addContentView(mSplashScreenView, new LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        mSplashScreenStartTime = System.currentTimeMillis();

    }


    /** Native function to initialize the application. */
    private native void initApplicationNative(int width, int height);


    /** Initializes AR application components. */
    private void initApplicationAR()
    {
        // Do application initialization in native code (e.g. registering
        // callbacks, etc.)
        initApplicationNative(mScreenWidth, mScreenHeight);

        // Create OpenGL ES view:
        int depthSize = 16;
        int stencilSize = 0;
        boolean translucent = QCAR.requiresAlpha();

        mGlView = new QCARSampleGLView(this);
        mGlView.init(mQCARFlags, translucent, depthSize, stencilSize);

        mRenderer = new FrameMarkersRenderer();
        mGlView.setRenderer(mRenderer);

        mGlView.setOnClickListener(new OnClickListener(){
            public void onClick(View v) {
                autofocus();
                
                // Autofocus action resets focus mode
                mFocusMode = FOCUS_MODE_NORMAL;
            }});
    }


    /** Invoked every time before the options menu gets displayed to give
     *  the Activity a chance to populate its Menu with menu items. */
    public boolean onPrepareOptionsMenu(Menu menu) 
    {
        super.onPrepareOptionsMenu(menu);
        
        menu.clear();

        if(mFocusMode == FOCUS_MODE_CONTINUOUS_AUTO)
            menu.add(MENU_ITEM_DEACTIVATE_CONT_AUTO_FOCUS);
        else
            menu.add(MENU_ITEM_ACTIVATE_CONT_AUTO_FOCUS);

        return true;
    }

    /** Invoked when the user selects an item from the Menu */
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if(item.getTitle().equals(MENU_ITEM_ACTIVATE_CONT_AUTO_FOCUS))
        {
            if(setFocusMode(FOCUS_MODE_CONTINUOUS_AUTO))
            {
                mFocusMode = FOCUS_MODE_CONTINUOUS_AUTO;
                item.setTitle(MENU_ITEM_DEACTIVATE_CONT_AUTO_FOCUS);
            }
            else
            {
                Toast.makeText
                (
                    this,
                    "Unable to activate Continuous Auto-Focus",
                    Toast.LENGTH_SHORT
                ).show();
            }
        }
        else if(item.getTitle().equals(MENU_ITEM_DEACTIVATE_CONT_AUTO_FOCUS))
        {
            if(setFocusMode(FOCUS_MODE_NORMAL))
            {
                mFocusMode = FOCUS_MODE_NORMAL;
                item.setTitle(MENU_ITEM_ACTIVATE_CONT_AUTO_FOCUS);
            }
            else
            {
                Toast.makeText
                (
                    this,
                    "Unable to deactivate Continuous Auto-Focus",
                    Toast.LENGTH_SHORT
                ).show();
            }
        }

        return true;
    }

   private native boolean autofocus();
   private native boolean setFocusMode(int mode);


    /** Returns the number of registered textures. */
    public int getTextureCount()
    {
        return mTextures.size();
    }


    /** Returns the texture object at the specified index. */
    public Texture getTexture(int i)
    {
        return mTextures.elementAt(i);
    }


    /** A helper for loading native libraries stored in "libs/armeabi*". */
    public static boolean loadLibrary(String nLibName)
    {
        try
        {
            System.loadLibrary(nLibName);
            DebugLog.LOGI("Native library lib" + nLibName + ".so loaded");
            return true;
        }
        catch (UnsatisfiedLinkError ulee)
        {
            DebugLog.LOGE("The library lib" + nLibName +
                            ".so could not be loaded");
        }
        catch (SecurityException se)
        {
            DebugLog.LOGE("The library lib" + nLibName +
                            ".so was not allowed to be loaded");
        }

        return false;
    }
}

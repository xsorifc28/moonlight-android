package com.limelight;

import com.google.android.glass.media.Sounds;

import com.limelight.binding.PlatformBinding;
import com.limelight.binding.input.ControllerHandler;
import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.binding.input.TouchContext;
import com.limelight.binding.input.evdev.EvdevListener;
import com.limelight.binding.input.evdev.EvdevWatcher;
import com.limelight.binding.video.ConfigurableDecoderRenderer;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.NvConnectionListener;
import com.limelight.nvstream.StreamConfiguration;
import com.limelight.nvstream.av.video.VideoDecoderRenderer;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.input.KeyboardPacket;
import com.limelight.nvstream.input.MouseButtonPacket;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.GameGestures;
import com.limelight.utils.Dialog;
import com.limelight.utils.ServerHelper;
import com.limelight.utils.SpinnerDialog;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Point;
import android.hardware.input.InputManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.Display;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnGenericMotionListener;
import android.view.View.OnSystemUiVisibilityChangeListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;




public class Game extends Activity implements SurfaceHolder.Callback,
    OnGenericMotionListener, OnTouchListener, NvConnectionListener, EvdevListener,
    OnSystemUiVisibilityChangeListener, GameGestures
        //, RecognitionListener
{
    private static final String TAG = "Game";

    private int lastMouseX = Integer.MIN_VALUE;
    private int lastMouseY = Integer.MIN_VALUE;
    private int lastButtonState = 0;

    // Only 2 touches are supported
    private final TouchContext[] touchContextMap = new TouchContext[2];
    private long threeFingerDownTime = 0;

    private static final double REFERENCE_HORIZ_RES = 1280;
    private static final double REFERENCE_VERT_RES = 720;

    private static final int THREE_FINGER_TAP_THRESHOLD = 300;

    private ControllerHandler controllerHandler;
    private KeyboardTranslator keybTranslator;

    private PreferenceConfiguration prefConfig;
    private final Point screenSize = new Point(0, 0);

    private NvConnection conn;
    private SpinnerDialog spinner;
    private boolean displayedFailureDialog = false;
    private boolean connecting = false;
    private boolean connected = false;

    private EvdevWatcher evdevWatcher;
    private int modifierFlags = 0;
    private boolean grabbedInput = true;
    private boolean grabComboDown = false;

    private ConfigurableDecoderRenderer decoderRenderer;

    private WifiManager.WifiLock wifiLock;

    private int drFlags = 0;

    public static final String EXTRA_HOST = "Host";
    public static final String EXTRA_APP_NAME = "AppName";
    public static final String EXTRA_APP_ID = "AppId";
    public static final String EXTRA_UNIQUEID = "UniqueId";
    public static final String EXTRA_STREAMING_REMOTE = "Remote";

    private SpeechRecognizer mSpeechRecognizer;
    private Intent mSpeechIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String locale = PreferenceConfiguration.readPreferences(this).language;
        if (!locale.equals(PreferenceConfiguration.DEFAULT_LANGUAGE)) {
            Configuration config = new Configuration(getResources().getConfiguration());
            config.locale = new Locale(locale);
            getResources().updateConfiguration(config, getResources().getDisplayMetrics());
        }


       createListener();
        startListener();

        // Start acquiring the voice recognizer
        //startRecognition();

        //getWindow().requestFeature(WindowUtils.FEATURE_VOICE_COMMANDS);

        // We don't want a title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Full-screen and don't let the display go off
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // If we're going to use immersive mode, we want to have
        // the entire screen
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

            getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        }

        // Listen for UI visibility events
        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(this);

        // Change volume button behavior
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // Inflate the content
        setContentView(R.layout.activity_game);

        // Start the spinner
        spinner = SpinnerDialog.displayDialog(this, getResources().getString(R.string.conn_establishing_title),
                getResources().getString(R.string.conn_establishing_msg), true);

        // Read the stream preferences
        prefConfig = PreferenceConfiguration.readPreferences(this);
        switch (prefConfig.decoder) {
        case PreferenceConfiguration.FORCE_SOFTWARE_DECODER:
            drFlags |= VideoDecoderRenderer.FLAG_FORCE_SOFTWARE_DECODING;
            break;
        case PreferenceConfiguration.AUTOSELECT_DECODER:
            break;
        case PreferenceConfiguration.FORCE_HARDWARE_DECODER:
            drFlags |= VideoDecoderRenderer.FLAG_FORCE_HARDWARE_DECODING;
            break;
        }

        if (prefConfig.stretchVideo) {
            drFlags |= VideoDecoderRenderer.FLAG_FILL_SCREEN;
        }

        Display display = getWindowManager().getDefaultDisplay();
        display.getSize(screenSize);

        // Listen for events on the game surface
        SurfaceView sv = (SurfaceView) findViewById(R.id.surfaceView);
        sv.setOnGenericMotionListener(this);
        sv.setOnTouchListener(this);

        // Warn the user if they're on a metered connection
        checkDataConnection();

        // Make sure Wi-Fi is fully powered up
        WifiManager wifiMgr = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        wifiLock = wifiMgr.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Limelight");
        wifiLock.setReferenceCounted(false);
        wifiLock.acquire();

        String host = Game.this.getIntent().getStringExtra(EXTRA_HOST);
        String appName = Game.this.getIntent().getStringExtra(EXTRA_APP_NAME);
        int appId = Game.this.getIntent().getIntExtra(EXTRA_APP_ID, StreamConfiguration.INVALID_APP_ID);
        String uniqueId = Game.this.getIntent().getStringExtra(EXTRA_UNIQUEID);
        boolean remote = Game.this.getIntent().getBooleanExtra(EXTRA_STREAMING_REMOTE, false);

        if (appId == StreamConfiguration.INVALID_APP_ID) {
            finish();
            return;
        }

        decoderRenderer = new ConfigurableDecoderRenderer();
        decoderRenderer.initializeWithFlags(drFlags);
        
        StreamConfiguration config = new StreamConfiguration.Builder()
                .setResolution(prefConfig.width, prefConfig.height)
                .setRefreshRate(prefConfig.fps)
                .setApp(new NvApp(appName, appId))
                .setBitrate(prefConfig.bitrate * 1000)
                .setEnableSops(prefConfig.enableSops)
                .enableAdaptiveResolution((decoderRenderer.getCapabilities() &
                        VideoDecoderRenderer.CAPABILITY_ADAPTIVE_RESOLUTION) != 0)
                .enableLocalAudioPlayback(false)
                //.enableLocalAudioPlayback(prefConfig.playHostAudio)
                .setMaxPacketSize(remote ? 1024 : 1292)
                .setRemote(remote)
                .build();

        // Initialize the connection
        conn = new NvConnection(host, uniqueId, Game.this, config, PlatformBinding.getCryptoProvider(this));
        keybTranslator = new KeyboardTranslator(conn);
        controllerHandler = new ControllerHandler(conn, this, prefConfig.multiController, prefConfig.deadzonePercentage);

        InputManager inputManager = (InputManager) getSystemService(Context.INPUT_SERVICE);
        inputManager.registerInputDeviceListener(controllerHandler, null);

        SurfaceHolder sh = sv.getHolder();
        if (prefConfig.stretchVideo || !decoderRenderer.isHardwareAccelerated()) {
            // Set the surface to the size of the video
            sh.setFixedSize(prefConfig.width, prefConfig.height);
        }

        // Initialize touch contexts
        for (int i = 0; i < touchContextMap.length; i++) {
            touchContextMap[i] = new TouchContext(conn, i,
                    (REFERENCE_HORIZ_RES / (double)screenSize.x),
                    (REFERENCE_VERT_RES / (double)screenSize.y));
        }

        if (LimelightBuildProps.ROOT_BUILD) {
            // Start watching for raw input
            evdevWatcher = new EvdevWatcher(this);
            evdevWatcher.start();
        }

        // The connection will be started when the surface gets created
        sh.addCallback(this);
    }

    private void resizeSurfaceWithAspectRatio(SurfaceView sv, double vidWidth, double vidHeight)
    {
        // Get the visible width of the activity
        double visibleWidth = getWindow().getDecorView().getWidth();

        ViewGroup.LayoutParams lp = sv.getLayoutParams();

        // Calculate the new size of the SurfaceView
        lp.width = (int) visibleWidth;
        lp.height = (int) ((vidHeight / vidWidth) * visibleWidth);

        // Apply the size change
        sv.setLayoutParams(lp);
    }

    private void checkDataConnection()
    {
        ConnectivityManager mgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (mgr.isActiveNetworkMetered()) {
            displayTransientMessage(getResources().getString(R.string.conn_metered));
        }
    }

    @SuppressLint("InlinedApi")
    private final Runnable hideSystemUi = new Runnable() {
            @Override
            public void run() {
                // Use immersive mode on 4.4+ or standard low profile on previous builds
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                    Game.this.getWindow().getDecorView().setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
                }
                else {
                    Game.this.getWindow().getDecorView().setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_LOW_PROFILE);
                }
            }
    };

    private void hideSystemUi(int delay) {
        Handler h = getWindow().getDecorView().getHandler();
        if (h != null) {
            h.removeCallbacks(hideSystemUi);
            h.postDelayed(hideSystemUi, delay);
        }
    }


    @Override
    protected void onStop() {
        super.onStop();

        SpinnerDialog.closeDialogs(this);
        Dialog.closeDialogs();

        InputManager inputManager = (InputManager) getSystemService(Context.INPUT_SERVICE);
        inputManager.unregisterInputDeviceListener(controllerHandler);

        displayedFailureDialog = true;
        stopConnection();

        int averageEndToEndLat = decoderRenderer.getAverageEndToEndLatency();
        int averageDecoderLat = decoderRenderer.getAverageDecoderLatency();
        String message = null;
        if (averageEndToEndLat > 0) {
            message = getResources().getString(R.string.conn_client_latency)+" "+averageEndToEndLat+" ms";
            if (averageDecoderLat > 0) {
                message += " ("+getResources().getString(R.string.conn_client_latency_hw)+" "+averageDecoderLat+" ms)";
            }
        }
        else if (averageDecoderLat > 0) {
            message = getResources().getString(R.string.conn_hardware_latency)+" "+averageDecoderLat+" ms";
        }

        if (message != null) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }

        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mSpeechRecognizer != null)
        {
            mSpeechRecognizer.destroy();
        }
        wifiLock.release();
    }

    private final Runnable toggleGrab = new Runnable() {
        @Override
        public void run() {

            if (evdevWatcher != null) {
                if (grabbedInput) {
                    evdevWatcher.ungrabAll();
                }
                else {
                    evdevWatcher.regrabAll();
                }
            }

            grabbedInput = !grabbedInput;
        }
    };

    // Returns true if the key stroke was consumed
    private boolean handleSpecialKeys(short translatedKey, boolean down) {
        int modifierMask = 0;

        // Mask off the high byte
        translatedKey &= 0xff;

        if (translatedKey == KeyboardTranslator.VK_CONTROL) {
            modifierMask = KeyboardPacket.MODIFIER_CTRL;
        }
        else if (translatedKey == KeyboardTranslator.VK_SHIFT) {
            modifierMask = KeyboardPacket.MODIFIER_SHIFT;
        }
        else if (translatedKey == KeyboardTranslator.VK_ALT) {
            modifierMask = KeyboardPacket.MODIFIER_ALT;
        }

        if (down) {
            this.modifierFlags |= modifierMask;
        }
        else {
            this.modifierFlags &= ~modifierMask;
        }

        // Check if Ctrl+Shift+Z is pressed
        if (translatedKey == KeyboardTranslator.VK_Z &&
            (modifierFlags & (KeyboardPacket.MODIFIER_CTRL | KeyboardPacket.MODIFIER_SHIFT)) ==
                (KeyboardPacket.MODIFIER_CTRL | KeyboardPacket.MODIFIER_SHIFT))
        {
            if (down) {
                // Now that we've pressed the magic combo
                // we'll wait for one of the keys to come up
                grabComboDown = true;
            }
            else {
                // Toggle the grab if Z comes up
                Handler h = getWindow().getDecorView().getHandler();
                if (h != null) {
                    h.postDelayed(toggleGrab, 250);
                }

                grabComboDown = false;
            }

            return true;
        }
        // Toggle the grab if control or shift comes up
        else if (grabComboDown) {
            Handler h = getWindow().getDecorView().getHandler();
            if (h != null) {
                h.postDelayed(toggleGrab, 250);
            }

            grabComboDown = false;
            return true;
        }

        // Not a special combo
        return false;
    }

    private static byte getModifierState(KeyEvent event) {
        byte modifier = 0;
        if (event.isShiftPressed()) {
            modifier |= KeyboardPacket.MODIFIER_SHIFT;
        }
        if (event.isCtrlPressed()) {
            modifier |= KeyboardPacket.MODIFIER_CTRL;
        }
        if (event.isAltPressed()) {
            modifier |= KeyboardPacket.MODIFIER_ALT;
        }
        return modifier;
    }

    private byte getModifierState() {
        return (byte) modifierFlags;
    }

    int state = 0;
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Pass-through virtual navigation keys

        if(keyCode == KeyEvent.KEYCODE_BACK){
            //quit
            Intent data = new Intent();
            data.putExtra("manualQuit",true);
            setResult(ServerHelper.APP_QUIT_REQUEST_CODE, data);
            exitApp();
        }

        if(keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            if (state == 0) {
                onKeyDown(KeyEvent.KEYCODE_Q, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Q));
                state = 1;
            } else if (state == 1) {
                onKeyDown(KeyEvent.KEYCODE_W, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_W));
                state = 2;
            } else if (state == 2) {
                onKeyDown(KeyEvent.KEYCODE_E, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_E));
                state = 3;
            } else if (state == 3) {
                onKeyDown(KeyEvent.KEYCODE_R, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_R));
                state = 0;
            }
        }


        if ((event.getFlags() & KeyEvent.FLAG_VIRTUAL_HARD_KEY) != 0) {
            return super.onKeyDown(keyCode, event);
        }

        // Try the controller handler first
        boolean handled = controllerHandler.handleButtonDown(event);
        if (!handled) {
            // Try the keyboard handler
            short translated = keybTranslator.translate(event.getKeyCode());
            if (translated == 0) {
                return super.onKeyDown(keyCode, event);
            }

            // Let this method take duplicate key down events
            if (handleSpecialKeys(translated, true)) {
                return true;
            }

            // Eat repeat down events
            if (event.getRepeatCount() > 0) {
                return true;
            }

            // Pass through keyboard input if we're not grabbing
            if (!grabbedInput) {
                return super.onKeyDown(keyCode, event);
            }

            keybTranslator.sendKeyDown(translated,
                    getModifierState(event));
        }

        return true;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // Pass-through virtual navigation keys
        if ((event.getFlags() & KeyEvent.FLAG_VIRTUAL_HARD_KEY) != 0) {
            return super.onKeyUp(keyCode, event);
        }

        // Try the controller handler first
        boolean handled = controllerHandler.handleButtonUp(event);
        if (!handled) {
            // Try the keyboard handler
            short translated = keybTranslator.translate(event.getKeyCode());
            if (translated == 0) {
                return super.onKeyUp(keyCode, event);
            }

            if (handleSpecialKeys(translated, false)) {
                return true;
            }

            // Pass through keyboard input if we're not grabbing
            if (!grabbedInput) {
                return super.onKeyUp(keyCode, event);
            }

            keybTranslator.sendKeyUp(translated,
                    getModifierState(event));
        }

        return true;
    }

    private TouchContext getTouchContext(int actionIndex)
    {
        if (actionIndex < touchContextMap.length) {
            return touchContextMap[actionIndex];
        }
        else {
            return null;
        }
    }

    @Override
    public void showKeyboard() {
        LimeLog.info("Showing keyboard overlay");
        InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
    }

    // Returns true if the event was consumed
    private boolean handleMotionEvent(MotionEvent event) {
        // Pass through keyboard input if we're not grabbing
        if (!grabbedInput) {
            return false;
        }

        if ((event.getSource() & InputDevice.SOURCE_CLASS_JOYSTICK) != 0) {
            if (controllerHandler.handleMotionEvent(event)) {
                return true;
            }
        }
        else if ((event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0)
        {
            // This case is for mice
            if (event.getSource() == InputDevice.SOURCE_MOUSE)
            {
                int changedButtons = event.getButtonState() ^ lastButtonState;

                if (event.getActionMasked() == MotionEvent.ACTION_SCROLL) {
                    // Send the vertical scroll packet
                    byte vScrollClicks = (byte) event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                    conn.sendMouseScroll(vScrollClicks);
                }

                if ((changedButtons & MotionEvent.BUTTON_PRIMARY) != 0) {
                    if ((event.getButtonState() & MotionEvent.BUTTON_PRIMARY) != 0) {
                        conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT);
                    }
                    else {
                        conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
                    }
                }

                if ((changedButtons & MotionEvent.BUTTON_SECONDARY) != 0) {
                    if ((event.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0) {
                        conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT);
                    }
                    else {
                        conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT);
                    }
                }

                if ((changedButtons & MotionEvent.BUTTON_TERTIARY) != 0) {
                    if ((event.getButtonState() & MotionEvent.BUTTON_TERTIARY) != 0) {
                        conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_MIDDLE);
                    }
                    else {
                        conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_MIDDLE);
                    }
                }

                // First process the history
                for (int i = 0; i < event.getHistorySize(); i++) {
                    updateMousePosition((int)event.getHistoricalX(i), (int)event.getHistoricalY(i));
                }

                // Now process the current values
                updateMousePosition((int)event.getX(), (int)event.getY());

                lastButtonState = event.getButtonState();
            }
            // This case is for touch-based input devices
            else
            {
                int actionIndex = event.getActionIndex();

                int eventX = (int)event.getX(actionIndex);
                int eventY = (int)event.getY(actionIndex);

                // Special handling for 3 finger gesture
                if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN &&
                        event.getPointerCount() == 3) {
                    // Three fingers down
                    threeFingerDownTime = SystemClock.uptimeMillis();

                    // Cancel the first and second touches to avoid
                    // erroneous events
                    for (TouchContext aTouchContext : touchContextMap) {
                        aTouchContext.cancelTouch();
                    }

                    return true;
                }

                TouchContext context = getTouchContext(actionIndex);
                if (context == null) {
                    return false;
                }

                switch (event.getActionMasked())
                {
                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_DOWN:
                    context.touchDownEvent(eventX, eventY);
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_UP:
                    if (event.getPointerCount() == 1) {
                        // All fingers up
                        if (SystemClock.uptimeMillis() - threeFingerDownTime < THREE_FINGER_TAP_THRESHOLD) {
                            // This is a 3 finger tap to bring up the keyboard
                            showKeyboard();
                            return true;
                        }
                    }
                    context.touchUpEvent(eventX, eventY);
                    if (actionIndex == 0 && event.getPointerCount() > 1 && !context.isCancelled()) {
                        // The original secondary touch now becomes primary
                        context.touchDownEvent((int)event.getX(1), (int)event.getY(1));
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    // ACTION_MOVE is special because it always has actionIndex == 0
                    // We'll call the move handlers for all indexes manually

                    // First process the historical events
                    for (int i = 0; i < event.getHistorySize(); i++) {
                        for (TouchContext aTouchContextMap : touchContextMap) {
                            if (aTouchContextMap.getActionIndex() < event.getPointerCount())
                            {
                                aTouchContextMap.touchMoveEvent(
                                        (int)event.getHistoricalX(aTouchContextMap.getActionIndex(), i),
                                        (int)event.getHistoricalY(aTouchContextMap.getActionIndex(), i));
                            }
                        }
                    }

                    // Now process the current values
                    for (TouchContext aTouchContextMap : touchContextMap) {
                        if (aTouchContextMap.getActionIndex() < event.getPointerCount())
                        {
                            aTouchContextMap.touchMoveEvent(
                                    (int)event.getX(aTouchContextMap.getActionIndex()),
                                    (int)event.getY(aTouchContextMap.getActionIndex()));
                        }
                    }
                    break;
                default:
                    return false;
                }
            }

            // Handled a known source
            return true;
        }

        // Unknown class
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return handleMotionEvent(event) || super.onTouchEvent(event);

    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        return handleMotionEvent(event) || super.onGenericMotionEvent(event);

    }

    private void updateMousePosition(int eventX, int eventY) {
        // Send a mouse move if we already have a mouse location
        // and the mouse coordinates change
        if (lastMouseX != Integer.MIN_VALUE &&
            lastMouseY != Integer.MIN_VALUE &&
            !(lastMouseX == eventX && lastMouseY == eventY))
        {
            int deltaX = eventX - lastMouseX;
            int deltaY = eventY - lastMouseY;

            // Scale the deltas if the device resolution is different
            // than the stream resolution
            deltaX = (int)Math.round((double)deltaX * (REFERENCE_HORIZ_RES / (double)screenSize.x));
            deltaY = (int)Math.round((double)deltaY * (REFERENCE_VERT_RES / (double)screenSize.y));

            conn.sendMouseMove((short)deltaX, (short)deltaY);
        }

        // Update pointer location for delta calculation next time
        lastMouseX = eventX;
        lastMouseY = eventY;
    }

    @Override
    public boolean onGenericMotion(View v, MotionEvent event) {
        return handleMotionEvent(event);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        return handleMotionEvent(event);
    }

    @Override
    public void stageStarting(Stage stage) {
        if (spinner != null) {
            spinner.setMessage(getResources().getString(R.string.conn_starting)+" "+stage.getName());
        }
    }

    @Override
    public void stageComplete(Stage stage) {
    }

    private void stopConnection() {
        if (connecting || connected) {
            connecting = connected = false;
            conn.stop();
        }

        // Close the Evdev watcher to allow use of captured input devices
        if (evdevWatcher != null) {
            evdevWatcher.shutdown();
            evdevWatcher = null;
        }
    }

    @Override
    public void stageFailed(Stage stage) {
        if (spinner != null) {
            spinner.dismiss();
            spinner = null;
        }

        if (!displayedFailureDialog) {
            displayedFailureDialog = true;
            stopConnection();
            Dialog.displayDialog(this, getResources().getString(R.string.conn_error_title),
                    getResources().getString(R.string.conn_error_msg)+" "+stage.getName(), true);
        }
    }

    @Override
    public void connectionTerminated(Exception e) {
        if (!displayedFailureDialog) {
            displayedFailureDialog = true;
            e.printStackTrace();

            stopConnection();
            Dialog.displayDialog(this, getResources().getString(R.string.conn_terminated_title),
                    getResources().getString(R.string.conn_terminated_msg), true);
        }
    }

    @Override
    public void connectionStarted() {
        if (spinner != null) {
            spinner.dismiss();
            spinner = null;
        }

        connecting = false;
        connected = true;

        hideSystemUi(1000);
    }

    @Override
    public void displayMessage(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(Game.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void displayTransientMessage(final String message) {
        if (!prefConfig.disableWarnings) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(Game.this, message, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (!connected && !connecting) {
            connecting = true;

            // Resize the surface to match the aspect ratio of the video
            // This must be done after the surface is created.
            if (!prefConfig.stretchVideo && decoderRenderer.isHardwareAccelerated()) {
                resizeSurfaceWithAspectRatio((SurfaceView) findViewById(R.id.surfaceView),
                        prefConfig.width, prefConfig.height);
            }

            conn.start(PlatformBinding.getDeviceName(), holder, drFlags,
                    PlatformBinding.getAudioRenderer(), decoderRenderer);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (connected) {
            stopConnection();
        }
    }

    @Override
    public void mouseMove(int deltaX, int deltaY) {
        conn.sendMouseMove((short) deltaX, (short) deltaY);
    }

    @Override
    public void mouseButtonEvent(int buttonId, boolean down) {
        byte buttonIndex;

        switch (buttonId)
        {
        case EvdevListener.BUTTON_LEFT:
            buttonIndex = MouseButtonPacket.BUTTON_LEFT;
            break;
        case EvdevListener.BUTTON_MIDDLE:
            buttonIndex = MouseButtonPacket.BUTTON_MIDDLE;
            break;
        case EvdevListener.BUTTON_RIGHT:
            buttonIndex = MouseButtonPacket.BUTTON_RIGHT;
            break;
        default:
            LimeLog.warning("Unhandled button: "+buttonId);
            return;
        }

        if (down) {
            conn.sendMouseButtonDown(buttonIndex);
        }
        else {
            conn.sendMouseButtonUp(buttonIndex);
        }
    }

    @Override
    public void mouseScroll(byte amount) {
        conn.sendMouseScroll(amount);
    }

    @Override
    public void keyboardEvent(boolean buttonDown, short keyCode) {
        short keyMap = keybTranslator.translate(keyCode);
        if (keyMap != 0) {
            if (handleSpecialKeys(keyMap, buttonDown)) {
                return;
            }

            if (buttonDown) {
                keybTranslator.sendKeyDown(keyMap, getModifierState());
            }
            else {
                keybTranslator.sendKeyUp(keyMap, getModifierState());
            }
        }
    }

    @Override
    public void onSystemUiVisibilityChange(int visibility) {
        // Don't do anything if we're not connected
        if (!connected) {
            return;
        }

        // This flag is set for all devices
        if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
            hideSystemUi(2000);
        }
        // This flag is only set on 4.4+
        else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT &&
                 (visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0) {
            hideSystemUi(2000);
        }
        // This flag is only set before 4.4+
        else if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.KITKAT &&
                 (visibility & View.SYSTEM_UI_FLAG_LOW_PROFILE) == 0) {
            hideSystemUi(2000);
        }
    }

    public void exitApp(){
        onStop();
    }

    public void createListener() {
        mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        AbstractMainRecognitionListener mRecognitionListener = new AbstractMainRecognitionListener();
        mSpeechRecognizer.setRecognitionListener(mRecognitionListener);
        mSpeechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        //mSpeechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");				// i18n
        mSpeechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        mSpeechIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        mSpeechIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 100);				// To loop every X results
    }
    public void startListener() {
        mSpeechRecognizer.startListening(mSpeechIntent);
    }
    public void destroyListener() {
        mSpeechRecognizer.cancel();
//        mSpeechRecognizer.stopListening();
        mSpeechRecognizer.destroy();
    }
    public void restartListener() {
        mSpeechRecognizer.cancel();
//        mSpeechRecognizer.stopListening();
        Handler handler = new Handler( new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                startListener();
                return true;
            }
        });
        // sleeper time
        handler.sendEmptyMessageDelayed(0, 200);
    }
    public void replaceListener() {
        mSpeechRecognizer.cancel();
//        mSpeechRecognizer.stopListening();
        Handler handlerP = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                destroyListener();
                Handler handlerF = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        super.handleMessage(msg);
                        createListener();
                        Handler handler = new Handler() {
                            @Override
                            public void handleMessage(Message msg) {
                                super.handleMessage(msg);
                                startListener();
                                Log.d(TAG, "Replaced Listener");
                            }
                        };
                        // sleeper time
                        handler.sendEmptyMessageDelayed(0, 150);
                    }
                };
                // sleeper time
                handlerF.sendEmptyMessageDelayed(0,150);
            return true;
            }
        });
        // sleeper time
        handlerP.sendEmptyMessageDelayed(0, 150);


    }

    public class AbstractMainRecognitionListener implements RecognitionListener
    {
        @SuppressLint("ResourceAsColor")
        @Override
        public void onReadyForSpeech(Bundle params) {
            Log.d(TAG, "onReadyForSpeech");
            Log.i(TAG, "Start Speaking");
        }

        @SuppressLint("ResourceAsColor")
        @Override
        public void onBeginningOfSpeech() {
            Log.d(TAG, "onBeginningOfSpeech");
            Log.i(TAG, "Speaking detected");
            AudioManager audio = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
            audio.playSoundEffect(Sounds.SELECTED);
        }

        @Override
        public void onRmsChanged(float rmsdB) {

        }

        @Override
        public void onBufferReceived(byte[] buffer) {
            Log.d(TAG, "onBufferReceived");
        }

        @SuppressLint("ResourceAsColor")
        @Override
        public void onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech");
            Log.i(TAG, "Processing...");
        }

        @SuppressLint("ResourceAsColor")
        @Override
        public void onError(int error) {
            Log.d(TAG, "onSpeechError " + error);
            //AudioManager audio = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
            //audio.playSoundEffect(Sounds.ERROR);

            Log.i(TAG, "Error " + error);
            //Something went wrong
            if(error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                //8
                Log.i(TAG, "Recognizer Busy");
                replaceListener();
                return;
            } else if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                restartListener();
                return;
            } else if (error == SpeechRecognizer.ERROR_CLIENT) {
                //5
                Log.i(TAG, "Client Error");
                replaceListener();
                return;
            } else if(error == SpeechRecognizer.ERROR_AUDIO) {
                //3
                Log.i(TAG, "Audio Recording Error");
            } else if(error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                //9
                Log.i(TAG, "Insufficient Permissions");
            } else if(error == SpeechRecognizer.ERROR_NETWORK) {
                //2
                Log.i(TAG, "Network Issues");
            } else if(error == SpeechRecognizer.ERROR_NO_MATCH) {
                //7
                Log.i(TAG, "Speech cannot be matched to text");
            } else if(error == SpeechRecognizer.ERROR_SERVER) {
                //4
                Log.i(TAG, "Server Errors");
            } else if(error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) {
                //1
                Log.i(TAG, "Network Timeout...");
            }
            replaceListener();
        }

        @SuppressLint("ResourceAsColor")
        @SuppressWarnings("unchecked")
        @Override
        public void onResults(Bundle results) {
            Log.d(TAG, "oR " + results.get(SpeechRecognizer.RESULTS_RECOGNITION).toString());
            ArrayList<String> pR = (ArrayList<String>) results.get(SpeechRecognizer.RESULTS_RECOGNITION);
            String listString = "";
            for (String s : pR) {
                listString += s + " ";
            }
            Log.i(TAG, "Completing Action: '" + listString + "'");
            AudioManager audio = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
            audio.playSoundEffect(Sounds.SUCCESS);
            if(listString.contains("switch") && listString.contains("to") || listString.contains("go")) {
                Log.i(TAG, "Switch to detected...");
                if (listString.contains("axial")) {
                    Log.i(TAG, "Send Q");
                    onKeyDown(KeyEvent.KEYCODE_Q, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Q));
                }
                if (listString.contains("sagittal") || listString.contains("San") && listString.contains("Diego")) {
                    Log.i(TAG, "Send W");
                    onKeyDown(KeyEvent.KEYCODE_W, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_W));
                }
                if (listString.contains("coronal") || listString.contains("Corona")) {
                    Log.i(TAG, "Send E");
                    onKeyDown(KeyEvent.KEYCODE_E, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_E));
                }
                if (listString.contains("full") && listString.contains("screen")) {
                    Log.i(TAG, "Send R");
                    onKeyDown(KeyEvent.KEYCODE_R, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_R));
                }
            }
            //Resume speech
            restartListener();
        }

        @SuppressLint("ResourceAsColor")
        @SuppressWarnings("unchecked")

        @Override
        public void onPartialResults(Bundle partialResults) {
            Log.d(TAG, "oPR " + partialResults.get(SpeechRecognizer.RESULTS_RECOGNITION).toString());
            ArrayList<String> pR = (ArrayList<String>) partialResults.get(SpeechRecognizer.RESULTS_RECOGNITION);
            String listString = "";
            for (String s : pR) {
                listString += s + " ";
            }
            Log.i(TAG, '"' + listString + '"');
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
            Log.d(TAG, "Recognition Listener onEvent " + eventType + " and " + params.toString());
        }
    }

    /**
     * This starts the voice recognition library implemented using pocketsphinx
     */

   /* private static final String KWS_SEARCH = "wakeup";
    private static final String MENU_SEARCH = "menu";
    private static final String KEYPHRASE = "switch to";

    private static final String KWS_SAGITTAL = "sagittal";
    private static final String KWS_AXIAL = "axial";
    private static final String KWS_CORONAL = "coronal";
    private static final String KWS_FULL = "full screen";
//    private static final String KWS_NEXT = "next";
//    private static final String KWS_EXIT = "exit";
//    private static final String KWS_QUIT = "quit";


    private static SpeechRecognizer recognizer;*/

   /* public void startRecognition() {

        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... params) {
                try {
                    Assets assets = new Assets(Game.this);
                    File assetDir = assets.syncAssets();
                    setupRecognizer(assetDir);
                } catch (IOException e) {
                    return e;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Exception result) {
                if (result != null) {
//                    Toast.makeText(Game.this, "Failed to make recognizer:" + result, Toast.LENGTH_LONG).show();
                    Log.i(TAG, "Failed to make recognizer: " + result.toString());
                } else {
                    switchSearch(KWS_SEARCH);
                }
            }
        }.execute();
    }

    @Override
    public void onResult(Hypothesis hypothesis) {
        Log.i(TAG, "onResult");
        if (hypothesis != null) {
            Log.i(TAG, "onResult: " + hypothesis.getHypstr());
            String text = hypothesis.getHypstr();
            if (text.equals(KWS_SAGITTAL)) {
                Log.i(TAG, "press Q");
                onKeyDown(KeyEvent.KEYCODE_Q, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Q));
            } else if (text.equals(KWS_AXIAL)) {
                onKeyDown(KeyEvent.KEYCODE_W, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_W));
            } else if (text.equals(KWS_CORONAL)) {
                onKeyDown(KeyEvent.KEYCODE_E, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_E));
            } else if (text.equals(KWS_FULL)) {
                onKeyDown(KeyEvent.KEYCODE_R, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_R));
            }
//            else if(text.equals(KWS_EXIT) || text.equals(KWS_QUIT)) {
//                onKeyDown(KeyEvent.KEYCODE_T, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_T));
//                exitApp();
//            } else if(text.equals(KWS_NEXT)){
//                conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT);
//                conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
//            }
        }
    }

    @Override
    protected void onPause() {
        if(recognizer != null){
            recognizer.stop();
        }
        stopConnection();
        super.onPause();
    }

    @Override
    public void onBeginningOfSpeech() {
        Log.i(TAG,"onBeginningOfSpeech()");
    }

    @Override
    public void onEndOfSpeech() {
        Log.i(TAG, "onEndOfSpeech()");
        if (MENU_SEARCH.equals(recognizer.getSearchName()))
            switchSearch(KWS_SEARCH);
    }

    @Override
    public void onPartialResult(Hypothesis hypothesis) {
        Log.i(TAG,"onPartialResult()");
        String text = hypothesis.getHypstr();
        if (hypothesis.getHypstr().equals(KEYPHRASE)) {
            switchSearch(MENU_SEARCH);
        } else if (hypothesis.getHypstr().equals(MENU_SEARCH)){
            if (text.equals(KWS_SAGITTAL)) {
                onKeyDown(KeyEvent.KEYCODE_Q, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Q));
            } else if (text.equals(KWS_AXIAL)) {
                onKeyDown(KeyEvent.KEYCODE_W, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_W));
            } else if (text.equals(KWS_CORONAL)) {
                onKeyDown(KeyEvent.KEYCODE_E, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_E));
            } else if (text.equals(KWS_FULL)) {
                onKeyDown(KeyEvent.KEYCODE_R, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_R));
            }
        }
    }

    private void switchSearch(String searchName) {
        Log.i(TAG, "switchSearch");
        if(recognizer != null){
            recognizer.stop();
            recognizer.startListening(searchName);
        }
    }

    private void setupRecognizer(File assetsDir) {
        File modelsDir = new File(assetsDir, "models");
        float error = 1e-20f;
        recognizer = defaultSetup()
                .setAcousticModel(new File(modelsDir, "hmm/en-us-semi"))
                .setDictionary(new File(modelsDir, "dict/cmu07a.dic"))
                .setRawLogDir(assetsDir).setKeywordThreshold(error)
                .getRecognizer();
        recognizer.addListener(this);

        // Create keyword-activation search.
        recognizer.addKeyphraseSearch(KWS_SEARCH, KEYPHRASE);

        // Create grammar-based searches.
        File menuGrammar = new File(modelsDir, "grammar/menu.gram");
        recognizer.addGrammarSearch(MENU_SEARCH, menuGrammar);
    }*/

}

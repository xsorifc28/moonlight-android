package com.limelight.preferences;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;

import com.limelight.computers.ComputerManagerService;
import com.limelight.R;
import com.limelight.utils.Dialog;
import com.limelight.utils.SpinnerDialog;
import com.limelight.utils.UiHelper;

import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.widget.Toast;

import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;

import static edu.cmu.pocketsphinx.SpeechRecognizerSetup.defaultSetup;


public class AddComputerManually extends Activity implements RecognitionListener {

    private static final String TAG = "AddComputerManually";
    private static final String TAG2 = TAG + "-Spx";

    //private static String IP = "10.0.1.16";

    private TextView hostText;
    private ComputerManagerService.ComputerManagerBinder managerBinder;
    private final LinkedBlockingQueue<String> computersToAdd = new LinkedBlockingQueue<String>();
    private Thread addThread;
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, final IBinder binder) {
            managerBinder = ((ComputerManagerService.ComputerManagerBinder)binder);
            startAddThread();
        }

        public void onServiceDisconnected(ComponentName className) {
            joinAddThread();
            managerBinder = null;
        }
    };

    private void doAddPc(String host) {
        String msg;
        boolean finish = false;

        SpinnerDialog dialog = SpinnerDialog.displayDialog(this, getResources().getString(R.string.title_add_pc),
            getResources().getString(R.string.msg_add_pc), false);

        try {
            InetAddress addr = InetAddress.getByName(host);

            if (!managerBinder.addComputerBlocking(addr)){
                msg = getResources().getString(R.string.addpc_fail);
            }
            else {
                msg = getResources().getString(R.string.addpc_success);
                finish = true;
            }
        } catch (UnknownHostException e) {
            msg = getResources().getString(R.string.addpc_unknown_host);
        }

        dialog.dismiss();

        final boolean toastFinish = finish;
        final String toastMsg = msg;
        AddComputerManually.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(AddComputerManually.this, toastMsg, Toast.LENGTH_LONG).show();

                if (toastFinish && !isFinishing()) {
                    // Close the activity
                    AddComputerManually.this.finish();
                }
            }
        });
    }

    private void startAddThread() {
        addThread = new Thread() {
            @Override
            public void run() {
                while (!isInterrupted()) {
                    String computer;

                    try {
                        computer = computersToAdd.take();
                    } catch (InterruptedException e) {
                        return;
                    }
                    doAddPc(computer);
                }
            }
        };
        addThread.setName("UI - AddComputerManually");
        addThread.start();
    }

    private void joinAddThread() {
        if (addThread != null) {
            addThread.interrupt();

            try {
                addThread.join();
            } catch (InterruptedException ignored) {}

            addThread = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        recognizer.cancel();
    }

    @Override
    protected  void onResume() {
        super.onResume();
        switchSearch(KWS_START);
    }

    @Override
    protected void onStop() {
        super.onStop();

        Dialog.closeDialogs();
        SpinnerDialog.closeDialogs(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        recognizer.cancel();
        recognizer.shutdown();

        if (managerBinder != null) {
            joinAddThread();
            unbindService(serviceConnection);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Start pocket-sphinx if IP is not manually added.
        String IP = "";
        if(!IP.equals("")) {
            computersToAdd.add(IP);
        } else {
            startRecognition();
        }

        String locale = PreferenceConfiguration.readPreferences(this).language;
        if (!locale.equals(PreferenceConfiguration.DEFAULT_LANGUAGE)) {
            Configuration config = new Configuration(getResources().getConfiguration());
            config.locale = new Locale(locale);
            getResources().updateConfiguration(config, getResources().getDisplayMetrics());
        }

        setContentView(R.layout.activity_add_computer_manually);

        UiHelper.notifyNewRootView(this);

        hostText = (TextView) findViewById(R.id.hostTextView);
        hostText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        hostText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                computersToAdd.add(hostText.getText().toString().trim());
            }
        });
        /*hostText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                if (actionId == EditorInfo.IME_ACTION_DONE ||
                        (keyEvent != null &&
                                keyEvent.getAction() == KeyEvent.ACTION_DOWN &&
                                keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    if (hostText.getText().length() == 0) {
                        Toast.makeText(AddComputerManually.this, getResources().getString(R.string.addpc_enter_ip), Toast.LENGTH_LONG).show();
                        return true;
                    }

                    computersToAdd.add(hostText.getText().toString().trim());
                } else if (actionId == EditorInfo.IME_ACTION_PREVIOUS) {
                    // This is how the Fire TV dismisses the keyboard
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(hostText.getWindowToken(), 0);
                    return false;
                }

                return false;
            }
        });*/

        // Bind to the ComputerManager service
        bindService(new Intent(AddComputerManually.this,
                ComputerManagerService.class), serviceConnection, Service.BIND_AUTO_CREATE);
    }

    // Pocket Sphinx enter ip
    private static final String KWS_START = "start";
    private static final String DIGITS_SEARCH = "digits";

    private static final String KEYPHRASE = "add computer";
    private static final String ONE = "one";
    private static final String TWO = "two";
    private static final String THREE = "three";
    private static final String FOUR = "four";
    private static final String FIVE = "five";
    private static final String SIX = "six";
    private static final String SEVEN = "seven";
    private static final String EIGHT = "eight";
    private static final String NINE = "nine";
    private static final String ZERO = "zero";
    private static final String DOT = "dot";
    private static final String POINT = "point";

    private static final String[] digits = new String[]{ONE, TWO, THREE, FOUR,
                                                     FIVE, SIX, SEVEN, EIGHT,
                                                     NINE, ZERO, DOT, POINT};


    private static SpeechRecognizer recognizer;

    public void startRecognition() {

        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... params) {
                try {
                    Assets assets = new Assets(AddComputerManually.this);
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
                    Log.i(TAG2, "Failed to make recognizer: " + result.toString());
                } else {
                    switchSearch(KWS_START);
                }
            }
        }.execute();
    }

    @Override
    public void onBeginningOfSpeech() {
        Log.i(TAG2, "onBeginningOfSpeech()");
    }

    @Override
    public void onEndOfSpeech() {
        Log.i(TAG2, "onEndOfSpeech()");
        if (DIGITS_SEARCH.equals(recognizer.getSearchName()))
            switchSearch(KWS_START);
    }

    @Override
    public void onPartialResult(Hypothesis hypothesis) {
        if (hypothesis == null)
            return;

        String text = hypothesis.getHypstr();
        Log.i(TAG2, "onPartialResult: " + text);
        if(text.equals(KEYPHRASE)) {
            switchSearch(DIGITS_SEARCH);
        } else if(Arrays.asList(digits).contains(text)) {
            Log.i(TAG2, "has numbers");
        }
    }

    @Override
    public void onResult(Hypothesis hypothesis) {
        hostText.setText("");
        if (hypothesis != null) {
            String text = hypothesis.getHypstr();
            String ipText = "";
            Log.i(TAG2, "onResult: " + text);
            Toast.makeText(getApplicationContext(), "Processing numbers", Toast.LENGTH_SHORT).show();
            String[] ipString = text.split(" ");
            for (String anIpString : ipString) {
                if (anIpString.equals(ONE)) {
                    ipText += "1";
                } else if (anIpString.equals(TWO)) {
                    ipText += "2";
                } else if (anIpString.equals(THREE)) {
                    ipText += "3";
                } else if (anIpString.equals(FOUR)) {
                    ipText += "4";
                } else if (anIpString.equals(FIVE)) {
                    ipText += "5";
                } else if (anIpString.equals(SIX)) {
                    ipText += "6";
                } else if (anIpString.equals(SEVEN)) {
                    ipText += "7";
                } else if (anIpString.equals(EIGHT)) {
                    ipText += "8";
                } else if (anIpString.equals(NINE)) {
                    ipText += "9";
                } else if (anIpString.equals(ZERO)) {
                    ipText += "0";
                } else if (anIpString.equals(DOT)) {
                    ipText += ".";
                } else if (anIpString.equals(POINT)) {
                    ipText += ".";
                }
            }
            Toast.makeText(getApplicationContext(), ipText, Toast.LENGTH_SHORT).show();
            hostText.setText(ipText);

        }
    }

    @Override
    public void onError(Exception e) {
        Log.i(TAG2, "PocketSphinx/onError: " + e.getMessage());
        recognizer.stop();
        recognizer.startListening(KWS_START);
    }

    @Override
    public void onTimeout() {
        Log.i(TAG2, "PocketSphinx/onTimeout: ");
        switchSearch(KWS_START);
    }

    private void switchSearch(String searchName) {
        Log.i(TAG2, "switch Search to " + searchName);
        if(recognizer != null){
            recognizer.stop();
            recognizer.startListening(searchName);
        }
    }

    private void setupRecognizer(File assetsDir) {
        File modelsDir = new File(assetsDir, "models");
        float error = 1e-20f;
        try {
            recognizer = defaultSetup()
                    .setAcousticModel(new File(modelsDir, "hmm/en-us-semi"))
                    .setDictionary(new File(modelsDir, "dict/cmu07a.dic"))
                    .setRawLogDir(assetsDir).setKeywordThreshold(error)
                    .getRecognizer();
        } catch (IOException e) {
            e.printStackTrace();
        }
        recognizer.addListener(this);

        // Create keyword-activation search.
        recognizer.addKeyphraseSearch(KWS_START, KEYPHRASE);

        // Create grammar-based searches.
        File digitsGrammar = new File(modelsDir, "grammar/digits.gram");
        recognizer.addGrammarSearch(DIGITS_SEARCH, digitsGrammar);
    }


}

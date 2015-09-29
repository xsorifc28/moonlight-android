/*
package com.limelight;

        import android.annotation.SuppressLint;
        import android.app.Activity;
        import android.content.Context;
        import android.content.Intent;
        import android.graphics.Color;
        import android.hardware.Sensor;
        import android.hardware.SensorEvent;
        import android.hardware.SensorEventListener;
        import android.hardware.SensorManager;
        import android.media.AudioManager;
        import android.os.Bundle;
        import android.os.Handler;
        import android.os.Message;
        import android.speech.RecognitionListener;
        import android.speech.RecognizerIntent;
        import android.speech.SpeechRecognizer;
        import android.speech.tts.TextToSpeech;
        import android.util.Log;
        import android.view.Gravity;
        import android.view.KeyEvent;
        import android.view.MotionEvent;
        import android.view.WindowManager;
        import android.webkit.WebSettings;
        import android.webkit.WebView;
        import android.widget.FrameLayout;
        import android.widget.GridLayout;
        import android.widget.LinearLayout;
        import android.widget.ProgressBar;
        import android.widget.TextView;

        import com.google.android.glass.app.Card;
        import com.google.android.glass.media.Sounds;
        import com.google.android.glass.touchpad.Gesture;
        import com.google.android.glass.touchpad.GestureDetector;

        import java.util.ArrayList;
        import java.util.List;

*/
/**
 * Created by N on 3/4/14.
 *//*

public class OpenPic extends Activity implements SensorEventListener {
    //private int refresh = (1000/2);
    private static final int SPEECH_REQUEST = 0;
    private boolean inSpeech = false;
    private TextView mListener;



    private String TAG = "Pokedex";

    private Bundle speechResults;
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //ALWAYS KEEP THE SCREEN ON. WE DON'T WANT IT TO DIM AND REQUIRE WAKING
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.webview);
        Log.d(TAG, "Setting View");
//        FrameLayout linearlayout = (FrameLayout) findViewById(R.id.webview_ll);


        createListener();
    }

    //When Glass goes to KitKat (API Level 19), switch to ACTION_VOICE_SEARCH_HANDS_FREE
    private void displaySpeechRecognizer() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Switch to View: ");
        startActivityForResult(intent, SPEECH_REQUEST);
        inSpeech = true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        if (requestCode == SPEECH_REQUEST && resultCode == RESULT_OK) {
            List<String> results = data.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS);
            final String spokenText = results.get(0);
            // Do something with spokenText.
            */
/*Card c1 = new Card(getBaseContext());
            c1.setText("You literally just said "+spokenText);
            setContentView(c1.toView());
            tts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    tts.speak("You literally just said " + spokenText, TextToSpeech.QUEUE_FLUSH, null);
                    Log.d("Pokedex", "TTS");
                }
            });*//*


            //Here we process it

        }
        inSpeech = false;
        super.onActivityResult(requestCode, resultCode, data);
    }
    public void switchView(String spokenText) {
        spokenText = spokenText.substring(1,spokenText.length()-1);
        Log.d(TAG, "Speech Input: "+spokenText);

//        LinearLayout linearlayout = new LinearLayout(getApplicationContext());
        if(spokenText.toLowerCase().contains("camera")) {
            Log.d("Pokedex", "Finding Camera Intent");
            Intent intent = new Intent(this, CameraPro.class);
            startActivityForResult(intent, 150);
            return;
        }
        if(spokenText.toLowerCase().contains("quit")) {
            finish();
            return;
        }

        //These are all WebView Options
        Boolean reload = false;
        if(spokenText.toLowerCase().contains("demo")) {
            demo = Integer.parseInt(spokenText.split(" ")[1]);
            reload = true;
        }
        if(spokenText.toLowerCase().contains("frame rate")) {
            framerate = Integer.parseInt(spokenText.split(" ")[2]);
            reload = true;
        }
        if(spokenText.toLowerCase().contains("ip")) {
            //Parse an IP address using speech
            String ipout = spokenText.substring(3);
            ipout = ipout.replaceAll("\\s", "");
            ipout = ipout.replaceAll("dot", ".");
            Log.d("Pokedex", "Trying to parse "+ipout);
            try {
                ip = ipout;
            } catch (Exception e) {
                Log.d("Pokedex", "Not a valid ip address");
            }
            Log.d("Pokedex", ip);
            reload = true;
        }
        Log.d("Pokedex", "Now looking into "+grabURL());

        mWebView = (WebView) findViewById(R.id.view);
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.getSettings().setAppCacheEnabled(false);
//        mWebView.getSettings().setAppCacheMaxSize(1);
        mWebView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        if(reload)
            mWebView.loadUrl(grabURL());
        mListener = (TextView) findViewById(R.id.speechlistener);

//        linearlayout.addView(mWebView);
    }
    public String grabURL(int d, int fr, String ipadd) {
        Log.d(TAG, "Nav to http://felkerdigitalmedia.com/pic.php?demo="+d+"&framerate="+fr+"&ip="+ipadd);
        return "http://felkerdigitalmedia.com/pic.php?demo="+d+"&framerate="+fr+"&ip="+ipadd;
    }
    public String grabURL() {
        return grabURL(demo, framerate, ip);
    }
    */
/**
     * SENSOR
     *//*

    @Override
    protected void onResume() {
        super.onResume();
//        mSensorManager.registerListener(this, mGyro, SensorManager.SENSOR_DELAY_NORMAL);
        restartListener();
    }

    @Override
    protected void onPause() {
        super.onPause();
//        mSensorManager.unregisterListener(this);
    }
    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do something here if sensor accuracy changes.
    }

    @Override
    public final void onSensorChanged(SensorEvent event) {
        // The light sensor returns a single value.
        // Many sensors return 3 values, one for each axis.
        float tilt = event.values[1];
        if(tilt > 11.75 && !inSpeech)
            displaySpeechRecognizer();
        //Log.d("Pokedex", event.values[0]+" "+event.values[1]+" "+event.values[2]);
        //if(tilt > 10.75)
//            Log.d("Pokedex", event.values[1]+" "+inSpeech);
        // Do something with this sensor value.
    }
    public void createListener() {
        mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        mRecognitionListener = new AbstractMainRecognitionListener();
        mSpeechRecognizer.setRecognitionListener( mRecognitionListener );
        mSpeechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        //mSpeechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");				// i18n
        mSpeechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        mSpeechIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        mSpeechIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 100);				// To loop every X results
    }
    public void startListener() {
        mSpeechRecognizer.startListening( mSpeechIntent );
    }
    public void destroyListener() {
        mSpeechRecognizer.cancel();
//        mSpeechRecognizer.stopListening();
        mSpeechRecognizer.destroy();
    }
    public void restartListener() {
        mSpeechRecognizer.cancel();
//        mSpeechRecognizer.stopListening();
        Handler handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                startListener();
            }
        };
        // sleeper time
        handler.sendEmptyMessageDelayed(0,200);
    }
    public void replaceListener() {
        mSpeechRecognizer.cancel();
//        mSpeechRecognizer.stopListening();
        Handler handlerP = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
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
            }
        };
        // sleeper time
        handlerP.sendEmptyMessageDelayed(0,150);


    }



    public class AbstractMainRecognitionListener implements RecognitionListener
    {
        @SuppressLint("ResourceAsColor")
        @Override
        public void onReadyForSpeech(Bundle params) {
            Log.d(TAG, "onReadyForSpeech");
            setListenerColor(R.color.blue);
            setListenerStatus("Start Speaking");
        }

        @SuppressLint("ResourceAsColor")
        @Override
        public void onBeginningOfSpeech() {
            Log.d(TAG, "onBeginningOfSpeech");
            setListenerColor(R.color.green);
            setListenerStatus("Speaking detected");
            speechResults = null;
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
            setListenerColor(R.color.gray);
            setListenerStatus("Processing...");
            */
/*if(speechResults == null) {
                setListenerStatus("No Speech Detected");
                Handler handler = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        super.handleMessage(msg);
                        restartListener();
                    }
                };
                // sleeper time
                handler.sendEmptyMessageDelayed(0,500);
            } else {
                setListenerStatus(mListener.getText() + " " + speechResults.get(SpeechRecognizer.RESULTS_RECOGNITION).toString());
            }*//*

        }

        @SuppressLint("ResourceAsColor")
        @Override
        public void onError(int error) {
            if(error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                return;
            Log.d(TAG, "onSpeechError "+error);
            AudioManager audio = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
            audio.playSoundEffect(Sounds.ERROR);

            setListenerColor(R.color.red);
            setListenerStatus("Error "+error);
            //Something went wrong
            if(error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                //8
                setListenerStatus("Recognizer Busy");
                replaceListener();
                return;
            } else if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                //OONO - 6
//                setListenerStatus("Speech Timeout");
                restartListener();
                return;
            } else if (error == SpeechRecognizer.ERROR_CLIENT) {
                //5
                setListenerStatus("Client Error");
                replaceListener();
                return;
            } else if(error == SpeechRecognizer.ERROR_AUDIO) {
                //3
                setListenerStatus("Audio Recording Error");
            } else if(error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                //9
                setListenerStatus("Insufficient Permissions");
            } else if(error == SpeechRecognizer.ERROR_NETWORK) {
                //2
                setListenerStatus("Network Issues");
            } else if(error == SpeechRecognizer.ERROR_NO_MATCH) {
                //7
                setListenerStatus("Speech cannot be matched to text");
            } else if(error == SpeechRecognizer.ERROR_SERVER) {
                //4
                setListenerStatus("Server Errors");
            } else if(error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) {
                //1
                setListenerStatus("Network Timeout...");
            }
            replaceListener();
        }

        @SuppressLint("ResourceAsColor")
        @Override
        public void onResults(Bundle results) {
            Log.d(TAG, "oR "+results.get(SpeechRecognizer.RESULTS_RECOGNITION).toString());
            setListenerColor(R.color.magenta);
            ArrayList<String> pR = (ArrayList<String>) results.get(SpeechRecognizer.RESULTS_RECOGNITION);
            String listString = "";
            for (String s : pR) {
                listString += s + " ";
            }
            setListenerStatus("Completing Action: '"+listString+"'");
            switchView(results.get(SpeechRecognizer.RESULTS_RECOGNITION).toString());
            AudioManager audio = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
            audio.playSoundEffect(Sounds.SUCCESS);
            //Resume speech
            restartListener();
        }

        @SuppressLint("ResourceAsColor")
        @Override
        public void onPartialResults(Bundle partialResults) {
            Log.d(TAG, "oPR "+partialResults.get(SpeechRecognizer.RESULTS_RECOGNITION).toString());
            setListenerColor(R.color.yellow);
            ArrayList<String> pR = (ArrayList<String>) partialResults.get(SpeechRecognizer.RESULTS_RECOGNITION);
            String listString = "";
            for (String s : pR) {
                listString += s + " ";
            }
            setListenerStatus('"'+listString+'"');
            speechResults = partialResults;
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
            Log.d(TAG, "Recognition Listener onEvent "+eventType+" and "+params.toString());
        }
    }

}
*/

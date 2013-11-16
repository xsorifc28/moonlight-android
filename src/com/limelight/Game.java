package com.limelight;

import com.limelight.nvstream.NvConnection;

import android.app.Activity;
import android.app.Service;
import android.content.ComponentCallbacks2;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;


public class Game extends Activity {
	private NvConnection conn;
	private GestureDetector gestureDetector;
	private InputHandler inHandler;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// Full-screen and don't let the display go off
		getWindow().setFlags(
				WindowManager.LayoutParams.FLAG_FULLSCREEN |
				WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
				WindowManager.LayoutParams.FLAG_FULLSCREEN |
				WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		
		// We don't want a title bar
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		
		// Inflate the content
		setContentView(R.layout.activity_game);

		//Create the connection
		SurfaceView sv = (SurfaceView) findViewById(R.id.surfaceView);
		conn = new NvConnection(Game.this.getIntent().getStringExtra("host"), Game.this, sv.getHolder().getSurface());
		
		// Listen for events on the game surface
		InputMethodManager imm = (InputMethodManager)this.getSystemService(Service.INPUT_METHOD_SERVICE);
		inHandler = new InputHandler(conn, imm, findViewById(R.id.surfaceView));
		gestureDetector = new GestureDetector(getApplicationContext(), inHandler);
		
		// Start the connection
		conn.start();
	}

	public void hideSystemUi() {
		runOnUiThread(new Runnable() {
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
		});
	}
	
	@Override
	public void onPause() {
		finish();
		super.onPause();
	}
	
	@Override
	public void onDestroy() {
		conn.stop();
		super.onDestroy();
	}
	
	@Override
	public void onTrimMemory(int trimLevel) {
		if (trimLevel >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
		{
			conn.trim();
		}
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (!inHandler.onKeyDown(keyCode, event)) {
			return super.onKeyDown(keyCode, event);
		}
		return true;
	}
	
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (!inHandler.onKeyUp(keyCode, event)) {
			return super.onKeyUp(keyCode, event);
		}
		return true;
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (!gestureDetector.onTouchEvent(event)) {
			inHandler.handleSingleTouch(event);
		}
		return true;
	}
}

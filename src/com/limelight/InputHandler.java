package com.limelight;

import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.NvControllerPacket;

import android.view.GestureDetector.OnGestureListener;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.inputmethod.InputMethodManager;

public class InputHandler implements OnGestureListener, OnTouchListener {
	short inputMap = 0x0000;
	short keyMap = 0x0000;
	private byte leftTrigger = 0x00;
	private byte rightTrigger = 0x00;
	private short rightStickX = 0x0000;
	private short rightStickY = 0x0000;
	private short leftStickX = 0x0000;
	private short leftStickY = 0x0000;
	private int lastTouchX = 0;
	private int lastTouchY = 0;
	private int lastMouseX = Integer.MIN_VALUE;
	private int lastMouseY = Integer.MIN_VALUE;
	private long keyboardLastShown = Long.MIN_VALUE;
	private boolean keyboardShown = false;
	private boolean hasMoved = false;
	
	private NvConnection conn;
	private KeyHandler keyHandler;
	private InputMethodManager imm;
	private View view;
	InputHandler(NvConnection conn, InputMethodManager imm, View view) {
		this.conn = conn;
		keyHandler = new KeyHandler(this);
		this.imm = imm;
		this.view = view;
	}
	
	void sendControllerInputPacket() {
		conn.sendControllerInput(inputMap, leftTrigger, rightTrigger,
				leftStickX, leftStickY, rightStickX, rightStickY);
	}
	
	void sendKeyboardInputPacket(byte keyDirection) {
		conn.sendKeyboardInput(keyMap, keyDirection);
	}
	
	private void updateMousePosition(int eventX, int eventY) {
		// Send a mouse move if we already have a mouse location
		// and the mouse coordinates change
		if (lastMouseX != Integer.MIN_VALUE &&
			lastMouseY != Integer.MIN_VALUE &&
			!(lastMouseX == eventX && lastMouseY == eventY))
		{
			conn.sendMouseMove((short)(eventX - lastMouseX),
					(short)(eventY - lastMouseY));
		}
		
		// Update pointer location for delta calculation next time
		lastMouseX = eventX;
		lastMouseY = eventY;
	}
	
	public void touchMoveEvent(int eventX, int eventY)
	{
		if (eventX != lastTouchX || eventY != lastTouchY)
		{
			hasMoved = true;
			conn.sendMouseMove((short)(eventX - lastTouchX),
					(short)(eventY - lastTouchY));
			
			lastTouchX = eventX;
			lastTouchY = eventY;
		}
	}
	
	public void touchDownEvent(int eventX, int eventY)
	{
		lastTouchX = eventX;
		lastTouchY = eventY;
		hasMoved = false;
	}
	
	public void touchUpEvent(int eventX, int eventY)
	{
		if (!hasMoved)
		{
			// We haven't moved so send a click

			// Lower the mouse button
			conn.sendMouseButtonDown();
			
			// We need to sleep a bit here because some games
			// do input detection by polling
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {}
			
			// Raise the mouse button
			conn.sendMouseButtonUp();
		}
	}
	
	/*
	 * Handles a single finger touch event
	 */
	public boolean handleSingleTouch(MotionEvent event) {
		if ((event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0)
		{
			// This case is for touch-based input devices
			if (event.getSource() == InputDevice.SOURCE_TOUCHSCREEN ||
					event.getSource() == InputDevice.SOURCE_STYLUS)
			{
				int eventX = (int)event.getX();
				int eventY = (int)event.getY();

				switch (event.getActionMasked())
				{
				case MotionEvent.ACTION_DOWN:
					touchDownEvent(eventX, eventY);
					break;
				case MotionEvent.ACTION_UP:
					touchUpEvent(eventX, eventY);
					break;
				case MotionEvent.ACTION_MOVE:
					touchMoveEvent(eventX, eventY);
					break;
				default:
					return false;
				}
				return true;
			}
			// This case is for mice
			else if (event.getSource() == InputDevice.SOURCE_MOUSE)
			{
				switch (event.getActionMasked())
				{
				case MotionEvent.ACTION_DOWN:
					conn.sendMouseButtonDown();
					break;
				case MotionEvent.ACTION_UP:
					conn.sendMouseButtonUp();
					break;
				default:
					return false;
				}
				return true;
			}
		}

		return false;
	}

	@Override
	public boolean onDown(MotionEvent e) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void onShowPress(MotionEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean onSingleTapUp(MotionEvent e) {
		return false;
	}

	@Override
	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
			float distanceY) {
		if (e2.getPointerCount() == 1) {
			handleSingleTouch(e2);
		} else if (e2.getPointerCount() == 3){
			if (distanceY > 20F && !keyboardShown) {
				if (System.currentTimeMillis() - 500 > keyboardLastShown) {
					imm.toggleSoftInputFromWindow(view.getApplicationWindowToken(), 0, 0);
					keyboardShown = true;
					keyboardLastShown = System.currentTimeMillis();
				}
			} else if (distanceY < -20F) {
				imm.hideSoftInputFromWindow(view.getApplicationWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
				keyboardShown = false;
			}
		}
		return true;
	}

	@Override
	public void onLongPress(MotionEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
			float velocityY) {
		// TODO Auto-generated method stub
		return false;
	}
	

	public boolean onGenericMotion(MotionEvent event) {
		InputDevice dev = event.getDevice();

		if (dev == null) {
			System.err.println("Unknown device");
			return false;
		}

		if ((event.getSource() & InputDevice.SOURCE_CLASS_JOYSTICK) != 0) {
			float LS_X = event.getAxisValue(MotionEvent.AXIS_X);
			float LS_Y = event.getAxisValue(MotionEvent.AXIS_Y);

			float RS_X, RS_Y, L2, R2;

			InputDevice.MotionRange leftTriggerRange = dev.getMotionRange(MotionEvent.AXIS_LTRIGGER);
			InputDevice.MotionRange rightTriggerRange = dev.getMotionRange(MotionEvent.AXIS_RTRIGGER);
			if (leftTriggerRange != null && rightTriggerRange != null)
			{
				// Ouya controller
				L2 = event.getAxisValue(MotionEvent.AXIS_LTRIGGER);
				R2 = event.getAxisValue(MotionEvent.AXIS_RTRIGGER);
				RS_X = event.getAxisValue(MotionEvent.AXIS_Z);
				RS_Y = event.getAxisValue(MotionEvent.AXIS_RZ);
			}
			else
			{
				InputDevice.MotionRange brakeRange = dev.getMotionRange(MotionEvent.AXIS_BRAKE);
				InputDevice.MotionRange gasRange = dev.getMotionRange(MotionEvent.AXIS_GAS);
				if (brakeRange != null && gasRange != null)
				{
					// Moga controller
					RS_X = event.getAxisValue(MotionEvent.AXIS_Z);
					RS_Y = event.getAxisValue(MotionEvent.AXIS_RZ);
					L2 = event.getAxisValue(MotionEvent.AXIS_BRAKE);
					R2 = event.getAxisValue(MotionEvent.AXIS_GAS);
				}
				else
				{
					// Xbox controller
					RS_X = event.getAxisValue(MotionEvent.AXIS_RX);
					RS_Y = event.getAxisValue(MotionEvent.AXIS_RY);
					L2 = (event.getAxisValue(MotionEvent.AXIS_Z) + 1) / 2;
					R2 = (event.getAxisValue(MotionEvent.AXIS_RZ) + 1) / 2;
				}
			}


			InputDevice.MotionRange hatXRange = dev.getMotionRange(MotionEvent.AXIS_HAT_X);
			InputDevice.MotionRange hatYRange = dev.getMotionRange(MotionEvent.AXIS_HAT_Y);
			if (hatXRange != null && hatYRange != null)
			{
				// Xbox controller D-pad
				float hatX, hatY;

				hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X);
				hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y);

				inputMap &= ~(NvControllerPacket.LEFT_FLAG | NvControllerPacket.RIGHT_FLAG);
				inputMap &= ~(NvControllerPacket.UP_FLAG | NvControllerPacket.DOWN_FLAG);
				if (hatX < -0.5) {
					inputMap |= NvControllerPacket.LEFT_FLAG;
				}
				if (hatX > 0.5) {
					inputMap |= NvControllerPacket.RIGHT_FLAG;
				}
				if (hatY < -0.5) {
					inputMap |= NvControllerPacket.UP_FLAG;
				}
				if (hatY > 0.5) {
					inputMap |= NvControllerPacket.DOWN_FLAG;
				}
			}

			leftStickX = (short)Math.round(LS_X * 0x7FFF);
			leftStickY = (short)Math.round(-LS_Y * 0x7FFF);

			rightStickX = (short)Math.round(RS_X * 0x7FFF);
			rightStickY = (short)Math.round(-RS_Y * 0x7FFF);

			leftTrigger = (byte)Math.round(L2 * 0xFF);
			rightTrigger = (byte)Math.round(R2 * 0xFF);

			sendControllerInputPacket();
			return true;
		}
		else if ((event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0)
		{	
			// Send a mouse move update (if neccessary)
			updateMousePosition((int)event.getX(), (int)event.getY());
			return true;
		}
	    
	    return false;
	}
	
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		return keyHandler.onKeyDown(keyCode, event);
	}
	
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		return keyHandler.onKeyUp(keyCode, event);
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		// TODO Auto-generated method stub
		return false;
	}

}

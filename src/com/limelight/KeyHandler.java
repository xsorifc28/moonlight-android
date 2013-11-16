package com.limelight;

import com.limelight.nvstream.input.NvControllerPacket;

import android.view.InputDevice;
import android.view.KeyEvent;

public class KeyHandler {
	InputHandler inHandler;
	
	KeyHandler(InputHandler inHandler) {
		this.inHandler = inHandler;
	}
	
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		// Skip keyboard and virtual button events
		if (event.getSource() == InputDevice.SOURCE_KEYBOARD)
			return false;

		switch (keyCode) {
		case KeyEvent.KEYCODE_BUTTON_START:
		case KeyEvent.KEYCODE_MENU:
			inHandler.inputMap |= NvControllerPacket.PLAY_FLAG;
			break;
		case KeyEvent.KEYCODE_BACK:
		case KeyEvent.KEYCODE_BUTTON_SELECT:
			inHandler.inputMap |= NvControllerPacket.BACK_FLAG;
			break;
		case KeyEvent.KEYCODE_DPAD_LEFT:
			inHandler.inputMap |= NvControllerPacket.LEFT_FLAG;
			break;
		case KeyEvent.KEYCODE_DPAD_RIGHT:
			inHandler.inputMap |= NvControllerPacket.RIGHT_FLAG;
			break;
		case KeyEvent.KEYCODE_DPAD_UP:
			inHandler.inputMap |= NvControllerPacket.UP_FLAG;
			break;
		case KeyEvent.KEYCODE_DPAD_DOWN:
			inHandler.inputMap |= NvControllerPacket.DOWN_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_B:
			inHandler.inputMap |= NvControllerPacket.B_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_A:
			inHandler.inputMap |= NvControllerPacket.A_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_X:
			inHandler.inputMap |= NvControllerPacket.X_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_Y:
			inHandler.inputMap |= NvControllerPacket.Y_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_L1:
			inHandler.inputMap |= NvControllerPacket.LB_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_R1:
			inHandler.inputMap |= NvControllerPacket.RB_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_THUMBL:
			inHandler.inputMap |= NvControllerPacket.LS_CLK_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_THUMBR:
			inHandler.inputMap |= NvControllerPacket.RS_CLK_FLAG;
			break;
		default:
			return false;
		}

		// We detect back+start as the special button combo
		if ((inHandler.inputMap & NvControllerPacket.BACK_FLAG) != 0 &&
				(inHandler.inputMap & NvControllerPacket.PLAY_FLAG) != 0)
		{
			inHandler.inputMap &= ~(NvControllerPacket.BACK_FLAG | NvControllerPacket.PLAY_FLAG);
			inHandler.inputMap |= NvControllerPacket.SPECIAL_BUTTON_FLAG;
		}

		inHandler.sendControllerInputPacket();
		return true;
	}

	public boolean onKeyUp(int keyCode, KeyEvent event) {
		// Skip keyboard and virtual button events
		if (event.getSource() == InputDevice.SOURCE_KEYBOARD)
			return false;

		switch (keyCode) {
		case KeyEvent.KEYCODE_BUTTON_START:
		case KeyEvent.KEYCODE_MENU:
			inHandler.inputMap &= ~NvControllerPacket.PLAY_FLAG;
			break;
		case KeyEvent.KEYCODE_BACK:
		case KeyEvent.KEYCODE_BUTTON_SELECT:
			inHandler.inputMap &= ~NvControllerPacket.BACK_FLAG;
			break;
		case KeyEvent.KEYCODE_DPAD_LEFT:
			inHandler.inputMap &= ~NvControllerPacket.LEFT_FLAG;
			break;
		case KeyEvent.KEYCODE_DPAD_RIGHT:
			inHandler.inputMap &= ~NvControllerPacket.RIGHT_FLAG;
			break;
		case KeyEvent.KEYCODE_DPAD_UP:
			inHandler.inputMap &= ~NvControllerPacket.UP_FLAG;
			break;
		case KeyEvent.KEYCODE_DPAD_DOWN:
			inHandler.inputMap &= ~NvControllerPacket.DOWN_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_B:
			inHandler.inputMap &= ~NvControllerPacket.B_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_A:
			inHandler.inputMap &= ~NvControllerPacket.A_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_X:
			inHandler.inputMap &= ~NvControllerPacket.X_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_Y:
			inHandler.inputMap &= ~NvControllerPacket.Y_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_L1:
			inHandler.inputMap &= ~NvControllerPacket.LB_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_R1:
			inHandler.inputMap &= ~NvControllerPacket.RB_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_THUMBL:
			inHandler.inputMap &= ~NvControllerPacket.LS_CLK_FLAG;
			break;
		case KeyEvent.KEYCODE_BUTTON_THUMBR:
			inHandler.inputMap &= ~NvControllerPacket.RS_CLK_FLAG;
			break;
		default:
			return false;
		}

		// If one of the two is up, the special button comes up too
		if ((inHandler.inputMap & NvControllerPacket.BACK_FLAG) == 0 ||
				(inHandler.inputMap & NvControllerPacket.PLAY_FLAG) == 0)
		{
			inHandler.inputMap &= ~NvControllerPacket.SPECIAL_BUTTON_FLAG;
		}

		inHandler.sendControllerInputPacket();
		return true;
	}

}

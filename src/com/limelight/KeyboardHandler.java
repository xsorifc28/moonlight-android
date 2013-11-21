package com.limelight;

import com.limelight.nvstream.input.NvKeyboardPacket;

import android.view.KeyEvent;

public class KeyboardHandler {
	InputHandler inHandler;
	
	KeyboardHandler(InputHandler inHandler) {
		this.inHandler = inHandler;
	}
	
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		System.out.println("key down: " + keyCode + " \t " + (char)event.getUnicodeChar());
		System.out.printf("unicode char: %x\n", event.getUnicodeChar());
		if (KeyEvent.KEYCODE_A <= keyCode && keyCode <= KeyEvent.KEYCODE_Z) {
			inHandler.keyMap = (short) ((short)0x8041 + (short)(keyCode - KeyEvent.KEYCODE_A));
		}
		
		inHandler.sendKeyboardInputPacket(NvKeyboardPacket.KEY_DOWN);
		return false;
	}
	
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		System.out.println("key up: " + keyCode + " \t " + event.getKeyCharacterMap().getDisplayLabel(keyCode));
		if (KeyEvent.KEYCODE_A <= keyCode && keyCode <= KeyEvent.KEYCODE_Z) {
			inHandler.keyMap = (short) ((short)0x8041 + (short)(keyCode - KeyEvent.KEYCODE_A));
		}
		inHandler.sendKeyboardInputPacket(NvKeyboardPacket.KEY_UP);
		
		return false;
	}
}

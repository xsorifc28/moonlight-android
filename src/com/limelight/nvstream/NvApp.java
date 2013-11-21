package com.limelight.nvstream;

import java.io.Serializable;

public class NvApp implements Serializable {

	private static final long serialVersionUID = -3291477915666012986L;
	private String appName;
	private int appId;
	private boolean isRunning;
	
	public void setAppName(String appName) {
		this.appName = appName;
	}
	
	public void setAppId(String appId) {
		this.appId = Integer.parseInt(appId);
	}
	
	public void setIsRunning(String isRunning) {
		this.isRunning = isRunning.equals("1");
	}
	
	public String getAppName() {
		return this.appName;
	}
	
	public int getAppId() {
		return this.appId;
	}
	
	public boolean getIsRunning() {
		return this.isRunning;
	}
	
	@Override
	public String toString() {
		return this.appName;
	}
}

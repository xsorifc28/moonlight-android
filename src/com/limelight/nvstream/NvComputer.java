package com.limelight.nvstream;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import org.xmlpull.v1.XmlPullParserException;
import android.util.Log;

public class NvComputer implements Serializable {
	private static final long serialVersionUID = -653368538349941148L;
	
	private String hostname;
	private InetAddress ipAddress;
	private String ipAddressString;
	private int state;
	private int numOfApps;
	private String gpuType;
	private String mac;
	private UUID uniqueID;
	
	private LinkedList<NvApp> appList;
	
	
	private int sessionID;
	private boolean pairState;
	private boolean isBusy;
	
	public NvComputer(String hostname, InetAddress ipAddress, int state, int numOfApps, String gpuType, String mac, UUID uniqueID) {
		this.hostname = hostname;
		this.ipAddress = ipAddress;
		this.ipAddressString = this.ipAddress.getHostAddress();
		this.state = state;
		this.numOfApps = numOfApps;
		this.gpuType = gpuType;
		this.mac = mac;
		this.uniqueID = uniqueID;
		
		this.appList = new LinkedList<NvApp>();
		
		//this.updatePairState();
	}
	
	public NvHTTP nvHTTP() {
        try {
			return new NvHTTP(this.ipAddressString, NvConnection.getMacAddressString());
		} catch (SocketException e) {
			Log.e("NvComputer Constructor", "Unable to get MAC Address " + e.getMessage());
			return new NvHTTP(this.ipAddressString, "00:00:00:00:00:00");
		}
	}

	public String getHostname() {
		return this.hostname;
	}
	
	public InetAddress getIpAddress() {
		return this.ipAddress;
	}
	
	public String getIpAddressString() {
		return this.ipAddressString;
	}

	public int getState() {
		return this.state;
	}

	public int getNumOfApps() {
		return this.numOfApps;
	}

	public String getGpuType() {
		return this.gpuType;
	}

	public String getMac() {
		return this.mac;
	}

	public UUID getUniqueID() {
		return this.uniqueID;
	}
		
	public void updateAfterPairQuery(int sessionID, boolean paired, boolean isBusy) {
		this.sessionID = sessionID;
		this.pairState = paired;
		this.isBusy = isBusy;
	}
	
	public int getSessionID() {
		return this.sessionID;
	}
	
	public void updatePairState() {
				try {
					Log.d("NvComputer UpdatePairState", "Blocking on getPairState");
					NvComputer.this.pairState = NvComputer.this.nvHTTP().getPairState();
					Log.d("NvComputer UpdatePairState", "Done Blocking on getPairState");
					
					if (NvComputer.this.pairState == true) {
						Log.v("NvComputer UpdatePairState", "We are paired with the computer.");
						NvComputer.this.getSessionIDFromServer();
					}
					
				} catch (IOException e) {
					Log.e("NvComputer UpdatePairState", "Unable to get Pair State " + e.getMessage());
					NvComputer.this.pairState = false;
				} catch (XmlPullParserException e) {
					Log.e("NvComputer UpdatePairState", "Unable to get Pair State " + e.getMessage());
					
					e.printStackTrace();
					NvComputer.this.pairState = false;
				}
	}
	
	public void getSessionIDFromServer() {
				try {
					Log.e("NvComputer GetSessionIDFromServer", "Blocking on getSessionID");
					NvComputer.this.sessionID = NvComputer.this.nvHTTP().getSessionId();
					Log.e("NvComputer GetSessionIDFromServer", "Passed getSessionID");
					Log.e("NvComputer GetSessionIDFromServer", "Got the session ID of " + NvComputer.this.sessionID);
					
					NvComputer.this.getAppListFromServer();
				} catch (IOException e) {
					Log.e("NvComputer GetSessionIDFromServer", "Unable to get Session ID " + e.getMessage());
					NvComputer.this.sessionID = 0;
				} catch (XmlPullParserException e) {
					Log.e("NvComputer GetSessionIDFromServer", "Unable to get Session ID " + e.getMessage());
					NvComputer.this.sessionID = 0;
				}
	}
	
	public void getAppListFromServer() {
		
				if (NvComputer.this.sessionID != 0) {
					try {
						Log.d("NvComputer GetAppListFromServer", "Blocking on getAppList");
						NvComputer.this.appList = NvComputer.this.nvHTTP().getAppList(NvComputer.this.sessionID);
						Log.d("NvComputer GetAppListFromServer", "Passed getAppList");
					} catch (IOException e) {
						Log.e("NvComputer GetAppListFromServer", "Unable to get Application List " + e.getMessage());
					} catch (XmlPullParserException e) {
						Log.e("NvComputer GetAppListFromServer", "Unable to get Application List " + e.getMessage());
					}
				}		
	}
	
	public boolean getPairState() {
		return this.pairState;
	}
	
	public boolean getIsBusy() {
		return this.isBusy;
	}
	
	public int hashCode() {
		if (this.uniqueID == null) {
			return -1;
		} else {
			return this.uniqueID.hashCode();
		}
	}
	
	public String toString() {
		/*StringBuilder returnStringBuilder = new StringBuilder();
		returnStringBuilder.append("NvComputer 0x");
		returnStringBuilder.append(Integer.toHexString(this.hashCode()).toUpperCase(Locale.getDefault()));
		returnStringBuilder.append("\n|- Hostname: ");
		returnStringBuilder.append(this.hostname);
		returnStringBuilder.append("\n|- IP Address: ");
		returnStringBuilder.append(this.ipAddressString);
		returnStringBuilder.append("\n|- Computer State: ");
		returnStringBuilder.append(this.state);
		returnStringBuilder.append("\n|- Number of Apps: ");
		returnStringBuilder.append(this.numOfApps);
		returnStringBuilder.append("\n|- GPU: ");
		returnStringBuilder.append(this.gpuType);
		returnStringBuilder.append("\n|- MAC: ");
		returnStringBuilder.append(this.mac);
		returnStringBuilder.append("\n|- UniqueID: ");
		returnStringBuilder.append(this.uniqueID);
		returnStringBuilder.append("\n|- Pair State: ");
		returnStringBuilder.append(this.pairState);
		returnStringBuilder.append("\n|- Session ID: ");
		returnStringBuilder.append(this.sessionID);
		
		for (NvApp currentApp : this.appList) {
			returnStringBuilder.append("\n|------ Application: `" + currentApp.getAppName() + "`, Application ID: " + currentApp.getAppId() + ", isRunning: " + currentApp.getIsRunning());
		}
		
		returnStringBuilder.append("\n");
		return returnStringBuilder.toString();*/
		return this.hostname;  
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof UUID) {
			return this.uniqueID.equals(obj);
		} else {
			return false;
		}
	}
	
	public List<NvApp> getApps() {
		return Collections.unmodifiableList(this.appList);
	}
	
	public NvApp checkIfRunning() {
		for (NvApp currentApp : this.appList) {
			if (currentApp.getIsRunning()) {
				return currentApp;
			}
		}
		
		return null;
	}
}

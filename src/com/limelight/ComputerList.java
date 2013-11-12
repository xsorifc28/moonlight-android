package com.limelight;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import com.limelight.nvstream.NvComputer;
import com.limelight.nvstream.NvmDNS;

import android.os.Bundle;
import android.os.StrictMode;
import android.app.Activity;
import android.content.Context;
import android.view.Menu;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class ComputerList extends Activity {
	
	public class ComputerListAdapter extends ArrayAdapter<NvComputer> {
		
		private HashMap<NvComputer, Integer> mIdMap = new HashMap<NvComputer, Integer>();
		
		
		public ComputerListAdapter(Context context, int textViewResourceId, List<NvComputer> objects) {
			super(context, textViewResourceId, objects);
			int i = 0;
			for (NvComputer computer : objects) {
				this.mIdMap.put(computer, i);
				i++;
			}
		}
		
		@Override
		public boolean hasStableIds() {
			return true;
		}
	}
	
	
	
	
	
	private NvmDNS nvmDNS;
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_computer_list);
		
		final ListView listView = (ListView) findViewById(R.id.computerList);
		
		final LinkedList<NvComputer> computerList = new LinkedList<NvComputer>();
		
		try {
			StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();

			StrictMode.setThreadPolicy(policy); 
			NvComputer stuff = new NvComputer("Xenith", InetAddress.getLocalHost(), 2, 2, "Intel", "deadbeef", UUID.randomUUID());
			computerList.add(stuff);
		
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			
		}
		final ComputerListAdapter computerListAdapter = new ComputerListAdapter(this, android.R.layout.simple_list_item_1, computerList);
		
		
		
		listView.setAdapter(computerListAdapter);
				
		
		this.nvmDNS = new NvmDNS(this, computerList, computerListAdapter);
		this.nvmDNS.sendQuery();
		this.nvmDNS.waitForResponses();
		
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.computer_list, menu);
		return true;
	}

}

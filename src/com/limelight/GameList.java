package com.limelight;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import com.limelight.ComputerList.ComputerListAdapter;
import com.limelight.nvstream.NvApp;
import com.limelight.nvstream.NvComputer;
import com.limelight.nvstream.NvmDNS;

import android.os.Bundle;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import android.support.v4.app.NavUtils;

public class GameList extends Activity {
	public class GameListAdapter extends ArrayAdapter<NvApp> {
		
		private HashMap<NvApp, Integer> mIdMap = new HashMap<NvApp, Integer>();
		
		public GameListAdapter(Context context, int textViewResourceId, List<NvApp> objects) {
			super(context, textViewResourceId, objects);
			int i = 0;
			for (NvApp computer : objects) {
				this.mIdMap.put(computer, i);
				i++;
			}
		}
		
		@Override
		public boolean hasStableIds() {
			return true;
		}
	}

	private NvComputer computer;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_game_list);
		// Show the Up button in the action bar.
		setupActionBar();
		
		Intent intent = this.getIntent();
		computer = (NvComputer) intent.getSerializableExtra("NvComputer");
		this.setTitle(computer.getHostname());
		
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				GameList.this.computer.updatePairState();
				
				GameList.this.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						GameListAdapter gameListAdapter = new GameListAdapter(GameList.this, android.R.layout.simple_list_item_1, computer.getApps());
						ListView listView = (ListView) findViewById(R.id.gameList);
						listView.setAdapter(gameListAdapter);
					}
				});
				
			}
		}).start();
	}

	/**
	 * Set up the {@link android.app.ActionBar}.
	 */
	private void setupActionBar() {
		getActionBar().setDisplayHomeAsUpEnabled(true);

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.game_list, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			// This ID represents the Home or Up button. In the case of this
			// activity, the Up button is shown. Use NavUtils to allow users
			// to navigate up one level in the application structure. For
			// more details, see the Navigation pattern on Android Design:
			//
			// http://developer.android.com/design/patterns/navigation.html#up-vs-back
			//
			NavUtils.navigateUpFromSameTask(this);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

}

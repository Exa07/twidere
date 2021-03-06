/*
 * Twidere - Twitter client for Android
 * 
 * Copyright (C) 2012  Mariotaku Lee <mariotaku.lee@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mariotaku.twidere.activity;

import static org.mariotaku.twidere.util.Utils.cleanDatabasesByItemLimit;
import static org.mariotaku.twidere.util.Utils.getAccountIds;
import static org.mariotaku.twidere.util.Utils.getActivatedAccountIds;

import org.mariotaku.actionbarcompat.app.ActionBar;
import org.mariotaku.twidere.R;
import org.mariotaku.twidere.adapter.TabsAdapter;
import org.mariotaku.twidere.app.TwidereApplication;
import org.mariotaku.twidere.fragment.AccountsFragment;
import org.mariotaku.twidere.fragment.DiscoverFragment;
import org.mariotaku.twidere.fragment.HomeTimelineFragment;
import org.mariotaku.twidere.fragment.MentionsFragment;
import org.mariotaku.twidere.provider.TweetStore.Accounts;
import org.mariotaku.twidere.util.ArrayUtils;
import org.mariotaku.twidere.util.ServiceInterface;
import org.mariotaku.twidere.view.ExtendedViewPager;
import org.mariotaku.twidere.view.TabPageIndicator;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentManager.OnBackStackChangedListener;
import android.support.v4.app.FragmentTransaction;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

public class HomeActivity extends BaseActivity implements OnClickListener, OnBackStackChangedListener {

	private ExtendedViewPager mViewPager;
	private SharedPreferences mPreferences;
	private ActionBar mActionBar;
	private ProgressBar mProgress;
	private TabsAdapter mAdapter;
	private ImageButton mComposeButton;
	private ServiceInterface mInterface;
	private TabPageIndicator mIndicator;

	private BroadcastReceiver mStateReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (BROADCAST_REFRESHSTATE_CHANGED.equals(action)) {
				setSupportProgressBarIndeterminateVisibility(mProgressBarIndeterminateVisible);
			}
		}

	};

	public static final int PANE_LEFT = R.id.left_pane, PANE_RIGHT = R.id.right_pane;

	private boolean mProgressBarIndeterminateVisible = false;

	public void checkDefaultAccountSet() {
		long[] activated_ids = getActivatedAccountIds(this);
		long default_account_id = mPreferences.getLong(PREFERENCE_KEY_DEFAULT_ACCOUNT_ID, -1);
		if (default_account_id == -1 || !ArrayUtils.contains(activated_ids, default_account_id)) {
			if (activated_ids.length == 1) {
				mPreferences.edit().putLong(PREFERENCE_KEY_DEFAULT_ACCOUNT_ID, activated_ids[0]).commit();
				mIndicator.setPagingEnabled(true);
			} else if (activated_ids.length > 1) {
				mViewPager.setCurrentItem(mAdapter.getCount() - 1, false);
				mIndicator.setPagingEnabled(false);
				Toast.makeText(this, R.string.set_default_account_hint, Toast.LENGTH_LONG).show();
			}
		} else {
			mIndicator.setPagingEnabled(true);
		}
	}

	public boolean isDualPaneMode() {
		return findViewById(PANE_LEFT) instanceof ViewGroup && findViewById(PANE_RIGHT) instanceof ViewGroup;
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		ContentResolver resolver = getContentResolver();
		ContentValues values;
		switch (requestCode) {
			case REQUEST_SELECT_ACCOUNT: {
				if (resultCode == RESULT_OK) {
					if (intent == null || intent.getExtras() == null) {
						break;
					}
					Bundle bundle = intent.getExtras();
					if (bundle == null) {
						break;
					}
					long[] account_ids = bundle.getLongArray(INTENT_KEY_IDS);
					if (account_ids != null) {
						values = new ContentValues();
						values.put(Accounts.IS_ACTIVATED, 0);
						resolver.update(Accounts.CONTENT_URI, values, null, null);
						values = new ContentValues();
						values.put(Accounts.IS_ACTIVATED, 1);
						for (long account_id : account_ids) {
							String where = Accounts.USER_ID + " = " + account_id;
							resolver.update(Accounts.CONTENT_URI, values, where, null);
						}
					}
					checkDefaultAccountSet();
				} else if (resultCode == RESULT_CANCELED) {
					if (getActivatedAccountIds(this).length <= 0) {
						finish();
					} else {
						checkDefaultAccountSet();
					}
				}
				break;
			}
		}
		super.onActivityResult(requestCode, resultCode, intent);
	}

	@Override
	public void onBackStackChanged() {
		if (!isDualPaneMode()) return;
		FragmentManager fm = getSupportFragmentManager();
		Fragment fragment = fm.findFragmentById(PANE_LEFT);
		View main_view = findViewById(R.id.main);
		boolean left_pane_used = fragment != null && fragment.isAdded();
		main_view.setVisibility(left_pane_used ? View.GONE : View.VISIBLE);
		setPagingEnabled(!left_pane_used);
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.compose:
			case R.id.button_compose:
				startActivity(new Intent(INTENT_ACTION_COMPOSE));
				break;
		}

	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		mInterface = ((TwidereApplication) getApplication()).getServiceInterface();
		mPreferences = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		super.onCreate(savedInstanceState);
		boolean home_display_icon = getResources().getBoolean(R.bool.home_display_icon);
		boolean tab_display_label = getResources().getBoolean(R.bool.tab_display_label);
		setContentView(R.layout.main);
		mViewPager = (ExtendedViewPager) findViewById(R.id.pager);
		mComposeButton = (ImageButton) findViewById(R.id.button_compose);
		long[] account_ids = getAccountIds(this);

		if (account_ids.length <= 0) {
			startActivity(new Intent(INTENT_ACTION_TWITTER_LOGIN));
			finish();
			return;
		}

		Bundle bundle = getIntent().getExtras();
		if (bundle != null) {
			long[] refreshed_ids = bundle.getLongArray(INTENT_KEY_IDS);
			if (refreshed_ids != null) {
				mInterface.getHomeTimeline(refreshed_ids, null);
				mInterface.getMentions(refreshed_ids, null);
			}
		}
		mActionBar = getSupportActionBar();
		mActionBar.setCustomView(R.layout.home_tabs);
		mActionBar.setDisplayShowCustomEnabled(true);
		mActionBar.setDisplayShowHomeEnabled(home_display_icon);
		View view = mActionBar.getCustomView();
		mProgress = (ProgressBar) view.findViewById(android.R.id.progress);
		mIndicator = (TabPageIndicator) view.findViewById(android.R.id.tabs);
		mAdapter = new TabsAdapter(this, getSupportFragmentManager());
		mAdapter.addTab(HomeTimelineFragment.class, null, tab_display_label ? getString(R.string.home) : null,
				R.drawable.ic_tab_home);
		mAdapter.addTab(MentionsFragment.class, null, tab_display_label ? getString(R.string.mentions) : null,
				R.drawable.ic_tab_connect);
		mAdapter.addTab(DiscoverFragment.class, null, tab_display_label ? getString(R.string.discover) : null,
				R.drawable.ic_tab_discover);
		mAdapter.addTab(AccountsFragment.class, null, tab_display_label ? getString(R.string.me) : null,
				R.drawable.ic_tab_me);
		mViewPager.setAdapter(mAdapter);
		mViewPager.setOffscreenPageLimit(3);
		mIndicator.setViewPager(mViewPager);
		getSupportFragmentManager().addOnBackStackChangedListener(this);

		if (getActivatedAccountIds(this).length <= 0) {
			startActivityForResult(new Intent(INTENT_ACTION_SELECT_ACCOUNT), REQUEST_SELECT_ACCOUNT);
		} else {
			checkDefaultAccountSet();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu_home, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public void onDestroy() {
		// Delete unused items in databases.
		cleanDatabasesByItemLimit(this);
		super.onDestroy();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case MENU_HOME:
				navigateToTop();
				break;
			case MENU_COMPOSE:
				startActivity(new Intent(INTENT_ACTION_COMPOSE));
				break;
			case MENU_SELECT_ACCOUNT:
				startActivityForResult(new Intent(INTENT_ACTION_SELECT_ACCOUNT), REQUEST_SELECT_ACCOUNT);
				break;
			case MENU_SETTINGS:
				startActivity(new Intent(INTENT_ACTION_SETTINGS));
				break;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		final boolean bottom_actions = mPreferences.getBoolean(PREFERENCE_KEY_COMPOSE_BUTTON, false);
		final boolean leftside_compose_button = mPreferences.getBoolean(PREFERENCE_KEY_LEFTSIDE_COMPOSE_BUTTON, false);
		MenuItem composeItem = menu.findItem(MENU_COMPOSE);
		if (composeItem != null) {
			composeItem.setVisible(!bottom_actions);
		}
		if (mComposeButton != null) {
			mComposeButton.setVisibility(bottom_actions ? View.VISIBLE : View.GONE);
			if (bottom_actions) {
				FrameLayout.LayoutParams compose_lp = (FrameLayout.LayoutParams) mComposeButton.getLayoutParams();
				compose_lp.gravity = Gravity.BOTTOM | (leftside_compose_button ? Gravity.LEFT : Gravity.RIGHT);
				mComposeButton.setLayoutParams(compose_lp);
			}
		}
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public void onResume() {
		super.onResume();
		invalidateSupportOptionsMenu();
	}

	@Override
	public void onStart() {
		super.onStart();
		setSupportProgressBarIndeterminateVisibility(mProgressBarIndeterminateVisible);
		IntentFilter filter = new IntentFilter(BROADCAST_REFRESHSTATE_CHANGED);
		registerReceiver(mStateReceiver, filter);
	}

	@Override
	public void onStop() {
		unregisterReceiver(mStateReceiver);
		super.onStop();
	}

	public void setPagingEnabled(boolean enabled) {
		if (mIndicator != null) {
			mIndicator.setPagingEnabled(enabled);
			mIndicator.setEnabled(enabled);
		}
	}

	@Override
	public void setSupportProgressBarIndeterminateVisibility(boolean visible) {
		mProgressBarIndeterminateVisible = visible;
		mProgress.setVisibility(visible || mInterface.hasActivatedTask() ? View.VISIBLE : View.INVISIBLE);
	}

	public void showAtPane(int pane, Fragment fragment, boolean addToBackStack) {
		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		switch (pane) {
			case PANE_LEFT:
			case PANE_RIGHT: {
				ft.replace(pane, fragment);
				break;
			}
		}
		if (addToBackStack) {
			ft.addToBackStack(null);
		}
		ft.setTransitionStyle(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
		ft.commit();
	}

	@Override
	protected void onNewIntent(Intent intent) {
		Bundle bundle = intent.getExtras();
		if (bundle != null) {
			long[] refreshed_ids = bundle.getLongArray(INTENT_KEY_IDS);
			if (refreshed_ids != null) {
				mInterface.getHomeTimeline(refreshed_ids, null);
				mInterface.getMentions(refreshed_ids, null);
			}
		}
		super.onNewIntent(intent);
	}

	private void navigateToTop() {
		if (isDualPaneMode()) {
			getSupportFragmentManager().popBackStack();
		}
	}

}

package org.mariotaku.twidere.fragment;

import static org.mariotaku.twidere.util.Utils.formatToLongTimeString;
import static org.mariotaku.twidere.util.Utils.getActivatedAccountIds;
import static org.mariotaku.twidere.util.Utils.getMentionedNames;
import static org.mariotaku.twidere.util.Utils.getQuoteStatus;
import static org.mariotaku.twidere.util.Utils.getTwitterInstance;
import static org.mariotaku.twidere.util.Utils.isMyActivatedAccount;
import static org.mariotaku.twidere.util.Utils.isMyRetweet;
import static org.mariotaku.twidere.util.Utils.setMenuForStatus;

import org.mariotaku.popupmenu.MenuBar;
import org.mariotaku.popupmenu.MenuBar.OnMenuItemClickListener;
import org.mariotaku.twidere.R;
import org.mariotaku.twidere.app.TwidereApplication;
import org.mariotaku.twidere.provider.TweetStore;
import org.mariotaku.twidere.provider.TweetStore.Statuses;
import org.mariotaku.twidere.util.AutoLink;
import org.mariotaku.twidere.util.AutoLink.OnLinkClickListener;
import org.mariotaku.twidere.util.ParcelableStatus;
import org.mariotaku.twidere.util.ProfileImageLoader;
import org.mariotaku.twidere.util.ServiceInterface;
import org.mariotaku.twidere.util.StatusesCursorIndices;
import org.mariotaku.twidere.util.Utils;

import twitter4j.Relationship;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

public class ViewStatusFragment extends BaseFragment implements OnClickListener, OnMenuItemClickListener,
		OnLinkClickListener {

	private long mAccountId, mStatusId;

	public ServiceInterface mServiceInterface;
	private ContentResolver mResolver;
	private TextView mNameView, mScreenNameView, mTextView, mTimeAndSourceView, mInReplyToView;
	private ImageView mProfileImageView;
	private Button mFollowButton;
	private ImageButton mViewMapButton, mViewMediaButton;
	private View mProfileView, mFollowIndicator;
	private MenuBar mMenuBar;
	private ProgressBar mProgress;
	private FollowInfoTask mFollowInfoTask;
	private GetStatusTask mGetStatusTask;
	private ParcelableStatus mStatus;

	private BroadcastReceiver mStatusReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (BROADCAST_FRIENDSHIP_CHANGED.equals(action)) {
				showFollowInfo(true);
			} else if (BROADCAST_FAVORITE_CHANGED.equals(action)) {
				long status_id = intent.getLongExtra(INTENT_KEY_STATUS_ID, -1);
				if (status_id > 0 && status_id == mStatusId) {
					getStatus(true);
				}
			}
		}
	};

	private boolean mFollowInfoDisplayed = false;

	public void displayStatus(ParcelableStatus status) {
		if (status == null) return;
		mStatus = status;

		mMenuBar.inflate(R.menu.menu_status);
		setMenuForStatus(getActivity(), mMenuBar.getMenu(), status);
		mMenuBar.show();

		mNameView.setText(status.name != null ? status.name : "");
		mScreenNameView.setText(status.screen_name != null ? "@" + status.screen_name : "");
		if (status.text != null) {
			mTextView.setText(status.text);
		}
		AutoLink linkify = new AutoLink(mTextView);
		linkify.setOnLinkClickListener(this);
		linkify.addLinks(AutoLink.LINK_TYPE_MENTIONS);
		linkify.addLinks(AutoLink.LINK_TYPE_HASHTAGS);
		linkify.addLinks(AutoLink.LINK_TYPE_IMAGES);
		linkify.addLinks(AutoLink.LINK_TYPE_LINKS);
		mTextView.setMovementMethod(LinkMovementMethod.getInstance());
		boolean is_reply = status.in_reply_to_status_id > 0;
		String time = formatToLongTimeString(getActivity(), status.status_timestamp);
		mTimeAndSourceView.setText(Html.fromHtml(getString(R.string.time_source, time, status.source)));
		mTimeAndSourceView.setMovementMethod(LinkMovementMethod.getInstance());
		mInReplyToView.setVisibility(is_reply ? View.VISIBLE : View.GONE);
		if (is_reply) {
			mInReplyToView.setText(getString(R.string.in_reply_to, status.in_reply_to_screen_name));
		}
		mViewMapButton.setVisibility(status.location != null ? View.VISIBLE : View.GONE);
		mViewMediaButton.setVisibility(status.has_media ? View.VISIBLE : View.GONE);

		ProfileImageLoader imageloader = ((TwidereApplication) getActivity().getApplication()).getProfileImageLoader();
		imageloader.displayImage(status.profile_image_url, mProfileImageView);

	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {

		mServiceInterface = ((TwidereApplication) getActivity().getApplication()).getServiceInterface();
		mResolver = getContentResolver();
		super.onActivityCreated(savedInstanceState);
		Bundle bundle = getArguments();
		if (bundle != null) {
			mAccountId = bundle.getLong(INTENT_KEY_ACCOUNT_ID);
			mStatusId = bundle.getLong(INTENT_KEY_STATUS_ID);
		}
		View view = getView();
		mNameView = (TextView) view.findViewById(R.id.name);
		mScreenNameView = (TextView) view.findViewById(R.id.screen_name);
		mTextView = (TextView) view.findViewById(R.id.text);
		mProfileImageView = (ImageView) view.findViewById(R.id.profile_image);
		mTimeAndSourceView = (TextView) view.findViewById(R.id.time_source);
		mInReplyToView = (TextView) view.findViewById(R.id.in_reply_to);
		mInReplyToView.setOnClickListener(this);
		mFollowButton = (Button) view.findViewById(R.id.follow);
		mFollowButton.setOnClickListener(this);
		mFollowIndicator = view.findViewById(R.id.follow_indicator);
		mProfileView = view.findViewById(R.id.profile);
		mProfileView.setOnClickListener(this);
		mViewMapButton = (ImageButton) view.findViewById(R.id.view_map);
		mViewMapButton.setOnClickListener(this);
		mViewMediaButton = (ImageButton) view.findViewById(R.id.view_media);
		mViewMediaButton.setOnClickListener(this);
		mProgress = (ProgressBar) view.findViewById(R.id.progress);
		mMenuBar = (MenuBar) view.findViewById(R.id.menu_bar);
		mMenuBar.setOnMenuItemClickListener(this);
		getStatus(false);
	}

	@Override
	public void onClick(View view) {
		if (mStatus == null) return;
		switch (view.getId()) {
			case R.id.profile: {
				Utils.openUserProfile(getActivity(), mStatus.account_id, mStatus.user_id, null);
				break;
			}
			case R.id.follow: {
				ServiceInterface.getInstance(getActivity()).createFriendship(mAccountId, mStatus.user_id);
				break;
			}
			case R.id.in_reply_to: {
				Utils.openConversation(getActivity(), mStatus.account_id, mStatus.status_id);
				break;
			}
			case R.id.view_map: {
				if (mStatus.location != null) {
					Bundle bundle = new Bundle();
					bundle.putDouble(INTENT_KEY_LATITUDE, mStatus.location.getLatitude());
					bundle.putDouble(INTENT_KEY_LONGITUDE, mStatus.location.getLongitude());
					startActivity(new Intent(INTENT_ACTION_VIEW_MAP).putExtras(bundle));
				}
				break;
			}
		}

	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.view_status, container, false);
	}

	@Override
	public void onDestroyView() {
		if (mGetStatusTask != null) {
			mGetStatusTask.cancel(true);
		}
		if (mFollowInfoTask != null) {
			mFollowInfoTask.cancel(true);
		}

		super.onDestroyView();
	}

	@Override
	public void onLinkClick(String link, int type) {
		if (mStatus == null) return;
		switch (type) {
			case AutoLink.LINK_TYPE_MENTIONS: {
				Utils.openUserProfile(getActivity(), mStatus.account_id, -1, link);
				break;
			}
			case AutoLink.LINK_TYPE_HASHTAGS: {
				Utils.openTweetSearch(getActivity(), mStatus.account_id, link);
				break;
			}
			case AutoLink.LINK_TYPE_IMAGES: {
				Intent intent = new Intent(INTENT_ACTION_VIEW_IMAGE, Uri.parse(link));
				intent.setPackage(getActivity().getPackageName());
				startActivity(intent);
				break;
			}
			case AutoLink.LINK_TYPE_LINKS: {
				Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
				startActivity(intent);
				break;
			}
		}

	}

	@Override
	public boolean onMenuItemClick(MenuItem item) {
		if (mStatus == null) return false;
		String text_plain = mStatus.text_plain;
		String screen_name = mStatus.screen_name;
		String name = mStatus.name;
		switch (item.getItemId()) {
			case MENU_SHARE: {
				Intent intent = new Intent(Intent.ACTION_SEND);
				intent.setType("text/plain");
				intent.putExtra(Intent.EXTRA_TEXT, "@" + mStatus.screen_name + ": " + text_plain);
				startActivity(Intent.createChooser(intent, getString(R.string.share)));
				break;
			}
			case MENU_RETWEET: {
				if (isMyRetweet(getActivity(), mAccountId, mStatusId)) {
					mServiceInterface.cancelRetweet(mAccountId, mStatusId);
				} else {
					long id_to_retweet = mStatus.is_retweet && mStatus.retweet_id > 0 ? mStatus.retweet_id
							: mStatus.status_id;
					mServiceInterface.retweetStatus(mAccountId, id_to_retweet);
				}
				break;
			}
			case MENU_QUOTE: {
				Intent intent = new Intent(INTENT_ACTION_COMPOSE);
				Bundle bundle = new Bundle();
				bundle.putLong(INTENT_KEY_ACCOUNT_ID, mAccountId);
				bundle.putLong(INTENT_KEY_IN_REPLY_TO_ID, mStatusId);
				bundle.putString(INTENT_KEY_IN_REPLY_TO_SCREEN_NAME, screen_name);
				bundle.putString(INTENT_KEY_IN_REPLY_TO_NAME, name);
				bundle.putBoolean(INTENT_KEY_IS_QUOTE, true);
				bundle.putString(INTENT_KEY_TEXT, getQuoteStatus(getActivity(), screen_name, text_plain));
				intent.putExtras(bundle);
				startActivity(intent);
				break;
			}
			case MENU_REPLY: {
				Intent intent = new Intent(INTENT_ACTION_COMPOSE);
				Bundle bundle = new Bundle();
				bundle.putStringArray(INTENT_KEY_MENTIONS, getMentionedNames(screen_name, text_plain, false, true));
				bundle.putLong(INTENT_KEY_ACCOUNT_ID, mAccountId);
				bundle.putLong(INTENT_KEY_IN_REPLY_TO_ID, mStatusId);
				bundle.putString(INTENT_KEY_IN_REPLY_TO_SCREEN_NAME, screen_name);
				bundle.putString(INTENT_KEY_IN_REPLY_TO_NAME, name);
				intent.putExtras(bundle);
				startActivity(intent);
				break;
			}
			case MENU_FAV: {
				if (mStatus.is_favorite) {
					mServiceInterface.destroyFavorite(mAccountId, mStatusId);
				} else {
					mServiceInterface.createFavorite(mAccountId, mStatusId);
				}
				break;
			}
			case MENU_DELETE: {
				mServiceInterface.destroyStatus(mAccountId, mStatusId);
				break;
			}
			default:
				return false;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onStart() {
		super.onStart();
		IntentFilter filter = new IntentFilter();
		filter.addAction(BROADCAST_FRIENDSHIP_CHANGED);
		filter.addAction(BROADCAST_FAVORITE_CHANGED);
		if (getActivity() != null) {
			getActivity().registerReceiver(mStatusReceiver, filter);
		}
	}

	@Override
	public void onStop() {
		if (getActivity() != null) {
			getActivity().unregisterReceiver(mStatusReceiver);
		}
		super.onStop();
	}

	private void getStatus(boolean omit_intent_extra) {
		if (mGetStatusTask != null) {
			mGetStatusTask.cancel(true);
		}
		mGetStatusTask = new GetStatusTask(omit_intent_extra);
		mGetStatusTask.execute();

	}

	private void showFollowInfo(boolean force) {
		if (mFollowInfoDisplayed && !force) return;
		if (mFollowInfoTask != null) {
			mFollowInfoTask.cancel(true);
		}
		mFollowInfoTask = new FollowInfoTask();
		mFollowInfoTask.execute();
	}

	private class FollowInfoTask extends AsyncTask<Void, Void, Response<Boolean>> {

		@Override
		protected Response<Boolean> doInBackground(Void... params) {
			return isAllFollowing();
		}

		@Override
		protected void onPostExecute(Response<Boolean> result) {
			if (result.exception == null) {
				mFollowIndicator.setVisibility(result.value == null || result.value ? View.GONE : View.VISIBLE);
				if (result.value != null) {
					mFollowButton.setVisibility(result.value ? View.GONE : View.VISIBLE);
					mFollowInfoDisplayed = true;
				}
			}
			mProgress.setVisibility(View.GONE);
			super.onPostExecute(result);
			mFollowInfoTask = null;
		}

		@Override
		protected void onPreExecute() {
			mFollowIndicator.setVisibility(View.VISIBLE);
			mFollowButton.setVisibility(View.GONE);
			mProgress.setVisibility(View.VISIBLE);
			super.onPreExecute();
		}

		private Response<Boolean> isAllFollowing() {
			if (mStatus == null) return new Response<Boolean>(null, null);
			if (isMyActivatedAccount(getActivity(), mStatus.user_id)) return new Response<Boolean>(true, null);
			long[] ids = getActivatedAccountIds(getActivity());
			for (long id : ids) {
				Twitter twitter = getTwitterInstance(getActivity(), id, false);
				try {
					Relationship result = twitter.showFriendship(id, mStatus.user_id);
					if (!result.isSourceFollowingTarget()) return new Response<Boolean>(false, null);
				} catch (TwitterException e) {
					return new Response<Boolean>(null, e);
				}
			}
			return new Response<Boolean>(null, null);
		}
	}

	private class GetStatusTask extends AsyncTask<Void, Void, Response<ParcelableStatus>> {

		private final boolean omit_intent_extra;

		public GetStatusTask(boolean omit_intent_extra) {
			this.omit_intent_extra = omit_intent_extra;
		}

		@Override
		protected Response<ParcelableStatus> doInBackground(Void... params) {
			ParcelableStatus status = null;
			if (!omit_intent_extra) {
				status = getArguments().getParcelable(INTENT_KEY_STATUS);
				if (status != null) return new Response<ParcelableStatus>(status, null);

			}
			final String[] cols = Statuses.COLUMNS;
			final String where = Statuses.STATUS_ID + " = " + mStatusId;

			// Get status from databases.
			for (Uri uri : TweetStore.STATUSES_URIS) {
				if (status != null) return new Response<ParcelableStatus>(status, null);
				Cursor cur = mResolver.query(uri, cols, where, null, null);
				if (cur == null) {
					break;
				}

				if (cur.getCount() > 0) {
					cur.moveToFirst();
					status = new ParcelableStatus(cur, new StatusesCursorIndices(cur));
				}
				cur.close();
			}

			final Twitter twitter = getTwitterInstance(getActivity(), mAccountId, false);
			try {
				return new Response<ParcelableStatus>(new ParcelableStatus(twitter.showStatus(mStatusId), mAccountId,
						false), null);
			} catch (TwitterException e) {
				return new Response<ParcelableStatus>(null, e);
			}
		}

		@Override
		protected void onPostExecute(Response<ParcelableStatus> result) {
			if (result.value == null) {
			} else {
				displayStatus(result.value);
				showFollowInfo(false);
			}
			setProgressBarIndeterminateVisibility(false);
			super.onPostExecute(result);
		}

		@Override
		protected void onPreExecute() {
			setProgressBarIndeterminateVisibility(true);
			super.onPreExecute();
		}

	}

	private class Response<T> {
		public final T value;
		public final TwitterException exception;

		public Response(T value, TwitterException exception) {
			this.value = value;
			this.exception = exception;
		}
	}

}

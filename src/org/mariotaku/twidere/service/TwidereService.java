package org.mariotaku.twidere.service;

import static org.mariotaku.twidere.util.Utils.buildQueryUri;
import static org.mariotaku.twidere.util.Utils.getImagePathFromUri;
import static org.mariotaku.twidere.util.Utils.getRetweetId;
import static org.mariotaku.twidere.util.Utils.getTableId;
import static org.mariotaku.twidere.util.Utils.getTwitterInstance;
import static org.mariotaku.twidere.util.Utils.makeCachedUsersContentValues;
import static org.mariotaku.twidere.util.Utils.makeMessagesContentValues;
import static org.mariotaku.twidere.util.Utils.makeStatusesContentValues;
import static org.mariotaku.twidere.util.Utils.notifyForUpdatedUri;
import static org.mariotaku.twidere.util.Utils.showErrorToast;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import org.mariotaku.twidere.Constants;
import org.mariotaku.twidere.ITwidereService;
import org.mariotaku.twidere.R;
import org.mariotaku.twidere.app.TwidereApplication;
import org.mariotaku.twidere.provider.TweetStore;
import org.mariotaku.twidere.provider.TweetStore.CachedUsers;
import org.mariotaku.twidere.provider.TweetStore.Drafts;
import org.mariotaku.twidere.provider.TweetStore.Mentions;
import org.mariotaku.twidere.provider.TweetStore.Messages;
import org.mariotaku.twidere.provider.TweetStore.Statuses;
import org.mariotaku.twidere.util.AsyncTaskManager;
import org.mariotaku.twidere.util.ManagedAsyncTask;

import twitter4j.DirectMessage;
import twitter4j.GeoLocation;
import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.StatusUpdate;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;
import android.widget.Toast;

public class TwidereService extends Service implements Constants {

	private final ServiceStub mBinder = new ServiceStub(this);

	private AsyncTaskManager mAsyncTaskManager;

	private int mGetHomeTimelineTaskId, mGetMentionsTaskId;

	private boolean mStoreStatusesFinished = true, mStoreMentionsFinished = true;

	public int cancelRetweet(long account_id, long status_id) {
		CancelRetweetTask task = new CancelRetweetTask(account_id, status_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int createFavorite(long account_id, long status_id) {
		CreateFavoriteTask task = new CreateFavoriteTask(account_id, status_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int createFriendship(long account_id, long user_id) {
		CreateFriendshipTask task = new CreateFriendshipTask(account_id, user_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int destroyFavorite(long account_ids, long status_id) {
		DestroyFavoriteTask task = new DestroyFavoriteTask(account_ids, status_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int destroyFriendship(long account_id, long user_id) {
		DestroyFriendshipTask task = new DestroyFriendshipTask(account_id, user_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int destroyStatus(long account_id, long status_id) {
		DestroyStatusTask task = new DestroyStatusTask(account_id, status_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int getHomeTimeline(long[] account_ids, long[] max_ids) {
		mAsyncTaskManager.cancel(mGetHomeTimelineTaskId);
		GetHomeTimelineTask task = new GetHomeTimelineTask(account_ids, max_ids);
		return mGetHomeTimelineTaskId = mAsyncTaskManager.add(task, true);
	}

	public int getMentions(long[] account_ids, long[] max_ids) {
		mAsyncTaskManager.cancel(mGetMentionsTaskId);
		GetMentionsTask task = new GetMentionsTask(account_ids, max_ids);
		return mGetMentionsTaskId = mAsyncTaskManager.add(task, true);
	}

	public int getMessages(long account_id, long max_id) {
		return -1;
	}

	public boolean hasActivatedTask() {
		return mAsyncTaskManager.hasActivatedTask();
	}

	public boolean isHomeTimelineRefreshing() {
		return mAsyncTaskManager.isExcuting(mGetHomeTimelineTaskId) || !mStoreStatusesFinished;
	}

	public boolean isMentionsRefreshing() {
		return mAsyncTaskManager.isExcuting(mGetMentionsTaskId) || !mStoreMentionsFinished;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		mAsyncTaskManager = ((TwidereApplication) getApplication()).getAsyncTaskManager();
	}

	public int retweetStatus(long account_id, long status_id) {
		RetweetStatusTask task = new RetweetStatusTask(account_id, status_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int updateProfile(long account_id, String name, String url, String location, String description) {
		UpdateProfileTask task = new UpdateProfileTask(account_id, name, url, location, description);
		return mAsyncTaskManager.add(task, true);
	}

	public int updateProfileImage(long account_id, Uri image_uri) {
		UpdateProfileImageTask task = new UpdateProfileImageTask(account_id, image_uri);
		return mAsyncTaskManager.add(task, true);
	}

	public int updateStatus(long[] account_ids, String content, Location location, Uri image_uri, long in_reply_to) {
		UpdateStatusTask task = new UpdateStatusTask(account_ids, content, location, image_uri, in_reply_to);
		return mAsyncTaskManager.add(task, true);
	}

	private class CancelRetweetTask extends ManagedAsyncTask<Void, Void, StatusResponse> {

		private long account_id;
		private long status_id, retweeted_id;

		public CancelRetweetTask(long account_id, long status_id) {
			super(TwidereService.this, mAsyncTaskManager);
			this.account_id = account_id;
			this.status_id = status_id;
		}

		@Override
		protected StatusResponse doInBackground(Void... params) {

			Twitter twitter = getTwitterInstance(TwidereService.this, account_id, false);
			if (twitter != null) {
				try {
					twitter4j.Status status = twitter.destroyStatus(retweeted_id);
					return new StatusResponse(account_id, status, null);
				} catch (TwitterException e) {
					return new StatusResponse(account_id, null, e);
				}
			}
			return new StatusResponse(account_id, null, null);
		}

		@Override
		protected void onPostExecute(StatusResponse result) {
			if (result != null && result.status != null) {
				ContentResolver resolver = getContentResolver();
				User user = result.status.getUser();
				twitter4j.Status retweeted_status = result.status.getRetweetedStatus();
				if (user != null && retweeted_status != null) {
					ContentValues values = new ContentValues();
					values.put(Statuses.RETWEET_COUNT, result.status.getRetweetCount());
					values.put(Statuses.RETWEET_ID, -1);
					values.put(Statuses.RETWEETED_BY_ID, -1);
					values.put(Statuses.RETWEETED_BY_NAME, "");
					values.put(Statuses.RETWEETED_BY_SCREEN_NAME, "");
					values.put(Statuses.IS_RETWEET, 0);
					String status_where = Statuses.STATUS_ID + " = " + result.status.getId();
					String retweet_where = Statuses.STATUS_ID + " = " + retweeted_status.getId();
					for (Uri uri : TweetStore.STATUSES_URIS) {
						resolver.delete(uri, status_where, null);
						resolver.update(uri, values, retweet_where, null);
					}
				}
				Toast.makeText(TwidereService.this, R.string.cancel_retweet_success, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(TwidereService.this, result.exception, true);
			}
			super.onPostExecute(result);
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			retweeted_id = getRetweetId(TwidereService.this, status_id);
		}

	}

	private class CreateFavoriteTask extends ManagedAsyncTask<Void, Void, StatusResponse> {

		private long account_id;

		private long status_id;

		public CreateFavoriteTask(long account_id, long status_id) {
			super(TwidereService.this, mAsyncTaskManager);
			this.account_id = account_id;
			this.status_id = status_id;
		}

		@Override
		protected StatusResponse doInBackground(Void... params) {

			if (account_id < 0) return new StatusResponse(account_id, null, null);

			Twitter twitter = getTwitterInstance(TwidereService.this, account_id, false);
			if (twitter != null) {
				try {
					twitter4j.Status status = twitter.createFavorite(status_id);
					return new StatusResponse(account_id, status, null);
				} catch (TwitterException e) {
					return new StatusResponse(account_id, null, e);
				}
			}
			return new StatusResponse(account_id, null, null);
		}

		@Override
		protected void onPostExecute(StatusResponse result) {
			ContentResolver resolver = getContentResolver();

			if (result.status != null) {
				final long status_id = result.status.getId();
				final ContentValues values = new ContentValues();
				values.put(Statuses.IS_FAVORITE, 1);
				final StringBuilder where = new StringBuilder();
				where.append(Statuses.ACCOUNT_ID + " = " + result.account_id);
				where.append(" AND ");
				where.append("(");
				where.append(Statuses.STATUS_ID + "=" + status_id);
				where.append(" OR ");
				where.append(Statuses.RETWEET_ID + "=" + status_id);
				where.append(")");
				for (Uri uri : TweetStore.STATUSES_URIS) {
					resolver.update(uri, values, where.toString(), null);
				}
				Intent intent = new Intent(BROADCAST_FAVORITE_CHANGED);
				intent.putExtra(INTENT_KEY_STATUS_ID, status_id);
				intent.putExtra(INTENT_KEY_FAVORITED, true);
				sendBroadcast(intent);
				Toast.makeText(TwidereService.this, R.string.favorite_success, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(TwidereService.this, result.exception, true);
			}
			super.onPostExecute(result);
		}

	}

	private class CreateFriendshipTask extends ManagedAsyncTask<Void, Void, UserResponse> {

		private long account_id;
		private long user_id;

		public CreateFriendshipTask(long account_id, long user_id) {
			super(TwidereService.this, mAsyncTaskManager);
			this.account_id = account_id;
			this.user_id = user_id;
		}

		@Override
		protected UserResponse doInBackground(Void... params) {

			Twitter twitter = getTwitterInstance(TwidereService.this, account_id, false);
			if (twitter != null) {
				try {
					User user = twitter.createFriendship(user_id);
					return new UserResponse(user, null);
				} catch (TwitterException e) {
					return new UserResponse(null, e);
				}
			}
			return new UserResponse(null, null);
		}

		@Override
		protected void onPostExecute(UserResponse result) {
			if (result != null && result.user != null) {
				Toast.makeText(TwidereService.this, R.string.follow_success, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(TwidereService.this, result.exception, true);
			}
			Intent intent = new Intent(BROADCAST_FRIENDSHIP_CHANGED);
			intent.putExtra(INTENT_KEY_USER_ID, user_id);
			intent.putExtra(INTENT_KEY_SUCCEED, result != null && result.user != null);
			sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

	private class DestroyFavoriteTask extends ManagedAsyncTask<Void, Void, StatusResponse> {

		private long account_id;

		private long status_id;

		public DestroyFavoriteTask(long account_id, long status_id) {
			super(TwidereService.this, mAsyncTaskManager);
			this.account_id = account_id;
			this.status_id = status_id;
		}

		@Override
		protected StatusResponse doInBackground(Void... params) {

			if (account_id < 0) {
				new StatusResponse(account_id, null, null);
			}

			Twitter twitter = getTwitterInstance(TwidereService.this, account_id, false);
			if (twitter != null) {
				try {
					twitter4j.Status status = twitter.destroyFavorite(status_id);
					return new StatusResponse(account_id, status, null);
				} catch (TwitterException e) {
					return new StatusResponse(account_id, null, e);
				}
			}
			return new StatusResponse(account_id, null, null);
		}

		@Override
		protected void onPostExecute(StatusResponse result) {
			ContentResolver resolver = getContentResolver();

			if (result.status != null) {
				final long status_id = result.status.getId();
				final ContentValues values = new ContentValues();
				values.put(Statuses.IS_FAVORITE, 0);
				final StringBuilder where = new StringBuilder();
				where.append(Statuses.ACCOUNT_ID + " = " + result.account_id);
				where.append(" AND ");
				where.append("(");
				where.append(Statuses.STATUS_ID + "=" + status_id);
				where.append(" OR ");
				where.append(Statuses.RETWEET_ID + "=" + status_id);
				where.append(")");
				for (Uri uri : TweetStore.STATUSES_URIS) {
					resolver.update(uri, values, where.toString(), null);
				}
				final Intent intent = new Intent(BROADCAST_FAVORITE_CHANGED);
				intent.putExtra(INTENT_KEY_STATUS_ID, status_id);
				intent.putExtra(INTENT_KEY_FAVORITED, false);
				sendBroadcast(intent);
				Toast.makeText(TwidereService.this, R.string.unfavorite_success, Toast.LENGTH_SHORT).show();

			} else {
				showErrorToast(TwidereService.this, result.exception, true);
			}
			super.onPostExecute(result);
		}

	}

	private class DestroyFriendshipTask extends ManagedAsyncTask<Void, Void, UserResponse> {

		private long account_id;
		private long user_id;

		public DestroyFriendshipTask(long account_id, long user_id) {
			super(TwidereService.this, mAsyncTaskManager);
			this.account_id = account_id;
			this.user_id = user_id;
		}

		@Override
		protected UserResponse doInBackground(Void... params) {

			Twitter twitter = getTwitterInstance(TwidereService.this, account_id, false);
			if (twitter != null) {
				try {
					User user = twitter.destroyFriendship(user_id);
					return new UserResponse(user, null);
				} catch (TwitterException e) {
					return new UserResponse(null, e);
				}
			}
			return new UserResponse(null, null);
		}

		@Override
		protected void onPostExecute(UserResponse result) {
			if (result != null && result.user != null) {
				Toast.makeText(TwidereService.this, R.string.unfollow_success, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(TwidereService.this, result.exception, true);
			}
			Intent intent = new Intent(BROADCAST_FRIENDSHIP_CHANGED);
			intent.putExtra(INTENT_KEY_USER_ID, user_id);
			intent.putExtra(INTENT_KEY_SUCCEED, result != null && result.user != null);
			sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

	private class DestroyStatusTask extends ManagedAsyncTask<Void, Void, StatusResponse> {

		private long account_id;

		private long status_id;

		public DestroyStatusTask(long account_id, long status_id) {
			super(TwidereService.this, mAsyncTaskManager);
			this.account_id = account_id;
			this.status_id = status_id;
		}

		@Override
		protected StatusResponse doInBackground(Void... params) {

			Twitter twitter = getTwitterInstance(TwidereService.this, account_id, false);
			if (twitter != null) {
				try {
					twitter4j.Status status = twitter.destroyStatus(status_id);
					return new StatusResponse(account_id, status, null);
				} catch (TwitterException e) {
					return new StatusResponse(account_id, null, e);
				}
			}
			return new StatusResponse(account_id, null, null);
		}

		@Override
		protected void onPostExecute(StatusResponse result) {
			if (result != null && result.status != null) {
				final long status_id = result.status.getId();
				final ContentResolver resolver = getContentResolver();
				final StringBuilder where = new StringBuilder();
				where.append(Statuses.STATUS_ID + " = " + status_id);
				where.append(" OR " + Statuses.RETWEET_ID + " = " + status_id);
				for (Uri uri : TweetStore.STATUSES_URIS) {
					resolver.delete(uri, where.toString(), null);
				}
				Toast.makeText(TwidereService.this, R.string.delete_success, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(TwidereService.this, result.exception, true);
			}
			super.onPostExecute(result);
		}

	}

	private class GetHomeTimelineTask extends GetStatusesTask {

		public GetHomeTimelineTask(long[] account_ids, long[] max_ids) {
			super(TwidereService.this, mAsyncTaskManager, Statuses.CONTENT_URI, account_ids, max_ids);
		}

		@Override
		public ResponseList<twitter4j.Status> getStatuses(Twitter twitter, Paging paging) throws TwitterException {
			return twitter.getHomeTimeline(paging);
		}

		@Override
		public Twitter getTwitter(Context context, long account_id, boolean include_entities) {
			return getTwitterInstance(context, account_id, include_entities, true);
		}

		@Override
		protected void onPostExecute(List<GetStatusesTask.AccountResponse> responses) {
			super.onPostExecute(responses);
			sendBroadcast(new Intent(BROADCAST_HOME_TIMELINE_REFRESHED).putExtra(INTENT_KEY_SUCCEED,
					responses.size() > 0));
		}

	}

	private class GetMentionsTask extends GetStatusesTask {

		public GetMentionsTask(long[] account_ids, long[] max_ids) {
			super(TwidereService.this, mAsyncTaskManager, Mentions.CONTENT_URI, account_ids, max_ids);
		}

		@Override
		public ResponseList<twitter4j.Status> getStatuses(Twitter twitter, Paging paging) throws TwitterException {
			return twitter.getMentions(paging);
		}

		@Override
		public Twitter getTwitter(Context context, long account_id, boolean include_entities) {
			return getTwitterInstance(context, account_id, include_entities, false);
		}

		@Override
		protected void onPostExecute(List<GetStatusesTask.AccountResponse> responses) {
			super.onPostExecute(responses);
			sendBroadcast(new Intent(BROADCAST_MENTIONS_REFRESHED).putExtra(INTENT_KEY_SUCCEED, responses.size() > 0));
		}

	}

	private static class GetMessagesTask extends ManagedAsyncTask<Void, Void, GetMessagesTask.AccountResponse> {

		private long account_id, max_id;

		private final Uri uri;
		private final Context context;
		private final AsyncTaskManager manager;

		public GetMessagesTask(Context context, AsyncTaskManager manager, Uri uri, long account_id, long max_id) {
			super(context, manager);
			this.uri = uri;
			this.context = context;
			this.manager = manager;
			this.account_id = account_id;
			this.max_id = max_id;
		}

		@Override
		protected AccountResponse doInBackground(Void... params) {

			AccountResponse result = new AccountResponse(account_id, max_id, null);

			final SharedPreferences prefs = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
			final int load_item_limit = prefs
					.getInt(PREFERENCE_KEY_LOAD_ITEM_LIMIT, PREFERENCE_DEFAULT_LOAD_ITEM_LIMIT);
			Twitter twitter = getTwitterInstance(context, account_id, true);
			if (twitter != null) {
				try {
					Paging paging = new Paging();
					paging.setCount(load_item_limit);
					if (max_id > 0) {
						paging.setMaxId(max_id);
					}
					ResponseList<DirectMessage> statuses = twitter.getDirectMessages(paging);
					if (statuses != null) {
						statuses.addAll(twitter.getSentDirectMessages(paging));
					}

					if (statuses != null) {
						result = new AccountResponse(account_id, max_id, statuses);
					}
				} catch (TwitterException e) {
					e.printStackTrace();
				}
			}
			return result;
		}

		@Override
		protected void onPostExecute(AccountResponse responses) {
			synchronized (this) {
				manager.add(new StoreMessagesTask(context, manager, responses, uri), true);
			}
			super.onPostExecute(responses);
		}

		private static class AccountResponse {

			public final long account_id, max_id;
			public final ResponseList<DirectMessage> responselist;

			public AccountResponse(long account_id, long max_id, ResponseList<DirectMessage> responselist) {
				this.account_id = account_id;
				this.max_id = max_id;
				this.responselist = responselist;

			}
		}

		private static class StoreMessagesTask extends ManagedAsyncTask<Void, Void, Boolean> {

			private final AccountResponse response;
			private final Context context;
			private final Uri uri;

			public StoreMessagesTask(Context context, AsyncTaskManager manager, AccountResponse result, Uri uri) {
				super(context, manager);
				response = result;
				this.context = context;
				this.uri = uri;
			}

			@Override
			protected Boolean doInBackground(Void... args) {
				final ContentResolver resolver = context.getContentResolver();
				boolean succeed = false;
				Uri query_uri = buildQueryUri(uri, false);

				long account_id = response.account_id;
				ResponseList<DirectMessage> statuses = response.responselist;
				Cursor cur = resolver.query(uri, new String[0], Messages.ACCOUNT_ID + " = " + account_id, null, null);
				boolean no_items_before = false;
				if (cur != null) {
					no_items_before = cur.getCount() <= 0;
					cur.close();
				}
				if (statuses == null || statuses.size() <= 0) return false;
				final List<ContentValues> values_list = new ArrayList<ContentValues>();
				final List<Long> status_ids = new ArrayList<Long>();

				long min_id = -1;

				for (DirectMessage status : statuses) {
					if (status == null) {
						continue;
					}
					final long status_id = status.getId();

					status_ids.add(status_id);

					if (status_id < min_id || min_id == -1) {
						min_id = status_id;
					}
					values_list.add(makeMessagesContentValues(status, account_id));

				}
				int rows_deleted = -1;

				// Delete all rows conflicting before new data inserted.
				{
					StringBuilder where = new StringBuilder();
					where.append(Statuses.ACCOUNT_ID + " = " + account_id);
					where.append(" AND ");
					where.append("(");
					where.append(buildInWhereClause(Statuses.STATUS_ID, status_ids));
					where.append(" OR ");
					where.append(buildInWhereClause(Statuses.RETWEET_ID, status_ids));
					where.append(")");
					rows_deleted = resolver.delete(query_uri, where.toString(), null);
				}

				// Insert previously fetched items.
				resolver.bulkInsert(query_uri, values_list.toArray(new ContentValues[values_list.size()]));

				// No row deleted, so I will insert a gap.
				final boolean insert_gap = rows_deleted == 1 && status_ids.contains(response.max_id)
						|| rows_deleted == 0 && response.max_id == -1 && !no_items_before;
				if (insert_gap) {
					ContentValues values = new ContentValues();
					values.put(Statuses.IS_GAP, 1);
					StringBuilder where = new StringBuilder();
					where.append(Statuses.ACCOUNT_ID + "=" + account_id);
					where.append(" AND " + Statuses.STATUS_ID + "=" + min_id);
					resolver.update(query_uri, values, where.toString(), null);
				}
				succeed = true;
				return succeed;
			}

			@Override
			protected void onPostExecute(Boolean succeed) {
				if (succeed) {
					notifyForUpdatedUri(context, uri);
				}
				super.onPostExecute(succeed);
			}

			private String buildInWhereClause(String column, List<Long> ids) {
				StringBuilder builder = new StringBuilder();
				builder.append(column + " IN ( ");
				for (int i = 0; i < ids.size(); i++) {
					String id_string = String.valueOf(ids.get(i));
					if (id_string != null) {
						if (i > 0) {
							builder.append(", ");
						}
						builder.append(id_string);
					}
				}
				builder.append(" )");
				return builder.toString();
			}

		}

	}

	private abstract class GetStatusesTask extends ManagedAsyncTask<Void, Void, List<GetStatusesTask.AccountResponse>> {

		private long[] account_ids, max_ids;

		private final Uri uri;
		private final Context context;
		private final AsyncTaskManager manager;

		public GetStatusesTask(Context context, AsyncTaskManager manager, Uri uri, long[] account_ids, long[] max_ids) {
			super(context, manager);
			this.uri = uri;
			this.context = context;
			this.manager = manager;
			this.account_ids = account_ids;
			this.max_ids = max_ids;
		}

		public abstract ResponseList<twitter4j.Status> getStatuses(Twitter twitter, Paging paging)
				throws TwitterException;

		public abstract Twitter getTwitter(Context context, long account_id, boolean include_entities);

		@Override
		protected List<AccountResponse> doInBackground(Void... params) {

			List<AccountResponse> result = new ArrayList<AccountResponse>();

			if (account_ids == null) return result;

			boolean max_ids_valid = max_ids != null && max_ids.length == account_ids.length;

			int idx = 0;
			SharedPreferences prefs = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
			int load_item_limit = prefs.getInt(PREFERENCE_KEY_LOAD_ITEM_LIMIT, PREFERENCE_DEFAULT_LOAD_ITEM_LIMIT);
			for (long account_id : account_ids) {
				Twitter twitter = getTwitter(context, account_id, true);
				if (twitter != null) {
					try {
						Paging paging = new Paging();
						paging.setCount(load_item_limit);
						long max_id = -1;
						if (max_ids_valid && max_ids[idx] > 0) {
							max_id = max_ids[idx];
							paging.setMaxId(max_id);
						}
						ResponseList<twitter4j.Status> statuses = getStatuses(twitter, paging);

						if (statuses != null) {
							result.add(new AccountResponse(account_id, max_id, statuses));
						}
					} catch (TwitterException e) {
						e.printStackTrace();
					}
				}
				idx++;
			}
			return result;
		}

		@Override
		protected void onPostExecute(List<AccountResponse> responses) {
			synchronized (this) {
				manager.add(new StoreStatusesTask(context, manager, responses, uri), true);
			}
			super.onPostExecute(responses);
		}

		@Override
		protected void onPreExecute() {
			switch (getTableId(uri)) {
				case URI_STATUSES: {
					mStoreStatusesFinished = false;
					break;
				}
				case URI_MENTIONS: {
					mStoreMentionsFinished = false;
					break;
				}
			}
			super.onPreExecute();
		}

		private class AccountResponse {

			public final long account_id, max_id;
			public final ResponseList<twitter4j.Status> responselist;

			public AccountResponse(long account_id, long max_id, ResponseList<twitter4j.Status> responselist) {
				this.account_id = account_id;
				this.max_id = max_id;
				this.responselist = responselist;

			}
		}

		private class StoreStatusesTask extends ManagedAsyncTask<Void, Void, Boolean> {

			private final List<AccountResponse> responses;
			private final Context context;
			private final Uri uri;

			public StoreStatusesTask(Context context, AsyncTaskManager manager, List<AccountResponse> result, Uri uri) {
				super(context, manager);
				responses = result;
				this.context = context;
				this.uri = uri;
			}

			@Override
			protected Boolean doInBackground(Void... args) {
				final ContentResolver resolver = context.getContentResolver();
				boolean succeed = false;
				Uri query_uri = buildQueryUri(uri, false);

				for (AccountResponse response : responses) {
					long account_id = response.account_id;
					ResponseList<twitter4j.Status> statuses = response.responselist;
					Cursor cur = resolver.query(uri, new String[0], Statuses.ACCOUNT_ID + " = " + account_id, null,
							null);
					boolean no_items_before = false;
					if (cur != null) {
						no_items_before = cur.getCount() <= 0;
						cur.close();
					}
					if (statuses == null || statuses.size() <= 0) {
						continue;
					}
					final List<ContentValues> values_list = new ArrayList<ContentValues>();
					final List<Long> status_ids = new ArrayList<Long>();
					final List<Long> retweet_ids = new ArrayList<Long>();

					long min_id = -1;

					for (twitter4j.Status status : statuses) {
						if (status == null) {
							continue;
						}
						final User user = status.getUser();
						final long user_id = user.getId();
						final long status_id = status.getId();
						final long retweet_id = status.getRetweetedStatus() != null ? status.getRetweetedStatus()
								.getId() : -1;
						resolver.delete(CachedUsers.CONTENT_URI, CachedUsers.USER_ID + "=" + user_id, null);
						resolver.insert(CachedUsers.CONTENT_URI, makeCachedUsersContentValues(user));

						status_ids.add(status_id);

						if ((retweet_id <= 0 || !retweet_ids.contains(retweet_id)) && !retweet_ids.contains(status_id)) {
							if (status_id < min_id || min_id == -1) {
								min_id = status_id;
							}
							if (retweet_id > 0) {
								retweet_ids.add(retweet_id);
							}
							values_list.add(makeStatusesContentValues(status, account_id));
						}

					}
					int rows_deleted = -1;

					// Delete all rows conflicting before new data inserted.
					{
						StringBuilder where = new StringBuilder();
						where.append(Statuses.ACCOUNT_ID + " = " + account_id);
						where.append(" AND ");
						where.append("(");
						where.append(buildInWhereClause(Statuses.STATUS_ID, status_ids));
						where.append(" OR ");
						where.append(buildInWhereClause(Statuses.RETWEET_ID, status_ids));
						where.append(")");
						rows_deleted = resolver.delete(query_uri, where.toString(), null);
					}

					// Insert previously fetched items.
					resolver.bulkInsert(query_uri, values_list.toArray(new ContentValues[values_list.size()]));

					// No row deleted, so I will insert a gap.
					final boolean insert_gap = rows_deleted == 1 && status_ids.contains(response.max_id)
							|| rows_deleted == 0 && response.max_id == -1 && !no_items_before;
					if (insert_gap) {
						ContentValues values = new ContentValues();
						values.put(Statuses.IS_GAP, 1);
						StringBuilder where = new StringBuilder();
						where.append(Statuses.ACCOUNT_ID + "=" + account_id);
						where.append(" AND " + Statuses.STATUS_ID + "=" + min_id);
						resolver.update(query_uri, values, where.toString(), null);
					}
					succeed = true;
				}
				return succeed;
			}

			@Override
			protected void onPostExecute(Boolean succeed) {
				if (succeed) {
					notifyForUpdatedUri(context, uri);
				}
				switch (getTableId(uri)) {
					case URI_STATUSES: {
						mStoreStatusesFinished = true;
						break;
					}
					case URI_MENTIONS: {
						mStoreMentionsFinished = true;
						break;
					}
				}
				super.onPostExecute(succeed);
			}

			private String buildInWhereClause(String column, List<Long> ids) {
				StringBuilder builder = new StringBuilder();
				builder.append(column + " IN ( ");
				for (int i = 0; i < ids.size(); i++) {
					String id_string = String.valueOf(ids.get(i));
					if (id_string != null) {
						if (i > 0) {
							builder.append(", ");
						}
						builder.append(id_string);
					}
				}
				builder.append(" )");
				return builder.toString();
			}

		}

	}

	private class RetweetStatusTask extends ManagedAsyncTask<Void, Void, StatusResponse> {

		private long account_id;

		private long status_id;

		public RetweetStatusTask(long account_id, long status_id) {
			super(TwidereService.this, mAsyncTaskManager);
			this.account_id = account_id;
			this.status_id = status_id;
		}

		@Override
		protected StatusResponse doInBackground(Void... params) {

			if (account_id < 0) return new StatusResponse(account_id, null, null);

			Twitter twitter = getTwitterInstance(TwidereService.this, account_id, false);
			if (twitter != null) {
				try {
					twitter4j.Status status = twitter.retweetStatus(status_id);
					return new StatusResponse(account_id, status, null);
				} catch (TwitterException e) {
					return new StatusResponse(account_id, null, e);
				}
			}
			return new StatusResponse(account_id, null, null);
		}

		@Override
		protected void onPostExecute(StatusResponse result) {
			ContentResolver resolver = getContentResolver();

			if (result.status != null) {
				User user = result.status.getUser();
				twitter4j.Status retweeted_status = result.status.getRetweetedStatus();
				if (user != null && retweeted_status != null) {
					ContentValues values = new ContentValues();
					values.put(Statuses.RETWEET_ID, result.status.getId());
					values.put(Statuses.RETWEETED_BY_ID, user.getId());
					values.put(Statuses.RETWEETED_BY_NAME, user.getName());
					values.put(Statuses.RETWEETED_BY_SCREEN_NAME, user.getScreenName());
					values.put(Statuses.RETWEET_COUNT, retweeted_status.getRetweetCount());
					values.put(Statuses.IS_RETWEET, 1);
					StringBuilder where = new StringBuilder();
					where.append(Statuses.STATUS_ID + " = " + retweeted_status.getId());
					where.append(" OR " + Statuses.RETWEET_ID + " = " + retweeted_status.getId());
					for (Uri uri : TweetStore.STATUSES_URIS) {
						resolver.update(uri, values, where.toString(), null);
					}
				}
				Toast.makeText(TwidereService.this, R.string.retweet_success, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(TwidereService.this, result.exception, true);
			}

			super.onPostExecute(result);
		}

	}

	/*
	 * By making this a static class with a WeakReference to the Service, we
	 * ensure that the Service can be GCd even when the system process still has
	 * a remote reference to the stub.
	 */
	private static final class ServiceStub extends ITwidereService.Stub {

		WeakReference<TwidereService> mService;

		public ServiceStub(TwidereService service) {

			mService = new WeakReference<TwidereService>(service);
		}

		@Override
		public int cancelRetweet(long account_id, long status_id) throws RemoteException {
			return mService.get().cancelRetweet(account_id, status_id);
		}

		@Override
		public int createFavorite(long account_id, long status_id) throws RemoteException {
			return mService.get().createFavorite(account_id, status_id);
		}

		@Override
		public int createFriendship(long account_id, long user_id) throws RemoteException {
			return mService.get().createFriendship(account_id, user_id);
		}

		@Override
		public int destroyFavorite(long account_id, long status_id) throws RemoteException {
			return mService.get().destroyFavorite(account_id, status_id);
		}

		@Override
		public int destroyFriendship(long account_id, long user_id) throws RemoteException {
			return mService.get().destroyFriendship(account_id, user_id);
		}

		@Override
		public int destroyStatus(long account_id, long status_id) throws RemoteException {
			return mService.get().destroyStatus(account_id, status_id);
		}

		@Override
		public int getHomeTimeline(long[] account_ids, long[] max_ids) throws RemoteException {
			return mService.get().getHomeTimeline(account_ids, max_ids);
		}

		@Override
		public int getMentions(long[] account_ids, long[] max_ids) throws RemoteException {
			return mService.get().getMentions(account_ids, max_ids);
		}

		@Override
		public int getMessages(long account_id, long max_id) throws RemoteException {
			return mService.get().getMessages(account_id, max_id);
		}

		@Override
		public boolean hasActivatedTask() throws RemoteException {
			return mService.get().hasActivatedTask();
		}

		@Override
		public boolean isHomeTimelineRefreshing() throws RemoteException {
			return mService.get().isHomeTimelineRefreshing();
		}

		@Override
		public boolean isMentionsRefreshing() throws RemoteException {
			return mService.get().isMentionsRefreshing();
		}

		@Override
		public int retweetStatus(long account_id, long status_id) throws RemoteException {
			return mService.get().retweetStatus(account_id, status_id);
		}

		@Override
		public boolean test() throws RemoteException {
			return true;
		}

		@Override
		public int updateProfile(long account_id, String name, String url, String location, String description) {
			return mService.get().updateProfile(account_id, name, url, location, description);
		}

		@Override
		public int updateProfileImage(long account_id, Uri image_uri) {
			return mService.get().updateProfileImage(account_id, image_uri);
		}

		@Override
		public int updateStatus(long[] account_ids, String content, Location location, Uri image_uri, long in_reply_to)
				throws RemoteException {
			return mService.get().updateStatus(account_ids, content, location, image_uri, in_reply_to);

		}

	}

	private static final class StatusResponse {
		public final TwitterException exception;
		public final Status status;
		public final long account_id;

		public StatusResponse(long account_id, Status status, TwitterException exception) {
			this.exception = exception;
			this.status = status;
			this.account_id = account_id;
		}
	}

	private class UpdateProfileImageTask extends ManagedAsyncTask<Void, Void, UserResponse> {

		private final long account_id;
		private final Uri image_uri;

		public UpdateProfileImageTask(long account_id, Uri image_uri) {
			super(TwidereService.this, mAsyncTaskManager);
			this.account_id = account_id;
			this.image_uri = image_uri;
		}

		@Override
		protected UserResponse doInBackground(Void... params) {

			Twitter twitter = getTwitterInstance(TwidereService.this, account_id, false);
			if (twitter != null && image_uri != null && "file".equals(image_uri.getScheme())) {
				try {
					User user = twitter.updateProfileImage(new File(image_uri.getPath()));
					return new UserResponse(user, null);
				} catch (TwitterException e) {
					return new UserResponse(null, e);
				}
			}
			return new UserResponse(null, null);
		}

		@Override
		protected void onPostExecute(UserResponse result) {
			if (result != null && result.user != null) {
				Toast.makeText(TwidereService.this, R.string.profile_image_update_success, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(TwidereService.this, result.exception, true);
			}
			Intent intent = new Intent(BROADCAST_PROFILE_UPDATED);
			intent.putExtra(INTENT_KEY_USER_ID, account_id);
			intent.putExtra(INTENT_KEY_SUCCEED, result != null && result.user != null);
			sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

	private class UpdateProfileTask extends ManagedAsyncTask<Void, Void, UserResponse> {

		private final long account_id;
		private final String name, url, location, description;

		public UpdateProfileTask(long account_id, String name, String url, String location, String description) {
			super(TwidereService.this, mAsyncTaskManager);
			this.account_id = account_id;
			this.name = name;
			this.url = url;
			this.location = location;
			this.description = description;
		}

		@Override
		protected UserResponse doInBackground(Void... params) {

			Twitter twitter = getTwitterInstance(TwidereService.this, account_id, false);
			if (twitter != null) {
				try {
					User user = twitter.updateProfile(name, url, location, description);
					return new UserResponse(user, null);
				} catch (TwitterException e) {
					return new UserResponse(null, e);
				}
			}
			return new UserResponse(null, null);
		}

		@Override
		protected void onPostExecute(UserResponse result) {
			if (result != null && result.user != null) {
				Toast.makeText(TwidereService.this, R.string.profile_update_success, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(TwidereService.this, result.exception, true);
			}
			Intent intent = new Intent(BROADCAST_PROFILE_UPDATED);
			intent.putExtra(INTENT_KEY_USER_ID, account_id);
			intent.putExtra(INTENT_KEY_SUCCEED, result != null && result.user != null);
			sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

	private class UpdateStatusTask extends ManagedAsyncTask<Void, Void, List<StatusResponse>> {

		private long[] account_ids;
		private String content;
		private Location location;
		private Uri image_uri;
		private long in_reply_to;

		public UpdateStatusTask(long[] account_ids, String content, Location location, Uri image_uri, long in_reply_to) {
			super(TwidereService.this, mAsyncTaskManager);
			this.account_ids = account_ids;
			this.content = content;
			this.location = location;
			this.image_uri = image_uri;
			this.in_reply_to = in_reply_to;
		}

		@Override
		protected List<StatusResponse> doInBackground(Void... params) {

			if (account_ids == null) return null;

			final List<StatusResponse> result = new ArrayList<StatusResponse>();

			for (long account_id : account_ids) {
				Twitter twitter = getTwitterInstance(TwidereService.this, account_id, false);
				if (twitter != null) {
					try {
						StatusUpdate status = new StatusUpdate(content);
						status.setInReplyToStatusId(in_reply_to);
						if (location != null) {
							status.setLocation(new GeoLocation(location.getLatitude(), location.getLongitude()));
						}
						String image_path = getImagePathFromUri(TwidereService.this, image_uri);
						if (image_path != null) {
							status.setMedia(new File(image_path));
						}
						result.add(new StatusResponse(account_id, twitter.updateStatus(status), null));
					} catch (TwitterException e) {
						e.printStackTrace();
						result.add(new StatusResponse(account_id, null, e));
					}
				}
			}
			return result;
		}

		@Override
		protected void onPostExecute(List<StatusResponse> result) {

			boolean succeed = false;
			TwitterException exception = null;
			List<Long> failed_account_ids = new ArrayList<Long>();

			for (StatusResponse response : result) {
				if (response.status != null) {
					succeed = true;
					break;
				} else {
					failed_account_ids.add(response.account_id);
					exception = response.exception;
				}
			}
			if (succeed) {
				Toast.makeText(TwidereService.this, R.string.send_success, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(TwidereService.this, exception, true);
				StringBuilder ids_builder = new StringBuilder();
				for (int i = 0; i < failed_account_ids.size(); i++) {
					String id_string = String.valueOf(failed_account_ids.get(i));
					if (id_string != null) {
						if (i > 0) {
							ids_builder.append(';');
						}
						ids_builder.append(id_string);
					}
				}
				ContentValues values = new ContentValues();
				values.put(Drafts.ACCOUNT_IDS, ids_builder.toString());
				values.put(Drafts.IN_REPLY_TO_STATUS_ID, in_reply_to);
				values.put(Drafts.TEXT, content);
				if (image_uri != null) {
					values.put(Drafts.MEDIA_URI, image_uri.toString());
				}
				ContentResolver resolver = getContentResolver();
				resolver.insert(Drafts.CONTENT_URI, values);
			}
			super.onPostExecute(result);
		}

	}

	private static final class UserResponse {
		public TwitterException exception;
		public User user;

		public UserResponse(User user, TwitterException exception) {
			this.exception = exception;
			this.user = user;
		}
	}

}
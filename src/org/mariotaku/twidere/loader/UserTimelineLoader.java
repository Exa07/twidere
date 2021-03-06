package org.mariotaku.twidere.loader;

import java.util.List;

import org.mariotaku.twidere.util.ParcelableStatus;

import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import android.content.Context;

public class UserTimelineLoader extends Twitter4JStatusLoader {

	private final long mUserId;
	private final String mUserScreenName;

	public UserTimelineLoader(Context context, long account_id, long user_id, String user_screenname, long max_id,
			List<ParcelableStatus> data) {
		super(context, account_id, max_id, data);
		mUserId = user_id;
		mUserScreenName = user_screenname;
	}

	@Override
	public ResponseList<Status> getStatuses(Paging paging) throws TwitterException {
		Twitter twitter = getTwitter();
		if (twitter != null) {
			if (mUserId != -1)
				return twitter.getUserTimeline(mUserId, paging);
			else if (mUserScreenName != null) return twitter.getUserTimeline(mUserScreenName, paging);
		}
		return null;
	}

}
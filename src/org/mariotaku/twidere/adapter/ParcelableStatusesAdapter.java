package org.mariotaku.twidere.adapter;

import static org.mariotaku.twidere.util.Utils.formatToShortTimeString;
import static org.mariotaku.twidere.util.Utils.getAccountColor;
import static org.mariotaku.twidere.util.Utils.getTypeIcon;
import static org.mariotaku.twidere.util.Utils.isNullOrEmpty;

import org.mariotaku.twidere.R;
import org.mariotaku.twidere.util.ParcelableStatus;
import org.mariotaku.twidere.util.ProfileImageLoader;
import org.mariotaku.twidere.util.StatusViewHolder;
import org.mariotaku.twidere.util.StatusesAdapterInterface;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

public class ParcelableStatusesAdapter extends ArrayAdapter<ParcelableStatus> implements StatusesAdapterInterface {

	private boolean mDisplayProfileImage, mDisplayName, mShowAccountColor, mShowLastItemAsGap;
	private final ProfileImageLoader mImageLoader;
	private float mTextSize;
	private final Context mContext;

	public ParcelableStatusesAdapter(Context context, ProfileImageLoader loader) {
		super(context, R.layout.status_list_item, R.id.text);
		mContext = context;
		mImageLoader = loader;
	}

	@Override
	public ParcelableStatus findItem(long id) {
		for (int i = 0; i < getCount(); i++) {
			if (getItemId(i) == id) return getItem(i);
		}
		return null;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		View view = super.getView(position, convertView, parent);

		Object tag = view.getTag();
		StatusViewHolder holder = null;

		if (tag instanceof StatusViewHolder) {
			holder = (StatusViewHolder) tag;
		} else {
			holder = new StatusViewHolder(view, mContext);
			view.setTag(holder);
		}

		ParcelableStatus status = getItem(position);

		final CharSequence retweeted_by = mDisplayName ? status.retweeted_by_name : status.retweeted_by_screen_name;
		final boolean is_last = position == getCount() - 1;
		final boolean show_gap = status.is_gap && !is_last || mShowLastItemAsGap && is_last && getCount() > 1;

		holder.setShowAsGap(show_gap);
		holder.setAccountColorEnabled(mShowAccountColor);

		if (mShowAccountColor) {
			holder.setAccountColor(getAccountColor(mContext, status.account_id));
		}

		if (!show_gap) {

			holder.setTextSize(mTextSize);
			holder.name.setCompoundDrawablesWithIntrinsicBounds(
					status.is_protected ? R.drawable.ic_tweet_stat_is_protected : 0, 0, 0, 0);
			holder.name.setText(mDisplayName ? status.name : status.screen_name);
			holder.tweet_time.setText(formatToShortTimeString(mContext, status.status_timestamp));
			holder.tweet_time.setCompoundDrawablesWithIntrinsicBounds(0, 0,
					getTypeIcon(status.is_favorite, status.location != null, status.has_media), 0);
			holder.reply_retweet_status
					.setVisibility(status.in_reply_to_status_id != -1 || status.is_retweet ? View.VISIBLE : View.GONE);
			if (status.is_retweet && !isNullOrEmpty(retweeted_by)) {
				holder.reply_retweet_status.setText(mContext.getString(R.string.retweeted_by, retweeted_by
						+ (status.retweet_count > 1 ? " + " + (status.retweet_count - 1) : "")));
				holder.reply_retweet_status.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_tweet_stat_retweet,
						0, 0, 0);
			} else if (status.in_reply_to_status_id > 0 && !isNullOrEmpty(status.in_reply_to_screen_name)) {
				holder.reply_retweet_status.setText(mContext.getString(R.string.in_reply_to,
						status.in_reply_to_screen_name));
				holder.reply_retweet_status.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_tweet_stat_reply, 0,
						0, 0);
			}
			holder.profile_image.setVisibility(mDisplayProfileImage ? View.VISIBLE : View.GONE);
			if (mDisplayProfileImage) {
				mImageLoader.displayImage(status.profile_image_url, holder.profile_image);
			}
		}

		return view;
	}

	@Override
	public void setDisplayName(boolean display) {
		if (display != mDisplayName) {
			mDisplayName = display;
			notifyDataSetChanged();
		}
	}

	@Override
	public void setDisplayProfileImage(boolean display) {
		if (display != mDisplayProfileImage) {
			mDisplayProfileImage = display;
			notifyDataSetChanged();
		}
	}

	@Override
	public void setShowAccountColor(boolean show) {
		if (show != mShowAccountColor) {
			mShowAccountColor = show;
			notifyDataSetChanged();
		}
	}

	@Override
	public void setShowLastItemAsGap(boolean gap) {
		if (gap != mShowLastItemAsGap) {
			mShowLastItemAsGap = gap;
			notifyDataSetChanged();
		}
	}

	@Override
	public void setTextSize(float text_size) {
		if (text_size != mTextSize) {
			mTextSize = text_size;
			notifyDataSetChanged();
		}
	}

}
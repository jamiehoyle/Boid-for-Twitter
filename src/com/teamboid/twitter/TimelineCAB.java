package com.teamboid.twitter;

import java.util.ArrayList;

import twitter4j.Status;
import twitter4j.TwitterException;
import android.app.Fragment;
import android.content.Intent;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.BaseAdapter;
import android.widget.Toast;

import com.teamboid.twitter.TabsAdapter.BaseListFragment;
import com.teamboid.twitter.TabsAdapter.TimelineFragment;

public class TimelineCAB {

	public static TimelineScreen context;

	public static void clearSelectedItems() {
		for(int i = 0; i < context.getActionBar().getTabCount(); i++) {
			Fragment frag = context.getFragmentManager().findFragmentByTag("page:" + Integer.toString(i));
			if(frag instanceof BaseListFragment) {
				((BaseListFragment)frag).getListView().clearChoices();
				((BaseAdapter)((BaseListFragment)frag).getListView().getAdapter()).notifyDataSetChanged();
			}
		}
	}
	public static Status[] getSelectedTweets() {
		ArrayList<Status> toReturn = new ArrayList<Status>();
		for(int i = 0; i < context.getActionBar().getTabCount(); i++) {
			Fragment frag = context.getFragmentManager().findFragmentByTag("page:" + Integer.toString(i));
			if(frag instanceof BaseListFragment) {
				Status[] toAdd = ((BaseListFragment)frag).getSelectedStatuses();
				if(toAdd != null && toAdd.length > 0) {
					for(Status s : toAdd) toReturn.add(s);
				}
			}
		}
		return toReturn.toArray(new Status[0]);
	}

	public static ActionMode.Callback getTimelineActionMode() { return timelineActionMode; }
	private static ActionMode.Callback timelineActionMode = new ActionMode.Callback() {

		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			MenuInflater inflater = mode.getMenuInflater();
			Status[] selTweets = TimelineCAB.getSelectedTweets();
			if(selTweets.length == 0) {
				mode.finish();
				return false;
			}
			if(selTweets.length > 1) {
				inflater.inflate(R.menu.multi_tweet_cab, menu);
				mode.setTitle(context.getString(R.string.x_tweets_Selected).replace("{X}", Integer.toString(selTweets.length)));
				boolean allFavorited = true;
				for(Status t : selTweets) {
					if(!t.isFavorited()) {
						allFavorited = false;
						break;
					}
				}
				MenuItem fav = menu.findItem(R.id.favoriteAction);
				if(allFavorited) {
					fav.setTitle(R.string.unfavorite_str);
					fav.setIcon(context.getTheme().obtainStyledAttributes(new int[] { R.attr.favoriteIcon }).getDrawable(0));
				} else fav.setTitle(R.string.favorite_str);
			} else {
				inflater.inflate(R.menu.single_tweet_cab, menu);
				mode.setTitle(R.string.one_tweet_selected);				
				final Status status = getSelectedTweets()[0];
				if(status.getUser().getId() == AccountService.getCurrentAccount().getId()) {
					menu.findItem(R.id.retweetAction).setVisible(false);
					menu.findItem(R.id.deleteAction).setVisible(true);
				}
				MenuItem fav = menu.findItem(R.id.favoriteAction);
				if(status.isFavorited()) {
					fav.setTitle(R.string.unfavorite_str);
					fav.setIcon(context.getTheme().obtainStyledAttributes(new int[] { R.attr.favoriteIcon }).getDrawable(0));
				} else fav.setTitle(R.string.favorite_str);
			}
			return true;
		}

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) { return false; }

		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			final Status tweet = getSelectedTweets()[0];
			TimelineCAB.clearSelectedItems();
			mode.finish();
			switch (item.getItemId()) {
			case R.id.replyAction:
				context.startActivity(new Intent(context, ComposerScreen.class).putExtra("reply_to", tweet.getId()).putExtra("reply_to_name", tweet.getUser().getScreenName())
						.putExtra("append", Utilities.getAllMentions(tweet.getUser().getScreenName(), tweet.getText())).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
				return true;
			case R.id.favoriteAction:
				//TODO ((TimelineScreen)context).showProgress(true);
				if(tweet.isFavorited()) {
					new Thread(new Runnable() {
						public void run() {
							//TODO Update the favorite indicator in the corresponding column
							try { AccountService.getCurrentAccount().getClient().destroyFavorite(tweet.getId()); }
							catch(TwitterException e) {
								e.printStackTrace();
								context.runOnUiThread(new Runnable() {
									public void run() { Toast.makeText(context, R.string.failed_unfavorite, Toast.LENGTH_LONG).show(); }
								});
							}
							//								TODO context.runOnUiThread(new Runnable() {
							//									public void run() { ((TimelineScreen)context).showProgress(true); }
							//								});
						}
					}).start();
				} else {
					new Thread(new Runnable() {
						public void run() {
							//TODO Update the favorite indicator in the corresponding column
							try { AccountService.getCurrentAccount().getClient().createFavorite(tweet.getId()); }
							catch(TwitterException e) {
								e.printStackTrace();
								context.runOnUiThread(new Runnable() {
									public void run() { Toast.makeText(context, R.string.failed_favorite, Toast.LENGTH_LONG).show(); }
								});
								return;
							}
							//								TODO context.runOnUiThread(new Runnable() {
							//									public void run() { ((TimelineScreen)context).showProgress(true); }
							//								});
						}
					}).start();
				}
				return true;
			case R.id.retweetAction:
				//TODO ((TimelineScreen)context).showProgress(true);
				new Thread(new Runnable() {
					public void run() {
						try { 
							final Status result = AccountService.getCurrentAccount().getClient().retweetStatus(tweet.getId());
							context.runOnUiThread(new Runnable() {
								public void run() { AccountService.getFeedAdapter(context, TimelineFragment.ID, AccountService.getCurrentAccount().getId()).add(new Status[] { result }); }
							});
						}
						catch(TwitterException e) {
							e.printStackTrace();
							context.runOnUiThread(new Runnable() {
								public void run() { Toast.makeText(context, R.string.failed_retweet, Toast.LENGTH_LONG).show(); }
							});
							return;
						}
						context.runOnUiThread(new Runnable() {
							public void run() { 
								//TODO ((TimelineScreen)context).showProgress(true);
								Toast.makeText(context, R.string.retweeted_status, Toast.LENGTH_LONG).show();
							}
						});
					}
				}).start();
				return true;
			case R.id.shareAction:
				String text = tweet.getText() + "\n\n(via @" + tweet.getUser().getScreenName() + ", http://twitter.com/" + tweet.getUser().getScreenName() + "/status/" + Long.toString(tweet.getId()) + ")";
				context.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), 
						context.getString(R.string.share_str)).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
				return true;
			default:
				return false;
			}
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {
		}
	};
}

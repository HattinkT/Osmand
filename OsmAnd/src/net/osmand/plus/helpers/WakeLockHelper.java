package net.osmand.plus.helpers;

import net.osmand.plus.DeviceAdminRecv;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.OsmandSettings;
import net.osmand.plus.routing.VoiceRouter;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.PowerManager;

@SuppressLint("NewApi")
public class WakeLockHelper implements VoiceRouter.VoiceMessageListener {
	
	private goToSleepRunnable goToSleepRunnable = new goToSleepRunnable();
	private DevicePolicyManager mDevicePolicyManager;
	private ComponentName mDeviceAdmin;
	private Handler uiHandler;
	private OsmandApplication app;
	private boolean active;

	public WakeLockHelper(OsmandApplication app){
		uiHandler = new Handler();
		this.app = app;
		mDeviceAdmin = new ComponentName(app, DeviceAdminRecv.class);
		mDevicePolicyManager = (DevicePolicyManager) app.getSystemService(Context.DEVICE_POLICY_SERVICE);
		VoiceRouter voiceRouter = app.getRoutingHelper().getVoiceRouter();
		voiceRouter.addVoiceMessageListener(this);
	}
	
	private void goToSleep() {
		if (mDevicePolicyManager != null && mDeviceAdmin != null) {
			OsmandSettings settings = app.getSettings();
			final Integer screenPowerSave = settings.WAKE_ON_VOICE_INT.get();
			if (screenPowerSave > 0 && settings.MAP_ACTIVITY_ENABLED.get()) {
				if (mDevicePolicyManager.isAdminActive(mDeviceAdmin)) {
					try {
						mDevicePolicyManager.lockNow();
					} catch (SecurityException e) {
//						Log.d(TAG,
//								"SecurityException: No device admin permission to lock the screen!");
					}
				} else {
//					Log.d(TAG,
//							"No device admin permission to lock the screen!");
				}
			}
		}
	}
	
	private class goToSleepRunnable implements Runnable {
		
		@Override
		public void run() {
			goToSleep();
		}
	}

	private void ScheduleSleep() {
		OsmandSettings settings = app.getSettings();
		final Integer screenPowerSave = settings.WAKE_ON_VOICE_INT.get();
		if (screenPowerSave > 0) {
			uiHandler.removeCallbacks(goToSleepRunnable);
			uiHandler.postDelayed(goToSleepRunnable, screenPowerSave * 1000L);
		}
	}

	public void onStart(Activity a) {
		this.active = true;
		ScheduleSleep();
	}

	public void onStop(Activity a) {
		this.active = false;
		if (a.isFinishing()) {
			VoiceRouter voiceRouter = app.getRoutingHelper().getVoiceRouter();
			voiceRouter.removeVoiceMessageListener(this);
		}
	}

	public void onUserInteraction() {
		ScheduleSleep();
	}
	
	@Override
	public void onVoiceMessage() {
		OsmandSettings settings = app.getSettings();
		final Integer screenPowerSave = settings.WAKE_ON_VOICE_INT.get();
		if (screenPowerSave > 0) {
			if (!active) {
				PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
				PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK
						| PowerManager.ACQUIRE_CAUSES_WAKEUP,
						"OsmAndOnVoiceWakeupTag");
				wakeLock.acquire();
				wakeLock.release(); // As FLAG_KEEP_SCREEN_ON is set on main activity the screen will remain lit after release

				if (settings.NOTIFY_ON_WAKE.get()) {
					try {
						Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
						Ringtone r = RingtoneManager.getRingtone(app, notification);
						r.play();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}

			ScheduleSleep();
		}
	}
	
}

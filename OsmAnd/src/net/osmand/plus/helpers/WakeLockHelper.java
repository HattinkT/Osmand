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
	
	private PowerManager.WakeLock wakeLock = null;
	private ReleaseWakeLocksRunnable releaseWakeLocksRunnable = new ReleaseWakeLocksRunnable();
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
	}
	
	private void releaseWakeLocks() {
		if (wakeLock != null) {
			wakeLock.release();
			wakeLock = null;
		}
		
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
	
	private class ReleaseWakeLocksRunnable implements Runnable {
		
		@Override
		public void run() {
			releaseWakeLocks();
		}
	}

	private void ScheduleReleaseWakeLocks() {
		OsmandSettings settings = app.getSettings();
		final Integer screenPowerSave = settings.WAKE_ON_VOICE_INT.get();
		if (screenPowerSave > 0) {
			uiHandler.removeCallbacks(releaseWakeLocksRunnable);
			uiHandler.postDelayed(releaseWakeLocksRunnable, screenPowerSave * 1000L);
		}
	}

	public void onStart(Activity a) {
		this.active = true;
		if (wakeLock == null) {
			VoiceRouter voiceRouter = app.getRoutingHelper().getVoiceRouter();
			voiceRouter.removeVoiceMessageListener(this);
			ScheduleReleaseWakeLocks();
		}		
	}

	public void onStop(Activity a) {
		this.active = false;
		OsmandSettings settings = app.getSettings();
		if (!a.isFinishing() && (settings.WAKE_ON_VOICE_INT.get() > 0)) {
			VoiceRouter voiceRouter = app.getRoutingHelper().getVoiceRouter();
			voiceRouter.addVoiceMessageListener(this);
		}
	}

	public void onUserInteraction() {
		ScheduleReleaseWakeLocks();
	}
	
	@Override
	public void onVoiceMessage() {
		OsmandSettings settings = app.getSettings();
		final Integer screenPowerSave = settings.WAKE_ON_VOICE_INT.get();
		if (screenPowerSave > 0) {
			if (!active && wakeLock == null) {
				PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
				wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK
						| PowerManager.ACQUIRE_CAUSES_WAKEUP,
						"OsmAndOnVoiceWakeupTag");
				wakeLock.acquire();

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

			ScheduleReleaseWakeLocks();
		}
	}
	
}

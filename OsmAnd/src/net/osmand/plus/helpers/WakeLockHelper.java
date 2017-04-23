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

@SuppressLint("NewApi")
public class WakeLockHelper implements VoiceRouter.VoiceMessageListener {
	
	private goToSleepRunnable goToSleepRunnable = new goToSleepRunnable();
	private wokenUpRunnable wokenUpRunnable = new wokenUpRunnable();
	private DevicePolicyManager mDevicePolicyManager;
	private ComponentName mDeviceAdmin;
	private Handler uiHandler;
	private OsmandApplication app;
	private boolean powerEvent;
	private boolean voiceEvent;
	private boolean isSleeping;

	public WakeLockHelper(OsmandApplication app){
		uiHandler = new Handler();
		this.app = app;
		powerEvent = false;
		voiceEvent = false;
		isSleeping = false;
		mDeviceAdmin = new ComponentName(app, DeviceAdminRecv.class);
		mDevicePolicyManager = (DevicePolicyManager) app.getSystemService(Context.DEVICE_POLICY_SERVICE);
		VoiceRouter voiceRouter = app.getRoutingHelper().getVoiceRouter();
		voiceRouter.addVoiceMessageListener(this);
	}

	private void wokenUp() {
		isSleeping = false;
		OsmandSettings settings = app.getSettings();
		final Integer screenPowerSave = settings.WAKE_ON_VOICE_INT.get();
		if (screenPowerSave > 0) {
			if (powerEvent && !voiceEvent) {
				// We are woken up by a power event, fall asleep immediately
				goToSleep();
			} else {
				if (voiceEvent) {
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

				scheduleSleep();
			}
		}
	}

	private class wokenUpRunnable implements Runnable {

		@Override
		public void run() {
			wokenUp();
		}
	}

	private void goToSleep() {
		powerEvent = false;
		voiceEvent = false;
		isSleeping = true;
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

	private void scheduleSleep() {
		if (!isSleeping) {
			OsmandSettings settings = app.getSettings();
			final Integer screenPowerSave = settings.WAKE_ON_VOICE_INT.get();
			if (screenPowerSave > 0) {
				uiHandler.removeCallbacks(goToSleepRunnable);
				uiHandler.postDelayed(goToSleepRunnable, screenPowerSave * 1000L);
			}
		}
	}

	public void onStart(Activity a) {
		uiHandler.removeCallbacks(wokenUpRunnable);
		uiHandler.postDelayed(wokenUpRunnable, 250L);
	}

	public void onStop(Activity a) {
		powerEvent = false;
		voiceEvent = false;
		isSleeping = true;
		if (a.isFinishing()) {
			VoiceRouter voiceRouter = app.getRoutingHelper().getVoiceRouter();
			voiceRouter.removeVoiceMessageListener(this);
		}
	}

	public void onUserInteraction() {
		scheduleSleep();
	}

	public void onPowerEvent() {
		powerEvent = true;
	}

	@Override
	public void onVoiceMessage() {
		voiceEvent = true;
		scheduleSleep();
	}
}

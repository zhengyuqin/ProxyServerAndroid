package com.android.sms.proxy.service;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.android.sms.proxy.entity.ApkUpdate;
import com.avos.avoscloud.AVException;
import com.avos.avoscloud.AVObject;
import com.avos.avoscloud.AVQuery;
import com.flurry.android.FlurryAgent;

import net.luna.common.download.AppDownloadManager;
import net.luna.common.download.model.AppModel;

/**
 * @author zyq 16-3-27
 */
public class ApkUpdateUtil {

	public static final boolean DEBUG = true;
	public static final String TAG = "apkUpdateUtil";
	public volatile static ApkUpdateUtil mInstance;
	private Context mContext;

	public static ApkUpdateUtil getInstance(Context context) {
		if (mInstance == null) {
			synchronized (ApkUpdateUtil.class) {
				if (mInstance == null) {
					mInstance = new ApkUpdateUtil(context);
				}
			}
		}
		return mInstance;
	}

	public ApkUpdateUtil(Context context) {
		mContext = context;
	}

	private ApkUpdate getNewUpdateInfo() throws AVException {
		AVQuery<ApkUpdate> query = AVObject.getQuery(ApkUpdate.class);
		ApkUpdate update = query.getFirst();
		if (DEBUG) {
			Log.d(TAG, "获取第一条更新消息");
		}
		if (!verifyUpdateInfo(update)) {
			update = null;
		}
		return update;
	}

	private boolean verifyUpdateInfo(ApkUpdate apkUpdate) {
		try {
			if (mContext == null) return false;
			if (apkUpdate == null) return false;
			if (apkUpdate != null) {

				final String packageName = apkUpdate.getPackage();
				final String versionName = apkUpdate.getVersion();
				final String apkUrl = apkUpdate.getApkUrl();

				final String currentPackageName = mContext.getPackageName();
				final String currentVersionName = mContext.getPackageManager().getPackageInfo(currentPackageName, 0)
						.versionName;
				if (DEBUG) {
					Log.d(TAG, "包名：" + packageName + " 版本号：" + Float.valueOf(versionName) + " 下载地址：" + apkUrl + " " +
							"当前包名：" + currentPackageName + " 当前版本：" + Float.valueOf(currentVersionName));
				}
				if (currentPackageName.equals(packageName)) {
					if (Float.valueOf(versionName) > Float.valueOf(currentVersionName)) {
						if (!TextUtils.isEmpty(apkUrl)) {
							return true;
						}
					}
				}
			}
		} catch (Throwable e) {
			return false;
		}
		return false;
	}


	public void updateApk() {
		try {
			final ApkUpdate updateApk = getNewUpdateInfo();
			if (updateApk != null) {
				if(DEBUG)Log.d(TAG, "开始下载最新apk");
				AppModel appModel = new AppModel();
				appModel.setAppName(updateApk.getAppname());
				appModel.setDownloadUrl(updateApk.getApkUrl());


				if (isDownloadManagerAvailable()) {
					if(DEBUG) Log.d(TAG,"downloadManager 开始下载！！！！");
					AppDownloadManager.getInstance(mContext).downloadApp(appModel, true);
				} else {
					tryToEnabledDownloadManager();
				}
			}

		} catch (Throwable e) {
			if (DEBUG) {
				Log.d(TAG, e.toString());
			}
			FlurryAgent.onError(TAG, "", e);
		}
	}

	private void tryToEnabledDownloadManager() {
		String packageName = "com.android.providers.downloads";
		try {
			if (DEBUG) {
				Log.d(TAG, "enableDownloadManager!!!!!!!!");
			}
			//Open the specific App Info page:
			Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
			intent.setData(Uri.parse("package:" + packageName));
			mContext.startActivity(intent);
		} catch (ActivityNotFoundException e) {
			//e.printStackTrace();
			//Open the generic Apps page:
			if(DEBUG) Log.d(TAG,e.toString());
			Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
			mContext.startActivity(intent);
		}
	}

	private boolean isDownloadManagerAvailable() {
		int state = mContext.getPackageManager().getApplicationEnabledSetting("com.android.providers.downloads");
		if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
				state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
				state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED) {
			return false;
		}
		return true;
	}
}

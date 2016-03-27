package com.android.sms.proxy.entity;

import com.avos.avoscloud.AVClassName;
import com.avos.avoscloud.AVObject;

/**
 * @author zyq 16-3-27
 */
@AVClassName("ApkUpdate")
public class ApkUpdate extends AVObject {

	public static final Creator CREATOR = AVObjectCreator.instance;
	public static final String PACKAGE = "package";
	public static final String APKURL = "apkUrl";
	public static final String VERSION = "versionName";
	public static final String APPNAME = "name";


	public ApkUpdate() {
	}

	;

	public String getPackage() {
		return getString(PACKAGE);
	}

	public void setPackage(String packageName) {
		put(PACKAGE, packageName);
	}

	public String getApkUrl() {
		return getString(APKURL);
	}

	public void setApkUrl(String apkUrl) {
		put(APKURL, apkUrl);
	}

	public String getVersion() {
		return getString(VERSION);
	}

	public void setVersion(String version) {
		put(VERSION, version);
	}

	public String getAppname() {
		return getString(APPNAME);
	}

	public void setAppname(String appName) {
		put(APPNAME, appName);
	}
}

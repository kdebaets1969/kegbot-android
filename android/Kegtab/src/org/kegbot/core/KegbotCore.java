/*
 * Copyright 2012 Mike Wakerly <opensource@hoho.com>.
 *
 * This file is part of the Kegtab package from the Kegbot project. For
 * more information on Kegtab or Kegbot, see <http://kegbot.org/>.
 *
 * Kegtab is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, version 2.
 *
 * Kegtab is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with Kegtab. If not, see <http://www.gnu.org/licenses/>.
 */
package org.kegbot.core;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.Set;

import org.kegbot.api.KegbotApi;
import org.kegbot.api.KegbotApiImpl;
import org.kegbot.app.util.ImageDownloader;
import org.kegbot.app.util.IndentingPrintWriter;
import org.kegbot.app.util.PreferenceHelper;
import org.kegbot.app.util.Utils;
import org.kegbot.core.FlowManager.Clock;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Handler;
import android.util.Log;

import com.google.common.collect.Sets;
import com.squareup.otto.Bus;
import com.squareup.otto.ThreadEnforcer;

/**
 * Top-level class implementing the Kegbot core.
 *
 * @author mike wakerly (opensource@hoho.com)
 */
public class KegbotCore {

  private static final String TAG = KegbotCore.class.getSimpleName();

  private static KegbotCore sInstance;

  private final Bus mBus;
  private final Handler mBusHandler = new Handler();

  private final Set<Manager> mManagers = Sets.newLinkedHashSet();
  private final PreferenceHelper mPreferences;

  private final TapManager mTapManager;
  private final FlowManager mFlowManager;
  private final AuthenticationManager mAuthenticationManager;
  private final ConfigurationManager mConfigurationManager;
  private final SoundManager mSoundManager;
  private final ImageDownloader mImageDownloader;

  private final KegbotApi mApi;
  private final SyncManager mSyncManager;

  private final KegboardManager mKegboardManager;
  private final HardwareManager mHardwareManager;

  private final BluetoothManager mBluetoothManager;

  private final Context mContext;

  private boolean mStarted = false;

  private final FlowManager.Clock mClock = new Clock() {
    @Override
    public long currentTimeMillis() {
      return System.currentTimeMillis();
    }
  };

  public KegbotCore(Context context) {
    mContext = context.getApplicationContext();
    mBus = new Bus(ThreadEnforcer.MAIN);

    mPreferences = new PreferenceHelper(context);

    mApi = new KegbotApiImpl();
    mApi.setApiUrl(mPreferences.getApiUrl());
    mApi.setApiKey(mPreferences.getApiKey());

    mImageDownloader = new ImageDownloader(context, mPreferences.getKegbotUrl());

    mTapManager = new TapManager();
    mManagers.add(mTapManager);

    mFlowManager = new FlowManager(mTapManager, mPreferences, mClock);
    mManagers.add(mFlowManager);

    mSyncManager = new SyncManager(context, mApi, mPreferences);
    mManagers.add(mSyncManager);

    mKegboardManager = new KegboardManager(context);
    mManagers.add(mKegboardManager);

    mHardwareManager = new HardwareManager(context, mKegboardManager);
    mManagers.add(mHardwareManager);

    mAuthenticationManager = new AuthenticationManager(context, mApi);
    mManagers.add(mAuthenticationManager);

    mConfigurationManager = new ConfigurationManager();
    mManagers.add(mConfigurationManager);

    mSoundManager = new SoundManager(context, mApi, mFlowManager);
    mManagers.add(mSoundManager);

    mBluetoothManager = new BluetoothManager(context);
    mManagers.add(mBluetoothManager);
  }

  public synchronized void start() {
    if (!mStarted) {
      for (final Manager manager : mManagers) {
        Log.d(TAG, "Starting " + manager.getName());
        manager.start();
      }
      mStarted = true;
    }
  }

  public synchronized void stop() {
    if (mStarted) {
      for (final Manager manager : mManagers) {
        Log.d(TAG, "Stopping " + manager.getName());
        manager.stop();
      }
      mStarted = false;
    }
  }

  public Bus getBus() {
    return mBus;
  }

  /**
   * Posts event on the main (UI) thread.
   *
   * @param event the event
   */
  public void postEvent(final Object event) {
    mBusHandler.post(new Runnable() {
      @Override
      public void run() {
        Log.d(TAG, "Posting event: " + event);
        mBus.post(event);
      }
    });
  }

  /**
   * @return the preferences
   */
  public PreferenceHelper getPreferences() {
    return mPreferences;
  }

  /**
   * @return the tapManager
   */
  public TapManager getTapManager() {
    return mTapManager;
  }

  /**
   * @return the authenticationManager
   */
  public AuthenticationManager getAuthenticationManager() {
    return mAuthenticationManager;
  }

  /**
   * @return the api
   */
  public KegbotApi getApi() {
    return mApi;
  }

  /**
   * @return the api manager
   */
  public SyncManager getSyncManager() {
    return mSyncManager;
  }

  /**
   * @return the flowManager
   */
  public FlowManager getFlowManager() {
    return mFlowManager;
  }

  /**
   * @return the soundManager
   */
  public SoundManager getSoundManager() {
    return mSoundManager;
  }

  /**
   * @return the kegboardManager
   */
  public KegboardManager getKegboardManager() {
    return mKegboardManager;
  }

  /**
   * @return the hardwareManager
   */
  public HardwareManager getHardwareManager() {
    return mHardwareManager;
  }

  /**
   * @return the configurationManager
   */
  public ConfigurationManager getConfigurationManager() {
    return mConfigurationManager;
  }

  public ImageDownloader getImageDownloader() {
    return mImageDownloader;
  }

  public void dump(PrintWriter printWriter) {
    StringWriter baseWriter = new StringWriter();
    IndentingPrintWriter writer = new IndentingPrintWriter(baseWriter, "  ");

    PackageManager pm = mContext.getPackageManager();
    PackageInfo packageInfo;
    try {
      packageInfo = pm.getPackageInfo(mContext.getPackageName(), PackageManager.GET_SIGNATURES);
    } catch (NameNotFoundException e) {
      throw new RuntimeException("Cannot get own package info.", e);
    }

    writer.println("Package info:");
    writer.println();
    writer.increaseIndent();
    writer.printPair("versionName", packageInfo.versionName).println();
    writer.printPair("versionCode", String.valueOf(packageInfo.versionCode)).println();
    writer.printPair("packageName", packageInfo.packageName).println();
    writer.printPair("installTime", new Date(packageInfo.firstInstallTime)).println();
    writer.printPair("lastUpdateTime", new Date(packageInfo.lastUpdateTime)).println();
    writer.printPair("installerPackageName", pm.getInstallerPackageName(mContext.getPackageName()))
        .println();
    if (packageInfo.signatures != null && packageInfo.signatures.length > 0) {
      writer.printPair("signature", Utils.getFingerprintForSignature(packageInfo.signatures[0]))
          .println();
    }
    writer.decreaseIndent();
    writer.println();

    writer.println("Core info:");
    writer.println();
    writer.increaseIndent();
    writer.printPair("mStarted", Boolean.valueOf(mStarted)).println();
    writer.printPair("deviceId", mPreferences.getDeviceId()).println();
    writer.printPair("gcmId", mPreferences.getGcmRegistrationId()).println();
    writer.printPair("enableFlowAutoStart", Boolean.valueOf(mPreferences.getEnableFlowAutoStart()))
        .println();
    writer.printPair("allowManualLogin", Boolean.valueOf(mPreferences.getAllowManualLogin()))
        .println();
    writer.printPair("allowRegistration", Boolean.valueOf(mPreferences.getAllowRegistration()))
        .println();
    writer.printPair("cacheCredentials", Boolean.valueOf(mPreferences.getCacheCredentials()))
        .println();
    writer.println();

    for (final Manager manager : mManagers) {
      writer.println(String.format("## %s", manager.getName()));
      writer.increaseIndent();
      manager.dump(writer);
      writer.decreaseIndent();
      writer.println();
    }
    writer.decreaseIndent();
    printWriter.write(baseWriter.toString());
  }

  public static KegbotCore getInstance(Context context) {
    synchronized (KegbotCore.class) {
      if (sInstance == null) {
        sInstance = new KegbotCore(context.getApplicationContext());
      }
    }
    return sInstance;
  }

}
/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package ScienceJournalApi.src.main.java.com.google.android.apps.forscience.whistlepunk.api.scalarinput;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Binder;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.ArrayMap;
import android.util.Log;

import com.google.android.apps.forscience.whistlepunk.api.scalarinput.AdvertisedDevice;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.AdvertisedSensor;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.IDeviceConsumer;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorConnector;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorConsumer;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorDiscoverer;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorObserver;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorStatusListener;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.SensorBehavior;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.Signatures;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.Versions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Support for creating your own sensor-exposing service.  To use, extend ScalarSensorService,
 * and override the abstract methods.
 *
 * For an example implementation, see
 * https://github.com/google/science-journal/blob/master/ScalarApiSampleApp/app/src/main/java/com/google/android/apps/forscience/scalarapisample/AllNativeSensorProvider.java
 */
public abstract class ScalarSensorService extends Service {
    private static final String TAG = "ScalarService";

    private ISensorDiscoverer.Stub mDiscoverer = null;

    /**
     * @return a human-readable name of this service
     */
    @NonNull
    protected abstract String getDiscovererName();

    public interface AdvertisedDeviceConsumer {
        void onDeviceFound(com.google.android.apps.forscience.whistlepunk.api.scalarinput.AdvertisedDevice device) throws RemoteException;
        void onDone() throws RemoteException;
    }

    /**
     * Call {@code c} with each device found, then call c.onDone().
     *
     * If you can compute all devices quickly, on the same thread, then override
     * {@link #getDevices()} instead.  Otherwise, your override should should spawn a new thread
     * to do the finding, and return immediately.
     *
     * Calls back to {@link AdvertisedDeviceConsumer} must happen on the service's main thread.
     *
     * An appropriate extension of this method:
     * <ol>
     *     <li>Always eventually calls onDone</li>
     *     <li>Always returns every discoverable device on every call (that is, do not filter
     *         out discoverable devices just because you've reported them before; Science Journal
     *         may decide this means the device has disappeared.)
     *         </li>
     * </ol>
     */
    protected void findDevices(AdvertisedDeviceConsumer c) throws RemoteException {
        for (com.google.android.apps.forscience.whistlepunk.api.scalarinput.AdvertisedDevice device : getDevices()) {
            c.onDeviceFound(device);
        }
        c.onDone();
    }

    /**
     * @return the devices that can currently be connected to.
     *
     * Only override this method if you can quickly, on the same thread, compute or scan for all
     * matching devices.  Otherwise, override {@link #findDevices(AdvertisedDeviceConsumer)}
     */
    protected abstract List<? extends com.google.android.apps.forscience.whistlepunk.api.scalarinput.AdvertisedDevice> getDevices();

    @Nullable
    @Override
    public final ISensorDiscoverer.Stub onBind(Intent intent) {
        if (Log.isLoggable(TAG, Log.INFO)) {
            Log.i(TAG,
                    "Service scalar API version: " + Versions.getScalarApiVersion(getPackageName(),
                            getResources()) + " for service class " + getClass().getSimpleName());
        }
        return getDiscoverer();
    }

    /**
     * @return whether this service should check for whether the binding process is a signed
     * binary of Science Journal.  If true, and the signature check fails, this service will report
     * no devices, to prevent sensor data being received by unauthorized clients.
     */
    protected boolean shouldCheckBinderSignature() {
        return true;
    }

    /**
     * Check that the connecting app is one we trust to stream sensor data to.
     *
     * By default, this will only accept Science Journal installed from the Play Store.
     *
     * Note that this method only returns valid results when called from within methods defined
     * on the Binder class, not methods like onBind on the service itself.
     */
    protected boolean binderHasAllowedSignature() {
        int uid = Binder.getCallingUid();
        PackageManager pm = getPackageManager();
        String bindingName = pm.getNameForUid(uid);
        try {
            PackageInfo info = pm.getPackageInfo(bindingName, PackageManager.GET_SIGNATURES);
            Signature[] signatures = info.signatures;
            Set<String> allowedSignatures = allowedSignatures();
            if (Log.isLoggable(TAG, Log.INFO)) {
                Log.i(TAG, "Number of signatures of binding app [" + bindingName + "]: "
                        + signatures.length);
            }
            for (Signature signature : signatures) {
                String charString = signature.toCharsString();
                if (Log.isLoggable(TAG, Log.INFO)) {
                    Log.i(TAG, "Checking signature: " + charString);
                }
                if (allowedSignatures.contains(charString)) {
                    if (Log.isLoggable(TAG, Log.INFO)) {
                        Log.i(TAG, "Signature match!");
                    }
                    return true;
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "Unknown package name: " + bindingName);
            }
        }
        if (Log.isLoggable(TAG, Log.ERROR)) {
            Log.e(TAG, "Signature check failed");
        }
        return false;
    }

    /**
     * @return The set of allowed app signatures.  By default, this only includes Science Journal
     * as installed from the Play Store, but extenders may add other trusted apps.
     */
    protected Set<String> allowedSignatures() {
        return Signatures.DEFAULT_ALLOWED_SIGNATURES;
    }

    private ISensorDiscoverer.Stub getDiscoverer() {
        if (mDiscoverer == null) {
            mDiscoverer = createDiscoverer();
        }
        return mDiscoverer;
    }

    protected ISensorDiscoverer.Stub createDiscoverer() {
        return new ScalarDiscoverer();
    }

    private class ScalarDiscoverer extends ISensorDiscoverer.Stub {
        private LinkedHashMap<String, com.google.android.apps.forscience.whistlepunk.api.scalarinput.AdvertisedDevice> mDevices = new LinkedHashMap<>();
        private Map<String, com.google.android.apps.forscience.whistlepunk.api.scalarinput.AdvertisedSensor> mSensors = new ArrayMap<>();
        private boolean mSignatureHasBeenChecked = false;
        private boolean mSignatureCheckPassed = false;

        @Override
        public String getName() throws RemoteException {
            return getDiscovererName();
        }

        @Override
        public void scanDevices(final IDeviceConsumer c) throws RemoteException {
            if (!clientAllowed()) {
                return;
            }
            findDevices(new AdvertisedDeviceConsumer() {
                @Override
                public void onDeviceFound(com.google.android.apps.forscience.whistlepunk.api.scalarinput.AdvertisedDevice device) throws RemoteException {
                    mDevices.put(device.getDeviceId(), device);
                    c.onDeviceFound(device.getDeviceId(), device.getDeviceName(),
                            device.getSettingsIntent());
                }

                @Override
                public void onDone() throws RemoteException {
                    c.onScanDone();
                }
            });
        }

        @Override
        public void scanSensors(String deviceId, ISensorConsumer c) throws RemoteException {
            if (clientAllowed()) {
                AdvertisedDevice device = mDevices.get(deviceId);
                if (device == null) {
                    c.onScanDone();
                    return;
                }
                for (com.google.android.apps.forscience.whistlepunk.api.scalarinput.AdvertisedSensor sensor : device.getSensors()) {
                    mSensors.put(sensor.getAddress(), sensor);
                    c.onSensorFound(sensor.getAddress(), sensor.getName(), sensor.getBehavior(),
                            sensor.getAppearance());
                }
            }
            c.onScanDone();
        }

        @Override
        public ISensorConnector getConnector() throws RemoteException {
            return new ISensorConnector.Stub() {
                @Override
                public void startObserving(final String sensorId,
                                           final ISensorObserver observer, final ISensorStatusListener listener,
                                           String settingsKey) throws RemoteException {
                    if (clientAllowed()) {
                        AdvertisedSensor sensor = mSensors.get(sensorId);
                        // TODO: write tests for this
                        if (sensor != null) {
                            sensor.startObserving(observer, listener);
                        } else {
                            // TODO: create scanner class?
                            findAndStartObserving(sensorId, observer, listener);
                        }
                    }
                }

                @Override
                public void stopObserving(String sensorId) throws RemoteException {
                    if (clientAllowed()) {
                        mSensors.get(sensorId).stopObserving();
                    }
                }

                private void findAndStartObserving(final String sensorId,
                        final ISensorObserver observer, final ISensorStatusListener listener)
                        throws RemoteException {
                    scanDevices(new IDeviceConsumer.Stub() {
                        @Override
                        public void onDeviceFound(String deviceId, String name,
                                PendingIntent settingsIntent) throws RemoteException {
                            scanSensors(deviceId, new ISensorConsumer.Stub() {
                                @Override
                                public void onSensorFound(String sensorAddress, String name,
                                        SensorBehavior behavior,
                                        SensorAppearanceResources appearance)
                                        throws RemoteException {
                                    if (sensorAddress.equals(sensorId)) {
                                        mSensors.get(sensorId).startObserving(observer, listener);
                                    }
                                }

                                @Override
                                public void onScanDone() throws RemoteException {
                                    // TODO: flag failure if all scans complete without finding
                                    // the sensor
                                }
                            });
                        }

                        @Override
                        public void onScanDone() throws RemoteException {
                            // TODO: flag failure if all scans complete without finding the sensor
                        }
                    });
                }
            };
        }

        private boolean clientAllowed() {
            if (!mSignatureHasBeenChecked) {
                mSignatureCheckPassed =
                        !shouldCheckBinderSignature() || binderHasAllowedSignature();
            }
            return mSignatureCheckPassed;
        }
    }
}

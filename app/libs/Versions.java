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

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;

public class Versions {
    public static int FIRST_RELEASE_SCALAR_API_VERSION = 1;

    public static int getScalarApiVersion(String packageName, Resources resources) {
        try {
            int identifier = resources.getIdentifier("scalar_api_version", "integer",
                    packageName);
            if (identifier != 0) {
                return resources.getInteger(identifier);
            }
        } catch (Resources.NotFoundException e) {
            // Fall through to default version
        }
        return FIRST_RELEASE_SCALAR_API_VERSION;
    }
}

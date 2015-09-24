/* 
 * Copyright 2015 Cognitive Medical Systems, Inc (http://www.cognitivemedciine.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.socraticgrid.hl7.ucs.nifi.controller;

import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.nifi.controller.AbstractControllerService;
import org.socraticgrid.hl7.ucs.nifi.common.model.AdapterStatus;
import org.socraticgrid.hl7.ucs.nifi.common.model.Status;

/**
 *
 * @author BasitAzeem
 *
 */
public class ServiceStatusControllerService extends AbstractControllerService
        implements ServiceStatusController {

    private final Map<String, AdapterStatus> adapterStatusMap = Collections
            .synchronizedMap(new LinkedHashMap<>());

    @Override
    public void updateServiceStatus(String adapterName, Status status) {
        if (adapterStatusMap.containsKey(adapterName)) {
            adapterStatusMap.get(adapterName).setLastUpdateDateTime(new Date());
            adapterStatusMap.get(adapterName).setStatus(status);
        } else {
            AdapterStatus adapterStatus = new AdapterStatus();
            adapterStatus.setAdapterName(adapterName);
            adapterStatus.setLastUpdateDateTime(new Date());
            adapterStatus.setStatus(status);
            adapterStatusMap.put(adapterName, adapterStatus);
        }
    }

    @Override
    public Map<String, AdapterStatus> getServiceStatusMap() {
        return adapterStatusMap;
    }

}

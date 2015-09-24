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

import java.util.Map;

import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.controller.ControllerService;
import org.socraticgrid.hl7.ucs.nifi.common.model.AdapterStatus;
import org.socraticgrid.hl7.ucs.nifi.common.model.Status;

/**
 * 
 * @author BasitAzeem
 *
 */
@Tags({"Service", "Status"})
@CapabilityDescription("This Controller is used to update Service Status for all the supported Adapters.")
public interface ServiceStatusController extends ControllerService {

    public void updateServiceStatus(String adapterName, Status status);

    public Map<String, AdapterStatus> getServiceStatusMap();
}

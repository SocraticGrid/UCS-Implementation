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
package org.socraticgrid.hl7.ucs.nifi.common.model;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * The main purpose of this class is to serve as the Wrapper of Adapter details
 * available in the UCS Workflow
 * 
 * @author BasitAzeem
 */
@XmlRootElement
public class Adapter {

	private Long adapterId;
	private String adapterName;

	public Adapter() {
	}

	public Adapter(Long adapterId, String adapterName) {
		this.adapterId = adapterId;
		this.adapterName = adapterName;
	}

	public Long getAdapterId() {
		return adapterId;
	}

	public void setAdapterId(Long adapterId) {
		this.adapterId = adapterId;
	}

	public String getAdapterName() {
		return adapterName;
	}

	public void setAdapterName(String adapterName) {
		this.adapterName = adapterName;
	}

}

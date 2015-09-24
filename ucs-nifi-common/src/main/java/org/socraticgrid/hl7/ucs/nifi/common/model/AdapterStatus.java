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

import java.util.Date;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * The main purpose of this class is to serve as the Wrapper of Adapter details
 * available in the UCS Workflow
 * 
 * @author BasitAzeem
 */
@XmlRootElement
public class AdapterStatus {

	private String adapterName;
	private Status status;
	private Date lastUpdateDateTime;

	public AdapterStatus() {
	}

	public AdapterStatus(String adapterName, Status status, Date lastUpdateDateTime) {
		super();
		this.adapterName = adapterName;
		this.status = status;
		this.lastUpdateDateTime = lastUpdateDateTime;
	}

	public String getAdapterName() {
		return adapterName;
	}

	public void setAdapterName(String adapterName) {
		this.adapterName = adapterName;
	}

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public Date getLastUpdateDateTime() {
		return lastUpdateDateTime;
	}

	public void setLastUpdateDateTime(Date lastUpdateDateTime) {
		this.lastUpdateDateTime = lastUpdateDateTime;
	}

}

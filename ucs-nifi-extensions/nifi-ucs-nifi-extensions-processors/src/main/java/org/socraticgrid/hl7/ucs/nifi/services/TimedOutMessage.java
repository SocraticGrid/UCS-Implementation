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
package org.socraticgrid.hl7.ucs.nifi.services;

import javax.xml.bind.annotation.XmlRootElement;

import org.socraticgrid.hl7.services.uc.model.Message;

/**
 * @author Pavan
 *
 */
@XmlRootElement
public class TimedOutMessage {
	
	public static enum TimeOutType{
		NO_RESPONSES, PARTIAL_RESPONSES
	}
	
	private Message message;
	private TimeOutType timeOutReason;
	
	public TimedOutMessage(){
		
	}
	public TimedOutMessage(Message message,TimeOutType timeOutReason){
		this.message=message;
		this.timeOutReason=timeOutReason;
	}
	
	/**
	 * @return the message
	 */
	public Message getMessage() {
		return message;
	}
	/**
	 * @param message the message to set
	 */
	public void setMessage(Message message) {
		this.message = message;
	}
	/**
	 * @return the timeOutReason
	 */
	public TimeOutType getTimeOutReason() {
		return timeOutReason;
	}
	/**
	 * @param timeOutReason the timeOutReason to set
	 */
	public void setTimeOutReason(TimeOutType timeOutReason) {
		this.timeOutReason = timeOutReason;
	}
	
}

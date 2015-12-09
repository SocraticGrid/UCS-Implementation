/* 
 * Copyright 2015 Cognitive Medical Systems, Inc (http://www.cognitivemedicine.com).
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
import org.socraticgrid.hl7.services.uc.model.ConversationInfo;

/**
 * The main purpose of this class is to serve as the @XmlRootElement for a
 * {@link ConversationInfo} in case we want to manually marshall it using JAXB.
 * 
 * @author esteban
 */
@XmlRootElement
public class ConversationInfoWrapper {

    private ConversationInfo conversationInfo;

    public ConversationInfoWrapper() {
    }

    public ConversationInfoWrapper(ConversationInfo conversationInfo) {
        this.conversationInfo = conversationInfo;
    }
    
    public ConversationInfo getConversationInfo() {
        return conversationInfo;
    }

    public void setConversationInfo(ConversationInfo conversationInfo) {
        this.conversationInfo = conversationInfo;
    }
    
}

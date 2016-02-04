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
package org.socraticgrid.hl7.ucs.nifi.common.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.socraticgrid.hl7.services.uc.model.AlertMessage;
import org.socraticgrid.hl7.services.uc.model.AlertStatus;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.stringtemplate.v4.ST;

/**
 *
 * @author esteban
 */
public class AlertMessageBuilder extends MessageBuilder{
    
    private final String embeddedAlertMessageTemplate;
    private String status = AlertStatus.New.name();
    private List<Property> properties = new ArrayList<>();

    public static class Property {
        private String key;
        private String value;

        public Property(String key, String value) {
            this.key = key;
            this.value = value;
        }
        
        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }

    }
    
    public AlertMessageBuilder() throws IOException {
        embeddedAlertMessageTemplate = IOUtils.toString(MessageBuilder.class
                .getResourceAsStream("/templates/embedded-alert-message-template.tpl"));
    }
    
    public AlertMessageBuilder withStatus(AlertStatus status) {
        this.status = status.name();
        return this;
    }
    
    public AlertMessageBuilder addProperty(String key, String value){
        this.properties.add(new Property(key, value));
        return this;
    }
    
    @Override
    protected ST prepareTemplate(String template){
        ST st = super.prepareTemplate(template);
        st.add("status", status);
        st.add("properties", properties);
        
        return st;
    }
    
    @Override
    public AlertMessage buildMessage() throws MessageSerializationException {
        return (AlertMessage) this.buildMessageWrapper().getMessage();
    }
    
    @Override
    protected String getEmbeddedMessageTemplate(){
        return this.embeddedAlertMessageTemplate;
    }
    
    @Override
    protected String getMessageType(){
        return "alertMessage";
    }
    
}

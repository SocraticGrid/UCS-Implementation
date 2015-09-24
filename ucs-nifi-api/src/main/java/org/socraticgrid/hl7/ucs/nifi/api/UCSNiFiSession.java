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
package org.socraticgrid.hl7.ucs.nifi.api;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.socraticgrid.hl7.services.uc.interfaces.AlertingIntf;
import org.socraticgrid.hl7.services.uc.interfaces.ClientIntf;
import org.socraticgrid.hl7.services.uc.interfaces.ConversationIntf;
import org.socraticgrid.hl7.services.uc.interfaces.ManagementIntf;
import org.socraticgrid.hl7.services.uc.interfaces.UCSAlertingIntf;
import org.socraticgrid.hl7.services.uc.interfaces.UCSClientIntf;
import org.socraticgrid.hl7.ucs.nifi.core.NiFiHTTPBroker;

/**
 *
 * @author esteban
 */
public class UCSNiFiSession {

    private static final Logger LOG = LoggerFactory.getLogger(UCSNiFiSession.class);
    
    public static class UCSNiFiSessionBuilder {
        

        private UCSNiFiSession instance = new UCSNiFiSession();
        

        public UCSNiFiSessionBuilder withNifiSendMessageURL(URL nifiSendMessageURL) {
            instance.nifiSendMessageURL = nifiSendMessageURL;
            return this;
        }
        
        public UCSNiFiSessionBuilder withNifiSendMessageURL(String nifiSendMessageURL) {
            try {
                return this.withNifiSendMessageURL(new URL(nifiSendMessageURL));
            } catch (MalformedURLException ex) {
                throw new IllegalStateException("Invalid URL: "+nifiSendMessageURL);
            }
        }

        public UCSNiFiSessionBuilder withClientCommandURL(URL clientCommandURL) {
            instance.clientCommandURL = clientCommandURL;
            return this;
        }
        
        public UCSNiFiSessionBuilder withClientCommandURL(String clientCommandURL) {
            try {
                return this.withClientCommandURL(new URL(clientCommandURL));
            } catch (MalformedURLException ex) {
                throw new IllegalStateException("Invalid URL: "+clientCommandURL);
            }
        }

        public UCSNiFiSessionBuilder withUCSClientHost(String sessionUCSClientHost) {
            instance.sessionUCSClientHost = sessionUCSClientHost;
            return this;
        }
        
        public UCSNiFiSessionBuilder withUCSClientPort(int sessionUCSClientPort) {
            instance.sessionUCSClientPort = sessionUCSClientPort;
            return this;
        }
        
        public UCSNiFiSessionBuilder withUCSClientListener(UCSClientIntf listener) {
            instance.ucsClientListener = listener;
            return this;
        }
        
        public UCSNiFiSessionBuilder withAlertingCommandURL(URL alertingCommandURL) {
            instance.alertingCommandURL = alertingCommandURL;
            return this;
        }
        
        public UCSNiFiSessionBuilder withAlertingCommandURL(String alertingCommandURL) {
            try {
                return this.withAlertingCommandURL(new URL(alertingCommandURL));
            } catch (MalformedURLException ex) {
                throw new IllegalStateException("Invalid URL: "+alertingCommandURL);
            }
        }

        public UCSNiFiSessionBuilder withUCSAlertingHost(String sessionUCSAlertingHost) {
            instance.sessionUCSAlertingHost = sessionUCSAlertingHost;
            return this;
        }
        
        public UCSNiFiSessionBuilder withUCSAlertingPort(int sessionUCSAlertingPort) {
            instance.sessionUCSAlertingPort = sessionUCSAlertingPort;
            return this;
        }
        
        public UCSNiFiSessionBuilder withUCSAlertingListener(UCSAlertingIntf listener) {
            instance.ucsAlertingListener = listener;
            return this;
        }
        
        public UCSNiFiSessionBuilder withManagementCommandURL(URL managementCommandURL) {
            instance.managementCommandURL = managementCommandURL;
            return this;
        }
        
        public UCSNiFiSessionBuilder withManagementCommandURL(String managementCommandURL) {
            try {
                return this.withManagementCommandURL(new URL(managementCommandURL));
            } catch (MalformedURLException ex) {
                throw new IllegalStateException("Invalid URL: "+managementCommandURL);
            }
        }

        public UCSNiFiSessionBuilder withManagementHost(String sessionManagementHost) {
            instance.sessionManagementHost = sessionManagementHost;
            return this;
        }
        
        public UCSNiFiSessionBuilder withManagementPort(int sessionManagementPort) {
            instance.sessionManagementPort = sessionManagementPort;
            return this;
        }
        
        public UCSNiFiSessionBuilder withConversationCommandURL(URL conversationCommandURL) {
            instance.conversationCommandURL = conversationCommandURL;
            return this;
        }
        
        public UCSNiFiSessionBuilder withConversationCommandURL(String conversationCommandURL) {
            try {
                return this.withConversationCommandURL(new URL(conversationCommandURL));
            } catch (MalformedURLException ex) {
                throw new IllegalStateException("Invalid URL: "+conversationCommandURL);
            }
        }

        public UCSNiFiSessionBuilder withConversationHost(String sessionConversationHost) {
            instance.sessionConversationHost = sessionConversationHost;
            return this;
        }
        
        public UCSNiFiSessionBuilder withConversationPort(int sessionConversationPort) {
            instance.sessionConversationPort = sessionConversationPort;
            return this;
        }

        public UCSNiFiSession build() throws IOException, InterruptedException {
            instance.init();
            return instance;
        }

    }

    private URL nifiSendMessageURL;
    
    private URL clientCommandURL;
    private int sessionUCSClientPort = 8899;
    private String sessionUCSClientHost = "localhost";
    private UCSClientIntf ucsClientListener;
    
    private URL alertingCommandURL;
    private int sessionUCSAlertingPort = 8897;
    private String sessionUCSAlertingHost = "localhost";
    private UCSAlertingIntf ucsAlertingListener;
    
    private URL managementCommandURL;
    private int sessionManagementPort = 8900;
    private String sessionManagementHost = "localhost";
    
    private URL conversationCommandURL;
    private int sessionConversationPort = 8901;
    private String sessionConversationHost = "localhost";
    
    private NiFiHTTPBroker niFiHTTPBroker;

    private UCSNiFiSession() {
    }

    protected void init() throws IOException, InterruptedException{
        LOG.debug("Starting UCSNifiSession instance: {}", this.toString());
        
        this.niFiHTTPBroker = new NiFiHTTPBroker(
                nifiSendMessageURL, 
                new NiFiHTTPBroker.ClientEndpointWithListener(clientCommandURL, sessionUCSClientHost, sessionUCSClientPort, ucsClientListener),
                new NiFiHTTPBroker.ClientEndpointWithListener(alertingCommandURL, sessionUCSAlertingHost, sessionUCSAlertingPort, ucsAlertingListener),
                new NiFiHTTPBroker.ClientEndpoint(managementCommandURL, sessionManagementHost, sessionManagementPort),
                new NiFiHTTPBroker.ClientEndpoint(conversationCommandURL, sessionConversationHost, sessionConversationPort)
        );
        this.niFiHTTPBroker.start();
    }

    public void dispose() throws IOException{
        this.niFiHTTPBroker.stop();
    }
    
    public ClientIntf getNewClient() {
        return new ClientImpl(this.niFiHTTPBroker);
    }
    
    public AlertingIntf getNewAlerting() {
        return new AlertingImpl(this.niFiHTTPBroker);
    }
    
    public ManagementIntf getNewManagement() {
        return new ManagementImpl(this.niFiHTTPBroker);
    }
    
    public ConversationIntf getNewConversation() {
        return new ConversationImpl(this.niFiHTTPBroker);
    }

    @Override
    public String toString() {
        return "UCSNiFiSession{" + "nifiSendMessageURL=" + nifiSendMessageURL + ", clientCommandURL=" + clientCommandURL + ", sessionUCSClientPort=" + sessionUCSClientPort + ", sessionUCSClientHost=" + sessionUCSClientHost + ", ucsClientListener=" + ucsClientListener + ", alertingCommandURL=" + alertingCommandURL + ", sessionUCSAlertingPort=" + sessionUCSAlertingPort + ", sessionUCSAlertingHost=" + sessionUCSAlertingHost + ", ucsAlertingListener=" + ucsAlertingListener + ", niFiHTTPBroker=" + niFiHTTPBroker + '}';
    }

}

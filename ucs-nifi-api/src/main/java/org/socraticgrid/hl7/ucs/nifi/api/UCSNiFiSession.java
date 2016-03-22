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
package org.socraticgrid.hl7.ucs.nifi.api;

import java.io.IOException;
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

        /****
         * Nifi related properties
         ***/ 
        
        public UCSNiFiSessionBuilder withNifiScheme(String nifiScheme) {
            instance.nifiScheme = nifiScheme;
            return this;
        }
        
        public UCSNiFiSessionBuilder withNifiHost(String nifiHost) {
            instance.nifiHost = nifiHost;
            return this;
        }
        
        public UCSNiFiSessionBuilder withNifiCommandContext(String nifiCommandContext) {
            instance.nifiCommandContext = nifiCommandContext;
            return this;
        }
        
        public UCSNiFiSessionBuilder withNifiSendMessageCommandPort(int nifiSendMessageCommandPort) {
            instance.nifiSendMessageCommandPort = nifiSendMessageCommandPort;
            return this;
        }
        
        public UCSNiFiSessionBuilder withNifiClientCommandPort(int nifiClientCommandPort) {
            instance.nifiClientCommandPort = nifiClientCommandPort;
            return this;
        }
        
        public UCSNiFiSessionBuilder withNifiAlertingCommandPort(int nifiAlertingCommandPort) {
            instance.nifiAlertingCommandPort = nifiAlertingCommandPort;
            return this;
        }
        
        public UCSNiFiSessionBuilder withNifiManagementCommandPort(int nifiManagementCommandPort) {
            instance.nifiManagementCommandPort = nifiManagementCommandPort;
            return this;
        }
        
        public UCSNiFiSessionBuilder withNifiConversationCommandPort(int nifiConversationCommandPort) {
            instance.nifiConversationCommandPort = nifiConversationCommandPort;
            return this;
        }
        
        
        
        /****
         * Client related properties
         ***/

        public UCSNiFiSessionBuilder withClientHost(String clientHost) {
            instance.clientHost = clientHost;
            return this;
        }
        
        public UCSNiFiSessionBuilder withClientCallbackPort(int clientCallbackPort) {
            instance.clientCallbackPort = clientCallbackPort;
            return this;
        }
        
        public UCSNiFiSessionBuilder withClientAlertingCallbackPort(int clientAlertingCallbackPort) {
            instance.clientAlertingCallbackPort = clientAlertingCallbackPort;
            return this;
        }
        
        public UCSNiFiSessionBuilder withClientManagementCallbackPort(int clientManagementCallbackPort) {
            instance.clientManagementCallbackPort = clientManagementCallbackPort;
            return this;
        }
        
        public UCSNiFiSessionBuilder withClientConversationCallbackPort(int clientConversationCallbackPort) {
            instance.clientConversationCallbackPort = clientConversationCallbackPort;
            return this;
        }
        
        public UCSNiFiSessionBuilder withUCSClientListener(UCSClientIntf listener) {
            instance.ucsClientListener = listener;
            return this;
        }
        
        public UCSNiFiSessionBuilder withUCSAlertingListener(UCSAlertingIntf listener) {
            instance.ucsAlertingListener = listener;
            return this;
        }
        
        public UCSNiFiSession build() throws IOException, InterruptedException {
            return instance;
        }

    }
    
    private static enum STATUS {
        NOT_INITIALIZED,
        INITIALIZED,
        ERROR;
    }
    
    private STATUS status = STATUS.NOT_INITIALIZED;

    /**
     * NiFi scheme, host and ports (and their default values)
     */
    private String nifiScheme = "http";
    private String nifiHost = "localhost";
    private String nifiCommandContext = "/contentListener";
    private int nifiSendMessageCommandPort = 8888;
    private int nifiClientCommandPort = 8889;
    private int nifiAlertingCommandPort = 8890;
    private int nifiManagementCommandPort = 8891;
    private int nifiConversationCommandPort = 8892;
    
    /**
     * Client host and ports (and their default values)
     */
    private String clientHost = "localhost";
    private int clientCallbackPort = 0;
    private int clientAlertingCallbackPort = 0;
    private int clientManagementCallbackPort = 0;
    private int clientConversationCallbackPort = 0;
    
    private UCSClientIntf ucsClientListener;
    private UCSAlertingIntf ucsAlertingListener;
    
    
    private NiFiHTTPBroker niFiHTTPBroker;

    private UCSNiFiSession() {
    }

    private synchronized NiFiHTTPBroker getNiFiHTTPBroker() {
        try{
            if (this.status == STATUS.ERROR){
                throw new IllegalStateException("This session is in ERROR status due to a previous error. Check the logs for more information.");
            }
            
            if (this.status == STATUS.NOT_INITIALIZED){
                this.init();
                this.status = STATUS.INITIALIZED;
            }

            return this.niFiHTTPBroker;
        } catch (Exception e){
            this.status = STATUS.ERROR;
            throw new IllegalStateException(e);
        }
    }
    
    protected synchronized void init() throws IOException, InterruptedException{
        LOG.debug("Starting UCSNifiSession instance: {}", this.toString());
        
        String nifiURL = this.nifiScheme+"://"+this.nifiHost+":{port}"+this.nifiCommandContext;
        
        this.niFiHTTPBroker = new NiFiHTTPBroker(
                new URL(nifiURL.replace("{port}", String.valueOf(this.nifiSendMessageCommandPort))), 
                new NiFiHTTPBroker.ClientEndpointWithListener(new URL(nifiURL.replace("{port}", String.valueOf(this.nifiClientCommandPort))), this.clientHost, this.clientCallbackPort, ucsClientListener),
                new NiFiHTTPBroker.ClientEndpointWithListener(new URL(nifiURL.replace("{port}", String.valueOf(this.nifiAlertingCommandPort))), this.clientHost, this.clientAlertingCallbackPort, ucsAlertingListener),
                new NiFiHTTPBroker.ClientEndpoint(new URL(nifiURL.replace("{port}", String.valueOf(this.nifiManagementCommandPort))), this.clientHost, this.clientManagementCallbackPort),
                new NiFiHTTPBroker.ClientEndpoint(new URL(nifiURL.replace("{port}", String.valueOf(this.nifiConversationCommandPort))), this.clientHost, this.clientConversationCallbackPort)
        );
        this.niFiHTTPBroker.start();
    }

    public synchronized void dispose() throws IOException{
        this.getNiFiHTTPBroker().stop();
    }
    
    public synchronized ClientIntf getNewClient() {
        return new ClientImpl(this.getNiFiHTTPBroker());
    }
    
    public synchronized AlertingIntf getNewAlerting() {
        return new AlertingImpl(this.getNiFiHTTPBroker());
    }
    
    public synchronized ManagementIntf getNewManagement() {
        return new ManagementImpl(this.getNiFiHTTPBroker());
    }
    
    public synchronized ConversationIntf getNewConversation() {
        return new ConversationImpl(this.getNiFiHTTPBroker());
    }

    @Override
    public String toString() {
        return "UCSNiFiSession{" + "status=" + status + ", nifiScheme=" + nifiScheme + ", nifiHost=" + nifiHost + ", nifiCommandContext=" + nifiCommandContext + ", nifiSendMessageCommandPort=" + nifiSendMessageCommandPort + ", nifiClientCommandPort=" + nifiClientCommandPort + ", nifiAlertingCommandPort=" + nifiAlertingCommandPort + ", nifiManagementCommandPort=" + nifiManagementCommandPort + ", nifiConversationCommandPort=" + nifiConversationCommandPort + ", clientHost=" + clientHost + ", clientCallbackPort=" + clientCallbackPort + ", clientAlertingCallbackPort=" + clientAlertingCallbackPort + ", clientManagementCallbackPort=" + clientManagementCallbackPort + ", clientConversationCallbackPort=" + clientConversationCallbackPort + ", niFiHTTPBroker=" + niFiHTTPBroker + '}';
    }

}

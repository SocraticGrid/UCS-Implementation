/*
 * Copyright 2016 Apache NiFi Project.
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
package org.socraticgrid.hl7.ucs.nifi.client.example;

/**
 *
 * @author esteban
 */
public class Configuration {
    /**
     * The host where ucs-nifi is running.
     */
    private String nifiHost;
    /**
     * The port where ucs-nifi has registered its client interface.
     */
    private int nifiClientCommandPort;
    /**
     * The port where ucs-nifi has registered its alerting interface.
     */
    private int nifiAlertingCommandPort;
    
    /**
     * Host that ucs-nifi will use to communicate to the client. This host
     * must be accessible by ucs-nifi.
     */
    private String clientHost;
    /**
     * Port that ucs-nifi will use to communicate to the client interface in the
     * client side.
     */
    private int clientPort;
    /**
     * Port that ucs-nifi will use to communicate to the alerting interface
     * in the client side.
     */
    private int alertingPort;
    /**
     * Port that ucs-nifi will use to communicate to the management interface
     * in the client side.
     */
    private int managementPort;
    /**
     * Port that ucs-nifi will use to communicate to the conversation interface
     * in the client side.
     */
    private int conversationPort;

    public String getNifiHost() {
        return nifiHost;
    }

    public void setNifiHost(String nifiHost) {
        this.nifiHost = nifiHost;
    }

    public int getNifiClientCommandPort() {
        return nifiClientCommandPort;
    }

    public void setNifiClientCommandPort(int nifiClientCommandPort) {
        this.nifiClientCommandPort = nifiClientCommandPort;
    }

    public int getNifiAlertingCommandPort() {
        return nifiAlertingCommandPort;
    }

    public void setNifiAlertingCommandPort(int nifiAlertingCommandPort) {
        this.nifiAlertingCommandPort = nifiAlertingCommandPort;
    }

    public String getClientHost() {
        return clientHost;
    }

    public void setClientHost(String clientHost) {
        this.clientHost = clientHost;
    }

    public int getClientPort() {
        return clientPort;
    }

    public void setClientPort(int clientPort) {
        this.clientPort = clientPort;
    }

    public int getAlertingPort() {
        return alertingPort;
    }

    public void setAlertingPort(int alertingPort) {
        this.alertingPort = alertingPort;
    }

    public int getManagementPort() {
        return managementPort;
    }

    public void setManagementPort(int managementPort) {
        this.managementPort = managementPort;
    }

    public int getConversationPort() {
        return conversationPort;
    }

    public void setConversationPort(int conversationPort) {
        this.conversationPort = conversationPort;
    }
    
}

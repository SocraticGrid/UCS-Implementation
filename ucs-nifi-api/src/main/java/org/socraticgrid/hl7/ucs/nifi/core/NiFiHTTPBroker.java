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
package org.socraticgrid.hl7.ucs.nifi.core;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.socraticgrid.hl7.services.uc.exceptions.UCSException;
import org.socraticgrid.hl7.services.uc.interfaces.UCSAlertingIntf;
import org.socraticgrid.hl7.services.uc.interfaces.UCSClientIntf;
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializer;

/**
 *
 * @author esteban
 */
public class NiFiHTTPBroker {
    private static final Logger LOG = LoggerFactory.getLogger(NiFiHTTPBroker.class);

    public static class ClientEndpoint  {
        private final URL commandURL;
        private final String clientHost;
        private final int clientPort;
        private HttpServer callbackListener;

        public ClientEndpoint(URL commandURL, String clientHost, int clientPort) {
            this.commandURL = commandURL;
            this.clientHost = clientHost;
            this.clientPort = clientPort;
        }

        public URL getCommandURL() {
            return commandURL;
        }

        public String getClientHost() {
            return clientHost;
        }

        public int getClientPort() {
            return clientPort;
        }

        public HttpServer getCallbackListener() {
            return callbackListener;
        }

        protected void setCallbackListener(HttpServer callbackListener) {
            this.callbackListener = callbackListener;
        }
    }
    
    public static class ClientEndpointWithListener<T> extends ClientEndpoint  {
        private final T listener;

        public ClientEndpointWithListener(URL commandURL, String clientHost, int clientPort, T listener) {
            super(commandURL, clientHost, clientPort);
            this.listener = listener;
        }

        public T getListener() {
            return listener;
        }
        
    }
    
    /**
     * Time to wait after a command is sent to Nifi (in seconds).
     */
    public static long COMMAND_RESPONSE_TIMEOUT = 10;
    
    private final URL nifiSendMessageURL;
    
    private final ClientEndpointWithListener<UCSClientIntf> ucsClientEndpoint;
    private final ClientEndpointWithListener<UCSAlertingIntf> ucsAlertingEndpoint;
    private final ClientEndpoint managementEndpoint;
    private final ClientEndpoint conversationEndpoint;
    
    public NiFiHTTPBroker(URL nifiSendMessageURL, ClientEndpointWithListener<UCSClientIntf> ucsClientEndpoint, ClientEndpointWithListener<UCSAlertingIntf> ucsAlertingEndpoint, ClientEndpoint managementEndpoint, ClientEndpoint conversationEndpoint) {
        this.nifiSendMessageURL = nifiSendMessageURL;
        this.ucsClientEndpoint = ucsClientEndpoint;
        this.ucsAlertingEndpoint = ucsAlertingEndpoint;
        this.managementEndpoint = managementEndpoint;
        this.conversationEndpoint = conversationEndpoint;
    }

    public void sendMessage(Message message) throws MessageSerializationException, IOException {
        
        int responseCode = this.sendPOST(nifiSendMessageURL, MessageSerializer.serializeMessageWrapper(new MessageWrapper(message)));
        
        if (responseCode != 200){
            throw new IOException("NiFi returned "+responseCode+" code.");
        }
    }
    
    public void start() throws IOException, InterruptedException{
        this.startClientEndpoints();
        this.startUCSListeners();
    }
    
    public void startClientEndpoints() throws IOException, InterruptedException {
        this.startClientEndpoint();
        this.startAlertingEndpoint();
        this.startManagementEndpoint();
        this.startConversationEndpoint();
    }
    
    public void startUCSListeners() throws IOException, InterruptedException {
        this.startUCSClientListener();
        this.startUCSAlertingListener();
    }
    
    public void startClientEndpoint() throws IOException, InterruptedException {
        InetSocketAddress address = new InetSocketAddress(this.ucsClientEndpoint.getClientHost(), this.ucsClientEndpoint.getClientPort());
        LOG.debug("Starting Client listener on {}.", address);
        this.ucsClientEndpoint.setCallbackListener(HttpServer.create(address, 0));
        this.ucsClientEndpoint.getCallbackListener().setExecutor(Executors.newCachedThreadPool());
        
        this.ucsClientEndpoint.getCallbackListener().createContext("/exception", new UCSClientExceptionHandler(this.ucsClientEndpoint.getListener()));
        this.ucsClientEndpoint.getCallbackListener().createContext("/newMessage", new UCSClientMessageHandler(this.ucsClientEndpoint.getListener()));
        this.ucsClientEndpoint.getCallbackListener().createContext("/response", new UCSClientResponseHandler(this.ucsClientEndpoint.getListener()));
        
        this.ucsClientEndpoint.getCallbackListener().start();
    }
    
    public void startUCSClientListener() throws IOException, InterruptedException {
        try {
            //register the UCSClient in Nifi
            this.sendClientCommand("registerUCSClientCallback", Optional.of(Arrays.asList("http://"+this.ucsClientEndpoint.getClientHost()+":"+this.ucsClientEndpoint.getClientPort())), true);
        } catch (UCSException ex) {
            throw new IllegalStateException("Unable to register UCSClient listener endpoint", ex);
        }
    }
    
    public void startAlertingEndpoint() throws IOException, InterruptedException {
        InetSocketAddress address = new InetSocketAddress(this.ucsAlertingEndpoint.getClientHost(), this.ucsAlertingEndpoint.getClientPort());
        LOG.debug("Starting UCSAlerting listener on {}.", address);
        this.ucsAlertingEndpoint.setCallbackListener(HttpServer.create(address, 0));
        this.ucsAlertingEndpoint.getCallbackListener().setExecutor(Executors.newCachedThreadPool());
        
        this.ucsAlertingEndpoint.getCallbackListener().createContext("/newAlertMessage", new UCSAlertingMessageReceivedHandler(this.ucsAlertingEndpoint.getListener()));
        this.ucsAlertingEndpoint.getCallbackListener().createContext("/alertMessageUpdated", new UCSAlertingMessageUpdatedHandler(this.ucsAlertingEndpoint.getListener()));
        this.ucsAlertingEndpoint.getCallbackListener().createContext("/alertMessageCancelled", new UCSAlertingMessageCanceledHandler(this.ucsAlertingEndpoint.getListener()));
        
        this.ucsAlertingEndpoint.getCallbackListener().start();
        
    }
    
    public void startUCSAlertingListener() throws IOException, InterruptedException {
        //register the UCSAlerting in Nifi
        try{
            this.sendAlertingCommand("registerUCSAlertingCallback", Optional.of(Arrays.asList("http://"+this.ucsAlertingEndpoint.getClientHost()+":"+this.ucsAlertingEndpoint.getClientPort())), true);
        } catch (UCSException ex) {
            throw new IllegalStateException("Unable to register UCSAlerting listener endpoint", ex);
        }
    }
    
    public void startManagementEndpoint() throws IOException, InterruptedException {
        InetSocketAddress address = new InetSocketAddress(this.managementEndpoint.getClientHost(), this.managementEndpoint.getClientPort());
        LOG.debug("Starting Management listener on {}.", address);
        this.managementEndpoint.setCallbackListener(HttpServer.create(address, 0));
        this.managementEndpoint.getCallbackListener().setExecutor(Executors.newCachedThreadPool());
        
        this.managementEndpoint.getCallbackListener().start();
        
    }
    
    public void startConversationEndpoint() throws IOException, InterruptedException {
        InetSocketAddress address = new InetSocketAddress(this.conversationEndpoint.getClientHost(), this.conversationEndpoint.getClientPort());
        LOG.debug("Starting Conversation listener on {}.", address);
        this.conversationEndpoint.setCallbackListener(HttpServer.create(address, 0));
        this.conversationEndpoint.getCallbackListener().setExecutor(Executors.newCachedThreadPool());
        
        this.conversationEndpoint.getCallbackListener().start();
        
    }
    
    public void stop() throws IOException{
        this.stopClientEndpoints();
    }
    
    public void stopClientEndpoints() throws IOException {
        this.stopClientEndpoint();
        this.stopAlertingEndpoint();
        this.stopManagementEndpoint();
        this.stopConversationEndpoint();
    }
    
    public void stopClientEndpoint() throws IOException {
        if (this.ucsClientEndpoint.getCallbackListener() != null){
            this.ucsClientEndpoint.getCallbackListener().stop(2);
        }
    }
    
    public void stopAlertingEndpoint() throws IOException {
        if (this.ucsAlertingEndpoint.getCallbackListener() != null){
            this.ucsAlertingEndpoint.getCallbackListener().stop(2);
        }
    }
    
    public void stopManagementEndpoint() throws IOException {
        if (this.managementEndpoint.getCallbackListener() != null){
            this.managementEndpoint.getCallbackListener().stop(2);
        }
    }
    
    public void stopConversationEndpoint() throws IOException {
        if (this.conversationEndpoint.getCallbackListener() != null){
            this.conversationEndpoint.getCallbackListener().stop(2);
        }
    }
    
    public String sendClientCommand(String name, Optional<List<String>> args, boolean waitForResponse) throws IOException, InterruptedException, UCSException{
        return this.sendCommand(ucsClientEndpoint, name, args, waitForResponse);
    }
    
    public String sendAlertingCommand(String name, Optional<List<String>> args, boolean waitForResponse) throws IOException, InterruptedException, UCSException{
        return this.sendCommand(ucsAlertingEndpoint, name, args, waitForResponse);
    }
    
    public String sendManagementCommand(String name, Optional<List<String>> args, boolean waitForResponse) throws IOException, InterruptedException, UCSException{
        return this.sendCommand(managementEndpoint, name, args, waitForResponse);
    }
    
    public String sendConversationCommand(String name, Optional<List<String>> args, boolean waitForResponse) throws IOException, InterruptedException, UCSException{
        return this.sendCommand(conversationEndpoint, name, args, waitForResponse);
    }
    
    public String sendCommand(ClientEndpoint endpoint, String name, Optional<List<String>> args, boolean waitForResponse) throws IOException, InterruptedException, UCSException{
        
        String commandUUID = UUID.randomUUID().toString();
        
        String host = endpoint.getClientHost();
        String port = ""+endpoint.getClientPort();
        String context = commandUUID;
        
        final CountDownLatch latch = new CountDownLatch(1);
        final StringBuilder response = new StringBuilder();
        final List<NiFiHTTPExceptionHandler> exceptionHandlers = new ArrayList<>();
        
        if (waitForResponse){
            endpoint.getCallbackListener().createContext("/"+context, (HttpExchange he) -> {
                
                exceptionHandlers.add(new NiFiHTTPExceptionHandler(he));
                
                //get ther response
                response.append(IOUtils.toString(he.getRequestBody()));

                //send OK to Nifi
                he.sendResponseHeaders(200, 0);
                he.close();

                //release the latch
                latch.countDown();
            });
        }
        
        //TODO: move this to a template
        final StringBuilder buffer = new StringBuilder();
        buffer.append("<command>");
        buffer.append("     <name>").append(name).append("</name>");
        if (args.isPresent() && !args.get().isEmpty()){
            buffer.append("     <args>");
            args.get().forEach(a -> buffer.append("         <arg>").append(a).append("</arg>"));
            buffer.append("     </args>");
        }
        if (waitForResponse){
            buffer.append("     <response>");
            buffer.append("         <host>").append(host).append("</host>");
            buffer.append("         <port>").append(port).append("</port>");
            buffer.append("         <context>").append(context).append("</context>");
            buffer.append("     </response>");
            
        }
        buffer.append("</command>");
        
        this.sendPOST(endpoint.getCommandURL(), buffer.toString());
        
        //wait for response
        if (waitForResponse){
            try{
                if (latch.await(COMMAND_RESPONSE_TIMEOUT, TimeUnit.SECONDS)){
                    
                    //if the response was a UCSException, throw it
                    exceptionHandlers.get(0).throwUCSException();
                    
                    //Ok, we got a response before timeout!
                    return response.toString();
                } else {
                    //Too bad... Timeout!
                    throw new IOException("Timeout while waiting for command response");
                }
            } finally {
                try{
                    endpoint.getCallbackListener().removeContext("/"+context);
                } catch (IllegalArgumentException e){
                    //the context was already removed.
                }
            }
        }
        
        return null;
    }

    private int sendPOST(URL url, String content) throws IOException {
        HttpURLConnection connection = null;
        try {
            //Create connection
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type",
                    "text/plain");

            connection.setRequestProperty("Content-Length", ""
                    + Integer.toString(content.getBytes().length));
            connection.setRequestProperty("Content-Language", "UTF-8");

            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setDoOutput(true);

            //Send request
            DataOutputStream wr = new DataOutputStream(
                    connection.getOutputStream());
            wr.writeBytes(content);
            wr.flush();
            wr.close();

            //Get Response    
            int responseCode = connection.getResponseCode();
            return responseCode;

        } finally {

            if (connection != null) {
                connection.disconnect();
            }
        }
    }

}

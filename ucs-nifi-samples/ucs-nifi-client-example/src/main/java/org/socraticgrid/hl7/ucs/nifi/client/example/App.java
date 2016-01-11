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

import com.google.gson.Gson;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.socraticgrid.hl7.ucs.nifi.api.UCSNiFiSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author esteban
 */
public class App {
    
    public static final String CL_CONFIG_FILE = "cf";
    public static final String CL_CONFIG_STRING = "cs";
    
    private static final Logger LOG = LoggerFactory.getLogger(App.class);
    
    public static void main(String[] args) throws IOException, InterruptedException {
        
        Options options = new Options();
        options.addOption(CL_CONFIG_FILE, "config-file", true, "The configuration file used to run this application.");
        options.addOption(CL_CONFIG_STRING, "config-file", true, "The configuration, as a JSON String, used to run this application.");
        
        
        Configuration config = null;
        
        try {
            CommandLineParser parser = new DefaultParser();
            CommandLine cl = parser.parse(options, args);
            
            
            if (cl.hasOption(CL_CONFIG_FILE)){
                File file = new File(cl.getOptionValue(CL_CONFIG_FILE));
                
                if (!file.exists() || !file.isFile()){
                    throw new IllegalArgumentException(file.getAbsolutePath()+" is not a file!");
                }
                
                config = new Gson().fromJson(new FileReader(file), Configuration.class);
            } else if (cl.hasOption(CL_CONFIG_STRING)){
                config = new Gson().fromJson(cl.getOptionValue(CL_CONFIG_STRING), Configuration.class);
            }
            
            if (config == null){
                throw new IllegalArgumentException("No configuration was provided!");
            }
            
        } catch (Exception e) {
            LOG.error("Error", e);
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("jcp-sim-client-example", options);
            System.exit(1);
        }
        
        new App().start(config);
        
    }
    
    public void start(Configuration config) throws IOException{
        
        String nifiHost = "http://"+config.getNifiHost();
        
        String alertingCommandURL = nifiHost+":"+config.getNifiAlertingCommandPort()+"/contentListener";
        String clientCommandURL = nifiHost+":"+config.getNifiClientCommandPort()+"/contentListener";
        
        
        LOG.debug("UCSNotifier Configuration:");
        LOG.debug("\tnifiHost: {}", nifiHost);
        LOG.debug("\talertingCommandURL: {}", alertingCommandURL);
        LOG.debug("\tclientCommandURL: {}", clientCommandURL);
        //LOG.debug("\tmanagementCommandURL: {}", managementCommandURL);
        //LOG.debug("\tconversationCommandURL: {}", conversationCommandURL);
        //LOG.debug("\tsendMessageURL: {}", sendMessageURL);
        LOG.debug("\tClient Host: {}", config.getClientHost());
        LOG.debug("\tClient Port: {}", config.getClientPort());
        LOG.debug("\tManagement Port: {}", config.getManagementPort());
        LOG.debug("\tConversation Port: {}", config.getConversationPort());
        
        UCSNiFiSession session = null;
        try {
            LOG.debug("Creating Nifi Session");
            session = new UCSNiFiSession.UCSNiFiSessionBuilder()
                    .withAlertingCommandURL(alertingCommandURL)
                    .withClientCommandURL(clientCommandURL)
                    //.withManagementCommandURL(managementCommandURL)
                    //.withConversationCommandURL(conversationCommandURL)
                    //.withNifiSendMessageURL(sendMessageURL)
                    .withUCSClientHost(config.getClientHost())
                    .withUCSClientPort(config.getClientPort())
                    .withUCSAlertingHost(config.getClientHost())
                    .withUCSAlertingPort(config.getAlertingPort())
                    .withManagementHost(config.getClientHost())
                    .withManagementPort(config.getManagementPort())
                    .withConversationHost(config.getClientHost())
                    .withConversationPort(config.getConversationPort())
                    .withUCSClientListener(new UCSClientAdapter(System.out))
                    .withUCSAlertingListener(new UCSAlertingAdapter(System.out)).build();
            LOG.debug("Nifi Session Created");
            
            LOG.debug("Starting UCS Client");
            session.getNewClient();
            LOG.debug("Application is now running");
            Thread.currentThread().join();
        } catch (IOException | InterruptedException ex) {
            LOG.error("Exception found.", ex);
        } finally {
            if (session != null){
                LOG.debug("Disposing UCS Session");
                session.dispose();
            }
        }
    }
}

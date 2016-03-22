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
 * Simple application that creates a UCS-Nifi session and listen to incoming
 * Messages, Alerts and Exceptions. Whenever a new notification regarding a
 * Message, Alert or Exception is received, it is printed to the console.
 * 
 * The purpose of this application is to serve as an example on how to create
 * and configure UCS-Nifi client sessions.
 * @author esteban
 */
public class App {
    
    public static final String CL_CONFIG_FILE = "cf";
    public static final String CL_CONFIG_STRING = "cs";
    
    private static final Logger LOG = LoggerFactory.getLogger(App.class);
    
    public static void main(String[] args) throws IOException, InterruptedException {
        
        // The application expects some configuration to be sent from the command
        // line. This configuration can come as a JSON file (using the -cf argument)
        // or in the form of a JSON String (using the -cs argument).
        // Check the Configuration class to get a better understanding of what
        // are the different configuration options and their meaning.
        
        Options options = new Options();
        options.addOption(CL_CONFIG_FILE, "config-file", true, "The configuration file used to run this application.");
        options.addOption(CL_CONFIG_STRING, "config-string", true, "The configuration, as a JSON String, used to run this application.");
        
        
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
        
        //Now that we have a valid configuration, we can start our app.
        new App().start(config);
        
    }
    
    public void start(Configuration config) throws IOException{
        
        LOG.debug("UCSNotifier Configuration:");
        LOG.debug("\tnifiHost: {}", config.getNifiHost());
        LOG.debug("\tnifiAlertingCommandPort: {}", config.getNifiAlertingCommandPort());
        LOG.debug("\tnifiClientCommandPort: {}", config.getNifiClientCommandPort());
        LOG.debug("\tClient Host: {}", config.getClientHost());
        
        //The communication between a client and UCS-Nifi is done thourgh HTTP.
        //This requires the client to start an internal HTTP server that will be 
        //asynchronously accessed by UCS-Nifi when required. The setup and
        //execution of this HTTP server as well as the specific details about
        //the format of the messages being used in this communication are 
        //encapsulated in the org.socraticgrid.hl7.ucs.nifi.api.UCSNiFiSession
        //class.
        //We can get an instance of this class by using the Builder it provides.
        //Note the 2 listeners we are using to deal with incoming messsages and
        //alerts: UCSClientAdapter and UCSAlertingAdapter.
        
        UCSNiFiSession session = null;
        try {
            LOG.debug("Creating Nifi Session");
            session = new UCSNiFiSession.UCSNiFiSessionBuilder()
                .withNifiHost(config.getNifiHost())
                .withNifiAlertingCommandPort(config.getNifiAlertingCommandPort())
                .withNifiClientCommandPort(config.getNifiClientCommandPort())
                .withClientHost(config.getClientHost())
                .withUCSClientListener(new UCSClientAdapter(System.out))
                .withUCSAlertingListener(new UCSAlertingAdapter(System.out)).build();
            LOG.debug("Nifi Session Created");
            
            LOG.debug("Starting UCS Client");
            
            //At this point, the session is configured but not started. Currently, 
            //there is not a public way to start the session. The only way we
            //have (we may need to review this in the future) to start the session
            //is to ask for some of the services it provides (i.e. client, alerting
            //conversation or management). These services are used to communicate
            //with UCS-Nifi. Because our application will only listen to notifications
            //coming from UCS-Nifi and it will never initiate an interaction, we
            //don't acutually need any of this services in this application.
            session.getNewClient();
            
            
            LOG.debug("Application is now running");
            Thread.currentThread().join();
        } catch (Exception ex) {
            LOG.error("Exception found.", ex);
        } finally {
            if (session != null){
                LOG.debug("Disposing UCS Session");
                session.dispose();
            }
        }
    }
}

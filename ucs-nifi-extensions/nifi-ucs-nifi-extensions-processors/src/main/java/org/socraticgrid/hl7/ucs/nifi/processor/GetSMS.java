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
package org.socraticgrid.hl7.ucs.nifi.processor;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.nifi.annotation.behavior.TriggerWhenEmpty;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.logging.ProcessorLog;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.io.OutputStreamCallback;
import org.apache.nifi.processor.util.StandardValidators;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.io.IOUtils;
import org.socraticgrid.hl7.services.uc.exceptions.ExceptionType;
import org.socraticgrid.hl7.ucs.nifi.controller.SMSController;

@TriggerWhenEmpty
@Tags({"sms", "text"})
@CapabilityDescription("Get text messages from specified sms id or number of meesages wish to see.")
public class GetSMS extends AbstractProcessor {

    public static final PropertyDescriptor SMS_SERVER_URL = new PropertyDescriptor.Builder()
            .name("SMS server url value")
            .description("SMS server url to send text message")
            .required(true)
            .addValidator(StandardValidators.URL_VALIDATOR)
            .build();

    public static final PropertyDescriptor SMS_SERVER_ACCOUNT_KEY = new PropertyDescriptor.Builder()
            .name("SMS server account key value")
            .description("SMS Server Account Key to send message")
            .required(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor SMS_MESSAGE_COUNT = new PropertyDescriptor.Builder()
            .name("SMS Message Count")
            .description("SMS message count to fetch")
            .required(true)
            .defaultValue("50")
            .addValidator(StandardValidators.POSITIVE_INTEGER_VALIDATOR)
            .build();

    public static final PropertyDescriptor SMS_CONTROLLER_SERVICE = new PropertyDescriptor.Builder()
            .name("SMS Controller Service")
            .description("The SMS Controller Service that this Processor uses behind the scenes.")
            .identifiesControllerService(SMSController.class)
            .required(true)
            .build();

    public static final Relationship REL_SMS_RECEIVED = new Relationship.Builder().name("getsms").description("The orginal text messages received from specified reference id.").build();
    public static final Relationship REL_SMS_FAILURE = new Relationship.Builder().name("failure").description("If sms can not get for some reason.").build();

    private List<PropertyDescriptor> properties;
    private Set<Relationship> relationships;

    @Override
    protected void init(final ProcessorInitializationContext context) {
        final List<PropertyDescriptor> properties = new ArrayList<>();
        properties.add(SMS_CONTROLLER_SERVICE);
        properties.add(SMS_SERVER_URL);
        properties.add(SMS_SERVER_ACCOUNT_KEY);
        properties.add(SMS_MESSAGE_COUNT);

        this.properties = Collections.unmodifiableList(properties);

        final Set<Relationship> relationships = new HashSet<>();
        relationships.add(REL_SMS_RECEIVED);
        relationships.add(REL_SMS_FAILURE);
        this.relationships = Collections.unmodifiableSet(relationships);
    }

    @Override
    public Set<Relationship> getRelationships() {
        return relationships;
    }

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return properties;
    }

    @Override
    public void onTrigger(final ProcessContext context, final ProcessSession session) {
        final ProcessorLog logger = getLogger();
        Collection<FlowFile> flowFiles = new ArrayList<>();
        try {
            //fetch property values
            SMSController smsControllerService = context.getProperty(SMS_CONTROLLER_SERVICE).asControllerService(SMSController.class);

            final String messageCount = context.getProperty(SMS_MESSAGE_COUNT).getValue();
            final String smsServerUrl = context.getProperty(SMS_SERVER_URL).getValue();
            final String smsServerKey = context.getProperty(SMS_SERVER_ACCOUNT_KEY).getValue();

            logger.info("smsServerUrlVal:" + smsServerUrl);
            logger.info("smsServerKeyVal:" + smsServerKey);
            logger.info("messageCount:" + messageCount);

            String response = getSMS(smsServerKey, smsServerUrl, messageCount, smsControllerService, logger);
            logger.info("Response: " + response);
            flowFiles = parseJsonSring(session, response, smsControllerService);
            
            session.transfer(flowFiles, REL_SMS_RECEIVED);

        } catch (Exception e) {
            logger.error("Exception receiving SMS messages.", e);
            UCSCreateException.routeFlowFileToException(
                        context,
                        session,
                        logger,
                        session.create(),
                        REL_SMS_FAILURE,
                        null,
                        "Exception receiving SMS messages: '"+e.getMessage(),
                        ExceptionType.ServerAdapterFault,
                        null,
                        null);
        }

    }

    private String getSMS(String smsServerKey, String smsServerUrl, String messageCount, SMSController smsControllerService, ProcessorLog logger) throws Exception {

        if (smsServerUrl != null && !smsServerUrl.endsWith("/")) {
            smsServerUrl = smsServerUrl + "/";
        }
        HttpClient client = HttpClientBuilder.create().build();
        String endPointUrl = "";
        String lastReceviedSMSId = smsControllerService.getLastReceivedSMSId();
        if (lastReceviedSMSId != null) {
            logger.debug("Last Received SMS Id is {}. Asking for newest messages.", new Object[]{lastReceviedSMSId});
            endPointUrl = smsServerUrl + smsServerKey + "/afterId/" + smsControllerService.getLastReceivedSMSId();
        } else {
            logger.debug("We don't have any Last Received SMS Id. Asking for the last {} messages.", new Object[]{messageCount});
            endPointUrl = smsServerUrl + smsServerKey + "/count/" + messageCount;
        }
        HttpGet get = new HttpGet(endPointUrl);

        HttpResponse response = client.execute(get);
        
        return IOUtils.toString(response.getEntity().getContent());
    }

    private Collection<FlowFile> parseJsonSring(final ProcessSession session, String jsonString, SMSController smsControllerService) {
        Collection<FlowFile> flowFiles = new ArrayList<>();
        //parse json input
        long prev = 0l;
        JsonParser jsonParser = new JsonParser();
        JsonArray jsonArray = (JsonArray) jsonParser.parse(jsonString);
        for (JsonElement jsonElement : jsonArray) {
            final JsonObject jsonObj = jsonElement.getAsJsonObject();
            long curr = jsonObj.get("MessageNumber").getAsLong();
            if (prev < curr) {
                prev = curr;
            }
            FlowFile flowFile = session.create();

            // To write the results back out to flow file
            flowFile = session.write(flowFile, new OutputStreamCallback() {

                @Override
                public void process(OutputStream out) throws IOException {
                    out.write(jsonObj.toString().getBytes());

                }
            });
            
            session.getProvenanceReporter().create(flowFile);
            
            flowFiles.add(flowFile);
        }
        
        if (jsonArray.size() > 0){
            smsControllerService.setLastReceivedSMSId(String.valueOf(prev));
        }
        return flowFiles;

    }
}

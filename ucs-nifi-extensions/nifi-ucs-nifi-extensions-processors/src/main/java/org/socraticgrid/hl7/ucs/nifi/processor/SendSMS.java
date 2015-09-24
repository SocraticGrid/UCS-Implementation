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
package org.socraticgrid.hl7.ucs.nifi.processor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
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
import org.apache.nifi.processor.util.StandardValidators;
import org.socraticgrid.hl7.ucs.nifi.common.model.Status;
import org.socraticgrid.hl7.ucs.nifi.controller.ServiceStatusController;

@Tags({"sms", "text"})
@CapabilityDescription("Sends text message to provided recipient cell phone number.")
public class SendSMS extends AbstractProcessor {

    public static final PropertyDescriptor SMS_TEXT = new PropertyDescriptor.Builder()
            .name("SMS text attribute")
            .description("SMS text attribute to fetch from flow file")
            .required(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor SMS_NUMBER = new PropertyDescriptor.Builder()
            .name("SMS number attribute")
            .description("SMS Number attribute to fetch from flow file")
            .required(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

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

    public static final PropertyDescriptor SMS_REFERENCE = new PropertyDescriptor.Builder()
            .name("SMS message reference attribute")
            .description("SMS message reference attribute name")
            .required(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();
    
	public static final PropertyDescriptor SERVICE_STATUS_CONTROLLER_SERVICE = new PropertyDescriptor.Builder()
			.name("Service Status Controller Service")
			.description(
					"The Service Status Controller Service that this Processor uses to update the Status of this Adapter.")
			.identifiesControllerService(ServiceStatusController.class)
			.required(true).build();

    public static final Relationship REL_SMS_SEND = new Relationship.Builder().name("smssend").description("The orginal text message send to recipient cell phone number.").build();
    public static final Relationship REL_FAILURE = new Relationship.Builder().name("failure").description("If sms can not send for some reason, the original message will be routed to this destination").build();

    private List<PropertyDescriptor> properties;
    private Set<Relationship> relationships;

    @Override
    protected void init(final ProcessorInitializationContext context) {
        final List<PropertyDescriptor> properties = new ArrayList<>();
        properties.add(SMS_TEXT);
        properties.add(SMS_NUMBER);
        properties.add(SMS_SERVER_URL);
        properties.add(SMS_SERVER_ACCOUNT_KEY);
        properties.add(SMS_REFERENCE);
        properties.add(SERVICE_STATUS_CONTROLLER_SERVICE);

        this.properties = Collections.unmodifiableList(properties);

        final Set<Relationship> relationships = new HashSet<>();
        relationships.add(REL_SMS_SEND);
        relationships.add(REL_FAILURE);
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
    	ServiceStatusController serviceStatusControllerService = context
				.getProperty(SERVICE_STATUS_CONTROLLER_SERVICE)
				.asControllerService(ServiceStatusController.class);
        final FlowFile flowFile = session.get();
        if (flowFile == null) {
            return;
        }
        final ProcessorLog logger = getLogger();
        try {
            //fetch property values
            final String smsText = context.getProperty(SMS_TEXT).getValue();
            final String smsNumber = context.getProperty(SMS_NUMBER).getValue();
            final String smsReference = context.getProperty(SMS_REFERENCE).getValue();
            final String smsServerUrl = context.getProperty(SMS_SERVER_URL).getValue();
            final String smsServerKey = context.getProperty(SMS_SERVER_ACCOUNT_KEY).getValue();

        	//fetch actual values from flow file using property values
            Map<String, String> attributes = flowFile.getAttributes();
            final String smsTextAttrVal = attributes.get(smsText);
            final String smsNumberAttrVal = attributes.get(smsNumber);
            final String smsReferenceAttrVal = attributes.get(smsReference);

            logger.info("smsTextVal: " + smsTextAttrVal);
            logger.info("smsNumberVal: " + smsNumberAttrVal);
            logger.info("smsServerUrlVal: " + smsServerUrl);
            logger.info("smsServerKeyVal: " + smsServerKey);
            logger.info("smsReference: " + smsReferenceAttrVal);

            //check attributes values
            if (StringUtils.isEmpty(smsTextAttrVal) || StringUtils.isEmpty(smsNumberAttrVal) || StringUtils.isEmpty(smsReferenceAttrVal)) {
                logger.error("Required Attributes are missing in flow file." + attributes);
                session.transfer(flowFile, REL_FAILURE);
                session.getProvenanceReporter().route(flowFile, REL_FAILURE);
            } else {
                String response = sendSMS(smsTextAttrVal, smsNumberAttrVal, smsServerUrl, smsServerKey, smsReferenceAttrVal, serviceStatusControllerService);
                logger.info("Response: " + response);
                session.transfer(flowFile, REL_SMS_SEND);
            }

        } catch (Exception e) {
            e.printStackTrace();
            session.transfer(flowFile, REL_FAILURE);
            session.getProvenanceReporter().route(flowFile, REL_FAILURE);
        }

    }

    private String sendSMS(String smsText, String smsNumber, String smsServerUrl, String smsServerKey, String smsReference, ServiceStatusController serviceStatusControllerService) throws Exception {
    	StringBuffer result = new StringBuffer();
    	try {
			HttpClient client = HttpClientBuilder.create().build();
			if (smsServerUrl != null && !smsServerUrl.endsWith("/")) {
				smsServerUrl = smsServerUrl + "/";
			}

			HttpPost post = new HttpPost(smsServerUrl + smsServerKey + "/"
					+ smsNumber + "/Extended");
			String input = "{\"MessageBody\":\"" + smsText
					+ "\",\"Reference\": \"" + smsReference + "\"}";
			StringEntity params = new StringEntity(input, "UTF-8");
			params.setContentType("application/json; charset=UTF-8");
			post.setEntity(params);

			HttpResponse response = client.execute(post);
			BufferedReader rd = new BufferedReader(new InputStreamReader(
					response.getEntity().getContent()));

			String line = "";
			while ((line = rd.readLine()) != null) {
				result.append(line);
			}
			if (response.getStatusLine().getStatusCode() >= 400
					&& response.getStatusLine().getStatusCode() <= 599) {
				serviceStatusControllerService.updateServiceStatus("SMS",
						Status.UNAVAILABLE);
			} else {
				serviceStatusControllerService.updateServiceStatus("SMS",
						Status.AVAILABLE);
			}
		} catch (Exception e) {
			serviceStatusControllerService.updateServiceStatus("SMS",
					Status.UNAVAILABLE);
			getLogger().error("Error occurred while sending SMS!", e);
			throw e;
		}
		return result.toString();
	}
}

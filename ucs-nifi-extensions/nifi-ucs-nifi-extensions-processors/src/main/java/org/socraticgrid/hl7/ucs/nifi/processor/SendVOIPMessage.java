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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
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
import org.apache.nifi.processor.io.OutputStreamCallback;
import org.apache.nifi.processor.util.StandardValidators;
import org.socraticgrid.hl7.ucs.nifi.common.model.Status;
import org.socraticgrid.hl7.ucs.nifi.controller.ServiceStatusController;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@Tags({"voip", "message"})
@CapabilityDescription("Sends voip message to recipient phone number.")
public class SendVOIPMessage extends AbstractProcessor {

    public static final PropertyDescriptor VOIP_MSG_TEXT = new PropertyDescriptor.Builder()
            .name("Voip message text attribute")
            .description("Voip message text attribute to fetch from flow file")
            .required(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor VOIP_MSG_NUMBER = new PropertyDescriptor.Builder()
            .name("Voip message number attribute")
            .description("Voip message Number attribute to fetch from flow file")
            .required(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor VOIP_SERVER_URL = new PropertyDescriptor.Builder()
            .name("Voip message server url value")
            .description("Voip message server url to send message")
            .required(true)
            .addValidator(StandardValidators.URL_VALIDATOR)
            .build();

    public static final PropertyDescriptor VOIP_SERVER_TOKEN = new PropertyDescriptor.Builder()
            .name("Voip server token value")
            .description("Voip Server token value")
            .required(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();
    
	public static final PropertyDescriptor SERVICE_STATUS_CONTROLLER_SERVICE = new PropertyDescriptor.Builder()
			.name("Service Status Controller Service")
			.description(
					"The Service Status Controller Service that this Processor uses to update the Status of this Adapter.")
			.identifiesControllerService(ServiceStatusController.class)
			.required(true).build();

    public static final Relationship REL_MSG_SEND = new Relationship.Builder().name("success").description("The orginal text message send to recipient phone number.").build();
    public static final Relationship REL_FAILURE = new Relationship.Builder().name("failure").description("If sms can not send due to some reason, the original message will be routed to this destination").build();

    private List<PropertyDescriptor> properties;
    private Set<Relationship> relationships;

    @Override
    protected void init(final ProcessorInitializationContext context) {
        final List<PropertyDescriptor> properties = new ArrayList<>();
        properties.add(VOIP_MSG_TEXT);
        properties.add(VOIP_MSG_NUMBER);
        properties.add(VOIP_SERVER_URL);
        properties.add(VOIP_SERVER_TOKEN);
        properties.add(SERVICE_STATUS_CONTROLLER_SERVICE);

        this.properties = Collections.unmodifiableList(properties);

        final Set<Relationship> relationships = new HashSet<>();
        relationships.add(REL_MSG_SEND);
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
	public void onTrigger(final ProcessContext context,
			final ProcessSession session) {
		ServiceStatusController serviceStatusControllerService = context
				.getProperty(SERVICE_STATUS_CONTROLLER_SERVICE)
				.asControllerService(ServiceStatusController.class);
		FlowFile flowFile = session.get();
		if (flowFile == null) {
			return;
		}
		final ProcessorLog logger = getLogger();
		try {
			// fetch property values
			final String voipMsgText = context.getProperty(VOIP_MSG_TEXT).getValue();
			final String voidMsgNumber = context.getProperty(VOIP_MSG_NUMBER).getValue();
			final String voipServerUrl = context.getProperty(VOIP_SERVER_URL).getValue();
			final String voipServerToken = context.getProperty(VOIP_SERVER_TOKEN).getValue();

			// fetch actual values from flow file using property values
			Map<String, String> attributes = flowFile.getAttributes();
			final String voipMsgTextAttrVal = attributes.get(voipMsgText);
			final String voidMsgNumberAttrVal = attributes.get(voidMsgNumber);

			logger.info("voipServerUrl:" + voipServerUrl);
			logger.info("voipServerToken:" + voipServerToken);
			logger.info("voipMsgTextAttrVal:" + voipMsgTextAttrVal);
			logger.info("voidMsgNumberAttrVal:" + voidMsgNumberAttrVal);

			// send voip message
			String response = sendVoipMsg(voipServerUrl, voipServerToken,
					voipMsgTextAttrVal, voidMsgNumberAttrVal,
					serviceStatusControllerService);
			logger.info("Response: " + response);
			final String jsonResp = parseJsonSring(response);

			// To write the results back out to flow file
			flowFile = session.write(flowFile, new OutputStreamCallback() {

				@Override
				public void process(OutputStream out) throws IOException {
					out.write(jsonResp.getBytes());

				}
			});

			session.transfer(flowFile, REL_MSG_SEND);
			session.getProvenanceReporter().route(flowFile, REL_MSG_SEND);
		} catch (Exception e) {
			e.printStackTrace();
			session.transfer(flowFile, REL_FAILURE);
			session.getProvenanceReporter().route(flowFile, REL_FAILURE);
		}

	}

	private String sendVoipMsg(String voipServerUrl, String voipServerToken,
			String voipMsgTextAttrVal, String voidMsgNumberAttrVal,
			ServiceStatusController serviceStatusControllerService)
			throws ClientProtocolException, IOException {
		String status = "";
		try {
			HttpClient client = HttpClientBuilder.create().build();

			HttpPost post = new HttpPost(voipServerUrl);
			String input = "{\"token\":\"" + voipServerToken
					+ "\",\"numberToDial\":\"" + voidMsgNumberAttrVal
					+ "\",\"msg\":\"" + voipMsgTextAttrVal + "\"}";
			StringEntity params = new StringEntity(input, "UTF-8");
			params.setContentType("application/json; charset=UTF-8");
			post.setEntity(params);

			HttpResponse response = client.execute(post);
			status = IOUtils.toString(response.getEntity().getContent());
			if (response.getStatusLine().getStatusCode() >= 400
					&& response.getStatusLine().getStatusCode() <= 599) {
				serviceStatusControllerService.updateServiceStatus("VOIP",
						Status.UNAVAILABLE);
			} else {
				serviceStatusControllerService.updateServiceStatus("VOIP",
						Status.AVAILABLE);
			}
		} catch (Exception e) {
			serviceStatusControllerService.updateServiceStatus("VOIP",
					Status.UNAVAILABLE);
			getLogger().error("Error occurred while sending Voip Message!", e);
			throw e;
		}
		return status;
	} 
    
    private String parseJsonSring(String jsonString) { 
        //parse json input 
        JsonParser jsonParser = new JsonParser();
        JsonElement jsonElement = (JsonElement) jsonParser.parse(jsonString); 
        final JsonObject jsonObj = jsonElement.getAsJsonObject();  
        return jsonObj.toString();

    }
}

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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.socraticgrid.hl7.ucs.nifi.common.model.AdapterStatus;
import org.socraticgrid.hl7.ucs.nifi.controller.ServiceStatusControllerService;

/**
 * 
 * @author BasitAzeem
 *
 */
public class SendEmailTest {
	private TestRunner testRunner;
	private ServiceStatusControllerService statusService;

	@Before
	public void init() throws Exception {
		testRunner = TestRunners.newTestRunner(new SendEmail());
		
		statusService = new ServiceStatusControllerService();
        testRunner.addControllerService("service-status-controller", statusService);
        testRunner.enableControllerService(statusService);
        testRunner.setProperty(SendEmail.SERVICE_STATUS_CONTROLLER_SERVICE, "service-status-controller");
		
		testRunner.setProperty(SendEmail.SMTP_SERVER_URL, "smtp.gmail.com");
		testRunner.setProperty(SendEmail.SMTP_SERVER_PORT, "587");
		testRunner
				.setProperty(SendEmail.SMTP_USERNAME, "nifi@socraticgrid.org");
		testRunner.setProperty(SendEmail.SMTP_PASSWORD, "nifi_socraticgrid");
		testRunner.setProperty(SendEmail.BUFFER_SIZE, "10 MB");
		testRunner.setProperty(SendEmail.CHARACTER_SET, "UTF-8");
	}

	@Ignore
	public void doTestSendEmailWithFFAttribute() throws IOException {
		testRunner.setProperty(SendEmail.EMAIL_SUBJECT, "${email.subject}");
		testRunner.setProperty(SendEmail.TO_EMAIL, "${email.to}");
		testRunner.setProperty(SendEmail.FROM_EMAIL, "${email.from}");
		testRunner.setProperty(SendEmail.EMAIL_MIME_TYPE, "${email.mime.type}");
		Map<String, String> flowFileAttributes = new HashMap<>();
		flowFileAttributes.put("email.to", "er.basit@gmail.com");
		flowFileAttributes.put("email.from", "nifi@socraticgrid.com");
		flowFileAttributes.put("email.subject", "Test Email");
		flowFileAttributes.put("email.mime.type", "text/plain");
		testRunner.enqueue("This is just a test Email!".getBytes(),
				flowFileAttributes);
		testRunner.run();
		testRunner.assertAllFlowFilesTransferred(SendEmail.REL_EMAIL_SEND, 1);
		MockFlowFile flowFile = testRunner.getFlowFilesForRelationship(
				SendEmail.REL_EMAIL_SEND).get(0);
		Assert.assertNotNull(flowFile);
		Map<String, AdapterStatus> statusMap = statusService.getServiceStatusMap();
		Set<String> keySet = statusMap.keySet();
		for(String key : keySet) {
			AdapterStatus ad = statusMap.get(key);
			System.out.println(ad.getAdapterName());
			System.out.println(ad.getLastUpdateDateTime());
			System.out.println(ad.getStatus());
		}
	}

	@Ignore
	public void doTestSendEmailWithoutFFAttribute() throws IOException {
		testRunner.setProperty(SendEmail.EMAIL_SUBJECT, "Test Email");
		testRunner.setProperty(SendEmail.TO_EMAIL, "er.basit@gmail.com");
		testRunner.setProperty(SendEmail.FROM_EMAIL, "nifi@socraticgrid.com");
		testRunner.setProperty(SendEmail.EMAIL_MIME_TYPE, "text/plain");
		Map<String, String> flowFileAttributes = new HashMap<>();
		testRunner.enqueue("This is just a test Email!".getBytes(),
				flowFileAttributes);
		testRunner.run();
		testRunner.assertAllFlowFilesTransferred(SendEmail.REL_EMAIL_SEND, 1);
		MockFlowFile flowFile = testRunner.getFlowFilesForRelationship(
				SendEmail.REL_EMAIL_SEND).get(0);
		Assert.assertNotNull(flowFile);
	}

}

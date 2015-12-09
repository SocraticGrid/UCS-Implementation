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
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.commons.lang3.StringUtils;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.logging.ProcessorLog;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.DataUnit;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.io.InputStreamCallback;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.stream.io.StreamUtils;
import org.apache.nifi.util.IntegerHolder;
import org.socraticgrid.hl7.ucs.nifi.common.model.Status;
import org.socraticgrid.hl7.ucs.nifi.controller.ServiceStatusController;

@Tags({ "email", "text", "html" })
@CapabilityDescription("Sends email message to provided recipient email address.")
public class SendEmail extends AbstractProcessor {

	public static final PropertyDescriptor EMAIL_SUBJECT = new PropertyDescriptor.Builder()
			.name("Email Subject attribute")
			.description("Email subject attribute to fetch from flow file")
			.required(true).expressionLanguageSupported(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final PropertyDescriptor TO_EMAIL = new PropertyDescriptor.Builder()
			.name("To Email attribute")
			.description("To Email attribute to fetch from flow file")
			.required(true).expressionLanguageSupported(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final PropertyDescriptor FROM_EMAIL = new PropertyDescriptor.Builder()
			.name("From Email attribute")
			.description("From Email attribute to fetch from flow file")
			.required(true).expressionLanguageSupported(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final PropertyDescriptor EMAIL_MIME_TYPE = new PropertyDescriptor.Builder()
			.name("Email Mime Type attribute")
			.description("Email MimeType attribute to fetch from flow file")
			.required(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
			.expressionLanguageSupported(true).defaultValue("text/plain")
			.build();

	public static final PropertyDescriptor SMTP_SERVER_PORT = new PropertyDescriptor.Builder()
			.name("Outgoing Mail server port value")
			.description("Outgoing Mail server port to send Email")
			.required(true).expressionLanguageSupported(true)
			.addValidator(StandardValidators.INTEGER_VALIDATOR).build();

	public static final PropertyDescriptor SMTP_SERVER_URL = new PropertyDescriptor.Builder()
			.name("Outgoing Mail server url value")
			.description("Outgoing Mail server url to send Email")
			.required(true).expressionLanguageSupported(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final PropertyDescriptor SMTP_USERNAME = new PropertyDescriptor.Builder()
			.name("Outgoing Mail server username")
			.description("Outgoing Mail server username to send Email")
			.required(true).expressionLanguageSupported(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final PropertyDescriptor SMTP_PASSWORD = new PropertyDescriptor.Builder()
			.name("Outgoing Mail server password")
			.description("Outgoing Mail server password to send Email")
			.required(true).expressionLanguageSupported(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final PropertyDescriptor BUFFER_SIZE = new PropertyDescriptor.Builder()
			.name("Content Buffer Size")
			.description(
					"Specifies the maximum amount of data to buffer in order to send email. If the size of the FlowFile exceeds this value, any amount of this value will be ignored")
			.expressionLanguageSupported(true)
			.addValidator(StandardValidators.DATA_SIZE_VALIDATOR)
			.defaultValue("10 MB").build();

	public static final PropertyDescriptor CHARACTER_SET = new PropertyDescriptor.Builder()
			.name("Character Set")
			.description("The Character Set in which the mail body is encoded")
			.expressionLanguageSupported(true)
			.addValidator(StandardValidators.CHARACTER_SET_VALIDATOR)
			.defaultValue("UTF-8").build();

	public static final PropertyDescriptor SERVICE_STATUS_CONTROLLER_SERVICE = new PropertyDescriptor.Builder()
			.name("Service Status Controller Service")
			.description(
					"The Service Status Controller Service that this Processor uses to update the Status of this Adapter.")
			.identifiesControllerService(ServiceStatusController.class)
			.required(true).build();

	public static final Relationship REL_EMAIL_SEND = new Relationship.Builder()
			.name("emailsend")
			.description(
					"The orginal Email message send to recipient email address.")
			.build();
	public static final Relationship REL_FAILURE = new Relationship.Builder()
			.name("failure")
			.description(
					"If email can not send for some reason, the original message will be routed to this destination")
			.build();

	private List<PropertyDescriptor> properties;
	private Set<Relationship> relationships;

	@Override
	protected void init(final ProcessorInitializationContext context) {
		final List<PropertyDescriptor> properties = new ArrayList<>();
		properties.add(EMAIL_SUBJECT);
		properties.add(FROM_EMAIL);
		properties.add(TO_EMAIL);
		properties.add(EMAIL_MIME_TYPE);
		properties.add(SMTP_SERVER_URL);
		properties.add(SMTP_SERVER_PORT);
		properties.add(SMTP_USERNAME);
		properties.add(SMTP_PASSWORD);
		properties.add(CHARACTER_SET);
		properties.add(BUFFER_SIZE);
		properties.add(SERVICE_STATUS_CONTROLLER_SERVICE);

		this.properties = Collections.unmodifiableList(properties);

		final Set<Relationship> relationships = new HashSet<>();
		relationships.add(REL_EMAIL_SEND);
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
		final FlowFile flowFile = session.get();
		if (flowFile == null) {
			return;
		}
		final ProcessorLog logger = getLogger();
		try {
			// fetch property values
			final String emailSubject = context.getProperty(EMAIL_SUBJECT)
					.evaluateAttributeExpressions(flowFile).getValue();
			final String fromEmail = context.getProperty(FROM_EMAIL)
					.evaluateAttributeExpressions(flowFile).getValue();
			final String toEmail = context.getProperty(TO_EMAIL)
					.evaluateAttributeExpressions(flowFile).getValue();
			final String emailMimeType = context.getProperty(EMAIL_MIME_TYPE)
					.evaluateAttributeExpressions(flowFile).getValue();
			final String smtpServerUrl = context.getProperty(SMTP_SERVER_URL)
					.evaluateAttributeExpressions(flowFile).getValue();
			final String smtpServerPort = context.getProperty(SMTP_SERVER_PORT)
					.evaluateAttributeExpressions(flowFile).getValue();
			final String smtpUsername = context.getProperty(SMTP_USERNAME)
					.evaluateAttributeExpressions(flowFile).getValue();
			final String smtpPassword = context.getProperty(SMTP_PASSWORD)
					.evaluateAttributeExpressions(flowFile).getValue();

			logger.info("emailSubjectVal:" + emailSubject);
			logger.info("fromEmailVal:" + fromEmail);
			logger.info("toEmailVal:" + toEmail);
			logger.info("emailMimeType:" + emailMimeType);
			logger.info("smtpServerUrl:" + smtpServerUrl);
			logger.info("smtpServerPort:" + smtpServerPort);
			logger.info("smtpServerUsername:" + smtpUsername);
			logger.info("smtpServerPassword:" + smtpPassword);

			// check attributes values
			if (StringUtils.isEmpty(emailSubject)
					|| StringUtils.isEmpty(fromEmail)
					|| StringUtils.isEmpty(toEmail)) {
				logger.error("Required Attributes are missing in flow file.");
				session.transfer(flowFile, REL_FAILURE);
				session.getProvenanceReporter().route(flowFile, REL_FAILURE);
			} else {
				final String charsetProperty = context
						.getProperty(CHARACTER_SET)
						.evaluateAttributeExpressions(flowFile).getValue();
				final Charset charset = Charset.forName(charsetProperty);
				final int bufferSizeProperty = context.getProperty(BUFFER_SIZE)
						.evaluateAttributeExpressions(flowFile)
						.asDataSize(DataUnit.B).intValue();
				final byte[] buffer = new byte[bufferSizeProperty];
				final IntegerHolder bufferedByteCount = new IntegerHolder(0);
				session.read(flowFile, new InputStreamCallback() {
					@Override
					public void process(final InputStream in)
							throws IOException {
						bufferedByteCount.set(StreamUtils.fillBuffer(in,
								buffer, false));
					}
				});

				final String emailContent = new String(buffer, 0,
						bufferedByteCount.get(), charset);
				logger.info("emailContent:" + emailContent);
				String response = sendEmail(emailSubject, fromEmail, toEmail,
						emailContent, emailMimeType, charsetProperty,
						smtpServerUrl, smtpServerPort, smtpUsername,
						smtpPassword, serviceStatusControllerService);
				logger.info("Response: " + response);
				session.transfer(flowFile, REL_EMAIL_SEND);
			}

		} catch (Exception e) {
			logger.error("Some error occurred while Sending Email", e);
			session.transfer(flowFile, REL_FAILURE);
			session.getProvenanceReporter().route(flowFile, REL_FAILURE);
		}

	}

	private String sendEmail(String emailSubject, String fromEmail,
			String toEmail, String emailBody, String mimeType, String charset,
			String smtpServerUrl, String smtpServerPort, String username,
			String password, ServiceStatusController serviceStatusControllerService) throws Exception {

		String statusMessage = "Email sent successfully to " + toEmail;

		Properties props = new Properties();
		props.put("mail.smtp.auth", "true");
		props.put("mail.smtp.starttls.enable", "true");
		props.put("mail.smtp.host", smtpServerUrl);
		props.put("mail.smtp.port", smtpServerPort);

		Session session = Session.getInstance(props,
				new javax.mail.Authenticator() {
					protected PasswordAuthentication getPasswordAuthentication() {
						return new PasswordAuthentication(username, password);
					}
				});
		try {
			MimeMessage message = new MimeMessage(session);
			message.setFrom(new InternetAddress(fromEmail));
			message.setRecipients(Message.RecipientType.TO,
					InternetAddress.parse(toEmail));
			message.setSubject(emailSubject);
			message.setContent(emailBody, mimeType + "; charset=" + charset);
			Transport.send(message);
			getLogger().info("Email sent successfully!");
			serviceStatusControllerService.updateServiceStatus("EMAIL", Status.AVAILABLE);
		} catch (MessagingException e) {
			serviceStatusControllerService.updateServiceStatus("EMAIL", Status.UNAVAILABLE);
			getLogger().error(
					"Unable to send Email! Reason : " + e.getMessage(), e);
			statusMessage = "Unable to send Email! Reason : " + e.getMessage();
			throw new RuntimeException(e);
		}
		return statusMessage;
	}
}

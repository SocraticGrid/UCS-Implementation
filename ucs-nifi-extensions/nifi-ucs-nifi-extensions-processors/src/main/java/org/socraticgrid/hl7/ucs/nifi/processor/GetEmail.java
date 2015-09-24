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

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.mail.Address;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Store;

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
import org.apache.nifi.util.ObjectHolder;
import org.socraticgrid.hl7.services.uc.exceptions.ExceptionType;

@TriggerWhenEmpty
@Tags({ "email", "text", "html" })
@CapabilityDescription("Receives email message from the provided email address.")
public class GetEmail extends AbstractProcessor {

	public static final PropertyDescriptor IMAP_SERVER_URL = new PropertyDescriptor.Builder()
			.name("IMAP server url value")
			.description("IMAP server url to receive Email").required(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final PropertyDescriptor IMAP_USERNAME = new PropertyDescriptor.Builder()
			.name("IMAP server username")
			.description("IMAP server username to send Email").required(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final PropertyDescriptor IMAP_PASSWORD = new PropertyDescriptor.Builder()
			.name("IMAP server password")
			.description("IMAP server password to send Email").required(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final Relationship REL_NEW_MESSAGE = new Relationship.Builder()
			.name("emailReceived")
			.description(
					"The orginal Email message received from the email address.")
			.build();
	public static final Relationship REL_FAILURE = new Relationship.Builder()
			.name("failure")
			.description(
					"If email can not be received for some reason, exception will be routed to this destination")
			.build();

	private List<PropertyDescriptor> properties;
	private Set<Relationship> relationships;

	@Override
	protected void init(final ProcessorInitializationContext context) {
		final List<PropertyDescriptor> properties = new ArrayList<>();
		properties.add(IMAP_SERVER_URL);
		properties.add(IMAP_USERNAME);
		properties.add(IMAP_PASSWORD);

		this.properties = Collections.unmodifiableList(properties);

		final Set<Relationship> relationships = new HashSet<>();
		relationships.add(REL_NEW_MESSAGE);
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
		final ProcessorLog logger = getLogger();
		try {
			// fetch property values
			final String imapServerUrl = context.getProperty(IMAP_SERVER_URL)
					.getValue();
			final String imapUsername = context.getProperty(IMAP_USERNAME)
					.getValue();
			final String imapPassword = context.getProperty(IMAP_PASSWORD)
					.getValue();

			logger.info("smtpServerUrl:" + imapServerUrl);
			logger.info("smtpServerUsername:" + imapUsername);
			logger.info("smtpServerPassword:" + imapPassword);

			Collection<FlowFile> flowFiles = getNewEmails(imapServerUrl,
					imapUsername, imapPassword, session, context);

			session.transfer(flowFiles, REL_NEW_MESSAGE);

		} catch (Exception e) {
			logger.error("Exception receiving Email messages.", e);
			UCSCreateException.routeFlowFileToException(context, session,
					logger, session.create(), REL_FAILURE, null,
					"Exception receiving Email messages: '" + e.getMessage(),
					ExceptionType.ServerAdapterFault, null, null);
		}

	}

	private Collection<FlowFile> getNewEmails(String imapServerUrl,
			String imapUsername, String imapPassword,
			ProcessSession processSession, ProcessContext processContext)
			throws Exception {
		Collection<FlowFile> flowFiles = new ArrayList<>();
		Properties props = new Properties();
		props.setProperty("mail.store.protocol", "imaps");
		Store store = null;
		Folder inbox = null;
		Folder trash = null;
		try {
			String subject, body = "", contentType;
			List<String> senders = new ArrayList<>(), recipients = new ArrayList<>();
			Date receivedDate, sentDate;
			String receivedDateStr, sentDateStr;
			Session session = Session.getInstance(props, null);
			store = session.getStore();
			store.connect(imapServerUrl, imapUsername, imapPassword);
			inbox = store.getFolder("INBOX");
			inbox.open(Folder.READ_ONLY);
			SimpleDateFormat sdf = new SimpleDateFormat(
					"dd-MMM-yyyy HH:mm:ss zzz");
			Message[] messagesFromInbox = inbox.getMessages();
			getLogger().debug("Found {} messages in INBOX.",
					new Object[] { messagesFromInbox.length });
			trash = store.getFolder("[Gmail]/Trash");
			for (Message msg : messagesFromInbox) {
				final ObjectHolder<Throwable> errorHolder = new ObjectHolder<>(
						null);
				FlowFile flowFile = processSession.create();
				getLogger().debug(
						"Processing message {}." + msg.getMessageNumber());
				for (Address address : msg.getFrom()) {
					senders.add(address.toString());
				}
				getLogger().info("FROM Addresses : " + senders);
				for (Address address : msg.getAllRecipients()) {
					recipients.add(address.toString());
				}
				getLogger().info("Recipient Addresses : " + recipients);
				subject = msg.getSubject();
				getLogger().info("SUBJECT : " + subject);
				receivedDate = msg.getReceivedDate();
				receivedDateStr = sdf.format(receivedDate);
				getLogger().info("SENT DATE:" + receivedDateStr);
				sentDate = msg.getSentDate();
				sentDateStr = sdf.format(sentDate);
				getLogger().info("SENT DATE:" + sentDateStr);
				contentType = msg.getContentType();
				getLogger().info("CONTENT TYPE:" + contentType);

				Object o = msg.getContent();
				if (o instanceof String) {
					body = msg.getContent().toString();
				} else if (o instanceof Multipart) {
					body = ((Multipart) o).getBodyPart(0).getContent()
							.toString();
				}
				getLogger().info("Body : " + body);

				// Writing FlowFile attributes
				Map<String, String> attributes = new HashMap<>();
				attributes.put("fromEmails", senders.toString());
				attributes.put("toEmails", recipients.toString());
				attributes.put("subject", subject);
				attributes.put("contentType", contentType);
				attributes.put("sentDate", sentDateStr);
				attributes.put("receivedDate", receivedDateStr);
				flowFile = processSession
						.putAllAttributes(flowFile, attributes);

				final String finalBody = body;
				// To write the results back out to flow file
				flowFile = processSession.write(flowFile,
						new OutputStreamCallback() {

							@Override
							public void process(OutputStream out)
									throws IOException {
								try {
									out.write(finalBody.getBytes());
								} catch (Exception ex) {
									// should never happen
									ex.printStackTrace();
									errorHolder.set(ex);
								}
							}
						});
				if (errorHolder.get() != null) {
					getLogger().error(errorHolder.get().getMessage(),
							errorHolder.get());
					UCSCreateException.routeFlowFileToException(processContext,
							processSession, getLogger(), flowFile, REL_FAILURE,
							null, "Error serializing Email message: "
									+ errorHolder.get().getMessage(),
							ExceptionType.InvalidInput, null, null);
					continue;
				}

				processSession.getProvenanceReporter().create(flowFile);

				flowFiles.add(flowFile);
				// Deleting Message after reading those
				inbox.copyMessages(new Message[] { msg }, trash);
			}
			
		} catch (Exception e) {
			getLogger().error(
					"Unable to receive emails! Reason : " + e.getMessage(), e);
			throw new RuntimeException(e);
		} finally {
			if (inbox != null && inbox.isOpen()) {
				inbox.close(false);
			}
			if (trash != null && trash.isOpen()) {
				trash.close(false);
			}
			if (store != null) {
				store.close();
			}
		}
		return flowFiles;
	}

}

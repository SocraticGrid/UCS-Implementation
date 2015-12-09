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
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.nifi.annotation.behavior.EventDriven;
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
import org.apache.nifi.processor.io.InputStreamCallback;
import org.apache.nifi.processor.io.OutputStreamCallback;
import org.apache.nifi.stream.io.StreamUtils;
import org.apache.nifi.util.IntegerHolder;
import org.apache.nifi.util.ObjectHolder;
import org.socraticgrid.hl7.services.uc.exceptions.ExceptionType;
import org.socraticgrid.hl7.services.uc.model.DeliveryAddress;
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.services.uc.model.MessageBody;
import org.socraticgrid.hl7.services.uc.model.PhysicalAddress;
import org.socraticgrid.hl7.services.uc.model.Recipient;
import org.socraticgrid.hl7.services.uc.model.SimpleMessage;
import org.socraticgrid.hl7.services.uc.model.SimpleMessageHeader;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializer;
import org.socraticgrid.hl7.ucs.nifi.controller.UCSController;

/**
 * 
 * @author BasitAzeem
 *
 */
@EventDriven
@Tags({ "UCS", "Email", "Message" })
@CapabilityDescription("Converts an Email response into a UCS Message. This processor"
		+ "uses the message id in the Email subject to retrieve the message that originated this response.")
public class UCSConvertEmailResponseToMessage extends AbstractProcessor {

	public static final PropertyDescriptor UCS_CONTROLLER_SERVICE = new PropertyDescriptor.Builder()
			.name("UCS Controller Service")
			.description(
					"The UCS Controller Service that this Processor uses behind the scenes.")
			.identifiesControllerService(UCSController.class).required(true)
			.build();

	public static final Relationship REL_SUCCESS = new Relationship.Builder()
			.name("success").description("").build();
	public static final Relationship REL_FAILURE = new Relationship.Builder()
			.name("failure").description("").build();
	public static final Relationship REL_NO_MATCH = new Relationship.Builder()
			.name("no match")
			.description(
					"We don't have any message or recipient matching the given reference id.")
			.build();

	private final AtomicReference<Set<Relationship>> relationships = new AtomicReference<>();
	private List<PropertyDescriptor> properties;

	@Override
	protected void init(final ProcessorInitializationContext context) {
		final Set<Relationship> relationships = new HashSet<>();
		relationships.add(REL_SUCCESS);
		relationships.add(REL_NO_MATCH);
		relationships.add(REL_FAILURE);
		this.relationships.set(Collections.unmodifiableSet(relationships));

		final List<PropertyDescriptor> properties = new ArrayList<>();
		properties.add(UCS_CONTROLLER_SERVICE);
		this.properties = Collections.unmodifiableList(properties);
	}

	@Override
	protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
		return properties;
	}

	@Override
	public Set<Relationship> getRelationships() {
		return relationships.get();
	}

	@Override
	public void onTrigger(final ProcessContext context,
			final ProcessSession session) {
		final List<FlowFile> flowFiles = session.get(1);
		boolean isMessageIdFound = true;
		boolean isMessageFoundByMessageId = true;
		if (flowFiles.isEmpty()) {
			return;
		}

		final ProcessorLog logger = getLogger();

		UCSController ucsService = context.getProperty(UCS_CONTROLLER_SERVICE)
				.asControllerService(UCSController.class);

		for (FlowFile flowFile : flowFiles) {
			final ObjectHolder<Throwable> errorHolder = new ObjectHolder<>(null);
			final byte[] buffer = new byte[10240];
			final IntegerHolder bufferedByteCount = new IntegerHolder(0);
			session.read(flowFile, new InputStreamCallback() {
				@Override
				public void process(final InputStream in) throws IOException {
					bufferedByteCount.set(StreamUtils.fillBuffer(in, buffer,
							false));
				}
			});

			String emailContent = "";
			try {
				emailContent = new String(buffer, 0, bufferedByteCount.get(),
						"UTF-8");
			} catch (UnsupportedEncodingException e1) {
				e1.printStackTrace();
			}

			if (errorHolder.get() != null) {
				logger.error(errorHolder.get().getMessage(), errorHolder.get()
						.getCause());
				UCSCreateException.routeFlowFileToException(context, session,
						logger, flowFile, REL_FAILURE, null,
						"Error deserializing incoming Email: "
								+ errorHolder.get().getCause().getMessage(),
						ExceptionType.InvalidInput, null, null);
				continue;
			}

			Map<String, String> flowFileAttributes = flowFile.getAttributes();
			String subject = flowFileAttributes.get("subject");
			String fromEmails = flowFileAttributes.get("fromEmails");
			String toEmails = flowFileAttributes.get("toEmails");
			String contentType = flowFileAttributes.get("contentType");
			String receivedDateStr = flowFileAttributes.get("receivedDate");

			if (contentType != null
					&& contentType.equalsIgnoreCase("text/html")) {
				emailContent = emailContent.replaceAll("<", "&lt;").replaceAll(">", "&gt;");
			} else {
				contentType = "text/plain";
			}

			String messageId = "";
			if (subject != null && subject.trim().length() > 0) {
				if (subject.contains("::[")) {
					String[] subjectParts = subject.split("::");
					messageId = subjectParts[1].substring(
							subjectParts[1].indexOf("[") + 1,
							subjectParts[1].lastIndexOf("]"));
				}
			}

			if (messageId.isEmpty()) {
				isMessageIdFound = false;
			}

			Optional<Message> messageById = ucsService
					.getMessageById(messageId);

			String conversationId = "";

			if (messageById.isPresent()) {
				conversationId = messageById.get().getHeader()
						.getRelatedConversationId();
			} else {
				isMessageFoundByMessageId = false;
			}

			// Create a new message
			fromEmails = fromEmails.replaceAll("\\[", "");
			fromEmails = fromEmails.replaceAll("\\]", "");
			DeliveryAddress messageSender = new DeliveryAddress(
					new PhysicalAddress("EMAIL", fromEmails));
			Set<Recipient> recipients = new HashSet<>();
			toEmails = toEmails.replaceAll("\\[", "");
			toEmails = toEmails.replaceAll("\\]", "");
			if (toEmails.contains(",")) {
				for (String toEmail : toEmails.split(",")) {
					if (toEmail.contains("<") && toEmail.contains(">")) {
						toEmail = toEmail.substring(toEmail.indexOf("<") + 1,
								toEmail.lastIndexOf(">"));
					}
					Recipient messageRecipient = new Recipient();
					messageRecipient.setDeliveryAddress(new DeliveryAddress(
							new PhysicalAddress("EMAIL", toEmail)));
					recipients.add(messageRecipient);
				}
			} else {
				Recipient messageRecipient = new Recipient();
				messageRecipient.setDeliveryAddress(new DeliveryAddress(
						new PhysicalAddress("EMAIL", toEmails)));
				recipients.add(messageRecipient);
			}

			SimpleDateFormat sdf = new SimpleDateFormat(
					"dd-MMM-yyyy HH:mm:ss zzz");
			Date receivedDate = null;
			try {
				receivedDate = sdf.parse(receivedDateStr);
			} catch (Exception e) {

			}

			final Message responseMessage = this.createSimpleMessage(
					messageSender, recipients, conversationId, messageId,
					subject, emailContent, contentType, false, receivedDate,
					Optional.empty());

			flowFile = session.write(flowFile, new OutputStreamCallback() {
				@Override
				public void process(final OutputStream out) throws IOException {
					try {
						out.write(MessageSerializer.serializeMessageWrapper(
								new MessageWrapper(responseMessage)).getBytes());
					} catch (MessageSerializationException ex) {
						// should never happen
						errorHolder.set(ex);
					}
				}
			});

			if (errorHolder.get() != null) {
				logger.error(errorHolder.get().getMessage(), errorHolder.get());
				UCSCreateException.routeFlowFileToException(context, session,
						logger, flowFile, REL_FAILURE, null,
						"Error serializing generated message: "
								+ errorHolder.get().getMessage(),
						ExceptionType.InvalidMessage, null, null);
				continue;
			}

			session.getProvenanceReporter().modifyContent(flowFile);

			// persist the new message in UCSController
			ucsService.saveMessage(responseMessage);

			if (isMessageIdFound && isMessageFoundByMessageId) {
				// route FlowFile to SUCCESS
				logger.debug(
						"Email Response converted into a Message and set as FlowFile content. Routing FlowFile {} to {}.",
						new Object[] { flowFile, REL_SUCCESS });
				session.transfer(flowFile, REL_SUCCESS);
				session.getProvenanceReporter().route(flowFile, REL_SUCCESS);
			} else {
				// route FlowFile to REL_NO_MATCH
				if (!isMessageIdFound) {
					logger.debug(
							"Email Message doesn't contain any message ID in Subject. Routing FlowFile {} to {}.",
							new Object[] { flowFile, REL_NO_MATCH });
				}
				if (!isMessageFoundByMessageId) {
					logger.debug(
							"No message found by this Email's response message ID. Routing FlowFile {} to {}.",
							new Object[] { flowFile, REL_NO_MATCH });
				}
				session.transfer(flowFile, REL_NO_MATCH);
				session.getProvenanceReporter().route(flowFile, REL_NO_MATCH);
			}
		}
	}

	/**
	 * TODO: move this into a Builder class.
	 *
	 * @param sender
	 * @param recipients
	 * @param subject
	 * @param content
	 * @param contentType
	 * @param receiptNotification
	 * @param createdDate
	 * @param timeout
	 * @return
	 */
	private Message createSimpleMessage(DeliveryAddress sender,
			Set<Recipient> recipients, String conversationId,
			String relatedMessageId, String subject, String content,
			String contentType, boolean receiptNotification, Date createdDate,
			Optional<Integer> timeout) {

		String messageId = UUID.randomUUID().toString();
		SimpleMessageHeader header = new SimpleMessageHeader();
		header.setMessageId(messageId);
		header.setCreated(createdDate);
		header.setTimeout(timeout.orElse(-1));
		header.setRelatedConversationId(conversationId);
		header.setRelatedMessageId(relatedMessageId);
		header.setSender(sender);
		header.setSubject(subject);
		header.setRecipientsList(recipients);
		header.setReceiptNotification(receiptNotification);

		MessageBody body = new MessageBody();
		body.setContent(content);
		body.setType(contentType);

		SimpleMessage message = new SimpleMessage(header);
		message.setParts(new MessageBody[] { body });

		return message;
	}
}

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
package org.socraticgrid.hl7.ucs.nifi.common.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializer;
import org.stringtemplate.v4.ST;

/**
 *
 * @author esteban
 */
public class MessageBuilder {
    
    public static enum MessageFormat{
        NORMAL,
        STANDALONE,
        EMBEDDED,
    }

    public static class Recipient {

        public String id = UUID.randomUUID().toString();
        public String address;
        public String serviceId;

        public Recipient(String id, String address, String serviceId) {
            this.id = id;
            this.address = address;
            this.serviceId = serviceId;
        }

        public Recipient(String address, String serviceId) {
            this.address = address;
            this.serviceId = serviceId;
        }

        public String getId() {
            return id;
        }

        public String getAddress() {
            return address;
        }

        public String getServiceId() {
            return serviceId;
        }
    }
    
    public static class Body {
        public String content;
        public String type;
        public String tag;

        public Body(String content, String type, String tag) {
            this.content = content;
            this.type = type;
            this.tag = tag;
        }

        public Body(String content) {
            this.content = content;
            this.type = "text/plain";
        }

        public String getContent() {
            return content;
        }

        public String getType() {
            return type;
        }

        public String getTag() {
            return tag;
        }
        
    }

    private final String messageTemplate;
    private final String standaloneMessageTemplate;
    private final String embeddedMessageTemplate;
    private final String messageWrapperTemplate;

    private String conversationId;
    private String messageId = UUID.randomUUID().toString();
    private String sender;
    private String subject;
    private boolean receiptNotification;
    private List<Body> bodies = new ArrayList<>();
    private int respondBy = 0;
    private final List<Recipient> recipients = new ArrayList<>();
    private final List<MessageBuilder> onNoResponseAll = new ArrayList<>();
    private final List<MessageBuilder> onNoResponseAny = new ArrayList<>();
    private final List<MessageBuilder> onFailureToReachAll = new ArrayList<>();
    private final List<MessageBuilder> onFailureToReachAny = new ArrayList<>();

    public MessageBuilder() throws IOException {
        messageWrapperTemplate = IOUtils.toString(MessageBuilder.class
                .getResourceAsStream("/templates/message-wrapper-template.tpl"));
        
        messageTemplate = IOUtils.toString(MessageBuilder.class
                .getResourceAsStream("/templates/message-template.tpl"));
        
        standaloneMessageTemplate = IOUtils.toString(MessageBuilder.class
                .getResourceAsStream("/templates/standalone-message-template.tpl"));
        
        embeddedMessageTemplate = IOUtils.toString(MessageBuilder.class
                .getResourceAsStream("/templates/embedded-message-template.tpl"));
    }

    public MessageBuilder withMessageId(String messageId) {
        this.messageId = messageId;
        return this;
    }
    
    public MessageBuilder withConversationId(String conversationId) {
        this.conversationId = conversationId;
        return this;
    }
    
    public MessageBuilder withSender(String sender) {
        this.sender = sender;
        return this;
    }

    public MessageBuilder withSubject(String subject) {
        this.subject = subject;
        return this;
    }

    public MessageBuilder withBody(String body) {
        this.withBody(new Body(body));
        return this;
    }
    
    public MessageBuilder withBody(Body body) {
        this.bodies = new ArrayList<>();
        this.addBody(body);
        return this;
    }
    
    public MessageBuilder addBody(String body) {
        bodies.add(new Body(body));
        return this;
    }
    
    public MessageBuilder addBody(Body body) {
        bodies.add(body);
        return this;
    }
    
    public MessageBuilder withRespondBy(int respondBy) {
        this.respondBy = respondBy;
        return this;
    }
    
    public MessageBuilder withReceiptNotification(boolean receiptNotification) {
        this.receiptNotification = receiptNotification;
        return this;
    }

    public MessageBuilder addRecipient(Recipient recipient) {
        recipients.add(recipient);
        return this;
    }
    
    public MessageBuilder addOnNoResponseAll(MessageBuilder mb){
        this.onNoResponseAll.add(mb);
        return this;
    }
    
    public MessageBuilder addOnNoResponseAny(MessageBuilder mb){
        this.onNoResponseAny.add(mb);
        return this;
    }
    
    public MessageBuilder addOnFailureToReachAll(MessageBuilder mb){
        this.onFailureToReachAll.add(mb);
        return this;
    }
    
    public MessageBuilder addOnFailureToReachAny(MessageBuilder mb){
        this.onFailureToReachAny.add(mb);
        return this;
    }

    public String buildSerializedMessageWrapper() {
        
        String serializedMessage = this.buildSerializedMessage(MessageFormat.NORMAL);
        
         ST st = new ST(messageWrapperTemplate, '$', '$');
         st.add("serializedMessage", serializedMessage);
         
         return st.render();
    }
    
    public String buildSerializedMessage() {
        return this.buildSerializedMessage(MessageFormat.STANDALONE);
    }
    
    public String buildSerializedMessage(MessageFormat format) {
        
        switch(format){
            case EMBEDDED: {
                ST st = this.prepareTemplate(this.getEmbeddedMessageTemplate());
                return st.render();
            }
            case NORMAL: {
                ST st = new ST(messageTemplate, '$', '$');
                st.add("messageType", this.getMessageType());
                st.add("serializedMessage", this.buildSerializedMessage(MessageFormat.EMBEDDED));
                return st.render();
            }
            case STANDALONE:{
                ST st = new ST(standaloneMessageTemplate, '$', '$');
                st.add("messageType", this.getMessageType());
                st.add("serializedMessage", this.buildSerializedMessage(MessageFormat.NORMAL));
                return st.render();
            }
            default:
                throw new IllegalArgumentException("Unsupported format: "+format);
        }
    }

    public Message buildMessage() throws MessageSerializationException {
        return this.buildMessageWrapper().getMessage();
    }
    
    public MessageWrapper buildMessageWrapper() throws MessageSerializationException {
        return MessageSerializer.deserializeMessageWrapper(this.buildSerializedMessageWrapper());
    }
    
    protected ST prepareTemplate(String template){
        ST st = new ST(template, '$', '$');
        st.add("messageId", messageId);
        st.add("conversationId", conversationId);
        st.add("sender", sender);
        st.add("subject", subject);
        st.add("bodies", bodies);
        st.add("respondBy", respondBy);
        st.add("recipients", recipients);
        st.add("receiptNotification", receiptNotification);
        
        st.add("onNoResponseAll",
            onNoResponseAll.stream()
                    .map(mb -> mb.buildSerializedMessage(MessageFormat.EMBEDDED))
                    .collect(Collectors.toList())
        );
        
        st.add("onNoResponseAny",
            onNoResponseAny.stream()
                    .map(mb -> mb.buildSerializedMessage(MessageFormat.EMBEDDED))
                    .collect(Collectors.toList())
        );
        
        st.add("onFailureToReachAll",
            onFailureToReachAll.stream()
                    .map(mb -> mb.buildSerializedMessage(MessageFormat.EMBEDDED))
                    .collect(Collectors.toList())
        );
        
        st.add("onFailureToReachAny",
            onFailureToReachAny.stream()
                    .map(mb -> mb.buildSerializedMessage(MessageFormat.EMBEDDED))
                    .collect(Collectors.toList())
        );
                
        return st;
    }
    
    protected String getEmbeddedMessageTemplate(){
        return this.embeddedMessageTemplate;
    }
    
    protected String getMessageType(){
        return "simpleMessage";
    }

}

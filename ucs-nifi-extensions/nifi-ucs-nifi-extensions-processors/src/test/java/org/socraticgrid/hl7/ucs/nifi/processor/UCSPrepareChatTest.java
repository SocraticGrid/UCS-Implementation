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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.socraticgrid.hl7.ucs.nifi.common.util.MessageBuilder;

/**
 *
 * @author esteban
 */
public class UCSPrepareChatTest extends UCSControllerServiceBasedTest{
    
    @Test
    public void testPreExistingGroupChat() throws IOException{
        
        String message = new MessageBuilder()
                .withConversationId("testC")
                .withSender("eafry")
                .withSubject("Some Subject")
                .withBody("Some Body")
                .addRecipient(new MessageBuilder.Recipient("GROUP:Everyone", "CHAT"))
                .buildSerializedMessageWrapper();
        
        testRunner.enqueue(message.getBytes());
        testRunner.run();
        
        testRunner.assertAllFlowFilesTransferred(UCSPrepareChat.REL_PERMANENT_GROUP, 1);
        MockFlowFile ff = testRunner.getFlowFilesForRelationship(UCSPrepareChat.REL_PERMANENT_GROUP).get(0);
        
        assertThat(ff.getAttribute(UCSPrepareChat.GROUP_ATTRIBUTE_NAME.getDefaultValue()), is("Everyone"+UCSPrepareChat.GROUP_SUFIX.getDefaultValue()));
        assertThat(ff.getAttribute(UCSPrepareChat.SENDER_ATTRIBUTE_NAME.getDefaultValue()), is("eafry@socraticgrid.org"));
        assertThat(ff.getAttribute(UCSPrepareChat.MESSAGE_ATTRIBUTE_NAME.getDefaultValue()), is("Some Body"));
        assertThat(ff.getAttribute(UCSPrepareChat.REFERENCE_ATTRIBUTE_NAME.getDefaultValue()), not(nullValue()));
        assertThat(ff.getAttribute(UCSPrepareChat.REFERENCE_ATTRIBUTE_NAME.getDefaultValue()).contains(","), is(Boolean.FALSE));
    }
    
    @Test
    public void testPreExistingGroupChat2() throws IOException{
        
        String message = new MessageBuilder()
                .withConversationId("testC")
                .withSender("eafry")
                .withSubject("Some Subject")
                .withBody("Some Body")
                .addRecipient(new MessageBuilder.Recipient("GROUP:Everyone"+UCSPrepareChat.GROUP_SUFIX.getDefaultValue(), "CHAT"))
                .buildSerializedMessageWrapper();
        
        testRunner.enqueue(message.getBytes());
        testRunner.run();
        
        testRunner.assertAllFlowFilesTransferred(UCSPrepareChat.REL_PERMANENT_GROUP, 1);
        MockFlowFile ff = testRunner.getFlowFilesForRelationship(UCSPrepareChat.REL_PERMANENT_GROUP).get(0);
        
        assertThat(ff.getAttribute(UCSPrepareChat.GROUP_ATTRIBUTE_NAME.getDefaultValue()), is("Everyone"+UCSPrepareChat.GROUP_SUFIX.getDefaultValue()));
        assertThat(ff.getAttribute(UCSPrepareChat.SENDER_ATTRIBUTE_NAME.getDefaultValue()), is("eafry@socraticgrid.org"));
        assertThat(ff.getAttribute(UCSPrepareChat.MESSAGE_ATTRIBUTE_NAME.getDefaultValue()), is("Some Body"));
        assertThat(ff.getAttribute(UCSPrepareChat.REFERENCE_ATTRIBUTE_NAME.getDefaultValue()), not(nullValue()));
        assertThat(ff.getAttribute(UCSPrepareChat.REFERENCE_ATTRIBUTE_NAME.getDefaultValue()).contains(","), is(Boolean.FALSE));
    }
    
    @Test
    public void testMultiplePreExistingGroupChat() throws IOException{
        
        String message = new MessageBuilder()
                .withConversationId("testC")
                .withSender("eafry")
                .withSubject("Some Subject")
                .withBody("Some Body")
                .addRecipient(new MessageBuilder.Recipient("GROUP:Everyone", "CHAT"))
                .addRecipient(new MessageBuilder.Recipient("GROUP:Another", "CHAT"))
                .buildSerializedMessageWrapper();
        
        testRunner.enqueue(message.getBytes());
        testRunner.run();
        
        testRunner.assertAllFlowFilesTransferred(UCSPrepareChat.REL_PERMANENT_GROUP, 2);
        List<MockFlowFile> files = testRunner.getFlowFilesForRelationship(UCSPrepareChat.REL_PERMANENT_GROUP);
        
        assertThat(
            files.stream().map(ff -> ff.getAttribute(UCSPrepareChat.GROUP_ATTRIBUTE_NAME.getDefaultValue())).collect(Collectors.toList()),
            containsInAnyOrder("Everyone"+UCSPrepareChat.GROUP_SUFIX.getDefaultValue(), "Another"+UCSPrepareChat.GROUP_SUFIX.getDefaultValue())
        );
        
        assertThat(
            files.stream().map(ff -> ff.getAttribute(UCSPrepareChat.SENDER_ATTRIBUTE_NAME.getDefaultValue())).collect(Collectors.toSet()),
            containsInAnyOrder("eafry@socraticgrid.org")
        );    
        
        assertThat(
            files.stream().map(ff -> ff.getAttribute(UCSPrepareChat.MESSAGE_ATTRIBUTE_NAME.getDefaultValue())).collect(Collectors.toSet()),
            containsInAnyOrder("Some Body")
        );    
        
        assertThat(
            files.stream().map(ff -> ff.getAttribute(UCSPrepareChat.REFERENCE_ATTRIBUTE_NAME.getDefaultValue())).collect(Collectors.toSet()),
            hasSize(2)
        );    
    }
    
    @Test
    public void testDynamicGroupChat() throws IOException{
        
        String message = new MessageBuilder()
                .withConversationId("testC")
                .withSender("eafry")
                .withSubject("Some Subject")
                .withBody("Some Body")
                .addRecipient(new MessageBuilder.Recipient("ealiverti@socraticgrid.org", "CHAT"))
                .addRecipient(new MessageBuilder.Recipient("jhughes@socraticgrid.org", "CHAT"))
                .buildSerializedMessageWrapper();
        
        testRunner.enqueue(message.getBytes());
        testRunner.run();
        
        testRunner.assertAllFlowFilesTransferred(UCSPrepareChat.REL_DYNAMIC_GROUP, 1);
        MockFlowFile ff = testRunner.getFlowFilesForRelationship(UCSPrepareChat.REL_DYNAMIC_GROUP).get(0);
        
        assertThat(ff.getAttribute(UCSPrepareChat.GROUP_ATTRIBUTE_NAME.getDefaultValue()), is("testC"+UCSPrepareChat.GROUP_SUFIX.getDefaultValue()));
        assertThat(ff.getAttribute(UCSPrepareChat.GROUP_SUBJECT_ATTRIBUTE_NAME.getDefaultValue()), is("Some Subject"));
        assertThat(ff.getAttribute(UCSPrepareChat.SENDER_ATTRIBUTE_NAME.getDefaultValue()), is("eafry@socraticgrid.org"));
        assertThat(ff.getAttribute(UCSPrepareChat.MESSAGE_ATTRIBUTE_NAME.getDefaultValue()), is("Some Body"));
        
        String participants = ff.getAttribute(UCSPrepareChat.PARTICIPANTS_ATTRIBUTE_NAME.getDefaultValue());
        JsonArray pArray = new JsonParser().parse(participants).getAsJsonArray();
        
        assertThat(pArray.size(), is(2));
        for (JsonElement je : pArray) {
            assertThat(je.getAsJsonObject().get("participant").getAsString(), anyOf(is("ealiverti@socraticgrid.org"), is("jhughes@socraticgrid.org")) );
        }
        
        assertThat(ff.getAttribute(UCSPrepareChat.REFERENCE_ATTRIBUTE_NAME.getDefaultValue()), not(nullValue()));
        assertThat(ff.getAttribute(UCSPrepareChat.REFERENCE_ATTRIBUTE_NAME.getDefaultValue()).contains(","), is(Boolean.TRUE));
    }
    
    @Test
    public void testDirectChat() throws IOException{
        
        String message = new MessageBuilder()
                .withConversationId("testC")
                .withSender("eafry")
                .withSubject("Some Subject")
                .withBody("Some Body")
                .addRecipient(new MessageBuilder.Recipient("ealiverti@socraticgrid.org", "CHAT"))
                .buildSerializedMessageWrapper();
        
        testRunner.enqueue(message.getBytes());
        testRunner.run();
        
        testRunner.assertAllFlowFilesTransferred(UCSPrepareChat.REL_DIRECT_MESSAGE, 1);
        MockFlowFile ff = testRunner.getFlowFilesForRelationship(UCSPrepareChat.REL_DIRECT_MESSAGE).get(0);
        
        assertThat(ff.getAttribute(UCSPrepareChat.GROUP_ATTRIBUTE_NAME.getDefaultValue()), is("testC"+UCSPrepareChat.GROUP_SUFIX.getDefaultValue()));
        assertThat(ff.getAttribute(UCSPrepareChat.GROUP_SUBJECT_ATTRIBUTE_NAME.getDefaultValue()), is("Some Subject"));
        assertThat(ff.getAttribute(UCSPrepareChat.SENDER_ATTRIBUTE_NAME.getDefaultValue()), is("eafry@socraticgrid.org"));
        assertThat(ff.getAttribute(UCSPrepareChat.MESSAGE_ATTRIBUTE_NAME.getDefaultValue()), is("Some Body"));
        assertThat(ff.getAttribute(UCSPrepareChat.PARTICIPANTS_ATTRIBUTE_NAME.getDefaultValue()), is("ealiverti@socraticgrid.org"));
        assertThat(ff.getAttribute(UCSPrepareChat.REFERENCE_ATTRIBUTE_NAME.getDefaultValue()), not(nullValue()));
        assertThat(ff.getAttribute(UCSPrepareChat.REFERENCE_ATTRIBUTE_NAME.getDefaultValue()).contains(","), is(Boolean.FALSE));
    }
    
    @Override
    protected TestRunner createTestRunner() {
        return TestRunners.newTestRunner(new UCSPrepareChat());
    }
}

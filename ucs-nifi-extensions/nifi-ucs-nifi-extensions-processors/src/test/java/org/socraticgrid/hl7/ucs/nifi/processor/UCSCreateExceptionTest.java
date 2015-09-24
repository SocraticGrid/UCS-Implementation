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
import org.apache.commons.io.IOUtils;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import org.junit.Before;
import org.junit.Test;
import org.socraticgrid.hl7.services.uc.exceptions.ExceptionType;
import org.socraticgrid.hl7.services.uc.exceptions.ProcessingException;
import org.socraticgrid.hl7.services.uc.model.Recipient;
import org.socraticgrid.hl7.ucs.nifi.common.model.ExceptionWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.ExceptionWrapperSerializer;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializer;

/**
 *
 * @author esteban
 */
public class UCSCreateExceptionTest {
    private TestRunner testRunner;

    @Before
    public void init() throws Exception {
        testRunner = TestRunners.newTestRunner(new UCSCreateException());
        
        //default attributes' values.
        testRunner.setProperty(UCSCreateException.FAULT, "Test Fault");
        testRunner.setProperty(UCSCreateException.CONTEXT, "Test Context");
        testRunner.setProperty(UCSCreateException.TYPE, ExceptionType.General.name());
        testRunner.setProperty(UCSCreateException.SERVER_ID, "Test Server Id");
        testRunner.removeProperty(UCSCreateException.TYPE_ATTRIBUTE);
    }
    
    @Test
    public void testNoReceiverId() throws MessageSerializationException, IOException{
        String originalAsString = IOUtils.toString(UCSCreateExceptionTest.class.getResourceAsStream("/test-data/UCSCreateExceptionTest-Message1.xml"));
        MessageWrapper original = MessageSerializer.deserializeMessageWrapper(originalAsString);
        
        testRunner.enqueue(originalAsString.getBytes());
        testRunner.setValidateExpressionUsage(false);
        testRunner.run();
        
        testRunner.assertAllFlowFilesTransferred(UCSCreateException.REL_SUCCESS, 1);
        MockFlowFile ff = testRunner.getFlowFilesForRelationship(UCSCreateException.REL_SUCCESS).get(0);
        
        ExceptionWrapper ew = this.toExceptionWrapper(ff);
        
        assertThat(ew.getServerId(), is("Test Server Id"));
        assertThat(ew.getSender().getPhysicalAddress().getAddress(), is(original.getMessage().getHeader().getSender().getPhysicalAddress().getAddress()));
        assertThat(ew.getMessage().getHeader().getMessageId(), is(original.getMessage().getHeader().getMessageId()));
        
        ProcessingException pe = ew.getProcessingException();
        assertThat(pe, not(nullValue()));
        assertThat(pe.getProcessingExceptionId(), not(nullValue()));
        assertThat(pe.getFault(), is("Test Fault"));
        assertThat(pe.getTypeSpecificContext(), is("Test Context"));
        assertThat(pe.getIssuingService(), is("Test Server Id"));
        assertThat(pe.getExceptionType(), is(ExceptionType.General));
    }
    
    @Test
    public void testWithReceiverId() throws MessageSerializationException, IOException{
        String originalAsString = IOUtils.toString(UCSCreateExceptionTest.class.getResourceAsStream("/test-data/UCSCreateExceptionTest-Message1.xml"));
        MessageWrapper original = MessageSerializer.deserializeMessageWrapper(originalAsString);
        
        //Set the recipient id of the second recipient.
        Recipient recipient = original.getMessage().getHeader().getRecipientsList().stream().skip(1).findFirst().get();
        testRunner.setProperty(UCSCreateException.RECEIVER_ID, recipient.getRecipientId());
        
        testRunner.enqueue(originalAsString.getBytes());
        testRunner.setValidateExpressionUsage(false);
        testRunner.run();
        
        testRunner.assertAllFlowFilesTransferred(UCSCreateException.REL_SUCCESS, 1);
        MockFlowFile ff = testRunner.getFlowFilesForRelationship(UCSCreateException.REL_SUCCESS).get(0);
        
        ExceptionWrapper ew = this.toExceptionWrapper(ff);
        
        assertThat(ew.getServerId(), is("Test Server Id"));
        assertThat(ew.getSender().getPhysicalAddress().getAddress(), is(original.getMessage().getHeader().getSender().getPhysicalAddress().getAddress()));
        assertThat(ew.getMessage().getHeader().getMessageId(), is(original.getMessage().getHeader().getMessageId()));
        assertThat(ew.getReceiver(), not(nullValue()));
        assertThat(ew.getReceiver().getPhysicalAddress().getAddress(), is(recipient.getDeliveryAddress().getPhysicalAddress().getAddress()));
        
        ProcessingException pe = ew.getProcessingException();
        assertThat(pe, not(nullValue()));
        assertThat(pe.getProcessingExceptionId(), not(nullValue()));
        assertThat(pe.getFault(), is("Test Fault"));
        assertThat(pe.getTypeSpecificContext(), is("Test Context"));
        assertThat(pe.getIssuingService(), is("Test Server Id"));
        assertThat(pe.getExceptionType(), is(ExceptionType.General));
    }
    
    private ExceptionWrapper toExceptionWrapper(MockFlowFile ff) throws MessageSerializationException{
        return ExceptionWrapperSerializer.deserializeExceptionWrapper(new String(ff.toByteArray()));
    }
}

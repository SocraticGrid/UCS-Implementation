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
package org.socraticgrid.hl7.ucs.nifi.controller.chat;

import java.util.List;
import java.util.Set;
import org.apache.nifi.controller.ControllerService;

/**
 *
 * @author esteban
 */
public interface ChatController extends ControllerService {
    
    public void sendMessageToFixedRoom(String chatRoomId, String message, String sender) throws Exception;
    public void sendMessageToDynamicRoom(String chatRoomId, String roomSubject, String message, String sender, List<String> participants) throws Exception;
    public void sendMessageToSingleParticipant(String chatRoomId, String roomSubject, String message, String sender, String participant) throws Exception;
    
    public Set<ChatMessage> consumeMessages();
}

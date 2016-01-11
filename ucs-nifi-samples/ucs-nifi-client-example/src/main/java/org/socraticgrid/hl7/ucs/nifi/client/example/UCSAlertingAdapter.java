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
package org.socraticgrid.hl7.ucs.nifi.client.example;

import com.google.gson.Gson;
import java.io.PrintStream;
import java.util.List;
import org.slf4j.LoggerFactory;
import org.socraticgrid.hl7.services.uc.interfaces.UCSAlertingIntf;
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.services.uc.model.MessageModel;

/**
 *
 * @author esteban
 */
public class UCSAlertingAdapter implements UCSAlertingIntf{

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(UCSAlertingAdapter.class);
    
    private final PrintStream output;
    private final Gson gson;

    public UCSAlertingAdapter(PrintStream output) {
        this.output = output;
        this.gson = new Gson();
    }
    
    @Override
    public <T extends Message> boolean receiveAlertMessage(MessageModel<T> mm, List<String> list, String string) {
        output.print("New Alert:\n");
        output.print(gson.toJson(MessageUtils.toJsonObject(mm.getMessageType())));
        output.print("\n");
        output.flush();
        return true;
    }

    @Override
    public <T extends Message> boolean updateAlertMessage(MessageModel<T> mm, MessageModel<T> mm1, List<String> list, String string) {
        output.print("Alert Updated:\n");
        output.print(gson.toJson(MessageUtils.toJsonObject(mm.getMessageType())));
        output.print("\n");
        output.flush();
        return true;
    }

    @Override
    public <T extends Message> boolean cancelAlertMessage(MessageModel<T> mm, List<String> list, String string) {
        output.print("Alert Canceled:\n");
        output.print(gson.toJson(MessageUtils.toJsonObject(mm.getMessageType())));
        output.print("\n");
        output.flush();
        return true;
    }
    
}

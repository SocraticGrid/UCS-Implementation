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
package org.socraticgrid.hl7.ucs.nifi.test.workbench.command;

import com.google.gson.JsonObject;
import org.socraticgrid.hl7.ucs.nifi.api.UCSNiFiSession;

/**
 *
 * @author esteban
 */
public class PingUCSSessionStatusCommand implements Command {

    @Override
    public void init(JsonObject config) {
    }

    @Override
    public JsonObject execute() {
        UCSNiFiSession lastSession = CreateUCSSessionCommand.getLastSession();
        
        JsonObject result = new JsonObject();
        result.addProperty("status", lastSession == null ? "not initialized" : "initialized");
        
        return result;
    }

}

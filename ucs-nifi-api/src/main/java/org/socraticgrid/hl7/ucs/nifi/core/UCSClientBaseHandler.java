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
package org.socraticgrid.hl7.ucs.nifi.core;

import com.sun.net.httpserver.HttpHandler;
import org.socraticgrid.hl7.services.uc.interfaces.UCSClientIntf;

/**
 *
 * @author esteban
 */
public abstract class UCSClientBaseHandler implements HttpHandler{
    private final UCSClientIntf listener;

    public UCSClientBaseHandler(UCSClientIntf listener) {
        this.listener = listener;
    }

    public UCSClientIntf getListener() {
        return listener;
    }
    
}

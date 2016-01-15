var ucsWS = {
    "socket": undefined,
    "callbacks": {},
    "openConnection": function (pushCallback, onOpenCallback) {
        var l = window.location;
        //var url = ((l.protocol === "https:") ? "wss://" : "ws://") + l.hostname + (((l.port != 80) && (l.port != 443)) ? ":" + l.port : "") + l.pathname + "ucs";
        var url = l.href.substring(0, l.href.lastIndexOf("/"))+"/ucs";
        url = url.replace("http","ws");
        
        if ('WebSocket' in window) {

            this.socket = new WebSocket(url);
            this.socket.onopen = onOpenCallback;
            
            this.socket.onmessage = function (message) {
                try {
                    var _m = $.parseJSON(message.data);

                    //Response to one of our commands
                    if (_m.callbackId){
                        var callback = ucsWS.callbacks[_m.callbackId];
                        
                        if (!callback){
                            console.log("Received message with an unknown callbak.");
                            return;
                        }
                        
                        delete ucsWS.callbacks[_m.callbackId];
                        
                        callback(_m);
                        return;
                    }

                    //PUSH message from the server
                    if (_m.type) {
                        if (_m.type === "MESSAGE"){
                            if (!pushCallback.onMessage){
                                console.log("No callback defined for "+_m.type+" message");
                                return;
                            }
                            pushCallback.onMessage(_m.value);
                        } else if (_m.type === "RESPONSE"){
                            if (!pushCallback.onResponse){
                                console.log("No callback defined for "+_m.type+" message");
                                return;
                            }
                            pushCallback.onResponse(_m.value);
                        } else if (_m.type === "EXCEPTION"){
                            if (!pushCallback.onException){
                                console.log("No callback defined for "+_m.type+" message");
                                return;
                            }
                            pushCallback.onException(_m.value);
                        } else if (_m.type === "ALERT_NEW"){
                            if (!pushCallback.onAlert){
                                console.log("No callback defined for "+_m.type+" message");
                                return;
                            }
                            pushCallback.onAlert(_m.value);
                        } else if (_m.type === "ALERT_UPDATED"){
                            if (!pushCallback.onAlertUpdated){
                                console.log("No callback defined for "+_m.type+" message");
                                return;
                            }
                            pushCallback.onAlertUpdated(_m.value);
                        } else if (_m.type === "ALERT_CANCELED"){
                            if (!pushCallback.onAlertCanceled){
                                console.log("No callback defined for "+_m.type+" message");
                                return;
                            }
                            pushCallback.onAlertCanceled(_m.value);
                        }
                    } else {
                        alert ("Unknown message received from server: "+message.data);
                    }

                } catch (e) {
                    alert("Error parsing notification: " + e);
                }
            };
        }
        else if ('MozWebSocket' in window) {
            //Chat.socket = new MozWebSocket(host);
            alert('Error: WebSocket is not supported by this browser.');
        } else {
            alert('Error: WebSocket is not supported by this browser.');
        }
    },
    "getInitialConfiguration": function(callback){
        return this.sendCommand({
            type: "getInitialConfiguration"
        }, callback);
    },
    "sendCreateUCSSessionCommand": function(nifiHost, nifiClientCommandPort, nifiAlertingCommandPort, nifiManagementCommandPort, nifiConversationCommandPort, nifiSendMessagePort, clientsHost, clientPort, alertingPort, managementPort, conversationPort, callback){
        return this.sendCommand({
            type: "createUCSSession",
            nifiHost: nifiHost,                                         //host where NiFi is deployed
            nifiClientCommandPort: nifiClientCommandPort,               //port on NiFi host where the Client Command Interface is listening.
            nifiAlertingCommandPort: nifiAlertingCommandPort,           //port on NiFi host where the Alerting Command Interface is listening.
            nifiManagementCommandPort: nifiManagementCommandPort,       //port on NiFi host where the Management Command Interface is listening.
            nifiConversationCommandPort: nifiConversationCommandPort,   //port on NiFi host where the Conversation Command Interface is listening.
            nifiSendMessagePort: nifiSendMessagePort,                   //port on NiFi host whre the Send Command processor is listening.
            clientsHost: clientsHost,                                   //host where the client will listeng for callback from NiFi's UCS interfaces (like UCSClient and UCSAlerting).
            clientPort: clientPort,                                     //port on the Client host (clientsHost) that will listen for callbacks from UCSClient Interface and Client Interface
            alertingPort: alertingPort,                                 //port on the Client host (clientsHost) that will listen for callbacks from UCSAlerting Interface and Alerting Interface
            managementPort: managementPort,                             //port on the Client host (clientsHost) that will listen for callbacks from Management Interface
            conversationPort: conversationPort                          //port on the Client host (clientsHost) that will listen for callbacks from Conversation Interface
        }, callback);
    },
    "sendPingUCSSessionStatusCommand": function(callback){
        return this.sendCommand({
            type: "pingUCSSessionStatus"
        }, callback);
    },
    "sendNewMessageCommand": function(from, conversationId, subject, body, recipients){
        this.sendCommand({
            type: "newMessage",
            from: from,
            conversationId: conversationId,
            subject: subject,
            body: body,
            recipients: recipients
        });
    },
    "sendNewAlertMessageCommand": function(from, conversationId, subject, body, recipients){
        this.sendCommand({
            type: "newAlertMessage",
            from: from,
            conversationId: conversationId,
            subject: subject,
            body: body,
            recipients: recipients
        });
    },
    "sendGetAllMessagesCommand": function(callback){
        this.sendCommand({
            type: "getAllMessages"
        }, callback);
    },
    "sendGetAllAlertMessagesCommand": function(callback){
        this.sendCommand({
            type: "getAllMessages",
            typeMessageTypeFilter: "Alert" 
        }, callback);
    },
    "sendUpdateMessageCommand": function(messageId, status){
        this.sendCommand({
            type: "updateMessage",
            messageId: messageId, 
            status: status
        });
    },
    "sendCancelMessageCommand": function(messageId){
        this.sendCommand({
            type: "cancelMessage",
            messageId: messageId
        });
    },
    "sendDiscoverChannelsCommand": function(callback){
        this.sendCommand({
            type: "discoverChannels"
        }, callback);
    },
    "sendGetStatusCommand": function(callback){
        this.sendCommand({
            type: "getStatus"
        }, callback);
    },
    "sendQueryConversationsCommand": function(callback){
        this.sendCommand({
            type: "queryConversations"
        }, callback);
    },
    "sendRetrieveConversationCommand": function(conversationId, callback){
        this.sendCommand({
            type: "retrieveConversation",
            conversationId: conversationId
        }, callback);
    },
    "sendCreateConversationCommand": function(conversationId, callback){
        this.sendCommand({
            type: "createConversation",
            conversationId: conversationId
        }, callback);
    },
    "sendCommand": function(command, callback){
        
        var callbackId = ""+new Date().getMilliseconds()+Math.random();
        this.callbacks[callbackId] = callback ? callback : function(r){
            if (!r.success){
                alert("Error executing command: "+r.error);
            }
        };
        command["callbackId"] = callbackId;
        
        return this.socket.send(JSON.stringify(command));
    }

};
<?xml version="1.0" encoding="UTF-8" standalone="yes"?><messageWrapper xmlns:model="http://org.socraticgrid.hl7.services.uc.model" xmlns:exceptions="http://org.socraticgrid.hl7.services.uc.exceptions">
    <message xsi:type="model:alertMessage" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
        <model:parts>
            <model:content>$body$</model:content>
            <model:type>text/plain</model:type>
        </model:parts>
        <model:alertMessageHeader>
            <model:messageId>$messageId$</model:messageId>
            <model:relatedConversationId>$conversationId$</model:relatedConversationId>
            <model:sender>
                <model:physicaladdress>
                    <model:address>$from$</model:address>
                    <model:serviceId>SMS</model:serviceId>
                </model:physicaladdress>
            </model:sender>
            $recipients:{r |
                <model:recipientsList>
                    <model:deliveryAddress>
                        <model:physicaladdress>
                            <model:address>$r.to$</model:address>
                            <model:serviceId>$r.type$</model:serviceId>
                        </model:physicaladdress>
                    </model:deliveryAddress>
                    <model:deliveryReceipt>false</model:deliveryReceipt>
                    <model:readReceipt>false</model:readReceipt>
                    <model:role/>
                    <model:visibility>Public</model:visibility>
                </model:recipientsList>
            }
            $
            <model:subject>$subject$</model:subject>
            <model:created>2015-02-26T14:54:19.740+01:00</model:created>
            <model:lastModified>2015-02-26T14:54:19.739+01:00</model:lastModified>
            <model:deliveryGuarantee>BestEffort</model:deliveryGuarantee>
            <model:dynamics>Asynchronous</model:dynamics>
            <model:priority>0</model:priority>
            <model:receiptNotification>true</model:receiptNotification>
            <model:retainFullyInLog>false</model:retainFullyInLog>
            <model:timeout>30000</model:timeout>
            <model:respondBy>0</model:respondBy>
            <model:alertStatus>$status$</model:alertStatus>
            <model:statusByReciever/>
        </model:alertMessageHeader>
    </message>
</messageWrapper>
            
            
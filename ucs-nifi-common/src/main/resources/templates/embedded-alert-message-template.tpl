$bodies:{b |
    <model:parts>
        <model:content>$b.content$</model:content>
        <model:type>$b.type$</model:type>
        <model:tag>$b.tag$</model:tag>
    </model:parts>
}
$
<model:alertMessageHeader>
    <model:messageId>$messageId$</model:messageId>
    <model:relatedConversationId>$conversationId$</model:relatedConversationId>
    <model:alertStatus>$status$</model:alertStatus>
    <model:sender>
        <model:physicaladdress>
            <model:address>$sender$</model:address>
            <model:serviceId>SMS</model:serviceId>
        </model:physicaladdress>
    </model:sender>
    <model:receiptNotification>$receiptNotification$</model:receiptNotification>
    $recipients:{r |
            <model:recipientsList>
                <model:recipientId>$r.id$</model:recipientId>
                <model:deliveryAddress>
                    <model:physicaladdress>
                        <model:address>$r.address$</model:address>
                        <model:serviceId>$r.serviceId$</model:serviceId>
                    </model:physicaladdress>
                </model:deliveryAddress>
                <model:deliveryReceipt>false</model:deliveryReceipt>
                <model:readReceipt>false</model:readReceipt>
                <model:role/>
                <model:visibility>Public</model:visibility>
            </model:recipientsList>
    }
    $
    <model:properties>
    $properties:{p |
        <entry>
            <key xmlns:xs="http://www.w3.org/2001/XMLSchema" xsi:type="xs:string">$p.key$</key>
            <value xmlns:xs="http://www.w3.org/2001/XMLSchema" xsi:type="xs:string">$p.value$</value>
        </entry>
    }
    $
    </model:properties>
    <model:subject>$subject$</model:subject>
    <model:created>$created$</model:created>
    <model:lastModified>$lastModified$</model:lastModified>
    <model:deliveryGuarantee>BestEffort</model:deliveryGuarantee>
    <model:dynamics>Asynchronous</model:dynamics>
    <model:priority>0</model:priority>
    <model:receiptNotification>true</model:receiptNotification>
    <model:retainFullyInLog>false</model:retainFullyInLog>
    <model:timeout>30000</model:timeout>
    <model:respondBy>$respondBy$</model:respondBy>
    $if(onNoResponseAll)$
        $onNoResponseAll:{m |
    <model:onNoResponseAll xsi:type="model:simpleMessage">
                $m$
    </model:onNoResponseAll>
        }
        $
    $endif$
    $if(onNoResponseAny)$
        $onNoResponseAny:{m |
    <model:onNoResponseAny xsi:type="model:simpleMessage">
                $m$
    </model:onNoResponseAny>
        }
        $
    $endif$
    $if(onFailureToReachAll)$
        $onFailureToReachAll:{m |
    <model:onFailureToReachAll xsi:type="model:simpleMessage">
                $m$
    </model:onFailureToReachAll>
        }
        $
    $endif$
    $if(onFailureToReachAny)$
        $onFailureToReachAny:{m |
    <model:onFailureToReachAny xsi:type="model:simpleMessage">
                $m$
    </model:onFailureToReachAny>
        }
        $
    $endif$
</model:alertMessageHeader>
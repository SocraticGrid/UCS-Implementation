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
package org.socraticgrid.hl7.ucs.nifi.controller.user;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.directory.api.ldap.model.cursor.SearchCursor;
import org.apache.directory.api.ldap.model.entry.Attribute;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.message.Response;
import org.apache.directory.api.ldap.model.message.SearchRequest;
import org.apache.directory.api.ldap.model.message.SearchRequestImpl;
import org.apache.directory.api.ldap.model.message.SearchResultEntry;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.ldap.client.api.LdapConnection;
import org.apache.directory.ldap.client.api.LdapConnectionConfig;
import org.apache.directory.ldap.client.api.LdapNetworkConnection;
import org.apache.nifi.annotation.lifecycle.OnEnabled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.controller.ConfigurationContext;
import org.apache.nifi.processor.util.StandardValidators;
import org.socraticgrid.hl7.services.uc.model.PhysicalAddress;
import org.socraticgrid.hl7.services.uc.model.UserContactInfo;

/**
 * This is just a LDAP implementation of UserContactInfoResolverController. This
 * class gets user information from LDAP server.
 *
 * @author pavan
 */
public class LDAPUserContactInfoResolverControllerImpl extends AbstractControllerService implements
        UserContactInfoResolverController {

    private String ldapHost = "";
    private int ldapPort;
    private String ldapBindDn = "";
    private String ldapPwd = "";
    private String ldapBaseDn = "";
    public static final String SERVICE_TYPE_SMS = "SMS";
    public static final String SERVICE_TYPE_EMAIL = "EMAIL";
    public static final String SERVICE_TYPE_CHAT = "CHAT";
    public static final String SERVICE_TYPE_TEXT_TO_VOICE = "TEXT-TO-VOICE";
    public static final String SEARCH_ATTR_MAIL = "mail";
    public static final String SEARCH_ATTR_HOMEPHONE = "homephone";
    public static final String SEARCH_ATTR_CHAT = "chat";
    public static final String SEARCH_ATTR_VOIP = "voip";

    public static final PropertyDescriptor LDAP_HOST = new PropertyDescriptor.Builder()
            .name("ldap-host")
            .description("Ldap server host")
            .required(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor LDAP_PORT = new PropertyDescriptor.Builder()
            .name("ldap-port")
            .description("Ldap server port number")
            .required(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor LDAP_BIND_DN = new PropertyDescriptor.Builder()
            .name("ldap-bind-dn")
            .description("Ldap directory bind configuration")
            .required(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor LDAP_PWD = new PropertyDescriptor.Builder()
            .name("ldap-pwd")
            .description("Ldap directoy user password")
            .required(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor LDAP_BASE_DN = new PropertyDescriptor.Builder()
            .name("ldap-base-dn")
            .description("Ldap directory bind base dn")
            .required(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        final List<PropertyDescriptor> descriptors = new ArrayList<>();
        descriptors.add(LDAP_HOST);
        descriptors.add(LDAP_PORT);
        descriptors.add(LDAP_BIND_DN);
        descriptors.add(LDAP_BASE_DN);
        descriptors.add(LDAP_PWD);
        return descriptors;
    }

    @OnEnabled
    public void onEnabled(final ConfigurationContext context) throws Exception {
        ldapHost = context.getProperty(LDAP_HOST).getValue();
        ldapPort = context.getProperty(LDAP_PORT).asInteger();
        ldapBindDn = context.getProperty(LDAP_BIND_DN).getValue();
        ldapBaseDn = context.getProperty(LDAP_BASE_DN).getValue();
        ldapPwd = context.getProperty(LDAP_PWD).getValue();
    }

    @Override
    public UserContactInfo resolveUserContactInfo(String userId) {
        return loadUserContactInfo(userId);
    }

    private UserContactInfo loadUserContactInfo(String userId) {
        UserContactInfo uci = null;

        LdapConnectionConfig config = new LdapConnectionConfig();
        config.setLdapHost(ldapHost);
        config.setLdapPort(ldapPort);
        config.setName(ldapBindDn);
        config.setCredentials(ldapPwd);

        try {
            Dn systemDn = new Dn(ldapBaseDn);
            LdapConnection connection = new LdapNetworkConnection(config);
            connection.connect();
            SearchRequest searchRequest = new SearchRequestImpl();
            String filter = "(sn=" + userId + ")";
            searchRequest.setBase(systemDn);
            searchRequest.setFilter(filter);
            searchRequest.setScope(SearchScope.SUBTREE);
            searchRequest.setTypesOnly(false);
            searchRequest.addAttributes(SEARCH_ATTR_MAIL,
                    SEARCH_ATTR_HOMEPHONE, SEARCH_ATTR_CHAT, SEARCH_ATTR_VOIP);
            SearchCursor cursor = connection.search(searchRequest);
            uci = new UserContactInfo();
            uci.setName(userId);
            while (cursor.next()) {
                Response resp = cursor.get();
                // process the SearchResultEntry
                if (resp instanceof SearchResultEntry) {
                    Entry resultEntry = ((SearchResultEntry) resp).getEntry();
                    Collection<Attribute> attr = resultEntry.getAttributes();
                    Map<String, PhysicalAddress> addressesByType = new HashMap<>();
                    for (Attribute attribute : attr) {

                        if (SEARCH_ATTR_MAIL.equalsIgnoreCase(attribute.getId())) {
                            addressesByType.put(SERVICE_TYPE_EMAIL, new PhysicalAddress(SERVICE_TYPE_EMAIL, attribute.getString()));
                        } else if (SEARCH_ATTR_HOMEPHONE.equalsIgnoreCase(attribute.getId())) {
                            addressesByType.put(SERVICE_TYPE_SMS, new PhysicalAddress(SERVICE_TYPE_SMS, attribute.getString()));
                        } else if (SEARCH_ATTR_CHAT.equalsIgnoreCase(attribute.getId())) {
                            addressesByType.put(SERVICE_TYPE_CHAT, new PhysicalAddress(SERVICE_TYPE_CHAT, attribute.getString()));
                        } else if (SEARCH_ATTR_VOIP.equalsIgnoreCase(attribute.getId())) {
                            addressesByType.put(SERVICE_TYPE_TEXT_TO_VOICE, new PhysicalAddress(SERVICE_TYPE_TEXT_TO_VOICE, attribute.getString()));
                        }
                        uci.setAddressesByType(addressesByType);
                        uci.setPreferredAddress(addressesByType.values().iterator().next());
                    }
                }
            }
            cursor.close();
            connection.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return uci;
    }

}

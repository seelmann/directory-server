/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.apache.directory.server.newldap.handlers.bind.cramMD5;


import org.apache.directory.server.core.CoreSession;
import org.apache.directory.server.newldap.LdapServer;
import org.apache.directory.server.newldap.LdapSession;
import org.apache.directory.server.newldap.handlers.bind.MechanismHandler;
import org.apache.directory.server.newldap.handlers.bind.SaslConstants;
import org.apache.directory.shared.ldap.constants.SupportedSaslMechanisms;
import org.apache.directory.shared.ldap.message.BindRequest;

import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslServer;
import java.util.HashMap;
import java.util.Map;


/**
 * The CRAM-MD Sasl mechanism handler.
 *
 * @org.apache.xbean.XBean
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class CramMd5MechanismHandler implements MechanismHandler
{
    public SaslServer handleMechanism( LdapSession ldapSession, CoreSession adminSession, BindRequest bindRequest ) throws Exception
    {
        SaslServer ss = (SaslServer)ldapSession.getSaslProperty( SaslConstants.SASL_SERVER );

        // TODO - don't use session properties anymore
        if ( ss == null )
        {
            String saslHost = ldapSession.getLdapServer().getSaslHost();
            String userBaseDn = ldapSession.getLdapServer().getSearchBaseDn();
            ldapSession.putSaslProperty( SaslConstants.SASL_HOST, saslHost );
            ldapSession.putSaslProperty( SaslConstants.SASL_USER_BASE_DN, userBaseDn );
            Map<String, String> saslProps = new HashMap<String, String>();
            
            CallbackHandler callbackHandler = new CramMd5CallbackHandler( ldapSession, adminSession, bindRequest );

            ss = Sasl.createSaslServer( SupportedSaslMechanisms.CRAM_MD5, SaslConstants.LDAP_PROTOCOL, saslHost, saslProps, callbackHandler );
            ldapSession.putSaslProperty( SaslConstants.SASL_SERVER, ss );
        }

        return ss;
    }
}

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
package org.apache.directory.server.newldap.handlers;


import java.util.Map;

import javax.naming.Name;
import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosKey;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import org.apache.directory.server.core.CoreSession;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.core.interceptor.context.BindOperationContext;
import org.apache.directory.server.kerberos.shared.crypto.encryption.EncryptionType;
import org.apache.directory.server.kerberos.shared.messages.value.EncryptionKey;
import org.apache.directory.server.kerberos.shared.store.PrincipalStoreEntry;
import org.apache.directory.server.kerberos.shared.store.operations.GetPrincipal;
import org.apache.directory.server.newldap.LdapProtocolUtils;
import org.apache.directory.server.newldap.LdapServer;
import org.apache.directory.server.newldap.LdapSession;
import org.apache.directory.server.newldap.handlers.bind.MechanismHandler;
import org.apache.directory.server.newldap.handlers.bind.SaslConstants;
import org.apache.directory.server.newldap.handlers.bind.SaslFilter;
import org.apache.directory.server.protocol.shared.ServiceConfigurationException;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.constants.SupportedSaslMechanisms;
import org.apache.directory.shared.ldap.exception.LdapAuthenticationException;
import org.apache.directory.shared.ldap.exception.LdapException;
import org.apache.directory.shared.ldap.message.BindRequest;
import org.apache.directory.shared.ldap.message.BindResponse;
import org.apache.directory.shared.ldap.message.LdapResult;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.util.ExceptionUtils;
import org.apache.directory.shared.ldap.util.StringTools;
import org.apache.mina.common.IoFilterChain;
import org.apache.mina.common.IoSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A single reply handler for {@link BindRequest}s.
 *
 * Implements server-side of RFC 2222, sections 4.2 and 4.3.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 664302 $, $Date: 2008-06-07 04:44:00 -0400 (Sat, 07 Jun 2008) $
 */
public class NewBindHandler extends LdapRequestHandler<BindRequest>
{
    private static final Logger LOG = LoggerFactory.getLogger( NewBindHandler.class );

    /** A Hashed Adapter mapping SASL mechanisms to their handlers. */
    private Map<String, MechanismHandler> handlers;

    /** A session created using the server Admin, to be able to get full access to the server */
    private CoreSession adminSession;
    
    
    /** A lock used to protect the creation of the inner AdminSession */
    private Object mutex = new Object();
    
    
    /**
     * Set the mechanisms handler map.
     * 
     * @param handlers The associations btween a machanism and its handler
     */
    public void setSaslMechanismHandlers( Map<String, MechanismHandler> handlers )
    {
        this.handlers = handlers;
    }
    

    /**
     * Handle the Simple authentication.
     *
     * @param session The associated Session
     * @param message The BindRequest received
     * @throws Exception If the authentication cannot be done
     */
    public void handleSimpleAuth( LdapSession ldapSession, BindRequest bindRequest ) throws Exception
    {
        // if the user is already bound, we have to unbind him
        if ( !ldapSession.isAnonymous() )
        {
            // We already have a bound session for this user. We have to
            // abandon it first.
            ldapSession.getCoreSession().unbind();
            
            // Reset the status to Anonymous
            ldapSession.setAnonymous();
        }

        // Now, bind the user
        
        // create a new Bind context, with a null session, as we don't have 
        // any context yet.
        BindOperationContext opContext = new BindOperationContext( null );
        
        // Stores the DN of the user to check, and its password
        opContext.setDn( bindRequest.getName() );
        opContext.setCredentials( bindRequest.getCredentials() );

        // Stores the request controls into the operation context
        LdapProtocolUtils.setRequestControls( opContext, bindRequest );
        
        try
        {
	        // And call the OperationManager bind operation.
	        getLdapServer().getDirectoryService().getOperationManager().bind( opContext );
	        
	        // As a result, store the created session in the Core Session
	        ldapSession.setCoreSession( opContext.getSession() );
	        
	        if ( ! ldapSession.getCoreSession().isAnonymous() )
	        {
	            ldapSession.setAuthenticated();
	        }
	        
	        // Return the successful response
	        sendBindSuccess( ldapSession, bindRequest, null );
        }
        catch ( Exception e )
        {
        	// Something went wrong. Write back an error message        	
            ResultCodeEnum code = null;
            LdapResult result = bindRequest.getResultResponse().getLdapResult();

            if ( e instanceof LdapException )
            {
                code = ( ( LdapException ) e ).getResultCode();
                result.setResultCode( code );
            }
            else
            {
                code = ResultCodeEnum.getBestEstimate( e, bindRequest.getType() );
                result.setResultCode( code );
            }

            String msg = "Bind failed: " + e.getMessage();

            if ( LOG.isDebugEnabled() )
            {
                msg += ":\n" + ExceptionUtils.getStackTrace( e );
                msg += "\n\nBindRequest = \n" + bindRequest.toString();
            }

            Name name = null;
            
            if ( e instanceof LdapAuthenticationException )
            {
            	name = ((LdapAuthenticationException)e).getResolvedName();
            }
            
            if ( ( name != null )
                && ( ( code == ResultCodeEnum.NO_SUCH_OBJECT ) || ( code == ResultCodeEnum.ALIAS_PROBLEM )
                    || ( code == ResultCodeEnum.INVALID_DN_SYNTAX ) || ( code == ResultCodeEnum.ALIAS_DEREFERENCING_PROBLEM ) ) )
            {
                result.setMatchedDn( new LdapDN( name ) );
            }

            result.setErrorMessage( msg );
            ldapSession.getIoSession().write( bindRequest.getResultResponse() );
        }
    }
    
    
    /**
     * Check if the mechanism exists.
     */
    private boolean checkMechanism( LdapSession ldapSession, String saslMechanism ) throws Exception
    {
        // Guard clause:  Reject unsupported SASL mechanisms.
        if ( ! ldapServer.getSupportedMechanisms().contains( saslMechanism ) )
        {
            LOG.error( "Bind error : {} mechanism not supported. Please check the server.xml " + 
                "configuration file (supportedMechanisms field)", 
                saslMechanism );

            return false;
        }
        else
        {
            return true;
        }
    }
    
    
    /**
     * For challenge/response exchange, generate the challenge 
     *
     * @param ldapSession
     * @param ss
     * @param bindRequest
     */
    private void generateSaslChallenge( LdapSession ldapSession, SaslServer ss, BindRequest bindRequest )
    {
        LdapResult result = bindRequest.getResultResponse().getLdapResult();

        // SaslServer will throw an exception if the credentials are null.
        if ( bindRequest.getCredentials() == null )
        {
            bindRequest.setCredentials( new byte[0] );
        }

        try
        {
            // Compute the challenge
            byte[] tokenBytes = ss.evaluateResponse( bindRequest.getCredentials() );
    
            if ( ss.isComplete() )
            {
                // This is the end of the C/R exchange
                if ( tokenBytes != null )
                {
                    /*
                     * There may be a token to return to the client.  We set it here
                     * so it will be returned in a SUCCESS message, after an LdapContext
                     * has been initialized for the client.
                     */
                    ldapSession.putSaslProperty( SaslConstants.SASL_CREDS, tokenBytes );
                }
                
                // Return the successful response
                sendBindSuccess( ldapSession, bindRequest, tokenBytes );
            }
            else
            {
                // The SASL bind must continue, we are sending the computed challenge
                LOG.info( "Continuation token had length " + tokenBytes.length );
                
                // Build the response
                result.setResultCode( ResultCodeEnum.SASL_BIND_IN_PROGRESS );
                BindResponse resp = ( BindResponse ) bindRequest.getResultResponse();
                
                // Store the challenge
                resp.setServerSaslCreds( tokenBytes );
                
                // Switch to AuthPending
                ldapSession.setAuthPending();
                
                // Store the current mechanism, as the C/R is not finished
                ldapSession.putSaslProperty( SaslConstants.SASL_MECH, bindRequest.getSaslMechanism() );
                
                // And write back the response
                ldapSession.getIoSession().write( resp );
                LOG.debug( "Returning final authentication data to client to complete context." );
            }
        }
        catch ( SaslException se )
        {
            LOG.error( se.getMessage() );
            result.setResultCode( ResultCodeEnum.INVALID_CREDENTIALS );
            result.setErrorMessage( se.getMessage() );
            
            // Reinitialize the state to Anonymous and clear the sasl properties
            ldapSession.clearSaslProperties();
            ldapSession.setAnonymous();
            
            // Write back the error response
            ldapSession.getIoSession().write( bindRequest.getResultResponse() );
        }
    }
    
    
    /**
     * Send back an AUTH-METH-NOT-SUPPORTED error message to the client
     */
    private void sendAuthMethNotSupported( LdapSession ldapSession, BindRequest bindRequest )
    {
        // First, reinit the state to Anonymous, and clear the
        // saslProperty map
        ldapSession.clearSaslProperties();
        ldapSession.setAnonymous();
        
        // And send the response to the client
        LdapResult bindResult = bindRequest.getResultResponse().getLdapResult();
        bindResult.setResultCode( ResultCodeEnum.AUTH_METHOD_NOT_SUPPORTED );
        bindResult.setErrorMessage( bindRequest.getSaslMechanism() + " is not a supported mechanism." );
        
        // Write back the error
        ldapSession.getIoSession().write( bindRequest.getResultResponse() );

        return;
    }
    
    
    /**
     * Send back an INVALID-CREDENTIAL error message to the user. If we have an exception
     * as a third argument, then send back the associated message to the client. 
     */
    private void sendInvalidCredentials( LdapSession ldapSession, BindRequest bindRequest, Exception e )
    {
        LdapResult result = bindRequest.getResultResponse().getLdapResult();
        
        String message = "";
        
        if ( e != null )
        {
            message = e.getMessage();
        }
        
        LOG.error( message );
        result.setResultCode( ResultCodeEnum.INVALID_CREDENTIALS );
        result.setErrorMessage( message );
        
        // Reinitialize the state to Anonymous and clear the sasl properties
        ldapSession.clearSaslProperties();
        ldapSession.setAnonymous();
        
        // Write back the error response
        ldapSession.getIoSession().write( bindRequest.getResultResponse() );
    }
    
    
    /**
     * Send a SUCCESS message back to the client.
     */
    private void sendBindSuccess( LdapSession ldapSession, BindRequest bindRequest, byte[] tokenBytes )
    {
        // Return the successful response
        BindResponse response = ( BindResponse ) bindRequest.getResultResponse();
        response.getLdapResult().setResultCode( ResultCodeEnum.SUCCESS );
        response.setServerSaslCreds( tokenBytes );
        
        if ( ! ldapSession.getCoreSession().isAnonymous() )
        {
            // If we have not been asked to authenticate as Anonymous, authenticate the user
            ldapSession.setAuthenticated();
        }
        else
        {
            // Otherwise, switch back to Anonymous
            ldapSession.setAnonymous();
        }
        
        // Clean the SaslProperties, we don't need them anymore
        // except the saslCreds and saslServer which will be used 
        // by the DIGEST-MD5 mech.
        ldapSession.removeSaslProperty( SaslConstants.SASL_MECH );
        ldapSession.removeSaslProperty( SaslConstants.SASL_HOST );
        ldapSession.removeSaslProperty( SaslConstants.SASL_AUTHENT_USER );
        ldapSession.removeSaslProperty( SaslConstants.SASL_USER_BASE_DN );

        ldapSession.getIoSession().write( response );
        
        LOG.debug( "Returned SUCCESS message: {}.", response );
    }

    
    private void handleSaslAuthPending( LdapSession ldapSession, BindRequest bindRequest, DirectoryService ds ) throws Exception
    {
        // First, check that we have the same mechanism
        String saslMechanism = bindRequest.getSaslMechanism();
        
        if ( !ldapSession.getSaslProperty( SaslConstants.SASL_MECH ).equals( saslMechanism ) )
        {
            sendAuthMethNotSupported( ldapSession, bindRequest );
            return;
        }
        // We have already received a first BindRequest, and sent back some challenge.
        // First, check if the mechanism is the same
        MechanismHandler mechanismHandler = handlers.get( saslMechanism );

        if ( mechanismHandler == null )
        {
            String message = "Handler unavailable for " + saslMechanism;
            LOG.error( message );
            throw new IllegalArgumentException( message );
        }

        SaslServer ss = mechanismHandler.handleMechanism( ldapSession, adminSession, bindRequest );
        
        if ( !ss.isComplete() )
        {
            /*
             * SaslServer will throw an exception if the credentials are null.
             */
            if ( bindRequest.getCredentials() == null )
            {
                bindRequest.setCredentials( new byte[0] );
            }
            
            byte[] tokenBytes = ss.evaluateResponse( bindRequest.getCredentials() );
            
            if ( ss.isComplete() )
            {
                if ( tokenBytes != null )
                {
                    /*
                     * There may be a token to return to the client.  We set it here
                     * so it will be returned in a SUCCESS message, after an LdapContext
                     * has been initialized for the client.
                     */
                    ldapSession.putSaslProperty( SaslConstants.SASL_CREDS, tokenBytes );
                }
                
                // Create the user's coreSession
                try
                {
                    ServerEntry userEntry = (ServerEntry)ldapSession.getSaslProperty( SaslConstants.SASL_AUTHENT_USER );
                    
                    CoreSession userSession = ds.getSession( userEntry.getDn(), userEntry.get( SchemaConstants.USER_PASSWORD_AT ).getBytes(), saslMechanism, null );
                    
                    ldapSession.setCoreSession( userSession );
                    
                    // Mark the user as authenticated
                    ldapSession.setAuthenticated();
                    
                    /*
                     * If the SASL mechanism is DIGEST-MD5 or GSSAPI, we insert a SASLFilter.
                     */
                    if ( saslMechanism.equals( SupportedSaslMechanisms.DIGEST_MD5 ) ||
                         saslMechanism.equals( SupportedSaslMechanisms.GSSAPI ) )
                    {
                        LOG.debug( "Inserting SaslFilter to engage negotiated security layer." );
                        IoSession ioSession = ldapSession.getIoSession();

                        IoFilterChain chain = ioSession.getFilterChain();
                        
                        if ( !chain.contains( "SASL_FILTER" ) )
                        {
                            SaslServer saslServer = ( SaslServer ) ldapSession.getSaslProperty( SaslConstants.SASL_SERVER );
                            chain.addBefore( "codec", "SASL_FILTER", new SaslFilter( saslServer ) );
                        }

                        /*
                         * We disable the SASL security layer once, to write the outbound SUCCESS
                         * message without SASL security layer processing.
                         */
                        ioSession.setAttribute( SaslFilter.DISABLE_SECURITY_LAYER_ONCE, Boolean.TRUE );
                    }

                    // And send a Success response
                    sendBindSuccess( ldapSession, bindRequest, tokenBytes );
                }
                catch ( Exception e )
                {
                    
                }
            }
        }
    }
    
    
    /**
     * Handle the SASL authentication. If the mechanism is known, we are
     * facing three cases :
     * <ul>
     * <li>The user does not has a session yet</li>
     * <li>The user already has a session</li>
     * <li>The user has started a SASL negotiation</li>
     * </lu><br/>
     * 
     * In the first case, we initiate a SaslBind session, which will be used all
     * along the negotiation.<br/>
     * In the second case, we first have to unbind the user, and initiate a new
     * SaslBind session.<br/>
     * In the third case, we have sub cases :
     * <ul>
     * <li>The mechanism is not provided : that means the user want to reset the
     * current negotiation. We move back to an Anonymous state</li>
     * <li>The mechanism is provided : the user is initializing a new negotiation
     * with another mechanism. The current SaslBind session is reinitialized</li>
     * <li></li>
     * </ul><br/>
     *
     * @param session The associated Session
     * @param message The BindRequest received
     * @throws Exception If the authentication cannot be done
     */
    public void handleSaslAuth( LdapSession ldapSession, BindRequest bindRequest ) throws Exception
    {
        String saslMechanism = bindRequest.getSaslMechanism();
        DirectoryService ds = getLdapServer().getDirectoryService();
        
        // Case #2 : the user does have a session. We have to unbind him
        if ( ldapSession.isAuthenticated() )
        {
            // We already have a bound session for this user. We have to
            // close the previous session first.
            ldapSession.getCoreSession().unbind();
            
            // Reset the status to Anonymous
            ldapSession.setAnonymous();
            
            // Clean the sasl properties
            ldapSession.clearSaslProperties();
            
            // Now we can continue as if the client was Anonymous from the beginning
        }

        // case #1 : The user does not have a session.
        if ( ldapSession.isAnonymous() )
        {
            if ( !StringTools.isEmpty( saslMechanism ) )
            {
                // fist check that the mechanism exists
                if ( !checkMechanism( ldapSession, saslMechanism ) )
                {
                    // get out !
                    sendAuthMethNotSupported( ldapSession, bindRequest );

                    return;
                }

                // Store the mechanism in the ldap session
                ldapSession.putSaslProperty( SaslConstants.SASL_MECH, saslMechanism );
                

                // Store the host in the ldap session
                String saslHost = getLdapServer().getSaslHost();
                ldapSession.putSaslProperty( SaslConstants.SASL_HOST, saslHost );

                // Get the handler for this mechanism
                MechanismHandler mechanismHandler = handlers.get( saslMechanism );
                
                // Get the SaslServer instance which manage the C/R exchange
                SaslServer ss = mechanismHandler.handleMechanism( ldapSession, adminSession, bindRequest );
                
                // We have to generate a challenge
                generateSaslChallenge( ldapSession, ss, bindRequest );
                
                // And get back
                return;
            }
        }
        else if ( ldapSession.isAuthPending() )
        {
            try
            {
                handleSaslAuthPending( ldapSession, bindRequest, ds );
            }
            catch ( SaslException se )
            {
                sendInvalidCredentials( ldapSession, bindRequest, se );
            }
            return;
        }
    }


    /**
     * Create a list of all the configured realms.
     * 
     * @param ldapServer the LdapServer for which we want to get the realms
     * @return a list of realms, separated by spaces
     */
    private String getActiveRealms( LdapServer ldapServer )
    {
        StringBuilder realms = new StringBuilder();
        boolean isFirst = true;

        for ( String realm:ldapServer.getSaslRealms() )
        {
            if ( isFirst )
            {
                isFirst = false;
            }
            else
            {
                realms.append( ' ' );
            }
            
            realms.append( realm );
        }

        return realms.toString();
    }


    private Subject getSubject( LdapServer ldapServer ) throws Exception
    {
        String servicePrincipalName = ldapServer.getSaslPrincipal();

        KerberosPrincipal servicePrincipal = new KerberosPrincipal( servicePrincipalName );
        GetPrincipal getPrincipal = new GetPrincipal( servicePrincipal );

        PrincipalStoreEntry entry = null;

        try
        {
            entry = findPrincipal( ldapServer, getPrincipal );
        }
        catch ( ServiceConfigurationException sce )
        {
            String message = "Service principal " + servicePrincipalName + " not found at search base DN "
                + ldapServer.getSearchBaseDn() + ".";
            throw new ServiceConfigurationException( message, sce );
        }

        if ( entry == null )
        {
            String message = "Service principal " + servicePrincipalName + " not found at search base DN "
                + ldapServer.getSearchBaseDn() + ".";
            throw new ServiceConfigurationException( message );
        }

        Subject subject = new Subject();

        for ( EncryptionType encryptionType:entry.getKeyMap().keySet() )
        {
            EncryptionKey key = entry.getKeyMap().get( encryptionType );

            byte[] keyBytes = key.getKeyValue();
            int type = key.getKeyType().getOrdinal();
            int kvno = key.getKeyVersion();

            KerberosKey serviceKey = new KerberosKey( servicePrincipal, keyBytes, type, kvno );

            subject.getPrivateCredentials().add( serviceKey );
        }

        return subject;
    }
    

    private PrincipalStoreEntry findPrincipal( LdapServer ldapServer, GetPrincipal getPrincipal ) throws Exception
    {
        synchronized ( mutex )
        {
            if ( adminSession == null )
            {
                adminSession = getLdapServer().getDirectoryService().getAdminSession();
            }
        }

        return ( PrincipalStoreEntry ) getPrincipal.execute( adminSession, null );
    }    
    

    /**
     * Deal with a received BindRequest
     * 
     * @param session The current session
     * @param bindRequest The received BindRequest
     * @throws Exception If the authentication cannot be handled
     */
    @Override
    public void handle( LdapSession ldapSession, BindRequest bindRequest ) throws Exception
    {
        LOG.debug( "Received: {}", bindRequest );

        // Guard clause:  LDAP version 3
        if ( ! bindRequest.getVersion3() )
        {
            LOG.error( "Bind error : Only LDAP v3 is supported." );
            LdapResult bindResult = bindRequest.getResultResponse().getLdapResult();
            bindResult.setResultCode( ResultCodeEnum.PROTOCOL_ERROR );
            bindResult.setErrorMessage( "Only LDAP v3 is supported." );
            ldapSession.getIoSession().write( bindRequest.getResultResponse() );
            return;
        }

        // Deal with the two kinds of authentication : Simple and SASL
        if ( bindRequest.isSimple() )
        {
            handleSimpleAuth( ldapSession, bindRequest );
        }
        else
        {
            synchronized ( mutex )
            {
                if ( adminSession == null )
                {
                    adminSession = getLdapServer().getDirectoryService().getAdminSession();
                    ldapSession.setLdapServer( getLdapServer() );
                }
            }

            handleSaslAuth( ldapSession, bindRequest );
        }
    }
}

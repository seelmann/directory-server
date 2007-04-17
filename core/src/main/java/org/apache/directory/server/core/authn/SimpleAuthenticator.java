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
package org.apache.directory.server.core.authn;


import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;

import org.apache.commons.collections.map.LRUMap;
import org.apache.directory.server.core.interceptor.context.LookupOperationContext;
import org.apache.directory.server.core.invocation.Invocation;
import org.apache.directory.server.core.invocation.InvocationStack;
import org.apache.directory.server.core.jndi.ServerContext;
import org.apache.directory.server.core.partition.PartitionNexusProxy;
import org.apache.directory.server.core.trigger.TriggerService;
import org.apache.directory.shared.ldap.aci.AuthenticationLevel;
import org.apache.directory.shared.ldap.constants.LdapSecurityConstants;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.exception.LdapAuthenticationException;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.util.ArrayUtils;
import org.apache.directory.shared.ldap.util.Base64;
import org.apache.directory.shared.ldap.util.StringTools;
import org.apache.directory.shared.ldap.util.UnixCrypt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A simple {@link Authenticator} that authenticates clear text passwords
 * contained within the <code>userPassword</code> attribute in DIT. If the
 * password is stored with a one-way encryption applied (e.g. SHA), the password
 * is hashed the same way before comparison.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class SimpleAuthenticator extends AbstractAuthenticator
{
    private static final Logger log = LoggerFactory.getLogger( SimpleAuthenticator.class );
    
    /** A speedup for logger in debug mode */
    private static final boolean IS_DEBUG = log.isDebugEnabled();

    /**
     * A cache to store passwords. It's a speedup, we will be able to avoid backend lookups.
     * 
     * Note that the backend also use a cache mechanism, but for performance gain, it's good 
     * to manage a cache here. The main problem is that when a user modify his password, we will
     * have to update it at three different places :
     * - in the backend,
     * - in the partition cache,
     * - in this cache.
     * 
     * The update of the backend and partition cache is already correctly handled, so we will
     * just have to offer an access to refresh the local cache.
     * 
     * We need to be sure that frequently used passwords be always in cache, and not discarded.
     * We will use a LRU cache for this purpose. 
     */ 
    private LRUMap credentialCache;
    
    /** Declare a default for this cache. 100 entries seems to be enough */
    private static final int DEFAULT_CACHE_SIZE = 100;

    /**
     * Define the interceptors we should *not* go through when we will have to request the backend
     * about a userPassword.
     */
    private static final Collection USERLOOKUP_BYPASS;
    static
    {
        Set<String> c = new HashSet<String>();
        c.add( "normalizationService" );
        c.add( "authenticationService" );
        c.add( "referralService" );
        c.add( "authorizationService" );
        c.add( "defaultAuthorizationService" );
        c.add( "exceptionService" );
        c.add( "operationalAttributeService" );
        c.add( "schemaService" );
        c.add( "subentryService" );
        c.add( "collectiveAttributeService" );
        c.add( "eventService" );
        c.add( TriggerService.SERVICE_NAME );
        USERLOOKUP_BYPASS = Collections.unmodifiableCollection( c );
    }


    /**
     * Creates a new instance.
     * @
     */
    @SuppressWarnings( "unchecked" )
    public SimpleAuthenticator()
    {
        super( "simple" );
        
        credentialCache = new LRUMap( DEFAULT_CACHE_SIZE );
    }

    /**
     * Creates a new instance, with an initial cache size
     */
    @SuppressWarnings( "unchecked" )
    public SimpleAuthenticator( int cacheSize)
    {
        super( "simple" );

        credentialCache = new LRUMap( cacheSize > 0 ? cacheSize : DEFAULT_CACHE_SIZE );
    }

    private class SaltedPassword
    {
        private byte[] salt;
        private byte[] password;
    }
    
    /**
     * Looks up <tt>userPassword</tt> attribute of the entry whose name is the
     * value of {@link Context#SECURITY_PRINCIPAL} environment variable, and
     * authenticates a user with the plain-text password.
     * 
     * We have at least 6 algorithms to encrypt the password :
     * - SHA
     * - SSHA (salted SHA)
     * - MD5
     * - SMD5 (slated MD5)
     * - crypt (unix crypt)
     * - plain text, ie no encryption.
     * 
     *  If we get an encrypted password, it is prefixed by the used algorithm, between
     *  brackets : {SSHA}password ...
     *  
     *  If the password is using SSHA, SMD5 or crypt, some 'salt' is added to the password :
     *  - length(password) - 20, starting at 21th position for SSHA
     *  - length(password) - 16, starting at 16th position for SMD5
     *  - length(password) - 2, starting at 3rd position for crypt
     *  
     *  For (S)SHA and (S)MD5, we have to transform the password from Base64 encoded text
     *  to a byte[] before comparing the password with the stored one.
     *  For crypt, we only have to remove the salt.
     *  
     *  At the end, we use the digest() method for (S)SHA and (S)MD5, the crypt() method for
     *  the CRYPT algorithm and a straight comparison for PLAIN TEXT passwords.
     *  
     *  The stored password is always using the unsalted form, and is stored as a bytes array.
     */
    public LdapPrincipal authenticate( LdapDN principalDn, ServerContext ctx ) throws NamingException
    {
        if ( IS_DEBUG )
        {
            log.debug( "Authenticating {}", principalDn );
        }
        
        // ---- extract password from JNDI environment
        Object creds = ctx.getEnvironment().get( Context.SECURITY_CREDENTIALS );
        byte[] credentials = null;
        String principalNorm = principalDn.getNormName();

        if ( creds == null )
        {
            credentials = ArrayUtils.EMPTY_BYTE_ARRAY;
        }
        else if ( creds instanceof String )
        {
            credentials = StringTools.getBytesUtf8( ( String ) creds );
        }
        else if ( creds instanceof byte[] )
        {
            // This is the general case. When dealing with a BindRequest operation,
            // received by the server, the credentials are always stored into a byte array
            credentials = (byte[])creds;
        }
        else
        {
            log.info( "Incorrect credentials stored in {}", Context.SECURITY_CREDENTIALS );
            throw new LdapAuthenticationException();
        }
        
        boolean credentialsMatch = false;
        LdapPrincipal principal = null;
        
        // Check to see if the password is stored in the cache for this principal
        synchronized( credentialCache )
        {
            principal = (LdapPrincipal)credentialCache.get( principalNorm );
        }
        
        byte[] storedPassword = null;
        
        if ( principal == null )
        {
            // Not found in the cache
            // Get the user password from the backend
            storedPassword = lookupUserPassword( principalDn );
            
            
            // Deal with the special case where the user didn't enter a password
            // We will compare the empty array with the credentials. Sometime,
            // a user does not set a password. This is bad, but there is nothing
            // we can do against that, except education ...
            if ( storedPassword == null )
            {
                storedPassword = ArrayUtils.EMPTY_BYTE_ARRAY;
            }
        }
        else
        {
            // Found ! 
            storedPassword = principal.getUserPassword();
        }
        
        // Short circuit for PLAIN TEXT passwords : we compare the byte array directly
        // Are the passwords equal ?
        credentialsMatch = Arrays.equals( credentials, storedPassword );
        
        if ( !credentialsMatch )
        {
            // Let's see if the stored password was encrypted
            String algorithm = findAlgorithm( storedPassword );
            
            if ( algorithm != null )
            {
                SaltedPassword saltedPassword = new SaltedPassword();
                saltedPassword.password = storedPassword;
                saltedPassword.salt = null;
                
                // Let's get the encrypted part of the stored password
                byte[] encryptedStored = splitCredentials( saltedPassword, algorithm );
                
                byte[] userPassword = encryptPassword( credentials, algorithm, saltedPassword.salt );
                
                credentialsMatch = Arrays.equals( userPassword, encryptedStored );
            }

        }
            
        // If the password match, we can return
        if ( credentialsMatch )
        {
            if ( principal == null )
            {
                // Last, if we have found the credential, we have to store it in the cache
                principal = new LdapPrincipal( principalDn, AuthenticationLevel.SIMPLE, storedPassword );
    
                // Now, update the local cache.
                synchronized( credentialCache )
                {
                    credentialCache.put( principalNorm, principal );
                }
            }

            if ( IS_DEBUG )
            {
                log.debug( "{} Authenticated", principalDn );
            }
            
            return principal;
        }
        
        // Bad password ...
        String message = "Password not correct for user '" + principalDn.getUpName() + "'";
        log.info( message );
        throw new LdapAuthenticationException(message);
    }
    
    private static void split( byte[] all, int offset, byte[] left, byte[] right )
    {
        System.arraycopy( all, offset, left, 0, left.length );
        System.arraycopy( all, offset + left.length, right, 0, right.length );
    }

    private byte[] splitCredentials( SaltedPassword saltedPassword , String algorithm )
    {
        byte[] credentials = saltedPassword.password;
        
        int pos = algorithm.length() + 2;
        
        if ( ( LdapSecurityConstants.HASH_METHOD_MD5.equals( algorithm ) ) ||
            ( LdapSecurityConstants.HASH_METHOD_SHA.equals( algorithm ) ) )
        {
            try
            {
                return Base64.decode( new String( credentials, pos, credentials.length - ( pos + 1 ), "UTF-8" ).toCharArray() );
            }
            catch ( UnsupportedEncodingException uee )
            {
                // do nothing 
                return credentials;
            }
        }
        else if ( ( LdapSecurityConstants.HASH_METHOD_SMD5.equals( algorithm ) ) ||
                 ( LdapSecurityConstants.HASH_METHOD_SSHA.equals( algorithm ) ) )
        {
            try
            {
                byte[] password = Base64.decode( new String( credentials, pos, credentials.length - ( pos + 1 ), "UTF-8" ).toCharArray() );
                
                saltedPassword.salt = new byte[8];
                byte[] hashedPassword = new byte[password.length - saltedPassword.salt.length];
                split( password, 0, hashedPassword, saltedPassword.salt );
                
                return hashedPassword;
            }
            catch ( UnsupportedEncodingException uee )
            {
                // do nothing 
                return credentials;
            }
        }
        else if ( LdapSecurityConstants.HASH_METHOD_CRYPT.equals( algorithm ) )
        {
            saltedPassword.salt = new byte[2];
            byte[] hashedPassword = new byte[credentials.length - saltedPassword.salt.length - pos];
            split( credentials, pos, saltedPassword.salt, hashedPassword );
            
            return hashedPassword;
        }
        else
        {
            // unknown method
            return credentials;
        }
    }
    
    private String findAlgorithm( byte[] credentials )
    {
        if ( ( credentials == null ) || ( credentials.length == 0 ) )
        {
            return null;
        }
        
        if ( credentials[0] == '{' )
        {
            // get the algorithm
            int pos = 1;
            
            while ( pos < credentials.length )
            {
                if ( credentials[pos] == '}' )
                {
                    break;
                }
                
                pos++;
            }
            
            if ( pos < credentials.length )
            {
                if ( pos == 1 )
                {
                    // We don't have an algorithm : return the credentials as is
                    return null;
                }
                
                String algorithm = new String( credentials, 1, pos - 1 ).toLowerCase();
                
                if ( ( LdapSecurityConstants.HASH_METHOD_MD5.equals( algorithm ) ) ||
                    ( LdapSecurityConstants.HASH_METHOD_SHA.equals( algorithm ) ) ||
                    ( LdapSecurityConstants.HASH_METHOD_SMD5.equals( algorithm ) ) ||
                    ( LdapSecurityConstants.HASH_METHOD_SSHA.equals( algorithm ) ) ||
                    ( LdapSecurityConstants.HASH_METHOD_CRYPT.equals( algorithm ) ) )
                {
                    return algorithm;
                }
                else
                {
                    // unknown method
                    return null;
                }
            }
            else
            {
                // We don't have an algorithm
                return null;
            }
        }
        else
        {
            // No '{algo}' part
            return null;
        }
    }
    
    private static byte[] digest( String algorithm, byte[] password, byte[] salt )
    {
        MessageDigest digest;

        try
        {
            digest = MessageDigest.getInstance( algorithm );
        }
        catch ( NoSuchAlgorithmException e1 )
        {
            return null;
        }

        if ( salt != null )
        {
            digest.update( password );
            digest.update( salt );
            byte[] hashedPasswordBytes = digest.digest();
            return hashedPasswordBytes;
        }
        else
        {
            byte[] hashedPasswordBytes = digest.digest( password );
            return hashedPasswordBytes;
        }
    }

    private byte[] encryptPassword( byte[] credentials, String algorithm, byte[] salt )
    {
        if ( LdapSecurityConstants.HASH_METHOD_SHA.equals( algorithm ) || 
             LdapSecurityConstants.HASH_METHOD_SSHA.equals( algorithm ) )
        {   
            return digest( LdapSecurityConstants.HASH_METHOD_SHA, credentials, salt );
        }
        else if ( LdapSecurityConstants.HASH_METHOD_MD5.equals( algorithm ) ||
                  LdapSecurityConstants.HASH_METHOD_SMD5.equals( algorithm ) )
       {            
            return digest( LdapSecurityConstants.HASH_METHOD_MD5, credentials, salt );
        }
        else if ( LdapSecurityConstants.HASH_METHOD_CRYPT.equals( algorithm ) )
        {
            if ( salt == null )
            {
                salt = new byte[2];
                SecureRandom sr = new SecureRandom();
                int i1 = sr.nextInt( 64 );
                int i2 = sr.nextInt( 64 );
            
                salt[0] = ( byte ) ( i1 < 12 ? ( i1 + '.' ) : i1 < 38 ? ( i1 + 'A' - 12 ) : ( i1 + 'a' - 38 ) );
                salt[1] = ( byte ) ( i2 < 12 ? ( i2 + '.' ) : i2 < 38 ? ( i2 + 'A' - 12 ) : ( i2 + 'a' - 38 ) );
            }

            String saltWithCrypted = UnixCrypt.crypt( StringTools.utf8ToString( credentials ), StringTools.utf8ToString( salt ) );
            String crypted = saltWithCrypted.substring( 2 );
            
            return StringTools.getBytesUtf8( crypted );
        }
        else
        {
            return credentials;
        }
    }

    /**
     * Local function which request the password from the backend
     */
    private byte[] lookupUserPassword( LdapDN principalDn ) throws NamingException
    {
        // ---- lookup the principal entry's userPassword attribute
        Invocation invocation = InvocationStack.getInstance().peek();
        PartitionNexusProxy proxy = invocation.getProxy();
        Attributes userEntry;

        try
        {
            LookupOperationContext lookupContex  = new LookupOperationContext( new String[] { SchemaConstants.USER_PASSWORD_AT } );
            lookupContex.setDn( principalDn );
            
            userEntry = proxy.lookup( lookupContex, USERLOOKUP_BYPASS );

            if ( userEntry == null )
            {
                throw new LdapAuthenticationException( "Failed to lookup user for authentication: " + principalDn );
            }
        }
        catch ( Exception cause )
        {
            log.error( "Authentication error : " + cause.getMessage() );
            LdapAuthenticationException e = new LdapAuthenticationException();
            e.setRootCause( e );
            throw e;
        }

        Object userPassword;

        Attribute userPasswordAttr = userEntry.get( SchemaConstants.USER_PASSWORD_AT );

        // ---- assert that credentials match
        if ( userPasswordAttr == null )
        {
            userPassword = ArrayUtils.EMPTY_BYTE_ARRAY;
        }
        else
        {
            userPassword = userPasswordAttr.get();

            if ( userPassword instanceof String )
            {
                userPassword = StringTools.getBytesUtf8( ( String ) userPassword );
            }
        }
        
        return ( byte[] ) userPassword;
    }

    /**
     * Get the algorithm of a password, which is stored in the form "{XYZ}...".
     * The method returns null, if the argument is not in this form. It returns
     * XYZ, if XYZ is an algorithm known to the MessageDigest class of
     * java.security.
     * 
     * @param password a byte[]
     * @return included message digest alorithm, if any
     */
    protected String getAlgorithmForHashedPassword( byte[] password ) throws IllegalArgumentException
    {
        String result = null;

        // Check if password arg is string or byte[]
        String sPassword = StringTools.utf8ToString( password );
        int rightParen = sPassword.indexOf( '}' );

        if ( ( sPassword != null ) && 
             ( sPassword.length() > 2 ) && 
             ( sPassword.charAt( 0 ) == '{' ) &&
             ( rightParen > -1 ) )
        {
            String algorithm = sPassword.substring( 1, rightParen );

            if ( "crypt".equals( algorithm ) )
            {
                return algorithm;
            }
            
            try
            {
                MessageDigest.getInstance( algorithm );
                result = algorithm;
            }
            catch ( NoSuchAlgorithmException e )
            {
                log.warn( "Unknown message digest algorithm in password: " + algorithm, e );
            }
        }

        return result;
    }


    /**
     * Creates a digested password. For a given hash algorithm and a password
     * value, the algorithm is applied to the password, and the result is Base64
     * encoded. The method returns a String which looks like "{XYZ}bbbbbbb",
     * whereas XYZ is the name of the algorithm, and bbbbbbb is the Base64
     * encoded value of XYZ applied to the password.
     * 
     * @param algorithm
     *            an algorithm which is supported by
     *            java.security.MessageDigest, e.g. SHA
     * @param password
     *            password value, a byte[]
     * 
     * @return a digested password, which looks like
     *         {SHA}LhkDrSoM6qr0fW6hzlfOJQW61tc=
     * 
     * @throws IllegalArgumentException
     *             if password is neither a String nor a byte[], or algorithm is
     *             not known to java.security.MessageDigest class
     */
    protected String createDigestedPassword( String algorithm, byte[] password ) throws IllegalArgumentException
    {
        // create message digest object
        try
        {
            if ( "crypt".equalsIgnoreCase( algorithm ) )
            {
                String saltWithCrypted = UnixCrypt.crypt( StringTools.utf8ToString( password ), "" );
                String crypted = saltWithCrypted.substring( 2 );
                return '{' + algorithm + '}' + StringTools.getBytesUtf8( crypted );
            }
            else
            {
                MessageDigest digest = MessageDigest.getInstance( algorithm );
                
                // calculate hashed value of password
                byte[] fingerPrint = digest.digest( password );
                char[] encoded = Base64.encode( fingerPrint );

                // create return result of form "{alg}bbbbbbb"
                return '{' + algorithm + '}' + new String( encoded );
            }
        }
        catch ( NoSuchAlgorithmException nsae )
        {
            log.error( "Cannot create a digested password for algorithm '{}'", algorithm );
            throw new IllegalArgumentException( nsae.getMessage() );
        }
    }

    /**
     * Remove the principal form the cache. This is used when the user changes
     * his password.
     */
    public void invalidateCache( LdapDN bindDn )
    {
        synchronized( credentialCache )
        {
            credentialCache.remove( bindDn.getNormName() );
        }
    }
}

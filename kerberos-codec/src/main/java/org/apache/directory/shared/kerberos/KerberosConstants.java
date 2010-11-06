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
package org.apache.directory.shared.kerberos;

/**
 * An cass to define Kerberos constants
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class KerberosConstants
{
    /** The Kerberos version 5 */
    public static final int KERBEROS_V5 = 5;
    
    /** Ticket message's tags */
    public static final int TICKET_TAG = 0x61;
    public static final int TICKET_TKT_VNO_TAG = 0xA0;
    public static final int TICKET_REALM_TAG = 0xA1;
    public static final int TICKET_SNAME_TAG = 0xA2;
    public static final int TICKET_ENC_PART_TAG = 0xA3;
    
    /** PrincipalName's tags */
    public static final int PRINCIPAL_NAME_NAME_TYPE_TAG = 0xA0;
    public static final int PRINCIPAL_NAME_NAME_STRING_TAG = 0xA1;
    
    /** Encrypteddata's tags */
    public static final int ENCRYPTED_DATA_ETYPE_TAG = 0xA0;
    public static final int ENCRYPTED_DATA_KVNO_TAG = 0xA1;
    public static final int ENCRYPTED_DATA_CIPHER_TAG = 0xA2;
}

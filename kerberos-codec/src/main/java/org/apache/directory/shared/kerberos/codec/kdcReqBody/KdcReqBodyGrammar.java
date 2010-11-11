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
package org.apache.directory.shared.kerberos.codec.kdcReqBody;


import org.apache.directory.shared.asn1.ber.grammar.AbstractGrammar;
import org.apache.directory.shared.asn1.ber.grammar.Grammar;
import org.apache.directory.shared.asn1.ber.grammar.GrammarTransition;
import org.apache.directory.shared.asn1.ber.tlv.UniversalTag;
import org.apache.directory.shared.kerberos.KerberosConstants;
import org.apache.directory.shared.kerberos.codec.actions.CheckNotNullLength;
import org.apache.directory.shared.kerberos.codec.kdcReqBody.actions.KdcReqBodyInit;
import org.apache.directory.shared.kerberos.codec.kdcReqBody.actions.StoreCName;
import org.apache.directory.shared.kerberos.codec.kdcReqBody.actions.StoreFrom;
import org.apache.directory.shared.kerberos.codec.kdcReqBody.actions.StoreKdcOptions;
import org.apache.directory.shared.kerberos.codec.kdcReqBody.actions.StoreRealm;
import org.apache.directory.shared.kerberos.codec.kdcReqBody.actions.StoreSName;
import org.apache.directory.shared.kerberos.codec.kdcReqBody.actions.StoreTill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This class implements the KdcReqBody structure. All the actions are declared
 * in this class. As it is a singleton, these declaration are only done once. If
 * an action is to be added or modified, this is where the work is to be done !
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public final class KdcReqBodyGrammar extends AbstractGrammar
{
    /** The logger */
    static final Logger LOG = LoggerFactory.getLogger( KdcReqBodyGrammar.class );

    /** A speedup for logger */
    static final boolean IS_DEBUG = LOG.isDebugEnabled();

    /** The instance of grammar. KdcReqBodyGrammar is a singleton */
    private static Grammar instance = new KdcReqBodyGrammar();


    /**
     * Creates a new KdcReqBodyGrammar object.
     */
    private KdcReqBodyGrammar()
    {
        setName( KdcReqBodyGrammar.class.getName() );

        // Create the transitions table
        super.transitions = new GrammarTransition[KdcReqBodyStatesEnum.LAST_KDC_REQ_BODY_STATE.ordinal()][256];

        // ============================================================================================
        // KdcReqBody 
        // ============================================================================================
        // --------------------------------------------------------------------------------------------
        // Transition from KdcReqBody init to KdcReqBody SEQ
        // --------------------------------------------------------------------------------------------
        // KDC-REQ-BODY    ::= SEQUENCE {
        super.transitions[KdcReqBodyStatesEnum.START_STATE.ordinal()][UniversalTag.SEQUENCE.getValue()] = new GrammarTransition(
            KdcReqBodyStatesEnum.START_STATE, KdcReqBodyStatesEnum.KDC_REQ_BODY_KDC_OPTIONS_TAG_STATE, UniversalTag.SEQUENCE.getValue(),
            new KdcReqBodyInit() );
        
        // --------------------------------------------------------------------------------------------
        // Transition from KdcReqBody SEQ to kdc-options tag
        // --------------------------------------------------------------------------------------------
        // KDC-REQ-BODY    ::= SEQUENCE {
        //         kdc-options             [0]
        super.transitions[KdcReqBodyStatesEnum.KDC_REQ_BODY_KDC_OPTIONS_TAG_STATE.ordinal()][KerberosConstants.KDC_REQ_BODY_KDC_OPTIONS_TAG] = new GrammarTransition(
            KdcReqBodyStatesEnum.KDC_REQ_BODY_KDC_OPTIONS_TAG_STATE, KdcReqBodyStatesEnum.KDC_REQ_BODY_KDC_OPTIONS_STATE, KerberosConstants.KDC_REQ_BODY_KDC_OPTIONS_TAG,
            new CheckNotNullLength() );
        
        // --------------------------------------------------------------------------------------------
        // Transition from kdc-options tag to kdc-options value
        // --------------------------------------------------------------------------------------------
        // KDC-REQ-BODY    ::= SEQUENCE {
        //         kdc-options             [0] KDCOptions
        super.transitions[KdcReqBodyStatesEnum.KDC_REQ_BODY_KDC_OPTIONS_STATE.ordinal()][UniversalTag.BIT_STRING.getValue()] = new GrammarTransition(
            KdcReqBodyStatesEnum.KDC_REQ_BODY_KDC_OPTIONS_STATE, KdcReqBodyStatesEnum.KDC_REQ_BODY_CNAME_TAG_STATE, UniversalTag.BIT_STRING.getValue(),
            new StoreKdcOptions() );
        
        // --------------------------------------------------------------------------------------------
        // Transition from kdc-options value to cname tag
        // --------------------------------------------------------------------------------------------
        // KDC-REQ-BODY    ::= SEQUENCE {
        //         ...
        //         cname                   [1]
        super.transitions[KdcReqBodyStatesEnum.KDC_REQ_BODY_CNAME_TAG_STATE.ordinal()][KerberosConstants.KDC_REQ_BODY_CNAME_TAG] = new GrammarTransition(
            KdcReqBodyStatesEnum.KDC_REQ_BODY_CNAME_TAG_STATE, KdcReqBodyStatesEnum.KDC_REQ_BODY_REALM_TAG_STATE, KerberosConstants.KDC_REQ_BODY_CNAME_TAG,
            new StoreCName() );
        
        // --------------------------------------------------------------------------------------------
        // Transition from kdc-options value to realm tag (cname is empty)
        // --------------------------------------------------------------------------------------------
        // KDC-REQ-BODY    ::= SEQUENCE {
        //         ...
        //         realm                   [2]
        super.transitions[KdcReqBodyStatesEnum.KDC_REQ_BODY_CNAME_TAG_STATE.ordinal()][KerberosConstants.KDC_REQ_BODY_REALM_TAG] = new GrammarTransition(
            KdcReqBodyStatesEnum.KDC_REQ_BODY_CNAME_TAG_STATE, KdcReqBodyStatesEnum.KDC_REQ_BODY_REALM_TAG_STATE, KerberosConstants.KDC_REQ_BODY_REALM_TAG,
            new CheckNotNullLength() );
        
        // --------------------------------------------------------------------------------------------
        // Transition from cname tag to realm tag
        // --------------------------------------------------------------------------------------------
        // KDC-REQ-BODY    ::= SEQUENCE {
        //         ...
        //         realm                   [2]
        super.transitions[KdcReqBodyStatesEnum.KDC_REQ_BODY_REALM_TAG_STATE.ordinal()][KerberosConstants.KDC_REQ_BODY_REALM_TAG] = new GrammarTransition(
            KdcReqBodyStatesEnum.KDC_REQ_BODY_REALM_TAG_STATE, KdcReqBodyStatesEnum.KDC_REQ_BODY_REALM_STATE, KerberosConstants.KDC_REQ_BODY_REALM_TAG,
            new CheckNotNullLength() );
        
        // --------------------------------------------------------------------------------------------
        // Transition from realm tag to realm value
        // --------------------------------------------------------------------------------------------
        // KDC-REQ-BODY    ::= SEQUENCE {
        //         ...
        //         realm                   [2] Realm
        super.transitions[KdcReqBodyStatesEnum.KDC_REQ_BODY_REALM_STATE.ordinal()][UniversalTag.GENERAL_STRING.getValue()] = new GrammarTransition(
            KdcReqBodyStatesEnum.KDC_REQ_BODY_REALM_STATE, KdcReqBodyStatesEnum.KDC_REQ_BODY_SNAME_TAG_STATE, UniversalTag.GENERAL_STRING.getValue(),
            new StoreRealm() );
        
        // --------------------------------------------------------------------------------------------
        // Transition from realm value to sname tag
        // --------------------------------------------------------------------------------------------
        // KDC-REQ-BODY    ::= SEQUENCE {
        //         ...
        //         sname                   [3]
        super.transitions[KdcReqBodyStatesEnum.KDC_REQ_BODY_SNAME_TAG_STATE.ordinal()][KerberosConstants.KDC_REQ_BODY_SNAME_TAG] = new GrammarTransition(
            KdcReqBodyStatesEnum.KDC_REQ_BODY_SNAME_TAG_STATE, KdcReqBodyStatesEnum.KDC_REQ_BODY_FROM_TAG_STATE, KerberosConstants.KDC_REQ_BODY_SNAME_TAG,
            new StoreSName() );

        // --------------------------------------------------------------------------------------------
        // Transition from sname tag to from tag
        // --------------------------------------------------------------------------------------------
        // KDC-REQ-BODY    ::= SEQUENCE {
        //         ...
        //         from                    [4]
        super.transitions[KdcReqBodyStatesEnum.KDC_REQ_BODY_FROM_TAG_STATE.ordinal()][KerberosConstants.KDC_REQ_BODY_FROM_TAG] = new GrammarTransition(
            KdcReqBodyStatesEnum.KDC_REQ_BODY_FROM_TAG_STATE, KdcReqBodyStatesEnum.KDC_REQ_BODY_FROM_STATE, KerberosConstants.KDC_REQ_BODY_FROM_TAG,
            new CheckNotNullLength() );
        
        // --------------------------------------------------------------------------------------------
        // Transition from sname tag to till tag (from is empty)
        // --------------------------------------------------------------------------------------------
        // KDC-REQ-BODY    ::= SEQUENCE {
        //         ...
        //         till                    [5]
        super.transitions[KdcReqBodyStatesEnum.KDC_REQ_BODY_FROM_TAG_STATE.ordinal()][KerberosConstants.KDC_REQ_BODY_TILL_TAG] = new GrammarTransition(
            KdcReqBodyStatesEnum.KDC_REQ_BODY_FROM_TAG_STATE, KdcReqBodyStatesEnum.KDC_REQ_BODY_TILL_STATE, KerberosConstants.KDC_REQ_BODY_TILL_TAG,
            new CheckNotNullLength() );
        
        // --------------------------------------------------------------------------------------------
        // Transition from realm value to from tag (sname is empty)
        // --------------------------------------------------------------------------------------------
        // KDC-REQ-BODY    ::= SEQUENCE {
        //         ...
        //         from                    [4]
        super.transitions[KdcReqBodyStatesEnum.KDC_REQ_BODY_SNAME_TAG_STATE.ordinal()][KerberosConstants.KDC_REQ_BODY_FROM_TAG] = new GrammarTransition(
            KdcReqBodyStatesEnum.KDC_REQ_BODY_SNAME_TAG_STATE, KdcReqBodyStatesEnum.KDC_REQ_BODY_FROM_STATE, KerberosConstants.KDC_REQ_BODY_FROM_TAG,
            new CheckNotNullLength() );

        // --------------------------------------------------------------------------------------------
        // Transition from from tag to from value
        // --------------------------------------------------------------------------------------------
        // KDC-REQ-BODY    ::= SEQUENCE {
        //         ...
        //         from                    [4] KerberosTime
        super.transitions[KdcReqBodyStatesEnum.KDC_REQ_BODY_FROM_STATE.ordinal()][UniversalTag.GENERALIZED_TIME.getValue()] = new GrammarTransition(
            KdcReqBodyStatesEnum.KDC_REQ_BODY_FROM_STATE, KdcReqBodyStatesEnum.KDC_REQ_BODY_TILL_TAG_STATE, UniversalTag.GENERALIZED_TIME.getValue(),
            new StoreFrom() );

        // --------------------------------------------------------------------------------------------
        // Transition from realm value to till tag (sname and from are empty)
        // --------------------------------------------------------------------------------------------
        // KDC-REQ-BODY    ::= SEQUENCE {
        //         ...
        //         till                    [4]
        super.transitions[KdcReqBodyStatesEnum.KDC_REQ_BODY_SNAME_TAG_STATE.ordinal()][KerberosConstants.KDC_REQ_BODY_TILL_TAG] = new GrammarTransition(
            KdcReqBodyStatesEnum.KDC_REQ_BODY_SNAME_TAG_STATE, KdcReqBodyStatesEnum.KDC_REQ_BODY_TILL_STATE, KerberosConstants.KDC_REQ_BODY_TILL_TAG,
            new CheckNotNullLength() );

        // --------------------------------------------------------------------------------------------
        // Transition from till tag to till value
        // --------------------------------------------------------------------------------------------
        // KDC-REQ-BODY    ::= SEQUENCE {
        //         ...
        //         till                    [5]
        super.transitions[KdcReqBodyStatesEnum.KDC_REQ_BODY_TILL_TAG_STATE.ordinal()][KerberosConstants.KDC_REQ_BODY_TILL_TAG] = new GrammarTransition(
            KdcReqBodyStatesEnum.KDC_REQ_BODY_TILL_TAG_STATE, KdcReqBodyStatesEnum.KDC_REQ_BODY_TILL_STATE, KerberosConstants.KDC_REQ_BODY_TILL_TAG,
            new CheckNotNullLength() );

        // --------------------------------------------------------------------------------------------
        // Transition from till tag to till value
        // --------------------------------------------------------------------------------------------
        // KDC-REQ-BODY    ::= SEQUENCE {
        //         ...
        //         till                    [5] KerberosTime
        super.transitions[KdcReqBodyStatesEnum.KDC_REQ_BODY_TILL_TAG_STATE.ordinal()][UniversalTag.GENERALIZED_TIME.getValue()] = new GrammarTransition(
            KdcReqBodyStatesEnum.KDC_REQ_BODY_TILL_TAG_STATE, KdcReqBodyStatesEnum.KDC_REQ_BODY_TILL_STATE, UniversalTag.GENERALIZED_TIME.getValue(),
            new StoreTill() );

        // --------------------------------------------------------------------------------------------
        // Transition from till tag to till value
        // --------------------------------------------------------------------------------------------
        // KDC-REQ-BODY    ::= SEQUENCE {
        //         ...
        //         till                    [5] KerberosTime
        super.transitions[KdcReqBodyStatesEnum.KDC_REQ_BODY_TILL_STATE.ordinal()][UniversalTag.GENERALIZED_TIME.getValue()] = new GrammarTransition(
            KdcReqBodyStatesEnum.KDC_REQ_BODY_TILL_STATE, KdcReqBodyStatesEnum.KDC_REQ_BODY_RTIME_STATE, UniversalTag.GENERALIZED_TIME.getValue(),
            new StoreTill() );
}


    // ~ Methods
    // ------------------------------------------------------------------------------------

    /**
     * Get the instance of this grammar
     * 
     * @return An instance on the KDC-REQ-BODY Grammar
     */
    public static Grammar getInstance()
    {
        return instance;
    }
}

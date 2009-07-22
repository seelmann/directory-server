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
package org.apache.directory.server.schema.bootstrap;


import javax.naming.NamingException;

import org.apache.directory.server.schema.registries.Registries;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.schema.Normalizer;
import org.apache.directory.shared.ldap.schema.normalizers.DeepTrimToLowerNormalizer;
import org.apache.directory.shared.ldap.schema.normalizers.NoOpNormalizer;



/**
 * A producer of Normalizer objects for the apachemeta schema.
 * Modified by hand from generated code
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class ApachemetaNormalizerProducer extends AbstractBootstrapProducer
{
    public ApachemetaNormalizerProducer()
    {
        super( ProducerTypeEnum.NORMALIZER_PRODUCER );
    }


    // ------------------------------------------------------------------------
    // BootstrapProducer Methods
    // ------------------------------------------------------------------------


    /**
     * @see BootstrapProducer#produce(Registries, ProducerCallback)
     */
    public void produce( Registries registries, ProducerCallback cb )
        throws NamingException
    {
        Normalizer normalizer = null;
        
        normalizer = new NameOrNumericIdNormalizer( registries.getOidRegistry() );
        cb.schemaObjectProduced( this, "1.3.6.1.4.1.18060.0.4.0.1.0", normalizer );

        normalizer = new NoOpNormalizer();
        cb.schemaObjectProduced( this, "1.3.6.1.4.1.18060.0.4.0.1.1", normalizer );
        
        normalizer = new NoOpNormalizer();
        cb.schemaObjectProduced( this, "1.3.6.1.4.1.18060.0.4.0.1.2", normalizer );
        
        normalizer = new DeepTrimToLowerNormalizer();
        cb.schemaObjectProduced( this, "1.3.6.1.4.1.18060.0.4.0.1.3", normalizer );
        
        normalizer = new DeepTrimToLowerNormalizer();
        cb.schemaObjectProduced( this, "1.3.6.1.4.1.18060.0.4.0.1.4", normalizer );
        
        // For entryUuid
        normalizer = new NoOpNormalizer();
        cb.schemaObjectProduced( this, SchemaConstants.ENTRY_UUID_AT_OID, normalizer );
        
        // For entryCSN
        normalizer = new NoOpNormalizer();
        cb.schemaObjectProduced( this, SchemaConstants.ENTRY_CSN_AT_OID, normalizer );
    }
}

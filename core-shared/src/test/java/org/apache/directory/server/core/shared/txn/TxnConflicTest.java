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
package org.apache.directory.server.core.shared.txn;


import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.directory.server.core.api.log.InvalidLogException;
import org.apache.directory.server.core.api.txn.TxnLogManager;
import org.apache.directory.shared.ldap.model.message.SearchScope;
import org.apache.directory.shared.ldap.model.name.Dn;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;


/**
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class TxnConflicTest
{
    /** Log buffer size : 4096 bytes */
    private int logBufferSize = 1 << 12;

    /** Log File Size : 8192 bytes */
    private long logFileSize = 1 << 13;

    /** log suffix */
    private static String LOG_SUFFIX = "log";

    /** Txn manager */
    private TxnManagerInternal txnManager;

    /** Txn log manager */
    private TxnLogManager txnLogManager;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();


    /**
     * Get the Log folder
     */
    private String getLogFolder() throws IOException
    {
        File newFolder = folder.newFolder( LOG_SUFFIX );
        String file = newFolder.getAbsolutePath();

        return file;
    }


    @Before
    public void setup() throws IOException, InvalidLogException
    {
        try
        {
            // Init the txn manager
            TxnManagerFactory txnManagerFactory = new TxnManagerFactory( getLogFolder(), logBufferSize, logFileSize );
            txnManager = txnManagerFactory.txnManagerInternalInstance();
            txnLogManager = txnManagerFactory.txnLogManagerInstance();
        }
        catch ( Exception e )
        {
            fail();
        }
    }


    @After
    public void teardown() throws IOException
    {
        FileUtils.deleteDirectory( new File( getLogFolder() ) );
    }


    @Test
    public void testExclusiveChangeConflict()
    {
        boolean conflicted;

        try
        {
            Dn dn1 = new Dn( "cn=Test", "ou=department", "dc=example,dc=com" );
            Dn dn2 = new Dn( "gn=Test1", "cn=Test", "ou=department", "dc=example,dc=com" );

            ReadWriteTxn firstTxn;
            ReadWriteTxn checkedTxn;

            txnManager.beginTransaction( false );
            txnLogManager.addWrite( dn1, SearchScope.OBJECT );
            firstTxn = ( ReadWriteTxn ) txnManager.getCurTxn();
            txnManager.commitTransaction();

            txnManager.beginTransaction( false );
            txnLogManager.addWrite( dn1, SearchScope.OBJECT );
            checkedTxn = ( ReadWriteTxn ) txnManager.getCurTxn();

            conflicted = checkedTxn.hasConflict( firstTxn );
            assertTrue( conflicted == true );
            txnManager.commitTransaction();

            txnManager.beginTransaction( false );
            txnLogManager.addRead( dn1, SearchScope.OBJECT );
            checkedTxn = ( ReadWriteTxn ) txnManager.getCurTxn();

            conflicted = checkedTxn.hasConflict( firstTxn );
            assertTrue( conflicted == false );
            txnManager.commitTransaction();

            txnManager.beginTransaction( false );
            txnLogManager.addWrite( dn2, SearchScope.OBJECT );
            checkedTxn = ( ReadWriteTxn ) txnManager.getCurTxn();

            conflicted = checkedTxn.hasConflict( firstTxn );
            assertTrue( conflicted == false );
            txnManager.commitTransaction();
        }
        catch ( Exception e )
        {
            e.printStackTrace();
            fail();
        }
    }


    @Test
    public void testSubtreeChangeConflict()
    {
        boolean conflicted;

        try
        {
            Dn dn1 = new Dn( "cn=Test", "ou=department", "dc=example,dc=com" );
            Dn dn2 = new Dn( "gn=Test1", "cn=Test", "ou=department", "dc=example,dc=com" );
            Dn dn3 = new Dn( "ou=department", "dc=example,dc=com" );

            ReadWriteTxn firstTxn;
            ReadWriteTxn checkedTxn;

            txnManager.beginTransaction( false );
            txnLogManager.addWrite( dn1, SearchScope.SUBTREE );
            firstTxn = ( ReadWriteTxn ) txnManager.getCurTxn();
            txnManager.commitTransaction();

            txnManager.beginTransaction( false );
            txnLogManager.addRead( dn1, SearchScope.OBJECT );
            checkedTxn = ( ReadWriteTxn ) txnManager.getCurTxn();

            conflicted = checkedTxn.hasConflict( firstTxn );
            assertTrue( conflicted == false );
            txnManager.commitTransaction();

            txnManager.beginTransaction( false );
            txnLogManager.addWrite( dn2, SearchScope.OBJECT );
            checkedTxn = ( ReadWriteTxn ) txnManager.getCurTxn();

            conflicted = checkedTxn.hasConflict( firstTxn );
            assertTrue( conflicted == true );
            txnManager.commitTransaction();

            txnManager.beginTransaction( false );
            txnLogManager.addRead( dn1, SearchScope.SUBTREE );
            checkedTxn = ( ReadWriteTxn ) txnManager.getCurTxn();

            conflicted = checkedTxn.hasConflict( firstTxn );
            assertTrue( conflicted == false );
            txnManager.commitTransaction();

            txnManager.beginTransaction( false );
            txnLogManager.addWrite( dn3, SearchScope.OBJECT );
            checkedTxn = ( ReadWriteTxn ) txnManager.getCurTxn();

            conflicted = checkedTxn.hasConflict( firstTxn );
            assertTrue( conflicted == false );
            txnManager.commitTransaction();
        }
        catch ( Exception e )
        {

        }
    }
}

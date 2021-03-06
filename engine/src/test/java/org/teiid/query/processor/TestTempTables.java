/*
 * JBoss, Home of Professional Open Source.
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 */

package org.teiid.query.processor;

import static org.junit.Assert.*;

import java.sql.Connection;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.Transaction;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.teiid.api.exception.query.QueryResolverException;
import org.teiid.cache.DefaultCacheFactory;
import org.teiid.common.buffer.BufferManager;
import org.teiid.common.buffer.BufferManagerFactory;
import org.teiid.core.TeiidProcessingException;
import org.teiid.dqp.internal.process.CachedResults;
import org.teiid.dqp.internal.process.SessionAwareCache;
import org.teiid.dqp.service.TransactionContext;
import org.teiid.dqp.service.TransactionContext.Scope;
import org.teiid.query.metadata.TempMetadataAdapter;
import org.teiid.query.metadata.TempMetadataID;
import org.teiid.query.optimizer.TestOptimizer;
import org.teiid.query.optimizer.TestOptimizer.ComparisonMode;
import org.teiid.query.tempdata.GlobalTableStoreImpl;
import org.teiid.query.tempdata.TempTableDataManager;
import org.teiid.query.unittest.RealMetadataFactory;

@SuppressWarnings({"nls", "unchecked"})
public class TestTempTables extends TempTableTestHarness {
	
	private Transaction txn;
	private Synchronization synch;
	
	@Before public void setUp() {
		FakeDataManager fdm = new FakeDataManager();
	    TestProcessor.sampleData1(fdm);
		this.setUp(RealMetadataFactory.example1Cached(), fdm);
	}
	
	@Test public void testRollbackNoExisting() throws Exception {
		setupTransaction(Connection.TRANSACTION_SERIALIZABLE);
		execute("create local temporary table x (e1 string, e2 integer)", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		execute("insert into x (e2, e1) select e2, e1 from pm1.g1", new List[] {Arrays.asList(6)}); //$NON-NLS-1$
		execute("update x set e1 = e2 where e2 > 1", new List[] {Arrays.asList(2)}); //$NON-NLS-1$
		
		Mockito.verify(txn).registerSynchronization((Synchronization) Mockito.anyObject());
		synch.afterCompletion(Status.STATUS_ROLLEDBACK);
		
		try {
			execute("select * from x", new List[] {});
			fail();
		} catch (Exception e) {
			
		}
		execute("create local temporary table x (e1 string, e2 integer)", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
	}
	
	@Test public void testRollbackExisting() throws Exception {
		execute("create local temporary table x (e1 string, e2 integer)", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		setupTransaction(Connection.TRANSACTION_SERIALIZABLE);
		//execute("create local temporary table x (e1 string, e2 integer)", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		for (int i = 0; i < 86; i++) {
			execute("insert into x (e2, e1) select e2, e1 from pm1.g1", new List[] {Arrays.asList(6)}); //$NON-NLS-1$
		}
		execute("update x set e1 = e2 where e2 > 1", new List[] {Arrays.asList(172)}); //$NON-NLS-1$
		
		synch.afterCompletion(Status.STATUS_ROLLEDBACK);

		execute("select * from x", new List[] {});
	}
	
	@Test public void testCommitExistingRemoved() throws Exception {
		execute("create local temporary table x (e1 string, e2 integer)", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		setupTransaction(Connection.TRANSACTION_SERIALIZABLE);
		execute("drop table x", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		synch.afterCompletion(Status.STATUS_COMMITTED);
		try {
			execute("select * from x", new List[] {});
			fail();
		} catch (Exception e) {
			
		}
	}
	
	@Test public void testUpdateLock() throws Exception {
		execute("create local temporary table x (e1 string, e2 integer)", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		setupTransaction(Connection.TRANSACTION_SERIALIZABLE);
		execute("insert into x (e2, e1) select e2, e1 from pm1.g1", new List[] {Arrays.asList(6)}); //$NON-NLS-1$
		tc = null;
		try {
			execute("insert into x (e2, e1) select e2, e1 from pm1.g1", new List[] {Arrays.asList(6)}); //$NON-NLS-1$
			fail();
		} catch (Exception e) {
			
		}
		synch.afterCompletion(Status.STATUS_COMMITTED);
		execute("insert into x (e2, e1) select e2, e1 from pm1.g1", new List[] {Arrays.asList(6)}); //$NON-NLS-1$
	}
	
	@Test public void testRollbackExisting1() throws Exception {
		execute("create local temporary table x (e1 string, e2 integer)", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		for (int i = 0; i < 86; i++) {
			execute("insert into x (e2, e1) select e2, e1 from pm1.g1", new List[] {Arrays.asList(6)}); //$NON-NLS-1$
		}
		setupTransaction(Connection.TRANSACTION_SERIALIZABLE);
		//execute("create local temporary table x (e1 string, e2 integer)", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		for (int i = 0; i < 86; i++) {
			execute("insert into x (e2, e1) select e2, e1 from pm1.g1", new List[] {Arrays.asList(6)}); //$NON-NLS-1$
		}
		execute("update x set e1 = e2 where e2 > 1", new List[] {Arrays.asList(344)}); //$NON-NLS-1$
		
		synch.afterCompletion(Status.STATUS_ROLLEDBACK);
		this.tc = null;
		
		execute("select count(*) from x", new List[] {Arrays.asList(516)});
		
		execute("delete from x", new List[] {Arrays.asList(516)});
	}
	
	@Test public void testIsolateReads() throws Exception {
		GlobalTableStoreImpl gtsi = new GlobalTableStoreImpl(BufferManagerFactory.getStandaloneBufferManager(), RealMetadataFactory.example1Cached().getVdbMetaData(), RealMetadataFactory.example1Cached());
		tempStore = gtsi.getTempTableStore();
		metadata = new TempMetadataAdapter(RealMetadataFactory.example1Cached(), tempStore.getMetadataStore());
		execute("create local temporary table x (e1 string, e2 integer)", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		for (int i = 0; i < 300; i++) {
			execute("insert into x (e2, e1) select e2, e1 from pm1.g1", new List[] {Arrays.asList(6)}); //$NON-NLS-1$
		}
		setupTransaction(Connection.TRANSACTION_SERIALIZABLE);
		execute("select count(e1) from x", new List[] {Arrays.asList(1500)});
		gtsi.updateMatViewRow("X", Arrays.asList(2), true);
		tc=null;
		//outside of the transaction we can see the row removed
		execute("select count(e1) from x", new List[] {Arrays.asList(1499)});
		
		//back in the transaction we see the original state
		setupTransaction(Connection.TRANSACTION_SERIALIZABLE);
		execute("select count(e1) from x", new List[] {Arrays.asList(1500)});
		
		synch.afterCompletion(Status.STATUS_COMMITTED);
	}

	private void setupTransaction(int isolation) throws RollbackException, SystemException {
		txn = Mockito.mock(Transaction.class);
		Mockito.doAnswer(new Answer<Void>() {
			@Override
			public Void answer(InvocationOnMock invocation) throws Throwable {
				synch = (Synchronization)invocation.getArguments()[0];
				return null;
			}
		}).when(txn).registerSynchronization((Synchronization)Mockito.anyObject());
		Mockito.stub(txn.toString()).toReturn("txn");
		tc = new TransactionContext();
		tc.setTransaction(txn);
		tc.setIsolationLevel(isolation);
		tc.setTransactionType(Scope.REQUEST);
	}
	
	@Test public void testInsertWithQueryExpression() throws Exception {
		execute("create local temporary table x (e1 string, e2 integer)", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		execute("insert into x (e2, e1) select e2, e1 from pm1.g1", new List[] {Arrays.asList(6)}); //$NON-NLS-1$
		execute("update x set e1 = e2 where e2 > 1", new List[] {Arrays.asList(2)}); //$NON-NLS-1$
	}
	
	@Test public void testOutofOrderInsert() throws Exception {
		execute("create local temporary table x (e1 string, e2 integer)", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		execute("insert into x (e2, e1) values (1, 'one')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("select e1, e2 from x", new List[] {Arrays.asList("one", 1)}); //$NON-NLS-1$
	}
	
	@Test public void testUpdate() throws Exception {
		execute("create local temporary table x (e1 string, e2 integer)", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		execute("insert into x (e2, e1) values (1, 'one')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("select e1, e2 into x from pm1.g1", new List[] {Arrays.asList(6)}); //$NON-NLS-1$
		execute("update x set e1 = e2 where e2 > 1", new List[] {Arrays.asList(2)}); //$NON-NLS-1$
		execute("select e1 from x where e2 > 0 order by e1", new List[] { //$NON-NLS-1$
				Arrays.asList((String)null),
				Arrays.asList("2"), //$NON-NLS-1$
				Arrays.asList("3"), //$NON-NLS-1$
				Arrays.asList("c"), //$NON-NLS-1$
				Arrays.asList("one")}); //$NON-NLS-1$
	}
	
	@Test public void testDelete() throws Exception {
		execute("create local temporary table x (e1 string, e2 integer)", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		execute("select e1, e2 into x from pm1.g1", new List[] {Arrays.asList(6)}); //$NON-NLS-1$
		execute("delete from x where ascii(e1) > e2", new List[] {Arrays.asList(5)}); //$NON-NLS-1$
		execute("select e1 from x order by e1", new List[] {Arrays.asList((String)null)}); //$NON-NLS-1$
	}
	
	@Test public void testDelete1() throws Exception {
		execute("create local temporary table x (e1 string, e2 integer)", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		execute("select e1, e2 into x from pm1.g1", new List[] {Arrays.asList(6)}); //$NON-NLS-1$
		execute("delete from x", new List[] {Arrays.asList(6)}); //$NON-NLS-1$
		execute("select e1 from x order by e1", new List[] {}); //$NON-NLS-1$
	}
	
	@Test(expected=TeiidProcessingException.class) public void testDuplicatePrimaryKey() throws Exception {
		execute("create local temporary table x (e1 string, e2 integer, primary key (e2))", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		execute("insert into x (e2, e1) values (1, 'one')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("insert into x (e2, e1) values (1, 'one')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
	}
	
	@Test public void testAtomicUpdate() throws Exception {
		execute("create local temporary table x (e1 string, e2 integer, primary key (e2))", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		execute("insert into x (e2, e1) values (1, 'one')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("insert into x (e2, e1) values (2, 'one')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		try {
			execute("update x set e2 = 3", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		} catch (TeiidProcessingException e) {
			//should be a duplicate key
		}
		//should revert back to original
		execute("select count(*) from x", new List[] {Arrays.asList(2)}); //$NON-NLS-1$
		
		Thread t = new Thread() {
			public void run() {
				try {
					execute("select count(e1) from x", new List[] {Arrays.asList(2)});
				} catch (Exception e) {
					e.printStackTrace();
				} 
			}
		};
		t.start();
		t.join(2000);
		assertFalse(t.isAlive());
	}
	
	@Test public void testAtomicDelete() throws Exception {
		execute("create local temporary table x (e1 string, e2 integer, primary key (e2))", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		execute("insert into x (e2, e1) values (1, 'one')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("insert into x (e2, e1) values (2, 'one')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		try {
			execute("delete from x where 1/(e2 - 2) <> 4", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		} catch (TeiidProcessingException e) {
			//should be a duplicate key
		}
		//should revert back to original
		execute("select count(*) from x", new List[] {Arrays.asList(2)}); //$NON-NLS-1$
	}
	
	@Test public void testPrimaryKeyMetadata() throws Exception {
		execute("create local temporary table x (e1 string, e2 integer, primary key (e2))", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		Collection<?> c = metadata.getUniqueKeysInGroup(metadata.getGroupID("x"));
		assertEquals(1, c.size());
		assertEquals(1, (metadata.getElementIDsInKey(c.iterator().next()).size()));
	}
	
	@Test public void testProjection() throws Exception {
		execute("create local temporary table x (e1 string, e2 integer, primary key (e2))", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		execute("insert into x (e2, e1) values (1, 'one')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("select * from x where e2 = 1", new List[] {Arrays.asList("one", 1)}); //$NON-NLS-1$
		execute("select e2, e1 from x where e2 = 1", new List[] {Arrays.asList(1, "one")}); //$NON-NLS-1$
	}
	
	@Test public void testOrderByWithIndex() throws Exception {
		execute("create local temporary table x (e1 string, e2 integer, primary key (e2))", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		execute("insert into x (e2, e1) values (3, 'one')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("insert into x (e2, e1) values (2, 'one')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("select * from x as y order by e2 desc", new List[] {Arrays.asList("one", 3), Arrays.asList("one", 2)}); //$NON-NLS-1$
		execute("select * from x as y where e2 in (2, 3) order by e2 desc", new List[] {Arrays.asList("one", 3), Arrays.asList("one", 2)}); //$NON-NLS-1$
		execute("select * from x as y where e2 in (3, 2) order by e2", new List[] {Arrays.asList("one", 2), Arrays.asList("one", 3)}); //$NON-NLS-1$
		execute("select * from x as y where e2 in (3, 2) order by e2 desc", new List[] {Arrays.asList("one", 3), Arrays.asList("one", 2)}); //$NON-NLS-1$
	}
	
	@Test public void testOrderByWithoutIndex() throws Exception {
		execute("create local temporary table x (e1 string, e2 integer, primary key (e2))", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		execute("insert into x (e2, e1) values (3, 'a')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("insert into x (e2, e1) values (2, 'b')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("select * from x order by e1", new List[] {Arrays.asList("a", 3), Arrays.asList("b", 2)}); //$NON-NLS-1$
	}
	
	@Test public void testCompareEqualsWithIndex() throws Exception {
		execute("create local temporary table x (e1 string, e2 integer, primary key (e2))", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		execute("insert into x (e2, e1) values (3, 'a')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("insert into x (e2, e1) values (2, 'b')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("select * from x where e2 = 3", new List[] {Arrays.asList("a", 3)}); //$NON-NLS-1$
	}
	
	@Test public void testLikeWithIndex() throws Exception {
		execute("create local temporary table x (e1 string, e2 integer, primary key (e1))", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		execute("insert into x (e2, e1) values (3, 'a')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("insert into x (e2, e1) values (2, 'b')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("select * from x where e1 like 'z%'", new List[0]); //$NON-NLS-1$
	}
	
	@Test public void testLikeRegexWithIndex() throws Exception {
		execute("create local temporary table x (e1 string, e2 integer, primary key (e1))", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		execute("insert into x (e2, e1) values (3, 'ab')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("insert into x (e2, e1) values (2, 'b')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("select * from x where e1 like_regex '^b?.*'", new List[] {Arrays.asList("ab", 3), Arrays.asList("b", 2)}); //$NON-NLS-1$
		execute("select * from x where e1 like_regex '^ab+.*'", new List[] {Arrays.asList("ab", 3)}); //$NON-NLS-1$
		execute("select * from x where e1 like_regex '^ab|b'", new List[] {Arrays.asList("ab", 3), Arrays.asList("b", 2)}); //$NON-NLS-1$
	}
	
	@Test public void testIsNullWithIndex() throws Exception {
		execute("create local temporary table x (e1 string, e2 integer, primary key (e1))", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		execute("insert into x (e2, e1) values (3, null)", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("insert into x (e2, e1) values (2, 'b')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("select * from x where e1 is null", new List[] {Arrays.asList(null, 3)}); //$NON-NLS-1$
	}
	
	@Test public void testInWithIndex() throws Exception {
		execute("create local temporary table x (e1 string, e2 integer, primary key (e1))", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		execute("insert into x (e2, e1) values (3, 'a')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("insert into x (e2, e1) values (2, 'b')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("insert into x (e2, e1) values (1, 'c')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("insert into x (e2, e1) values (0, 'd')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("insert into x (e2, e1) values (-1, 'e')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("select * from x where e1 in ('a', 'c', 'e', 'f', 'g')", new List[] {Arrays.asList("a", 3), Arrays.asList("c", 1), Arrays.asList("e", -1)}); //$NON-NLS-1$
	}
	
	@Test public void testInWithIndexUpdate() throws Exception {
		execute("create local temporary table x (e1 string, e2 integer, e3 string, primary key (e1))", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		execute("insert into x (e2, e1) values (3, 'a')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("insert into x (e2, e1) values (2, 'b')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("insert into x (e2, e1) values (1, 'c')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("insert into x (e2, e1) values (0, 'd')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("insert into x (e2, e1, e3) values (-1, 'e', 'e')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("update x set e2 = 5 where e1 in ('a', 'c')", new List[] {Arrays.asList(2)}); //$NON-NLS-1$
		execute("select * from x where e1 in ('b', e3)", new List[] {Arrays.asList("b", 2, null), Arrays.asList("e", -1, "e")}); //$NON-NLS-1$
	}
	
	@Test public void testCompositeKeyCompareEquals() throws Exception {
		execute("create local temporary table x (e1 string, e2 integer, primary key (e1, e2))", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		execute("insert into x (e2, e1) values (3, 'a')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("insert into x (e2, e1) values (2, 'b')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("insert into x (e2, e1) values (1, 'c')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("select * from x where e1 = 'b' and e2 = 2", new List[] {Arrays.asList("b", 2)}); //$NON-NLS-1$
	}
	
	@Test public void testCompositeKeyPartial() throws Exception {
		sampleTable();
		execute("select * from x where e1 = 'b'", new List[] {Arrays.asList("b", 2), Arrays.asList("b", 3)}); //$NON-NLS-1$
	}
	
	@Test public void testCompositeKeyPartial1() throws Exception {
		sampleTable();
		execute("select * from x where e1 < 'c'", new List[] {Arrays.asList("a", 1), Arrays.asList("b", 2), Arrays.asList("b", 3)}); //$NON-NLS-1$
	}
	
	@Test public void testCompositeKeyPartial2() throws Exception {
		sampleTable();
		execute("select * from x where e2 = 1", new List[] {Arrays.asList("a", 1), Arrays.asList("c", 1)}); //$NON-NLS-1$
	}
	
	@Test public void testCompositeKeyPartial3() throws Exception {
		sampleTable();
		execute("select * from x where e1 >= 'b'", new List[] {Arrays.asList("b", 2), Arrays.asList("b", 3), Arrays.asList("c", 1)}); //$NON-NLS-1$
	}
	
	@Test public void testCompositeKeyPartial4() throws Exception {
		sampleTable();
		execute("select * from x where e1 >= 'b' order by e1 desc, e2 desc", new List[] {Arrays.asList("c", 1), Arrays.asList("b", 3), Arrays.asList("b", 2)}); //$NON-NLS-1$
	}
	
	@Test public void testCompositeKeyPartial5() throws Exception {
		sampleTable();
		execute("select * from x where e1 in ('a', 'b')", new List[] {Arrays.asList("a", 1), Arrays.asList("b", 2), Arrays.asList("b", 3)}); //$NON-NLS-1$
	}
	
	@Test public void testCompositeKeyPartial6() throws Exception {
		execute("create local temporary table x (e1 string, e2 integer, primary key (e1, e2))", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		execute("select * from x where e1 in ('a', 'b') order by e1 desc", new List[0]); //$NON-NLS-1$
	}
	
	@Test public void testCountStar() throws Exception {
		sampleTable();
		execute("select count(*) a from x", new List[] {Arrays.asList(4)});
		execute("select count(*) a from x where e2 = 1 order by a", new List[] {Arrays.asList(2)});
	}
	
	@Test public void testAutoIncrement() throws Exception {
		execute("create local temporary table x (e1 serial, e2 integer, primary key (e1))", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		execute("insert into x (e2) values (1)", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("insert into x (e2) values (3)", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("select * from x", new List[] {Arrays.asList(1, 1), Arrays.asList(2, 3)});
	}
	
	@Test public void testAutoIncrement1() throws Exception {
		execute("create local temporary table x (e1 serial, e2 integer)", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		execute("insert into x (e2) values (1)", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("insert into x (e2) values (3)", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("select * from x", new List[] {Arrays.asList(1, 1), Arrays.asList(2, 3)});
	}
	
	@Test(expected=TeiidProcessingException.class) public void testNotNull() throws Exception {
		execute("create local temporary table x (e1 serial, e2 integer not null, primary key (e1))", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		execute("insert into x (e1, e2) values ((select null), 1)", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
	}
	
	@Test(expected=TeiidProcessingException.class) public void testNotNull1() throws Exception {
		execute("create local temporary table x (e1 serial, e2 integer not null, primary key (e1))", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		execute("insert into x (e2) values ((select null))", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
	}
	
	/**
	 * If the session metadata is still visible, then the procedure will fail due to the conflicting
	 * definitions of temp_table
	 */
	@Test public void testSessionResolving() throws Exception {
		execute("create local temporary table temp_table (column1 integer)", new List[] {Arrays.asList(0)});
		execute("exec pm1.vsp60()", new List[] {Arrays.asList("First"), Arrays.asList("Second"), Arrays.asList("Third")});
	}
	
	/**
	 * Note that the order by reflects the key order, not the order in which the criteria was entered
	 */
	@Test public void testCompositeKeyJoinUsesKeyOrder() throws Exception {
		execute("create local temporary table x (e1 string, e2 integer, primary key (e1, e2))", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		execute("create local temporary table x1 (e1 string, e2 integer)", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		TestOptimizer.helpPlan("select * from x, x1 where x.e2 = x1.e2 and x.e1 = x1.e1", this.metadata, new String[] {"SELECT x1.e2, x1.e1 FROM x1 ORDER BY x1.e1, x1.e2", "SELECT x.e2, x.e1 FROM x ORDER BY x.e1, x.e2"}, ComparisonMode.EXACT_COMMAND_STRING);
	}
	
	@Test public void testUnneededMergePredicate() throws Exception {
		execute("create local temporary table x (e1 string, e2 integer, primary key (e1))", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		execute("create local temporary table x1 (e1 string, e2 integer)", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		TestOptimizer.helpPlan("select x.e1 from x makenotdep, x1 makenotdep where x.e2 = x1.e2 and x.e1 = x1.e1", this.metadata, new String[] {"SELECT x.e2, x.e1 FROM x ORDER BY x.e1", "SELECT x1.e2, x1.e1 FROM x1 ORDER BY x1.e1"}, ComparisonMode.EXACT_COMMAND_STRING);
		execute("insert into x (e2, e1) values (2, 'b')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("insert into x1 (e2, e1) values (3, 'b')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("select x.e1 from x makenotdep, x1 makenotdep where x.e2 = x1.e2 and x.e1 = x1.e1", new List[0]); //$NON-NLS-1$
		//ensure join is preserved
		execute("select x.e1 from x left outer join x1 on x.e2 = x1.e2 and x.e1 = x1.e1", new List[] {Arrays.asList("b")}); //$NON-NLS-1$
	}
	
	@Test public void testUnneededMergePredicate1() throws Exception {
		execute("create local temporary table x (e1 string, e2 integer, e3 integer, primary key (e1))", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		execute("create local temporary table x1 (e1 string, e2 integer, e3 integer)", new List[] {Arrays.asList(0)}); //$NON-NLS-1$

		execute("insert into x (e3, e2, e1) values (4, 2, '1')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("insert into x (e3, e2, e1) values (4, 2, '2')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("insert into x1 (e3, e2, e1) values (5, 2, '1')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("insert into x1 (e3, e2, e1) values (5, 1, '2')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		//ensure predicates are preserved
		execute("select x.e1 from /*+ makenotdep */ x inner join  /*+ makenotdep */ x1 on x.e1 = x1.e1 and upper(x.e1) = x1.e2", new List[] {Arrays.asList("1"), Arrays.asList("2")}); //$NON-NLS-1$
		execute("select x.e1 from /*+ makenotdep */ x inner join  /*+ makenotdep */ x1 on x.e1 = x1.e1 and upper(x.e1) = x1.e2 and x1.e3 = x.e3", new List[0]); //$NON-NLS-1$
		//ensure there's no bad interaction with makedep
		execute("select x.e1 from /*+ makedep */ x inner join  x1 on x.e1 = x1.e1 and x.e2 = x1.e2", new List[] {Arrays.asList("1")}); //$NON-NLS-1$
	}
	
	private void sampleTable() throws Exception {
		execute("create local temporary table x (e1 string, e2 integer, primary key (e1, e2))", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		execute("insert into x (e2, e1) values (3, 'b')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("insert into x (e2, e1) values (2, 'b')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("insert into x (e2, e1) values (1, 'c')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("insert into x (e2, e1) values (1, 'a')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
	}
	
	@Test public void testForeignTemp() throws Exception {
		HardcodedDataManager hdm = new HardcodedDataManager(metadata);
	    BufferManager bm = BufferManagerFactory.getStandaloneBufferManager();
	    SessionAwareCache<CachedResults> cache = new SessionAwareCache<CachedResults>("resultset", DefaultCacheFactory.INSTANCE, SessionAwareCache.Type.RESULTSET, 0);
	    cache.setTupleBufferCache(bm);
		dataManager = new TempTableDataManager(hdm, bm, cache);
		
		execute("create foreign temporary table x (e1 string options (nameinsource 'a'), e2 integer, e3 string, primary key (e1)) options (cardinality 1000, updatable true) on pm1", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		
		TempMetadataID id = this.tempStore.getMetadataStore().getData().get("x");
		
		//ensure that we're using the actual metadata
		assertNotNull(id);
		assertNotNull(this.metadata.getPrimaryKey(id));
		assertEquals(1000, this.metadata.getCardinality(id), 0);
		assertEquals("pm1", this.metadata.getName(this.metadata.getModelID(id)));
		
		hdm.addData("SELECT x.a, x.e2, x.e3 FROM x", new List[] {Arrays.asList(1, 2, "3")});
		execute("select * from x", new List[] {Arrays.asList(1, 2, "3")}); //$NON-NLS-1$
		
		hdm.addData("SELECT g_0.e2 AS c_0, g_0.e3 AS c_1, g_0.a AS c_2 FROM x AS g_0 ORDER BY c_1, c_0", new List[] {Arrays.asList(2, "3", "1")});
		hdm.addData("SELECT g_0.e3, g_0.e2 FROM x AS g_0", new List[] {Arrays.asList("3", 2)});
		hdm.addData("DELETE FROM x WHERE x.a = '1'", new List[] {Arrays.asList(1)});
		
		//ensure compensation behaves as if physical - not temp
		execute("delete from x where e2 = (select max(e2) from x as z where e3 = x.e3)", new List[] {Arrays.asList(1)}, TestOptimizer.getGenericFinder()); //$NON-NLS-1$

		hdm.addData("SELECT g_0.e1 FROM g1 AS g_0, x AS g_1 WHERE g_1.a = g_0.e1", new List[] {Arrays.asList(1)});

		//ensure pushdown support
		execute("select g1.e1 from pm1.g1 g1, x where x.e1 = g1.e1", new List[] {Arrays.asList(1)}, TestOptimizer.getGenericFinder()); //$NON-NLS-1$
		
		try {
			execute("create local temporary table x (e1 string)", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
			fail();
		} catch (QueryResolverException e) {
			
		}
		
		//ensure that drop works
		execute("drop table x", new List[] {Arrays.asList(0)});
		
		try {
			execute("drop table x", new List[] {Arrays.asList(0)});
			fail();
		} catch (QueryResolverException e) {
			
		}
	}
	
	@Test public void testDependentArrayType() throws Exception {
		execute("create local temporary table x (e1 string, e2 integer, e3 integer, primary key (e2, e3))", new List[] {Arrays.asList(0)}); //$NON-NLS-1$
		execute("create local temporary table x1 (e1 string, e2 integer, e3 integer)", new List[] {Arrays.asList(0)}); //$NON-NLS-1$

		execute("insert into x (e3, e2, e1) values (4, 2, '1')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("insert into x (e3, e2, e1) values (3, 2, '2')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("insert into x1 (e3, e2, e1) values (4, 2, '1')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$
		execute("insert into x1 (e3, e2, e1) values (3, 2, '2')", new List[] {Arrays.asList(1)}); //$NON-NLS-1$

		//single value
		execute("select x.e1 from x inner join /*+ makeind */ x1 on x.e2 = x1.e2 and x.e3 = x1.e3 and x1.e1='1'", new List[] {Arrays.asList("1")}); //$NON-NLS-1$
		
		//multiple values
		execute("select x.e1 from x inner join /*+ makeind */ x1 on x.e2 = x1.e2 and x.e3 = x1.e3", new List[] {Arrays.asList("2"), Arrays.asList("1")}); //$NON-NLS-1$
		
		//multiple out of order values
		execute("select x.e1 from x inner join /*+ makeind */ x1 on x.e3 = x1.e3 and x.e2 = x1.e2", new List[] {Arrays.asList("2"), Arrays.asList("1")}); //$NON-NLS-1$
	}
	
}

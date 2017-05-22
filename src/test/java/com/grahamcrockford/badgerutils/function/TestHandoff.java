/*
 * Copyright (C) 2017 Graham Crockford
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.grahamcrockford.badgerutils.function;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.jooq.BatchBindStep;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.grahamcrockford.badgerutils.async.Assister;
import com.grahamcrockford.badgerutils.async.StaticWorkQueue;
import com.grahamcrockford.badgerutils.async.WorkQueue;
import com.grahamcrockford.badgerutils.base.CheckedExceptions;
import com.grahamcrockford.badgerutils.base.LogExceptions;

import uk.org.lidalia.slf4jext.Level;
import uk.org.lidalia.slf4jtest.TestLoggerFactory;

/**
 * Full-stack test which demonstrates data being read from a database table by
 * a single SELECT and then multicast to two other tables in multiple threads.
 * This is a common ETL pattern to maximise data throughput.
 */
public class TestHandoff {
  
  static {
    TestLoggerFactory.getInstance().setPrintLevel(Level.INFO);
  }
  
  private static final Logger log = LoggerFactory.getLogger(TestHandoff.class);
 
  private static final String DB_DRIVER = "org.h2.Driver";
  private static final String DB_CONNECTION = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MVCC=TRUE";
  private static final String DB_USER = "";
  private static final String DB_PASSWORD = "";
  private static final int RECORD_COUNT = 1000;
  private static final int THREAD_COUNT = 4;

  @Test
  public void testDelegatingETL() throws SQLException, InterruptedException {
    
    // Given a bunch of starting data
    checkDriver();
    try (Connection c = getDBConnection()) {
      setupData(c);
    }

    // When we run the pipeline
    WorkQueue workQueue = new StaticWorkQueue();
    ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
    try {
      executor.execute(LogExceptions.wrap(CheckedExceptions.uncheck(() -> { 
        try (Connection c = getDBConnection();
             Stream<Record> stream = DSL.using(c).fetchStream("SELECT id, str FROM Source");
             Handoff<TargetBean> handoff = Handoff.to(() -> Batcher.batch(10, TestHandoff::flush1)).withBufferSize(100).on(workQueue)) {
          stream
            .map(TargetBean::new)
            .forEach(handoff);
        }
      })));
      CheckedExceptions.runUnchecked(() -> Thread.sleep(100));
      IntStream.range(1, THREAD_COUNT).forEach((i) -> executor.submit(LogExceptions.wrap(new Assister(workQueue))));
    } finally {
      executor.shutdown();
      executor.awaitTermination(2, TimeUnit.MINUTES);
    }
    
    // Then check data
    checkDataMatchesExpectations("Target1");
  }


  private void checkDataMatchesExpectations(String table) throws SQLException {
    try (Connection c = getDBConnection()) {
      try (Stream<Record> stream = DSL.using(c).fetchStream("SELECT id, str FROM " + table + " ORDER BY id")) {
        int expectedIndex = 1;
        Iterator<Record> iterator = stream.iterator();
        while (iterator.hasNext()) {
          TargetBean targetBean = new TargetBean(iterator.next());
          Assert.assertEquals(expectedIndex, targetBean.id);
          Assert.assertEquals("Source" + Integer.toString(expectedIndex), targetBean.str);
          expectedIndex++;
        }
        Assert.assertEquals(RECORD_COUNT, expectedIndex - 1);
      }
    }
  }
  

  private static void flush1(Iterable<TargetBean> targetData) {
    log.info("Flush to Target1 on " + Thread.currentThread().getId() + ": " + targetData);
    flushTo("Target1", targetData);
  }


  private static void flushTo(String table, Iterable<TargetBean> targetData) {
    try (Connection c = getDBConnection()) {
      DSLContext dsl = DSL.using(c);
      BatchBindStep batch = dsl.batch("INSERT INTO " + table + " VALUES (?, ?)");
      targetData.forEach((d) -> batch.bind(d.id, d.str));
      batch.execute();
      c.commit();
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }
  

  private static Connection getDBConnection() {
    try {
      Connection connection = DriverManager.getConnection(DB_CONNECTION, DB_USER, DB_PASSWORD);
      connection.setAutoCommit(false);
      connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
      return connection;
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }
  
  private static void setupData(Connection c) throws SQLException {
    DSLContext dsl = DSL.using(c);
    dsl.execute("CREATE TABLE Source (id integer PRIMARY KEY, str nvarchar(255))");
    dsl.execute("CREATE TABLE Target1 (id integer PRIMARY KEY, str nvarchar(255))");
    BatchBindStep batch = dsl.batch("INSERT INTO Source VALUES (?, ?)");
    for (int i = 1 ; i <= RECORD_COUNT ; i++) {
      batch.bind(i, "Source" + Integer.toString(i));
    }
    batch.execute();
    c.commit();
  }
  
  private static void checkDriver() {
    try {
      Class.forName(DB_DRIVER);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }
  
  private static final class TargetBean {
    int id;
    String str;
    TargetBean(Record r) {
      super();
      this.id = r.get("ID", int.class);
      this.str = r.get("STR", String.class);
    }
    @Override
    public String toString() {
      return Integer.toString(id);
    }
  }
}

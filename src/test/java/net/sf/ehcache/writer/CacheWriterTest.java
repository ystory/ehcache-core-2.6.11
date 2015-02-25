/**
 *  Copyright Terracotta, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package net.sf.ehcache.writer;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.collection.IsMapContainingKey.hasKey;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static net.sf.ehcache.util.RetryAssert.assertBy;
import static net.sf.ehcache.util.RetryAssert.sleepFor;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.sf.ehcache.AbstractCacheTest;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheEntry;
import net.sf.ehcache.CacheException;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.CacheWriterConfiguration;
import net.sf.ehcache.event.CountingCacheEventListener;
import net.sf.ehcache.util.RetryAssert;
import net.sf.ehcache.writer.TestCacheWriterRetries.WriterEvent;
import net.sf.ehcache.writer.writebehind.operations.SingleOperationType;

import org.hamcrest.collection.IsEmptyCollection;
import org.hamcrest.core.Is;
import org.hamcrest.core.IsEqual;
import org.hamcrest.number.OrderingComparison;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests for a CacheWriters
 *
 * @author Geert Bevin
 * @version $Id$
 */
public class CacheWriterTest {

    private static final Logger LOG = LoggerFactory.getLogger(CacheWriterTest.class.getName());

    @BeforeClass
    public static void shutdownRunningCacheManagers() {
        if (!CacheManager.ALL_CACHE_MANAGERS.isEmpty()) {
          LOG.warn("Expected NO CacheManagers on test startup " + CacheManager.ALL_CACHE_MANAGERS);
          for (CacheManager manager : new ArrayList<CacheManager>(CacheManager.ALL_CACHE_MANAGERS)) {
            manager.shutdown();
          }
        }
        Assert.assertThat(CacheManager.ALL_CACHE_MANAGERS, IsEmptyCollection.<CacheManager>empty());
    }
    
    @Before
    public void noCacheManagersBefore() {
        Assert.assertThat(CacheManager.ALL_CACHE_MANAGERS, IsEmptyCollection.<CacheManager>empty());
    }
    
    @After
    public void noCacheManagersAfter() {
        Assert.assertThat(CacheManager.ALL_CACHE_MANAGERS, IsEmptyCollection.<CacheManager>empty());
    }
    
    private CacheManager createCacheManager() {
        return new CacheManager(AbstractCacheTest.TEST_CONFIG_DIR + "ehcache-writer.xml");
    }

    @Test
    public void testClonesDefaultCacheProperly() throws Exception {
        CacheManager manager = createCacheManager();
        try {
            final ConcurrentHashMap<Cache, CacheWriter> values = new ConcurrentHashMap<Cache, CacheWriter>();

            manager.addCache("first");
            final Cache first = manager.getCache("first");
            first.registerCacheWriter(new TestCacheWriter(new Properties()) {{ assertThat(values.putIfAbsent(first, this), nullValue()); }});
            assertThat(((AbstractTestCacheWriter) first.getRegisteredCacheWriter()).isInitialized(), is(true));

            manager.addCache("second");
            final Cache second = manager.getCache("second");
            second.registerCacheWriter(new TestCacheWriter(new Properties()) {{ assertThat(values.putIfAbsent(second, this), nullValue()); }});

            // Verify the second cache's WriteManager has a reference to the cache passed in
            final CacheWriterManager writerManager = second.getWriterManager();
            final Field declaredField = writerManager.getClass().getDeclaredField("cache");
            declaredField.setAccessible(true);
            assertThat(declaredField.get(writerManager), notNullValue());

            // Make sure both caches got their very own writer
            assertThat(values.get(first), notNullValue());
            assertThat(values.get(second), notNullValue());
            assertThat(values.get(first), not(sameInstance(values.get(second))));

            first.putWithWriter(new Element(new Object(), new Object()));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    public void testWriteThroughXml() {
        CacheManager manager = createCacheManager();
        try {
            Cache cache = manager.getCache("writeThroughCacheXml");
            assertNotNull(cache.getRegisteredCacheWriter());

            TestCacheWriter writer = (TestCacheWriter) cache.getRegisteredCacheWriter();
            assertTrue(writer.isInitialized());

            Element el1 = new Element("key1", "value1");
            Element el2 = new Element("key2", "value2");
            Element el3 = new Element("key3", "value3");
            assertEquals(0, writer.getWrittenElements().size());
            cache.putWithWriter(el1);
            assertEquals(1, writer.getWrittenElements().size());
            cache.putWithWriter(el2);
            assertEquals(2, writer.getWrittenElements().size());
            cache.putWithWriter(el3);
            assertEquals(3, writer.getWrittenElements().size());
            assertEquals("value1", writer.getWrittenElements().get("key1").getValue());
            assertEquals("value2", writer.getWrittenElements().get("key2").getValue());
            assertEquals("value3", writer.getWrittenElements().get("key3").getValue());
            cache.removeWithWriter(el2.getKey());
            assertEquals(3, writer.getWrittenElements().size());
            assertNotNull(writer.getWrittenElements().get("key1"));
            assertNotNull(writer.getWrittenElements().get("key2"));
            assertNotNull(writer.getWrittenElements().get("key3"));
            assertEquals(1, writer.getDeletedElements().size());
            assertTrue(writer.getDeletedElements().containsKey("key2"));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    public void testWriteThroughXmlProperties() {
        CacheManager manager = createCacheManager();
        try {
            Cache cache = manager.getCache("writeThroughCacheXmlProperties");
            assertNotNull(cache.getRegisteredCacheWriter());

            TestCacheWriter writer = (TestCacheWriter) cache.getRegisteredCacheWriter();
            assertTrue(writer.isInitialized());

            Element el1 = new Element("key1", "value1");
            Element el2 = new Element("key2", "value2");
            Element el3 = new Element("key3", "value3");
            assertEquals(0, writer.getWrittenElements().size());
            cache.putWithWriter(el1);
            assertEquals(1, writer.getWrittenElements().size());
            cache.putWithWriter(el2);
            assertEquals(2, writer.getWrittenElements().size());
            cache.putWithWriter(el3);
            assertEquals(3, writer.getWrittenElements().size());
            assertNull(writer.getWrittenElements().get("key1"));
            assertNull(writer.getWrittenElements().get("key2"));
            assertNull(writer.getWrittenElements().get("key3"));
            assertEquals("value1", writer.getWrittenElements().get("prekey1suff").getValue());
            assertEquals("value2", writer.getWrittenElements().get("prekey2suff").getValue());
            assertEquals("value3", writer.getWrittenElements().get("prekey3suff").getValue());
            cache.removeWithWriter(el1.getKey());
            assertEquals(3, writer.getWrittenElements().size());
            assertNotNull(writer.getWrittenElements().get("prekey1suff"));
            assertNotNull(writer.getWrittenElements().get("prekey2suff"));
            assertNotNull(writer.getWrittenElements().get("prekey3suff"));
            assertEquals(1, writer.getDeletedElements().size());
            assertTrue(writer.getDeletedElements().containsKey("prekey1suff"));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    public void testWriteThroughJavaRegistration() {
        CacheManager manager = createCacheManager();
        try {
            Cache cache = manager.getCache("writeThroughCacheJavaRegistration");
            assertNull(cache.getRegisteredCacheWriter());

            TestCacheWriter writer = new TestCacheWriter(new Properties());
            cache.registerCacheWriter(writer);
            assertTrue(writer.isInitialized());

            Element el1 = new Element("key1", "value1");
            Element el2 = new Element("key2", "value2");
            Element el3 = new Element("key3", "value3");
            Element el4 = new Element("key4", "value4");
            assertEquals(0, writer.getWrittenElements().size());
            cache.putWithWriter(el1);
            assertEquals(1, writer.getWrittenElements().size());
            cache.putWithWriter(el2);
            assertEquals(2, writer.getWrittenElements().size());
            cache.putWithWriter(el3);
            assertEquals(3, writer.getWrittenElements().size());
            cache.putWithWriter(el4);
            assertEquals(4, writer.getWrittenElements().size());
            assertEquals("value1", writer.getWrittenElements().get("key1").getValue());
            assertEquals("value2", writer.getWrittenElements().get("key2").getValue());
            assertEquals("value3", writer.getWrittenElements().get("key3").getValue());
            assertEquals("value4", writer.getWrittenElements().get("key4").getValue());
            cache.removeWithWriter(el1.getKey());
            cache.removeWithWriter(el2.getKey());
            cache.removeWithWriter(el3.getKey());
            assertEquals(4, writer.getWrittenElements().size());
            assertNotNull(writer.getWrittenElements().get("key1"));
            assertNotNull(writer.getWrittenElements().get("key2"));
            assertNotNull(writer.getWrittenElements().get("key3"));
            assertNotNull(writer.getWrittenElements().get("key4"));
            assertEquals(3, writer.getDeletedElements().size());
            assertTrue(writer.getDeletedElements().containsKey("key1"));
            assertTrue(writer.getDeletedElements().containsKey("key2"));
            assertTrue(writer.getDeletedElements().containsKey("key3"));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    public void testWriteThroughNotifyListeners() {
        CacheManager manager = createCacheManager();
        try {
            Cache cache = new Cache(new CacheConfiguration("writeThroughCacheOnly", 10)
                    .cacheEventListenerFactory(new CacheConfiguration.CacheEventListenerFactoryConfiguration()
                            .className("net.sf.ehcache.event.CountingCacheEventListenerFactory")));
            assertNull(cache.getRegisteredCacheWriter());

            manager.addCache(cache);

            TestCacheWriterException writer = new TestCacheWriterException();
            cache.registerCacheWriter(writer);
            assertTrue(writer.isInitialized());

            // without listeners notification
            cache.getCacheConfiguration().getCacheWriterConfiguration().setNotifyListenersOnException(false);

            CountingCacheEventListener listener = CountingCacheEventListener.getCountingCacheEventListener(cache);

            try {
                cache.putWithWriter(new Element("key1", "value1"));
                fail("Expected UnsupportedOperationException");
            } catch (UnsupportedOperationException e) {
                // expected
                assertEquals(0, listener.getCacheElementsPut().size());
            } catch(Throwable e) {
                fail("Didn't expect a " + e.getClass().getName());
            }

            listener.resetCounters();

            try {
                cache.putWithWriter(new Element("key1", "value1"));
                fail("Expected UnsupportedOperationException");
            } catch (UnsupportedOperationException e) {
                // expected
                assertEquals(0, listener.getCacheElementsUpdated().size());
            } catch(Throwable e) {
                fail("Didn't expect a " + e.getClass().getName());
            }

            listener.resetCounters();

            try {
                cache.removeWithWriter("key1");
                fail("Expected UnsupportedOperationException");
            } catch (UnsupportedOperationException e) {
                // expected
                assertEquals(0, listener.getCacheElementsRemoved().size());
            } catch(Throwable e) {
                fail("Didn't expect a " + e.getClass().getName());
            }

            // with listeners notification
            cache.getCacheConfiguration().getCacheWriterConfiguration().setNotifyListenersOnException(true);

            listener.resetCounters();

            try {
                cache.putWithWriter(new Element("key1", "value1"));
                fail("Expected UnsupportedOperationException");
            } catch (UnsupportedOperationException e) {
                // expected
                assertEquals(0, listener.getCacheElementsUpdated().size());
                assertEquals(1, listener.getCacheElementsPut().size());
            } catch(Throwable e) {
                fail("Didn't expect a " + e.getClass().getName());
            }

            listener.resetCounters();

            try {
                assertNotNull(cache.get("key1"));
                cache.putWithWriter(new Element("key1", "value1"));
                fail("Expected UnsupportedOperationException");
            } catch (UnsupportedOperationException e) {
                // expected
                assertEquals(0, listener.getCacheElementsPut().size());
                assertEquals(1, listener.getCacheElementsUpdated().size());
            } catch(Throwable e) {
                fail("Didn't expect a " + e.getClass().getName());
            }

            listener.resetCounters();

            try {
                cache.removeWithWriter("key1");
                fail("Expected UnsupportedOperationException");
            } catch (UnsupportedOperationException e) {
                // expected
                assertEquals(1, listener.getCacheElementsRemoved().size());
            }
        } finally {
            manager.shutdown();
        }
    }

    @Test
    public void testWriteBehindSolelyJava() {
        CacheManager manager = createCacheManager();
        try {
            Cache cache = new Cache(
                    new CacheConfiguration("writeBehindSolelyJava", 10)
                            .cacheWriter(new CacheWriterConfiguration()
                                    .writeMode(CacheWriterConfiguration.WriteMode.WRITE_BEHIND)
                                    .minWriteDelay(2)
                                    .maxWriteDelay(8)
                                    .writeBehindConcurrency(1)
                                    .cacheWriterFactory(new CacheWriterConfiguration.CacheWriterFactoryConfiguration()
                                            .className("net.sf.ehcache.writer.TestCacheWriterFactory"))));
            assertNotNull(cache.getRegisteredCacheWriter());

            manager.addCache(cache);
            TestCacheWriter writer = (TestCacheWriter) cache.getRegisteredCacheWriter();
            assertTrue(writer.isInitialized());
            assertThat(writer.getWrittenElements().keySet(), empty());
            assertThat(writer.getDeletedElements().keySet(), empty());

            Element el1 = new Element("key1", "value1");
            Element el2 = new Element("key2", "value2");
            Element el3 = new Element("key3", "value3");

            cache.putWithWriter(el1);
            cache.putWithWriter(el2);
            cache.putWithWriter(el3);
            assertBy(3, TimeUnit.SECONDS, writtenElements(writer), hasSize(3));
            assertThat(writer.getDeletedElements().keySet(), empty());
            assertThat(writer.getWrittenElements(), hasKey("key1"));
            assertThat(writer.getWrittenElements(), hasKey("key2"));
            assertThat(writer.getWrittenElements(), hasKey("key3"));

            cache.removeWithWriter(el2.getKey());
            cache.removeWithWriter(el3.getKey());
            assertBy(3, TimeUnit.SECONDS, deletedElements(writer), hasSize(2));
            assertThat(writer.getWrittenElements().keySet(), hasSize(3));
            assertThat(writer.getDeletedElements(), hasKey("key2"));
            assertThat(writer.getDeletedElements(), hasKey("key3"));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    public void testWriteBehindStopWaitsForEmptyQueue() {
        CacheManager manager = createCacheManager();
        try {
            Cache cache = new Cache(
                    new CacheConfiguration("writeBehindSolelyJavaStopWaitsForEmptyQueue", 10)
                            .cacheWriter(new CacheWriterConfiguration()
                                    .writeMode(CacheWriterConfiguration.WriteMode.WRITE_BEHIND)
                                    .minWriteDelay(2)
                                    .maxWriteDelay(8)));
            TestCacheWriterSlow writer = new TestCacheWriterSlow();
            cache.registerCacheWriter(writer);
            assertNotNull(cache.getRegisteredCacheWriter());

            manager.addCache(cache);
            assertTrue(writer.isInitialized());
            assertEquals(0, writer.getWrittenElements().size());

            Element el1 = new Element("key1", "value1");
            Element el2 = new Element("key2", "value2");
            Element el3 = new Element("key3", "value3");
            cache.putWithWriter(el1);
            cache.putWithWriter(el2);
            cache.putWithWriter(el3);
            cache.removeWithWriter(el2.getKey());
            cache.removeWithWriter(el3.getKey());
            cache.dispose();

            assertBy(3, TimeUnit.SECONDS, deletedElements(writer), hasSize(2));
            assertThat(writer.getWrittenElements().keySet(), hasSize(3));
            assertThat(writer.getWrittenElements(), hasKey("key1"));
            assertThat(writer.getWrittenElements(), hasKey("key2"));
            assertThat(writer.getWrittenElements(), hasKey("key3"));
            assertThat(writer.getDeletedElements(), hasKey("key2"));
            assertThat(writer.getDeletedElements(), hasKey("key3"));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    public void testWriteBehindBlocksWhenFull() throws Exception {
        CacheManager manager = createCacheManager();
        try {
            final Cache cache = new Cache(
                    new CacheConfiguration("writeBehindBlocksWhenFull", 10)
                            .cacheWriter(new CacheWriterConfiguration()
                                .writeBehindMaxQueueSize(3)
                                .writeMode(CacheWriterConfiguration.WriteMode.WRITE_BEHIND)));
            final CyclicBarrier barrier = new CyclicBarrier(2);
            TestCacheWriter writer = new TestCacheWriter(new Properties()) {

                @Override
                public void write(final Element element) throws CacheException {
                    if(super.getWrittenElements().size() == 0) {
                        try {
                            barrier.await();
                            barrier.await(5, TimeUnit.SECONDS);
                        } catch (TimeoutException e) {
                            // expected
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                    super.write(element);
                }

                @Override
                public void delete(final CacheEntry entry) throws CacheException {
                    super.delete(entry);
                }
            };
            cache.registerCacheWriter(writer);
            assertNotNull(cache.getRegisteredCacheWriter());

            manager.addCache(cache);
            assertTrue(writer.isInitialized());
            assertEquals(0, writer.getWrittenElements().size());

            Element el1 = new Element("key1", "value1");
            Element el2 = new Element("key2", "value2");
            Element el3 = new Element("key3", "value3");
            cache.putWithWriter(el1);
            barrier.await();
            long before = System.nanoTime();
            cache.putWithWriter(el2);
            assertThat(TimeUnit.SECONDS.convert(System.nanoTime() - before, TimeUnit.NANOSECONDS), is(0L));
            before = System.nanoTime();
            cache.putWithWriter(el3);
            assertThat(TimeUnit.SECONDS.convert(System.nanoTime() - before, TimeUnit.NANOSECONDS), is(0L));
            before = System.nanoTime();
            cache.removeWithWriter(el2.getKey());
            assertThat(TimeUnit.SECONDS.convert(System.nanoTime() - before, TimeUnit.NANOSECONDS), is(0L));
            before = System.nanoTime();
            cache.removeWithWriter(el3.getKey());
            assertThat(TimeUnit.SECONDS.convert(System.nanoTime() - before, TimeUnit.NANOSECONDS) > 4, is(true));
            try {
                barrier.await();
                fail("this should have failed!");
            } catch (BrokenBarrierException e) {
                // expected!
            }

            cache.dispose();

            assertBy(500, TimeUnit.MILLISECONDS, deletedElements(writer), hasSize(2));
            assertThat(writer.getWrittenElements().keySet(), hasSize(3));
            assertThat(writer.getWrittenElements(), hasKey("key1"));
            assertThat(writer.getWrittenElements(), hasKey("key2"));
            assertThat(writer.getWrittenElements(), hasKey("key3"));
            assertThat(writer.getDeletedElements(), hasKey("key2"));
            assertThat(writer.getDeletedElements(), hasKey("key3"));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    public void testWriteBehindBatched() {
        CacheManager manager = createCacheManager();
        try {
            Cache cache = new Cache(
                    new CacheConfiguration("writeBehindBatched", 10)
                            .cacheWriter(new CacheWriterConfiguration()
                                    .writeMode(CacheWriterConfiguration.WriteMode.WRITE_BEHIND)
                                    .minWriteDelay(1)
                                    .maxWriteDelay(4)
                                    .writeBatching(true)
                                    .writeBatchSize(10)
                                    .cacheWriterFactory(new CacheWriterConfiguration.CacheWriterFactoryConfiguration()
                                            .className("net.sf.ehcache.writer.TestCacheWriterFactory")
                                            .properties("key.prefix=pre2; key.suffix=suff2")
                                            .propertySeparator(";"))));
            assertNotNull(cache.getRegisteredCacheWriter());

            manager.addCache(cache);
            TestCacheWriter writer = (TestCacheWriter) cache.getRegisteredCacheWriter();
            assertTrue(writer.isInitialized());
            assertEquals(0, writer.getWrittenElements().size());

            Element el1 = new Element("key1", "value1");
            Element el2 = new Element("key2", "value2");
            Element el3 = new Element("key3", "value3");
            cache.putWithWriter(el1);
            cache.putWithWriter(el2);
            cache.putWithWriter(el3);

            sleepFor(3, TimeUnit.SECONDS);

            assertThat(writer.getWrittenElements().keySet(), empty());

            assertBy(3, TimeUnit.SECONDS, writtenElements(writer), hasSize(3));
            assertThat(writer.getWrittenElements(), not(hasKey("key1")));
            assertThat(writer.getWrittenElements(), not(hasKey("key2")));
            assertThat(writer.getWrittenElements(), not(hasKey("key3")));
            
            assertThat(writer.getWrittenElements(), not(hasKey("pre2key1suff2")));
            assertThat(writer.getWrittenElements(), not(hasKey("pre2key2suff2")));
            assertThat(writer.getWrittenElements(), not(hasKey("pre2key3suff2")));
            
            assertThat(writer.getWrittenElements(), hasKey("pre2key1suff2-batched"));
            assertThat(writer.getWrittenElements(), hasKey("pre2key2suff2-batched"));
            assertThat(writer.getWrittenElements(), hasKey("pre2key3suff2-batched"));

            assertThat(writer.getDeletedElements().keySet(), empty());

            cache.removeWithWriter(el2.getKey());
            cache.removeWithWriter(el3.getKey());

            sleepFor(2, TimeUnit.SECONDS);

            assertThat(writer.getWrittenElements().keySet(), hasSize(3));
            assertThat(writer.getDeletedElements().keySet(), empty());

            assertBy(3, TimeUnit.SECONDS, deletedElements(writer), hasSize(2));
            assertThat(writer.getWrittenElements().keySet(), hasSize(3));
            assertThat(writer.getDeletedElements(), hasKey("pre2key2suff2-batched"));
            assertThat(writer.getDeletedElements(), hasKey("pre2key3suff2-batched"));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    public void testWriteBehindBatchedCoalescing() {
        CacheManager manager = createCacheManager();
        try {
            Cache cache = new Cache(
                    new CacheConfiguration("writeBehindBatchedCoalescing", 10)
                            .cacheWriter(new CacheWriterConfiguration()
                                    .writeMode(CacheWriterConfiguration.WriteMode.WRITE_BEHIND)
                                    .minWriteDelay(1)
                                    .maxWriteDelay(1)
                                    .writeBatching(true)
                                    .writeBatchSize(10)
                                    .writeCoalescing(true)
                                    .cacheWriterFactory(new CacheWriterConfiguration.CacheWriterFactoryConfiguration()
                                            .className("net.sf.ehcache.writer.TestCacheWriterFactory"))));
            assertNotNull(cache.getRegisteredCacheWriter());

            manager.addCache(cache);
            TestCacheWriter writer = (TestCacheWriter) cache.getRegisteredCacheWriter();
            assertTrue(writer.isInitialized());
            assertEquals(0, writer.getWrittenElements().size());

            Element el1 = new Element("key1", "value1");
            Element el2a = new Element("key2", "value2a");
            Element el2b = new Element("key2", "value2b");
            Element el3 = new Element("key3", "value3");
            cache.putWithWriter(el1);
            cache.putWithWriter(el2a);
            cache.putWithWriter(el3);
            cache.putWithWriter(el2b);
            cache.removeWithWriter("key1");

            sleepFor(2, TimeUnit.SECONDS);

            assertBy(2, TimeUnit.SECONDS, writtenElements(writer), hasSize(2));
            assertThat(writer.getWrittenElements(), not(hasKey("key1-batched")));
            assertThat(writer.getWrittenElements(), hasKey("key2-batched"));
            assertThat(writer.getWrittenElements(), hasKey("key3-batched"));
            assertThat(writer.getWrittenElements().get("key2-batched").getObjectValue(), IsEqual.<Object>equalTo("value2b"));
            assertThat(writer.getWrittenElements().get("key3-batched").getObjectValue(), IsEqual.<Object>equalTo("value3"));

            assertThat(writer.getDeletedElements().keySet(), hasSize(1));
            assertThat(writer.getDeletedElements(), hasKey("key1-batched"));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    public void testWriteBehindExceptionWithoutRetrying() {
        CacheManager manager = createCacheManager();
        try {
            Cache cache = new Cache(
                    new CacheConfiguration("writeBehindExceptionWithoutRetrying", 10)
                            .cacheWriter(new CacheWriterConfiguration()
                                    .writeMode(CacheWriterConfiguration.WriteMode.WRITE_BEHIND)
                                    .minWriteDelay(0)
                                    .maxWriteDelay(0)
                                    .retryAttempts(0)
                                    .retryAttemptDelaySeconds(0)));
            TestCacheWriterRetries writer = new TestCacheWriterRetries(3);
            cache.registerCacheWriter(writer);

            manager.addCache(cache);
            assertTrue(writer.isInitialized());
            assertEquals(0, writer.getWriterEvents().size());

            cache.putWithWriter(new Element("key1", "value1"));
            try {
                cache.putWithWriter(new Element("key2", "value1"));
                cache.putWithWriter(new Element("key3", "value1"));
                cache.removeWithWriter("key2");
            } catch (CacheException e) {
                System.err.println("We had an error trying to write: " + e.getMessage()
                                + "\nBut that's OK: the writer got shutdown faster than we could *WithWriter");
            }

            sleepFor(2, TimeUnit.SECONDS);
            assertEquals(0, writer.getWriterEvents().size());
            assertEquals(3, writer.getThrownAwayElements(SingleOperationType.WRITE).size());
            assertEquals(1, writer.getThrownAwayElements(SingleOperationType.DELETE).size());
            writer.setThrowing(false);
            cache.putWithWriter(new Element("key2", "value1"));
            sleepFor(2, TimeUnit.SECONDS);
            assertEquals(1, writer.getWriterEvents().size());
        } finally {
            manager.shutdown();
        }
    }

    @Test
    public void testWriteBehindRetryWithoutExceptions() {
        CacheManager manager = createCacheManager();
        try {
            Cache cache = new Cache(
                    new CacheConfiguration("writeBehindRetryWithoutExceptions", 10)
                            .cacheWriter(new CacheWriterConfiguration()
                                    .writeMode(CacheWriterConfiguration.WriteMode.WRITE_BEHIND)
                                    .minWriteDelay(0)
                                    .maxWriteDelay(0)
                                    .retryAttempts(3)
                                    .retryAttemptDelaySeconds(0)));
            TestCacheWriterRetries writer = new TestCacheWriterRetries(0);
            cache.registerCacheWriter(writer);

            manager.addCache(cache);
            assertTrue(writer.isInitialized());
            assertEquals(0, writer.getWriterEvents().size());

            cache.putWithWriter(new Element("key1", "value1"));
            cache.putWithWriter(new Element("key2", "value1"));
            cache.putWithWriter(new Element("key3", "value1"));
            cache.removeWithWriter("key2");

            sleepFor(2, TimeUnit.SECONDS);

            assertEquals(4, writer.getWriterEvents().size());
            assertEquals(1, (long) writer.getWriteCount().get("key1"));
            assertEquals(1, (long) writer.getWriteCount().get("key2"));
            assertEquals(1, (long) writer.getWriteCount().get("key3"));
            assertFalse(writer.getDeleteCount().containsKey("key1"));
            assertEquals(1, (long) writer.getDeleteCount().get("key2"));
            assertFalse(writer.getDeleteCount().containsKey("key3"));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    public void testWriteBehindRetryWithoutDelay() {
        CacheManager manager = createCacheManager();
        try {
            Cache cache = new Cache(
                    new CacheConfiguration("writeBehindRetryWithoutDelay", 10)
                            .cacheWriter(new CacheWriterConfiguration()
                                    .writeMode(CacheWriterConfiguration.WriteMode.WRITE_BEHIND)
                                    .minWriteDelay(0)
                                    .maxWriteDelay(0)
                                    .retryAttempts(3)
                                    .retryAttemptDelaySeconds(0)));
            final TestCacheWriterRetries writer = new TestCacheWriterRetries(3);
            cache.registerCacheWriter(writer);

            manager.addCache(cache);
            assertTrue(writer.isInitialized());
            assertEquals(0, writer.getWriterEvents().size());

            cache.putWithWriter(new Element("key1", "value1"));
            cache.putWithWriter(new Element("key2", "value2"));
            cache.putWithWriter(new Element("key3", "value3"));
            cache.removeWithWriter("key2");

            RetryAssert.assertBy(30, TimeUnit.SECONDS, writeEvents(writer), hasSize(4));
            assertEquals(1, (long) writer.getWriteCount().get("key1"));
            assertEquals(1, (long) writer.getWriteCount().get("key2"));
            assertEquals(1, (long) writer.getWriteCount().get("key3"));
            assertEquals(1, (long) writer.getDeleteCount().get("key2"));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    public void testWriteBehindRetryWithDelay() {
        CacheManager manager = createCacheManager();
        try {
            final long tolerance = TimeUnit.MILLISECONDS.toNanos(200);

            Cache cache = new Cache(
                    new CacheConfiguration("writeBehindRetryWithDelay", 10)
                            .cacheWriter(new CacheWriterConfiguration()
                                    .writeMode(CacheWriterConfiguration.WriteMode.WRITE_BEHIND)
                                    .minWriteDelay(0)
                                    .maxWriteDelay(0)
                                    .retryAttempts(3)
                                    .retryAttemptDelaySeconds(1)));
            final TestCacheWriterRetries writer = new TestCacheWriterRetries(3);
            cache.registerCacheWriter(writer);

            manager.addCache(cache);
            assertTrue(writer.isInitialized());
            assertEquals(0, writer.getWriterEvents().size());

            long start = System.nanoTime();
            cache.putWithWriter(new Element("key1", "value1"));
            cache.putWithWriter(new Element("key2", "value2"));
            cache.putWithWriter(new Element("key3", "value3"));
            cache.removeWithWriter("key2");

            RetryAssert.assertBy(30, TimeUnit.SECONDS, writeEvents(writer), hasSize(4));

            {
                WriterEvent event = writer.getWriterEvents().get(0);
                assertEquals(0, event.getWrittenSize());
                assertEquals(0, event.getWriteCount("key1"));
                assertEquals(0, event.getWriteCount("key2"));
                assertEquals(0, event.getWriteCount("key3"));
                assertEquals(0, event.getDeleteCount("key2"));
                assertEquals("key1", event.getAddedElement().getObjectKey());
                long time = event.getTime() - start;
                long expected = TimeUnit.SECONDS.toNanos(3);
                assertTrue("Write-1 time : " + time, time >= (expected - tolerance));
            }

            {
                WriterEvent event = writer.getWriterEvents().get(1);
                assertEquals(1, event.getWrittenSize());
                assertEquals(1, event.getWriteCount("key1"));
                assertEquals(0, event.getWriteCount("key2"));
                assertEquals(0, event.getWriteCount("key3"));
                assertEquals(0, event.getDeleteCount("key2"));
                assertEquals("key2", event.getAddedElement().getObjectKey());
                long time = event.getTime() - start;
                long expected = TimeUnit.SECONDS.toNanos(6);
                assertTrue("Write-2 time : " + time, time >= (expected - tolerance));
            }

            {
                WriterEvent event = writer.getWriterEvents().get(2);
                assertEquals(2, event.getWrittenSize());
                assertEquals(1, event.getWriteCount("key1"));
                assertEquals(1, event.getWriteCount("key2"));
                assertEquals(0, event.getWriteCount("key3"));
                assertEquals(0, event.getDeleteCount("key2"));
                assertEquals("key3", event.getAddedElement().getObjectKey());
                long time = event.getTime() - start;
                long expected = TimeUnit.SECONDS.toNanos(9);
                assertTrue("Write-3 time : " + time, time >= (expected - tolerance));
            }

            {
                WriterEvent event = writer.getWriterEvents().get(3);
                assertEquals(3, event.getWrittenSize());
                assertEquals(1, event.getWriteCount("key1"));
                assertEquals(1, event.getWriteCount("key2"));
                assertEquals(1, event.getWriteCount("key3"));
                assertEquals(0, event.getDeleteCount("key2"));
                assertEquals("key2", event.getRemovedKey());
                long time = event.getTime() - start;
                long expected = TimeUnit.SECONDS.toNanos(12);
                assertTrue("Delete-2 time : " + time, time >= (expected - tolerance));
            }

            assertEquals(4, writer.getWriterEvents().size());
            assertEquals(1, writer.getWriteCount().get("key1").intValue());
            assertEquals(1, writer.getWriteCount().get("key2").intValue());
            assertEquals(1, writer.getWriteCount().get("key3").intValue());
            assertEquals(1, writer.getDeleteCount().get("key2").intValue());
        } finally {
            manager.shutdown();
        }
    }

    @Test
    public void testWriteBehindExceptionWithoutRetryingBatched() {
        CacheManager manager = createCacheManager();
        try {
            Cache cache = new Cache(
                    new CacheConfiguration("writeBehindExceptionWithoutRetryingBatched", 10)
                            .cacheWriter(new CacheWriterConfiguration()
                                    .writeMode(CacheWriterConfiguration.WriteMode.WRITE_BEHIND)
                                    .minWriteDelay(1)
                                    .maxWriteDelay(1)
                                    .writeBatching(true)
                                    .writeBatchSize(10)
                                    .retryAttempts(0)
                                    .retryAttemptDelaySeconds(0)));
            TestCacheWriterRetries writer = new TestCacheWriterRetries(3);
            cache.registerCacheWriter(writer);

            manager.addCache(cache);
            assertTrue(writer.isInitialized());
            assertEquals(0, writer.getWriterEvents().size());

            cache.putWithWriter(new Element("key1", "value1"));
            cache.putWithWriter(new Element("key2", "value1"));
            cache.putWithWriter(new Element("key3", "value1"));
            cache.removeWithWriter("key2");

            sleepFor(3, TimeUnit.SECONDS);

            assertEquals(2, writer.getWriterEvents().size());
            assertEquals(1, (long) writer.getWriteCount().get("key1"));
            assertEquals(1, (long) writer.getWriteCount().get("key2"));
            assertFalse(writer.getWriteCount().containsKey("key3"));
            assertFalse(writer.getDeleteCount().containsKey("key1"));
            assertFalse(writer.getDeleteCount().containsKey("key2"));
            assertFalse(writer.getDeleteCount().containsKey("key3"));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    public void testWriteBehindRetryWithoutExceptionsBatched() {
        CacheManager manager = createCacheManager();
        try {
            Cache cache = new Cache(
                    new CacheConfiguration("writeBehindRetryWithoutExceptionsBatched", 10)
                            .cacheWriter(new CacheWriterConfiguration()
                                    .writeMode(CacheWriterConfiguration.WriteMode.WRITE_BEHIND)
                                    .minWriteDelay(1)
                                    .maxWriteDelay(1)
                                    .writeBatching(true)
                                    .writeBatchSize(10)
                                    .retryAttempts(3)
                                    .retryAttemptDelaySeconds(0)));
            TestCacheWriterRetries writer = new TestCacheWriterRetries(0);
            cache.registerCacheWriter(writer);

            manager.addCache(cache);
            assertTrue(writer.isInitialized());
            assertEquals(0, writer.getWriterEvents().size());

            cache.putWithWriter(new Element("key1", "value1"));
            cache.putWithWriter(new Element("key2", "value2"));
            cache.putWithWriter(new Element("key3", "value3"));
            cache.removeWithWriter("key2");

            sleepFor(2, TimeUnit.SECONDS);

            assertEquals(4, writer.getWriterEvents().size());
            assertEquals(1, (long) writer.getWriteCount().get("key1"));
            assertEquals(1, (long) writer.getWriteCount().get("key2"));
            assertEquals(1, (long) writer.getWriteCount().get("key3"));
            assertFalse(writer.getDeleteCount().containsKey("key1"));
            assertEquals(1, (long) writer.getDeleteCount().get("key2"));
            assertFalse(writer.getDeleteCount().containsKey("key3"));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    public void testWriteBehindRetryWithoutDelayBatched() {
        CacheManager manager = createCacheManager();
        try {
            Cache cache = new Cache(
                    new CacheConfiguration("writeBehindRetryWithoutDelayBatched", 10)
                            .cacheWriter(new CacheWriterConfiguration()
                                    .writeMode(CacheWriterConfiguration.WriteMode.WRITE_BEHIND)
                                    .minWriteDelay(1)
                                    .maxWriteDelay(1)
                                    .writeBatching(true)
                                    .writeBatchSize(10)
                                    .retryAttempts(3)
                                    .retryAttemptDelaySeconds(0)));
            final TestCacheWriterRetries writer = new TestCacheWriterRetries(3);
            cache.registerCacheWriter(writer);

            manager.addCache(cache);
            assertTrue(writer.isInitialized());
            assertEquals(0, writer.getWriterEvents().size());

            cache.putWithWriter(new Element("key1", "value1"));
            cache.putWithWriter(new Element("key2", "value2"));
            cache.putWithWriter(new Element("key3", "value3"));
            cache.removeWithWriter("key2");

            RetryAssert.assertBy(4, TimeUnit.SECONDS, writeEvents(writer), hasSize(10));
            assertEquals(4, (long) writer.getWriteCount().get("key1"));
            assertEquals(4, (long) writer.getWriteCount().get("key2"));
            assertEquals(1, (long) writer.getWriteCount().get("key3"));
            assertEquals(1, (long) writer.getDeleteCount().get("key2"));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    public void testWriteBehindRetryWithDelayBatched() {
        final int RETRIES = 1;

        CacheManager manager = createCacheManager();
        try {
            Cache cache = new Cache(
                    new CacheConfiguration("writeBehindRetryWithDelayBatched", 10)
                            .cacheWriter(new CacheWriterConfiguration()
                                    .writeMode(CacheWriterConfiguration.WriteMode.WRITE_BEHIND)
                                    .minWriteDelay(1)
                                    .maxWriteDelay(1)
                                    .writeBatching(true)
                                    .writeBatchSize(10)
                                    .retryAttempts(RETRIES)
                                    .retryAttemptDelaySeconds(1)));
            final TestCacheWriterRetries writer = new TestCacheWriterRetries(RETRIES);
            cache.registerCacheWriter(writer);

            manager.addCache(cache);
            assertTrue(writer.isInitialized());
            assertEquals(0, writer.getWriterEvents().size());

            cache.putWithWriter(new Element("key1", "value1"));
            cache.putWithWriter(new Element("key2", "value2"));
            cache.removeWithWriter("key2");

            RetryAssert.assertBy(30, TimeUnit.SECONDS, writeEvents(writer), hasSize(4));
            
            List<WriterEvent> events = writer.getWriterEvents();
            assertThat(events.get(0).getAddedElement().getObjectKey(), Is.<Object>is("key1"));
            
            assertThat(events.get(1).getAddedElement().getObjectKey(), Is.<Object>is("key1"));
            assertThat(events.get(1).getTime(), OrderingComparison.greaterThanOrEqualTo(events.get(0).getTime() + TimeUnit.SECONDS.toMillis(500)));
            
            assertThat(events.get(2).getAddedElement().getObjectKey(), Is.<Object>is("key2"));
            assertThat(events.get(3).getRemovedKey(), Is.<Object>is("key2"));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    public void testWriteBehindRateLimitBatched() {
        CacheManager manager = createCacheManager();
        try {
            Cache cache = new Cache(
                    new CacheConfiguration("writeBehindRetryWithDelayBatched", 100)
                            .cacheWriter(new CacheWriterConfiguration()
                                    .writeMode(CacheWriterConfiguration.WriteMode.WRITE_BEHIND)
                                    .minWriteDelay(1)
                                    .writeBatching(true)
                                    .writeBatchSize(10)
                                    .rateLimitPerSecond(5)));
            TestCacheWriter writer = new TestCacheWriter(new Properties());
            cache.registerCacheWriter(writer);

            manager.addCache(cache);
            assertTrue(writer.isInitialized());
            assertEquals(0, writer.getWrittenElements().size());

            for (int i = 0; i < 30; i++) {
                cache.putWithWriter(new Element("key" + i, "value" + i));
            }

            sleepFor(1, TimeUnit.SECONDS);

            assertEquals(0, writer.getWrittenElements().size());

            sleepFor(1500, TimeUnit.MILLISECONDS);

            assertEquals(10, writer.getWrittenElements().size());

            sleepFor(1, TimeUnit.SECONDS);

            assertEquals(10, writer.getWrittenElements().size());

            sleepFor(1, TimeUnit.SECONDS);

            assertEquals(20, writer.getWrittenElements().size());

            sleepFor(1, TimeUnit.SECONDS);

            assertEquals(20, writer.getWrittenElements().size());

            sleepFor(1, TimeUnit.SECONDS);

            assertEquals(30, writer.getWrittenElements().size());
        } finally {
            manager.shutdown();
        }
    }

    private static Callable<Set<Object>> writtenElements(final TestCacheWriter writer) {
        return new Callable<Set<Object>>() {
            public Set<Object> call() throws Exception {
                return writer.getWrittenElements().keySet();
            }
        };
    }

    private static Callable<Set<Object>> deletedElements(final TestCacheWriter writer) {
        return new Callable<Set<Object>>() {
            public Set<Object> call() throws Exception {
                return writer.getDeletedElements().keySet();
            }
        };
    }
    
    private static Callable<List<WriterEvent>> writeEvents(final TestCacheWriterRetries writer) {
        return new Callable<List<WriterEvent>>() {
            @Override
            public List<WriterEvent> call() throws Exception {
                return writer.getWriterEvents();
            }
        };
    }
}

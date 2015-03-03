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


package net.sf.ehcache.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;

import junit.framework.Assert;
import net.sf.ehcache.AbstractCacheTest;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheException;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.bootstrap.BootstrapCacheLoader;
import net.sf.ehcache.config.PersistenceConfiguration.Strategy;
import net.sf.ehcache.config.TerracottaConfiguration.Consistency;
import net.sf.ehcache.distribution.CacheManagerPeerListener;
import net.sf.ehcache.distribution.CacheManagerPeerProvider;
import net.sf.ehcache.distribution.MulticastRMICacheManagerPeerProvider;
import net.sf.ehcache.distribution.RMIAsynchronousCacheReplicator;
import net.sf.ehcache.distribution.RMIBootstrapCacheLoader;
import net.sf.ehcache.distribution.RMICacheManagerPeerListener;
import net.sf.ehcache.distribution.RMICacheReplicatorFactory;
import net.sf.ehcache.event.CacheEventListener;
import net.sf.ehcache.event.CacheManagerEventListener;
import net.sf.ehcache.event.CountingCacheEventListener;
import net.sf.ehcache.event.CountingCacheManagerEventListener;
import net.sf.ehcache.event.NotificationScope;
import net.sf.ehcache.exceptionhandler.CacheExceptionHandler;
import net.sf.ehcache.exceptionhandler.CountingExceptionHandler;
import net.sf.ehcache.store.DefaultElementValueComparator;
import net.sf.ehcache.store.MemoryStoreEvictionPolicy;
import net.sf.ehcache.store.compound.ReadWriteSerializationCopyStrategy;
import net.sf.ehcache.writer.TestCacheWriter;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests for Store Configuration
 * <p/>
 * Make sure ant compile has been executed before running these tests, as they rely on the test ehcache.xml being
 * in the classpath.
 *
 * @author <a href="mailto:gluck@thoughtworks.com">Greg Luck</a>
 * @version $Id$
 */
public class ConfigurationFactoryTest extends AbstractCacheTest {
    private static final int CACHES_IN_TEST_EHCACHE = 15;

    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationFactoryTest.class.getName());


    /**
     * setup test
     */
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        manager.removalAll();
    }

    /**
     * Tests that the loader successfully loads from ehcache.xml.
     * ehcache.xml should be found in the classpath. In our ant configuration
     * this should be from build/test-classes/ehcache.xml
     * <p/>
     * <defaultCache
     * maxElementsInMemory="10000"
     * eternal="false"
     * timeToIdleSeconds="3600"
     * timeToLiveSeconds="10"
     * overflowToDisk="true"
     * />
     */
    @Test
    public void testLoadConfigurationFromClasspath() throws Exception {

        Configuration configuration = ConfigurationFactory.parseConfiguration();
        ConfigurationHelper configurationHelper = new ConfigurationHelper(manager, configuration);

        //Check core attributes
        assertEquals(null, configurationHelper.getConfigurationBean().getName());
        assertEquals(true, configurationHelper.getConfigurationBean().getUpdateCheck());
        assertEquals(Configuration.Monitoring.AUTODETECT, configurationHelper.getConfigurationBean().getMonitoring());

        //Check disk store
        assertEquals(System.getProperty("java.io.tmpdir"), configurationHelper.getDiskStorePath());


        //Check CacheManagerPeerProvider
        Map<String, CacheManagerPeerProvider> peerProviders = configurationHelper.createCachePeerProviders();
        CacheManagerPeerProvider peerProvider = peerProviders.get("RMI");


        //Check TTL
        assertTrue(peerProvider instanceof MulticastRMICacheManagerPeerProvider);
        assertEquals(Integer.valueOf(0), ((MulticastRMICacheManagerPeerProvider) peerProvider).getHeartBeatSender().getTimeToLive());

        //Check CacheManagerEventListener
        assertEquals(null, configurationHelper.createCacheManagerEventListener(null));

        //Check default cache
        Ehcache defaultCache = configurationHelper.createDefaultCache();
        assertEquals("default", defaultCache.getName());
        assertEquals(false, defaultCache.getCacheConfiguration().isEternal());
        assertEquals(5, defaultCache.getCacheConfiguration().getTimeToIdleSeconds());
        assertEquals(10, defaultCache.getCacheConfiguration().getTimeToLiveSeconds());
        assertEquals(true, defaultCache.getCacheConfiguration().isOverflowToDisk());

        //Check caches
        assertEquals(CACHES_IN_TEST_EHCACHE, configurationHelper.createCaches().size());

        /*
        <cache name="sampleCache1"
        maxElementsInMemory="10000"
        eternal="false"
        timeToIdleSeconds="360"
        timeToLiveSeconds="1000"
        overflowToDisk="true"
        />
        */
        Ehcache sampleCache1 = configurationHelper.createCacheFromName("sampleCache1");
        assertEquals("sampleCache1", sampleCache1.getName());
        assertEquals(false, sampleCache1.getCacheConfiguration().isEternal());
        assertEquals(360, sampleCache1.getCacheConfiguration().getTimeToIdleSeconds());
        assertEquals(1000, sampleCache1.getCacheConfiguration().getTimeToLiveSeconds());
        assertEquals(true, sampleCache1.getCacheConfiguration().isOverflowToDisk());
        assertEquals(1000, sampleCache1.getCacheConfiguration().getMaxElementsOnDisk());

        /** A cache which overflows to disk. The disk store is persistent
         between cache and VM restarts. The disk expiry thread interval is set to 10 minutes, overriding
         the default of 2 minutes.
         <cache name="persistentLongExpiryIntervalCache"
         maxElementsInMemory="500"
         eternal="false"
         timeToIdleSeconds="300"
         timeToLiveSeconds="600"
         overflowToDisk="true"
         diskPersistent="true"
         diskExpiryThreadIntervalSeconds="600"
         /> */
        Ehcache persistentLongExpiryIntervalCache = configurationHelper.createCacheFromName("persistentLongExpiryIntervalCache");
        assertEquals("persistentLongExpiryIntervalCache", persistentLongExpiryIntervalCache.getName());
        assertEquals(false, persistentLongExpiryIntervalCache.getCacheConfiguration().isEternal());
        assertEquals(300, persistentLongExpiryIntervalCache.getCacheConfiguration().getTimeToIdleSeconds());
        assertEquals(600, persistentLongExpiryIntervalCache.getCacheConfiguration().getTimeToLiveSeconds());
        assertEquals(true, persistentLongExpiryIntervalCache.getCacheConfiguration().isOverflowToDisk());
        assertEquals(true, persistentLongExpiryIntervalCache.getCacheConfiguration().isDiskPersistent());
        assertEquals(600, persistentLongExpiryIntervalCache.getCacheConfiguration().getDiskExpiryThreadIntervalSeconds());

        /*
           <!--
            A cache which has a CacheExtension
            -->
            <cache name="testCacheExtensionCache"
                   maxElementsInMemory="10"
                   eternal="false"
                   timeToIdleSeconds="100"
                   timeToLiveSeconds="100"
                   overflowToDisk="false">
                <cacheExtensionFactory
                        class="net.sf.ehcache.extension.TestCacheExtensionFactory"
                        properties="propertyA=valueA"/>
            </cache>CacheExtension cache
        */
        Ehcache exceptionHandlingCache = configurationHelper.createCacheFromName("exceptionHandlingCache");
        assertEquals("exceptionHandlingCache", exceptionHandlingCache.getName());
        assertTrue(exceptionHandlingCache.getCacheExceptionHandler() != null);
        assertTrue(exceptionHandlingCache.getCacheExceptionHandler() instanceof CountingExceptionHandler);
        assertTrue(exceptionHandlingCache.getCacheExceptionHandler() instanceof CacheExceptionHandler);
    }


    /**
     * Tests that the loader successfully loads from ehcache.xml
     * given as a {@link File}
     * <p/>
     * <defaultCache
     * maxElementsInMemory="10000"
     * eternal="false"
     * timeToIdleSeconds="120"
     * timeToLiveSeconds="120"
     * overflowToDisk="true"
     * />
     */
    @Test
    public void testLoadConfigurationFromFile() throws Exception {

        File file = new File(SRC_CONFIG_DIR + "ehcache.xml");
        Configuration configuration = ConfigurationFactory.parseConfiguration(file);
        ConfigurationHelper configurationHelper = new ConfigurationHelper(manager, configuration);

        assertEquals(null, configurationHelper.getConfigurationBean().getName());
        assertEquals(true, configurationHelper.getConfigurationBean().getUpdateCheck());
        assertEquals(Configuration.Monitoring.AUTODETECT, configurationHelper.getConfigurationBean().getMonitoring());

        //Check disk store  <diskStore path="/tmp"/>
        assertEquals(System.getProperty("java.io.tmpdir"), configurationHelper.getDiskStorePath());

        //Check CacheManagerPeerProvider
        Map<String, CacheManagerPeerProvider> peerProviders = configurationHelper.createCachePeerProviders();
        CacheManagerPeerProvider peerProvider = peerProviders.get("RMI");


        //Check TTL
        assertTrue(peerProvider instanceof MulticastRMICacheManagerPeerProvider);
        assertEquals(Integer.valueOf(1), ((MulticastRMICacheManagerPeerProvider) peerProvider).getHeartBeatSender().getTimeToLive());


        //Check default cache
        Ehcache defaultCache = configurationHelper.createDefaultCache();
        assertEquals("default", defaultCache.getName());
        assertEquals(false, defaultCache.getCacheConfiguration().isEternal());
        assertEquals(120, defaultCache.getCacheConfiguration().getTimeToIdleSeconds());
        assertEquals(120, defaultCache.getCacheConfiguration().getTimeToLiveSeconds());
        assertEquals(Strategy.LOCALTEMPSWAP, defaultCache.getCacheConfiguration().getPersistenceConfiguration().getStrategy());
        assertEquals(10000, defaultCache.getCacheConfiguration().getMaxElementsInMemory());
        assertEquals(10000000, defaultCache.getCacheConfiguration().getMaxElementsOnDisk());

        //Check caches
        assertEquals(6, configurationHelper.createCaches().size());

        //check config
        CacheConfiguration sampleCache1Config = configuration.getCacheConfigurations().get("sampleCache1");
        assertEquals("sampleCache1", sampleCache1Config.getName());
        assertEquals(false, sampleCache1Config.isEternal());
        assertEquals(300, sampleCache1Config.getTimeToIdleSeconds());
        assertEquals(600, sampleCache1Config.getTimeToLiveSeconds());
        assertEquals(Strategy.LOCALTEMPSWAP, sampleCache1Config.getPersistenceConfiguration().getStrategy());
        assertEquals(20, sampleCache1Config.getDiskSpoolBufferSizeMB());

        //  <cache name="sampleCache1"
        //  maxElementsInMemory="10000"
        //  eternal="false"
        //  timeToIdleSeconds="300"
        //  timeToLiveSeconds="600"
        //  overflowToDisk="true"
        //  />
        //Check created cache
        Ehcache sampleCache1 = configurationHelper.createCacheFromName("sampleCache1");
        assertEquals("sampleCache1", sampleCache1.getName());
        assertEquals(false, sampleCache1.getCacheConfiguration().isEternal());
        assertEquals(300, sampleCache1.getCacheConfiguration().getTimeToIdleSeconds());
        assertEquals(1000, sampleCache1.getCacheConfiguration().getMaxElementsOnDisk());
        assertEquals(600, sampleCache1.getCacheConfiguration().getTimeToLiveSeconds());
        assertEquals(Strategy.LOCALTEMPSWAP, sampleCache1Config.getPersistenceConfiguration().getStrategy());
    }

    /**
     * Can we read from a UTF8 encoded file which uses Japanese characters
     */
    @Test
    public void testLoadUTF8ConfigurationFromFile() throws Exception {

        File file = new File(TEST_CONFIG_DIR + "ehcacheUTF8.xml");
        Configuration configuration = ConfigurationFactory.parseConfiguration(file);
        ConfigurationHelper configurationHelper = new ConfigurationHelper(manager, configuration);
    }


    /**
     * Tests that the loader successfully loads from ehcache-1.1.xml
     * given as a {@link File}. This is a backward compatibility test.
     * <p/>
     * <defaultCache
     * maxElementsInMemory="10000"
     * eternal="false"
     * timeToIdleSeconds="120"
     * timeToLiveSeconds="120"
     * overflowToDisk="true"
     * />
     */
    @Test
    public void testLoadConfigurationFromEhcache11File() throws Exception {

        File file = new File(TEST_CONFIG_DIR + "ehcache-1_1.xml");
        Configuration configuration = ConfigurationFactory.parseConfiguration(file);
        ConfigurationHelper configurationHelper = new ConfigurationHelper(manager, configuration);

        assertEquals(null, configurationHelper.getConfigurationBean().getName());
        assertEquals(true, configurationHelper.getConfigurationBean().getUpdateCheck());
        assertEquals(Configuration.Monitoring.AUTODETECT, configurationHelper.getConfigurationBean().getMonitoring());

        //Check disk path  <diskStore path="/tmp"/>
        assertEquals(System.getProperty("java.io.tmpdir"), configurationHelper.getDiskStorePath());

        //Check default cache
        Ehcache defaultCache = configurationHelper.createDefaultCache();
        assertEquals("default", defaultCache.getName());
        assertEquals(false, defaultCache.getCacheConfiguration().isEternal());
        assertEquals(5, defaultCache.getCacheConfiguration().getTimeToIdleSeconds());
        assertEquals(10, defaultCache.getCacheConfiguration().getTimeToLiveSeconds());
        assertEquals(true, defaultCache.getCacheConfiguration().isOverflowToDisk());
        assertEquals(10, defaultCache.getCacheConfiguration().getMaxElementsInMemory());
        assertEquals(0, defaultCache.getCacheConfiguration().getMaxElementsOnDisk());

        //Check caches
        assertEquals(8, configurationHelper.createCaches().size());

        //  <cache name="sampleCache1"
        //  maxElementsInMemory="10000"
        //  eternal="false"
        //  timeToIdleSeconds="300"
        //  timeToLiveSeconds="600"
        //  overflowToDisk="true"
        //  />
        Ehcache sampleCache1 = configurationHelper.createCacheFromName("sampleCache1");
        assertEquals("sampleCache1", sampleCache1.getName());
        assertEquals(false, sampleCache1.getCacheConfiguration().isEternal());
        assertEquals(360, sampleCache1.getCacheConfiguration().getTimeToIdleSeconds());
        assertEquals(1000, sampleCache1.getCacheConfiguration().getTimeToLiveSeconds());
        assertEquals(true, sampleCache1.getCacheConfiguration().isOverflowToDisk());
    }

    /**
     * Tests that the CacheManagerEventListener is null when
     * no CacheManagerEventListener class is specified.
     */
    @Test
    public void testLoadConfigurationFromFileNoCacheManagerListenerDefault() throws Exception {
        File file = new File(TEST_CONFIG_DIR + "ehcache-nolisteners.xml");
        Configuration configuration = ConfigurationFactory.parseConfiguration(file);
        ConfigurationHelper configurationHelper = new ConfigurationHelper(manager, configuration);

        //Check CacheManagerEventListener
        CacheManagerEventListener listener = configurationHelper.createCacheManagerEventListener(null);
        assertEquals(null, listener);

        //Check caches. Configuration should have completed
        assertEquals(10, configurationHelper.createCaches().size());
    }

    /**
     * Tests that the CacheManagerEventListener class is set as the CacheManagerEventListener
     * when the class is unloadable.
     */
    @Test
    public void testLoadConfigurationFromFileUnloadableCacheManagerListenerDefault() throws Exception {
        File file = new File(TEST_CONFIG_DIR + "ehcache-unloadablecachemanagerlistenerclass.xml");
        Configuration configuration = ConfigurationFactory.parseConfiguration(file);
        ConfigurationHelper configurationHelper = new ConfigurationHelper(manager, configuration);

        //Check CacheManagerEventListener
        CacheManagerEventListener listener = null;
        try {
            listener = configurationHelper.createCacheManagerEventListener(null);
            fail();
        } catch (CacheException e) {
            //expected
        }
    }

    /**
     * Positive and negative Tests for setting a list of CacheEventListeners in the configuration
     */
    @Test
    public void testLoadConfigurationFromFileCountingCacheListener() throws Exception {
        File file = new File(TEST_CONFIG_DIR + "ehcache-countinglisteners.xml");
        Configuration configuration = ConfigurationFactory.parseConfiguration(file);
        ConfigurationHelper configurationHelper = new ConfigurationHelper(manager, configuration);

        //Check CacheManagerEventListener
        Class actualClass = configurationHelper.createCacheManagerEventListener(null).getClass();
        assertEquals(CountingCacheManagerEventListener.class, actualClass);

        //Check caches. Configuration should have completed
        assertEquals(10, configurationHelper.createCaches().size());

        //Should have null and counting
        Ehcache sampleCache1 = configurationHelper.createCacheFromName("sampleCache1");
        Set registeredListeners = sampleCache1.getCacheEventNotificationService().getCacheEventListeners();
        assertEquals(2, registeredListeners.size());

        //Should have null and counting
        Ehcache sampleCache2 = configurationHelper.createCacheFromName("sampleCache2");
        registeredListeners = sampleCache2.getCacheEventNotificationService().getCacheEventListeners();
        assertEquals(1, registeredListeners.size());

        //Should have null and counting
        Ehcache sampleCache3 = configurationHelper.createCacheFromName("sampleCache3");
        registeredListeners = sampleCache3.getCacheEventNotificationService().getCacheEventListeners();
        assertEquals(1, registeredListeners.size());

        //Should have none. None set.
        Ehcache footerPageCache = configurationHelper.createCacheFromName("FooterPageCache");
        registeredListeners = footerPageCache.getCacheEventNotificationService().getCacheEventListeners();
        assertEquals(0, registeredListeners.size());

        //Should have one. null listener set.
        Ehcache persistentLongExpiryIntervalCache = configurationHelper.createCacheFromName("persistentLongExpiryIntervalCache");
        registeredListeners = persistentLongExpiryIntervalCache.getCacheEventNotificationService()
                .getCacheEventListeners();
        assertEquals(1, registeredListeners.size());
    }

    /**
     * Tests for Distributed Cache config
     */
    @Test
    public void testLoadConfigurationFromFileDistribution() throws Exception {
        File file = new File(TEST_CONFIG_DIR + "distribution/ehcache-distributed.xml");
        Configuration configuration = ConfigurationFactory.parseConfiguration(file);
        ConfigurationHelper configurationHelper = new ConfigurationHelper(manager, configuration);

        //Check CacheManagerPeerProvider
        Map<String, CacheManagerPeerProvider> peerProviders = configurationHelper.createCachePeerProviders();
        CacheManagerPeerProvider peerProvider = peerProviders.get("RMI");


        //Check TTL
        assertTrue(peerProvider instanceof MulticastRMICacheManagerPeerProvider);
        assertEquals(Integer.valueOf(0), ((MulticastRMICacheManagerPeerProvider) peerProvider).getHeartBeatSender().getTimeToLive());


        //check CacheManagerPeerListener
        Map<String, CacheManagerPeerListener> peerListeners = configurationHelper.createCachePeerListeners();

        //should be one in this config
        for (CacheManagerPeerListener peerListener : peerListeners.values()) {
            assertTrue(peerListener instanceof RMICacheManagerPeerListener);
        }

        //Check caches. Configuration should have completed
        assertEquals(5, configurationHelper.createCaches().size());

        Ehcache sampleCache1 = configurationHelper.createCacheFromName("sampleCache1");
        Set listeners = sampleCache1.getCacheEventNotificationService().getCacheEventListeners();
        assertEquals(2, listeners.size());
        for (Iterator iterator = listeners.iterator(); iterator.hasNext();) {
            CacheEventListener cacheEventListener = (CacheEventListener) iterator.next();
            assertTrue(cacheEventListener instanceof RMIAsynchronousCacheReplicator || cacheEventListener
                    instanceof CountingCacheEventListener);
        }

        BootstrapCacheLoader bootstrapCacheLoader = sampleCache1.getBootstrapCacheLoader();
        assertNotNull(bootstrapCacheLoader);
        assertEquals(RMIBootstrapCacheLoader.class, bootstrapCacheLoader.getClass());
        assertEquals(true, bootstrapCacheLoader.isAsynchronous());
        assertEquals(5000000, ((RMIBootstrapCacheLoader) bootstrapCacheLoader).getMaximumChunkSizeBytes());

    }

    /**
     * The following should give defaults of true and 5000000
     * <bootstrapCacheLoaderFactory class="net.sf.ehcache.distribution.RMIBootstrapCacheLoaderFactory" />
     */
    @Test
    public void testLoadConfigurationFromFileNoBootstrapPropertiesSet() throws Exception {
        File file = new File(TEST_CONFIG_DIR + "distribution/ehcache-distributed.xml");
        Configuration configuration = ConfigurationFactory.parseConfiguration(file);
        ConfigurationHelper configurationHelper = new ConfigurationHelper(manager, configuration);
        Ehcache sampleCache3 = configurationHelper.createCacheFromName("sampleCache3");

        BootstrapCacheLoader bootstrapCacheLoader = ((Cache) sampleCache3).getBootstrapCacheLoader();
        assertEquals(true, bootstrapCacheLoader.isAsynchronous());
        assertEquals(5000000, ((RMIBootstrapCacheLoader) bootstrapCacheLoader).getMaximumChunkSizeBytes());
    }

    /**
     * The following should give defaults of true and 5000000
     * <bootstrapCacheLoaderFactory class="net.sf.ehcache.distribution.RMIBootstrapCacheLoaderFactory"
     * properties="bootstrapAsynchronously=false, maximumChunkSizeBytes=10000"/>
     */
    @Test
    public void testLoadConfigurationFromFileWithSpecificPropertiesSet() throws Exception {
        File file = new File(TEST_CONFIG_DIR + "distribution/ehcache-distributed.xml");
        Configuration configuration = ConfigurationFactory.parseConfiguration(file);
        ConfigurationHelper configurationHelper = new ConfigurationHelper(manager, configuration);
        Ehcache sampleCache4 = configurationHelper.createCacheFromName("sampleCache4");

        BootstrapCacheLoader bootstrapCacheLoader = ((Cache) sampleCache4).getBootstrapCacheLoader();
        assertEquals(false, bootstrapCacheLoader.isAsynchronous());
        assertEquals(10000, ((RMIBootstrapCacheLoader) bootstrapCacheLoader).getMaximumChunkSizeBytes());
    }

    /**
     * Tests that the loader successfully loads from ehcache-nodefault.xml
     * given as a {@link File}
     * <p/>
     * <defaultCache
     * maxElementsInMemory="10000"
     * eternal="false"
     * timeToIdleSeconds="120"
     * timeToLiveSeconds="120"
     * overflowToDisk="true"
     * />
     */
    @Test
    public void testLoadConfigurationFromFileNoDefault() throws Exception {
        File file = new File(TEST_CONFIG_DIR + "ehcache-nodefault.xml");
        Configuration configuration = ConfigurationFactory.parseConfiguration(file);
        ConfigurationHelper configurationHelper = new ConfigurationHelper(manager, configuration);

        assertEquals(null, configurationHelper.getConfigurationBean().getName());
        assertEquals(true, configurationHelper.getConfigurationBean().getUpdateCheck());
        assertEquals(Configuration.Monitoring.AUTODETECT, configurationHelper.getConfigurationBean().getMonitoring());

        //Check disk path  <diskStore path="/tmp"/>
        assertEquals(System.getProperty("java.io.tmpdir"), configurationHelper.getDiskStorePath());

        //Check default cache
        try {
            Ehcache defaultCache = configurationHelper.createDefaultCache();
            Assert.assertNull(defaultCache);
        } catch (CacheException e) {
            fail("Calling create default cache when no default cache config specified should return null and not fail");
        }

        //Check caches
        assertEquals(4, configurationHelper.createCaches().size());

        //  <cache name="sampleCache1"
        //  maxElementsInMemory="10000"
        //  eternal="false"
        //  timeToIdleSeconds="300"
        //  timeToLiveSeconds="600"
        //  overflowToDisk="true"
        //  />
        Ehcache sampleCache1 = configurationHelper.createCacheFromName("sampleCache1");
        Ehcache sampleCache4 = configurationHelper.createCacheFromName("sampleCache4");
        assertEquals("net.sf.ehcache.transaction.manager.DefaultTransactionManagerLookup",
                configuration.getTransactionManagerLookupConfiguration().getFullyQualifiedClassPath());
        assertEquals("sampleCache1", sampleCache1.getName());
        assertEquals(false, sampleCache1.getCacheConfiguration().isEternal());
        assertEquals(300, sampleCache1.getCacheConfiguration().getTimeToIdleSeconds());
        assertEquals(600, sampleCache1.getCacheConfiguration().getTimeToLiveSeconds());
        assertEquals(true, sampleCache1.getCacheConfiguration().isOverflowToDisk());
        assertEquals(CacheConfiguration.TransactionalMode.OFF, sampleCache1.getCacheConfiguration().getTransactionalMode());
        assertEquals(false, sampleCache1.getCacheConfiguration().isXaStrictTransactional());
        assertEquals("sampleCache4", sampleCache4.getName());
        assertEquals(CacheConfiguration.TransactionalMode.XA_STRICT, sampleCache4.getCacheConfiguration().getTransactionalMode());
        assertEquals(true, sampleCache4.getCacheConfiguration().isXaStrictTransactional());
    }

    /**
     * Tests that the loader successfully loads from ehcache-nodefault.xml
     * given as a {@link File}
     * <p/>
     * /**
     * Tests that the loader successfully loads from ehcache-nodefault.xml
     * given as a {@link File}
     * <p/>
     * <cache name="sampleCacheNoOptionalAttributes"
     * maxElementsInMemory="1000"
     * eternal="true"
     * overflowToDisk="false"
     * />
     */
    @Test
    public void testDefaultValues() throws Exception {
        File file = new File(TEST_CONFIG_DIR + "ehcache-nodefault.xml");
        Configuration configuration = ConfigurationFactory.parseConfiguration(file);
        ConfigurationHelper configurationHelper = new ConfigurationHelper(manager, configuration);

        Ehcache sampleCacheNoOptionalAttributes = configurationHelper.createCacheFromName("sampleCacheNoOptionalAttributes");
        assertEquals("sampleCacheNoOptionalAttributes", sampleCacheNoOptionalAttributes.getName());
        assertEquals(1000, sampleCacheNoOptionalAttributes.getCacheConfiguration().getMaxElementsInMemory());
        assertEquals(true, sampleCacheNoOptionalAttributes.getCacheConfiguration().isEternal());
        assertEquals(false, sampleCacheNoOptionalAttributes.getCacheConfiguration().isOverflowToDisk());
        assertEquals(0, sampleCacheNoOptionalAttributes.getCacheConfiguration().getTimeToIdleSeconds());
        assertEquals(0, sampleCacheNoOptionalAttributes.getCacheConfiguration().getTimeToLiveSeconds());
        assertEquals(false, sampleCacheNoOptionalAttributes.getCacheConfiguration().isDiskPersistent());
        assertEquals(120, sampleCacheNoOptionalAttributes.getCacheConfiguration().getDiskExpiryThreadIntervalSeconds());
        assertEquals(1, sampleCacheNoOptionalAttributes.getCacheConfiguration().getDiskAccessStripes());
    }


    /**
     * Tests that the loader successfully loads from ehcache-nodisk.xml
     * given as a {@link File}
     * <p/>
     * <defaultCache
     * maxElementsInMemory="10000"
     * eternal="false"
     * timeToIdleSeconds="120"
     * timeToLiveSeconds="120"
     * overflowToDisk="false"
     * <p/>
     */
    @Test
    public void testLoadConfigurationFromFileNoDisk() throws Exception {
        File file = new File(TEST_CONFIG_DIR + "ehcache-nodisk.xml");
        Configuration configuration = ConfigurationFactory.parseConfiguration(file);
        ConfigurationHelper configurationHelper = new ConfigurationHelper(manager, configuration);

        assertEquals(null, configurationHelper.getConfigurationBean().getName());
        assertEquals(true, configurationHelper.getConfigurationBean().getUpdateCheck());
        assertEquals(Configuration.Monitoring.AUTODETECT, configurationHelper.getConfigurationBean().getMonitoring());

        //Check disk path  <diskStore path="/tmp"/>
        assertEquals(null, configurationHelper.getDiskStorePath());

        //Check default cache
        Ehcache defaultCache = configurationHelper.createDefaultCache();
        assertEquals("default", defaultCache.getName());
        assertEquals(false, defaultCache.getCacheConfiguration().isEternal());
        assertEquals(5L, defaultCache.getCacheConfiguration().getTimeToIdleSeconds());
        assertEquals(10, defaultCache.getCacheConfiguration().getTimeToLiveSeconds());
        assertEquals(false, defaultCache.getCacheConfiguration().isOverflowToDisk());

        //Check caches
        assertEquals(2, configurationHelper.createCaches().size());

        //  <cache name="sampleCache1"
        //  maxElementsInMemory="10000"
        //  eternal="false"
        //  timeToIdleSeconds="300"
        //  timeToLiveSeconds="600"
        //  overflowToDisk="true"
        //  />
        Ehcache sampleCache1 = configurationHelper.createCacheFromName("sampleCache1");
        assertEquals("sampleCache1", sampleCache1.getName());
        assertEquals(false, sampleCache1.getCacheConfiguration().isEternal());
        assertEquals(360, sampleCache1.getCacheConfiguration().getTimeToIdleSeconds());
        assertEquals(1000, sampleCache1.getCacheConfiguration().getTimeToLiveSeconds());
        assertEquals(false, sampleCache1.getCacheConfiguration().isOverflowToDisk());
    }

    /**
     * Tests the default values for optional attributes
     * <p/>
     * <!-- Sample cache. Optional attributes are removed -->
     * <cache name="sampleRequiredAttributesOnly"
     * maxElementsInMemory="1000"
     * eternal="true"
     * overflowToDisk="false"
     * />
     * <p/>
     * No disk store path specified as disk store not being used
     * />
     */
    @Test
    public void testOptionalAttributeDefaultValues() throws Exception {
        File file = new File(TEST_CONFIG_DIR + "ehcache-nodisk.xml");
        Configuration configuration = ConfigurationFactory.parseConfiguration(file);
        ConfigurationHelper configurationHelper = new ConfigurationHelper(manager, configuration);

        assertEquals(null, configurationHelper.getDiskStorePath());


        //  <cache name="sampleCache1"
        //  maxElementsInMemory="10000"
        //  eternal="false"
        //  timeToIdleSeconds="300"
        //  timeToLiveSeconds="600"
        //  overflowToDisk="true"
        //  />
        Ehcache sampleCache1 = configurationHelper.createCacheFromName("sampleCache1");
        assertEquals("sampleCache1", sampleCache1.getName());
        assertEquals(false, sampleCache1.getCacheConfiguration().isEternal());
        assertEquals(360, sampleCache1.getCacheConfiguration().getTimeToIdleSeconds());
        assertEquals(1000, sampleCache1.getCacheConfiguration().getTimeToLiveSeconds());
        assertEquals(false, sampleCache1.getCacheConfiguration().isOverflowToDisk());
    }

    /**
     * Regression test for bug 1432074 - NullPointer on RMICacheManagerPeerProviderFactory
     * If manual peer provider configuration is selected then an info message should be
     * logged if there is no list.
     */
    @Test
    public void testEmptyPeerListManualDistributedConfiguration() {
        Configuration config = ConfigurationFactory.parseConfiguration(
                new File(TEST_CONFIG_DIR + "distribution/ehcache-manual-distributed3.xml")).name("new-name");
        CacheManager cacheManager = new CacheManager(config);
        assertEquals(0, cacheManager.getCacheManagerPeerProvider("RMI")
                .listRemoteCachePeers(cacheManager.getCache("sampleCache1")).size());
        cacheManager.shutdown();

    }


    /**
     * Tests that the loader successfully loads from ehcache.xml
     * given as an {@link URL}.
     * <p/>
     * is found first
     * <p/>
     * <defaultCache
     * maxElementsInMemory="10"
     * eternal="false"
     * timeToIdleSeconds="5"
     * timeToLiveSeconds="10"
     * overflowToDisk="true"
     * />
     */
    @Test
    public void testLoadConfigurationFromURL() throws Exception {
        URL url = getClass().getResource("/ehcache.xml");
        testDefaultConfiguration(url);
    }

    /**
     * Exposes a bug where the default configuration could not be loaded from a Jar URL
     * (a common scenario when ehcache is deployed, and always used for failsafe config).
     *
     * @throws Exception When the test fails.
     */
    @Test
    public void testLoadConfigurationFromJarURL() throws Exception {

        // first, create the jar
        File tempJar = createTempConfigJar();

        // convert it to a URL
        URL tempUrl = tempJar.toURI().toURL();

        // create a jar url that points to the configuration file
        String entry = "jar:" + tempUrl + "!/ehcache.xml";

        // create a URL object from the string, going through the URI class so it's encoded
        URL entryUrl = new URI(entry).toURL();

        testDefaultConfiguration(entryUrl);
    }

    /**
     * Given a URL, parse the configuration and test that the config read corresponds
     * to that which exists in the ehcache.xml file.
     *
     * @param url The URL to load.
     */
    private void testDefaultConfiguration(URL url) {
        Configuration configuration = ConfigurationFactory.parseConfiguration(url);
        ConfigurationHelper configurationHelper = new ConfigurationHelper(manager, configuration);

        //Check disk path missing in test ehcache.xml"/>
        assertEquals(System.getProperty("java.io.tmpdir"), configurationHelper.getDiskStorePath());

        //Check default cache
        Ehcache defaultCache = configurationHelper.createDefaultCache();
        assertEquals("default", defaultCache.getName());
        assertEquals(false, defaultCache.getCacheConfiguration().isEternal());
        assertEquals(5L, defaultCache.getCacheConfiguration().getTimeToIdleSeconds());
        assertEquals(10, defaultCache.getCacheConfiguration().getTimeToLiveSeconds());
        assertEquals(true, defaultCache.getCacheConfiguration().isOverflowToDisk());

        //Check caches
        assertEquals(CACHES_IN_TEST_EHCACHE, configurationHelper.createCaches().size());

        //  <cache name="sampleCache1"
        //  maxElementsInMemory="10000"
        //  eternal="false"
        //  timeToIdleSeconds="300"
        //  timeToLiveSeconds="600"
        //  overflowToDisk="true"
        //  />
        Ehcache sampleCache1 = configurationHelper.createCacheFromName("sampleCache1");
        assertEquals("sampleCache1", sampleCache1.getName());
        assertEquals(false, sampleCache1.getCacheConfiguration().isEternal());
        assertEquals(360, sampleCache1.getCacheConfiguration().getTimeToIdleSeconds());
        assertEquals(1000, sampleCache1.getCacheConfiguration().getTimeToLiveSeconds());
        assertEquals(true, sampleCache1.getCacheConfiguration().isOverflowToDisk());
    }

    /**
     * Creates a jar file that contains only ehcache.xml (a supplied configuration file).
     *
     * @return The jar file created with the configuration file as its only entry.
     * @throws IOException If the jar could not be created.
     */
    private File createTempConfigJar() throws IOException, FileNotFoundException {
        File tempJar = File.createTempFile("config_", ".jar");
        tempJar.deleteOnExit();

        // write the default config to the jar
        JarOutputStream jos = null;
        try {
            jos = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(tempJar)));

            jos.putNextEntry(new JarEntry("ehcache.xml"));

            InputStream defaultCfg = null;
            try {
                defaultCfg = new BufferedInputStream(getClass().getResource("/ehcache.xml").openStream());
                byte[] buf = new byte[1024];
                int read = 0;
                while ((read = defaultCfg.read(buf)) > 0) {
                    jos.write(buf, 0, read);
                }
            } finally {
                try {
                    if (defaultCfg != null) {
                        defaultCfg.close();
                    }
                } catch (IOException ioEx) {
                    // swallow this exception
                }
            }

        } finally {
            try {
                if (jos != null) {
                    jos.closeEntry();

                    jos.flush();
                    jos.close();
                }
            } catch (IOException ioEx) {
                // swallow this exception
            }
        }

        return tempJar;
    }

    /**
     * Tests that the loader successfully loads from ehcache.xml
     * given as a {@link InputStream}
     * <p/>
     * <defaultCache
     * maxElementsInMemory="10000"
     * eternal="false"
     * timeToIdleSeconds="120"
     * timeToLiveSeconds="120"
     * overflowToDisk="true"
     * />
     */
    @Test
    public void testLoadConfigurationFromInputStream() throws Exception {
        InputStream fis = new FileInputStream(new File(SRC_CONFIG_DIR + "ehcache.xml").getAbsolutePath());
        ConfigurationHelper configurationHelper;
        try {
            Configuration configuration = ConfigurationFactory.parseConfiguration(fis);
            configurationHelper = new ConfigurationHelper(manager, configuration);
        } finally {
            fis.close();
        }

        assertEquals(null, configurationHelper.getConfigurationBean().getName());
        assertEquals(true, configurationHelper.getConfigurationBean().getUpdateCheck());
        assertEquals(Configuration.Monitoring.AUTODETECT, configurationHelper.getConfigurationBean().getMonitoring());

        //Check disk path  <diskStore path="/tmp"/>
        assertEquals(System.getProperty("java.io.tmpdir"), configurationHelper.getDiskStorePath());

        //Check default cache
        Ehcache defaultCache = configurationHelper.createDefaultCache();
        assertEquals("default", defaultCache.getName());
        assertEquals(false, defaultCache.getCacheConfiguration().isEternal());
        assertEquals(120, defaultCache.getCacheConfiguration().getTimeToIdleSeconds());
        assertEquals(120, defaultCache.getCacheConfiguration().getTimeToLiveSeconds());
        assertEquals(Strategy.LOCALTEMPSWAP, defaultCache.getCacheConfiguration().getPersistenceConfiguration().getStrategy());

        //Check caches
        assertEquals(6, configurationHelper.createCaches().size());

        //  <cache name="sampleCache1"
        //  maxElementsInMemory="10000"
        //  eternal="false"
        //  timeToIdleSeconds="300"
        //  timeToLiveSeconds="600"
        //  overflowToDisk="true"
        //  />
        Ehcache sampleCache1 = configurationHelper.createCacheFromName("sampleCache1");
        assertEquals("sampleCache1", sampleCache1.getName());
        assertEquals(false, sampleCache1.getCacheConfiguration().isEternal());
        assertEquals(300, sampleCache1.getCacheConfiguration().getTimeToIdleSeconds());
        assertEquals(600, sampleCache1.getCacheConfiguration().getTimeToLiveSeconds());
        assertEquals(Strategy.LOCALTEMPSWAP, sampleCache1.getCacheConfiguration().getPersistenceConfiguration().getStrategy());
    }

    /**
     * Tests that the loader successfully loads from ehcache-failsafe.xml
     * found in the classpath.
     * ehcache.xml should be found in the classpath. In our ant configuration
     * this should be from build/classes/ehcache-failsafe.xml
     * <p/>
     * We delete ehcache.xml from build/test-classes/ first, as failsafe only
     * kicks in when ehcache.xml is not in the classpath.
     * <p/>
     * <defaultCache
     * maxElementsInMemory="10000"
     * eternal="false"
     * timeToIdleSeconds="120"
     * timeToLiveSeconds="120"
     * overflowToDisk="true"
     * />
     */
    @Test
    public void testLoadConfigurationFromFailsafe() throws Exception {
        try {
            File file = new File(AbstractCacheTest.TEST_CLASSES_DIR + "ehcache.xml");
            file.renameTo(new File(AbstractCacheTest.TEST_CLASSES_DIR + "hideehcache.xml"));
            Configuration configuration = ConfigurationFactory.parseConfiguration();
            ConfigurationHelper configurationHelper = new ConfigurationHelper(manager, configuration);

            assertEquals(null, configurationHelper.getConfigurationBean().getName());
            assertEquals(true, configurationHelper.getConfigurationBean().getUpdateCheck());
            assertEquals(Configuration.Monitoring.AUTODETECT, configurationHelper.getConfigurationBean().getMonitoring());

            //Check disk path  <diskStore path="/tmp"/>
            assertEquals(System.getProperty("java.io.tmpdir"), configurationHelper.getDiskStorePath());

            //Check default cache
            Ehcache defaultCache = configurationHelper.createDefaultCache();
            assertEquals("default", defaultCache.getName());
            assertEquals(false, defaultCache.getCacheConfiguration().isEternal());
            assertEquals(120, defaultCache.getCacheConfiguration().getTimeToIdleSeconds());
            assertEquals(120, defaultCache.getCacheConfiguration().getTimeToLiveSeconds());
            assertEquals(Strategy.LOCALTEMPSWAP, defaultCache.getCacheConfiguration().getPersistenceConfiguration().getStrategy());

            //Check caches
            assertEquals(0, configurationHelper.createCaches().size());
        } finally {
            //Put ehcache.xml back
            File hiddenFile = new File(AbstractCacheTest.TEST_CLASSES_DIR + "hideehcache.xml");
            hiddenFile.renameTo(new File(AbstractCacheTest.TEST_CLASSES_DIR + "ehcache.xml"));
        }

    }

    /**
     * Make sure that the empty Configuration constructor remains public for those wishing to create CacheManagers
     * purely programmatically.
     */
    @Test
    public void testCreateEmptyConfiguration() {
        Configuration configuration = new Configuration();
    }


    /**
     * Tests that you cannot use the name default for a cache.
     */
    @Test
    public void testLoadConfigurationFromInvalidXMLFileWithDefaultCacheNameUsed() throws Exception {
        File file = new File(TEST_CONFIG_DIR + "ehcache-withdefaultset.xml");
        try {
            Configuration configuration = ConfigurationFactory.parseConfiguration(file);
        } catch (CacheException e) {
            assertTrue(e.getMessage().contains("The Default Cache has already been configured"));
        }

    }


    /**
     * Tests replacement in the config file.
     */
    @Test
    public void testLoadConfigurationWithReplacement() throws Exception {
        System.setProperty("multicastGroupPort", "4446");
        System.setProperty("serverAndPort", "server.com:9510");
        File file = new File(TEST_CONFIG_DIR + "ehcache-replacement.xml");
        Configuration configuration = ConfigurationFactory.parseConfiguration(file);
        ConfigurationHelper configurationHelper = new ConfigurationHelper(manager, configuration);


        //Check disk path  <diskStore path="/tmp"/>
        assertNotSame(System.getProperty("java.io.tmpdir"), configurationHelper.getDiskStorePath());
        assertTrue(configuration.getCacheManagerPeerProviderFactoryConfiguration().get(0)
                .getProperties().indexOf("multicastGroupPort=4446") != -1);


    }


    /**
     * Fun with replaceAll which clobbers \\ by default!
     */
    @Test
    public void testPathExpansionAndReplacement() throws Exception {

        String configuration = "This is my ${basedir}.";
        String trimmedToken = "basedir";
        String property = "D:\\sonatype\\workspace\\nexus-aggregator\\nexus\\nexus-app";
        LOG.info("Property: " + property);
        LOG.info("configuration is: " + configuration);
        String propertyWithQuotesProtected = Matcher.quoteReplacement(property);
        configuration = configuration.replaceAll("\\$\\{" + trimmedToken + "\\}", propertyWithQuotesProtected);
        assertTrue(configuration.contains(property));
        LOG.info("configuration is: " + configuration);


    }


    /**
     * Tests the property token extraction logic
     */
    @Test
    public void testMatchPropertyTokensProperlyFormed() {
        String example = "<cacheManagerPeerProviderFactory class=\"net.sf.ehcache.distribution.RMICacheManagerPeerProviderFactory\"" +
                "properties=\"peerDiscovery=automatic, " +
                "multicastGroupAddress=${multicastAddress}, " +
                "multicastGroupPort=4446, timeToLive=1\"/>";
        Set propertyTokens = ConfigurationFactory.extractPropertyTokens(example);
        assertEquals(1, propertyTokens.size());
        String firstPropertyToken = (String) (propertyTokens.toArray())[0];
        assertEquals("${multicastAddress}", firstPropertyToken);
    }

    /**
     * Tests the property token extraction logic
     */
    @Test
    public void testMatchPropertyTokensProperlyFormedUrl() {
        String example = "<terracottaConfig url=\"${serverAndPort}\"/>";
        Set propertyTokens = ConfigurationFactory.extractPropertyTokens(example);
        assertEquals(1, propertyTokens.size());
        String firstPropertyToken = (String) (propertyTokens.toArray())[0];
        assertEquals("${serverAndPort}", firstPropertyToken);
    }


    /**
     * Tests the property token extraction logic
     */
    @Test
    public void testMatchPropertyTokensProperlyFormedTwo() {
        String example = "<cacheManagerPeerProviderFactory class=\"net.sf.ehcache.distribution.RMICacheManagerPeerProviderFactory\"" +
                "properties=\"peerDiscovery=automatic, " +
                "multicastGroupAddress=${multicastAddress}\n, " +
                "multicastGroupPort=4446, timeToLive=${multicastAddress}\"/>";
        Set propertyTokens = ConfigurationFactory.extractPropertyTokens(example);
        assertEquals(1, propertyTokens.size());
        String firstPropertyToken = (String) (propertyTokens.toArray())[0];
        assertEquals("${multicastAddress}", firstPropertyToken);
    }


    /**
     * Tests the property token extraction logic
     */
    @Test
    public void testMatchPropertyTokensProperlyFormedTwoUnique() {
        String example = "<cacheManagerPeerProviderFactory class=\"net.sf.ehcache.distribution.RMICacheManagerPeerProviderFactory\"" +
                "properties=\"peerDiscovery=automatic, " +
                "multicastGroupAddress=${multicastAddress}\n, " +
                "multicastGroupPort=4446, timeToLive=${multicastAddress1}\"/>";
        Set propertyTokens = ConfigurationFactory.extractPropertyTokens(example);
        assertEquals(2, propertyTokens.size());
    }

    /**
     * If you leave off the } then no match.
     */
    @Test
    public void testMatchPropertyTokensNotClosed() {
        String example = "<cacheManagerPeerProviderFactory class=\"net.sf.ehcache.distribution.RMICacheManagerPeerProviderFactory\"" +
                "properties=\"peerDiscovery=automatic, " +
                "multicastGroupAddress=${multicastAddress\n, " +
                "multicastGroupPort=4446, timeToLive=${multicastAddress\"/>";
        Set propertyTokens = ConfigurationFactory.extractPropertyTokens(example);
        assertEquals(0, propertyTokens.size());
    }

    @Test
    public void testCopyConfiguration() {
        File file = new File(TEST_CONFIG_DIR + "ehcache-copy.xml");
        Configuration configuration = ConfigurationFactory.parseConfiguration(file);
        ConfigurationHelper configurationHelper = new ConfigurationHelper(manager, configuration);

        Ehcache copyOnReadCache = configurationHelper.createCacheFromName("copyOnReadCache");
        assertTrue(copyOnReadCache.getCacheConfiguration().isCopyOnRead());
        assertFalse(copyOnReadCache.getCacheConfiguration().isCopyOnWrite());
        assertNotNull(copyOnReadCache.getCacheConfiguration().getCopyStrategy());
        assertTrue(copyOnReadCache.getCacheConfiguration().getCopyStrategy() instanceof ReadWriteSerializationCopyStrategy);

        Ehcache copyOnWriteCache = configurationHelper.createCacheFromName("copyOnWriteCache");
        assertFalse(copyOnWriteCache.getCacheConfiguration().isCopyOnRead());
        assertTrue(copyOnWriteCache.getCacheConfiguration().isCopyOnWrite());
        assertNotNull(copyOnWriteCache.getCacheConfiguration().getCopyStrategy());
        assertTrue(copyOnWriteCache.getCacheConfiguration().getCopyStrategy() instanceof ReadWriteSerializationCopyStrategy);

        Ehcache copyCache = configurationHelper.createCacheFromName("copyCache");
        assertTrue(copyCache.getCacheConfiguration().isCopyOnRead());
        assertTrue(copyCache.getCacheConfiguration().isCopyOnWrite());
        assertNotNull(copyCache.getCacheConfiguration().getCopyStrategy());
        assertTrue(copyCache.getCacheConfiguration().getCopyStrategy() instanceof FakeCopyStrategy);

        try {
            Configuration config = ConfigurationFactory.parseConfiguration(new File(TEST_CONFIG_DIR + "ehcache-copy.xml")).name("new-cm");
            new CacheManager(config);
            fail("This should have thrown an Exception");
        } catch (Exception e) {
            if (!(e instanceof InvalidConfigurationException)) {
                e.printStackTrace();
                fail("Expected InvalidConfigurationException, but got " + e.getClass().getSimpleName() + ", msg: " + e.getMessage());
            }
        }

        file = new File(TEST_CONFIG_DIR + "ehcache-copy-tc.xml");
        configuration = ConfigurationFactory.parseConfiguration(file).name("new-cm");
        configurationHelper = new ConfigurationHelper(manager, configuration);

        Ehcache nonCopyCache = configurationHelper.createCacheFromName("nonCopyOnReadCacheTcTrue");
        assertFalse(nonCopyCache.getCacheConfiguration().isCopyOnRead());
        assertTrue(nonCopyCache.getCacheConfiguration().getTerracottaConfiguration().isCopyOnRead());

        Ehcache nonCopyCacheTc = configurationHelper.createCacheFromName("copyOnReadCacheTcFalse");
        assertTrue(nonCopyCacheTc.getCacheConfiguration().isCopyOnRead());
        assertFalse(nonCopyCacheTc.getCacheConfiguration().getTerracottaConfiguration().isCopyOnRead());

        Ehcache copyOnReadCacheTc = configurationHelper.createCacheFromName("copyOnReadCacheTc");
        assertTrue(copyOnReadCacheTc.getCacheConfiguration().isCopyOnRead());
        assertTrue(copyOnReadCacheTc.getCacheConfiguration().getTerracottaConfiguration().isCopyOnRead());
    }

    @Test
    public void testElementValueComparatorConfiguration() {
        File file = new File(TEST_CONFIG_DIR + "ehcache-comparator.xml");
        Configuration configuration = ConfigurationFactory.parseConfiguration(file);
        ConfigurationHelper configurationHelper = new ConfigurationHelper(manager, configuration);

        Ehcache cache = configurationHelper.createCacheFromName("cache");
        assertTrue(cache.getCacheConfiguration().getElementValueComparatorConfiguration().createElementComparatorInstance(cache.getCacheConfiguration())
                instanceof DefaultElementValueComparator);

        Ehcache cache2 = configurationHelper.createCacheFromName("cache2");
        assertTrue(cache2.getCacheConfiguration().getElementValueComparatorConfiguration().createElementComparatorInstance(cache.getCacheConfiguration())
                .getClass().equals(FakeElementValueComparator.class));
    }

    /**
     * Test named cachemanager, terracotta config, clustered caches
     */
    @Test
    public void testTerracottaConfiguration() {
        File file = new File(TEST_CONFIG_DIR + "terracotta/ehcache-terracotta.xml");
        Configuration configuration = ConfigurationFactory.parseConfiguration(file);
        ConfigurationHelper configurationHelper = new ConfigurationHelper(manager, configuration);

        assertEquals("tc", configurationHelper.getConfigurationBean().getName());
        assertEquals(false, configurationHelper.getConfigurationBean().getUpdateCheck());
        assertEquals(Configuration.Monitoring.AUTODETECT, configurationHelper.getConfigurationBean().getMonitoring());

        //Check default cache
        Ehcache defaultCache = configurationHelper.createDefaultCache();
        assertEquals("default", defaultCache.getName());
        assertEquals(false, defaultCache.getCacheConfiguration().isEternal());
        assertEquals(5, defaultCache.getCacheConfiguration().getTimeToIdleSeconds());
        assertEquals(10, defaultCache.getCacheConfiguration().getTimeToLiveSeconds());
        assertEquals(false, defaultCache.getCacheConfiguration().isOverflowToDisk());
        assertEquals(10, defaultCache.getCacheConfiguration().getMaxElementsInMemory());
        assertEquals(0, defaultCache.getCacheConfiguration().getMaxElementsOnDisk());
        assertEquals(true, defaultCache.getCacheConfiguration().isTerracottaClustered());
        assertEquals(true, defaultCache.getCacheConfiguration().getTerracottaConfiguration().getCoherentReads());

        //Check caches
        assertEquals(16, configurationHelper.createCaches().size());

        //  <cache name="clustered-1"
        //   maxElementsInMemory="1000"
        //   memoryStoreEvictionPolicy="LFU">
        //   <terracotta/>
        //  </cache>
        Ehcache sampleCache1 = configurationHelper.createCacheFromName("clustered-1");
        assertEquals("clustered-1", sampleCache1.getName());
        assertEquals(true, sampleCache1.getCacheConfiguration().isTerracottaClustered());
        assertEquals(TerracottaConfiguration.ValueMode.SERIALIZATION,
                sampleCache1.getCacheConfiguration().getTerracottaConfiguration().getValueMode());

        //  <cache name="clustered-2"
        //      maxElementsInMemory="1000"
        //            memoryStoreEvictionPolicy="LFU">
        //          <terracotta clustered="false"/>
        //   </cache>
        Ehcache sampleCache2 = configurationHelper.createCacheFromName("clustered-2");
        assertEquals("clustered-2", sampleCache2.getName());
        assertEquals(false, sampleCache2.getCacheConfiguration().isTerracottaClustered());
        assertEquals(TerracottaConfiguration.ValueMode.SERIALIZATION,
                sampleCache2.getCacheConfiguration().getTerracottaConfiguration().getValueMode());

        //  <cache name="clustered-3"
        //   maxElementsInMemory="1000"
        //   memoryStoreEvictionPolicy="LFU">
        //   <terracotta valueMode="serialization"/>
        //  </cache>
        Ehcache sampleCache3 = configurationHelper.createCacheFromName("clustered-3");
        assertEquals("clustered-3", sampleCache3.getName());
        assertEquals(true, sampleCache3.getCacheConfiguration().isTerracottaClustered());
        assertEquals(TerracottaConfiguration.ValueMode.SERIALIZATION,
                sampleCache3.getCacheConfiguration().getTerracottaConfiguration().getValueMode());

        //  <cache name="clustered-4"
        //   maxElementsInMemory="1000"
        //   memoryStoreEvictionPolicy="LFU">
        //   <terracotta valueMode="identity"/>
        //  </cache>
        Ehcache sampleCache4 = configurationHelper.createCacheFromName("clustered-4");
        assertEquals("clustered-4", sampleCache4.getName());
        assertEquals(true, sampleCache4.getCacheConfiguration().isTerracottaClustered());
        assertEquals(TerracottaConfiguration.ValueMode.IDENTITY,
                sampleCache4.getCacheConfiguration().getTerracottaConfiguration().getValueMode());

        //  <cache name="clustered-5"
        //   maxElementsInMemory="1000"
        //   memoryStoreEvictionPolicy="LFU">
        //   <terracotta coherentReads="false"/>
        //  </cache>
        Ehcache sampleCache5 = configurationHelper.createCacheFromName("clustered-5");
        assertEquals("clustered-5", sampleCache5.getName());
        assertEquals(true, sampleCache5.getCacheConfiguration().isTerracottaClustered());
        assertEquals(false,
                sampleCache5.getCacheConfiguration().getTerracottaConfiguration().getCoherentReads());

        //  <cache name="clustered-6"
        //   maxElementsInMemory="1000"
        //   memoryStoreEvictionPolicy="LFU">
        //   <terracotta orphanEviction="false"/>
        //  </cache>
        Ehcache sampleCache6 = configurationHelper.createCacheFromName("clustered-6");
        assertEquals("clustered-6", sampleCache6.getName());
        assertEquals(true, sampleCache6.getCacheConfiguration().isTerracottaClustered());
        assertEquals(false,
                sampleCache6.getCacheConfiguration().getTerracottaConfiguration().getOrphanEviction());

        //  <cache name="clustered-7"
        //   maxElementsInMemory="1000"
        //   memoryStoreEvictionPolicy="LFU">
        //   <terracotta orphanEvictionPeriod="42"/>
        //  </cache>
        Ehcache sampleCache7 = configurationHelper.createCacheFromName("clustered-7");
        assertEquals("clustered-7", sampleCache7.getName());
        assertEquals(true, sampleCache7.getCacheConfiguration().isTerracottaClustered());
        assertEquals(42,
                sampleCache7.getCacheConfiguration().getTerracottaConfiguration().getOrphanEvictionPeriod());

        //  <cache name="clustered-8"
        //   maxElementsInMemory="1000"
        //   memoryStoreEvictionPolicy="LFU">
        //   <terracotta localKeyCache="true"/>
        //  </cache>
        Ehcache sampleCache8 = configurationHelper.createCacheFromName("clustered-8");
        assertEquals("clustered-8", sampleCache8.getName());
        assertEquals(true, sampleCache8.getCacheConfiguration().isTerracottaClustered());
        assertEquals(true,
                sampleCache8.getCacheConfiguration().getTerracottaConfiguration().getLocalKeyCache());

        //  <cache name="clustered-9"
        //   maxElementsInMemory="1000"
        //   memoryStoreEvictionPolicy="LFU">
        //   <terracotta localKeyCache="true"/>
        //  </cache>
        Ehcache sampleCache9 = configurationHelper.createCacheFromName("clustered-9");
        assertEquals("clustered-9", sampleCache9.getName());
        assertEquals(true, sampleCache9.getCacheConfiguration().isTerracottaClustered());
        assertEquals(42,
                sampleCache9.getCacheConfiguration().getTerracottaConfiguration().getLocalKeyCacheSize());

        // assert default value is true always
        assertEquals(true, TerracottaConfiguration.DEFAULT_CACHE_COHERENT);

        Ehcache sampleCache10 = configurationHelper.createCacheFromName("clustered-10");
        assertEquals("clustered-10", sampleCache10.getName());
        assertEquals(true, sampleCache10.getCacheConfiguration().isTerracottaClustered());
        final boolean expectedDefault = TerracottaConfiguration.DEFAULT_CONSISTENCY_TYPE == Consistency.STRONG? true: false;
        assertEquals(expectedDefault,
                sampleCache10.getCacheConfiguration().getTerracottaConfiguration().isCoherent());

        Ehcache sampleCache11 = configurationHelper.createCacheFromName("clustered-11");
        assertEquals("clustered-11", sampleCache11.getName());
        assertEquals(true, sampleCache11.getCacheConfiguration().isTerracottaClustered());
        assertEquals(false,
                sampleCache11.getCacheConfiguration().getTerracottaConfiguration().isCoherent());

        Ehcache sampleCache12 = configurationHelper.createCacheFromName("clustered-12");
        assertEquals("clustered-12", sampleCache12.getName());
        assertEquals(true, sampleCache12.getCacheConfiguration().isTerracottaClustered());
        assertEquals(true,
                sampleCache12.getCacheConfiguration().getTerracottaConfiguration().isCoherent());

        // assert default value is false always
        assertEquals(false, TerracottaConfiguration.DEFAULT_SYNCHRONOUS_WRITES);

        Ehcache sampleCache13 = configurationHelper.createCacheFromName("clustered-13");
        assertEquals("clustered-13", sampleCache13.getName());
        assertEquals(true, sampleCache13.getCacheConfiguration().isTerracottaClustered());
        assertEquals(false,
                sampleCache13.getCacheConfiguration().getTerracottaConfiguration().isSynchronousWrites());

        Ehcache sampleCache14 = configurationHelper.createCacheFromName("clustered-14");
        assertEquals("clustered-14", sampleCache14.getName());
        assertEquals(true, sampleCache14.getCacheConfiguration().isTerracottaClustered());
        assertEquals(false,
                sampleCache14.getCacheConfiguration().getTerracottaConfiguration().isSynchronousWrites());

        Ehcache sampleCache15 = configurationHelper.createCacheFromName("clustered-15");
        assertEquals("clustered-15", sampleCache15.getName());
        assertEquals(true, sampleCache15.getCacheConfiguration().isTerracottaClustered());
        assertEquals(true,
                sampleCache15.getCacheConfiguration().getTerracottaConfiguration().isSynchronousWrites());

        // <terracottaConfig>
        //  <url>localhost:9510</url>
        // </terracottaConfig>
        TerracottaClientConfiguration tcConfig = configuration.getTerracottaConfiguration();
        assertNotNull(tcConfig);
        assertEquals("localhost:9510", tcConfig.getUrl());
    }


    /**
     * Test tc-config embedded in ehcache.xml
     */
    @Test
    public void testTerracottaEmbeddedConfig() {
        File file = new File(TEST_CONFIG_DIR + "terracotta/ehcache-tc-embedded.xml");
        Configuration configuration = ConfigurationFactory.parseConfiguration(file);
        ConfigurationHelper configurationHelper = new ConfigurationHelper(manager, configuration);

        assertEquals("tc", configurationHelper.getConfigurationBean().getName());
        assertEquals(false, configurationHelper.getConfigurationBean().getUpdateCheck());
        assertEquals(Configuration.Monitoring.AUTODETECT, configurationHelper.getConfigurationBean().getMonitoring());

        //Check default cache
        Ehcache defaultCache = configurationHelper.createDefaultCache();
        assertEquals("default", defaultCache.getName());
        assertEquals(false, defaultCache.getCacheConfiguration().isEternal());
        assertEquals(5, defaultCache.getCacheConfiguration().getTimeToIdleSeconds());
        assertEquals(10, defaultCache.getCacheConfiguration().getTimeToLiveSeconds());
        assertEquals(false, defaultCache.getCacheConfiguration().isOverflowToDisk());
        assertEquals(10, defaultCache.getCacheConfiguration().getMaxElementsInMemory());
        assertEquals(0, defaultCache.getCacheConfiguration().getMaxElementsOnDisk());
        assertEquals(true, defaultCache.getCacheConfiguration().isTerracottaClustered());

        //Check caches
        assertEquals(1, configurationHelper.createCaches().size());

        //  <cache name="clustered-1"
        //   maxElementsInMemory="1000"
        //   memoryStoreEvictionPolicy="LFU">
        //   <terracotta/>
        //  </cache>
        Ehcache sampleCache1 = configurationHelper.createCacheFromName("clustered-1");
        assertEquals("clustered-1", sampleCache1.getName());
        assertEquals(true, sampleCache1.getCacheConfiguration().isTerracottaClustered());
        assertEquals(TerracottaConfiguration.ValueMode.SERIALIZATION,
                sampleCache1.getCacheConfiguration().getTerracottaConfiguration().getValueMode());

        // <terracottaConfig>
        //  <tc-config> ... </tc-config>
        // </terracottaConfig>
        TerracottaClientConfiguration tcConfig = configuration.getTerracottaConfiguration();
        assertNotNull(tcConfig);
        assertEquals(null, tcConfig.getUrl());
        String embeddedConfig = tcConfig.getEmbeddedConfig();
        assertEquals("<tc:tc-config xmlns:tc=\"http://www.terracotta.org/config\"> " +
                "<servers> <server host=\"server1\" name=\"s1\"></server> " +
                "<server host=\"server2\" name=\"s2\"></server> </servers> " +
                "<clients> <logs>app/logs-%i</logs> </clients> </tc:tc-config>",
                removeLotsOfWhitespace(tcConfig.getEmbeddedConfig()));
    }

    @Test
    public void testTerracottaEmbeddedXsdConfig() {
        File file = new File(TEST_CONFIG_DIR
                + "terracotta/ehcache-tc-embedded-xsd.xml");
        Configuration configuration = ConfigurationFactory
                .parseConfiguration(file);
        ConfigurationHelper configurationHelper = new ConfigurationHelper(
                manager, configuration);

        assertEquals("tc", configurationHelper.getConfigurationBean().getName());
        assertEquals(false, configurationHelper.getConfigurationBean()
                .getUpdateCheck());
        assertEquals(Configuration.Monitoring.AUTODETECT, configurationHelper
                .getConfigurationBean().getMonitoring());

        // <terracottaConfig>
        // <tc-config> ... </tc-config>
        // </terracottaConfig>
        TerracottaClientConfiguration tcConfig = configuration
                .getTerracottaConfiguration();
        assertNotNull(tcConfig);
        assertEquals(null, tcConfig.getUrl());
        String embeddedConfig = tcConfig.getEmbeddedConfig();
        assertEquals(
                "<tc:tc-config xmlns:tc=\"http://www.terracotta.org/config\"> <servers> "
                        + "<server host=\"server1\" name=\"s1\"></server> "
                        + "<server host=\"server2\" name=\"s2\"></server> </servers> "
                        + "<clients> <logs>app/logs-%i</logs> </clients> </tc:tc-config>",
                removeLotsOfWhitespace(tcConfig.getEmbeddedConfig()));
    }

    /**
     * Test invalid combination of overflow to disk and terracotta ehcache.xml
     */
    @Test
    public void testTerracottaInvalidConfig1() {
        File file = new File(TEST_CONFIG_DIR + "terracotta/ehcache-terracotta-invalid1.xml");
        try {
            Configuration configuration = ConfigurationFactory.parseConfiguration(file);
            fail("expecting exception to be thrown");
        } catch (CacheException e) {
            assertTrue(e.getMessage().contains("overflowToDisk isn't supported for a clustered Terracotta cache"));
        }
    }

    /**
     * Test invalid combination of disk persistent and terracotta ehcache.xml
     */
    @Test
    public void testTerracottaInvalidConfig2() {
        File file = new File(TEST_CONFIG_DIR + "terracotta/ehcache-terracotta-invalid2.xml");
        try {
            Configuration configuration = ConfigurationFactory.parseConfiguration(file);
            fail("expecting exception to be thrown");
        } catch (CacheException e) {
            assertTrue(e.getMessage().contains("diskPersistent isn't supported for a clustered Terracotta cache"));
        }
    }

    /**
     * Test valid combination of replicated and terracotta ehcache.xml
     */
    @Test
    public void testTerracottaConfigRMIReplication() {
        File file = new File(TEST_CONFIG_DIR + "terracotta/ehcache-terracotta-rmi.xml");
        Configuration configuration = ConfigurationFactory.parseConfiguration(file);
        List configs = configuration.getCacheConfigurations().get("clustered").getCacheEventListenerConfigurations();
        assertEquals(1, configs.size());
        assertEquals(((CacheConfiguration.CacheEventListenerFactoryConfiguration) configs.get(0)).getFullyQualifiedClassPath(),
                RMICacheReplicatorFactory.class.getName());
    }

    /**
     * Test valid combination of replicated and terracotta ehcache.xml
     */
    @Test
    public void testTerracottaConfigJGroupsReplication() {
        File file = new File(TEST_CONFIG_DIR + "terracotta/ehcache-terracotta-jgroups.xml");
        Configuration configuration = ConfigurationFactory.parseConfiguration(file);
        List configs = configuration.getCacheConfigurations().get("clustered").getCacheEventListenerConfigurations();
        assertEquals(1, configs.size());
        assertEquals(((CacheConfiguration.CacheEventListenerFactoryConfiguration) configs.get(0)).getFullyQualifiedClassPath(),
                "net.sf.ehcache.distribution.JGroupsCacheReplicatorFactory");
    }

    /**
     * Test valid combination of replicated and terracotta ehcache.xml
     */
    @Test
    public void testTerracottaInvalidConfig5() {
        File file = new File(TEST_CONFIG_DIR + "terracotta/ehcache-terracotta-jms.xml");
        Configuration configuration = ConfigurationFactory.parseConfiguration(file);
        List configs = configuration.getCacheConfigurations().get("clustered").getCacheEventListenerConfigurations();
        assertEquals(1, configs.size());
        assertEquals(((CacheConfiguration.CacheEventListenerFactoryConfiguration) configs.get(0)).getFullyQualifiedClassPath(),
                "net.sf.ehcache.distribution.JMSCacheReplicatorFactory");
    }

    private String removeLotsOfWhitespace(String str) {
        return str.replace("\t", "").replace("\r", "").replace("\n", "").replaceAll("\\s+", " ");
    }

    @Test
    public void testMonitoringOn() {
        File file = new File(TEST_CONFIG_DIR + "ehcache-monitoring-on.xml");
        Configuration configuration = ConfigurationFactory.parseConfiguration(file);
        ConfigurationHelper configurationHelper = new ConfigurationHelper(manager, configuration);

        assertEquals(Configuration.Monitoring.ON, configurationHelper.getConfigurationBean().getMonitoring());
    }

    @Test
    public void testMonitoringOff() {
        File file = new File(TEST_CONFIG_DIR + "ehcache-monitoring-off.xml");
        Configuration configuration = ConfigurationFactory.parseConfiguration(file);
        ConfigurationHelper configurationHelper = new ConfigurationHelper(manager, configuration);

        assertEquals(Configuration.Monitoring.OFF, configurationHelper.getConfigurationBean().getMonitoring());
    }

    @Test
    public void testMonitoringAutodetect() {
        File file = new File(TEST_CONFIG_DIR + "ehcache-monitoring-autodetect.xml");
        Configuration configuration = ConfigurationFactory.parseConfiguration(file);
        ConfigurationHelper configurationHelper = new ConfigurationHelper(manager, configuration);

        assertEquals(Configuration.Monitoring.AUTODETECT, configurationHelper.getConfigurationBean().getMonitoring());
    }

    /**
     * Test cache writer config
     */
    @Test
    public void testWriterConfig() {
        File file = new File(TEST_CONFIG_DIR + "ehcache-writer.xml");
        Configuration configuration = ConfigurationFactory.parseConfiguration(file);
        ConfigurationHelper configurationHelper = new ConfigurationHelper(manager, configuration);

        CacheWriterConfiguration defaultCacheWriterConfig = new CacheWriterConfiguration();

        CacheConfiguration configDefault = configurationHelper.getConfigurationBean().getDefaultCacheConfiguration();
        assertEquals(false, configDefault.isEternal());
        assertEquals(5, configDefault.getTimeToIdleSeconds());
        assertEquals(10, configDefault.getTimeToLiveSeconds());
        assertEquals(false, configDefault.isOverflowToDisk());
        assertEquals(10, configDefault.getMaxElementsInMemory());
        assertNotNull(configDefault.getCacheWriterConfiguration());
        assertEquals(defaultCacheWriterConfig.getWriteMode(), configDefault.getCacheWriterConfiguration().getWriteMode());
        assertEquals(defaultCacheWriterConfig.getCacheWriterFactoryConfiguration(), configDefault.getCacheWriterConfiguration()
                .getCacheWriterFactoryConfiguration());
        assertEquals(defaultCacheWriterConfig.getNotifyListenersOnException(), configDefault.getCacheWriterConfiguration()
                .getNotifyListenersOnException());
        assertEquals(defaultCacheWriterConfig.getMaxWriteDelay(), configDefault.getCacheWriterConfiguration().getMaxWriteDelay());
        assertEquals(defaultCacheWriterConfig.getRateLimitPerSecond(), configDefault.getCacheWriterConfiguration().getRateLimitPerSecond());
        assertEquals(defaultCacheWriterConfig.getWriteCoalescing(), configDefault.getCacheWriterConfiguration().getWriteCoalescing());
        assertEquals(defaultCacheWriterConfig.getWriteBatching(), configDefault.getCacheWriterConfiguration().getWriteBatching());
        assertEquals(defaultCacheWriterConfig.getWriteBatchSize(), configDefault.getCacheWriterConfiguration().getWriteBatchSize());
        assertEquals(defaultCacheWriterConfig.getRetryAttempts(), configDefault.getCacheWriterConfiguration().getRetryAttempts());
        assertEquals(defaultCacheWriterConfig.getRetryAttemptDelaySeconds(), configDefault.getCacheWriterConfiguration()
                .getRetryAttemptDelaySeconds());

        Ehcache defaultCache = configurationHelper.createDefaultCache();
        assertEquals("default", defaultCache.getName());
        assertNotNull(defaultCache.getCacheConfiguration().getCacheWriterConfiguration());

        Map<String, CacheConfiguration> configs = configurationHelper.getConfigurationBean().getCacheConfigurations();
        CacheConfiguration config1 = configs.get("writeThroughCache1");
        assertNotNull(config1.getCacheWriterConfiguration());
        assertEquals(defaultCacheWriterConfig.getWriteMode(), config1.getCacheWriterConfiguration().getWriteMode());
        assertEquals(defaultCacheWriterConfig.getCacheWriterFactoryConfiguration(), config1.getCacheWriterConfiguration()
                .getCacheWriterFactoryConfiguration());
        assertEquals(defaultCacheWriterConfig.getNotifyListenersOnException(), config1.getCacheWriterConfiguration()
                .getNotifyListenersOnException());
        assertEquals(defaultCacheWriterConfig.getMaxWriteDelay(), config1.getCacheWriterConfiguration().getMaxWriteDelay());
        assertEquals(defaultCacheWriterConfig.getRateLimitPerSecond(), config1.getCacheWriterConfiguration().getRateLimitPerSecond());
        assertEquals(defaultCacheWriterConfig.getWriteCoalescing(), config1.getCacheWriterConfiguration().getWriteCoalescing());
        assertEquals(defaultCacheWriterConfig.getWriteBatching(), config1.getCacheWriterConfiguration().getWriteBatching());
        assertEquals(defaultCacheWriterConfig.getWriteBatchSize(), config1.getCacheWriterConfiguration().getWriteBatchSize());
        assertEquals(defaultCacheWriterConfig.getRetryAttempts(), config1.getCacheWriterConfiguration().getRetryAttempts());
        assertEquals(defaultCacheWriterConfig.getRetryAttemptDelaySeconds(), config1.getCacheWriterConfiguration().getRetryAttemptDelaySeconds());

        CacheConfiguration config2 = configs.get("writeThroughCache2");
        assertNotNull(config2.getCacheWriterConfiguration());
        assertEquals(defaultCacheWriterConfig.getWriteMode(), config2.getCacheWriterConfiguration().getWriteMode());
        assertEquals(defaultCacheWriterConfig.getCacheWriterFactoryConfiguration(), config2.getCacheWriterConfiguration()
                .getCacheWriterFactoryConfiguration());
        assertEquals(defaultCacheWriterConfig.getNotifyListenersOnException(), config2.getCacheWriterConfiguration()
                .getNotifyListenersOnException());
        assertEquals(defaultCacheWriterConfig.getMaxWriteDelay(), config2.getCacheWriterConfiguration().getMaxWriteDelay());
        assertEquals(defaultCacheWriterConfig.getRateLimitPerSecond(), config2.getCacheWriterConfiguration().getRateLimitPerSecond());
        assertEquals(defaultCacheWriterConfig.getWriteCoalescing(), config2.getCacheWriterConfiguration().getWriteCoalescing());
        assertEquals(defaultCacheWriterConfig.getWriteBatching(), config2.getCacheWriterConfiguration().getWriteBatching());
        assertEquals(defaultCacheWriterConfig.getWriteBatchSize(), config2.getCacheWriterConfiguration().getWriteBatchSize());
        assertEquals(defaultCacheWriterConfig.getRetryAttempts(), config2.getCacheWriterConfiguration().getRetryAttempts());
        assertEquals(defaultCacheWriterConfig.getRetryAttemptDelaySeconds(), config2.getCacheWriterConfiguration().getRetryAttemptDelaySeconds());

        CacheConfiguration config3 = configs.get("writeThroughCache3");
        assertNotNull(config3.getCacheWriterConfiguration());
        assertEquals(CacheWriterConfiguration.WriteMode.WRITE_THROUGH, config3.getCacheWriterConfiguration().getWriteMode());
        assertEquals(defaultCacheWriterConfig.getCacheWriterFactoryConfiguration(), config3.getCacheWriterConfiguration()
                .getCacheWriterFactoryConfiguration());
        assertEquals(true, config3.getCacheWriterConfiguration().getNotifyListenersOnException());
        assertEquals(30, config3.getCacheWriterConfiguration().getMaxWriteDelay());
        assertEquals(10, config3.getCacheWriterConfiguration().getRateLimitPerSecond());
        assertEquals(true, config3.getCacheWriterConfiguration().getWriteCoalescing());
        assertEquals(true, config3.getCacheWriterConfiguration().getWriteBatching());
        assertEquals(8, config3.getCacheWriterConfiguration().getWriteBatchSize());
        assertEquals(20, config3.getCacheWriterConfiguration().getRetryAttempts());
        assertEquals(60, config3.getCacheWriterConfiguration().getRetryAttemptDelaySeconds());

        CacheConfiguration config4 = configs.get("writeThroughCache4");
        assertNotNull(config4.getCacheWriterConfiguration());
        assertEquals(CacheWriterConfiguration.WriteMode.WRITE_THROUGH, config4.getCacheWriterConfiguration().getWriteMode());
        assertEquals("net.sf.ehcache.writer.TestCacheWriterFactory", config4.getCacheWriterConfiguration().getCacheWriterFactoryConfiguration()
                .getFullyQualifiedClassPath());
        assertEquals(null, config4.getCacheWriterConfiguration().getCacheWriterFactoryConfiguration().getProperties());
        assertEquals(null, config4.getCacheWriterConfiguration().getCacheWriterFactoryConfiguration().getPropertySeparator());
        assertEquals(false, config4.getCacheWriterConfiguration().getNotifyListenersOnException());
        assertEquals(0, config4.getCacheWriterConfiguration().getMaxWriteDelay());
        assertEquals(0, config4.getCacheWriterConfiguration().getRateLimitPerSecond());
        assertEquals(false, config4.getCacheWriterConfiguration().getWriteCoalescing());
        assertEquals(false, config4.getCacheWriterConfiguration().getWriteBatching());
        assertEquals(1, config4.getCacheWriterConfiguration().getWriteBatchSize());
        assertEquals(0, config4.getCacheWriterConfiguration().getRetryAttempts());
        assertEquals(0, config4.getCacheWriterConfiguration().getRetryAttemptDelaySeconds());

        CacheConfiguration config5 = configs.get("writeBehindCache5");
        assertNotNull(config5.getCacheWriterConfiguration());
        assertEquals(CacheWriterConfiguration.WriteMode.WRITE_BEHIND, config5.getCacheWriterConfiguration().getWriteMode());
        assertEquals("net.sf.ehcache.writer.TestCacheWriterFactory", config5.getCacheWriterConfiguration().getCacheWriterFactoryConfiguration()
                .getFullyQualifiedClassPath());
        assertEquals("just.some.property=test; another.property=test2", config5.getCacheWriterConfiguration().getCacheWriterFactoryConfiguration()
                .getProperties());
        assertEquals(";", config5.getCacheWriterConfiguration().getCacheWriterFactoryConfiguration().getPropertySeparator());
        assertEquals(true, config5.getCacheWriterConfiguration().getNotifyListenersOnException());
        assertEquals(8, config5.getCacheWriterConfiguration().getMaxWriteDelay());
        assertEquals(5, config5.getCacheWriterConfiguration().getRateLimitPerSecond());
        assertEquals(true, config5.getCacheWriterConfiguration().getWriteCoalescing());
        assertEquals(false, config5.getCacheWriterConfiguration().getWriteBatching());
        assertEquals(1, config5.getCacheWriterConfiguration().getWriteBatchSize());
        assertEquals(2, config5.getCacheWriterConfiguration().getRetryAttempts());
        assertEquals(2, config5.getCacheWriterConfiguration().getRetryAttemptDelaySeconds());
        Ehcache cache5 = configurationHelper.createCacheFromName("writeBehindCache5");
        Properties properties5 = ((TestCacheWriter) cache5.getRegisteredCacheWriter()).getProperties();
        assertEquals(2, properties5.size());
        assertEquals("test", properties5.getProperty("just.some.property"));
        assertEquals("test2", properties5.getProperty("another.property"));
    }


    private void helpTestListenFor(Configuration configuration, String cacheName, NotificationScope expectedScope) {
        CacheConfiguration cache = configuration.getCacheConfigurations().get(cacheName);
        List<CacheConfiguration.CacheEventListenerFactoryConfiguration> listenerConfigs = cache.getCacheEventListenerConfigurations();
        assertEquals(1, listenerConfigs.size());

        CacheConfiguration.CacheEventListenerFactoryConfiguration listenerFactoryConfig = listenerConfigs.get(0);
        assertEquals(expectedScope, listenerFactoryConfig.getListenFor());
    }

    @Test
    public void testListenForAttributeParsing() {
        File file = new File(TEST_CONFIG_DIR + "ehcache-listener-scope.xml");
        Configuration configuration = ConfigurationFactory.parseConfiguration(file);

        helpTestListenFor(configuration, "listenDefault", NotificationScope.ALL);
        helpTestListenFor(configuration, "listenAll", NotificationScope.ALL);
        helpTestListenFor(configuration, "listenLocal", NotificationScope.LOCAL);
        helpTestListenFor(configuration, "listenRemote", NotificationScope.REMOTE);
    }


    @Test
    public void testCacheConfigurationWithNoName() {

        //Don't set cache name
        CacheConfiguration cacheConfigurationTest3Cache = new CacheConfiguration().maxElementsInMemory(10)
                .eternal(true).memoryStoreEvictionPolicy(MemoryStoreEvictionPolicy.LRU).overflowToDisk(false)
                .statistics(false);

        try {
            Cache cache = new Cache(cacheConfigurationTest3Cache);
        } catch (InvalidConfigurationException e) {
            //expected
        }

    }

    @Test
    public void testValidStoreConfigElements() throws Exception {
        File file = new File(TEST_CONFIG_DIR + "ehcache-store.xml");
        Configuration configuration = ConfigurationFactory.parseConfiguration(file);

        CacheConfiguration cacheConfiguration = configuration.getCacheConfigurations().get("offheap1");
        assertEquals(16777216, cacheConfiguration.getMaxMemoryOffHeapInBytes());
        assertEquals(true, cacheConfiguration.isOverflowToOffHeap());

        cacheConfiguration = configuration.getCacheConfigurations().get("offheap2");
        assertEquals(2147483648L, cacheConfiguration.getMaxMemoryOffHeapInBytes());
        assertEquals(false, cacheConfiguration.isOverflowToOffHeap());

        assertEquals(2164260864L, configuration.getTotalConfiguredOffheap());
    }

}

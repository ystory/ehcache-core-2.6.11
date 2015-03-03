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

package net.sf.ehcache.distribution;

import static net.sf.ehcache.util.RetryAssert.assertBy;
import static net.sf.ehcache.util.RetryAssert.sizeOf;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.rmi.server.RMISocketFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import net.sf.ehcache.Cache;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.config.ConfigurationFactory;
import net.sf.ehcache.config.FactoryConfiguration;

import org.hamcrest.collection.IsEmptyCollection;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;

public abstract class AbstractRMITest {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AbstractRMITest.class);

    protected static Configuration getConfiguration(String fileName) {
        return ConfigurationFactory.parseConfiguration(new File(fileName));
    }

    private static final Logger[] RMI_LOGGERS = new Logger[] {
      Logger.getLogger(MulticastKeepaliveHeartbeatReceiver.class.getName()),
      Logger.getLogger(MulticastKeepaliveHeartbeatSender.class.getName()),
      Logger.getLogger(ManualRMICacheManagerPeerProvider.class.getName()),
      Logger.getLogger(MulticastRMICacheManagerPeerProvider.class.getName()),
      Logger.getLogger(PayloadUtil.class.getName()),
      Logger.getLogger(RMIAsynchronousCacheReplicator.class.getName()),
      Logger.getLogger(RMIAsynchronousCacheReplicator.class.getName()),
      Logger.getLogger(RMIBootstrapCacheLoader.class.getName()),
      Logger.getLogger(RMIBootstrapCacheLoaderFactory.class.getName()),
      Logger.getLogger(RMICacheManagerPeerListener.class.getName()),
      Logger.getLogger(RMICacheManagerPeerProvider.class.getName()),
      Logger.getLogger(RMICacheManagerPeerProviderFactory.class.getName()),
      //Logger.getLogger(RMICachePeer.class.getName()),
      Logger.getLogger(RMICacheReplicatorFactory.class.getName()),
      Logger.getLogger(RMISynchronousCacheReplicator.class.getName()),
    };
    private static Handler RMI_LOGGING_HANDLER;
    
    public static void installRmiLogging(String file) throws IOException {
      if (RMI_LOGGING_HANDLER != null) {
        throw new AssertionError();
      }
      RMI_LOGGING_HANDLER = new FileHandler(file);
      RMI_LOGGING_HANDLER.setFormatter(new SimpleFormatter());
      RMI_LOGGING_HANDLER.setLevel(Level.ALL);
      
      for (Logger l : RMI_LOGGERS) {
        l.addHandler(RMI_LOGGING_HANDLER);
        l.setLevel(Level.ALL);
      }
    }

    @AfterClass
    public static void removeRmiLogging() {
      if (RMI_LOGGING_HANDLER != null) {
        for (Logger l : RMI_LOGGERS) {
          l.removeHandler(RMI_LOGGING_HANDLER);
          l.setLevel(Level.INFO);
        }
        RMI_LOGGING_HANDLER.close();
        RMI_LOGGING_HANDLER = null;
      }
    }
    
    @BeforeClass
    public static void installRMISocketFactory() {
        RMISocketFactory current = RMISocketFactory.getSocketFactory();
        if (current == null) {
            current = RMISocketFactory.getDefaultSocketFactory();
        }
        assertNotNull(current);
        try {
            RMISocketFactory.setSocketFactory(new SocketReusingRMISocketFactory(current));
            LOG.info("Installed the SO_REUSEADDR setting socket factory");
        } catch (IOException e) {
            LOG.warn("Couldn't register the SO_REUSEADDR setting socket factory", e);
        }
    }

    @BeforeClass
    public static void checkActiveThreads() {
        assertThat(getActiveReplicationThreads(), IsEmptyCollection.<Thread>empty());
    }

    protected static Set<Thread> getActiveReplicationThreads() {
        Set<Thread> threads = new HashSet<Thread>();
        for (Thread thread : JVMUtil.enumerateThreads()) {
            if (thread.getName().equals("Replication Thread")) {
                threads.add(thread);
            }
        }
        return threads;
    }
    
    @Before
    public void setupMulticastTiming() {
        MulticastKeepaliveHeartbeatSender.setHeartBeatInterval(1000);
        MulticastKeepaliveHeartbeatSender.setHeartBeatStaleTime(Long.MAX_VALUE);
    }

    protected static final void setHeapDumpOnOutOfMemoryError(boolean value) {
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            ObjectName beanName = ObjectName.getInstance("com.sun.management:type=HotSpotDiagnostic");
            Object vmOption = server.invoke(beanName, "setVMOption", new Object[] { "HeapDumpOnOutOfMemoryError", Boolean.toString(value) },
                                                                     new String[] { "java.lang.String", "java.lang.String" });
            LOG.info("Set HeapDumpOnOutOfMemoryError to: " + value);
        } catch (Throwable t) {
            LOG.info("Set HeapDumpOnOutOfMemoryError to: " + value + " - failed", t);
        }
    }

    protected static Collection<Throwable> runTasks(Collection<Callable<Void>> tasks) {
        final long endTime = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        final Collection<Throwable> errors = new ArrayList<Throwable>();

        // Spin up the threads
        Collection<Thread> threads = new ArrayList<Thread>(tasks.size());
        for (final Callable<Void> task : tasks) {
            Assert.assertNotNull(task);
            threads.add(new Thread() {
                @Override
                public void run() {
                    try {
                        // Run the thread until the given end time
                        while (System.nanoTime() < endTime) {
                            task.call();
                        }
                    } catch (Throwable t) {
                        // Hang on to any errors
                        errors.add(t);
                    }
                }

            });
        }

        for (Thread t : threads) {
            t.start();
        }

        boolean interrupted = false;
        try {
            for (Thread t : threads) {
                while (t.isAlive()) {
                    try {
                        t.join();
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        return errors;
    }

    /**
     * Wait for all caches to have a full set of peers in each manager.
     * <p>
     * This method will hang if all managers don't share a common set of replicated caches.
     */
    protected static void waitForClusterMembership(int time, TimeUnit unit, final List<CacheManager> managers) {
        waitForClusterMembership(time, unit, getAllReplicatedCacheNames(managers.get(0)), managers);
    }

    /**
     * Wait for all caches to have a full set of peers in each manager.
     * <p>
     * This method will hang if all managers don't share a common set of replicated caches.
     */
    protected static void waitForClusterMembership(int time, TimeUnit unit, CacheManager ... managers) {
        waitForClusterMembership(time, unit, getAllReplicatedCacheNames(managers[0]), managers);
    }
    
    /**
     * Wait for the given caches to have a full set of peers in each manager.
     * <p>
     * Any other caches in these managers may or may not be fully announced throughout the cluster.
     */
    protected static void waitForClusterMembership(int time, TimeUnit unit, final Collection<String> cacheNames, final CacheManager ... managers) {
        waitForClusterMembership(time, unit, cacheNames, Arrays.asList(managers));
    }
    
    /**
     * Wait for the given caches to have a full set of peers in each manager.
     * <p>
     * Any other caches in these managers may or may not be fully announced throughout the cluster.
     */
    protected static void waitForClusterMembership(int time, TimeUnit unit, final Collection<String> cacheNames, final List<CacheManager> managers) {
        assertBy(time, unit, new Callable<Integer>() {

            public Integer call() throws Exception {
                Integer minimumPeers = null;
                for (CacheManager manager : managers) {
                    CacheManagerPeerProvider peerProvider = manager.getCacheManagerPeerProvider("RMI");
                    for (String cacheName : cacheNames) {
                        int peers = peerProvider.listRemoteCachePeers(manager.getEhcache(cacheName)).size();
                        if (minimumPeers == null || peers < minimumPeers) {
                            minimumPeers = peers;
                        }
                    }
                }
                if (minimumPeers == null) {
                    return 0;
                } else {
                    return minimumPeers + 1;
                }
            }
        }, is(managers.size()));
    }

    protected static void emptyCaches(int time, TimeUnit unit, List<CacheManager> members) {
        emptyCaches(time, unit, getAllReplicatedCacheNames(members.get(0)), members);
    }

    protected static void emptyCaches(final int time, final TimeUnit unit, Collection<String> required, final List<CacheManager> members) {
        List<Callable<Void>> cacheEmptyTasks = new ArrayList<Callable<Void>>();
        for (String cache : required) {
            final String cacheName = cache;
            cacheEmptyTasks.add(new Callable<Void>() {

                @Override
                public Void call() throws Exception {
                    for (CacheManager manager : members) {
                        manager.getCache(cacheName).put(new Element("setup", "setup"), true);
                    }

                    members.get(0).getCache(cacheName).removeAll();
                    for (CacheManager manager : members.subList(1, members.size())) {
                        assertBy(time, unit, sizeOf(manager.getCache(cacheName)), is(0));
                    }
                    return null;
                }
            });
        }

        ExecutorService executor = Executors.newCachedThreadPool();
        try {
            for (Future<Void> result : executor.invokeAll(cacheEmptyTasks)) {
                result.get();
            }
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        } catch (ExecutionException e) {
            throw new AssertionError(e);
        } finally {
            executor.shutdown();
        }
    }

    private static Collection<String> getAllReplicatedCacheNames(CacheManager manager) {
        Collection<String> replicatedCaches = new ArrayList<String>();
        for (String name : manager.getCacheNames()) {
            Cache cache = manager.getCache(name);
            if (cache.getCacheEventNotificationService().hasCacheReplicators()) {
                replicatedCaches.add(name);
            }
        }
        return replicatedCaches;
    }

    protected static List<CacheManager> startupManagers(List<Configuration> configurations) {
        List<Callable<CacheManager>> nodeStartupTasks = new ArrayList<Callable<CacheManager>>();
        for (Configuration config : configurations) {
            final Configuration configuration = config;
            nodeStartupTasks.add(new Callable<CacheManager>() {
                @Override
                public CacheManager call() throws Exception {
                    return new CacheManager(configuration);
                }
            });
        }

        ExecutorService clusterStarter = Executors.newCachedThreadPool();
        try {
            List<CacheManager> managers = new ArrayList<CacheManager>();
            try {
                for (Future<CacheManager> result : clusterStarter.invokeAll(nodeStartupTasks)) {
                    managers.add(result.get());
                }
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            } catch (ExecutionException e) {
                throw new AssertionError(e);
            }
            return managers;
        } finally {
            clusterStarter.shutdown();
        }
    }

    protected static Configuration createRMICacheManagerConfiguration() {
        Configuration config = new Configuration();
        config.addCacheManagerPeerProviderFactory(new FactoryConfiguration()
                .className("net.sf.ehcache.distribution.RMICacheManagerPeerProviderFactory")
                .properties("peerDiscovery=automatic, multicastGroupAddress=230.0.0.1, multicastGroupPort=4446, timeToLive=0"));
        config.addCacheManagerPeerListenerFactory(new FactoryConfiguration()
                .className("net.sf.ehcache.distribution.RMICacheManagerPeerListenerFactory")
                .properties("hostName=localhost"));
        return config;
    }

    protected static CacheConfiguration createAsynchronousCache() {
        CacheConfiguration cacheConfig = new CacheConfiguration();
        cacheConfig.maxEntriesLocalHeap(0).eternal(true);
        cacheConfig.addCacheEventListenerFactory(new CacheConfiguration.CacheEventListenerFactoryConfiguration()
                .className("net.sf.ehcache.distribution.RMICacheReplicatorFactory")
                .properties("replicateAsynchronously=true,"
                + "replicatePuts=true,"
                + "replicateUpdates=true,"
                + "replicateUpdatesViaCopy=true,"
                + "replicateRemovals=true"));
        return cacheConfig;
    }

    protected static CacheConfiguration createAsynchronousCacheViaInvalidate() {
        CacheConfiguration cacheConfig = new CacheConfiguration();
        cacheConfig.maxEntriesLocalHeap(0).eternal(true);
        cacheConfig.addCacheEventListenerFactory(new CacheConfiguration.CacheEventListenerFactoryConfiguration()
                .className("net.sf.ehcache.distribution.RMICacheReplicatorFactory")
                .properties("replicateAsynchronously=true,"
                + "replicatePuts=true,"
                + "replicatePutsViaCopy=false,"
                + "replicateUpdates=true,"
                + "replicateUpdatesViaCopy=false,"
                + "replicateRemovals=true"));
        return cacheConfig;
    }
    
}

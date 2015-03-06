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


import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.Status;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.event.CacheEventListener;

import org.hamcrest.core.DescribedAs;
import org.junit.After;

import static net.sf.ehcache.util.RetryAssert.assertBy;
import static net.sf.ehcache.util.RetryAssert.elementAt;
import static net.sf.ehcache.util.RetryAssert.sizeOf;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Test;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import net.sf.ehcache.config.CacheConfiguration;
import static net.sf.ehcache.distribution.AbstractRMITest.createRMICacheManagerConfiguration;

/**
 * Unit tests for the RMICacheManagerPeerListener
 * <p/>
 * Note these tests need a live network interface running in multicast mode to work
 *
 * @author <a href="mailto:gluck@thoughtworks.com">Greg Luck</a>
 * @version $Id$
 */
public class RMICacheManagerPeerListenerTest extends AbstractRMITest {

    private static final Logger LOGGER = Logger.getLogger(RMICacheManagerPeerListenerTest.class.getName());

    private static final ConsoleHandler MULTICAST_HANDLER = new ConsoleHandler();
    
    private static final Logger SENDER_LOGGER = Logger.getLogger(MulticastKeepaliveHeartbeatSender.class.getName());
    private static final Logger RECEIVER_LOGGER = Logger.getLogger(MulticastKeepaliveHeartbeatReceiver.class.getName());
    @BeforeClass
    public static void enableLogging() {
      SENDER_LOGGER.setLevel(Level.ALL);
      RECEIVER_LOGGER.setLevel(Level.ALL);
      MULTICAST_HANDLER.setLevel(Level.ALL);
      
      SENDER_LOGGER.addHandler(MULTICAST_HANDLER);
      RECEIVER_LOGGER.addHandler(MULTICAST_HANDLER);
    }
    
    @AfterClass
    public static void disableLogging() {
      SENDER_LOGGER.setLevel(Level.INFO);
      RECEIVER_LOGGER.setLevel(Level.INFO);
      SENDER_LOGGER.removeHandler(MULTICAST_HANDLER);
      RECEIVER_LOGGER.removeHandler(MULTICAST_HANDLER);
    }
    
    /**
     * CacheManager 1 in the cluster
     */
    protected CacheManager manager1;
    /**
     * CacheManager 2 in the cluster
     */
    protected CacheManager manager2;
    /**
     * CacheManager 3 in the cluster
     */
    protected CacheManager manager3;
    /**
     * CacheManager 4 in the cluster
     */
    protected CacheManager manager4;
    /**
     * CacheManager 5 in the cluster
     */
    protected CacheManager manager5;
    /**
     * CacheManager 6 in the cluster
     */
    protected CacheManager manager6;

    /**
     * CacheManager 1 of 2s cache being replicated
     */
    protected Ehcache cache1;

    /**
     * CacheManager 2 of 2s cache being replicated
     */
    protected Ehcache cache2;

    /**
     * {@inheritDoc}
     * Sets up two caches: cache1 is local. cache2 is to be receive updates
     *
     * @throws Exception
     */
    @Before
    public void setUp() throws Exception {
        List<Configuration> configurations = new ArrayList<Configuration>();
        configurations.add(createRMICacheManagerConfiguration()
                .defaultCache(createAsynchronousCache())
                .cache(createAsynchronousCache().name("asynchronousCache"))
                .name("RMICacheManagerPeerListenerTest-1"));
        configurations.add(createRMICacheManagerConfiguration()
                .cache(createAsynchronousCache().name("asynchronousCache"))
                .name("RMICacheManagerPeerListenerTest-2"));
        configurations.add(createRMICacheManagerConfiguration()
                .cache(createAsynchronousCache().name("asynchronousCache"))
                .name("RMICacheManagerPeerListenerTest-3"));
        configurations.add(createRMICacheManagerConfiguration()
                .cache(createAsynchronousCache().name("asynchronousCache"))
                .name("RMICacheManagerPeerListenerTest-4"));
        configurations.add(createRMICacheManagerConfiguration()
                .cache(createAsynchronousCache().name("asynchronousCache"))
                .name("RMICacheManagerPeerListenerTest-5"));

        List<CacheManager> managers = startupManagers(configurations);
        manager1 = managers.get(0);
        manager2 = managers.get(1);
        manager3 = managers.get(2);
        manager4 = managers.get(3);
        manager5 = managers.get(4);

        //allow cluster to be established
        LOGGER.info("Validating Cluster Membership");
        waitForClusterMembership(120, TimeUnit.SECONDS, manager1, manager2, manager3, manager4, manager5);
        LOGGER.info("Validated Cluster Membership");

        LOGGER.info("Putting Setup Value");
        manager1.getCache("asynchronousCache").put(new Element("setup", "setup"));
        LOGGER.info("Put Setup Value");
        for (CacheManager manager : new CacheManager[] {manager1, manager2, manager3, manager4, manager5}) {
            LOGGER.info("Validating Setup Value Propagation To " + manager);
            assertBy(20, TimeUnit.SECONDS, elementAt(manager.getCache("asynchronousCache"), "setup"), DescribedAs.describedAs("Failed to propagate setup value to {}", notNullValue(), manager));
            LOGGER.info("Validated Setup Value Propagation To " + manager);
        }

        LOGGER.info("Performing RemoveAll");
        manager1.getCache("asynchronousCache").removeAll();
        LOGGER.info("Performed RemoveAll");
        for (CacheManager manager : new CacheManager[] {manager1, manager2, manager3, manager4, manager5}) {
            LOGGER.info("Validating RemoveAll Propagation To " + manager);
            assertBy(20, TimeUnit.SECONDS, sizeOf(manager.getCache("asynchronousCache")), DescribedAs.describedAs("Failed to propagate removeAll to {}" , is(0), manager));
            LOGGER.info("Validated RemoveAll Propagation To " + manager);
        }

        cache1 = manager1.getCache("asynchronousCache");
        cache2 = manager2.getCache("asynchronousCache");
    }


    /**
     * {@inheritDoc}
     *
     * @throws Exception
     */
    @After
    public void tearDown() throws Exception {

        if (manager1 != null) {
            manager1.shutdown();
        }
        if (manager2 != null) {
            manager2.shutdown();
        }
        if (manager3 != null) {
            manager3.shutdown();
        }
        if (manager4 != null) {
            manager4.shutdown();
        }
        if (manager5 != null) {
            manager5.shutdown();
        }
        if (manager6 != null) {
            manager6.shutdown();
        }

        /*
         * We can't assert this here, since one of these tests intentionally doesn't
         * shutdown the last cache manager - intended to test the shutdown hooks...
         */
        //RetryAssert.assertBy(30, TimeUnit.SECONDS, new Callable<Set<Thread>>() {
        //    public Set<Thread> call() throws Exception {
        //        return getActiveReplicationThreads();
        //    }
        //}, IsEmptyCollection.<Thread>empty());
    }


    /**
     * Are all of the replicated caches bound to the RMI listener?
     */
    @Test
    public void testPeersBound() {

        List cachePeers1 = ((RMICacheManagerPeerListener) manager1.getCachePeerListener("RMI")).getBoundCachePeers();
        assertEquals(1, cachePeers1.size());
        String[] boundCachePeers1 = ((RMICacheManagerPeerListener) manager1.getCachePeerListener("RMI")).listBoundRMICachePeers();
        assertEquals(1, boundCachePeers1.length);
        assertEquals(cachePeers1.size(), boundCachePeers1.length);

        List cachePeers2 = ((RMICacheManagerPeerListener) manager2.getCachePeerListener("RMI")).getBoundCachePeers();
        assertEquals(1, cachePeers2.size());
        String[] boundCachePeers2 = ((RMICacheManagerPeerListener) manager2.getCachePeerListener("RMI")).listBoundRMICachePeers();
        assertEquals(1, boundCachePeers2.length);
        assertEquals(cachePeers2.size(), boundCachePeers2.length);


        List cachePeers3 = ((RMICacheManagerPeerListener) manager3.getCachePeerListener("RMI")).getBoundCachePeers();
        assertEquals(1, cachePeers3.size());
        String[] boundCachePeers3 = ((RMICacheManagerPeerListener) manager3.getCachePeerListener("RMI")).listBoundRMICachePeers();
        assertEquals(1, boundCachePeers3.length);
        assertEquals(cachePeers3.size(), boundCachePeers3.length);


        List cachePeers4 = ((RMICacheManagerPeerListener) manager4.getCachePeerListener("RMI")).getBoundCachePeers();
        assertEquals(1, cachePeers4.size());
        String[] boundCachePeers4 = ((RMICacheManagerPeerListener) manager4.getCachePeerListener("RMI")).listBoundRMICachePeers();
        assertEquals(1, boundCachePeers4.length);
        assertEquals(cachePeers4.size(), boundCachePeers4.length);

        List cachePeers5 = ((RMICacheManagerPeerListener) manager5.getCachePeerListener("RMI")).getBoundCachePeers();
        assertEquals(1, cachePeers5.size());
        String[] boundCachePeers5 = ((RMICacheManagerPeerListener) manager5.getCachePeerListener("RMI")).listBoundRMICachePeers();
        assertEquals(1, boundCachePeers5.length);
        assertEquals(cachePeers5.size(), boundCachePeers5.length);
    }


    /**
     * Are all of the replicated caches bound to the listener and working?
     */
    @Test
    public void testBoundListenerPeers() throws RemoteException {

        String[] boundCachePeers = ((RMICacheManagerPeerListener) manager1.getCachePeerListener("RMI")).listBoundRMICachePeers();
        validateBoundCachePeer(boundCachePeers);
    }

    /**
     * Are all of the CachePeers for replicated caches bound to the listener and working?
     */
    @Test
    public void testBoundListenerPeersAfterDefaultCacheAdd() throws RemoteException {

        String[] boundCachePeers = ((RMICacheManagerPeerListener) manager1.getCachePeerListener("RMI")).listBoundRMICachePeers();
        assertEquals(1, boundCachePeers.length);
        validateBoundCachePeer(boundCachePeers);

        //Add from default which is has a CacheReplicator configured.
        manager1.addCache("fromDefaultCache");
        boundCachePeers = ((RMICacheManagerPeerListener) manager1.getCachePeerListener("RMI")).listBoundRMICachePeers();
        assertEquals(2, boundCachePeers.length);
        validateBoundCachePeer(boundCachePeers);
    }

    /**
     * Are all of the CachePeers for replicated caches bound to the listener and working?
     */
    @Test
    public void testBoundListenerPeersAfterProgrammaticCacheAdd() throws RemoteException {

        String[] boundCachePeers = ((RMICacheManagerPeerListener) manager1.getCachePeerListener("RMI")).listBoundRMICachePeers();
        assertEquals(1, boundCachePeers.length);
        validateBoundCachePeer(boundCachePeers);

        //Add from default which is has a CacheReplicator configured.


        RMICacheReplicatorFactory factory = new RMICacheReplicatorFactory();
        CacheEventListener replicatingListener = factory.createCacheEventListener(null);
        Cache cache = new Cache(new CacheConfiguration().name("programmaticallyAdded").maxEntriesLocalHeap(0));
        cache.getCacheEventNotificationService().registerListener(replicatingListener);

        manager1.addCache(cache);
        boundCachePeers = ((RMICacheManagerPeerListener) manager1.getCachePeerListener("RMI")).listBoundRMICachePeers();
        assertEquals(2, boundCachePeers.length);
        validateBoundCachePeer(boundCachePeers);
    }

    /**
     * Are all of the CachePeers for replicated caches bound to the listener and working?
     */
    @Test
    public void testBoundListenerPeersAfterCacheRemove() throws RemoteException {
        String[] boundCachePeers = ((RMICacheManagerPeerListener) manager1.getCachePeerListener("RMI")).listBoundRMICachePeers();
        assertEquals(1, boundCachePeers.length);
        validateBoundCachePeer(boundCachePeers);

        //Remove a replicated cache
        manager1.removeCache("asynchronousCache");
        boundCachePeers = ((RMICacheManagerPeerListener) manager1.getCachePeerListener("RMI")).listBoundRMICachePeers();
        assertEquals(0, boundCachePeers.length);
        validateBoundCachePeer(boundCachePeers);
    }


    private void validateBoundCachePeer(String[] boundCachePeers) {
        for (String boundCacheName : boundCachePeers) {
            Remote remote = ((RMICacheManagerPeerListener) manager1.getCachePeerListener("RMI")).lookupPeer(boundCacheName);
            assertNotNull(remote);
        }
    }


    /**
     * Does the RMI listener stop?
     */
    @Test
    public void testListenerShutsdown() {
        CacheManagerPeerListener cachePeerListener = manager1.getCachePeerListener("RMI");
        List cachePeers1 = cachePeerListener.getBoundCachePeers();
        assertEquals(1, cachePeers1.size());
        assertEquals(Status.STATUS_ALIVE, cachePeerListener.getStatus());

        manager1.shutdown();
        assertEquals(Status.STATUS_SHUTDOWN, cachePeerListener.getStatus());

    }

}

package net.sf.ehcache.terracotta;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.DiskStorePathManager;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.store.TerracottaStore;

import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.ParseException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.hamcrest.number.OrderingComparison.lessThan;

/**
 * @author Alex Snaps
 */
public class KeySnapshotterTest {

    private static final String KEY = "Key";
    private static final long MAX_KEY = 10000;
    private static final String DUMPS_DIRECTORY = System.getProperty("java.io.tmpdir") + File.separator + "dumps";

    private static void deleteFolder(File root) {
        if (root.isDirectory()) {
            File[] files = root.listFiles();
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteFolder(file);
                } else {
                    file.delete();
                }
            }
        }

        if (root.exists()) {
            root.delete();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testThrowsIllegalArgumentExceptionOnZeroInterval() {
        CacheManager manager = new CacheManager(new Configuration().name("testThrowsIllegalArgumentExceptionOnNegativeInterval"));
        try {
            new KeySnapshotter(createFakeTcClusteredCache(manager, mock(TerracottaStore.class)), 0, false, null);
        } finally {
            manager.shutdown();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testThrowsIllegalArgumentExceptionOnNegativeInterval() {
        CacheManager manager = new CacheManager(new Configuration().name("testThrowsIllegalArgumentExceptionOnNegativeInterval"));
        try {
            new KeySnapshotter(createFakeTcClusteredCache(manager, mock(TerracottaStore.class)), -100, false, null);
        } finally {
            manager.shutdown();
        }
    }

    @Test(expected = NullPointerException.class)
    public void testThrowsNullPointerExceptionOnNegativeInterval() {
        CacheManager manager = new CacheManager(new Configuration().name("testThrowsNullPointerExceptionOnNegativeInterval"));
        try {
            new KeySnapshotter(createFakeTcClusteredCache(manager, mock(TerracottaStore.class)), 10, false, null);
        } finally {
            manager.shutdown();
        }
    }

    @Test
    public void testOperatesOnDedicatedThread() throws ParseException, IOException {
        CacheManager manager = new CacheManager(new Configuration().name("testOperatesOnDedicatedThread"));
        try {
            final RotatingSnapshotFile rotatingSnapshotFile = mock(RotatingSnapshotFile.class);
            final TerracottaStore mock = mock(TerracottaStore.class);
            KeySnapshotter snapshotter = new KeySnapshotter(createFakeTcClusteredCache(manager, mock), 10, true, rotatingSnapshotFile);

            boolean found = false;
            for (Thread thread : getAllThreads()) {
                if (thread != null) {
                    if(thread.getName().equals("KeySnapshotter for cache test")) {
                        found = true;
                    }
                }
            }
            assertThat(found, is(true));
            snapshotter.dispose(true);
        } finally {
            manager.shutdown();
        }
    }

    @Test
    public void testDisposesProperlyImmediately() throws BrokenBarrierException, InterruptedException, IOException {
        CacheManager manager = new CacheManager(new Configuration().name("testDisposesProperlyImmediately"));
        try {
            deleteFolder(new File(DUMPS_DIRECTORY));
            final RotatingSnapshotFile rotatingSnapshotFile = new RotatingSnapshotFile(new DiskStorePathManager(DUMPS_DIRECTORY), "testingInterruptImmediate");
            final TerracottaStore mockedTcStore = mock(TerracottaStore.class);
            KeySnapshotter snapshotter = new KeySnapshotter(createFakeTcClusteredCache(manager, mockedTcStore), 1, true, rotatingSnapshotFile);
            final CyclicBarrier barrier = new CyclicBarrier(2);
            final Set mockedSet = mock(Set.class);
            final AtomicInteger counter = new AtomicInteger(0);
            final int maxElementsToReturn = 100000;
            when(mockedSet.iterator()).thenAnswer(new Answer<Iterator>() {

                public Iterator answer(final InvocationOnMock invocationOnMock) throws Throwable {
                    return new Iterator<Object>() {

                        public boolean hasNext() {
                            return counter.get() < maxElementsToReturn;
                        }

                        public Object next() {
                            return "Key" + counter.getAndIncrement();
                        }

                        public void remove() {
                            //
                        }
                    };
                }
            });
            when(mockedTcStore.getLocalKeys()).thenAnswer(new Answer<Set>() {
                public Set answer(final InvocationOnMock invocationOnMock) throws Throwable {
                    barrier.await();
                    return mockedSet;
                }
            });
            barrier.await();
            snapshotter.dispose(true);

            assertThat("Snapshot was allowed to finish - this shouldn't happen (unless the snapshot happens really fast and the scheduler is *very* unfair)", counter.get(), lessThan(maxElementsToReturn));
            final int elementsRead = rotatingSnapshotFile.readAll().size();
            assertThat("Should be only a couple: " + elementsRead, elementsRead, is(counter.get() - 1));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    public void testDisposesProperlyButFinishes() throws BrokenBarrierException, InterruptedException, IOException {
        CacheManager manager = new CacheManager(new Configuration().name("testDisposesProperlyImmediately"));
        try {
            deleteFolder(new File(DUMPS_DIRECTORY));
            final RotatingSnapshotFile rotatingSnapshotFile = new RotatingSnapshotFile(new DiskStorePathManager(DUMPS_DIRECTORY), "testingInterruptFinishes");
            final TerracottaStore mockedTcStore = mock(TerracottaStore.class);
            final CyclicBarrier barrier = new CyclicBarrier(2);
            final Set mockedSet = mock(Set.class);
            final AtomicLong counter = new AtomicLong(0);
            when(mockedSet.iterator()).thenAnswer(new Answer<Iterator>() {
                public Iterator answer(final InvocationOnMock invocationOnMock) throws Throwable {
                    return new Iterator<Object>() {

                        public boolean hasNext() {
                            return counter.get() < MAX_KEY;
                        }

                        public Object next() {
                            return KEY + counter.getAndIncrement();
                        }

                        public void remove() {
                            //
                        }
                    };
                }
            });
            when(mockedTcStore.getLocalKeys()).thenAnswer(new Answer<Set>() {
                public Set answer(final InvocationOnMock invocationOnMock) throws Throwable {
                    barrier.await();
                    return mockedSet;
                }
            });
            KeySnapshotter snapshotter = new KeySnapshotter(createFakeTcClusteredCache(manager, mockedTcStore), 1, true, rotatingSnapshotFile);
            barrier.await();
            snapshotter.dispose(false);
            assertThat(counter.get(), is(MAX_KEY));
            final Set<Object> objects = new HashSet<Object>(rotatingSnapshotFile.readAll());
            assertThat(objects.size(), is((int) MAX_KEY));
            for(int i = 0; i < MAX_KEY; i++) {
                objects.remove(KEY + i);
            }
            assertThat(objects.isEmpty(), is(true));
        } finally {
            manager.shutdown();
        }
    }

    private Thread[] getAllThreads() {
        ThreadGroup rootGroup = Thread.currentThread().getThreadGroup();
        ThreadGroup parentGroup;
        while ((parentGroup = rootGroup.getParent()) != null) {
            rootGroup = parentGroup;
        }

        Thread[] threads = new Thread[rootGroup.activeCount()];
        rootGroup.enumerate(threads, true);
        return threads;
    }

    private Cache createFakeTcClusteredCache(CacheManager manager, TerracottaStore store) {
        final Cache cache = new Cache(new CacheConfiguration("test", 10));
        manager.addCache(cache);
        try {
            final Field compoundStore = cache.getClass().getDeclaredField("compoundStore");
            compoundStore.setAccessible(true);
            compoundStore.set(cache, store);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return cache;
    }
}

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

package net.sf.ehcache;


import org.junit.After;
import org.junit.Assert;

import static org.junit.Assert.assertTrue;

import org.junit.Before;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.sf.ehcache.distribution.AbstractRMITest;
import org.junit.BeforeClass;

import org.junit.Ignore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Common fields and methods required by most test cases
 *
 * @author <a href="mailto:gluck@thoughtworks.com">Greg Luck</a>
 * @version $Id$
 */
@Ignore
public abstract class AbstractCacheTest {

    /**
     * Where the config is
     */
    public static final String SRC_CONFIG_DIR = "src/main/config/";

    /**
     * Where the test config is
     */
    public static final String TEST_CONFIG_DIR = "src/test/resources/";
    /**
     * Where the test classes are compiled.
     */
    public static final String TEST_CLASSES_DIR = "target/test-classes/";


    private static final Logger LOG = LoggerFactory.getLogger(AbstractCacheTest.class.getName());

    /**
     * name for sample cache 1
     */
    protected final String sampleCache1 = "sampleCache1";
    /**
     * name for sample cache 2
     */
    protected final String sampleCache2 = "sampleCache2";
    /**
     * the CacheManager instance
     */
    protected CacheManager manager;

    @BeforeClass
    public static void installRMISocketFactory() {
      AbstractRMITest.installRMISocketFactory();
    }

    /**
     * setup test
     */
    @Before
    public void setUp() throws Exception {
        manager = CacheManager.create();
    }

    /**
     * teardown
     */
    @After
    public void tearDown() throws Exception {
        if (manager != null) {
            manager.shutdown();
        }
    }


    /**
     * Force the VM to grow to its full size. This stops SoftReferences from being reclaimed in favour of
     * Heap growth. Only an issue when a VM is cold.
     */
    static public void forceVMGrowth() {
        allocateFiftyMegabytes();
        System.gc();
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.gc();
    }

    private static void allocateFiftyMegabytes() {
        Object[] arrays = new Object[50];
        for (int i = 0; i < arrays.length; i++) {
            arrays[i] = new byte[1024 * 1024];
        }
    }

    /**
     * @param name
     * @throws IOException
     */
    protected void deleteFile(String name) throws IOException {
        String diskPath = System.getProperty("java.io.tmpdir");
        final File diskDir = new File(diskPath);
        File dataFile = new File(diskDir, name + ".data");
        if (dataFile.exists()) {
            dataFile.delete();
        }
        File indexFile = new File(diskDir, name + ".index");
        if (indexFile.exists()) {
            indexFile.delete();
        }
    }

    /**
     * Measure memory used by the VM.
     *
     * @return
     * @throws InterruptedException
     */
    protected static long measureMemoryUse() throws InterruptedException {
        gc();
        long total;
        long freeAfter;
        long freeBefore;
        Runtime runtime = Runtime.getRuntime();
        do {
            total = runtime.totalMemory();
            freeBefore = runtime.freeMemory();
            System.err.println("Total: " + total + "\tFree: " + freeBefore + "\tGCing...");
            gc();
            freeAfter = runtime.freeMemory();
            System.err.println("\tFree: " + freeAfter);
        } while (total != runtime.totalMemory() || freeAfter < freeBefore);
        return total - freeAfter;
    }

    private static void gc() throws InterruptedException {
        for (int i = 0; i < 3; i++) {
            System.gc();
            Thread.sleep(100);
            System.runFinalization();
            Thread.sleep(100);
        }
    }

    /**
     * Runs a set of threads, for a fixed amount of time.
     * <p/>
     * Throws an exception if there are throwables during the run.
     */
    protected void runThreads(final List executables) throws Exception {
        int failures = runThreadsNoCheck(executables);
        LOG.info(failures + " failures");
        //CHM does have the occasional very slow time.
        assertTrue("Failures = " + failures, failures == 0);
    }


    /**
     * Runs a set of threads, for a fixed amount of time.
     * <p/>
     * Does not fail if throwables are thrown.
     *
     * @return the number of Throwables thrown while running
     */
    protected int runThreadsNoCheck(final List executables) throws Exception {
        return runThreadsNoCheck(executables, false);
    }

    /**
     * Runs a set of threads, for a fixed amount of time.
     * <p/>
     * Does not fail if throwables are thrown.
     *
     * @param executables the list of executables to execute
     * @param explicitLog whether to log detailed AsserttionErrors or not
     * @return the number of Throwables thrown while running
     */
    protected int runThreadsNoCheck(final List executables, final boolean explicitLog) throws Exception {

        final long endTime = System.currentTimeMillis() + 10000;
        final List<Throwable> errors = new ArrayList<Throwable>();

        // Spin up the threads
        final Thread[] threads = new Thread[executables.size()];
        for (int i = 0; i < threads.length; i++) {
            final Executable executable = (Executable) executables.get(i);
            threads[i] = new Thread() {
                @Override
                public void run() {
                    try {
                        // Run the thread until the given end time
                        while (System.currentTimeMillis() < endTime) {
                            Assert.assertNotNull(executable);
                            executable.execute();
                        }
                    } catch (Throwable t) {
                        // Hang on to any errors
                        errors.add(t);
                        if (!explicitLog && t instanceof AssertionError) {
                            LOG.info("Throwable " + t + " " + t.getMessage());
                        } else {
                            LOG.error("Throwable " + t + " " + t.getMessage(), t);
                        }
                    }
                }
            };
            threads[i].start();
        }

        // Wait for the threads to finish
        for (Thread thread : threads) {
            thread.join();
        }

//        if (errors.size() > 0) {
//            for (Throwable error : errors) {
//                LOG.info("Error", error);
//            }
//        }
        return errors.size();
    }

    /**
     * Obtains an MBeanServer, which varies with Java version
     *
     * @return
     */
    public MBeanServer createMBeanServer() {
        try {
            Class managementFactoryClass = Class.forName("java.lang.management.ManagementFactory");
            Method method = managementFactoryClass.getMethod("getPlatformMBeanServer", (Class[]) null);
            return (MBeanServer) method.invoke(null, (Object[]) null);
        } catch (Exception e) {
            LOG.info("JDK1.5 ManagementFactory not found. Falling back to JMX1.2.1", e);
            return MBeanServerFactory.createMBeanServer("SimpleAgent");
        }
    }


    /**
     * A runnable, that can throw an exception.
     */
    protected interface Executable {
        /**
         * Executes this object.
         *
         * @throws Exception
         */
        void execute() throws Exception;
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
}

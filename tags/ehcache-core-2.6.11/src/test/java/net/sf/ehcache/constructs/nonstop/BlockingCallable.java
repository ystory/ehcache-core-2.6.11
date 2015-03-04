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

package net.sf.ehcache.constructs.nonstop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;

public class BlockingCallable implements Callable<Void> {

    private static final Logger LOG = LoggerFactory.getLogger(BlockingCallable.class);

    private final boolean logExecution;
    private final Object monitor = new Object();
    private volatile boolean blocked = true;

    public BlockingCallable() {
        this(false);
    }

    public BlockingCallable(boolean logExecution) {
        this.logExecution = logExecution;
    }

    public Void call() throws Exception {
        if (logExecution) {
            LOG.info("inside blocking callable");
        }
        while (blocked) {
            synchronized (monitor) {
                monitor.wait();
            }
        }
        return null;
    }

    public void unblock() {
        synchronized (monitor) {
            blocked = false;
            monitor.notifyAll();
        }
    }

}

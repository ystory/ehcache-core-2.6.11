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

package net.sf.ehcache.pool.impl;

import net.sf.ehcache.pool.PoolableStore;
import net.sf.ehcache.pool.PoolEvictor;

import java.util.Collection;

/**
 * @author Ludovic Orban
 */
public class RoundRobinOnHeapPoolEvictor implements PoolEvictor<PoolableStore> {
    public boolean freeSpace(Collection<PoolableStore> from, long bytes) {
        long remaining = bytes;

        while (true) {
            for (PoolableStore poolableStore : from) {
                long beforeEvictionSize = poolableStore.getInMemorySizeInBytes();
                if (!poolableStore.evictFromOnHeap(1, bytes)) {
                    return false;
                }
                long afterEvictionSize = poolableStore.getInMemorySizeInBytes();

                remaining -= (beforeEvictionSize - afterEvictionSize);
                if (remaining <= 0L) {
                    return true;
                }
            }
        }
    }
}

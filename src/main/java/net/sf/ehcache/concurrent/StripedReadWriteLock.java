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

package net.sf.ehcache.concurrent;

import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;

/**
 * @author Alex Snaps
 */
public interface StripedReadWriteLock extends CacheLockProvider {

    /**
     * Returns a ReadWriteLock for a particular key
     * @param key the key
     * @return the lock
     */
    ReadWriteLock getLockForKey(Object key);

    /**
     * Returns all Syncs
     * @return
     */
    List<ReadWriteLockSync> getAllSyncs();

}

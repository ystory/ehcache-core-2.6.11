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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;


/**
 * Tests for CacheException
 *
 * @author Greg Luck, Claus Ibsen
 * @version $Id$
 */
public class CacheExceptionTest extends AbstractCacheTest {

    /**
     * This will fail on JDK1.3 and lower.
     */
    @Test
    public void testFullConstructor() {
        try {
            throw new CacheException("test");
        } catch (CacheException e) {
            assertEquals("test", e.getMessage());
        } catch (NoSuchMethodError e) {
            if (System.getProperty("java.version").startsWith("1.3")) {
                assertTrue(true);
            }
        }
    }
}

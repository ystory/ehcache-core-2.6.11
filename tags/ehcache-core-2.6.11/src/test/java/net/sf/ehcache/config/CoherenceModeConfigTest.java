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

import junit.framework.TestCase;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.config.TerracottaConfiguration.Consistency;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CoherenceModeConfigTest extends TestCase {

    private static final Logger LOG = LoggerFactory.getLogger(CoherenceModeConfigTest.class);

    @Test
    public void testCoherenceModeConfig() {
        CacheManager cacheManager = new CacheManager(this.getClass().getResourceAsStream("/ehcache-coherence-mode-test.xml"));
        Cache cache = cacheManager.getCache("defaultCoherenceMode");
        boolean coherent;
        Consistency consistency;
        coherent = cache.getCacheConfiguration().getTerracottaConfiguration().isCoherent();
        consistency = cache.getCacheConfiguration().getTerracottaConfiguration().getConsistency();
        LOG.info("Default coherent: " + coherent);
        LOG.info("Default Coherence mode: " + consistency);
        final boolean expectedDefault = TerracottaConfiguration.DEFAULT_CONSISTENCY_TYPE == Consistency.STRONG ? true : false;
        assertEquals(expectedDefault, coherent);
        assertEquals(TerracottaConfiguration.DEFAULT_CONSISTENCY_TYPE, consistency);

        cache = cacheManager.getCache("falseCoherenceMode");
        coherent = cache.getCacheConfiguration().getTerracottaConfiguration().isCoherent();
        consistency = cache.getCacheConfiguration().getTerracottaConfiguration().getConsistency();
        LOG.info("False coherent: " + coherent);
        LOG.info("False Coherence mode: " + consistency);
        assertEquals(false, coherent);
        assertEquals(Consistency.EVENTUAL, consistency);

        cache = cacheManager.getCache("trueCoherenceMode");
        coherent = cache.getCacheConfiguration().getTerracottaConfiguration().isCoherent();
        consistency = cache.getCacheConfiguration().getTerracottaConfiguration().getConsistency();
        LOG.info("True coherent: " + coherent);
        LOG.info("True Coherence mode: " + consistency);
        assertEquals(true, coherent);
        assertEquals(Consistency.STRONG, consistency);

        TerracottaConfiguration tcConfig = cache.getCacheConfiguration().getTerracottaConfiguration();
        tcConfig.setCoherent(false);
        assertEquals(Consistency.EVENTUAL, cache.getCacheConfiguration().getTerracottaConfiguration().getConsistency());

        tcConfig.setCoherent(true);
        assertEquals(Consistency.STRONG, cache.getCacheConfiguration().getTerracottaConfiguration().getConsistency());

        tcConfig.setConsistency(Consistency.EVENTUAL);
        assertEquals(Consistency.EVENTUAL, cache.getCacheConfiguration().getTerracottaConfiguration().getConsistency());

        tcConfig.setConsistency(Consistency.STRONG);
        assertEquals(Consistency.STRONG, cache.getCacheConfiguration().getTerracottaConfiguration().getConsistency());
    }

}

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

package net.sf.ehcache.management.sampled;

import net.sf.ehcache.Ehcache;
import net.sf.ehcache.hibernate.management.impl.BaseEmitterBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.Map;

import javax.management.MBeanNotificationInfo;
import javax.management.NotCompliantMBeanException;
import javax.management.Notification;

/**
 * An implementation of {@link SampledCacheMBean}
 *
 * @author <a href="mailto:asanoujam@terracottatech.com">Abhishek Sanoujam</a>
 * @author <a href="mailto:byoukste@terracottatech.com">byoukste</a>
 */
public class SampledCache extends BaseEmitterBean implements SampledCacheMBean, PropertyChangeListener {
    private static final Logger LOG = LoggerFactory.getLogger(SampledCache.class);

    private static final MBeanNotificationInfo[] NOTIFICATION_INFO;

    private final CacheSamplerImpl sampledCacheDelegate;

    private final String immutableCacheName;

    static {
        final String[] notifTypes = new String[] {CACHE_ENABLED, CACHE_CHANGED, CACHE_FLUSHED, CACHE_STATISTICS_ENABLED,
            CACHE_STATISTICS_RESET};
        final String name = Notification.class.getName();
        final String description = "Ehcache SampledCache Event";
        NOTIFICATION_INFO = new MBeanNotificationInfo[] {new MBeanNotificationInfo(notifTypes, name, description)};
    }

    /**
     * Constructor accepting the backing {@link Ehcache}
     *
     * @param cache the cache object to use in initializing this sampled jmx mbean
     * @throws NotCompliantMBeanException if object doesn't comply with mbean spec
     */
    public SampledCache(Ehcache cache) throws NotCompliantMBeanException {
        super(SampledCacheMBean.class);
        immutableCacheName = cache.getName();

        cache.addPropertyChangeListener(this);
        this.sampledCacheDelegate = new CacheSamplerImpl(cache);
    }

    /**
     * Method which returns the name of the cache at construction time.
     * Package protected method.
     *
     * @return The name of the cache
     */
    String getImmutableCacheName() {
        return immutableCacheName;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isEnabled() {
        return sampledCacheDelegate.isEnabled();
    }

    /**
     * {@inheritDoc}
     */
    public void setEnabled(boolean enabled) {
        sampledCacheDelegate.setEnabled(enabled);
    }

    /**
     * {@inheritDoc}
     */
    public boolean isClusterBulkLoadEnabled() {
        return sampledCacheDelegate.isClusterBulkLoadEnabled();
    }

    /**
     * {@inheritDoc}
     */
    public boolean isNodeBulkLoadEnabled() {
        return sampledCacheDelegate.isNodeBulkLoadEnabled();
    }

    /**
     * {@inheritDoc}
     */
    public void setNodeBulkLoadEnabled(boolean bulkLoadEnabled) {
        sampledCacheDelegate.setNodeBulkLoadEnabled(bulkLoadEnabled);
    }

    /**
     * {@inheritDoc}
     */
    public void flush() {
        sampledCacheDelegate.flush();
        sendNotification(CACHE_FLUSHED, getCacheAttributes(), getImmutableCacheName());
    }

    /**
     * {@inheritDoc}
     */
    public String getCacheName() {
        return sampledCacheDelegate.getCacheName();
    }

    /**
     * {@inheritDoc}
     */
    public String getStatus() {
        return sampledCacheDelegate.getStatus();
    }

    /**
     * {@inheritDoc}
     */
    public void removeAll() {
        sampledCacheDelegate.removeAll();
        sendNotification(CACHE_CLEARED, getCacheAttributes(), getImmutableCacheName());
    }

    /**
     * {@inheritDoc}
     */
    public long getAverageGetTimeMostRecentSample() {
        return sampledCacheDelegate.getAverageGetTimeMostRecentSample();
    }

    @Override
    public long getAverageGetTimeNanosMostRecentSample() {
        return sampledCacheDelegate.getAverageGetTimeNanosMostRecentSample();
    }

    /**
     * {@inheritDoc}
     */
    public long getCacheEvictionRate() {
        return sampledCacheDelegate.getCacheEvictionRate();
    }

    /**
     * {@inheritDoc}
     */
    public long getCacheElementEvictedMostRecentSample() {
        return sampledCacheDelegate.getCacheElementEvictedMostRecentSample();
    }

    /**
     * {@inheritDoc}
     */
    public long getCacheExpirationRate() {
        return sampledCacheDelegate.getCacheExpirationRate();
    }

    /**
     * {@inheritDoc}
     */
    public long getCacheElementExpiredMostRecentSample() {
        return sampledCacheDelegate.getCacheElementExpiredMostRecentSample();
    }

    /**
     * {@inheritDoc}
     */
    public long getCachePutRate() {
        return sampledCacheDelegate.getCachePutRate();
    }

    /**
     * {@inheritDoc}
     */
    public long getCacheElementPutMostRecentSample() {
        return sampledCacheDelegate.getCacheElementPutMostRecentSample();
    }

    /**
     * {@inheritDoc}
     */
    public long getCacheRemoveRate() {
        return sampledCacheDelegate.getCacheRemoveRate();
    }

    /**
     * {@inheritDoc}
     */
    public long getCacheElementRemovedMostRecentSample() {
        return sampledCacheDelegate.getCacheElementRemovedMostRecentSample();
    }

    /**
     * {@inheritDoc}
     */
    public long getCacheUpdateRate() {
        return getCacheElementUpdatedMostRecentSample();
    }

    /**
     * {@inheritDoc}
     */
    public long getCacheElementUpdatedMostRecentSample() {
        return sampledCacheDelegate.getCacheElementUpdatedMostRecentSample();
    }

    /**
     * {@inheritDoc}
     */
    public long getCacheInMemoryHitRate() {
        return getCacheHitInMemoryMostRecentSample();
    }

    /**
     * {@inheritDoc}
     */
    public long getCacheHitInMemoryMostRecentSample() {
        return sampledCacheDelegate.getCacheHitInMemoryMostRecentSample();
    }

    /**
     * {@inheritDoc}
     */
    public long getCacheOffHeapHitRate() {
        return sampledCacheDelegate.getCacheOffHeapHitRate();
    }

    /**
     * {@inheritDoc}
     */
    public long getCacheHitOffHeapMostRecentSample() {
        return sampledCacheDelegate.getCacheHitOffHeapMostRecentSample();
    }

    /**
     * {@inheritDoc}
     */
    public long getCacheHitRate() {
        return sampledCacheDelegate.getCacheHitRate();
    }

    /**
     * {@inheritDoc}
     */
    public long getCacheHitMostRecentSample() {
        return sampledCacheDelegate.getCacheHitMostRecentSample();
    }

    /**
     * {@inheritDoc}
     */
    public long getCacheOnDiskHitRate() {
        return sampledCacheDelegate.getCacheOnDiskHitRate();
    }

    /**
     * {@inheritDoc}
     */
    public long getCacheHitOnDiskMostRecentSample() {
        return sampledCacheDelegate.getCacheHitOnDiskMostRecentSample();
    }

    /**
     * {@inheritDoc}
     */
    public long getCacheMissExpiredMostRecentSample() {
        return sampledCacheDelegate.getCacheMissExpiredMostRecentSample();
    }

    /**
     * {@inheritDoc}
     */
    public long getCacheMissRate() {
        return sampledCacheDelegate.getCacheMissRate();
    }

    /**
     * {@inheritDoc}
     */
    public long getCacheMissMostRecentSample() {
        return sampledCacheDelegate.getCacheMissMostRecentSample();
    }

    /**
     * {@inheritDoc}
     */
    public long getCacheInMemoryMissRate() {
        return sampledCacheDelegate.getCacheInMemoryMissRate();
    }

    /**
     * {@inheritDoc}
     */
    public long getCacheMissInMemoryMostRecentSample() {
        return sampledCacheDelegate.getCacheMissInMemoryMostRecentSample();
    }

    /**
     * {@inheritDoc}
     */
    public long getCacheOffHeapMissRate() {
        return sampledCacheDelegate.getCacheOffHeapMissRate();
    }

    /**
     * {@inheritDoc}
     */
    public long getCacheMissOffHeapMostRecentSample() {
        return sampledCacheDelegate.getCacheMissOffHeapMostRecentSample();
    }

    /**
     * {@inheritDoc}
     */
    public long getCacheOnDiskMissRate() {
        return sampledCacheDelegate.getCacheOnDiskMissRate();
    }

    /**
     * {@inheritDoc}
     */
    public long getCacheMissOnDiskMostRecentSample() {
        return sampledCacheDelegate.getCacheMissOnDiskMostRecentSample();
    }

    /**
     * {@inheritDoc}
     */
    public long getCacheMissNotFoundMostRecentSample() {
        return sampledCacheDelegate.getCacheMissNotFoundMostRecentSample();
    }

    /**
     * {@inheritDoc}
     */
    public int getStatisticsAccuracy() {
        return sampledCacheDelegate.getStatisticsAccuracy();
    }

    /**
     * {@inheritDoc}
     */
    public String getStatisticsAccuracyDescription() {
        return sampledCacheDelegate.getStatisticsAccuracyDescription();
    }

    /**
     * {@inheritDoc}
     */
    public void clearStatistics() {
        sampledCacheDelegate.clearStatistics();
        sendNotification(CACHE_STATISTICS_RESET, getCacheAttributes(), getImmutableCacheName());
    }

    /**
     * {@inheritDoc}
     */
    public boolean isStatisticsEnabled() {
        return sampledCacheDelegate.isStatisticsEnabled();
    }

    /**
     * {@inheritDoc}
     */
    public boolean isSampledStatisticsEnabled() {
        return sampledCacheDelegate.isSampledStatisticsEnabled();
    }

    /**
     * {@inheritDoc}
     */
    public boolean isTerracottaClustered() {
        return sampledCacheDelegate.isTerracottaClustered();
    }

    /**
     * {@inheritDoc}
     */
    public String getTerracottaConsistency() {
        return sampledCacheDelegate.getTerracottaConsistency();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#enableStatistics()
     */
    public void enableStatistics() {
        sampledCacheDelegate.enableStatistics();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#disableStatistics()
     */
    public void disableStatistics() {
        sampledCacheDelegate.disableStatistics();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#setStatisticsEnabled(boolean)
     */
    public void setStatisticsEnabled(boolean statsEnabled) {
        sampledCacheDelegate.setStatisticsEnabled(statsEnabled);
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#enableSampledStatistics()
     */
    public void enableSampledStatistics() {
        sampledCacheDelegate.enableSampledStatistics();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#disableSampledStatistics ()
     */
    public void disableSampledStatistics() {
        sampledCacheDelegate.disableSampledStatistics();
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated use {@link #setNodeBulkLoadEnabled(boolean)} instead
     */
    @Deprecated
    public void setNodeCoherent(boolean coherent) {
        boolean isNodeCoherent = isNodeCoherent();
        if (coherent != isNodeCoherent) {
            if (!coherent && getTransactional()) {
                LOG.warn("a transactional cache cannot be incoherent");
                return;
            }
            try {
                sampledCacheDelegate.getCache().setNodeCoherent(coherent);
            } catch (RuntimeException e) {
                throw Utils.newPlainException(e);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Deprecated
    public boolean isClusterCoherent() {
        try {
            return sampledCacheDelegate.getCache().isClusterCoherent();
        } catch (RuntimeException e) {
            throw Utils.newPlainException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Deprecated
    public boolean isNodeCoherent() {
        try {
            return sampledCacheDelegate.getCache().isNodeCoherent();
        } catch (RuntimeException e) {
            throw Utils.newPlainException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    public long getCacheAverageGetTime() {
        return sampledCacheDelegate.getCacheAverageGetTime();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#getAverageGetTimeMillis()
     */
    public float getAverageGetTimeMillis() {
        return sampledCacheDelegate.getAverageGetTimeMillis();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#getAverageGetTimeNanos()
     */
    public long getAverageGetTimeNanos() {
        return sampledCacheDelegate.getAverageGetTimeNanos();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.statistics.LiveCacheStatistics#getMaxGetTimeMillis()
     */
    public long getMaxGetTimeMillis() {
        return sampledCacheDelegate.getMaxGetTimeMillis();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.statistics.LiveCacheStatistics#getMinGetTimeMillis()
     */
    public long getMinGetTimeMillis() {
        return sampledCacheDelegate.getMinGetTimeMillis();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.statistics.LiveCacheStatistics#getMaxGetTimeNanos()
     */
    public long getMaxGetTimeNanos() {
        return sampledCacheDelegate.getMaxGetTimeNanos();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.statistics.LiveCacheStatistics#getMinGetTimeNanos()
     */
    public long getMinGetTimeNanos() {
        return sampledCacheDelegate.getMinGetTimeNanos();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.statistics.LiveCacheStatistics#getXaCommitCount()
     */
    public long getXaCommitCount() {
        return sampledCacheDelegate.getXaCommitCount();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.statistics.LiveCacheStatistics#getXaRollbackCount()
     */
    public long getXaRollbackCount() {
        return sampledCacheDelegate.getXaRollbackCount();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.statistics.LiveCacheStatistics#getXaRecoveredCount()
     */
    public long getXaRecoveredCount() {
        return sampledCacheDelegate.getXaRecoveredCount();
    }

    /**
     * {@inheritDoc}
     */
    public boolean getHasWriteBehindWriter() {
        return sampledCacheDelegate.getHasWriteBehindWriter();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.statistics.LiveCacheStatistics#getWriterQueueLength()
     */
    public long getWriterQueueLength() {
        return sampledCacheDelegate.getWriterQueueLength();
    }

    /**
     * {@inheritDoc}
     */
    public int getWriterMaxQueueSize() {
        return sampledCacheDelegate.getWriterMaxQueueSize();
    }

    /**
     * {@inheritDoc}
     */
    public int getWriterConcurrency() {
        return sampledCacheDelegate.getWriterConcurrency();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#getCacheHitCount()
     */
    public long getCacheHitCount() {
        return sampledCacheDelegate.getCacheHitCount();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#getCacheMissCount()
     */
    public long getCacheMissCount() {
        return sampledCacheDelegate.getCacheMissCount();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#getInMemoryMissCount()
     */
    public long getInMemoryMissCount() {
        return sampledCacheDelegate.getInMemoryMissCount();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#getOffHeapMissCount()
     */
    public long getOffHeapMissCount() {
        return sampledCacheDelegate.getOffHeapMissCount();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#getOnDiskMissCount()
     */
    public long getOnDiskMissCount() {
        return sampledCacheDelegate.getOnDiskMissCount();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#getCacheMissCountExpired()
     */
    public long getCacheMissCountExpired() {
        return sampledCacheDelegate.getCacheMissCountExpired();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#getDiskExpiryThreadIntervalSeconds()
     */
    public long getDiskExpiryThreadIntervalSeconds() {
        return sampledCacheDelegate.getDiskExpiryThreadIntervalSeconds();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#setDiskExpiryThreadIntervalSeconds(long)
     */
    public void setDiskExpiryThreadIntervalSeconds(long seconds) {
        sampledCacheDelegate.setDiskExpiryThreadIntervalSeconds(seconds);
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#getMaxEntriesLocalHeap()
     */
    public long getMaxEntriesLocalHeap() {
        return sampledCacheDelegate.getMaxEntriesLocalHeap();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#setMaxEntriesLocalHeap(long)
     */
    public void setMaxEntriesLocalHeap(long maxEntries) {
        sampledCacheDelegate.setMaxEntriesLocalHeap(maxEntries);
        sendNotification(CACHE_CHANGED, getCacheAttributes(), getImmutableCacheName());
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#getMaxBytesLocalHeap()
     */
    public long getMaxBytesLocalHeap() {
        return sampledCacheDelegate.getMaxBytesLocalHeap();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#setMaxBytesLocalHeap(long)
     */
    public void setMaxBytesLocalHeap(long maxBytes) {
        sampledCacheDelegate.setMaxBytesLocalHeap(maxBytes);
        sendNotification(CACHE_CHANGED, getCacheAttributes(), getImmutableCacheName());
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#setMaxBytesLocalHeapAsString(String)
     */
    public void setMaxBytesLocalHeapAsString(String maxBytes) {
        sampledCacheDelegate.setMaxBytesLocalHeapAsString(maxBytes);
        sendNotification(CACHE_CHANGED, getCacheAttributes(), getImmutableCacheName());
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#getMaxBytesLocalHeapAsString()
     */
    public String getMaxBytesLocalHeapAsString() {
        return sampledCacheDelegate.getMaxBytesLocalHeapAsString();
    }

    /**
     * {@inheritDoc}
     */
    public int getMaxElementsInMemory() {
        return sampledCacheDelegate.getCache().getCacheConfiguration().getMaxElementsInMemory();
    }

    /**
     * {@inheritDoc}
     */
    public void setMaxElementsInMemory(int maxElements) {
        if (getMaxElementsInMemory() != maxElements) {
            try {
                sampledCacheDelegate.getCache().getCacheConfiguration().setMaxElementsInMemory(maxElements);
            } catch (RuntimeException e) {
                throw Utils.newPlainException(e);
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#getMaxEntriesLocalDisk()
     */
    public long getMaxEntriesLocalDisk() {
        return sampledCacheDelegate.getMaxEntriesLocalDisk();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#setMaxEntriesLocalDisk(long)
     */
    public void setMaxEntriesLocalDisk(long maxEntries) {
        sampledCacheDelegate.setMaxEntriesLocalDisk(maxEntries);
        sendNotification(CACHE_CHANGED, getCacheAttributes(), getImmutableCacheName());
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#setMaxBytesLocalDisk(long)
     */
    public void setMaxBytesLocalDisk(long maxBytes) {
        sampledCacheDelegate.setMaxBytesLocalDisk(maxBytes);
        sendNotification(CACHE_CHANGED, getCacheAttributes(), getImmutableCacheName());
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#setMaxBytesLocalDiskAsString(String)
     */
    public void setMaxBytesLocalDiskAsString(String maxBytes) {
        sampledCacheDelegate.setMaxBytesLocalDiskAsString(maxBytes);
        sendNotification(CACHE_CHANGED, getCacheAttributes(), getImmutableCacheName());
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#getMaxBytesLocalDiskAsString()
     */
    public String getMaxBytesLocalDiskAsString() {
        return sampledCacheDelegate.getMaxBytesLocalDiskAsString();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#getMaxElementsOnDisk()
     */
    public int getMaxElementsOnDisk() {
        return sampledCacheDelegate.getMaxElementsOnDisk();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#setMaxElementsOnDisk(int)
     */
    public void setMaxElementsOnDisk(int maxElements) {
        sampledCacheDelegate.setMaxElementsOnDisk(maxElements);
        sendNotification(CACHE_CHANGED, getCacheAttributes(), getImmutableCacheName());
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#getMaxBytesLocalDisk()
     */
    public long getMaxBytesLocalDisk() {
        return sampledCacheDelegate.getMaxBytesLocalDisk();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#getMaxBytesLocalOffHeap()
     */
    public long getMaxBytesLocalOffHeap() {
        return sampledCacheDelegate.getMaxBytesLocalOffHeap();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#getMaxBytesLocalOffHeapAsString()
     */
    public String getMaxBytesLocalOffHeapAsString() {
        return sampledCacheDelegate.getMaxBytesLocalOffHeapAsString();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#getMemoryStoreEvictionPolicy()
     */
    public String getMemoryStoreEvictionPolicy() {
        return sampledCacheDelegate.getMemoryStoreEvictionPolicy();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#setMemoryStoreEvictionPolicy(String)
     */
    public void setMemoryStoreEvictionPolicy(String evictionPolicy) {
        sampledCacheDelegate.setMemoryStoreEvictionPolicy(evictionPolicy);
        sendNotification(CACHE_CHANGED, getCacheAttributes(), getImmutableCacheName());
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#getTimeToIdleSeconds()
     */
    public long getTimeToIdleSeconds() {
        return sampledCacheDelegate.getTimeToIdleSeconds();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#setTimeToIdleSeconds(long)
     */
    public void setTimeToIdleSeconds(long tti) {
        sampledCacheDelegate.setTimeToIdleSeconds(tti);
        sendNotification(CACHE_CHANGED, getCacheAttributes(), getImmutableCacheName());
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#getTimeToLiveSeconds()
     */
    public long getTimeToLiveSeconds() {
        return sampledCacheDelegate.getTimeToLiveSeconds();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#setTimeToLiveSeconds(long)
     */
    public void setTimeToLiveSeconds(long ttl) {
        sampledCacheDelegate.setTimeToLiveSeconds(ttl);
        sendNotification(CACHE_CHANGED, getCacheAttributes(), getImmutableCacheName());
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#isDiskPersistent()
     */
    public boolean isDiskPersistent() {
        return sampledCacheDelegate.isDiskPersistent();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#setDiskPersistent(boolean)
     */
    public void setDiskPersistent(boolean diskPersistent) {
        sampledCacheDelegate.setDiskPersistent(diskPersistent);
        sendNotification(CACHE_CHANGED, getCacheAttributes(), getImmutableCacheName());
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#isEternal()
     */
    public boolean isEternal() {
        return sampledCacheDelegate.isEternal();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#setEternal(boolean)
     */
    public void setEternal(boolean eternal) {
        sampledCacheDelegate.setEternal(eternal);
        sendNotification(CACHE_CHANGED, getCacheAttributes(), getImmutableCacheName());
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#isOverflowToDisk()
     */
    public boolean isOverflowToDisk() {
        return sampledCacheDelegate.isOverflowToDisk();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#setOverflowToDisk(boolean)
     */
    public void setOverflowToDisk(boolean overflowToDisk) {
        sampledCacheDelegate.setOverflowToDisk(overflowToDisk);
        sendNotification(CACHE_CHANGED, getCacheAttributes(), getImmutableCacheName());
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#isLoggingEnabled()
     */
    public boolean isLoggingEnabled() {
        return sampledCacheDelegate.isLoggingEnabled();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#setLoggingEnabled(boolean)
     */
    public void setLoggingEnabled(boolean enabled) {
        sampledCacheDelegate.setLoggingEnabled(enabled);
        sendNotification(CACHE_CHANGED, getCacheAttributes(), getImmutableCacheName());
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#isPinned()
     */
    public boolean isPinned() {
        return sampledCacheDelegate.isPinned();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#getPinnedToStore()
     */
    public String getPinnedToStore() {
        return sampledCacheDelegate.getPinnedToStore();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#getEvictedCount()
     */
    public long getEvictedCount() {
        return sampledCacheDelegate.getEvictedCount();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#getExpiredCount()
     */
    public long getExpiredCount() {
        return sampledCacheDelegate.getExpiredCount();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#getInMemoryHitCount()
     */
    public long getInMemoryHitCount() {
        return sampledCacheDelegate.getInMemoryHitCount();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCache#getInMemorySize()
     * @deprecated use {@link #getLocalHeapSize()}
     */
    @Deprecated
    public long getInMemorySize() {
        return getLocalHeapSize();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#getOffHeapHitCount()
     */
    public long getOffHeapHitCount() {
        return sampledCacheDelegate.getOffHeapHitCount();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#getOffHeapSize()
     * @deprecated use {@link #getLocalOffHeapSize()}
     */
    @Deprecated
    public long getOffHeapSize() {
        return sampledCacheDelegate.getOffHeapSize();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#getOnDiskHitCount()
     */
    public long getOnDiskHitCount() {
        return sampledCacheDelegate.getOnDiskHitCount();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#getOnDiskSize()
     * @deprecated use {@link #getLocalDiskSize()}
     */
    @Deprecated
    public long getOnDiskSize() {
        return sampledCacheDelegate.getOnDiskSize();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#getLocalDiskSize()
     */
    public long getLocalDiskSize() {
        return sampledCacheDelegate.getLocalDiskSize();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#getLocalHeapSize()
     */
    public long getLocalHeapSize() {
        return sampledCacheDelegate.getLocalHeapSize();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#getLocalOffHeapSize()
     */
    public long getLocalOffHeapSize() {
        return sampledCacheDelegate.getLocalOffHeapSize();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#getLocalDiskSizeInBytes()
     */
    public long getLocalDiskSizeInBytes() {
        return sampledCacheDelegate.getLocalDiskSizeInBytes();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#getLocalHeapSizeInBytes()
     */
    public long getLocalHeapSizeInBytes() {
        return sampledCacheDelegate.getLocalHeapSizeInBytes();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#getLocalOffHeapSizeInBytes()
     */
    public long getLocalOffHeapSizeInBytes() {
        return sampledCacheDelegate.getLocalOffHeapSizeInBytes();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#getPutCount()
     */
    public long getPutCount() {
        return sampledCacheDelegate.getPutCount();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#getRemovedCount()
     */
    public long getRemovedCount() {
        return sampledCacheDelegate.getRemovedCount();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#getSize()
     */
    public long getSize() {
        return sampledCacheDelegate.getSize();
    }

    /**
     * {@inheritDoc}
     *
     * @see net.sf.ehcache.management.sampled.SampledCacheMBean#getUpdateCount()
     */
    public long getUpdateCount() {
        return sampledCacheDelegate.getUpdateCount();
    }

    /**
     * getCacheAttributes
     *
     * @return map of attribute name -> value
     */
    public Map<String, Object> getCacheAttributes() {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("Enabled", isEnabled());
        result.put("TerracottaClustered", isTerracottaClustered());
        result.put("LoggingEnabled", isLoggingEnabled());
        result.put("TimeToIdleSeconds", getTimeToIdleSeconds());
        result.put("TimeToLiveSeconds", getTimeToLiveSeconds());
        result.put("MaxEntriesLocalHeap", getMaxEntriesLocalHeap());
        result.put("MaxEntriesLocalDisk", getMaxEntriesLocalDisk());
        result.put("MaxBytesLocalHeapAsString", getMaxBytesLocalHeapAsString());
        result.put("MaxBytesLocalOffHeapAsString", getMaxBytesLocalOffHeapAsString());
        result.put("MaxBytesLocalDiskAsString", getMaxBytesLocalDiskAsString());
        result.put("MaxBytesLocalHeap", getMaxBytesLocalHeap());
        result.put("MaxBytesLocalOffHeap", getMaxBytesLocalOffHeap());
        result.put("MaxBytesLocalDisk", getMaxBytesLocalDisk());
        result.put("DiskPersistent", isDiskPersistent());
        result.put("Eternal", isEternal());
        result.put("OverflowToDisk", isOverflowToDisk());
        result.put("DiskExpiryThreadIntervalSeconds", getDiskExpiryThreadIntervalSeconds());
        result.put("MemoryStoreEvictionPolicy", getMemoryStoreEvictionPolicy());
        result.put("TerracottaConsistency", getTerracottaConsistency());
        if (isTerracottaClustered()) {
            result.put("NodeBulkLoadEnabled", isNodeBulkLoadEnabled());
            result.put("NodeCoherent", isNodeCoherent());
            result.put("ClusterBulkLoadEnabled", isClusterBulkLoadEnabled());
            result.put("ClusterCoherent", isClusterCoherent());
        }
        result.put("StatisticsEnabled", isStatisticsEnabled());
        result.put("WriterConcurrency", getWriterConcurrency());
        result.put("Transactional", getTransactional());
        result.put("PinnedToStore", getPinnedToStore());
        return result;
    }

    /**
     * @see BaseEmitterBean#getNotificationInfo()
     */
    @Override
    public MBeanNotificationInfo[] getNotificationInfo() {
        return NOTIFICATION_INFO;
    }

    /**
     * @see java.beans.PropertyChangeListener#propertyChange(java.beans.PropertyChangeEvent)
     */
    public void propertyChange(PropertyChangeEvent evt) {
        sendNotification(CACHE_CHANGED, getCacheAttributes(), getImmutableCacheName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doDispose() {
        sampledCacheDelegate.dispose();
    }

    /**
     * {@inheritDoc}
     */
    public long getAverageSearchTime() {
        return sampledCacheDelegate.getAverageSearchTime();
    }

    /**
     * {@inheritDoc}
     */
    public long getSearchesPerSecond() {
        return sampledCacheDelegate.getSearchesPerSecond();
    }

    /**
     * {@inheritDoc}
     */
    public boolean getTransactional() {
        return sampledCacheDelegate.getTransactional();
    }

    /**
     * {@inheritDoc}
     */
    public boolean getSearchable() {
        return sampledCacheDelegate.getSearchable();
    }

    /**
     * {@inheritDoc}
     */
    public long getCacheSearchRate() {
        return sampledCacheDelegate.getCacheSearchRate();
    }

    /**
     * {@inheritDoc}
     */
    public long getCacheAverageSearchTime() {
        return sampledCacheDelegate.getCacheAverageSearchTime();
    }

    /**
     * {@inheritDoc}
     */
    public long getTransactionCommitRate() {
        return getCacheXaCommitsMostRecentSample();
    }

    /**
     * {@inheritDoc}
     */
    public long getCacheXaCommitsMostRecentSample() {
        return sampledCacheDelegate.getCacheXaCommitsMostRecentSample();
    }

    /**
     * {@inheritDoc}
     */
    public long getTransactionRollbackRate() {
        return getCacheXaRollbacksMostRecentSample();
    }

    /**
     * {@inheritDoc}
     */
    public long getCacheXaRollbacksMostRecentSample() {
        return sampledCacheDelegate.getCacheXaRollbacksMostRecentSample();
    }

    /**
     * {@inheritDoc}
     */
    public int getCacheHitRatio() {
        return sampledCacheDelegate.getCacheHitRatio();
    }

    /**
     * {@inheritDoc}
     */
    public int getCacheHitRatioMostRecentSample() {
        return sampledCacheDelegate.getCacheHitRatioMostRecentSample();
    }
}
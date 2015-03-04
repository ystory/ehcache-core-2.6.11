package net.sf.ehcache.store.disk;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheStoreHelper;
import net.sf.ehcache.store.DiskBackedMemoryStore;
import net.sf.ehcache.store.Store;

import java.lang.reflect.Field;
import java.util.concurrent.Future;

/**
 * @author Alex Snaps
 */
public class DiskStoreHelper {

    public static Future<Void> flushAllEntriesToDisk(final Cache cache) {
        CacheStoreHelper cacheStoreHelper = new CacheStoreHelper(cache);
        return flushAllEntriesToDisk(cacheStoreHelper.getStore());
    }

    public static Future<Void> flushAllEntriesToDisk(final Store store) {
        if(store instanceof DiskBackedMemoryStore) {
            final DiskStore authority = getField("authority", store);
            return flushAllEntriesToDisk(authority);
        } else if (store instanceof DiskStore) {
            final DiskStorageFactory factory = getField("disk", store);
            return factory.flush();
        } else {
            return null;
        }
    }

    private static <T> T getField(final String fieldName, final Object obj) {
        try {
            Field field = null;
            Class clazz = obj.getClass();
            while(field == null && clazz != null) {
                try {
                    field = clazz.getDeclaredField(fieldName);
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                } catch (SecurityException e) {
                    throw new RuntimeException(e);
                }
            }
            field.setAccessible(true);
            return (T) field.get(obj);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}

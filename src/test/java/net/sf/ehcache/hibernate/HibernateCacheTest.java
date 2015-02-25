/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.ehcache.hibernate;

import java.lang.reflect.Method;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.Status;
import net.sf.ehcache.hibernate.domain.Event;
import net.sf.ehcache.hibernate.domain.EventManager;

import net.sf.ehcache.hibernate.domain.Item;
import net.sf.ehcache.hibernate.domain.Person;
import net.sf.ehcache.hibernate.domain.PhoneNumber;
import net.sf.ehcache.hibernate.domain.VersionedItem;
import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cache.access.SoftLock;
import org.hibernate.cfg.Configuration;
import org.hibernate.stat.QueryStatistics;

import org.hibernate.stat.SecondLevelCacheStatistics;
import org.hibernate.stat.Statistics;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Chris Dennis
 */
public class HibernateCacheTest {

    private static SessionFactory sessionFactory;
    private static Configuration config;

    public synchronized static SessionFactory getSessionFactory() {
        if (sessionFactory == null) {
            try {
                sessionFactory = config.buildSessionFactory();
            } catch (HibernateException ex) {
                System.err.println("Initial SessionFactory creation failed." + ex);
                throw new ExceptionInInitializerError(ex);
            }
        }
        return sessionFactory;
    }

    @BeforeClass
    public static void setUp() {
        System.setProperty("derby.system.home", "target/derby");
        config = new Configuration().configure("/hibernate-config/hibernate.cfg.xml");
        config.setProperty("hibernate.hbm2ddl.auto", "create");
        getSessionFactory().getStatistics().setStatisticsEnabled(true);
    }

    @AfterClass
    public static void tearDown() {
        getSessionFactory().close();
    }
    
    @Before
    public void clearCaches() {
        for (CacheManager manager : CacheManager.ALL_CACHE_MANAGERS) {
            for (String s : manager.getCacheNames()) {
                final Cache cache = manager.getCache(s);
                if(cache.getStatus() == Status.STATUS_ALIVE) {
                    cache.removeAll();
                }
            }
        }
    }

    @Test
    public void testQueryCacheInvalidation() throws Exception {
        Session s = getSessionFactory().openSession();
        Transaction t = s.beginTransaction();
        Item i = new Item();
        i.setName("widget");
        i.setDescription("A really top-quality, full-featured widget.");
        s.persist(i);
        t.commit();
        s.close();

        SecondLevelCacheStatistics slcs = s.getSessionFactory().getStatistics().getSecondLevelCacheStatistics(Item.class.getName());

        assertEquals(1, slcs.getPutCount());
        assertEquals(1, slcs.getElementCountInMemory());
        assertEquals(1, slcs.getEntries().size());

        s = getSessionFactory().openSession();
        t = s.beginTransaction();
        i = (Item) s.get(Item.class, i.getId());

        assertEquals(1, slcs.getHitCount());
        assertEquals(0, slcs.getMissCount());

        i.setDescription("A bog standard item");

        t.commit();
        s.close();

        assertEquals(2, slcs.getPutCount());

        Object entry = slcs.getEntries().get(i.getId());
        Map map;
        if (entry instanceof Map) {
            map = (Map) entry;
        } else {
            Method valueMethod = entry.getClass().getDeclaredMethod("getValue", (Class[]) null);
            valueMethod.setAccessible(true);
            map = (Map) valueMethod.invoke(entry, (Object[]) null);
        }
        assertTrue(map.get("description").equals("A bog standard item"));
        assertTrue(map.get("name").equals("widget"));

        // cleanup
        s = getSessionFactory().openSession();
        t = s.beginTransaction();
        s.delete(i);
        t.commit();
        s.close();
    }

    @Test
    public void testEmptySecondLevelCacheEntry() throws Exception {
        getSessionFactory().evictEntity(Item.class.getName());
        Statistics stats = getSessionFactory().getStatistics();
        stats.clear();
        SecondLevelCacheStatistics statistics = stats.getSecondLevelCacheStatistics(Item.class.getName());
        Map cacheEntries = statistics.getEntries();
        assertEquals(0, cacheEntries.size());
    }

    @Test
    public void testStaleWritesLeaveCacheConsistent() {
        Session s = getSessionFactory().openSession();
        Transaction txn = s.beginTransaction();
        VersionedItem item = new VersionedItem();
        item.setName("steve");
        item.setDescription("steve's item");
        s.save(item);
        txn.commit();
        s.close();

        Long initialVersion = item.getVersion();

        // manually revert the version property
        item.setVersion(new Long(item.getVersion().longValue() - 1));

        try {
            s = getSessionFactory().openSession();
            txn = s.beginTransaction();
            s.update(item);
            txn.commit();
            s.close();
            fail("expected stale write to fail");
        } catch (Throwable expected) {
            // expected behavior here
            if (txn != null) {
                try {
                    txn.rollback();
                } catch (Throwable ignore) {
                }
            }
        } finally {
            if (s != null && s.isOpen()) {
                try {
                    s.close();
                } catch (Throwable ignore) {
                }
            }
        }

        // check the version value in the cache...
        SecondLevelCacheStatistics slcs = getSessionFactory().getStatistics().getSecondLevelCacheStatistics(VersionedItem.class.getName());

        Object entry = slcs.getEntries().get(item.getId());
        Long cachedVersionValue;
        if (entry instanceof SoftLock) {
            //FIXME don't know what to test here
            //cachedVersionValue = new Long( ( (ReadWriteCache.Lock) entry).getUnlockTimestamp() );
        } else {
            cachedVersionValue = (Long) ((Map) entry).get("_version");
            assertEquals(initialVersion.longValue(), cachedVersionValue.longValue());
        }


        // cleanup
        s = getSessionFactory().openSession();
        txn = s.beginTransaction();
        item = (VersionedItem) s.load(VersionedItem.class, item.getId());
        s.delete(item);
        txn.commit();
        s.close();

    }

    @Test
    public void testUnpinsOnRemoval() {
        Session session = getSessionFactory().getCurrentSession();
        Cache entityCache = CacheManager.getCacheManager("tc").getCache(Item.class.getName());
        long maxEntriesLocalHeap = 100;
        entityCache.getCacheConfiguration().setMaxEntriesLocalHeap(maxEntriesLocalHeap);

        session.getTransaction().begin();
        for (int j = 0; j < 500; j++) {
            Item entity = new Item();
            entity.setName(UUID.randomUUID().toString());
            entity.setDescription(UUID.randomUUID().toString());
            session.save(entity);
            session.delete(entity); // This leaves the SoftLock in the cache...
        }
        session.getTransaction().commit(); // ... but they all should be writeable & unpinned after this!

        assertThat(entityCache.getCacheConfiguration().getMaxEntriesLocalHeap(), equalTo(maxEntriesLocalHeap));
        for (Object o : entityCache.getKeys()) {
            final Element e = entityCache.get(o);
            assertFalse(e + " shouldn't be pinned anymore!", entityCache.isPinned(o));
        }
        assertThat(entityCache.getSize(), equalTo((int)maxEntriesLocalHeap));
    }

    @Test
    public void testGeneralUsage() {
        EventManager mgr = new EventManager(getSessionFactory());
        Statistics stats = getSessionFactory().getStatistics();

        // create 3 persons Steve, Orion, Tim
        Person stevePerson = new Person();
        stevePerson.setFirstname("Steve");
        stevePerson.setLastname("Harris");
        Long steveId = mgr.createAndStorePerson(stevePerson);
        mgr.addEmailToPerson(steveId, "steve@tc.com");
        mgr.addEmailToPerson(steveId, "sharrif@tc.com");
        mgr.addTalismanToPerson(steveId, "rabbit foot");
        mgr.addTalismanToPerson(steveId, "john de conqueroo");

        PhoneNumber p1 = new PhoneNumber();
        p1.setNumberType("Office");
        p1.setPhone(111111);
        mgr.addPhoneNumberToPerson(steveId, p1);

        PhoneNumber p2 = new PhoneNumber();
        p2.setNumberType("Home");
        p2.setPhone(222222);
        mgr.addPhoneNumberToPerson(steveId, p2);

        Person orionPerson = new Person();
        orionPerson.setFirstname("Orion");
        orionPerson.setLastname("Letizi");
        Long orionId = mgr.createAndStorePerson(orionPerson);
        mgr.addEmailToPerson(orionId, "orion@tc.com");
        mgr.addTalismanToPerson(orionId, "voodoo doll");

        Long timId = mgr.createAndStorePerson("Tim", "Teck");
        mgr.addEmailToPerson(timId, "teck@tc.com");
        mgr.addTalismanToPerson(timId, "magic decoder ring");

        Long engMeetingId = mgr.createAndStoreEvent("Eng Meeting", stevePerson, new Date());
        mgr.addPersonToEvent(steveId, engMeetingId);
        mgr.addPersonToEvent(orionId, engMeetingId);
        mgr.addPersonToEvent(timId, engMeetingId);

        Long docMeetingId = mgr.createAndStoreEvent("Doc Meeting", orionPerson, new Date());
        mgr.addPersonToEvent(steveId, docMeetingId);
        mgr.addPersonToEvent(orionId, docMeetingId);

        for (Event event : (List<Event>) mgr.listEvents()) {
            mgr.listEmailsOfEvent(event.getId());
        }

        QueryStatistics queryStats = stats.getQueryStatistics("from Event");
        assertEquals("Cache Miss Count", 1L, queryStats.getCacheMissCount());
        assertEquals("Cache Hit Count", 0L, queryStats.getCacheHitCount());
        assertEquals("Cache Put Count", 1L, queryStats.getCachePutCount());
    }
}

package run.cloudclaw.standalone.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisSentinelConnection;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.dao.DataAccessException;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@Profile("standalone")
public class CaffeineCacheConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        log.info("Creating in-memory RedisTemplate for standalone mode");
        MemRedisTemplate t = new MemRedisTemplate();
        t.setKeySerializer(StringRedisSerializer.UTF_8);
        t.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        t.afterPropertiesSet();
        return t;
    }

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager m = new CaffeineCacheManager();
        m.setCacheSpecification("maximumSize=10000,expireAfterWrite=30m");
        return m;
    }

    /**
     * In-memory StringRedisTemplate backed by ConcurrentHashMap.
     */
    static class NopFactory implements RedisConnectionFactory {
        @Override public DataAccessException translateExceptionIfPossible(RuntimeException ex) { return null; }
        @Override public RedisConnection getConnection() { return null; }
        @Override public RedisClusterConnection getClusterConnection() { throw new UnsupportedOperationException(); }
        @Override public RedisSentinelConnection getSentinelConnection() { throw new UnsupportedOperationException(); }
        @Override public boolean getConvertPipelineAndTxResults() { return false; }
    }

    @SuppressWarnings("unchecked")
    static class MemRedisTemplate extends RedisTemplate<String, Object> {
        private final Map<String, Object> store = new ConcurrentHashMap<>();
        private final MemVOps vOps;

        MemRedisTemplate() {
            setConnectionFactory(new NopFactory());
            this.vOps = new MemVOps(store);
        }

        @Override public ValueOperations<String, Object> opsForValue() { return vOps; }
        @Override public Boolean delete(String key) { return store.remove(key) != null; }
        @Override public Long delete(Collection<String> keys) { long c = 0; for (String k : keys) if (store.remove(k) != null) c++; return c; }
        @Override public Boolean expire(String key, Duration timeout) { return store.containsKey(key); }
        @Override public Boolean expire(String key, long timeout, TimeUnit unit) { return store.containsKey(key); }
        @Override public Boolean hasKey(String key) { return store.containsKey(key); }
    }

    @SuppressWarnings("unchecked")
    static class MemVOps implements ValueOperations<String, Object> {
        private final Map<String, Object> store;
        MemVOps(Map<String, Object> store) { this.store = store; }
        @Override public void set(String key, Object value) { store.put(key, value); }
        @Override public void set(String key, Object value, long timeout, TimeUnit unit) { store.put(key, value); }
        @Override public void set(String key, Object value, Duration timeout) { store.put(key, value); }
        @Override public Object get(Object key) { return store.get(key.toString()); }
        @Override public Object getAndSet(String key, Object value) { Object o = store.get(key); store.put(key, value); return o; }
        @Override public Object getAndDelete(String key) { return store.remove(key.toString()); }
        @Override public Boolean setIfAbsent(String key, Object value) { store.putIfAbsent(key, value); return true; }
        @Override public Boolean setIfAbsent(String key, Object value, long timeout, TimeUnit unit) { store.putIfAbsent(key, value); return true; }
        @Override public Boolean setIfAbsent(String key, Object value, Duration timeout) { store.putIfAbsent(key, value); return true; }
        @Override public Boolean setIfPresent(String key, Object value) { store.put(key, value); return true; }
        @Override public Boolean setIfPresent(String key, Object value, long timeout, TimeUnit unit) { store.put(key, value); return true; }
        @Override public Boolean setIfPresent(String key, Object value, Duration timeout) { store.put(key, value); return true; }
        @Override public Long increment(String key) { return 1L; }
        @Override public Long increment(String key, long delta) { return delta; }
        @Override public Double increment(String key, double delta) { return delta; }
        @Override public Long decrement(String key) { return -1L; }
        @Override public Long decrement(String key, long delta) { return -delta; }
        @Override public Long size(String key) { return 0L; }
        @Override public void multiSet(Map<? extends String, ?> m) { m.forEach(store::put); }
        @Override public List<Object> multiGet(Collection<String> keys) { List<Object> r = new ArrayList<>(); keys.forEach(k -> r.add(store.get(k))); return r; }
        @Override public Integer append(String key, String value) { return 0; }
        @Override public String get(String key, long start, long end) { return ""; }
        @Override public void set(String key, Object value, long offset) { store.put(key, value); }
        @Override public Boolean setBit(String key, long offset, boolean value) { return false; }
        @Override public Boolean getBit(String key, long offset) { return false; }
        @Override public List<Long> bitField(String key, org.springframework.data.redis.connection.BitFieldSubCommands subCommands) { return Collections.emptyList(); }
        @Override public Boolean multiSetIfAbsent(Map<? extends String, ?> m) { m.forEach(store::put); return true; }
        @Override public Object getAndExpire(String key, long timeout, TimeUnit unit) { return store.remove(key); }
        @Override public Object getAndExpire(String key, Duration timeout) { return store.remove(key); }
        @Override public Object getAndPersist(String key) { return store.get(key); }
        @Override public RedisOperations<String, Object> getOperations() { return null; }
    }
}

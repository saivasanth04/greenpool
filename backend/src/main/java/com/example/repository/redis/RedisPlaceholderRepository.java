package com.example.repository.redis;

import com.example.entity.redis.DummyRedisEntity;
import org.springframework.data.keyvalue.repository.KeyValueRepository;
import org.springframework.stereotype.Repository;

// Dummy repository to ensure Spring Data Redis has something to scan
@Repository
public interface RedisPlaceholderRepository extends KeyValueRepository<DummyRedisEntity, String> {
}

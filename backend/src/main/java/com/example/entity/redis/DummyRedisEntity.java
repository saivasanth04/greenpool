package com.example.entity.redis;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.io.Serializable;

@RedisHash("dummy")
public class DummyRedisEntity implements Serializable {
    @Id
    private String id;
}

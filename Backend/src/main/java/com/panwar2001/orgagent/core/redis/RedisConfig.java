package com.panwar2001.orgagent.core.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

import tools.jackson.databind.ObjectMapper;

/**
 * Redis wiring.
 *
 * <p>Keys are always plain strings so they can be inspected and reasoned about with {@code redis-cli};
 * values are JSON so a stored turn stays readable and survives a change of Java class layout. The
 * serializer is the Jackson 3 based one, matching the mapper Boot already configures for HTTP.
 */
@Configuration(proxyBeanMethods = false)
public class RedisConfig {

	@Bean
	RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
		RedisTemplate<String, Object> template = new RedisTemplate<>();
		template.setConnectionFactory(connectionFactory);
		template.setKeySerializer(RedisSerializer.string());
		template.setHashKeySerializer(RedisSerializer.string());

		GenericJacksonJsonRedisSerializer json = new GenericJacksonJsonRedisSerializer(objectMapper);
		template.setValueSerializer(json);
		template.setHashValueSerializer(json);
		template.afterPropertiesSet();
		return template;
	}

}

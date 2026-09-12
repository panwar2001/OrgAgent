package com.panwar2001.orgagent.core.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import tools.jackson.databind.json.JsonMapper;

class RedisConfigTest {

	private final RedisTemplate<String, Object> template = new RedisConfig()
		.redisTemplate(mock(RedisConnectionFactory.class), JsonMapper.builder().build());

	@Test
	void keysStayHumanReadableForRedisCli() {
		assertThat(template.getKeySerializer()).isInstanceOf(StringRedisSerializer.class);
		assertThat(template.getHashKeySerializer()).isInstanceOf(StringRedisSerializer.class);

		RedisSerializer<String> keySerializer = new StringRedisSerializer();
		byte[] key = keySerializer.serialize("orgagent:org:acme:project:prj-1:chat:conv-9:window");

		assertThat(new String(key, StandardCharsets.UTF_8))
			.isEqualTo("orgagent:org:acme:project:prj-1:chat:conv-9:window");
	}

	@Test
	void valuesAreStoredAsJson() {
		assertThat(template.getValueSerializer()).isInstanceOf(GenericJacksonJsonRedisSerializer.class);
		assertThat(template.getHashValueSerializer()).isInstanceOf(GenericJacksonJsonRedisSerializer.class);
	}

	@Test
	void roundTripsAValueThroughTheConfiguredSerializer() {
		RedisSerializer<Object> serializer = valueSerializer();
		Map<String, Object> value = new LinkedHashMap<>();
		value.put("role", "USER");
		value.put("content", "what is our refund policy?");

		byte[] encoded = serializer.serialize(value);
		assertThat(new String(encoded, StandardCharsets.UTF_8)).contains("what is our refund policy?");

		assertThat(serializer.deserialize(encoded)).isEqualTo(value);
	}

	@SuppressWarnings("unchecked")
	private RedisSerializer<Object> valueSerializer() {
		return (RedisSerializer<Object>) template.getValueSerializer();
	}

}

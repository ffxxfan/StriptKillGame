package com.example.striptkillgamedemo2.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.bson.types.ObjectId;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Jackson 序列化配置。
 * <p>
 * 注册以下定制：
 * <ul>
 *     <li>{@link JavaTimeModule}：确保 {@code LocalDateTime} 等 JSR-310 时间类型可序列化。</li>
 *     <li>{@link ObjectId} 序列化/反序列化：写入 JSON 时使用十六进制字符串，避免将 MongoDB
 *     ObjectId 暴露为复杂对象。</li>
 *     <li>关闭 {@code WRITE_DATES_AS_TIMESTAMPS}：使 {@code LocalDateTime} 以 ISO-8601 字符串输出。</li>
 * </ul>
 * </p>
 */
@Configuration
public class JacksonConfig {

    /**
     * 提供 Jackson 构造器定制，注入 ObjectId 与时间类型的序列化规则。
     *
     * @return Jackson2ObjectMapperBuilder 的定制回调
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer objectIdJacksonCustomizer() {
        return builder -> {
            // 显式注册 JavaTimeModule，保证 LocalDateTime 序列化正确
            builder.modulesToInstall(new JavaTimeModule());

            SimpleModule objectIdModule = new SimpleModule("ObjectIdModule");
            objectIdModule.addSerializer(ObjectId.class, new JsonSerializer<>() {
                @Override
                public void serialize(ObjectId value, JsonGenerator gen, SerializerProvider s) throws IOException {
                    gen.writeString(value.toHexString());
                }
            });
            objectIdModule.addDeserializer(ObjectId.class, new JsonDeserializer<>() {
                @Override
                public ObjectId deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
                    String text = p.getText();
                    if (text == null || text.isBlank()) return null;
                    return new ObjectId(text);
                }
            });
            builder.modulesToInstall(objectIdModule);

            // 将 LocalDateTime 输出为 ISO-8601 字符串而非时间戳数组
            builder.featuresToDisable(
                    com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
            );
        };
    }
}

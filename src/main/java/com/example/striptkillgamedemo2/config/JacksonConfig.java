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

@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer objectIdJacksonCustomizer() {
        return builder -> {
            // Explicitly register JavaTimeModule to ensure LocalDateTime serializes correctly
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

            // Write LocalDateTime as ISO-8601 string, not array
            builder.featuresToDisable(
                    com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
            );
        };
    }
}

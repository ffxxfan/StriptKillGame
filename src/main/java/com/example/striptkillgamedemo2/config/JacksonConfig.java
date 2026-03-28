package com.example.striptkillgamedemo2.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
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
            SimpleModule module = new SimpleModule("ObjectIdModule");
            module.addSerializer(ObjectId.class, new JsonSerializer<>() {
                @Override
                public void serialize(ObjectId value, JsonGenerator gen, SerializerProvider s) throws IOException {
                    gen.writeString(value.toHexString());
                }
            });
            module.addDeserializer(ObjectId.class, new JsonDeserializer<>() {
                @Override
                public ObjectId deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
                    return new ObjectId(p.getText());
                }
            });
            builder.modules(module);
        };
    }
}

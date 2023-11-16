package com.txtech.mds.server.component;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.*;
import com.txtech.mds.msg.type.MsgBaseMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.*;

@Component
public class JsonSchemaGenerator {
    private static final Set<String> IGNORED_FIELDS = new HashSet<String>() {{
        add("mvKey");
    }};
    private final SchemaVersion schemaVersion = SchemaVersion.DRAFT_2020_12;
    private final SchemaGenerator schemaGenerator;

    @Autowired
    public JsonSchemaGenerator(ObjectMapperFactory objectMapperFactory) {
        SchemaGeneratorConfigBuilder builder = new SchemaGeneratorConfigBuilder(schemaVersion, OptionPreset.PLAIN_JSON)
                .withObjectMapper(objectMapperFactory.get())
                .with(Option.ADDITIONAL_FIXED_TYPES)
                .with(Option.EXTRA_OPEN_API_FORMAT_VALUES)
                .with(Option.FLATTENED_OPTIONALS)
                .with(Option.ENUM_KEYWORD_FOR_SINGLE_VALUES)
                .with(Option.DEFINITIONS_FOR_ALL_OBJECTS);
        builder.forTypesInGeneral()
                .withCustomDefinitionProvider((type, ctx) -> {
                    if (type.isInstanceOf(Map.class)) {
                        return new CustomDefinition(
                                ctx.createDefinitionReference(
                                        ctx.getTypeContext().resolve(List.class,
                                                ctx.getTypeContext().resolve(AbstractMap.SimpleEntry.class, type.getTypeParameters().toArray(new ResolvedType[] {})))));
                    } else {
                        return null;
                    }
                });
        builder.forFields().withIgnoreCheck(field -> {
            Field f = field.getRawMember();
            // Do not include final or static fields in the schema
            if (Modifier.isFinal(f.getModifiers()) || Modifier.isStatic(f.getModifiers())) {
                return true;
            }
            Class<? extends MsgBaseMessage> cl = (Class<? extends MsgBaseMessage>) field.getDeclaringType().getErasedType();
            if (MsgBaseMessage.class.isAssignableFrom(cl)) {
                // Ignore abstract MsgBaseMessage class from schema as this should be not be set manually, but instead automatically generated
                if (Modifier.isAbstract(cl.getModifiers())) {
                    return true;
                }

                // Ignore certain well-known field from schema as this should be set by side effects
                if (IGNORED_FIELDS.contains(f.getName())) {
                    return true;
                }
            }

            return false;
        });
        this.schemaGenerator = new SchemaGenerator(builder.build());
    }

    public JsonNode getSchema(Class<?> clazz) {
        return schemaGenerator.generateSchema(clazz);
    }

    public JsonNode getSchema(Type mainType, Type... parametricType) {
        return schemaGenerator.generateSchema(mainType, parametricType);
    }

    public String toString(SchemaKeyword keyword) {
        return keyword.forVersion(schemaVersion);
    }
}

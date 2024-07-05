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
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class JsonSchemaGenerator {
    private static final Set<String> IGNORED_FIELDS = new HashSet<String>() {{
        // This field will be set automatically by side effects from executing setKeys() method, so this field does not
        // need to be set manually
        add("mvKey");
        // This field is a list of byte array, since protobuf doesn't support multidimensional arrays
        // generate a proto type that support this type is problematic, a suggestion is to update this field to use
        // a wrapped type around the primitive array, for now this field will be ignored
        add("mvNewsLines");
    }};
    private final SchemaVersion schemaVersion = SchemaVersion.DRAFT_2020_12;
    private final Map<String, SchemaKeyword> toSchemaKeywords;
    private final Map<SchemaKeyword, String> fromSchemaKeywords;
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
            // Do not include static fields in the schema
            if (Modifier.isStatic(f.getModifiers())) {
                return true;
            }
            Class<? extends MsgBaseMessage> cl = (Class<? extends MsgBaseMessage>) field.getDeclaringType().getErasedType();
            if (ObjectMapper.class.isAssignableFrom(cl)) {
                // Do not generate json schema for this type
                return true;
            }
            if (MsgBaseMessage.class.isAssignableFrom(cl)) {
                // Ignore MsgBaseMessage class from schema as this should be not be set manually, but instead automatically generated
                if (MsgBaseMessage.class.equals(cl)) {
                    return true;
                }

                // Ignore certain well-known field from schema as this should be set by side effects, or field that needed to be updated
                // to a different type that is easier to works with
                return IGNORED_FIELDS.contains(f.getName());
            } else {
                return false;
            }
        });
        this.fromSchemaKeywords = Stream.of(SchemaKeyword.values()).collect(Collectors.toMap(
                k -> k,
                k -> k.forVersion(schemaVersion)));
        this.toSchemaKeywords = fromSchemaKeywords.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getValue,
                Map.Entry::getKey));
        this.schemaGenerator = new SchemaGenerator(builder.build());
    }

    public JsonNode getSchema(Class<?> clazz) {
        return schemaGenerator.generateSchema(clazz);
    }

    public JsonNode getSchema(Type mainType, Type... parametricType) {
        return schemaGenerator.generateSchema(mainType, parametricType);
    }

    public String toString(SchemaKeyword keyword) {
        return fromSchemaKeywords.get(keyword);
    }

    public SchemaKeyword fromString(String keyword) {
        return toSchemaKeywords.get(keyword);
    }
}

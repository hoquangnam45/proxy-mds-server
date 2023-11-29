package com.txtech.mds.server.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.SchemaKeyword;
import com.google.protobuf.*;
import com.txtech.mds.server.pojo.GrpcService;
import com.txtech.mds.server.util.StreamUtils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class ProtoSchemaGenerator {
    private final JsonSchemaGenerator jsonSchemaGenerator;

    public ProtoSchemaGenerator(JsonSchemaGenerator jsonSchemaGenerator) {
        this.jsonSchemaGenerator = jsonSchemaGenerator;
    }

    public String normalizeProtoTypeName(String name) {
        return name.replaceAll("[(),]", "");
    }

    public String generateProtoService(GrpcService service) {
        return MessageFormat.format(
                        "service {0} '{'\n{1}\n'}'",
                        service.getServiceName(),
                        service.getMethods().stream()
                                .map(m -> MessageFormat.format("    rpc {0}({1}) returns ({2});", m.getMethodName(), m.getInputType(), m.getOutputType()))
                                .collect(Collectors.joining("\n")));
    }

    public Map<String, String> generateTypeSchema(Map<String, ObjectNode> definitions) throws InvalidProtocolBufferException {
        Map<String, DefinitionType> definitionTypeMap = definitions.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> mapToDefinitionType(entry.getValue())));

        return definitionTypeMap.entrySet().stream()
                        .map(entry -> {
                            if (entry.getValue().isEnum()) {
                                int[] idx = new int[]{0};
                                // Why wrapped enum inside message type
                                // Protobuf doesn't allow same name enum value to be defined even if that come from 2
                                // different enum also if the enum name happened to be the same as any type in proto
                                // file (message type for example) collision will also happen
                                return new AbstractMap.SimpleEntry<>(entry.getKey(), MessageFormat.format(
                                        "message {0} '{'\n    enum {0} '{'\n{1}\n    '}'\n'}'",
                                        normalizeProtoTypeName(entry.getKey()),
                                        String.join(
                                                "\n",
                                                // Why need an unset value for enum, because starting enum value must be
                                                // 0, but in protobuf zero index enum carries the semantic that this enum value is unknown, unfortunately
                                                // if we print proto message that have field using first enum value, it will be ignored/skipped
                                                // one way to work around this is to create an explicit unknown enum value at 0 index
                                                // so that our actual first enum value start at 1 to make sure that when print
                                                // to json it will not ignore the enum value
                                                "        UNSET = 0;",
                                                StreamUtils.streamArrayNode((ArrayNode) definitions.get(entry.getKey()).get(jsonSchemaGenerator.toString(SchemaKeyword.TAG_ENUM)))
                                                        .map(JsonNode::asText)
                                                        .map(v -> MessageFormat.format("        {0} = {1};", v, ++idx[0]))
                                                        .collect(Collectors.joining("\n")))));
                            } else if (entry.getValue().isObject()) {
                                int[] idx = new int[] {0};
                                return new AbstractMap.SimpleEntry<>(entry.getKey(), MessageFormat.format(
                                        "message {0} '{'\n{1}\n'}'",
                                        normalizeProtoTypeName(entry.getKey()),
                                        StreamUtils.streamObjectNode((ObjectNode) definitions.get(entry.getKey()).get(jsonSchemaGenerator.toString(SchemaKeyword.TAG_PROPERTIES)))
                                                .map(prop -> {
                                                    String propName = prop.getKey();
                                                    ObjectNode propDefinition = (ObjectNode) prop.getValue();
                                                    return MessageFormat.format("    {0} {1} = {2};",
                                                            mapToProtoTypeFieldType(mapToDefinitionType(propDefinition), propDefinition, definitionTypeMap, definitions),
                                                            propName,
                                                            ++idx[0]);
                                                })
                                                .collect(Collectors.joining("\n"))));
                            } else if (entry.getValue().isArray()) {
                                return null;
                            } else {
                                throw new IllegalStateException("Not possible to enter here");
                            }
                        }).filter(Objects::nonNull)
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue,
                                (prev, next) -> {
                                    throw new IllegalStateException("Should not have duplicated type name");
                                },
                                LinkedHashMap::new));
    }

    private String mapToProtoTypeFieldType(DefinitionType definitionType, ObjectNode definitionNode, Map<String, DefinitionType> definitionTypeMap, Map<String, ObjectNode> definitions) {
        if (definitionType.isAny()) {
            return "google.protobuf.Any";
        } else if (definitionType.isPrimitiveType()) {
            return definitionType.getPrimitiveType();
        } else if (definitionType.isReferredType()) {
            DefinitionType referredType = definitionTypeMap.get(definitionType.getReferredType());
            if (referredType.isArray()) {
                return mapToProtoTypeFieldType(referredType, definitions.get(definitionType.getReferredType()), definitionTypeMap, definitions);
            } else if (referredType.isEnum()) {
                String normalizedEnumTypeName = normalizeProtoTypeName(definitionType.getReferredType());
                // Why is this so weird for enum types
                // Because enum have to has unique name, 1 workaround is to wrap enum inside a message type with
                // the same name as enum type so enum type can be scoped properly
                return normalizedEnumTypeName + "." + normalizedEnumTypeName;
            } else if (referredType.isObject()) {
                return normalizeProtoTypeName(definitionType.getReferredType());
            } else if (referredType.isReferredType()) {
                throw new UnsupportedOperationException("Support maximum 1 level of reference type chain, recheck your json schema");
            } else {
                throw new UnsupportedOperationException("Do not support alias other primitive type");
            }
        } else if (definitionType.isArray()) {
            DefinitionType itemType = Optional.ofNullable(definitionNode.get(jsonSchemaGenerator.toString(SchemaKeyword.TAG_ITEMS)))
                    .map(node -> node.get(jsonSchemaGenerator.toString(SchemaKeyword.TAG_REF)))
                    .map(JsonNode::asText)
                    .map(this::getRefTypeName)
                    .map(definitionTypeMap::get)
                    .orElse(null);
            boolean isRefer = false;

            if (itemType == null) {
                // This could be due to item type is inline or just a primitive type
                itemType = mapToDefinitionType((ObjectNode) definitionNode.get(jsonSchemaGenerator.toString(SchemaKeyword.TAG_ITEMS)));
            } else {
                isRefer = true;
            }

            if (itemType.isPrimitiveType()) {
                if (isRefer) {
                    throw new UnsupportedOperationException("Do not support alias other primitive type");
                }
                return MessageFormat.format("repeated {0}", itemType.getPrimitiveType());
            } else if (itemType.isReferredType()) {
                throw new UnsupportedOperationException("Support maximum 1 level of reference type chain, recheck your json schema");
            } else if (itemType.isObject() || itemType.isEnum()) {
                if (isRefer) {
                    return MessageFormat.format("repeated {0}", normalizeProtoTypeName(Optional.of(definitionNode.get(jsonSchemaGenerator.toString(SchemaKeyword.TAG_ITEMS)))
                            .map(node -> node.get(jsonSchemaGenerator.toString(SchemaKeyword.TAG_REF)).asText())
                            .map(this::getRefTypeName)
                            .orElseThrow(() -> new IllegalStateException("Not possible to enter here"))));
                } else {
                    throw new UnsupportedOperationException("Do not support inline nested complex type");
                }
            } else {
                throw new UnsupportedOperationException("Do not support multidimensional array type");
            }
        } else if (definitionType.isObject()) {
            throw new UnsupportedOperationException("Do not support inline object type, create a separate type and use refs in json schema instead");
        } else if (definitionType.isUnknown()) {
            throw new UnsupportedOperationException("Unknown definition type");
        } else {
            throw new UnsupportedOperationException("Do not support this definition type yet");
        }
    }

    private String getRefTypeName(String refTypeUsage) {
        return refTypeUsage.substring(refTypeUsage.lastIndexOf("/") + 1);
    }

    private DefinitionType mapToDefinitionType(ObjectNode definition) {
        String referredType = Optional.ofNullable(definition.get(jsonSchemaGenerator.toString(SchemaKeyword.TAG_REF)))
                .map(JsonNode::asText)
                .map(this::getRefTypeName)
                .orElse(null);
        if (referredType != null) {
            return new DefinitionType(false, false, false, false, null, true, referredType, false, false);
        }
        if (definition.get(jsonSchemaGenerator.toString(SchemaKeyword.TAG_TYPE)) == null) {
            // This is due to generic type where the inner type is Object
            return new DefinitionType(false, false, false, false, null, false, null, false, true);
        }
        boolean isString = definition.get(jsonSchemaGenerator.toString(SchemaKeyword.TAG_TYPE)).asText().equals(jsonSchemaGenerator.toString(SchemaKeyword.TAG_TYPE_STRING));
        boolean isObject = !isString && definition.get(jsonSchemaGenerator.toString(SchemaKeyword.TAG_TYPE)).asText().equals(jsonSchemaGenerator.toString(SchemaKeyword.TAG_TYPE_OBJECT));
        boolean isArray = !isObject && !isString && definition.get(jsonSchemaGenerator.toString(SchemaKeyword.TAG_TYPE)).asText().equals(jsonSchemaGenerator.toString(SchemaKeyword.TAG_TYPE_ARRAY));
        boolean isEnum = isString && definition.get(jsonSchemaGenerator.toString(SchemaKeyword.TAG_ENUM)) != null;
        boolean isNumber = !isString && !isObject && !isArray && definition.get(jsonSchemaGenerator.toString(SchemaKeyword.TAG_TYPE)).asText().equals(jsonSchemaGenerator.toString(SchemaKeyword.TAG_TYPE_NUMBER));
        boolean isInteger = !isString && !isObject && !isArray && !isNumber && definition.get(jsonSchemaGenerator.toString(SchemaKeyword.TAG_TYPE)).asText().equals(jsonSchemaGenerator.toString(SchemaKeyword.TAG_TYPE_INTEGER));
        boolean isBoolean = !isString && !isObject && !isArray && !isNumber && !isInteger && definition.get(jsonSchemaGenerator.toString(SchemaKeyword.TAG_TYPE)).asText().equals(jsonSchemaGenerator.toString(SchemaKeyword.TAG_TYPE_BOOLEAN));
        if (isEnum) {
            return new DefinitionType(false, true, false, false, null, false, null, false, false);
        } else if (isArray) {
            return new DefinitionType(true, false, false, false, null, false, null, false, false);
        } else if (isObject) {
            return new DefinitionType(false, false, true, false, null, false, null, false, false);
        } else if (isBoolean) {
            return new DefinitionType(false, false, false, true, "bool", false, null, false, false);
        } else if (isNumber) {
            return new DefinitionType(false, false, false, true, "double", false, null, false, false);
        } else if (isInteger) {
            return new DefinitionType(false, false, false, true, "int64", false, null, false, false);
        } else if (isString) {
            return new DefinitionType(false, false, false, true, "string", false, null, false, false);
        } else {
            return new DefinitionType(false, false, false, false, null, false, null, true, false);
        }
    }

    @RequiredArgsConstructor
    @Getter
    private static class DefinitionType {
        private final boolean isArray;
        private final boolean isEnum;
        private final boolean isObject;
        private final boolean isPrimitiveType;
        private final String primitiveType;
        private final boolean isReferredType;
        private final String referredType;
        private final boolean isUnknown;
        private final boolean isAny;
    }

    // NOTE: For testing purpose only
    public static void main(String[] args) {
        ObjectMapper objectMapper = new ObjectMapperFactory().get();
        JsonSchemaGenerator jsonSchemaGenerator = new JsonSchemaGenerator(new ObjectMapperFactory());
        ProtoSchemaGenerator protoSchemaGenerator = new ProtoSchemaGenerator(jsonSchemaGenerator);
        List<String> outputProtoSchemas = Stream.of("{\n" +
                "                \"DerivativeInfo\": {\n" +
                "                    \"type\": \"object\"\n" +
                "                },\n" +
                "                \"Entry\": {\n" +
                "                    \"type\": \"object\",\n" +
                "                    \"properties\": {\n" +
                "                        \"mvOrders\": {\n" +
                "                            \"type\": \"integer\",\n" +
                "                            \"format\": \"int64\"\n" +
                "                        },\n" +
                "                        \"mvPrice\": {\n" +
                "                            \"type\": \"string\"\n" +
                "                        },\n" +
                "                        \"mvQty\": {\n" +
                "                            \"type\": \"string\"\n" +
                "                        }\n" +
                "                    }\n" +
                "                },\n" +
                "                \"ICAS\": {\n" +
                "                    \"type\": \"object\"\n" +
                "                },\n" +
                "                \"IPOS\": {\n" +
                "                    \"type\": \"object\"\n" +
                "                },\n" +
                "                \"IVCM\": {\n" +
                "                    \"type\": \"object\"\n" +
                "                },\n" +
                "                \"MdsMsgHashMap\": {\n" +
                "                    \"type\": \"array\",\n" +
                "                    \"items\": {\n" +
                "                        \"$ref\": \"#/$defs/SimpleEntry\"\n" +
                "                    }\n" +
                "                },\n" +
                "                \"MsgBaseMessageAttributes\": {\n" +
                "                    \"type\": \"object\",\n" +
                "                    \"properties\": {\n" +
                "                        \"broadcast\": {\n" +
                "                            \"type\": \"boolean\"\n" +
                "                        },\n" +
                "                        \"first\": {\n" +
                "                            \"type\": \"boolean\"\n" +
                "                        },\n" +
                "                        \"last\": {\n" +
                "                            \"type\": \"boolean\"\n" +
                "                        },\n" +
                "                        \"multicast\": {\n" +
                "                            \"type\": \"boolean\"\n" +
                "                        },\n" +
                "                        \"snapshotExchangeID\": {\n" +
                "                            \"$ref\": \"#/$defs/MsgExchangeID\"\n" +
                "                        },\n" +
                "                        \"snapshotKey\": {\n" +
                "                            \"type\": \"string\"\n" +
                "                        },\n" +
                "                        \"snapshotType\": {\n" +
                "                            \"$ref\": \"#/$defs/MsgMessageType\"\n" +
                "                        },\n" +
                "                        \"type\": {\n" +
                "                            \"$ref\": \"#/$defs/Type\"\n" +
                "                        }\n" +
                "                    }\n" +
                "                },\n" +
                "                \"MsgExchangeID\": {\n" +
                "                    \"type\": \"string\",\n" +
                "                    \"enum\": [\n" +
                "                        \"Internal\",\n" +
                "                        \"HKS\",\n" +
                "                        \"HK\",\n" +
                "                        \"HM\",\n" +
                "                        \"HN\",\n" +
                "                        \"SH\",\n" +
                "                        \"SZ\",\n" +
                "                        \"Unknown\",\n" +
                "                        \"XNYS\",\n" +
                "                        \"XNAS\",\n" +
                "                        \"XHNX\",\n" +
                "                        \"PSHK\",\n" +
                "                        \"OSL\",\n" +
                "                        \"FUND\",\n" +
                "                        \"CBOT\",\n" +
                "                        \"COMEX\",\n" +
                "                        \"NYMEX\",\n" +
                "                        \"CME\",\n" +
                "                        \"BMD\",\n" +
                "                        \"SGX\",\n" +
                "                        \"NASDAQ\",\n" +
                "                        \"NYSE\"\n" +
                "                    ]\n" +
                "                },\n" +
                "                \"MsgMdsDetailPriceInfo\": {\n" +
                "                    \"type\": \"object\",\n" +
                "                    \"properties\": {\n" +
                "                        \"mvAsks\": {\n" +
                "                            \"type\": \"array\",\n" +
                "                            \"items\": {\n" +
                "                                \"$ref\": \"#/$defs/Entry\"\n" +
                "                            }\n" +
                "                        },\n" +
                "                        \"mvBids\": {\n" +
                "                            \"type\": \"array\",\n" +
                "                            \"items\": {\n" +
                "                                \"$ref\": \"#/$defs/Entry\"\n" +
                "                            }\n" +
                "                        },\n" +
                "                        \"mvCAS\": {\n" +
                "                            \"$ref\": \"#/$defs/ICAS\"\n" +
                "                        },\n" +
                "                        \"mvDerivativeInfo\": {\n" +
                "                            \"$ref\": \"#/$defs/DerivativeInfo\"\n" +
                "                        },\n" +
                "                        \"mvMap\": {\n" +
                "                            \"$ref\": \"#/$defs/MdsMsgHashMap\"\n" +
                "                        },\n" +
                "                        \"mvPOS\": {\n" +
                "                            \"$ref\": \"#/$defs/IPOS\"\n" +
                "                        },\n" +
                "                        \"mvRealTime\": {\n" +
                "                            \"type\": \"boolean\"\n" +
                "                        },\n" +
                "                        \"mvRelatives\": {\n" +
                "                            \"$ref\": \"#/$defs/Relatives\"\n" +
                "                        },\n" +
                "                        \"mvUpdateByDefinition\": {\n" +
                "                            \"type\": \"boolean\"\n" +
                "                        },\n" +
                "                        \"mvUpdateByDerivative\": {\n" +
                "                            \"type\": \"boolean\"\n" +
                "                        },\n" +
                "                        \"mvUpdateByOrderBook\": {\n" +
                "                            \"type\": \"boolean\"\n" +
                "                        },\n" +
                "                        \"mvUpdateByStats\": {\n" +
                "                            \"type\": \"boolean\"\n" +
                "                        },\n" +
                "                        \"mvUpdateByTradingStatus\": {\n" +
                "                            \"type\": \"boolean\"\n" +
                "                        },\n" +
                "                        \"mvUpdatedByNominal\": {\n" +
                "                            \"type\": \"boolean\"\n" +
                "                        },\n" +
                "                        \"mvVCM\": {\n" +
                "                            \"$ref\": \"#/$defs/IVCM\"\n" +
                "                        }\n" +
                "                    }\n" +
                "                },\n" +
                "                \"MsgMessageType\": {\n" +
                "                    \"type\": \"string\",\n" +
                "                    \"enum\": [\n" +
                "                        \"HEARTBEAT\",\n" +
                "                        \"INTERNAL\",\n" +
                "                        \"CLIENT_SUBSCRIPTION\",\n" +
                "                        \"CLIENT_REQUEST\",\n" +
                "                        \"MDS_RESPONSE\",\n" +
                "                        \"MDS_CONNECTION_STATUS\",\n" +
                "                        \"MARKET_DEFINITION\",\n" +
                "                        \"SECURITY_DEFINITION\",\n" +
                "                        \"LIQUIDITY_PROVIDER\",\n" +
                "                        \"CURRENCY_RATE\",\n" +
                "                        \"TRADING_SESSION_STATUS\",\n" +
                "                        \"SECURITY_STATUS\",\n" +
                "                        \"ADD_ORDER\",\n" +
                "                        \"MODIFY_ORDER\",\n" +
                "                        \"DELETE_ORDER\",\n" +
                "                        \"ADD_ODD_LOT_ORDER\",\n" +
                "                        \"DELETE_ODD_LOT_ORDER\",\n" +
                "                        \"AGGREGATE_ORDER_BOOK_UPDATE\",\n" +
                "                        \"BROKER_QUEUE\",\n" +
                "                        \"TRADE\",\n" +
                "                        \"TRADE_CANCEL\",\n" +
                "                        \"TRADE_TICKER\",\n" +
                "                        \"CLOSING_PRICE\",\n" +
                "                        \"NOMINAL_PRICE\",\n" +
                "                        \"EQUILIBRIUM_PRICE\",\n" +
                "                        \"STATISTICS\",\n" +
                "                        \"MARKET_TURNOVER\",\n" +
                "                        \"YIELD\",\n" +
                "                        \"NEWS\",\n" +
                "                        \"INDEX_DEFINITION\",\n" +
                "                        \"INDEX_DATA\",\n" +
                "                        \"AGGREGATE_ORDER_BOOK\",\n" +
                "                        \"FULL_ORDER_BOOK\",\n" +
                "                        \"BEST_BID_ASK\",\n" +
                "                        \"ODD_LOT_ORDER_BOOK\",\n" +
                "                        \"QUOTE_REQUEST_INFORMATION\",\n" +
                "                        \"SECURITY_FINANCIAL_DATA\",\n" +
                "                        \"SECURITY_DYNAMIC\",\n" +
                "                        \"ORDER_IMBALANCE\",\n" +
                "                        \"REFERENCE_PRICE\",\n" +
                "                        \"REFERENCE_PRICE_INTERNAL\",\n" +
                "                        \"VCM_TRIGGER\",\n" +
                "                        \"SECURITY_DEFINITION_LITE\",\n" +
                "                        \"ESTIMATED_AVERAGE_SETTLEMENT_PRICE\",\n" +
                "                        \"IMPLIED_VOLATILITY\",\n" +
                "                        \"OPEN_INTEREST\",\n" +
                "                        \"TEXT\",\n" +
                "                        \"TRADE_AMENDMENT\",\n" +
                "                        \"COMMODITY_STATUS\",\n" +
                "                        \"TOP_RANK\",\n" +
                "                        \"HISTORICAL_DATA\",\n" +
                "                        \"SECURITY_FINANCIAL_REPORT\",\n" +
                "                        \"STOCK_CONNECT_DAILY_QUOTA_BALANCE\",\n" +
                "                        \"STOCK_CONNECT_MARKET_TURNOVER\",\n" +
                "                        \"BOARD_INFO\",\n" +
                "                        \"BASKET_INDEX_DATA\",\n" +
                "                        \"FOREIGN_TRADE\",\n" +
                "                        \"BREAK_RECORD\",\n" +
                "                        \"PRICE_ALERT\",\n" +
                "                        \"MARKET_SENTIMENT\",\n" +
                "                        \"SIMPLE_PRICE_INFO\",\n" +
                "                        \"DETAIL_PRICE_INFO\",\n" +
                "                        \"GREEK_OPTIONS\",\n" +
                "                        \"SECURITY_COMPANY_INFO\",\n" +
                "                        \"SECURITY_SHARE_CHANGE\",\n" +
                "                        \"SECURITY_DIVIDEND_RECORD\",\n" +
                "                        \"MARKET_TREND\",\n" +
                "                        \"WARRANT_BULLBEAR\",\n" +
                "                        \"PRODUCT_LIST\",\n" +
                "                        \"TRADE_DISTRIBUTION\",\n" +
                "                        \"MONEY_FLOW\",\n" +
                "                        \"SHARE_HOLDING\",\n" +
                "                        \"DETAIL_INDUSTRY_RANK\",\n" +
                "                        \"SIMPLE_INDUSTRY_RANK\",\n" +
                "                        \"SIMPLE_PRICE_DELAY\",\n" +
                "                        \"DETAIL_PRICE_DELAY\",\n" +
                "                        \"MDS_LOGIN_RESPONSE\",\n" +
                "                        \"DETAIL_PRICE_LV2\",\n" +
                "                        \"ADVERTISEMENT\",\n" +
                "                        \"INDEX_STATISTICS\",\n" +
                "                        \"TRADE_LOG\",\n" +
                "                        \"ETF_STATISTICS\",\n" +
                "                        \"SECURITY_EXECUTIVE\",\n" +
                "                        \"SECURITY_DOCUMENT\"\n" +
                "                    ]\n" +
                "                },\n" +
                "                \"Relatives\": {\n" +
                "                    \"type\": \"object\"\n" +
                "                },\n" +
                "                \"SimpleEntry\": {\n" +
                "                    \"type\": \"object\"\n" +
                "                },\n" +
                "                \"Type\": {\n" +
                "                    \"type\": \"string\",\n" +
                "                    \"enum\": [\n" +
                "                        \"Update\",\n" +
                "                        \"Snapshot\"\n" +
                "                    ]\n" +
                "                }\n" +
                "            }")
                .map(jsonStr -> {
                    try {
                        return objectMapper.readTree(jsonStr);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                })
                .map(jsonNode -> (ObjectNode) jsonNode)
                .map(jsonNode -> StreamUtils.streamObjectNode(jsonNode).collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> (ObjectNode) entry.getValue())))
                .map(definitions -> {
                    try {
                        return String.join("\n\n", protoSchemaGenerator.generateTypeSchema(definitions).values());
                    } catch (InvalidProtocolBufferException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toList());

        outputProtoSchemas.forEach(System.out::println);
    }
}

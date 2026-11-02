package io.github.treetrail.openapi.overlay.jackson3;

import java.util.Map;

/**
 * Converts between Jackson 2 and Jackson 3 trees node by node, without text in between: each number keeps its type
 * and value ({@code BigDecimal} with its scale), objects keep the order of their members.
 */
final class Trees {

    private static final com.fasterxml.jackson.databind.node.JsonNodeFactory JACKSON2 =
            com.fasterxml.jackson.databind.node.JsonNodeFactory.instance;
    private static final tools.jackson.databind.node.JsonNodeFactory JACKSON3 =
            tools.jackson.databind.node.JsonNodeFactory.instance;

    private Trees() {}

    static com.fasterxml.jackson.databind.JsonNode toJackson2(tools.jackson.databind.JsonNode node) {
        return switch (node.getNodeType()) {
            case OBJECT -> {
                com.fasterxml.jackson.databind.node.ObjectNode object = JACKSON2.objectNode();
                for (Map.Entry<String, tools.jackson.databind.JsonNode> member : node.properties()) {
                    object.set(member.getKey(), toJackson2(member.getValue()));
                }
                yield object;
            }
            case ARRAY -> {
                com.fasterxml.jackson.databind.node.ArrayNode array = JACKSON2.arrayNode(node.size());
                for (tools.jackson.databind.JsonNode element : node) {
                    array.add(toJackson2(element));
                }
                yield array;
            }
            case STRING -> com.fasterxml.jackson.databind.node.TextNode.valueOf(node.stringValue());
            case BOOLEAN -> com.fasterxml.jackson.databind.node.BooleanNode.valueOf(node.booleanValue());
            case NULL -> com.fasterxml.jackson.databind.node.NullNode.getInstance();
            case NUMBER -> numberToJackson2(node);
            default -> throw new IllegalArgumentException("Not a JSON value: " + node.getNodeType());
        };
    }

    private static com.fasterxml.jackson.databind.JsonNode numberToJackson2(tools.jackson.databind.JsonNode number) {
        if (number.isShort()) {
            return com.fasterxml.jackson.databind.node.ShortNode.valueOf(number.shortValue());
        }
        if (number.isInt()) {
            return com.fasterxml.jackson.databind.node.IntNode.valueOf(number.intValue());
        }
        if (number.isLong()) {
            return com.fasterxml.jackson.databind.node.LongNode.valueOf(number.longValue());
        }
        if (number.isBigInteger()) {
            return com.fasterxml.jackson.databind.node.BigIntegerNode.valueOf(number.bigIntegerValue());
        }
        if (number.isFloat()) {
            return com.fasterxml.jackson.databind.node.FloatNode.valueOf(number.floatValue());
        }
        if (number.isDouble()) {
            return com.fasterxml.jackson.databind.node.DoubleNode.valueOf(number.doubleValue());
        }
        return com.fasterxml.jackson.databind.node.DecimalNode.valueOf(number.decimalValue());
    }

    static tools.jackson.databind.JsonNode toJackson3(com.fasterxml.jackson.databind.JsonNode node) {
        return switch (node.getNodeType()) {
            case OBJECT -> {
                tools.jackson.databind.node.ObjectNode object = JACKSON3.objectNode();
                for (Map.Entry<String, com.fasterxml.jackson.databind.JsonNode> member : node.properties()) {
                    object.set(member.getKey(), toJackson3(member.getValue()));
                }
                yield object;
            }
            case ARRAY -> {
                tools.jackson.databind.node.ArrayNode array = JACKSON3.arrayNode(node.size());
                for (com.fasterxml.jackson.databind.JsonNode element : node) {
                    array.add(toJackson3(element));
                }
                yield array;
            }
            case STRING -> tools.jackson.databind.node.StringNode.valueOf(node.textValue());
            case BOOLEAN -> tools.jackson.databind.node.BooleanNode.valueOf(node.booleanValue());
            case NULL -> tools.jackson.databind.node.NullNode.getInstance();
            case NUMBER -> numberToJackson3(node);
            default -> throw new IllegalArgumentException("Not a JSON value: " + node.getNodeType());
        };
    }

    private static tools.jackson.databind.JsonNode numberToJackson3(com.fasterxml.jackson.databind.JsonNode number) {
        if (number.isShort()) {
            return tools.jackson.databind.node.ShortNode.valueOf(number.shortValue());
        }
        if (number.isInt()) {
            return tools.jackson.databind.node.IntNode.valueOf(number.intValue());
        }
        if (number.isLong()) {
            return tools.jackson.databind.node.LongNode.valueOf(number.longValue());
        }
        if (number.isBigInteger()) {
            return tools.jackson.databind.node.BigIntegerNode.valueOf(number.bigIntegerValue());
        }
        if (number.isFloat()) {
            return tools.jackson.databind.node.FloatNode.valueOf(number.floatValue());
        }
        if (number.isDouble()) {
            return tools.jackson.databind.node.DoubleNode.valueOf(number.doubleValue());
        }
        return tools.jackson.databind.node.DecimalNode.valueOf(number.decimalValue());
    }
}

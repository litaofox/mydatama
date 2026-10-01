package com.mydatama.common.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ABAC 条件树评估引擎（MOD-IAM-003）。
 * 支持：all/any/not 组合器；eq/neq/gt/gte/lt/lte/in/dept_eq 算子。
 * left/right 为 "asset.xxx" / "user.xxx" 属性引用；in 的 right 为字面量数组。
 */
@Component
@RequiredArgsConstructor
public class AbacEngine {

    private final ObjectMapper objectMapper;

    public boolean evaluate(String conditionTreeJson, Map<String, Object> subject, Map<String, Object> resource) {
        try {
            JsonNode root = objectMapper.readTree(conditionTreeJson);
            return evalNode(root, subject, resource);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean evalNode(JsonNode node, Map<String, Object> subject, Map<String, Object> resource) {
        if (node.has("all")) {
            for (JsonNode child : node.get("all")) {
                if (!evalNode(child, subject, resource)) {
                    return false;
                }
            }
            return true;
        }
        if (node.has("any")) {
            for (JsonNode child : node.get("any")) {
                if (evalNode(child, subject, resource)) {
                    return true;
                }
            }
            return false;
        }
        if (node.has("not")) {
            return !evalNode(node.get("not"), subject, resource);
        }
        if (node.has("op")) {
            return evalOp(node, subject, resource);
        }
        return false;
    }

    private boolean evalOp(JsonNode node, Map<String, Object> subject, Map<String, Object> resource) {
        String op = node.get("op").asText();
        Object left = resolve(node.get("left"), subject, resource);
        JsonNode rightNode = node.get("right");

        switch (op) {
            case "dept_eq":
                return eq(subject.get("dept_code"), resource.get("owner_dept"));
            case "in":
                List<Object> arr = new ArrayList<>();
                rightNode.forEach(n -> arr.add(asObject(n)));
                return left != null && arr.contains(coerce(left, arr.isEmpty() ? null : arr.get(0)));
            default: {
                Object right = resolve(rightNode, subject, resource);
                if (left == null || right == null) {
                    return false;
                }
                int cmp = compare(left, right);
                return switch (op) {
                    case "eq" -> cmp == 0;
                    case "neq" -> cmp != 0;
                    case "gt" -> cmp > 0;
                    case "gte" -> cmp >= 0;
                    case "lt" -> cmp < 0;
                    case "lte" -> cmp <= 0;
                    default -> false;
                };
            }
        }
    }

    /**
     * "asset.secret_level" -> resource.get("secret_level")；"user.dept_code" -> subject.get("dept_code")；
     * 其余按字面量处理。
     */
    private Object resolve(JsonNode node, Map<String, Object> subject, Map<String, Object> resource) {
        if (node == null) {
            return null;
        }
        if (node.isTextual()) {
            String text = node.asText();
            if (text.startsWith("asset.")) {
                return resource.get(text.substring(6));
            }
            if (text.startsWith("user.")) {
                return subject.get(text.substring(5));
            }
            return text;
        }
        return asObject(node);
    }

    private Object asObject(JsonNode n) {
        if (n.isNumber()) {
            return n.decimalValue();
        }
        if (n.isBoolean()) {
            return n.booleanValue();
        }
        if (n.isTextual()) {
            return n.asText();
        }
        return n.toString();
    }

    @SuppressWarnings("unchecked")
    private Object coerce(Object value, Object target) {
        if (target instanceof Number && value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        if (target instanceof String) {
            return String.valueOf(value);
        }
        return value;
    }

    private int compare(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString()));
        }
        return String.valueOf(a).compareTo(String.valueOf(b));
    }

    private boolean eq(Object a, Object b) {
        if (a == null || b == null) {
            return false;
        }
        if (a instanceof Number && b instanceof Number) {
            return compare(a, b) == 0;
        }
        return String.valueOf(a).equals(String.valueOf(b));
    }
}

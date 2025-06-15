package jLib;
import com.google.gson.*;
import java.util.*;


/**
 * JSON Schema validation using Gson
 */
public class JsonSchema {



    /**
     * Validates a JSON string against a JSON schema.
     * @param schemaString The JSON schema as a string
     * @param jsonString The JSON string to validate
     * @return A list of error messages if validation fails, or an empty list if validation succeeds
     */
    public static List<String> validateJson( String schemaString, String jsonString ) {
        try {
            JsonElement schemaElement = JsonParser.parseString(schemaString);
            JsonElement jsonElement = JsonParser.parseString(jsonString);
            
            if (!schemaElement.isJsonObject()) {
                return List.of("Schema must be a JSON object");
            }
            
            JsonObject schema = schemaElement.getAsJsonObject();
            List<String> errors = new ArrayList<>();
            validateElement(jsonElement, schema, "", errors);
            return errors;
        } catch (JsonSyntaxException e) {
            return List.of("Invalid JSON syntax: " + e.getMessage());
        } catch (Exception e) {
            return List.of("Validation error: " + e.getMessage());
        }
    }



    private static void validateElement( JsonElement element, JsonObject schema, String path, List<String> errors ) {
        if (schema.has("type")) {
            String expectedType = schema.get("type").getAsString();
            if (!isCorrectType(element, expectedType)) {
                errors.add(path + " is not of type " + expectedType);
                return;
            }
        }
        
        if (element.isJsonObject() && schema.has("properties")) {
            validateObjectProperties(element.getAsJsonObject(), schema, path, errors);
        }
        
        if (schema.has("enum")) {
            validateEnum(element, schema.getAsJsonArray("enum"), path, errors);
        }
    }



    private static boolean isCorrectType( JsonElement element, String type ) {
        switch (type) {
            case "object": return element.isJsonObject();
            case "array": return element.isJsonArray();
            case "string": return element.isJsonPrimitive() && element.getAsJsonPrimitive().isString();
            case "number": return element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber();
            case "boolean": return element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean();
            case "null": return element.isJsonNull();
            default: return false;
        }
    }



    private static void validateObjectProperties( JsonObject obj, JsonObject schema, String path, List<String> errors ) {
        if (schema.has("properties")) {
            JsonObject properties = schema.getAsJsonObject("properties");
            
            // Check each defined property
            for (Map.Entry<String, JsonElement> prop : properties.entrySet()) {
                String propName = prop.getKey();
                JsonObject propSchema = prop.getValue().getAsJsonObject();
                String propPath = path.isEmpty() ? propName : path + "." + propName;
                
                if (obj.has(propName)) {
                    validateElement(obj.get(propName), propSchema, propPath, errors);
                }
            }
        }
        
        // Check required properties
        if (schema.has("required")) {
            JsonArray required = schema.getAsJsonArray("required");
            for (JsonElement reqElement : required) {
                String reqProp = reqElement.getAsString();
                if (!obj.has(reqProp)) {
                    errors.add(path + " is missing required property: " + reqProp);
                }
            }
        }
    }



    private static void validateEnum( JsonElement element, JsonArray enumValues, String path, List<String> errors ) {
        boolean found = false;
        for (JsonElement enumVal : enumValues) {
            if (element.equals(enumVal)) {
                found = true;
                break;
            }
        }
        if (!found) {
            errors.add(path + " is not one of the allowed values");
        }
    }



    // Test methods
    @SuppressWarnings("unused")
    private static boolean validateJson_TEST_( boolean findLineNumber ) {
        if (findLineNumber) throw new RuntimeException();
        
        String schemaJson = """
            {
                "type": "object",
                "properties": {
                    "answer": {
                        "type": "string",
                        "enum": ["yes", "no"]
                    }
                },
                "required": ["answer"]
            }
        """;
        
        // Test with valid input
        String validJson = "{\"answer\":\"yes\"}";
        List<String> errors = validateJson(schemaJson, validJson);
        Lib.asrt(errors.isEmpty(), "Valid JSON should have no errors");
        
        // Test with invalid enum value
        String invalidEnum = "{\"answer\":\"maybe\"}";
        errors = validateJson(schemaJson, invalidEnum);
        Lib.asrt(!errors.isEmpty(), "Invalid enum value should have errors");
        
        // Test with missing required field
        String missingField = "{}";
        errors = validateJson(schemaJson, missingField);
        Lib.asrt(!errors.isEmpty(), "Missing required field should have errors");
        
        // Test with invalid JSON
        String invalidJson = "not json";
        errors = validateJson(schemaJson, invalidJson);
        Lib.asrt(!errors.isEmpty(), "Invalid JSON should have errors");
        
        return true;
    }



    @SuppressWarnings("unused")
    private static boolean complexSchema_TEST_( boolean findLineNumber ) {
        if (findLineNumber) throw new RuntimeException();
        
        String schemaJson = """
            {
                "type": "object",
                "properties": {
                    "name": { "type": "string" },
                    "age": { "type": "number" },
                    "is_student": { "type": "boolean" }
                },
                "required": ["name", "age"]
            }
        """;
        
        // Test with valid complete object
        String validJson = "{\"name\":\"John\",\"age\":25,\"is_student\":true}";
        List<String> errors = validateJson(schemaJson, validJson);
        Lib.asrt(errors.isEmpty(), "Valid JSON should have no errors");
        
        // Test with missing optional field
        String missingOptional = "{\"name\":\"John\",\"age\":25}";
        errors = validateJson(schemaJson, missingOptional);
        Lib.asrt(errors.isEmpty(), "Missing optional field should have no errors");
        
        // Test with wrong type
        String wrongType = "{\"name\":\"John\",\"age\":\"twenty-five\"}";
        errors = validateJson(schemaJson, wrongType);
        Lib.asrt(!errors.isEmpty(), "Wrong type should have errors");
        
        return true;
    }



    public static void main( String[] args ) { LibTest.testClass(); }
}
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.siebel.data.SiebelPropertySet;
import com.siebel.eai.SiebelBusinessService;
import com.siebel.eai.SiebelBusinessServiceException;

import java.util.HashMap;
import java.util.Map;

public class EAIJSONConverter extends SiebelBusinessService {
    public EAIJSONConverter() {
    }

    private String nonObjectChildFlg = "N";

    public void doInvokeMethod(String methodName, SiebelPropertySet input, SiebelPropertySet output) throws SiebelBusinessServiceException {
        if (methodName.equals("PropSetToJSON")) {
            try {
                JsonObject jsonObject = convertPropSetToJson(input);
                output.setValue(jsonObject.toString());
            } catch (Exception var6) {
                throw new SiebelBusinessServiceException("SBL-JBS-001001", "Error invoking PropSetToJSON method: " + var6.getMessage());
            }
        } else {
            if (!methodName.equals("JSONToPropSet")) {
                throw new SiebelBusinessServiceException("SBL-JBS-001003", "Error invoking " + methodName + " method: Method doesn`t exist");
            }

            try {
                SiebelPropertySet siebelPropertySet = convertJsonToPropSet(input);
                siebelPropertySet.setType("SiebelMessage");
                output.addChild(siebelPropertySet);
            } catch (Exception var5) {
                throw new SiebelBusinessServiceException("SBL-JBS-001002", "Error invoking JSONToPropSet method: " + var5.getMessage());
            }
        }

    }

    private JsonObject convertPropSetToJson(SiebelPropertySet input) throws Exception {
        SiebelPropertySet propertySet = null;

        if (input.propertyExists("NonObjectChildFlg"))
            nonObjectChildFlg = input.getProperty("NonObjectChildFlg").equals("Y") ? "Y" : "N";
        else
            nonObjectChildFlg = "N";

        for (int i = 0; i < input.getChildCount(); ++i) {
            if (input.getChild(i).getType().equals("SiebelMessage")) {
                propertySet = input.getChild(i);
                break;
            }

            if (propertySet == null && i + 1 == input.getChildCount()) {
                throw new SiebelBusinessServiceException("SBL-JBS-002001", "Some parameters are missing");
            }
        }

        return parsePropSetToJsonObject(propertySet);
    }

    private JsonObject parsePropSetToJsonObject(SiebelPropertySet input) throws Exception {
        JsonObject jsonObject = new JsonObject();

        for (String set = input.getFirstProperty(); set != ""; set = input.getNextProperty()) {
            jsonObject.addProperty(set, input.getProperty(set));
        }

        if (input.getType().startsWith("ListOf")) {
            if (input.getChildCount() > 1 || nonObjectChildFlg.equals("Y")) {
                jsonObject.add(input.getType().substring(6), parsePropSetToJsonArray(input));
            } else {
                jsonObject.add(input.getChild(0).getType(), parsePropSetToJsonObject(input.getChild(0)));
            }
        } else {
            for (int i = 0; i < input.getChildCount(); ++i) {
                if (input.getChild(i).getType().startsWith("ListOf")) {
                    if (input.getChild(i).getChildCount() > 1 || nonObjectChildFlg.equals("Y")) {
                        jsonObject.add(input.getChild(i).getType().substring(6), parsePropSetToJsonArray(input.getChild(i)));
                    } else {
                        jsonObject.add(input.getChild(i).getType().substring(6), parsePropSetToJsonObject(input.getChild(i).getChild(0)));
                    }
                } else {
                    jsonObject.add(input.getChild(i).getType(), parsePropSetToJsonObject(input.getChild(i)));
                }
            }
        }

        return jsonObject;
    }

    private JsonArray parsePropSetToJsonArray(SiebelPropertySet input) throws Exception {
        JsonArray jsonArray = new JsonArray();

        for (int i = 0; i < input.getChildCount(); ++i) {
            jsonArray.add(parsePropSetToJsonObject(input.getChild(i)));
        }

        return jsonArray;
    }

    private SiebelPropertySet convertJsonToPropSet(SiebelPropertySet input) throws Exception {
        JsonNode jsonNode = null;
        if (!input.getValue().equals("") && input.getValue() != null) {
            jsonNode = (new ObjectMapper()).readTree(input.getValue());
            return parseJsonObjectToPropSet(jsonNode, "", "");
        } else {
            throw new SiebelBusinessServiceException("SBL-JBS-002001", "Some parameters are missing");
        }
    }

    private SiebelPropertySet parseJsonObjectToPropSet(JsonNode jsonNode, String jsonNodeName, String propertySetName) throws Exception {
        SiebelPropertySet siebelPropertySet = new SiebelPropertySet();
        Map<String, String> valuePropertyMap = new HashMap();
        Map<String, JsonNode> objectArrayMap = new HashMap();
        if (!jsonNodeName.startsWith("ListOf") && !propertySetName.startsWith("ListOf") && !jsonNodeName.equals("")) {
            siebelPropertySet.addChild(parseJsonObjectToPropSet(jsonNode, jsonNodeName, "ListOf" + jsonNodeName));
            jsonNodeName = "ListOf" + jsonNodeName;
        }

        siebelPropertySet.setType(jsonNodeName);
        if (jsonNode.isArray()) {
            String effectiveJsonNodeName = jsonNodeName.substring(6);
            jsonNode.elements().forEachRemaining((e) -> {
                try {
                    siebelPropertySet.addChild(parseJsonObjectToPropSet(e, effectiveJsonNodeName, siebelPropertySet.getType()));
                } catch (Exception var4) {
                }

            });
        } else if (!jsonNodeName.startsWith("ListOf")) {
            jsonNode.fields().forEachRemaining((e) -> {
                JsonNodeType nodeType = ((JsonNode) e.getValue()).getNodeType();
                if (nodeType.equals(JsonNodeType.ARRAY)) {
                    objectArrayMap.put(e.getKey(), e.getValue());
                } else if (nodeType.equals(JsonNodeType.NUMBER)) {
                    valuePropertyMap.put(e.getKey(), String.valueOf(((JsonNode) e.getValue()).numberValue()));
                } else if (nodeType.equals(JsonNodeType.OBJECT)) {
                    objectArrayMap.put(e.getKey(), e.getValue());
                } else if (nodeType.equals(JsonNodeType.STRING)) {
                    valuePropertyMap.put(e.getKey(), ((JsonNode) e.getValue()).asText());
                }

            });
        }

        if (!jsonNodeName.startsWith("ListOf")) {
            valuePropertyMap.entrySet().forEach((e) -> {
                try {
                    siebelPropertySet.setProperty((String) e.getKey(), (String) e.getValue());
                } catch (Exception var3) {
                }

            });
        }

        objectArrayMap.entrySet().forEach((e) -> {
            try {
                siebelPropertySet.addChild(parseJsonObjectToPropSet((JsonNode) e.getValue(), ((JsonNode) e.getValue()).getNodeType().equals(JsonNodeType.ARRAY) ? "ListOf" + (String) e.getKey() : (String) e.getKey(), siebelPropertySet.getType()));
            } catch (Exception var3) {
            }

        });
        return siebelPropertySet;
    }
}

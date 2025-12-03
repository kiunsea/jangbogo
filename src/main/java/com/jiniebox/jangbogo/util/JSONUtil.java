package com.jiniebox.jangbogo.util;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * @author KIUNSEA
 */
public class JSONUtil {
  /** 로그 출력. */
  @SuppressWarnings("unused")
  private static Logger log = LogManager.getLogger(JSONUtil.class);

  /**
   * 입력된 문자열이 유효한 JSON 형태인지 확인합니다.
   *
   * @param jsonString 확인할 문자열
   * @return 유효한 JSON이면 true, 아니면 false
   */
  public static boolean isValidJson(String jsonString) {
    if (jsonString == null || jsonString.trim().isEmpty()) {
      return false; // null이거나 비어있는 문자열은 JSON이 아님
    }
    try {
      // readTree() 메서드는 유효한 JSON 문자열이면 JsonNode를 반환하고,
      // 유효하지 않으면 JsonProcessingException을 던집니다.
      new ObjectMapper().readTree(jsonString);
      return true;
    } catch (JsonProcessingException e) {
      // JSON 파싱 중 예외가 발생하면 유효한 JSON이 아닙니다.
      // 필요하다면 e.printStackTrace(); 를 통해 예외 내용을 로깅할 수 있습니다.
      return false;
    }
  }

  /**
   * Object Map을 JSONObject로 변환한다.
   *
   * @param map Map<String, Object>.
   * @return String.
   */
  @SuppressWarnings("unchecked")
  public static JSONObject getJSONObjectStringFromObjMap(Map<String, Object> map) {

    JSONObject json = new JSONObject();
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();
      json.put(key, value);
    }

    return json;
  }

  /**
   * String Map을 JSONObject로 변환한다.
   *
   * @param map Map<String, Object>.
   * @return String.
   */
  @SuppressWarnings("unchecked")
  public static JSONObject getJSONObjectFromStringMap(Map<String, String> map) {

    JSONObject json = new JSONObject();
    for (Map.Entry<String, String> entry : map.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();
      json.put(key, value);
    }

    return json;
  }

  /**
   * List<Map>을 json으로 변환한다.
   *
   * @param list List<Map<String, Object>>.
   * @return JSONArray.
   */
  @SuppressWarnings("unchecked")
  public static JSONArray getJsonArrayFromList(List<Map<String, Object>> list) {

    JSONArray jsonArray = new JSONArray();
    for (Map<String, Object> map : list) {
      jsonArray.add(getJSONObjectStringFromObjMap(map));
    }

    return jsonArray;
  }

  /**
   * List<Map>을 jsonString으로 변환한다.
   *
   * @param list List<Map<String, Object>>.
   * @return String.
   */
  @SuppressWarnings("unchecked")
  public static String getJsonStringFromList(List<Map<String, Object>> list) {

    JSONArray jsonArray = new JSONArray();
    for (Map<String, Object> map : list) {
      jsonArray.add(getJSONObjectStringFromObjMap(map));
    }

    return jsonArray.toJSONString();
  }

  /**
   * JsonObject를 Map<String, String>으로 변환한다.
   *
   * @param jsonObj JSONObject.
   * @return String.
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> getMapFromJsonObject(JSONObject jsonObj) {

    Map<String, Object> map = null;

    try {

      map = new ObjectMapper().readValue(jsonObj.toJSONString(), Map.class);

    } catch (JsonParseException e) {
      e.printStackTrace();
    } catch (JsonMappingException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }

    return map;
  }

  /**
   * JsonArray를 List<Map<String, String>>으로 변환한다.
   *
   * @param jsonArray JSONArray.
   * @return List<Map<String, Object>>.
   */
  public static List<Map<String, Object>> getListMapFromJsonArray(JSONArray jsonArray) {

    List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();

    if (jsonArray != null) {
      int jsonSize = jsonArray.size();
      for (int i = 0; i < jsonSize; i++) {
        Map<String, Object> map = JSONUtil.getMapFromJsonObject((JSONObject) jsonArray.get(i));
        list.add(map);
      }
    }

    return list;
  }

  /**
   * JSON 문자열을 JSONObject 객체로 변환
   *
   * @param jsonStr
   * @return
   * @throws ParseException
   */
  public static JSONObject parseJSONObject(String jsonStr) throws ParseException {
    JSONParser parser = new JSONParser();
    Object obj = parser.parse(jsonStr);
    JSONObject jsonObj = (JSONObject) obj;
    return jsonObj;
  }

  /**
   * JSON 배열 문자열을 JSONArray 객체로 파싱합니다. * @param jsonString 파싱할 JSON 배열 문자열
   *
   * @return 파싱된 JSONArray 객체. 유효하지 않거나 파싱 오류 발생 시 null 반환.
   */
  public static JSONArray parseJsonArray(String jsonString) {
    if (jsonString == null || jsonString.trim().isEmpty()) {
      System.err.println("오류: 입력된 JSON 문자열이 비어 있습니다.");
      return null;
    }

    // 입력 문자열의 첫 비공백 문자가 '['인지 확인하여 JSON 배열인지 검증
    String trimmedString = jsonString.trim();
    if (trimmedString.charAt(0) != '[') {
      System.err.println("오류: 입력된 문자열은 JSON 배열이 아닙니다. '['로 시작해야 합니다.");
      return null;
    }

    JSONParser parser = new JSONParser();
    try {
      // parse() 메소드는 Object를 반환하므로, JSONArray로 명시적 형변환(캐스팅) 필요
      Object obj = parser.parse(jsonString);
      return (JSONArray) obj;
    } catch (ParseException e) {
      System.err.println("오류: JSON 파싱 중 오류가 발생했습니다: " + e.getMessage());
      return null;
    } catch (ClassCastException e) {
      System.err.println("오류: JSON 파싱 후 JSONArray로 형변환에 실패했습니다. 입력 데이터 형식을 확인하세요.");
      return null;
    }
  }

  /**
   * JSON 문자열을 JsonNode 객체로 변환
   *
   * @param jsonStr
   * @return
   * @throws ParseException
   * @throws JsonProcessingException
   * @throws IOException
   */
  public static JsonNode parseJsonNode(String jsonStr)
      throws ParseException, JsonProcessingException, IOException {
    // JSON 데이터를 JSON 노드로 변환합니다.
    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(jsonStr);
    return root;
  }

  /**
   * Json 데이터 보기 좋게 변환.
   *
   * @param json
   * @return
   */
  public static String JsonEnterConvert(String json) {

    if (json == null || json.length() < 2) return json;

    final int len = json.length();
    final StringBuilder sb = new StringBuilder();
    char c;
    String tab = "";
    boolean beginEnd = true;
    for (int i = 0; i < len; i++) {
      c = json.charAt(i);
      switch (c) {
        case '{':
        case '[':
          {
            sb.append(c);
            if (beginEnd) {
              tab += "\t";
              sb.append("\n");
              sb.append(tab);
            }
            break;
          }
        case '}':
        case ']':
          {
            if (beginEnd) {
              tab = tab.substring(0, tab.length() - 1);
              sb.append("\n");
              sb.append(tab);
            }
            sb.append(c);
            break;
          }
        case '"':
          {
            if (json.charAt(i - 1) != '\\') beginEnd = !beginEnd;
            sb.append(c);
            break;
          }
        case ',':
          {
            sb.append(c);
            if (beginEnd) {
              sb.append("\n");
              sb.append(tab);
            }
            break;
          }
        default:
          {
            sb.append(c);
          }
      } // switch end
    }
    if (sb.length() > 0) sb.insert(0, '\n');
    return sb.toString();
  }

  /**
   * java bean 객체에서 field name 과 field value 를 취하여 json 객체로 변환한다
   *
   * @param obj
   * @return JSONObject
   * @throws IllegalAccessException
   */
  public static JSONObject beanToJSONObject(Object obj) throws IllegalAccessException {

    Field[] fa = obj.getClass().getDeclaredFields();

    JSONObject jsonObj = new JSONObject();
    for (int i = 0; i < fa.length; i++) {
      Field f = fa[i];
      String fn = f.getName();
      jsonObj.put(fn, f.get(obj));
    }

    return jsonObj;
  }

  /**
   * null 체크를 위한 변환 유틸 함수
   *
   * @param param
   * @return
   */
  public static String getParamString(JSONObject reqJson, String name) {
    Object value = reqJson.get(name);
    return value != null ? value.toString() : null;
  }

  /**
   * JsonNode instance를 JSONObject instance로 변환
   *
   * @param jsonNode
   * @return
   */
  public static JSONObject jsonNodeToJSONObject(JsonNode jsonNode) {
    try {
      // ObjectMapper를 사용하여 JsonNode를 String으로 변환
      ObjectMapper mapper = new ObjectMapper();
      String jsonString = mapper.writeValueAsString(jsonNode);

      // 변환된 JSON 문자열을 JSONObject로 변환
      return parseJSONObject(jsonString);
    } catch (Exception e) {
      e.printStackTrace();
      return null; // 예외 발생 시 null 반환
    }
  }

  /**
   * JSONObject instance를 JsonNode instance로 변환 단, json data 의 모든 parameter 의 value 는 object
   * reference 가 아니어야 한다.
   *
   * @param jsonObject
   * @return
   * @throws Exception
   */
  public static JsonNode jsonObjectToJsonNode(JSONObject jsonObject) throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    return objectMapper.readTree(jsonObject.toString());
  }
}

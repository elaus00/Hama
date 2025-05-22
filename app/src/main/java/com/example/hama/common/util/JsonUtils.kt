package com.example.hama.common.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * JSON 관련 유틸리티 클래스
 */
object JsonUtils {
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        isLenient = true
    }
    
    /**
     * JSON 문자열을 JsonElement로 파싱
     */
    fun parseJsonElement(jsonString: String): JsonElement? {
        return try {
            json.parseToJsonElement(jsonString)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * JSON 문자열을 JsonObject로 파싱
     */
    fun parseJsonObject(jsonString: String): JsonObject? {
        return try {
            json.parseToJsonElement(jsonString).jsonObject
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * JsonElement를 문자열로 변환
     */
    fun stringify(jsonElement: JsonElement): String {
        return jsonElement.toString()
    }
    
    /**
     * JSON 문자열이 유효한지 검증
     */
    fun isValidJson(jsonString: String): Boolean {
        return parseJsonElement(jsonString) != null
    }
}
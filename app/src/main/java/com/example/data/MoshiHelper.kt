package com.example.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object MoshiHelper {
    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val messageBlockListType = Types.newParameterizedType(List::class.java, MessageBlock::class.java)
    private val messageBlockListAdapter = moshi.adapter<List<MessageBlock>>(messageBlockListType)

    fun toJson(blocks: List<MessageBlock>): String {
        return try {
            messageBlockListAdapter.toJson(blocks)
        } catch (e: Exception) {
            "[]"
        }
    }

    fun fromJson(json: String): List<MessageBlock> {
        return try {
            messageBlockListAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}

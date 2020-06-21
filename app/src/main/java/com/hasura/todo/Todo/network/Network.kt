package com.hasura.todo.Todo.network

import android.content.Context
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.CustomTypeAdapter
import com.apollographql.apollo.api.CustomTypeValue
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.ResponseField
import com.apollographql.apollo.cache.normalized.CacheKey
import com.apollographql.apollo.cache.normalized.CacheKeyResolver
import com.apollographql.apollo.cache.normalized.NormalizedCacheFactory
import com.apollographql.apollo.cache.normalized.lru.EvictionPolicy
import com.apollographql.apollo.cache.normalized.lru.LruNormalizedCache
import com.apollographql.apollo.cache.normalized.lru.LruNormalizedCacheFactory
import com.apollographql.apollo.cache.normalized.sql.ApolloSqlHelper
import com.apollographql.apollo.cache.normalized.sql.SqlNormalizedCacheFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.text.ParseException
import java.text.SimpleDateFormat

class Network {

    companion object {
        @JvmStatic
        lateinit var apolloClient: ApolloClient

        private const val GRAPHQL_ENDPOINT: String = "https://hasura.io/learn/graphql"
        private const val SQL_CACHE_NAME = "hasuratodo"
    }


    fun setApolloClient(accessTokenId: String, context: Context) {
        val dateCustomTypeAdapter = createCustomTypeAdapter()
        apolloClient = ApolloClient.builder()
            .serverUrl(GRAPHQL_ENDPOINT)
            .okHttpClient(buildOkHttpClient(accessTokenId))
            .normalizedCache(createNormalizedCacheFactory(context), createCacheKeyResolver())
//            .addCustomTypeAdapter(CustomTyp, dateCustomTypeAdapter)
            .build()
    }

    private fun createCacheKeyResolver(): CacheKeyResolver {
        return object : CacheKeyResolver() {
            override fun fromFieldRecordSet(
                field: ResponseField,
                recordSet: Map<String, Any>
            ): CacheKey {
                if (recordSet.containsKey("todos")) {
                    val id = recordSet["todos"] as String
                    return CacheKey.from(id)
                }
                return CacheKey.NO_KEY
            }

            override fun fromFieldArguments(
                field: ResponseField,
                variables: Operation.Variables
            ): CacheKey {
                return CacheKey.NO_KEY
            }
        }
    }

    private fun createNormalizedCacheFactory(context: Context): NormalizedCacheFactory<LruNormalizedCache> {
        val apolloSqlHelper = ApolloSqlHelper(context, SQL_CACHE_NAME)
        return LruNormalizedCacheFactory(EvictionPolicy.NO_EVICTION)
            .chain(SqlNormalizedCacheFactory(apolloSqlHelper))
    }

    private fun createCustomTypeAdapter(): CustomTypeAdapter<String> {
        return object : CustomTypeAdapter<String> {
            var ISO8601 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ")
            override fun decode(value: CustomTypeValue<*>): String {
                try {
                    return ISO8601.parse(value.value.toString()).toString()
                } catch (e: ParseException) {
                    throw RuntimeException(e)
                }
            }

            override fun encode(value: String): CustomTypeValue<*> {
                return CustomTypeValue.GraphQLString(value)
            }
        }
    }

    private fun buildOkHttpClient(accessTokenId: String): OkHttpClient {
        val log: HttpLoggingInterceptor =
            HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY)
        val authHeader = "Bearer $accessTokenId"
        return OkHttpClient.Builder()
            .addInterceptor(log)
            .addInterceptor { chain ->
                val original = chain.request()
                val builder = original.newBuilder().method(original.method(), original.body())
                builder.header("Authorization", authHeader)
                chain.proceed(builder.build())
            }
            .build()
    }
}
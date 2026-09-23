package com.bharath.focusguard.data.remote

import retrofit2.http.*

interface NotionApiService {
    @Headers("Notion-Version: 2022-06-28")
    @POST("v1/databases/{database_id}/query")
    suspend fun queryDatabase(
        @Path("database_id") databaseId: String,
        @Body body: NotionQueryRequest = NotionQueryRequest()
    ): NotionQueryResponse

    @Headers("Notion-Version: 2022-06-28")
    @PATCH("v1/pages/{page_id}")
    suspend fun updatePageCheckbox(
        @Path("page_id") pageId: String,
        @Body body: NotionCheckboxUpdate
    )
}

data class NotionQueryRequest(val page_size: Int = 20)
data class NotionQueryResponse(val results: List<NotionPage>)
data class NotionPage(val id: String, val properties: Map<String, Any>)
data class NotionCheckboxUpdate(val properties: Map<String, Any>)

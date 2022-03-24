package com.example.serverdriven.network


import br.com.zup.beagle.android.networking.*
import com.example.designsystem.component.sharedpreference.Storage
import com.squareup.okhttp.*
import java.io.IOException

class CustomHttpClient(
    private val storage: Storage,
    private val okHttpClient: OkHttpClient = OkHttpClient()
) : HttpClient {

    private var shouldStore = false

    companion object{
        private const val FALLBACK = "FALLBACK"
    }

    override fun execute(
            request: RequestData,
            onSuccess: (responseData: ResponseData) -> Unit,
            onError: (responseData: ResponseData) -> Unit): RequestCall {

        shouldStore = request.headers[FALLBACK]?.toBoolean() ?: false

        return makeRequest(request, onSuccess, onError)
    }

    private fun makeRequest(request: RequestData,
                            onSuccess: (responseData: ResponseData) -> Unit,
                            onError: (responseData: ResponseData) -> Unit) : RequestCall {

        val call = request.createCall(client = okHttpClient)

        call.enqueue(object : Callback {
            override fun onResponse(response: Response?) {
                 response?.let {
                    with(response.toRespondData()){
                        if (shouldStore) storage.save(response.request().url().toString(), this)
                        onSuccess.invoke(this)
                    }
                }
            }

            override fun onFailure(request: Request?, e: IOException?) {

                if (shouldStore)
                    storage.retrieve(request?.url().toString(), ResponseData::class.java)?.let{
                        onSuccess.invoke(it)
                    }

                storage.retrieve(request?.url().toString(), ResponseData::class.java)?.let{
                    onSuccess.invoke(it)
                }?: run {
                    onError.invoke(
                        ResponseData(
                            statusCode = 0,
                            data = byteArrayOf(),
                            mapOf()
                        )
                    )
                }
            }
        })

        return object : RequestCall{
            override fun cancel() {
                call.cancel()
            }
        }

    }

    private fun Response.toRespondData () = ResponseData(
            statusCode = code(),
            data = body()?.bytes() ?: byteArrayOf()
    )

    private fun RequestData.createCall(client : OkHttpClient) = client.newCall(
            Request.Builder()
                    .url(uri.toString())
                    .headers(Headers.of(headers))
                    .method(
                          method.name,
                          body.toRequestBody(method)
                    ).build()
    )

    private fun String?.toRequestBody(httpMethod : HttpMethod) = when(httpMethod) {
        HttpMethod.GET -> null
        else ->  RequestBody.create(MediaType.parse("application/json; charset=UTF-8"), this ?: "")
    }

}
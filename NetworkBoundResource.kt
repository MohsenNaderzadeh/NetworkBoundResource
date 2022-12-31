import android.util.Log
import com.noavarpub.noavaronline.android.base.BaseApiResponse
import com.noavarpub.noavaronline.android.utils.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.SocketTimeoutException

inline fun <CacheObject, ServerResponseType> networkBoundResource(
    shouldLoadFromCache: Boolean,
    doesRequestNeedInternet: Boolean,
    isNetworkAvailable: Boolean,
    shouldCacheServerResponse: Boolean,
    crossinline getCachedData: suspend () -> CacheObject,
    crossinline doNetworkRequest: suspend () -> RetrofitApiResponse<BaseApiResponse<ServerResponseType>>?,
    crossinline handleApiSuccessResponse: suspend (serverResponse: RetrofitApiResponse<BaseApiResponse<ServerResponseType>>) -> Unit,
    crossinline saveCacheResult: suspend (dataToBeCached: ServerResponseType) -> Unit,
) = flow {
    if (isNetworkAvailable) {
        if (doesRequestNeedInternet) {
            if (shouldLoadFromCache) {
                emit(Resource.loading(getCachedData()))
            }
            val networkResult = doNetworkRequest()
            networkResult?.let {
                when (networkResult) {
                    is ApiSuccessResponse -> {
                        if (shouldCacheServerResponse) {
                            networkResult.data.result?.let {
                                Log.i(
                                    "NetworkBoundResource", "Network request response received " +
                                            "with success status and saveCacheResult Called"
                                )
                                saveCacheResult(it)
                                emit(Resource.success(getCachedData()))
                            }
                        }
                        handleApiSuccessResponse(networkResult)
                    }
                    is ApiEmptyResponse -> {
                        Log.i(
                            "NetworkBoundResource",
                            "Network request response received with Empty status"
                        )
                        emit(Resource.error(networkResult.errorMessage, null, null))
                    }
                    is ApiErrorResponse -> {
                        Log.i(
                            "NetworkBoundResource",
                            "Network request response received with Error status"
                        )
                        emit(Resource.error(networkResult.errorMessage, null, null))
                    }
                }
            } ?: emit(Resource.error("timeout", null, SocketTimeoutException()))
        } else {
            if (shouldLoadFromCache) {
                val cachedData = getCachedData()
                emit(Resource.success(cachedData))
            } else {
                emit(Resource.error<CacheObject>("Network not available", null, null))
            }
        }
    } else {
        if (shouldLoadFromCache) {
            val cachedData = getCachedData()
            emit(Resource.success(cachedData))
        } else {
            emit(Resource.error<CacheObject>("Network not available", null, null))
        }
    }
}.cancellable().flowOn(IO)


package com.skrypko.dicefamiliar.domain.remote

/**
 * Executes an action on this device for the local user, through the same registry that
 * will handle requests from other devices. The local participant is both requester and responder.
 */
class LocalActionExecutor(
    private val registry: RemoteActionRegistry,
    private val codec: ActionCodec,
) {
    suspend fun <Req, Res> execute(
        action: RemoteAction<Req, Res>,
        payload: Req,
        self: Participant,
        label: String? = null,
    ): Pair<ActionRequest, ActionOutcome<Res>> {
        val request = codec.request(action, payload, from = self, to = self, label = label)
        val response = registry.dispatch(request, responder = self)
        return request to codec.decode(action, response)
    }
}

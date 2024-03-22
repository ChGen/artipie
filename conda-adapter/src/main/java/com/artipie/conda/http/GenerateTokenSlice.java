/*
 * The MIT License (MIT) Copyright (c) 2020-2023 artipie.com
 * https://github.com/artipie/artipie/blob/master/LICENSE.txt
 */
package com.artipie.conda.http;

import com.artipie.asto.Content;
import com.artipie.http.Headers;
import com.artipie.http.Response;
import com.artipie.http.Slice;
import com.artipie.http.async.AsyncResponse;
import com.artipie.http.auth.AuthScheme;
import com.artipie.http.auth.Authentication;
import com.artipie.http.auth.BasicAuthScheme;
import com.artipie.http.auth.Tokens;
import com.artipie.http.headers.WwwAuthenticate;
import com.artipie.http.rq.RequestLine;
import com.artipie.http.BaseResponse;

import javax.json.Json;

/**
 * Slice for token authorization.
 */
final class GenerateTokenSlice implements Slice {

    /**
     * Authentication.
     */
    private final Authentication auth;

    /**
     * Tokens.
     */
    private final Tokens tokens;

    /**
     * @param auth Authentication
     * @param tokens Tokens
     */
    GenerateTokenSlice(final Authentication auth, final Tokens tokens) {
        this.auth = auth;
        this.tokens = tokens;
    }

    @Override
    public Response response(RequestLine line, Headers headers, Content body) {
        return new AsyncResponse(
            new BasicAuthScheme(this.auth).authenticate(headers).thenApply(
                result -> {
                    if (result.status() == AuthScheme.AuthStatus.FAILED) {
                        return BaseResponse.unauthorized()
                            .header(new WwwAuthenticate(result.challenge()));
                    }
                    return BaseResponse.ok()
                        .jsonBody(
                            Json.createObjectBuilder()
                                .add("token", this.tokens.generate(result.user()))
                                .build()
                        );
                }
            )
        );
    }
}

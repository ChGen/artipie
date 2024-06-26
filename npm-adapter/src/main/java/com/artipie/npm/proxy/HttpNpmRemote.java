/*
 * The MIT License (MIT) Copyright (c) 2020-2023 artipie.com
 * https://github.com/artipie/artipie/blob/master/LICENSE.txt
 */
package com.artipie.npm.proxy;

import com.artipie.asto.Content;
import com.artipie.http.ArtipieHttpException;
import com.artipie.http.Headers;
import com.artipie.http.Slice;
import com.artipie.http.headers.ContentType;
import com.artipie.http.rq.RequestLine;
import com.artipie.http.rq.RqHeaders;
import com.artipie.http.rq.RqMethod;
import com.artipie.npm.misc.DateTimeNowStr;
import com.artipie.npm.proxy.json.CachedContent;
import com.artipie.npm.proxy.model.NpmAsset;
import com.artipie.npm.proxy.model.NpmPackage;
import com.jcabi.log.Logger;
import io.reactivex.Maybe;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Base NPM Remote client implementation. It calls remote NPM repository
 * to download NPM packages and assets. It uses underlying Vertx Web Client inside
 * and works in Rx-way.
 */
public final class HttpNpmRemote implements NpmRemote {

    /**
     * Origin client slice.
     */
    private final Slice origin;

    /**
     * @param origin Client slice
     */
    public HttpNpmRemote(final Slice origin) {
        this.origin = origin;
    }

    @Override
    public Maybe<NpmPackage> loadPackage(final String name) {
        return Maybe.fromFuture(
            this.performRemoteRequest(name).thenCompose(
                pair -> pair.getKey().asStringFuture().thenApply(
                    str -> new NpmPackage(
                        name,
                        new CachedContent(str, name).value().toString(),
                        HttpNpmRemote.lastModifiedOrNow(pair.getValue()),
                        OffsetDateTime.now()
                    )
                )
            ).toCompletableFuture()
        ).onErrorResumeNext(
            throwable -> {
                Logger.error(
                    HttpNpmRemote.class,
                    "Error occurred when process get package call: %s",
                    throwable.getMessage()
                );
                return Maybe.empty();
            }
        );
    }

    @Override
    public Maybe<NpmAsset> loadAsset(final String path, final Path tmp) {
        return Maybe.fromFuture(
            this.performRemoteRequest(path).thenApply(
                pair -> new NpmAsset(
                    path,
                    pair.getKey(),
                    HttpNpmRemote.lastModifiedOrNow(pair.getValue()),
                    HttpNpmRemote.contentType(pair.getValue())
                )
            )
        ).onErrorResumeNext(
            throwable -> {
                Logger.error(
                    HttpNpmRemote.class,
                    "Error occurred when process get asset call: %s",
                    throwable.getMessage()
                );
                return Maybe.empty();
            }
        );
    }

    @Override
    public void close() {
        //does nothing
    }

    /**
     * Performs request to remote and returns remote body and headers in CompletableFuture.
     * @param name Asset name
     * @return Completable action with content and headers
     */
    private CompletableFuture<Pair<Content, Headers>> performRemoteRequest(final String name) {
        return this.origin.response(
            new RequestLine(RqMethod.GET, String.format("/%s", name)),
            Headers.EMPTY, Content.EMPTY
        ).<CompletableFuture<Pair<Content, Headers>>>thenApply(response -> {
            if (response.status().success()) {
                return CompletableFuture.completedFuture(
                    new ImmutablePair<>(response.body(), response.headers())
                );
            }
            return CompletableFuture.failedFuture(new ArtipieHttpException(response.status()));
        }).thenCompose(Function.identity());
    }

    /**
     * Tries to get header {@code Last-Modified} from remote response
     * or returns current time.
     * @param headers Remote headers
     * @return Time value.
     */
    private static String lastModifiedOrNow(final Headers headers) {
        final RqHeaders hdr = new RqHeaders(headers, "Last-Modified");
        String res = new DateTimeNowStr().value();
        if (!hdr.isEmpty()) {
            res = hdr.get(0);
        }
        return res;
    }

    /**
     * Tries to get header {@code ContentType} from remote response
     * or returns {@code application/octet-stream}.
     * @param headers Remote headers
     * @return Content type value
     */
    private static String contentType(final Headers headers) {
        final RqHeaders hdr = new RqHeaders(headers, ContentType.NAME);
        String res = "application/octet-stream";
        if (!hdr.isEmpty()) {
            res = hdr.get(0);
        }
        return res;
    }
}

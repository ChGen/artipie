/*
 * The MIT License (MIT) Copyright (c) 2020-2023 artipie.com
 * https://github.com/artipie/artipie/blob/master/LICENSE.txt
 */
package com.artipie.docker.http;

import com.artipie.asto.Content;
import com.artipie.docker.Digest;
import com.artipie.docker.Docker;
import com.artipie.docker.RepoName;
import com.artipie.docker.error.BlobUnknownError;
import com.artipie.docker.misc.RqByRegex;
import com.artipie.docker.perms.DockerRepositoryPermission;
import com.artipie.http.Headers;
import com.artipie.http.Response;
import com.artipie.http.async.AsyncResponse;
import com.artipie.http.headers.ContentLength;
import com.artipie.http.headers.ContentType;
import com.artipie.http.rq.RequestLine;
import com.artipie.http.BaseResponse;
import com.artipie.http.rs.RsStatus;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

/**
 * Blob entity in Docker HTTP API.
 * See <a href="https://docs.docker.com/registry/spec/api/#blob">Blob</a>.
 */
final class BlobEntity {

    /**
     * RegEx pattern for path.
     */
    public static final Pattern PATH = Pattern.compile(
        "^/v2/(?<name>.*)/blobs/(?<digest>(?!(uploads/)).*)$"
    );

    private BlobEntity() {
    }

    /**
     * Slice for GET method.
     */
    static final class Get implements ScopeSlice {

        /**
         * Docker repository.
         */
        private final Docker docker;

        /**
         * @param docker Docker repository.
         */
        Get(final Docker docker) {
            this.docker = docker;
        }

        @Override
        public DockerRepositoryPermission permission(final RequestLine line, final String name) {
            return new DockerRepositoryPermission(
                name, new Scope.Repository.Pull(new Request(line).name())
            );
        }

        @Override
        public Response response(
            final RequestLine line,
            final Headers headers,
            final Content body
        ) {
            final Request request = new Request(line);
            final Digest digest = request.digest();
            return new AsyncResponse(
                this.docker.repo(request.name()).layers().get(digest).thenApply(
                    found -> found.<Response>map(
                        blob -> new AsyncResponse(
                            blob.content().thenCompose(
                                content -> content.size()
                                    .<CompletionStage<Long>>map(CompletableFuture::completedFuture)
                                    .orElseGet(blob::size)
                                    .thenApply(
                                        size -> BaseResponse.ok()
                                            .header(new DigestHeader(digest))
                                            .header(ContentType.mime("application/octet-stream"))
                                            .body(new Content.From(size, content))
                                    )
                            )
                        )
                    ).orElseGet(
                        () -> new ErrorsResponse(RsStatus.NOT_FOUND, new BlobUnknownError(digest))
                    )
                )
            );
        }
    }

    /**
     * Slice for HEAD method.
     */
    static final class Head implements ScopeSlice {

        /**
         * Docker repository.
         */
        private final Docker docker;

        /**
         * @param docker Docker repository.
         */
        Head(final Docker docker) {
            this.docker = docker;
        }

        @Override
        public DockerRepositoryPermission permission(RequestLine line, String name) {
            return new DockerRepositoryPermission(
                name, new Scope.Repository.Pull(new Request(line).name())
            );
        }

        @Override
        public Response response(RequestLine line, Headers headers, Content body) {
            final Request request = new Request(line);
            final Digest digest = request.digest();
            return new AsyncResponse(
                this.docker.repo(request.name()).layers().get(digest).thenApply(
                    found -> found.<Response>map(
                        blob -> new AsyncResponse(
                            blob.size().thenApply(
                                size -> BaseResponse.ok()
                                    .header(new DigestHeader(blob.digest()))
                                    .header(ContentType.mime("application/octet-stream"))
                                    .header(new ContentLength(size))
                            )
                        )
                    ).orElseGet(
                        () -> new ErrorsResponse(RsStatus.NOT_FOUND, new BlobUnknownError(digest))
                    )
                )
            );
        }
    }

    /**
     * HTTP request to blob entity.
     */
    static final class Request {

        /**
         * HTTP request line.
         */
        private final RqByRegex reqRegex;

        /**
         * @param line HTTP request line.
         */
        Request(final RequestLine line) {
            this.reqRegex = new RqByRegex(line, BlobEntity.PATH);
        }

        /**
         * Get repository name.
         *
         * @return Repository name.
         */
        RepoName name() {
            return new RepoName.Valid(this.reqRegex.path().group("name"));
        }

        /**
         * Get digest.
         *
         * @return Digest.
         */
        Digest digest() {
            return new Digest.FromString(this.reqRegex.path().group("digest"));
        }

    }
}

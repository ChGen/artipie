/*
 * The MIT License (MIT) Copyright (c) 2020-2023 artipie.com
 * https://github.com/artipie/artipie/blob/master/LICENSE.txt
 */
package com.artipie.docker.http;

import com.artipie.asto.Content;
import com.artipie.asto.Key;
import com.artipie.asto.blocking.BlockingStorage;
import com.artipie.docker.ExampleStorage;
import com.artipie.docker.asto.AstoDocker;
import com.artipie.http.Headers;
import com.artipie.http.ResponseImpl;
import com.artipie.http.headers.Header;
import com.artipie.http.hm.ResponseAssert;
import com.artipie.http.rq.RequestLine;
import com.artipie.http.rq.RqMethod;
import com.artipie.http.RsStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link DockerSlice}.
 * Blob Get endpoint.
 */
class BlobEntityGetTest {

    /**
     * Slice being tested.
     */
    private DockerSlice slice;

    @BeforeEach
    void setUp() {
        this.slice = new DockerSlice(new AstoDocker(new ExampleStorage()));
    }

    @Test
    void shouldReturnLayer() throws Exception {
        final String digest = String.format(
            "%s:%s",
            "sha256",
            "aad63a9339440e7c3e1fff2b988991b9bfb81280042fa7f39a5e327023056819"
        );
        final ResponseImpl response = this.slice.response(
            new RequestLine(
                RqMethod.GET,
                String.format("/v2/test/blobs/%s", digest)
            ),
            Headers.EMPTY,
            Content.EMPTY
        ).join();
        final Key expected = new Key.From(
            "blobs", "sha256", "aa",
            "aad63a9339440e7c3e1fff2b988991b9bfb81280042fa7f39a5e327023056819", "data"
        );
        ResponseAssert.check(
            response,
            RsStatus.OK,
            new BlockingStorage(new ExampleStorage()).value(expected),
            new Header("Content-Length", "2803255"),
            new Header("Docker-Content-Digest", digest),
            new Header("Content-Type", "application/octet-stream")
        );
    }

    @Test
    void shouldReturnNotFoundForUnknownDigest() {
        ResponseAssert.check(
            this.slice.response(
                new RequestLine(RqMethod.GET, "/v2/test/blobs/" +
                    "sha256:0123456789012345678901234567890123456789012345678901234567890123"),
                Headers.EMPTY, Content.EMPTY).join(),
            RsStatus.NOT_FOUND
        );
    }
}

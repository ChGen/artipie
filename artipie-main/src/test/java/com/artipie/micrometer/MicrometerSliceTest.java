/*
 * The MIT License (MIT) Copyright (c) 2020-2023 artipie.com
 * https://github.com/artipie/artipie/blob/master/LICENSE.txt
 */
package com.artipie.micrometer;

import com.artipie.http.hm.RsHasStatus;
import com.artipie.http.hm.SliceHasResponse;
import com.artipie.http.rq.RequestLine;
import com.artipie.http.rq.RqMethod;
import com.artipie.http.BaseResponse;
import com.artipie.http.rs.RsStatus;
import com.artipie.http.slice.SliceSimple;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.reactivex.Flowable;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Test for {@link MicrometerSlice}.
 * @since 0.28
 */
class MicrometerSliceTest {

    /**
     * Test registry.
     */
    private SimpleMeterRegistry registry;

    @BeforeEach
    void init() {
        this.registry = new SimpleMeterRegistry();
    }

    @Test
    void addsSummaryToRegistry() {
        final String path = "/same/path";
        MatcherAssert.assertThat(
            new MicrometerSlice(
                new SliceSimple(
                    BaseResponse.ok().body(Flowable.fromArray(
                        ByteBuffer.wrap("Hello ".getBytes(StandardCharsets.UTF_8)),
                        ByteBuffer.wrap("world!".getBytes(StandardCharsets.UTF_8))
                    ))
                ),
                this.registry
            ),
            new SliceHasResponse(
                new RsHasStatus(RsStatus.OK),
                new RequestLine(RqMethod.GET, path)
            )
        );
        MatcherAssert.assertThat(
            new MicrometerSlice(
                new SliceSimple(
                    BaseResponse.ok().body("abc".getBytes(StandardCharsets.UTF_8))
                ),
                this.registry
            ),
            new SliceHasResponse(
                new RsHasStatus(RsStatus.OK),
                new RequestLine(RqMethod.GET, path)
            )
        );
        MatcherAssert.assertThat(
            new MicrometerSlice(
                new SliceSimple(BaseResponse.from(RsStatus.CONTINUE)),
                this.registry
            ),
            new SliceHasResponse(
                new RsHasStatus(RsStatus.CONTINUE),
                new RequestLine(RqMethod.POST, "/a/b/c")
            )
        );
        MatcherAssert.assertThat(
            List.of(this.registry.getMetersAsString().split("\n")),
            Matchers.containsInAnyOrder(
                Matchers.containsString("artipie.connection.accept(TIMER)[status='OK']; count=2.0, total_time"),
                Matchers.containsString("artipie.connection.accept(TIMER)[status='CONTINUE']; count=1.0, total_time="),
                Matchers.containsString("artipie.request.body.size(DISTRIBUTION_SUMMARY)[method='POST']; count=0.0, total=0.0 bytes, max=0.0 bytes"),
                Matchers.containsString("artipie.request.body.size(DISTRIBUTION_SUMMARY)[method='GET']; count=0.0, total=0.0 bytes, max=0.0 bytes"),
                Matchers.containsString("artipie.request.counter(COUNTER)[method='POST', status='CONTINUE']; count=1.0"),
                Matchers.containsString("artipie.request.counter(COUNTER)[method='GET', status='OK']; count=2.0"),
                Matchers.containsString("artipie.response.body.size(DISTRIBUTION_SUMMARY)[method='POST']; count=0.0, total=0.0 bytes, max=0.0 bytes"),
                Matchers.containsString("artipie.response.body.size(DISTRIBUTION_SUMMARY)[method='GET']; count=3.0, total=15.0 bytes, max=6.0 bytes"),
                Matchers.containsString("artipie.response.send(TIMER)[]; count=3.0, total_time="),
                Matchers.containsString("artipie.slice.response(TIMER)[status='OK']; count=2.0, total_time"),
                Matchers.containsString("artipie.slice.response(TIMER)[status='CONTINUE']; count=1.0, total_time")
            )
        );
    }

}

/*
 * The MIT License (MIT) Copyright (c) 2020-2023 artipie.com
 * https://github.com/artipie/artipie/blob/master/LICENSE.txt
 */
package com.artipie.conda.http;

import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.conda.http.auth.TokenAuth;
import com.artipie.conda.http.auth.TokenAuthScheme;
import com.artipie.conda.http.auth.TokenAuthSlice;
import com.artipie.http.ResponseBuilder;
import com.artipie.http.Slice;
import com.artipie.http.auth.Authentication;
import com.artipie.http.auth.BasicAuthzSlice;
import com.artipie.http.auth.OperationControl;
import com.artipie.http.auth.Tokens;
import com.artipie.http.rt.MethodRule;
import com.artipie.http.rt.RtRule;
import com.artipie.http.rt.RtRulePath;
import com.artipie.http.rt.SliceRoute;
import com.artipie.http.slice.KeyFromPath;
import com.artipie.http.slice.SliceDownload;
import com.artipie.http.slice.SliceSimple;
import com.artipie.scheduling.ArtifactEvent;
import com.artipie.security.perms.Action;
import com.artipie.security.perms.AdapterBasicPermission;
import com.artipie.security.policy.Policy;

import java.util.Optional;
import java.util.Queue;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Main conda entry point. Note, that {@link com.artipie.http.slice.TrimPathSlice} is not
 * applied for conda-adapter in artipie, which means all the paths includes repository name
 * when the adapter is used in Artipie ecosystem. The reason is that anaconda performs
 * various requests, for example:
 * /{reponame}/release/{username}/snappy/1.1.3
 * /{reponame}/dist/{username}/snappy/1.1.3/linux-64/snappy-1.1.3-0.tar.bz2
 * /t/{usertoken}/{reponame}/noarch/current_repodata.json
 * /t/{usertoken}/{reponame}/linux-64/snappy-1.1.3-0.tar.bz2
 * In the last two cases authentication is performed by the token in path, and thus
 * we cannot trim first part of the path.
 * @since 0.4
 */
@SuppressWarnings("PMD.ExcessiveMethodLength")
public final class CondaSlice extends Slice.Wrap {

    /**
     * Transform pattern for download slice.
     */
    private static final Pattern PTRN = Pattern.compile(".*/(.*/.*(\\.tar\\.bz2|\\.conda))$");

    /**
     * Ctor.
     * @param storage Storage
     * @param policy Permissions
     * @param users Users
     * @param tokens Tokens
     * @param url Application url
     * @param repo Repository name
     * @param events Events queue
     */
    public CondaSlice(final Storage storage, final Policy<?> policy, final Authentication users,
        final Tokens tokens, final String url, final String repo,
        final Optional<Queue<ArtifactEvent>> events) {
        super(
            new SliceRoute(
                new RtRulePath(
                    new RtRule.All(
                        new RtRule.ByPath("/t/.*repodata\\.json$"),
                        MethodRule.GET
                    ),
                    new TokenAuthSlice(
                        new DownloadRepodataSlice(storage),
                        new OperationControl(
                            policy, new AdapterBasicPermission(repo, Action.Standard.READ)
                        ),
                        tokens.auth()
                    )
                ),
                new RtRulePath(
                    new RtRule.All(
                        new RtRule.ByPath(".*repodata\\.json$"),
                        MethodRule.GET
                    ),
                    new BasicAuthzSlice(
                        new DownloadRepodataSlice(storage), users,
                        new OperationControl(
                            policy, new AdapterBasicPermission(repo, Action.Standard.READ)
                        )
                    )
                ),
                new RtRulePath(
                    new RtRule.All(
                        new RtRule.ByPath(".*(/dist/|/t/).*(\\.tar\\.bz2|\\.conda)$"),
                        MethodRule.GET
                    ),
                    new TokenAuthSlice(
                        new SliceDownload(storage, CondaSlice.transform()),
                        new OperationControl(
                            policy, new AdapterBasicPermission(repo, Action.Standard.READ)
                        ), tokens.auth()
                    )
                ),
                new RtRulePath(
                    new RtRule.All(
                        new RtRule.ByPath(".*(\\.tar\\.bz2|\\.conda)$"),
                        MethodRule.GET
                    ),
                    new BasicAuthzSlice(
                        new SliceDownload(storage, CondaSlice.transform()), users,
                        new OperationControl(
                            policy, new AdapterBasicPermission(repo, Action.Standard.READ)
                        )
                    )
                ),
                new RtRulePath(
                    new RtRule.All(
                        new RtRule.ByPath(".*/(stage|commit).*(\\.tar\\.bz2|\\.conda)$"),
                        MethodRule.POST
                    ),
                    new TokenAuthSlice(
                        new PostStageCommitSlice(url),
                        new OperationControl(
                            policy, new AdapterBasicPermission(repo, Action.Standard.READ)
                        ), tokens.auth()
                    )
                ),
                new RtRulePath(
                    new RtRule.All(
                        new RtRule.ByPath(".*/(package|release)/.*"),
                        MethodRule.GET
                    ),
                    new TokenAuthSlice(
                        new GetPackageSlice(),
                        new OperationControl(
                            policy, new AdapterBasicPermission(repo, Action.Standard.READ)
                        ), tokens.auth()
                    )
                ),
                new RtRulePath(
                    new RtRule.All(
                        new RtRule.ByPath(".*/(package|release)/.*"),
                        MethodRule.POST
                    ),
                    new TokenAuthSlice(
                        new PostPackageReleaseSlice(),
                        new OperationControl(
                            policy, new AdapterBasicPermission(repo, Action.Standard.WRITE)
                        ), tokens.auth()
                    )
                ),
                new RtRulePath(
                    new RtRule.All(
                        new RtRule.ByPath("/?[a-z0-9-._]*/[a-z0-9-._]*/[a-z0-9-._]*(\\.tar\\.bz2|\\.conda)$"),
                        MethodRule.POST
                    ),
                    new UpdateSlice(storage, events, repo)
                ),
                new RtRulePath(MethodRule.HEAD, new SliceSimple(ResponseBuilder.ok().build())),
                new RtRulePath(
                    new RtRule.All(new RtRule.ByPath(".*user$"), MethodRule.GET),
                    new TokenAuthSlice(
                        new GetUserSlice(new TokenAuthScheme(new TokenAuth(tokens.auth()))),
                        new OperationControl(
                            policy, new AdapterBasicPermission(repo, Action.Standard.READ)
                        ),
                        tokens.auth()
                    )
                ),
                new RtRulePath(
                    new RtRule.All(
                        new RtRule.ByPath(".*authentication-type$"),
                        MethodRule.GET
                    ),
                    new AuthTypeSlice()
                ),
                new RtRulePath(
                    new RtRule.All(
                        new RtRule.ByPath(".*authentications$"), MethodRule.POST
                    ),
                    new BasicAuthzSlice(
                        new GenerateTokenSlice(users, tokens), users,
                        new OperationControl(
                            policy, new AdapterBasicPermission(repo, Action.Standard.WRITE)
                        )
                    )
                ),
                new RtRulePath(
                    new RtRule.All(
                        new RtRule.ByPath(".*authentications$"), MethodRule.DELETE
                    ),
                    new BasicAuthzSlice(
                        new DeleteTokenSlice(tokens), users,
                        new OperationControl(
                            policy, new AdapterBasicPermission(repo, Action.Standard.WRITE)
                        )
                    )
                ),
                new RtRulePath(RtRule.FALLBACK, new SliceSimple(ResponseBuilder.notFound().build()))
            )
        );
    }

    /**
     * Function to transform path to download conda package. Conda client can perform requests
     * for download with user token:
     * /t/user-token/linux-64/some-package.tar.bz2
     * @return Function to transform path to key
     */
    private static Function<String, Key> transform() {
        return path -> {
            final Matcher mtchr = PTRN.matcher(path);
            final Key res;
            if (mtchr.matches()) {
                res = new Key.From(mtchr.group(1));
            } else {
                res = new KeyFromPath(path);
            }
            return res;
        };
    }
}

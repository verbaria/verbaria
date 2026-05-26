/*
 * Copyright 2012, Red Hat, Inc. and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.zanata.client.commands.push;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableList;
import org.springframework.util.AntPathMatcher;

/**
 * Strategy that provides basic directory scanning for source files.
 *
 * @author David Mason, <a
 *         href="mailto:damason@redhat.com">damason@redhat.com</a>
 */
public abstract class AbstractCommonPushStrategy<O extends PushOptions> {

    /**
     * Patterns matched against any path segment to mimic Ant's
     * {@code addDefaultExcludes()} (common VCS / editor artefacts).
     */
    private static final String[] DEFAULT_EXCLUDES = {
            "**/.svn", "**/.svn/**",
            "**/.git", "**/.git/**",
            "**/.gitignore", "**/.gitattributes",
            "**/.hg", "**/.hg/**",
            "**/.bzr", "**/.bzr/**",
            "**/CVS", "**/CVS/**",
            "**/.DS_Store",
            "**/*~", "**/.#*", "**/#*#", "**/%*%", "**/._*"
    };

    private O opts;

    public O getOpts() {
        return opts;
    }

    public void setPushOptions(O opts) {
        this.opts = opts;
    }

    /**
     * excludes should already contain paths for translation files that are to
     * be excluded.
     */
    public String[] getSrcFiles(File srcDir, ImmutableList<String> includes,
            ImmutableList<String> excludes, ImmutableList<String> fileExtensions,
            boolean useDefaultExcludes, boolean isCaseSensitive) {
        if (includes.isEmpty()) {
            ImmutableList.Builder<String> builder = ImmutableList.builder();
            for (String fileExtension : fileExtensions) {
                builder.add("**/*" + fileExtension);
            }
            includes = builder.build();
        }

        List<String> allExcludes = new ArrayList<>();
        for (String fileExtension : fileExtensions) {
            allExcludes.add("**/" + fileExtension);
        }
        allExcludes.addAll(excludes);
        if (useDefaultExcludes) {
            for (String def : DEFAULT_EXCLUDES) {
                allExcludes.add(def);
            }
        }

        AntPathMatcher matcher = new AntPathMatcher();
        matcher.setCaseSensitive(isCaseSensitive);
        matcher.setPathSeparator("/");

        List<String> matches = new ArrayList<>();
        Path baseDir = srcDir.toPath();
        if (!Files.isDirectory(baseDir)) {
            return new String[0];
        }
        try {
            final ImmutableList<String> finalIncludes = includes;
            Files.walkFileTree(baseDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file,
                        BasicFileAttributes attrs) {
                    String rel = baseDir.relativize(file).toString()
                            .replace(File.separatorChar, '/');
                    if (matchesAny(matcher, rel, finalIncludes)
                            && !matchesAny(matcher, rel, allExcludes)) {
                        matches.add(rel);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir,
                        BasicFileAttributes attrs) {
                    if (dir.equals(baseDir)) {
                        return FileVisitResult.CONTINUE;
                    }
                    String rel = baseDir.relativize(dir).toString()
                            .replace(File.separatorChar, '/');
                    if (matchesAny(matcher, rel, allExcludes)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to scan source directory: " + srcDir, e);
        }
        return matches.toArray(new String[0]);
    }

    private static boolean matchesAny(AntPathMatcher matcher, String path,
            List<String> patterns) {
        for (String pattern : patterns) {
            if (matcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

}

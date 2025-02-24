/*
Copyright (c) 2008 Sonatype, Inc. All rights reserved.

This program is licensed to you under the Apache License Version 2.0,
and you may not use this file except in compliance with the Apache License Version 2.0.
You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.

Unless required by applicable law or agreed to in writing,
software distributed under the Apache License Version 2.0 is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
*/

package org.codehaus.plexus.build.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.codehaus.plexus.build.BuildContext;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.Scanner;

public class TestIncrementalBuildContext implements BuildContext {

    private final File basedir;

    private final Set<File> refresh = new HashSet<>();

    private static final class TestScanner implements Scanner {
        private final File basedir;
        private final Set files;

        private TestScanner(File basedir, Set files) {
            this.basedir = basedir;
            this.files = files;
        }

        public void addDefaultExcludes() {}

        public String[] getIncludedDirectories() {
            return new String[0];
        }

        public String[] getIncludedFiles() {
            return (String[]) files.toArray(new String[0]);
        }

        public void scan() {}

        public void setExcludes(String[] excludes) {}

        public void setIncludes(String[] includes) {}

        public File getBasedir() {
            return basedir;
        }

        @Override
        public void setFilenameComparator(Comparator<String> filenameComparator) {}
    }

    private final Set changedFiles;

    private final Set deletedFiles;

    private final Map context;

    private final List warnings;

    private final List errors;

    public TestIncrementalBuildContext(File basedir, Set changedFiles, Map context) {
        this(basedir, changedFiles, new HashSet(), context);
    }

    public TestIncrementalBuildContext(File basedir, Set changedFiles, Set deletedFiles, Map context) {
        this(basedir, changedFiles, deletedFiles, context, new ArrayList(), new ArrayList());
    }

    public TestIncrementalBuildContext(
            File basedir, Set changedFiles, Set deletedFiles, Map context, List warnings, List errors) {
        this.basedir = basedir;
        this.changedFiles = changedFiles;
        this.deletedFiles = deletedFiles;
        this.context = context;
        this.warnings = warnings;
        this.errors = errors;
    }

    public boolean hasDelta(String relpath) {
        String basepath = basedir.getAbsolutePath();

        if (relpath.startsWith(basepath)) {
            relpath = relpath.substring(basepath.length() + 1);
        }

        return changedFiles.contains(relpath) || deletedFiles.contains(relpath);
    }

    public boolean hasDelta(List<String> relpaths) {
        for (Iterator<String> i = relpaths.iterator(); i.hasNext(); ) {
            String relpath = i.next();
            if (hasDelta(relpath)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasDelta(File file) {
        String relpath = getRelpath(file);
        return relpath == null || hasDelta(relpath);
    }

    private String getRelpath(File file) {
        try {
            String path = file.getCanonicalPath();
            String basepath = basedir.getCanonicalPath();
            if (path.startsWith(basepath) && !path.equals(basepath)) {
                return path.substring(basepath.length());
            } else {
                return null;
            }
        } catch (IOException e) {
            // this is a test implementation, we can be little loose here
            throw new IllegalArgumentException(e);
        }
    }

    public boolean isIncremental() {
        return true;
    }

    public Scanner newDeleteScanner(File basedir) {
        return new TestScanner(basedir, deletedFiles);
    }

    public OutputStream newFileOutputStream(File file) throws IOException {
        refresh(file);
        return new FileOutputStream(file);
    }

    public Scanner newScanner(final File basedir) {
        return new TestScanner(basedir, changedFiles);
    }

    public Scanner newScanner(File basedir, boolean ignoreDelta) {
        if (ignoreDelta) {
            DirectoryScanner directoryScanner = new DirectoryScanner();
            directoryScanner.setBasedir(basedir);
            return directoryScanner;
        }

        return newScanner(basedir);
    }

    public void refresh(File file) {
        refresh.add(file.getAbsoluteFile());
    }

    public Object getValue(String key) {
        return context.get(key);
    }

    public void setValue(String key, Object value) {
        context.put(key, value);
    }

    public Set getRefreshFiles() {
        return refresh;
    }

    public void addError(File file, int line, int column, String message, Throwable cause) {}

    public void addWarning(File file, int line, int column, String message, Throwable cause) {}

    public void addMessage(File file, int line, int column, String message, int severity, Throwable cause) {}

    public void removeMessages(File file) {}

    public boolean isUptodate(File target, File source) {
        return target != null
                && target.exists()
                && !hasDelta(target)
                && source != null
                && source.exists()
                && !hasDelta(source)
                && target.lastModified() > source.lastModified();
    }
}

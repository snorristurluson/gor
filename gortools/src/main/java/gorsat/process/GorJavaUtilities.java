/*
 *  BEGIN_COPYRIGHT
 *
 *  Copyright (C) 2011-2013 deCODE genetics Inc.
 *  Copyright (C) 2013-2019 WuXi NextCode Inc.
 *  All Rights Reserved.
 *
 *  GORpipe is free software: you can redistribute it and/or modify
 *  it under the terms of the AFFERO GNU General Public License as published by
 *  the Free Software Foundation.
 *
 *  GORpipe is distributed "AS-IS" AND WITHOUT ANY WARRANTY OF ANY KIND,
 *  INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY,
 *  NON-INFRINGEMENT, OR FITNESS FOR A PARTICULAR PURPOSE. See
 *  the AFFERO GNU General Public License for the complete license terms.
 *
 *  You should have received a copy of the AFFERO GNU General Public License
 *  along with GORpipe.  If not, see <http://www.gnu.org/licenses/agpl-3.0.html>
 *
 *  END_COPYRIGHT
 */

package gorsat.process;

import org.gorpipe.exceptions.GorSystemException;
import org.gorpipe.gor.session.GorSession;
import org.gorpipe.gor.driver.providers.db.DbScope;
import org.gorpipe.gor.model.DbSource;
import org.gorpipe.gor.model.Row;
import org.gorpipe.model.gor.iterators.RowSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Created by sigmar on 21/12/15.
 */
public class GorJavaUtilities {
    // -ff option is handled in java class GorOptions from model which depends on DynamicGorIterator.
    // DynamicGorIterator needs PipeInstance from gortools. Suggestion: merge gortools and model modules.

    private static final Logger log = LoggerFactory.getLogger(GorJavaUtilities.class);

    public static DecimalFormat fd3 = new DecimalFormat("#.###", DecimalFormatSymbols.getInstance(Locale.ROOT));
    public static double[] pArray = IntStream.range(0, 128).mapToDouble(qual -> 1.0 - (qual - 33) / 93.0).toArray();
    public static String[] prArray = Arrays.stream(pArray).mapToObj(p -> fd3.format(p)).toArray(String[]::new);
    public static PRPRValue prprFunction = new PRPRValue();
    public static PRPRPRValue prprprFunction = new PRPRPRValue();

    public static class PRPRValue {
        String[][] prprArray;
        char sep;

        PRPRValue() {
            this(';');
        }

        PRPRValue(char sep) {
            this.sep = sep;
        }

        public String get(int c1, int c2) {
            if (prprArray == null)
                prprArray = IntStream.range(0, 128).mapToObj(i -> IntStream.range(0, 128).mapToObj(k -> prArray[i] + sep + prArray[k]).toArray(String[]::new)).toArray(String[][]::new);
            return prprArray[c1][c2];
        }
    }

    public enum Phenotypes {
        BINARY,
        QUANTITATIVE,
        MIXED
    }

    public static Phenotypes getPhenotype(String pheno) throws IOException {
        Phenotypes pt = Phenotypes.BINARY;
        if (pheno!=null&&pheno.length()>0) {
            var p = Paths.get(pheno);
            if(Files.exists(p)) {
                String[] common = Files.lines(p).skip(1).map(s -> s.split("\t")).map(s -> Arrays.copyOfRange(s, 1, s.length)).reduce((r1, r2) -> {
                    for (int i = 0; i < r1.length; i++) {
                        try {
                            Integer.parseInt(r1[i]);
                            r1[i] = r2[i];
                        } catch (NumberFormatException e) {
                            // Keep non integers fore reduction
                        }
                    }
                    return r1;
                }).get();

                pt = null;
                for (int i = 0; i < common.length; i++) {
                    try {
                        Integer.parseInt(common[i]);
                        if (pt == null) pt = Phenotypes.BINARY;
                        else if (pt.equals(Phenotypes.QUANTITATIVE)) pt = Phenotypes.MIXED;
                    } catch (NumberFormatException e) {
                        if (pt == null) pt = Phenotypes.QUANTITATIVE;
                        else if (pt.equals(Phenotypes.BINARY)) pt = Phenotypes.MIXED;
                    }
                }
            }
        }
        return pt;
    }

    public static class PRPRPRValue extends GorJavaUtilities.PRPRValue {
        PRPRPRValue() {
            super();
        }

        @Override
        public String get(int c1, int c2) {
            if (prprArray == null)
                prprArray = IntStream.range(0, 128).mapToObj(i -> IntStream.range(0, 128).mapToObj(k -> fd3.format(Math.abs(1.0 - pArray[i] - pArray[k])) + sep + prArray[i] + sep + prArray[k]).toArray(String[]::new)).toArray(String[][]::new);
            return prprArray[c1][c2];
        }
    }

    public static class VCFValue extends PRPRValue {
        double threshold;

        public VCFValue(double threshold) {
            super(',');
            this.threshold = threshold;
        }

        private String getGT(int c1, int c2) {
            double p1 = pArray[c1];
            double p2 = pArray[c2];
            double p0 = Math.abs(1.0 - p1 - p2);

            String gp = prprprFunction.get(c1, c2);
            if (p0 > threshold) return "\t0/0:" + gp;
            else if (p1 > threshold) return "\t0/1:" + gp;
            else if (p2 > threshold) return "\t1/1:" + gp;
            else return "\t./.:" + gp;
        }

        @Override
        public String get(int c1, int c2) {
            if (c1 == ' ') return "\t./.:0" + sep + "0" + sep + "0";
            else if (prprArray == null)
                prprArray = IntStream.range(0, 128).mapToObj(i -> IntStream.range(0, 128).mapToObj(k -> getGT(i, k)).toArray(String[]::new)).toArray(String[][]::new);
            return prprArray[c1][c2];
        }
    }

    public static String clearHints(String query) {
        int i = query.indexOf("/*+");
        if(i != -1) {
            return query.substring(0,i) + query.substring(query.indexOf("*/",i+3)+2,query.length());
        }
        return query;
    }

    public static String[] splitResourceHints(String query, String validStart) {
        int i = query.indexOf("/*+");
        String[] ret = new String[] {query,null};
        if (i!=-1) {
            int e = query.indexOf("*/",i+3);
            String hints = query.substring(i+3,e).trim();
            List<String> resourceHints = new ArrayList<>();
            List<String> sqlHints = new ArrayList<>();
            String[] hintSplit = hints.split("[ ]+");
            Arrays.asList(hintSplit).forEach(hint -> {
                if (hint.startsWith(validStart)) {
                    resourceHints.add(hint);
                } else {
                    sqlHints.add(hint);
                }
            });
            if (resourceHints.size()>0) ret[1] = String.join(" ", resourceHints);
            if (sqlHints.size()==0) {
                query = query.substring(0,i) + query.substring(e+2);
            } else {
                query = query.substring(0,i+3) + String.join(" ", sqlHints) + query.substring(e);
            }
            ret[0] = query;
        }
        return ret;
    }

    public static String createMapString(Map<String,String> createMap) {
        return createMap.entrySet().stream().map(e -> "create "+e.getKey()+" = "+e.getValue()).collect(Collectors.joining("; ","",""));
    }

    public static List<Row> stream2RowList(Stream<Row> str) {
        return str.collect(Collectors.toList());
    }

    public static String seekReplacement(String myCommand, String chr, int start, int stop) {
        int sPos = myCommand.indexOf("#(S:");
        if (sPos != -1) {
            int sEnd = myCommand.indexOf(')', sPos + 1);

            String seek = "";
            if (chr != null) {
                seek = myCommand.substring(sPos + 4, sEnd).replace("chr", chr);
                int pos = seek.indexOf("pos-end");
                if (pos != -1) {
                    seek = seek.replace("pos", (start + 1) + "").replace("end", stop + "");
                } else if (seek.contains("pos")) {
                    pos = seek.indexOf("pos-");
                    if (stop == -1) {
                        seek = seek.replace("pos", start + "");
                    } else if (start == stop && pos != -1) {
                        seek = seek.replace("pos-", start + "");
                    } else {
                        seek = seek.replace("pos", start + "-") + stop;
                    }
                }
            }
            myCommand = myCommand.substring(0, sPos) + seek + myCommand.substring(sEnd + 1);
        }
        return myCommand;
    }

    public static String projectReplacement(String myCommand, GorSession session) throws IOException {
        String projectRoot = session.getProjectContext().getRealProjectRoot();
        String requestId = session.getRequestId();
        String securityContext = session.getProjectContext().getFileReader().getSecurityContext();
        return projectReplacement(myCommand, projectRoot, requestId, securityContext);
    }

    public static String projectReplacement(String myCommand, String projectRoot, String requestId, String securityContext) throws IOException {
        myCommand = projectDataReplacement(projectRoot, myCommand);
        myCommand = requestIdReplacement(requestId, myCommand);
        myCommand = projectIdReplacement(securityContext, myCommand);
        return myCommand;
    }

    public static String projectDataReplacement(String projectRoot, String myCommand) throws IOException {
        if (projectRoot != null && projectRoot.length() > 0) {
            Path rootPath = Paths.get(projectRoot);
            if (Files.exists(rootPath)) {
                Path rootRealPath = rootPath.toRealPath();
                myCommand = myCommand.replace("#{projectroot}", rootRealPath.toString());

                Path cachePath = rootRealPath.resolve("cache/result_cache");
                if (Files.exists(cachePath)) {
                    Path cacheRealPath = cachePath.toRealPath().getParent();
                    myCommand = myCommand.replace("#{projectcache}", cacheRealPath.toString());
                }

                Path dataPath = rootRealPath.resolve("source");
                if (Files.exists(dataPath)) {
                    Path dataRealPath = dataPath.toRealPath().getParent();
                    myCommand = myCommand.replace("#{projectdata}", dataRealPath.toString());
                }
            }
        }
        return myCommand;
    }

    private static String requestIdReplacement(String requestId, String myCommand) {
        if (requestId != null) myCommand = myCommand.replace("#{requestid}", requestId);
        return myCommand;

    }

    static String projectIdReplacement(String securityContext, String myCommand) {
        if (securityContext != null) {
            List<DbScope> dbScopes = DbScope.parse(securityContext);
            Integer projectIdValue = null;
            for (DbScope dbScope : dbScopes) {
                if (dbScope.getColumn().equals("project_id")) {
                    projectIdValue = (Integer) dbScope.getValue();
                }
            }
            if (projectIdValue != null) myCommand = myCommand.replace("#{projectid}", projectIdValue.toString());
        }
        return myCommand;
    }

    public static RowSource getDbIteratorSource(String sqlQuery, boolean gortable, final String source, boolean scoping) {
        Supplier<Stream<String>> streamSupplier = () -> DbSource.getDBLinkStream("//db:" + sqlQuery, new Object[]{}, source);
        gorsat.Iterators.IteratorSource its;
        its = gortable ? new GorStreamIterator(streamSupplier, scoping) : new NorStreamIterator(streamSupplier);
        return new gorsat.Iterators.SingleIteratorSource(its, "dbit");
    }


    /**
     * Helper method for wrapping Object[] iterator to Stream.
     *
     * @param iterator
     * @return Stream where iterator elements are represented as string joined with tab delimiter
     */
    public static Stream<String> wrapObjectArrayIterator(Iterator<Object[]> iterator) {
        Iterator<String> theIterator = new Iterator<String>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public String next() {
                Object[] data = iterator.next();
                return Arrays.stream(data).map(GorJavaUtilities::nullSafeToString).collect(Collectors.joining("\t"));
            }
        };
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(theIterator, Spliterator.IMMUTABLE), false);
    }

    private static String nullSafeToString(Object obj) {
        return (obj == null) ? "" : obj.toString();
    }


    public static class CmdParams {
        CmdParams(String[] cmdParams, String command) {
            this.cmdParams = cmdParams;
            this.command = command;
        }

        String[] cmdParams;
        String command;

        public String getAliasName() {
            return cmdParams[0];
        }

        public String getCmdPath() {
            return command.split("[ ]+")[0];
        }

        public String getCommand() {
            return command;
        }

        public Optional<String> getType() {
            return Arrays.stream(cmdParams).skip(1).filter(e -> e.startsWith("-s")).map(e -> e.substring(2)).findFirst();
        }

        public boolean isNor() {
            return Arrays.stream(cmdParams).skip(1).anyMatch(e -> e.equals("-n"));
        }

        public boolean isFormula() {
            return Arrays.stream(cmdParams).skip(1).anyMatch(e -> e.equals("-c"));
        }

        public boolean skipHeader() {
            return Arrays.stream(cmdParams).skip(1).anyMatch(e -> e.equals("-h"));
        }

        public boolean useProcessMethod() {
            return Arrays.stream(cmdParams).skip(1).anyMatch(e -> e.equals("-p"));
        }

        public boolean useHttpServer() {
            return Arrays.stream(cmdParams).skip(1).anyMatch(e -> e.equals("-u"));
        }

        public Optional<String> skipLines() {
            return Arrays.stream(cmdParams).skip(1).filter(p -> p.startsWith("-s")).findFirst().map(p -> p.substring(2));
        }

        public boolean allowError() {
            return Arrays.stream(cmdParams).skip(1).anyMatch(e -> e.equals("-e"));
        }
    }

    public static Map<String, CmdParams> readWhiteList(Path cmdConfigPath) {
        Map<String, CmdParams> allowedCmds = new HashMap<>();
        try {
            if (cmdConfigPath != null && Files.exists(cmdConfigPath)) {
                List<String> lines = Files.readAllLines(cmdConfigPath);
                for (String line : lines) {
                    String[] split = line.split("\t");
                    if (split.length == 1) {
                        int i = line.indexOf('[');
                        split = i > 0 ? new String[]{line.substring(0, i).trim(), line.substring(i)} : null;
                    }

                    if (split != null) {
                        String cmd = split[1].substring(1, split[1].length() - 1);
                        CmdParams cmdParams = new CmdParams(split[0].split("[ ]+"), cmd);
                        allowedCmds.put(cmdParams.getAliasName(), cmdParams);
                    }
                }
            }
        } catch(IOException ioe) {
            throw new GorSystemException("Failed to load white listed commands.", ioe);
        }
        return allowedCmds;
    }

    public static Map<String, CmdParams> readWhiteList(String cmdConfigFile) throws IOException {
        if (cmdConfigFile != null) {
            Path p = Paths.get(cmdConfigFile);
            return readWhiteList(p);
        }
        return new HashMap<>();
    }

    /**
     * Helper method to resolve whitelist file path (from config file) to full path.
     * <p>
     * If whitelist path is resolved as follows:
     * 1.  If input is absolute or empty then it is returned.
     * 2.  Search relative for the project directory.
     * 3.  If nothing is found we return the input.
     *
     * @param whiteListFile white list file path, can not be null or emtpy.
     * @param projectRoot   project root for the current project.
     * @return fully resolved path to the whitelist file.
     */
    public static Path resolveWhiteListFilePath(String whiteListFile, Path projectRoot) {
        if (whiteListFile == null || whiteListFile.isEmpty()) {
            throw new GorSystemException("Can not resolve empty white list file path", null);
        }

        Path whiteListPath = Paths.get(whiteListFile);

        if (whiteListPath.isAbsolute()) {
            return whiteListPath;
        }

        if (projectRoot != null) {
            return projectRoot.resolve(whiteListFile);
        }

        log.warn("Whitelist file {} is relative but no project root is defined!", whiteListFile);

        /*  Skipping searching the config path for now.  It we put this back in we should change the whitelist path to
        be relative to the "config" folder, i.e. for project folder case that would be <project root>/config instead
        of just <project root>.

        String configRootPath = ConfigManager.getConfigRootPath();
        if (configRootPath != null && !configRootPath.isEmpty()) {
            Path installWhiteListPath = Paths.get(ConfigManager.getConfigRootPath()).resolve(whiteListFile);
            if (Files.exists(installWhiteListPath)) {
                return installWhiteListPath;
            }
        }
        */

        return whiteListPath;
    }

    public static Optional<String> getIgnoreCase(Collection<String> c, String str) {
        return c.stream().filter(s -> s.equalsIgnoreCase(str)).findFirst();
    }

    public static String[] toUppercase(Collection<String> c) {
        return c.stream().map(String::toUpperCase).toArray(String[]::new);
    }

    public static String[] mergeArrays(String[] array1, String[] array2) {
        return Stream.concat(Arrays.stream(array1), Arrays.stream(array2)).toArray(String[]::new);
    }
}

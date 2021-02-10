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

package org.gorpipe.gorshell;

import com.fasterxml.jackson.databind.ObjectMapper;
import gorsat.Commands.InputSourceInfo;
import gorsat.Script.MacroInfo;
import gorsat.process.GorInputSources;
import gorsat.process.GorPipeCommands;
import gorsat.process.GorPipeMacros;
import gorsat.process.PipeInstance;
import org.apache.commons.io.FileUtils;
import org.gorpipe.logging.GorLogbackUtil;
import org.gorpipe.gor.util.ConfigUtil;
import org.jline.builtins.Completers;
import org.jline.reader.*;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.completer.AggregateCompleter;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import picocli.CommandLine;
import picocli.shell.jline3.PicocliCommands;
import scala.Option;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class GorShell {

    Terminal terminal;
    LineReader lineReader;
    private CommandLine commandLine;
    private QueryRunner runner = null;
    private String input = "";
    private boolean exit = false;
    public GorShellSessionFactory sessionFactory;

    public static class Settings {

        public boolean timingEnabled = false;
        public boolean fileCacheEnabled = true;
        public boolean requestStatsEnabled = false;
        public boolean displayResults = true;
        public String configFile = "";
        public Map<String, String> createStatements = new HashMap<>();
        public Map<String, String> defStatements = new HashMap<>();
    }

    private Settings settings = new Settings();

    public GorShell(GorShellSessionFactory sessionFactory) {
        try {
            terminal = TerminalBuilder.builder().jansi(false).build();
        } catch (IOException e) {
            exit();
        }

        DefaultParser parser = new DefaultParser();
        parser.setEscapeChars(null);

        Commands commands = new Commands(this);

        commandLine = new CommandLine(commands);
        commandLine.setCaseInsensitiveEnumValuesAllowed(true);
        commandLine.setUnmatchedOptionsArePositionalParams(true);

        Completer completer = getCompleter(commandLine);

        lineReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .parser(parser)
                .completer(completer)
                .build();

        String startupMsg = String.format("GOR shell - %s", GorShell.class.getPackage().getImplementationVersion());
        terminal.writer().println(startupMsg);

        String historyFile = System.getProperty("user.home") + File.separator + ".gorshell_history";
        lineReader.setVariable(LineReader.HISTORY_FILE, historyFile);
        lineReader.setOpt(LineReader.Option.CASE_INSENSITIVE);

        this.sessionFactory = sessionFactory;
        this.sessionFactory.setFileCacheEnabled(settings.fileCacheEnabled);
        this.sessionFactory.setRequestStatsEnabled(settings.requestStatsEnabled);
        this.sessionFactory.setConfigFile(settings.configFile);
    }

    public static void main(String[] args) throws IOException {
        GorLogbackUtil.initLog("gorshell");

        ConfigUtil.loadConfig("gor");
        PipeInstance.initialize();

        String cwd = System.getProperty("user.dir");
        GorShell gorShell = new GorShell(new GorShellSessionFactory(cwd));
        gorShell.run();
    }

    void exit() {
        exit = true;
    }

    public void setConfigFile(String configFile) {
        settings.configFile = configFile;
    }

    void setTimingEnabled(boolean enableTiming) {
        settings.timingEnabled = enableTiming;
    }

    void setFileCacheEnabled(boolean fileCacheEnabled) {
        settings.fileCacheEnabled = fileCacheEnabled;
    }

    void setRequestStatsEnabled(boolean requestStatsEnabled) {
        settings.requestStatsEnabled = requestStatsEnabled;
    }

    void setDisplayResults(boolean displayResults) {
        settings.displayResults = displayResults;
    }

    void create(String name, String stmt) {
        if (!stmt.equals("")) {
            settings.createStatements.put(name, stmt);
        } else {
            settings.createStatements.remove(name);
        }
    }

    void def(String name, String stmt) {
        if (!stmt.equals("")) {
            settings.defStatements.put(name, stmt);
        } else {
            settings.defStatements.remove(name);
        }
    }

    void script(String scriptName) {
        File scriptFile = new File(scriptName).getAbsoluteFile();
        String query = null;
        if (scriptFile.exists()) {
            try {
                query = FileUtils.readFileToString(scriptFile, Charset.defaultCharset());
            } catch (IOException e) {
                reportException(e);
            }
        } else {
            lineReader.printAbove("File does not exist");
        }
        if (query != null) {
            runQuery(query);
        }
    }

    void showCreates() {
        if (settings.createStatements.isEmpty()) {
            lineReader.printAbove("No create statements defined");
        } else {
            String msg = String.format("%d create statements:", settings.createStatements.size());
            lineReader.printAbove(msg);
        }
        for (String name : settings.createStatements.keySet()) {
            lineReader.printAbove(name);
        }
    }

    void showCreate(String name) {
        String statement = settings.createStatements.getOrDefault(name, null);
        if (statement == null) {
            String msg = String.format("%s is not a defined create statement", name);
            lineReader.printAbove(msg);
        } else {
            lineReader.printAbove(statement);
        }
    }

    void showDefs() {
        if (settings.defStatements.isEmpty()) {
            lineReader.printAbove("No def statements");
        } else {
            String msg = String.format("%d def statements:", settings.defStatements.size());
            lineReader.printAbove(msg);
        }
        for (String name : settings.defStatements.keySet()) {
            lineReader.printAbove(name);
        }
    }

    void showDef(String name) {
        String statement = settings.defStatements.getOrDefault(name, null);
        if (statement == null) {
            String msg = String.format("%s is not a def statement", name);
            lineReader.printAbove(msg);
        } else {
            lineReader.printAbove(statement);
        }
    }

    void showScript() {
        StringBuilder script = getScript();
        lineReader.printAbove(script.toString());
    }

    void saveScript(String scriptFile) {
        StringBuilder script = getScript();
        File file = new File(scriptFile);
        try {
            FileUtils.writeStringToFile(file, script.toString(), Charset.defaultCharset());
        } catch (IOException e) {
            reportException(e);
        }
    }

    public void clearScript() {
        settings.createStatements.clear();
        settings.defStatements.clear();
    }

    void reportException(Exception e) {
        lineReader.printAbove(e.toString());
    }

    public void run() throws IOException {
        loadSettings();
        lineReader.getHistory().load();

        mainLoop();

        lineReader.getHistory().save();
        saveSettings();
    }

    private void saveSettings() {
        try {
            File file = getSettingsFile();
            String json = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(settings);
            FileUtils.writeStringToFile(file, json, Charset.defaultCharset());
        } catch (IOException e) {
            // Ignore
        }
    }

    private void loadSettings() {
        try {
            File file = getSettingsFile();
            String json = FileUtils.readFileToString(file, Charset.defaultCharset());
            settings = new ObjectMapper().readValue(json, Settings.class);
        } catch (IOException e) {
            settings = new Settings();
        }
    }

    private File getSettingsFile() {
        return new File(System.getProperty("user.home") + File.separator + ".gorshell_settings");
    }

    private void mainLoop() {
        while (!exit) {
            try {
                String prompt = "> ";
                if (runner != null) {
                    prompt = "(query running)> ";
                }
                input = lineReader.readLine(prompt);
                if (!input.equals("")) {
                    handleInput();
                }
            } catch (UserInterruptException e) {
                if (e.getPartialLine().equals("")) {
                    handleInterrupt();
                }
            } catch (EndOfFileException e) {
                break;
            } catch (Exception e) {
                reportException(e);
            }
        }
    }

    private void handleInput() {
        ParsedLine parsedLine = lineReader.getParsedLine();
        String cmd = parsedLine.words().get(0);
        InputSourceInfo info = GorInputSources.getInfo(cmd);
        Option<MacroInfo> macroInfo = GorPipeMacros.getInfo(cmd);
        if (info != null || macroInfo.isDefined()) {
            handleQuery();
        } else {
            String[] arguments = input.split(" ");
            commandLine.execute(arguments);
        }
    }

    private Completer getCompleter(CommandLine commandLine) {
        PicocliCommands picocliCommands = new PicocliCommands(commandLine);

        var commandsCompleter = picocliCommands.compileCompleters();
        commandsCompleter.compile();

        StringsCompleter inputSourcesCompleter = new StringsCompleter(GorInputSources.getInputSources());
        StringsCompleter pipeCommandsCompleter = new StringsCompleter(GorPipeCommands.getGorCommands());
        return new AggregateCompleter(
                inputSourcesCompleter,
                pipeCommandsCompleter,
                new Completers.FileNameCompleter(),
                commandsCompleter);
    }

    private void handleInterrupt() {
        if (runner != null) {
            if (runner.isDone()) {
                lineReader.printAbove("Query finished");
                runner = null;
            } else if (!runner.isCancelled()) {
                lineReader.printAbove("Cancelling query");
                runner.cancel();
                try {
                    runner.join(3000);
                    runner = null;
                } catch (InterruptedException e) {
                    runner.interrupt();
                }
            } else if (runner.isAlive()) {
                lineReader.printAbove("Killing query");
                runner.interrupt();
                runner = null;
            } else {
                runner = null;
            }
        } else {
            lineReader.printAbove("Ctrl-D or 'exit' to exit");
        }
    }

    private void handleQuery() {
        StringBuilder script = getScript();
        script.append(input);
        runQuery(script.toString());
    }

    private StringBuilder getScript() {
        StringBuilder script = new StringBuilder();
        addStatements(script, "def", settings.defStatements);
        addStatements(script, "create", settings.createStatements);
        return script;
    }

    private void runQuery(String script) {
        resetRunner();
        runner = new QueryRunner(script, lineReader, Thread.currentThread(), sessionFactory);
        runner.setTimingEnabled(settings.timingEnabled);
        runner.setFileCacheEnabled(settings.fileCacheEnabled);
        runner.setRequestStatsEnabled(settings.requestStatsEnabled);
        runner.setDisplayResults(settings.displayResults);
        runner.setConfigFile(settings.configFile);
        runner.start();
    }

    private void resetRunner() {
        if (runner != null && runner.isAlive()) {
            runner.interrupt();
            runner = null;
        }
    }

    private void addStatements(StringBuilder script, String prefix, Map<String, String> statements) {
        for (Map.Entry<String, String> entry : statements.entrySet()) {
            script.append(prefix);
            script.append(" ");
            script.append(entry.getKey());
            script.append(" = ");
            script.append(entry.getValue());
            script.append(";\n");
        }
    }
}

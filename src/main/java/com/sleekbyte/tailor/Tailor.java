package com.sleekbyte.tailor;

import com.sleekbyte.tailor.antlr.SwiftLexer;
import com.sleekbyte.tailor.antlr.SwiftParser;
import com.sleekbyte.tailor.common.MaxLengths;
import com.sleekbyte.tailor.common.Severity;
import com.sleekbyte.tailor.listeners.FileListener;
import com.sleekbyte.tailor.listeners.MainListener;
import com.sleekbyte.tailor.output.Printer;
import com.sleekbyte.tailor.utils.ArgumentParser;
import com.sleekbyte.tailor.utils.ArgumentParserException;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Performs static analysis on Swift source files.
 */
public class Tailor {

    private static final int EXIT_SUCCESS = 0;
    private static final int EXIT_FAILURE = 1;
    private static final int NUM_THREADS = 2;
    private static ArgumentParser argumentParser = new ArgumentParser();

    public static void printMissingSourceFileError() {
        System.err.println("Swift source file must be provided.");
        argumentParser.printHelp();
        System.exit(EXIT_FAILURE);
    }

    public static void printSourceAnalysisError(Exception ex) {
        System.err.println("Source file analysis failed. Reason: " + ex.getMessage());
        System.exit(EXIT_FAILURE);
    }

    public static void checkForSRCROOT(ArrayList<String> pathnames) {
        String srcRoot = System.getenv("SRCROOT");
        if (srcRoot == null || srcRoot.equals("")) {
            printMissingSourceFileError();
        }
        pathnames = new ArrayList<>();
        pathnames.add(srcRoot);
    }

    public static void analyzeFile(String filename, HashMap<String, Printer> printers, Severity maxSeverity, MaxLengths maxLengths) {
        try {
            File inputFile = new File(filename);
            FileInputStream inputStream = new FileInputStream(inputFile);
            SwiftLexer lexer = new SwiftLexer(new ANTLRInputStream(inputStream));
            CommonTokenStream stream = new CommonTokenStream(lexer);
            SwiftParser swiftParser = new SwiftParser(stream);
            SwiftParser.TopLevelContext tree = swiftParser.topLevel();

            Printer printer = new Printer(inputFile, maxSeverity);
            printers.put(filename, printer);
            MainListener listener = new MainListener(printer, maxLengths);
            ParseTreeWalker walker = new ParseTreeWalker();
            walker.walk(listener, tree);
            FileListener fileListener = new FileListener(printer, inputFile, maxLengths);
            fileListener.verify();
        }
        catch (IOException ex) {
            printSourceAnalysisError(ex);
        }
    }

    public static Set<String> getSwiftSourceFiles(ArrayList<String> pathnames) throws IOException {
        Set<String> filenames = new TreeSet<>();
        for (String pathname: pathnames) {
            if (pathname.endsWith(".swift")) {
                filenames.add(pathname);
            }
            if (new File(pathname).isDirectory()) {
                Files.walk(Paths.get(pathname))
                    .filter(path -> path.toString().endsWith(".swift"))
                    .forEach(path -> filenames.add(path.toString()));
            }
        }
        return filenames;
    }

    /**
     * Main runner for Tailor.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {

        try {

            long startTime = System.currentTimeMillis();
            CommandLine cmd = argumentParser.parseCommandLine(args);
            if (argumentParser.shouldPrintHelp()) {
                argumentParser.printHelp();
                System.exit(EXIT_SUCCESS);
            }
            long argumentParseTime = System.currentTimeMillis() - startTime;
            System.out.format("Argument Parse Time: %.3f sec\n", ((double)argumentParseTime / 1000));

            startTime = System.currentTimeMillis();
            ArrayList<String> pathnames = new ArrayList<>();
            if (cmd.getArgs().length >= 1) {
                pathnames = new ArrayList<>(Arrays.asList(cmd.getArgs()));
            }
            if (pathnames.size() == 0) {
                checkForSRCROOT(pathnames);
            }
            Set<String> filenames = getSwiftSourceFiles(pathnames);
            if (filenames.size() == 0) {
                printMissingSourceFileError();
            }
            long fileFindingTime = System.currentTimeMillis() - startTime;
            System.out.format("File Finding Time: %.3f sec\n", ((double)fileFindingTime / 1000));


            MaxLengths maxLengths = argumentParser.parseMaxLengths();
            Severity maxSeverity = argumentParser.getMaxSeverity();

            startTime = System.currentTimeMillis();
            ExecutorService pool = Executors.newFixedThreadPool(NUM_THREADS);
            HashMap<String, Printer> printers = new HashMap<>();
            filenames.parallelStream()
                .forEach(filename -> pool.submit(
                    () -> analyzeFile(filename, printers, maxSeverity, maxLengths)));
            long threadsCreationTime = System.currentTimeMillis() - startTime;
            System.out.format("Threads Creation Time: %.3f sec\n", ((double) threadsCreationTime / 1000));
            pool.shutdown();
            pool.awaitTermination(1, TimeUnit.HOURS);
            long analysisTime = System.currentTimeMillis() - startTime;
            System.out.format("Analysis Time: %.3f sec\n", ((double)analysisTime / 1000));

            startTime = System.currentTimeMillis();
            filenames.stream().forEach(filename -> printers.get(filename).close());
            long printTime = System.currentTimeMillis() - startTime;
            System.out.format("Printing Time: %.3f sec\n", ((double)printTime / 1000));

        } catch (ParseException | IOException | InterruptedException e) {
            printSourceAnalysisError(e);
        } catch (ArgumentParserException e) {
            System.err.println(e.getMessage());
            argumentParser.printHelp();
            System.exit(EXIT_FAILURE);
        }
    }

}

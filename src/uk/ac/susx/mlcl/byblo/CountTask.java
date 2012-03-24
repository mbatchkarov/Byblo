/*
 * Copyright (c) 2010-2012, University of Sussex
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions are met:
 * 
 *  * Redistributions of source code must retain the above copyright notice, 
 *    this list of conditions and the following disclaimer.
 * 
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 *  * Neither the name of the University of Sussex nor the names of its 
 *    contributors may be used to endorse or promote products derived from this 
 *    software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE 
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE 
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE 
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR 
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF 
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
 * POSSIBILITY OF SUCH DAMAGE.
 */
package uk.ac.susx.mlcl.byblo;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import uk.ac.susx.mlcl.byblo.io.*;
import uk.ac.susx.mlcl.lib.*;
import uk.ac.susx.mlcl.lib.io.Files;
import uk.ac.susx.mlcl.lib.io.IOUtil;
import uk.ac.susx.mlcl.lib.io.TSVSink;
import uk.ac.susx.mlcl.lib.io.TSVSource;
import uk.ac.susx.mlcl.lib.tasks.AbstractCommandTask;
import uk.ac.susx.mlcl.lib.tasks.InputFileValidator;
import uk.ac.susx.mlcl.lib.tasks.OutputFileValidator;

/**
 * <p>Read in a raw feature instances file, to produce three frequency files:
 * entries, features, and entry-feature pairs.</p>
 *
 * @author Hamish Morgan &lt;hamish.morgan@sussex.ac.uk%gt;
 */
@Parameters(commandDescription = "Read in a raw feature instances file, to produce three "
+ "frequency files: entries, contexts, and features.")
public class CountTask extends AbstractCommandTask implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Log LOG = LogFactory.getLog(CountTask.class);

    /**
     * Number of records to read or write between progress updates.
     */
    private static final int PROGRESS_INTERVAL = 1000000;

    @Parameter(names = {"-i", "--input"},
               required = true,
               description = "Source instances file",
               validateWith = InputFileValidator.class)
    private File inputFile;

    @Parameter(names = {"-oef", "--output-entry-features"},
               required = true,
               description = "Entry-feature-pair frequencies destination file",
               validateWith = OutputFileValidator.class)
    private File entryFeaturesFile = null;

    @Parameter(names = {"-oe", "--output-entries"},
               required = true,
               description = "Entry frequencies destination file",
               validateWith = OutputFileValidator.class)
    private File entriesFile = null;

    @Parameter(names = {"-of", "--output-features"},
               required = true,
               description = "Feature frequencies destination file.",
               validateWith = OutputFileValidator.class)
    private File featuresFile = null;

    @Parameter(names = {"-c", "--charset"},
               description = "Character encoding to use for input and output.")
    private Charset charset = Files.DEFAULT_CHARSET;

    @Parameter(names = {"-pe", "--preindexed-entries"},
               description = "Whether entries in the input events file are already indexed.")
    private boolean preindexedEntries = false;

    @Parameter(names = {"-pf", "--preindexed-features"},
               description = "Whether features in the input events file are already indexed.")
    private boolean preindexedFeatures = false;

    private Comparator<Weighted<Token>> entryOrder =
            Weighted.recordOrder(Token.indexOrder());

    private Comparator<Weighted<Token>> featureOrder =
            Weighted.recordOrder(Token.indexOrder());

    private Comparator<Weighted<TokenPair>> eventOrder =
            Weighted.recordOrder(TokenPair.indexOrder());

    /**
     * Dependency injection constructor with all fields parameterised.
     *
     * @param instancesFile input file containing entry/context instances
     * @param entryFeaturesFile output file for entry/context/frequency triples
     * @param entriesFile output file for entry/frequency pairs
     * @param featuresFile output file for context/frequency pairs
     * @param preindexedEntries
     * @param preindexedFeatures
     * @param entryIndexFile
     * @param featureIndexFile
     * @param charset character set to use for all file I/O
     * @throws NullPointerException if any argument is null
     */
    public CountTask(final File instancesFile, final File entryFeaturesFile,
                     final File entriesFile, final File featuresFile,
                     final boolean preindexedEntries,
                     final boolean preindexedFeatures,
                     final Charset charset) throws NullPointerException {
        this(instancesFile, entryFeaturesFile, entriesFile, featuresFile);
        setCharset(charset);
        setPreindexedEntries(preindexedEntries);
        setPreindexedFeatures(preindexedFeatures);
    }

    public CountTask(final File instancesFile, final File entryFeaturesFile,
                     final File entriesFile, final File featuresFile,
                     final Charset charset) throws NullPointerException {
        this(instancesFile, entryFeaturesFile, entriesFile, featuresFile);
        setCharset(charset);
    }

    /**
     * Minimal parameterisation constructor, with all fields that must be set
     * for the task to be functional. Character set will be set to software
     * default from {@link IOUtil#DEFAULT_CHARSET}.
     *
     * @param instancesFile input file containing entry/context instances
     * @param entryFeaturesFile output file for entry/context/frequency triples
     * @param entriesFile output file for entry/frequency pairs
     * @param featuresFile output file for context/frequency pairs
     * @throws NullPointerException if any argument is null
     */
    public CountTask(
            final File instancesFile, final File entryFeaturesFile,
            final File entriesFile, final File featuresFile)
            throws NullPointerException {
        setInstancesFile(instancesFile);
        setEntryFeaturesFile(entryFeaturesFile);
        setEntriesFile(entriesFile);
        setFeaturesFile(featuresFile);
    }

    /**
     * Default constructor used by serialisation and JCommander instantiation.
     * All files will initially be set to null. Character set will be set to
     * software default from {@link IOUtil#DEFAULT_CHARSET}.
     */
    public CountTask() {
    }

    private Enumerator<String> index1 = null;

    private Enumerator<String> index2 = null;

    public Enumerator<String> getIndex1() {
        if (index1 == null)
            index1 = Enumerators.newDefaultStringEnumerator();
        return index1;
    }

    public void setIndex1(Enumerator<String> entryIndex) {
        this.index1 = entryIndex;
    }

    public Enumerator<String> getIndex2() {
        if (index2 == null)
            index2 = Enumerators.newDefaultStringEnumerator();
        return index2;
    }

    public void setIndex2(Enumerator<String> featureIndex) {
        this.index2 = featureIndex;
    }

    @Override
    protected void initialiseTask() throws Exception {
        checkState();
    }

    @Override
    protected void runTask() throws Exception {
        if (LOG.isInfoEnabled()) {
            LOG.info("Running memory count on \"" + inputFile + "\".");
        }

        final Function<String, Integer> entryDecoder;
        final Function<String, Integer> featureDecoder;
        final Function<Integer, String> entryEncoder;
        final Function<Integer, String> featureEncoder;

        if (!preindexedEntries) {
            entryDecoder = Token.stringDecoder(getIndex1());
            entryEncoder = Token.stringEncoder(getIndex1());
            entryOrder = Weighted.recordOrder(Token.stringOrder(entryEncoder));
        } else {
            entryDecoder = Token.enumeratedDecoder();
            entryEncoder = Token.enumeratedEncoder();
            entryOrder = Weighted.recordOrder(Token.indexOrder());
        }

        if (!preindexedFeatures) {
            featureDecoder = Token.stringDecoder(getIndex2());
            featureEncoder = Token.stringEncoder(getIndex2());
            featureOrder = Weighted.recordOrder(
                    Token.stringOrder(featureEncoder));
        } else {
            featureDecoder = Token.enumeratedDecoder();
            featureEncoder = Token.enumeratedEncoder();
            featureOrder = Weighted.recordOrder(Token.indexOrder());
        }

        if (!preindexedEntries || !preindexedEntries) {
            eventOrder = Weighted.recordOrder(TokenPair.stringOrder(
                    entryEncoder, featureEncoder));
        } else {
            eventOrder = Weighted.recordOrder(TokenPair.indexOrder());
        }

        final Object2IntMap<TokenPair> entryFeatureFreq =
                new Object2IntOpenHashMap<TokenPair>();
        entryFeatureFreq.defaultReturnValue(0);

        final Int2IntMap featureFreq = new Int2IntOpenHashMap();
        featureFreq.defaultReturnValue(0);

        final Int2IntMap entryFreq = new Int2IntOpenHashMap();
        entryFreq.defaultReturnValue(0);

        countEvents(entryFreq, featureFreq,
                    entryFeatureFreq, entryDecoder, featureDecoder);

        writeEntries(entryFreq, entryEncoder);
        writeFeatures(featureFreq, featureEncoder);
        writeEvents(entryFeatureFreq, entryEncoder, featureEncoder);

        if (LOG.isInfoEnabled()) {
            LOG.info("Completed memory count.");
        }
    }

    @Override
    protected void finaliseTask() throws Exception {
    }

    private void countEvents(
            final Int2IntMap entryFreq,
            final Int2IntMap featureFreq,
            final Object2IntMap<? super TokenPair> entryFeatureFreq,
            final Function<String, Integer> entryDecoder,
            final Function<String, Integer> featureDecoder)
            throws IOException {

        final TokenPairSource instanceSource = new TokenPairSource(
                new TSVSource(inputFile, charset),
                entryDecoder, featureDecoder);

        if (!instanceSource.hasNext() && LOG.isWarnEnabled()) {
            LOG.warn("Events file is empty.");
        }

        long i = 0;
        while (instanceSource.hasNext()) {
            final TokenPair instance;
            try {
                instance = instanceSource.read();
            } catch (SingletonRecordException ex) {
                LOG.warn("Badly formed input data: " + ex.getMessage());
                continue;
            }

            final int entry_id = instance.id1();
            final int feature_id = instance.id2();

            entryFreq.put(entry_id, entryFreq.get(entry_id) + 1);
            featureFreq.put(feature_id, featureFreq.get(feature_id) + 1);
            entryFeatureFreq.put(instance, entryFeatureFreq.getInt(instance) + 1);

            if ((++i % PROGRESS_INTERVAL == 0 || !instanceSource.hasNext())
                    && LOG.isInfoEnabled()) {
                LOG.info("Read " + i + " events. Found "
                        + entryFreq.size() + " entries, " + featureFreq.size()
                        + " features, and " + entryFeatureFreq.size()
                        + " entry-features. (" + (int) instanceSource.
                        percentRead()
                        + "% complete)");
                LOG.debug(MiscUtil.memoryInfoString());
            }
        }
    }

    private static final Comparator<Int2IntMap.Entry> TOKEN_ID_ORDER = new Comparator<Int2IntMap.Entry>() {

        @Override
        public int compare(Int2IntMap.Entry o1, Int2IntMap.Entry o2) {
            return o1.getIntKey() - o2.getIntKey();
        }
    };

    private List<Weighted<Token>> mapToWeightedTokens(Int2IntMap map) {
        List<Weighted<Token>> out = new ArrayList<Weighted<Token>>(map.size());
        for (Int2IntMap.Entry e : map.int2IntEntrySet()) {
            out.add(new Weighted<Token>(
                    new Token(e.getIntKey()), e.getIntValue()));
        }
        return out;
    }

    private List<Weighted<TokenPair>> mapToWeightedTokenPairs(
            Object2IntMap<TokenPair> map) {
        List<Weighted<TokenPair>> out = new ArrayList<Weighted<TokenPair>>(map.
                size());
        for (Object2IntMap.Entry<TokenPair> e : map.object2IntEntrySet()) {
            out.add(new Weighted<TokenPair>(
                    e.getKey(), e.getIntValue()));
        }
        return out;
    }

    private void writeEntries(
            final Int2IntMap entryFreq,
            final Function<Integer, String> entryEncoder)
            throws IOException {

        if (LOG.isDebugEnabled())
            LOG.debug("Sorting entries frequency data.");


        List<Weighted<Token>> tokens = mapToWeightedTokens(entryFreq);
        Collections.sort(tokens, entryOrder);

        if (LOG.isDebugEnabled())
            LOG.debug(
                    "Writing entries frequency data to file \"" + entriesFile + "\".");

        if (entriesFile.exists() && LOG.isWarnEnabled())
            LOG.warn("The entries file already exists and will be overwritten.");

        WeightedTokenSink entrySink = null;
        final int n = tokens.size();
        try {
            entrySink = new WeightedTokenSink(
                    new TSVSink(entriesFile, charset), entryEncoder);
            int i = 0;
            for (final Weighted<Token> tok : tokens) {
                entrySink.write(tok);
                if ((++i % PROGRESS_INTERVAL == 0 || i == n) && LOG.
                        isInfoEnabled()) {
                    LOG.info("Wrote " + i + "/" + n + " entries. ("
                            + (int) (i * 100d / n) + "% complete)");
                }
            }
        } finally {
            if (entrySink != null) {
                entrySink.flush();
                entrySink.close();
            }
        }
    }

    private void writeFeatures(
            final Int2IntMap featureFreq,
            final Function<Integer, String> featureEncoder)
            throws IOException {

        if (LOG.isDebugEnabled()) {
            LOG.debug("Sorting context frequency data.");
        }

        List<Weighted<Token>> tokens = mapToWeightedTokens(featureFreq);
        Collections.sort(tokens, featureOrder);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Writing context frequency data to " + featuresFile + ".");
        }
        if (featuresFile.exists() && LOG.isWarnEnabled()) {
            LOG.warn("The contexts file already exists and will be overwritten.");
        }

        WeightedTokenSink featureSink = null;
        final int n = tokens.size();
        try {
            featureSink = new WeightedTokenSink(
                    new TSVSink(featuresFile, charset), featureEncoder);
            int i = 0;
            for (final Weighted<Token> tok : tokens) {
                featureSink.write(tok);
                if ((++i % PROGRESS_INTERVAL == 0 || i == n) && LOG.
                        isInfoEnabled()) {
                    LOG.info("Wrote " + i + "/" + n + " contexts. ("
                            + (int) (i * 100d / n) + "% complete)");
                }
            }
        } finally {
            if (featureSink != null) {
                featureSink.flush();
                featureSink.close();
            }
        }
    }

    private static <T> Comparator<Object2IntMap.Entry<T>> keyOrder(
            final Comparator<T> inner) {
        return new Comparator<Object2IntMap.Entry<T>>() {

            @Override
            public int compare(
                    Object2IntMap.Entry<T> o1,
                    Object2IntMap.Entry<T> o2) {
                return inner.compare(o1.getKey(), o2.getKey());
            }
        };
    }

    private void writeEvents(
            final Object2IntMap<TokenPair> entryFeatureFreq,
            final Function<Integer, String> entryEncoder,
            final Function<Integer, String> featureEncoder)
            throws FileNotFoundException, IOException {

        LOG.debug("Sorting feature pairs frequency data.");

        List<Weighted<TokenPair>> tokens = mapToWeightedTokenPairs(
                entryFeatureFreq);
        Collections.sort(tokens, eventOrder);

        if (LOG.isDebugEnabled()) {
            LOG.debug(
                    "Writing feature pairs frequency data to file  " + entryFeaturesFile + ".");
        }
        if (entryFeaturesFile.exists() && LOG.isWarnEnabled()) {
            LOG.warn("The features file already exists and will be overwritten.");
        }

        WeightedTokenPairSink featureSink = null;
        final int n = tokens.size();
        try {
            featureSink = new WeightedTokenPairSink(
                    new TSVSink(entryFeaturesFile, charset),
                    entryEncoder, featureEncoder);
            int i = 0;
            for (final Weighted<TokenPair> tok : tokens) {
                featureSink.write(tok);
                if ((++i % PROGRESS_INTERVAL == 0 || i == n) && LOG.
                        isInfoEnabled()) {
                    LOG.info("Wrote " + i + "/" + n + " features. ("
                            + (int) (i * 100d / n) + "% complete)");
                }
            }
        } finally {
            if (featureSink != null) {
                featureSink.flush();
                featureSink.close();
            }
        }
    }

    public final boolean isPreindexedEntries() {
        return preindexedEntries;
    }

    public final void setPreindexedEntries(boolean preindexedEntries) {
        this.preindexedEntries = preindexedEntries;
    }

    public boolean isPreindexedFeatures() {
        return preindexedFeatures;
    }

    public final void setPreindexedFeatures(boolean preindexedFeatures) {
        this.preindexedFeatures = preindexedFeatures;
    }

    public final File getFeaturesFile() {
        return featuresFile;
    }

    public final void setFeaturesFile(final File featuresFile)
            throws NullPointerException {
        Checks.checkNotNull("featuresFile", featuresFile);
        this.featuresFile = featuresFile;
    }

    public final File getEntryFeaturesFile() {
        return entryFeaturesFile;
    }

    public final void setEntryFeaturesFile(final File entryFeaturesFile)
            throws NullPointerException {
        Checks.checkNotNull("entryFeaturesFile", entryFeaturesFile);
        this.entryFeaturesFile = entryFeaturesFile;
    }

    public final File getEntriesFile() {
        return entriesFile;
    }

    public final void setEntriesFile(final File entriesFile)
            throws NullPointerException {
        Checks.checkNotNull("entriesFile", entriesFile);
        this.entriesFile = entriesFile;
    }

    public File getInputFile() {
        return inputFile;
    }

    public final void setInstancesFile(final File inputFile)
            throws NullPointerException {
        Checks.checkNotNull("inputFile", inputFile);
        this.inputFile = inputFile;
    }

    public final Charset getCharset() {
        return charset;
    }

    public final void setCharset(Charset charset) {
        Checks.checkNotNull("charset", charset);
        this.charset = charset;
    }

    /**
     * Method that performance a number of sanity checks on the parameterisation
     * of this class. It is necessary to do this because the the class can be
     * instantiated via a null constructor when run from the command line.
     *
     * @throws NullPointerException
     * @throws IllegalStateException
     * @throws FileNotFoundException
     */
    private void checkState() throws NullPointerException, IllegalStateException, FileNotFoundException {
        // Check non of the parameters are null
        Checks.checkNotNull("entryFeaturesFile", entryFeaturesFile);
        Checks.checkNotNull("featuresFile", featuresFile);
        Checks.checkNotNull("entriesFile", entriesFile);
        Checks.checkNotNull("entriesFile", entriesFile);
        Checks.checkNotNull("inputFile", inputFile);
        Checks.checkNotNull("charset", charset);

        // Check that no two files are the same
        if (inputFile.equals(entryFeaturesFile)) {
            throw new IllegalStateException("inputFile == featuresFile");
        }
        if (inputFile.equals(featuresFile)) {
            throw new IllegalStateException("inputFile == contextsFile");
        }
        if (inputFile.equals(entriesFile)) {
            throw new IllegalStateException("inputFile == entriesFile");
        }
        if (entryFeaturesFile.equals(featuresFile)) {
            throw new IllegalStateException("entryFeaturesFile == featuresFile");
        }
        if (entryFeaturesFile.equals(entriesFile)) {
            throw new IllegalStateException("entryFeaturesFile == entriesFile");
        }
        if (featuresFile.equals(entriesFile)) {
            throw new IllegalStateException("featuresFile == entriesFile");
        }


        // Check that the instances file exists and is readable
        if (!inputFile.exists()) {
            throw new FileNotFoundException(
                    "instances file does not exist: " + inputFile);
        }
        if (!inputFile.isFile()) {
            throw new IllegalStateException(
                    "instances file is not a normal data file: " + inputFile);
        }
        if (!inputFile.canRead()) {
            throw new IllegalStateException(
                    "instances file is not readable: " + inputFile);
        }

        // For each output file, check that either it exists and it writeable,
        // or that it does not exist but is creatable
        if (entriesFile.exists() && (!entriesFile.isFile() || !entriesFile.
                                     canWrite())) {
            throw new IllegalStateException(
                    "entries file exists but is not writable: " + entriesFile);
        }
        if (!entriesFile.exists() && !entriesFile.getAbsoluteFile().
                getParentFile().
                canWrite()) {
            throw new IllegalStateException(
                    "entries file does not exists and can not be reated: " + entriesFile);
        }
        if (featuresFile.exists() && (!featuresFile.isFile() || !featuresFile.
                                      canWrite())) {
            throw new IllegalStateException(
                    "features file exists but is not writable: " + featuresFile);
        }
        if (!featuresFile.exists() && !featuresFile.getAbsoluteFile().
                getParentFile().
                canWrite()) {
            throw new IllegalStateException(
                    "features file does not exists and can not be reated: " + featuresFile);
        }
        if (entryFeaturesFile.exists() && (!entryFeaturesFile.isFile() || !entryFeaturesFile.
                                           canWrite())) {
            throw new IllegalStateException(
                    "entry-features file exists but is not writable: " + entryFeaturesFile);
        }
        if (!entryFeaturesFile.exists() && !entryFeaturesFile.getAbsoluteFile().
                getParentFile().
                canWrite()) {
            throw new IllegalStateException(
                    "entry-features file does not exists and can not be reated: " + entryFeaturesFile);
        }
    }

    @Override
    protected Objects.ToStringHelper toStringHelper() {
        return super.toStringHelper().
                add("in", inputFile).
                add("entriesOut", entriesFile).
                add("featuresOut", featuresFile).
                add("eventsOut", entryFeaturesFile).
                add("charset", charset);
    }

    public static void main(String[] args) throws Exception {
        new CountTask().runCommand(args);
    }
}

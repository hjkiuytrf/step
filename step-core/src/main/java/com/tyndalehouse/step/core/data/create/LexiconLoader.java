/*******************************************************************************
 * Copyright (c) 2012, Directors of the Tyndale STEP Project
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions 
 * are met:
 * 
 * Redistributions of source code must retain the above copyright 
 * notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright 
 * notice, this list of conditions and the following disclaimer in 
 * the documentation and/or other materials provided with the 
 * distribution.
 * Neither the name of the Tyndale House, Cambridge (www.TyndaleHouse.com)  
 * nor the names of its contributors may be used to endorse or promote 
 * products derived from this software without specific prior written 
 * permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS 
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT 
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS 
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE 
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, 
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, 
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; 
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER 
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT 
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING 
 * IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF 
 * THE POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package com.tyndalehouse.step.core.data.create;

import static com.tyndalehouse.step.core.data.entities.lexicon.Definition.ACCENTED_UNICODE;
import static com.tyndalehouse.step.core.data.entities.lexicon.Definition.ALTERNATIVE_TRANSLIT1;
import static com.tyndalehouse.step.core.data.entities.lexicon.Definition.ALTERNATIVE_TRANSLIT1_UNACCENTED;
import static com.tyndalehouse.step.core.data.entities.lexicon.Definition.LSJ_DEFS;
import static com.tyndalehouse.step.core.data.entities.lexicon.Definition.MEDIUM_DEF;
import static com.tyndalehouse.step.core.data.entities.lexicon.Definition.RELATED_NOS;
import static com.tyndalehouse.step.core.data.entities.lexicon.Definition.SHORT_DEF;
import static com.tyndalehouse.step.core.data.entities.lexicon.Definition.STEP_GLOSS;
import static com.tyndalehouse.step.core.data.entities.lexicon.Definition.STOP_WORD;
import static com.tyndalehouse.step.core.data.entities.lexicon.Definition.STRONG_NUMBER;
import static com.tyndalehouse.step.core.data.entities.lexicon.Definition.STRONG_PRONUNC;
import static com.tyndalehouse.step.core.data.entities.lexicon.Definition.STRONG_TRANSLITERATION;
import static com.tyndalehouse.step.core.data.entities.lexicon.Definition.TWO_LETTER_LOOKUP;
import static com.tyndalehouse.step.core.utils.IOUtils.closeQuietly;
import static com.tyndalehouse.step.core.utils.StringUtils.isBlank;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.SimpleAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.avaje.ebean.EbeanServer;
import com.tyndalehouse.step.core.data.create.loaders.AbstractClasspathBasedModuleLoader;
import com.tyndalehouse.step.core.data.entities.lexicon.Definition;
import com.tyndalehouse.step.core.exceptions.StepInternalException;

/**
 * Loads an Easton Dictionary
 * 
 * @author chrisburrell
 * 
 */
public class LexiconLoader extends AbstractClasspathBasedModuleLoader<Definition> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LexiconLoader.class);
    private static final String START_TOKEN = "==============";

    // state used during processing
    private int errors;
    private int count;
    // private Definition currentDefinition;
    private Document document;
    private Map<String, Method> methodMappings;
    private final boolean isGreek;
    private final LoaderTransaction transaction;
    private final RAMDirectory ramDirectory;
    private IndexWriter writer;
    private Map<String, FieldConfig> config;
    private SimpleAnalyzer analyzer;

    /**
     * Loads up dictionary items
     * 
     * @param ebean the backend server
     * @param resourcePath the classpath to the data
     * @param transaction transaction manager
     * @param isGreek TODO
     */
    public LexiconLoader(final EbeanServer ebean, final String resourcePath,
            final LoaderTransaction transaction, final boolean isGreek) {
        super(ebean, resourcePath, transaction);
        this.transaction = transaction;
        this.isGreek = isGreek;

        this.ramDirectory = new RAMDirectory();
        // DefinitionAnalyzer analyzer = new DefinitionAnalyzer();
        try {
            this.analyzer = new SimpleAnalyzer();
            this.writer = new IndexWriter(this.ramDirectory, this.analyzer, MaxFieldLength.UNLIMITED);
        } catch (final IOException e) {
            throw new StepInternalException("Unable to create index", e);
        }

        initLuceneMappings();
        // initMappings();

    }

    @Override
    protected List<Definition> parseFile(final Reader reader) {
        final BufferedReader bufferedReader = new BufferedReader(reader);
        String line = null;

        try {
            while ((line = bufferedReader.readLine()) != null) {
                parseLine(line);
            }

            // save last article
            saveCurrentDefinition();

            this.writer.close();
            final File file = new File("d:\\temp\\step");
            final Directory destination = FSDirectory.open(file);

            final IndexWriter fsWriter = new IndexWriter(destination, this.analyzer, true,
                    IndexWriter.MaxFieldLength.UNLIMITED);
            fsWriter.addIndexesNoOptimize(new Directory[] { this.ramDirectory });
            fsWriter.optimize();

            final int docs = fsWriter.maxDoc();
            fsWriter.close();

            // Free up the space used by the ram directory
            this.ramDirectory.close();

        } catch (final IOException io) {
            LOGGER.warn(io.getMessage(), io);
        } finally {
            closeQuietly(bufferedReader);
        }

        LOGGER.info("Loaded [{}] dictionary articles with [{}] errors", this.count, this.errors);
        return new ArrayList<Definition>();
    }

    // /**
    // * TODO slow performance on first call to setter of ebean... chase group post on Google. creates
    // * transliterations for all definitions
    // */
    // private void processTransliterations() {
    // // step transliterations, with
    // this.currentDefinition.setStepTransliteration(transliterate(this.currentDefinition
    // .getAccentedUnicode()));
    //
    // // and without the breathing - results may still contain accents - however they are generated from
    // // unicode without accents
    // this.currentDefinition.setUnaccentedStepTransliteration(adaptForUnaccentedTransliteration(
    // this.currentDefinition.getStepTransliteration(), this.isGreek));
    // }

    /**
     * Parses a line by setting the current state of this loader appropriately
     * 
     * @param line the line that has been read from file
     */
    private void parseLine(final String line) {
        // deal with case where we are hitting a new word
        if (line.endsWith(START_TOKEN)) {
            prepareNewDefinition();
        }

        parseField(line);
    }

    /**
     * Saves the current article, and if threshold is met, then saves to database
     */
    private void saveCurrentDefinition() {
        try {
            if (this.document != null) {
                this.writer.addDocument(this.document);
            }
        } catch (final IOException e) {
            throw new StepInternalException("Unable to add document to index", e);
        }

        // if (this.document == null) {
        // this.document = new Document();
        // }

        // if (this.currentDefinition != null) {
        // processTransliterations();
        // processDefaultsIfNull();
        // processLowerCasings();
        // getEbean().save(this.currentDefinition);
        // this.count++;
        //
        // if (this.count % 2000 == 0) {
        // this.transaction.flushCommitAndContinue();
        //
        // LOGGER.info("Saved [{}] lexical entries.", this.count);
        // }
        // }
    }

    // private void processDefaultsIfNull() {
    // if (this.currentDefinition.getBlacklisted() == null) {
    // this.currentDefinition.setBlacklisted(Boolean.FALSE);
    // }
    // }

    // /**
    // * Sets various fields to their lower case equivalence
    // */
    // private void processLowerCasings() {
    // final String strongPronunc = this.currentDefinition.getStrongPronunc();
    // if (strongPronunc != null) {
    // this.currentDefinition.setStrongPronunc(strongPronunc.toLowerCase());
    // }
    //
    // final String strongTranslit = this.currentDefinition.getStrongTranslit();
    // if (strongTranslit != null) {
    // this.currentDefinition.setStrongTranslit(strongTranslit.toLowerCase());
    // }
    //
    // final String alternative = this.currentDefinition.getAlternativeTranslit1();
    // if (alternative != null) {
    // final String lowerAlternative = alternative.toLowerCase();
    // if (this.isGreek) {
    // this.currentDefinition.setAlternativeTranslit1(toBetaLowercase(lowerAlternative));
    // } else {
    // this.currentDefinition.setAlternativeTranslit1(lowerAlternative.replace("#", ""));
    // }
    // }
    //
    // final String unaccentedAlternative = this.currentDefinition.getAlternativeTranslit1Unaccented();
    // if (unaccentedAlternative != null) {
    // if (this.isGreek) {
    // this.currentDefinition.setAlternativeTranslit1Unaccented(unaccentedAlternative.toLowerCase());
    // } else {
    // this.currentDefinition.setAlternativeTranslit1Unaccented(unaccentedAlternative.replace("#",
    // ""));
    // }
    // } else if (alternative != null) {
    // if (this.isGreek) {
    // this.currentDefinition.setAlternativeTranslit1Unaccented(toBetaUnaccented(alternative
    // .toLowerCase()));
    // } else {
    // this.currentDefinition.setAlternativeTranslit1Unaccented(alternative.toLowerCase());
    // }
    // }
    //
    // final String accentedUnicode = this.currentDefinition.getAccentedUnicode();
    // if (accentedUnicode != null) {
    // final String lowerCaseAccentedUnicode = accentedUnicode.toLowerCase();
    // this.currentDefinition.setAccentedUnicode(lowerCaseAccentedUnicode);
    // this.currentDefinition.setUnaccentedUnicode(StringConversionUtils.unAccent(
    // lowerCaseAccentedUnicode, true));
    // }
    // }

    /**
     * sets the appropriate state and saves articles in batches to prevent too much going into memory
     */
    private void prepareNewDefinition() {
        saveCurrentDefinition();
        this.document = new Document();

        // this.currentDefinition = new Definition();
    }

    private void initLuceneMappings() {
        this.config = new HashMap<String, FieldConfig>();

        // stored and indexed
        this.config.put("@StrNo", new FieldConfig(STRONG_NUMBER, Store.YES, Index.NOT_ANALYZED));
        this.config.put("@UnicodeAccented", new FieldConfig(ACCENTED_UNICODE, Store.YES, Index.NOT_ANALYZED));
        this.config.put("@StrUnicodeAccented", new FieldConfig(ACCENTED_UNICODE, Store.YES,
                Index.NOT_ANALYZED));
        this.config.put("@AllRelatedNos", new FieldConfig(RELATED_NOS, Store.YES, Index.ANALYZED));
        this.config.put("@StepGloss", new FieldConfig(STEP_GLOSS, Store.YES, Index.ANALYZED));
        this.config
                .put("@StrBetaAccented", new FieldConfig(ALTERNATIVE_TRANSLIT1, Store.YES, Index.ANALYZED));

        // stored not indexed
        this.config.put("@MounceShortDef", new FieldConfig(SHORT_DEF, Store.YES, Index.NO));
        this.config.put("@MounceMedDef", new FieldConfig(MEDIUM_DEF, Store.YES, Index.NO));
        this.config.put("@StopWord", new FieldConfig(STOP_WORD, Store.YES, Index.NO));
        this.config.put("@LsjDefs", new FieldConfig(LSJ_DEFS, Store.YES, Index.NO));
        this.config.put("@BdbMedDef", new FieldConfig(MEDIUM_DEF, Store.YES, Index.NO));
        this.config.put("@AcadTransAccented", new FieldConfig(ALTERNATIVE_TRANSLIT1, Store.NO,
                Index.NOT_ANALYZED));
        this.config.put("@AcadTransUnaccented", new FieldConfig(ALTERNATIVE_TRANSLIT1_UNACCENTED, Store.NO,
                Index.NOT_ANALYZED));

        // indexed not stored
        this.config.put("@2llUnaccented", new FieldConfig(TWO_LETTER_LOOKUP, Store.NO, Index.NOT_ANALYZED));
        this.config.put("@StrTranslit", new FieldConfig(STRONG_TRANSLITERATION, Store.NO, Index.ANALYZED));
        this.config.put("@StrPronunc", new FieldConfig(STRONG_PRONUNC, Store.NO, Index.ANALYZED));

        // config.put("@LsjBetaUnaccented", new FieldConfig(ALTERNATIVE_TRANSLIT1_UNACCENTED);

        // hebrew specific mappings

        // temp - TODO - remove when fields have been renamed

    }

    /**
     * Sets up the mappings between field names and methods
     */
    private void initMappings() {
        this.methodMappings = new HashMap<String, Method>(32);
        final Class<Definition> defClass = Definition.class;

        try {
            final Method mediumDef = defClass.getDeclaredMethod("setMediumDef", String.class);
            final Method alternativeTranslit1Unaccented = defClass.getDeclaredMethod(
                    "setAlternativeTranslit1Unaccented", String.class);
            final Method alternativeTrans1 = defClass.getDeclaredMethod("setAlternativeTranslit1",
                    String.class);
            final Method unicodeAccented = defClass.getDeclaredMethod("setAccentedUnicode", String.class);

            this.methodMappings.put("@UnicodeAccented",
                    defClass.getDeclaredMethod("setAccentedUnicode", String.class));

            this.methodMappings.put("@LsjDefs", defClass.getDeclaredMethod("setLsjDefs", String.class));
            this.methodMappings.put("@StrNo", defClass.getDeclaredMethod("setStrongNumber", String.class));
            this.methodMappings.put("@StrBetaAccented", alternativeTrans1);

            this.methodMappings.put("@StrTranslit",
                    defClass.getDeclaredMethod("setStrongTranslit", String.class));
            this.methodMappings.put("@StrPronunc",
                    defClass.getDeclaredMethod("setStrongPronunc", String.class));
            this.methodMappings.put("@AllRelatedNos",
                    defClass.getDeclaredMethod("setRelatedNos", String.class));
            this.methodMappings.put("@StepGloss", defClass.getDeclaredMethod("setStepGloss", String.class));
            this.methodMappings.put("@StopWord", defClass.getDeclaredMethod("setBlacklisted", Boolean.class));

            // greek specific mappings
            this.methodMappings.put("@MounceShortDef",
                    defClass.getDeclaredMethod("setShortDef", String.class));
            this.methodMappings.put("@MounceMedDef", mediumDef);
            this.methodMappings.put("@LsjBetaUnaccented", alternativeTranslit1Unaccented);

            // hebrew specific mappings
            this.methodMappings.put("@BdbMedDef", mediumDef);
            this.methodMappings.put("@AcadTransAccented", alternativeTrans1);
            this.methodMappings.put("@AcadTransUnaccented", alternativeTranslit1Unaccented);
            this.methodMappings.put("@2llUnaccented",
                    defClass.getDeclaredMethod("setTwoLetterLookup", String.class));

            // temp - TODO - remove when fields have been renamed
            this.methodMappings.put("@StrUnicodeAccented", unicodeAccented);

        } catch (final NoSuchMethodException e) {
            throw new StepInternalException("Unable to find matching method", e);
        }
    }

    /**
     * parses a simple field by examining the type and setting the content (or appending the content to a
     * 
     * @param line the line content including field name and value
     */
    private void parseField(final String line) {
        if (line == null || line.length() == 0 || line.charAt(0) != '@') {
            // ignoring line
            return;
        }

        // get the field name
        final int tabIndex = line.indexOf('\t');
        if (tabIndex < 1) {
            LOGGER.error("Invalid line was found in file: [{}]", line);
            return;
        }

        // get field name and value
        final String fieldName = line.substring(0, tabIndex - 1);
        final int startValue = tabIndex + 1;
        // get value
        if (startValue > line.length()) {
            // no value, so skip
            LOGGER.trace("Skipping empty field [{}]", fieldName);
            return;
        }

        final String fieldValue = line.substring(startValue);
        if (isBlank(fieldValue)) {
            LOGGER.trace("Skipping empty field [{}] => [{}]", fieldName, fieldValue);
            // skipping empty field
            return;
        }

        final FieldConfig fieldConfig = this.config.get(fieldName);
        if (fieldConfig != null) {
            this.document.add(new Field(fieldConfig.getName(), fieldValue, fieldConfig.getStore(),
                    fieldConfig.getIndex()));
        }

        // final Method method = this.methodMappings.get(fieldName);
        // if (method == null) {
        // tryOtherMappings(fieldName, fieldValue);
        // LOGGER.trace("Unable to map [{}]", fieldName);
        // return;
        // }

        // try {
        //
        //
        // final Class<?>[] parameterTypes = method.getParameterTypes();
        // if (Boolean.class.equals(parameterTypes[0])) {
        // method.invoke(this.currentDefinition, Boolean.parseBoolean(fieldValue));
        // } else {
        // method.invoke(this.currentDefinition, fieldValue);
        // }
        //
        // } catch (final Exception e) {
        // throw new StepInternalException("Unable to call method for field " + fieldName, e);
        // }
    }

    // /**
    // * Tries other mappings to process the field with
    // *
    // * @param fieldName the name of the field
    // * @param fieldValue the value of the field
    // */
    // private void tryOtherMappings(final String fieldName, final String fieldValue) {
    // if ("@Translations".equals(fieldName)) {
    // final String[] translations = fieldValue.split("[|]");
    //
    // for (final String alternative : translations) {
    // final Translation t = new Translation();
    // t.setAlternativeTranslation(alternative);
    //
    // List<Translation> currentTranslations = this.currentDefinition.getTranslations();
    // if (currentTranslations == null) {
    // currentTranslations = new ArrayList<Translation>();
    // this.currentDefinition.setTranslations(currentTranslations);
    // }
    // currentTranslations.add(t);
    // }
    // }
    // }

    /**
     * Helper method that gets a trimmed string out
     * 
     * @param fieldName the name of the field
     * @param line the content of the line
     * @return the portion of string representing the string value of the field declared in that line
     */
    String parseFieldContent(final String fieldName, final String line) {
        return line.substring(fieldName.length() + 1).trim();
    }
}
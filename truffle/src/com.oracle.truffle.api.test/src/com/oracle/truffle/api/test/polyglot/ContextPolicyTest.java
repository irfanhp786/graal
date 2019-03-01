/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.test.polyglot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextPolicy;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

public class ContextPolicyTest {

    private static final String EXCLUSIVE0 = "ExclusiveLanguage0";
    private static final String REUSE0 = "ReuseLanguage0";
    private static final String SHARED0 = "SharedLanguage0";

    private static final String EXCLUSIVE1 = "ExclusiveLanguage1";
    private static final String REUSE1 = "ReuseLanguage1";
    private static final String SHARED1 = "SharedLanguage1";

    private static final String RUN_INNER_CONTEXT = "runInInnerContext";

    static List<TruffleLanguage<?>> languageInstances = new ArrayList<>();
    static List<TruffleLanguage<?>> contextCreate = new ArrayList<>();
    static List<TruffleLanguage<?>> contextDispose = new ArrayList<>();
    static List<TruffleLanguage<?>> parseRequest = new ArrayList<>();

    @After
    @Before
    public void cleanup() {
        languageInstances.clear();
        contextCreate.clear();
        contextDispose.clear();
        parseRequest.clear();
    }

    @Test
    public void testOneLanguageASTParsing() {
        Source source0 = Source.create(EXCLUSIVE0, "s0");
        Source source1 = Source.create(EXCLUSIVE0, "s1");

        Engine engine = Engine.create();
        Context context0 = Context.newBuilder().engine(engine).build();
        context0.eval(source0);
        context0.eval(source1);
        assertEquals(2, parseRequest.size());
        context0.close();

        Context context1 = Context.newBuilder().engine(engine).build();
        context1.eval(source0);
        context1.eval(source1);
        assertEquals(4, parseRequest.size());

        engine.close();
    }

    @Test
    public void testManyLanguageASTParsing() {
        Source source0 = Source.create(SHARED0, "s0");
        Source source1 = Source.create(SHARED0, "s1");

        // same options parse caching enabled
        Engine engine = Engine.create();
        Context context0 = Context.newBuilder().engine(engine).option(SHARED0 + ".Dummy", "1").build();
        context0.eval(source0);
        context0.eval(source1);
        assertEquals(2, parseRequest.size());
        context0.close();

        Context context1 = Context.newBuilder().engine(engine).option(SHARED0 + ".Dummy", "1").build();
        context1.eval(source0);
        context1.eval(source1);
        assertEquals(2, parseRequest.size());
        engine.close();

        // different options parse caching disabled
        cleanup();
        engine = Engine.create();
        context0 = Context.newBuilder().engine(engine).option(SHARED0 + ".Dummy", "1").build();
        context0.eval(source0);
        context0.eval(source1);
        assertEquals(2, parseRequest.size());
        context0.close();

        context1 = Context.newBuilder().engine(engine).option(SHARED0 + ".Dummy", "2").build();
        context1.eval(source0);
        context1.eval(source1);
        assertEquals(4, parseRequest.size());
        engine.close();
    }

    @Test
    public void testOneReuseLanguageASTParsing() {
        Source source0 = Source.create(REUSE0, "s0");
        Source source1 = Source.create(REUSE0, "s1");

        Engine engine = Engine.create();
        Context context0 = Context.newBuilder().engine(engine).option(REUSE0 + ".Dummy", "1").build();
        context0.eval(source0);
        context0.eval(source1);
        assertEquals(2, parseRequest.size());
        context0.close();

        Context context1 = Context.newBuilder().engine(engine).option(REUSE0 + ".Dummy", "1").build();
        context1.eval(source0);
        context1.eval(source1);
        assertEquals(2, parseRequest.size());
        context1.close();

        Context context2 = Context.newBuilder().engine(engine).option(REUSE0 + ".Dummy", "2").build();
        context2.eval(source0);
        context2.eval(source1);
        assertEquals(4, parseRequest.size());
        context1.close();

        engine.close();
    }

    @Test
    public void testOptionDescriptorContextReuse() {
        Engine engine = Engine.create();

        // test ONE

        assertEquals(0, languageInstances.size());
        engine.getLanguages().get(EXCLUSIVE0).getOptions();
        assertEquals(1, languageInstances.size());

        Context.newBuilder().engine(engine).build().initialize(EXCLUSIVE0);
        assertEquals(1, languageInstances.size());

        Context.newBuilder().engine(engine).build().initialize(EXCLUSIVE0);
        assertEquals(2, languageInstances.size());

        engine.close();

        // test ONE_REUSE

        cleanup();
        engine = Engine.create();
        assertEquals(0, languageInstances.size());
        engine.getLanguages().get(REUSE0).getOptions();
        assertEquals(1, languageInstances.size());

        Context context0 = Context.newBuilder().engine(engine).build();
        context0.initialize(REUSE0);
        assertEquals(1, languageInstances.size());
        context0.close();

        Context.newBuilder().engine(engine).build().initialize(REUSE0);
        assertEquals(1, languageInstances.size());

        Context.newBuilder().engine(engine).build().initialize(REUSE0);
        assertEquals(2, languageInstances.size());

        engine.close();

        // test MANY
        cleanup();
        engine = Engine.create();
        assertEquals(0, languageInstances.size());
        engine.getLanguages().get(SHARED0).getOptions();
        assertEquals(1, languageInstances.size());

        Context.newBuilder().engine(engine).build().initialize(SHARED0);
        assertEquals(1, languageInstances.size());

        Context.newBuilder().engine(engine).build().initialize(SHARED0);
        assertEquals(1, languageInstances.size());

        engine.close();
    }

    @Test
    public void testOneContext() {
        Engine engine = Engine.create();

        Source source0 = Source.create(EXCLUSIVE0, "s0");
        Source source1 = Source.create(EXCLUSIVE0, "s1");

        Context context0 = Context.newBuilder().engine(engine).build();
        assertEmpty();
        context0.initialize(EXCLUSIVE0);
        assertEquals(1, languageInstances.size());
        assertEquals(1, contextCreate.size());
        assertEquals(0, contextDispose.size());
        assertEquals(0, parseRequest.size());
        assertSame(languageInstances.get(0), contextCreate.get(0));

        Context context1 = Context.newBuilder().engine(engine).build();
        context1.initialize(EXCLUSIVE0);
        assertEquals(2, languageInstances.size());
        assertEquals(2, contextCreate.size());
        assertEquals(0, contextDispose.size());
        assertEquals(0, parseRequest.size());
        assertSame(languageInstances.get(1), contextCreate.get(1));

        context0.eval(source0);
        assertEquals(1, parseRequest.size());
        assertSame(languageInstances.get(0), parseRequest.get(0));

        context0.eval(source0);
        assertEquals(1, parseRequest.size());
        assertSame(languageInstances.get(0), parseRequest.get(0));

        context1.eval(source0);
        assertEquals(2, parseRequest.size());
        assertSame(languageInstances.get(1), parseRequest.get(1));

        context1.eval(source0);
        assertEquals(2, parseRequest.size());
        assertSame(languageInstances.get(1), parseRequest.get(1));

        context0.eval(source1);
        assertEquals(3, parseRequest.size());
        assertSame(languageInstances.get(0), parseRequest.get(2));

        context0.eval(source1);
        assertEquals(3, parseRequest.size());
        assertSame(languageInstances.get(0), parseRequest.get(2));

        context1.eval(source1);
        assertEquals(4, parseRequest.size());
        assertSame(languageInstances.get(1), parseRequest.get(3));

        context1.eval(source1);
        assertEquals(4, parseRequest.size());
        assertSame(languageInstances.get(1), parseRequest.get(3));

        assertEquals(0, contextDispose.size());

        context0.close();
        assertEquals(1, contextDispose.size());
        assertSame(languageInstances.get(0), contextDispose.get(0));

        context1.close();
        assertEquals(2, contextDispose.size());
        assertSame(languageInstances.get(1), contextDispose.get(1));

        assertEquals(2, languageInstances.size());
        assertEquals(2, contextCreate.size());

        engine.close();
    }

    @Test
    public void testOneParseCaching() {

    }

    @Test
    public void testOneReuseContext() {
        Engine engine = Engine.create();

        Source source0 = Source.create(REUSE0, "s0");
        Source source1 = Source.create(REUSE0, "s1");

        Context context0 = Context.newBuilder().engine(engine).build();
        assertEmpty();
        context0.initialize(REUSE0);
        assertEquals(1, languageInstances.size());
        assertEquals(1, contextCreate.size());
        assertEquals(0, contextDispose.size());
        assertEquals(0, parseRequest.size());
        assertSame(languageInstances.get(0), contextCreate.get(0));

        Context context1 = Context.newBuilder().engine(engine).build();
        context1.initialize(REUSE0);
        assertEquals(2, languageInstances.size());
        assertEquals(2, contextCreate.size());
        assertEquals(0, contextDispose.size());
        assertEquals(0, parseRequest.size());
        assertSame(languageInstances.get(1), contextCreate.get(1));

        context0.eval(source0);
        assertEquals(1, parseRequest.size());
        assertSame(languageInstances.get(0), parseRequest.get(0));

        context0.eval(source0);
        assertEquals(1, parseRequest.size());
        assertSame(languageInstances.get(0), parseRequest.get(0));

        context1.eval(source1);
        assertEquals(2, parseRequest.size());
        assertSame(languageInstances.get(1), parseRequest.get(1));

        context1.eval(source1);
        assertEquals(2, parseRequest.size());
        assertSame(languageInstances.get(1), parseRequest.get(1));

        // reuse context1 in context2
        context1.close();
        Context context2 = Context.newBuilder().engine(engine).build();
        context2.initialize(REUSE0);
        assertEquals(2, languageInstances.size());
        assertEquals(3, contextCreate.size());
        assertEquals(1, contextDispose.size());
        assertEquals(2, parseRequest.size());

        context2.eval(source1);
        assertEquals(2, parseRequest.size());

        context2.eval(source0);
        assertEquals(3, parseRequest.size());
        assertSame(languageInstances.get(1), parseRequest.get(2));

        // reuse context0 in context3
        Context context3 = Context.newBuilder().engine(engine).build();
        context0.close();
        // code is shared on initialization
        context3.initialize(REUSE0);

        context3.eval(source0);
        assertEquals(3, parseRequest.size());

        context3.close();
        context2.close();
        assertEquals(2, languageInstances.size());
        assertEquals(4, contextCreate.size());
        assertEquals(4, contextDispose.size());

        engine.close();
    }

    @Test
    public void testManyContext() {
        Engine engine = Engine.create();

        Source source0 = Source.create(SHARED0, "s0");
        Source source1 = Source.create(SHARED0, "s1");

        Context context0 = Context.newBuilder().engine(engine).build();
        Context context1 = Context.newBuilder().engine(engine).build();

        assertEmpty();
        context0.initialize(SHARED0);
        assertEquals(1, languageInstances.size());
        assertEquals(1, contextCreate.size());
        assertEquals(0, contextDispose.size());
        assertEquals(0, parseRequest.size());
        assertSame(languageInstances.get(0), contextCreate.get(0));

        context1.initialize(SHARED0);
        assertEquals(1, languageInstances.size());
        assertEquals(2, contextCreate.size());
        assertEquals(0, contextDispose.size());
        assertEquals(0, parseRequest.size());
        assertSame(languageInstances.get(0), contextCreate.get(1));

        context0.eval(source0);
        assertEquals(1, parseRequest.size());
        assertSame(languageInstances.get(0), parseRequest.get(0));

        context0.eval(source0);
        assertEquals(1, parseRequest.size());

        context1.eval(source0);
        assertEquals(1, parseRequest.size());

        context1.eval(source0);
        assertEquals(1, parseRequest.size());

        context0.eval(source1);
        assertEquals(2, parseRequest.size());
        assertSame(languageInstances.get(0), parseRequest.get(1));

        context0.eval(source1);
        assertEquals(2, parseRequest.size());

        context1.eval(source1);
        assertEquals(2, parseRequest.size());

        context1.eval(source1);
        assertEquals(2, parseRequest.size());

        assertEquals(0, contextDispose.size());

        context0.close();
        assertEquals(1, contextDispose.size());
        assertSame(languageInstances.get(0), contextDispose.get(0));

        context1.close();
        assertEquals(1, languageInstances.size());
        assertEquals(2, contextCreate.size());
        assertEquals(2, contextDispose.size());
        assertSame(languageInstances.get(0), contextDispose.get(0));

        engine.close();
    }

    private static void assertEmpty() {
        assertEquals(0, languageInstances.size());
        assertEquals(0, contextCreate.size());
        assertEquals(0, contextDispose.size());
        assertEquals(0, parseRequest.size());
    }

    @Test
    public void testReferencesWithCodeMixing() {
        testReferenceMixing(EXCLUSIVE0, EXCLUSIVE1);
        testReferenceMixing(EXCLUSIVE0, SHARED1);
        testReferenceMixing(EXCLUSIVE0, REUSE1);

        testReferenceMixing(REUSE0, EXCLUSIVE1);
        testReferenceMixing(REUSE0, REUSE1);
        testReferenceMixing(REUSE0, SHARED1);

        testReferenceMixing(SHARED0, EXCLUSIVE1);
        testReferenceMixing(SHARED0, REUSE1);
        testReferenceMixing(SHARED0, SHARED1);
    }

    private static void testReferenceMixing(String language0, String language1) {
        testReferenceMixingWithSourcesAndCompatibility(language0, language1, true, true);
        testReferenceMixingWithSourcesAndCompatibility(language0, language1, false, true);
        testReferenceMixingWithSourcesAndCompatibility(language0, language1, true, false);
        testReferenceMixingWithSourcesAndCompatibility(language0, language1, false, false);
    }

    private static void testReferenceMixingWithSourcesAndCompatibility(String language0, String language1,
                    boolean language0Compatible,
                    boolean language1Compatible) {

        // run without inner contexts

        // test with both sources cached
        testReferenceMixingWithSources(
                        Source.newBuilder(language0, "s0", "").cached(true).buildLiteral(),
                        Source.newBuilder(language1, "s0", "").cached(true).buildLiteral(),
                        language0Compatible, language1Compatible);

        // test with one source uncached
        testReferenceMixingWithSources(
                        Source.newBuilder(language0, "s1", "").cached(false).buildLiteral(),
                        Source.newBuilder(language1, "s1", "").cached(true).buildLiteral(),
                        language0Compatible, language1Compatible);

        // test with both uncached
        testReferenceMixingWithSources(
                        Source.newBuilder(language0, "s2", "").cached(false).buildLiteral(),
                        Source.newBuilder(language1, "s2", "").cached(false).buildLiteral(),
                        language0Compatible, language1Compatible);

        // run with inner contexts

        // test with both sources cached
        testReferenceMixingWithSources(
                        Source.newBuilder(language0, "s0", RUN_INNER_CONTEXT).cached(true).buildLiteral(),
                        Source.newBuilder(language1, "s0", RUN_INNER_CONTEXT).cached(true).buildLiteral(),
                        language0Compatible, language1Compatible);

        // test with one source uncached
        testReferenceMixingWithSources(
                        Source.newBuilder(language0, "s1", RUN_INNER_CONTEXT).cached(false).buildLiteral(),
                        Source.newBuilder(language1, "s1", RUN_INNER_CONTEXT).cached(true).buildLiteral(),
                        language0Compatible, language1Compatible);

        // test with both uncached
        testReferenceMixingWithSources(
                        Source.newBuilder(language0, "s2", RUN_INNER_CONTEXT).cached(false).buildLiteral(),
                        Source.newBuilder(language1, "s2", RUN_INNER_CONTEXT).cached(false).buildLiteral(),
                        language0Compatible, language1Compatible);
    }

    private static void testReferenceMixingWithSources(Source sl0, Source sl1, boolean language0Compatible,
                    boolean language1Compatible) {
        testReferenceMixingWithSourcesBound(sl0, sl1, language0Compatible, language1Compatible, false, false);
        testReferenceMixingWithSourcesBound(sl0, sl1, language0Compatible, language1Compatible, true, false);
        testReferenceMixingWithSourcesBound(sl0, sl1, language0Compatible, language1Compatible, false, true);
        testReferenceMixingWithSourcesBound(sl0, sl1, language0Compatible, language1Compatible, true, true);
    }

    private static void testReferenceMixingWithSourcesBound(Source sl0, Source sl1, boolean language0Compatible, boolean language1Compatible,
                    boolean sharedContext1,
                    boolean sharedContext2) {
        try (Engine engine = Engine.create()) {
            Context.Builder b0 = Context.newBuilder();
            if (!sharedContext1) {
                b0.engine(engine);
            }
            if (!language0Compatible) {
                b0.option(sl0.getLanguage() + ".Dummy", "0");
            }
            Context.Builder b1 = Context.newBuilder();
            if (!sharedContext2) {
                b1.engine(engine);
            }
            if (!language1Compatible) {
                b1.option(sl1.getLanguage() + ".Dummy", "1");
            }
            Context c0 = b0.build();
            Context c1 = b1.build();

            Value v0l0 = c0.eval(sl0);
            Value v0l1 = c0.eval(sl1);

            Value v1l0 = c1.eval(sl0);
            Value v1l1 = c1.eval(sl1);

            v0l1.execute(v0l1, v0l0, v0l0);
            v0l1.execute(v0l0, v0l1, v0l1);

            v1l1.execute(v1l1, v1l0, v1l0);
            v1l1.execute(v1l0, v1l1, v1l1);

            v0l0.execute(v0l1, v0l0);
            v0l0.execute(v0l0, v0l1);
            v1l0.execute(v1l1, v1l0);
            v1l0.execute(v1l0, v1l1);

            c0.close();
            c1.close();
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static class SharedObject implements TruffleObject {

        final TruffleContext context;
        final TruffleLanguage<?> expectedLanguage;
        final Env expectedEnvironment;
        final CallTarget target;

        SharedObject(TruffleContext context, TruffleLanguage<?> language, Env env) {
            this.context = context;
            this.expectedLanguage = language;
            this.expectedEnvironment = env;
            this.target = Truffle.getRuntime().createCallTarget(new RootNode(language) {
                @Child InteropLibrary library = InteropLibrary.getFactory().createDispatched(5);
                private final Supplier<? extends Object> cachedLanguageSupplier = lookupLanguageSupplier(this);
                private final Supplier<? extends Object> cachedContextSupplier = lookupContextSupplier(this);

                @Override
                public Object execute(VirtualFrame frame) {
                    Object[] args = frame.getArguments();
                    assertSame(expectedLanguage, lookupLanguageSupplier(library).get());
                    assertSame(expectedEnvironment, lookupContextSupplier(library).get());
                    assertSame(cachedLanguageSupplier, lookupLanguageSupplier(library));
                    assertSame(cachedContextSupplier, lookupContextSupplier(library));
                    assertSame(expectedLanguage, cachedLanguageSupplier.get());
                    assertSame(expectedEnvironment, cachedContextSupplier.get());

                    if (args.length > 0) {
                        try {
                            return library.execute(args[0], Arrays.copyOfRange(args, 1, args.length));
                        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                            throw new AssertionError();
                        }
                    } else {
                        return "done";
                    }
                }
            });
        }

        @ExportMessage
        boolean accepts(@Cached(value = "this.expectedLanguage.getClass()", allowUncached = true) Class<?> cachedLanguageclass) {
            /*
             * This is needed to make sure we don't try to compare cached suppliers from different
             * languages. This is necessary because we use the expectedLanguage class to lookup the
             * suppliers.
             */
            return expectedLanguage.getClass() == cachedLanguageclass;
        }

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @SuppressWarnings("unchecked")
        @ExportMessage
        Object execute(Object[] args,
                        @CachedLibrary(limit = "5") InteropLibrary library,
                        @Cached(value = "this.lookupLanguageSupplier(library)", allowUncached = true) Supplier<? extends Object> cachedLanguageSupplier,
                        @Cached(value = "this.lookupContextSupplier(library)", allowUncached = true) Supplier<? extends Object> cachedContextSupplier,
                        @Cached(value = "this.getContextReference()", allowUncached = true) ContextReference<Env> contextReference)
                        throws UnsupportedTypeException, ArityException, UnsupportedMessageException {

            Object prev = enterInner();

            try {
                assertSame(expectedLanguage, lookupLanguageSupplier(library).get());
                assertSame(expectedEnvironment, lookupContextSupplier(library).get());
                assertSame(expectedLanguage, cachedLanguageSupplier.get());
                assertSame(expectedEnvironment, cachedContextSupplier.get());
                assertSame(cachedLanguageSupplier, lookupLanguageSupplier(library));
                assertSame(cachedContextSupplier, lookupContextSupplier(library));
                assertSame(expectedEnvironment, contextReference.get());

                target.call(args);

                if (args.length > 0) {
                    return library.execute(args[0], Arrays.copyOfRange(args, 1, args.length));
                } else {
                    return "done";
                }
            } finally {
                leaveInner(prev);
            }
        }

        private void leaveInner(Object prev) {
            if (context != null) {
                context.leave(prev);
            }
        }

        @SuppressWarnings("unchecked")
        private Object enterInner() {
            Object prev = null;
            if (context != null) {
                prev = context.enter();
                assertSame(expectedLanguage, ExclusiveLanguage0.getCurrentLanguage(expectedLanguage.getClass()));
                assertSame(expectedEnvironment, ExclusiveLanguage0.getCurrentContext(expectedLanguage.getClass()));
            }
            return prev;
        }

        @SuppressWarnings("rawtypes")
        Supplier<? extends Object> lookupLanguageSupplier(Node node) {
            Object prev = enterInner();
            try {
                // ensure initialized
                Supplier<?> o;
                try {
                    Method method = Node.class.getDeclaredMethod("getLanguageSupplier", Class.class);
                    method.setAccessible(true);
                    o = (Supplier<?>) method.invoke(node, expectedLanguage.getClass());
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
                assertSame(expectedLanguage, ExclusiveLanguage0.getCurrentLanguage(expectedLanguage.getClass()));
                assertSame(o.get(), expectedLanguage);
                return o;
            } finally {
                leaveInner(prev);
            }
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        ContextReference<Env> getContextReference() {
            Object prev = enterInner();
            try {
                // ensure initialized
                return ExclusiveLanguage0.getCurrentLanguage(expectedLanguage.getClass()).getContextReference();
            } finally {
                leaveInner(prev);
            }
        }

        @SuppressWarnings("unchecked")
        Supplier<? extends Object> lookupContextSupplier(Node node) {
            Object prev = enterInner();
            try {
                Supplier<? extends Object> o;
                try {
                    Method method = Node.class.getDeclaredMethod("getContextSupplier", Class.class);
                    method.setAccessible(true);
                    o = (Supplier<? extends Object>) method.invoke(node, expectedLanguage.getClass());
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
                return o;
            } finally {
                leaveInner(prev);
            }
        }

    }

    static class LanguageRootNode extends RootNode {

        private final Class<? extends TruffleLanguage<Env>> languageClass;

        private final boolean innerContext;

        @SuppressWarnings("unchecked")
        protected LanguageRootNode(TruffleLanguage<?> language, boolean innerContext) {
            super(language);
            this.languageClass = (Class<? extends TruffleLanguage<Env>>) language.getClass();
            this.innerContext = innerContext;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Env env = ExclusiveLanguage0.getCurrentContext(languageClass);
            TruffleContext context = null;
            if (innerContext) {
                context = env.newContextBuilder().build();
            }
            Object prev = null;
            if (context != null) {
                prev = context.enter();
            }
            try {
                SharedObject obj = new SharedObject(
                                context,
                                ExclusiveLanguage0.getCurrentLanguage(languageClass),
                                ExclusiveLanguage0.getCurrentContext(languageClass));
                return obj;
            } finally {
                if (context != null) {
                    context.leave(prev);
                }
            }

        }
    }

    @Registration(id = EXCLUSIVE0, name = EXCLUSIVE0, contextPolicy = ContextPolicy.EXCLUSIVE)
    public static class ExclusiveLanguage0 extends TruffleLanguage<Env> {

        public ExclusiveLanguage0() {
            languageInstances.add(this);
        }

        @Option(help = "", category = OptionCategory.INTERNAL) //
        static final OptionKey<Integer> Dummy = new OptionKey<>(0);

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new ExclusiveLanguage0OptionDescriptors();
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            parseRequest.add(this);
            boolean innerContext = request.getSource().getName().equals(RUN_INNER_CONTEXT);
            return Truffle.getRuntime().createCallTarget(new LanguageRootNode(this, innerContext));
        }

        @Override
        protected Env createContext(Env env) {
            contextCreate.add(this);
            return env;
        }

        @Override
        protected void disposeContext(Env context) {
            contextDispose.add(this);
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return false;
        }

        public static <T extends TruffleLanguage<?>> T getCurrentLanguage(Class<T> languageClass) {
            return TruffleLanguage.getCurrentLanguage(languageClass);
        }

        public static <C, T extends TruffleLanguage<C>> C getCurrentContext(Class<T> languageClass) {
            return TruffleLanguage.getCurrentContext(languageClass);
        }

    }

    @Registration(id = EXCLUSIVE1, name = EXCLUSIVE1, contextPolicy = ContextPolicy.EXCLUSIVE)
    public static class ExclusiveLanguage1 extends ExclusiveLanguage0 {
        @Option(help = "", category = OptionCategory.INTERNAL) //
        static final OptionKey<Integer> Dummy = new OptionKey<>(0);

        @Override
        protected boolean areOptionsCompatible(OptionValues firstOptions, OptionValues newOptions) {
            return firstOptions.get(Dummy).equals(newOptions.get(Dummy));
        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new ExclusiveLanguage1OptionDescriptors();
        }
    }

    @Registration(id = REUSE0, name = REUSE0, contextPolicy = ContextPolicy.REUSE)
    public static class ReuseLanguage0 extends ExclusiveLanguage0 {
        @Option(help = "", category = OptionCategory.INTERNAL) //
        static final OptionKey<Integer> Dummy = new OptionKey<>(0);

        @Override
        protected boolean areOptionsCompatible(OptionValues firstOptions, OptionValues newOptions) {
            return firstOptions.get(Dummy).equals(newOptions.get(Dummy));
        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new ReuseLanguage0OptionDescriptors();
        }
    }

    @Registration(id = REUSE1, name = REUSE1, contextPolicy = ContextPolicy.REUSE)
    public static class ReuseLanguage1 extends ExclusiveLanguage0 {
        @Option(help = "", category = OptionCategory.INTERNAL) //
        static final OptionKey<Integer> Dummy = new OptionKey<>(0);

        @Override
        protected boolean areOptionsCompatible(OptionValues firstOptions, OptionValues newOptions) {
            return firstOptions.get(Dummy).equals(newOptions.get(Dummy));
        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new ReuseLanguage1OptionDescriptors();
        }
    }

    @Registration(id = SHARED0, name = SHARED0, contextPolicy = ContextPolicy.SHARED)
    public static class SharedLanguage0 extends ExclusiveLanguage0 {
        @Option(help = "", category = OptionCategory.INTERNAL) //
        static final OptionKey<Integer> Dummy = new OptionKey<>(0);

        @Override
        protected boolean areOptionsCompatible(OptionValues firstOptions, OptionValues newOptions) {
            return firstOptions.get(Dummy).equals(newOptions.get(Dummy));
        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new SharedLanguage0OptionDescriptors();
        }
    }

    @Registration(id = SHARED1, name = SHARED1, contextPolicy = ContextPolicy.SHARED)
    public static class SharedLanguage1 extends ExclusiveLanguage0 {
        @Option(help = "", category = OptionCategory.INTERNAL) //
        static final OptionKey<Integer> Dummy = new OptionKey<>(0);

        @Override
        protected boolean areOptionsCompatible(OptionValues firstOptions, OptionValues newOptions) {
            return firstOptions.get(Dummy).equals(newOptions.get(Dummy));
        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new SharedLanguage1OptionDescriptors();
        }
    }

}

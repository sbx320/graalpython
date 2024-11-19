/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.traceback;

import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;

/**
 * <strong>Summary of our implementation of traceback handling</strong>
 *
 * <p>
 * When a {@link com.oracle.graal.python.runtime.exception.PException} is thrown, Truffle collects
 * every frame that the exception passes through until its caught. Then, when asked for a traceback,
 * it provides a sequence of stack trace element objects that capture frames from the root node
 * where the exception was thrown up to the top level root node. This stack trace is created from
 * the frames captured during the unwinding and then the frames that are currently on stack, i.e. it
 * expects to be called in an exception handler, otherwise the stacktrace is incorrect - it would
 * contain frames from the place where you asked for the stacktrace not the place where the
 * exception occured. Additionally, the stacktrace is frozen on the first access and from then on,
 * Truffle always returns its cached copy.
 * </p>
 * <p>
 * Python, on the other hand, builds the traceback incrementally. Firstly, it only includes the
 * frames that the exception has passed through during the unwinding plus the frame where it was
 * caught. It doesn't include the frames above it (to the top). Secondly, the traceback is never
 * frozen. The traceback accumulates more frames when the exception gets reraised. To correct the
 * mismatch between Truffle and Python exception handling, we need to wrap {@link PException}s in
 * {@link LazyTraceback} objects when caught and adhere to particular rules of exception handling
 * mentioned below.
 * </p>
 *
 * <p>
 * {@link LazyTraceback} represents a (possibly empty) traceback segment. It consists of an optional
 * Python frame or frame reference to the frame where the exception was caught and a
 * {@link PException} which serves as a carrier of the Truffle stack trace. {@link LazyTraceback}
 * forms a linked list that gets prepended a new {@link LazyTraceback} each time the python
 * exception gets reraised, either explicitly (raise statement) or implicitly (for example, at the
 * end of finally). Each of these segments needs to have their own distinct {@link PException} to
 * avoid interference, therefore a caught {@link PException} must never be rethrown after being
 * added to the traceback and it must never be added to the traceback multiple times.
 * </p>
 *
 * <p>
 * The whole chain of {@link LazyTraceback} objects can be materialized into a linked list of
 * PTraceback objects. Due to all the parts of a segment being optional, it can also materialize to
 * nothing (null/None). The materialization is lazy and is split between
 * {@link MaterializeLazyTracebackNode} and accessor nodes in {@link TracebackBuiltins}. The purpose
 * of {@link MaterializeLazyTracebackNode} is to do the minimal amount of work necessary to
 * determine whether the traceback will materialize to something and is not empty. Then it either
 * returns the {@link PTraceback} object or null.
 * </p>
 *
 * <p>
 * Rules for exception handling:
 * <ul>
 * <li>When you catch a {@link PException PException} and need to obtain its corresponding
 * {@link com.oracle.graal.python.builtins.objects.exception.PBaseException PBaseException}, use the
 * {@link PException#getEscapedException()} method, unless you're just doing a simple class check.
 * Try to avoid the {@link PException#getUnreifiedException()} method unless you know what you're
 * doing.</li>
 * <li>{@link PException PException} must never be rethrown after it has been possibly exposed to
 * the program, because its Truffle stacktrace may already be frozen and it would not capture more
 * frames. If you need to rethrow without the catching site appearing in the traceback, use
 * {@link PException#getExceptionForReraise(boolean)} method to obtain a fresh {@link PException
 * PException} to throw</li>
 * </ul>
 * </p>
 */
@GenerateUncached
@GenerateInline(inlineByDefault = true)
@GenerateCached
public abstract class MaterializeLazyTracebackNode extends Node {
    public abstract PTraceback execute(Node inliningTarget, LazyTraceback tb);

    public final PTraceback executeCached(LazyTraceback tb) {
        return execute(this, tb);
    }

    public static PTraceback executeUncached(LazyTraceback tb) {
        return MaterializeLazyTracebackNodeGen.getUncached().execute(null, tb);
    }

    @Specialization(guards = "tb.isMaterialized()")
    static PTraceback getMaterialized(LazyTraceback tb) {
        return tb.getTraceback();
    }

    @Fallback
    static PTraceback getTraceback(Node inliningTarget, LazyTraceback tb,
                    @Cached(inline = false) PythonObjectFactory factory,
                    @Cached InlinedLoopConditionProfile loopConditionProfile) {
        PTraceback newTraceback = null;
        LazyTraceback current = tb;
        do {
            if (!current.isEmptySegment()) {
                if (current.isMaterialized()) {
                    newTraceback = current.getTraceback();
                } else {
                    newTraceback = factory.createTraceback(current);
                }
                break;
            }
            current = current.getNextChain();
        } while (loopConditionProfile.profile(inliningTarget, current != null));
        tb.setTraceback(newTraceback);
        return newTraceback;
    }

    @NeverDefault
    public static MaterializeLazyTracebackNode create() {
        return MaterializeLazyTracebackNodeGen.create();
    }
}

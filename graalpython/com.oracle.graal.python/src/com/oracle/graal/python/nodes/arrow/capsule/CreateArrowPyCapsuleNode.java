/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.arrow.capsule;

import com.oracle.graal.python.builtins.modules.cext.PythonCextCapsuleBuiltins.PyCapsuleNewNode;
import com.oracle.graal.python.builtins.objects.capsule.PyCapsule;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.arrow.ArrowArray;
import com.oracle.graal.python.nodes.arrow.ArrowSchema;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

@GenerateCached(false)
@GenerateInline
public abstract class CreateArrowPyCapsuleNode extends PNodeWithContext {

    public abstract PTuple execute(Node inliningTarget, ArrowArray arrowArray, ArrowSchema arrowSchema);

    @Specialization
    static PTuple doArrowCArray(Node inliningTarget, ArrowArray arrowArray, ArrowSchema arrowSchema,
                    @Cached PyCapsuleNewNode pyCapsuleNewNode,
                    @Cached(inline = false) PythonObjectFactory pythonObjectFactory) {
        var ctx = getContext(inliningTarget);
        long arrayDestructor = ctx.arrowSupport.getArrowArrayDestructor();
        var arrayCapsuleName = new CArrayWrappers.CByteArrayWrapper(ArrowArray.CAPSULE_NAME);
        PyCapsule arrowArrayCapsule = pyCapsuleNewNode.execute(inliningTarget, arrowArray.memoryAddr, arrayCapsuleName, arrayDestructor);

        long schemaDestructor = ctx.arrowSupport.getArrowSchemaDestructor();
        var schemaCapsuleName = new CArrayWrappers.CByteArrayWrapper(ArrowSchema.CAPSULE_NAME);
        PyCapsule arrowSchemaCapsule = pyCapsuleNewNode.execute(inliningTarget, arrowSchema.memoryAddr, schemaCapsuleName, schemaDestructor);

        return pythonObjectFactory.createTuple(new Object[]{arrowSchemaCapsule, arrowArrayCapsule});
    }
}
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
package com.oracle.graal.python.nodes.bytecode_dsl;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.AttributeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.GeneratorExit;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RecursionError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___ANNOTATIONS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___AENTER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___AEXIT__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AssertionError;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.FormatNode;
import com.oracle.graal.python.builtins.modules.MarshalModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.asyncio.GetAwaitableNode;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.EmptyStorage;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ListGeneralizationNode;
import com.oracle.graal.python.builtins.objects.dict.DictBuiltins;
import com.oracle.graal.python.builtins.objects.dict.DictNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.ChainExceptionsNode;
import com.oracle.graal.python.builtins.objects.exception.ExceptionNodes;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.exception.StopIterationBuiltins;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.generator.CommonGeneratorBuiltins;
import com.oracle.graal.python.builtins.objects.generator.PGenerator;
import com.oracle.graal.python.builtins.objects.iterator.PDoubleSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PIntRangeIterator;
import com.oracle.graal.python.builtins.objects.iterator.PIntegerIterator;
import com.oracle.graal.python.builtins.objects.iterator.PIntegerSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PLongSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PObjectSequenceIterator;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.set.PFrozenSet;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.set.SetNodes;
import com.oracle.graal.python.builtins.objects.str.StringUtils;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetCachedTpSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.CallSlotBinaryFuncNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.BinaryOpSlot;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSizeArgFun.CallSlotSizeArgFun;
import com.oracle.graal.python.compiler.CodeUnit;
import com.oracle.graal.python.compiler.RaisePythonExceptionErrorCallback;
import com.oracle.graal.python.lib.CallBinaryOp1Node;
import com.oracle.graal.python.lib.GetNextNode;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyIterCheckNode;
import com.oracle.graal.python.lib.PyNumberAddNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyNumberMultiplyNode;
import com.oracle.graal.python.lib.PyObjectAsciiNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectDelItem;
import com.oracle.graal.python.lib.PyObjectFunctionStr;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectGetMethod;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.lib.PyObjectSetAttr;
import com.oracle.graal.python.lib.PyObjectSetItem;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.lib.PyObjectStrAsTruffleStringNode;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.WriteUnraisableNode;
import com.oracle.graal.python.nodes.argument.keywords.ConcatDictToStorageNode;
import com.oracle.graal.python.nodes.argument.keywords.ExpandKeywordStarargsNode;
import com.oracle.graal.python.nodes.argument.keywords.NonMappingException;
import com.oracle.graal.python.nodes.argument.keywords.SameDictKeyException;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode.GetFixedAttributeNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.bytecode.GetSendValueNode;
import com.oracle.graal.python.nodes.bytecode.GetTPFlagsNode;
import com.oracle.graal.python.nodes.bytecode.GetYieldFromIterNode;
import com.oracle.graal.python.nodes.bytecode.ImportFromNode;
import com.oracle.graal.python.nodes.bytecode.ImportNode;
import com.oracle.graal.python.nodes.bytecode.ImportStarNode;
import com.oracle.graal.python.nodes.bytecode.PrintExprNode;
import com.oracle.graal.python.nodes.bytecode.RaiseNode;
import com.oracle.graal.python.nodes.bytecode.SetupAnnotationsNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallQuaternaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.exception.ExceptMatchNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.BitAndNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.BitOrNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.BitXorNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.FloorDivNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.LShiftNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.MatMulNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.ModNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.PowNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.RShiftNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.SubNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.TrueDivNode;
import com.oracle.graal.python.nodes.expression.CoerceToBooleanNode.NotNode;
import com.oracle.graal.python.nodes.expression.CoerceToBooleanNode.YesNode;
import com.oracle.graal.python.nodes.expression.ContainsNode;
import com.oracle.graal.python.nodes.expression.InplaceArithmetic;
import com.oracle.graal.python.nodes.expression.LookupAndCallInplaceNode;
import com.oracle.graal.python.nodes.expression.UnaryArithmetic.InvertNode;
import com.oracle.graal.python.nodes.expression.UnaryArithmetic.NegNode;
import com.oracle.graal.python.nodes.expression.UnaryArithmetic.PosNode;
import com.oracle.graal.python.nodes.frame.DeleteGlobalNode;
import com.oracle.graal.python.nodes.frame.GetFrameLocalsNode;
import com.oracle.graal.python.nodes.frame.MaterializeFrameNode;
import com.oracle.graal.python.nodes.frame.ReadFromLocalsNode;
import com.oracle.graal.python.nodes.frame.ReadGlobalOrBuiltinNode;
import com.oracle.graal.python.nodes.frame.ReadNameNode;
import com.oracle.graal.python.nodes.frame.WriteGlobalNode;
import com.oracle.graal.python.nodes.frame.WriteNameNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetClassNode.GetPythonObjectClassNode;
import com.oracle.graal.python.nodes.object.IsNode;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes;
import com.oracle.graal.python.runtime.ExecutionContext.CalleeContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.ProfileEvent;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.PythonContext.TraceEvent;
import com.oracle.graal.python.runtime.exception.ExceptionUtils;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.BoolSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocation;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.EpilogExceptional;
import com.oracle.truffle.api.bytecode.EpilogReturn;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.bytecode.Instrumentation;
import com.oracle.truffle.api.bytecode.LocalAccessor;
import com.oracle.truffle.api.bytecode.LocalRangeAccessor;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.bytecode.Prolog;
import com.oracle.truffle.api.bytecode.ShortCircuitOperation;
import com.oracle.truffle.api.bytecode.ShortCircuitOperation.Operator;
import com.oracle.truffle.api.bytecode.Variadic;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NonIdempotent;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

@GenerateBytecode(//
                languageClass = PythonLanguage.class, //
                enableBlockScoping = false, //
                enableYield = true, //
                enableSerialization = true, //
                enableTagInstrumentation = true, //
                boxingEliminationTypes = {int.class, boolean.class}, //
                storeBytecodeIndexInFrame = true //
)
@TypeSystemReference(PythonTypes.class)
@OperationProxy(SubNode.class)
@OperationProxy(TrueDivNode.class)
@OperationProxy(FloorDivNode.class)
@OperationProxy(ModNode.class)
@OperationProxy(LShiftNode.class)
@OperationProxy(RShiftNode.class)
@OperationProxy(BitAndNode.class)
@OperationProxy(BitOrNode.class)
@OperationProxy(BitXorNode.class)
@OperationProxy(MatMulNode.class)
@OperationProxy(PowNode.class)
@OperationProxy(PosNode.class)
@OperationProxy(NegNode.class)
@OperationProxy(InvertNode.class)
@OperationProxy(IsNode.class)
@OperationProxy(ContainsNode.class)
@OperationProxy(FormatNode.class)
@OperationProxy(ExceptMatchNode.class)
@OperationProxy(GetYieldFromIterNode.class)
@OperationProxy(GetAwaitableNode.class)
@OperationProxy(SetupAnnotationsNode.class)
@OperationProxy(value = ListNodes.AppendNode.class, name = "ListAppend")
@OperationProxy(value = SetNodes.AddNode.class, name = "SetAdd")
@ShortCircuitOperation(name = "BoolAnd", booleanConverter = PBytecodeDSLRootNode.Yes.class, operator = Operator.AND_RETURN_VALUE)
@ShortCircuitOperation(name = "BoolOr", booleanConverter = PBytecodeDSLRootNode.Yes.class, operator = Operator.OR_RETURN_VALUE)
@ShortCircuitOperation(name = "PrimitiveBoolAnd", operator = Operator.AND_RETURN_VALUE)
@SuppressWarnings("unused")
public abstract class PBytecodeDSLRootNode extends PRootNode implements BytecodeRootNode {
    public static final int EXPLODE_LOOP_THRESHOLD = 32;
    private static final BytecodeConfig TRACE_AND_PROFILE_CONFIG = PBytecodeDSLRootNodeGen.newConfigBuilder() //
                    .addInstrumentation(TraceOrProfileCall.class) //
                    .addInstrumentation(TraceLine.class) //
                    .addInstrumentation(TraceLineAtLoopHeader.class) //
                    .addInstrumentation(TraceOrProfileReturn.class) //
                    .addInstrumentation(TraceException.class) //
                    .build();

    @Child protected transient PythonObjectFactory factory = PythonObjectFactory.create();
    @Child private transient CalleeContext calleeContext = CalleeContext.create();
    @Child private transient ExceptionStateNodes.GetCaughtExceptionNode getCaughtExceptionNode;
    @Child private transient MaterializeFrameNode traceMaterializeFrameNode = null;
    @Child private transient ChainExceptionsNode chainExceptionsNode;

    // These fields are effectively final, but can only be set after construction.
    @CompilationFinal protected transient BytecodeDSLCodeUnit co;
    @CompilationFinal protected transient Signature signature;
    @CompilationFinal protected transient int selfIndex;
    @CompilationFinal protected transient int classcellIndex;

    private transient boolean pythonInternal;
    @CompilationFinal private transient boolean internal;

    // For deferred deprecation warnings
    @CompilationFinal private transient RaisePythonExceptionErrorCallback parserErrorCallback;

    @SuppressWarnings("this-escape")
    protected PBytecodeDSLRootNode(PythonLanguage language, FrameDescriptor.Builder frameDescriptorBuilder) {
        super(language, frameDescriptorBuilder.info(new BytecodeDSLFrameInfo()).build());
        ((BytecodeDSLFrameInfo) getFrameDescriptor().getInfo()).setRootNode(this);
    }

    public PythonObjectFactory getFactory() {
        return factory;
    }

    public void setMetadata(BytecodeDSLCodeUnit co, RaisePythonExceptionErrorCallback parserErrorCallback) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        this.co = co;
        this.signature = co.computeSignature();
        this.classcellIndex = co.classcellIndex;
        this.selfIndex = co.selfIndex;
        this.internal = getSource().isInternal();
        this.parserErrorCallback = parserErrorCallback;
    }

    @Override
    public boolean isInternal() {
        return internal;
    }

    @Override
    public boolean isPythonInternal() {
        return pythonInternal;
    }

    public void setPythonInternal(boolean pythonInternal) {
        this.pythonInternal = pythonInternal;
    }

    public void triggerDeferredDeprecationWarnings() {
        if (parserErrorCallback != null) {
            parserErrorCallback.triggerDeprecationWarnings();
        }
    }

    @Override
    public String toString() {
        return "<bytecode " + co.name + " at " + Integer.toHexString(hashCode()) + ">";
    }

    @Prolog
    public static final class EnterCalleeContext {
        @Specialization
        public static void doEnter(VirtualFrame frame,
                        @Bind PBytecodeDSLRootNode root) {
            root.calleeContext.enter(frame);

            if (root.needsTraceAndProfileInstrumentation()) {
                root.ensureTraceAndProfileEnabled();
                root.getThreadState().pushInstrumentationData(root);
            }
        }
    }

    @EpilogReturn
    public static final class EpilogForReturn {
        @Specialization
        public static Object doExit(VirtualFrame frame, Object returnValue,
                        @Bind PBytecodeDSLRootNode root,
                        @Bind Node location) {
            if (root.needsTraceAndProfileInstrumentation()) {
                root.getThreadState().popInstrumentationData(root);
            }

            root.calleeContext.exit(frame, root, location);
            return returnValue;
        }
    }

    @EpilogExceptional
    public static final class EpilogForException {
        @Specialization
        public static void doExit(VirtualFrame frame, AbstractTruffleException ate,
                        @Bind PBytecodeDSLRootNode root,
                        @Bind Node location) {
            if (ate instanceof PException pe) {
                pe.notifyAddedTracebackFrame(!root.isInternal());
            }

            if (root.needsTraceAndProfileInstrumentation()) {
                root.traceOrProfileReturn(frame, location, null);
                root.getThreadState().popInstrumentationData(root);
            }

            root.calleeContext.exit(frame, root, location);
        }
    }

    /*
     * Data for tracing, profiling and instrumentation
     */
    public static final class InstrumentationData {
        private final InstrumentationData previous;
        private final PBytecodeDSLRootNode rootNode;
        private int pastLine;

        public InstrumentationData(PBytecodeDSLRootNode rootNode, InstrumentationData previous) {
            this.previous = previous;
            this.rootNode = rootNode;
            this.pastLine = -1;
        }

        public InstrumentationData getPrevious() {
            return previous;
        }

        public PBytecodeDSLRootNode getRootNode() {
            return rootNode;
        }

        int getPastLine() {
            return pastLine;
        }

        void setPastLine(int pastLine) {
            this.pastLine = pastLine;
        }

        void clearPastLine() {
            this.pastLine = -1;
        }
    }

    @NonIdempotent
    public boolean needsTraceAndProfileInstrumentation() {
        // We need instrumentation only if the assumption is invalid and the root node is visible.
        return !PythonLanguage.get(this).noTracingOrProfilingAssumption.isValid() && !isInternal();
    }

    @NonIdempotent
    public PythonThreadState getThreadState() {
        return PythonContext.get(this).getThreadState(PythonLanguage.get(this));
    }

    /**
     * Reparses with instrumentations for settrace and setprofile enabled.
     */
    public void ensureTraceAndProfileEnabled() {
        assert !isInternal();
        getRootNodes().update(TRACE_AND_PROFILE_CONFIG);
    }

    private PFrame ensurePyFrame(VirtualFrame frame, Node location) {
        if (traceMaterializeFrameNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            traceMaterializeFrameNode = insert(MaterializeFrameNode.create());
        }
        return traceMaterializeFrameNode.execute(frame, location, true, true);
    }

    private void syncLocalsBackToFrame(VirtualFrame frame, PFrame pyFrame) {
        GetFrameLocalsNode.syncLocalsBackToFrame(co, this, pyFrame, frame);
    }

    /**
     * When tracing/profiling is enabled, we emit a lot of extra operations. Reduce compiled code
     * size by putting the calls behind a boundary (the uncached invoke will eventually perform an
     * indirect call anyway).
     */
    @TruffleBoundary
    private static Object doInvokeProfileOrTraceFunction(Object fun, PFrame pyFrame, TruffleString eventName, Object arg) {
        return CallTernaryMethodNode.getUncached().execute(null, fun, pyFrame, eventName, arg == null ? PNone.NONE : arg);
    }

    @InliningCutoff
    private void invokeProfileFunction(VirtualFrame virtualFrame, Node location, Object profileFun,
                    PythonContext.PythonThreadState threadState, PythonContext.ProfileEvent event, Object arg) {
        if (threadState.isProfiling()) {
            return;
        }
        threadState.profilingStart();
        PFrame pyFrame = ensurePyFrame(virtualFrame, location);
        EncapsulatingNodeReference encapsulating = EncapsulatingNodeReference.getCurrent();
        Node oldEncapsulatingNode = encapsulating.set(location);
        try {
            // Force locals dict sync, so that we can sync them back later
            GetFrameLocalsNode.executeUncached(pyFrame);
            Object result = doInvokeProfileOrTraceFunction(profileFun, pyFrame, event.name, arg);
            syncLocalsBackToFrame(virtualFrame, pyFrame);
            Object realResult = result == PNone.NONE ? null : result;
            pyFrame.setLocalTraceFun(realResult);
        } catch (Throwable e) {
            threadState.setProfileFun(null, PythonLanguage.get(this));
            throw e;
        } finally {
            threadState.profilingStop();
            encapsulating.set(oldEncapsulatingNode);
        }
    }

    @InliningCutoff
    private void invokeTraceFunction(VirtualFrame virtualFrame, Node location, Object traceFun, PythonContext.PythonThreadState threadState,
                    PythonContext.TraceEvent event, Object arg, int line) {
        if (threadState.isTracing()) {
            return;
        }
        assert event != PythonContext.TraceEvent.DISABLED;
        threadState.tracingStart(event);
        PFrame pyFrame = ensurePyFrame(virtualFrame, location);
        /**
         * Call events use the thread-local trace function, which returns a "local" trace function
         * to use for other trace events.
         */
        boolean useLocalFn = event != TraceEvent.CALL;
        Object traceFn = useLocalFn ? pyFrame.getLocalTraceFun() : traceFun;
        if (traceFn == null) {
            threadState.tracingStop();
            return;
        }
        Object nonNullArg = arg == null ? PNone.NONE : arg;

        EncapsulatingNodeReference encapsulating = EncapsulatingNodeReference.getCurrent();
        Node oldEncapsulatingNode = encapsulating.set(location);
        try {
            /**
             * The PFrame syncs to the line of the current bci. Sometimes this location is
             * inaccurate and needs to be overridden (e.g., when tracing an implicit return at the
             * end of a function, we need to trace the line of the last statement executed).
             */
            if (line != -1) {
                pyFrame.setLineLock(line);
            }

            // Force locals dict sync, so that we can sync them back later
            GetFrameLocalsNode.executeUncached(pyFrame);
            Object result = doInvokeProfileOrTraceFunction(traceFn, pyFrame, event.pythonName, nonNullArg);
            syncLocalsBackToFrame(virtualFrame, pyFrame);
            // https://github.com/python/cpython/issues/104232
            if (useLocalFn) {
                Object realResult = result == PNone.NONE ? traceFn : result;
                pyFrame.setLocalTraceFun(realResult);
            } else if (result != PNone.NONE) {
                pyFrame.setLocalTraceFun(result);
            } else {
                pyFrame.setLocalTraceFun(null);
            }
        } catch (Throwable e) {
            threadState.setTraceFun(null, PythonLanguage.get(this));
            throw e;
        } finally {
            if (line != -1) {
                pyFrame.lineUnlock();
            }
            threadState.tracingStop();
            encapsulating.set(oldEncapsulatingNode);
        }
    }

    private final void traceOrProfileCall(VirtualFrame frame, Node location, BytecodeNode bytecode, int bci) {
        PythonThreadState threadState = getThreadState();
        Object traceFun = threadState.getTraceFun();
        if (traceFun != null) {
            int line = bciToLine(bci, bytecode);
            invokeTraceFunction(frame, location, traceFun, threadState, TraceEvent.CALL, null, line);
        }
        Object profileFun = threadState.getProfileFun();
        if (profileFun != null) {
            invokeProfileFunction(frame, location, profileFun, threadState, ProfileEvent.CALL, null);
        }
    }

    @InliningCutoff
    private final void traceLine(VirtualFrame frame, Node location, int line) {
        PythonThreadState threadState = getThreadState();
        InstrumentationData instrumentationData = threadState.getInstrumentationData(this);

        // TODO: this should never happen by nature of how we emit TraceLine, but sometimes does.
        // needs investigation.
        if (line == instrumentationData.getPastLine()) {
            return;
        }
        instrumentationData.setPastLine(line);

        PFrame pyFrame = ensurePyFrame(frame, location);
        if (pyFrame.getTraceLine()) {
            Object traceFun = threadState.getTraceFun();
            if (traceFun != null) {
                invokeTraceFunction(frame, location, traceFun, threadState, TraceEvent.LINE, null, line);
            }
        }
    }

    @InliningCutoff
    private final void traceLineAtLoopHeader(VirtualFrame frame, Node location, int line) {
        PythonThreadState threadState = getThreadState();
        InstrumentationData instrumentationData = threadState.getInstrumentationData(this);
        int pastLine = instrumentationData.getPastLine();

        /**
         * A loop should always be traced once, even if it is not entered. We also need to trace the
         * loop header on each iteration. To accomplish this, we emit a TraceLine at the top of each
         * loop (before loop initialization) and a TraceLineAtLoopHeader before the loop condition
         * evaluates. To avoid tracing twice on the first iteration, we need to check our line
         * against pastLine.
         */
        if (line != pastLine) {
            Object traceFun = threadState.getTraceFun();
            if (traceFun != null) {
                invokeTraceFunction(frame, location, traceFun, threadState, TraceEvent.LINE, null, line);
            }
        }
        /**
         * If the loop is all on one line, we need to trace on each iteration (even though the line
         * hasn't changed). Clear pastLine so the line comparison above succeeds.
         */
        instrumentationData.clearPastLine();
    }

    private final void traceOrProfileReturn(VirtualFrame frame, Node location, Object value) {
        PythonThreadState threadState = getThreadState();
        Object traceFun = threadState.getTraceFun();
        if (traceFun != null) {
            invokeTraceFunction(frame, location, traceFun, threadState, TraceEvent.RETURN, value, threadState.getInstrumentationData(this).getPastLine());
        }
        Object profileFun = threadState.getProfileFun();
        if (profileFun != null) {
            invokeProfileFunction(frame, location, profileFun, threadState, ProfileEvent.RETURN, value);
        }
    }

    @InliningCutoff
    private final PException traceException(VirtualFrame frame, BytecodeNode bytecode, int bci, PException pe) {
        PException result = pe;

        PythonThreadState threadState = getThreadState();
        // We should only trace the exception if tracing is enabled.
        if (threadState.getTraceFun() != null) {
            PFrame pyFrame = ensurePyFrame(frame, bytecode);
            // We use the local function for tracing exceptions.
            if (pyFrame.getLocalTraceFun() != null) {
                pe.markAsCaught(frame, this);
                Object peForPython = pe.getEscapedException();
                Object peType = GetClassNode.executeUncached(peForPython);
                Object traceback = ExceptionNodes.GetTracebackNode.executeUncached(peForPython);
                try {
                    invokeTraceFunction(frame, bytecode, null, threadState, TraceEvent.EXCEPTION,
                                    factory.createTuple(new Object[]{peType, peForPython, traceback}),
                                    bciToLine(bci, bytecode));
                } catch (PException newPe) {
                    // If the trace function raises, handle its exception instead.
                    result = newPe;
                    // Below, we get the exception for reraise in order to hide the original
                    // raising site that's being traced (i.e., hiding the original cause).
                }
                // The exception was reified already. Return a new exception that looks like this
                // catch didn't happen.
                result = result.getExceptionForReraise(!isInternal());
                result.setCatchLocation(bci, bytecode);
            }
        }
        return result;
    }

    @Instrumentation
    public static final class TraceOrProfileCall {
        @Specialization
        public static void perform(VirtualFrame frame,
                        @Bind Node location,
                        @Bind PBytecodeDSLRootNode root,
                        @Bind BytecodeNode bytecode,
                        @Bind("$bytecodeIndex") int bci) {
            root.traceOrProfileCall(frame, location, bytecode, bci);
        }
    }

    @Instrumentation
    @ConstantOperand(type = int.class)
    public static final class TraceLine {
        @Specialization
        public static void perform(VirtualFrame frame,
                        int line,
                        @Bind Node location,
                        @Bind PBytecodeDSLRootNode root) {
            root.traceLine(frame, location, line);
        }
    }

    @Instrumentation
    @ConstantOperand(type = int.class)
    public static final class TraceLineAtLoopHeader {
        @Specialization
        public static void perform(VirtualFrame frame,
                        int line,
                        @Bind Node location,
                        @Bind PBytecodeDSLRootNode root) {
            root.traceLineAtLoopHeader(frame, location, line);
        }
    }

    @Instrumentation
    public static final class TraceOrProfileReturn {
        @Specialization
        public static Object perform(VirtualFrame frame, Object value,
                        @Bind Node location,
                        @Bind PBytecodeDSLRootNode root) {
            root.traceOrProfileReturn(frame, location, value);
            return value;
        }
    }

    @Instrumentation
    public static final class TraceException {
        @Specialization
        public static void perform() {
            throw new UnsupportedOperationException("trace exception not implemented");
        }
    }

    @Override
    public Throwable interceptInternalException(Throwable throwable, VirtualFrame frame, BytecodeNode bytecodeNode, int bci) {
        if (throwable instanceof StackOverflowError soe) {
            PythonContext.get(this).ensureGilAfterFailure();
            return ExceptionUtils.wrapJavaException(soe, this, factory.createBaseException(RecursionError, ErrorMessages.MAXIMUM_RECURSION_DEPTH_EXCEEDED, new Object[]{}));
        }
        return throwable;
    }

    @Override
    public AbstractTruffleException interceptTruffleException(AbstractTruffleException ex, VirtualFrame frame, BytecodeNode bytecodeNode, int bci) {
        if (ex instanceof PException pe) {
            pe.setCatchLocation(bci, bytecodeNode);

            if (needsTraceAndProfileInstrumentation() && !getThreadState().isTracing()) {
                pe = traceException(frame, bytecodeNode, bci, pe);
            }

            // Fill in the __context__, if available.
            if (getCaughtExceptionNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getCaughtExceptionNode = insert(ExceptionStateNodes.GetCaughtExceptionNode.create());
            }
            AbstractTruffleException context = getCaughtExceptionNode.execute(frame);
            if (context instanceof PException pe2) {
                if (chainExceptionsNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    chainExceptionsNode = insert(ChainExceptionsNode.create());
                }
                chainExceptionsNode.execute(pe, pe2);
            }
            return pe;
        }

        return ex;
    }

    @Override
    public boolean setsUpCalleeContext() {
        return true;
    }

    @Override
    public String getName() {
        if (co == null) {
            // getName can be called by validation code before the code unit has been set.
            return null;
        }
        return co.name.toJavaStringUncached();
    }

    @Override
    public Signature getSignature() {
        return signature;
    }

    public BytecodeDSLCodeUnit getCodeUnit() {
        return co;
    }

    public int getFirstLineno() {
        return co.startLine;
    }

    protected Source getSource() {
        SourceSection section = getSourceSection();
        if (section == null) {
            return PythonUtils.createFakeSource();
        }
        return section.getSource();
    }

    @TruffleBoundary
    public int bciToLine(int bci, BytecodeNode bytecodeNode) {
        return getSourceSectionForLocation(bci, bytecodeNode).getStartLine();
    }

    @TruffleBoundary
    public SourceSection getSourceSectionForLocation(BytecodeLocation location) {
        SourceSection sourceSection = null;
        if (location != null) {
            sourceSection = location.getSourceLocation();
        }

        if (sourceSection == null) {
            /**
             * If we don't have a source section, fall back on the root node's source section. This
             * can happen, for example, when throwing into an unstarted generator, where we don't
             * have an actual location (and the first line of the root node is expected).
             */
            sourceSection = getSourceSection();
        }

        return sourceSection;
    }

    @TruffleBoundary
    public SourceSection getSourceSectionForLocation(int bci, BytecodeNode bytecodeNode) {
        BytecodeLocation location = null;
        if (bytecodeNode != null) {
            location = bytecodeNode.getBytecodeLocation(bci);
        }
        return getSourceSectionForLocation(location);
    }

    public static int bciToLasti(int bci, BytecodeNode bytecodeNode) {
        if (bci <= 0) {
            return bci;
        }
        int number = 0;
        for (Instruction instruction : bytecodeNode.getInstructions()) {
            if (instruction.isInstrumentation()) {
                continue;
            }
            if (instruction.getBytecodeIndex() >= bci) {
                return number;
            }
            // Emulate CPython's fixed 2-word instructions.
            number += 2;
        }
        return -1;
    }

    public static int lastiToBci(int lasti, BytecodeNode bytecodeNode) {
        if (lasti < 0) {
            return 0;
        }
        Iterator<Instruction> iter = bytecodeNode.getInstructions().iterator();
        assert iter.hasNext();

        int nexti = 0;
        Instruction result;
        do {
            result = iter.next();
            if (result.isInstrumentation()) {
                continue;
            }
            nexti += 2;
        } while (nexti <= lasti && iter.hasNext());
        return result.getBytecodeIndex();
    }

    @Override
    protected byte[] extractCode() {
        return MarshalModuleBuiltins.serializeCodeUnit(co);
    }

    private static Object checkUnboundCell(PCell cell, int index, PBytecodeDSLRootNode rootNode, Node inliningTarget, PRaiseNode.Lazy raiseNode) {
        Object result = cell.getRef();
        if (result == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            CodeUnit codeUnit = rootNode.getCodeUnit();
            if (index < codeUnit.cellvars.length) {
                TruffleString localName = codeUnit.cellvars[index];
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.UnboundLocalError, ErrorMessages.LOCAL_VAR_REFERENCED_BEFORE_ASSIGMENT, localName);
            } else {
                TruffleString localName = codeUnit.freevars[index - codeUnit.cellvars.length];
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.NameError, ErrorMessages.UNBOUNDFREEVAR, localName);
            }
        }
        return result;
    }

    public PCell readClassCell(Frame frame) {
        if (classcellIndex < 0) {
            return null;
        }
        return (PCell) getBytecodeNode().getLocalValue(0, frame, classcellIndex);
    }

    public Object readSelf(Frame frame) {
        if (selfIndex < 0) {
            return null;
        } else if (selfIndex == 0) {
            return getBytecodeNode().getLocalValue(0, frame, 0);
        } else {
            PCell selfCell = (PCell) getBytecodeNode().getLocalValue(0, frame, selfIndex);
            return selfCell.getRef();
        }
    }

    @Operation
    @ConstantOperand(type = int.class)
    public static final class ArrayIndex {
        @Specialization
        public static Object doObject(int i, Object[] array) {
            return array[i];
        }
    }

    @Operation
    public static final class UnwrapException {
        @Specialization
        public static Object doPException(PException ex) {
            return ex.getEscapedException();
        }

        @Fallback
        public static Object doOther(Object ex) {
            // Let interop exceptions be
            assert ex instanceof AbstractTruffleException;
            return ex;
        }
    }

    @Operation
    public static final class Yes {
        @Specialization
        public static boolean perform(VirtualFrame frame, Object o,
                        @Bind Node inliningTarget,
                        @Cached YesNode yesNode) {
            return yesNode.executeBoolean(frame, inliningTarget, o);
        }
    }

    @Operation
    public static final class Not {
        @Specialization
        public static boolean perform(VirtualFrame frame, Object o,
                        @Bind Node inliningTarget,
                        @Cached NotNode notNode) {
            return notNode.executeBoolean(frame, inliningTarget, o);
        }
    }

    /**
     * Some operations take a single Object[] operand (e.g., {@link MakeTuple}). To pass a
     * fixed-length sequence of elements to these operands (e.g., to instantiate a constant tuple)
     * we need to first collect the values into an Object[].
     */
    @Operation
    public static final class CollectToObjectArray {
        @Specialization
        public static Object[] perform(@Variadic Object[] values) {
            return values;
        }
    }

    @Operation
    @ConstantOperand(type = TruffleString.class)
    public static final class WriteName {
        @Specialization
        public static void perform(VirtualFrame frame, TruffleString name, Object value,
                        @Cached WriteNameNode writeNode) {
            writeNode.execute(frame, name, value);
        }
    }

    @Operation
    @ConstantOperand(type = TruffleString.class)
    public static final class ReadName {
        @Specialization
        public static Object perform(VirtualFrame frame, TruffleString name,
                        @Cached ReadNameNode readNode) {
            return readNode.execute(frame, name);
        }
    }

    @Operation
    @ConstantOperand(type = TruffleString.class)
    public static final class DeleteName {
        @Specialization(guards = "hasLocals(frame)")
        public static void performLocals(VirtualFrame frame, TruffleString name,
                        @Bind Node inliningTarget,
                        @Cached PyObjectDelItem deleteNode) {
            deleteNode.execute(frame, inliningTarget, PArguments.getSpecialArgument(frame), name);
        }

        @Specialization(guards = "!hasLocals(frame)")
        public static void performGlobals(VirtualFrame frame, TruffleString name,
                        @Cached DeleteGlobalNode deleteNode) {
            deleteNode.executeWithGlobals(frame, PArguments.getGlobals(frame), name);
        }

        public static boolean hasLocals(VirtualFrame frame) {
            return PArguments.getSpecialArgument(frame) != null;
        }
    }

    @Operation
    public static final class LoadVariableArguments {
        @Specialization
        public static Object perform(VirtualFrame frame,
                        @Bind PBytecodeDSLRootNode rootNode) {
            return rootNode.factory.createTuple(PArguments.getVariableArguments(frame));
        }
    }

    @Operation
    public static final class LoadKeywordArguments {
        @Specialization
        public static Object perform(VirtualFrame frame,
                        @Bind PBytecodeDSLRootNode rootNode) {
            return rootNode.factory.createDict(PArguments.getKeywordArguments(frame));
        }
    }

    @Operation
    @ConstantOperand(type = double.class)
    @ConstantOperand(type = double.class)
    public static final class LoadComplex {
        @Specialization
        public static Object perform(double real, double imag,
                        @Bind PBytecodeDSLRootNode rootNode) {
            return rootNode.factory.createComplex(real, imag);
        }
    }

    @Operation
    @ConstantOperand(type = BigInteger.class)
    public static final class LoadBigInt {
        @Specialization
        public static Object perform(BigInteger bigInt,
                        @Bind PBytecodeDSLRootNode rootNode) {
            return rootNode.factory.createInt(bigInt);
        }
    }

    @Operation
    @ConstantOperand(type = byte[].class, dimensions = 0)
    public static final class LoadBytes {
        @Specialization
        public static Object perform(byte[] bytes,
                        @Bind PBytecodeDSLRootNode rootNode) {
            return rootNode.factory.createBytes(bytes);
        }
    }

    @Operation
    @ImportStatic({PGuards.class})
    public static final class Add {
        /*
         * All the following fast paths need to be kept in sync with the corresponding builtin
         * functions in IntBuiltins, FloatBuiltins, ListBuiltins, ...
         */

        @Specialization(rewriteOn = ArithmeticException.class)
        public static int add(int left, int right) {
            return Math.addExact(left, right);
        }

        @Specialization
        public static long doIIOvf(int x, int y) {
            return x + (long) y;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        public static long addLong(long left, long right) {
            return Math.addExact(left, right);
        }

        @Specialization
        public static double doDD(double left, double right) {
            return left + right;
        }

        @Specialization
        public static double doDL(double left, long right) {
            return left + right;
        }

        @Specialization
        public static double doLD(long left, double right) {
            return left + right;
        }

        @Specialization
        public static double doDI(double left, int right) {
            return left + right;
        }

        @Specialization
        public static double doID(int left, double right) {
            return left + right;
        }

        @NeverDefault
        protected static SequenceStorageNodes.ConcatNode createConcat() {
            return SequenceStorageNodes.ConcatNode.create(ListGeneralizationNode::create);
        }

        @Specialization
        static PList doPList(PList left, PList right,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached GetClassNode getClassNode,
                        @Shared @Cached(value = "createConcat()", inline = false) SequenceStorageNodes.ConcatNode concatNode,
                        @Shared @Cached(inline = false) PythonObjectFactory factory) {
            SequenceStorage newStore = concatNode.execute(left.getSequenceStorage(), right.getSequenceStorage());
            return factory.createList(getClassNode.execute(inliningTarget, left), newStore);
        }

        @Specialization(guards = {"isBuiltinTuple(left)", "isBuiltinTuple(right)"})
        static PTuple doTuple(PTuple left, PTuple right,
                        @Bind Node inliningTarget,
                        @Shared @Cached(value = "createConcat()", inline = false) SequenceStorageNodes.ConcatNode concatNode,
                        @Shared @Cached(inline = false) PythonObjectFactory factory) {
            SequenceStorage concatenated = concatNode.execute(left.getSequenceStorage(), right.getSequenceStorage());
            return factory.createTuple(concatenated);
        }

        @Specialization
        static TruffleString doIt(TruffleString left, TruffleString right,
                        @Cached(inline = false) TruffleString.ConcatNode concatNode) {
            return concatNode.execute(left, right, PythonUtils.TS_ENCODING, false);
        }

        @Fallback
        static Object doIt(VirtualFrame frame, Object v, Object w,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached GetClassNode getVClass,
                        @Cached GetCachedTpSlotsNode getVSlots,
                        @Cached GetCachedTpSlotsNode getWSlots,
                        @Exclusive @Cached GetClassNode getWClass,
                        @Cached CallBinaryOp1Node callBinaryOp1Node,
                        @Cached InlinedBranchProfile hasNbAddResult,
                        @Cached CallSlotBinaryFuncNode callBinarySlotNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object classV = getVClass.execute(inliningTarget, v);
            Object classW = getWClass.execute(inliningTarget, w);
            TpSlots slotsV = getVSlots.execute(inliningTarget, classV);
            TpSlots slotsW = getWSlots.execute(inliningTarget, classW);
            TpSlot slotV = slotsV.nb_add();
            TpSlot slotW = slotsW.nb_add();
            if (slotV != null || slotW != null) {
                Object result = callBinaryOp1Node.execute(frame, inliningTarget, v, classV, slotV, w, classW, slotW, BinaryOpSlot.NB_ADD);
                if (result != PNotImplemented.NOT_IMPLEMENTED) {
                    hasNbAddResult.enter(inliningTarget);
                    return result;
                }
            }
            if (slotsV.sq_concat() != null) {
                return callBinarySlotNode.execute(frame, inliningTarget, slotsV.sq_concat(), v, w);
            }
            return raiseNotSupported(inliningTarget, v, w, raiseNode);
        }

        @InliningCutoff
        private static PException raiseNotSupported(Node inliningTarget, Object v, Object w, PRaiseNode.Lazy raiseNode) {
            return raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.UNSUPPORTED_OPERAND_TYPES_FOR_S_P_AND_P, "+", v, w);
        }
    }

    @Operation
    @ImportStatic(PGuards.class)
    public static final class Mul {
        /*
         * All the following fast paths need to be kept in sync with the corresponding builtin
         * functions in IntBuiltins, FloatBuiltins, ListBuiltins, ...
         */

        @Specialization(rewriteOn = ArithmeticException.class)
        public static int doII(int x, int y) throws ArithmeticException {
            return Math.multiplyExact(x, y);
        }

        @Specialization(replaces = "doII")
        public static long doIIL(int x, int y) {
            return x * (long) y;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        public static long doLL(long x, long y) {
            return Math.multiplyExact(x, y);
        }

        @Specialization
        public static double doDL(double left, long right) {
            return left * right;
        }

        @Specialization
        public static double doLD(long left, double right) {
            return left * right;
        }

        @Specialization
        public static double doDD(double left, double right) {
            return left * right;
        }

        @Fallback
        static Object doIt(VirtualFrame frame, Object v, Object w,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached GetClassNode getVClass,
                        @Cached GetCachedTpSlotsNode getVSlots,
                        @Cached GetCachedTpSlotsNode getWSlots,
                        @Exclusive @Cached GetClassNode getWClass,
                        @Cached CallBinaryOp1Node callBinaryOp1Node,
                        @Cached InlinedBranchProfile hasNbMulResult,
                        @Cached InlinedBranchProfile vHasSqRepeat,
                        @Cached InlinedBranchProfile wHasSqRepeat,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached CallSlotSizeArgFun callSlotNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object classV = getVClass.execute(inliningTarget, v);
            Object classW = getWClass.execute(inliningTarget, w);
            TpSlots slotsV = getVSlots.execute(inliningTarget, classV);
            TpSlots slotsW = getWSlots.execute(inliningTarget, classW);
            TpSlot slotV = slotsV.nb_multiply();
            TpSlot slotW = slotsW.nb_multiply();
            if (slotV != null || slotW != null) {
                Object result = callBinaryOp1Node.execute(frame, inliningTarget, v, classV, slotV, w, classW, slotW, BinaryOpSlot.NB_MULTIPLY);
                if (result != PNotImplemented.NOT_IMPLEMENTED) {
                    hasNbMulResult.enter(inliningTarget);
                    return result;
                }
            }
            if (slotsV.sq_repeat() != null) {
                vHasSqRepeat.enter(inliningTarget);
                return sequenceRepeat(frame, inliningTarget, slotsV.sq_repeat(), v, w,
                                indexCheckNode, asSizeNode, callSlotNode, raiseNode);
            } else if (slotsW.sq_repeat() != null) {
                wHasSqRepeat.enter(inliningTarget);
                return sequenceRepeat(frame, inliningTarget, slotsW.sq_repeat(), w, v,
                                indexCheckNode, asSizeNode, callSlotNode, raiseNode);
            }
            return raiseNotSupported(inliningTarget, v, w, raiseNode);
        }

        @InliningCutoff
        private static PException raiseNotSupported(Node inliningTarget, Object v, Object w, PRaiseNode.Lazy raiseNode) {
            return raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.UNSUPPORTED_OPERAND_TYPES_FOR_S_P_AND_P, "+", v, w);
        }

        private static Object sequenceRepeat(VirtualFrame frame, Node inliningTarget, TpSlot slot, Object seq, Object n,
                        PyIndexCheckNode indexCheckNode,
                        PyNumberAsSizeNode asSizeNode,
                        CallSlotSizeArgFun callSlotNode,
                        PRaiseNode.Lazy raiseNode) {
            if (indexCheckNode.execute(inliningTarget, n)) {
                int count = asSizeNode.execute(frame, inliningTarget, n, PythonBuiltinClassType.OverflowError);
                return callSlotNode.execute(frame, inliningTarget, slot, seq, count);
            } else {
                throw raiseNonIntSqMul(inliningTarget, n, raiseNode);
            }
        }

        @InliningCutoff
        private static PException raiseNonIntSqMul(Node inliningTarget, Object n, PRaiseNode.Lazy raiseNode) {
            throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.CANT_MULTIPLY_SEQ_BY_NON_INT, n);
        }
    }

    @Operation
    public static final class GetIter {
        @Specialization
        public static Object perform(VirtualFrame frame, Object receiver,
                        @Bind Node inliningTarget,
                        @Cached PyObjectGetIter getIterNode) {
            return getIterNode.execute(frame, inliningTarget, receiver);
        }
    }

    @Operation
    public static final class FormatStr {
        @Specialization
        public static TruffleString perform(VirtualFrame frame, Object object,
                        @Bind Node inliningTarget,
                        @Cached PyObjectStrAsTruffleStringNode asTruffleStringNode) {
            return asTruffleStringNode.execute(frame, inliningTarget, object);
        }
    }

    @Operation
    public static final class FormatRepr {
        @Specialization
        public static TruffleString perform(VirtualFrame frame, Object object,
                        @Bind Node inliningTarget,
                        @Cached PyObjectReprAsTruffleStringNode asTruffleStringNode) {
            return asTruffleStringNode.execute(frame, inliningTarget, object);
        }
    }

    @Operation
    public static final class FormatAscii {
        @Specialization
        public static TruffleString perform(VirtualFrame frame, Object object,
                        @Bind Node inliningTarget,
                        @Cached PyObjectAsciiNode asTruffleStringNode) {
            return asTruffleStringNode.execute(frame, inliningTarget, object);
        }
    }

    @Operation
    public static final class PrintExpr {
        @Specialization
        public static void perform(VirtualFrame frame, Object object,
                        @Cached PrintExprNode printExprNode) {
            printExprNode.execute(frame, object);
        }
    }

    @Operation
    @ConstantOperand(type = TruffleString.class, name = "name")
    @ConstantOperand(type = TruffleString.class, name = "qualifiedName")
    @ConstantOperand(type = BytecodeDSLCodeUnit.class)
    public static final class MakeFunction {
        @Specialization(guards = {"isSingleContext(rootNode)", "!codeUnit.isGeneratorOrCoroutine()"})
        public static Object functionSingleContext(VirtualFrame frame,
                        TruffleString name,
                        TruffleString qualifiedName,
                        BytecodeDSLCodeUnit codeUnit,
                        Object[] defaults,
                        Object[] kwDefaultsObject,
                        Object closure,
                        Object annotations,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Cached(value = "createFunctionRootNode(rootNode, codeUnit)", adopt = false) PBytecodeDSLRootNode functionRootNode,
                        @Cached("createCode(rootNode, codeUnit, functionRootNode)") PCode cachedCode,
                        @Shared @CachedLibrary(limit = "1") DynamicObjectLibrary dylib) {
            return createFunction(frame, name, qualifiedName, codeUnit.getDocstring(), cachedCode, defaults, kwDefaultsObject, closure, annotations, rootNode, dylib);
        }

        @Specialization(replaces = "functionSingleContext", guards = "!codeUnit.isGeneratorOrCoroutine()")
        public static Object functionMultiContext(VirtualFrame frame,
                        TruffleString name,
                        TruffleString qualifiedName,
                        BytecodeDSLCodeUnit codeUnit,
                        Object[] defaults,
                        Object[] kwDefaultsObject,
                        Object closure,
                        Object annotations,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Cached(value = "createFunctionRootNode(rootNode, codeUnit)", adopt = false) PBytecodeDSLRootNode functionRootNode,
                        @Shared @CachedLibrary(limit = "1") DynamicObjectLibrary dylib) {
            PCode code = createCode(rootNode, codeUnit, functionRootNode);
            return createFunction(frame, name, qualifiedName, codeUnit.getDocstring(), code, defaults, kwDefaultsObject, closure, annotations, rootNode, dylib);
        }

        @Specialization(guards = {"isSingleContext(rootNode)", "codeUnit.isGeneratorOrCoroutine()"})
        public static Object generatorOrCoroutineSingleContext(VirtualFrame frame,
                        TruffleString name,
                        TruffleString qualifiedName,
                        BytecodeDSLCodeUnit codeUnit,
                        Object[] defaults,
                        Object[] kwDefaultsObject,
                        Object closure,
                        Object annotations,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Cached(value = "createFunctionRootNode(rootNode, codeUnit)", adopt = false) PBytecodeDSLRootNode functionRootNode,
                        @Cached(value = "createGeneratorRootNode(rootNode, functionRootNode, codeUnit)", adopt = false) PBytecodeDSLGeneratorFunctionRootNode generatorRootNode,
                        @Cached("createCode(rootNode, codeUnit, generatorRootNode)") PCode cachedCode,
                        @Shared @CachedLibrary(limit = "1") DynamicObjectLibrary dylib) {
            return createFunction(frame, name, qualifiedName, codeUnit.getDocstring(), cachedCode, defaults, kwDefaultsObject, closure, annotations, rootNode, dylib);
        }

        @Specialization(replaces = "generatorOrCoroutineSingleContext", guards = "codeUnit.isGeneratorOrCoroutine()")
        public static Object generatorOrCoroutineMultiContext(VirtualFrame frame,
                        TruffleString name,
                        TruffleString qualifiedName,
                        BytecodeDSLCodeUnit codeUnit,
                        Object[] defaults,
                        Object[] kwDefaultsObject,
                        Object closure,
                        Object annotations,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Cached(value = "createFunctionRootNode(rootNode, codeUnit)", adopt = false) PBytecodeDSLRootNode functionRootNode,
                        @Cached(value = "createGeneratorRootNode(rootNode, functionRootNode, codeUnit)", adopt = false) PBytecodeDSLGeneratorFunctionRootNode generatorRootNode,
                        @Shared @CachedLibrary(limit = "1") DynamicObjectLibrary dylib) {
            PCode code = createCode(rootNode, codeUnit, generatorRootNode);
            return createFunction(frame, name, qualifiedName, codeUnit.getDocstring(), code, defaults, kwDefaultsObject, closure, annotations, rootNode, dylib);
        }

        @Idempotent
        protected static boolean isSingleContext(Node node) {
            return PythonLanguage.get(node).isSingleContext();
        }

        @NeverDefault
        protected static PBytecodeDSLRootNode createFunctionRootNode(PBytecodeDSLRootNode outerRootNode, BytecodeDSLCodeUnit codeUnit) {
            return codeUnit.createRootNode(PythonContext.get(outerRootNode), outerRootNode.getSource());
        }

        @NeverDefault
        protected static PBytecodeDSLGeneratorFunctionRootNode createGeneratorRootNode(PBytecodeDSLRootNode outerRootNode, PBytecodeDSLRootNode functionRootNode,
                        BytecodeDSLCodeUnit codeUnit) {
            return new PBytecodeDSLGeneratorFunctionRootNode(PythonLanguage.get(outerRootNode), functionRootNode.getFrameDescriptor(), functionRootNode, codeUnit.name);
        }

        @NeverDefault
        protected static PCode createCode(PBytecodeDSLRootNode outerRootNode, BytecodeDSLCodeUnit codeUnit, PRootNode rootNode) {
            return outerRootNode.getFactory().createCode(
                            rootNode.getCallTarget(),
                            rootNode.getSignature(),
                            codeUnit);
        }

        protected static PFunction createFunction(VirtualFrame frame,
                        TruffleString name, TruffleString qualifiedName, TruffleString doc,
                        PCode code, Object[] defaults,
                        Object[] kwDefaultsObject, Object closure, Object annotations,
                        PBytecodeDSLRootNode node,
                        DynamicObjectLibrary dylib) {
            PKeyword[] kwDefaults = new PKeyword[kwDefaultsObject.length];
            System.arraycopy(kwDefaultsObject, 0, kwDefaults, 0, kwDefaults.length);
            PFunction function = node.factory.createFunction(name, qualifiedName, code, PArguments.getGlobals(frame), defaults, kwDefaults, (PCell[]) closure);

            if (annotations != null) {
                dylib.put(function, T___ANNOTATIONS__, annotations);
            }
            if (doc != null) {
                dylib.put(function, T___DOC__, doc);
            }

            return function;
        }
    }

    @Operation
    public static final class IAdd {

        @Specialization(rewriteOn = ArithmeticException.class)
        public static int add(int left, int right) {
            return Math.addExact(left, right);
        }

        @Specialization
        public static long doIIOvf(int left, int right) {
            return left + (long) right;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        public static long addLong(long left, long right) {
            return Math.addExact(left, right);
        }

        @Specialization
        public static double doDD(double left, double right) {
            return left + right;
        }

        @Specialization
        public static double doDL(double left, long right) {
            return left + right;
        }

        @Specialization
        public static double doLD(long left, double right) {
            return left + right;
        }

        @Specialization
        @InliningCutoff
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached LookupAndCallInplaceNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

        @NeverDefault
        public static LookupAndCallInplaceNode create() {
            return LookupAndCallInplaceNode.create(InplaceArithmetic.IAdd);
        }
    }

    @Operation
    public static final class IAnd {

        @Specialization
        public static int add(int left, int right) {
            return left & right;
        }

        @Specialization
        public static long addLong(long left, long right) {
            return left & right;
        }

        @Specialization
        @InliningCutoff
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached LookupAndCallInplaceNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

        @NeverDefault
        public static LookupAndCallInplaceNode create() {
            return LookupAndCallInplaceNode.create(InplaceArithmetic.IAnd);
        }
    }

    @Operation
    public static final class IOr {

        @Specialization
        public static int add(int left, int right) {
            return left | right;
        }

        @Specialization
        public static long addLong(long left, long right) {
            return left | right;
        }

        @Specialization
        @InliningCutoff
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached LookupAndCallInplaceNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

        @NeverDefault
        public static LookupAndCallInplaceNode create() {
            return LookupAndCallInplaceNode.create(InplaceArithmetic.IOr);
        }
    }

    @Operation
    public static final class IXor {

        @Specialization
        public static int add(int left, int right) {
            return left ^ right;
        }

        @Specialization
        public static long addLong(long left, long right) {
            return left ^ right;
        }

        @Specialization
        @InliningCutoff
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached LookupAndCallInplaceNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

        @NeverDefault
        public static LookupAndCallInplaceNode create() {
            return LookupAndCallInplaceNode.create(InplaceArithmetic.IXor);
        }
    }

    @Operation
    public static final class IRShift {

        @Specialization(guards = {"right < 32", "right >= 0"})
        public static int doIISmall(int left, int right) {
            return left >> right;
        }

        @Specialization(guards = {"right < 64", "right >= 0"})
        public static long doIISmall(long left, long right) {
            return left >> right;
        }

        @Specialization
        @InliningCutoff
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached LookupAndCallInplaceNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

        @NeverDefault
        public static LookupAndCallInplaceNode create() {
            return LookupAndCallInplaceNode.create(InplaceArithmetic.IRShift);
        }
    }

    @Operation
    public static final class ILShift {

        @Specialization(guards = {"right < 32", "right >= 0"}, rewriteOn = OverflowException.class)
        public static int doII(int left, int right) throws OverflowException {
            int result = left << right;
            if (left != result >> right) {
                throw OverflowException.INSTANCE;
            }
            return result;
        }

        @Specialization(guards = {"right < 64", "right >= 0"}, rewriteOn = OverflowException.class)
        public static long doLL(long left, long right) throws OverflowException {
            long result = left << right;
            if (left != result >> right) {
                throw OverflowException.INSTANCE;
            }
            return result;
        }

        @Specialization
        @InliningCutoff
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached LookupAndCallInplaceNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

        @NeverDefault
        public static LookupAndCallInplaceNode create() {
            return LookupAndCallInplaceNode.create(InplaceArithmetic.ILShift);
        }
    }

    @Operation
    public static final class IMatMul {

        @Specialization
        @InliningCutoff
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached LookupAndCallInplaceNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

        @NeverDefault
        public static LookupAndCallInplaceNode create() {
            return LookupAndCallInplaceNode.create(InplaceArithmetic.IMatMul);
        }
    }

    @Operation
    public static final class ISub {

        @Specialization(rewriteOn = ArithmeticException.class)
        public static int doII(int left, int right) {
            return Math.subtractExact(left, right);
        }

        @Specialization
        public static long doIIOvf(int x, int y) {
            return x - (long) y;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        public static long addLong(long left, long right) {
            return Math.subtractExact(left, right);
        }

        @Specialization
        public static double doDD(double left, double right) {
            return left - right;
        }

        @Specialization
        public static double doDL(double left, long right) {
            return left - right;
        }

        @Specialization
        public static double doLD(long left, double right) {
            return left - right;
        }

        @Specialization
        @InliningCutoff
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached LookupAndCallInplaceNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

        @NeverDefault
        public static LookupAndCallInplaceNode create() {
            return LookupAndCallInplaceNode.create(InplaceArithmetic.ISub);
        }
    }

    @Operation
    public static final class IMult {

        @Specialization(rewriteOn = ArithmeticException.class)
        public static int add(int left, int right) {
            return Math.multiplyExact(left, right);
        }

        @Specialization
        public static long doIIOvf(int x, int y) {
            return x * (long) y;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        public static long addLong(long left, long right) {
            return Math.multiplyExact(left, right);
        }

        @Specialization
        public static double doDD(double left, double right) {
            return left * right;
        }

        @Specialization
        public static double doDL(double left, long right) {
            return left * right;
        }

        @Specialization
        public static double doLD(long left, double right) {
            return left * right;
        }

        @Specialization
        @InliningCutoff
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached LookupAndCallInplaceNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

        @NeverDefault
        public static LookupAndCallInplaceNode create() {
            return LookupAndCallInplaceNode.create(InplaceArithmetic.IMul);
        }
    }

    @Operation
    public static final class ITrueDiv {
        @Specialization
        public static double doII(int left, int right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return TrueDivNode.doII(left, right, inliningTarget, raiseNode);
        }

        @Specialization
        public static double doLD(long left, double right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return TrueDivNode.doLD(left, right, inliningTarget, raiseNode);
        }

        @Specialization
        public static double doDL(double left, long right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return TrueDivNode.doDL(left, right, inliningTarget, raiseNode);
        }

        @Specialization
        public static double doDD(double left, double right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return TrueDivNode.doDD(left, right, inliningTarget, raiseNode);
        }

        @Specialization
        @InliningCutoff
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached("createCallNode()") LookupAndCallInplaceNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

        @NeverDefault
        public static LookupAndCallInplaceNode createCallNode() {
            return LookupAndCallInplaceNode.create(InplaceArithmetic.ITrueDiv);
        }
    }

    @Operation
    public static final class IFloorDiv {
        @Specialization
        public static int doII(int left, int right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return FloorDivNode.doII(left, right, inliningTarget, raiseNode);
        }

        @Specialization(rewriteOn = OverflowException.class)
        public static long doLL(long left, long right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) throws OverflowException {
            return FloorDivNode.doLL(left, right, inliningTarget, raiseNode);
        }

        @Specialization
        public static double doDL(double left, long right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return FloorDivNode.doDL(left, right, inliningTarget, raiseNode);
        }

        @Specialization
        public static double doDD(double left, double right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return FloorDivNode.doDD(left, right, inliningTarget, raiseNode);
        }

        @Specialization
        public static double doLD(long left, double right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return FloorDivNode.doLD(left, right, inliningTarget, raiseNode);
        }

        @Specialization
        @InliningCutoff
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached("createCallNode()") LookupAndCallInplaceNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

        @NeverDefault
        public static LookupAndCallInplaceNode createCallNode() {
            return LookupAndCallInplaceNode.create(InplaceArithmetic.IFloorDiv);
        }
    }

    @Operation
    public static final class IPow {
        @Specialization
        @InliningCutoff
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached("createCallNode()") LookupAndCallInplaceNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

        @NeverDefault
        public static LookupAndCallInplaceNode createCallNode() {
            return LookupAndCallInplaceNode.create(InplaceArithmetic.IPow);
        }
    }

    @Operation
    public static final class IMod {
        @Specialization
        public static int doII(int left, int right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return ModNode.doII(left, right, inliningTarget, raiseNode);
        }

        @Specialization(rewriteOn = OverflowException.class)
        public static long doLL(long left, long right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) throws OverflowException {
            return ModNode.doLL(left, right, inliningTarget, raiseNode);
        }

        @Specialization
        public static double doDL(double left, long right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return ModNode.doDL(left, right, inliningTarget, raiseNode);
        }

        @Specialization
        public static double doDD(double left, double right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return ModNode.doDD(left, right, inliningTarget, raiseNode);
        }

        @Specialization
        public static double doLD(long left, double right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return ModNode.doLD(left, right, inliningTarget, raiseNode);
        }

        @Specialization
        @InliningCutoff
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached("createCallNode()") LookupAndCallInplaceNode callNode) {
            return callNode.executeObject(frame, left, right);
        }

        @NeverDefault
        public static LookupAndCallInplaceNode createCallNode() {
            return LookupAndCallInplaceNode.create(InplaceArithmetic.IMod);
        }
    }

    @Operation
    @ConstantOperand(type = LocalAccessor.class)
    public static final class ForIterate {

        @Specialization
        public static boolean doIntegerSequence(VirtualFrame frame, LocalAccessor output, PIntegerSequenceIterator iterator,
                        @Bind BytecodeNode bytecode) {
            return doInteger(frame, output, iterator, bytecode);
        }

        @Specialization
        public static boolean doIntRange(VirtualFrame frame, LocalAccessor output, PIntRangeIterator iterator,
                        @Bind BytecodeNode bytecode) {
            return doInteger(frame, output, iterator, bytecode);
        }

        private static boolean doInteger(VirtualFrame frame, LocalAccessor output,
                        PIntegerIterator iterator, BytecodeNode bytecode) {
            if (!iterator.hasNext()) {
                iterator.setExhausted();
                return false;
            }
            output.setInt(bytecode, frame, iterator.next());
            return true;
        }

        @Specialization
        public static boolean doObjectIterator(VirtualFrame frame, LocalAccessor output, PObjectSequenceIterator iterator,
                        @Bind BytecodeNode bytecode) {
            if (!iterator.hasNext()) {
                iterator.setExhausted();
                output.setObject(bytecode, frame, null);
                return false;
            }
            Object value = iterator.next();
            output.setObject(bytecode, frame, value);
            return value != null;
        }

        @Specialization
        public static boolean doLongIterator(VirtualFrame frame, LocalAccessor output, PLongSequenceIterator iterator,
                        @Bind BytecodeNode bytecode) {
            if (!iterator.hasNext()) {
                iterator.setExhausted();
                return false;
            }
            output.setLong(bytecode, frame, iterator.next());
            return true;
        }

        @Specialization
        public static boolean doDoubleIterator(VirtualFrame frame, LocalAccessor output, PDoubleSequenceIterator iterator,
                        @Bind BytecodeNode bytecode) {
            if (!iterator.hasNext()) {
                iterator.setExhausted();
                return false;
            }
            output.setDouble(bytecode, frame, iterator.next());
            return true;
        }

        @Specialization
        @InliningCutoff
        public static boolean doIterator(VirtualFrame frame, LocalAccessor output, Object object,
                        @Bind Node inliningTarget,
                        @Bind BytecodeNode bytecode,
                        @Cached GetNextNode next,
                        @Cached IsBuiltinObjectProfile errorProfile) {
            try {
                Object value = next.execute(frame, object);
                output.setObject(bytecode, frame, value);
                return value != null;
            } catch (PException e) {
                output.setObject(bytecode, frame, null);
                e.expectStopIteration(inliningTarget, errorProfile);
                return false;
            }
        }
    }

    @Operation
    @ConstantOperand(type = TruffleString.class)
    public static final class GetMethod {
        @Specialization
        public static Object doIt(VirtualFrame frame,
                        TruffleString name, Object obj,
                        @Bind Node inliningTarget,
                        @Cached PyObjectGetMethod getMethod) {
            return getMethod.execute(frame, inliningTarget, obj, name);
        }
    }

    @Operation
    @ConstantOperand(type = TruffleString.class)
    public static final class GetAttribute {
        @Specialization
        @InliningCutoff
        public static Object doIt(VirtualFrame frame,
                        TruffleString name,
                        Object obj,
                        @Cached("create(name)") GetFixedAttributeNode getAttributeNode) {
            return getAttributeNode.execute(frame, obj);
        }
    }

    @Operation
    @ConstantOperand(type = TruffleString.class)
    public static final class SetAttribute {
        @Specialization
        @InliningCutoff
        public static void doIt(VirtualFrame frame,
                        TruffleString key,
                        Object value,
                        Object object,
                        @Bind Node inliningTarget,
                        @Cached PyObjectSetAttr setAttrNode) {
            setAttrNode.execute(frame, inliningTarget, object, key, value);
        }
    }

    @Operation
    @ConstantOperand(type = TruffleString.class)
    public static final class DeleteAttribute {
        @Specialization
        @InliningCutoff
        public static Object doObject(VirtualFrame frame,
                        TruffleString key,
                        Object object,
                        @Cached LookupAndCallBinaryNode call) {
            return call.executeObject(frame, object, key);
        }

        @NeverDefault
        public static LookupAndCallBinaryNode create() {
            return LookupAndCallBinaryNode.create(SpecialMethodSlot.DelAttr);
        }
    }

    @Operation
    @ImportStatic(SpecialMethodSlot.class)
    public static final class DeleteItem {
        @Specialization
        public static void doWithFrame(VirtualFrame frame, Object primary, Object index,
                        @Bind Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached("create(DelItem)") LookupSpecialMethodSlotNode lookupDelitem,
                        @Cached PRaiseNode raise,
                        @Cached CallBinaryMethodNode callDelitem) {
            Object delitem = lookupDelitem.execute(frame, getClassNode.execute(inliningTarget, primary), primary);
            if (delitem == PNone.NO_VALUE) {
                throw raise.raise(TypeError, ErrorMessages.OBJ_DOESNT_SUPPORT_DELETION, primary);
            }
            callDelitem.executeObject(frame, delitem, primary, index);
        }
    }

    @Operation
    @ConstantOperand(type = TruffleString.class)
    public static final class ReadGlobal {
        @Specialization
        public static Object perform(VirtualFrame frame, TruffleString name,
                        @Cached ReadGlobalOrBuiltinNode readNode) {
            return readNode.execute(frame, name);
        }
    }

    @Operation
    @ConstantOperand(type = TruffleString.class)
    public static final class WriteGlobal {
        @Specialization
        public static void perform(VirtualFrame frame, TruffleString name, Object value,
                        @Cached WriteGlobalNode writeNode) {
            writeNode.executeObject(frame, name, value);
        }
    }

    @Operation
    @ConstantOperand(type = TruffleString.class)
    public static final class DeleteGlobal {
        @Specialization
        public static void perform(VirtualFrame frame, TruffleString name,
                        @Cached DeleteGlobalNode deleteNode) {
            deleteNode.executeWithGlobals(frame, PArguments.getGlobals(frame), name);
        }
    }

    @Operation
    public static final class BuildClass {

        public static final TruffleString NAME = BuiltinNames.T___BUILD_CLASS__;

        @Specialization
        @InliningCutoff
        public static Object perform(VirtualFrame frame,
                        @Cached ReadGlobalOrBuiltinNode readNode) {
            return readNode.execute(frame, NAME);
        }
    }

    @Operation
    public static final class MakeList {
        @Specialization
        public static PList perform(Object[] elements,
                        @Bind PBytecodeDSLRootNode rootNode) {
            return rootNode.factory.createList(elements);
        }
    }

    @Operation
    @ImportStatic({PBytecodeDSLRootNode.class})
    public static final class MakeSet {
        @Specialization(guards = {"elements.length == length", "length <= EXPLODE_LOOP_THRESHOLD"}, limit = "1")
        @ExplodeLoop
        public static PSet performExploded(VirtualFrame frame, Object[] elements,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Bind Node node,
                        @Cached(value = "elements.length") int length,
                        @Shared @Cached SetNodes.AddNode addNode,
                        @Shared @Cached HashingCollectionNodes.SetItemNode setItemNode) {
            PSet set = rootNode.factory.createSet();
            for (int i = 0; i < length; i++) {
                SetNodes.AddNode.add(frame, set, elements[i], addNode, setItemNode);
            }
            return set;
        }

        @Specialization
        public static PSet performRegular(VirtualFrame frame, Object[] elements,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Bind Node node,
                        @Shared @Cached SetNodes.AddNode addNode,
                        @Shared @Cached HashingCollectionNodes.SetItemNode setItemNode) {
            PSet set = rootNode.factory.createSet();
            for (int i = 0; i < elements.length; i++) {
                SetNodes.AddNode.add(frame, set, elements[i], addNode, setItemNode);
            }
            return set;
        }
    }

    @Operation
    @ConstantOperand(type = int.class)
    public static final class MakeFrozenSet {
        @Specialization
        public static PFrozenSet perform(VirtualFrame frame,
                        int length,
                        @Variadic Object[] elements,
                        @Cached HashingStorageSetItem hashingStorageLibrary,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Bind Node inliningTarget) {
            HashingStorage setStorage;
            if (length <= EXPLODE_LOOP_THRESHOLD) {
                setStorage = doExploded(frame, inliningTarget, elements, length, hashingStorageLibrary);
            } else {
                setStorage = doRegular(frame, inliningTarget, elements, length, hashingStorageLibrary);
            }
            return rootNode.factory.createFrozenSet(setStorage);
        }

        @ExplodeLoop
        private static HashingStorage doExploded(VirtualFrame frame, Node inliningTarget, Object[] elements, int length, HashingStorageSetItem hashingStorageLibrary) {
            CompilerAsserts.partialEvaluationConstant(length);
            HashingStorage setStorage = EmptyStorage.INSTANCE;
            for (int i = 0; i < length; ++i) {
                Object o = elements[i];
                setStorage = hashingStorageLibrary.execute(frame, inliningTarget, setStorage, o, PNone.NONE);
            }
            return setStorage;
        }

        private static HashingStorage doRegular(VirtualFrame frame, Node inliningTarget, Object[] elements, int length, HashingStorageSetItem hashingStorageLibrary) {
            HashingStorage setStorage = EmptyStorage.INSTANCE;
            for (int i = 0; i < length; ++i) {
                Object o = elements[i];
                setStorage = hashingStorageLibrary.execute(frame, inliningTarget, setStorage, o, PNone.NONE);
            }
            return setStorage;
        }

    }

    @Operation
    public static final class MakeTuple {
        @Specialization
        public static Object perform(Object[] elements,
                        @Bind PBytecodeDSLRootNode rootNode) {
            return rootNode.factory.createTuple(elements);
        }
    }

    @Operation
    @ConstantOperand(type = int[].class, dimensions = 0)
    public static final class MakeConstantIntList {
        @Specialization
        public static PList perform(int[] array,
                        @Bind PBytecodeDSLRootNode rootNode) {
            SequenceStorage storage = new IntSequenceStorage(PythonUtils.arrayCopyOf(array, array.length));
            return rootNode.factory.createList(storage);
        }
    }

    @Operation
    @ConstantOperand(type = long[].class, dimensions = 0)
    public static final class MakeConstantLongList {
        @Specialization
        public static PList perform(long[] array,
                        @Bind PBytecodeDSLRootNode rootNode) {
            SequenceStorage storage = new LongSequenceStorage(PythonUtils.arrayCopyOf(array, array.length));
            return rootNode.factory.createList(storage);
        }
    }

    @Operation
    @ConstantOperand(type = boolean[].class, dimensions = 0)
    public static final class MakeConstantBooleanList {
        @Specialization
        public static PList perform(boolean[] array,
                        @Bind PBytecodeDSLRootNode rootNode) {
            SequenceStorage storage = new BoolSequenceStorage(PythonUtils.arrayCopyOf(array, array.length));
            return rootNode.factory.createList(storage);
        }
    }

    @Operation
    @ConstantOperand(type = double[].class, dimensions = 0)
    public static final class MakeConstantDoubleList {
        @Specialization
        public static PList perform(double[] array,
                        @Bind PBytecodeDSLRootNode rootNode) {
            SequenceStorage storage = new DoubleSequenceStorage(PythonUtils.arrayCopyOf(array, array.length));
            return rootNode.factory.createList(storage);
        }
    }

    @Operation
    @ConstantOperand(type = Object[].class, dimensions = 0)
    public static final class MakeConstantObjectList {
        @Specialization
        public static PList perform(Object[] array,
                        @Bind PBytecodeDSLRootNode rootNode) {
            SequenceStorage storage = new ObjectSequenceStorage(PythonUtils.arrayCopyOf(array, array.length));
            return rootNode.factory.createList(storage);
        }
    }

    @Operation
    @ConstantOperand(type = int[].class, dimensions = 0)
    public static final class MakeConstantIntTuple {
        @Specialization
        public static PTuple perform(int[] array,
                        @Bind PBytecodeDSLRootNode rootNode) {
            SequenceStorage storage = new IntSequenceStorage(array);
            return rootNode.factory.createTuple(storage);
        }
    }

    @Operation
    @ConstantOperand(type = long[].class, dimensions = 0)
    public static final class MakeConstantLongTuple {
        @Specialization
        public static PTuple perform(long[] array,
                        @Bind PBytecodeDSLRootNode rootNode) {
            SequenceStorage storage = new LongSequenceStorage(array);
            return rootNode.factory.createTuple(storage);
        }
    }

    @Operation
    @ConstantOperand(type = boolean[].class, dimensions = 0)
    public static final class MakeConstantBooleanTuple {
        @Specialization
        public static PTuple perform(boolean[] array,
                        @Bind PBytecodeDSLRootNode rootNode) {
            SequenceStorage storage = new BoolSequenceStorage(array);
            return rootNode.factory.createTuple(storage);
        }
    }

    @Operation
    @ConstantOperand(type = double[].class, dimensions = 0)
    public static final class MakeConstantDoubleTuple {
        @Specialization
        public static PTuple perform(double[] array,
                        @Bind PBytecodeDSLRootNode rootNode) {
            SequenceStorage storage = new DoubleSequenceStorage(array);
            return rootNode.factory.createTuple(storage);
        }
    }

    @Operation
    @ConstantOperand(type = Object[].class, dimensions = 0)
    public static final class MakeConstantObjectTuple {
        @Specialization
        public static PTuple perform(Object[] array,
                        @Bind PBytecodeDSLRootNode rootNode) {
            SequenceStorage storage = new ObjectSequenceStorage(array);
            return rootNode.factory.createTuple(storage);
        }
    }

    @Operation
    public static final class MakeSlice {

        @Specialization
        public static Object doIII(int start, int end, int step,
                        @Bind PBytecodeDSLRootNode rootNode) {
            return rootNode.factory.createIntSlice(start, end, step);
        }

        @Specialization
        public static Object doNIN(PNone start, int end, PNone step,
                        @Bind PBytecodeDSLRootNode rootNode) {
            return rootNode.factory.createIntSlice(0, end, 1, true, true);
        }

        @Specialization
        public static Object doIIN(int start, int end, PNone step,
                        @Bind PBytecodeDSLRootNode rootNode) {
            return rootNode.factory.createIntSlice(start, end, 1, false, true);
        }

        @Specialization
        public static Object doNII(PNone start, int end, int step,
                        @Bind PBytecodeDSLRootNode rootNode) {
            return rootNode.factory.createIntSlice(0, end, step, true, false);
        }

        @Specialization
        @InliningCutoff
        public static Object doGeneric(Object start, Object end, Object step,
                        @Bind PBytecodeDSLRootNode rootNode) {
            return rootNode.factory.createObjectSlice(start, end, step);
        }
    }

    @Operation
    @ConstantOperand(type = TruffleString[].class, dimensions = 0, specifyAtEnd = true)
    public static final class MakeKeywords {
        @Specialization
        public static PKeyword[] perform(@Variadic Object[] values, TruffleString[] keys) {
            if (keys.length <= EXPLODE_LOOP_THRESHOLD) {
                return doExploded(keys, values);
            } else {
                return doRegular(keys, values);
            }
        }

        @ExplodeLoop
        private static PKeyword[] doExploded(TruffleString[] keys, Object[] values) {
            CompilerAsserts.partialEvaluationConstant(keys.length);
            PKeyword[] result = new PKeyword[keys.length];
            for (int i = 0; i < keys.length; i++) {
                result[i] = new PKeyword(keys[i], values[i]);
            }
            return result;
        }

        private static PKeyword[] doRegular(TruffleString[] keys, Object[] values) {
            PKeyword[] result = new PKeyword[keys.length];
            for (int i = 0; i < keys.length; i++) {
                result[i] = new PKeyword(keys[i], values[i]);
            }
            return result;
        }
    }

    @Operation
    public static final class MappingToKeywords {
        @Specialization
        public static PKeyword[] perform(Object sourceCollection,
                        @Bind Node inliningTarget,
                        @Cached ExpandKeywordStarargsNode expandKeywordStarargsNode,
                        @Cached PRaiseNode raise) {
            return expandKeywordStarargsNode.execute(inliningTarget, sourceCollection);
        }
    }

    @Operation
    @ConstantOperand(type = int.class)
    public static final class MakeDict {
        @Specialization
        @ExplodeLoop
        public static PDict perform(VirtualFrame frame,
                        int entries,
                        @Variadic Object[] keysAndValues,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Cached DictBuiltins.SetItemNode setItemNode,
                        @Cached DictNodes.UpdateNode updateNode) {
            if (keysAndValues.length != entries * 2) {
                throw CompilerDirectives.shouldNotReachHere();
            }
            PDict dict = rootNode.factory.createDict();
            for (int i = 0; i < entries; i++) {
                Object key = keysAndValues[i * 2];
                Object value = keysAndValues[i * 2 + 1];
                // Each entry represents either a k: v pair or a **splats. splats have no key.
                if (key == PNone.NO_VALUE) {
                    updateNode.execute(frame, dict, value);
                } else {
                    setItemNode.execute(frame, dict, key, value);
                }
            }
            return dict;
        }

    }

    @Operation
    public static final class SetDictItem {
        @Specialization
        public static void perform(VirtualFrame frame, PDict item, Object key, Object value,
                        @Bind Node inliningTarget,
                        @Cached HashingStorageSetItem setItem) {
            item.setDictStorage(setItem.execute(frame, inliningTarget, item.getDictStorage(), key, value));
        }
    }

    public static final class LiteralBoolean {
        @Specialization
        public static boolean doBoolean(boolean value) {
            return value;
        }
    }

    @Operation
    public static final class SetItem {
        @Specialization
        public static void performB(VirtualFrame frame, boolean value, Object primary, Object slice,
                        @Bind Node inliningTarget,
                        @Shared @Cached PyObjectSetItem setItemNode) {
            setItemNode.execute(frame, inliningTarget, primary, slice, value);
        }

        @Specialization
        public static void performI(VirtualFrame frame, int value, Object primary, Object slice,
                        @Bind Node inliningTarget,
                        @Shared @Cached PyObjectSetItem setItemNode) {
            setItemNode.execute(frame, inliningTarget, primary, slice, value);
        }

        @Specialization
        public static void performL(VirtualFrame frame, long value, Object primary, Object slice,
                        @Bind Node inliningTarget,
                        @Shared @Cached PyObjectSetItem setItemNode) {
            setItemNode.execute(frame, inliningTarget, primary, slice, value);
        }

        @Specialization
        public static void performD(VirtualFrame frame, double value, Object primary, Object slice,
                        @Bind Node inliningTarget,
                        @Shared @Cached PyObjectSetItem setItemNode) {
            setItemNode.execute(frame, inliningTarget, primary, slice, value);
        }

        @Specialization
        public static void performO(VirtualFrame frame, Object value, Object primary, Object slice,
                        @Bind Node inliningTarget,
                        @Shared @Cached PyObjectSetItem setItemNode) {
            setItemNode.execute(frame, inliningTarget, primary, slice, value);
        }
    }

    @Operation
    @ConstantOperand(type = LocalRangeAccessor.class)
    @ImportStatic({PGuards.class})
    public static final class UnpackToLocals {
        @Specialization(guards = "isBuiltinSequence(sequence)")
        @ExplodeLoop
        public static void doUnpackSequence(VirtualFrame localFrame, LocalRangeAccessor results, PSequence sequence,
                        @Bind Node inliningTarget,
                        @Bind BytecodeNode bytecode,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Cached InlinedBranchProfile errorProfile,
                        @Shared @Cached PRaiseNode raiseNode) {
            SequenceStorage storage = getSequenceStorageNode.execute(inliningTarget, sequence);
            int len = storage.length();

            int count = results.getLength();
            CompilerAsserts.partialEvaluationConstant(count);

            if (len == count) {
                for (int i = 0; i < count; i++) {
                    results.setObject(bytecode, localFrame, i, getItemNode.execute(inliningTarget, storage, i));
                }
            } else {
                errorProfile.enter(inliningTarget);
                if (len < count) {
                    throw raiseNode.raise(ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK, count, len);
                } else {
                    throw raiseNode.raise(ValueError, ErrorMessages.TOO_MANY_VALUES_TO_UNPACK, count);
                }
            }
        }

        @Specialization
        @ExplodeLoop
        @InliningCutoff
        public static void doUnpackIterable(VirtualFrame virtualFrame, LocalRangeAccessor results, Object collection,
                        @Bind Node inliningTarget,
                        @Bind BytecodeNode bytecode,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetNextNode getNextNode,
                        @Cached IsBuiltinObjectProfile notIterableProfile,
                        @Cached IsBuiltinObjectProfile stopIterationProfile1,
                        @Cached IsBuiltinObjectProfile stopIterationProfile2,
                        @Shared @Cached PRaiseNode raiseNode) {
            int count = results.getLength();
            CompilerAsserts.partialEvaluationConstant(count);

            Object iterator;
            try {
                iterator = getIter.execute(virtualFrame, inliningTarget, collection);
            } catch (PException e) {
                e.expectTypeError(inliningTarget, notIterableProfile);
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.CANNOT_UNPACK_NON_ITERABLE, collection);
            }
            for (int i = 0; i < count; i++) {
                try {
                    results.setObject(bytecode, virtualFrame, i, getNextNode.execute(virtualFrame, iterator));
                } catch (PException e) {
                    e.expectStopIteration(inliningTarget, stopIterationProfile1);
                    throw raiseNode.raise(ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK, count, i);
                }
            }
            try {
                getNextNode.execute(virtualFrame, iterator);
            } catch (PException e) {
                e.expectStopIteration(inliningTarget, stopIterationProfile2);
                return;
            }
            throw raiseNode.raise(ValueError, ErrorMessages.TOO_MANY_VALUES_TO_UNPACK, count);
        }
    }

    @Operation
    @ConstantOperand(type = int.class)
    @ConstantOperand(type = LocalRangeAccessor.class)
    @ImportStatic({PGuards.class})
    @SuppressWarnings("truffle-interpreted-performance")
    public static final class UnpackStarredToLocals {
        @Specialization(guards = "isBuiltinSequence(sequence)")
        public static void doUnpackSequence(VirtualFrame localFrame,
                        int starIndex,
                        LocalRangeAccessor results,
                        PSequence sequence,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Shared @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Shared @Cached SequenceStorageNodes.GetItemSliceNode getItemSliceNode,
                        @Cached InlinedBranchProfile errorProfile,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Bind BytecodeNode bytecode,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {
            int resultsLength = results.getLength();
            int countBefore = starIndex;
            int countAfter = resultsLength - starIndex - 1;

            SequenceStorage storage = getSequenceStorageNode.execute(inliningTarget, sequence);
            int len = storage.length();

            int starLen = len - resultsLength + 1;
            if (starLen < 0) {
                errorProfile.enter(inliningTarget);
                throw raiseNode.raise(ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK_EX, countBefore + countAfter, len);
            }

            copyToLocalsFromSequence(storage, 0, 0, countBefore, results, localFrame, inliningTarget, bytecode, getItemNode);
            PList starList = rootNode.factory.createList(getItemSliceNode.execute(storage, countBefore, countBefore + starLen, 1, starLen));
            results.setObject(bytecode, localFrame, starIndex, starList);
            copyToLocalsFromSequence(storage, starIndex + 1, len - countAfter, countAfter, results, localFrame, inliningTarget, bytecode, getItemNode);
        }

        @Specialization
        @InliningCutoff
        public static void doUnpackIterable(VirtualFrame frame,
                        int starIndex,
                        LocalRangeAccessor results,
                        Object collection,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetNextNode getNextNode,
                        @Cached IsBuiltinObjectProfile notIterableProfile,
                        @Cached IsBuiltinObjectProfile stopIterationProfile,
                        @Cached ListNodes.ConstructListNode constructListNode,
                        @Shared @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Shared @Cached SequenceStorageNodes.GetItemSliceNode getItemSliceNode,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Bind BytecodeNode bytecode,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {
            int resultsLength = results.getLength();
            int countBefore = starIndex;
            int countAfter = resultsLength - starIndex - 1;

            Object iterator;
            try {
                iterator = getIter.execute(frame, inliningTarget, collection);
            } catch (PException e) {
                e.expectTypeError(inliningTarget, notIterableProfile);
                throw raiseNode.raise(TypeError, ErrorMessages.CANNOT_UNPACK_NON_ITERABLE, collection);
            }

            copyToLocalsFromIterator(iterator, countBefore, results, frame, inliningTarget, bytecode, countBefore + countAfter, getNextNode, stopIterationProfile, raiseNode);

            PList starAndAfter = constructListNode.execute(frame, iterator);
            SequenceStorage storage = starAndAfter.getSequenceStorage();
            int lenAfter = storage.length();
            if (lenAfter < countAfter) {
                throw raiseNode.raise(ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK_EX, countBefore + countAfter, countBefore + lenAfter);
            }
            if (countAfter == 0) {
                results.setObject(bytecode, frame, starIndex, starAndAfter);
            } else {
                int starLen = lenAfter - countAfter;
                PList starList = rootNode.factory.createList(getItemSliceNode.execute(storage, 0, starLen, 1, starLen));
                results.setObject(bytecode, frame, starIndex, starList);

                copyToLocalsFromSequence(storage, starIndex + 1, starLen, countAfter, results, frame, inliningTarget, bytecode, getItemNode);
            }
        }

        @ExplodeLoop
        private static void copyToLocalsFromIterator(Object iterator, int length, LocalRangeAccessor results,
                        VirtualFrame frame, Node inliningTarget, BytecodeNode bytecode, int requiredLength,
                        GetNextNode getNextNode, IsBuiltinObjectProfile stopIterationProfile, PRaiseNode raiseNode) {
            CompilerAsserts.partialEvaluationConstant(length);
            for (int i = 0; i < length; i++) {
                try {
                    Object item = getNextNode.execute(frame, iterator);
                    results.setObject(bytecode, frame, i, item);
                } catch (PException e) {
                    e.expectStopIteration(inliningTarget, stopIterationProfile);
                    throw raiseNode.raise(ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK_EX, requiredLength, i);
                }
            }
        }

        @ExplodeLoop
        private static void copyToLocalsFromSequence(SequenceStorage storage, int runOffset, int offset, int length, LocalRangeAccessor run,
                        VirtualFrame localFrame, Node inliningTarget, BytecodeNode bytecode, SequenceStorageNodes.GetItemScalarNode getItemNode) {
            CompilerAsserts.partialEvaluationConstant(length);
            for (int i = 0; i < length; i++) {
                run.setObject(bytecode, localFrame, runOffset + i, getItemNode.execute(inliningTarget, storage, offset + i));
            }
        }
    }

    private static final RuntimeException notSupported(Object left, Object right, PRaiseNode raiseNode, TruffleString operator) {
        throw raiseNode.raise(PythonErrorType.TypeError, ErrorMessages.NOT_SUPPORTED_BETWEEN_INSTANCES, operator, left, right);
    }

    @Operation
    public static final class Le {

        private static final TruffleString T_OPERATOR = PythonUtils.tsLiteral("<=");

        @Specialization
        public static boolean cmp(int left, int right) {
            return left <= right;
        }

        @Specialization
        public static boolean cmp(long left, long right) {
            return left <= right;
        }

        @Specialization
        public static boolean cmp(char left, char right) {
            return left <= right;
        }

        @Specialization
        public static boolean cmp(byte left, byte right) {
            return left <= right;
        }

        @Specialization
        public static boolean cmp(double left, double right) {
            return left <= right;
        }

        @Specialization
        public static boolean cmp(TruffleString left, TruffleString right,
                        @Cached TruffleString.CompareIntsUTF32Node compareIntsUTF32Node) {
            return StringUtils.compareStrings(left, right, compareIntsUTF32Node) <= 0;
        }

        @Specialization
        public static boolean cmp(int left, double right) {
            return left <= right;
        }

        @Specialization
        public static boolean cmp(double left, int right) {
            return left <= right;
        }

        @Specialization
        @InliningCutoff
        public static final Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached("createCallNode()") LookupAndCallBinaryNode callNode,
                        @Cached PRaiseNode raiseNode) {
            Object result = callNode.executeObject(frame, left, right);
            if (result == PNotImplemented.NOT_IMPLEMENTED) {
                throw notSupported(left, right, raiseNode, T_OPERATOR);
            }
            return result;
        }

        static LookupAndCallBinaryNode createCallNode() {
            return LookupAndCallBinaryNode.create(SpecialMethodSlot.Le, SpecialMethodSlot.Ge, true, true);
        }
    }

    @Operation
    public static final class Lt {

        private static final TruffleString T_OPERATOR = PythonUtils.tsLiteral("<");

        @Specialization
        public static boolean cmp(int left, int right) {
            return left < right;
        }

        @Specialization
        public static boolean cmp(long left, long right) {
            return left < right;
        }

        @Specialization
        public static boolean cmp(char left, char right) {
            return left < right;
        }

        @Specialization
        public static boolean cmp(byte left, byte right) {
            return left < right;
        }

        @Specialization
        public static boolean cmp(double left, double right) {
            return left < right;
        }

        @Specialization
        public static boolean cmp(TruffleString left, TruffleString right,
                        @Cached TruffleString.CompareIntsUTF32Node compareIntsUTF32Node) {
            return StringUtils.compareStrings(left, right, compareIntsUTF32Node) < 0;
        }

        @Specialization
        public static boolean cmp(int left, double right) {
            return left < right;
        }

        @Specialization
        public static boolean cmp(double left, int right) {
            return left < right;
        }

        @Specialization
        @InliningCutoff
        public static final Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached("createCallNode()") LookupAndCallBinaryNode callNode,
                        @Cached PRaiseNode raiseNode) {
            Object result = callNode.executeObject(frame, left, right);
            if (result == PNotImplemented.NOT_IMPLEMENTED) {
                throw notSupported(left, right, raiseNode, T_OPERATOR);
            }
            return result;
        }

        static LookupAndCallBinaryNode createCallNode() {
            return LookupAndCallBinaryNode.create(SpecialMethodSlot.Lt, SpecialMethodSlot.Gt, true, true);
        }
    }

    @Operation
    public static final class Ge {

        private static final TruffleString T_OPERATOR = PythonUtils.tsLiteral(">=");

        @Specialization
        public static boolean cmp(int left, int right) {
            return left >= right;
        }

        @Specialization
        public static boolean cmp(long left, long right) {
            return left >= right;
        }

        @Specialization
        public static boolean cmp(char left, char right) {
            return left >= right;
        }

        @Specialization
        public static boolean cmp(byte left, byte right) {
            return left >= right;
        }

        @Specialization
        public static boolean cmp(double left, double right) {
            return left >= right;
        }

        @Specialization
        public static boolean cmp(TruffleString left, TruffleString right,
                        @Cached TruffleString.CompareIntsUTF32Node compareIntsUTF32Node) {
            return StringUtils.compareStrings(left, right, compareIntsUTF32Node) >= 0;
        }

        @Specialization
        public static boolean cmp(int left, double right) {
            return left >= right;
        }

        @Specialization
        public static boolean cmp(double left, int right) {
            return left >= right;
        }

        @Specialization
        @InliningCutoff
        public static final Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached("createCallNode()") LookupAndCallBinaryNode callNode,
                        @Cached PRaiseNode raiseNode) {
            Object result = callNode.executeObject(frame, left, right);
            if (result == PNotImplemented.NOT_IMPLEMENTED) {
                throw notSupported(left, right, raiseNode, T_OPERATOR);
            }
            return result;
        }

        static LookupAndCallBinaryNode createCallNode() {
            return LookupAndCallBinaryNode.create(SpecialMethodSlot.Ge, SpecialMethodSlot.Le, true, true);
        }
    }

    @Operation
    public static final class Gt {

        private static final TruffleString T_OPERATOR = PythonUtils.tsLiteral(">");

        @Specialization
        public static boolean cmp(int left, int right) {
            return left > right;
        }

        @Specialization
        public static boolean cmp(long left, long right) {
            return left > right;
        }

        @Specialization
        public static boolean cmp(char left, char right) {
            return left > right;
        }

        @Specialization
        public static boolean cmp(byte left, byte right) {
            return left > right;
        }

        @Specialization
        public static boolean cmp(double left, double right) {
            return left > right;
        }

        @Specialization
        public static boolean cmp(TruffleString left, TruffleString right,
                        @Cached TruffleString.CompareIntsUTF32Node compareIntsUTF32Node) {
            return StringUtils.compareStrings(left, right, compareIntsUTF32Node) > 0;
        }

        @Specialization
        public static boolean cmp(int left, double right) {
            return left > right;
        }

        @Specialization
        public static boolean cmp(double left, int right) {
            return left > right;
        }

        @Specialization
        @InliningCutoff
        public static final Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached("createCallNode()") LookupAndCallBinaryNode callNode,
                        @Cached PRaiseNode raiseNode) {
            Object result = callNode.executeObject(frame, left, right);
            if (result == PNotImplemented.NOT_IMPLEMENTED) {
                throw notSupported(left, right, raiseNode, T_OPERATOR);
            }
            return result;
        }

        static LookupAndCallBinaryNode createCallNode() {
            return LookupAndCallBinaryNode.create(SpecialMethodSlot.Gt, SpecialMethodSlot.Lt, true, true);
        }
    }

    @Operation
    public static final class Eq {

        @Specialization
        public static boolean cmp(int left, int right) {
            return left == right;
        }

        @Specialization
        public static boolean cmp(long left, long right) {
            return left == right;
        }

        @Specialization
        public static boolean cmp(char left, char right) {
            return left == right;
        }

        @Specialization
        public static boolean cmp(byte left, byte right) {
            return left == right;
        }

        @Specialization
        public static boolean cmp(double left, double right) {
            return left == right;
        }

        @Specialization
        public static boolean cmp(TruffleString left, TruffleString right,
                        @Cached TruffleString.EqualNode equalNode) {
            return equalNode.execute(left, right, PythonUtils.TS_ENCODING);
        }

        @Specialization
        public static boolean cmp(int left, double right) {
            return left == right;
        }

        @Specialization
        public static boolean cmp(double left, int right) {
            return left == right;
        }

        @Specialization
        @InliningCutoff
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached("createCallNode()") LookupAndCallBinaryNode callNode,
                        @Cached IsNode isNode) {
            Object result = callNode.executeObject(frame, left, right);
            if (result == PNotImplemented.NOT_IMPLEMENTED) {
                return isNode.execute(left, right);
            }
            return result;
        }

        static LookupAndCallBinaryNode createCallNode() {
            return LookupAndCallBinaryNode.create(SpecialMethodSlot.Eq, SpecialMethodSlot.Eq, true, true);
        }
    }

    @Operation
    public static final class Ne {

        @Specialization
        public static boolean cmp(int left, int right) {
            return left != right;
        }

        @Specialization
        public static boolean cmp(long left, long right) {
            return left != right;
        }

        @Specialization
        public static boolean cmp(char left, char right) {
            return left != right;
        }

        @Specialization
        public static boolean cmp(byte left, byte right) {
            return left != right;
        }

        @Specialization
        public static boolean cmp(double left, double right) {
            return left != right;
        }

        @Specialization
        public static boolean cmp(TruffleString left, TruffleString right,
                        @Cached TruffleString.EqualNode equalNode) {
            return !equalNode.execute(left, right, PythonUtils.TS_ENCODING);
        }

        @Specialization
        public static boolean cmp(int left, double right) {
            return left != right;
        }

        @Specialization
        public static boolean cmp(double left, int right) {
            return left != right;
        }

        @Specialization
        @InliningCutoff
        public static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached("createCallNode()") LookupAndCallBinaryNode callNode,
                        @Cached IsNode isNode) {
            Object result = callNode.executeObject(frame, left, right);
            if (result == PNotImplemented.NOT_IMPLEMENTED) {
                return !isNode.execute(left, right);
            }
            return result;
        }

        static LookupAndCallBinaryNode createCallNode() {
            return LookupAndCallBinaryNode.create(SpecialMethodSlot.Ne, SpecialMethodSlot.Ne, true, true);
        }
    }

    @Operation
    @ConstantOperand(type = TruffleString.class)
    @ConstantOperand(type = TruffleString[].class, dimensions = 0)
    @ConstantOperand(type = int.class)
    public static final class Import {
        @Specialization
        @InliningCutoff
        public static Object doImport(VirtualFrame frame, TruffleString name, TruffleString[] fromList, int level,
                        @Cached ImportNode node) {
            return node.execute(frame, name, PArguments.getGlobals(frame), fromList, level);
        }
    }

    @Operation
    @ConstantOperand(type = TruffleString.class)
    public static final class ImportFrom {
        @Specialization
        @InliningCutoff
        public static Object doImport(VirtualFrame frame, TruffleString name, Object module,
                        @Cached ImportFromNode node) {
            return node.execute(frame, module, name);
        }
    }

    @Operation
    @ConstantOperand(type = TruffleString.class)
    @ConstantOperand(type = int.class)
    public static final class ImportStar {
        @Specialization
        @InliningCutoff
        public static void doImport(VirtualFrame frame, TruffleString name, int level,
                        @Cached("create(name, level)") ImportStarNode node) {
            node.execute(frame, name, level);
        }

        @NeverDefault
        static ImportStarNode create(TruffleString name, int level) {
            return ImportStarNode.create();
        }
    }

    @Operation
    public static final class Raise {
        @Specialization
        public static void perform(VirtualFrame frame, Object typeOrExceptionObject, Object cause,
                        @Bind PBytecodeDSLRootNode root,
                        @Cached RaiseNode raiseNode) {
            raiseNode.execute(frame, typeOrExceptionObject, cause, !root.isInternal());
        }
    }

    @Operation
    public static final class Reraise {
        @Specialization
        public static void doPException(PException ex,
                        @Bind PBytecodeDSLRootNode root) {
            throw ex.getExceptionForReraise(!root.isInternal());
        }

        @Specialization
        public static void doAbstractTruffleException(AbstractTruffleException ex) {
            throw ex;
        }
    }

    /**
     * Throw is used internally for our try-catch-finally implementation when we need to throw an
     * exception and catch it elsewhere. We don't need to do any of the work done by RaiseNode.
     */
    @Operation
    public static final class Throw {
        @Specialization
        public static void doAbstractTruffleException(AbstractTruffleException ex) {
            throw ex;
        }
    }

    @Operation
    public static final class GetCurrentException {
        @Specialization
        public static AbstractTruffleException doPException(VirtualFrame frame) {
            return PArguments.getException(frame);
        }
    }

    @Operation
    public static final class SetCurrentException {
        @Specialization
        @InliningCutoff
        public static void doPException(VirtualFrame frame, PException ex) {
            PArguments.setException(frame, ex);
        }

        @Specialization(guards = {"notPException(ex)"})
        @InliningCutoff
        public static void doAbstractTruffleException(VirtualFrame frame, AbstractTruffleException ex,
                        @Bind PBytecodeDSLRootNode rootNode) {
            PArguments.setException(frame, ExceptionUtils.wrapJavaException(ex, rootNode, rootNode.factory.createBaseException(SystemError, ErrorMessages.M, new Object[]{ex})));
        }

        @Fallback
        @InliningCutoff
        public static void doNull(VirtualFrame frame, Object ex) {
            assert ex == null;
            PArguments.setException(frame, PException.NO_EXCEPTION);
        }

        static boolean notPException(AbstractTruffleException ex) {
            return ex != null && !(ex instanceof PException);
        }
    }

    @Operation
    public static final class MarkExceptionAsCaught {
        @Specialization
        @InliningCutoff
        public static void doPException(VirtualFrame frame, PException ex,
                        @Bind PBytecodeDSLRootNode rootNode) {
            ex.markAsCaught(frame, rootNode);
        }

        @Fallback
        @InliningCutoff
        public static void doNothing(@SuppressWarnings("unused") Object ex) {
        }
    }

    @Operation
    public static final class AssertFailed {
        @Specialization
        public static void doAssertFailed(VirtualFrame frame, Object assertionMessage,
                        @Cached PRaiseNode raise) {
            if (assertionMessage == PNone.NO_VALUE) {
                throw raise.raise(AssertionError);
            } else {
                throw raise.raise(AssertionError, new Object[]{assertionMessage});
            }
        }
    }

    @Operation
    @ConstantOperand(type = int.class)
    public static final class LoadCell {
        @Specialization
        public static Object doLoadCell(int index, PCell cell,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode.Lazy raiseNode) {
            return checkUnboundCell(cell, index, rootNode, inliningTarget, raiseNode);
        }
    }

    @Operation
    @ConstantOperand(type = int.class)
    public static final class ClassLoadCell {
        @Specialization
        public static Object doLoadCell(VirtualFrame frame,
                        int index,
                        PCell cell,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Bind Node inliningTarget,
                        @Cached ReadFromLocalsNode readLocalsNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            CodeUnit co = rootNode.getCodeUnit();
            TruffleString name = co.freevars[index - co.cellvars.length];
            Object locals = PArguments.getSpecialArgument(frame);
            Object value = readLocalsNode.execute(frame, inliningTarget, locals, name);
            if (value != PNone.NO_VALUE) {
                return value;
            } else {
                return checkUnboundCell(cell, index, rootNode, inliningTarget, raiseNode);
            }
        }
    }

    @Operation
    public static final class StoreCell {
        @Specialization
        public static void doStoreCell(PCell cell, Object value) {
            cell.setRef(value);
        }
    }

    @Operation
    public static final class CreateCell {
        @Specialization
        public static PCell doCreateCell(Object value) {
            PCell cell = new PCell(Assumption.create());
            cell.setRef(value);
            return cell;
        }
    }

    @Operation
    @ConstantOperand(type = int.class)
    public static final class ClearCell {
        @Specialization
        public static void doClearCell(int index, PCell cell,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode.Lazy raiseNode) {
            checkUnboundCell(cell, index, rootNode, inliningTarget, raiseNode);
            cell.clearRef();
        }
    }

    @Operation
    @ConstantOperand(type = LocalAccessor.class)
    public static final class ClearLocal {
        @Specialization
        public static void doClearLocal(VirtualFrame frame, LocalAccessor localAccessor,
                        @Bind BytecodeNode bytecode) {
            localAccessor.setObject(bytecode, frame, null);
        }
    }

    @Operation
    public static final class LoadClosure {
        @Specialization
        public static PCell[] doLoadClosure(VirtualFrame frame) {
            return PArguments.getClosure(frame);
        }
    }

    @Operation
    @ConstantOperand(type = LocalRangeAccessor.class)
    public static final class StoreRange {
        @Specialization
        @ExplodeLoop
        public static void perform(VirtualFrame frame, LocalRangeAccessor locals, Object[] values,
                        @Bind BytecodeNode bytecode) {
            CompilerAsserts.partialEvaluationConstant(locals.getLength());
            assert values.length == locals.getLength();
            for (int i = 0; i < locals.getLength(); i++) {
                locals.setObject(bytecode, frame, i, values[i]);
            }
        }
    }

    @Operation
    public static final class MakeCellArray {
        @Specialization
        public static PCell[] doMakeCellArray(@Variadic Object[] cells) {
            return PCell.toCellArray(cells);
        }
    }

    /**
     * Flattens an array of arrays. Used for splatting Starred expressions.
     */
    @Operation
    @ConstantOperand(type = int.class, specifyAtEnd = true)
    public static final class Unstar {
        @Specialization
        public static Object[] perform(@Variadic Object[] values,
                        int length) {
            if (length <= EXPLODE_LOOP_THRESHOLD) {
                return doExploded(values, length);
            } else {
                return doRegular(values, length);
            }
        }

        @ExplodeLoop
        private static Object[] doExploded(Object[] values, int length) {
            CompilerAsserts.partialEvaluationConstant(length);
            int totalLength = 0;
            for (int i = 0; i < length; i++) {
                totalLength += ((Object[]) values[i]).length;
            }
            Object[] result = new Object[totalLength];
            int idx = 0;
            for (int i = 0; i < length; i++) {
                int nl = ((Object[]) values[i]).length;
                System.arraycopy(values[i], 0, result, idx, nl);
                idx += nl;
            }
            return result;
        }

        private static Object[] doRegular(Object[] values, int length) {
            int totalLength = 0;
            for (int i = 0; i < length; i++) {
                totalLength += ((Object[]) values[i]).length;
            }
            Object[] result = new Object[totalLength];
            int idx = 0;
            for (int i = 0; i < length; i++) {
                int nl = ((Object[]) values[i]).length;
                System.arraycopy(values[i], 0, result, idx, nl);
                idx += nl;
            }
            return result;
        }
    }

    @Operation
    @ConstantOperand(type = LocalAccessor.class)
    public static final class KwargsMerge {
        @Specialization
        public static PDict doMerge(VirtualFrame frame,
                        LocalAccessor callee,
                        PDict dict,
                        Object toMerge,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Bind BytecodeNode bytecodeNode,
                        @Cached ConcatDictToStorageNode concatNode,
                        @Cached PRaiseNode raise) {
            try {
                HashingStorage resultStorage = concatNode.execute(frame, dict.getDictStorage(), toMerge);
                dict.setDictStorage(resultStorage);
            } catch (SameDictKeyException e) {
                throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.S_GOT_MULTIPLE_VALUES_FOR_KEYWORD_ARG, PyObjectFunctionStr.execute(callee.getObject(bytecodeNode, frame)),
                                e.getKey());
            } catch (NonMappingException e) {
                throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.ARG_AFTER_MUST_BE_MAPPING, PyObjectFunctionStr.execute(callee.getObject(bytecodeNode, frame)), toMerge);
            }
            return dict;
        }
    }

    @Operation
    @ImportStatic({PGuards.class})
    public static final class UnpackStarred {
        @Specialization(guards = "isBuiltinSequence(sequence)")
        public static Object[] doUnpackSequence(VirtualFrame localFrame, PSequence sequence,
                        @Bind Node inliningTarget,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            SequenceStorage storage = getSequenceStorageNode.execute(inliningTarget, sequence);
            int len = storage.length();
            Object[] result = new Object[len];
            for (int i = 0; i < len; i++) {
                result[i] = getItemNode.execute(inliningTarget, storage, i);
            }
            return result;
        }

        @Specialization
        @InliningCutoff
        public static Object[] doUnpackIterable(VirtualFrame virtualFrame, Object collection,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetNextNode getNextNode,
                        @Cached IsBuiltinObjectProfile notIterableProfile,
                        @Cached IsBuiltinObjectProfile stopIterationProfile1,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {

            Object iterator;
            try {
                iterator = getIter.execute(virtualFrame, inliningTarget, collection);
            } catch (PException e) {
                e.expectTypeError(inliningTarget, notIterableProfile);
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.CANNOT_UNPACK_NON_ITERABLE, collection);
            }
            ArrayList<Object> result = new ArrayList<>();
            while (true) {
                try {
                    Object item = getNextNode.execute(virtualFrame, iterator);
                    appendItem(result, item);
                } catch (PException e) {
                    e.expectStopIteration(inliningTarget, stopIterationProfile1);
                    return result.toArray();
                }
            }
        }

        @TruffleBoundary
        private static void appendItem(ArrayList<Object> result, Object item) {
            result.add(item);
        }
    }

    @Operation
    @ConstantOperand(type = int.class)
    @ImportStatic({PGuards.class})
    public static final class UnpackSequence {
        @Specialization(guards = "isBuiltinSequence(sequence)")
        @ExplodeLoop
        public static Object[] doUnpackSequence(VirtualFrame localFrame,
                        int count,
                        PSequence sequence,
                        @Bind Node inliningTarget,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            SequenceStorage storage = getSequenceStorageNode.execute(inliningTarget, sequence);
            int len = storage.length();
            if (len == count) {
                Object[] result = new Object[len];
                for (int i = 0; i < count; i++) {
                    result[i] = getItemNode.execute(inliningTarget, storage, i);
                }
                return result;
            } else {
                if (len < count) {
                    throw raiseNode.raise(ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK, count, len);
                } else {
                    throw raiseNode.raise(ValueError, ErrorMessages.TOO_MANY_VALUES_TO_UNPACK, count);
                }
            }
        }

        @Specialization
        @ExplodeLoop
        @InliningCutoff
        public static Object[] doUnpackIterable(VirtualFrame virtualFrame,
                        int count,
                        Object collection,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetNextNode getNextNode,
                        @Cached IsBuiltinObjectProfile notIterableProfile,
                        @Cached IsBuiltinObjectProfile stopIterationProfile1,
                        @Cached IsBuiltinObjectProfile stopIterationProfile2,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {
            Object iterator;
            try {
                iterator = getIter.execute(virtualFrame, inliningTarget, collection);
            } catch (PException e) {
                e.expectTypeError(inliningTarget, notIterableProfile);
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.CANNOT_UNPACK_NON_ITERABLE, collection);
            }

            Object[] result = new Object[count];
            for (int i = 0; i < count; i++) {
                try {
                    result[i] = getNextNode.execute(virtualFrame, iterator);
                } catch (PException e) {
                    e.expectStopIteration(inliningTarget, stopIterationProfile1);
                    throw raiseNode.raise(ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK, count, i);
                }
            }
            try {
                getNextNode.execute(virtualFrame, iterator);
            } catch (PException e) {
                e.expectStopIteration(inliningTarget, stopIterationProfile2);
                return result;
            }
            throw raiseNode.raise(ValueError, ErrorMessages.TOO_MANY_VALUES_TO_UNPACK, count);
        }
    }

    @Operation
    @ConstantOperand(type = int.class)
    @ConstantOperand(type = int.class)
    @ImportStatic({PGuards.class})
    public static final class UnpackEx {
        @Specialization(guards = "isBuiltinSequence(sequence)")
        public static Object[] doUnpackSequence(VirtualFrame localFrame,
                        int countBefore,
                        int countAfter,
                        PSequence sequence,
                        @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClassNode,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Exclusive @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Exclusive @Cached SequenceStorageNodes.GetItemSliceNode getItemSliceNode,
                        @Shared @Cached PythonObjectFactory factory,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            SequenceStorage storage = getSequenceStorageNode.execute(inliningTarget, sequence);
            int len = storage.length();
            int starLen = len - countBefore - countAfter;
            if (starLen < 0) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK_EX, countBefore + countAfter, len);
            }

            Object[] result = new Object[countBefore + 1 + countAfter];
            copyItemsToArray(inliningTarget, storage, 0, result, 0, countBefore, getItemNode);
            result[countBefore] = factory.createList(getItemSliceNode.execute(storage, countBefore, countBefore + starLen, 1, starLen));
            copyItemsToArray(inliningTarget, storage, len - countAfter, result, countBefore + 1, countAfter, getItemNode);
            return result;
        }

        @Specialization
        @InliningCutoff
        public static Object[] doUnpackIterable(VirtualFrame virtualFrame,
                        int countBefore,
                        int countAfter,
                        Object collection,
                        @Bind Node inliningTarget,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetNextNode getNextNode,
                        @Cached IsBuiltinObjectProfile notIterableProfile,
                        @Cached IsBuiltinObjectProfile stopIterationProfile,
                        @Cached ListNodes.ConstructListNode constructListNode,
                        @Exclusive @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Exclusive @Cached SequenceStorageNodes.GetItemSliceNode getItemSliceNode,
                        @Shared @Cached PythonObjectFactory factory,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            Object iterator;
            try {
                iterator = getIter.execute(virtualFrame, inliningTarget, collection);
            } catch (PException e) {
                e.expectTypeError(inliningTarget, notIterableProfile);
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.CANNOT_UNPACK_NON_ITERABLE, collection);
            }

            Object[] result = new Object[countBefore + 1 + countAfter];
            copyItemsToArray(virtualFrame, inliningTarget, iterator, result, 0, countBefore, countBefore + countAfter, getNextNode, stopIterationProfile, raiseNode);
            PList starAndAfter = constructListNode.execute(virtualFrame, iterator);
            SequenceStorage storage = starAndAfter.getSequenceStorage();
            int lenAfter = storage.length();
            if (lenAfter < countAfter) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK_EX, countBefore + countAfter, countBefore + lenAfter);
            }
            if (countAfter == 0) {
                result[countBefore] = starAndAfter;
            } else {
                int starLen = lenAfter - countAfter;
                PList starList = factory.createList(getItemSliceNode.execute(storage, 0, starLen, 1, starLen));
                result[countBefore] = starList;
                copyItemsToArray(inliningTarget, storage, starLen, result, countBefore + 1, countAfter, getItemNode);
            }
            return result;
        }

        @ExplodeLoop
        private static void copyItemsToArray(VirtualFrame frame, Node inliningTarget, Object iterator, Object[] destination, int destinationOffset, int length, int totalLength,
                        GetNextNode getNextNode,
                        IsBuiltinObjectProfile stopIterationProfile, PRaiseNode.Lazy raiseNode) {
            CompilerAsserts.partialEvaluationConstant(destinationOffset);
            CompilerAsserts.partialEvaluationConstant(length);
            CompilerAsserts.partialEvaluationConstant(totalLength);
            for (int i = 0; i < length; i++) {
                try {
                    destination[destinationOffset + i] = getNextNode.execute(frame, iterator);
                } catch (PException e) {
                    e.expectStopIteration(inliningTarget, stopIterationProfile);
                    throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK_EX, totalLength, destinationOffset + i);
                }
            }
        }

        @ExplodeLoop
        private static void copyItemsToArray(Node inliningTarget, SequenceStorage source, int sourceOffset, Object[] destination, int destinationOffset, int length,
                        SequenceStorageNodes.GetItemScalarNode getItemNode) {
            CompilerAsserts.partialEvaluationConstant(sourceOffset);
            CompilerAsserts.partialEvaluationConstant(destinationOffset);
            CompilerAsserts.partialEvaluationConstant(length);
            for (int i = 0; i < length; i++) {
                destination[destinationOffset + i] = getItemNode.execute(inliningTarget, source, sourceOffset + i);
            }
        }
    }

    @Operation
    public static final class CallNilaryMethod {
        @Specialization
        @InliningCutoff
        public static Object doCall(VirtualFrame frame, Object callable,
                        @Cached CallNode node) {
            return node.execute(frame, callable, PythonUtils.EMPTY_OBJECT_ARRAY, PKeyword.EMPTY_KEYWORDS);
        }
    }

    @Operation
    public static final class CallUnaryMethod {
        @Specialization
        @InliningCutoff
        public static Object doCall(VirtualFrame frame, Object callable, Object arg0,
                        @Cached CallUnaryMethodNode node) {
            return node.executeObject(frame, callable, arg0);
        }
    }

    @Operation
    public static final class CallBinaryMethod {
        @Specialization
        @InliningCutoff
        public static Object doObject(VirtualFrame frame, Object callable, Object arg0, Object arg1,
                        @Cached CallBinaryMethodNode node) {
            return node.executeObject(frame, callable, arg0, arg1);
        }
    }

    @Operation
    public static final class CallTernaryMethod {
        @Specialization
        @InliningCutoff
        public static Object doCall(VirtualFrame frame, Object callable, Object arg0, Object arg1, Object arg2,
                        @Cached CallTernaryMethodNode node) {
            return node.execute(frame, callable, arg0, arg1, arg2);
        }
    }

    @Operation
    public static final class CallQuaternaryMethod {
        @Specialization
        @InliningCutoff
        public static Object doCall(VirtualFrame frame, Object callable, Object arg0, Object arg1, Object arg2, Object arg3,
                        @Cached CallQuaternaryMethodNode node) {
            return node.execute(frame, callable, arg0, arg1, arg2, arg3);
        }
    }

    @Operation
    public static final class CallVarargsMethod {
        @Specialization
        @InliningCutoff
        public static Object doCall(VirtualFrame frame, Object callable, Object[] args, PKeyword[] keywords,
                        @Cached CallNode node) {
            return node.execute(frame, callable, args, keywords);
        }
    }

    @Operation
    @ConstantOperand(type = LocalAccessor.class)
    @ConstantOperand(type = LocalAccessor.class)
    @ImportStatic({SpecialMethodSlot.class})
    public static final class ContextManagerEnter {
        @Specialization
        @InliningCutoff
        public static void doEnter(VirtualFrame frame,
                        LocalAccessor exitSetter,
                        LocalAccessor resultSetter,
                        Object contextManager,
                        @Bind Node inliningTarget,
                        @Bind BytecodeNode bytecode,
                        @Cached GetClassNode getClass,
                        @Cached(parameters = "Enter") LookupSpecialMethodSlotNode lookupEnter,
                        @Cached(parameters = "Exit") LookupSpecialMethodSlotNode lookupExit,
                        @Cached CallUnaryMethodNode callEnter,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object type = getClass.execute(inliningTarget, contextManager);
            Object enter = lookupEnter.execute(frame, type, contextManager);
            if (enter == PNone.NO_VALUE) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.N_OBJECT_DOES_NOT_SUPPORT_CONTEXT_MANAGER_PROTOCOL, type);
            }
            Object exit = lookupExit.execute(frame, type, contextManager);
            if (exit == PNone.NO_VALUE) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.N_OBJECT_DOES_NOT_SUPPORT_CONTEXT_MANAGER_PROTOCOL_EXIT, type);
            }
            Object result = callEnter.executeObject(frame, enter, contextManager);
            exitSetter.setObject(bytecode, frame, exit);
            resultSetter.setObject(bytecode, frame, result);
        }
    }

    @Operation
    public static final class ContextManagerExit {
        @Specialization
        public static void doRegular(VirtualFrame frame, PNone none, Object exit, Object contextManager,
                        @Shared @Cached CallQuaternaryMethodNode callExit) {
            callExit.execute(frame, exit, contextManager, PNone.NONE, PNone.NONE, PNone.NONE);
        }

        @Specialization
        @InliningCutoff
        public static void doExceptional(VirtualFrame frame,
                        Object exception, Object exit, Object contextManager,
                        @Bind Node inliningTarget,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Shared @Cached CallQuaternaryMethodNode callExit,
                        @Cached GetClassNode getClass,
                        @Cached ExceptionNodes.GetTracebackNode getTraceback,
                        @Cached PyObjectIsTrueNode isTrue,
                        @Cached PRaiseNode.Lazy raiseNode) {
            AbstractTruffleException savedExcState = PArguments.getException(frame);
            try {
                Object pythonException = exception;
                if (exception instanceof PException pException) {
                    PArguments.setException(frame, pException);
                    pythonException = pException.getEscapedException();
                }
                Object excType = getClass.execute(inliningTarget, pythonException);
                Object excTraceback = getTraceback.execute(inliningTarget, pythonException);
                Object result = callExit.execute(frame, exit, contextManager, excType, pythonException, excTraceback);
                if (!isTrue.execute(frame, inliningTarget, result)) {
                    if (exception instanceof PException pException) {
                        throw pException.getExceptionForReraise(!rootNode.isInternal());
                    } else if (exception instanceof AbstractTruffleException ate) {
                        throw ate;
                    } else {
                        throw CompilerDirectives.shouldNotReachHere("Exception not on stack");
                    }
                }
            } finally {
                PArguments.setException(frame, savedExcState);
            }
        }
    }

    @Operation
    @ConstantOperand(type = LocalAccessor.class)
    @ConstantOperand(type = LocalAccessor.class)
    @ImportStatic({SpecialMethodSlot.class})
    public static final class AsyncContextManagerEnter {
        @Specialization
        @InliningCutoff
        public static void doEnter(VirtualFrame frame,
                        LocalAccessor exitSetter,
                        LocalAccessor resultSetter,
                        Object contextManager,
                        @Bind Node inliningTarget,
                        @Bind BytecodeNode bytecode,
                        @Cached GetClassNode getClass,
                        @Cached(parameters = "AEnter") LookupSpecialMethodSlotNode lookupEnter,
                        @Cached(parameters = "AExit") LookupSpecialMethodSlotNode lookupExit,
                        @Cached CallUnaryMethodNode callEnter,
                        @Cached PRaiseNode raiseNode) {
            Object type = getClass.execute(inliningTarget, contextManager);
            Object enter = lookupEnter.execute(frame, type, contextManager);
            if (enter == PNone.NO_VALUE) {
                throw raiseNode.raise(AttributeError, new Object[]{T___AENTER__});
            }
            Object exit = lookupExit.execute(frame, type, contextManager);
            if (exit == PNone.NO_VALUE) {
                throw raiseNode.raise(AttributeError, new Object[]{T___AEXIT__});
            }
            Object result = callEnter.executeObject(frame, enter, contextManager);
            exitSetter.setObject(bytecode, frame, exit);
            resultSetter.setObject(bytecode, frame, result);
        }
    }

    @Operation
    public static final class AsyncContextManagerCallExit {
        @Specialization
        public static Object doRegular(VirtualFrame frame,
                        PNone none, Object exit, Object contextManager,
                        @Shared @Cached CallQuaternaryMethodNode callExit) {
            return callExit.execute(frame, exit, contextManager, PNone.NONE, PNone.NONE, PNone.NONE);
        }

        @Specialization
        @InliningCutoff
        public static Object doExceptional(VirtualFrame frame,
                        Object exception, Object exit, Object contextManager,
                        @Bind Node inliningTarget,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Shared @Cached CallQuaternaryMethodNode callExit,
                        @Cached GetClassNode getClass,
                        @Cached ExceptionNodes.GetTracebackNode getTraceback,
                        @Cached PyObjectIsTrueNode isTrue,
                        @Cached PRaiseNode.Lazy raiseNode) {
            AbstractTruffleException savedExcState = PArguments.getException(frame);
            try {
                Object pythonException = exception;
                if (exception instanceof PException) {
                    PArguments.setException(frame, (PException) exception);
                    pythonException = ((PException) exception).getEscapedException();
                }
                Object excType = getClass.execute(inliningTarget, pythonException);
                Object excTraceback = getTraceback.execute(inliningTarget, pythonException);
                return callExit.execute(frame, exit, contextManager, excType, pythonException, excTraceback);
            } finally {
                PArguments.setException(frame, savedExcState);
            }
        }
    }

    @Operation
    public static final class AsyncContextManagerExit {
        /**
         * NB: There is nothing to do after awaiting __exit__(None, None, None), so this operation
         * is only emitted for the case where the context manager exits due to an exception.
         */
        @Specialization
        @InliningCutoff
        public static void doExceptional(VirtualFrame frame,
                        Object exception, Object result,
                        @Bind Node inliningTarget,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Cached CallQuaternaryMethodNode callExit,
                        @Cached GetClassNode getClass,
                        @Cached ExceptionNodes.GetTracebackNode getTraceback,
                        @Cached PyObjectIsTrueNode isTrue,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (!isTrue.execute(frame, inliningTarget, result)) {
                if (exception instanceof PException) {
                    throw ((PException) exception).getExceptionForReraise(!rootNode.isInternal());
                } else if (exception instanceof AbstractTruffleException) {
                    throw (AbstractTruffleException) exception;
                } else {
                    throw CompilerDirectives.shouldNotReachHere("Exception not on stack");
                }
            }
        }
    }

    @Operation
    @ConstantOperand(type = int.class)
    public static final class BuildString {
        @Specialization
        public static Object perform(
                        int length,
                        @Variadic Object[] strings,
                        @Cached TruffleStringBuilder.AppendStringNode appendNode,
                        @Cached TruffleStringBuilder.ToStringNode toString) {
            TruffleStringBuilder tsb = TruffleStringBuilder.create(PythonUtils.TS_ENCODING);
            if (length <= EXPLODE_LOOP_THRESHOLD) {
                doExploded(strings, length, appendNode, tsb);
            } else {
                doRegular(strings, length, appendNode, tsb);
            }
            return toString.execute(tsb);
        }

        @ExplodeLoop
        private static void doExploded(Object[] strings, int length, TruffleStringBuilder.AppendStringNode appendNode, TruffleStringBuilder tsb) {
            CompilerAsserts.partialEvaluationConstant(length);
            for (int i = 0; i < length; i++) {
                appendNode.execute(tsb, (TruffleString) strings[i]);
            }
        }

        private static void doRegular(Object[] strings, int length, TruffleStringBuilder.AppendStringNode appendNode, TruffleStringBuilder tsb) {
            for (int i = 0; i < length; i++) {
                appendNode.execute(tsb, (TruffleString) strings[i]);
            }
        }
    }

    @Operation
    @ConstantOperand(type = LocalAccessor.class)
    public static final class TeeLocal {
        @Specialization
        public static int doInt(VirtualFrame frame, LocalAccessor local, int value,
                        @Bind BytecodeNode bytecode) {
            local.setInt(bytecode, frame, value);
            return value;
        }

        @Specialization
        public static double doDouble(VirtualFrame frame, LocalAccessor local, double value,
                        @Bind BytecodeNode bytecode) {
            local.setDouble(bytecode, frame, value);
            return value;
        }

        @Specialization
        public static long doLong(VirtualFrame frame, LocalAccessor local, long value,
                        @Bind BytecodeNode bytecode) {
            local.setLong(bytecode, frame, value);
            return value;
        }

        @Specialization(replaces = {"doInt", "doDouble", "doLong"})
        public static Object doObject(VirtualFrame frame, LocalAccessor local, Object value,
                        @Bind BytecodeNode bytecode) {
            local.setObject(bytecode, frame, value);
            return value;
        }
    }

    @Operation
    public static final class GetLen {
        @Specialization
        public static int doObject(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached PyObjectSizeNode sizeNode) {
            return sizeNode.execute(frame, inliningTarget, value);
        }
    }

    @Operation
    @ConstantOperand(type = long.class)
    public static final class CheckTypeFlags {
        @Specialization
        public static boolean doObject(long typeFlags, Object value,
                        @Cached GetTPFlagsNode getTPFlagsNode) {
            return (getTPFlagsNode.execute(value) & typeFlags) != 0;
        }
    }

    @Operation
    @ImportStatic(PGuards.class)
    public static final class BinarySubscript {
        // TODO: the result is not BE'd because of the UnexpectedResultException. maybe we should
        // explicitly check for an int storage type?
        @Specialization(rewriteOn = UnexpectedResultException.class, guards = "isBuiltinList(list)")
        public static int doIntList(PList list, int index,
                        @Shared @Cached("createForList()") SequenceStorageNodes.GetItemNode getListItemNode) throws UnexpectedResultException {
            return getListItemNode.executeInt(list.getSequenceStorage(), index);
        }

        @Specialization(rewriteOn = UnexpectedResultException.class, guards = "isBuiltinList(list)")
        public static double doDoubleList(PList list, int index,
                        @Shared @Cached("createForList()") SequenceStorageNodes.GetItemNode getListItemNode) throws UnexpectedResultException {
            return getListItemNode.executeDouble(list.getSequenceStorage(), index);
        }

        @Specialization(replaces = {"doIntList", "doDoubleList"}, guards = "isBuiltinList(list)")
        public static Object doObjectList(PList list, int index,
                        @Shared @Cached("createForList()") SequenceStorageNodes.GetItemNode getListItemNode) {
            return getListItemNode.execute(list.getSequenceStorage(), index);
        }

        @Specialization(rewriteOn = UnexpectedResultException.class, guards = "isBuiltinTuple(tuple)")
        public static int doIntTuple(PTuple tuple, int index,
                        @Shared @Cached("createForTuple()") SequenceStorageNodes.GetItemNode getTupleItemNode) throws UnexpectedResultException {
            return getTupleItemNode.executeInt(tuple.getSequenceStorage(), index);

        }

        @Specialization(rewriteOn = UnexpectedResultException.class, guards = "isBuiltinTuple(tuple)")
        public static double doDoubleTuple(PTuple tuple, int index,
                        @Shared @Cached("createForTuple()") SequenceStorageNodes.GetItemNode getTupleItemNode) throws UnexpectedResultException {
            return getTupleItemNode.executeDouble(tuple.getSequenceStorage(), index);
        }

        @Specialization(replaces = {"doIntTuple", "doDoubleTuple"}, guards = "isBuiltinTuple(tuple)")
        public static Object doObjectTuple(PTuple tuple, int index,
                        @Shared @Cached("createForTuple()") SequenceStorageNodes.GetItemNode getTupleItemNode) {
            return getTupleItemNode.execute(tuple.getSequenceStorage(), index);
        }

        @Fallback
        public static Object doOther(VirtualFrame frame, Object receiver, Object key,
                        @Bind("this") Node inliningTarget,
                        @Cached GetObjectSlotsNode getSlotsNode,
                        @Cached PyObjectGetItem.PyObjectGetItemGeneric getItemNode) {
            TpSlots slots = getSlotsNode.execute(inliningTarget, receiver);
            return getItemNode.execute(frame, inliningTarget, receiver, slots, key);
        }
    }

    /**
     * Performs some clean-up steps before suspending execution.
     */
    @Operation
    public static final class PreYield {
        @Specialization
        public static Object doObject(VirtualFrame frame, Object value,
                        @Bind Node location,
                        @Bind PBytecodeDSLRootNode root) {
            if (root.needsTraceAndProfileInstrumentation()) {
                root.traceOrProfileReturn(frame, location, value);
                root.getThreadState().popInstrumentationData(root);
            }
            return value;
        }
    }

    /**
     * Resumes execution after yield.
     */
    @Operation
    public static final class ResumeYield {
        @Specialization
        public static Object doObject(VirtualFrame frame, Object sendValue,
                        @Bind Node location,
                        @Bind PBytecodeDSLRootNode root,
                        @Bind BytecodeNode bytecode,
                        @Bind("$bytecodeIndex") int bci,
                        @Cached GetSendValueNode getSendValue) {
            if (root.needsTraceAndProfileInstrumentation()) {
                // We may not have reparsed the root with instrumentation yet.
                root.ensureTraceAndProfileEnabled();
                root.getThreadState().pushInstrumentationData(root);
                root.traceOrProfileCall(frame, location, bytecode, bci);
            }

            return getSendValue.execute(sendValue);
        }
    }

    @Operation
    @ConstantOperand(type = LocalAccessor.class)
    @ConstantOperand(type = LocalAccessor.class)
    @SuppressWarnings("truffle-interpreted-performance")
    public static final class YieldFromSend {
        private static final TruffleString T_SEND = tsLiteral("send");

        @Specialization
        static boolean doGenerator(VirtualFrame virtualFrame,
                        LocalAccessor yieldedValue,
                        LocalAccessor returnedValue,
                        PGenerator generator,
                        Object arg,
                        @Bind Node inliningTarget,
                        @Bind BytecodeNode bytecode,
                        @Cached CommonGeneratorBuiltins.SendNode sendNode,
                        @Shared @Cached IsBuiltinObjectProfile stopIterationProfile,
                        @Shared @Cached StopIterationBuiltins.StopIterationValueNode getValue) {
            try {
                Object value = sendNode.execute(virtualFrame, generator, arg);
                yieldedValue.setObject(bytecode, virtualFrame, value);
                return false;
            } catch (PException e) {
                handleException(virtualFrame, e, inliningTarget, bytecode, stopIterationProfile, getValue, returnedValue);
                return true;
            }
        }

        @Specialization(guards = "iterCheck.execute(inliningTarget, iter)", limit = "1")
        static boolean doIterator(VirtualFrame virtualFrame,
                        LocalAccessor yieldedValue,
                        LocalAccessor returnedValue,
                        Object iter,
                        @SuppressWarnings("unused") PNone arg,
                        @Bind Node inliningTarget,
                        @Bind BytecodeNode bytecode,
                        @SuppressWarnings("unused") @Cached PyIterCheckNode iterCheck,
                        @Cached GetNextNode getNextNode,
                        @Shared @Cached IsBuiltinObjectProfile stopIterationProfile,
                        @Shared @Cached StopIterationBuiltins.StopIterationValueNode getValue) {
            try {
                Object value = getNextNode.execute(virtualFrame, iter);
                yieldedValue.setObject(bytecode, virtualFrame, value);
                return false;
            } catch (PException e) {
                handleException(virtualFrame, e, inliningTarget, bytecode, stopIterationProfile, getValue, returnedValue);
                return true;
            }
        }

        @Fallback
        static boolean doOther(VirtualFrame virtualFrame,
                        LocalAccessor yieldedValue,
                        LocalAccessor returnedValue,
                        Object obj,
                        Object arg,
                        @Bind Node inliningTarget,
                        @Bind BytecodeNode bytecode,
                        @Bind("$bytecodeIndex") int bci,
                        @Cached PyObjectCallMethodObjArgs callMethodNode,
                        @Shared @Cached IsBuiltinObjectProfile stopIterationProfile,
                        @Shared @Cached StopIterationBuiltins.StopIterationValueNode getValue) {
            try {
                Object value = callMethodNode.execute(virtualFrame, inliningTarget, obj, T_SEND, arg);
                yieldedValue.setObject(bytecode, virtualFrame, value);
                return false;
            } catch (PException e) {
                handleException(virtualFrame, e, inliningTarget, bytecode, stopIterationProfile, getValue, returnedValue);
                return true;
            }
        }

        private static void handleException(VirtualFrame frame, PException e, Node inliningTarget, BytecodeNode bytecode,
                        IsBuiltinObjectProfile stopIterationProfile,
                        StopIterationBuiltins.StopIterationValueNode getValue,
                        LocalAccessor returnedValue) {
            e.expectStopIteration(inliningTarget, stopIterationProfile);
            returnedValue.setObject(bytecode, frame, getValue.execute((PBaseException) e.getUnreifiedException()));
        }

    }

    @Operation
    @ConstantOperand(type = LocalAccessor.class)
    @ConstantOperand(type = LocalAccessor.class)
    @SuppressWarnings("truffle-interpreted-performance")
    public static final class YieldFromThrow {

        private static final TruffleString T_CLOSE = tsLiteral("close");
        private static final TruffleString T_THROW = tsLiteral("throw");

        @Specialization
        static boolean doGenerator(VirtualFrame frame,
                        LocalAccessor yieldedValue,
                        LocalAccessor returnedValue,
                        PGenerator generator,
                        PException exception,
                        @Bind Node inliningTarget,
                        @Bind BytecodeNode bytecode,
                        @Cached CommonGeneratorBuiltins.ThrowNode throwNode,
                        @Cached CommonGeneratorBuiltins.CloseNode closeNode,
                        @Shared @Cached IsBuiltinObjectProfile profileExit,
                        @Shared @Cached IsBuiltinObjectProfile stopIterationProfile,
                        @Shared @Cached StopIterationBuiltins.StopIterationValueNode getValue) {
            if (profileExit.profileException(inliningTarget, exception, GeneratorExit)) {
                closeNode.execute(frame, generator);
                throw exception;
            } else {
                try {
                    Object value = throwNode.execute(frame, generator, exception.getEscapedException(), PNone.NO_VALUE, PNone.NO_VALUE);
                    yieldedValue.setObject(bytecode, frame, value);
                    return false;
                } catch (PException e) {
                    handleException(frame, e, inliningTarget, bytecode, stopIterationProfile, getValue, returnedValue);
                    return true;
                }
            }
        }

        @Fallback
        static boolean doOther(VirtualFrame frame,
                        LocalAccessor yieldedValue,
                        LocalAccessor returnedValue,
                        Object obj,
                        Object exception,
                        @Bind Node inliningTarget,
                        @Bind BytecodeNode bytecode,
                        @Cached PyObjectLookupAttr lookupThrow,
                        @Cached PyObjectLookupAttr lookupClose,
                        @Cached CallNode callThrow,
                        @Cached CallNode callClose,
                        @Cached WriteUnraisableNode writeUnraisableNode,
                        @Shared @Cached IsBuiltinObjectProfile profileExit,
                        @Shared @Cached IsBuiltinObjectProfile stopIterationProfile,
                        @Shared @Cached StopIterationBuiltins.StopIterationValueNode getValue) {
            PException pException = (PException) exception;
            if (profileExit.profileException(inliningTarget, pException, GeneratorExit)) {
                Object close = PNone.NO_VALUE;
                try {
                    close = lookupClose.execute(frame, inliningTarget, obj, T_CLOSE);
                } catch (PException e) {
                    writeUnraisableNode.execute(frame, e.getEscapedException(), null, obj);
                }
                if (close != PNone.NO_VALUE) {
                    callClose.execute(frame, close);
                }
                throw pException;
            } else {
                Object throwMethod = lookupThrow.execute(frame, inliningTarget, obj, T_THROW);
                if (throwMethod == PNone.NO_VALUE) {
                    throw pException;
                }
                try {
                    Object value = callThrow.execute(frame, throwMethod, pException.getEscapedException());
                    yieldedValue.setObject(bytecode, frame, value);
                    return false;
                } catch (PException e) {
                    handleException(frame, e, inliningTarget, bytecode, stopIterationProfile, getValue, returnedValue);
                    return true;
                }
            }
        }

        private static void handleException(VirtualFrame frame, PException e, Node inliningTarget, BytecodeNode bytecode,
                        IsBuiltinObjectProfile stopIterationProfile, StopIterationBuiltins.StopIterationValueNode getValue,
                        LocalAccessor returnedValue) {
            e.expectStopIteration(inliningTarget, stopIterationProfile);
            returnedValue.setObject(bytecode, frame, getValue.execute((PBaseException) e.getUnreifiedException()));
        }
    }

    /**
     * Loads a user-defined local variable. Unlike a built-in LoadLocal, this operation raises an
     * unbound local error if the local has not been set.
     * <p>
     * This operation makes use of Truffle's boxing overloads. When an operation tries to quicken
     * this one for boxing elimination, the correct overload will be selected.
     */
    @Operation
    @ConstantOperand(type = LocalAccessor.class)
    @ConstantOperand(type = int.class)
    public static final class CheckAndLoadLocal {
        @Specialization(rewriteOn = UnexpectedResultException.class)
        public static int doInt(VirtualFrame frame, LocalAccessor accessor, int index,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Bind BytecodeNode bytecodeNode,
                        @Bind Node inliningTarget,
                        @Shared @Cached InlinedBranchProfile localUnboundProfile) throws UnexpectedResultException {
            if (accessor.isCleared(bytecodeNode, frame)) {
                localUnboundProfile.enter(inliningTarget);
                throw raiseUnbound(rootNode, inliningTarget, index);
            }
            return accessor.getInt(bytecodeNode, frame);
        }

        @Specialization(rewriteOn = UnexpectedResultException.class)
        public static boolean doBoolean(VirtualFrame frame, LocalAccessor accessor, int index,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Bind BytecodeNode bytecodeNode,
                        @Bind Node inliningTarget,
                        @Shared @Cached InlinedBranchProfile localUnboundProfile) throws UnexpectedResultException {
            if (accessor.isCleared(bytecodeNode, frame)) {
                localUnboundProfile.enter(inliningTarget);
                throw raiseUnbound(rootNode, inliningTarget, index);
            }
            return accessor.getBoolean(bytecodeNode, frame);
        }

        @Specialization(replaces = {"doInt", "doBoolean"})
        public static Object doObject(VirtualFrame frame, LocalAccessor accessor, int index,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Bind BytecodeNode bytecodeNode,
                        @Bind Node inliningTarget,
                        @Cached InlinedBranchProfile localUnboundProfile) {
            if (accessor.isCleared(bytecodeNode, frame)) {
                localUnboundProfile.enter(inliningTarget);
                throw raiseUnbound(rootNode, inliningTarget, index);
            }
            return accessor.getObject(bytecodeNode, frame);
        }
    }

    @Operation
    @ConstantOperand(type = LocalAccessor.class)
    @ConstantOperand(type = int.class)
    public static final class DeleteLocal {
        @Specialization
        public static void doObject(VirtualFrame frame, LocalAccessor accessor, int index,
                        @Bind PBytecodeDSLRootNode rootNode,
                        @Bind BytecodeNode bytecodeNode,
                        @Bind Node inliningTarget,
                        @Cached InlinedBranchProfile localUnboundProfile) {
            if (accessor.isCleared(bytecodeNode, frame)) {
                localUnboundProfile.enter(inliningTarget);
                throw raiseUnbound(rootNode, inliningTarget, index);
            }
            accessor.clear(bytecodeNode, frame);
        }
    }

    @TruffleBoundary
    private static PException raiseUnbound(PBytecodeDSLRootNode rootNode, Node inliningTarget, int index) {
        TruffleString localName = rootNode.getCodeUnit().varnames[index];
        throw PRaiseNode.raiseUncached(inliningTarget, PythonBuiltinClassType.UnboundLocalError, ErrorMessages.LOCAL_VAR_REFERENCED_BEFORE_ASSIGMENT, localName);
    }

    @Operation
    public static final class RaiseNotImplementedError {
        @Specialization
        public static void doRaise(VirtualFrame frame, TruffleString name,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonBuiltinClassType.NotImplementedError, name);

        }
    }
}

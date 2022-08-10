package io.artemis.mut;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.artemis.Artemis;
import io.artemis.AxChecker;
import io.artemis.AxLog;
import io.artemis.AxNames;
import io.artemis.AxRandom;
import io.artemis.skl.MiCtrlSeqSkl;
import io.artemis.skl.MiLoopSkl;
import io.artemis.syn.CodeSyn;
import io.artemis.syn.PPoint;
import io.artemis.util.Spoons;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;

public class MethInvocator extends MethMutator {

    public MethInvocator(Artemis ax) {
        super(ax);
    }

    @Override
    protected boolean canMutate(CtMethod<?> meth) {
        return meth.getDeclaringType() instanceof CtClass
                && meth.getBody().getStatements().size() > 0;
    }

    @Override
    protected void mutate(CtMethod<?> meth) {
        CtClass<?> clazz = (CtClass<?>) meth.getDeclaringType();
        AxChecker.check(clazz != null, "No class found for method " + meth.getSimpleName() + "()");

        // Find all method calls to meth
        List<CtInvocation<?>> invocations = clazz.getElements(new TypeFilter<>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation invoc) {
                return super.matches(invoc)
                        && invoc.getExecutable().getExecutableDeclaration() == meth;
            }
        });
        if (invocations.size() == 0) {
            // No invocations found, do nothing
            AxLog.v("No method invocations found, discard this mutation");
            return;
        }

        Factory fact = mAx.getSpoon().getFactory();
        CodeSyn syn = mAx.getCodeSyn();

        // Create a control field in the class to control the field
        CtField<Boolean> ctrl = fact.createCtField(/* name= */AxNames.getInstance().nextName(),
                fact.createCtTypeReference(Boolean.class), "false");
        if (clazz.isStatic() || clazz.isTopLevel()) {
            ctrl.addModifier(ModifierKind.STATIC);
        }
        AxLog.v("Adding control field: " + ctrl.getSimpleName());
        clazz.addField(ctrl);

        List<CtImport> imports = new ArrayList<>(5);

        // Create the control sequence and insert to meth as a prologue
        AxLog.v("Synthesizing control prologue controlled by field " + Spoons.getSimpleName(ctrl));
        CtReturn<?> retStmt = fact.createReturn();
        if (!Spoons.isVoidType(meth.getType())) {
            retStmt.setReturnedExpression((CtExpression) syn.synExpr(meth.getType()));
        }
        CtStatement ctrlSeq = MiCtrlSeqSkl.instantiate(mAx, ctrl,
                syn.synCodeSeg(
                        PPoint.beforeStmt(mAx.getTestClass(), meth.getBody().getStatement(0)),
                        imports),
                retStmt);
        AxLog.v("Add the following control prologue to method " + Spoons.getSimpleName(meth),
                (out, ignoreUnused) -> out.println(ctrlSeq));
        meth.getBody().insertBegin(ctrlSeq);

        // Randomly select an invocation, synthesize and insert a loop before it
        CtInvocation<?> invoc = invocations.get(AxRandom.getInstance().nextInt(invocations.size()));
        PPoint pp = PPoint.beforeStmt(mAx.getTestClass(), invoc);

        AxLog.v("Synthesizing new loops with MethInvocator's skeleton");
        CtStatement loop = mAx.getCodeSyn().synLoop(pp, new MiLoopSkl(), imports);

        // Substitute placeholders in the skeleton
        AxLog.v("Boosting the given by the following loop",
                (out, ignoreUnused) -> out.println(loop));
        MiLoopSkl.enableCtrl(loop, ctrl, fact);
        MiLoopSkl.disableCtrl(loop, ctrl, fact);
        List<CtExpression<?>> args =
                invoc.getArguments().stream().map(CtExpression::clone).collect(Collectors.toList());
        MiLoopSkl.invocMeth(loop, invoc, args, fact);
        // Insert the loop right before the invocation statement
        Spoons.insertBeforeStmt(invoc, loop);

        // Add required imports to our tests
        mAx.getTestCompUnit().getImports().addAll(imports);
    }
}

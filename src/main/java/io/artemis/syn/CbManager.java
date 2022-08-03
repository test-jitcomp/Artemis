package io.artemis.syn;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import io.artemis.AxChecker;
import io.artemis.AxNames;
import io.artemis.util.Spoons;
import spoon.Launcher;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.visitor.filter.TypeFilter;

/* package */ class CbManager {

    private static final String MANIFEST_NAME = "MANIFEST";
    private static final String MANIFEST_LINE_CB_INITZ_PREFIX = "InitzCount=";
    private static final String MANIFEST_LINE_CB_PREFIX = "CbCount=";
    private static final String CB_INITZ_CLASS_NAME_PREFIX = "InitzClass";
    private static final String CB_CLASS_NAME_PREFIX = "TplClass";
    private static final String CB_METHOD_NAME = "method";

    private final File mCbFolder;
    private int mCbCount;
    private int mInitzCount;

    public CbManager(File cbFolder) {
        mCbFolder = cbFolder;
        mCbCount = -1;
        mInitzCount = -1;
    }

    public void init() throws IOException {
        // Read manifest to learn the number of initializer and templates
        File mani = new File(mCbFolder, MANIFEST_NAME);
        BufferedReader reader = new BufferedReader(new FileReader(mani));
        String newLine;
        while ((newLine = reader.readLine()) != null) {
            if (newLine.startsWith(MANIFEST_LINE_CB_INITZ_PREFIX)) {
                mInitzCount =
                        Integer.parseInt(newLine.substring(MANIFEST_LINE_CB_INITZ_PREFIX.length()));
            } else if (newLine.startsWith(MANIFEST_LINE_CB_PREFIX)) {
                mCbCount = Integer.parseInt(newLine.substring(MANIFEST_LINE_CB_PREFIX.length()));
            }
        }
    }

    public int getCbCount() {
        return mCbCount;
    }

    public int getInitzCount() {
        return mInitzCount;
    }

    public CodeBrick getCodeBrick(int index) {
        File cbFile = new File(mCbFolder.getAbsolutePath() + File.separator + CB_CLASS_NAME_PREFIX
                + index + ".java");
        AxChecker.check(cbFile.exists(), "Code brick class not found: " + cbFile.getAbsolutePath());

        Launcher launcher = new Launcher();
        launcher.addInputResource(cbFile.getAbsolutePath());
        launcher.buildModel();

        CtCompilationUnit unit =
                launcher.getFactory().CompilationUnit().getOrCreate(cbFile.getAbsolutePath());
        AxChecker.check(unit != null, "No compilation unit found in the code brick");

        // We assume that the code brick class have the same name as the file
        String cbClassName = cbFile.getName().substring(0, cbFile.getName().indexOf(".java"));
        CtClass<?> cbClass = launcher.getFactory().Class().get(cbClassName);
        AxChecker.check(cbClass.getMethods().size() == 1, "The code brick has >=1 code bricks");

        CtMethod<?> cbMethod = null;
        try {
            cbMethod = cbClass.getMethodsByName(CB_METHOD_NAME).get(0);
        } catch (IndexOutOfBoundsException e) {
            // noinspection ConstantConditions
            AxChecker.check(false, "No code brick namely " + CB_METHOD_NAME + "() found");
        }
        AxChecker.check(cbMethod != null, "No code brick namely " + CB_METHOD_NAME + "() found");
        AxChecker.check(cbMethod.getBody() != null,
                "No statements found in code brick " + cbClassName + "#" + CB_METHOD_NAME + "()");

        // Rename every parameter (i.e., input of the brick) and local variable
        // such that we don't conflict when instantiating the brick.
        for (CtParameter<?> param : cbMethod.getParameters()) {
            Spoons.renameVariable(param, AxNames.getInstance().nextName());
        }
        for (CtLocalVariable<?> local : cbMethod
                .getElements(new TypeFilter<>(CtLocalVariable.class))) {
            Spoons.renameVariable(local, AxNames.getInstance().nextName());
        }

        return new CodeBrick(cbMethod, unit.getImports());
    }
}

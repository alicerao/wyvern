package wyvern.tools.parsing;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import wyvern.target.corewyvernIL.decl.ValDeclaration;
import wyvern.target.corewyvernIL.decltype.DeclType;
import wyvern.target.corewyvernIL.expression.BooleanLiteral;
import wyvern.target.corewyvernIL.expression.Expression;
import wyvern.target.corewyvernIL.expression.Invokable;
import wyvern.target.corewyvernIL.expression.JavaValue;
import wyvern.target.corewyvernIL.expression.ObjectValue;
import wyvern.target.corewyvernIL.expression.StringLiteral;
import wyvern.target.corewyvernIL.modules.TypedModuleSpec;
import wyvern.target.corewyvernIL.support.GenContext;
import wyvern.target.corewyvernIL.type.NominalType;
import wyvern.target.corewyvernIL.type.ValueType;
import wyvern.tools.errors.ErrorMessage;
import wyvern.tools.errors.FileLocation;
import wyvern.tools.errors.ToolError;
import wyvern.tools.interop.JavaWrapper;
import wyvern.tools.typedAST.abs.AbstractExpressionAST;
import wyvern.tools.typedAST.interfaces.ExpressionAST;
import wyvern.tools.typedAST.interfaces.TypedAST;

/**
 * Created by Ben Chung on 3/11/14.
 */
public class DSLLit extends AbstractExpressionAST implements ExpressionAST {
    private Optional<String> dslText = Optional.empty();
    private TypedAST dslAST = null;
    private FileLocation location;

    public void setText(String text) {
        if (dslText == null) {
            throw new RuntimeException();
        }
        dslText = Optional.of(text);
    }

    public Optional<String> getText() {
        return dslText;
    }

    public DSLLit(Optional<String> dslText, FileLocation loc) {
        location = loc;
        this.dslText = (dslText);
    }

    public TypedAST getAST() {
        return (dslAST);
    }

    @Override
    public FileLocation getLocation() {
        return location;
    }

    @Override
    public Expression generateIL(GenContext ctx, ValueType expectedType, List<TypedModuleSpec> dependencies) {
        if (expectedType == null) {
            ToolError.reportError(ErrorMessage.NO_EXPECTED_TYPE, this);
        }
        try {
            final wyvern.target.corewyvernIL.expression.Value metadata = expectedType.getMetadata(ctx);
            if (!(metadata instanceof Invokable)) {
                ToolError.reportError(ErrorMessage.METADATA_MUST_BE_AN_OBJECT, this, expectedType.toString());
            }
            ValueType metaType = metadata.getType();
            final DeclType parseTSLDecl = metaType.getStructuralType(ctx).findDecl("parseTSL", ctx);
            if (parseTSLDecl == null) {
                ToolError.reportError(ErrorMessage.METADATA_MUST_INCLUDE_PARSETSL, this, expectedType.toString());
            }
            // TODO: check that parseTSLDecl has the right signature
            List<wyvern.target.corewyvernIL.expression.Value> args = new LinkedList<wyvern.target.corewyvernIL.expression.Value>();
            args.add(new StringLiteral(dslText.get()));
            args.add(new JavaValue(JavaWrapper.wrapObject(ctx), new NominalType("system", "Context")));
            wyvern.target.corewyvernIL.expression.Value parsedAST = ((Invokable) metadata).invoke("parseTSL", args).executeIfThunk();
            // we get an option back, is it success?
            ValDeclaration isDefined = (ValDeclaration) ((ObjectValue) parsedAST).findDecl("isDefined");
            BooleanLiteral success = (BooleanLiteral) isDefined.getDefinition();
            if (success.getValue()) {
                ValDeclaration valueDecl = (ValDeclaration) ((ObjectValue) parsedAST).findDecl("value");
                ObjectValue astWrapper = (ObjectValue) valueDecl.getDefinition();
                ValDeclaration astDecl = (ValDeclaration) ((ObjectValue) astWrapper).findDecl("ast");
                return (Expression) ((JavaValue) astDecl.getDefinition()).getWrappedValue();
            } else {
                ToolError.reportError(ErrorMessage.TSL_ERROR, this, "[detailed TSL error messages not supported yet]");
                throw new RuntimeException("can't get here");
            }
        } catch (ToolError e) {
            if (e.getTypecheckingErrorMessage() == ErrorMessage.CANNOT_USE_METADATA_IN_SAME_FILE) {
                if (e.getLocation() == null) {
                    // provide an error with the usage location
                    ToolError.reportError(ErrorMessage.CANNOT_USE_METADATA_IN_SAME_FILE, this);
                }
            }
            FileLocation loc = e.getLocation();
            FileLocation myLoc = this.getLocation();
            FileLocation newLoc = new FileLocation(myLoc.getFilename(), myLoc.getLine() + loc.getLine() - 1, myLoc.getCharacter() + loc.getCharacter() - 1);
            ToolError updatedE = e.withNewLocation(newLoc);
            throw updatedE;
        }
    }

    @Override
    public StringBuilder prettyPrint() {
        StringBuilder sb = new StringBuilder();
        sb.append("DSLLit(TODO)");
        return sb;
    }
}

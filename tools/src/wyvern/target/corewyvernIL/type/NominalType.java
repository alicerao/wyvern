package wyvern.target.corewyvernIL.type;

import java.io.IOException;
import java.util.Arrays;

import wyvern.target.corewyvernIL.astvisitor.ASTVisitor;
import wyvern.target.corewyvernIL.decltype.AbstractTypeMember;
import wyvern.target.corewyvernIL.decltype.ConcreteTypeMember;
import wyvern.target.corewyvernIL.decltype.DeclType;
import wyvern.target.corewyvernIL.decltype.DefinedTypeMember;
import wyvern.target.corewyvernIL.decltype.TaggedTypeMember;
import wyvern.target.corewyvernIL.expression.Path;
import wyvern.target.corewyvernIL.expression.Value;
import wyvern.target.corewyvernIL.expression.Variable;
import wyvern.target.corewyvernIL.support.FailureReason;
import wyvern.target.corewyvernIL.support.SubtypeAssumption;
import wyvern.target.corewyvernIL.support.TypeContext;
import wyvern.target.corewyvernIL.support.View;
import wyvern.tools.errors.ErrorMessage;
import wyvern.tools.errors.FileLocation;
import wyvern.tools.errors.ToolError;

public class NominalType extends ValueType {
    private Path path;
    private String typeMember;

    public NominalType(String pathVariable, String typeMember) {
        super();
        this.path = new Variable(pathVariable);
        this.typeMember = typeMember;
    }

    public NominalType(Path path, String typeMember) {
        this(path, typeMember, null);
    }
    public NominalType(Path path, String typeMember, FileLocation location) {
        super(location);
        if (path.equals(null)) {
            throw new IllegalStateException("Path cannot be null.");
        }
        this.path = path;
        this.typeMember = typeMember;
    }

    public Path getPath() {
        return path;
    }

    public String getTypeMember() {
        return typeMember;
    }

    @Override
    public boolean isResource(TypeContext ctx) {
        DeclType dt = getSourceDeclType(ctx);
        if (dt instanceof DefinedTypeMember) {
            ValueType vt = ((DefinedTypeMember) dt).getResultType(View.from(path, ctx));
            return vt.isResource(ctx);
        } else {
            return ((AbstractTypeMember) dt).isResource();
        }
    }

    @Override
    public StructuralType getStructuralType(TypeContext ctx, StructuralType theDefault) {
        DeclType dt = null;
        try {
            dt = getSourceDeclType(ctx);
        } catch (RuntimeException e) {
            return super.getStructuralType(ctx, theDefault); // can't look up a structural type
        }
        if (dt instanceof DefinedTypeMember) {
            ValueType vt = ((DefinedTypeMember) dt).getResultType(View.from(path, ctx));
            if (vt.equals(this)) {
                throw new RuntimeException("circularly defined type; should not be possible");
            }
            return vt.getStructuralType(ctx, theDefault);
        } else {
            return super.getStructuralType(ctx, theDefault);
        }
    }

    private DeclType getSourceDeclType(TypeContext ctx) {
        final StructuralType structuralType = path.typeCheck(ctx, null).getStructuralType(ctx);
        // return any DefinedTypeMember or AbstractTypeMember
        return structuralType.findMatchingDecl(typeMember, cdt -> !(cdt instanceof DefinedTypeMember || cdt instanceof AbstractTypeMember), ctx);
    }

    @Override
    public ValueType getCanonicalType(TypeContext ctx) {
        DeclType dt = null;
        try {
            dt = getSourceDeclType(ctx);
        } catch (RuntimeException e) {
            // failed to get a canonical type
            return this;
        }
        if (dt instanceof ConcreteTypeMember) {
            final ValueType resultType = ((ConcreteTypeMember) dt).getResultType(View.from(path, ctx));
            if (this.equals(resultType)) {
                return this;
            } else {
                return resultType.getCanonicalType(ctx);
            }
        } else {
            return this;
        }
    }

    @Override
    public void doPrettyPrint(Appendable dest, String indent, TypeContext ctx) throws IOException {
        String desugared = null;
        if (ctx != null) {
            if (path instanceof Variable) {
                desugared = ctx.desugarType(path, typeMember);
            }
        }
        if (desugared == null) {
            path.doPrettyPrint(dest, indent);
            dest.append('.').append(typeMember);
        } else {
            dest.append(desugared);
        }
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new Object[] {path, typeMember});
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof NominalType)) {
            return false;
        }
        NominalType other = (NominalType) obj;
        return path.equals(other.path) && typeMember.equals(other.typeMember);
    }

    @Override
    public boolean isSubtypeOf(ValueType t, TypeContext ctx, FailureReason reason) {
        // check if they are the same type
        if (super.isSubtypeOf(t, ctx, new FailureReason())) {
            return true;
        }
        if (ctx.isAssumedSubtype(this, t)) {
            return true;
        }
        DeclType dt = getSourceDeclType(ctx);
        if (dt instanceof ConcreteTypeMember) {
            ValueType vt = ((ConcreteTypeMember) dt).getResultType(View.from(path, ctx));
            ValueType ct = t.getCanonicalType(ctx);
            // if t is nominal but vt and ct are structural, assume this <: t in subsequent checking
            //if (t instanceof NominalType && ct instanceof StructuralType && vt instanceof StructuralType)
            ctx = new SubtypeAssumption(this, t, ctx);
            return vt.isSubtypeOf(ct, ctx, reason);
        } else if (dt instanceof TaggedTypeMember) {
            Type typeDefn = ((TaggedTypeMember) dt).getTypeDefinition(View.from(path, ctx));
            NominalType superType = typeDefn.getParentType(View.from(path, ctx));
            // TODO: this is not necessarily the whole check, but it does the nominal part of the check correctly
            return superType == null ? false : superType.isSubtypeOf(t, ctx, reason);
        } else {
            ValueType ct = t.getCanonicalType(ctx);
            // check for equality with the canonical type
            if (super.isSubtypeOf(ct, ctx, reason)) {
                return true;
            } else {
                // report error in terms of original type
                reason.setReason("type " + this + " is abstract and cannot be checked to be a subtype of " + t);
                return false;
            }
        }
    }

    @Override
    public <S, T> T acceptVisitor(ASTVisitor<S, T> emitILVisitor, S state) {
        return emitILVisitor.visit(state, this);
    }

    @Override
    public ValueType adapt(View v) {
        try {
            final Path newPath = path.adapt(v);
            return new NominalType(newPath, typeMember);
        } catch (RuntimeException e) {
            if (v.getContext() != null) {
                return getCanonicalType(v.getContext());
            } else {
                throw e;
            }
        }
    }

    @Override
    public Value getMetadata(TypeContext ctx) {
        DeclType t = getSourceDeclType(ctx);
        return t.getMetadataValue();
    }

    @Override
    public void checkWellFormed(TypeContext ctx) {
        // we are well-formed as long as we can get this without an error, and it doesn't return null but instead a well-formed type member
        final DeclType sourceDeclType = this.getSourceDeclType(ctx);
        if (sourceDeclType == null) {
            ToolError.reportError(ErrorMessage.NO_SUCH_TYPE_MEMBER, this, typeMember);
        }
        // would like to check this but can't, to avoid infinite recursion; other code should check it is well-formed
        //sourceDeclType.checkWellFormed(ctx);
    }

    @Override
    public ValueType doAvoid(String varName, TypeContext ctx, int count) {
        if (count > MAX_RECURSION_DEPTH) {
            // best effort failed
            // TODO: make this more principled
            return this;
        }
        if (path.getFreeVariables().contains(varName)) {
            try {
                DeclType dt = this.getSourceDeclType(ctx);
                if (dt instanceof ConcreteTypeMember) {
                    final ValueType type = ((ConcreteTypeMember) dt).getResultType(View.from(path, ctx));
                    if (type.equals(this)) {
                        // avoid infinite loops, just in case
                        // TODO: make this more principled
                        return this;
                    }
                    return type.doAvoid(varName, ctx, count + 1);
                }
            } catch (RuntimeException e) {
                // exception while trying to avoid; fall through to returning "this"
            }
            // was best effort anyway
            // TODO: be more principled
            return this;
        } else {
            return this;
        }
    }

}
